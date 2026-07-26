package io.casehub.connectors.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.delivery.DigestGroupBy;
import io.casehub.platform.api.delivery.DigestSummary;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;

import static org.assertj.core.api.Assertions.assertThat;

class DigestFormatterTest {

    private static final NotificationSource SOURCE =
            new NotificationSource("e1", "work-item", "wi-1", "actor-1");

    private static final Instant PERIOD_START = Instant.parse("2026-07-26T08:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-07-26T12:00:00Z");

    private DigestSummary summary(int count, DigestGroupBy groupBy) {
        var notifications = new ArrayList<NotificationInput>();
        for (int i = 0; i < count; i++) {
            notifications.add(new NotificationInput(
                    "user-1", "tenant-1", "Alert " + (i + 1),
                    "Body " + (i + 1), i % 2 == 0 ? "sla.breached" : "work-item.created",
                    i == 0 ? NotificationSeverity.URGENT : NotificationSeverity.INFO,
                    "https://app/item/" + (i + 1), SOURCE));
        }
        return new DigestSummary("user-1", "tenant-1", "email",
                notifications, PERIOD_START, PERIOD_END, groupBy);
    }

    @Test
    void emailFormatter_htmlBody() {
        var formatter = new EmailDigestFormatter();
        var msg = formatter.format(summary(3, null), "user1@example.com");
        assertThat(msg.destination()).isEqualTo("user1@example.com");
        assertThat(msg.title()).contains("3 notifications");
        assertThat(msg.body()).contains("<html>");
        assertThat(msg.body()).contains("Alert 1");
        assertThat(msg.body()).contains("Alert 2");
        assertThat(msg.body()).contains("Alert 3");
        assertThat(msg.attributes()).containsEntry("format", "html");
    }

    @Test
    void emailFormatter_categoryGrouping() {
        var formatter = new EmailDigestFormatter();
        var msg = formatter.format(summary(4, DigestGroupBy.CATEGORY), "user1@example.com");
        assertThat(msg.body()).contains("sla.breached");
        assertThat(msg.body()).contains("work-item.created");
    }

    @Test
    void emailFormatter_entityGrouping_treatedAsFlat() {
        var formatter = new EmailDigestFormatter();
        var msg = formatter.format(summary(2, DigestGroupBy.ENTITY), "user1@example.com");
        assertThat(msg.body()).contains("Alert 1");
        assertThat(msg.body()).contains("Alert 2");
    }

    @Test
    void smsFormatter_shortText() {
        var formatter = new SmsDigestFormatter();
        var msg = formatter.format(summary(5, null), "+447700900000");
        assertThat(msg.destination()).isEqualTo("+447700900000");
        assertThat(msg.body()).contains("5 notifications");
        assertThat(msg.body()).contains("Alert 1");
        assertThat(msg.body().length()).isLessThan(160);
    }

    @Test
    void whatsappFormatter_countAndCategories() {
        var formatter = new WhatsAppDigestFormatter();
        var msg = formatter.format(summary(4, null), "+447700900000");
        assertThat(msg.destination()).isEqualTo("+447700900000");
        assertThat(msg.body()).contains("4 notifications");
        assertThat(msg.body()).contains("sla.breached");
        assertThat(msg.body()).contains("Alert 1");
    }

    @Test
    void defaultFormat_plainText() {
        var msg = DefaultDigestFormat.format(summary(3, null), "dest");
        assertThat(msg.body()).contains("3 notifications");
        assertThat(msg.body()).contains(PERIOD_START.toString());
    }

    @Test
    void emailFormatter_channelId() {
        assertThat(new EmailDigestFormatter().channelId()).isEqualTo("email");
    }

    @Test
    void smsFormatter_channelId() {
        assertThat(new SmsDigestFormatter().channelId()).isEqualTo("sms");
    }

    @Test
    void whatsappFormatter_channelId() {
        assertThat(new WhatsAppDigestFormatter().channelId()).isEqualTo("whatsapp");
    }

    @Test
    void emailFormatter_actionUrlIncluded() {
        var formatter = new EmailDigestFormatter();
        var msg = formatter.format(summary(1, null), "user@example.com");
        assertThat(msg.body()).contains("https://app/item/1");
        assertThat(msg.body()).contains("View");
    }

    @Test
    void whatsappFormatter_actionUrlIncluded() {
        var formatter = new WhatsAppDigestFormatter();
        var msg = formatter.format(summary(1, null), "+447700900000");
        assertThat(msg.body()).contains("https://app/item/1");
    }
}
