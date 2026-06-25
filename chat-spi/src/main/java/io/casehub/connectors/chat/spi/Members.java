package io.casehub.connectors.chat.spi;

import java.util.List;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.Member;

public interface Members {
    List<Member> list(final ChatChannelRef channel);
}
