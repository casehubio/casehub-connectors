package io.casehub.connectors.chat.slack;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.*;
import io.casehub.connectors.chat.spi.InboundTranslator;

@ApplicationScoped
public class SlackInboundTranslator implements InboundTranslator {

    private static final Logger LOG = Logger.getLogger(SlackInboundTranslator.class.getName());

    @Override
    public String connectorType() {
        return InboundConnectorTypes.SLACK;
    }

    @Override
    public ReceivedMessage translate(final InboundMessage msg) {
        final var channel = new ChatChannelRef(msg.externalChannelRef());
        final var messageRef = new ChatMessageRef(channel, msg.metadata().get("slack-ts"));
        final String threadTs = msg.metadata().get("slack-thread-ts");
        final ChatMessageRef parentRef = threadTs != null
                ? new ChatMessageRef(channel, threadTs) : null;
        return new ReceivedMessage(
                InboundConnectorTypes.SLACK,
                channel,
                messageRef,
                parentRef,
                new MemberRef(msg.externalSenderId()),
                new ChatContent(msg.content(), null, msg.attachments(),
                        parseBlocks(msg.metadata().get("slack-blocks"))),
                msg.receivedAt());
    }

    static List<RichCard> parseBlocks(final String blocksJson) {
        if (blocksJson == null || blocksJson.isBlank()) {
            return List.of();
        }
        try {
            final JsonArray array = Json.createReader(new StringReader(blocksJson)).readArray();
            final List<RichCard> cards = new ArrayList<>();
            for (final JsonValue jv : array) {
                final JsonObject block = jv.asJsonObject();
                final String type = block.getString("type", "");
                switch (type) {
                    case "header" -> {
                        final String text = extractBlockText(block);
                        if (text != null) cards.add(RichCard.builder().title(text).build());
                    }
                    case "section" -> {
                        final var builder = RichCard.builder();
                        final String text = extractBlockText(block);
                        if (text != null) builder.description(text);
                        if (block.containsKey("fields")) {
                            final List<RichCard.Field> fields = new ArrayList<>();
                            for (final JsonValue fv : block.getJsonArray("fields")) {
                                final JsonObject fo = fv.asJsonObject();
                                fields.add(new RichCard.Field(
                                        "", fo.getString("text", ""), false));
                            }
                            builder.fields(fields);
                        }
                        if (text != null || block.containsKey("fields")) {
                            cards.add(builder.build());
                        }
                    }
                    default -> { /* skip divider, image, actions, etc. */ }
                }
            }
            return List.copyOf(cards);
        } catch (final Exception e) {
            LOG.warning("Failed to parse slack-blocks: " + e.getMessage());
            return List.of();
        }
    }

    private static String extractBlockText(final JsonObject block) {
        if (!block.containsKey("text") || block.isNull("text")) return null;
        return block.getJsonObject("text").getString("text", null);
    }
}
