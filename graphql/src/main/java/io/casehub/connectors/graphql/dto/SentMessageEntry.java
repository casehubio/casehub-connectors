package io.casehub.connectors.graphql.dto;

import java.time.Instant;

public record SentMessageEntry(
        String connectorId,
        String destination,
        String body,
        Instant sentAt) {
}
