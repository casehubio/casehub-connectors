# casehub-connectors — Contributor Guide

> Internal architecture, extension points, and development details for platform builders modifying casehub-connectors.

**GitHub:** [casehubio/connectors](https://github.com/casehubio/connectors)

---

## Internal Architecture

### Outbound Delivery

The `Connector` CDI SPI has two methods: an `id()` accessor and a `send()` method taking a `ConnectorMessage` (destination, title, body). Returns `boolean`. Custom connectors implement it as CDI beans — auto-discovered at startup.

`ConnectorMeshBridge` SPI (in core) — called by MCP tools after successful delivery. No-op `@DefaultBean @Unremovable` default. Qhorus bridge implementation activates by classpath presence and posts `EVENT` to the active observe channel. Contract: must return quickly, never throw, tolerate absent case context.

### Inbound Pipeline

`InboundConnectorService` polls all registered `InboundConnector` implementations on a configurable schedule and fires `Event<InboundMessage>` via `fireAsync()`. At-least-once delivery.

`WebhookRouter` (`@Path("/connectors")`) dispatches `GET|POST /connectors/{id}/webhook` to registered `WebhookInboundConnector` beans. Each webhook connector handles its own signature verification via `SigHelper` (shared HMAC utilities with constant-time comparison).

Webhook signature schemes:
- Slack: HMAC-SHA256 with signing secret, url_verification challenge handling, bot-message filtering
- Teams: HMAC-SHA256 with Base64-encoded shared secret
- WhatsApp: HMAC-SHA256 + GET hub.mode verify token challenge
- Twilio SMS: HMAC-SHA1 (Twilio algorithm), form-encoded POST

### CloudEvent Adapter

`ConnectorsCloudEventAdapter` — CDI adapter observing `@ObservesAsync InboundMessage`, fires `Event<CloudEvent>.fireAsync()` with type `io.casehub.connectors.inbound.<connectorType>`. Follows canonical CloudEvent adapter pattern (GE-20260621-629712). `InboundMessage.connectorType` (non-null, enforced by compact constructor; values: slack, email, sms, whatsapp, teams, discord, irc) and `tenancyId` (nullable) propagated as CloudEvent extensions.

### Notification Bridge

`notification-bridge` module bridges the platform notification delivery system (`NotificationDeliverer`, `DeliveryChannelRegistry`) to the connector SPI. Each `Connector` with non-null `channelType()` auto-registers at startup. `Connector.channelType()` defaults to `id()`; override to map to a different channel type (`TwilioSmsConnector` -> `"sms"`) or return `null` to opt out.

`DeliveryChannelDescriptor` carries `DestinationScope` (PER_USER or PER_TENANT). Per-tenant channels deliver once per tenant per event with dispatcher deduplication.

`DestinationResolver` SPI (in `casehub-platform-api`) resolves `userId` -> connector-specific destination. Config-based fallback reads from `casehub.notification.destinations.<channel>.<userId>`.

`DigestFormatter` CDI SPI provides channel-type-aware digest delivery. `EmailConnector` supports `format=html` attribute for HTML rendering via `Mail.withHtml()`.

---

## Full Module Details

| Module | Contents |
|--------|----------|
| `casehub-connectors` (core) | `Connector` SPI + Slack, Teams, Twilio SMS, WhatsApp outbound impls; `InboundConnector` SPI + `InboundConnectorService` polling engine; `WebhookInboundConnector` abstract base. `ConnectorDiscovery` SPI — optional interface for discoverable targets. `ConnectorsCloudEventAdapter`. `ConnectorMeshBridge` SPI. |
| `casehub-connectors-webhook` | JAX-RS `WebhookRouter`, `SlackInboundConnector`, `TeamsInboundConnector`, `WhatsAppInboundConnector`, `TwilioSmsInboundConnector`. `SigHelper` for HMAC utilities. |
| `casehub-connectors-email` | SMTP outbound via `quarkus-mailer` |
| `casehub-connectors-email-inbound` | `EmailInboundConnector` — IMAP polling, `EmailInboundAccountProvider` SPI |
| `casehub-connectors-mcp` | MCP tool surface: `send_slack`, `send_teams`, `send_sms`, `send_whatsapp`, `send_email`, `send_chat`, `list_channels`, `list_chat_channels`, `calendar_list_calendars`, `calendar_list_events`, `calendar_get_event`, `calendar_create_event`, `calendar_update_event`, `calendar_delete_event`. Integrates with Qhorus via `ConnectorMeshBridge` SPI. |
| `casehub-connectors-slack-bot` | `SlackBotClient` — pure `java.net.http` client for Slack Web API (16 methods including 2 `postMessage` overloads). `ConversationInfo` includes `numMembers`. Paginating methods use generic `paginateGet<T>` helper with fail-soft partial results. |
| `casehub-connectors-discord` | `DiscordClient` (REST API v10 — send, reply, channels, guilds, reactions, members, attachments; rate-limit retry on 429; CDN attachment download with SSRF defense + rich embed serialization). `DiscordGateway` — Gateway v10 WebSocket via Vert.x; full lifecycle: HELLO, IDENTIFY, HEARTBEAT, DISPATCH, RESUME, re-IDENTIFY on INVALID_SESSION; states: DISCONNECTED, CONNECTING, HELLO_RECEIVED, IDENTIFYING, READY, RUNNING, RESUMING; exponential backoff (max 60s); virtual threads; NOT a CDI bean — instantiated by `DiscordInboundConnector`. `DiscordGuild` with nullable `approximateMemberCount`. |
| `casehub-connectors-chat-spi` | `ChatPlatform` SPI, capability interfaces (`Messaging`, `Threading`, `Discovery`, `Reactions`, `Presence`, `Members`, `ChannelManagement`, `MemberManagement`, `MessageHistory`), `RichCard` model with Builder, `ChatContent`, `Channel` (with `memberCount`), `ReceivedMessage`. |
| `casehub-connectors-chat-ref` | In-memory reference `ChatPlatform` for testing. |
| `casehub-connectors-chat-discord` | Discord `ChatPlatform` — 8 native capabilities (Messaging, Threading, Discovery, Reactions, Presence via `DiscordGatewayPresenceCache`, Members, ChannelManagement, MessageHistory; MemberManagement degraded). RichCard <-> DiscordEmbed translation. `DiscordInboundConnector` (`@ApplicationScoped`, implements `InboundConnector`) — handles Gateway events: MESSAGE_CREATE, GUILD_CREATE, PRESENCE_UPDATE; filters bot messages; downloads attachments on virtual threads; connectorType `"discord"`. |
| `casehub-connectors-chat-slack` | Slack `ChatPlatform` — 9 native capabilities (most complete). RichCard <-> Block Kit translation. Batch user fetch for members, full ts-precision message history. |
| `casehub-connectors-chat-irc` | IRC `ChatPlatform` — 3 native capabilities. |
| `casehub-connectors-qhorus` | Optional — `WatchdogAlertEvent -> ConnectorService.send()` bridge (Qhorus -> connectors); activates by classpath presence |
| `notification-bridge` | Bridges platform notification delivery to connector SPI. `DeliveryChannelDescriptor` with `DestinationScope`, `DestinationResolver` SPI, `DigestFormatter` SPI. |
| `calendar-spi` | `CalendarPlatform` SPI — list calendars, list/get/create/update/delete events. Sealed `EventTiming` model (Timed/AllDay). |
| `calendar-ref` | In-memory reference `CalendarPlatform` for testing. |
| `calendar-google` | Google Calendar API with OAuth2 refresh token auth, paginated listEvents. |

**Cross-repo integration (not in this repo):**

| Module | Location | What it does |
|--------|----------|-------------|
| `casehub-qhorus-connector-backend` | casehub-qhorus repo | `InboundMessage -> ConnectorChannelBackend` bridge; activates by classpath presence |

---

## Depended On By

| Repo | Usage |
|------|-------|
| `casehub-engine` | Escalation and notification paths (not yet wired) |
| `casehub-work-notifications` | Should delegate to `casehub-connectors` rather than maintain its own Slack/Teams implementations |
| `casehub-qhorus` | Optional `WatchdogAlertEvent -> ConnectorService.send()` bridge; `casehub-qhorus-slack-channel` (pending) will depend on `SlackBotClient` |
| `casehub-life` | Household and care notifications (contractor alerts, carer escalations) |

---

## Current State

- Multiple shipped epics — connectors#4 (webhook inbound SPI), connectors#7 (email inbound)
- Chat-demo app migrated to `casehubio/chat-app` — removed from connectors build (directory still exists but excluded from pom.xml modules)
- Published to GitHub Packages at `0.2-SNAPSHOT`
- GroupId: `io.casehub`
- Not yet wired into casehub-engine or casehub-work escalation paths
- `ChannelManagement` SPI includes `delete()` — Slack archives via `conversations.archive`, Discord calls `DELETE /channels/{id}`

**Notification consolidation rule:** `casehub-work-notifications` currently has parallel Slack/Teams implementations — known overlap risk, should be resolved by delegating to `casehub-connectors`.

---

## Design Documents

- `ARC42STORIES.MD` — primary design doc (check sections 9-10 after SPI, module, or connector changes)
- `docs/adr/INDEX.md` — architecture decision records
- `docs/DESIGN.md` — legacy design doc
