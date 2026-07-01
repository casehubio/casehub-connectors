package io.casehub.connectors.mcp;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.smallrye.common.annotation.Blocking;

import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.connectors.chat.model.*;
import io.casehub.connectors.chat.spi.ChatPlatform;

@ApplicationScoped
public class ChatPlatformMcpTool {

    private static final Logger LOG = Logger.getLogger(ChatPlatformMcpTool.class);

    private final ChatPlatformService platformService;
    private final ConnectorMeshBridge meshBridge;

    @Inject
    public ChatPlatformMcpTool(final ChatPlatformService platformService,
                               final ConnectorMeshBridge meshBridge) {
        this.platformService = platformService;
        this.meshBridge = meshBridge;
    }

    @Tool(name = "send_chat",
          description = "Sends a message to a chat channel on any configured platform "
                      + "(slack, discord, irc, ref). Supports optional rich content via "
                      + "card parameters (title, description, fields, images) or a JSON "
                      + "array of multiple cards. "
                      + "Returns 'Sent to <channel> (messageId=<id>)' on success.")
    @Blocking
    public String sendChat(
            @ToolArg(description = "Chat platform id: slack, discord, irc, ref.")
            final String platform,
            @ToolArg(description = "Channel ID (e.g. C123ABC for Slack, snowflake for Discord).")
            final String channel,
            @ToolArg(description = "Message text — required. Serves as notification fallback "
                                 + "when rich cards are present.")
            final String text,
            @ToolArg(description = "Parent message ID for threaded replies. "
                                 + "Slack: ts value. Discord: message ID. Omit for new message.",
                     required = false)
            final String parentMessageId,
            @ToolArg(description = "Rich card title (max 256 on Discord).", required = false)
            final String cardTitle,
            @ToolArg(description = "Rich card description/body text.", required = false)
            final String cardDescription,
            @ToolArg(description = "Card color as decimal RGB (e.g. 16711680 = red). "
                                 + "Discord only — ignored on Slack.",
                     required = false)
            final String cardColor,
            @ToolArg(description = "URL — makes card title a hyperlink.", required = false)
            final String cardUrl,
            @ToolArg(description = "Thumbnail URL — small image.", required = false)
            final String cardThumbnailUrl,
            @ToolArg(description = "Image URL — full-width image.", required = false)
            final String cardImageUrl,
            @ToolArg(description = "Card footer text.", required = false)
            final String cardFooter,
            @ToolArg(description = "Card author text.", required = false)
            final String cardAuthor,
            @ToolArg(description = "Card fields as JSON array: "
                                 + "[{\"name\":\"...\",\"value\":\"...\",\"inline\":true}].",
                     required = false)
            final String cardFields,
            @ToolArg(description = "Multiple cards as JSON array. Each object supports: "
                                 + "title, description, color (decimal int), url, thumbnailUrl, "
                                 + "imageUrl, footer, author, fields (array of {name,value,inline}). "
                                 + "When present, overrides the flat card parameters above.",
                     required = false)
            final String cards) {
        try {
            final ChatPlatform p = platformService.platform(platform);

            final List<RichCard> richCards;
            if (isNotBlank(cards)) {
                try {
                    richCards = parseCardsArray(cards);
                } catch (final IllegalArgumentException e) {
                    return "Failed: " + e.getMessage();
                }
            } else if (hasAnyCardParam(cardTitle, cardDescription, cardColor, cardUrl,
                    cardThumbnailUrl, cardImageUrl, cardFooter, cardAuthor, cardFields)) {
                try {
                    richCards = List.of(parseSingleCard(cardTitle, cardDescription, cardColor,
                            cardUrl, cardThumbnailUrl, cardImageUrl, cardFooter, cardAuthor, cardFields));
                } catch (final IllegalArgumentException e) {
                    return "Failed: " + e.getMessage();
                }
            } else {
                richCards = List.of();
            }

            final var content = new ChatContent(text, null, List.of(), richCards);
            final SendResult result;
            if (isNotBlank(parentMessageId)) {
                final var parentRef = new ChatMessageRef(
                        new ChatChannelRef(channel), parentMessageId);
                result = p.threading().reply(parentRef, content);
            } else {
                result = p.messaging().send(new ChatChannelRef(channel), content);
            }

            if (!result.ok()) {
                return "Failed: " + result.error();
            }

            meshBridge.notifyDelivered(p.id(), channel,
                    McpContentSanitizer.sanitize(text));

            return "Sent to " + channel + " (messageId="
                    + result.messageRef().messageId() + ")";
        } catch (final IllegalArgumentException e) {
            return "Failed: " + e.getMessage();
        } catch (final Exception e) {
            LOG.warnf("send_chat failed [%s]: %s",
                    e.getClass().getSimpleName(), e.getMessage());
            return "Failed: " + e.getMessage();
        }
    }

    @Tool(name = "list_chat_channels",
          description = "Lists channels on a chat platform with rich detail: "
                      + "name, ID, topic, description, private flag, member count. "
                      + "Use list_channels for a thin cross-connector overview.")
    @Blocking
    public String listChatChannels(
            @ToolArg(description = "Chat platform id: slack, discord, irc, ref.")
            final String platform) {
        try {
            final ChatPlatform p = platformService.platform(platform);
            final var channels = p.discovery().listChannels();

            if (channels.isEmpty()) {
                return "No channels found on " + platform + ".";
            }

            final var sb = new StringBuilder();
            for (final Channel ch : channels) {
                sb.append("#").append(ch.name())
                  .append(" (").append(ch.ref().id()).append(")");
                if (ch.isPrivate()) sb.append(" [private]");
                if (ch.memberCount() != null) sb.append(" [").append(ch.memberCount()).append(" members]");
                if (isNotBlank(ch.topic()))
                    sb.append(" — ").append(ch.topic());
                if (isNotBlank(ch.description()))
                    sb.append(" | ").append(ch.description());
                sb.append("\n");
            }
            return sb.toString().stripTrailing();
        } catch (final IllegalArgumentException e) {
            return "Failed: " + e.getMessage();
        } catch (final Exception e) {
            LOG.warnf("list_chat_channels failed [%s]: %s",
                    e.getClass().getSimpleName(), e.getMessage());
            return "Failed: " + e.getMessage();
        }
    }

    private static List<RichCard> parseCardsArray(final String json) {
        try {
            final var jsonArray = Json.createReader(new StringReader(json)).readArray();
            final List<RichCard> result = new ArrayList<>();
            for (final var jv : jsonArray) {
                final var jo = jv.asJsonObject();
                final var builder = RichCard.builder()
                        .title(jo.getString("title", null))
                        .description(jo.getString("description", null))
                        .url(jo.getString("url", null))
                        .thumbnailUrl(jo.getString("thumbnailUrl", null))
                        .imageUrl(jo.getString("imageUrl", null))
                        .footer(jo.getString("footer", null))
                        .author(jo.getString("author", null));
                if (jo.containsKey("color") && !jo.isNull("color")) {
                    builder.color(jo.getInt("color"));
                }
                if (jo.containsKey("fields") && !jo.isNull("fields")) {
                    builder.fields(parseFields(jo.getJsonArray("fields")));
                }
                result.add(builder.build());
            }
            return List.copyOf(result);
        } catch (final Exception e) {
            throw new IllegalArgumentException(
                    "cards must be a JSON array of card objects");
        }
    }

    private static RichCard parseSingleCard(final String title, final String description,
                                            final String color, final String url,
                                            final String thumbnailUrl, final String imageUrl,
                                            final String footer, final String author,
                                            final String fields) {
        final var builder = RichCard.builder()
                .title(title).description(description).url(url)
                .thumbnailUrl(thumbnailUrl).imageUrl(imageUrl)
                .footer(footer).author(author);
        if (isNotBlank(color)) {
            try {
                builder.color(Integer.parseInt(color));
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException("cardColor must be a decimal integer");
            }
        }
        if (isNotBlank(fields)) {
            try {
                builder.fields(parseFields(
                        Json.createReader(new StringReader(fields)).readArray()));
            } catch (final IllegalArgumentException e) {
                throw e;
            } catch (final Exception e) {
                throw new IllegalArgumentException(
                        "cardFields must be a JSON array of {name, value, inline} objects");
            }
        }
        return builder.build();
    }

    private static List<RichCard.Field> parseFields(final jakarta.json.JsonArray jsonArray) {
        final List<RichCard.Field> result = new ArrayList<>();
        for (final var jv : jsonArray) {
            final var jo = jv.asJsonObject();
            result.add(new RichCard.Field(
                    jo.getString("name"),
                    jo.getString("value"),
                    jo.getBoolean("inline", false)));
        }
        return List.copyOf(result);
    }

    private static boolean isNotBlank(final String s) {
        return s != null && !s.isBlank();
    }

    private static boolean hasAnyCardParam(final String... params) {
        for (final String p : params) {
            if (isNotBlank(p)) return true;
        }
        return false;
    }
}
