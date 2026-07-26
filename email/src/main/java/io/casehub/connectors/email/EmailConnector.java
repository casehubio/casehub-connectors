package io.casehub.connectors.email;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.logging.Logger;

/**
 * Email connector backed by {@code quarkus-mailer}.
 *
 * <p>
 * {@link ConnectorMessage#destination()} is the recipient email address.
 * {@link ConnectorMessage#title()} is the email subject (defaults to "Notification" if blank).
 * {@link ConnectorMessage#body()} is the plain-text body.
 *
 * <h2>Configuration</h2>
 * <p>
 * Configure {@code quarkus-mailer} as normal in {@code application.properties}:
 * <pre>
 * quarkus.mailer.from=notifications@yourcompany.com
 * quarkus.mailer.host=smtp.yourcompany.com
 * quarkus.mailer.port=587
 * quarkus.mailer.username=...
 * quarkus.mailer.password=...
 * </pre>
 * For testing without a real SMTP server: {@code quarkus.mailer.mock=true}
 * (default in test profile) — emails are intercepted and not sent.
 */
@ApplicationScoped
public class EmailConnector implements Connector {

    public static final String ID = "email";

    private static final Logger LOG = Logger.getLogger(EmailConnector.class.getName());

    @Inject
    Mailer mailer;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean send(final ConnectorMessage message) {
        if (message.destination() == null || message.destination().isBlank()) {
            LOG.warning("EmailConnector: destination (email address) is blank — message not sent");
            return false;
        }

        final String subject = message.title() != null && !message.title().isBlank()
                               ? message.title()
                               : "Notification";
        final String body = message.body() != null ? message.body() : "";

        try {
            String format = message.attributes() != null
                            ? message.attributes().getOrDefault("format", "text") : "text";
            if ("html".equals(format)) {
                mailer.send(Mail.withHtml(message.destination(), subject, body));
            } else {
                mailer.send(Mail.withText(message.destination(), subject, body));
            }
            return true;
        } catch (final Exception e) {
            LOG.warning("EmailConnector: failed to send to " + message.destination()
                        + ": " + e.getMessage());
            return false;
        }}
}
