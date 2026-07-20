package io.casehub.connectors.notification;

import java.util.HashMap;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.delivery.DeliveryResult;
import io.casehub.platform.api.delivery.DestinationResolver;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.notification.NotificationInput;

class ConnectorNotificationDeliverer implements NotificationDeliverer {

    private final Connector connector;
    private final String channelType;
    private final DestinationResolver resolver;

    ConnectorNotificationDeliverer(Connector connector, String channelType,
                                   DestinationResolver resolver) {
        this.connector = connector;
        this.channelType = channelType;
        this.resolver = resolver;
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
        var attributes = new HashMap<String, String>();
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
        return new DeliveryResult(false,
                "digest delivery not yet supported for bridged channels");
    }
}
