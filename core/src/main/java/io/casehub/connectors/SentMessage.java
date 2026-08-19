package io.casehub.connectors;

import java.time.Instant;

public record SentMessage(
        String connectorId,
        String destination,
        String title,
        String body,
        Instant sentAt,
        boolean success) {
}
