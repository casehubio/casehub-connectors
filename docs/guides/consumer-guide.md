# casehub-connectors -- Consumer Guide

> Outbound and inbound message connector library for the casehubio platform.

**GitHub:** [casehubio/connectors](https://github.com/casehubio/connectors)
**Tier:** Foundation (no casehubio dependencies)

---

## Purpose

Canonical notification and messaging infrastructure for the platform. Provides CDI SPIs for outbound message delivery, inbound message reception, structured chat-system interaction, calendar integration, and notification bridging. Any casehubio repo that needs to send or receive messages must use these SPIs rather than implementing its own connectors.

Pure delivery infrastructure -- no domain logic, no routing decisions, no scheduling. Callers decide when, what, and to whom; observers decide what to do with received messages.

No Camel, no vendor SDKs -- pure `java.net.http.HttpClient` for HTTP-based connectors.

---

## Module Structure

There are 17 active modules in the build (pom.xml `<modules>`):

| Module (artifactId prefix: `casehub-connectors-`) | What consumers need to know |
|----------------------------------------------------|-----------------------------|
| `core` | `Connector` outbound SPI, `InboundConnector` / `WebhookInboundConnector` inbound SPIs, `ConnectorService` routing, `ConnectorDiscovery` SPI, `ConnectorsCloudEventAdapter`, `ConnectorMeshBridge` SPI, built-in outbound impls (Slack, Teams, Twilio SMS, WhatsApp), `InboundMessage` / `Attachment` / `ConnectorMessage` records |
| `email` | Email outbound via `quarkus-mailer` (`EmailConnector`) |
| `email-inbound` | IMAP polling inbound (`EmailInboundConnector`), `EmailInboundAccountProvider` SPI |
| `webhook` | JAX-RS `WebhookRouter` + webhook-based inbound connectors (Slack, Teams, WhatsApp, Twilio SMS), `SigHelper` HMAC utilities |
| `mcp` | MCP tool surface for LLM agents: `send_slack`, `send_teams`, `send_sms`, `send_whatsapp`, `send_email`, `send_chat`, `list_channels`, `list_chat_channels`, plus 6 calendar tools |
| `slack-bot` | `SlackBotClient` -- pure `java.net.http` client for the Slack Web API (16 methods including 2 `postMessage` overloads). Pagination via generic `paginateGet<T>` with fail-soft partial results |
| `discord` | `DiscordClient` (REST API v10), `DiscordGateway` (Gateway v10 WebSocket via Vert.x), `DiscordGatewayPresenceCache`, `DiscordDiscovery` |
| `chat-spi` | `ChatPlatform` SPI, 9 capability interfaces, `ChatPlatformService` routing, `ChatInboundAdapter`, `InboundTranslator` SPI, model records (`RichCard`, `Channel`, `ChatContent`, `ReceivedMessage`, `SendResult`, `Member`, `PresenceStatus`) |
| `chat-ref` | In-memory reference `ChatPlatform` for testing (`RefChatPlatform`) |
| `chat-irc` | IRC `ChatPlatform` (3 native capabilities: Messaging, Discovery, Members) |
| `chat-discord` | Discord `ChatPlatform` (8 native capabilities), `DiscordInboundConnector` (Gateway-based), RichCard-to-DiscordEmbed translation |
| `chat-slack` | Slack `ChatPlatform` (9 native capabilities -- most complete), RichCard-to-Block Kit translation |
| `notification-bridge` | Bridges platform notification delivery system to connector SPI. `NotificationBridgeStartup`, `ConnectorNotificationDeliverer`, `DigestFormatter` SPI, `ConfigDestinationResolver` |
| `calendar-spi` | `CalendarPlatform` SPI, `CalendarPlatformService` routing, model records (`CalendarEvent`, `CalendarInfo`, `EventDetails`), sealed `EventTiming` (Timed/AllDay) |
| `calendar-ref` | In-memory reference `CalendarPlatform` for testing (`RefCalendarPlatform`) |
| `calendar-google` | Google Calendar API provider with OAuth2 refresh token auth, paginated `listEvents` |
| `graphql` | `ConnectorOperations` `@McpDomain("connectors")` SPI — GraphQL/MCP surface with 4 operations: `injectChat` (constructs `InboundMessage`, fires via `InboundConnectorService`), `sendNotification` (delegates to `ConnectorService.send()`), `connectorStatus` (aggregates outbound + chat + inbound connectors), `sentMessages` (queries `SentMessageCapture`, profile-gated). `ConnectorsModelEnricher` provides domain summary/state for MCP. `SentMessageCapture` (`@UnlessBuildProfile("prod")`) CDI observer for test/dev message capture. |

**CDI events:** `ConnectorService.send()` fires `Event<SentMessage>` on every outbound delivery. `SentMessage` record carries the connector ID, recipient, message content, and timestamp. Observe with `@ObservesAsync SentMessage` for delivery tracking.

---

## Key Consumer APIs and SPIs

### Outbound -- `Connector` SPI

```java
public interface Connector {
    String id();
    boolean send(ConnectorMessage message);
    default String channelType() { return id(); }
}
```

CDI SPI. Implementations are `@ApplicationScoped` beans auto-discovered at startup. `send()` takes a `ConnectorMessage(destination, title, body, attributes)` and returns `boolean` (success/failure).

**Contract:** `send()` must not throw unchecked exceptions, must be thread-safe, and should complete within its configured timeout.

`channelType()` defaults to `id()`. Override to map to a different notification channel type (e.g. `TwilioSmsConnector` returns `"sms"` instead of `"twilio-sms"`). Return `null` to opt out of notification bridging.

**ConnectorService** -- inject this rather than individual `Connector` beans. Routes by `connectorId`, validates registered ids, throws `IllegalArgumentException` for unknown ids.

```java
@Inject ConnectorService connectorService;
connectorService.send("slack", new ConnectorMessage(webhookUrl, "Title", "Body"));
```

**Built-in outbound implementations:**

| Connector ID | Class | Module | Auth | Notes |
|-------------|-------|--------|------|-------|
| `slack` | `SlackConnector` | core | Webhook URL in `destination` | No config needed -- webhook URL is the credential |
| `teams` | `TeamsConnector` | core | Webhook URL in `destination` | Renders as Adaptive Card (v1.4) |
| `twilio-sms` | `TwilioSmsConnector` | core | Account SID + Auth Token + From number in MP Config | `channelType()` returns `"sms"`. E.164 phone number in `destination` |
| `whatsapp` | `WhatsAppConnector` | core | API Token + Phone Number ID in MP Config | Template messages via `attributes("templateName")` + `attributes("templateLanguage")` (default `en_US`) |
| `email` | `EmailConnector` | `email` | SMTP via `quarkus-mailer` config | Supports `format=html` attribute for HTML rendering via `Mail.withHtml()` |

### ConnectorMessage Record

```java
public record ConnectorMessage(
    String destination,   // webhook URL, E.164 phone number, or email address
    String title,         // optional subject/card title (null = connector default)
    String body,          // main text content
    Map<String, String> attributes  // optional key-value metadata
) {}
```

Convenience constructors: `ConnectorMessage(destination, title, body)` and `ConnectorMessage(destination, body)`.

The `attributes` map carries connector-specific extras. Known attribute keys:
- `templateName` -- WhatsApp template name for first-contact / outside 24-hour window
- `templateLanguage` -- BCP-47 code for WhatsApp template (default `en_US`)
- `format` -- `"html"` for email HTML rendering
- `category`, `severity`, `actionUrl` -- set by notification bridge during delivery

### Inbound -- `InboundConnector` / `WebhookInboundConnector`

Two inbound SPIs for different transport patterns:

**`InboundConnector`** -- pull-based polling (e.g. IMAP, Discord Gateway). `@ApplicationScoped` CDI beans. `InboundConnectorService` calls `start(InboundMessageSink)` at startup, `stop()` at shutdown.

```java
public interface InboundConnector {
    String id();
    void start(InboundMessageSink sink);
    void stop();
}
```

**`WebhookInboundConnector`** -- push-based webhook reception. Abstract base class (NOT an interface). Does not implement `InboundConnector` -- webhook connectors have no pull lifecycle. Discovered by `WebhookRouter` via `@All List<WebhookInboundConnector>`.

```java
public abstract class WebhookInboundConnector {
    public abstract String id();
    public abstract WebhookResult handle(WebhookRequest request);
}
```

**ID contract for both:** must be lowercase, URL-safe, no slashes or spaces (pattern: `[a-z0-9][a-z0-9\-]*`). Validated at startup -- violations cause startup failure. Webhook connector IDs are also the URL path segment: `POST /connectors/{id}/webhook`.

**Breaking contract:** observers MUST use `@ObservesAsync InboundMessage` -- synchronous `@Observes` will not receive events. At-least-once delivery.

### InboundMessage Record

```java
public record InboundMessage(
    String connectorId,          // e.g. "slack-inbound", "email-inbound"
    String connectorType,        // non-null semantic type: "slack", "email", "sms", "whatsapp", "teams", "discord", "irc"
    String externalSenderId,
    String externalChannelRef,
    String content,
    List<Attachment> attachments, // always non-null, defensively copied
    Instant receivedAt,
    Map<String, String> metadata,
    String tenancyId             // nullable -- null in single-tenant deployments
) {}
```

Constants for connector IDs: `InboundConnectorIds` (e.g. `SLACK_INBOUND = "slack-inbound"`, `EMAIL = "email-inbound"`).
Constants for connector types: `InboundConnectorTypes` (e.g. `SLACK = "slack"`, `EMAIL = "email"`).

**Built-in inbound implementations:**

| Connector ID | Class | Module | Transport | Auth |
|-------------|-------|--------|-----------|------|
| `email-inbound` | `EmailInboundConnector` | `email-inbound` | IMAP polling | IMAP username/password in MP Config |
| `slack-inbound` | `SlackInboundConnector` | `webhook` | Webhook POST | HMAC-SHA256 signing secret |
| `teams-inbound` | `TeamsInboundConnector` | `webhook` | Webhook POST | HMAC-SHA256 with Base64-encoded shared secret |
| `whatsapp-inbound` | `WhatsAppInboundConnector` | `webhook` | Webhook POST/GET | HMAC-SHA256 + hub.mode verify token challenge |
| `twilio-sms-inbound` | `TwilioSmsInboundConnector` | `webhook` | Webhook POST | HMAC-SHA1 (Twilio algorithm), form-encoded |
| `discord-inbound` | `DiscordInboundConnector` | `chat-discord` | Discord Gateway WebSocket | Discord bot token |
| `irc-inbound` | `IrcInboundConnector` | `chat-irc` | IRC connection | IRC server config |

### ConnectorDiscovery SPI

Optional interface for connectors whose targets are discoverable at runtime (e.g. Slack channels via `conversations.list`).

```java
public interface ConnectorDiscovery {
    String id();                        // matches Connector.id()
    List<DiscoveredTarget> discover();  // DiscoveredTarget(id, displayName)
}
```

Contract: should not throw (caller catches per-discovery); return empty list on failure. The `list_channels` MCP tool aggregates all registered implementations.

### CloudEvent Adapter

`ConnectorsCloudEventAdapter` observes `@ObservesAsync InboundMessage` and fires `Event<CloudEvent>.fireAsync()`. CloudEvent type: `io.casehub.connectors.inbound.<connectorType>`. Source: `/casehub-connectors/<connectorId>`. Subject: `channel/<externalChannelRef>`. `tenancyId` propagated as CloudEvent extension when non-null.

### ChatPlatform SPI

Structured interface for chat-system interactions beyond simple message delivery. Nine capability interfaces compose the platform:

| Capability | Interface | Methods |
|-----------|-----------|---------|
| Messaging | `Messaging` | `send(ChatChannelRef, ChatContent) -> SendResult` |
| Threading | `Threading` | `reply(ChatMessageRef, ChatContent) -> SendResult` |
| Discovery | `Discovery` | `listChannels() -> List<Channel>` |
| Reactions | `Reactions` | `add(ChatMessageRef, emoji)`, `remove(ChatMessageRef, emoji)`, `list(ChatMessageRef) -> List<String>` |
| Presence | `Presence` | `of(MemberRef) -> PresenceStatus`, `set(MemberRef, PresenceStatus)` |
| Members | `Members` | `list(ChatChannelRef) -> List<Member>` |
| Channel Management | `ChannelManagement` | `create(name, topic, description, isPrivate) -> Channel`, `delete(channelId)`, `find(channelId) -> Optional<Channel>` |
| Member Management | `MemberManagement` | `add(ChatChannelRef, Member)`, `remove(ChatChannelRef, MemberRef)` |
| Message History | `MessageHistory` | `messages(ChatChannelRef, since) -> List<ReceivedMessage>` |

**Builder pattern with auto-degradation.** Only `Messaging` is required. All other capabilities fill with graceful degraded implementations if not provided:

```java
ChatPlatform.builder("my-platform")
    .messaging(myMessaging)        // required
    .threading(myThreading)        // optional -- falls back to ChannelFallbackThreading
    .discovery(myDiscovery)        // optional -- falls back to EmptyDiscovery
    .build();
```

`ChatPlatform.supports(Class<?>)` returns `true` for natively supported capabilities, `false` for degraded.

**ChatPlatformService** -- inject for routing. `platform(id)` throws `IllegalArgumentException` for unknown ids.

**Key model records:**

- `ChatContent(text, markdown, attachments, cards)` -- `text` is required; `cards` carries `List<RichCard>`
- `RichCard(title, description, url, color, fields, thumbnailUrl, imageUrl, footer, author)` -- platform-agnostic rich content. Requires at least `title` or `description`. Has `Builder`. Translated to platform-native formats automatically (Block Kit for Slack, DiscordEmbed for Discord)
- `Channel(ref, name, topic, description, isPrivate, memberCount)` -- `memberCount` is nullable `Integer`
- `SendResult(ok, messageRef, timestamp, error)` -- factory methods `success(ref, ts)` and `failure(error)`
- `ReceivedMessage(platformId, channel, messageRef, parentRef, sender, content, receivedAt)` -- `parentRef` nullable (non-null for threaded replies)

**ChatInboundAdapter** -- CDI adapter that observes `@ObservesAsync InboundMessage`, looks up a matching `InboundTranslator` by `connectorType`, translates to `ReceivedMessage`, and fires `Event<ReceivedMessage>.fireAsync()`. Each chat platform module provides its own `InboundTranslator`.

| Implementation | Platform ID | Native capabilities |
|----------------|------------|-------------------|
| `RefChatPlatform` | `ref` | All 9 (in-memory reference for testing) |
| `IrcChatPlatform` | `irc` | 3 (Messaging, Discovery, Members) |
| `DiscordChatPlatform` | `discord` | 8 (all except MemberManagement, which is degraded) |
| `SlackChatPlatform` | `slack` | 9 (most complete) |

### CalendarPlatform SPI

Calendar integration with full CRUD operations.

```java
public interface CalendarPlatform {
    String id();
    List<CalendarInfo> listCalendars();
    List<CalendarEvent> listEvents(String calendarId, Instant from, Instant to);
    CalendarEvent getEvent(String calendarId, String eventId);
    CalendarEvent createEvent(String calendarId, EventDetails details);
    CalendarEvent updateEvent(String calendarId, String eventId, EventDetails details);
    void deleteEvent(String calendarId, String eventId);
}
```

**CalendarPlatformService** -- inject for routing. Same pattern as `ConnectorService` and `ChatPlatformService`.

**Model records:**
- `CalendarInfo(id, summary, description, primary)` -- `primary` boolean marks the default calendar
- `CalendarEvent(id, calendarId, summary, description, location, timing, attendees, recurringEventId)` -- `recurringEventId` nullable
- `EventDetails(summary, description, location, timing, attendees)` -- input record for create/update
- `EventTiming` -- sealed interface: `Timed(start, end, timeZone)` or `AllDay(start, end)` where `start`/`end` are `Instant`/`LocalDate` respectively

| Implementation | Platform ID | Notes |
|----------------|------------|-------|
| `RefCalendarPlatform` | `ref` | In-memory reference for testing |
| `GoogleCalendarPlatform` | `google` | Google Calendar API with OAuth2 refresh token auth, paginated listEvents (max 20 pages) |

### Notification Bridge

The `notification-bridge` module bridges the platform notification delivery system (`NotificationDeliverer`, `DeliveryChannelRegistry` from `casehub-platform-api`) to the connector SPI.

**Auto-registration at startup:** `NotificationBridgeStartup` scans all `Connector` beans. Each with a non-null `channelType()` is registered as a notification delivery channel with `DeliveryChannelRegistry`.

**Destination scoping:** `DeliveryChannelDescriptor` carries `DestinationScope` (PER_USER or PER_TENANT). Slack and Teams are PER_TENANT -- per-tenant channels deliver once per tenant per event, with the dispatcher deduplicating across the per-user loop. All other channels are PER_USER.

**DestinationResolver** -- CDI SPI (in `casehub-platform-api`) resolves `userId` + `tenancyId` to a connector-specific destination string. Config-based fallback reads from `casehub.notification.destinations.<channel>.<userId>` when no CDI resolver is found for a channel type.

**DigestFormatter** -- CDI SPI for channel-type-aware digest delivery. Built-in formatters:
- `EmailDigestFormatter` -- HTML digest grouped by category, uses `format=html` attribute
- `SmsDigestFormatter` -- short text digest
- `WhatsAppDigestFormatter` -- rich text digest

When no `DigestFormatter` is registered for a channel, `DefaultDigestFormat` provides a plain-text fallback.

### MCP Tool Surface

The `mcp` module exposes tools for LLM agents via `quarkus-mcp-server`. All tools are annotated `@Blocking`.

| Tool name | Class | Parameters | What it does |
|-----------|-------|------------|-------------|
| `send_slack` | `SlackMcpTool` | webhookUrl, title, body | Post via Slack webhook |
| `send_teams` | `TeamsMcpTool` | webhookUrl, title, body | Post via Teams webhook |
| `send_sms` | `TwilioSmsMcpTool` | to (E.164), body | Send SMS via Twilio |
| `send_whatsapp` | `WhatsAppMcpTool` | to (E.164), body, templateName?, templateLanguage? | Send WhatsApp via Meta Cloud API |
| `send_email` | `EmailMcpTool` | to, subject, body | Send email via SMTP |
| `send_chat` | `ChatPlatformMcpTool` | platform, channel, text, parentMessageId?, card params or cards JSON | Send to any ChatPlatform with optional RichCard content and threading |
| `list_channels` | `ChannelDiscoveryMcpTool` | (none) | Aggregate all `ConnectorDiscovery` implementations |
| `list_chat_channels` | `ChatPlatformMcpTool` | platform | Rich channel listing from `ChatPlatform.discovery()` |
| `listCalendars` | `CalendarMcpTool` | platform | List available calendars |
| `listCalendarEvents` | `CalendarMcpTool` | platform, calendarId?, from, to | List events in time range |
| `getCalendarEvent` | `CalendarMcpTool` | platform, calendarId?, eventId | Get specific event |
| `createCalendarEvent` | `CalendarMcpTool` | platform, calendarId?, summary, description, location, timing params, attendees | Create event (timed or all-day) |
| `updateCalendarEvent` | `CalendarMcpTool` | platform, calendarId?, eventId, updatable fields | Update event (merge semantics -- omitted fields keep current values) |
| `deleteCalendarEvent` | `CalendarMcpTool` | platform, calendarId?, eventId | Delete event |

All MCP tools call `ConnectorMeshBridge.notifyDelivered()` after successful delivery for mesh integration.

`McpContentSanitizer` strips ASCII control characters and truncates to 500 chars before passing content to `ConnectorMeshBridge`.

---

## Configuration

Slack and Teams webhook connectors require no configuration -- the webhook URL is passed as the destination at call time.

| Property | Module | Purpose |
|----------|--------|---------|
| `casehub.connectors.twilio.account-sid` | core | Twilio Account SID |
| `casehub.connectors.twilio.auth-token` | core | Twilio Auth Token |
| `casehub.connectors.twilio.from` | core | Twilio sender phone number (E.164) |
| `casehub.connectors.whatsapp.api-token` | core | Meta Cloud API token |
| `casehub.connectors.whatsapp.phone-number-id` | core | WhatsApp Phone Number ID |
| `casehub.connectors.slack-bot.token` | slack-bot | Bot OAuth token (`xoxb-...`) |
| `casehub.connectors.slack-bot.api-base-url` | slack-bot | API base URL (default: `https://slack.com`; override for tests) |
| `casehub.connectors.calendar.google.client-id` | calendar-google | Google OAuth2 client ID |
| `casehub.connectors.calendar.google.client-secret` | calendar-google | Google OAuth2 client secret |
| `casehub.connectors.calendar.google.refresh-token` | calendar-google | Google OAuth2 refresh token |
| `quarkus.mailer.*` | email | SMTP configuration (host, port, from, username, password) |
| IMAP host, port, username, password | email-inbound | Email inbound polling (via `EmailInboundAccountProvider` SPI) |
| `casehub.notification.destinations.<channel>.<userId>` | notification-bridge | Config-based destination resolution fallback |

Connectors with blank credentials are no-ops -- they log a warning and return `false` from `send()`, allowing safe deployment without full configuration.

---

## Dependencies

Nothing in the casehubio ecosystem except `casehub-platform-api` (for `notification-bridge` only). Core module: `java.net.http.HttpClient`, `cloudevents-core` (CNCF CloudEvents SDK), `jackson-databind`. Optional modules: `quarkus-mailer` (email), `jakarta.mail` (email inbound), `quarkus-mcp-server` (MCP tools), Google Calendar API client (calendar-google), Vert.x WebSocket (Discord Gateway).

GroupId: `io.casehub` -- published to GitHub Packages at `0.2-SNAPSHOT`.

---

## What This Repo Does NOT Do

- Provide domain logic -- purely delivery infrastructure
- Route or schedule notifications -- callers decide when and what to send
- Depend on casehub-work, casehub-ledger, or casehub-engine
- Include vendor SDKs (Slack SDK, Twilio SDK) -- all HTTP-based connectors use `java.net.http.HttpClient` directly

**Consolidation rule:** Do not implement a new Slack, Teams, SMS, email, WhatsApp, Discord, IRC, or inbound connector in any other repo. All outbound and inbound messaging routes through these SPIs. If a new channel type is needed, add it here.
