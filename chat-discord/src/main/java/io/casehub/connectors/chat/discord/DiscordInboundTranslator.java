package io.casehub.connectors.chat.discord;

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
public class DiscordInboundTranslator implements InboundTranslator {

    @Override
    public String connectorType() {
        return InboundConnectorTypes.DISCORD;
    }

    @Override
    public ReceivedMessage translate(final InboundMessage msg) {
        final var channel = new ChatChannelRef(msg.externalChannelRef());
        final var messageRef = new ChatMessageRef(channel,
                msg.metadata().get("discord-message-id"));
        final String refId = msg.metadata().get("discord-reference-id");
        final ChatMessageRef parentRef = refId != null
                ? new ChatMessageRef(channel, refId) : null;
        return new ReceivedMessage(
                InboundConnectorTypes.DISCORD,
                channel,
                messageRef,
                parentRef,
                new MemberRef(msg.externalSenderId()),
                new ChatContent(msg.content()),
                msg.receivedAt());
    }
}
