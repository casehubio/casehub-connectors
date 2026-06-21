package io.casehub.connectors;

/**
 * SPI — notifies the active mesh implementation that a connector delivery has been
 * dispatched via an MCP tool call.
 *
 * <p>The default implementation ({@link NoOpConnectorMeshBridge}) does nothing.
 * When {@code qhorus/connector-backend} is on the classpath, its implementation
 * activates by classpath presence and posts a {@code STATUS} message to the channel
 * configured via {@code casehub.qhorus.connector-backend.delivery-channel}.
 * If no channel is configured, the implementation is a no-op (casehubio/qhorus#249).
 *
 * <h2>Contract for implementations</h2>
 * <ul>
 * <li>Must return quickly — no blocking network I/O on the calling thread.</li>
 * <li>Must tolerate missing or misconfigured delivery channel without throwing.</li>
 * <li>Must never throw — exceptions propagate to the MCP tool caller.</li>
 * </ul>
 */
public interface ConnectorMeshBridge {

    /**
     * Called after each MCP-initiated connector dispatch.
     *
     * @param connectorId  connector type id — use the connector's {@code ID} constant,
     *                     e.g. {@link io.casehub.connectors.slack.SlackConnector#ID}
     * @param destination  delivery target: webhook URL, E.164 number, email address, or channel ID
     * @param content      message body, pre-sanitized and truncated to 500 chars;
     *                     {@code null} is permitted — implementations must treat it as empty
     */
    void notifyDelivered(String connectorId, String destination, String content);
}
