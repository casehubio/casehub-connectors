package io.casehub.connectors.graphql;

import io.casehub.connectors.ConnectorService;
import io.casehub.connectors.InboundConnectorService;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.ModelEnricher;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@McpDomain("connectors")
@ApplicationScoped
public class ConnectorsModelEnricher implements ModelEnricher {

    @Inject ConnectorService connectorService;
    @Inject ChatPlatformService chatPlatformService;
    @Inject InboundConnectorService inboundConnectorService;
    @Inject @All List<WebhookInboundConnector> webhookConnectors;

    @Override
    public String summary() {
        return "Connector infrastructure — inject inbound chat messages, "
             + "send outbound notifications, query connector status "
             + "and sent message history.";
    }

    @Override
    public Map<String, Object> state() {
        return Map.of(
                "outboundConnectors", connectorService.ids().size(),
                "chatPlatforms", chatPlatformService.ids().size(),
                "inboundConnectors", inboundConnectorService.pullIds().size()
                        + webhookConnectors.size());
    }
}
