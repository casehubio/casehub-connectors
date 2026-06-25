package io.casehub.connectors.chat.degraded;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;

class DegradationTypesTest {

    @Test
    void noOpReactionsDoNotThrow() {
        NoOpReactions reactions = new NoOpReactions();
        ChatMessageRef ref = new ChatMessageRef(new ChatChannelRef("ch"), "msg");
        assertThatCode(() -> reactions.add(ref, "thumbsup")).doesNotThrowAnyException();
        assertThatCode(() -> reactions.remove(ref, "thumbsup")).doesNotThrowAnyException();
    }

    @Test
    void unknownPresenceAlwaysReturnsUnknown() {
        UnknownPresence presence = new UnknownPresence();
        assertThat(presence.of(new MemberRef("user1"))).isEqualTo(PresenceStatus.UNKNOWN);
    }

    @Test
    void emptyMembersReturnsEmptyList() {
        EmptyMembers members = new EmptyMembers();
        assertThat(members.list(new ChatChannelRef("ch"))).isEmpty();
    }

    @Test
    void emptyDiscoveryReturnsEmptyList() {
        EmptyDiscovery discovery = new EmptyDiscovery();
        assertThat(discovery.listChannels()).isEmpty();
    }
}
