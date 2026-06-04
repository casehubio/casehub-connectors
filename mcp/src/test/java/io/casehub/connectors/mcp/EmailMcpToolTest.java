package io.casehub.connectors.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.email.EmailConnector;
import io.casehub.connectors.mcp.McpToolTestSupport.RecordingBridge;
import io.casehub.connectors.mcp.McpToolTestSupport.RecordingConnector;

class EmailMcpToolTest {

    private final RecordingConnector connector = new RecordingConnector(EmailConnector.ID);
    private final RecordingBridge bridge = new RecordingBridge();
    private final EmailMcpTool tool = new EmailMcpTool(
            McpToolTestSupport.serviceWith(connector), bridge);

    @BeforeEach void reset() { connector.reset(); bridge.reset(); }

    @Test
    void sendEmail_dispatches_withCorrectFields() {
        String result = tool.sendEmail("user@example.com", "Deploy complete", "v2.3 is live");

        assertThat(result).isEqualTo("Dispatched to user@example.com");
        assertThat(connector.lastMessage.destination()).isEqualTo("user@example.com");
        assertThat(connector.lastMessage.title()).isEqualTo("Deploy complete");
        assertThat(connector.lastMessage.body()).isEqualTo("v2.3 is live");
    }

    @Test
    void sendEmail_callsBridge_withEmailConnectorId() {
        tool.sendEmail("user@example.com", "S", "B");
        assertThat(bridge.lastConnectorId).isEqualTo(EmailConnector.ID);
        assertThat(bridge.lastDestination).isEqualTo("user@example.com");
    }

    @Test
    void sendEmail_notRegistered_returnsFailedString() {
        var failTool = new EmailMcpTool(McpToolTestSupport.serviceWith(), bridge);
        assertThat(failTool.sendEmail("user@example.com", "S", "B")).startsWith("Failed:");
        assertThat(bridge.lastConnectorId).isNull();
    }
}
