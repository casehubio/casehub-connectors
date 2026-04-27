package io.casehubio.connectors;

import java.util.Map;

/**
 * A message to be delivered by a {@link Connector}.
 *
 * <p>
 * The semantics of each field depend on the connector:
 * <ul>
 * <li><b>Slack / Teams</b> — {@code destination} is the webhook URL; {@code title}
 *     becomes the header/card title; {@code body} is the main text.</li>
 * <li><b>Twilio SMS</b> — {@code destination} is the E.164 phone number (e.g.
 *     {@code +447700900000}); {@code body} is the SMS text (max 1600 chars).</li>
 * <li><b>WhatsApp</b> — {@code destination} is the E.164 phone number; {@code body}
 *     is the message text.</li>
 * <li><b>Email</b> — {@code destination} is the recipient email address;
 *     {@code title} is the subject; {@code body} is the plain-text body.</li>
 * </ul>
 *
 * <p>
 * {@code attributes} carries connector-specific extras — for example a Slack
 * channel override, a Teams card colour, or a WhatsApp template name. Connectors
 * that do not recognise an attribute silently ignore it.
 *
 * @param destination  where to send: webhook URL, phone number, or email address
 * @param title        optional subject or card title (null = connector default)
 * @param body         main text content
 * @param attributes   optional key-value metadata for connector-specific behaviour
 */
public record ConnectorMessage(
        String destination,
        String title,
        String body,
        Map<String, String> attributes) {

    /** Convenience constructor — no attributes. */
    public ConnectorMessage(final String destination, final String title, final String body) {
        this(destination, title, body, Map.of());
    }

    /** Convenience constructor — body only, no title or attributes. */
    public ConnectorMessage(final String destination, final String body) {
        this(destination, null, body, Map.of());
    }
}
