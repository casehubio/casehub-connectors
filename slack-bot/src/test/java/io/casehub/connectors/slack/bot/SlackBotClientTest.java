package io.casehub.connectors.slack.bot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.not;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.casehub.connectors.DiscoveredTarget;

import io.casehub.connectors.slack.bot.SlackBotClient.ApiResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

class SlackBotClientTest {

    private WireMockServer wireMock;
    private SlackBotClient client;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        client = new SlackBotClient();
        client.apiBaseUrl = "http://localhost:" + wireMock.port();
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    // ── Payload shape ─────────────────────────────────────────────────────────

    @Test
    void postMessage_sendsAuthorizationHeader() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .willReturn(okJson("{\"ok\":true,\"ts\":\"1638535627.000200\"}")));

        client.postMessage("xoxb-test-token", "C123ABC", "Hello", null);

        wireMock.verify(postRequestedFor(urlEqualTo("/api/chat.postMessage"))
                .withHeader("Authorization", equalTo("Bearer xoxb-test-token")));
    }

    @Test
    void postMessage_sendsChannelAndText() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .willReturn(okJson("{\"ok\":true,\"ts\":\"1638535627.000200\"}")));

        client.postMessage("xoxb-test-token", "C123ABC", "Hello world", null);

        wireMock.verify(postRequestedFor(urlEqualTo("/api/chat.postMessage"))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("C123ABC")))
                .withRequestBody(matchingJsonPath("$.text", equalTo("Hello world"))));
    }

    @Test
    void postMessage_withoutThreadTs_noThreadTsField() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .willReturn(okJson("{\"ok\":true,\"ts\":\"1638535627.000200\"}")));

        client.postMessage("xoxb-test-token", "C123ABC", "Hello", null);

        wireMock.verify(postRequestedFor(urlEqualTo("/api/chat.postMessage"))
                .withRequestBody(notMatching(".*thread_ts.*")));
    }

    @Test
    void postMessage_withThreadTs_includesThreadTsField() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .willReturn(okJson("{\"ok\":true,\"ts\":\"1638535628.000400\"}")));

        client.postMessage("xoxb-test-token", "C123ABC", "Reply", "1638535600.000100");

        wireMock.verify(postRequestedFor(urlEqualTo("/api/chat.postMessage"))
                .withRequestBody(matchingJsonPath("$.thread_ts", equalTo("1638535600.000100"))));
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    @Test
    void postMessage_okResponse_returnsSuccessResult() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .willReturn(okJson("{\"ok\":true,\"ts\":\"1638535627.000200\"}")));

        final SlackBotClient.PostResult result =
                client.postMessage("xoxb-test-token", "C123ABC", "Hello", null);

        assertThat(result.ok()).isTrue();
        assertThat(result.ts()).isEqualTo("1638535627.000200");
        assertThat(result.error()).isNull();
    }

    @Test
    void postMessage_notOkResponse_returnsFailureResult() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .willReturn(okJson("{\"ok\":false,\"error\":\"channel_not_found\"}")));

        final SlackBotClient.PostResult result =
                client.postMessage("xoxb-test-token", "C123ABC", "Hello", null);

        assertThat(result.ok()).isFalse();
        assertThat(result.ts()).isNull();
        assertThat(result.error()).isEqualTo("channel_not_found");
    }

    // ── Rate limit retry ──────────────────────────────────────────────────────

    @Test
    void postMessage_429WithRetryAfterZero_retriesOnceAndSucceeds() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .inScenario("rate-limit")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "0")
                        .withBody("{\"ok\":false,\"error\":\"ratelimited\"}"))
                .willSetStateTo("retried"));

        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("retried")
                .willReturn(okJson("{\"ok\":true,\"ts\":\"1638535627.000200\"}")));

        final SlackBotClient.PostResult result =
                client.postMessage("xoxb-test-token", "C123ABC", "Hello", null);

        assertThat(result.ok()).isTrue();
        wireMock.verify(2, postRequestedFor(urlEqualTo("/api/chat.postMessage")));
    }

    @Test
    void postMessage_429WithoutRetryAfter_sleepsOneSecondAndRetries() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .inScenario("rate-limit-no-header")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(429)
                        .withBody("{\"ok\":false,\"error\":\"ratelimited\"}"))
                .willSetStateTo("retried"));

        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .inScenario("rate-limit-no-header")
                .whenScenarioStateIs("retried")
                .willReturn(okJson("{\"ok\":true,\"ts\":\"1638535627.000200\"}")));

        final SlackBotClient.PostResult result =
                client.postMessage("xoxb-test-token", "C123ABC", "Hello", null);

        assertThat(result.ok()).isTrue();
        wireMock.verify(2, postRequestedFor(urlEqualTo("/api/chat.postMessage")));
    }

    @Test
    void postMessage_429ThenAnotherError_returnsSecondResult() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .inScenario("rate-limit-fail")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "0"))
                .willSetStateTo("retried"));

        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .inScenario("rate-limit-fail")
                .whenScenarioStateIs("retried")
                .willReturn(okJson("{\"ok\":false,\"error\":\"fatal_error\"}")));

        final SlackBotClient.PostResult result =
                client.postMessage("xoxb-test-token", "C123ABC", "Hello", null);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("fatal_error");
        wireMock.verify(2, postRequestedFor(urlEqualTo("/api/chat.postMessage")));
    }

    // ── Channel discovery ─────────────────────────────────────────────────────────

    @Test
    void listChannels_returnsDiscoveredTargets() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true"))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C123ABC\",\"name\":\"general\"},"
                        + "{\"id\":\"C456DEF\",\"name\":\"engineering\"}"
                        + "]}")));

        final List<DiscoveredTarget> result = client.listChannels("xoxb-test-token");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("C123ABC");
        assertThat(result.get(0).displayName()).isEqualTo("#general");
        assertThat(result.get(1).id()).isEqualTo("C456DEF");
        assertThat(result.get(1).displayName()).isEqualTo("#engineering");
    }

    @Test
    void listChannels_sendsAuthorizationHeader() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true"))
                .willReturn(okJson("{\"ok\":true,\"channels\":[]}")));

        client.listChannels("xoxb-my-token");

        wireMock.verify(getRequestedFor(urlEqualTo(
                "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true"))
                .withHeader("Authorization", equalTo("Bearer xoxb-my-token")));
    }

    @Test
    void listChannels_slackReturnsNotOk_returnsEmptyList() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true"))
                .willReturn(okJson("{\"ok\":false,\"error\":\"invalid_auth\"}")));

        final List<DiscoveredTarget> result = client.listChannels("xoxb-bad");

        assertThat(result).isEmpty();
    }

    @Test
    void listChannels_twoPagesWithCursor_returnsBothPages() {
        final String page1Url = "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true";
        final String page2Url = page1Url + "&cursor=cursor-page2";

        wireMock.stubFor(get(urlEqualTo(page1Url))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C001\",\"name\":\"general\"}"
                        + "],\"response_metadata\":{\"next_cursor\":\"cursor-page2\"}}")));
        wireMock.stubFor(get(urlEqualTo(page2Url))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C002\",\"name\":\"engineering\"}"
                        + "]}")));

        final List<DiscoveredTarget> result = client.listChannels("xoxb-test-token");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DiscoveredTarget::id).containsExactly("C001", "C002");
    }

    @Test
    void listChannels_threePagesWithCursor_returnsAllPages() {
        final String base = "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true";

        wireMock.stubFor(get(urlEqualTo(base))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C001\",\"name\":\"alpha\"}"
                        + "],\"response_metadata\":{\"next_cursor\":\"c2\"}}")));
        wireMock.stubFor(get(urlEqualTo(base + "&cursor=c2"))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C002\",\"name\":\"beta\"}"
                        + "],\"response_metadata\":{\"next_cursor\":\"c3\"}}")));
        wireMock.stubFor(get(urlEqualTo(base + "&cursor=c3"))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C003\",\"name\":\"gamma\"}"
                        + "]}")));

        final List<DiscoveredTarget> result = client.listChannels("xoxb-test-token");

        assertThat(result).hasSize(3);
        assertThat(result).extracting(DiscoveredTarget::id).containsExactly("C001", "C002", "C003");
    }

    @Test
    void listChannels_cursorPresentInUrl_onlyOnSubsequentRequests() {
        // Use a realistic Slack cursor with '=' padding to verify URLEncoder.encode() is applied.
        // Without encoding, the client sends &cursor=dXNlcjpVMEc5V0ZYNlo= (stub won't match).
        final String page1Url = "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true";
        final String rawCursor = "dXNlcjpVMEc5V0ZYNlo=";
        final String encodedCursor = "dXNlcjpVMEc5V0ZYNlo%3D";
        final String page2Url = page1Url + "&cursor=" + encodedCursor;

        wireMock.stubFor(get(urlEqualTo(page1Url))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C001\",\"name\":\"general\"}"
                        + "],\"response_metadata\":{\"next_cursor\":\"" + rawCursor + "\"}}")));
        wireMock.stubFor(get(urlEqualTo(page2Url))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C002\",\"name\":\"random\"}"
                        + "]}")));

        client.listChannels("xoxb-test-token");

        wireMock.verify(1, getRequestedFor(urlEqualTo(page1Url)));
        wireMock.verify(1, getRequestedFor(urlEqualTo(page2Url)));
    }

    @Test
    void listChannels_withCursor_paginatesWithoutWarning() {
        final String page1Url = "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true";
        final String page2Url = page1Url + "&cursor=page2-cursor";

        wireMock.stubFor(get(urlEqualTo(page1Url))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C001\",\"name\":\"general\"}"
                        + "],\"response_metadata\":{\"next_cursor\":\"page2-cursor\"}}")));
        wireMock.stubFor(get(urlEqualTo(page2Url))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C002\",\"name\":\"random\"}"
                        + "]}")));

        final List<LogRecord> warnings = captureWarnings(() -> client.listChannels("xoxb-test-token"));

        wireMock.verify(2, getRequestedFor(urlMatching("/api/conversations.list.*")));
        assertThat(warnings).isEmpty();
    }

    @Test
    void listChannels_midLoopApiError_returnsPartialWithWarning() {
        final String page1Url = "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true";
        final String page2Url = page1Url + "&cursor=next-cursor";

        wireMock.stubFor(get(urlEqualTo(page1Url))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C001\",\"name\":\"general\"}"
                        + "],\"response_metadata\":{\"next_cursor\":\"next-cursor\"}}")));
        wireMock.stubFor(get(urlEqualTo(page2Url))
                .willReturn(okJson("{\"ok\":false,\"error\":\"api_error\"}")));

        final AtomicReference<List<DiscoveredTarget>> result = new AtomicReference<>();
        final List<LogRecord> warnings = captureWarnings(
                () -> result.set(client.listChannels("xoxb-test-token")));

        wireMock.verify(1, getRequestedFor(urlEqualTo(page2Url)));
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).id()).isEqualTo("C001");
        assertThat(warnings).anyMatch(r -> r.getMessage().contains("api_error"));
    }

    @Test
    void listChannels_midLoopRateLimited_returnsPartialWithRatelimitedWarning() {
        final String page1Url = "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true";
        final String page2Url = page1Url + "&cursor=next-cursor";

        wireMock.stubFor(get(urlEqualTo(page1Url))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C001\",\"name\":\"general\"}"
                        + "],\"response_metadata\":{\"next_cursor\":\"next-cursor\"}}")));
        wireMock.stubFor(get(urlEqualTo(page2Url))
                .willReturn(okJson("{\"ok\":false,\"error\":\"ratelimited\"}")));

        final AtomicReference<List<DiscoveredTarget>> result = new AtomicReference<>();
        final List<LogRecord> warnings = captureWarnings(
                () -> result.set(client.listChannels("xoxb-test-token")));

        wireMock.verify(1, getRequestedFor(urlEqualTo(page2Url)));
        assertThat(result.get()).hasSize(1);
        assertThat(warnings).anyMatch(r -> r.getMessage().contains("ratelimited"));
    }

    @Test
    void listChannels_pageCapReached_returnsAccumulatedWithWarning() {
        wireMock.stubFor(get(urlMatching("/api/conversations.list.*"))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C001\",\"name\":\"cap-test\"}"
                        + "],\"response_metadata\":{\"next_cursor\":\"always-cursor\"}}")));

        final AtomicReference<List<DiscoveredTarget>> result = new AtomicReference<>();
        final List<LogRecord> warnings = captureWarnings(
                () -> result.set(client.listChannels("xoxb-token")));

        wireMock.verify(50, getRequestedFor(urlMatching("/api/conversations.list.*")));
        assertThat(result.get()).hasSize(50);
        assertThat(warnings).anyMatch(r -> r.getMessage().contains("capped"));
    }

    @Test
    void listChannels_responseIsNotTruncated_noWarningLogged() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true"))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C123ABC\",\"name\":\"general\"}"
                        + "]}")));

        final List<LogRecord> warnings = captureWarnings(() -> client.listChannels("xoxb-test-token"));

        assertThat(warnings).isEmpty();
    }

    @Test
    void listChannels_responseMetaPresentButNoCursor_noWarningLogged() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/conversations.list?types=public_channel,private_channel&limit=200&include_num_members=true"))
                .willReturn(okJson("{\"ok\":true,\"channels\":["
                        + "{\"id\":\"C123ABC\",\"name\":\"general\"}"
                        + "],\"response_metadata\":{}}")));

        final List<LogRecord> warnings = captureWarnings(() -> client.listChannels("xoxb-test-token"));

        assertThat(warnings).isEmpty();
    }

    private List<LogRecord> captureWarnings(final Runnable action) {
        final Logger logger = Logger.getLogger(SlackBotClient.class.getName());
        final List<LogRecord> records = new ArrayList<>();
        final Handler handler = new Handler() {
            @Override public void publish(final LogRecord r) { if (r.getLevel() == Level.WARNING) records.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);
        try { action.run(); } finally { logger.removeHandler(handler); }
        return records;
    }

    // ── listConversations ─────────────────────────────────────────────────────

    @Test
    void listConversations_returnsFullDetail() {
        wireMock.stubFor(get(urlMatching("/api/conversations\\.list.*"))
                .willReturn(okJson("""
                        {"ok":true,"channels":[
                          {"id":"C1","name":"general","topic":{"value":"Main"},"purpose":{"value":"General discussion"},"is_private":false},
                          {"id":"C2","name":"secret","topic":{"value":""},"purpose":{"value":"Private stuff"},"is_private":true}
                        ],"response_metadata":{"next_cursor":""}}
                        """)));

        List<SlackBotClient.ConversationInfo> convos = client.listConversations("tok");

        assertThat(convos).hasSize(2);
        assertThat(convos.get(0).id()).isEqualTo("C1");
        assertThat(convos.get(0).name()).isEqualTo("general");
        assertThat(convos.get(0).topic()).isEqualTo("Main");
        assertThat(convos.get(0).purpose()).isEqualTo("General discussion");
        assertThat(convos.get(0).isPrivate()).isFalse();
        assertThat(convos.get(1).isPrivate()).isTrue();
    }

    @Test
    void listChannels_delegatesToListConversations() {
        wireMock.stubFor(get(urlMatching("/api/conversations\\.list.*"))
                .willReturn(okJson("""
                        {"ok":true,"channels":[
                          {"id":"C1","name":"general","topic":{"value":""},"purpose":{"value":""},"is_private":false}
                        ],"response_metadata":{"next_cursor":""}}
                        """)));

        List<DiscoveredTarget> targets = client.listChannels("tok");

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).id()).isEqualTo("C1");
        assertThat(targets.get(0).displayName()).isEqualTo("#general");
    }

    // ── Reaction methods ──────────────────────────────────────────────────────

    @Test
    void addReaction_success() {
        wireMock.stubFor(post(urlEqualTo("/api/reactions.add"))
                .willReturn(okJson("{\"ok\":true}")));

        ApiResult result = client.addReaction("tok", "C1", "1234567890.123456", "thumbsup");

        assertThat(result.ok()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/api/reactions.add"))
                .withRequestBody(matchingJsonPath("$.channel", equalTo("C1")))
                .withRequestBody(matchingJsonPath("$.timestamp", equalTo("1234567890.123456")))
                .withRequestBody(matchingJsonPath("$.name", equalTo("thumbsup"))));
    }

    @Test
    void removeReaction_success() {
        wireMock.stubFor(post(urlEqualTo("/api/reactions.remove"))
                .willReturn(okJson("{\"ok\":true}")));

        ApiResult result = client.removeReaction("tok", "C1", "1234567890.123456", "thumbsup");

        assertThat(result.ok()).isTrue();
    }

    @Test
    void getReactions_success() {
        wireMock.stubFor(get(urlMatching("/api/reactions\\.get.*"))
                .willReturn(okJson("""
                        {"ok":true,"message":{"reactions":[
                          {"name":"thumbsup","count":3},
                          {"name":"heart","count":1}
                        ]}}
                        """)));

        SlackBotClient.ReactionListResult result = client.getReactions("tok", "C1", "1234567890.123456");

        assertThat(result.ok()).isTrue();
        assertThat(result.emojis()).containsExactly("thumbsup", "heart");
    }

    @Test
    void addReaction_apiError() {
        wireMock.stubFor(post(urlEqualTo("/api/reactions.add"))
                .willReturn(okJson("{\"ok\":false,\"error\":\"already_reacted\"}")));

        ApiResult result = client.addReaction("tok", "C1", "ts", "thumbsup");

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("already_reacted");
    }

    // ── Presence, members, users ──────────────────────────────────────────────

    @Test
    void getPresence_active() {
        wireMock.stubFor(get(urlMatching("/api/users\\.getPresence.*"))
                .willReturn(okJson("{\"ok\":true,\"presence\":\"active\"}")));

        SlackBotClient.PresenceResult result = client.getPresence("tok", "U123");

        assertThat(result.ok()).isTrue();
        assertThat(result.presence()).isEqualTo("active");
    }

    @Test
    void listConversationMembers_paginates() {
        wireMock.stubFor(get(urlMatching("/api/conversations\\.members\\?channel=C1&limit=200$"))
                .willReturn(okJson("""
                        {"ok":true,"members":["U1","U2"],"response_metadata":{"next_cursor":"page2"}}
                        """)));
        wireMock.stubFor(get(urlMatching("/api/conversations\\.members\\?channel=C1&limit=200&cursor=page2"))
                .willReturn(okJson("""
                        {"ok":true,"members":["U3"],"response_metadata":{"next_cursor":""}}
                        """)));

        List<String> members = client.listConversationMembers("tok", "C1");

        assertThat(members).containsExactly("U1", "U2", "U3");
    }

    @Test
    void listUsers_returnsDisplayNames() {
        wireMock.stubFor(get(urlMatching("/api/users\\.list.*"))
                .willReturn(okJson("""
                        {"ok":true,"members":[
                          {"id":"U1","profile":{"display_name":"Alice","real_name":"Alice Smith"}},
                          {"id":"U2","profile":{"display_name":"","real_name":"Bob Jones"}}
                        ],"response_metadata":{"next_cursor":""}}
                        """)));

        List<SlackBotClient.UserInfo> users = client.listUsers("tok");

        assertThat(users).hasSize(2);
        assertThat(users.get(0).displayName()).isEqualTo("Alice");
        assertThat(users.get(1).displayName()).isEmpty();
        assertThat(users.get(1).realName()).isEqualTo("Bob Jones");
    }

    // ── Channel management + member management ───────────────────────────────

    @Test
    void createConversation_success() {
        wireMock.stubFor(post(urlEqualTo("/api/conversations.create"))
                .willReturn(okJson("""
                        {"ok":true,"channel":{"id":"C99","name":"new-chan","topic":{"value":""},"purpose":{"value":""},"is_private":false}}
                        """)));

        SlackBotClient.ConversationResult result = client.createConversation("tok", "new-chan", false);

        assertThat(result.ok()).isTrue();
        assertThat(result.info().id()).isEqualTo("C99");
        assertThat(result.info().name()).isEqualTo("new-chan");
    }

    @Test
    void getConversationInfo_success() {
        wireMock.stubFor(get(urlMatching("/api/conversations\\.info.*"))
                .willReturn(okJson("""
                        {"ok":true,"channel":{"id":"C1","name":"general","topic":{"value":"Main topic"},"purpose":{"value":"General chat"},"is_private":false}}
                        """)));

        SlackBotClient.ConversationResult result = client.getConversationInfo("tok", "C1");

        assertThat(result.ok()).isTrue();
        assertThat(result.info().topic()).isEqualTo("Main topic");
    }

    @Test
    void inviteToConversation_success() {
        wireMock.stubFor(post(urlEqualTo("/api/conversations.invite"))
                .willReturn(okJson("{\"ok\":true}")));

        ApiResult result = client.inviteToConversation("tok", "C1", "U1");

        assertThat(result.ok()).isTrue();
    }

    @Test
    void kickFromConversation_success() {
        wireMock.stubFor(post(urlEqualTo("/api/conversations.kick"))
                .willReturn(okJson("{\"ok\":true}")));

        ApiResult result = client.kickFromConversation("tok", "C1", "U1");

        assertThat(result.ok()).isTrue();
    }

    // ── Message history ───────────────────────────────────────────────────────

    @Test
    void getHistory_success() {
        wireMock.stubFor(get(urlMatching("/api/conversations\\.history.*"))
                .willReturn(okJson("""
                        {"ok":true,"messages":[
                          {"ts":"1234567890.123456","user":"U1","text":"Hello","thread_ts":null},
                          {"ts":"1234567891.654321","user":"U2","text":"Reply","thread_ts":"1234567890.123456"}
                        ]}
                        """)));

        SlackBotClient.HistoryResult result = client.getHistory("tok", "C1", "1234567889.000000", 100);

        assertThat(result.ok()).isTrue();
        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(0).ts()).isEqualTo("1234567890.123456");
        assertThat(result.messages().get(0).text()).isEqualTo("Hello");
        assertThat(result.messages().get(1).threadTs()).isEqualTo("1234567890.123456");
    }

    @Test
    void getHistory_apiError() {
        wireMock.stubFor(get(urlMatching("/api/conversations\\.history.*"))
                .willReturn(okJson("{\"ok\":false,\"error\":\"channel_not_found\"}")));

        SlackBotClient.HistoryResult result = client.getHistory("tok", "C1", "0", 100);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("channel_not_found");
    }

    // ── Block Kit support ─────────────────────────────────────────────────────

    @Test
    void postMessageWithBlocks() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .willReturn(okJson("{\"ok\":true,\"ts\":\"1234567890.123456\"}")));

        final String blocksJson = "[{\"type\":\"header\",\"text\":{\"type\":\"plain_text\",\"text\":\"Title\"}}]";
        final var result = client.postMessage("xoxb-test", "C123", "fallback", null, blocksJson);

        assertThat(result.ok()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/api/chat.postMessage"))
                .withRequestBody(matchingJsonPath("$.blocks")));
    }

    @Test
    void postMessageWithoutBlocksDelegates() {
        wireMock.stubFor(post(urlEqualTo("/api/chat.postMessage"))
                .willReturn(okJson("{\"ok\":true,\"ts\":\"1234567890.123456\"}")));

        final var result = client.postMessage("xoxb-test", "C123", "hello", null);

        assertThat(result.ok()).isTrue();
        wireMock.verify(postRequestedFor(urlEqualTo("/api/chat.postMessage"))
                .withRequestBody(not(matchingJsonPath("$.blocks"))));
    }

    @Test
    void listConversationsParsesMemberCount() {
        wireMock.stubFor(get(urlMatching("/api/conversations\\.list.*"))
                .willReturn(okJson("{\"ok\":true,\"channels\":[{\"id\":\"C1\",\"name\":\"general\","
                        + "\"topic\":{\"value\":\"t\"},\"purpose\":{\"value\":\"p\"},"
                        + "\"is_private\":false,\"num_members\":42}],"
                        + "\"response_metadata\":{\"next_cursor\":\"\"}}")));

        final var result = client.listConversations("xoxb-test");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().numMembers()).isEqualTo(42);
    }
}
