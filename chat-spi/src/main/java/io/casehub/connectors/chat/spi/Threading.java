package io.casehub.connectors.chat.spi;

import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.SendResult;

public interface Threading {
    SendResult reply(final ChatMessageRef parent, final ChatContent content);
}
