package io.casehub.connectors.chat.irc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ReceivedMessage;

class IrcInboundTranslatorTest {

    private final IrcInboundTranslator translator = new IrcInboundTranslator();

    @Test
    void connectorTypeIsIrc() {
        assertThat(translator.connectorType()).isEqualTo(InboundConnectorTypes.IRC);
    }

    @Test
    void translateMapsAllFields() {
        Instant now = Instant.now();
        InboundMessage msg = new InboundMessage(
                InboundConnectorIds.IRC,
                InboundConnectorTypes.IRC,
                "alice",
                "#general",
                "hello world",
                List.of(),
                now,
                Map.of("nick-prefix", "alice!user@host"),
                null);

        ReceivedMessage result = translator.translate(msg);

        assertThat(result.platformId()).isEqualTo("irc");
        assertThat(result.channel().id()).isEqualTo("#general");
        assertThat(result.messageRef().channel().id()).isEqualTo("#general");
        assertThat(result.messageRef().messageId()).isNotBlank();
        assertThat(result.parentRef()).isNull();
        assertThat(result.sender().id()).isEqualTo("alice");
        assertThat(result.content().text()).isEqualTo("hello world");
        assertThat(result.content().attachments()).isEmpty();
        assertThat(result.receivedAt()).isEqualTo(now);
    }

    @Test
    void syntheticMessageIdIsUniquePerCall() {
        InboundMessage msg = new InboundMessage(
                InboundConnectorIds.IRC, InboundConnectorTypes.IRC,
                "alice", "#general", "text", List.of(),
                Instant.now(), Map.of(), null);

        String id1 = translator.translate(msg).messageRef().messageId();
        String id2 = translator.translate(msg).messageRef().messageId();
        assertThat(id1).isNotEqualTo(id2);
    }
}
