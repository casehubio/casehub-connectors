package io.casehub.connectors.chat.spi;

import java.util.List;

import io.casehub.connectors.chat.model.ChatMessageRef;

public interface Reactions {
    void add(final ChatMessageRef message, final String emoji);
    void remove(final ChatMessageRef message, final String emoji);
    List<String> list(final ChatMessageRef message);
}
