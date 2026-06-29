package io.casehub.connectors.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.discord.model.*;

class DiscordClientTest {

    private static final String TOKEN = "test-bot-token";
    private static final String GUILD_ID = "123456789";

    private WireMockServer wireMock;
    private DiscordClient client;

    @BeforeEach
    void setup() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        client = new DiscordClient();
        client.apiBaseUrl = wireMock.baseUrl();
        client.guildId = GUILD_ID;
    }

    @AfterEach
    void teardown() {
        wireMock.stop();
    }

    @Test
    void sendMessage_success() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        final PostResult result = client.sendMessage(TOKEN, "ch1", "hello");

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isEqualTo("msg1");
        assertThat(result.channelId()).isEqualTo("ch1");
        assertThat(result.error()).isNull();

        wireMock.verify(postRequestedFor(urlEqualTo("/channels/ch1/messages"))
                .withHeader("Authorization", equalTo("Bot " + TOKEN))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(matchingJsonPath("$.content", equalTo("hello"))));
    }

    @Test
    void sendMessage_errorReturnsFailure() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(aResponse().withStatus(403).withBody("Forbidden")));

        final PostResult result = client.sendMessage(TOKEN, "ch1", "hello");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("403");
    }

    @Test
    void sendReply_includesMessageReference() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"msg2\",\"channel_id\":\"ch1\"}")));

        final PostResult result = client.sendReply(TOKEN, "ch1", "reply", "parent1");

        assertThat(result.ok()).isTrue();

        wireMock.verify(postRequestedFor(urlEqualTo("/channels/ch1/messages"))
                .withRequestBody(matchingJsonPath("$.message_reference.message_id", equalTo("parent1"))));
    }

    @Test
    void getMessages_paginates() {
        wireMock.stubFor(get(urlMatching("/channels/ch1/messages\\?limit=100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"1\",\"channel_id\":\"ch1\",\"author\":{\"id\":\"u1\",\"username\":\"user1\",\"global_name\":\"User One\",\"bot\":false},\"content\":\"msg1\",\"timestamp\":\"2024-01-01T00:00:00Z\",\"type\":0}," +
                                "{\"id\":\"2\",\"channel_id\":\"ch1\",\"author\":{\"id\":\"u1\",\"username\":\"user1\",\"global_name\":\"User One\",\"bot\":false},\"content\":\"msg2\",\"timestamp\":\"2024-01-01T00:01:00Z\",\"type\":0}]")));

        wireMock.stubFor(get(urlMatching("/channels/ch1/messages\\?limit=100&after=2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"3\",\"channel_id\":\"ch1\",\"author\":{\"id\":\"u1\",\"username\":\"user1\",\"global_name\":\"User One\",\"bot\":false},\"content\":\"msg3\",\"timestamp\":\"2024-01-01T00:02:00Z\",\"type\":0}]")));

        final List<DiscordMessage> messages = client.getMessages(TOKEN, "ch1", null, 100);

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).id()).isEqualTo("1");
        assertThat(messages.get(2).id()).isEqualTo("3");
    }

    @Test
    void getMessages_failSoftOnPage2() {
        wireMock.stubFor(get(urlMatching("/channels/ch1/messages\\?limit=100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"1\",\"channel_id\":\"ch1\",\"author\":{\"id\":\"u1\",\"username\":\"user1\",\"global_name\":\"User One\",\"bot\":false},\"content\":\"msg1\",\"timestamp\":\"2024-01-01T00:00:00Z\",\"type\":0}," +
                                "{\"id\":\"2\",\"channel_id\":\"ch1\",\"author\":{\"id\":\"u1\",\"username\":\"user1\",\"global_name\":\"User One\",\"bot\":false},\"content\":\"msg2\",\"timestamp\":\"2024-01-01T00:01:00Z\",\"type\":0}," +
                                "{\"id\":\"3\",\"channel_id\":\"ch1\",\"author\":{\"id\":\"u1\",\"username\":\"user1\",\"global_name\":\"User One\",\"bot\":false},\"content\":\"msg3\",\"timestamp\":\"2024-01-01T00:02:00Z\",\"type\":0}]")));

        wireMock.stubFor(get(urlMatching("/channels/ch1/messages\\?limit=100&after=3"))
                .willReturn(aResponse().withStatus(500)));

        final List<DiscordMessage> messages = client.getMessages(TOKEN, "ch1", null, 100);

        assertThat(messages).hasSize(3);
    }

    @Test
    void listGuildChannels_success() {
        wireMock.stubFor(get(urlEqualTo("/guilds/" + GUILD_ID + "/channels"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"ch1\",\"name\":\"general\",\"type\":0}," +
                                "{\"id\":\"ch2\",\"name\":\"random\",\"type\":0,\"topic\":\"Random stuff\"}]")));

        final List<DiscordChannel> channels = client.listGuildChannels(TOKEN);

        assertThat(channels).hasSize(2);
        assertThat(channels.get(0).id()).isEqualTo("ch1");
        assertThat(channels.get(0).name()).isEqualTo("general");
        assertThat(channels.get(1).topic()).isEqualTo("Random stuff");
    }

    @Test
    void getChannel_success() {
        wireMock.stubFor(get(urlEqualTo("/channels/ch1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"ch1\",\"name\":\"general\",\"type\":0}")));

        final DiscordChannel channel = client.getChannel(TOKEN, "ch1");

        assertThat(channel).isNotNull();
        assertThat(channel.id()).isEqualTo("ch1");
    }

    @Test
    void getChannel_404ReturnsNull() {
        wireMock.stubFor(get(urlEqualTo("/channels/ch1"))
                .willReturn(aResponse().withStatus(404)));

        final DiscordChannel channel = client.getChannel(TOKEN, "ch1");

        assertThat(channel).isNull();
    }

    @Test
    void createChannel_sendsCorrectBody() {
        wireMock.stubFor(post(urlEqualTo("/guilds/" + GUILD_ID + "/channels"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"ch-new\",\"name\":\"test\",\"type\":0}")));

        final DiscordChannel channel = client.createChannel(TOKEN, "test", "t", 0, false, false);

        assertThat(channel).isNotNull();

        wireMock.verify(postRequestedFor(urlEqualTo("/guilds/" + GUILD_ID + "/channels"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("test")))
                .withRequestBody(matchingJsonPath("$.topic", equalTo("t")))
                .withRequestBody(matchingJsonPath("$.type", equalTo("0"))));
    }

    @Test
    void createChannel_privateIncludesOverwrites() {
        wireMock.stubFor(post(urlEqualTo("/guilds/" + GUILD_ID + "/channels"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"ch-private\",\"name\":\"private\",\"type\":0}")));

        final DiscordChannel channel = client.createChannel(TOKEN, "private", "secret", 0, false, true);

        assertThat(channel).isNotNull();

        wireMock.verify(postRequestedFor(urlEqualTo("/guilds/" + GUILD_ID + "/channels"))
                .withRequestBody(matchingJsonPath("$.permission_overwrites[0].id", equalTo(GUILD_ID)))
                .withRequestBody(matchingJsonPath("$.permission_overwrites[0].type", equalTo("0")))
                .withRequestBody(matchingJsonPath("$.permission_overwrites[0].deny", equalTo("1024"))));
    }

    @Test
    void addReaction_204Success() {
        wireMock.stubFor(put(urlMatching("/channels/ch1/messages/msg1/reactions/.*/@me"))
                .willReturn(aResponse().withStatus(204)));

        assertThatNoException().isThrownBy(() ->
                client.addReaction(TOKEN, "ch1", "msg1", "👍"));
    }

    @Test
    void removeReaction_204Success() {
        wireMock.stubFor(delete(urlMatching("/channels/ch1/messages/msg1/reactions/.*/@me"))
                .willReturn(aResponse().withStatus(204)));

        assertThatNoException().isThrownBy(() ->
                client.removeReaction(TOKEN, "ch1", "msg1", "👍"));
    }

    @Test
    void listReactionEmoji_extractsFromMessage() {
        wireMock.stubFor(get(urlEqualTo("/channels/ch1/messages/msg1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"msg1\",\"reactions\":[" +
                                "{\"emoji\":{\"name\":\"👍\"}}," +
                                "{\"emoji\":{\"name\":\"custom\",\"id\":\"123456\"}}" +
                                "]}")));

        final List<String> emojis = client.listReactionEmoji(TOKEN, "ch1", "msg1");

        assertThat(emojis).containsExactly("👍", "custom:123456");
    }

    @Test
    void listGuildMembers_paginates() {
        wireMock.stubFor(get(urlMatching("/guilds/" + GUILD_ID + "/members\\?limit=100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"user\":{\"id\":\"u1\",\"username\":\"user1\",\"global_name\":\"User One\",\"bot\":false},\"joined_at\":\"2024-01-01T00:00:00Z\"}," +
                                "{\"user\":{\"id\":\"u2\",\"username\":\"user2\",\"global_name\":\"User Two\",\"bot\":false},\"joined_at\":\"2024-01-01T00:00:00Z\"}]")));

        wireMock.stubFor(get(urlMatching("/guilds/" + GUILD_ID + "/members\\?limit=100&after=u2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"user\":{\"id\":\"u3\",\"username\":\"user3\",\"global_name\":\"User Three\",\"bot\":false},\"joined_at\":\"2024-01-01T00:00:00Z\"}]")));

        final List<DiscordMember> members = client.listGuildMembers(TOKEN, 100, null);

        assertThat(members).hasSize(3);
        assertThat(members.get(0).user().id()).isEqualTo("u1");
        assertThat(members.get(2).user().id()).isEqualTo("u3");
    }

    @Test
    void listGuildMembers_failSoftOnPage2() {
        wireMock.stubFor(get(urlMatching("/guilds/" + GUILD_ID + "/members\\?limit=100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"user\":{\"id\":\"u1\",\"username\":\"user1\",\"global_name\":\"User One\",\"bot\":false},\"joined_at\":\"2024-01-01T00:00:00Z\"}]")));

        wireMock.stubFor(get(urlMatching("/guilds/" + GUILD_ID + "/members\\?limit=100&after=u1"))
                .willReturn(aResponse().withStatus(500)));

        final List<DiscordMember> members = client.listGuildMembers(TOKEN, 100, null);

        assertThat(members).hasSize(1);
    }

    @Test
    void getGuildMember_success() {
        wireMock.stubFor(get(urlEqualTo("/guilds/" + GUILD_ID + "/members/u1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"user\":{\"id\":\"u1\",\"username\":\"user1\",\"global_name\":\"User One\",\"bot\":false},\"joined_at\":\"2024-01-01T00:00:00Z\"}")));

        final DiscordMember member = client.getGuildMember(TOKEN, "u1");

        assertThat(member).isNotNull();
        assertThat(member.user().id()).isEqualTo("u1");
    }

    @Test
    void getGuildMember_404ReturnsNull() {
        wireMock.stubFor(get(urlEqualTo("/guilds/" + GUILD_ID + "/members/u1"))
                .willReturn(aResponse().withStatus(404)));

        final DiscordMember member = client.getGuildMember(TOKEN, "u1");

        assertThat(member).isNull();
    }

    @Test
    void getGuild_success() {
        wireMock.stubFor(get(urlMatching("/guilds/" + GUILD_ID + "\\?with_counts=true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + GUILD_ID + "\",\"name\":\"Test Guild\",\"approximate_member_count\":42}")));

        final DiscordGuild guild = client.getGuild(TOKEN, true);

        assertThat(guild).isNotNull();
        assertThat(guild.id()).isEqualTo(GUILD_ID);
        assertThat(guild.name()).isEqualTo("Test Guild");
        assertThat(guild.approximateMemberCount()).isEqualTo(42);
    }

    @Test
    void rateLimitRetry_429WithRetryAfter() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "1"))
                .willSetStateTo("retry"));

        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("retry")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        final PostResult result = client.sendMessage(TOKEN, "ch1", "hello");

        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isEqualTo("msg1");
    }

    @Test
    void rateLimitRetry_doubleRateLimitReturnsFailure() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "1")));

        final PostResult result = client.sendMessage(TOKEN, "ch1", "hello");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("rate-limited");
    }

    @Test
    void getGatewayUrl_success() {
        wireMock.stubFor(get(urlEqualTo("/gateway"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"url\":\"wss://gateway.discord.gg/\"}")));

        final String gatewayUrl = client.getGatewayUrl(TOKEN);

        assertThat(gatewayUrl).isEqualTo("wss://gateway.discord.gg/");
    }
}
