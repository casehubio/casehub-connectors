package io.casehub.connectors.chat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.arc.All;

import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.chat.model.ReceivedMessage;
import io.casehub.connectors.chat.spi.InboundTranslator;

@ApplicationScoped
public class ChatInboundAdapter {

    private static final Logger LOG = Logger.getLogger(ChatInboundAdapter.class);

    private final Map<String, InboundTranslator> translators;
    private final Event<ReceivedMessage> receivedEvent;

    @Inject
    ChatInboundAdapter(@All final List<InboundTranslator> translators,
                       final Event<ReceivedMessage> receivedEvent) {
        this.translators = translators.stream()
                .collect(Collectors.toMap(
                        InboundTranslator::connectorType,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate connectorType: '" + a.connectorType() + "'");
                        }));
        this.receivedEvent = receivedEvent;
    }

    public void onMessage(@ObservesAsync final InboundMessage msg) {
        final InboundTranslator translator = translators.get(msg.connectorType());
        if (translator != null) {
            try {
                receivedEvent.fireAsync(translator.translate(msg));
            } catch (final Exception e) {
                LOG.warnf("ChatInboundAdapter: translation failed for %s: %s",
                        msg.connectorType(), e.getMessage());
            }
        }
    }
}
