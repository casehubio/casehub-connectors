package io.casehub.connectors;

import org.junit.jupiter.api.Test;

class NoOpConnectorMeshBridgeTest {

    private final ConnectorMeshBridge bridge = new NoOpConnectorMeshBridge();

    @Test
    void notifyDelivered_doesNotThrow_forNormalInput() {
        bridge.notifyDelivered("slack", "https://hooks.slack.com/T/B/X", "hello");
    }

    @Test
    void notifyDelivered_doesNotThrow_forNullContent() {
        bridge.notifyDelivered("email", "user@example.com", null);
    }

    @Test
    void notifyDelivered_doesNotThrow_forBlankInputs() {
        bridge.notifyDelivered("", "", "");
    }
}
