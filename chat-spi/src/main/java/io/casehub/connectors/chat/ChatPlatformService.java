package io.casehub.connectors.chat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.All;

import io.casehub.connectors.chat.spi.ChatPlatform;

/**
 * Routes chat platform operations to the appropriate {@link ChatPlatform} by id.
 *
 * <p>
 * All registered {@link ChatPlatform} CDI beans are discovered at startup and indexed
 * by id. Duplicate ids cause startup failure. Unknown ids throw
 * {@link IllegalArgumentException} with the set of available ids in the message.
 *
 * <p>
 * Callers should inject this service rather than working with {@link ChatPlatform}
 * beans directly.
 */
@ApplicationScoped
public class ChatPlatformService {

    private final Map<String, ChatPlatform> registry;

    public ChatPlatformService(@All final List<ChatPlatform> platforms) {
        this.registry = platforms.stream()
                .collect(Collectors.toMap(
                        ChatPlatform::id,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate chat platform id: '" + a.id() + "'");
                        }));
    }

    /**
     * Get the chat platform for the given id.
     *
     * @param id id of the chat platform (e.g. {@code "slack"}, {@code "ref"})
     * @return the chat platform instance
     * @throws IllegalArgumentException if no platform is registered for {@code id}
     */
    public ChatPlatform platform(final String id) {
        final ChatPlatform platform = registry.get(id);
        if (platform == null) {
            throw new IllegalArgumentException(
                    "No chat platform registered for id '" + id
                    + "'. Available: " + registry.keySet());
        }
        return platform;
    }

    /**
     * Returns {@code true} if a chat platform with the given id is registered.
     */
    public boolean supports(final String id) {
        return registry.containsKey(id);
    }

    /**
     * Returns the ids of all registered chat platforms.
     */
    public Set<String> ids() {
        return Set.copyOf(registry.keySet());
    }
}
