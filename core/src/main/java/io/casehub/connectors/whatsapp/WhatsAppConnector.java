package io.casehub.connectors.whatsapp;

import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.http.HttpHelper;

/**
 * WhatsApp Business connector via the Meta Cloud API.
 *
 * <p>
 * {@link ConnectorMessage#destination()} is the recipient E.164 phone number
 * (e.g. {@code +447700900000}).
 * {@link ConnectorMessage#body()} is the message text.
 *
 * <h2>Configuration</h2>
 * <pre>
 * casehub.connectors.whatsapp.api-token=EAAxxxx...
 * casehub.connectors.whatsapp.phone-number-id=12345678901234
 * </pre>
 *
 * <p>
 * This connector sends free-form text messages. For template messages (required
 * for the first 24-hour window), extend this connector or use
 * {@link ConnectorMessage#attributes()} with key {@code templateName}.
 *
 * <p>
 * If {@code api-token} is blank, all {@code send()} calls are logged and no-op'd.
 */
@ApplicationScoped
public class WhatsAppConnector implements Connector {

    public static final String ID = "whatsapp";

    private static final Logger LOG = Logger.getLogger(WhatsAppConnector.class.getName());

    @Inject
    @ConfigProperty(name = "casehub.connectors.whatsapp.api-token", defaultValue = "")
    String apiToken;

    @Inject
    @ConfigProperty(name = "casehub.connectors.whatsapp.phone-number-id", defaultValue = "")
    String phoneNumberId;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void send(final ConnectorMessage message) {
        if (apiToken.isBlank() || phoneNumberId.isBlank()) {
            LOG.warning("WhatsAppConnector: casehub.connectors.whatsapp.* not configured — message not sent");
            return;
        }

        final String url = "https://graph.facebook.com/v18.0/" + phoneNumberId + "/messages";
        final String to = message.destination().replaceAll("[^0-9+]", "");
        final String templateName = message.attributes() != null
                ? message.attributes().get("templateName") : null;
        final String templateLanguage = (message.attributes() != null
                && message.attributes().get("templateLanguage") != null)
                ? message.attributes().get("templateLanguage") : "en_US";

        final String json = buildPayload(to, message.body(), templateName, templateLanguage);
        final boolean ok = HttpHelper.postJson(url, json, "Authorization", "Bearer " + apiToken);
        if (!ok) {
            LOG.warning("WhatsApp connector failed to: " + to);
        }
    }

    /**
     * Package-private for unit testing.
     *
     * @param to               E.164 recipient number
     * @param body             message body (used for text messages only)
     * @param templateName     if non-blank, produces a template message; otherwise text
     * @param templateLanguage BCP-47 language code for template lookup (e.g. {@code "en_US"},
     *                         {@code "es_MX"}); only used when {@code templateName} is non-blank
     */
    static String buildPayload(final String to, final String body,
                               final String templateName, final String templateLanguage) {
        if (templateName != null && !templateName.isBlank()) {
            // TODO: validate templateName matches Meta's naming rules (lowercase, alphanumeric, underscores only)
            // Meta may return HTTP 200 with an error body for invalid names, which HttpHelper treats as success.
            return "{"
                    + "\"messaging_product\":\"whatsapp\","
                    + "\"to\":" + HttpHelper.jsonQuote(to) + ","
                    + "\"type\":\"template\","
                    + "\"template\":{"
                    + "\"name\":" + HttpHelper.jsonQuote(templateName) + ","
                    + "\"language\":{\"code\":" + HttpHelper.jsonQuote(templateLanguage) + "}"
                    + "}"
                    + "}";
        }
        final String text = body != null ? body : "";
        return "{"
                + "\"messaging_product\":\"whatsapp\","
                + "\"to\":" + HttpHelper.jsonQuote(to) + ","
                + "\"type\":\"text\","
                + "\"text\":{\"body\":" + HttpHelper.jsonQuote(text) + "}"
                + "}";
    }
}
