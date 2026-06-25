package io.casehub.connectors.chat.spi;

import io.casehub.connectors.chat.model.ChatMessageRef;

public interface Reactions {
    void add(final ChatMessageRef message, final String emoji);
    void remove(final ChatMessageRef message, final String emoji);
}
