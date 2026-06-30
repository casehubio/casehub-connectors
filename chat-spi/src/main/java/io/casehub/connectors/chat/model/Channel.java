package io.casehub.connectors.chat.model;

public record Channel(ChatChannelRef ref, String name, String topic,
                      String description, boolean isPrivate, Integer memberCount) {

    public Channel(final ChatChannelRef ref, final String name, final String topic,
                   final String description, final boolean isPrivate) {
        this(ref, name, topic, description, isPrivate, null);
    }
}
