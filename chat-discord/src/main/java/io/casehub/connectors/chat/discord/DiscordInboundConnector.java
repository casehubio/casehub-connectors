package io.casehub.connectors.chat.discord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.casehub.connectors.Attachment;
import io.casehub.connectors.InboundConnector;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.InboundMessageSink;
import io.casehub.connectors.discord.DiscordClient;
import io.casehub.connectors.discord.DiscordGateway;
import io.casehub.connectors.discord.DiscordGatewayPresenceCache;
import io.casehub.connectors.discord.GatewayEventListener;
import io.casehub.connectors.discord.model.DiscordAttachment;

@ApplicationScoped
public class DiscordInboundConnector implements InboundConnector {

    private static final Logger LOG = Logger.getLogger(
            DiscordInboundConnector.class.getName());

    // Gateway intents bitmask:
    // GUILDS (1 << 0) | GUILD_MEMBERS (1 << 1) | GUILD_PRESENCES (1 << 8) |
    // GUILD_MESSAGES (1 << 9) | GUILD_MESSAGE_REACTIONS (1 << 10) | MESSAGE_CONTENT (1 << 15)
    private static final int INTENTS = (1 << 0) | (1 << 1) | (1 << 8) | (1 << 9) | (1 << 10) | (1 << 15);

    private final DiscordClient client;
    private final DiscordGatewayPresenceCache presenceCache;
    private final String token;

    private volatile DiscordGateway gateway;
    private volatile boolean stopping = false;

    @Inject
    public DiscordInboundConnector(
            final DiscordClient client,
            final DiscordGatewayPresenceCache presenceCache,
            @ConfigProperty(name = "casehub.discord.token", defaultValue = "")
            final String token) {
        this.client = client;
        this.presenceCache = presenceCache;
        this.token = token;
    }

    @Override
    public String id() {
        return InboundConnectorIds.DISCORD_INBOUND;
    }

    @Override
    public void start(final InboundMessageSink sink) {
        if (token.isBlank()) {
            LOG.warning("discord-inbound: token not configured, connector inactive");
            return;
        }

        if (gateway != null) {
            return; // already started
        }

        try {
            String gatewayUrl = client.getGatewayUrl(token);
            if (gatewayUrl == null) {
                LOG.severe("discord-inbound: failed to get Gateway URL");
                return;
            }

            gateway = new DiscordGateway();
            GatewayEventListener listener = (eventType, data) -> handleEvent(eventType, data, sink);
            gateway.connect(gatewayUrl, token, INTENTS, listener);
            LOG.info("discord-inbound: Gateway connection started");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "discord-inbound: failed to start Gateway", e);
        }
    }

    @Override
    public void stop() {
        stopping = true;
        if (gateway != null) {
            gateway.disconnect();
            gateway = null;
        }
    }

    void handleEvent(final String eventType, final JsonNode data,
                     final InboundMessageSink sink) {
        if (stopping) return;

        try {
            switch (eventType) {
                case "MESSAGE_CREATE":
                    handleMessageCreate(data, sink);
                    break;
                case "GUILD_CREATE":
                    handleGuildCreate(data);
                    break;
                case "PRESENCE_UPDATE":
                    handlePresenceUpdate(data);
                    break;
                default:
                    // ignore other events
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "discord-inbound: error handling " + eventType, e);
        }
    }

    private void handleMessageCreate(final JsonNode data, final InboundMessageSink sink) {
        JsonNode author = data.get("author");
        if (author != null && author.has("bot") && author.get("bot").asBoolean()) {
            return;
        }

        int type = data.has("type") ? data.get("type").asInt() : 0;
        if (type != 0 && type != 19) {
            return;
        }

        final List<DiscordAttachment> discordAttachments =
                client.parseAttachments(data.get("attachments"));

        if (discordAttachments.isEmpty()) {
            deliverMessage(data, List.of(), 0, 0, sink);
        } else {
            Thread.ofVirtual().name("discord-attachment-download").start(() -> {
                try {
                    downloadAndDeliver(data, discordAttachments, sink);
                } catch (final Exception e) {
                    LOG.log(Level.WARNING,
                            "discord-inbound: attachment download failed, delivering without attachments", e);
                    deliverMessage(data, List.of(), discordAttachments.size(),
                            discordAttachments.size(), sink);
                }
            });
        }
    }

    private void downloadAndDeliver(final JsonNode data,
                                    final List<DiscordAttachment> discordAttachments,
                                    final InboundMessageSink sink) {
        final List<Attachment> downloaded = new ArrayList<>();
        int failures = 0;
        for (final DiscordAttachment da : discordAttachments) {
            final Attachment att = client.downloadAttachment(da);
            if (att != null) {
                downloaded.add(att);
            } else {
                failures++;
            }
        }
        deliverMessage(data, downloaded, discordAttachments.size(), failures, sink);
    }

    private void deliverMessage(final JsonNode data, final List<Attachment> attachments,
                                final int attachmentCount, final int downloadFailures,
                                final InboundMessageSink sink) {
        final JsonNode author = data.get("author");
        final String messageId = data.get("id").asText();
        final String channelId = data.get("channel_id").asText();
        final String content = data.has("content") ? data.get("content").asText() : "";
        final String senderId = author.get("id").asText();
        final int type = data.has("type") ? data.get("type").asInt() : 0;

        final Map<String, String> metadata = new java.util.HashMap<>();
        metadata.put("discord-message-id", messageId);
        final String eventGuildId = data.has("guild_id") ? data.get("guild_id").asText() : "unknown";
        metadata.put("discord-guild-id", eventGuildId);

        if (type == 19 && data.has("message_reference")) {
            final JsonNode ref = data.get("message_reference");
            if (ref.has("message_id")) {
                metadata.put("discord-reference-id", ref.get("message_id").asText());
            }
        }

        if (attachmentCount > 0) {
            metadata.put("discord-attachment-count", String.valueOf(attachmentCount));
            metadata.put("discord-attachment-download-failures", String.valueOf(downloadFailures));
        }

        if (data.has("embeds") && data.get("embeds").isArray()
                && !data.get("embeds").isEmpty()) {
            metadata.put("discord-embeds", data.get("embeds").toString());
        }

        final InboundMessage msg = new InboundMessage(
                InboundConnectorIds.DISCORD_INBOUND,
                InboundConnectorTypes.DISCORD,
                senderId,
                channelId,
                content,
                attachments,
                Instant.now(),
                metadata,
                null);

        try {
            sink.receive(msg);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "discord-inbound: sink threw", e);
        }
    }

    private void handleGuildCreate(final JsonNode data) {
        if (!data.has("presences")) {
            return;
        }

        JsonNode presences = data.get("presences");
        if (!presences.isArray()) {
            return;
        }

        for (JsonNode presence : presences) {
            if (presence.has("user") && presence.has("status")) {
                String userId = presence.get("user").get("id").asText();
                String status = presence.get("status").asText();
                presenceCache.update(userId, status);
            }
        }
    }

    private void handlePresenceUpdate(final JsonNode data) {
        if (!data.has("user") || !data.has("status")) {
            return;
        }

        String userId = data.get("user").get("id").asText();
        String status = data.get("status").asText();
        presenceCache.update(userId, status);
    }
}
