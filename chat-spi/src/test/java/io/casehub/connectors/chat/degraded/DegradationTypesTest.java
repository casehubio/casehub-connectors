package io.casehub.connectors.chat.degraded;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.Member;
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

    @Test
    void noOpReactionsListReturnsEmptyList() {
        NoOpReactions reactions = new NoOpReactions();
        ChatMessageRef ref = new ChatMessageRef(new ChatChannelRef("ch"), "msg");
        assertThat(reactions.list(ref)).isEmpty();
    }

    @Test
    void unknownPresenceSetDoesNotThrow() {
        UnknownPresence presence = new UnknownPresence();
        assertThatCode(() -> presence.set(new MemberRef("user1"), PresenceStatus.ONLINE))
                .doesNotThrowAnyException();
    }

    @Test
    void noOpChannelManagementCreateThrows() {
        NoOpChannelManagement mgmt = new NoOpChannelManagement();
        assertThatCode(() -> mgmt.create("test", null, null, false))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void noOpChannelManagementFindReturnsEmpty() {
        NoOpChannelManagement mgmt = new NoOpChannelManagement();
        assertThat(mgmt.find("any")).isEmpty();
    }

    @Test
    void noOpMemberManagementDoesNotThrow() {
        NoOpMemberManagement mgmt = new NoOpMemberManagement();
        ChatChannelRef ch = new ChatChannelRef("ch");
        assertThatCode(() -> mgmt.add(ch, new Member(new MemberRef("u1"), "User")))
                .doesNotThrowAnyException();
        assertThatCode(() -> mgmt.remove(ch, new MemberRef("u1")))
                .doesNotThrowAnyException();
    }

    @Test
    void emptyMessageHistoryReturnsEmptyList() {
        EmptyMessageHistory history = new EmptyMessageHistory();
        assertThat(history.messages(new ChatChannelRef("ch"), Instant.EPOCH)).isEmpty();
    }
}
