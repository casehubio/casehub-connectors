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
    private static final String LIST_BASE_QUERY = "?types=public_channel,private_channel&limit=200&include_num_members=true";
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
        return postMessage(token, channelId, text, threadTs, null);
    }

    /**
     * Posts a message to a Slack channel with optional Block Kit blocks.
     *
     * @param token      bot token ({@code xoxb-…})
     * @param channelId  Slack channel ID (e.g. {@code C123ABC})
     * @param text       message text (fallback text when blocks are provided)
     * @param threadTs   thread root {@code ts} for replies, or {@code null} for new top-level messages
     * @param blocksJson Block Kit blocks as JSON array string, or {@code null}
     * @return the result of the API call
     */
    public PostResult postMessage(final String token, final String channelId,
                                  final String text, final String threadTs,
                                  final String blocksJson) {
        final String json = buildPayload(channelId, text, threadTs, blocksJson);
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
     * Lists all conversations accessible to the bot with full metadata, following cursor pagination until exhausted.
     *
     * <p>Fetches up to {@value MAX_PAGES} pages (10,000 channels). A WARNING is logged if the
     * cap is hit, if a mid-loop error occurs, or if the request is interrupted — in all cases
     * the partial result accumulated so far is returned rather than an empty list.
     *
     * <p>Rate limiting (HTTP 429 mid-loop) is not retried; it surfaces as a WARNING with
     * {@code "ratelimited"} as the error. Full retry handling is a known deferred gap.
     *
     * @param token bot token ({@code xoxb-…})
     * @return list of conversations with full detail; empty if the first request fails
     */
    public List<ConversationInfo> listConversations(final String token) {
        final List<ConversationInfo> accumulated = new ArrayList<>();
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
                final ConversationPageResult result = parseConversationPage(response.body());
                if (!result.ok()) {
                    LOG.warning(String.format(
                            "SlackBotClient: listConversations stopped after %d complete page(s) — returned %d channels: %s",
                            pageNum, accumulated.size(), result.error()));
                    return List.copyOf(accumulated);
                }
                accumulated.addAll(result.conversations());
                pageNum++;
                cursor = result.nextCursor();
                if (cursor.isBlank()) {
                    break;
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warning(String.format(
                        "SlackBotClient: listConversations interrupted after %d complete page(s) — returned %d channels",
                        pageNum, accumulated.size()));
                return List.copyOf(accumulated);
            } catch (final Exception e) {
                LOG.warning(String.format(
                        "SlackBotClient: listConversations error after %d complete page(s) — returned %d channels: %s",
                        pageNum, accumulated.size(), e.getMessage()));
                return List.copyOf(accumulated);
            }
        }
        if (!cursor.isBlank()) {
            LOG.warning(String.format(
                    "SlackBotClient: listConversations capped at %d pages — returned %d channels (workspace may have more)",
                    MAX_PAGES, accumulated.size()));
        }
        return List.copyOf(accumulated);
    }

    /**
     * Lists all channels accessible to the bot, following cursor pagination until exhausted.
     *
     * <p>Delegates to {@link #listConversations(String)} and maps to {@link DiscoveredTarget}.
     *
     * @param token bot token ({@code xoxb-…})
     * @return list of discovered targets; empty if the first request fails
     */
    public List<DiscoveredTarget> listChannels(final String token) {
        return listConversations(token).stream()
                .map(c -> new DiscoveredTarget(c.id(), "#" + c.name()))
                .toList();
    }

    /**
     * Adds a reaction emoji to a message.
     *
     * @param token     bot token ({@code xoxb-…})
     * @param channel   Slack channel ID
     * @param timestamp message timestamp (e.g. {@code 1234567890.123456})
     * @param emoji     emoji name without colons (e.g. {@code thumbsup})
     * @return the result of the API call
     */
    public ReactionResult addReaction(final String token, final String channel,
                                      final String timestamp, final String emoji) {
        final String json = Json.createObjectBuilder()
                .add("channel", channel)
                .add("timestamp", timestamp)
                .add("name", emoji)
                .build().toString();
        return sendReactionRequest(token, "/api/reactions.add", json);
    }

    /**
     * Removes a reaction emoji from a message.
     *
     * @param token     bot token ({@code xoxb-…})
     * @param channel   Slack channel ID
     * @param timestamp message timestamp
     * @param emoji     emoji name without colons
     * @return the result of the API call
     */
    public ReactionResult removeReaction(final String token, final String channel,
                                         final String timestamp, final String emoji) {
        final String json = Json.createObjectBuilder()
                .add("channel", channel)
                .add("timestamp", timestamp)
                .add("name", emoji)
                .build().toString();
        return sendReactionRequest(token, "/api/reactions.remove", json);
    }

    /**
     * Gets all reactions on a message.
     *
     * @param token     bot token ({@code xoxb-…})
     * @param channel   Slack channel ID
     * @param timestamp message timestamp
     * @return list of emoji names
     */
    public ReactionListResult getReactions(final String token, final String channel,
                                           final String timestamp) {
        final String url = apiBaseUrl + "/api/reactions.get?channel="
                + URLEncoder.encode(channel, StandardCharsets.UTF_8)
                + "&timestamp=" + URLEncoder.encode(timestamp, StandardCharsets.UTF_8)
                + "&full=true";
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return parseReactionListResponse(response.body());
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: getReactions error — " + e.getMessage());
            return new ReactionListResult(false, List.of(), "http-error");
        }
    }

    private ReactionResult sendReactionRequest(final String token, final String path, final String json) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return parseReactionResponse(response.body());
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: reaction request error — " + e.getMessage());
            return new ReactionResult(false, "http-error");
        }
    }

    private ReactionResult parseReactionResponse(final String body) {
        if (body == null || body.isBlank()) {
            return new ReactionResult(false, "empty-response");
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            final JsonObject json = reader.readObject();
            final boolean ok = json.getBoolean("ok", false);
            final String error = !ok ? json.getString("error", null) : null;
            return new ReactionResult(ok, error);
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: failed to parse reaction response — " + e.getMessage());
            return new ReactionResult(false, "parse-error");
        }
    }

    private ReactionListResult parseReactionListResponse(final String body) {
        if (body == null || body.isBlank()) {
            return new ReactionListResult(false, List.of(), "empty-response");
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            final JsonObject json = reader.readObject();
            final boolean ok = json.getBoolean("ok", false);
            if (!ok) {
                return new ReactionListResult(false, List.of(), json.getString("error", ""));
            }
            final List<String> emojis = json.containsKey("message")
                    && json.getJsonObject("message").containsKey("reactions")
                    ? json.getJsonObject("message").getJsonArray("reactions").stream()
                            .map(JsonValue::asJsonObject)
                            .map(r -> r.getString("name"))
                            .toList()
                    : List.of();
            return new ReactionListResult(true, emojis, null);
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: failed to parse reaction list response — " + e.getMessage());
            return new ReactionListResult(false, List.of(), "parse-error");
        }
    }

    /**
     * Gets the presence status of a user.
     *
     * @param token  bot token ({@code xoxb-…})
     * @param userId Slack user ID
     * @return presence status ({@code active} or {@code away})
     */
    public PresenceResult getPresence(final String token, final String userId) {
        final String url = apiBaseUrl + "/api/users.getPresence?user="
                + URLEncoder.encode(userId, StandardCharsets.UTF_8);
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return parsePresenceResponse(response.body());
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: getPresence error — " + e.getMessage());
            return new PresenceResult(false, null, "http-error");
        }
    }

    /**
     * Lists all members of a conversation, following cursor pagination.
     *
     * @param token     bot token ({@code xoxb-…})
     * @param channelId Slack channel ID
     * @return list of user IDs
     */
    public List<String> listConversationMembers(final String token, final String channelId) {
        final List<String> accumulated = new ArrayList<>();
        String cursor = "";
        int pageNum = 0;
        while (pageNum < MAX_PAGES) {
            final String url = apiBaseUrl + "/api/conversations.members?channel="
                    + URLEncoder.encode(channelId, StandardCharsets.UTF_8)
                    + "&limit=200"
                    + (cursor.isBlank() ? "" : "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
            try {
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();
                final HttpResponse<String> response =
                        HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                final MembersPageResult result = parseMembersPage(response.body());
                if (!result.ok()) {
                    LOG.warning(String.format(
                            "SlackBotClient: listConversationMembers stopped after %d complete page(s) — returned %d members: %s",
                            pageNum, accumulated.size(), result.error()));
                    return List.copyOf(accumulated);
                }
                accumulated.addAll(result.members());
                pageNum++;
                cursor = result.nextCursor();
                if (cursor.isBlank()) {
                    break;
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warning(String.format(
                        "SlackBotClient: listConversationMembers interrupted after %d complete page(s) — returned %d members",
                        pageNum, accumulated.size()));
                return List.copyOf(accumulated);
            } catch (final Exception e) {
                LOG.warning(String.format(
                        "SlackBotClient: listConversationMembers error after %d complete page(s) — returned %d members: %s",
                        pageNum, accumulated.size(), e.getMessage()));
                return List.copyOf(accumulated);
            }
        }
        if (!cursor.isBlank()) {
            LOG.warning(String.format(
                    "SlackBotClient: listConversationMembers capped at %d pages — returned %d members",
                    MAX_PAGES, accumulated.size()));
        }
        return List.copyOf(accumulated);
    }

    /**
     * Lists all users in the workspace, following cursor pagination.
     *
     * @param token bot token ({@code xoxb-…})
     * @return list of user info
     */
    public List<UserInfo> listUsers(final String token) {
        final List<UserInfo> accumulated = new ArrayList<>();
        String cursor = "";
        int pageNum = 0;
        while (pageNum < MAX_PAGES) {
            final String url = apiBaseUrl + "/api/users.list?limit=200"
                    + (cursor.isBlank() ? "" : "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8));
            try {
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();
                final HttpResponse<String> response =
                        HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                final UsersPageResult result = parseUsersPage(response.body());
                if (!result.ok()) {
                    LOG.warning(String.format(
                            "SlackBotClient: listUsers stopped after %d complete page(s) — returned %d users: %s",
                            pageNum, accumulated.size(), result.error()));
                    return List.copyOf(accumulated);
                }
                accumulated.addAll(result.users());
                pageNum++;
                cursor = result.nextCursor();
                if (cursor.isBlank()) {
                    break;
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warning(String.format(
                        "SlackBotClient: listUsers interrupted after %d complete page(s) — returned %d users",
                        pageNum, accumulated.size()));
                return List.copyOf(accumulated);
            } catch (final Exception e) {
                LOG.warning(String.format(
                        "SlackBotClient: listUsers error after %d complete page(s) — returned %d users: %s",
                        pageNum, accumulated.size(), e.getMessage()));
                return List.copyOf(accumulated);
            }
        }
        if (!cursor.isBlank()) {
            LOG.warning(String.format(
                    "SlackBotClient: listUsers capped at %d pages — returned %d users",
                    MAX_PAGES, accumulated.size()));
        }
        return List.copyOf(accumulated);
    }

    private PresenceResult parsePresenceResponse(final String body) {
        if (body == null || body.isBlank()) {
            return new PresenceResult(false, null, "empty-response");
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            final JsonObject json = reader.readObject();
            final boolean ok = json.getBoolean("ok", false);
            if (!ok) {
                return new PresenceResult(false, null, json.getString("error", ""));
            }
            final String presence = json.getString("presence", null);
            return new PresenceResult(true, presence, null);
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: failed to parse presence response — " + e.getMessage());
            return new PresenceResult(false, null, "parse-error");
        }
    }

    private MembersPageResult parseMembersPage(final String body) {
        if (body == null || body.isBlank()) {
            return new MembersPageResult(false, List.of(), "", "empty-response");
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            final JsonObject obj = reader.readObject();
            if (!obj.getBoolean("ok", false)) {
                return new MembersPageResult(false, List.of(), "", obj.getString("error", ""));
            }
            final String nextCursor = obj.containsKey("response_metadata")
                    ? obj.getJsonObject("response_metadata").getString("next_cursor", "")
                    : "";
            final List<String> members = obj.getJsonArray("members").stream()
                    .map(v -> v.toString().replace("\"", ""))
                    .toList();
            return new MembersPageResult(true, members, nextCursor, "");
        } catch (final Exception e) {
            return new MembersPageResult(false, List.of(), "", "parse-error");
        }
    }

    private UsersPageResult parseUsersPage(final String body) {
        if (body == null || body.isBlank()) {
            return new UsersPageResult(false, List.of(), "", "empty-response");
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            final JsonObject obj = reader.readObject();
            if (!obj.getBoolean("ok", false)) {
                return new UsersPageResult(false, List.of(), "", obj.getString("error", ""));
            }
            final String nextCursor = obj.containsKey("response_metadata")
                    ? obj.getJsonObject("response_metadata").getString("next_cursor", "")
                    : "";
            final List<UserInfo> users = obj.getJsonArray("members").stream()
                    .map(JsonValue::asJsonObject)
                    .map(u -> {
                        final String id = u.getString("id");
                        final JsonObject profile = u.getJsonObject("profile");
                        final String displayName = profile.getString("display_name", "");
                        final String realName = profile.getString("real_name", "");
                        return new UserInfo(id, displayName, realName);
                    })
                    .toList();
            return new UsersPageResult(true, users, nextCursor, "");
        } catch (final Exception e) {
            return new UsersPageResult(false, List.of(), "", "parse-error");
        }
    }

    /**
     * Creates a new conversation (channel).
     *
     * @param token     bot token ({@code xoxb-…})
     * @param name      channel name
     * @param isPrivate {@code true} for private channels
     * @return conversation result with channel info
     */
    public ConversationResult createConversation(final String token, final String name, final boolean isPrivate) {
        final String json = Json.createObjectBuilder()
                .add("name", name)
                .add("is_private", isPrivate)
                .build().toString();
        return sendConversationRequest(token, "/api/conversations.create", json);
    }

    /**
     * Gets information about a conversation.
     *
     * @param token     bot token ({@code xoxb-…})
     * @param channelId Slack channel ID
     * @return conversation result with channel info
     */
    public ConversationResult getConversationInfo(final String token, final String channelId) {
        final String url = apiBaseUrl + "/api/conversations.info?channel="
                + URLEncoder.encode(channelId, StandardCharsets.UTF_8);
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return parseConversationResult(response.body());
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: getConversationInfo error — " + e.getMessage());
            return new ConversationResult(false, null, "http-error");
        }
    }

    /**
     * Invites a user to a conversation.
     *
     * @param token     bot token ({@code xoxb-…})
     * @param channelId Slack channel ID
     * @param userId    Slack user ID
     * @return reaction result
     */
    public ReactionResult inviteToConversation(final String token, final String channelId, final String userId) {
        final String json = Json.createObjectBuilder()
                .add("channel", channelId)
                .add("users", userId)
                .build().toString();
        return sendSimpleRequest(token, "/api/conversations.invite", json);
    }

    /**
     * Removes a user from a conversation.
     *
     * @param token     bot token ({@code xoxb-…})
     * @param channelId Slack channel ID
     * @param userId    Slack user ID
     * @return reaction result
     */
    public ReactionResult kickFromConversation(final String token, final String channelId, final String userId) {
        final String json = Json.createObjectBuilder()
                .add("channel", channelId)
                .add("user", userId)
                .build().toString();
        return sendSimpleRequest(token, "/api/conversations.kick", json);
    }

    private ConversationResult sendConversationRequest(final String token, final String path, final String json) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return parseConversationResult(response.body());
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: conversation request error — " + e.getMessage());
            return new ConversationResult(false, null, "http-error");
        }
    }

    private ReactionResult sendSimpleRequest(final String token, final String path, final String json) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return parseReactionResponse(response.body());
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: simple request error — " + e.getMessage());
            return new ReactionResult(false, "http-error");
        }
    }

    /**
     * Gets message history from a conversation.
     *
     * <p>This is NOT a paginating method — it returns up to {@code limit} messages in a single call.
     *
     * @param token     bot token ({@code xoxb-…})
     * @param channelId Slack channel ID
     * @param oldest    timestamp (e.g. {@code 1234567889.000000})
     * @param limit     max messages to return (up to 1000)
     * @return history result with messages
     */
    public HistoryResult getHistory(final String token, final String channelId,
                                     final String oldest, final int limit) {
        final String url = apiBaseUrl + "/api/conversations.history?channel="
                + URLEncoder.encode(channelId, StandardCharsets.UTF_8)
                + "&oldest=" + URLEncoder.encode(oldest, StandardCharsets.UTF_8)
                + "&limit=" + limit;
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return parseHistoryResult(response.body());
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: getHistory error — " + e.getMessage());
            return new HistoryResult(false, List.of(), "http-error");
        }
    }

    private ConversationResult parseConversationResult(final String body) {
        if (body == null || body.isBlank()) {
            return new ConversationResult(false, null, "empty-response");
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            final JsonObject json = reader.readObject();
            final boolean ok = json.getBoolean("ok", false);
            if (!ok) {
                return new ConversationResult(false, null, json.getString("error", ""));
            }
            final JsonObject ch = json.getJsonObject("channel");
            final String id = ch.getString("id");
            final String name = ch.getString("name");
            final String topic = ch.containsKey("topic")
                    ? ch.getJsonObject("topic").getString("value", "")
                    : "";
            final String purpose = ch.containsKey("purpose")
                    ? ch.getJsonObject("purpose").getString("value", "")
                    : "";
            final boolean isPrivate = ch.getBoolean("is_private", false);
            final Integer numMembers = ch.containsKey("num_members")
                    ? ch.getInt("num_members") : null;
            return new ConversationResult(true, new ConversationInfo(id, name, topic, purpose, isPrivate, numMembers), null);
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: failed to parse conversation result — " + e.getMessage());
            return new ConversationResult(false, null, "parse-error");
        }
    }

    private HistoryResult parseHistoryResult(final String body) {
        if (body == null || body.isBlank()) {
            return new HistoryResult(false, List.of(), "empty-response");
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            final JsonObject json = reader.readObject();
            final boolean ok = json.getBoolean("ok", false);
            if (!ok) {
                return new HistoryResult(false, List.of(), json.getString("error", ""));
            }
            final List<HistoryMessage> messages = json.getJsonArray("messages").stream()
                    .map(JsonValue::asJsonObject)
                    .map(m -> {
                        final String ts = m.getString("ts", "");
                        final String user = m.getString("user", "");
                        final String text = m.getString("text", "");
                        final String threadTs = m.containsKey("thread_ts") && !m.isNull("thread_ts")
                                ? m.getString("thread_ts") : null;
                        return new HistoryMessage(ts, user, text, threadTs);
                    })
                    .toList();
            return new HistoryResult(true, messages, null);
        } catch (final Exception e) {
            LOG.warning("SlackBotClient: failed to parse history result — " + e.getMessage());
            return new HistoryResult(false, List.of(), "parse-error");
        }
    }

    private ConversationPageResult parseConversationPage(final String body) {
        if (body == null || body.isBlank()) {
            return new ConversationPageResult(false, List.of(), "", "empty-response");
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            final JsonObject obj = reader.readObject();
            if (!obj.getBoolean("ok", false)) {
                return new ConversationPageResult(false, List.of(), "", obj.getString("error", ""));
            }
            final String nextCursor = obj.containsKey("response_metadata")
                    ? obj.getJsonObject("response_metadata").getString("next_cursor", "")
                    : "";
            final List<ConversationInfo> conversations = obj.getJsonArray("channels").stream()
                    .map(JsonValue::asJsonObject)
                    .map(ch -> {
                        final String id = ch.getString("id");
                        final String name = ch.getString("name");
                        final String topic = ch.containsKey("topic")
                                ? ch.getJsonObject("topic").getString("value", "")
                                : "";
                        final String purpose = ch.containsKey("purpose")
                                ? ch.getJsonObject("purpose").getString("value", "")
                                : "";
                        final boolean isPrivate = ch.getBoolean("is_private", false);
                        final Integer numMembers = ch.containsKey("num_members")
                                ? ch.getInt("num_members") : null;
                        return new ConversationInfo(id, name, topic, purpose, isPrivate, numMembers);
                    })
                    .toList();
            return new ConversationPageResult(true, conversations, nextCursor, "");
        } catch (final Exception e) {
            return new ConversationPageResult(false, List.of(), "", "parse-error");
        }
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
                                       final String threadTs, final String blocksJson) {
        final JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("channel", channelId)
                .add("text", text);
        if (threadTs != null) {
            builder.add("thread_ts", threadTs);
        }
        if (blocksJson != null) {
            builder.add("blocks", Json.createReader(new StringReader(blocksJson)).readArray());
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
    private record ConversationPageResult(boolean ok, List<ConversationInfo> conversations, String nextCursor, String error) {}
    private record MembersPageResult(boolean ok, List<String> members, String nextCursor, String error) {}
    private record UsersPageResult(boolean ok, List<UserInfo> users, String nextCursor, String error) {}

    public record PostResult(boolean ok, String ts, String error) {}
    public record ConversationInfo(String id, String name, String topic, String purpose, boolean isPrivate, Integer numMembers) {}
    public record ConversationResult(boolean ok, ConversationInfo info, String error) {}
    public record ReactionResult(boolean ok, String error) {}
    public record ReactionListResult(boolean ok, List<String> emojis, String error) {}
    public record PresenceResult(boolean ok, String presence, String error) {}
    public record UserInfo(String id, String displayName, String realName) {}
    public record HistoryMessage(String ts, String user, String text, String threadTs) {}
    public record HistoryResult(boolean ok, List<HistoryMessage> messages, String error) {}
}
