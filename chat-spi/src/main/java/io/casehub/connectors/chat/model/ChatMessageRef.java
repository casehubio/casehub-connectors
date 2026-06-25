package io.casehub.connectors.chat.model;

public record ChatMessageRef(ChatChannelRef channel, String messageId) {}
