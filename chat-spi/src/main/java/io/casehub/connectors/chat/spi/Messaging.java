package io.casehub.connectors.chat.spi;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.SendResult;

public interface Messaging {
    SendResult send(final ChatChannelRef channel, final ChatContent content);
}
