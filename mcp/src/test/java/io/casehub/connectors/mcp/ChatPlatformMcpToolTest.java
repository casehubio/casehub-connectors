package io.casehub.connectors.mcp;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.connectors.chat.model.*;
import io.casehub.connectors.chat.ref.InMemoryChatBackend;
import io.casehub.connectors.chat.ref.RefChatPlatform;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPlatformMcpToolTest {

    private ChatPlatformMcpTool tool;
    private InMemoryChatBackend backend;
    private McpToolTestSupport.RecordingBridge bridge;

    @BeforeEach
    void setUp() {
        backend = new InMemoryChatBackend();
        backend.createChannel("general", "topic", "desc", false);
        final var refPlatform = new RefChatPlatform(backend);
        final var service = new ChatPlatformService(List.of(refPlatform));
        bridge = new McpToolTestSupport.RecordingBridge();
        tool = new ChatPlatformMcpTool(service, bridge);
    }

    @Test
    void sendChatPlainText() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var result = tool.sendChat("ref", channelId, "hello", null,
                null, null, null, null, null, null, null, null, null, null);

        assertThat(result).startsWith("Sent to ");
        assertThat(backend.messages(new ChatChannelRef(channelId),
                java.time.Instant.EPOCH)).hasSize(1);
    }

    @Test
    void sendChatWithRichCard() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var result = tool.sendChat("ref", channelId, "fallback", null,
                "Deploy", "3 services", null, null, null, null, null, null, null, null);

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
                null, null, null, null, null, null, null, null, null, null);
        assertThat(result).startsWith("Failed:");
    }

    @Test
    void sendChatWithThread() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var firstResult = tool.sendChat("ref", channelId, "parent", null,
                null, null, null, null, null, null, null, null, null, null);
        assertThat(firstResult).contains("messageId=");

        final var messageId = firstResult.replaceAll(".*messageId=([^)]+)\\).*", "$1");
        final var replyResult = tool.sendChat("ref", channelId, "reply", messageId,
                null, null, null, null, null, null, null, null, null, null);
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
                null, null, null, null, null, null, null, null, null, null);
        assertThat(bridge.calls).hasSize(1);
        assertThat(bridge.calls.getFirst().connectorId()).isEqualTo("ref");
    }

    @Test
    void sendChatWithCardColor() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var result = tool.sendChat("ref", channelId, "fallback", null,
                "Alert", null, "16711680", null, null, null, null, null, null, null);

        assertThat(result).startsWith("Sent to ");
        final var messages = backend.messages(new ChatChannelRef(channelId),
                java.time.Instant.EPOCH);
        assertThat(messages).hasSize(1);
        final var card = messages.getFirst().content().cards().getFirst();
        assertThat(card.title()).isEqualTo("Alert");
        assertThat(card.color()).isEqualTo(16711680);
    }

    @Test
    void sendChatWithCardColorInvalid() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var result = tool.sendChat("ref", channelId, "fallback", null,
                "Alert", null, "not-a-number", null, null, null, null, null, null, null);

        assertThat(result).isEqualTo("Failed: cardColor must be a decimal integer");
    }

    @Test
    void sendChatWithCardFields() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var fields = """
                [{"name":"Status","value":"Running","inline":true},{"name":"Count","value":"5","inline":false}]""";
        final var result = tool.sendChat("ref", channelId, "fallback", null,
                "Deploy", null, null, null, null, null, null, null, fields, null);

        assertThat(result).startsWith("Sent to ");
        final var messages = backend.messages(new ChatChannelRef(channelId),
                java.time.Instant.EPOCH);
        assertThat(messages).hasSize(1);
        final var card = messages.getFirst().content().cards().getFirst();
        assertThat(card.fields()).hasSize(2);
        assertThat(card.fields().get(0).name()).isEqualTo("Status");
        assertThat(card.fields().get(0).inline()).isTrue();
        assertThat(card.fields().get(1).name()).isEqualTo("Count");
        assertThat(card.fields().get(1).inline()).isFalse();
    }

    @Test
    void sendChatWithCardFieldsInvalid() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var result = tool.sendChat("ref", channelId, "fallback", null,
                "Deploy", null, null, null, null, null, null, null, "not-json", null);

        assertThat(result).startsWith("Failed: cardFields must be a JSON array");
    }

    @Test
    void sendChatWithMultipleCards() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var cardsJson = """
                [{"title":"Card 1","description":"First"},{"title":"Card 2","description":"Second"}]""";
        final var result = tool.sendChat("ref", channelId, "fallback", null,
                null, null, null, null, null, null, null, null, null, cardsJson);

        assertThat(result).startsWith("Sent to ");
        final var messages = backend.messages(new ChatChannelRef(channelId),
                java.time.Instant.EPOCH);
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().content().cards()).hasSize(2);
        assertThat(messages.getFirst().content().cards().get(0).title()).isEqualTo("Card 1");
        assertThat(messages.getFirst().content().cards().get(1).title()).isEqualTo("Card 2");
    }

    @Test
    void sendChatWithMultipleCardsOverridesFlatParams() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var cardsJson = """
                [{"title":"Array Card"}]""";
        final var result = tool.sendChat("ref", channelId, "fallback", null,
                "Flat Card", "flat desc", null, null, null, null, null, null, null, cardsJson);

        assertThat(result).startsWith("Sent to ");
        final var messages = backend.messages(new ChatChannelRef(channelId),
                java.time.Instant.EPOCH);
        final var cards = messages.getFirst().content().cards();
        assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().title()).isEqualTo("Array Card");
    }

    @Test
    void sendChatWithMultipleCardsWithColorAndFields() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var cardsJson = """
                [{"title":"Status","color":255,"fields":[{"name":"Env","value":"prod","inline":true}]}]""";
        final var result = tool.sendChat("ref", channelId, "fallback", null,
                null, null, null, null, null, null, null, null, null, cardsJson);

        assertThat(result).startsWith("Sent to ");
        final var card = backend.messages(new ChatChannelRef(channelId),
                java.time.Instant.EPOCH).getFirst().content().cards().getFirst();
        assertThat(card.title()).isEqualTo("Status");
        assertThat(card.color()).isEqualTo(255);
        assertThat(card.fields()).hasSize(1);
        assertThat(card.fields().getFirst().name()).isEqualTo("Env");
    }

    @Test
    void sendChatWithMultipleCardsInvalidJson() {
        final var channelId = backend.listChannels().getFirst().ref().id();
        final var result = tool.sendChat("ref", channelId, "fallback", null,
                null, null, null, null, null, null, null, null, null, "not-json");

        assertThat(result).isEqualTo("Failed: cards must be a JSON array of card objects");
    }
}
