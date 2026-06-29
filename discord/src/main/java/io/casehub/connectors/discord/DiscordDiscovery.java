package io.casehub.connectors.discord;

import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehub.connectors.ConnectorDiscovery;
import io.casehub.connectors.DiscoveredTarget;

/**
 * Implements {@link ConnectorDiscovery} for Discord.
 *
 * <p>Discovers all text channels (type 0), announcement channels (type 5), and
 * threads (types 10, 11, 12) in the configured guild.
 * Voice channels (type 2), categories (type 4), and forum channels (type 15) are excluded.
 */
@ApplicationScoped
public class DiscordDiscovery implements ConnectorDiscovery {

    public static final String ID = "discord";

    private static final Set<Integer> TEXT_CHANNEL_TYPES = Set.of(0, 5, 10, 11, 12);

    private final DiscordClient client;
    private final String token;
    private final String guildId;

    @Inject
    DiscordDiscovery(final DiscordClient client,
                     @ConfigProperty(name = "casehub.discord.token",
                                     defaultValue = "") final String token,
                     @ConfigProperty(name = "casehub.discord.guild-id",
                                     defaultValue = "") final String guildId) {
        this.client = client;
        this.token = token;
        this.guildId = guildId;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<DiscoveredTarget> discover() {
        if (token.isBlank() || guildId.isBlank()) {
            return List.of();
        }
        final var channels = client.listGuildChannels(token);
        if (channels == null) {
            return List.of();
        }
        return channels.stream()
                .filter(ch -> TEXT_CHANNEL_TYPES.contains(ch.type()))
                .map(ch -> new DiscoveredTarget(ch.id(), "#" + ch.name()))
                .toList();
    }
}
