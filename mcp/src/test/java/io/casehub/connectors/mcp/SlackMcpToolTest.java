package io.casehub.connectors.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.slack.SlackConnector;
import io.casehub.connectors.mcp.McpToolTestSupport.RecordingBridge;
import io.casehub.connectors.mcp.McpToolTestSupport.RecordingConnector;

class SlackMcpToolTest {

    private final RecordingConnector connector = new RecordingConnector(SlackConnector.ID);
    private final RecordingBridge bridge = new RecordingBridge();
    private final SlackMcpTool tool = new SlackMcpTool(
            McpToolTestSupport.serviceWith(connector), bridge);

    @BeforeEach
    void reset() {
        connector.reset();
        bridge.reset();
    }

    @Test
    void sendSlack_dispatches_toSlackConnectorWithCorrectMessage() {
        String result = tool.sendSlack(
                "https://hooks.slack.com/services/T/B/X", "Alert", "Server is down");

        assertThat(result).isEqualTo("Dispatched to https://hooks.slack.com/services/T/B/X");
        assertThat(connector.lastMessage.destination())
                .isEqualTo("https://hooks.slack.com/services/T/B/X");
        assertThat(connector.lastMessage.title()).isEqualTo("Alert");
        assertThat(connector.lastMessage.body()).isEqualTo("Server is down");
    }

    @Test
    void sendSlack_callsBridge_withConnectorIdDestinationAndSanitizedContent() {
        tool.sendSlack("https://hooks.slack.com/services/T/B/X", "T", "line1\nline2");

        assertThat(bridge.lastConnectorId).isEqualTo(SlackConnector.ID);
        assertThat(bridge.lastDestination).isEqualTo("https://hooks.slack.com/services/T/B/X");
        assertThat(bridge.lastContent).isEqualTo("line1 line2");
    }

    @Test
    void sendSlack_connectorNotRegistered_returnsFailedString() {
        var emptyService = McpToolTestSupport.serviceWith(); // no connectors
        var failTool = new SlackMcpTool(emptyService, bridge);

        String result = failTool.sendSlack("https://hooks.slack.com/services/T/B/X", "T", "B");

        assertThat(result).startsWith("Failed:");
        assertThat(bridge.lastConnectorId).isNull(); // bridge not called on failure
    }

    @Test
    void sendSlack_longBody_contentTruncatedTo500InBridge() {
        String longBody = "x".repeat(600);
        tool.sendSlack("https://hooks.slack.com/services/T/B/X", "T", longBody);

        assertThat(bridge.lastContent).hasSize(500);
        assertThat(connector.lastMessage.body()).hasSize(600); // original body passed to connector
    }
}
