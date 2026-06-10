package io.casehub.connectors.slack.bot;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehub.connectors.DiscoveredTarget;
import io.casehub.connectors.http.HttpHelper;

/**
 * Pure-HTTP client for the Slack Web API.
 *
 * <p>Calls {@code chat.postMessage} with a bot token ({@code xoxb-…}).
 * Uses {@code java.net.http.HttpClient} — no Slack SDK dependency.
 * Shares {@link HttpHelper#CLIENT} (5 s connect timeout) with other connectors.
 *
 * <p>On HTTP 429, reads {@code Retry-After}, sleeps, and retries once.
 * Sleep is safe on virtual threads (no carrier-thread starvation).
 * Cap the {@code Retry-After} externally if high-frequency rate limiting becomes a concern.
 *
 * <p>{@code apiBaseUrl} is package-private to allow direct field injection in unit tests,
 * mirroring the {@code SlackInboundConnector.signingSecret} pattern.
 *
 * <p>Consumed by {@code SlackChannelBackend} in {@code casehub-qhorus-slack-channel}.
 */
@ApplicationScoped
public class SlackBotClient {

    public static final String ID = "slack-bot";

    private static final Logger LOG = Logger.getLogger(SlackBotClient.class.getName());
    private static final String API_PATH = "/api/chat.postMessage";
    private static final String LIST_PATH = "/api/conversations.list";
    private static final String LIST_BASE_QUERY = "?types=public_channel,private_channel&limit=200";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_PAGES = 50;

    /** Override in tests by setting this field directly before use. */
    @ConfigProperty(name = "casehub.connectors.slack-bot.api-base-url",
                    defaultValue = "https://slack.com")
    String apiBaseUrl;

    /**
     * Posts a message to a Slack channel.
     *
     * @param token     bot token ({@code xoxb-…})
     * @param channelId Slack channel ID (e.g. {@code C123ABC})
     * @param text      message text
     * @param threadTs  thread root {@code ts} for replies, or {@code null} for new top-level messages
     * @return the result of the API call
     */
    public PostResult postMessage(final String token, final String channelId,
                                  final String text, final String threadTs) {
        final String json = buildPayload(channelId, text, threadTs);
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + API_PATH))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return sendWithRetry(request);
    }

    /**
     * Lists all channels accessible to the bot, following cursor pagination until exhausted.
     *
     * <p>Fetches up to {@value MAX_PAGES} pages (10,000 channels). A WARNING is logged if the
     * cap is hit, if a mid-loop error occurs, or if the request is interrupted — in all cases
     * the partial result accumulated so far is returned rather than an empty list.
     *
     * <p>Rate limiting (HTTP 429 mid-loop) is not retried; it surfaces as a WARNING with
     * {@code "ratelimited"} as the error. Full retry handling is a known deferred gap.
     *
     * @param token bot token ({@code xoxb-…})
     * @return list of discovered targets; empty if the first request fails
     */
    public List<DiscoveredTarget> listChannels(final String token) {
        final List<DiscoveredTarget> accumulated = new ArrayList<>();
        String cursor = "";
        int pageNum = 0;
        while (pageNum < MAX_PAGES) {
            final String query = cursor.isBlank()
                    ? LIST_BASE_QUERY
                    : LIST_BASE_QUERY + "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
            try {
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiBaseUrl + LIST_PATH + query))
                        .header("Authorization", "Bearer " + token)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();
                final HttpResponse<String> response =
                        HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                final PageResult result = parsePage(response.body());
                if (!result.ok()) {
                    LOG.warning(String.format(
                            "SlackBotClient: listChannels stopped after %d complete page(s) — returned %d channels: %s",
                            pageNum, accumulated.size(), result.error()));
                    return List.copyOf(accumulated);
                }
                accumulated.addAll(result.channels());
                pageNum++;
                cursor = result.nextCursor();
                if (cursor.isBlank()) {
                    break;
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warning(String.format(
                        "SlackBotClient: listChannels interrupted after %d complete page(s) — returned %d channels",
                        pageNum, accumulated.size()));
                return List.copyOf(accumulated);
            } catch (final Exception e) {
                LOG.warning(String.format(
                        "SlackBotClient: listChannels error after %d complete page(s) — returned %d channels: %s",
                        pageNum, accumulated.size(), e.getMessage()));
                return List.copyOf(accumulated);
            }
        }
        if (!cursor.isBlank()) {
            LOG.warning(String.format(
                    "SlackBotClient: listChannels capped at %d pages — returned %d channels (workspace may have more)",
                    MAX_PAGES, accumulated.size()));
        }
        return List.copyOf(accumulated);
    }

    private PageResult parsePage(final String body) {
        if (body == null || body.isBlank()) {
            return new PageResult(false, List.of(), "", "empty-response");
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            final JsonObject obj = reader.readObject();
            if (!obj.getBoolean("ok", false)) {
                return new PageResult(false, List.of(), "", obj.getString("error", ""));
            }
            final String nextCursor = obj.containsKey("response_metadata")
                    ? obj.getJsonObject("response_metadata").getString("next_cursor", "")
                    : "";
            final List<DiscoveredTarget> channels = obj.getJsonArray("channels").stream()
                    .map(JsonValue::asJsonObject)
                    .map(ch -> new DiscoveredTarget(ch.getString("id"), "#" + ch.getString("name")))
                    .toList();
            return new PageResult(true, channels, nextCursor, "");
        } catch (final Exception e) {
            return new PageResult(false, List.of(), "", "parse-error");
        }
    }

    private PostResult sendWithRetry(final HttpRequest request) {
        try {
            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                final String retryAfter = response.headers()
                        .firstValue("Retry-After").orElse("1");
                final long seconds = parseLongSafe(retryAfter);
                LOG.warning("SlackBotClient: rate limited by Slack — retrying after " + seconds + "s");
                if (seconds > 0) {
                    Thread.sleep(seconds * 1_000);
                }
                final HttpResponse<String> retry =
                        HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                return parseResponse(retry.body());
            }

            return parseResponse(response.body());

        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PostResult(false, null, "interrupted");
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: HTTP error — " + e.getMessage());
            return new PostResult(false, null, "http-error");
        }
    }

    private static PostResult parseResponse(final String body) {
        if (body == null || body.isBlank()) {
            return new PostResult(false, null, "empty-response");
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            final JsonObject json = reader.readObject();
            final boolean ok = json.getBoolean("ok", false);
            final String ts = ok ? json.getString("ts", null) : null;
            final String error = !ok ? json.getString("error", null) : null;
            return new PostResult(ok, ts, error);
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: failed to parse API response — " + e.getMessage());
            return new PostResult(false, null, "parse-error");
        }
    }

    private static String buildPayload(final String channelId, final String text,
                                       final String threadTs) {
        final JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("channel", channelId)
                .add("text", text);
        if (threadTs != null) {
            builder.add("thread_ts", threadTs);
        }
        return builder.build().toString();
    }

    /** Falls back to 1 s on null or non-numeric {@code Retry-After} values. */
    private static long parseLongSafe(final String value) {
        if (value == null) return 1L;
        try {
            return Long.parseLong(value.trim());
        } catch (final NumberFormatException e) {
            return 1L;
        }
    }

    private record PageResult(boolean ok, List<DiscoveredTarget> channels, String nextCursor, String error) {}

    public record PostResult(boolean ok, String ts, String error) {}
}
