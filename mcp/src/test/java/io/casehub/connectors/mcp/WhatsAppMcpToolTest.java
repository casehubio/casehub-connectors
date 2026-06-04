package io.casehub.connectors.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.connectors.whatsapp.WhatsAppConnector;
import io.casehub.connectors.mcp.McpToolTestSupport.RecordingBridge;
import io.casehub.connectors.mcp.McpToolTestSupport.RecordingConnector;

class WhatsAppMcpToolTest {

    private final RecordingConnector connector = new RecordingConnector(WhatsAppConnector.ID);
    private final RecordingBridge bridge = new RecordingBridge();
    private final WhatsAppMcpTool tool = new WhatsAppMcpTool(
            McpToolTestSupport.serviceWith(connector), bridge);

    @BeforeEach void reset() { connector.reset(); bridge.reset(); }

    @Test
    void sendWhatsApp_noTemplate_sendsTextMessage() {
        String result = tool.sendWhatsApp("+447700900000", "Hello!", null, null);

        assertThat(result).isEqualTo("Dispatched to +447700900000");
        assertThat(connector.lastMessage.destination()).isEqualTo("+447700900000");
        assertThat(connector.lastMessage.body()).isEqualTo("Hello!");
        assertThat(connector.lastMessage.attributes()).doesNotContainKey("templateName");
    }

    @Test
    void sendWhatsApp_withTemplate_passesTemplateNameInAttributes() {
        tool.sendWhatsApp("+447700900000", null, "hello_world", null);

        assertThat(connector.lastMessage.attributes()).containsEntry("templateName", "hello_world");
    }

    @Test
    void sendWhatsApp_blankTemplate_treatedAsNoTemplate() {
        tool.sendWhatsApp("+447700900000", "hi", "", null);

        assertThat(connector.lastMessage.attributes()).doesNotContainKey("templateName");
    }

    @Test
    void sendWhatsApp_callsBridge_withWhatsAppConnectorId() {
        tool.sendWhatsApp("+447700900000", "hi", null, null);
        assertThat(bridge.lastConnectorId).isEqualTo(WhatsAppConnector.ID);
        assertThat(bridge.lastDestination).isEqualTo("+447700900000");
    }

    @Test
    void sendWhatsApp_notRegistered_returnsFailedString() {
        var failTool = new WhatsAppMcpTool(McpToolTestSupport.serviceWith(), bridge);
        assertThat(failTool.sendWhatsApp("+447700900000", "hi", null, null)).startsWith("Failed:");
    }

    @Test
    void sendWhatsApp_withTemplateLanguage_passesLanguageInAttributes() {
        tool.sendWhatsApp("+447700900000", null, "hello_world", "es_MX");

        assertThat(connector.lastMessage.attributes())
                .containsEntry("templateName", "hello_world")
                .containsEntry("templateLanguage", "es_MX");
    }
}
