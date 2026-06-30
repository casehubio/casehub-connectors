package io.casehub.connectors.mcp;

import java.util.List;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
            @ToolArg(description = "Embed title.", required = false)
            final String embedTitle,
            @ToolArg(description = "Embed description.", required = false)
            final String embedDescription,
            @ToolArg(description = "Embed color as decimal integer RGB "
                                 + "(e.g. 16711680 for red #FF0000, "
                                 + "65280 for green #00FF00).",
                     required = false)
            final String embedColor) {
        try {
            if (token.isBlank()) {
                return "Failed: casehub.connectors.discord.token is not configured";
            }

            final boolean hasText = text != null && !text.isBlank();
            final boolean hasEmbed = (embedTitle != null && !embedTitle.isBlank())
                    || (embedDescription != null && !embedDescription.isBlank());

            if (!hasText && !hasEmbed) {
                return "Failed: text or embed required";
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
                embeds = List.of(new DiscordEmbed(
                        embedTitle, embedDescription, null, color,
                        List.of(), null, null, null, null));
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
            } else {
                bridgeContent = McpContentSanitizer.sanitize(embedDescription);
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
