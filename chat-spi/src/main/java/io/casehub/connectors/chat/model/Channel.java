package io.casehub.connectors.chat.model;

public record Channel(ChatChannelRef ref, String name, String topic, String description, boolean isPrivate) {}
