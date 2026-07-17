package io.casehub.connectors.chat.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.discord.DiscordClient;
import io.casehub.connectors.discord.DiscordGatewayPresenceCache;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class DiscordInboundConnectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WireMockServer wireMock;
    private DiscordInboundConnector connector;
    private CopyOnWriteArrayList<InboundMessage> received;

    @BeforeEach
    void setup() throws Exception {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();

        final DiscordClient client = new DiscordClient();
        setField(client, "apiBaseUrl", wireMock.baseUrl());
        setField(client, "maxAttachmentBytes", 8_388_608L);
        setField(client, "allowedCdnHostsConfig", "localhost");
        setField(client, "allowedCdnHosts", Set.of("localhost"));

        connector = new DiscordInboundConnector(
                client, new DiscordGatewayPresenceCache(), "test-token");
        received = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void teardown() {
        wireMock.stop();
    }

    @Test
    void messageWithAttachments_downloadsAndPopulatesInboundMessage() throws Exception {
        final byte[] fileContent = "PNG content".getBytes();
        wireMock.stubFor(get(urlPathEqualTo("/cdn/image.png"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Length", String.valueOf(fileContent.length))
                        .withBody(fileContent)));

        final JsonNode data = MAPPER.readTree("""
                {"id":"msg1","channel_id":"ch1","content":"look at this",
                 "type":0,"author":{"id":"u1","username":"user1","bot":false},
                 "attachments":[{"id":"a1","filename":"image.png","content_type":"image/png",
                   "size":%d,"url":"%s/cdn/image.png"}]}
                """.formatted(fileContent.length, wireMock.baseUrl()));

        connector.handleEvent("MESSAGE_CREATE", data, received::add);

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> !received.isEmpty());

        final InboundMessage msg = received.get(0);
        assertThat(msg.attachments()).hasSize(1);
        assertThat(msg.attachments().get(0).filename()).isEqualTo("image.png");
        assertThat(msg.attachments().get(0).contentType()).isEqualTo("image/png");
        assertThat(msg.attachments().get(0).content()).isEqualTo(fileContent);
        assertThat(msg.metadata().get("discord-attachment-count")).isEqualTo("1");
        assertThat(msg.metadata().get("discord-attachment-download-failures")).isEqualTo("0");
    }

    @Test
    void messageWithAttachments_partialFailure_includesSuccessfulDownloads() throws Exception {
        final byte[] fileContent = "OK".getBytes();
        wireMock.stubFor(get(urlPathEqualTo("/cdn/good.png"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Length", String.valueOf(fileContent.length))
                        .withBody(fileContent)));
        wireMock.stubFor(get(urlPathEqualTo("/cdn/expired.png"))
                .willReturn(aResponse().withStatus(403)));

        final JsonNode data = MAPPER.readTree("""
                {"id":"msg1","channel_id":"ch1","content":"two files",
                 "type":0,"author":{"id":"u1","username":"user1","bot":false},
                 "attachments":[
                   {"id":"a1","filename":"good.png","content_type":"image/png",
                    "size":%d,"url":"%s/cdn/good.png"},
                   {"id":"a2","filename":"expired.png","content_type":"image/png",
                    "size":100,"url":"%s/cdn/expired.png"}
                 ]}
                """.formatted(fileContent.length, wireMock.baseUrl(), wireMock.baseUrl()));

        connector.handleEvent("MESSAGE_CREATE", data, received::add);

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> !received.isEmpty());

        final InboundMessage msg = received.get(0);
        assertThat(msg.attachments()).hasSize(1);
        assertThat(msg.attachments().get(0).filename()).isEqualTo("good.png");
        assertThat(msg.metadata().get("discord-attachment-count")).isEqualTo("2");
        assertThat(msg.metadata().get("discord-attachment-download-failures")).isEqualTo("1");
    }

    @Test
    void messageWithNoAttachments_emptyAttachmentList() throws Exception {
        final JsonNode data = MAPPER.readTree("""
                {"id":"msg1","channel_id":"ch1","content":"no files",
                 "type":0,"author":{"id":"u1","username":"user1","bot":false},
                 "attachments":[]}
                """);

        connector.handleEvent("MESSAGE_CREATE", data, received::add);

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                .until(() -> !received.isEmpty());

        final InboundMessage msg = received.get(0);
        assertThat(msg.attachments()).isEmpty();
        assertThat(msg.metadata()).doesNotContainKey("discord-attachment-count");
    }

    @Test
    void botMessages_filtered() throws Exception {
        final JsonNode data = MAPPER.readTree("""
                {"id":"msg1","channel_id":"ch1","content":"bot says hi",
                 "type":0,"author":{"id":"bot1","username":"testbot","bot":true},
                 "attachments":[]}
                """);

        connector.handleEvent("MESSAGE_CREATE", data, received::add);

        Thread.sleep(200);
        assertThat(received).isEmpty();
    }

    @Test
    void messageWithGuildId_extractedFromEventData() throws Exception {
        final JsonNode data = MAPPER.readTree("""
                                              {"id":"msg1","channel_id":"ch1","content":"hello",
                                               "guild_id":"guild-from-event",
                                               "type":0,"author":{"id":"u1","username":"user1","bot":false},
                                               "attachments":[]}
                                              """);

        connector.handleEvent("MESSAGE_CREATE", data, received::add);

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                  .until(() -> !received.isEmpty());

        assertThat(received.get(0).metadata().get("discord-guild-id"))
                .isEqualTo("guild-from-event");
    }

    @Test
    void messageWithoutGuildId_setsUnknown() throws Exception {
        final JsonNode data = MAPPER.readTree("""
                                              {"id":"msg1","channel_id":"ch1","content":"dm",
                                               "type":0,"author":{"id":"u1","username":"user1","bot":false},
                                               "attachments":[]}
                                              """);

        connector.handleEvent("MESSAGE_CREATE", data, received::add);

        Awaitility.await().atMost(2, TimeUnit.SECONDS)
                  .until(() -> !received.isEmpty());

        assertThat(received.get(0).metadata().get("discord-guild-id"))
                .isEqualTo("unknown");
    }


    private static void setField(final Object target, final String fieldName,
                                  final Object value) throws Exception {
        final var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
