package io.casehub.connectors.chat.degraded;

import java.util.List;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.spi.Members;

public class EmptyMembers implements Members {
    @Override
    public List<Member> list(final ChatChannelRef channel) {
        return List.of();
    }
}
