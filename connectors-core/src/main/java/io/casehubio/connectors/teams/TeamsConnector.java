package io.casehubio.connectors.teams;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehubio.connectors.Connector;
import io.casehubio.connectors.ConnectorMessage;
import io.casehubio.connectors.http.HttpHelper;

/**
 * Microsoft Teams connector via Incoming Webhooks (Adaptive Cards).
 *
 * <p>
 * {@link ConnectorMessage#destination()} must be a Teams Incoming Webhook URL.
 * {@link ConnectorMessage#title()} becomes the card title.
 * {@link ConnectorMessage#body()} becomes the card body text.
 *
 * <p>
 * No Teams SDK required. No Microsoft API credentials needed — the webhook URL is the credential.
 */
@ApplicationScoped
public class TeamsConnector implements Connector {

    public static final String ID = "teams";

    private static final Logger LOG = Logger.getLogger(TeamsConnector.class.getName());

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void send(final ConnectorMessage message) {
        final String json = buildPayload(message.title(), message.body());
        final boolean ok = HttpHelper.postJson(message.destination(), json);
        if (!ok) {
            LOG.warning("Teams connector failed for destination: " + message.destination());
        }
    }

    /** Package-private for unit testing. */
    public static String buildPayload(final String title, final String body) {
        final String safeTitle = title != null ? HttpHelper.jsonEscape(title) : "";
        final String safeBody = body != null ? HttpHelper.jsonEscape(body) : "";
        return "{"
                + "\"type\":\"message\","
                + "\"attachments\":[{"
                + "\"contentType\":\"application/vnd.microsoft.card.adaptive\","
                + "\"content\":{"
                + "\"type\":\"AdaptiveCard\","
                + "\"version\":\"1.4\","
                + "\"body\":["
                + (safeTitle.isEmpty() ? "" :
                        "{\"type\":\"TextBlock\",\"text\":\"" + safeTitle + "\","
                        + "\"weight\":\"Bolder\",\"size\":\"Medium\"},")
                + "{\"type\":\"TextBlock\",\"text\":\"" + safeBody + "\","
                + "\"wrap\":true}"
                + "]}}]}";
    }
}
