package io.casehub.connectors.slack;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.http.HttpHelper;

/**
 * Slack connector via Incoming Webhooks.
 *
 * <p>
 * {@link ConnectorMessage#destination()} must be a Slack Incoming Webhook URL.
 * {@link ConnectorMessage#title()} is used as bold header text (optional).
 * {@link ConnectorMessage#body()} is the main message text.
 *
 * <p>
 * No Slack SDK required. No API credentials needed — the webhook URL is the credential.
 *
 * <h2>Setup</h2>
 * <ol>
 * <li>In Slack: Apps → Incoming Webhooks → Add to Workspace → copy the webhook URL.</li>
 * <li>Store the URL in your notification rule's {@code targetUrl}.</li>
 * </ol>
 */
@ApplicationScoped
public class SlackConnector implements Connector {

    public static final String ID = "slack";

    private static final Logger LOG = Logger.getLogger(SlackConnector.class.getName());

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void send(final ConnectorMessage message) {
        final String json = buildPayload(message.title(), message.body());
        final boolean ok = HttpHelper.postJson(message.destination(), json);
        if (!ok) {
            LOG.warning("Slack connector failed for destination: " + message.destination());
        }
    }

    /** Package-private for unit testing. */
    public static String buildPayload(final String title, final String body) {
        final StringBuilder sb = new StringBuilder("{");
        if (title != null && !title.isBlank()) {
            sb.append("\"text\":").append(HttpHelper.jsonQuote("*" + title + "*\n" + (body != null ? body : "")));
        } else {
            sb.append("\"text\":").append(HttpHelper.jsonQuote(body));
        }
        sb.append("}");
        return sb.toString();
    }
}
