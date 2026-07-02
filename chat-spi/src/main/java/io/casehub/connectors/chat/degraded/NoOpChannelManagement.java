package io.casehub.connectors.chat.degraded;

import java.util.Optional;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.spi.ChannelManagement;

public class NoOpChannelManagement implements ChannelManagement {
    @Override
    public Channel create(final String name, final String topic, final String description, final boolean isPrivate) {
        throw new UnsupportedOperationException("Channel creation not supported by this platform");
    }

    @Override
    public void delete(final String channelId) {
        throw new UnsupportedOperationException("Channel deletion not supported by this platform");
    }

    @Override
    public Optional<Channel> find(final String channelId) {
        return Optional.empty();
    }
}
