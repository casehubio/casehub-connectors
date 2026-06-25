package io.casehub.connectors.chat.model;

import java.time.Instant;
import java.util.Objects;

public record SendResult(boolean ok, ChatMessageRef messageRef, Instant timestamp, String error) {

    public static SendResult success(final ChatMessageRef ref, final Instant ts) {
        Objects.requireNonNull(ref, "messageRef");
        return new SendResult(true, ref, ts, null);
    }

    public static SendResult failure(final String error) {
        return new SendResult(false, null, null, error);
    }
}
