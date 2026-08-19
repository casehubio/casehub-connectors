package io.casehub.connectors.graphql.dto;

public record SendNotificationResult(boolean ok, String connectorId, String destination) {
}
