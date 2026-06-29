package io.casehub.connectors.discord;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Callback for Discord Gateway DISPATCH events.
 *
 * <p>Invoked on the WebSocket listener thread — implementations must not block.
 */
@FunctionalInterface
public interface GatewayEventListener {
    void onEvent(String eventType, JsonNode data);
}
