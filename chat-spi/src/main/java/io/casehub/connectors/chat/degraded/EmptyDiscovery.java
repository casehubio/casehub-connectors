package io.casehub.connectors.chat.degraded;

import java.util.List;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.spi.Discovery;

public class EmptyDiscovery implements Discovery {
    @Override
    public List<Channel> listChannels() {
        return List.of();
    }
}
