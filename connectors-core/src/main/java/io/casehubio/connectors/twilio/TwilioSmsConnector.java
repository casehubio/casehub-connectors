package io.casehubio.connectors.twilio;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehubio.connectors.Connector;
import io.casehubio.connectors.ConnectorMessage;
import io.casehubio.connectors.http.HttpHelper;

/**
 * Twilio SMS connector via the Twilio Messages REST API.
 *
 * <p>
 * {@link ConnectorMessage#destination()} is the recipient E.164 phone number
 * (e.g. {@code +447700900000}).
 * {@link ConnectorMessage#body()} is the SMS text (max 1600 chars for concatenated SMS).
 *
 * <h2>Configuration</h2>
 * <pre>
 * casehub.connectors.twilio.account-sid=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 * casehub.connectors.twilio.auth-token=your_auth_token
 * casehub.connectors.twilio.from=+14155552671
 * </pre>
 *
 * <p>
 * This connector is only active when the above config properties are present.
 * If {@code account-sid} is blank, all {@code send()} calls are logged and no-op'd.
 */
@ApplicationScoped
public class TwilioSmsConnector implements Connector {

    public static final String ID = "twilio-sms";

    private static final Logger LOG = Logger.getLogger(TwilioSmsConnector.class.getName());
    private static final String TWILIO_API = "https://api.twilio.com/2010-04-01/Accounts/";

    @Inject
    @ConfigProperty(name = "casehub.connectors.twilio.account-sid", defaultValue = "")
    String accountSid;

    @Inject
    @ConfigProperty(name = "casehub.connectors.twilio.auth-token", defaultValue = "")
    String authToken;

    @Inject
    @ConfigProperty(name = "casehub.connectors.twilio.from", defaultValue = "")
    String from;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void send(final ConnectorMessage message) {
        if (accountSid.isBlank() || authToken.isBlank() || from.isBlank()) {
            LOG.warning("TwilioSmsConnector: casehub.connectors.twilio.* not configured — message not sent");
            return;
        }

        final String url = TWILIO_API + accountSid + "/Messages.json";
        final String body = "To=" + encode(message.destination())
                + "&From=" + encode(from)
                + "&Body=" + encode(message.body() != null ? message.body() : "");

        final String credentials = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        try {
            final HttpResponse<String> response = HttpHelper.CLIENT.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Authorization", "Basic " + credentials)
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warning("Twilio SMS failed to " + message.destination()
                        + " status=" + response.statusCode());
            }
        } catch (final Exception e) {
            LOG.warning("Twilio SMS error to " + message.destination() + ": " + e.getMessage());
        }
    }

    private static String encode(final String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
