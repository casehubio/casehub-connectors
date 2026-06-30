package io.casehub.connectors.mcp;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.Blocking;

import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.connectors.discord.DiscordClient;
import io.casehub.connectors.discord.DiscordDiscovery;
import io.casehub.connectors.discord.model.DiscordEmbed;
import io.casehub.connectors.discord.model.PostResult;

@ApplicationScoped
public class DiscordMcpTool {

    private static final Logger LOG = Logger.getLogger(DiscordMcpTool.class);
    private static final int DISCORD_TOTAL_EMBED_LIMIT = 6000;

    private final DiscordClient client;
    private final ConnectorMeshBridge meshBridge;
    private final String token;

    @Inject
    public DiscordMcpTool(final DiscordClient client,
                          final ConnectorMeshBridge meshBridge,
                          @ConfigProperty(name = "casehub.connectors.discord.token",
                                          defaultValue = "") final String token) {
        this.client = client;
        this.meshBridge = meshBridge;
        this.token = token;
    }

    @Tool(name = "send_discord",
          description = "Posts a message to a Discord channel via bot token. "
                      + "Returns 'Posted to <channel> (id=<messageId>)' on success "
                      + "or 'Failed: <reason>' on error. At least text or an embed "
                      + "arg is required. Use list_discord_channels to discover IDs.")
    @Blocking
    public String sendDiscord(
            @ToolArg(description = "Discord channel ID (snowflake).")
            final String channel,
            @ToolArg(description = "Message text (max 2000 chars). "
                                 + "Optional if embed args are provided.",
                     required = false)
            final String text,
            @ToolArg(description = "Discord message ID to reply to. "
                                 + "Use discord-message-id from inbound metadata.",
                     required = false)
            final String replyToMessageId,
            @ToolArg(description = "Embed title (max 256 chars).", required = false)
            final String embedTitle,
            @ToolArg(description = "Embed description (max 4096 chars).", required = false)
            final String embedDescription,
            @ToolArg(description = "Embed color as decimal integer RGB "
                                 + "(e.g. 16711680 for red #FF0000, "
                                 + "65280 for green #00FF00).",
                     required = false)
            final String embedColor,
            @ToolArg(description = "Embed URL — makes title a hyperlink. Requires embedTitle.",
                     required = false)
            final String embedUrl,
            @ToolArg(description = "Embed thumbnail URL — small image top-right.",
                     required = false)
            final String embedThumbnailUrl,
            @ToolArg(description = "Embed image URL — full-width image below description.",
                     required = false)
            final String embedImageUrl,
            @ToolArg(description = "Embed footer text (max 2048 chars).", required = false)
            final String embedFooter,
            @ToolArg(description = "Embed author name (max 256 chars).", required = false)
            final String embedAuthor,
            @ToolArg(description = "Embed fields as JSON array: "
                                 + "[{\"name\":\"...\",\"value\":\"...\",\"inline\":true}]. "
                                 + "Max 25 fields.",
                     required = false)
            final String embedFields) {
        try {
            if (token.isBlank()) {
                return "Failed: casehub.connectors.discord.token is not configured";
            }

            final boolean hasText = text != null && !text.isBlank();
            final boolean hasEmbed = (embedTitle != null && !embedTitle.isBlank())
                    || (embedDescription != null && !embedDescription.isBlank())
                    || (embedUrl != null && !embedUrl.isBlank())
                    || (embedThumbnailUrl != null && !embedThumbnailUrl.isBlank())
                    || (embedImageUrl != null && !embedImageUrl.isBlank())
                    || (embedFooter != null && !embedFooter.isBlank())
                    || (embedAuthor != null && !embedAuthor.isBlank())
                    || (embedFields != null && !embedFields.isBlank());

            if (!hasText && !hasEmbed) {
                return "Failed: text or embed required";
            }

            // Validate Discord API limits
            if (embedTitle != null && embedTitle.length() > 256) {
                return "Failed: embedTitle exceeds 256 characters";
            }
            if (embedDescription != null && embedDescription.length() > 4096) {
                return "Failed: embedDescription exceeds 4096 characters";
            }
            if (embedFooter != null && embedFooter.length() > 2048) {
                return "Failed: embedFooter exceeds 2048 characters";
            }
            if (embedAuthor != null && embedAuthor.length() > 256) {
                return "Failed: embedAuthor exceeds 256 characters";
            }
            if (embedUrl != null && !embedUrl.isBlank()
                    && (embedTitle == null || embedTitle.isBlank())) {
                return "Failed: embedUrl requires embedTitle";
            }

            final List<DiscordEmbed> embeds;
            if (hasEmbed) {
                final Integer color;
                if (embedColor != null && !embedColor.isBlank()) {
                    try {
                        color = Integer.parseInt(embedColor);
                    } catch (final NumberFormatException e) {
                        return "Failed: embedColor must be a decimal integer (e.g. 16711680 for red)";
                    }
                } else {
                    color = null;
                }

                // Parse embedFields
                final List<DiscordEmbed.Field> fields;
                if (embedFields != null && !embedFields.isBlank()) {
                    try {
                        final var jsonArray = Json.createReader(new StringReader(embedFields))
                                .readArray();
                        if (jsonArray.size() > 25) {
                            return "Failed: embedFields exceeds 25 fields";
                        }
                        fields = new ArrayList<>();
                        for (final var jsonValue : jsonArray) {
                            final var jsonObject = jsonValue.asJsonObject();
                            final var name = jsonObject.getString("name", null);
                            final var value = jsonObject.getString("value", null);
                            if (name == null || value == null) {
                                return "Failed: embedFields must contain name and value";
                            }
                            final var inline = jsonObject.getBoolean("inline", false);
                            fields.add(new DiscordEmbed.Field(name, value, inline));
                        }
                    } catch (final Exception e) {
                        return "Failed: embedFields must be a JSON array of "
                             + "{name, value, inline} objects";
                    }
                } else {
                    fields = List.of();
                }

                // Check total embed content limit (6000 chars)
                long totalContent = 0;
                if (embedTitle != null) totalContent += embedTitle.length();
                if (embedDescription != null) totalContent += embedDescription.length();
                if (embedFooter != null) totalContent += embedFooter.length();
                if (embedAuthor != null) totalContent += embedAuthor.length();
                for (final var field : fields) {
                    totalContent += field.name().length() + field.value().length();
                }
                if (totalContent > DISCORD_TOTAL_EMBED_LIMIT) {
                    return "Failed: total embed content exceeds " + DISCORD_TOTAL_EMBED_LIMIT + " characters";
                }

                final DiscordEmbed.Footer footer = (embedFooter != null && !embedFooter.isBlank())
                        ? new DiscordEmbed.Footer(embedFooter) : null;
                final DiscordEmbed.Author author = (embedAuthor != null && !embedAuthor.isBlank())
                        ? new DiscordEmbed.Author(embedAuthor) : null;

                embeds = List.of(new DiscordEmbed(
                        embedTitle, embedDescription, embedUrl, color,
                        fields, embedThumbnailUrl, embedImageUrl, footer, author));
            } else {
                embeds = List.of();
            }

            final PostResult result;
            if (replyToMessageId != null && !replyToMessageId.isBlank()) {
                result = client.sendReply(token, channel,
                        hasText ? text : null, replyToMessageId, embeds);
            } else {
                result = client.sendMessage(token, channel,
                        hasText ? text : null, embeds);
            }

            if (!result.ok()) {
                return "Failed: " + result.error();
            }

            final String bridgeContent;
            if (hasText) {
                bridgeContent = McpContentSanitizer.sanitize(text);
            } else if (embedTitle != null && !embedTitle.isBlank()) {
                bridgeContent = McpContentSanitizer.sanitize(embedTitle);
            } else if (embedDescription != null && !embedDescription.isBlank()) {
                bridgeContent = McpContentSanitizer.sanitize(embedDescription);
            } else if (embedFooter != null && !embedFooter.isBlank()) {
                bridgeContent = McpContentSanitizer.sanitize(embedFooter);
            } else if (embedAuthor != null && !embedAuthor.isBlank()) {
                bridgeContent = McpContentSanitizer.sanitize(embedAuthor);
            } else {
                bridgeContent = "[embed]";
            }
            meshBridge.notifyDelivered(DiscordDiscovery.ID, channel, bridgeContent);

            return "Posted to " + channel + " (id=" + result.messageId() + ")";
        } catch (final Exception e) {
            LOG.warnf("send_discord failed [%s]: %s",
                    e.getClass().getSimpleName(), e.getMessage());
            return "Failed: " + e.getMessage();
        }
    }

    @Tool(name = "list_discord_channels",
          description = "Lists text channels in the configured Discord guild "
                      + "with name, ID, topic, and type. For Discord-specific "
                      + "detail use this tool. For a cross-platform overview "
                      + "across all connectors, use list_channels.")
    @Blocking
    public String listDiscordChannels() {
        if (token.isBlank()) {
            return "Failed: casehub.connectors.discord.token is not configured";
        }
        if (client.guildId() == null || client.guildId().isBlank()) {
            return "Failed: casehub.discord.guild-id is not configured";
        }

        try {
            final var channels = client.listGuildChannels(token);
            if (channels == null || channels.isEmpty()) {
                return "No channels found.";
            }

            final StringBuilder sb = new StringBuilder();
            for (final var ch : channels) {
                if (!DiscordDiscovery.TEXT_CHANNEL_TYPES.contains(ch.type())) continue;
                sb.append("#").append(ch.name())
                  .append(" (").append(ch.id()).append(")");
                if (ch.topic() != null && !ch.topic().isBlank()) {
                    sb.append(" — ").append(ch.topic());
                }
                sb.append("\n");
            }
            return sb.isEmpty() ? "No text channels found." : sb.toString().stripTrailing();
        } catch (final Exception e) {
            LOG.warnf("list_discord_channels failed: %s", e.getMessage());
            return "Failed: " + e.getMessage();
        }
    }
}
