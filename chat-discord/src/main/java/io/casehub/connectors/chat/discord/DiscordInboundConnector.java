package io.casehub.connectors.chat.discord;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.casehub.connectors.InboundConnector;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.InboundMessageSink;
import io.casehub.connectors.discord.DiscordClient;
import io.casehub.connectors.discord.DiscordGateway;
import io.casehub.connectors.discord.DiscordGatewayPresenceCache;
import io.casehub.connectors.discord.GatewayEventListener;

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
    private final String guildId;

    private volatile DiscordGateway gateway;
    private volatile boolean stopping = false;

    @Inject
    public DiscordInboundConnector(
            final DiscordClient client,
            final DiscordGatewayPresenceCache presenceCache,
            @ConfigProperty(name = "casehub.discord.token", defaultValue = "")
            final String token,
            @ConfigProperty(name = "casehub.discord.guild-id", defaultValue = "")
            final String guildId) {
        this.client = client;
        this.presenceCache = presenceCache;
        this.token = token;
        this.guildId = guildId;
    }

    @Override
    public String id() {
        return InboundConnectorIds.DISCORD_INBOUND;
    }

    @Override
    public void start(final InboundMessageSink sink) {
        if (token.isBlank() || guildId.isBlank()) {
            LOG.warning("discord-inbound: token or guild-id not configured, connector inactive");
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

    private void handleEvent(final String eventType, final JsonNode data,
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
        // Filter bot messages
        JsonNode author = data.get("author");
        if (author != null && author.has("bot") && author.get("bot").asBoolean()) {
            return;
        }

        // Filter to types 0 (DEFAULT) and 19 (REPLY) only
        int type = data.has("type") ? data.get("type").asInt() : 0;
        if (type != 0 && type != 19) {
            return; // skip system messages
        }

        String messageId = data.get("id").asText();
        String channelId = data.get("channel_id").asText();
        String content = data.has("content") ? data.get("content").asText() : "";
        String senderId = author.get("id").asText();

        Map<String, String> metadata = new java.util.HashMap<>();
        metadata.put("discord-message-id", messageId);
        metadata.put("discord-guild-id", guildId);

        // Extract reply reference if present (type 19)
        if (type == 19 && data.has("message_reference")) {
            JsonNode ref = data.get("message_reference");
            if (ref.has("message_id")) {
                metadata.put("discord-reference-id", ref.get("message_id").asText());
            }
        }

        InboundMessage msg = new InboundMessage(
                InboundConnectorIds.DISCORD_INBOUND,
                io.casehub.connectors.InboundConnectorTypes.DISCORD,
                senderId,
                channelId,
                content,
                List.of(), // attachments deferred
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
        // Seed presence cache from GUILD_CREATE
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
