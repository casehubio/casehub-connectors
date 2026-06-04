package io.casehub.connectors.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.twilio.TwilioSmsConnector;
import io.casehub.connectors.mcp.McpToolTestSupport.RecordingBridge;
import io.casehub.connectors.mcp.McpToolTestSupport.RecordingConnector;

class TwilioSmsMcpToolTest {

    private final RecordingConnector connector = new RecordingConnector(TwilioSmsConnector.ID);
    private final RecordingBridge bridge = new RecordingBridge();
    private final TwilioSmsMcpTool tool = new TwilioSmsMcpTool(
            McpToolTestSupport.serviceWith(connector), bridge);

    @BeforeEach void reset() { connector.reset(); bridge.reset(); }

    @Test
    void sendSms_dispatches_withE164NumberAsDestination() {
        String result = tool.sendSms("+447700900000", "Your code is 123456");

        assertThat(result).isEqualTo("Dispatched to +447700900000");
        assertThat(connector.lastMessage.destination()).isEqualTo("+447700900000");
        assertThat(connector.lastMessage.body()).isEqualTo("Your code is 123456");
        assertThat(connector.lastMessage.title()).isNull();
    }

    @Test
    void sendSms_callsBridge_withTwilioConnectorIdAndPhoneNumber() {
        tool.sendSms("+447700900000", "Hello");
        assertThat(bridge.lastConnectorId).isEqualTo(TwilioSmsConnector.ID);
        assertThat(bridge.lastDestination).isEqualTo("+447700900000");
    }

    @Test
    void sendSms_notRegistered_returnsFailedString() {
        var failTool = new TwilioSmsMcpTool(McpToolTestSupport.serviceWith(), bridge);
        assertThat(failTool.sendSms("+447700900000", "Hi")).startsWith("Failed:");
        assertThat(bridge.lastConnectorId).isNull();
    }
}
