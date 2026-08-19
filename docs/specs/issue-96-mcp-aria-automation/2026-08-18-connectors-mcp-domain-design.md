# MCP Domain for Connectors — Design Spec

**Issue:** casehubio/connectors#96
**Branch:** issue-96-mcp-aria-automation
**Date:** 2026-08-18

## Problem

The platform MCP surface (`casehub_model`/`casehub_action`) exposes engine and work domains but connectors have no `@McpDomain`. Chat injection — the entry point for scenario automation — requires direct `InboundConnectorService` calls with no platform-dispatch path. This breaks the MCP-only automation goal: the scenario engine cannot use `delivery: mcp` for connector operations.

## Solution

Add `@McpDomain("connectors")` via a new `casehub-connectors-graphql` module. Four operations: two mutations (`injectChat`, `sendNotification`) and two queries (`connectorStatus`, `sentMessages`). `GraphQLModelScanner` discovers the resolvers automatically — no platform-mcp changes required.

## Module: `casehub-connectors-graphql`

**Maven coordinates:** `io.casehub:casehub-connectors-graphql:0.2-SNAPSHOT`

**Pattern:** Same as `casehub-engine-graphql` / `casehub-work-graphql`.

### Dependencies

| Dependency | Scope | Purpose |
|-----------|-------|---------|
| `casehub-connectors-core` | compile | Connector, ConnectorService, InboundConnectorService, InboundMessage, SentMessage |
| `casehub-connectors-chat-spi` | compile | ChatPlatformService, ChatPlatform capabilities |
| `casehub-platform-api` | provided | McpDomain, PlatformQuery, PlatformMutation, ModelEnricher, CurrentPrincipal |
| `casehub-platform-graphql` | compile | Shared GraphQL types (PageInfo, PageInput) if needed |
| `quarkus-smallrye-graphql` | provided | GraphQL runtime |
| `jakarta.enterprise.cdi-api` | provided | CDI annotations |

### Build plugins

- `jandex-maven-plugin` — index for `GraphQLModelScanner` discovery
- `quarkus-maven-plugin` — `generate-code` / `generate-code-tests`

## Resolver Strategy

Define a `@McpDomain("connectors")` SPI interface with `@PlatformQuery` / `@PlatformMutation` methods. `GraphQLResolverProcessor` generates the `@GraphQLApi` resolver class at compile time, delegating to a CDI bean that implements the interface.

If any operation requires logic the generator cannot express as pure delegation, hand-write that resolver. `scanHandWrittenMethods` in the processor skips operations that already have a hand-written `@GraphQLApi` resolver for the same domain — both approaches coexist cleanly.

### SPI Interface

```java
@McpDomain("connectors")
public interface ConnectorOperations {

    @PlatformMutation("Inject a chat message as if a customer sent it")
    InjectChatResult injectChat(String platform, String sender,
                                String channel, String text);

    @PlatformMutation("Send a notification via a named connector")
    SendNotificationResult sendNotification(String connectorId, String destination,
                                            String body, String title,
                                            Map<String, String> attributes);

    @PlatformQuery("List registered connectors, chat platforms, and their capabilities")
    ConnectorStatusResult connectorStatus();

    @PlatformQuery("Retrieve recently sent messages for verification")
    List<SentMessageEntry> sentMessages(String connectorId, Integer limit);
}
```

### Service Implementation

```java
@ApplicationScoped
public class ConnectorOperationsImpl implements ConnectorOperations { ... }
```

Injected dependencies:
- `InboundConnectorService` — for `injectChat`
- `ConnectorService` — for `sendNotification`
- `ChatPlatformService` — for `connectorStatus` and `injectChat` validation
- `@All List<Connector>` — for `connectorStatus` (need instances to call `channelType()`)
- `@All List<WebhookInboundConnector>` — for `connectorStatus` (webhook-based inbound IDs)
- `Instance<SentMessageCapture>` — for `sentMessages` (optional — absent in production)
- `CurrentPrincipal` — for tenancy context

## Operations

### 1. `injectChat` (Mutation)

Simulates a customer sending a chat message. Constructs an `InboundMessage` and fires it via `InboundConnectorService.receive()`.

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `platform` | String | yes | Chat platform id: "slack", "discord", "irc", "ref" |
| `sender` | String | yes | External sender identifier |
| `channel` | String | yes | External channel reference |
| `text` | String | yes | Message content |

**InboundMessage construction:**

| Field | Value | Rationale |
|-------|-------|-----------|
| `connectorType` | `platform` param | Faithful simulation — observers route on this |
| `connectorId` | `"chat-inject"` (constant) | Distinguishes injected from real; new constant in `InboundConnectorIds` |
| `externalSenderId` | `sender` param | |
| `externalChannelRef` | `channel` param | |
| `content` | `text` param | |
| `attachments` | `List.of()` | Injection is text-only |
| `receivedAt` | `Instant.now()` | |
| `metadata` | `Map.of("source", "mcp-inject")` | Provenance in metadata, not in routing fields |
| `tenancyId` | `CurrentPrincipal.tenancyId()` | Multi-tenant context; null in single-tenant |

**Validation:** Confirms `platform` matches a registered `ChatPlatform` via `ChatPlatformService`. Fails with `IllegalArgumentException` if not found — prevents silent drops in scenario automation.

**Note on `connectorType` semantics:** The `platform` value becomes the `connectorType` on the `InboundMessage`. For real platforms (slack, discord, irc), this matches `InboundConnectorTypes` constants and produces valid CloudEvents. The `ref` (in-memory reference) platform is valid for testing the injection mechanism but has no corresponding `InboundConnectorTypes` constant — observers keying on known types will not process `ref` messages.

**Return type:** `InjectChatResult(boolean ok, String connectorType, String channel)`

### 2. `sendNotification` (Mutation)

Sends an outbound message via a named connector. Thin surface over `ConnectorService.send()`.

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `connectorId` | String | yes | Connector to use: "slack", "teams", "twilio-sms", "whatsapp", "email" |
| `destination` | String | yes | Delivery target: webhook URL, channel ID, E.164 number, email address |
| `body` | String | yes | Message body |
| `title` | String | no | Title or subject line (email subject, Slack/Teams card title) |
| `attributes` | Map | no | Connector-specific data (e.g., `format=html` for email) |

**Implementation:** Constructs a `ConnectorMessage` from the parameters, calls `ConnectorService.send(connectorId, message)`.

**Return type:** `SendNotificationResult(boolean ok, String connectorId, String destination)`

### 3. `connectorStatus` (Query)

Aggregates the connector ecosystem status into a single structured response.

**Parameters:** None.

**Return type:**

```java
public record ConnectorStatusResult(
    List<OutboundConnectorInfo> outbound,
    List<ChatPlatformInfo> chatPlatforms,
    List<InboundConnectorInfo> inboundConnectors
) {}

public record OutboundConnectorInfo(
    String id,
    String channelType
) {}

public record ChatPlatformInfo(
    String id,
    List<String> capabilities
) {}

public record InboundConnectorInfo(
    String id,
    String transport
) {}
```

**Implementation:**
- `outbound` — from `@All List<Connector>`, mapping each to `OutboundConnectorInfo(id(), channelType())`
- `chatPlatforms` — from `ChatPlatformService`, with capability detection via `ChatPlatform.supports()` for each capability interface (Messaging, Threading, Discovery, Reactions, Presence, Members, ChannelManagement, MemberManagement, MessageHistory)
- `inboundConnectors` — aggregates both sources:
  - Pull connectors: `InboundConnectorService.pullIds()` → `InboundConnectorInfo(id, "pull")`
  - Webhook connectors: `@All List<WebhookInboundConnector>` → `InboundConnectorInfo(id(), "webhook")`
  - This ensures all inbound connectors are reported (pull-based like email-inbound AND webhook-based like slack-inbound, discord-inbound, etc.)

### 4. `sentMessages` (Query)

Returns messages recorded by a CDI event observer for verification in demo/test scenarios.

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `connectorId` | String | no | Filter by connector; null returns all |
| `limit` | Integer | no | Max results, default 50 |

**Return type:** `List<SentMessageEntry>` where:

```java
public record SentMessageEntry(
    String connectorId,
    String destination,
    String body,
    Instant sentAt
) {}
```

## Changes to `casehub-connectors-core`

### New: `SentMessage` record

```java
public record SentMessage(
    String connectorId,
    String destination,
    String title,
    String body,
    Instant sentAt,
    boolean success
) {}
```

### Modified: `ConnectorService`

Add `@Inject Event<SentMessage> sentMessageEvent` and fire it after each `connector.send()` call:

```java
public boolean send(String connectorId, ConnectorMessage message) {
    Connector connector = registry.get(connectorId);
    // ... existing null check ...
    boolean success = connector.send(message);
    sentMessageEvent.fireAsync(new SentMessage(
        connectorId, message.destination(), message.title(),
        message.body(), Instant.now(), success))
        .exceptionally(ex -> {
            LOG.log(Level.SEVERE, "Async SentMessage dispatch failed", ex);
            return null;
        });
    return success;
}
```

The event fires on both success and failure — the `success` field distinguishes them. Observers decide whether to record failures. The `exceptionally()` handler follows the `InboundConnectorService` pattern to prevent silent event dispatch failures.

**Relationship to `ConnectorMeshBridge`:** `SentMessage` events and `ConnectorMeshBridge.notifyDelivered()` are complementary. `SentMessage` fires from `ConnectorService.send()` on every outbound send — it is a core CDI event for recording/verification. `ConnectorMeshBridge` is called by MCP `@Tool` methods after delivery — it is a caller-level notification to the mesh. They serve different purposes and do not replace each other.

### New: `InboundConnectorIds.CHAT_INJECT`

```java
public static final String CHAT_INJECT = "chat-inject";
```

## `SentMessageCapture` — Profile-gated Observer

Lives in the `graphql` module, not in core. Activated only in dev/test profiles.

```java
@ApplicationScoped
@UnlessBuildProfile("prod")
public class SentMessageCapture {

    private final Deque<SentMessageEntry> buffer = new ConcurrentLinkedDeque<>();
    private static final int MAX_SIZE = 500;

    void onSent(@ObservesAsync SentMessage event) {
        buffer.addFirst(new SentMessageEntry(
            event.connectorId(), event.destination(),
            event.body(), event.sentAt()));
        while (buffer.size() > MAX_SIZE) buffer.removeLast();
    }

    public List<SentMessageEntry> query(String connectorId, int limit) {
        return buffer.stream()
            .filter(e -> connectorId == null || connectorId.equals(e.connectorId()))
            .limit(limit)
            .toList();
    }
}
```

When `SentMessageCapture` is not active (production), the `sentMessages` query returns an empty list — the `ConnectorOperationsImpl` checks for the bean's availability via `Instance<SentMessageCapture>`.

**Verification scope:** `sentMessages` captures outbound sends only (`Event<SentMessage>` from `ConnectorService.send()`). It does not capture inbound injections (`injectChat` fires `Event<InboundMessage>`, a different event type). Verifying that `injectChat` was processed is an app-layer concern — the observer that handles `InboundMessage` events determines the outcome.

## ModelEnricher

```java
@McpDomain("connectors")
@ApplicationScoped
public class ConnectorsModelEnricher implements ModelEnricher {

    @Inject ConnectorService connectorService;
    @Inject ChatPlatformService chatPlatformService;
    @Inject InboundConnectorService inboundConnectorService;

    @Override
    public String summary() {
        return "Connector infrastructure — inject inbound chat messages, "
             + "send outbound notifications, query connector status "
             + "and sent message history.";
    }

    @Override
    public Map<String, Object> state() {
        return Map.of(
            "outboundConnectors", connectorService.ids().size(),
            "chatPlatforms", chatPlatformService.ids().size(),
            "inboundConnectors", inboundConnectorService.pullIds().size()
        );
    }
}
```

## Testing Strategy

### Unit tests (in `graphql` module)
- `ConnectorOperationsImplTest` — tests each operation with mocked services
  - `injectChat`: verifies InboundMessage construction, field mapping, platform validation failure
  - `sendNotification`: verifies ConnectorMessage construction, delegation to ConnectorService
  - `connectorStatus`: verifies aggregation from all three sources
  - `sentMessages`: verifies query filtering and limit

### Integration tests
- `SentMessageCaptureTest` — verifies CDI event capture with `@QuarkusTest`
- `ConnectorService` event firing — verifies `Event<SentMessage>` is fired on send

### Edge cases
- `injectChat` with unknown platform → `IllegalArgumentException`
- `sendNotification` with unknown connector → `IllegalArgumentException` (from `ConnectorService`)
- `sentMessages` when capture is not active (no dev profile) → empty list
- `connectorStatus` with no connectors registered → empty lists, not null

## File Layout

```
graphql/
  pom.xml
  src/main/java/io/casehub/connectors/graphql/
    ConnectorOperations.java          — @McpDomain interface
    ConnectorOperationsImpl.java      — service implementation
    ConnectorsModelEnricher.java      — ModelEnricher
    SentMessageCapture.java           — profile-gated observer
    dto/
      InjectChatResult.java
      SendNotificationResult.java
      ConnectorStatusResult.java
      OutboundConnectorInfo.java
      ChatPlatformInfo.java
      InboundConnectorInfo.java
      SentMessageEntry.java
  src/test/java/io/casehub/connectors/graphql/
    ConnectorOperationsImplTest.java
    SentMessageCaptureTest.java
```

Changes in `core/`:
- `SentMessage.java` — new record
- `ConnectorService.java` — add `Event<SentMessage>` firing
- `InboundConnectorIds.java` — add `CHAT_INJECT` constant

## Protocols Checked

| Protocol | Status |
|----------|--------|
| `mcp-tool-blocking-annotation` | N/A — `@McpDomain` resolvers are not `@Tool` methods; GraphQL execution handles threading |
| `spi-id-method-naming` | Compliant — `ConnectorOperations` is not an SPI with an `id()` method |
| `shared-http-client` | N/A — no outbound HTTP calls from the graphql module; delegation to existing services |
| `credential-config-ownership` | N/A — no credential config in this module |
| `inbound-connector-id-constants` | Compliant — `CHAT_INJECT` added to `InboundConnectorIds` |

## References

- `io.casehub.platform.api.mcp.McpDomain` — platform-api annotation
- `io.casehub.platform.mcp.GraphQLModelScanner` — auto-discovery of `@McpDomain` resolvers
- `io.casehub.platform.mcp.ReflectiveOperationDispatcher` — runtime dispatch
- `io.casehub.platform.graphql.generator.GraphQLResolverProcessor` — compile-time resolver generation
- `io.casehub.platform.api.mcp.PlatformQuery` / `PlatformMutation` — SPI method annotations for generation
- `io.casehub.connectors.InboundConnectorService` — CDI event bus for inbound messages
- `io.casehub.connectors.ConnectorService` — outbound message routing
- `io.casehub.connectors.chat.ChatPlatformService` — chat platform registry
- `io.casehub.connectors.InboundConnectorIds` — inbound connector ID constants
- `casehub-engine-graphql` — reference implementation of the `-graphql` module pattern
- `casehub-work-graphql` — second reference implementation
- casehubio/connectors#96 — issue
