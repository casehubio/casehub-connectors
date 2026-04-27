package io.casehubio.connectors.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehubio.connectors.ConnectorMessage;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;

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
}
