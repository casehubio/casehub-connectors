package io.casehub.connectors.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.casehub.connectors.Attachment;
import io.casehub.connectors.discord.model.DiscordAttachment;
import io.casehub.connectors.discord.model.DiscordChannel;
import io.casehub.connectors.discord.model.DiscordEmbed;
import io.casehub.connectors.discord.model.DiscordGuild;
import io.casehub.connectors.discord.model.DiscordMember;
import io.casehub.connectors.discord.model.DiscordMessage;
import io.casehub.connectors.discord.model.PostResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class DiscordClientTest {

    private static final String TOKEN = "test-bot-token";
    private static final String GUILD_ID = "123456789";

    private WireMockServer wireMock;
    private DiscordClient client;

    @BeforeEach
    void setup() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        client                       = new DiscordClient();
        client.apiBaseUrl            = wireMock.baseUrl();
        client.maxAttachmentBytes    = 8_388_608;
        client.allowedCdnHostsConfig = "cdn.discordapp.com,media.discordapp.net";
        client.init();}

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

        final List<DiscordChannel> channels = client.listGuildChannels(TOKEN, GUILD_ID);

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
    void parseChannel_extractsGuildId() {
        wireMock.stubFor(get(urlEqualTo("/channels/ch1"))
                                 .willReturn(okJson("""
                                                    {"id":"ch1","name":"general","type":0,"guild_id":"g1"}
                                                    """)));

        final DiscordChannel channel = client.getChannel(TOKEN, "ch1");

        assertThat(channel).isNotNull();
        assertThat(channel.guildId()).isEqualTo("g1");
    }

    @Test
    void parseChannel_nullGuildIdWhenAbsent() {
        wireMock.stubFor(get(urlEqualTo("/channels/dm1"))
                                 .willReturn(okJson("""
                                                    {"id":"dm1","name":"DM","type":1}
                                                    """)));

        final DiscordChannel channel = client.getChannel(TOKEN, "dm1");

        assertThat(channel).isNotNull();
        assertThat(channel.guildId()).isNull();
    }


    @Test
    void createChannel_sendsCorrectBody() {
        wireMock.stubFor(post(urlEqualTo("/guilds/" + GUILD_ID + "/channels"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"ch-new\",\"name\":\"test\",\"type\":0}")));

        final DiscordChannel channel = client.createChannel(TOKEN, GUILD_ID, "test", "t", 0, false, false);

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

        final DiscordChannel channel = client.createChannel(TOKEN, GUILD_ID, "private", "secret", 0, false, true);

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

        final List<DiscordMember> members = client.listGuildMembers(TOKEN, GUILD_ID, 100, null);

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

        final List<DiscordMember> members = client.listGuildMembers(TOKEN, GUILD_ID, 100, null);

        assertThat(members).hasSize(1);
    }

    @Test
    void getGuildMember_success() {
        wireMock.stubFor(get(urlEqualTo("/guilds/" + GUILD_ID + "/members/u1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"user\":{\"id\":\"u1\",\"username\":\"user1\",\"global_name\":\"User One\",\"bot\":false},\"joined_at\":\"2024-01-01T00:00:00Z\"}")));

        final DiscordMember member = client.getGuildMember(TOKEN, GUILD_ID, "u1");

        assertThat(member).isNotNull();
        assertThat(member.user().id()).isEqualTo("u1");
    }

    @Test
    void getGuildMember_404ReturnsNull() {
        wireMock.stubFor(get(urlEqualTo("/guilds/" + GUILD_ID + "/members/u1"))
                .willReturn(aResponse().withStatus(404)));

        final DiscordMember member = client.getGuildMember(TOKEN, GUILD_ID, "u1");

        assertThat(member).isNull();
    }

    @Test
    void getGuild_success() {
        wireMock.stubFor(get(urlMatching("/guilds/" + GUILD_ID + "\\?with_counts=true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + GUILD_ID + "\",\"name\":\"Test Guild\",\"approximate_member_count\":42}")));

        final DiscordGuild guild = client.getGuild(TOKEN, GUILD_ID, true);

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

    @Test
    void listBotGuilds_success() {
        wireMock.stubFor(get(urlEqualTo("/users/@me/guilds?limit=200"))
                                 .willReturn(okJson("""
                                                    [{"id":"g1","name":"Guild One"},
                                                     {"id":"g2","name":"Guild Two"}]
                                                    """)));
        wireMock.stubFor(get(urlMatching("/users/@me/guilds\\?limit=200&after=g2"))
                                 .willReturn(okJson("[]")));

        final List<DiscordGuild> guilds = client.listBotGuilds(TOKEN);

        assertThat(guilds).hasSize(2);
        assertThat(guilds.get(0).id()).isEqualTo("g1");
        assertThat(guilds.get(0).name()).isEqualTo("Guild One");
        assertThat(guilds.get(1).id()).isEqualTo("g2");
    }

    @Test
    void listBotGuilds_nullOnFirstPageFailure() {
        wireMock.stubFor(get(urlEqualTo("/users/@me/guilds?limit=200"))
                                 .willReturn(aResponse().withStatus(401)));

        final List<DiscordGuild> guilds = client.listBotGuilds(TOKEN);

        assertThat(guilds).isNull();
    }

    @Test
    void listBotGuilds_failSoftOnPage2() {
        wireMock.stubFor(get(urlEqualTo("/users/@me/guilds?limit=200"))
                                 .willReturn(okJson("""
                                                    [{"id":"g1","name":"Guild One"}]
                                                    """)));
        wireMock.stubFor(get(urlMatching("/users/@me/guilds\\?limit=200&after=g1"))
                                 .willReturn(aResponse().withStatus(500)));

        final List<DiscordGuild> guilds = client.listBotGuilds(TOKEN);

        assertThat(guilds).hasSize(1);
        assertThat(guilds.get(0).id()).isEqualTo("g1");
    }

    @Test
    void listBotGuilds_emptyListForNoGuilds() {
        wireMock.stubFor(get(urlEqualTo("/users/@me/guilds?limit=200"))
                                 .willReturn(okJson("[]")));

        final List<DiscordGuild> guilds = client.listBotGuilds(TOKEN);

        assertThat(guilds).isEmpty();
    }


    // ── Attachment parsing ────────────────────────────────────────────────────

    @Test
    void parseAttachments_parsesArrayWithMultipleAttachments() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final String json = """
                [
                  {"id":"att1","filename":"image.png","content_type":"image/png","size":12345,"url":"https://cdn.discordapp.com/attachments/1/2/image.png"},
                  {"id":"att2","filename":"doc.pdf","content_type":"application/pdf","size":67890,"url":"https://cdn.discordapp.com/attachments/1/2/doc.pdf"}
                ]""";
        final JsonNode array = mapper.readTree(json);

        final List<DiscordAttachment> result = client.parseAttachments(array);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("att1");
        assertThat(result.get(0).filename()).isEqualTo("image.png");
        assertThat(result.get(0).contentType()).isEqualTo("image/png");
        assertThat(result.get(0).size()).isEqualTo(12345L);
        assertThat(result.get(0).url()).isEqualTo("https://cdn.discordapp.com/attachments/1/2/image.png");
        assertThat(result.get(1).id()).isEqualTo("att2");
    }

    @Test
    void parseAttachments_emptyArray_returnsEmptyList() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode array = mapper.readTree("[]");

        final List<DiscordAttachment> result = client.parseAttachments(array);

        assertThat(result).isEmpty();
    }

    @Test
    void parseAttachments_nullArray_returnsEmptyList() {
        final List<DiscordAttachment> result = client.parseAttachments(null);

        assertThat(result).isEmpty();
    }

    @Test
    void getMessages_includesAttachments() {
        wireMock.stubFor(get(urlMatching("/channels/ch1/messages\\?limit=1"))
                .willReturn(okJson("""
                        [{"id":"msg1","channel_id":"ch1","content":"hello",
                          "timestamp":"2026-06-01T00:00:00Z","type":0,
                          "author":{"id":"u1","username":"user1","bot":false},
                          "attachments":[{"id":"a1","filename":"f.png","content_type":"image/png","size":100,"url":"https://cdn.discordapp.com/a1"}]}]
                        """)));

        final List<DiscordMessage> msgs = client.getMessages(TOKEN, "ch1", null, 1);

        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).attachments()).hasSize(1);
        assertThat(msgs.get(0).attachments().get(0).filename()).isEqualTo("f.png");
    }

    @Test
    void getMessages_noAttachmentsField_returnsEmptyList() {
        wireMock.stubFor(get(urlMatching("/channels/ch1/messages\\?limit=1"))
                .willReturn(okJson("""
                        [{"id":"msg1","channel_id":"ch1","content":"hello",
                          "timestamp":"2026-06-01T00:00:00Z","type":0,
                          "author":{"id":"u1","username":"user1","bot":false}}]
                        """)));

        final List<DiscordMessage> msgs = client.getMessages(TOKEN, "ch1", null, 1);

        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).attachments()).isEmpty();
    }

    // ── Attachment downloading ────────────────────────────────────────────────

    @Test
    void downloadAttachment_success() {
        final byte[] content = "hello world".getBytes();
        wireMock.stubFor(get(urlPathEqualTo("/cdn/file.png"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Length", String.valueOf(content.length))
                        .withBody(content)));

        final var att = new DiscordAttachment("a1", "file.png", "image/png",
                content.length, wireMock.baseUrl() + "/cdn/file.png");
        final Attachment result = client.downloadAttachment(att, Set.of("localhost"));

        assertThat(result).isNotNull();
        assertThat(result.filename()).isEqualTo("file.png");
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.content()).isEqualTo(content);
    }

    @Test
    void downloadAttachment_ssrfRejected_nonAllowedHost() {
        final var att = new DiscordAttachment("a1", "f.png", "image/png",
                100, "https://evil.com/payload");

        final Attachment result = client.downloadAttachment(att);

        assertThat(result).isNull();
    }

    @Test
    void downloadAttachment_contentLengthExceedsLimit_returnsNull() {
        wireMock.stubFor(get(urlPathEqualTo("/cdn/huge.bin"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Length", "999999999")));

        final var att = new DiscordAttachment("a1", "huge.bin", "application/octet-stream",
                999999999, wireMock.baseUrl() + "/cdn/huge.bin");
        final Attachment result = client.downloadAttachment(att, Set.of("localhost"));

        assertThat(result).isNull();
    }

    @Test
    void downloadAttachment_cdn403_returnsNull() {
        wireMock.stubFor(get(urlPathEqualTo("/cdn/expired.png"))
                .willReturn(aResponse().withStatus(403)));

        final var att = new DiscordAttachment("a1", "expired.png", "image/png",
                100, wireMock.baseUrl() + "/cdn/expired.png");
        final Attachment result = client.downloadAttachment(att, Set.of("localhost"));

        assertThat(result).isNull();
    }

    @Test
    void downloadAttachment_streamingAbortOnOversizedChunkedResponse() {
        final byte[] oversized = new byte[1024 * 1024 + 1];
        wireMock.stubFor(get(urlPathEqualTo("/cdn/chunked.bin"))
                .willReturn(aResponse().withStatus(200).withBody(oversized)));

        client.maxAttachmentBytes = 1024 * 1024;
        final var att = new DiscordAttachment("a1", "chunked.bin", "application/octet-stream",
                0, wireMock.baseUrl() + "/cdn/chunked.bin");
        final Attachment result = client.downloadAttachment(att, Set.of("localhost"));

        assertThat(result).isNull();
    }

    @Test
    void downloadAttachment_nullUrl_returnsNull() {
        final var att = new DiscordAttachment("a1", "file.png", "image/png", 100, null);

        final Attachment result = client.downloadAttachment(att);

        assertThat(result).isNull();
    }


    // ── Embed sending ─────────────────────────────────────────────────────────

    @Test
    void sendMessage_withEmbed_includesEmbedsArray() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        final var embed = new DiscordEmbed("Title", "Desc", null, 16711680,
                List.of(new DiscordEmbed.Field("f1", "v1", true)),
                null, null, new DiscordEmbed.Footer("foot"), null);

        final PostResult result = client.sendMessage(TOKEN, "ch1", "text", List.of(embed));

        assertThat(result.ok()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/channels/ch1/messages"))
                .withRequestBody(matchingJsonPath("$.content", equalTo("text")))
                .withRequestBody(matchingJsonPath("$.embeds[0].title", equalTo("Title")))
                .withRequestBody(matchingJsonPath("$.embeds[0].description", equalTo("Desc")))
                .withRequestBody(matchingJsonPath("$.embeds[0].color", equalTo("16711680")))
                .withRequestBody(matchingJsonPath("$.embeds[0].fields[0].name", equalTo("f1")))
                .withRequestBody(matchingJsonPath("$.embeds[0].footer.text", equalTo("foot"))));
    }

    @Test
    void sendMessage_embedOnly_omitsContentField() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        final var embed = new DiscordEmbed("Title", "Desc", null, null,
                List.of(), null, null, null, null);

        final PostResult result = client.sendMessage(TOKEN, "ch1", null, List.of(embed));

        assertThat(result.ok()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/channels/ch1/messages"))
                .withRequestBody(matchingJsonPath("$.embeds[0].title", equalTo("Title"))));
    }

    @Test
    void sendReply_withEmbed_includesEmbedAndReference() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg2\",\"channel_id\":\"ch1\"}")));

        final var embed = new DiscordEmbed("T", "D", null, 65280,
                List.of(), null, null, null, null);

        final PostResult result = client.sendReply(TOKEN, "ch1", "reply",
                "parent1", List.of(embed));

        assertThat(result.ok()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/channels/ch1/messages"))
                .withRequestBody(matchingJsonPath("$.embeds[0].title", equalTo("T")))
                .withRequestBody(matchingJsonPath("$.message_reference.message_id",
                        equalTo("parent1"))));
    }

    @Test
    void sendMessage_existingThreeArgDelegates_noEmbedsInBody() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        client.sendMessage(TOKEN, "ch1", "text");

        wireMock.verify(postRequestedFor(urlEqualTo("/channels/ch1/messages"))
                .withRequestBody(matchingJsonPath("$.content", equalTo("text"))));
    }

    @Test
    void sendMessage_embedWithThumbnailAndImage() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        final var embed = new DiscordEmbed(null, null, null, null,
                List.of(), "https://img/thumb.png", "https://img/big.png",
                null, new DiscordEmbed.Author("Bot"));

        client.sendMessage(TOKEN, "ch1", "text", List.of(embed));

        wireMock.verify(postRequestedFor(urlEqualTo("/channels/ch1/messages"))
                .withRequestBody(matchingJsonPath("$.embeds[0].thumbnail.url",
                        equalTo("https://img/thumb.png")))
                .withRequestBody(matchingJsonPath("$.embeds[0].image.url",
                        equalTo("https://img/big.png")))
                .withRequestBody(matchingJsonPath("$.embeds[0].author.name",
                        equalTo("Bot"))));
    }
}
