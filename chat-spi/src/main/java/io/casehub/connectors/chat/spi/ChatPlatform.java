package io.casehub.connectors.chat.spi;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.casehub.connectors.chat.degraded.ChannelFallbackThreading;
import io.casehub.connectors.chat.degraded.EmptyDiscovery;
import io.casehub.connectors.chat.degraded.EmptyMembers;
import io.casehub.connectors.chat.degraded.NoOpReactions;
import io.casehub.connectors.chat.degraded.UnknownPresence;

/**
 * SPI for chat platform integration.
 *
 * <p>
 * Implementations are CDI {@code @ApplicationScoped} beans discoverable at startup.
 * The {@link #id()} string identifies the platform type and is used to route
 * operations to the correct implementation.
 *
 * <p>
 * Built-in implementations: {@code "ref"} (reference implementation for testing).
 *
 * <p>
 * Custom chat platforms: provide a CDI {@code @ApplicationScoped} bean implementing this
 * interface. It will be discovered automatically.
 *
 * <h2>Contract</h2>
 * <ul>
 * <li>All capability methods ({@link #messaging()}, {@link #threading()}, etc.) must not
 *     return null. If a capability is not natively supported, return a degraded
 *     implementation (e.g. {@link ChannelFallbackThreading}, {@link EmptyDiscovery}).</li>
 * <li>Operations may throw unchecked exceptions on failure — callers handle retries and logging.</li>
 * <li>Operations may block briefly (HTTP call) but should complete within their configured
 *     timeout. Callers are responsible for async dispatch if needed.</li>
 * <li>All methods must be thread-safe — they may be called from multiple threads.</li>
 * </ul>
 */
public interface ChatPlatform {

    /**
     * Unique identifier for this chat platform.
     * Examples: {@code "slack"}, {@code "teams"}, {@code "ref"}.
     *
     * @return the platform type string; must not be null or blank
     */
    String id();

    /**
     * Returns the messaging capability for sending messages to channels.
     *
     * @return the messaging implementation; never null
     */
    Messaging messaging();

    /**
     * Returns the threading capability for sending threaded replies.
     *
     * @return the threading implementation; never null (may be degraded)
     */
    Threading threading();

    /**
     * Returns the discovery capability for listing channels.
     *
     * @return the discovery implementation; never null (may be degraded)
     */
    Discovery discovery();

    /**
     * Returns the reactions capability for adding/removing emoji reactions.
     *
     * @return the reactions implementation; never null (may be degraded)
     */
    Reactions reactions();

    /**
     * Returns the presence capability for querying member online status.
     *
     * @return the presence implementation; never null (may be degraded)
     */
    Presence presence();

    /**
     * Returns the members capability for listing channel members.
     *
     * @return the members implementation; never null (may be degraded)
     */
    Members members();

    /**
     * Returns {@code true} if this platform natively supports the given capability.
     * Returns {@code false} if the capability is degraded or emulated.
     *
     * @param capability the capability class (e.g. {@code Threading.class})
     * @return {@code true} if natively supported, {@code false} otherwise
     */
    boolean supports(Class<?> capability);

    static Builder builder(final String id) {
        return new Builder(id);
    }

    class Builder {
        private final String id;
        private Messaging messaging;
        private Threading threading;
        private Discovery discovery;
        private Reactions reactions;
        private Presence presence;
        private Members members;
        private final Set<Class<?>> nativeCapabilities = new HashSet<>();

        Builder(final String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder messaging(final Messaging m) { this.messaging = m; nativeCapabilities.add(Messaging.class); return this; }
        public Builder threading(final Threading t) { this.threading = t; nativeCapabilities.add(Threading.class); return this; }
        public Builder discovery(final Discovery d) { this.discovery = d; nativeCapabilities.add(Discovery.class); return this; }
        public Builder reactions(final Reactions r) { this.reactions = r; nativeCapabilities.add(Reactions.class); return this; }
        public Builder presence(final Presence p) { this.presence = p; nativeCapabilities.add(Presence.class); return this; }
        public Builder members(final Members m) { this.members = m; nativeCapabilities.add(Members.class); return this; }

        public ChatPlatform build() {
            Objects.requireNonNull(messaging, "messaging is required");
            return new DefaultChatPlatform(
                    id,
                    messaging,
                    threading != null ? threading : new ChannelFallbackThreading(messaging),
                    discovery != null ? discovery : new EmptyDiscovery(),
                    reactions != null ? reactions : new NoOpReactions(),
                    presence != null ? presence : new UnknownPresence(),
                    members != null ? members : new EmptyMembers(),
                    Set.copyOf(nativeCapabilities));
        }
    }
}
