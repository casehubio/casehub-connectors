package io.casehub.connectors.chat.spi;

import java.time.Instant;
import java.util.List;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ReceivedMessage;

public interface MessageHistory {
    List<ReceivedMessage> messages(final ChatChannelRef channel, final Instant since);
}
