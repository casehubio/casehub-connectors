package io.casehub.connectors.chat.ref;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.model.Channel;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;

class RefChatPlatformContractTest {

    private RefChatPlatform platform;
    private ChatChannelRef channel;

    @BeforeEach
    void setUp() {
        platform = new RefChatPlatform();
        channel = new ChatChannelRef("general");
        platform.addChannel(new Channel(channel, "general", "General chat", false));
    }

    @Test
    void idIsRef() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supportsAllCapabilities() {
        assertThat(platform.supports(Messaging.class)).isTrue();
        assertThat(platform.supports(Threading.class)).isTrue();
        assertThat(platform.supports(Discovery.class)).isTrue();
        assertThat(platform.supports(Reactions.class)).isTrue();
        assertThat(platform.supports(Presence.class)).isTrue();
        assertThat(platform.supports(Members.class)).isTrue();
    }

    @Test
    void sendToChannelReturnsSuccessWithMessageRef() {
        SendResult result = platform.messaging().send(channel, new ChatContent("hello"));
        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef()).isNotNull();
        assertThat(result.messageRef().channel()).isEqualTo(channel);
        assertThat(result.messageRef().messageId()).isNotBlank();
        assertThat(result.timestamp()).isNotNull();
    }

    @Test
    void replyToMessageReturnsSuccessWithParentChannel() {
        SendResult original = platform.messaging().send(channel, new ChatContent("original"));
        SendResult reply = platform.threading().reply(
                original.messageRef(), new ChatContent("reply"));

        assertThat(reply.ok()).isTrue();
        assertThat(reply.messageRef().channel()).isEqualTo(channel);
        assertThat(reply.messageRef().messageId())
                .isNotEqualTo(original.messageRef().messageId());
    }

    @Test
    void replyToReplyCreatesChain() {
        SendResult msg1 = platform.messaging().send(channel, new ChatContent("msg1"));
        SendResult msg2 = platform.threading().reply(msg1.messageRef(), new ChatContent("msg2"));
        SendResult msg3 = platform.threading().reply(msg2.messageRef(), new ChatContent("msg3"));

        assertThat(msg3.ok()).isTrue();
        assertThat(msg3.messageRef().channel()).isEqualTo(channel);
    }

    @Test
    void listChannelsReturnsAddedChannels() {
        ChatChannelRef ch2 = new ChatChannelRef("random");
        platform.addChannel(new Channel(ch2, "random", "Random chat", false));

        assertThat(platform.discovery().listChannels()).hasSize(2);
    }

    @Test
    void addAndRemoveReaction() {
        SendResult msg = platform.messaging().send(channel, new ChatContent("react to me"));
        platform.reactions().add(msg.messageRef(), "thumbsup");
        assertThat(platform.getReactions(msg.messageRef().messageId()))
                .containsExactly("thumbsup");

        platform.reactions().remove(msg.messageRef(), "thumbsup");
        assertThat(platform.getReactions(msg.messageRef().messageId())).isEmpty();
    }

    @Test
    void presenceReflectsSetState() {
        MemberRef member = new MemberRef("user1");
        assertThat(platform.presence().of(member)).isEqualTo(PresenceStatus.UNKNOWN);

        platform.setPresence("user1", PresenceStatus.ONLINE);
        assertThat(platform.presence().of(member)).isEqualTo(PresenceStatus.ONLINE);
    }

    @Test
    void listMembersReturnsAddedMembers() {
        MemberRef m1 = new MemberRef("user1");
        MemberRef m2 = new MemberRef("user2");
        platform.addMember("general", m1);
        platform.addMember("general", m2);

        assertThat(platform.members().list(channel)).hasSize(2);
    }

    @Test
    void listMembersReturnsEmptyForUnknownChannel() {
        assertThat(platform.members().list(new ChatChannelRef("nonexistent"))).isEmpty();
    }
}
