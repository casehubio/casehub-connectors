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
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.ChannelManagement;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.MemberManagement;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.MessageHistory;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;

class RefChatPlatformContractTest {

    private RefChatPlatform platform;
    private Channel channel;

    @BeforeEach
    void setUp() {
        platform = new RefChatPlatform(new InMemoryChatBackend());
        channel = platform.channelManagement().create("general", "General chat", "The general channel", false);
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
        assertThat(platform.supports(ChannelManagement.class)).isTrue();
        assertThat(platform.supports(MemberManagement.class)).isTrue();
        assertThat(platform.supports(MessageHistory.class)).isTrue();
    }

    @Test
    void sendToChannelReturnsSuccessWithMessageRef() {
        SendResult result = platform.messaging().send(channel.ref(), new ChatContent("hello"));
        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef()).isNotNull();
        assertThat(result.messageRef().channel()).isEqualTo(channel.ref());
        assertThat(result.messageRef().messageId()).isNotBlank();
        assertThat(result.timestamp()).isNotNull();
    }

    @Test
    void replyToMessageReturnsSuccessWithParentChannel() {
        SendResult original = platform.messaging().send(channel.ref(), new ChatContent("original"));
        SendResult reply = platform.threading().reply(
                original.messageRef(), new ChatContent("reply"));

        assertThat(reply.ok()).isTrue();
        assertThat(reply.messageRef().channel()).isEqualTo(channel.ref());
        assertThat(reply.messageRef().messageId())
                .isNotEqualTo(original.messageRef().messageId());
    }

    @Test
    void replyToReplyCreatesChain() {
        SendResult msg1 = platform.messaging().send(channel.ref(), new ChatContent("msg1"));
        SendResult msg2 = platform.threading().reply(msg1.messageRef(), new ChatContent("msg2"));
        SendResult msg3 = platform.threading().reply(msg2.messageRef(), new ChatContent("msg3"));

        assertThat(msg3.ok()).isTrue();
        assertThat(msg3.messageRef().channel()).isEqualTo(channel.ref());
    }

    @Test
    void listChannelsReturnsCreatedChannels() {
        platform.channelManagement().create("random", "Random chat", null, false);
        assertThat(platform.discovery().listChannels()).hasSize(2);
    }

    @Test
    void addAndRemoveReaction() {
        SendResult msg = platform.messaging().send(channel.ref(), new ChatContent("react to me"));
        platform.reactions().add(msg.messageRef(), "thumbsup");
        assertThat(platform.reactions().list(msg.messageRef()))
                .containsExactly("thumbsup");

        platform.reactions().remove(msg.messageRef(), "thumbsup");
        assertThat(platform.reactions().list(msg.messageRef())).isEmpty();
    }

    @Test
    void listReactionsReturnsMultiple() {
        SendResult msg = platform.messaging().send(channel.ref(), new ChatContent("react to me"));
        platform.reactions().add(msg.messageRef(), "thumbsup");
        platform.reactions().add(msg.messageRef(), "heart");

        assertThat(platform.reactions().list(msg.messageRef()))
                .containsExactly("thumbsup", "heart");
    }

    @Test
    void presenceSetAndQuery() {
        MemberRef member = new MemberRef("user1");
        assertThat(platform.presence().of(member)).isEqualTo(PresenceStatus.UNKNOWN);

        platform.presence().set(member, PresenceStatus.ONLINE);
        assertThat(platform.presence().of(member)).isEqualTo(PresenceStatus.ONLINE);

        platform.presence().set(member, PresenceStatus.DND);
        assertThat(platform.presence().of(member)).isEqualTo(PresenceStatus.DND);
    }

    @Test
    void channelManagementCreateAndFind() {
        Channel created = platform.channelManagement().create("incidents", "Incidents", "Incident tracking", true);

        assertThat(created.name()).isEqualTo("incidents");
        assertThat(created.topic()).isEqualTo("Incidents");
        assertThat(created.description()).isEqualTo("Incident tracking");
        assertThat(created.isPrivate()).isTrue();
        assertThat(created.ref().id()).isNotBlank();

        assertThat(platform.channelManagement().find(created.ref().id()))
                .isPresent()
                .get()
                .isEqualTo(created);
    }

    @Test
    void channelManagementFindReturnsEmptyForUnknown() {
        assertThat(platform.channelManagement().find("nonexistent")).isEmpty();
    }

    @Test
    void memberManagementAddAndRemove() {
        Member member = new Member(new MemberRef("user1"), "User One");
        platform.memberManagement().add(channel.ref(), member);

        assertThat(platform.members().list(channel.ref()))
                .hasSize(1)
                .first()
                .satisfies(m -> {
                    assertThat(m.ref().id()).isEqualTo("user1");
                    assertThat(m.displayName()).isEqualTo("User One");
                });

        platform.memberManagement().remove(channel.ref(), new MemberRef("user1"));
        assertThat(platform.members().list(channel.ref())).isEmpty();
    }

    @Test
    void listMembersReturnsEmptyForUnknownChannel() {
        assertThat(platform.members().list(new ChatChannelRef("nonexistent"))).isEmpty();
    }

    @Test
    void messageHistoryReturnsMessagesInOrder() {
        platform.messaging().send(channel.ref(), new ChatContent("first"));
        platform.messaging().send(channel.ref(), new ChatContent("second"));

        var messages = platform.messageHistory().messages(channel.ref(), Instant.EPOCH);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content().text()).isEqualTo("first");
        assertThat(messages.get(1).content().text()).isEqualTo("second");
    }

    @Test
    void messageHistoryFiltersBySince() {
        platform.messaging().send(channel.ref(), new ChatContent("old"));
        Instant midpoint = Instant.now();
        platform.messaging().send(channel.ref(), new ChatContent("new"));

        var recent = platform.messageHistory().messages(channel.ref(), midpoint);

        assertThat(recent)
                .hasSizeGreaterThanOrEqualTo(1)
                .allSatisfy(m -> assertThat(m.receivedAt()).isAfterOrEqualTo(midpoint));
    }

    @Test
    void messageHistoryReturnsEmptyForUnknownChannel() {
        assertThat(platform.messageHistory().messages(new ChatChannelRef("nonexistent"), Instant.EPOCH))
                .isEmpty();
    }

    @Test
    void messageHistoryIncludesThreadedReplies() {
        SendResult parent = platform.messaging().send(channel.ref(), new ChatContent("parent"));
        platform.threading().reply(parent.messageRef(), new ChatContent("reply"));

        var messages = platform.messageHistory().messages(channel.ref(), Instant.EPOCH);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).parentRef()).isEqualTo(parent.messageRef());
    }

    @Test
    void messageSenderIsRecordedAsPlatformId() {
        platform.messaging().send(channel.ref(), new ChatContent("hello"));

        var messages = platform.messageHistory().messages(channel.ref(), Instant.EPOCH);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).sender().id()).isEqualTo("ref");
    }
}
