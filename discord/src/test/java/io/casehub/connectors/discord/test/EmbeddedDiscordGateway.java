package io.casehub.connectors.discord.test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;

/**
 * Minimal WebSocket server simulating Discord Gateway v10 for testing.
 *
 * <p>Uses Vert.x {@link HttpServer#webSocketHandler} for standard RFC 6455
 * WebSocket communication. Text frames only. Follows the
 * {@code EmbeddedIrcServer} pattern: port 0, minimal API.
 */
public final class EmbeddedDiscordGateway {

    private static final int HEARTBEAT_INTERVAL_MS = 500;

    private Vertx vertx;
    private HttpServer server;
    private volatile boolean suppressAcks;
    private volatile boolean rejectConnections;
    private final CopyOnWriteArrayList<ServerWebSocket> sessions = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> receivedIdentifies = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> receivedResumes = new CopyOnWriteArrayList<>();
    private final AtomicInteger connectionCount = new AtomicInteger(0);
    private volatile CountDownLatch connectionLatch;
    private final AtomicInteger dispatchSeq = new AtomicInteger(0);

    @SuppressWarnings("deprecation") // ServerWebSocket.reject() — test infrastructure only
    public synchronized void start() {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer(new HttpServerOptions().setPort(0));

        server.webSocketHandler(ws -> {
            connectionCount.incrementAndGet();

            if (rejectConnections) {
                ws.reject();
                CountDownLatch latch = connectionLatch;
                if (latch != null) latch.countDown();
                return;
            }

            sessions.add(ws);

            CountDownLatch latch = connectionLatch;
            if (latch != null) latch.countDown();

            // Send HELLO (opcode 10)
            ws.writeTextMessage("{\"op\":10,\"d\":{\"heartbeat_interval\":" + HEARTBEAT_INTERVAL_MS + "}}");

            ws.textMessageHandler(msg -> handleClientMessage(msg, ws));

            ws.closeHandler(v -> sessions.remove(ws));

            ws.exceptionHandler(err -> sessions.remove(ws));
        });

        server.listen().toCompletionStage().toCompletableFuture().join();
    }

    public synchronized void stop() {
        if (server != null) {
            server.close().toCompletionStage().toCompletableFuture().join();
        }
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    public int getPort() {
        return server.actualPort();
    }

    public void suppressHeartbeatAcks(boolean suppress) {
        this.suppressAcks = suppress;
    }

    public void rejectConnections(boolean reject) {
        this.rejectConnections = reject;
    }

    /** Sends a DISPATCH (opcode 0) to all connected clients. */
    public void sendDispatch(String eventType, String dataJson) {
        int seq = dispatchSeq.incrementAndGet();
        String payload = "{\"op\":0,\"s\":" + seq + ",\"t\":\"" + eventType + "\",\"d\":" + dataJson + "}";
        for (ServerWebSocket ws : sessions) {
            ws.writeTextMessage(payload);
        }
    }

    /** Sends a DISPATCH split across two WebSocket frames (for multi-frame testing). */
    public void sendDispatchMultiFrame(String eventType, String dataJson) {
        int seq = dispatchSeq.incrementAndGet();
        String payload = "{\"op\":0,\"s\":" + seq + ",\"t\":\"" + eventType + "\",\"d\":" + dataJson + "}";
        int mid = payload.length() / 2;
        String part1 = payload.substring(0, mid);
        String part2 = payload.substring(mid);
        for (ServerWebSocket ws : sessions) {
            ws.writeFrame(WebSocketFrame.textFrame(part1, false));
            ws.writeFrame(WebSocketFrame.continuationFrame(Buffer.buffer(part2), true));
        }
    }

    /** Sends RECONNECT (opcode 7) to all connected clients. */
    public void sendReconnect() {
        String payload = "{\"op\":7,\"d\":null}";
        for (ServerWebSocket ws : sessions) {
            ws.writeTextMessage(payload);
        }
    }

    /** Sends INVALID_SESSION (opcode 9) to all connected clients. */
    public void sendInvalidSession(boolean resumable) {
        String payload = "{\"op\":9,\"d\":" + resumable + "}";
        for (ServerWebSocket ws : sessions) {
            ws.writeTextMessage(payload);
        }
    }

    /** Forcefully closes all client connections. */
    public void disconnectAllClients() {
        for (ServerWebSocket ws : sessions) {
            ws.close();
        }
    }

    public List<String> getReceivedIdentifies() {
        return List.copyOf(receivedIdentifies);
    }

    public List<String> getReceivedResumes() {
        return List.copyOf(receivedResumes);
    }

    public int getConnectionCount() {
        return connectionCount.get();
    }

    /** Sets a latch that counts down each time a new client connects. */
    public void expectConnections(int count) {
        connectionLatch = new CountDownLatch(count);
    }

    /** Waits for the expected number of connections. Returns false on timeout. */
    public boolean awaitConnections(long timeout, TimeUnit unit) throws InterruptedException {
        CountDownLatch latch = connectionLatch;
        return latch != null && latch.await(timeout, unit);
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    private void handleClientMessage(String message, ServerWebSocket ws) {
        // Parse opcode from JSON
        try {
            int opIndex = message.indexOf("\"op\":");
            if (opIndex < 0) return;
            int colonPos = message.indexOf(':', opIndex + 4);
            int commaPos = message.indexOf(',', colonPos);
            int bracePos = message.indexOf('}', colonPos);
            int end = commaPos >= 0 && (bracePos < 0 || commaPos < bracePos) ? commaPos : bracePos;
            int op = Integer.parseInt(message.substring(colonPos + 1, end).trim());

            switch (op) {
                case 1: // HEARTBEAT
                    if (!suppressAcks) {
                        ws.writeTextMessage("{\"op\":11,\"d\":null}");
                    }
                    break;
                case 2: // IDENTIFY
                    receivedIdentifies.add(message);
                    // Send READY dispatch
                    int seq = dispatchSeq.incrementAndGet();
                    ws.writeTextMessage("{\"op\":0,\"s\":" + seq + ",\"t\":\"READY\",\"d\":{" +
                            "\"session_id\":\"test-session-id\"," +
                            "\"resume_gateway_url\":\"ws://localhost:" + getPort() + "\"" +
                            "}}");
                    break;
                case 6: // RESUME
                    receivedResumes.add(message);
                    // Resume is acknowledged by replaying missed events — for tests, just continue
                    break;
            }
        } catch (NumberFormatException e) {
            // malformed opcode — ignore
        }
    }
}
