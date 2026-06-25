package io.casehub.connectors.chat.spi;

import java.util.List;

import io.casehub.connectors.chat.model.Channel;

public interface Discovery {
    List<Channel> listChannels();
}
