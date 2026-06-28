package io.casehub.connectors.chat.degraded;

import java.time.Instant;
import java.util.List;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.spi.MessageHistory;

public class EmptyMessageHistory implements MessageHistory {
    @Override
    public List<ReceivedMessage> messages(final ChatChannelRef channel, final Instant since) {
        return List.of();
    }
}
