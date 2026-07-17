package io.casehub.connectors.discord;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.discord.test.EmbeddedDiscordGateway;

class DiscordGatewayTest {

    private static final String TOKEN = "test-bot-token";
    private static final int INTENTS = (1 << 9); // GUILD_MESSAGES

    private EmbeddedDiscordGateway server;
    private DiscordGateway gateway;

    @BeforeEach
    void setup() throws Exception {
        server = new EmbeddedDiscordGateway();
        server.start();
    }

    @AfterEach
    void teardown() {
        if (gateway != null) {
            gateway.disconnect();
        }
        server.stop();
    }

    @Test
    void connectAndIdentify() throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(1);
        AtomicReference<String> readyEventType = new AtomicReference<>();

        gateway = new DiscordGateway();
        gateway.connect(gatewayUrl(), TOKEN, INTENTS, (eventType, data) -> {
            if ("READY".equals(eventType)) {
                readyEventType.set(eventType);
                readyLatch.countDown();
            }
        });

        assertThat(readyLatch.await(5, TimeUnit.SECONDS))
                .as("Should receive READY event").isTrue();
        assertThat(readyEventType.get()).isEqualTo("READY");
        assertThat(gateway.isConnected()).isTrue();

        // Verify IDENTIFY was sent with correct token
        List<String> identifies = server.getReceivedIdentifies();
        assertThat(identifies).hasSize(1);
        assertThat(identifies.get(0)).contains("\"token\":\"" + TOKEN + "\"");
        assertThat(identifies.get(0)).contains("\"intents\":" + INTENTS);
    }

    @Test
    void heartbeatLoop() throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(1);

        gateway = new DiscordGateway();
        gateway.connect(gatewayUrl(), TOKEN, INTENTS, (eventType, data) -> {
            if ("READY".equals(eventType)) readyLatch.countDown();
        });
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Send a dispatch to set sequence number
        server.sendDispatch("MESSAGE_CREATE", "{\"content\":\"hello\"}");
        Thread.sleep(200);

        // Wait for at least 2 heartbeats (interval=500ms, so ~1200ms should suffice)
        Thread.sleep(1200);

        assertThat(gateway.isConnected()).isTrue();
    }

    @Test
    void heartbeatAckTimeout() throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(1);

        gateway = new DiscordGateway();
        gateway.connect(gatewayUrl(), TOKEN, INTENTS, (eventType, data) -> {
            if ("READY".equals(eventType)) readyLatch.countDown();
        });
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Suppress ACKs — should trigger reconnect after heartbeat timeout.
        // With heartbeat_interval=500ms and jitter, the missed ACK detection
        // happens after ~1000ms (jitter + interval + check).
        server.suppressHeartbeatAcks(true);

        // Re-enable acks so the reconnection handshake succeeds
        Thread.sleep(300);
        server.suppressHeartbeatAcks(false);

        // After heartbeat timeout, the gateway reconnects and sends RESUME
        // (sessionId was cached from initial READY). Wait for the RESUME.
        org.awaitility.Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> !server.getReceivedResumes().isEmpty());
    }

    @Test
    void dispatchEvent() throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(1);
        CountDownLatch eventLatch = new CountDownLatch(1);
        AtomicReference<String> receivedType = new AtomicReference<>();
        AtomicReference<JsonNode> receivedData = new AtomicReference<>();

        gateway = new DiscordGateway();
        gateway.connect(gatewayUrl(), TOKEN, INTENTS, (eventType, data) -> {
            if ("READY".equals(eventType)) {
                readyLatch.countDown();
            } else {
                receivedType.set(eventType);
                receivedData.set(data);
                eventLatch.countDown();
            }
        });
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        server.sendDispatch("MESSAGE_CREATE", "{\"content\":\"hello world\",\"channel_id\":\"ch1\"}");

        assertThat(eventLatch.await(5, TimeUnit.SECONDS))
                .as("Should receive dispatch event").isTrue();
        assertThat(receivedType.get()).isEqualTo("MESSAGE_CREATE");
        assertThat(receivedData.get().get("content").asText()).isEqualTo("hello world");
    }

    @Test
    void resumeOnDisconnect() throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(1);

        gateway = new DiscordGateway();
        gateway.connect(gatewayUrl(), TOKEN, INTENTS, (eventType, data) -> {
            if ("READY".equals(eventType)) readyLatch.countDown();
        });
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Send a dispatch so sequence is > 0
        server.sendDispatch("MESSAGE_CREATE", "{\"content\":\"test\"}");
        Thread.sleep(200);

        // Expect a reconnection
        server.expectConnections(1);

        // Forcefully disconnect the client
        server.disconnectAllClients();

        assertThat(server.awaitConnections(10, TimeUnit.SECONDS))
                .as("Should reconnect after disconnect").isTrue();

        // Wait for RESUME to be sent (replaces fixed Thread.sleep)
        org.awaitility.Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> !server.getReceivedResumes().isEmpty());

        List<String> resumes = server.getReceivedResumes();
        assertThat(resumes.get(0)).contains("\"session_id\":\"test-session-id\"");
        assertThat(resumes.get(0)).contains("\"token\":\"" + TOKEN + "\"");
    }

    @Test
    void invalidSessionFallback() throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(1);

        gateway = new DiscordGateway();
        gateway.connect(gatewayUrl(), TOKEN, INTENTS, (eventType, data) -> {
            if ("READY".equals(eventType)) readyLatch.countDown();
        });
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        int identifyCountBefore = server.getReceivedIdentifies().size();

        // Server sends INVALID_SESSION with d=false (not resumable)
        // This should trigger a full re-IDENTIFY after reconnect
        server.expectConnections(1);
        server.sendInvalidSession(false);

        assertThat(server.awaitConnections(10, TimeUnit.SECONDS))
                .as("Should reconnect after INVALID_SESSION").isTrue();

        // Wait for the new IDENTIFY to arrive (replaces fixed Thread.sleep)
        org.awaitility.Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> server.getReceivedIdentifies().size() > identifyCountBefore);
    }

    @Test
    void reconnectBackoff() throws Exception {
        CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();
        Logger gatewayLogger = Logger.getLogger(DiscordGateway.class.getName());
        Handler testHandler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        gatewayLogger.addHandler(testHandler);
        gatewayLogger.setLevel(Level.ALL);

        try {
            // Reject connections — forces repeated reconnect attempts
            server.rejectConnections(true);

            gateway = new DiscordGateway();
            long startTime = System.currentTimeMillis();
            gateway.connect(gatewayUrl(), TOKEN, INTENTS, (t, d) -> {});

            // Wait for at least 3 "connection failed" log entries
            // Backoff: attempt 1 (immediate), sleep 1s, attempt 2, sleep 2s, attempt 3
            // Total minimum: ~3s
            org.awaitility.Awaitility.await()
                    .atMost(15, TimeUnit.SECONDS)
                    .until(() -> records.stream()
                            .filter(r -> r.getMessage() != null && r.getMessage().contains("connection failed"))
                            .count() >= 3);

            long elapsed = System.currentTimeMillis() - startTime;
            // At least 2s total backoff (1s + 2s = 3s minimum, 2s floor for timing tolerance)
            assertThat(elapsed).isGreaterThan(2000);
        } finally {
            gatewayLogger.removeHandler(testHandler);
        }
    }

    @Test
    void reconnectBackoff_logEscalation() throws Exception {
        CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();
        Logger gatewayLogger = Logger.getLogger(DiscordGateway.class.getName());
        Handler testHandler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        gatewayLogger.addHandler(testHandler);
        gatewayLogger.setLevel(Level.ALL);

        try {
            // Reject all connections
            server.rejectConnections(true);

            gateway = new DiscordGateway();
            gateway.connect(gatewayUrl(), TOKEN, INTENTS, (t, d) -> {});

            // Wait for at least 5 "connection failed" log entries
            // Backoff: 1s + 2s + 4s + 8s + 16s = 31s minimum to reach attempt 6
            // But we need 5, so 1s + 2s + 4s + 8s = 15s minimum
            org.awaitility.Awaitility.await()
                    .atMost(60, TimeUnit.SECONDS)
                    .until(() -> records.stream()
                            .filter(r -> r.getMessage() != null && r.getMessage().contains("connection failed"))
                            .count() >= 5);

            List<LogRecord> connectionFailures = records.stream()
                    .filter(r -> r.getMessage() != null && r.getMessage().contains("connection failed"))
                    .toList();

            assertThat(connectionFailures.size()).isGreaterThanOrEqualTo(5);

            // First 4 should be WARNING
            for (int i = 0; i < 4 && i < connectionFailures.size(); i++) {
                assertThat(connectionFailures.get(i).getLevel())
                        .as("Attempt %d should be WARNING", i + 1)
                        .isEqualTo(Level.WARNING);
            }

            // 5th+ should be SEVERE
            assertThat(connectionFailures.get(4).getLevel())
                    .as("Attempt 5 should be SEVERE")
                    .isEqualTo(Level.SEVERE);
        } finally {
            gatewayLogger.removeHandler(testHandler);
        }
    }

    @Test
    void multiFrameMessage() throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(1);
        CountDownLatch eventLatch = new CountDownLatch(1);
        AtomicReference<String> receivedType = new AtomicReference<>();
        AtomicReference<JsonNode> receivedData = new AtomicReference<>();

        gateway = new DiscordGateway();
        gateway.connect(gatewayUrl(), TOKEN, INTENTS, (eventType, data) -> {
            if ("READY".equals(eventType)) {
                readyLatch.countDown();
            } else {
                receivedType.set(eventType);
                receivedData.set(data);
                eventLatch.countDown();
            }
        });
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Send a dispatch split across two frames
        server.sendDispatchMultiFrame("GUILD_CREATE", "{\"id\":\"guild1\",\"name\":\"Test Guild\"}");

        assertThat(eventLatch.await(5, TimeUnit.SECONDS))
                .as("Should receive multi-frame dispatch event").isTrue();
        assertThat(receivedType.get()).isEqualTo("GUILD_CREATE");
        assertThat(receivedData.get().get("name").asText()).isEqualTo("Test Guild");
    }

    @Test
    void guildCreate_presencesDelivered() throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(1);
        CountDownLatch eventLatch = new CountDownLatch(1);
        AtomicReference<JsonNode> receivedData = new AtomicReference<>();

        gateway = new DiscordGateway();
        gateway.connect(gatewayUrl(), TOKEN, INTENTS, (eventType, data) -> {
            if ("READY".equals(eventType)) {
                readyLatch.countDown();
            } else if ("GUILD_CREATE".equals(eventType)) {
                receivedData.set(data);
                eventLatch.countDown();
            }
        });
        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Send GUILD_CREATE with presences array
        String guildData = "{\"id\":\"guild1\",\"name\":\"Test Guild\"," +
                "\"presences\":[{\"user\":{\"id\":\"user1\"},\"status\":\"online\"}," +
                "{\"user\":{\"id\":\"user2\"},\"status\":\"idle\"}]}";
        server.sendDispatch("GUILD_CREATE", guildData);

        assertThat(eventLatch.await(5, TimeUnit.SECONDS))
                .as("Should receive GUILD_CREATE with presences").isTrue();
        assertThat(receivedData.get().get("presences")).hasSize(2);
        assertThat(receivedData.get().get("presences").get(0).get("status").asText()).isEqualTo("online");
    }

    private String gatewayUrl() {
        return "ws://localhost:" + server.getPort();
    }
}
