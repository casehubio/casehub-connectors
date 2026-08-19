package io.casehub.connectors;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ConnectorService {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(ConnectorService.class.getName());

    private final Map<String, Connector>                   registry;
    private final java.util.function.Consumer<SentMessage> eventSink;

    @jakarta.inject.Inject
    public ConnectorService(@All final List<Connector> connectors,
                            final jakarta.enterprise.event.Event<SentMessage> sentMessageEvent) {
        this(connectors, msg -> sentMessageEvent.fireAsync(msg)
                                                .exceptionally(ex -> {
                                                    LOG.log(java.util.logging.Level.SEVERE, "Async SentMessage dispatch failed", ex);
                                                    return null;
                                                }));
    }

    ConnectorService(final List<Connector> connectors,
                     final java.util.function.Consumer<SentMessage> eventSink) {
        this.eventSink = eventSink;
        this.registry  = connectors.stream()
                                   .collect(java.util.stream.Collectors.toMap(
                                           Connector::id,
                                           java.util.function.Function.identity(),
                                           (a, b) -> {
                                               throw new IllegalStateException(
                                                       "Duplicate connector id: '" + a.id() + "'");
                                           }));
    }

    public static ConnectorService withEventSink(final List<Connector> connectors, final java.util.function.Consumer<SentMessage> eventSink) {
        return new ConnectorService(connectors, eventSink);
    }


    public boolean send(final String connectorId, final ConnectorMessage message) {
        final Connector connector = registry.get(connectorId);
        if (connector == null) {
            throw new IllegalArgumentException(
                    "No connector registered for id '" + connectorId
                    + "'. Available: " + registry.keySet());
        }
        boolean success = connector.send(message);
        eventSink.accept(new SentMessage(
                connectorId, message.destination(), message.title(),
                message.body(), java.time.Instant.now(), success));
        return success;
    }

    public boolean supports(final String connectorId) {
        return registry.containsKey(connectorId);
    }

    public Set<String> ids() {
        return Set.copyOf(registry.keySet());
    }
}
