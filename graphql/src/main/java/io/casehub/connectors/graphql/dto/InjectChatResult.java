package io.casehub.connectors.graphql.dto;

public record InjectChatResult(boolean ok, String connectorType, String channel) {
}
