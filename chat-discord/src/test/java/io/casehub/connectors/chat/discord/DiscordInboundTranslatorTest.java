package io.casehub.connectors.chat.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ReceivedMessage;

class DiscordInboundTranslatorTest {

    private DiscordInboundTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new DiscordInboundTranslator();
    }

    @Test
    void connectorType_returnsDiscord() {
        assertThat(translator.connectorType()).isEqualTo("discord");
    }

    @Test
    void translate_basicMessage() {
        Instant now = Instant.now();
        InboundMessage inbound = new InboundMessage(
                "discord-inbound",
                "discord",
                "user-123",
                "channel-456",
                "Hello world",
                List.of(),
                now,
                Map.of("discord-message-id", "msg-789",
                       "discord-guild-id", "guild-999"),
                null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.platformId()).isEqualTo("discord");
        assertThat(received.channel().id()).isEqualTo("channel-456");
        assertThat(received.messageRef().messageId()).isEqualTo("msg-789");
        assertThat(received.messageRef().channel().id()).isEqualTo("channel-456");
        assertThat(received.parentRef()).isNull();
        assertThat(received.sender().id()).isEqualTo("user-123");
        assertThat(received.content().text()).isEqualTo("Hello world");
        assertThat(received.receivedAt()).isEqualTo(now);
    }

    @Test
    void translate_replyMessage() {
        Instant now = Instant.now();
        InboundMessage inbound = new InboundMessage(
                "discord-inbound",
                "discord",
                "user-123",
                "channel-456",
                "Reply text",
                List.of(),
                now,
                Map.of("discord-message-id", "msg-789",
                       "discord-guild-id", "guild-999",
                       "discord-reference-id", "ref-111"),
                null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.parentRef()).isNotNull();
        assertThat(received.parentRef().messageId()).isEqualTo("ref-111");
        assertThat(received.parentRef().channel().id()).isEqualTo("channel-456");
    }

    @Test
    void translate_noReference() {
        Instant now = Instant.now();
        InboundMessage inbound = new InboundMessage(
                "discord-inbound",
                "discord",
                "user-123",
                "channel-456",
                "No reply",
                List.of(),
                now,
                Map.of("discord-message-id", "msg-789",
                       "discord-guild-id", "guild-999"),
                null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.parentRef()).isNull();
    }
}
