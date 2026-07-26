package io.casehub.connectors.notification;

import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.delivery.DigestSummary;

final class DefaultDigestFormat {

    private DefaultDigestFormat() {}

    static ConnectorMessage format(DigestSummary summary, String destination) {
        String body = "You have " + summary.notifications().size()
                + " notifications from " + summary.periodStart()
                + " to " + summary.periodEnd();
        return new ConnectorMessage(destination, "Notification digest", body);
    }
}
