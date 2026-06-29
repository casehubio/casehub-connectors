package io.casehub.connectors.chat.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.casehub.connectors.chat.model.*;
import io.casehub.connectors.chat.spi.*;
import io.casehub.connectors.discord.DiscordClient;
import io.casehub.connectors.discord.DiscordGatewayPresenceCache;

class DiscordChatPlatformTest {

    private static WireMockServer wireMock;
    private DiscordClient client;
    private DiscordGatewayPresenceCache presenceCache;
    private DiscordChatPlatform platform;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        wireMock.resetAll();
        client = new DiscordClient();

        // Use reflection to set package-private fields
        var apiBaseUrlField = DiscordClient.class.getDeclaredField("apiBaseUrl");
        apiBaseUrlField.setAccessible(true);
        apiBaseUrlField.set(client, "http://localhost:" + wireMock.port());

        var guildIdField = DiscordClient.class.getDeclaredField("guildId");
        guildIdField.setAccessible(true);
        guildIdField.set(client, "test-guild-123");

        presenceCache = new DiscordGatewayPresenceCache();
        platform = new DiscordChatPlatform(client, presenceCache, "test-token", "test-guild-123");
    }

    @Test
    void idIsDiscord() {
        assertThat(platform.id()).isEqualTo("discord");
    }

    @Test
    void supportsEightNativeCapabilities() {
        assertThat(platform.supports(Messaging.class)).isTrue();
        assertThat(platform.supports(Threading.class)).isTrue();
        assertThat(platform.supports(Discovery.class)).isTrue();
        assertThat(platform.supports(Reactions.class)).isTrue();
        assertThat(platform.supports(Presence.class)).isTrue();
        assertThat(platform.supports(Members.class)).isTrue();
        assertThat(platform.supports(ChannelManagement.class)).isTrue();
        assertThat(platform.supports(MessageHistory.class)).isTrue();
        assertThat(platform.supports(MemberManagement.class)).isFalse();
    }

    @Test
    void messaging_send() {
        wireMock.stubFor(post(urlEqualTo("/channels/chan-123/messages"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "msg-456", "channel_id": "chan-123"}
                                """)));

        ChatChannelRef channel = new ChatChannelRef("chan-123");
        ChatContent content = new ChatContent("Hello Discord", null, List.of());

        SendResult result = platform.messaging().send(channel, content);

        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef()).isNotNull();
        assertThat(result.messageRef().messageId()).isEqualTo("msg-456");
        assertThat(result.messageRef().channel().id()).isEqualTo("chan-123");
    }

    @Test
    void messaging_contentExceeds2000CharsReturnsFailure() {
        ChatChannelRef channel = new ChatChannelRef("chan-123");
        String longContent = "x".repeat(2001);
        ChatContent content = new ChatContent(longContent, null, List.of());

        SendResult result = platform.messaging().send(channel, content);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("exceeds Discord's 2000-character limit");
    }

    @Test
    void messaging_prefersMarkdownOverText() {
        wireMock.stubFor(post(urlEqualTo("/channels/chan-123/messages"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "msg-456", "channel_id": "chan-123"}
                                """)));

        ChatChannelRef channel = new ChatChannelRef("chan-123");
        ChatContent content = new ChatContent("plain text", "**bold markdown**", List.of());

        platform.messaging().send(channel, content);

        wireMock.verify(postRequestedFor(urlEqualTo("/channels/chan-123/messages"))
                .withRequestBody(containing("**bold markdown**")));
    }

    @Test
    void threading_reply() {
        wireMock.stubFor(post(urlEqualTo("/channels/chan-123/messages"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "msg-789", "channel_id": "chan-123"}
                                """)));

        ChatChannelRef channel = new ChatChannelRef("chan-123");
        ChatMessageRef parent = new ChatMessageRef(channel, "msg-456");
        ChatContent content = new ChatContent("Reply text", null, List.of());

        SendResult result = platform.threading().reply(parent, content);

        assertThat(result.ok()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/channels/chan-123/messages"))
                .withRequestBody(containing("msg-456")));
    }

    @Test
    void discovery_listChannels() {
        wireMock.stubFor(get(urlEqualTo("/guilds/test-guild-123/channels"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id": "ch-1", "name": "general", "topic": "Welcome", "type": 0},
                                  {"id": "ch-2", "name": "announcements", "topic": "News", "type": 5}
                                ]
                                """)));

        List<Channel> channels = platform.discovery().listChannels();

        assertThat(channels).hasSize(2);
        assertThat(channels.stream().map(c -> c.name())).contains("general", "announcements");
        assertThat(channels.stream().map(c -> c.topic())).contains("Welcome", "News");
    }

    @Test
    void discovery_excludesForumChannels() {
        wireMock.stubFor(get(urlEqualTo("/guilds/test-guild-123/channels"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {"id": "ch-1", "name": "general", "topic": "Welcome", "type": 0},
                                  {"id": "ch-forum", "name": "forum", "topic": "", "type": 15}
                                ]
                                """)));

        List<Channel> channels = platform.discovery().listChannels();

        assertThat(channels).hasSize(1);
        assertThat(channels.get(0).name()).isEqualTo("general");
    }

    @Test
    void reactions_addRemoveList() {
        wireMock.stubFor(put(urlMatching("/channels/chan-123/messages/msg-456/reactions/.*/@me"))
                .willReturn(aResponse().withStatus(204)));
        wireMock.stubFor(delete(urlMatching("/channels/chan-123/messages/msg-456/reactions/.*/@me"))
                .willReturn(aResponse().withStatus(204)));
        wireMock.stubFor(get(urlEqualTo("/channels/chan-123/messages/msg-456"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "msg-456",
                                  "channel_id": "chan-123",
                                  "content": "test",
                                  "author": {"id": "u1", "username": "user1"},
                                  "timestamp": "2026-06-29T10:00:00Z",
                                  "reactions": [
                                    {"emoji": {"name": "thumbsup"}, "count": 1}
                                  ]
                                }
                                """)));

        ChatChannelRef channel = new ChatChannelRef("chan-123");
        ChatMessageRef messageRef = new ChatMessageRef(channel, "msg-456");

        platform.reactions().add(messageRef, "thumbsup");
        platform.reactions().remove(messageRef, "thumbsup");
        List<String> emojis = platform.reactions().list(messageRef);

        assertThat(emojis).contains("thumbsup");
    }

    @Test
    void presence_ofMember() {
        presenceCache.update("user-123", "online");
        presenceCache.update("user-456", "idle");
        presenceCache.update("user-789", "dnd");
        presenceCache.update("user-000", "offline");

        assertThat(platform.presence().of(new MemberRef("user-123"))).isEqualTo(PresenceStatus.ONLINE);
        assertThat(platform.presence().of(new MemberRef("user-456"))).isEqualTo(PresenceStatus.AWAY);
        assertThat(platform.presence().of(new MemberRef("user-789"))).isEqualTo(PresenceStatus.DND);
        assertThat(platform.presence().of(new MemberRef("user-000"))).isEqualTo(PresenceStatus.OFFLINE);
        assertThat(platform.presence().of(new MemberRef("user-unknown"))).isEqualTo(PresenceStatus.UNKNOWN);
    }

    @Test
    void presence_setLogsWarning() {
        // Should not throw
        platform.presence().set(new MemberRef("user-123"), PresenceStatus.ONLINE);
    }

    @Test
    void members_list() {
        wireMock.stubFor(get(urlEqualTo("/guilds/test-guild-123/members?limit=1000"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "user": {"id": "u1", "username": "alice", "global_name": "Alice"},
                                    "nick": "AliceNick",
                                    "joined_at": "2026-01-01T00:00:00Z"
                                  },
                                  {
                                    "user": {"id": "u2", "username": "bob", "global_name": "Bob"},
                                    "nick": null,
                                    "joined_at": "2026-01-02T00:00:00Z"
                                  }
                                ]
                                """)));
        // Second page - empty to stop pagination
        wireMock.stubFor(get(urlMatching("/guilds/test-guild-123/members\\?limit=1000&after=.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        ChatChannelRef channel = new ChatChannelRef("chan-123");
        List<Member> members = platform.members().list(channel);

        assertThat(members).hasSize(2);
        assertThat(members.get(0).ref().id()).isEqualTo("u1");
        assertThat(members.get(0).displayName()).isEqualTo("AliceNick");
        assertThat(members.get(1).ref().id()).isEqualTo("u2");
        assertThat(members.get(1).displayName()).isEqualTo("Bob");
    }

    @Test
    void channelManagement_createAndFind() {
        wireMock.stubFor(post(urlEqualTo("/guilds/test-guild-123/channels"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "new-ch-123",
                                  "name": "new-channel",
                                  "topic": "A new channel",
                                  "type": 0
                                }
                                """)));
        wireMock.stubFor(get(urlEqualTo("/channels/new-ch-123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "new-ch-123",
                                  "name": "new-channel",
                                  "topic": "A new channel",
                                  "type": 0
                                }
                                """)));

        Channel created = platform.channelManagement().create("new-channel", "A new channel", null, false);
        assertThat(created.name()).isEqualTo("new-channel");
        assertThat(created.topic()).isEqualTo("A new channel");

        Optional<Channel> found = platform.channelManagement().find("new-ch-123");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("new-channel");
    }

    @Test
    void channelManagement_createPrivateChannel() {
        wireMock.stubFor(post(urlEqualTo("/guilds/test-guild-123/channels"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "priv-ch-123",
                                  "name": "private-channel",
                                  "topic": "Secret",
                                  "type": 0,
                                  "permission_overwrites": [
                                    {"id": "test-guild-123", "type": 0, "allow": "0", "deny": "1024"}
                                  ]
                                }
                                """)));

        Channel created = platform.channelManagement().create("private-channel", "Secret", null, true);

        wireMock.verify(postRequestedFor(urlEqualTo("/guilds/test-guild-123/channels"))
                .withRequestBody(containing("permission_overwrites")));
        assertThat(created.isPrivate()).isTrue();
    }

    @Test
    void channelManagement_findDerivesIsPrivate() {
        wireMock.stubFor(get(urlEqualTo("/channels/priv-ch-456"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "priv-ch-456",
                                  "name": "private-channel",
                                  "topic": "Secret",
                                  "type": 0,
                                  "permission_overwrites": [
                                    {"id": "test-guild-123", "type": 0, "allow": "0", "deny": "1024"}
                                  ]
                                }
                                """)));

        Optional<Channel> found = platform.channelManagement().find("priv-ch-456");

        assertThat(found).isPresent();
        assertThat(found.get().isPrivate()).isTrue();
    }

    @Test
    void messageHistory_messagesSince() {
        // First page
        wireMock.stubFor(get(urlMatching("/channels/chan-123/messages\\?limit=100&after=\\d+"))
                .inScenario("message-pagination")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                  {
                                    "id": "msg-1",
                                    "channel_id": "chan-123",
                                    "content": "Hello",
                                    "author": {"id": "u1", "username": "alice"},
                                    "timestamp": "2026-06-29T10:00:00Z",
                                    "type": 0
                                  },
                                  {
                                    "id": "msg-2",
                                    "channel_id": "chan-123",
                                    "content": "Reply",
                                    "author": {"id": "u2", "username": "bob"},
                                    "timestamp": "2026-06-29T10:01:00Z",
                                    "message_reference": {"message_id": "msg-1"},
                                    "type": 19
                                  }
                                ]
                                """))
                .willSetStateTo("page-1-done"));

        // Second page - empty to stop pagination
        wireMock.stubFor(get(urlMatching("/channels/chan-123/messages\\?limit=100&after=.*"))
                .inScenario("message-pagination")
                .whenScenarioStateIs("page-1-done")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        ChatChannelRef channel = new ChatChannelRef("chan-123");
        Instant since = Instant.parse("2026-06-29T09:00:00Z");

        List<ReceivedMessage> messages = platform.messageHistory().messages(channel, since);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).messageRef().messageId()).isEqualTo("msg-1");
        assertThat(messages.get(0).content().text()).isEqualTo("Hello");
        assertThat(messages.get(1).messageRef().messageId()).isEqualTo("msg-2");
        assertThat(messages.get(1).parentRef()).isNotNull();
        assertThat(messages.get(1).parentRef().messageId()).isEqualTo("msg-1");
    }
}
