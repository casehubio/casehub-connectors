package io.casehub.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InboundConnectorTypesTest {

    @Test
    void constants_areStableSemanticLabels() {
        assertThat(InboundConnectorTypes.SLACK).isEqualTo("slack");
        assertThat(InboundConnectorTypes.EMAIL).isEqualTo("email");
        assertThat(InboundConnectorTypes.SMS).isEqualTo("sms");
        assertThat(InboundConnectorTypes.WHATSAPP).isEqualTo("whatsapp");
        assertThat(InboundConnectorTypes.TEAMS).isEqualTo("teams");
    }

    @Test
    void types_doNotContainProviderOrDirectionSuffix() {
        assertThat(InboundConnectorTypes.SMS).doesNotContain("twilio");
        assertThat(InboundConnectorTypes.SMS).doesNotContain("inbound");
        assertThat(InboundConnectorTypes.SLACK).doesNotContain("inbound");
    }
}
