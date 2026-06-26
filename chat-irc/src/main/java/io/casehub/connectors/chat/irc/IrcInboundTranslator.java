package io.casehub.connectors.chat.irc;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.spi.InboundTranslator;

@ApplicationScoped
public class IrcInboundTranslator implements InboundTranslator {

    @Override
    public String connectorType() {
        return InboundConnectorTypes.IRC;
    }

    @Override
    public ReceivedMessage translate(final InboundMessage msg) {
        ChatChannelRef channel = new ChatChannelRef(msg.externalChannelRef());
        ChatMessageRef messageRef = new ChatMessageRef(channel,
                UUID.randomUUID().toString());
        return new ReceivedMessage(
                InboundConnectorTypes.IRC,
                channel,
                messageRef,
                null,
                new MemberRef(msg.externalSenderId()),
                new ChatContent(msg.content(), null, msg.attachments()),
                msg.receivedAt());
    }
}
