package io.casehub.connectors.chat.degraded;

import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.spi.Reactions;

public class NoOpReactions implements Reactions {
    @Override public void add(final ChatMessageRef message, final String emoji) {}
    @Override public void remove(final ChatMessageRef message, final String emoji) {}
}
