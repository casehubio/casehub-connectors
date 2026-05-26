# casehub-connectors — Design

## Purpose

Lightweight outbound message delivery library for the casehubio platform.
Provides a single CDI SPI — `Connector` — with built-in implementations for
Slack, Teams, Twilio SMS, WhatsApp, and email. Callers decide when and what to
send; this library handles the delivery.

---

## Design Principles

**CDI-native.** Connectors are plain `@ApplicationScoped` CDI beans. Discovery,
lifecycle, and injection use standard CDI — no registry, no factory, no custom
wiring.

**Standard library HTTP.** All channel implementations use `java.net.http.HttpClient`
from the JDK. This keeps the core module's dependency footprint to zero — nothing
beyond the JVM is required to embed it.

**Direct REST implementations.** Each channel is implemented against its own REST
API. This makes each connector self-contained and auditable — the full delivery
path is visible in a single class.

**Minimal scope.** This library does delivery only. No routing, scheduling,
templating, or retry orchestration. Callers own those concerns.

---

## Module Structure

| Module | Artifact | Purpose |
|--------|----------|---------|
| `core` | `casehub-connectors` | SPI + Slack, Teams, Twilio SMS, WhatsApp |
| `email` | `casehub-connectors-email` | Email via `quarkus-mailer` |

Email is a separate module because `quarkus-mailer` is an optional dependency —
services that don't need email should not pull it in.

---

## SPI

```java
public interface Connector {
    String id();
    void send(ConnectorMessage message);
}
```

`id()` returns the connector's type string (e.g. `"slack"`, `"twilio-sms"`).
Callers use this to select the right connector at runtime.

`send()` delivers the message. **Contract:**
- Must not throw unchecked exceptions — log failures and return.
- May block briefly (one HTTP call) but must complete within its configured timeout.
- Must be thread-safe — it may be called from multiple threads concurrently.

**Custom connectors:** implement `Connector` as an `@ApplicationScoped` CDI bean.
It will be discovered automatically alongside the built-in implementations.

---

## Data Model

```java
public record ConnectorMessage(
        String destination,
        String title,
        String body,
        Map<String, String> attributes) { }
```

| Field | Type | Semantics |
|-------|------|-----------|
| `destination` | `String` | Where to send: webhook URL, E.164 phone number, or email address |
| `title` | `String?` | Subject or card title — connector-specific; null uses a connector default |
| `body` | `String` | Main text content |
| `attributes` | `Map<String,String>` | Connector-specific extras (e.g. `templateName` for WhatsApp); unrecognised keys are silently ignored |

**Per-connector field semantics:**

| Connector | `destination` | `title` | `body` |
|-----------|--------------|---------|--------|
| Slack | Webhook URL | Card header | Message text |
| Teams | Webhook URL | Card title | Message text |
| Twilio SMS | E.164 number (e.g. `+447700900000`) | Ignored | SMS text (max 1600 chars) |
| WhatsApp | E.164 number | Ignored | Message text |
| Email | Email address | Subject (`"Notification"` if blank) | Plain-text body |

Convenience constructors are provided for the common cases (no attributes; body only).

---

## Configuration

Slack and Teams require no application configuration — the webhook URL is passed
as `destination` at call time.

**Twilio SMS:**

| Property | Description |
|----------|-------------|
| `casehub.connectors.twilio.account-sid` | Twilio Account SID (`ACxxx...`) |
| `casehub.connectors.twilio.auth-token` | Twilio Auth Token |
| `casehub.connectors.twilio.from` | Sender phone number (E.164) |

If `account-sid` is blank, `send()` logs a warning and no-ops — the connector
remains active but inactive, safe to include in a deployment that doesn't use SMS.

**WhatsApp:**

| Property | Description |
|----------|-------------|
| `casehub.connectors.whatsapp.api-token` | Meta Cloud API bearer token |
| `casehub.connectors.whatsapp.phone-number-id` | WhatsApp Business phone number ID |

If `api-token` is blank, `send()` logs and no-ops (same pattern as Twilio).

**Email** — configure `quarkus-mailer` as normal:

| Property | Description |
|----------|-------------|
| `quarkus.mailer.from` | Sender address |
| `quarkus.mailer.host` | SMTP host |
| `quarkus.mailer.port` | SMTP port (typically 587) |
| `quarkus.mailer.username` | SMTP credentials |
| `quarkus.mailer.password` | SMTP credentials |

Set `quarkus.mailer.mock=true` (the default test-profile value) to intercept
emails in tests without a real SMTP server.

---

## Usage

Inject `ConnectorService` and route by id:

```java
@ApplicationScoped
public class NotificationService {

    @Inject
    ConnectorService connectors;

    public void notify(String channel, String destination, String title, String body) {
        connectors.send(channel, new ConnectorMessage(destination, title, body));
    }
}
```

`channel` is one of `"slack"`, `"teams"`, `"twilio-sms"`, `"whatsapp"`, `"email"`,
or the id of a custom connector registered in the CDI context.

`send()` throws `IllegalArgumentException` if the channel is not registered — the
message includes the available ids, making misconfiguration straightforward to diagnose.

Use `supports()` to guard before sending when the channel id comes from user input:

```java
if (connectors.supports(channel)) {
    connectors.send(channel, message);
}
```

Use `ids()` to enumerate available channels (e.g. for UI validation or capability checks):

```java
Set<String> available = connectors.ids();
```
