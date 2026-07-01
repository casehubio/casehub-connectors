package io.casehub.connectors.chat.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.*;
import io.casehub.connectors.chat.spi.InboundTranslator;

@ApplicationScoped
public class DiscordInboundTranslator implements InboundTranslator {

    private static final Logger LOG = Logger.getLogger(DiscordInboundTranslator.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String connectorType() {
        return InboundConnectorTypes.DISCORD;
    }

    @Override
    public ReceivedMessage translate(final InboundMessage msg) {
        final var channel = new ChatChannelRef(msg.externalChannelRef());
        final var messageRef = new ChatMessageRef(channel,
                msg.metadata().get("discord-message-id"));
        final String refId = msg.metadata().get("discord-reference-id");
        final ChatMessageRef parentRef = refId != null
                ? new ChatMessageRef(channel, refId) : null;
        return new ReceivedMessage(
                InboundConnectorTypes.DISCORD,
                channel,
                messageRef,
                parentRef,
                new MemberRef(msg.externalSenderId()),
                new ChatContent(msg.content(), null, msg.attachments(),
                        parseEmbeds(msg.metadata().get("discord-embeds"))),
                msg.receivedAt());
    }

    static List<RichCard> parseEmbeds(final String embedsJson) {
        if (embedsJson == null || embedsJson.isBlank()) {
            return List.of();
        }
        try {
            final JsonNode array = MAPPER.readTree(embedsJson);
            if (!array.isArray()) return List.of();
            final List<RichCard> cards = new ArrayList<>();
            for (final JsonNode embed : array) {
                final var builder = RichCard.builder();
                if (embed.has("title")) builder.title(embed.get("title").asText());
                if (embed.has("description")) builder.description(embed.get("description").asText());
                if (embed.has("url")) builder.url(embed.get("url").asText());
                if (embed.has("color")) builder.color(embed.get("color").asInt());
                if (embed.has("thumbnail") && embed.get("thumbnail").has("url")) {
                    builder.thumbnailUrl(embed.get("thumbnail").get("url").asText());
                }
                if (embed.has("image") && embed.get("image").has("url")) {
                    builder.imageUrl(embed.get("image").get("url").asText());
                }
                if (embed.has("footer") && embed.get("footer").has("text")) {
                    builder.footer(embed.get("footer").get("text").asText());
                }
                if (embed.has("author") && embed.get("author").has("name")) {
                    builder.author(embed.get("author").get("name").asText());
                }
                if (embed.has("fields") && embed.get("fields").isArray()) {
                    final List<RichCard.Field> fields = new ArrayList<>();
                    for (final JsonNode f : embed.get("fields")) {
                        fields.add(new RichCard.Field(
                                f.has("name") ? f.get("name").asText() : "",
                                f.has("value") ? f.get("value").asText() : "",
                                f.has("inline") && f.get("inline").asBoolean()));
                    }
                    builder.fields(fields);
                }
                if (embed.has("title") || embed.has("description")) {
                    cards.add(builder.build());
                }
            }
            return List.copyOf(cards);
        } catch (final Exception e) {
            LOG.warning("Failed to parse discord-embeds: " + e.getMessage());
            return List.of();
        }
    }
}
