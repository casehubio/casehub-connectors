package io.casehub.connectors.mcp;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMeshBridge;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.ConnectorService;

import java.util.List;

/** Shared test doubles for MCP tool unit tests. */
public final class McpToolTestSupport {

    private McpToolTestSupport() {}

    /** Records the last call to {@link Connector#send(ConnectorMessage)}. */
    public static final class RecordingConnector implements Connector {

        private final String id;
        public ConnectorMessage lastMessage;

        public RecordingConnector(final String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean send(final ConnectorMessage message) {
            this.lastMessage = message;
            return true;
        }

        public void reset() {
            lastMessage = null;
        }
    }

    /** Records all calls to {@link ConnectorMeshBridge#notifyDelivered}. */
    public static final class RecordingBridge implements ConnectorMeshBridge {

        public record Call(String connectorId, String destination, String content) {}

        public final List<Call> calls = new java.util.ArrayList<>();

        public String lastConnectorId;
        public String lastDestination;
        public String lastContent;

        @Override
        public void notifyDelivered(final String connectorId,
                                    final String destination,
                                    final String content) {
            calls.add(new Call(connectorId, destination, content));
            this.lastConnectorId = connectorId;
            this.lastDestination = destination;
            this.lastContent = content;
        }

        public void reset() {
            calls.clear();
            lastConnectorId = lastDestination = lastContent = null;
        }
    }

    /** Builds a ConnectorService backed only by the given connectors. */
    static ConnectorService serviceWith(final Connector... connectors) {
        return new ConnectorService(List.of(connectors));
    }
}
