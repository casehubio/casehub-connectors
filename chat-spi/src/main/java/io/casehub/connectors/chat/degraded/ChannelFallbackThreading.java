package io.casehub.connectors.chat.degraded;

import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Threading;

public class ChannelFallbackThreading implements Threading {

    private final Messaging messaging;

    public ChannelFallbackThreading(final Messaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public SendResult reply(final ChatMessageRef parent, final ChatContent content) {
        return messaging.send(parent.channel(), content);
    }
}
