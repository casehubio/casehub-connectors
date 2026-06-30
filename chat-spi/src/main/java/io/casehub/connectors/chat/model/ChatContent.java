package io.casehub.connectors.chat.model;

import java.util.List;
import java.util.Objects;

import io.casehub.connectors.Attachment;

public record ChatContent(
        String text,
        String markdown,
        List<Attachment> attachments,
        List<RichCard> cards) {

    public ChatContent {
        Objects.requireNonNull(text, "text");
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        cards = cards == null ? List.of() : List.copyOf(cards);
    }

    public ChatContent(final String text) {
        this(text, null, List.of(), List.of());
    }
}
