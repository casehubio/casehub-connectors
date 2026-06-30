package io.casehub.connectors.chat.slack;

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
import io.casehub.connectors.slack.bot.SlackBotClient;

class SlackChatPlatformTest {

    private static WireMockServer wireMock;
    private SlackBotClient client;
    private SlackChatPlatform platform;

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
        client = new SlackBotClient();

        // Use reflection to set the package-private apiBaseUrl field
        var apiBaseUrlField = SlackBotClient.class.getDeclaredField("apiBaseUrl");
        apiBaseUrlField.setAccessible(true);
        apiBaseUrlField.set(client, "http://localhost:" + wireMock.port());

        platform = new SlackChatPlatform(client, "xoxb-test-token");
        platform.init();
    }

    @Test
    void idIsSlack() {
        assertThat(platform.id()).isEqualTo("slack");
    }

    @Test
    void supportsNineNativeCapabilities() {
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
    void messaging_send() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"ok": true, "ts": "1234567890.123456"}
                                """)));

        ChatChannelRef channel = new ChatChannelRef("C123");
        ChatContent content = new ChatContent("Hello Slack", null, List.of());

        SendResult result = platform.messaging().send(channel, content);

        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef()).isNotNull();
        assertThat(result.messageRef().messageId()).isEqualTo("1234567890.123456");
        assertThat(result.messageRef().channel().id()).isEqualTo("C123");
        assertThat(result.timestamp()).isNotNull();
        // Verify ts parsed to correct instant: 1234567890 seconds, 123456 microseconds
        assertThat(result.timestamp().getEpochSecond()).isEqualTo(1234567890L);
        assertThat(result.timestamp().getNano()).isEqualTo(123456000); // 123456 micros = 123456000 nanos
    }

    @Test
    void threading_reply() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"ok": true, "ts": "1234567891.654321"}
                                """)));

        ChatChannelRef channel = new ChatChannelRef("C123");
        ChatMessageRef parent = new ChatMessageRef(channel, "1234567890.123456");
        ChatContent content = new ChatContent("Reply text", null, List.of());

        SendResult result = platform.threading().reply(parent, content);

        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef().messageId()).isEqualTo("1234567891.654321");

        // Verify thread_ts was included in the request
        wireMock.verify(postRequestedFor(urlEqualTo("/api/chat.postMessage"))
                .withRequestBody(containing("thread_ts"))
                .withRequestBody(containing("1234567890.123456")));
    }

    @Test
    void discovery_listChannels() {
        wireMock.stubFor(get(urlPathEqualTo("/api/conversations.list"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "channels": [
                                    {
                                      "id": "C1",
                                      "name": "general",
                                      "topic": {"value": "Welcome"},
                                      "purpose": {"value": "General chat"},
                                      "is_private": false
                                    },
                                    {
                                      "id": "C2",
                                      "name": "secret",
                                      "topic": {"value": "Private stuff"},
                                      "purpose": {"value": "Secret chat"},
                                      "is_private": true
                                    }
                                  ],
                                  "response_metadata": {"next_cursor": ""}
                                }
                                """)));

        List<Channel> channels = platform.discovery().listChannels();

        assertThat(channels).hasSize(2);
        assertThat(channels.get(0).ref().id()).isEqualTo("C1");
        assertThat(channels.get(0).name()).isEqualTo("general");
        assertThat(channels.get(0).topic()).isEqualTo("Welcome");
        assertThat(channels.get(0).description()).isEqualTo("General chat");
        assertThat(channels.get(0).isPrivate()).isFalse();

        assertThat(channels.get(1).ref().id()).isEqualTo("C2");
        assertThat(channels.get(1).name()).isEqualTo("secret");
        assertThat(channels.get(1).isPrivate()).isTrue();
    }

    @Test
    void reactions_addRemoveList() {
        wireMock.stubFor(post(urlEqualTo("/api/reactions.add"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"ok": true}
                                """)));
        wireMock.stubFor(post(urlEqualTo("/api/reactions.remove"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"ok": true}
                                """)));
        wireMock.stubFor(get(urlPathEqualTo("/api/reactions.get"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "message": {
                                    "reactions": [
                                      {"name": "thumbsup", "count": 1},
                                      {"name": "heart", "count": 2}
                                    ]
                                  }
                                }
                                """)));

        ChatChannelRef channel = new ChatChannelRef("C123");
        ChatMessageRef messageRef = new ChatMessageRef(channel, "1234567890.123456");

        platform.reactions().add(messageRef, "thumbsup");
        platform.reactions().remove(messageRef, "heart");
        List<String> emojis = platform.reactions().list(messageRef);

        assertThat(emojis).containsExactly("thumbsup", "heart");
    }

    @Test
    void presence_ofMember() {
        wireMock.stubFor(get(urlPathEqualTo("/api/users.getPresence"))
                .withQueryParam("user", equalTo("U111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"ok": true, "presence": "active"}
                                """)));

        wireMock.stubFor(get(urlPathEqualTo("/api/users.getPresence"))
                .withQueryParam("user", equalTo("U222"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"ok": true, "presence": "away"}
                                """)));

        assertThat(platform.presence().of(new MemberRef("U111"))).isEqualTo(PresenceStatus.ONLINE);
        assertThat(platform.presence().of(new MemberRef("U222"))).isEqualTo(PresenceStatus.AWAY);
    }

    @Test
    void presence_setLogsWarning() {
        // Should not throw — it's a no-op
        platform.presence().set(new MemberRef("U111"), PresenceStatus.ONLINE);
    }

    @Test
    void members_listWithBatchUserFetch() {
        // Stub conversations.members
        wireMock.stubFor(get(urlPathEqualTo("/api/conversations.members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "members": ["U1", "U2", "U3"],
                                  "response_metadata": {"next_cursor": ""}
                                }
                                """)));

        // Stub users.list — U3 not included to test fallback
        wireMock.stubFor(get(urlPathEqualTo("/api/users.list"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "members": [
                                    {"id": "U1", "profile": {"display_name": "Alice", "real_name": "Alice Smith"}},
                                    {"id": "U2", "profile": {"display_name": "", "real_name": "Bob Jones"}}
                                  ],
                                  "response_metadata": {"next_cursor": ""}
                                }
                                """)));

        ChatChannelRef channel = new ChatChannelRef("C123");
        List<Member> members = platform.members().list(channel);

        assertThat(members).hasSize(3);

        // U1: has display_name
        Member u1 = members.stream().filter(m -> m.ref().id().equals("U1")).findFirst().orElseThrow();
        assertThat(u1.displayName()).isEqualTo("Alice");

        // U2: empty display_name, falls back to real_name
        Member u2 = members.stream().filter(m -> m.ref().id().equals("U2")).findFirst().orElseThrow();
        assertThat(u2.displayName()).isEqualTo("Bob Jones");

        // U3: not in users.list, falls back to userId as displayName
        Member u3 = members.stream().filter(m -> m.ref().id().equals("U3")).findFirst().orElseThrow();
        assertThat(u3.displayName()).isEqualTo("U3");
    }

    @Test
    void channelManagement_createAndFind() {
        wireMock.stubFor(post(urlEqualTo("/api/conversations.create"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "channel": {
                                    "id": "C-NEW",
                                    "name": "new-channel",
                                    "topic": {"value": "A new channel"},
                                    "purpose": {"value": "Testing"},
                                    "is_private": false
                                  }
                                }
                                """)));

        wireMock.stubFor(get(urlPathEqualTo("/api/conversations.info"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "channel": {
                                    "id": "C-NEW",
                                    "name": "new-channel",
                                    "topic": {"value": "A new channel"},
                                    "purpose": {"value": "Testing"},
                                    "is_private": false
                                  }
                                }
                                """)));

        Channel created = platform.channelManagement().create("new-channel", "A new channel", "Testing", false);
        assertThat(created.name()).isEqualTo("new-channel");
        assertThat(created.topic()).isEqualTo("A new channel");
        assertThat(created.description()).isEqualTo("Testing");

        Optional<Channel> found = platform.channelManagement().find("C-NEW");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("new-channel");
    }

    @Test
    void memberManagement_addAndRemove() {
        wireMock.stubFor(post(urlEqualTo("/api/conversations.invite"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"ok": true}
                                """)));

        wireMock.stubFor(post(urlEqualTo("/api/conversations.kick"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"ok": true}
                                """)));

        ChatChannelRef channel = new ChatChannelRef("C123");
        Member member = new Member(new MemberRef("U456"), "Alice");

        // Should not throw
        platform.memberManagement().add(channel, member);
        platform.memberManagement().remove(channel, member.ref());

        wireMock.verify(postRequestedFor(urlEqualTo("/api/conversations.invite"))
                .withRequestBody(containing("C123"))
                .withRequestBody(containing("U456")));
        wireMock.verify(postRequestedFor(urlEqualTo("/api/conversations.kick"))
                .withRequestBody(containing("C123"))
                .withRequestBody(containing("U456")));
    }

    @Test
    void messageHistory_messagesSince() {
        wireMock.stubFor(get(urlPathEqualTo("/api/conversations.history"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "messages": [
                                    {"ts": "1719651600.000000", "user": "U1", "text": "Hello"},
                                    {"ts": "1719651660.123456", "user": "U2", "text": "Reply", "thread_ts": "1719651600.000000"}
                                  ]
                                }
                                """)));

        ChatChannelRef channel = new ChatChannelRef("C123");
        Instant since = Instant.parse("2024-06-29T09:00:00Z");

        List<ReceivedMessage> messages = platform.messageHistory().messages(channel, since);

        assertThat(messages).hasSize(2);

        // First message: no thread
        ReceivedMessage msg1 = messages.get(0);
        assertThat(msg1.messageRef().messageId()).isEqualTo("1719651600.000000");
        assertThat(msg1.sender().id()).isEqualTo("U1");
        assertThat(msg1.content().text()).isEqualTo("Hello");
        assertThat(msg1.parentRef()).isNull();
        // Verify timestamp precision: 1719651600 seconds, 0 micros
        assertThat(msg1.receivedAt().getEpochSecond()).isEqualTo(1719651600L);
        assertThat(msg1.receivedAt().getNano()).isEqualTo(0);

        // Second message: threaded, with microsecond precision
        ReceivedMessage msg2 = messages.get(1);
        assertThat(msg2.messageRef().messageId()).isEqualTo("1719651660.123456");
        assertThat(msg2.parentRef()).isNotNull();
        assertThat(msg2.parentRef().messageId()).isEqualTo("1719651600.000000");
        // Verify timestamp precision: 1719651660 seconds, 123456 micros = 123456000 nanos
        assertThat(msg2.receivedAt().getEpochSecond()).isEqualTo(1719651660L);
        assertThat(msg2.receivedAt().getNano()).isEqualTo(123456000);
    }

    @Test
    void messaging_blankTokenReturnsFailure() {
        SlackChatPlatform blankPlatform = new SlackChatPlatform(client, "");
        blankPlatform.init();

        ChatChannelRef channel = new ChatChannelRef("C123");
        ChatContent content = new ChatContent("Hello", null, List.of());

        SendResult result = blankPlatform.messaging().send(channel, content);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("not configured");
    }

    @Test
    void degradedMode_blankToken() {
        SlackChatPlatform blankPlatform = new SlackChatPlatform(client, "");
        blankPlatform.init();

        // All capabilities should return degraded implementations
        assertThat(blankPlatform.supports(Messaging.class)).isFalse();
        assertThat(blankPlatform.supports(Threading.class)).isFalse();
        assertThat(blankPlatform.supports(Discovery.class)).isFalse();
        assertThat(blankPlatform.supports(Reactions.class)).isFalse();
        assertThat(blankPlatform.supports(Presence.class)).isFalse();
        assertThat(blankPlatform.supports(Members.class)).isFalse();
        assertThat(blankPlatform.supports(ChannelManagement.class)).isFalse();
        assertThat(blankPlatform.supports(MemberManagement.class)).isFalse();
        assertThat(blankPlatform.supports(MessageHistory.class)).isFalse();

        // Discovery should return empty
        assertThat(blankPlatform.discovery().listChannels()).isEmpty();
    }
}
