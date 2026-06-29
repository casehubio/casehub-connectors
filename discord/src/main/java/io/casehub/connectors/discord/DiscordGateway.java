package io.casehub.connectors.discord;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;

/**
 * Discord Gateway v10 WebSocket client.
 *
 * <p>Manages a long-lived WebSocket connection to Discord for real-time event
 * delivery. Handles the full lifecycle: HELLO, IDENTIFY, HEARTBEAT loop,
 * DISPATCH events, RESUME on disconnect, and re-IDENTIFY on INVALID_SESSION.
 *
 * <p>NOT a CDI bean — instantiated by {@code DiscordInboundConnector} which
 * controls its lifecycle. The connection lifecycle is explicit, not container-managed.
 *
 * <p>Uses Vert.x WebSocket client for Gateway connections (RFC 6455 compliant).
 * REST calls still use {@link io.casehub.connectors.http.HttpHelper#CLIENT}.
 */
public class DiscordGateway {

    private static final Logger LOG = Logger.getLogger(DiscordGateway.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_BACKOFF_SECONDS = 60;

    // Gateway opcodes
    private static final int OP_DISPATCH = 0;
    private static final int OP_HEARTBEAT = 1;
    private static final int OP_IDENTIFY = 2;
    private static final int OP_RESUME = 6;
    private static final int OP_RECONNECT = 7;
    private static final int OP_INVALID_SESSION = 9;
    private static final int OP_HELLO = 10;
    private static final int OP_HEARTBEAT_ACK = 11;

    enum GatewayState {
        DISCONNECTED, CONNECTING, HELLO_RECEIVED, IDENTIFYING, READY, RUNNING, RESUMING
    }

    private volatile GatewayState state = GatewayState.DISCONNECTED;
    private volatile WebSocket webSocket;
    private volatile Thread heartbeatThread;
    private volatile Thread connectThread;
    private volatile boolean stopping;

    // Signalled when the current connection should close — either by the server
    // (close/exception handler) or by closeWebSocket().
    private volatile CompletableFuture<Void> closeFuture;

    // Vert.x resources — created per connect(), closed on disconnect()
    private volatile Vertx vertx;
    private volatile HttpClient httpClient;

    private String token;
    private int intents;
    private GatewayEventListener listener;
    private String gatewayUrl;

    // Session state for resume
    private volatile String sessionId;
    private volatile String resumeGatewayUrl;
    private final AtomicLong lastSequence = new AtomicLong(-1);

    // Heartbeat state
    private volatile long heartbeatIntervalMs;
    private volatile boolean heartbeatAckReceived;

    /**
     * Connects to the Discord Gateway and begins event delivery.
     *
     * <p>Starts the connection loop on a virtual thread and returns immediately.
     * The loop handles reconnection with exponential backoff. Events are delivered
     * via the provided {@link GatewayEventListener}.
     *
     * @param gatewayUrl the WebSocket Gateway URL (e.g., {@code ws://...} or {@code wss://...})
     * @param token      the bot token
     * @param intents    Gateway intents bitmask
     * @param listener   callback for DISPATCH events
     */
    public void connect(String gatewayUrl, String token, int intents,
                        GatewayEventListener listener) {
        if (connectThread != null) {
            return; // already connecting
        }
        this.gatewayUrl = gatewayUrl;
        this.token = token;
        this.intents = intents;
        this.listener = listener;
        this.stopping = false;

        // Create Vert.x resources for the lifetime of this connection
        vertx = Vertx.vertx();
        httpClient = vertx.createHttpClient(new HttpClientOptions());

        connectThread = Thread.ofVirtual().name("discord-gateway-connect").start(this::connectLoop);
    }

    /**
     * Disconnects from the Gateway, stopping heartbeat and event delivery.
     */
    public void disconnect() {
        stopping = true;
        state = GatewayState.DISCONNECTED;
        stopHeartbeat();
        closeWebSocket();
        Thread ct = connectThread;
        if (ct != null) {
            ct.interrupt();
            connectThread = null;
        }

        // Close Vert.x resources
        HttpClient hc = httpClient;
        if (hc != null) {
            try {
                hc.close().toCompletionStage().toCompletableFuture().join();
            } catch (Exception e) {
                // ignore
            }
            httpClient = null;
        }
        Vertx v = vertx;
        if (v != null) {
            try {
                v.close().toCompletionStage().toCompletableFuture().join();
            } catch (Exception e) {
                // ignore
            }
            vertx = null;
        }
    }

    /**
     * Returns whether the Gateway connection is active and receiving events.
     */
    public boolean isConnected() {
        return state == GatewayState.RUNNING || state == GatewayState.READY;
    }

    // Set to true when the connection reaches RUNNING state; reset on each connect attempt.
    private volatile boolean connectionWasSuccessful;

    private void connectLoop() {
        int backoffSeconds = 1;
        int consecutiveFailures = 0;

        while (!stopping && !Thread.currentThread().isInterrupted()) {
            connectionWasSuccessful = false;
            try {
                doConnect();
            } catch (Exception e) {
                // fall through to backoff logic
            }
            if (!stopping) {
                if (connectionWasSuccessful) {
                    // Connection was established and later dropped — reset backoff
                    backoffSeconds = 1;
                    consecutiveFailures = 0;
                } else {
                    // Connection failed before reaching RUNNING — apply backoff
                    consecutiveFailures++;
                    Level level = consecutiveFailures >= 5 ? Level.SEVERE : Level.WARNING;
                    LOG.log(level, "discord-gateway: connection failed (attempt "
                            + consecutiveFailures + ")");
                    sleepQuietly(backoffSeconds * 1000L);
                    backoffSeconds = Math.min(backoffSeconds * 2, MAX_BACKOFF_SECONDS);
                }
            }
        }
    }

    private void doConnect() throws Exception {
        state = GatewayState.CONNECTING;
        heartbeatAckReceived = true;

        // Choose URL: resume URL if resuming, otherwise initial gateway URL
        String url = (sessionId != null && resumeGatewayUrl != null)
                ? resumeGatewayUrl : gatewayUrl;

        // Append query parameters if not already present
        String connectUrl = url.contains("?") ? url : url + "?v=10&encoding=json";

        closeFuture = new CompletableFuture<>();

        // Parse the URL to extract host, port, ssl, path
        URI uri = URI.create(connectUrl);
        boolean ssl = "wss".equalsIgnoreCase(uri.getScheme());
        String host = uri.getHost();
        int port = uri.getPort();
        if (port < 0) {
            port = ssl ? 443 : 80;
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            path = path + "?" + query;
        }

        WebSocketConnectOptions opts = new WebSocketConnectOptions()
                .setHost(host)
                .setPort(port)
                .setSsl(ssl)
                .setURI(path);

        webSocket = httpClient.webSocket(opts)
                .toCompletionStage().toCompletableFuture().join();

        WebSocket ws = webSocket;
        ws.textMessageHandler(this::handleMessage);
        ws.closeHandler(v -> closeFuture.complete(null));
        ws.exceptionHandler(err -> closeFuture.completeExceptionally(err));

        // Block until connection closes
        try {
            closeFuture.join();
        } catch (Exception e) {
            // Expected when closeWebSocket() completes the future exceptionally
        } finally {
            stopHeartbeat();
            if (!stopping && state != GatewayState.DISCONNECTED) {
                state = GatewayState.CONNECTING;
            }
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode json = MAPPER.readTree(message);
            int op = json.get("op").asInt();

            switch (op) {
                case OP_HELLO:
                    handleHello(json);
                    break;
                case OP_HEARTBEAT_ACK:
                    heartbeatAckReceived = true;
                    break;
                case OP_DISPATCH:
                    handleDispatch(json);
                    break;
                case OP_RECONNECT:
                    handleReconnect();
                    break;
                case OP_INVALID_SESSION:
                    handleInvalidSession(json);
                    break;
                case OP_HEARTBEAT:
                    // Server-requested heartbeat — send immediately
                    sendHeartbeat();
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LOG.warning("discord-gateway: failed to parse message: " + e.getMessage());
        }
    }

    private void handleHello(JsonNode json) {
        heartbeatIntervalMs = json.get("d").get("heartbeat_interval").asLong();
        state = GatewayState.HELLO_RECEIVED;

        // Start heartbeat thread
        startHeartbeat();

        // Send IDENTIFY or RESUME
        if (sessionId != null) {
            sendResume();
        } else {
            sendIdentify();
        }
    }

    private void handleDispatch(JsonNode json) {
        // Update sequence
        if (json.has("s") && !json.get("s").isNull()) {
            lastSequence.set(json.get("s").asLong());
        }

        String eventType = json.has("t") && !json.get("t").isNull()
                ? json.get("t").asText() : null;
        JsonNode data = json.get("d");

        if ("READY".equals(eventType)) {
            sessionId = data.get("session_id").asText();
            if (data.has("resume_gateway_url")) {
                resumeGatewayUrl = data.get("resume_gateway_url").asText();
            }
            state = GatewayState.RUNNING;
            connectionWasSuccessful = true;
        }

        if (eventType != null && listener != null) {
            try {
                listener.onEvent(eventType, data);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "discord-gateway: listener threw", e);
            }
        }
    }

    private void handleReconnect() {
        // Close and reconnect — session remains valid for RESUME
        closeWebSocket();
    }

    private void handleInvalidSession(JsonNode json) {
        boolean resumable = json.get("d").asBoolean(false);
        if (!resumable) {
            // Clear session — force full re-IDENTIFY on next connect
            sessionId = null;
            resumeGatewayUrl = null;
            lastSequence.set(-1);
        }
        // Close and reconnect
        closeWebSocket();
    }

    private void sendIdentify() {
        state = GatewayState.IDENTIFYING;
        String identify = "{\"op\":2,\"d\":{" +
                "\"token\":\"" + token + "\"," +
                "\"intents\":" + intents + "," +
                "\"properties\":{\"os\":\"linux\",\"browser\":\"casehub\",\"device\":\"casehub\"}" +
                "}}";
        sendText(identify);
    }

    private void sendResume() {
        state = GatewayState.RESUMING;
        long seq = lastSequence.get();
        String resume = "{\"op\":6,\"d\":{" +
                "\"token\":\"" + token + "\"," +
                "\"session_id\":\"" + sessionId + "\"," +
                "\"seq\":" + (seq >= 0 ? seq : "null") +
                "}}";
        sendText(resume);
    }

    private void sendHeartbeat() {
        long seq = lastSequence.get();
        String heartbeat = "{\"op\":1,\"d\":" + (seq >= 0 ? seq : "null") + "}";
        sendText(heartbeat);
    }

    private void sendText(String text) {
        WebSocket ws = webSocket;
        if (ws != null) {
            ws.writeTextMessage(text).onFailure(error ->
                    LOG.warning("discord-gateway: send failed: " + error.getMessage()));
        }
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatAckReceived = true;

        heartbeatThread = Thread.ofVirtual().name("discord-heartbeat").start(() -> {
            try {
                // First heartbeat after interval * jitter
                long jitterDelay = (long) (heartbeatIntervalMs * ThreadLocalRandom.current().nextDouble());
                Thread.sleep(jitterDelay);

                while (!stopping && !Thread.currentThread().isInterrupted()) {
                    if (!heartbeatAckReceived) {
                        // Missing ACK — close and reconnect
                        LOG.warning("discord-gateway: heartbeat ACK missed, reconnecting");
                        closeWebSocket();
                        return;
                    }
                    heartbeatAckReceived = false;
                    sendHeartbeat();
                    Thread.sleep(heartbeatIntervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void stopHeartbeat() {
        Thread hb = heartbeatThread;
        if (hb != null) {
            hb.interrupt();
            heartbeatThread = null;
        }
    }

    private void closeWebSocket() {
        WebSocket ws = webSocket;
        if (ws != null) {
            try {
                ws.close().onComplete(ar -> {});
            } catch (Exception e) {
                // ignore — connection may already be closed
            }
        }
        // Signal the connect loop to proceed
        CompletableFuture<Void> cf = closeFuture;
        if (cf != null) {
            cf.complete(null);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
