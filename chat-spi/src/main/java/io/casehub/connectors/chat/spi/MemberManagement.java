package io.casehub.connectors.chat.spi;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;

public interface MemberManagement {
    void add(final ChatChannelRef channel, final Member member);
    void remove(final ChatChannelRef channel, final MemberRef member);
}
