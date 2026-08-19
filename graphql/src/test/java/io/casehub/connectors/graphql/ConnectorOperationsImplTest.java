package io.casehub.connectors.graphql;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.ConnectorService;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorService;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.connectors.chat.spi.ChatPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorOperationsImplTest {

    private final List<InboundMessage> capturedInbound = new ArrayList<>();
    private ConnectorOperationsImpl ops;

    @BeforeEach
    void setUp() {
        capturedInbound.clear();

        var inboundService = InboundConnectorService.withEventBus(
                List.of(), capturedInbound::add);

        var chatPlatform = ChatPlatform.builder("slack")
                .messaging((channel, content) -> null)
                .build();
        var chatPlatformService = new ChatPlatformService(List.of(chatPlatform));

        var connector = new StubConnector("slack", true);
        var connectorService = ConnectorService.withEventSink(
                List.of(connector), msg -> {});

        ops = new ConnectorOperationsImpl(
                inboundService, connectorService, chatPlatformService,
                List.of(connector), List.of(), null, null);
    }

    @Test
    void injectChatConstructsCorrectInboundMessage() {
        var result = ops.injectChat("slack", "user-123", "C001", "Hello world");

        assertThat(result.ok()).isTrue();
        assertThat(result.connectorType()).isEqualTo("slack");
        assertThat(result.channel()).isEqualTo("C001");

        assertThat(capturedInbound).hasSize(1);
        var msg = capturedInbound.getFirst();
        assertThat(msg.connectorId()).isEqualTo(InboundConnectorIds.CHAT_INJECT);
        assertThat(msg.connectorType()).isEqualTo("slack");
        assertThat(msg.externalSenderId()).isEqualTo("user-123");
        assertThat(msg.externalChannelRef()).isEqualTo("C001");
        assertThat(msg.content()).isEqualTo("Hello world");
        assertThat(msg.attachments()).isEmpty();
        assertThat(msg.metadata()).containsEntry("source", "mcp-inject");
    }

    @Test
    void injectChatRejectsUnknownPlatform() {
        assertThatThrownBy(() -> ops.injectChat("telegram", "u1", "c1", "hi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("telegram");
    }

    @Test
    void sendNotificationDelegatesToConnectorService() {
        var result = ops.sendNotification(
                "slack", "https://hooks.slack.com/xxx", "Hello", null, null);

        assertThat(result.ok()).isTrue();
        assertThat(result.connectorId()).isEqualTo("slack");
        assertThat(result.destination()).isEqualTo("https://hooks.slack.com/xxx");
    }

    @Test
    void sendNotificationRejectsUnknownConnector() {
        assertThatThrownBy(() -> ops.sendNotification(
                "unknown", "dest", "body", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connectorStatusAggregatesAllSources() {
        var result = ops.connectorStatus();

        assertThat(result.outbound()).hasSize(1);
        assertThat(result.outbound().getFirst().id()).isEqualTo("slack");

        assertThat(result.chatPlatforms()).hasSize(1);
        assertThat(result.chatPlatforms().getFirst().id()).isEqualTo("slack");

        assertThat(result.inboundConnectors()).isEmpty();
    }

    @Test
    void sentMessagesReturnsEmptyWhenCaptureAbsent() {
        var result = ops.sentMessages(null, null);
        assertThat(result).isEmpty();
    }

    private record StubConnector(String id, boolean result) implements Connector {
        @Override
        public boolean send(ConnectorMessage message) {
            return result;
        }
    }
}
