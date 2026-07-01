package io.casehub.connectors.chat.slack;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.Attachment;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ReceivedMessage;

class SlackInboundTranslatorTest {

    private SlackInboundTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new SlackInboundTranslator();
    }

    @Test
    void connectorType_returnsSlack() {
        assertThat(translator.connectorType()).isEqualTo("slack");
    }

    @Test
    void translate_basicMessage() {
        Instant now = Instant.now();
        InboundMessage inbound = new InboundMessage(
                "slack-inbound", "slack", "U123", "C456",
                "Hello world", List.of(), now,
                Map.of("slack-ts", "1234567890.123456", "workspace-id", "T1"),
                null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.platformId()).isEqualTo("slack");
        assertThat(received.channel().id()).isEqualTo("C456");
        assertThat(received.messageRef().messageId()).isEqualTo("1234567890.123456");
        assertThat(received.parentRef()).isNull();
        assertThat(received.sender().id()).isEqualTo("U123");
        assertThat(received.content().text()).isEqualTo("Hello world");
    }

    @Test
    void translate_threadedMessage() {
        InboundMessage inbound = new InboundMessage(
                "slack-inbound", "slack", "U123", "C456",
                "Reply", List.of(), Instant.now(),
                Map.of("slack-ts", "1234567891.654321",
                       "slack-thread-ts", "1234567890.123456"),
                null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.parentRef()).isNotNull();
        assertThat(received.parentRef().messageId()).isEqualTo("1234567890.123456");
    }

    @Test
    void translate_forwardsAttachments() {
        Attachment att = new Attachment("doc.pdf", "application/pdf", new byte[]{1, 2});
        InboundMessage inbound = new InboundMessage(
                "slack-inbound", "slack", "U123", "C456",
                "See attached", List.of(att), Instant.now(),
                Map.of("slack-ts", "ts1"), null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.content().attachments()).hasSize(1);
        assertThat(received.content().attachments().get(0).filename()).isEqualTo("doc.pdf");
    }

    @Test
    void translate_blocksParsedAsRichCards() {
        final String blocks = """
                [{"type":"header","text":{"type":"plain_text","text":"Deploy Report"}},\
                {"type":"section","text":{"type":"mrkdwn","text":"All services healthy"}}]""";
        InboundMessage inbound = new InboundMessage(
                "slack-inbound", "slack", "U123", "C456",
                "Deploy Report", List.of(), Instant.now(),
                Map.of("slack-ts", "ts1", "slack-blocks", blocks), null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.content().cards()).hasSize(2);
        assertThat(received.content().cards().get(0).title()).isEqualTo("Deploy Report");
        assertThat(received.content().cards().get(1).description()).isEqualTo("All services healthy");
    }

    @Test
    void translate_noBlocks_emptyCards() {
        InboundMessage inbound = new InboundMessage(
                "slack-inbound", "slack", "U123", "C456",
                "plain", List.of(), Instant.now(),
                Map.of("slack-ts", "ts1"), null);

        ReceivedMessage received = translator.translate(inbound);

        assertThat(received.content().cards()).isEmpty();
    }

    @Test
    void parseBlocks_sectionWithFields() {
        final String json = """
                [{"type":"section","text":{"type":"mrkdwn","text":"Status"},\
                "fields":[{"type":"mrkdwn","text":"*Env:* prod"},{"type":"mrkdwn","text":"*Region:* us-east"}]}]""";
        var cards = SlackInboundTranslator.parseBlocks(json);
        assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().description()).isEqualTo("Status");
        assertThat(cards.getFirst().fields()).hasSize(2);
        assertThat(cards.getFirst().fields().get(0).value()).isEqualTo("*Env:* prod");
    }

    @Test
    void parseBlocks_skipsDividers() {
        final String json = """
                [{"type":"divider"},{"type":"section","text":{"type":"mrkdwn","text":"Content"}}]""";
        var cards = SlackInboundTranslator.parseBlocks(json);
        assertThat(cards).hasSize(1);
        assertThat(cards.getFirst().description()).isEqualTo("Content");
    }

    @Test
    void parseBlocks_invalidJson_returnsEmpty() {
        assertThat(SlackInboundTranslator.parseBlocks("not-json")).isEmpty();
    }

    @Test
    void parseBlocks_null_returnsEmpty() {
        assertThat(SlackInboundTranslator.parseBlocks(null)).isEmpty();
    }
}
