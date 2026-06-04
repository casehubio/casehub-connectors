package io.casehub.connectors;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.All;

/**
 * Routes outbound messages to the appropriate {@link Connector} by id.
 *
 * <p>
 * All registered {@link Connector} CDI beans are discovered at startup and indexed
 * by id. Duplicate ids cause startup failure. Unknown ids throw
 * {@link IllegalArgumentException} with the set of available ids in the message.
 *
 * <p>
 * Callers should inject this service rather than working with {@link Connector}
 * beans directly.
 */
@ApplicationScoped
public class ConnectorService {

    private final Map<String, Connector> registry;

    public ConnectorService(@All final List<Connector> connectors) {
        this.registry = connectors.stream()
                .collect(Collectors.toMap(
                        Connector::id,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate connector id: '" + a.id() + "'");
                        }));
    }

    /**
     * Send a message via the named connector.
     *
     * @param connectorId id of the connector to use (e.g. {@code "slack"})
     * @param message     the message to deliver
     * @throws IllegalArgumentException if no connector is registered for {@code connectorId}
     */
    public void send(final String connectorId, final ConnectorMessage message) {
        final Connector connector = registry.get(connectorId);
        if (connector == null) {
            throw new IllegalArgumentException(
                    "No connector registered for id '" + connectorId
                    + "'. Available: " + registry.keySet());
        }
        connector.send(message);
    }

    /**
     * Returns {@code true} if a connector with the given id is registered.
     */
    public boolean supports(final String connectorId) {
        return registry.containsKey(connectorId);
    }

    /**
     * Returns the ids of all registered connectors.
     */
    public Set<String> ids() {
        return Set.copyOf(registry.keySet());
    }
}
