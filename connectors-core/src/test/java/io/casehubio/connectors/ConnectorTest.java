package io.casehubio.connectors;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.casehubio.connectors.slack.SlackConnector;
import io.casehubio.connectors.teams.TeamsConnector;
import io.casehubio.connectors.http.HttpHelper;

/**
 * Unit and WireMock integration tests for connector implementations.
 */
class ConnectorTest {

    private WireMockServer wireMock;
    private String baseUrl;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        wireMock.stubFor(post(urlEqualTo("/hook")).willReturn(ok()));
        baseUrl = "http://localhost:" + wireMock.port();
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    // ── ConnectorMessage ─────────────────────────────────────────────────────

    @Test
    void connectorMessage_twoArgConstructor_nullAttributes() {
        final ConnectorMessage msg = new ConnectorMessage("dest", "body");
        assertThat(msg.destination()).isEqualTo("dest");
        assertThat(msg.body()).isEqualTo("body");
        assertThat(msg.title()).isNull();
        assertThat(msg.attributes()).isEmpty();
    }

    @Test
    void connectorMessage_threeArgConstructor() {
        final ConnectorMessage msg = new ConnectorMessage("dest", "title", "body");
        assertThat(msg.title()).isEqualTo("title");
        assertThat(msg.body()).isEqualTo("body");
    }

    // ── Slack payload building ────────────────────────────────────────────────

    @Test
    void slack_buildPayload_withTitle() {
        final String json = SlackConnector.buildPayload("My Title", "My Body");
        assertThat(json).contains("\"text\"");
        assertThat(json).contains("My Title");
        assertThat(json).contains("My Body");
    }

    @Test
    void slack_buildPayload_bodyOnly() {
        final String json = SlackConnector.buildPayload(null, "Just body");
        assertThat(json.trim()).startsWith("{").endsWith("}");
        assertThat(json).contains("Just body");
    }

    @Test
    void slack_buildPayload_escapesSpecialChars() {
        final String json = SlackConnector.buildPayload(null, "Say \"hello\"");
        assertThat(json).doesNotContain("Say \"hello\""); // raw unescaped quotes
        assertThat(json).contains("\\\"hello\\\"");
    }

    @Test
    void slack_send_postsToDestination() {
        final SlackConnector connector = new SlackConnector();
        connector.send(new ConnectorMessage(baseUrl + "/hook", "Alert", "Something happened"));
        wireMock.verify(postRequestedFor(urlEqualTo("/hook")));
    }

    // ── Teams payload building ────────────────────────────────────────────────

    @Test
    void teams_buildPayload_containsAdaptiveCard() {
        final String json = TeamsConnector.buildPayload("Incident", "P1 alert triggered");
        assertThat(json).contains("AdaptiveCard");
        assertThat(json).contains("Incident");
        assertThat(json).contains("P1 alert triggered");
    }

    @Test
    void teams_buildPayload_noTitle() {
        final String json = TeamsConnector.buildPayload(null, "Body only");
        assertThat(json).contains("Body only");
    }

    @Test
    void teams_send_postsToDestination() {
        final TeamsConnector connector = new TeamsConnector();
        connector.send(new ConnectorMessage(baseUrl + "/hook", "Teams test", "Body"));
        wireMock.verify(postRequestedFor(urlEqualTo("/hook")));
    }

    // ── HttpHelper ────────────────────────────────────────────────────────────

    @Test
    void httpHelper_hmacSha256_deterministicAndPrefixed() {
        final String sig = HttpHelper.hmacSha256Hex("payload", "secret");
        assertThat(sig).startsWith("sha256=");
        assertThat(sig).isEqualTo(HttpHelper.hmacSha256Hex("payload", "secret"));
    }

    @Test
    void httpHelper_hmacSha256_differentForDifferentInputs() {
        assertThat(HttpHelper.hmacSha256Hex("a", "s"))
                .isNotEqualTo(HttpHelper.hmacSha256Hex("b", "s"));
    }

    @Test
    void httpHelper_jsonEscape_handlesSpecialChars() {
        assertThat(HttpHelper.jsonEscape("say \"hi\"")).isEqualTo("say \\\"hi\\\"");
        assertThat(HttpHelper.jsonEscape("line1\nline2")).isEqualTo("line1\\nline2");
    }

    @Test
    void httpHelper_jsonQuote_nullReturnsNull() {
        assertThat(HttpHelper.jsonQuote(null)).isEqualTo("null");
    }

    @Test
    void httpHelper_postJson_returnsFalseOnNon2xx() {
        wireMock.stubFor(post(urlEqualTo("/fail"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.serverError()));
        final boolean result = HttpHelper.postJson(baseUrl + "/fail", "{}");
        assertThat(result).isFalse();
    }

    @Test
    void httpHelper_postJson_returnsTrueOn200() {
        final boolean result = HttpHelper.postJson(baseUrl + "/hook", "{}");
        assertThat(result).isTrue();
    }
}
