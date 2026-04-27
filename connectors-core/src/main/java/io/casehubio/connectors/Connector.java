package io.casehubio.connectors;

/**
 * SPI for outbound message delivery.
 *
 * <p>
 * Implementations are CDI {@code @ApplicationScoped} beans discoverable at startup.
 * The {@link #id()} string identifies the connector type and is used to route
 * messages to the correct implementation.
 *
 * <p>
 * Built-in implementations: {@code "slack"}, {@code "teams"}, {@code "twilio-sms"},
 * {@code "whatsapp"}. The {@code connectors-email} module provides {@code "email"}.
 *
 * <p>
 * Custom connectors: provide a CDI {@code @ApplicationScoped} bean implementing this
 * interface. It will be discovered automatically.
 *
 * <h2>Contract</h2>
 * <ul>
 * <li>{@code send()} must not throw unchecked exceptions — log failures and return.</li>
 * <li>{@code send()} may block briefly (HTTP call) but should complete within its
 *     configured timeout. Callers are responsible for async dispatch if needed.</li>
 * <li>{@code send()} must be thread-safe — it may be called from multiple threads.</li>
 * </ul>
 */
public interface Connector {

    /**
     * Unique identifier for this connector type.
     * Examples: {@code "slack"}, {@code "teams"}, {@code "twilio-sms"},
     * {@code "whatsapp"}, {@code "email"}.
     *
     * @return the connector type string; must not be null or blank
     */
    String id();

    /**
     * Send a message via this connector.
     *
     * @param message the message to deliver; must not be null
     */
    void send(ConnectorMessage message);
}
