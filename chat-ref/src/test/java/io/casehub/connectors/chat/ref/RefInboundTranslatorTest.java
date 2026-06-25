package io.casehub.connectors.chat.ref;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.Attachment;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ReceivedMessage;

class RefInboundTranslatorTest {

    private RefInboundTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new RefInboundTranslator();
    }

    @Test
    void connectorTypeIsRef() {
        assertThat(translator.connectorType()).isEqualTo("ref");
    }

    @Test
    void translateSimpleMessage() {
        InboundMessage msg = new InboundMessage(
                "conn1", "ref", "sender1", "channel1",
                "Hello", List.of(), Instant.now(),
                Map.of("message-id", "msg1"), "tenant1");

        ReceivedMessage received = translator.translate(msg);

        assertThat(received.platformId()).isEqualTo("ref");
        assertThat(received.channel().id()).isEqualTo("channel1");
        assertThat(received.messageRef().messageId()).isEqualTo("msg1");
        assertThat(received.messageRef().channel().id()).isEqualTo("channel1");
        assertThat(received.parentRef()).isNull();
        assertThat(received.sender().id()).isEqualTo("sender1");
        assertThat(received.content().text()).isEqualTo("Hello");
    }

    @Test
    void translateThreadedMessage() {
        InboundMessage msg = new InboundMessage(
                "conn1", "ref", "sender1", "channel1",
                "Reply", List.of(), Instant.now(),
                Map.of("message-id", "msg2", "parent-id", "msg1"), "tenant1");

        ReceivedMessage received = translator.translate(msg);

        assertThat(received.parentRef()).isNotNull();
        assertThat(received.parentRef().messageId()).isEqualTo("msg1");
        assertThat(received.parentRef().channel().id()).isEqualTo("channel1");
    }

    @Test
    void translateMessageWithAttachments() {
        Attachment att = new Attachment("file.txt", "text/plain", "test content".getBytes());
        InboundMessage msg = new InboundMessage(
                "conn1", "ref", "sender1", "channel1",
                "See attached", List.of(att), Instant.now(),
                Map.of("message-id", "msg3"), "tenant1");

        ReceivedMessage received = translator.translate(msg);

        assertThat(received.content().attachments()).hasSize(1);
        assertThat(received.content().attachments().get(0).filename()).isEqualTo("file.txt");
        assertThat(received.content().attachments().get(0).contentType()).isEqualTo("text/plain");
    }

    @Test
    void translateGeneratesMessageIdWhenMissing() {
        InboundMessage msg = new InboundMessage(
                "conn1", "ref", "sender1", "channel1",
                "Hello", List.of(), Instant.now(),
                Map.of(), "tenant1");

        ReceivedMessage received = translator.translate(msg);

        assertThat(received.messageRef().messageId()).isNotBlank();
    }
}
