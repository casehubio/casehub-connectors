package io.casehub.connectors.graphql;

import io.casehub.connectors.SentMessage;
import io.casehub.connectors.graphql.dto.SentMessageEntry;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@ApplicationScoped
@UnlessBuildProfile("prod")
public class SentMessageCapture {

    private final Deque<SentMessageEntry> buffer = new ConcurrentLinkedDeque<>();
    static final int MAX_SIZE = 500;

    void onSent(@ObservesAsync final SentMessage event) {
        buffer.addFirst(new SentMessageEntry(
                event.connectorId(), event.destination(),
                event.body(), event.sentAt()));
        while (buffer.size() > MAX_SIZE) {
            buffer.removeLast();
        }
    }

    public List<SentMessageEntry> query(final String connectorId, final int limit) {
        return buffer.stream()
                .filter(e -> connectorId == null || connectorId.equals(e.connectorId()))
                .limit(limit)
                .toList();
    }
}
