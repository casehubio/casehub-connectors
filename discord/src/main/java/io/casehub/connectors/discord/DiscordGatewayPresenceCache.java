package io.casehub.connectors.discord;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * In-memory cache of Discord user presence statuses.
 *
 * <p>Stores raw Discord status strings ("online", "idle", "dnd", "offline", "unknown")
 * to keep the {@code discord} module independent of {@code chat-spi}.
 * The mapping to {@link io.casehub.connectors.chat.model.PresenceStatus} happens in
 * {@code chat-discord}.
 */
@ApplicationScoped
public class DiscordGatewayPresenceCache {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Updates the presence status for a user.
     *
     * @param userId Discord user ID (snowflake)
     * @param status Discord status string: "online", "idle", "dnd", "offline"
     */
    public void update(final String userId, final String status) {
        cache.put(userId, status);
    }

    /**
     * Retrieves the presence status for a user.
     *
     * @param userId Discord user ID (snowflake)
     * @return status string, or "unknown" if not found
     */
    public String get(final String userId) {
        return cache.getOrDefault(userId, "unknown");
    }

    /**
     * Clears all cached presence data.
     */
    public void clear() {
        cache.clear();
    }
}
