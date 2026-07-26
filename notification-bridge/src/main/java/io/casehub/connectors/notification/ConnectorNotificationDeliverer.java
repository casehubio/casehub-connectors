package io.casehub.connectors.notification;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.delivery.DeliveryResult;
import io.casehub.platform.api.delivery.DestinationResolver;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.notification.NotificationInput;

class ConnectorNotificationDeliverer implements NotificationDeliverer {

    private final Connector           connector;
    private final String              channelType;
    private final DestinationResolver resolver;
    private final DigestFormatter     digestFormatter;

    ConnectorNotificationDeliverer(Connector connector, String channelType,
                                   DestinationResolver resolver, DigestFormatter digestFormatter) {
        this.connector       = connector;
        this.channelType     = channelType;
        this.resolver        = resolver;
        this.digestFormatter = digestFormatter;
    }

    @Override
    public String channelId() {
        return channelType;
    }

    @Override
    public DeliveryResult deliver(NotificationInput notification) {
        if (resolver == null) {
            return new DeliveryResult(false,
                                      "no destination resolver for " + channelType);
        }
        var destination = resolver.resolve(
                notification.userId(), notification.tenancyId());
        if (destination.isEmpty()) {
            return new DeliveryResult(false,
                                      "no destination for user " + notification.userId());
        }

        String body = notification.body() != null
                      ? notification.body() : notification.title();
        var attributes = new java.util.HashMap<String, String>();
        attributes.put("category", notification.category());
        attributes.put("severity", notification.severity().name());
        if (notification.actionUrl() != null) {
            attributes.put("actionUrl", notification.actionUrl());
        }

        boolean success = connector.send(new ConnectorMessage(
                destination.get(), notification.title(), body, attributes));
        return new DeliveryResult(success,
                                  success ? null : "connector reported delivery failure");
    }

    @Override
    public DeliveryResult deliverDigest(DigestSummary summary) {
        if (resolver == null) {
            return new DeliveryResult(false,
                                      "no destination resolver for " + channelType);
        }
        var destination = resolver.resolve(summary.userId(), summary.tenancyId());
        if (destination.isEmpty()) {
            return new DeliveryResult(false,
                                      "no destination for user " + summary.userId());
        }
        ConnectorMessage msg = digestFormatter != null
                               ? digestFormatter.format(summary, destination.get())
                               : DefaultDigestFormat.format(summary, destination.get());
        boolean success = connector.send(msg);
        return new DeliveryResult(success,
                                  success ? null : "connector reported delivery failure");
    }
}
