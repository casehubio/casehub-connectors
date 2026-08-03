# casehub-connectors — Consumer Guide

> Outbound and inbound message connector library for the casehubio platform.

**GitHub:** [casehubio/connectors](https://github.com/casehubio/connectors)
**Tier:** Foundation (no casehubio dependencies)

---

## Purpose

Canonical notification and messaging infrastructure for the platform. Provides CDI SPIs for outbound message delivery, inbound message reception, structured chat-system interaction, and calendar integration. Any casehubio repo that needs to send or receive messages must use these SPIs rather than implementing its own connectors.

Pure delivery infrastructure — no domain logic, no routing decisions, no scheduling. Callers decide when, what, and to whom; observers decide what to do with received messages.

No Camel, no vendor SDKs — pure `java.net.http.HttpClient`.

---

## Module Structure

| Module | What consumers need to know |
|--------|-----------------------------|
| `casehub-connectors` (core) | `Connector` outbound SPI, `InboundConnector` / `WebhookInboundConnector` inbound SPIs, `ConnectorDiscovery` SPI, `ConnectorsCloudEventAdapter`, built-in outbound impls (Slack, Teams, Twilio SMS, WhatsApp) |
| `casehub-connectors-email` | SMTP outbound via `quarkus-mailer` |
| `casehub-connectors-email-inbound` | IMAP polling inbound, `EmailInboundAccountProvider` SPI |
| `casehub-connectors-webhook` | JAX-RS webhook router + webhook-based inbound connectors (Slack, Teams, WhatsApp, Twilio SMS) |
| `casehub-connectors-mcp` | MCP tool surface: `send_slack`, `send_teams`, `send_sms`, `send_whatsapp`, `send_email`, `send_chat`, `list_channels`, `list_chat_channels`, calendar tools |
| `casehub-connectors-chat-spi` | `ChatPlatform` SPI, capability interfaces, `RichCard` model, `Channel`, `ReceivedMessage` |
| `casehub-connectors-chat-ref` | In-memory reference `ChatPlatform` for testing |
| `casehub-connectors-chat-discord` | Discord `ChatPlatform` (8 native capabilities) |
| `casehub-connectors-chat-slack` | Slack `ChatPlatform` (9 native capabilities — most complete) |
| `casehub-connectors-chat-irc` | IRC `ChatPlatform` (3 native capabilities) |
| `calendar-spi` | `CalendarPlatform` SPI — list calendars, list/get/create/update/delete events |
| `calendar-ref` | In-memory reference `CalendarPlatform` for testing |
| `calendar-google` | Google Calendar API with OAuth2 refresh token auth |
| `notification-bridge` | Bridges platform notification delivery system to connector SPI |

---

## Key Consumer APIs and SPIs

### Outbound — `Connector` SPI

CDI SPI with two methods: an `id()` accessor and a `send()` method that takes a `ConnectorMessage` (destination, title, body). Returns `boolean` (success/failure). Custom connectors implement it as CDI beans — auto-discovered.

`Connector.channelType()` defaults to `id()`; override to map to a different channel type or return `null` to opt out of notification bridging.

**Built-in outbound implementations:**

| ID | Module | Auth |
|----|--------|------|
| `slack` | core | Webhook URL in `destination` |
| `teams` | core | Webhook URL in `destination` |
| `twilio-sms` | core | Account SID + Auth Token in config |
| `whatsapp` | core | API Token + Phone Number ID in config. Template messages via `ConnectorMessage.attributes("templateName")` + `attributes("templateLanguage")` (default `en_US`) |
| `email` | `casehub-connectors-email` | SMTP via `quarkus-mailer`. Supports `format=html` attribute for HTML rendering |

### Inbound — `InboundConnector` / `WebhookInboundConnector`

Two inbound SPIs:

- **`InboundConnector`** — pull-based polling (e.g. IMAP). `InboundConnectorService` polls on a configurable schedule and fires `Event<InboundMessage>` via `fireAsync()`.
- **`WebhookInboundConnector`** — push-based webhook reception. Abstract base class; implementations register an HTTP endpoint and normalise payloads to `InboundMessage`. Also fires via `fireAsync()`.

**Breaking contract:** observers MUST use `@ObservesAsync InboundMessage` — synchronous `@Observes` will not receive events. At-least-once delivery.

`InboundMessage` carries `connectorType` (non-null; values: slack, email, sms, whatsapp, teams, discord, irc) and `tenancyId` (nullable).

**Built-in inbound implementations:**

| ID | Module | Auth |
|----|--------|------|
| `email-inbound` | `casehub-connectors-email-inbound` | IMAP username/password in MP Config |
| `slack-inbound` | `casehub-connectors-webhook` | HMAC-SHA256 signing secret |
| `teams-inbound` | `casehub-connectors-webhook` | HMAC-SHA256 with Base64-encoded shared secret |
| `whatsapp-inbound` | `casehub-connectors-webhook` | HMAC-SHA256 + hub.mode verify token |
| `twilio-sms-inbound` | `casehub-connectors-webhook` | HMAC-SHA1 (Twilio algorithm) |
| `discord-inbound` | `casehub-connectors-chat-discord` | Discord bot token via Gateway WebSocket |

### ConnectorDiscovery SPI

Optional interface CDI beans implement when their targets are discoverable (e.g. Slack channels via `conversations.list`). Methods: `connectorId()` + `discover() -> List<DiscoveredTarget>`.

### CloudEvent Adapter

`ConnectorsCloudEventAdapter` observes `@ObservesAsync InboundMessage` and fires `Event<CloudEvent>.fireAsync()` with type `io.casehub.connectors.inbound.<connectorType>`. Follows canonical CloudEvent adapter pattern.

### ChatPlatform SPI

Structured interface for chat-system interactions beyond simple message delivery. Defines capability interfaces: `Messaging`, `Threading`, `Discovery`, `Reactions`, `Presence`, `Members`, `ChannelManagement`, `MemberManagement`, `MessageHistory`. Each platform declares which capabilities it supports; unsupported capabilities degrade gracefully.

**RichCard** — platform-agnostic rich content model (title, description, url, color, fields, thumbnailUrl, imageUrl, footer, author + Builder). Translated to platform-native formats automatically.

**Channel** includes `memberCount` (nullable Integer).

| Implementation | Native capabilities |
|----------------|-------------------|
| `chat-ref` | In-memory reference for testing |
| `chat-irc` | 3 capabilities |
| `chat-discord` | 8 capabilities (MemberManagement degraded) |
| `chat-slack` | 9 capabilities (most complete) |

### CalendarPlatform SPI

Calendar integration: list calendars, list/get/create/update/delete events. Sealed `EventTiming` model (Timed/AllDay).

| Implementation | Notes |
|----------------|-------|
| `calendar-ref` | In-memory reference for testing |
| `calendar-google` | Google Calendar API with OAuth2 refresh token auth, paginated listEvents |

### Notification Bridge

`notification-bridge` module bridges the platform notification delivery system (`NotificationDeliverer`, `DeliveryChannelRegistry`) to the connector SPI. Each `Connector` with a non-null `channelType()` auto-registers as a notification delivery channel at startup.

`DeliveryChannelDescriptor` carries `DestinationScope` (PER_USER or PER_TENANT) — per-tenant channels (Slack, Teams) deliver once per tenant per event, with the dispatcher deduplicating across the per-user loop.

`DestinationResolver` SPI resolves `userId` to connector-specific destination per channel. Config-based fallback reads from `casehub.notification.destinations.<channel>.<userId>`.

`DigestFormatter` CDI SPI provides channel-type-aware digest delivery (email HTML, SMS short text, WhatsApp rich text).

---

## Configuration

Slack and Teams webhook: no config — webhook URL is passed as the destination at call time.

| Property | Module | Purpose |
|----------|--------|---------|
| `casehub.connectors.slack-bot.token` | `casehub-connectors-slack-bot` | Bot OAuth token for Slack Web API |
| Twilio Account SID + Auth Token | core | Twilio SMS outbound |
| WhatsApp API Token + Phone Number ID | core | WhatsApp outbound |
| IMAP host, port, username, password | email-inbound | Email inbound polling |

---

## Dependencies

Nothing in the casehubio ecosystem. Core module: `java.net.http.HttpClient`, `cloudevents-core` (CNCF CloudEvents SDK), `jackson-databind`. Optional modules: `quarkus-mailer` (email outbound), `jakarta.mail` (email inbound).

GroupId: `io.casehub` — published to GitHub Packages at `0.2-SNAPSHOT`.

---

## What This Repo Does NOT Do

- Provide domain logic — purely delivery infrastructure
- Route or schedule notifications — callers decide when and what to send
- Depend on casehub-work, casehub-ledger, or casehub-engine

**Consolidation rule:** Do not implement a new Slack, Teams, SMS, email, or inbound connector in any other repo. All outbound and inbound messaging routes through these SPIs. If a new channel type is needed, add it here.
