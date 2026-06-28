package io.casehub.connectors.chat.spi;

import java.util.Optional;

import io.casehub.connectors.chat.model.Channel;

public interface ChannelManagement {
    Channel create(final String name, final String topic, final String description, final boolean isPrivate);
    Optional<Channel> find(final String channelId);
}
