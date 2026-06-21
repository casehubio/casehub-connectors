package io.casehub.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class InboundMessageTest {

    @Test
    void canonicalConstructor_allFieldsSet() {
        final List<Attachment> atts = List.of(
                new Attachment("f.pdf", "application/pdf", new byte[]{1}));
        final Instant now = Instant.now();
        final InboundMessage msg = new InboundMessage(
                "email-inbound", "email", "sender@example.com", "inbox@example.com",
                "body", atts, now, Map.of("k", "v"), "tenant-1");

        assertThat(msg.connectorId()).isEqualTo("email-inbound");
        assertThat(msg.connectorType()).isEqualTo("email");
        assertThat(msg.externalSenderId()).isEqualTo("sender@example.com");
        assertThat(msg.externalChannelRef()).isEqualTo("inbox@example.com");
        assertThat(msg.content()).isEqualTo("body");
        assertThat(msg.attachments()).hasSize(1);
        assertThat(msg.receivedAt()).isEqualTo(now);
        assertThat(msg.metadata()).containsEntry("k", "v");
        assertThat(msg.tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void nullTenancyId_isAllowed() {
        final InboundMessage msg = new InboundMessage(
                "slack-inbound", "slack", "U123", "C456",
                "hello", List.of(), Instant.now(), Map.of(), null);
        assertThat(msg.tenancyId()).isNull();
    }

    @Test
    void nullConnectorType_throwsNPE() {
        assertThatNullPointerException().isThrownBy(() ->
                new InboundMessage("slack-inbound", null, "U123", "C456",
                        "hello", List.of(), Instant.now(), Map.of(), null))
                .withMessageContaining("connectorType");
    }

    @Test
    void attachments_defensivelyCopied() {
        final List<Attachment> mutable = new ArrayList<>();
        mutable.add(new Attachment("f.pdf", "application/pdf", new byte[]{1}));
        final InboundMessage msg = new InboundMessage(
                "email-inbound", "email", "s", "c",
                "body", mutable, Instant.now(), Map.of(), null);

        mutable.clear();
        assertThat(msg.attachments()).hasSize(1);
    }
}
