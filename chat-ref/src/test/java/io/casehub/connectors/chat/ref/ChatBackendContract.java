package io.casehub.connectors.chat.ref;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.Member;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.ReceivedMessage;

public abstract class ChatBackendContract {

    protected abstract ChatBackend createBackend();

    private ChatBackend backend;
    private Channel channel;

    @BeforeEach
    void setUp() {
        backend = createBackend();
        channel = backend.createChannel("general", "General chat", "The general channel", false);
    }

    @Test
    void createChannelReturnsChannelWithGeneratedId() {
        assertThat(channel.ref().id()).isNotBlank();
        assertThat(channel.name()).isEqualTo("general");
        assertThat(channel.topic()).isEqualTo("General chat");
        assertThat(channel.description()).isEqualTo("The general channel");
        assertThat(channel.isPrivate()).isFalse();
    }

    @Test
    void findChannelReturnsCreatedChannel() {
        assertThat(backend.findChannel(channel.ref().id()))
                .isPresent()
                .get()
                .isEqualTo(channel);
    }

    @Test
    void findChannelReturnsEmptyForUnknown() {
        assertThat(backend.findChannel("nonexistent")).isEmpty();
    }

    @Test
    void listChannelsReturnsAllCreated() {
        backend.createChannel("random", "Random", null, false);
        assertThat(backend.listChannels()).hasSize(2);
    }

    @Test
    void storeMessageReturnsReceivedMessageWithFields() {
        final ReceivedMessage msg = backend.storeMessage(
                "ref", channel.ref(), new ChatContent("hello"),
                new MemberRef("agent-1"), null);

        assertThat(msg.platformId()).isEqualTo("ref");
        assertThat(msg.channel()).isEqualTo(channel.ref());
        assertThat(msg.content().text()).isEqualTo("hello");
        assertThat(msg.sender().id()).isEqualTo("agent-1");
        assertThat(msg.parentRef()).isNull();
        assertThat(msg.messageRef()).isNotNull();
        assertThat(msg.messageRef().messageId()).isNotBlank();
        assertThat(msg.receivedAt()).isNotNull();
    }

    @Test
    void storeMessageWithParentSetsParentRef() {
        final ReceivedMessage parent = backend.storeMessage(
                "ref", channel.ref(), new ChatContent("parent"),
                new MemberRef("user1"), null);
        final ReceivedMessage reply = backend.storeMessage(
                "ref", channel.ref(), new ChatContent("reply"),
                new MemberRef("user2"), parent.messageRef());

        assertThat(reply.parentRef()).isEqualTo(parent.messageRef());
    }

    @Test
    void messagesReturnsStoredInOrder() {
        backend.storeMessage("ref", channel.ref(), new ChatContent("first"),
                new MemberRef("u1"), null);
        backend.storeMessage("ref", channel.ref(), new ChatContent("second"),
                new MemberRef("u2"), null);

        final var messages = backend.messages(channel.ref(), Instant.EPOCH);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content().text()).isEqualTo("first");
        assertThat(messages.get(1).content().text()).isEqualTo("second");
    }

    @Test
    void messagesFiltersBySince() {
        backend.storeMessage("ref", channel.ref(), new ChatContent("old"),
                new MemberRef("u1"), null);
        final Instant midpoint = Instant.now();
        backend.storeMessage("ref", channel.ref(), new ChatContent("new"),
                new MemberRef("u2"), null);

        final var recent = backend.messages(channel.ref(), midpoint);
        assertThat(recent)
                .hasSizeGreaterThanOrEqualTo(1)
                .allSatisfy(m -> assertThat(m.receivedAt()).isAfterOrEqualTo(midpoint));
    }

    @Test
    void messagesReturnsEmptyForUnknownChannel() {
        assertThat(backend.messages(new ChatChannelRef("unknown"), Instant.EPOCH)).isEmpty();
    }

    @Test
    void addAndRemoveReaction() {
        final ReceivedMessage msg = backend.storeMessage(
                "ref", channel.ref(), new ChatContent("react"),
                new MemberRef("u1"), null);

        backend.addReaction(msg.messageRef(), "thumbsup");
        assertThat(backend.reactions(msg.messageRef())).containsExactly("thumbsup");

        backend.removeReaction(msg.messageRef(), "thumbsup");
        assertThat(backend.reactions(msg.messageRef())).isEmpty();
    }

    @Test
    void reactionsReturnsMultiple() {
        final ReceivedMessage msg = backend.storeMessage(
                "ref", channel.ref(), new ChatContent("react"),
                new MemberRef("u1"), null);

        backend.addReaction(msg.messageRef(), "thumbsup");
        backend.addReaction(msg.messageRef(), "heart");
        assertThat(backend.reactions(msg.messageRef())).containsExactly("thumbsup", "heart");
    }

    @Test
    void reactionsReturnsEmptyForUnknownMessage() {
        assertThat(backend.reactions(new io.casehub.connectors.chat.model.ChatMessageRef(
                channel.ref(), "unknown"))).isEmpty();
    }

    @Test
    void setAndQueryPresence() {
        final MemberRef member = new MemberRef("user1");
        assertThat(backend.presence(member)).isEqualTo(PresenceStatus.UNKNOWN);

        backend.setPresence(member, PresenceStatus.ONLINE);
        assertThat(backend.presence(member)).isEqualTo(PresenceStatus.ONLINE);

        backend.setPresence(member, PresenceStatus.DND);
        assertThat(backend.presence(member)).isEqualTo(PresenceStatus.DND);
    }

    @Test
    void addAndRemoveMember() {
        final Member member = new Member(new MemberRef("user1"), "User One");
        backend.addMember(channel.ref(), member);

        assertThat(backend.members(channel.ref()))
                .hasSize(1)
                .first()
                .satisfies(m -> {
                    assertThat(m.ref().id()).isEqualTo("user1");
                    assertThat(m.displayName()).isEqualTo("User One");
                });

        backend.removeMember(channel.ref(), new MemberRef("user1"));
        assertThat(backend.members(channel.ref())).isEmpty();
    }

    @Test
    void membersReturnsEmptyForUnknownChannel() {
        assertThat(backend.members(new ChatChannelRef("unknown"))).isEmpty();
    }
}
