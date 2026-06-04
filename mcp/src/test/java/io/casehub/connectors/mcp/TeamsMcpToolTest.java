package io.casehub.connectors.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.teams.TeamsConnector;
import io.casehub.connectors.mcp.McpToolTestSupport.RecordingBridge;
import io.casehub.connectors.mcp.McpToolTestSupport.RecordingConnector;

class TeamsMcpToolTest {

    private final RecordingConnector connector = new RecordingConnector(TeamsConnector.ID);
    private final RecordingBridge bridge = new RecordingBridge();
    private final TeamsMcpTool tool = new TeamsMcpTool(
            McpToolTestSupport.serviceWith(connector), bridge);

    @BeforeEach void reset() { connector.reset(); bridge.reset(); }

    @Test
    void sendTeams_dispatches_withCorrectMessage() {
        String result = tool.sendTeams(
                "https://company.webhook.office.com/webhookb2/...", "Deploy", "v2.3 deployed");

        assertThat(result).startsWith("Dispatched to");
        assertThat(connector.lastMessage.destination())
                .isEqualTo("https://company.webhook.office.com/webhookb2/...");
        assertThat(connector.lastMessage.title()).isEqualTo("Deploy");
        assertThat(connector.lastMessage.body()).isEqualTo("v2.3 deployed");
    }

    @Test
    void sendTeams_callsBridge_withTeamsConnectorId() {
        tool.sendTeams("https://company.webhook.office.com/x", "T", "B");
        assertThat(bridge.lastConnectorId).isEqualTo(TeamsConnector.ID);
    }

    @Test
    void sendTeams_notRegistered_returnsFailedString() {
        var failTool = new TeamsMcpTool(McpToolTestSupport.serviceWith(), bridge);
        assertThat(failTool.sendTeams("https://x.com", "T", "B")).startsWith("Failed:");
        assertThat(bridge.lastConnectorId).isNull();
    }
}
