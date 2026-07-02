package io.casehub.connectors.discord;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.casehub.connectors.Attachment;
import io.casehub.connectors.discord.model.*;
import io.casehub.connectors.http.HttpHelper;

/**
 * Pure-HTTP client for the Discord Bot REST API v10.
 *
 * <p>Uses {@code java.net.http.HttpClient} — no Discord SDK dependency.
 * Shares {@link HttpHelper#CLIENT} (5 s connect timeout) with other connectors.
 *
 * <p>On HTTP 429, reads {@code Retry-After}, sleeps, and retries once.
 * Sleep is safe on virtual threads (no carrier-thread starvation).
 *
 * <p>{@code apiBaseUrl} is package-private to allow direct field injection in unit tests,
 * mirroring the {@code SlackBotClient.apiBaseUrl} pattern.
 */
@ApplicationScoped
public class DiscordClient {

    private static final Logger LOG = Logger.getLogger(DiscordClient.class.getName());
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_PAGES = 50;
    private static final long VIEW_CHANNEL_PERMISSION = 1024L; // 1 << 10
    Set<String> allowedCdnHosts = Set.of(
            "cdn.discordapp.com", "media.discordapp.net");

    private final ObjectMapper mapper;

    @ConfigProperty(name = "casehub.discord.api-base-url",
                    defaultValue = "https://discord.com/api/v10")
    String apiBaseUrl;

    @ConfigProperty(name = "casehub.discord.guild-id", defaultValue = "")
    String guildId;

    @ConfigProperty(name = "casehub.discord.attachment.max-bytes",
                    defaultValue = "8388608")
    long maxAttachmentBytes;

    public DiscordClient() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /**
     * Posts a message to a Discord channel.
     *
     * @param token     bot token
     * @param channelId Discord channel ID (snowflake)
     * @param content   message content (max 2000 characters)
     * @return the result of the API call
     */
    public PostResult sendMessage(final String token, final String channelId, final String content) {
        return sendMessage(token, channelId, content, List.of());
    }

    public PostResult sendMessage(final String token, final String channelId,
                                  final String content, final List<DiscordEmbed> embeds) {
        try {
            final ObjectNode body = buildMessageBody(content, embeds);

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/channels/" + channelId + "/messages"))
                    .header("Authorization", "Bot " + token)
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            return sendWithRetry(request);

        } catch (final Exception e) {
            LOG.warning("DiscordClient: sendMessage error — " + e.getMessage());
            return PostResult.failure("json-error");
        }
    }

    /**
     * Posts a reply to a message in a Discord channel.
     *
     * @param token            bot token
     * @param channelId        Discord channel ID (snowflake)
     * @param content          message content (max 2000 characters)
     * @param replyToMessageId message ID to reply to
     * @return the result of the API call
     */
    public PostResult sendReply(final String token, final String channelId,
                                final String content, final String replyToMessageId) {
        return sendReply(token, channelId, content, replyToMessageId, List.of());
    }

    public PostResult sendReply(final String token, final String channelId,
                                final String content, final String replyToMessageId,
                                final List<DiscordEmbed> embeds) {
        try {
            final ObjectNode body = buildMessageBody(content, embeds);

            final ObjectNode messageReference = mapper.createObjectNode();
            messageReference.put("message_id", replyToMessageId);
            body.set("message_reference", messageReference);

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/channels/" + channelId + "/messages"))
                    .header("Authorization", "Bot " + token)
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            return sendWithRetry(request);

        } catch (final Exception e) {
            LOG.warning("DiscordClient: sendReply error — " + e.getMessage());
            return PostResult.failure("json-error");
        }
    }

    /**
     * Fetches messages from a Discord channel with pagination.
     *
     * <p>Fetches up to {@value MAX_PAGES} pages. A WARNING is logged if the
     * cap is hit, if a mid-loop error occurs, or if the request is interrupted — in all cases
     * the partial result accumulated so far is returned rather than an empty list.
     *
     * @param token     bot token
     * @param channelId Discord channel ID
     * @param afterId   snowflake ID to fetch messages after (for pagination), or null
     * @param limit     max messages per page (1-100)
     * @return list of messages; empty if the first request fails
     */
    public List<DiscordMessage> getMessages(final String token, final String channelId,
                                            final String afterId, final int limit) {
        final List<DiscordMessage> accumulated = new ArrayList<>();
        String currentAfterId = afterId;
        int pageNum = 0;

        while (pageNum < MAX_PAGES) {
            final String query = currentAfterId == null
                    ? "?limit=" + limit
                    : "?limit=" + limit + "&after=" + URLEncoder.encode(currentAfterId, StandardCharsets.UTF_8);

            try {
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiBaseUrl + "/channels/" + channelId + "/messages" + query))
                        .header("Authorization", "Bot " + token)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                final HttpResponse<String> response =
                        HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    LOG.warning(String.format(
                            "DiscordClient: getMessages stopped after %d page(s) — returned %d messages: HTTP %d",
                            pageNum, accumulated.size(), response.statusCode()));
                    return List.copyOf(accumulated);
                }

                final ArrayNode messages = (ArrayNode) mapper.readTree(response.body());
                if (messages.isEmpty()) {
                    break;
                }

                for (final JsonNode msgNode : messages) {
                    accumulated.add(parseMessage(msgNode));
                }

                pageNum++;
                currentAfterId = messages.get(messages.size() - 1).get("id").asText();

            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warning(String.format(
                        "DiscordClient: getMessages interrupted after %d page(s) — returned %d messages",
                        pageNum, accumulated.size()));
                return List.copyOf(accumulated);
            } catch (final Exception e) {
                LOG.warning(String.format(
                        "DiscordClient: getMessages error after %d page(s) — returned %d messages: %s",
                        pageNum, accumulated.size(), e.getMessage()));
                return List.copyOf(accumulated);
            }
        }

        return List.copyOf(accumulated);
    }

    /**
     * Lists all channels in the guild.
     *
     * @param token bot token
     * @return list of guild channels
     */
    public List<DiscordChannel> listGuildChannels(final String token) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/guilds/" + guildId + "/channels"))
                    .header("Authorization", "Bot " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warning("DiscordClient: listGuildChannels HTTP " + response.statusCode());
                return List.of();
            }

            final ArrayNode channels = (ArrayNode) mapper.readTree(response.body());
            final List<DiscordChannel> result = new ArrayList<>();
            for (final JsonNode ch : channels) {
                result.add(parseChannel(ch));
            }
            return result;

        } catch (final Exception e) {
            LOG.warning("DiscordClient: listGuildChannels error — " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches a single channel by ID.
     *
     * @param token     bot token
     * @param channelId Discord channel ID
     * @return channel details, or null on error or 404
     */
    public DiscordChannel getChannel(final String token, final String channelId) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/channels/" + channelId))
                    .header("Authorization", "Bot " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() != 200) {
                LOG.warning("DiscordClient: getChannel HTTP " + response.statusCode());
                return null;
            }

            return parseChannel(mapper.readTree(response.body()));

        } catch (final Exception e) {
            LOG.warning("DiscordClient: getChannel error — " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a new channel in the guild.
     *
     * @param token     bot token
     * @param name      channel name
     * @param topic     channel topic
     * @param type      channel type (0 = GUILD_TEXT)
     * @param nsfw      whether the channel is NSFW
     * @param isPrivate whether to deny @everyone VIEW_CHANNEL
     * @return created channel, or null on error
     */
    public DiscordChannel createChannel(final String token, final String name, final String topic,
                                        final int type, final boolean nsfw, final boolean isPrivate) {
        try {
            final ObjectNode body = mapper.createObjectNode();
            body.put("name", name);
            body.put("topic", topic);
            body.put("type", type);
            body.put("nsfw", nsfw);

            if (isPrivate) {
                final ArrayNode overwrites = mapper.createArrayNode();
                final ObjectNode overwrite = mapper.createObjectNode();
                overwrite.put("id", guildId);
                overwrite.put("type", 0); // role
                overwrite.put("deny", String.valueOf(VIEW_CHANNEL_PERMISSION));
                overwrites.add(overwrite);
                body.set("permission_overwrites", overwrites);
            }

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/guilds/" + guildId + "/channels"))
                    .header("Authorization", "Bot " + token)
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                LOG.warning("DiscordClient: createChannel HTTP " + response.statusCode());
                return null;
            }

            return parseChannel(mapper.readTree(response.body()));

        } catch (final Exception e) {
            LOG.warning("DiscordClient: createChannel error — " + e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a channel.
     *
     * @param token     bot token
     * @param channelId Discord channel ID
     */
    public void deleteChannel(final String token, final String channelId) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/channels/" + channelId))
                    .header("Authorization", "Bot " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .DELETE()
                    .build();

            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                LOG.warning("DiscordClient: deleteChannel HTTP " + response.statusCode());
            }
        } catch (final Exception e) {
            LOG.warning("DiscordClient: deleteChannel error — " + e.getMessage());
        }
    }

    /**
     * Adds a reaction to a message.
     *
     * @param token     bot token
     * @param channelId Discord channel ID
     * @param messageId Discord message ID
     * @param emoji     emoji (unicode or custom emoji as "name:id")
     */
    public void addReaction(final String token, final String channelId,
                            final String messageId, final String emoji) {
        try {
            final String encodedEmoji = URLEncoder.encode(emoji, StandardCharsets.UTF_8);
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/channels/" + channelId + "/messages/" +
                            messageId + "/reactions/" + encodedEmoji + "/@me"))
                    .header("Authorization", "Bot " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();

            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204) {
                LOG.warning("DiscordClient: addReaction HTTP " + response.statusCode());
            }

        } catch (final Exception e) {
            LOG.warning("DiscordClient: addReaction error — " + e.getMessage());
        }
    }

    /**
     * Removes a reaction from a message.
     *
     * @param token     bot token
     * @param channelId Discord channel ID
     * @param messageId Discord message ID
     * @param emoji     emoji (unicode or custom emoji as "name:id")
     */
    public void removeReaction(final String token, final String channelId,
                               final String messageId, final String emoji) {
        try {
            final String encodedEmoji = URLEncoder.encode(emoji, StandardCharsets.UTF_8);
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/channels/" + channelId + "/messages/" +
                            messageId + "/reactions/" + encodedEmoji + "/@me"))
                    .header("Authorization", "Bot " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .DELETE()
                    .build();

            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 204) {
                LOG.warning("DiscordClient: removeReaction HTTP " + response.statusCode());
            }

        } catch (final Exception e) {
            LOG.warning("DiscordClient: removeReaction error — " + e.getMessage());
        }
    }

    /**
     * Lists all reaction emoji on a message.
     *
     * @param token     bot token
     * @param channelId Discord channel ID
     * @param messageId Discord message ID
     * @return list of emoji (unicode or custom emoji as "name:id")
     */
    public List<String> listReactionEmoji(final String token, final String channelId, final String messageId) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/channels/" + channelId + "/messages/" + messageId))
                    .header("Authorization", "Bot " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warning("DiscordClient: listReactionEmoji HTTP " + response.statusCode());
                return List.of();
            }

            final JsonNode message = mapper.readTree(response.body());
            if (!message.has("reactions")) {
                return List.of();
            }

            final List<String> emojis = new ArrayList<>();
            for (final JsonNode reaction : message.get("reactions")) {
                final JsonNode emojiNode = reaction.get("emoji");
                final String name = emojiNode.get("name").asText();
                if (emojiNode.has("id") && !emojiNode.get("id").isNull()) {
                    emojis.add(name + ":" + emojiNode.get("id").asText());
                } else {
                    emojis.add(name);
                }
            }
            return emojis;

        } catch (final Exception e) {
            LOG.warning("DiscordClient: listReactionEmoji error — " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Lists guild members with pagination.
     *
     * <p>Fetches up to {@value MAX_PAGES} pages. A WARNING is logged if the
     * cap is hit, if a mid-loop error occurs, or if the request is interrupted — in all cases
     * the partial result accumulated so far is returned rather than an empty list.
     *
     * @param token       bot token
     * @param limit       max members per page (1-1000)
     * @param afterUserId snowflake ID to fetch members after (for pagination), or null
     * @return list of members; empty if the first request fails
     */
    public List<DiscordMember> listGuildMembers(final String token, final int limit, final String afterUserId) {
        final List<DiscordMember> accumulated = new ArrayList<>();
        String currentAfterId = afterUserId;
        int pageNum = 0;

        while (pageNum < MAX_PAGES) {
            final String query = currentAfterId == null
                    ? "?limit=" + limit
                    : "?limit=" + limit + "&after=" + URLEncoder.encode(currentAfterId, StandardCharsets.UTF_8);

            try {
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiBaseUrl + "/guilds/" + guildId + "/members" + query))
                        .header("Authorization", "Bot " + token)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();

                final HttpResponse<String> response =
                        HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    LOG.warning(String.format(
                            "DiscordClient: listGuildMembers stopped after %d page(s) — returned %d members: HTTP %d",
                            pageNum, accumulated.size(), response.statusCode()));
                    return List.copyOf(accumulated);
                }

                final ArrayNode members = (ArrayNode) mapper.readTree(response.body());
                if (members.isEmpty()) {
                    break;
                }

                for (final JsonNode memberNode : members) {
                    accumulated.add(parseMember(memberNode));
                }

                pageNum++;
                currentAfterId = members.get(members.size() - 1).get("user").get("id").asText();

            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warning(String.format(
                        "DiscordClient: listGuildMembers interrupted after %d page(s) — returned %d members",
                        pageNum, accumulated.size()));
                return List.copyOf(accumulated);
            } catch (final Exception e) {
                LOG.warning(String.format(
                        "DiscordClient: listGuildMembers error after %d page(s) — returned %d members: %s",
                        pageNum, accumulated.size(), e.getMessage()));
                return List.copyOf(accumulated);
            }
        }

        return List.copyOf(accumulated);
    }

    /**
     * Fetches a single guild member by user ID.
     *
     * @param token  bot token
     * @param userId Discord user ID
     * @return member details, or null on error or 404
     */
    public DiscordMember getGuildMember(final String token, final String userId) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/guilds/" + guildId + "/members/" + userId))
                    .header("Authorization", "Bot " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() != 200) {
                LOG.warning("DiscordClient: getGuildMember HTTP " + response.statusCode());
                return null;
            }

            return parseMember(mapper.readTree(response.body()));

        } catch (final Exception e) {
            LOG.warning("DiscordClient: getGuildMember error — " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetches the guild details.
     *
     * @param token      bot token
     * @param withCounts whether to include approximate member counts
     * @return guild details, or null on error
     */
    public DiscordGuild getGuild(final String token, final boolean withCounts) {
        try {
            final String query = withCounts ? "?with_counts=true" : "";
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/guilds/" + guildId + query))
                    .header("Authorization", "Bot " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warning("DiscordClient: getGuild HTTP " + response.statusCode());
                return null;
            }

            final JsonNode guild = mapper.readTree(response.body());
            return new DiscordGuild(
                    guild.get("id").asText(),
                    guild.get("name").asText(),
                    guild.has("approximate_member_count") ? guild.get("approximate_member_count").asInt() : null
            );

        } catch (final Exception e) {
            LOG.warning("DiscordClient: getGuild error — " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetches the Gateway WebSocket URL.
     *
     * @param token bot token
     * @return Gateway URL, or null on error
     */
    public String getGatewayUrl(final String token) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/gateway"))
                    .header("Authorization", "Bot " + token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            final HttpResponse<String> response =
                    HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warning("DiscordClient: getGatewayUrl HTTP " + response.statusCode());
                return null;
            }

            final JsonNode result = mapper.readTree(response.body());
            return result.get("url").asText();

        } catch (final Exception e) {
            LOG.warning("DiscordClient: getGatewayUrl error — " + e.getMessage());
            return null;
        }
    }

    public String guildId() {
        return guildId;
    }

    private ObjectNode buildMessageBody(final String content,
                                        final List<DiscordEmbed> embeds) {
        final ObjectNode body = mapper.createObjectNode();
        if (content != null && !content.isEmpty()) {
            body.put("content", content);
        }
        if (embeds != null && !embeds.isEmpty()) {
            final ArrayNode embedsArray = body.putArray("embeds");
            for (final DiscordEmbed embed : embeds) {
                serializeEmbed(embedsArray.addObject(), embed);
            }
        }
        return body;
    }

    private void serializeEmbed(final ObjectNode node, final DiscordEmbed embed) {
        if (embed.title() != null) node.put("title", embed.title());
        if (embed.description() != null) node.put("description", embed.description());
        if (embed.url() != null) node.put("url", embed.url());
        if (embed.color() != null) node.put("color", embed.color());
        if (!embed.fields().isEmpty()) {
            final ArrayNode fields = node.putArray("fields");
            for (final DiscordEmbed.Field f : embed.fields()) {
                final ObjectNode fn = fields.addObject();
                fn.put("name", f.name());
                fn.put("value", f.value());
                fn.put("inline", f.inline());
            }
        }
        if (embed.thumbnailUrl() != null)
            node.putObject("thumbnail").put("url", embed.thumbnailUrl());
        if (embed.imageUrl() != null)
            node.putObject("image").put("url", embed.imageUrl());
        if (embed.footer() != null)
            node.putObject("footer").put("text", embed.footer().text());
        if (embed.author() != null)
            node.putObject("author").put("name", embed.author().name());
    }

    /**
     * Downloads an attachment from a Discord CDN URL.
     *
     * @param attachment the attachment metadata
     * @return the downloaded attachment, or {@code null} on failure
     */
    public Attachment downloadAttachment(final DiscordAttachment attachment) {
        return downloadAttachment(attachment, allowedCdnHosts);
    }

    Attachment downloadAttachment(final DiscordAttachment attachment,
                                  final Set<String> allowedHosts) {
        final URI uri;
        try {
            uri = URI.create(attachment.url());
        } catch (final Exception e) {
            LOG.warning("DiscordClient: invalid attachment URL — " + attachment.url());
            return null;
        }
        if (!allowedHosts.contains(uri.getHost())) {
            LOG.warning("DiscordClient: attachment URL host rejected (SSRF defense) — " + uri.getHost());
            return null;
        }

        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri).timeout(REQUEST_TIMEOUT).GET().build();
            final HttpResponse<InputStream> response = HttpHelper.CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 403) {
                LOG.warning("DiscordClient: CDN 403 (expired URL) — " + attachment.filename());
                return null;
            }
            if (response.statusCode() != 200) {
                LOG.warning("DiscordClient: attachment download HTTP " + response.statusCode());
                return null;
            }

            final long contentLength = response.headers()
                    .firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > maxAttachmentBytes) {
                LOG.warning(String.format(
                        "DiscordClient: attachment too large (%d bytes, limit %d) — %s",
                        contentLength, maxAttachmentBytes, attachment.filename()));
                return null;
            }

            try (final InputStream in = response.body()) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buf = new byte[8192];
                long total = 0;
                int read;
                while ((read = in.read(buf)) != -1) {
                    total += read;
                    if (total > maxAttachmentBytes) {
                        LOG.warning(String.format(
                                "DiscordClient: attachment stream exceeded limit (%d bytes) — %s",
                                maxAttachmentBytes, attachment.filename()));
                        return null;
                    }
                    out.write(buf, 0, read);
                }
                return new Attachment(attachment.filename(),
                        attachment.contentType(), out.toByteArray());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (final Exception e) {
            LOG.warning("DiscordClient: attachment download error — " + e.getMessage());
            return null;
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
                LOG.warning("DiscordClient: rate limited by Discord — retrying after " + seconds + "s");
                if (seconds > 0) {
                    Thread.sleep(seconds * 1_000);
                }
                final HttpResponse<String> retry =
                        HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (retry.statusCode() == 429) {
                    return PostResult.failure("rate-limited");
                }

                return parsePostResponse(retry);
            }

            return parsePostResponse(response);

        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return PostResult.failure("interrupted");
        } catch (final Exception e) {
            LOG.warning("DiscordClient: HTTP error — " + e.getMessage());
            return PostResult.failure("http-error");
        }
    }

    private PostResult parsePostResponse(final HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return PostResult.failure(response.statusCode() + " " + response.body());
        }

        try {
            final JsonNode json = mapper.readTree(response.body());
            final String messageId = json.get("id").asText();
            final String channelId = json.get("channel_id").asText();
            return PostResult.success(messageId, channelId);

        } catch (final Exception e) {
            LOG.warning("DiscordClient: failed to parse API response — " + e.getMessage());
            return PostResult.failure("parse-error");
        }
    }

    /**
     * Parses a Discord {@code attachments[]} JSON array into metadata records.
     *
     * @param attachmentsArray the JSON array node, or null
     * @return list of attachment metadata; never null
     */
    public List<DiscordAttachment> parseAttachments(final JsonNode attachmentsArray) {
        if (attachmentsArray == null || !attachmentsArray.isArray() || attachmentsArray.isEmpty()) {
            return List.of();
        }
        final List<DiscordAttachment> result = new ArrayList<>();
        for (final JsonNode node : attachmentsArray) {
            result.add(new DiscordAttachment(
                    node.get("id").asText(),
                    node.has("filename") ? node.get("filename").asText() : null,
                    node.has("content_type") ? node.get("content_type").asText() : null,
                    node.has("size") ? node.get("size").asLong() : 0,
                    node.has("url") ? node.get("url").asText() : null));
        }
        return List.copyOf(result);
    }

    private DiscordMessage parseMessage(final JsonNode node) {
        final JsonNode authorNode = node.get("author");
        final DiscordUser author = new DiscordUser(
                authorNode.get("id").asText(),
                authorNode.get("username").asText(),
                authorNode.has("global_name") && !authorNode.get("global_name").isNull()
                        ? authorNode.get("global_name").asText() : null,
                authorNode.has("bot") && authorNode.get("bot").asBoolean()
        );

        final String referencedMessageId;
        if (node.has("message_reference") && node.get("message_reference").has("message_id")) {
            referencedMessageId = node.get("message_reference").get("message_id").asText();
        } else {
            referencedMessageId = null;
        }

        final List<DiscordAttachment> attachments = parseAttachments(
                node.has("attachments") ? node.get("attachments") : null);

        return new DiscordMessage(
                node.get("id").asText(),
                node.get("channel_id").asText(),
                author,
                node.get("content").asText(),
                Instant.parse(node.get("timestamp").asText()),
                referencedMessageId,
                node.get("type").asInt(),
                attachments
        );
    }

    private DiscordChannel parseChannel(final JsonNode node) {
        final List<PermissionOverwrite> overwrites = new ArrayList<>();
        if (node.has("permission_overwrites") && !node.get("permission_overwrites").isNull()) {
            for (final JsonNode ow : node.get("permission_overwrites")) {
                overwrites.add(new PermissionOverwrite(
                        ow.get("id").asText(),
                        ow.get("type").asInt(),
                        ow.get("allow").asLong(),
                        ow.get("deny").asLong()
                ));
            }
        }

        return new DiscordChannel(
                node.get("id").asText(),
                node.has("name") ? node.get("name").asText() : "",
                node.has("topic") && !node.get("topic").isNull() ? node.get("topic").asText() : null,
                node.get("type").asInt(),
                node.has("parent_id") && !node.get("parent_id").isNull() ? node.get("parent_id").asText() : null,
                overwrites
        );
    }

    private DiscordMember parseMember(final JsonNode node) {
        final JsonNode userNode = node.get("user");
        final DiscordUser user = new DiscordUser(
                userNode.get("id").asText(),
                userNode.get("username").asText(),
                userNode.has("global_name") && !userNode.get("global_name").isNull()
                        ? userNode.get("global_name").asText() : null,
                userNode.has("bot") && userNode.get("bot").asBoolean()
        );

        final List<String> roles = new ArrayList<>();
        if (node.has("roles") && !node.get("roles").isNull()) {
            for (final JsonNode roleNode : node.get("roles")) {
                roles.add(roleNode.asText());
            }
        }

        return new DiscordMember(
                user,
                node.has("nick") && !node.get("nick").isNull() ? node.get("nick").asText() : null,
                roles,
                Instant.parse(node.get("joined_at").asText())
        );
    }

    private static long parseLongSafe(final String value) {
        if (value == null) return 1L;
        try {
            return Long.parseLong(value.trim());
        } catch (final NumberFormatException e) {
            return 1L;
        }
    }
}
