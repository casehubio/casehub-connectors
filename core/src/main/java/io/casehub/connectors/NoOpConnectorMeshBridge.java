package io.casehub.connectors;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default no-op {@link ConnectorMeshBridge}. Active when no other implementation is on
 * the classpath. Displaced automatically by any {@code @ApplicationScoped} (non-{@code
 * @DefaultBean}) implementation — specifically {@code qhorus/connector-backend}
 * (casehubio/qhorus#249).
 *
 * <p>{@code @Unremovable}: ARC sees no injection point within {@code core} itself;
 * the injection point lives in the {@code mcp} module. Without this annotation, ARC
 * eliminates the bean at augmentation time when core is used without mcp.
 */
@DefaultBean
@Unremovable
@ApplicationScoped
public class NoOpConnectorMeshBridge implements ConnectorMeshBridge {

    @Override
    public void notifyDelivered(final String connectorId,
                                final String destination,
                                final String content) {
        // intentional no-op — Qhorus bridge activates by classpath presence (qhorus#249)
    }
}
