package io.casehub.connectors.chat.model;

import java.time.Instant;
import java.util.Objects;

public record ReceivedMessage(
        String platformId,
        ChatChannelRef channel,
        ChatMessageRef messageRef,
        ChatMessageRef parentRef,
        MemberRef sender,
        ChatContent content,
        Instant receivedAt) {

    public ReceivedMessage {
        Objects.requireNonNull(platformId, "platformId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(messageRef, "messageRef");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
