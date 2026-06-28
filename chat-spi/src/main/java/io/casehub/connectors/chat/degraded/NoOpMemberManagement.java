package io.casehub.connectors.chat.degraded;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.spi.MemberManagement;

public class NoOpMemberManagement implements MemberManagement {
    @Override public void add(final ChatChannelRef channel, final Member member) {}
    @Override public void remove(final ChatChannelRef channel, final MemberRef member) {}
}
