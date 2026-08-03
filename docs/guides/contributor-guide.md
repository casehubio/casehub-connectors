# casehub-connectors -- Contributor Guide

> Internal architecture, extension points, and development details for platform builders modifying casehub-connectors.

**GitHub:** [casehubio/connectors](https://github.com/casehubio/connectors)

---

## Internal Architecture

### Outbound Delivery Pipeline

The outbound pipeline has three layers:

1. **`Connector` SPI** (`core`) -- `@ApplicationScoped` CDI beans discovered at startup. Each has an `id()`, a `send(ConnectorMessage)` method returning `boolean`, and a `channelType()` for notification bridging.

2. **`ConnectorService`** (`core`) -- routing service. Constructor receives `@All List<Connector>`, indexes by `id()`. Duplicate ids cause startup failure. `send(connectorId, message)` looks up the connector and delegates. `supports(id)` and `ids()` for introspection.

3. **MCP tools** (`mcp`) -- `@Blocking` tool methods that call `ConnectorService.send()`, then `ConnectorMeshBridge.notifyDelivered()`. Each tool class handles one connector type, with content sanitized via `McpContentSanitizer` (strips ASCII control chars 0x00-0x1F and DEL, truncates to 500 chars).

**ConnectorMeshBridge** (`core`) -- SPI notifying the active mesh implementation that an MCP tool dispatched a delivery. The default `NoOpConnectorMeshBridge` (`@DefaultBean @Unremovable`) does nothing. When `qhorus/connector-backend` is on the classpath, its implementation activates and posts a `STATUS` message to the configured delivery channel. Contract: must return quickly, never throw, tolerate absent case context.

### Inbound Pipeline

Two transport patterns converge into a single CDI event bus:

**Pull-based (`InboundConnector`):** `InboundConnectorService` calls `start(InboundMessageSink)` on every registered `InboundConnector` at Quarkus startup, passing `this::receive` as the sink. At shutdown, calls `stop()` on all. The `receive()` method fires `Event<InboundMessage>.fireAsync()`.

**Push-based (`WebhookInboundConnector`):** `WebhookRouter` (`@Path("/connectors")`) dispatches `GET|POST /connectors/{id}/webhook` to registered `WebhookInboundConnector` beans (discovered via `@All List<WebhookInboundConnector>`). The router calls the connector's `handle(WebhookRequest)` method, which returns a sealed `WebhookResult`:

- `Delivered(List<InboundMessage>)` -- router calls `InboundConnectorService.receive()` for each message, returns HTTP 200
- `Challenged(responseBody, contentType)` -- router returns HTTP 200 with the challenge body (platform verification handshakes)
- `Ignored()` -- HTTP 200, no event fired (bot messages, unsupported event types)
- `Unauthorized()` -- POST returns HTTP 200 + SECURITY WARNING log (suppress retry storms); GET returns HTTP 403 (admin needs clear failure signal)

**Exception handling:** the router wraps `handle()` in a try-catch as defense-in-depth, mapping exceptions to HTTP 200. Connectors are still expected to catch their own exceptions and return `Ignored` or `Unauthorized`.

**WebhookRequest** -- transport-agnostic record built by the router from JAX-RS types. Header keys are normalized to lower-case. Includes `requestUrl` (required by Twilio's signature scheme). `tenancyId()` reads the `x-tenancy-id` header.

**Webhook signature verification** -- each webhook connector handles its own signature verification via `SigHelper` (shared HMAC utilities with constant-time comparison in `webhook` module):

| Connector | Algorithm | Notes |
|-----------|-----------|-------|
| `SlackInboundConnector` | HMAC-SHA256 | Signing secret; `url_verification` challenge handling; filters bot messages and subtypes (message_changed, message_deleted, channel_join) |
| `TeamsInboundConnector` | HMAC-SHA256 | Base64-encoded shared secret |
| `WhatsAppInboundConnector` | HMAC-SHA256 | GET challenge via `hub.mode` verify token |
| `TwilioSmsInboundConnector` | HMAC-SHA1 | Twilio algorithm; form-encoded POST; signs full request URL |

**ID validation** -- both `InboundConnectorService` and `WebhookRouter` validate connector IDs at startup against pattern `[a-z0-9][a-z0-9\-]*`. Violations cause startup failure.

### CloudEvent Adapter

`ConnectorsCloudEventAdapter` -- `@ApplicationScoped` CDI adapter observing `@ObservesAsync InboundMessage`, fires `Event<CloudEvent>.fireAsync()`. CloudEvent fields:
- `type`: `io.casehub.connectors.inbound.<connectorType>` (uses `InboundConnectorTypes` values, not connector IDs)
- `source`: `/casehub-connectors/<connectorId>`
- `subject`: `channel/<externalChannelRef>`
- `data`: JSON-serialized `InboundMessage` via Jackson `ObjectMapper`
- `tenancyid` extension: propagated when `InboundMessage.tenancyId()` is non-null

### Chat Platform Architecture

**`ChatPlatform` SPI** (`chat-spi`) -- composed of 9 capability interfaces. Only `Messaging` is required; all others degrade gracefully via provided fallback implementations in `io.casehub.connectors.chat.degraded`:

| Degraded Implementation | What it does |
|------------------------|-------------|
| `ChannelFallbackThreading` | Threading falls back to channel-level messaging (sends reply as a new message to the channel) |
| `EmptyDiscovery` | Returns empty channel list |
| `NoOpReactions` | No-ops on add/remove, returns empty list |
| `UnknownPresence` | Returns UNKNOWN status for all members |
| `EmptyMembers` | Returns empty member list |
| `NoOpChannelManagement` | No-ops on create/delete, returns empty on find |
| `NoOpMemberManagement` | No-ops on add/remove |
| `EmptyMessageHistory` | Returns empty message list |

**`ChatPlatformService`** (`chat-spi`) -- routing service, same pattern as `ConnectorService`. Constructor receives `@All List<ChatPlatform>`, indexes by `id()`. Duplicate ids cause startup failure.

**`ChatInboundAdapter`** (`chat-spi`) -- CDI adapter observing `@ObservesAsync InboundMessage`. Looks up an `InboundTranslator` by `connectorType`, translates to `ReceivedMessage`, and fires `Event<ReceivedMessage>.fireAsync()`. Each chat platform module provides its `InboundTranslator` implementation (e.g. `DiscordInboundTranslator`, `SlackInboundTranslator`, `IrcInboundTranslator`, `RefInboundTranslator`).

**`InboundTranslator` SPI** (`chat-spi`):
```java
public interface InboundTranslator {
    String connectorType();  // matches InboundConnectorTypes constant
    ReceivedMessage translate(InboundMessage msg);
}
```

### Calendar Platform Architecture

**`CalendarPlatform` SPI** (`calendar-spi`) -- 7 methods: `id()`, `listCalendars()`, `listEvents(calendarId, from, to)`, `getEvent(calendarId, eventId)`, `createEvent(calendarId, EventDetails)`, `updateEvent(calendarId, eventId, EventDetails)`, `deleteEvent(calendarId, eventId)`.

**`CalendarPlatformService`** (`calendar-spi`) -- routing service, same `@All List<CalendarPlatform>` pattern.

**`EventTiming`** -- sealed interface with two permitted records: `Timed(Instant start, Instant end, ZoneId timeZone)` and `AllDay(LocalDate start, LocalDate end)`. All fields are non-null (enforced by compact constructors).

**`GoogleCalendarPlatform`** (`calendar-google`) -- uses `google-api-services-calendar` with OAuth2 refresh token auth via `UserCredentials`. `listEvents` paginates up to 20 pages. `GoogleEventMapper` handles bidirectional mapping between Google Calendar model and `CalendarEvent`/`EventDetails`. Inactive when credentials are blank.

### Notification Bridge Architecture

**`NotificationBridgeStartup`** (`notification-bridge`) -- `@Startup @ApplicationScoped`. At `@PostConstruct`:
1. Indexes all `DestinationResolver` beans by `channelId()`
2. Indexes all `DigestFormatter` beans by `channelId()`
3. Iterates all `Connector` beans. For each with non-null `channelType()`:
   - Validates no duplicate `channelType` across connectors
   - Finds matching `DestinationResolver`, or falls back to `ConfigDestinationResolver` scanning `casehub.notification.destinations.<channel>.*`
   - Finds matching `DigestFormatter`
   - Creates `ConnectorNotificationDeliverer` wrapping the connector
   - Registers with `DeliveryChannelRegistry` including a `DeliveryChannelDescriptor`

**`DeliveryChannelDescriptor` configuration:**
- Display names: `email -> "Email"`, `sms -> "SMS"`, `slack -> "Slack"`, `teams -> "Teams"`, `whatsapp -> "WhatsApp"`
- Retry policies: email and sms have `NotificationSeverity.WARNING` retry threshold
- Per-tenant scope: slack and teams are `PER_TENANT`; all others are `PER_USER`

**`ConnectorNotificationDeliverer`** -- implements `NotificationDeliverer`. Two delivery methods:
- `deliver(NotificationInput)` -- resolves destination via `DestinationResolver`, creates `ConnectorMessage` with category/severity/actionUrl in attributes, delegates to `Connector.send()`
- `deliverDigest(DigestSummary)` -- resolves destination, formats via `DigestFormatter` (or `DefaultDigestFormat` fallback), delegates to `Connector.send()`

**`ConfigDestinationResolver`** -- reads destinations from MP Config properties: `casehub.notification.destinations.<channel>.<userId>=<destination>`. Created automatically during bridge startup when no CDI `DestinationResolver` is found but config properties exist.

**Built-in `DigestFormatter` implementations:**
- `EmailDigestFormatter` (channel: `email`) -- HTML digest grouped by `DigestGroupBy.CATEGORY`, uses `format=html` attribute. `ENTITY` grouping not yet supported (logs warning, falls back to FLAT)
- `SmsDigestFormatter` (channel: `sms`) -- short text summary
- `WhatsAppDigestFormatter` (channel: `whatsapp`) -- rich text summary

---

## Full Module Details

### core (`casehub-connectors`)

**SPIs defined:** `Connector`, `InboundConnector`, `WebhookInboundConnector`, `ConnectorDiscovery`, `ConnectorMeshBridge`, `InboundMessageSink`

**Services:** `ConnectorService` (outbound routing), `InboundConnectorService` (inbound lifecycle + CDI event bus)

**Records:** `ConnectorMessage`, `InboundMessage`, `Attachment`, `DiscoveredTarget`, `WebhookRequest`, `WebhookResult` (sealed)

**Constants:** `InboundConnectorIds`, `InboundConnectorTypes`, `HttpMethod`

**Outbound implementations:** `SlackConnector` (ID `"slack"`), `TeamsConnector` (ID `"teams"`), `TwilioSmsConnector` (ID `"twilio-sms"`, channelType `"sms"`), `WhatsAppConnector` (ID `"whatsapp"`)

**Utilities:** `HttpHelper` (shared `HttpClient` with 5s connect timeout, `postJson`, `jsonQuote`, `jsonEscape`), `NoOpConnectorMeshBridge` (`@DefaultBean @Unremovable`)

**CloudEvent adapter:** `ConnectorsCloudEventAdapter`

### webhook (`casehub-connectors-webhook`)

**JAX-RS router:** `WebhookRouter` (`@Path("/connectors")`) dispatching `GET|POST /connectors/{id}/webhook`

**Webhook connectors:** `SlackInboundConnector`, `TeamsInboundConnector`, `WhatsAppInboundConnector`, `TwilioSmsInboundConnector`

**Utilities:** `SigHelper` (HMAC signature verification with constant-time comparison)

### email (`casehub-connectors-email`)

`EmailConnector` (ID `"email"`) -- SMTP outbound via `quarkus-mailer`. Supports `format=html` attribute for HTML rendering via `Mail.withHtml()`. Subject defaults to `"Notification"` when title is blank.

### email-inbound (`casehub-connectors-email-inbound`)

`EmailInboundConnector` -- IMAP polling, implements `InboundConnector`.

`EmailInboundAccountProvider` -- `@FunctionalInterface` SPI returning `List<EmailInboundAccount>`. Default reads a single account from MP Config; override by providing an `@ApplicationScoped` bean without `@DefaultBean`.

`ContentExtractor` / `ExtractionResult` -- MIME content extraction with attachment parsing.

### slack-bot (`casehub-connectors-slack-bot`)

`SlackBotClient` (ID `"slack-bot"`) -- pure `java.net.http` client for Slack Web API. 16 methods including 2 `postMessage` overloads.

**API methods:** `postMessage` (2 overloads: with/without Block Kit blocks), `listConversations`, `listChannels`, `addReaction`, `removeReaction`, `getReactions`, `getPresence`, `listConversationMembers`, `listUsers`, `createConversation`, `getConversationInfo`, `inviteToConversation`, `kickFromConversation`, `archiveConversation`, `getHistory`

**Pagination:** generic `paginateGet<T>` helper with fail-soft partial results. Caps at 50 pages. On mid-loop error/interruption, returns accumulated results rather than empty list. Rate limiting (429) during pagination is NOT retried -- surfaces as a warning.

**Rate limiting:** on HTTP 429, reads `Retry-After`, sleeps, retries once. Safe on virtual threads.

**Result records:** `PostResult`, `ConversationInfo` (includes `numMembers`), `ConversationResult`, `ApiResult`, `ReactionListResult`, `PresenceResult`, `UserInfo`, `HistoryMessage`, `HistoryResult`, `PageSlice<T>` (internal)

`SlackBotDiscovery` -- implements `ConnectorDiscovery`, delegates to `SlackBotClient.listChannels()`.

### discord (`casehub-connectors-discord`)

`DiscordClient` -- REST API v10 client. Operations: send, reply, list channels, list guilds, reactions, members, attachments (CDN download with SSRF defense), rich embed serialization. Rate-limit retry on 429.

`DiscordGateway` -- Gateway v10 WebSocket via Vert.x. Full lifecycle: HELLO, IDENTIFY, HEARTBEAT, DISPATCH, RESUME, re-IDENTIFY on INVALID_SESSION. States: DISCONNECTED, CONNECTING, HELLO_RECEIVED, IDENTIFYING, READY, RUNNING, RESUMING. Exponential backoff (max 60s). Virtual threads. NOT a CDI bean -- instantiated by `DiscordInboundConnector`.

`DiscordGatewayPresenceCache` -- caches presence status from Gateway PRESENCE_UPDATE events.

`DiscordDiscovery` -- discovers guilds and channels via REST API.

**Model records:** `DiscordAttachment`, `DiscordChannel`, `DiscordEmbed`, `DiscordGuild` (with nullable `approximateMemberCount`), `DiscordMember`, `DiscordMessage`, `DiscordUser`, `PermissionOverwrite`, `PostResult`

### chat-spi (`casehub-connectors-chat-spi`)

**SPI:** `ChatPlatform`, `DefaultChatPlatform` (internal), `ChatPlatform.Builder`

**Capability interfaces:** `Messaging`, `Threading`, `Discovery`, `Reactions`, `Presence`, `Members`, `ChannelManagement`, `MemberManagement`, `MessageHistory`

**Degraded implementations:** `ChannelFallbackThreading`, `EmptyDiscovery`, `NoOpReactions`, `UnknownPresence`, `EmptyMembers`, `NoOpChannelManagement`, `NoOpMemberManagement`, `EmptyMessageHistory`

**Services:** `ChatPlatformService` (routing), `ChatInboundAdapter` (InboundMessage -> ReceivedMessage bridge)

**Translation SPI:** `InboundTranslator` -- `connectorType()` + `translate(InboundMessage) -> ReceivedMessage`

**Model records:** `RichCard` (with `Field` and `Builder`), `Channel`, `ChatContent`, `ReceivedMessage`, `SendResult`, `Member`, `MemberRef`, `ChatChannelRef`, `ChatMessageRef`, `PresenceStatus`

### chat-ref (`casehub-connectors-chat-ref`)

`RefChatPlatform` (ID `"ref"`) -- in-memory reference implementation. All 9 capabilities backed by `InMemoryChatBackend`.

`ChatBackend` / `InMemoryChatBackend` -- storage abstraction for the reference implementation.

`RefInboundTranslator` -- `InboundTranslator` for connector type `"ref"`.

### chat-irc (`casehub-connectors-chat-irc`)

`IrcChatPlatform` (ID `"irc"`) -- 3 native capabilities (Messaging, Discovery, Members).

`IrcClient` -- IRC protocol client.

`IrcInboundConnector` -- implements `InboundConnector` for IRC.

`IrcInboundTranslator` -- `InboundTranslator` for connector type `"irc"`.

**Protocol records:** `IrcMessage`, `IrcCommand`, `ChannelInfo`, `IrcParser`.

### chat-discord (`casehub-connectors-chat-discord`)

`DiscordChatPlatform` (ID `"discord"`) -- 8 native capabilities: Messaging, Threading, Discovery, Reactions, Presence (via `DiscordGatewayPresenceCache`), Members, ChannelManagement, MessageHistory. MemberManagement is degraded (`NoOpMemberManagement`).

`DiscordInboundConnector` (`@ApplicationScoped`, implements `InboundConnector`) -- handles Gateway events: MESSAGE_CREATE, GUILD_CREATE, PRESENCE_UPDATE. Filters bot messages. Downloads attachments on virtual threads. Connector type: `"discord"`.

`DiscordInboundTranslator` -- `InboundTranslator` for connector type `"discord"`. RichCard-to-DiscordEmbed bidirectional translation.

`ChannelManagement.delete()` -- calls `DELETE /channels/{id}` (true deletion, unlike Slack which archives).

### chat-slack (`casehub-connectors-chat-slack`)

`SlackChatPlatform` (ID `"slack"`) -- 9 native capabilities (most complete implementation). RichCard-to-Block Kit translation. Batch user fetch for member listing. Full ts-precision message history.

`SlackInboundTranslator` -- `InboundTranslator` for connector type `"slack"`. Inbound rich content parsing: surfaces embeds/blocks as `RichCard`.

`ChannelManagement.delete()` -- calls `conversations.archive` (Slack does not support true channel deletion).

### mcp (`casehub-connectors-mcp`)

**MCP tool classes:** `SlackMcpTool`, `TeamsMcpTool`, `TwilioSmsMcpTool`, `WhatsAppMcpTool`, `EmailMcpTool`, `ChatPlatformMcpTool` (send_chat, list_chat_channels), `ChannelDiscoveryMcpTool` (list_channels), `CalendarMcpTool` (6 calendar operations)

All annotated `@Blocking` (required -- MCP tool methods run on event-loop thread by default).

`McpContentSanitizer` -- strips ASCII control characters (0x00-0x1F, 0x7F) and truncates to 500 chars before passing to `ConnectorMeshBridge`.

`ChatPlatformMcpTool.send_chat` supports:
- Flat card parameters (cardTitle, cardDescription, cardColor, cardUrl, etc.)
- JSON array of multiple cards via `cards` parameter (overrides flat params when present)
- Threaded replies via `parentMessageId` parameter

`CalendarMcpTool` handles timing mode detection (timed vs all-day), merge semantics for updates (omitted fields keep current values from existing event), and `calendarId` defaulting to `"primary"`.

### notification-bridge

`NotificationBridgeStartup` -- `@Startup @ApplicationScoped`. Auto-discovers and registers connector-backed delivery channels.

`ConnectorNotificationDeliverer` -- `NotificationDeliverer` implementation wrapping a `Connector`.

`ConfigDestinationResolver` -- config-property-based fallback resolver.

`DigestFormatter` -- CDI SPI: `channelId()` + `format(DigestSummary, destination) -> ConnectorMessage`.

`DefaultDigestFormat` -- static utility for plain-text fallback formatting.

Built-in formatters: `EmailDigestFormatter`, `SmsDigestFormatter`, `WhatsAppDigestFormatter`.

### calendar-spi

`CalendarPlatform` SPI, `CalendarPlatformService` routing.

Model records: `CalendarEvent`, `CalendarInfo`, `EventDetails`.

Sealed `EventTiming`: `Timed(Instant, Instant, ZoneId)` | `AllDay(LocalDate, LocalDate)`.

### calendar-ref

`RefCalendarPlatform` (ID `"ref"`) -- in-memory reference backed by `InMemoryCalendarBackend`.

`CalendarBackend` / `InMemoryCalendarBackend` -- storage abstraction.

### calendar-google

`GoogleCalendarPlatform` (ID `"google"`) -- Google Calendar API via `google-api-services-calendar`. OAuth2 refresh token auth via `UserCredentials`. Paginated `listEvents` (max 20 pages). Inactive when credentials are blank.

`GoogleEventMapper` -- bidirectional mapping between Google Calendar API model and `CalendarEvent`/`EventDetails`, including `EventTiming` sealed type handling.

---

## Cross-Repo Integration

These are NOT modules in this repo but are relevant for understanding the ecosystem:

| Module | Location | What it does |
|--------|----------|-------------|
| `casehub-qhorus-connector-backend` | casehub-qhorus repo | `InboundMessage -> ConnectorChannelBackend` bridge; activates by classpath presence. Implements `ConnectorMeshBridge` to post STATUS messages to the delivery channel |

---

## Depended On By

| Repo | Usage |
|------|-------|
| `casehub-qhorus` | Optional `WatchdogAlertEvent -> ConnectorService.send()` bridge; `ConnectorMeshBridge` implementation for mesh integration |
| `casehub-engine` | Escalation and notification paths (not yet wired) |
| `casehub-work` | `casehub-work-notifications` should delegate to `casehub-connectors` (known consolidation gap -- parallel Slack/Teams implementations exist) |
| `casehub-life` | Household and care notifications (contractor alerts, carer escalations) |
| `chat-app` | Standalone chat application (migrated from `chat-demo` directory, now in `casehubio/chat-app`) |

---

## Current State

- 16 active modules in pom.xml
- `chat-demo` directory still exists on disk but is excluded from pom.xml modules -- migrated to `casehubio/chat-app`
- Published to GitHub Packages at `0.2-SNAPSHOT`, GroupId `io.casehub`
- Not yet wired into casehub-engine or casehub-work escalation paths
- `ChannelManagement.delete()` behaves differently per platform: Slack archives via `conversations.archive`, Discord calls `DELETE /channels/{id}`
- `EmailDigestFormatter` does not yet support `DigestGroupBy.ENTITY` (logs warning, falls back to FLAT)
- SlackBotClient pagination does not retry on 429 mid-loop (known deferred gap)

**Notification consolidation rule:** `casehub-work-notifications` currently has parallel Slack/Teams implementations -- known overlap risk, should be resolved by delegating to `casehub-connectors`.

**Open issues (as of August 2026):**
- #45 -- Teams ChatPlatform implementation (chat-teams module)
- #32 -- Discord slash commands and interactions support
- #58 -- Responsive layout primitives for pages-runtime

---

## Design Documents

- `ARC42STORIES.MD` -- primary design doc (check sections 9-10 after SPI, module, or connector changes)
- `docs/adr/INDEX.md` -- architecture decision records
- `docs/DESIGN.md` -- legacy design doc
