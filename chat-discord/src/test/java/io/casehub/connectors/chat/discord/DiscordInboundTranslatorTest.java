package io.casehub.connectors.chat.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.Attachment;
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

    @Test
    void translate_forwardsAttachments() {
        Attachment att = new Attachment("file.pdf", "application/pdf", new byte[]{1, 2, 3});
        InboundMessage inbound = new InboundMessage(
                "discord-inbound", "discord", "user-123", "channel-456",
                "See attached", List.of(att), Instant.now(),
                Map.of("discord-message-id", "msg-789", "discord-guild-id", "guild-999"),
                null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.content().attachments()).hasSize(1);
        assertThat(received.content().attachments().get(0).filename()).isEqualTo("file.pdf");
        assertThat(received.content().attachments().get(0).contentType()).isEqualTo("application/pdf");
    }

    @Test
    void translate_embedsParsedAsRichCards() {
        final String embeds = """
                [{"title":"Deploy Status","description":"3 services updated","color":65280,\
                "fields":[{"name":"Env","value":"prod","inline":true}],\
                "footer":{"text":"via CI"},"author":{"name":"bot"}}]""";
        InboundMessage inbound = new InboundMessage(
                "discord-inbound", "discord", "user-123", "channel-456",
                "text", List.of(), Instant.now(),
                Map.of("discord-message-id", "msg-789", "discord-guild-id", "guild-999",
                       "discord-embeds", embeds),
                null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.content().cards()).hasSize(1);
        var card = received.content().cards().getFirst();
        assertThat(card.title()).isEqualTo("Deploy Status");
        assertThat(card.description()).isEqualTo("3 services updated");
        assertThat(card.color()).isEqualTo(65280);
        assertThat(card.footer()).isEqualTo("via CI");
        assertThat(card.author()).isEqualTo("bot");
        assertThat(card.fields()).hasSize(1);
        assertThat(card.fields().getFirst().name()).isEqualTo("Env");
        assertThat(card.fields().getFirst().inline()).isTrue();
    }

    @Test
    void translate_noEmbeds_emptyCards() {
        InboundMessage inbound = new InboundMessage(
                "discord-inbound", "discord", "user-123", "channel-456",
                "plain", List.of(), Instant.now(),
                Map.of("discord-message-id", "msg-789", "discord-guild-id", "guild-999"),
                null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.content().cards()).isEmpty();
    }

    @Test
    void parseEmbeds_multipleEmbeds() {
        final String json = """
                [{"title":"One","description":"First"},{"title":"Two","description":"Second"}]""";
        var cards = DiscordInboundTranslator.parseEmbeds(json);
        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).title()).isEqualTo("One");
        assertThat(cards.get(1).title()).isEqualTo("Two");
    }

    @Test
    void parseEmbeds_embedWithImagesAndUrl() {
        final String json = """
                [{"title":"Preview","url":"https://example.com",\
                "thumbnail":{"url":"https://img/thumb.png"},\
                "image":{"url":"https://img/full.png"}}]""";
        var cards = DiscordInboundTranslator.parseEmbeds(json);
        assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().url()).isEqualTo("https://example.com");
        assertThat(cards.getFirst().thumbnailUrl()).isEqualTo("https://img/thumb.png");
        assertThat(cards.getFirst().imageUrl()).isEqualTo("https://img/full.png");
    }

    @Test
    void parseEmbeds_skipsEmbedsWithoutTitleOrDescription() {
        final String json = """
                [{"color":255},{"title":"Valid"}]""";
        var cards = DiscordInboundTranslator.parseEmbeds(json);
        assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().title()).isEqualTo("Valid");
    }

    @Test
    void parseEmbeds_invalidJson_returnsEmpty() {
        assertThat(DiscordInboundTranslator.parseEmbeds("not-json")).isEmpty();
    }

    @Test
    void parseEmbeds_null_returnsEmpty() {
        assertThat(DiscordInboundTranslator.parseEmbeds(null)).isEmpty();
    }
}
