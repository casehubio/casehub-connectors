package io.casehub.connectors.notification;

import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.api.notification.NotificationInput;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class WhatsAppDigestFormatter implements DigestFormatter {

    @Override
    public String channelId() {
        return "whatsapp";
    }

    @Override
    public ConnectorMessage format(DigestSummary summary, String destination) {
        var mostUrgent = summary.notifications().stream()
                                .max(Comparator.comparing(NotificationInput::severity))
                                .orElseThrow();

        Map<String, Long> categories = summary.notifications().stream()
                                              .collect(Collectors.groupingBy(NotificationInput::category, Collectors.counting()));

        StringBuilder body = new StringBuilder();
        body.append(summary.notifications().size()).append(" notifications\n");
        categories.forEach((cat, count) ->
                                   body.append("• ").append(cat).append(": ").append(count).append("\n"));
        body.append("\nMost urgent: ").append(mostUrgent.title());
        if (mostUrgent.actionUrl() != null) {
            body.append("\n").append(mostUrgent.actionUrl());
        }
        return new ConnectorMessage(destination, body.toString());
    }
}
