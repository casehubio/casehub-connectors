package io.casehub.connectors.graphql.dto;

import java.util.List;

public record ConnectorStatusResult(
        List<OutboundConnectorInfo> outbound,
        List<ChatPlatformInfo> chatPlatforms,
        List<InboundConnectorInfo> inboundConnectors) {
}
