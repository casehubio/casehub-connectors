package io.casehub.connectors.chat.irc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.chat.irc.test.EmbeddedIrcServer;
import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.ChatMessageRef;
import io.casehub.connectors.chat.model.MemberRef;
import io.casehub.connectors.chat.model.SendResult;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;

class IrcChatPlatformTest {

    private static EmbeddedIrcServer server;
    private IrcClient client;
    private IrcChatPlatform platform;

    @BeforeAll
    static void startServer() {
        server = new EmbeddedIrcServer(0);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    @BeforeEach
    void setUp() {
        client = new IrcClient("localhost", server.getPort(), "testbot");
        platform = new IrcChatPlatform(client);
        client.connect();
    }

    @AfterEach
    void tearDown() {
        client.disconnect();
    }

    @Test
    void idIsIrc() {
        assertThat(platform.id()).isEqualTo("irc");
    }

    @Test
    void supportsMessagingDiscoveryMembers() {
        assertThat(platform.supports(Messaging.class)).isTrue();
        assertThat(platform.supports(Discovery.class)).isTrue();
        assertThat(platform.supports(Members.class)).isTrue();
        assertThat(platform.supports(Threading.class)).isFalse();
        assertThat(platform.supports(Reactions.class)).isFalse();
        assertThat(platform.supports(Presence.class)).isFalse();
    }

    @Test
    void sendToChannelReturnsSuccess() {
        client.join("#test");
        ChatChannelRef channel = new ChatChannelRef("#test");
        ChatContent content = new ChatContent("hello", null, List.of(), List.of());

        SendResult result = platform.messaging().send(channel, content);

        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef()).isNotNull();
        assertThat(result.messageRef().messageId()).isNotBlank();
    }

    @Test
    void sendWhenDisconnectedReturnsFailure() {
        client.disconnect();
        ChatChannelRef channel = new ChatChannelRef("#test");
        ChatContent content = new ChatContent("hello", null, List.of(), List.of());

        SendResult result = platform.messaging().send(channel, content);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("not connected");
    }

    @Test
    void listChannelsReturnsJoinedChannels() {
        client.join("#channel1");
        client.join("#channel2");

        List<io.casehub.connectors.chat.model.Channel> channels = platform.discovery().listChannels();

        assertThat(channels).hasSize(2);
        assertThat(channels.stream().map(ch -> ch.ref().id()))
                .contains("#channel1", "#channel2");
        assertThat(channels).allMatch(ch -> !ch.isPrivate());
    }

    @Test
    void listMembersReturnsNicks() {
        client.join("#members-test");
        ChatChannelRef channel = new ChatChannelRef("#members-test");

        List<io.casehub.connectors.chat.model.Member> members = platform.members().list(channel);

        assertThat(members).isNotEmpty();
        assertThat(members.stream().map(m -> m.ref().id())).contains("testbot");
    }

    @Test
    void threadingDegradesToChannelSend() {
        client.join("#thread-test");
        ChatChannelRef channel = new ChatChannelRef("#thread-test");
        ChatMessageRef messageRef = new ChatMessageRef(channel, "msg-123");
        ChatContent content = new ChatContent("reply text", null, List.of(), List.of());

        SendResult result = platform.threading().reply(messageRef, content);

        assertThat(result.ok()).isTrue();
    }

    @Test
    void reactionsAreNoOp() {
        ChatChannelRef channel = new ChatChannelRef("#test");
        ChatMessageRef messageRef = new ChatMessageRef(channel, "msg-123");
        platform.reactions().add(messageRef, "emoji");
        platform.reactions().remove(messageRef, "emoji");
        // no exception = pass
    }

    @Test
    void presenceReturnsUnknown() {
        MemberRef member = new MemberRef("alice");
        assertThat(platform.presence().of(member))
                .isEqualTo(io.casehub.connectors.chat.model.PresenceStatus.UNKNOWN);
    }
}
