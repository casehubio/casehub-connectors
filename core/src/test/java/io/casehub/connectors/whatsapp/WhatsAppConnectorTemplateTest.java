package io.casehub.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WhatsAppConnectorTemplateTest {

    // buildPayload is tested directly without HTTP
    @Test
    void buildTextPayload_noTemplate_producesTextType() {
        String json = WhatsAppConnector.buildPayload("+447700900000", "Hello world", null, "en_US");
        assertThat(json).contains("\"type\":\"text\"");
        assertThat(json).contains("\"body\":\"Hello world\"");
        assertThat(json).doesNotContain("template");
    }

    @Test
    void buildTemplatePayload_withTemplateName_producesTemplateType() {
        String json = WhatsAppConnector.buildPayload("+447700900000", null, "hello_world", "en_US");
        assertThat(json).contains("\"type\":\"template\"");
        assertThat(json).contains("\"name\":\"hello_world\"");
        assertThat(json).contains("en_US");
        assertThat(json).doesNotContain("\"type\":\"text\"");
    }

    @Test
    void buildTemplatePayload_blankTemplateName_producesTextType() {
        String json = WhatsAppConnector.buildPayload("+447700900000", "hi", "", "en_US");
        assertThat(json).contains("\"type\":\"text\"");
    }

    @Test
    void buildTemplatePayload_customLanguage_usesProvidedLanguageCode() {
        String json = WhatsAppConnector.buildPayload("+447700900000", null, "hello_world", "es_MX");
        assertThat(json).contains("es_MX");
        assertThat(json).doesNotContain("en_US");
    }
}
