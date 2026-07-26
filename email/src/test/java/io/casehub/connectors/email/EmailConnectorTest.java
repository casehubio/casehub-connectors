package io.casehub.connectors.email;

import io.casehub.connectors.ConnectorMessage;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class EmailConnectorTest {

    @Inject
    EmailConnector connector;

    @Inject
    MockMailbox mailbox;

    @Test
    void send_happyPath_emailDelivered() {
        mailbox.clear();
        connector.send(new ConnectorMessage("alice@example.com", "Alert", "Your WorkItem was assigned"));

        final var messages = mailbox.getMailMessagesSentTo("alice@example.com");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getSubject()).isEqualTo("Alert");
        assertThat(messages.get(0).getText()).isEqualTo("Your WorkItem was assigned");
    }

    @Test
    void send_noTitle_defaultsToNotification() {
        mailbox.clear();
        connector.send(new ConnectorMessage("bob@example.com", "Message body"));

        final var messages = mailbox.getMailMessagesSentTo("bob@example.com");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getSubject()).isEqualTo("Notification");
    }

    @Test
    void send_blankDestination_noEmailSent() {
        mailbox.clear();
        connector.send(new ConnectorMessage("", "Subject", "Body"));
        assertThat(mailbox.getTotalMessagesSent()).isEqualTo(0);
    }

    @Test
    void connectorId_isEmail() {
        assertThat(connector.id()).isEqualTo("email");
    }

    @Test
    void send_htmlFormat_usesMailWithHtml() {
        mailbox.clear();
        var attributes = java.util.Map.of("format", "html");
        connector.send(new ConnectorMessage("alice@example.com", "Digest",
                                            "<html><body><h1>Report</h1></body></html>", attributes));

        final var messages = mailbox.getMailMessagesSentTo("alice@example.com");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getHtml()).contains("<h1>Report</h1>");
    }

    @Test
    void send_noFormatAttribute_usesPlainText() {
        mailbox.clear();
        connector.send(new ConnectorMessage("bob@example.com", "Alert", "Plain text body"));

        final var messages = mailbox.getMailMessagesSentTo("bob@example.com");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getText()).isEqualTo("Plain text body");
        assertThat(messages.get(0).getHtml()).isNull();
    }

    @Test
    void send_textFormatAttribute_usesPlainText() {
        mailbox.clear();
        var attributes = java.util.Map.of("format", "text");
        connector.send(new ConnectorMessage("bob@example.com", "Alert", "Plain body", attributes));

        final var messages = mailbox.getMailMessagesSentTo("bob@example.com");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getText()).isEqualTo("Plain body");
    }

}
