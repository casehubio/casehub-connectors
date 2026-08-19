package io.casehub.connectors.graphql;

import io.casehub.connectors.graphql.dto.ConnectorStatusResult;
import io.casehub.connectors.graphql.dto.InjectChatResult;
import io.casehub.connectors.graphql.dto.SendNotificationResult;
import io.casehub.connectors.graphql.dto.SentMessageEntry;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.List;
import java.util.Map;

@McpDomain("connectors")
public interface ConnectorOperations {

    @PlatformMutation("Inject a chat message as if a customer sent it")
    InjectChatResult injectChat(String platform, String sender,
                                String channel, String text);

    @PlatformMutation("Send a notification via a named connector")
    SendNotificationResult sendNotification(String connectorId, String destination,
                                            String body, String title,
                                            Map<String, String> attributes);

    @PlatformQuery("List registered connectors, chat platforms, and their capabilities")
    ConnectorStatusResult connectorStatus();

    @PlatformQuery("Retrieve recently sent messages for verification")
    List<SentMessageEntry> sentMessages(String connectorId, Integer limit);
}
