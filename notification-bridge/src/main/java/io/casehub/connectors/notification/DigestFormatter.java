package io.casehub.connectors.notification;

import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.delivery.DigestSummary;

public interface DigestFormatter {

    String channelId();

    ConnectorMessage format(DigestSummary summary, String destination);
}
