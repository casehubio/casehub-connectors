package io.casehub.connectors.chat.ref;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.spi.InboundTranslator;

@ApplicationScoped
public class RefInboundTranslator implements InboundTranslator {

    static final String CONNECTOR_TYPE = "ref";

    @Override
    public String connectorType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public ReceivedMessage translate(final InboundMessage msg) {
        ChatChannelRef channel = new ChatChannelRef(msg.externalChannelRef());
        String messageId = msg.metadata().getOrDefault("message-id",
                java.util.UUID.randomUUID().toString());
        String parentId = msg.metadata().get("parent-id");
        ChatMessageRef messageRef = new ChatMessageRef(channel, messageId);
        ChatMessageRef parentRef = parentId != null
                ? new ChatMessageRef(channel, parentId) : null;
        return new ReceivedMessage(CONNECTOR_TYPE, channel, messageRef, parentRef,
                new MemberRef(msg.externalSenderId()),
                new ChatContent(msg.content(), null, msg.attachments(), List.of()),
                msg.receivedAt());
    }
}
