package io.casehub.connectors.notification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.connectors.ConnectorMessage;
import io.casehub.platform.api.delivery.DigestGroupBy;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.api.notification.NotificationInput;

@ApplicationScoped
public class EmailDigestFormatter implements DigestFormatter {

    @Override
    public String channelId() {
        return "email";
    }

    @Override
    public ConnectorMessage format(DigestSummary summary, String destination) {
        String title = summary.notifications().size() + " notifications ("
                + summary.periodStart() + " — " + summary.periodEnd() + ")";

        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h2>").append(title).append("</h2>");

        if (summary.groupBy() == DigestGroupBy.CATEGORY) {
            Map<String, List<NotificationInput>> grouped = summary.notifications().stream()
                    .collect(Collectors.groupingBy(NotificationInput::category));
            for (var entry : grouped.entrySet()) {
                html.append("<h3>").append(entry.getKey()).append("</h3><ul>");
                for (var n : entry.getValue()) {
                    appendNotification(html, n);
                }
                html.append("</ul>");
            }
        } else {
            if (summary.groupBy() == DigestGroupBy.ENTITY) {
                org.jboss.logging.Logger.getLogger(EmailDigestFormatter.class)
                        .warnf("DigestGroupBy.ENTITY not yet supported — formatting as FLAT");
            }
            html.append("<ul>");
            for (var n : summary.notifications()) {
                appendNotification(html, n);
            }
            html.append("</ul>");
        }

        html.append("</body></html>");
        var attributes = new HashMap<String, String>();
        attributes.put("format", "html");
        return new ConnectorMessage(destination, title, html.toString(), attributes);
    }

    private static void appendNotification(StringBuilder html, NotificationInput n) {
        html.append("<li><strong>").append(n.title()).append("</strong>");
        html.append(" [").append(n.severity()).append("] ");
        html.append(n.category());
        if (n.actionUrl() != null) {
            html.append(" — <a href=\"").append(n.actionUrl()).append("\">View</a>");
        }
        html.append("</li>");
    }
}
