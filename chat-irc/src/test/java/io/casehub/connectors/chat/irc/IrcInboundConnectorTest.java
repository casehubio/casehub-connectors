package io.casehub.connectors.chat.irc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.irc.test.EmbeddedIrcServer;

class IrcInboundConnectorTest {

    private static final int FIXED_PORT = 16667;  // Fixed port for reconnect tests
    private EmbeddedIrcServer server;
    private IrcClient client;
    private IrcInboundConnector connector;
    private RecordingSink sink;

    @AfterEach
    void tearDown() {
        if (connector != null) {
            connector.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void inboundPrivmsgDeliveredToSink() {
        server = new EmbeddedIrcServer(0);
        server.start();
        client = new IrcClient("localhost", server.getPort(), "testbot");
        sink = new RecordingSink();
        connector = new IrcInboundConnector(client, Optional.of(List.of("#test")));

        connector.start(sink);
        await().atMost(Duration.ofSeconds(5)).until(client::isConnected);

        server.sendToChannel("#test", "alice", "hello world");

        await().atMost(Duration.ofSeconds(2)).until(() -> !sink.messages.isEmpty());
        assertThat(sink.messages).hasSize(1);

        InboundMessage msg = sink.messages.get(0);
        assertThat(msg.connectorId()).isEqualTo(InboundConnectorIds.IRC);
        assertThat(msg.connectorType()).isEqualTo(InboundConnectorTypes.IRC);
        assertThat(msg.externalSenderId()).isEqualTo("alice");
        assertThat(msg.externalChannelRef()).isEqualTo("#test");
        assertThat(msg.content()).isEqualTo("hello world");
        assertThat(msg.metadata().get("nick-prefix")).isEqualTo("alice!alice@localhost");
    }

    @Test
    void reconnectsAfterServerDrop() {
        server = new EmbeddedIrcServer(FIXED_PORT);
        server.start();
        client = new IrcClient("localhost", FIXED_PORT, "testbot");
        sink = new RecordingSink();
        connector = new IrcInboundConnector(client, Optional.of(List.of("#test")));

        connector.start(sink);
        await().atMost(Duration.ofSeconds(5)).until(client::isConnected);

        server.sendToChannel("#test", "alice", "first message");
        await().atMost(Duration.ofSeconds(2)).until(() -> !sink.messages.isEmpty());

        // Stop server
        server.stop();
        await().atMost(Duration.ofSeconds(3)).until(() -> !client.isConnected());

        // Start NEW server on SAME port
        server = new EmbeddedIrcServer(FIXED_PORT);
        server.start();

        // Wait for reconnect
        await().atMost(Duration.ofSeconds(10)).until(() -> client.isConnected());

        // Send another message
        sink.messages.clear();
        server.sendToChannel("#test", "bob", "reconnected message");
        await().atMost(Duration.ofSeconds(2)).until(() -> !sink.messages.isEmpty());

        assertThat(sink.messages).hasSize(1);
        assertThat(sink.messages.get(0).externalSenderId()).isEqualTo("bob");
        assertThat(sink.messages.get(0).content()).isEqualTo("reconnected message");
    }

    @Test
    void stopPreventsReconnect() throws InterruptedException {
        server = new EmbeddedIrcServer(0);
        server.start();
        client = new IrcClient("localhost", server.getPort(), "testbot");
        sink = new RecordingSink();
        connector = new IrcInboundConnector(client, Optional.of(List.of("#test")));

        connector.start(sink);
        await().atMost(Duration.ofSeconds(5)).until(client::isConnected);

        connector.stop();
        await().atMost(Duration.ofSeconds(2)).until(() -> !client.isConnected());

        // Verify no reconnect attempt — wait briefly and check still disconnected
        Thread.sleep(2000);
        assertThat(client.isConnected()).isFalse();
    }

    private static class RecordingSink implements io.casehub.connectors.InboundMessageSink {
        final List<InboundMessage> messages = new ArrayList<>();

        @Override
        public void receive(InboundMessage message) {
            messages.add(message);
        }
    }
}
