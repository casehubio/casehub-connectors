package io.casehub.connectors.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.casehub.connectors.discord.DiscordClient;
import io.casehub.connectors.discord.DiscordDiscovery;

class DiscordMcpToolTest {

    private WireMockServer wireMock;
    private DiscordClient client;
    private McpToolTestSupport.RecordingBridge bridge;
    private DiscordMcpTool tool;

    @BeforeEach
    void start() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        client = new DiscordClient();
        setField(client, "apiBaseUrl", wireMock.baseUrl());
        setField(client, "guildId", "guild1");
        setField(client, "maxAttachmentBytes", 8_388_608L);
        bridge = new McpToolTestSupport.RecordingBridge();
        tool = new DiscordMcpTool(client, bridge, "test-token");
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    // ── send_discord ──────────────────────────────────────────────────────────

    @Test
    void sendDiscord_success_returnsPostedWithId() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        final String result = tool.sendDiscord("ch1", "Hello", null, null, null, null,
                null, null, null, null, null, null);

        assertThat(result).isEqualTo("Posted to ch1 (id=msg1)");
    }

    @Test
    void sendDiscord_success_bridgeCalled() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        tool.sendDiscord("ch1", "Hello", null, null, null, null,
                null, null, null, null, null, null);

        assertThat(bridge.lastConnectorId).isEqualTo(DiscordDiscovery.ID);
        assertThat(bridge.lastDestination).isEqualTo("ch1");
        assertThat(bridge.lastContent).isEqualTo("Hello");
    }

    @Test
    void sendDiscord_blankToken_returnsFailedNoBridgeCall() {
        final var blankTool = new DiscordMcpTool(client, bridge, "");

        final String result = blankTool.sendDiscord("ch1", "Hello", null, null, null, null,
                null, null, null, null, null, null);

        assertThat(result).isEqualTo("Failed: casehub.connectors.discord.token is not configured");
        assertThat(bridge.lastConnectorId).isNull();
    }

    @Test
    void sendDiscord_withReplyToMessageId_callsSendReply() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg2\",\"channel_id\":\"ch1\"}")));

        tool.sendDiscord("ch1", "reply", "parent1", null, null, null,
                null, null, null, null, null, null);

        wireMock.verify(postRequestedFor(urlEqualTo("/channels/ch1/messages"))
                .withRequestBody(matchingJsonPath("$.message_reference.message_id",
                        equalTo("parent1"))));
    }

    @Test
    void sendDiscord_withEmbedArgs_sendsEmbed() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        tool.sendDiscord("ch1", "text", null, "Embed Title", "Embed Desc", "16711680",
                null, null, null, null, null, null);

        wireMock.verify(postRequestedFor(urlEqualTo("/channels/ch1/messages"))
                .withRequestBody(matchingJsonPath("$.embeds[0].title", equalTo("Embed Title")))
                .withRequestBody(matchingJsonPath("$.embeds[0].color", equalTo("16711680"))));
    }

    @Test
    void sendDiscord_embedOnly_noTextRequired() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        final String result = tool.sendDiscord("ch1", null, null, "Title", null, null,
                null, null, null, null, null, null);

        assertThat(result).startsWith("Posted to");
        assertThat(bridge.lastContent).isEqualTo("Title");
    }

    @Test
    void sendDiscord_noTextNoEmbed_returnsFailed() {
        final String result = tool.sendDiscord("ch1", null, null, null, null, null,
                null, null, null, null, null, null);

        assertThat(result).isEqualTo("Failed: text or embed required");
        assertThat(bridge.lastConnectorId).isNull();
    }

    @Test
    void sendDiscord_apiError_returnsFailed() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(aResponse().withStatus(403).withBody("Forbidden")));

        final String result = tool.sendDiscord("ch1", "Hello", null, null, null, null,
                null, null, null, null, null, null);

        assertThat(result).startsWith("Failed:");
        assertThat(bridge.lastConnectorId).isNull();
    }

    @Test
    void sendDiscord_fullEmbed_allParams() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        final String result = tool.sendDiscord("ch1", null, null,
                "Title", "Description", "255",
                "https://example.com",
                "https://example.com/thumb.png",
                "https://example.com/image.png",
                "Footer text", "Author Name",
                "[{\"name\":\"Field 1\",\"value\":\"Value 1\",\"inline\":true}]");

        assertThat(result).startsWith("Posted to");
        wireMock.verify(postRequestedFor(urlEqualTo("/channels/ch1/messages"))
                .withRequestBody(matchingJsonPath("$.embeds[0].url", equalTo("https://example.com")))
                .withRequestBody(matchingJsonPath("$.embeds[0].thumbnail.url", equalTo("https://example.com/thumb.png")))
                .withRequestBody(matchingJsonPath("$.embeds[0].image.url", equalTo("https://example.com/image.png")))
                .withRequestBody(matchingJsonPath("$.embeds[0].footer.text", equalTo("Footer text")))
                .withRequestBody(matchingJsonPath("$.embeds[0].author.name", equalTo("Author Name")))
                .withRequestBody(matchingJsonPath("$.embeds[0].fields[0].name", equalTo("Field 1")))
                .withRequestBody(matchingJsonPath("$.embeds[0].fields[0].inline", equalTo("true"))));
    }

    @Test
    void sendDiscord_embedFieldsMalformedJson_returnsFailed() {
        final String result = tool.sendDiscord("ch1", null, null,
                "Title", null, null, null, null, null, null, null, "not-json");

        assertThat(result).startsWith("Failed: embedFields must be");
    }

    @Test
    void sendDiscord_embedFieldMissingName_returnsFailed() {
        final String result = tool.sendDiscord("ch1", null, null,
                "Title", null, null, null, null, null, null, null,
                "[{\"value\":\"val\"}]");

        assertThat(result).startsWith("Failed: embedFields");
    }

    @Test
    void sendDiscord_embedTitleExceedsLimit_returnsFailed() {
        final String result = tool.sendDiscord("ch1", null, null,
                "x".repeat(257), null, null, null, null, null, null, null, null);

        assertThat(result).isEqualTo("Failed: embedTitle exceeds 256 characters");
    }

    @Test
    void sendDiscord_embedDescriptionExceedsLimit_returnsFailed() {
        final String result = tool.sendDiscord("ch1", null, null,
                "Title", "x".repeat(4097), null, null, null, null, null, null, null);

        assertThat(result).isEqualTo("Failed: embedDescription exceeds 4096 characters");
    }

    @Test
    void sendDiscord_embedFieldsExceeds25_returnsFailed() {
        StringBuilder fields = new StringBuilder("[");
        for (int i = 0; i < 26; i++) {
            if (i > 0) fields.append(",");
            fields.append("{\"name\":\"f").append(i).append("\",\"value\":\"v\"}");
        }
        fields.append("]");

        final String result = tool.sendDiscord("ch1", null, null,
                "Title", null, null, null, null, null, null, null, fields.toString());

        assertThat(result).isEqualTo("Failed: embedFields exceeds 25 fields");
    }

    @Test
    void sendDiscord_totalEmbedContentExceedsLimit_returnsFailed() {
        final String result = tool.sendDiscord("ch1", null, null,
                "Title", "x".repeat(4096), null, null, null, null,
                "x".repeat(2000), null, null);

        assertThat(result).startsWith("Failed: total embed content exceeds 6000 characters");
    }

    @Test
    void sendDiscord_embedUrlWithoutTitle_returnsFailed() {
        final String result = tool.sendDiscord("ch1", "text", null,
                null, "Desc", null, "https://example.com", null, null, null, null, null);

        assertThat(result).isEqualTo("Failed: embedUrl requires embedTitle");
    }

    @Test
    void sendDiscord_onlyFooter_isValidEmbed() {
        wireMock.stubFor(post(urlEqualTo("/channels/ch1/messages"))
                .willReturn(okJson("{\"id\":\"msg1\",\"channel_id\":\"ch1\"}")));

        final String result = tool.sendDiscord("ch1", null, null,
                null, null, null, null, null, null, "Just footer", null, null);

        assertThat(result).startsWith("Posted to");
    }

    // ── list_discord_channels ─────────────────────────────────────────────────

    @Test
    void listDiscordChannels_success() {
        wireMock.stubFor(get(urlPathEqualTo("/guilds/guild1/channels"))
                .willReturn(okJson("""
                        [{"id":"ch1","name":"general","topic":"Main channel","type":0,
                          "permission_overwrites":[]},
                         {"id":"ch2","name":"announcements","topic":null,"type":5,
                          "permission_overwrites":[]},
                         {"id":"ch3","name":"voice","topic":null,"type":2,
                          "permission_overwrites":[]}]
                        """)));

        final String result = tool.listDiscordChannels();

        assertThat(result).contains("#general (ch1)").contains("Main channel");
        assertThat(result).contains("#announcements (ch2)");
        assertThat(result).doesNotContain("voice");
    }

    @Test
    void listDiscordChannels_blankToken_returnsFailed() {
        final var blankTool = new DiscordMcpTool(client, bridge, "");

        assertThat(blankTool.listDiscordChannels())
                .isEqualTo("Failed: casehub.connectors.discord.token is not configured");
    }

    @Test
    void listDiscordChannels_blankGuildId_returnsFailed() throws Exception {
        setField(client, "guildId", "");

        assertThat(tool.listDiscordChannels())
                .isEqualTo("Failed: casehub.discord.guild-id is not configured");
    }

    private static void setField(final Object target, final String fieldName,
                                  final Object value) throws Exception {
        final var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
