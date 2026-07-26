package io.casehub.connectors.notification;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.delivery.DeliveryResult;
import io.casehub.platform.api.delivery.DestinationResolver;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorNotificationDelivererTest {

    private static final NotificationSource SOURCE = new NotificationSource(
            "evt-1", "work-item", "wi-1", "actor-1");

    static class StubConnector implements Connector {
        ConnectorMessage lastMessage;
        boolean shouldSucceed = true;

        @Override public String id() { return "email"; }
        @Override public boolean send(ConnectorMessage message) {
            lastMessage = message;
            return shouldSucceed;
        }
    }

    static class StubResolver implements DestinationResolver {
        private final String channelId;
        private final Map<String, String> destinations;

        StubResolver(String channelId, Map<String, String> destinations) {
            this.channelId = channelId;
            this.destinations = destinations;
        }

        @Override public String channelId() { return channelId; }
        @Override public Optional<String> resolve(String userId, String tenancyId) {
            return Optional.ofNullable(destinations.get(userId));
        }
    }

    @Test
    void deliver_withResolverAndDestination_callsConnectorAndReturnsSuccess() {
        var connector = new StubConnector();
        var resolver = new StubResolver("email", Map.of("user-1", "user1@example.com"));
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", resolver, null);

        var input = new NotificationInput("user-1", "tenant-1", "Alert",
                "Something happened", "incident", NotificationSeverity.WARNING,
                "https://app/incidents/1", SOURCE);

        DeliveryResult result = deliverer.deliver(input);

        assertThat(result.success()).isTrue();
        assertThat(connector.lastMessage.destination()).isEqualTo("user1@example.com");
        assertThat(connector.lastMessage.title()).isEqualTo("Alert");
        assertThat(connector.lastMessage.body()).isEqualTo("Something happened");
    }

    @Test
    void deliver_passesMetadataAsAttributes() {
        var connector = new StubConnector();
        var resolver = new StubResolver("email", Map.of("user-1", "user1@example.com"));
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", resolver, null);

        var input = new NotificationInput("user-1", "tenant-1", "Alert",
                "Body", "sla.breached", NotificationSeverity.URGENT,
                "https://app/sla/1", SOURCE);

        deliverer.deliver(input);

        assertThat(connector.lastMessage.attributes())
                .containsEntry("category", "sla.breached")
                .containsEntry("severity", "URGENT")
                .containsEntry("actionUrl", "https://app/sla/1");
    }

    @Test
    void deliver_nullBody_fallsBackToTitle() {
        var connector = new StubConnector();
        var resolver = new StubResolver("email", Map.of("user-1", "user1@example.com"));
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", resolver, null);

        var input = new NotificationInput("user-1", "tenant-1", "Alert title",
                null, "test", NotificationSeverity.INFO, null, SOURCE);

        deliverer.deliver(input);

        assertThat(connector.lastMessage.body()).isEqualTo("Alert title");
    }

    @Test
    void deliver_nullActionUrl_omitsFromAttributes() {
        var connector = new StubConnector();
        var resolver = new StubResolver("email", Map.of("user-1", "user1@example.com"));
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", resolver, null);

        var input = new NotificationInput("user-1", "tenant-1", "Alert",
                "Body", "test", NotificationSeverity.INFO, null, SOURCE);

        deliverer.deliver(input);

        assertThat(connector.lastMessage.attributes()).doesNotContainKey("actionUrl");
    }

    @Test
    void deliver_noResolver_returnsFailure() {
        var connector = new StubConnector();
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", null, null);

        var input = new NotificationInput("user-1", "tenant-1", "Alert",
                "Body", "test", NotificationSeverity.INFO, null, SOURCE);

        DeliveryResult result = deliverer.deliver(input);

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("no destination resolver");
        assertThat(connector.lastMessage).isNull();
    }

    @Test
    void deliver_noDestinationForUser_returnsFailure() {
        var connector = new StubConnector();
        var resolver = new StubResolver("email", Map.of());
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", resolver, null);

        var input = new NotificationInput("unknown-user", "tenant-1", "Alert",
                "Body", "test", NotificationSeverity.INFO, null, SOURCE);

        DeliveryResult result = deliverer.deliver(input);

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("no destination");
    }

    @Test
    void deliver_connectorReturnsFalse_returnsFailure() {
        var connector = new StubConnector();
        connector.shouldSucceed = false;
        var resolver = new StubResolver("email", Map.of("user-1", "user1@example.com"));
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", resolver, null);

        var input = new NotificationInput("user-1", "tenant-1", "Alert",
                "Body", "test", NotificationSeverity.INFO, null, SOURCE);

        DeliveryResult result = deliverer.deliver(input);

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("delivery failure");
    }

    @Test
    void channelId_returnsChannelType() {
        var connector = new StubConnector();
        var deliverer = new ConnectorNotificationDeliverer(connector, "sms", null, null);

        assertThat(deliverer.channelId()).isEqualTo("sms");
    }

    @Test
    void deliverDigest_withFormatter_formatsAndSends() {
        var connector = new StubConnector();
        var resolver  = new StubResolver("email", Map.of("user-1", "user1@example.com"));
        var formatter = new EmailDigestFormatter();
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", resolver, formatter);

        var input = new NotificationInput("user-1", "tenant-1", "Alert",
                                          null, "test", NotificationSeverity.INFO, null, SOURCE);
        var summary = new DigestSummary("user-1", "tenant-1", "email",
                                        List.of(input), Instant.now().minusSeconds(3600), Instant.now(), null);

        DeliveryResult result = deliverer.deliverDigest(summary);

        assertThat(result.success()).isTrue();
        assertThat(connector.lastMessage.destination()).isEqualTo("user1@example.com");
        assertThat(connector.lastMessage.body()).contains("<html>");
    }

    @Test
    void deliverDigest_noFormatter_usesDefault() {
        var connector = new StubConnector();
        var resolver  = new StubResolver("email", Map.of("user-1", "user1@example.com"));
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", resolver, null);

        var input = new NotificationInput("user-1", "tenant-1", "Alert",
                                          null, "test", NotificationSeverity.INFO, null, SOURCE);
        var summary = new DigestSummary("user-1", "tenant-1", "email",
                                        List.of(input), Instant.now().minusSeconds(3600), Instant.now(), null);

        DeliveryResult result = deliverer.deliverDigest(summary);

        assertThat(result.success()).isTrue();
        assertThat(connector.lastMessage.body()).contains("1 notifications");
        assertThat(connector.lastMessage.body()).doesNotContain("<html>");
    }

    @Test
    void deliverDigest_noResolver_returnsFailure() {
        var connector = new StubConnector();
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", null, null);

        var input = new NotificationInput("user-1", "tenant-1", "Alert",
                                          null, "test", NotificationSeverity.INFO, null, SOURCE);
        var summary = new DigestSummary("user-1", "tenant-1", "email",
                                        List.of(input), Instant.now().minusSeconds(3600), Instant.now(), null);

        DeliveryResult result = deliverer.deliverDigest(summary);
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("no destination resolver");
    }

    @Test
    void deliverDigest_noDestination_returnsFailure() {
        var connector = new StubConnector();
        var resolver  = new StubResolver("email", Map.of());
        var deliverer = new ConnectorNotificationDeliverer(connector, "email", resolver, null);

        var input = new NotificationInput("user-1", "tenant-1", "Alert",
                                          null, "test", NotificationSeverity.INFO, null, SOURCE);
        var summary = new DigestSummary("user-1", "tenant-1", "email",
                                        List.of(input), Instant.now().minusSeconds(3600), Instant.now(), null);

        DeliveryResult result = deliverer.deliverDigest(summary);
        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("no destination");
    }
}
