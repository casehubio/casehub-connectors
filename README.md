# casehub-connectors

Lightweight outbound message connectors for the [CaseHub](https://github.com/casehubio) platform.

Provides a unified `Connector` SPI with built-in implementations for:

| Connector | Module | ID | Auth |
|---|---|---|---|
| Slack (Incoming Webhooks) | `casehub-connectors` | `slack` | Webhook URL (no credentials) |
| Microsoft Teams (Adaptive Cards) | `casehub-connectors` | `teams` | Webhook URL (no credentials) |
| Twilio SMS | `casehub-connectors` | `twilio-sms` | Account SID + Auth Token |
| WhatsApp Business (Meta) | `casehub-connectors` | `whatsapp` | API Token + Phone Number ID |
| Email (quarkus-mailer) | `casehub-connectors-email` | `email` | SMTP config |

## Why this exists

- **No Camel**: Camel Quarkus has Slack and Twilio components but the full Camel stack is a heavy dependency for outbound webhooks.
- **No vendor SDK**: Pure `java.net.http.HttpClient` — zero extra dependencies for HTTP-based connectors.
- **CDI SPI**: Custom connectors implement `Connector` as `@ApplicationScoped` CDI beans and are discovered automatically.

## Usage

```xml
<!-- HTTP-based connectors: Slack, Teams, Twilio SMS, WhatsApp -->
<dependency>
  <groupId>io.casehubio</groupId>
  <artifactId>casehub-connectors-core</artifactId>
  <version>0.2-SNAPSHOT</version>
</dependency>

<!-- Add only if you need email (brings quarkus-mailer) -->
<dependency>
  <groupId>io.casehubio</groupId>
  <artifactId>casehub-connectors-email</artifactId>
  <version>0.2-SNAPSHOT</version>
</dependency>
```

## Configuration

```properties
# Twilio SMS (only needed if you use the twilio-sms connector)
casehub.connectors.twilio.account-sid=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
casehub.connectors.twilio.auth-token=your_auth_token
casehub.connectors.twilio.from=+14155552671

# WhatsApp Business (only needed if you use the whatsapp connector)
casehub.connectors.whatsapp.api-token=EAAxxxx...
casehub.connectors.whatsapp.phone-number-id=12345678901234

# Slack and Teams: no config needed — webhook URL goes in ConnectorMessage.destination()
```

## Sending a message

```java
@Inject
@Any
Instance<Connector> connectors;

// Find and use a specific connector
connectors.stream()
    .filter(c -> "slack".equals(c.id()))
    .findFirst()
    .ifPresent(c -> c.send(new ConnectorMessage(
        "https://hooks.slack.com/services/T.../B.../...",  // destination
        "WorkItem Assigned",                                // title
        "Loan application #1234 has been assigned to alice" // body
    )));
```

## Custom connectors

```java
@ApplicationScoped
public class PagerDutyConnector implements Connector {
    @Override public String id() { return "pagerduty"; }
    @Override public void send(ConnectorMessage message) { /* ... */ }
}
```

## Artifacts

Published to [GitHub Packages](https://github.com/orgs/casehubio/packages) at `0.2-SNAPSHOT`.
