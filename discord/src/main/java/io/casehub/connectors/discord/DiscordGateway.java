package io.casehub.connectors.discord;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.connectors.http.HttpHelper;

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
 * <p>Uses {@link HttpHelper#CLIENT} for WebSocket connections per the
 * shared-http-client protocol.
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
    // (onClose/onError) or by closeWebSocket() after ws.abort().
    private volatile CompletableFuture<Void> closeFuture;

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

    // Frame accumulation
    private final StringBuilder frameBuffer = new StringBuilder();

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
        frameBuffer.setLength(0);
        heartbeatAckReceived = true;

        // Choose URL: resume URL if resuming, otherwise initial gateway URL
        String url = (sessionId != null && resumeGatewayUrl != null)
                ? resumeGatewayUrl : gatewayUrl;

        // Append query parameters if not already present
        String connectUrl = url.contains("?") ? url : url + "?v=10&encoding=json";

        closeFuture = new CompletableFuture<>();

        webSocket = HttpHelper.CLIENT.newWebSocketBuilder()
                .buildAsync(URI.create(connectUrl), new WebSocket.Listener() {

                    @Override
                    public void onOpen(WebSocket ws) {
                        // Assign here — buildAsync().join() may not have returned yet
                        // when onText fires with the HELLO frame, so the field must be
                        // set before any message handling that calls sendText().
                        webSocket = ws;
                        ws.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        frameBuffer.append(data);
                        if (last) {
                            String message = frameBuffer.toString();
                            frameBuffer.setLength(0);
                            handleMessage(message);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        closeFuture.complete(null);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        closeFuture.completeExceptionally(error);
                    }
                })
                .join();

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
            // Do NOT call .join() — this method is called from the WebSocket's
            // onText callback thread, and blocking it would deadlock the I/O loop.
            ws.sendText(text, true).whenComplete((webSocket1, error) -> {
                if (error != null) {
                    LOG.warning("discord-gateway: send failed: " + error.getMessage());
                }
            });
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
                // Abort rather than sendClose — avoids blocking the event thread
                // and handles cases where the connection is already broken.
                ws.abort();
            } catch (Exception e) {
                // ignore — connection may already be closed
            }
        }
        // Signal the connect loop to proceed — abort() does not fire onClose/onError.
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
