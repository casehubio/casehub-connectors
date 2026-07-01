package io.casehub.connectors.mcp;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.connectors.chat.model.*;
import io.casehub.connectors.chat.ref.InMemoryChatBackend;
import io.casehub.connectors.chat.ref.RefChatPlatform;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPlatformMcpToolTest {

    private ChatPlatformMcpTool tool;
    private InMemoryChatBackend backend;
    private RecordingBridge bridge;

    @BeforeEach
    void setUp() {
        backend = new InMemoryChatBackend();
        backend.createChannel("general", "topic", "desc", false);
        final var refPlatform = new RefChatPlatform(backend);
        final var service = new ChatPlatformService(List.of(refPlatform));
        bridge = new RecordingBridge();
        tool = new ChatPlatformMcpTool(service, bridge);
    }

    @Test
    void sendChatPlainText() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var result = tool.sendChat("ref", channelId, "hello", null,
                null, null, null, null, null, null, null, null, null);

        assertThat(result).startsWith("Sent to ");
        assertThat(backend.messages(new ChatChannelRef(channelId),
                java.time.Instant.EPOCH)).hasSize(1);
    }

    @Test
    void sendChatWithRichCard() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var result = tool.sendChat("ref", channelId, "fallback", null,
                "Deploy", "3 services", null, null, null, null, null, null, null);

        assertThat(result).startsWith("Sent to ");
        final var messages = backend.messages(new ChatChannelRef(channelId),
                java.time.Instant.EPOCH);
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().content().cards()).hasSize(1);
        assertThat(messages.getFirst().content().cards().getFirst().title())
                .isEqualTo("Deploy");
    }

    @Test
    void sendChatUnknownPlatform() {
        final var result = tool.sendChat("nonexistent", "ch", "hi", null,
                null, null, null, null, null, null, null, null, null);
        assertThat(result).startsWith("Failed:");
    }

    @Test
    void sendChatWithThread() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var firstResult = tool.sendChat("ref", channelId, "parent", null,
                null, null, null, null, null, null, null, null, null);
        assertThat(firstResult).contains("messageId=");

        final var messageId = firstResult.replaceAll(".*messageId=([^)]+)\\).*", "$1");
        final var replyResult = tool.sendChat("ref", channelId, "reply", messageId,
                null, null, null, null, null, null, null, null, null);
        assertThat(replyResult).startsWith("Sent to ");
    }

    @Test
    void listChatChannels() {
        final var result = tool.listChatChannels("ref");
        assertThat(result).contains("general");
    }

    @Test
    void listChatChannelsUnknownPlatform() {
        final var result = tool.listChatChannels("nonexistent");
        assertThat(result).startsWith("Failed:");
    }

    @Test
    void meshBridgeNotified() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        tool.sendChat("ref", channelId, "hello", null,
                null, null, null, null, null, null, null, null, null);
        assertThat(bridge.calls).hasSize(1);
        assertThat(bridge.calls.getFirst().connectorId()).isEqualTo("ref");
    }

    static class RecordingBridge implements ConnectorMeshBridge {
        final java.util.List<Call> calls = new java.util.ArrayList<>();
        record Call(String connectorId, String destination, String content) {}
        @Override
        public void notifyDelivered(String connectorId, String destination, String content) {
            calls.add(new Call(connectorId, destination, content));
        }
    }
}
