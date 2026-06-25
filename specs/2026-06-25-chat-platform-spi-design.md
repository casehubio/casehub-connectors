# Chat Platform SPI — Design Spec

**Date:** 2026-06-25
**Modules:** `chat-spi`, `chat-slack`, `chat-discord`, `chat-irc`, `chat-ref`
**Artifacts:** `casehub-connectors-chat-spi`, `casehub-connectors-chat-slack`, etc.

---

## Problem

The connectors repo provides transport-level message delivery (`Connector.send()`) and reception (`InboundConnector`, `WebhookInboundConnector`). These SPIs have no concept of channels, threads, reactions, presence, or members — the structural elements that chat platforms share.

Each platform connector handles these concepts independently. `SlackBotClient` bypasses the `Connector` SPI because `send()` is void and can't return a thread timestamp. `SlackChannelBackend` in Qhorus extracts platform-specific metadata strings (`slack-ts`, `slack-thread-ts`) from `InboundMessage.metadata()` and manages a raw `String`-keyed thread cache. There is no common model for callers to write against when interacting with chat-like systems.

## Goal

A common Chat Platform SPI that models the shared structure of chat systems (Slack, Discord, IRC, and others), with graceful degradation for platforms that don't support all features. Plus a pure Quarkus reference implementation for SPI contract testing with zero external infrastructure.

## Who Consumes ChatPlatform

### Primary Consumer: ChannelBackend Implementations

The real consumer is `ChannelBackend` implementations in Qhorus. Today `SlackChannelBackend` implements `HumanParticipatingChannelBackend` with direct `SlackBotClient` calls, Slack-specific thread caching (`slack-ts`, `slack-thread-ts`), and binding stores.

Without `ChatPlatform`, a `DiscordChannelBackend` would reimplement the same patterns against Discord's API: HTTP client, thread management, error handling. An `IrcChannelBackend` would do the same against IRC protocol.

**Before (without ChatPlatform) — each backend reimplements transport:**
```java
// DiscordChannelBackend — direct Discord API
@Override public void post(ChannelRef channel, OutboundMessage message) {
    DiscordBinding binding = bindingCache.get(channel.id());
    String token = resolveToken(binding.guildId);
    DiscordClient.PostResult result = discordClient.createMessage(
        token, binding.discordChannelId, message.content());
    // ... thread caching, error handling, terminal eviction — all reimplemented
}
```

**After (with ChatPlatform) — backend delegates transport, keeps domain logic:**
```java
// DiscordChannelBackend — delegates to ChatPlatform
@Override public void post(ChannelRef channel, OutboundMessage message) {
    ChatPlatform discord = chatPlatformService.platform("discord");
    ChatChannelRef chatChannel = resolveChannel(channel);
    ChatMessageRef lastRef = threadCache.getLatestRef(channel.id());
    SendResult result;
    if (lastRef != null) {
        result = discord.threading().reply(lastRef, new ChatContent(message.content()));
    } else {
        result = discord.messaging().send(chatChannel, new ChatContent(message.content()));
    }
    if (!result.ok()) { LOG.warnf(...); return; }
    // domain logic (correlation, caching, eviction) stays in the backend
}
```

The transport layer (HTTP calls, protocol handling, authentication) moves to `ChatPlatform`. The domain logic (correlation IDs, thread caching, terminal-type eviction) stays in the `ChannelBackend`.

### Future Migration: Existing Slack Code

`SlackChannelBackend` and `SlackBotMcpTool` should migrate to `ChatPlatform` once the SPI is proven with new platforms. The Slack-specific metadata handling in `SlackChannelBackend` (`slack-ts`, `slack-thread-ts` extraction, raw-String thread cache) is exactly what `ChatPlatform` abstracts — typed `ChatMessageRef` replaces raw `String threadTs`, `ReceivedMessage.parentRef()` replaces `msg.metadata().get("slack-thread-ts")`. Migration is not immediate but is the intended trajectory.

## Prior Art Considered

- **Unified.to Messaging API** — `parent_id` threading model, multi-format content (`message`, `message_markdown`, `message_html`), channel/message/member objects.
- **Merge.dev Chat Unified API** — Five core objects (Messages, Conversations, Users, Groups, Members). Threading via `root_message_id`.
- **Vercel Chat SDK** — TypeScript adapter pattern. Thread as central object with per-thread state.

These informed the model but are not constraints.

## Design: Composed Capabilities with Auto-Degrading Builder (Approach D)

### Why Not A, B, or C

| Approach | Problem |
|----------|---------|
| A — Single interface, Optional returns | Every method exists on every platform; interface grows monotonically; callers see methods that don't work |
| B — Capability interfaces + instanceof | Caller branches on capabilities — the platform's job pushed to the call site; CDI can't express intersection types naturally |
| C — Single interface, silent degradation | Dishonest API — methods exist but silently no-op; caller can't distinguish success from silent drop; same growth problem as A |

Root cause: A, B, and C define capabilities at the interface level and handle variation at the method level.

### Approach D

`ChatPlatform` is a composition of focused capability interfaces, assembled by a builder (or direct CDI implementation) that provides degradation defaults for anything the platform doesn't natively support.

**Principles:**
1. Each capability is a focused, independent contract
2. Platforms compose the capabilities they support
3. Degradation is explicit, named, reusable, and testable
4. Callers never *must* branch on capabilities — but *can* via `supports()`
5. Adding a new capability does not change any existing interface or type
6. A platform's capability profile is visible in one place

---

## Model Types

```java
public record ChatChannelRef(String id) {}
public record ChatMessageRef(ChatChannelRef channel, String messageId) {}
public record MemberRef(String id) {}

public record ChatContent(
    String text,                    // plain text (always present)
    String markdown,                // optional rich format
    List<Attachment> attachments    // reuses existing core Attachment
) {
    public ChatContent {
        Objects.requireNonNull(text, "text");
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
    public ChatContent(String text) { this(text, null, List.of()); }
}

public record SendResult(boolean ok, ChatMessageRef messageRef, Instant timestamp, String error) {
    public static SendResult success(ChatMessageRef ref, Instant ts) {
        Objects.requireNonNull(ref, "messageRef");
        return new SendResult(true, ref, ts, null);
    }
    public static SendResult failure(String error) {
        return new SendResult(false, null, null, error);
    }
}

public record Channel(ChatChannelRef ref, String name, String topic, boolean isPrivate) {}
public record Member(MemberRef ref, String displayName) {}
public enum PresenceStatus { ONLINE, OFFLINE, AWAY, DND, UNKNOWN }
```

`Attachment` is reused from `core`.

### Why ChatChannelRef, Not ChannelRef

The primary consumer — `ChannelBackend` implementations — imports both `io.casehub.qhorus.api.gateway.ChannelRef(UUID id, String name)` (Qhorus-internal channel) and the chat model's channel reference (external platform channel). These are different concepts with different types (`UUID` vs `String`). Naming the chat type `ChatChannelRef` eliminates import ambiguity in every class that works with both.

### ChatMessageRef on Platforms Without Message Identity

`ChatMessageRef(ChatChannelRef channel, String messageId)` models Slack's (channel, ts) naturally. On other platforms:

- **Discord:** Globally unique snowflake IDs. `ChatChannelRef` inside `ChatMessageRef` is redundant but harmless — the adapter fills it in, Discord APIs can ignore it.
- **IRC:** No message identity. `Messaging.send()` returns `SendResult.success(new ChatMessageRef(channel, syntheticId), timestamp)` where `syntheticId` is a generated value. The `ChatMessageRef` is opaque to the caller — they pass it to `threading().reply()`, which (on IRC) ignores the `messageId` and degrades to a channel post.

The alternative — `Optional<ChatMessageRef>` or nullable — pushes branching to the caller. A present-but-opaque ref on IRC keeps degradation in the implementation, not the caller.

### Error Contract

`Messaging.send()` and `Threading.reply()` must not throw. On transport failure, they return `SendResult.failure(reason)`. Consistent with:
- `Connector.send()` — void, must not throw (ARC42STORIES L1)
- `SlackBotClient.PostResult` — explicit ok/error state
- `ChannelBackend.post()` — "must catch all exceptions internally — failure is non-fatal"

`Reactions.add()`/`remove()` are void and must not throw (fire-and-forget). `Presence.of()` returns `UNKNOWN` on failure. `Members.list()` and `Discovery.listChannels()` return empty list on failure.

---

## Capability Interfaces

Six focused interfaces, each with a single responsibility:

```java
public interface Messaging {
    SendResult send(ChatChannelRef channel, ChatContent content);
}

public interface Threading {
    SendResult reply(ChatMessageRef parent, ChatContent content);
}

public interface Discovery {
    List<Channel> listChannels();
}

public interface Reactions {
    void add(ChatMessageRef message, String emoji);
    void remove(ChatMessageRef message, String emoji);
}

public interface Presence {
    PresenceStatus of(MemberRef member);
}

public interface Members {
    List<Member> list(ChatChannelRef channel);
}
```

- **Messaging** is the universal baseline — every platform must provide it. Send only; discovery is separate.
- **Threading** takes a `ChatMessageRef` parent — the ref carries its channel.
- **Discovery** is separate from Messaging. Listing channels is discovery, not messaging. Platforms where channels aren't listable (IRC) degrade to empty list.
- **Reactions** is fire-and-forget (void) — same contract as `Connector.send()`.
- **Presence** returns the enum directly — `UNKNOWN` is the degradation value.
- **Members** returns a list — empty list is the degradation value.

### Relationship to ConnectorDiscovery

`Discovery.listChannels()` returns `List<Channel>` (rich: ref, name, topic, isPrivate). `ConnectorDiscovery.discover()` returns `List<DiscoveredTarget>` (flat: id, displayName). Different consumers, different fidelity. Each chat platform module that supports Discovery also provides a `ConnectorDiscovery` adapter bean so that `ChannelDiscoveryMcpTool` (`list_channels`) sees all platforms:

```java
@ApplicationScoped
public class DiscordConnectorDiscovery implements ConnectorDiscovery {
    @Inject DiscordChatPlatform platform;
    @Override public String id() { return platform.id(); }
    @Override public List<DiscoveredTarget> discover() {
        return platform.discovery().listChannels().stream()
            .map(ch -> new DiscoveredTarget(ch.ref().id(), ch.name()))
            .toList();
    }
}
```

This is a per-platform convention — 5 lines of explicit delegation. The MCP tool is unmodified.

---

## Inbound: InboundTranslator + ChatInboundAdapter

Chat is bidirectional. But inbound does not need a new lifecycle SPI — the existing `InboundConnector` and `WebhookInboundConnector` SPIs already handle inbound lifecycle. The chat layer adds a typed translation on top.

### Why Not a Receiving Capability on ChatPlatform

A `Receiving` capability with `start(ChatMessageSink)/stop()` would duplicate the existing `InboundConnector` lifecycle, create CDI ordering problems for adapter-based platforms (Slack — where inbound already flows through `WebhookInboundConnector`), and split the event bus (new platforms invisible to `ConnectorsCloudEventAdapter`).

Instead: all platforms fire `InboundMessage` on the existing CDI bus. The chat layer adds a typed `ReceivedMessage` as a derived event.

### Architecture

```
Platform → InboundConnector/WebhookInboundConnector → InboundMessage (existing bus)
  ├→ ConnectorsCloudEventAdapter (fires CloudEvent — sees ALL platforms)
  ├→ ChatInboundAdapter (translates → fires ReceivedMessage for chat-typed consumers)
  └→ Any other @ObservesAsync InboundMessage observer
```

Single event bus. No gaps. No new lifecycle management.

### InboundTranslator

Per-platform translation from `InboundMessage` to chat-typed `ReceivedMessage`:

```java
public interface InboundTranslator {
    String connectorType();
    ReceivedMessage translate(InboundMessage msg);
}
```

Each chat platform module provides an `InboundTranslator` bean. Example for Slack:

```java
@ApplicationScoped
public class SlackInboundTranslator implements InboundTranslator {
    @Override public String connectorType() { return InboundConnectorTypes.SLACK; }
    @Override public ReceivedMessage translate(InboundMessage msg) {
        ChatChannelRef channel = new ChatChannelRef(msg.externalChannelRef());
        String slackTs = msg.metadata().get("slack-ts");
        String threadTs = msg.metadata().get("slack-thread-ts");
        ChatMessageRef messageRef = new ChatMessageRef(channel, slackTs);
        ChatMessageRef parentRef = (threadTs != null && !threadTs.equals(slackTs))
            ? new ChatMessageRef(channel, threadTs) : null;
        return new ReceivedMessage("slack", channel, messageRef, parentRef,
            new MemberRef(msg.externalSenderId()),
            new ChatContent(msg.content(), null, msg.attachments()), msg.receivedAt());
    }
}
```

### ChatInboundAdapter

Generic adapter in `chat-spi` — one class, no per-platform logic:

```java
@ApplicationScoped
public class ChatInboundAdapter {
    private final Map<String, InboundTranslator> translators;
    private final Event<ReceivedMessage> receivedEvent;

    ChatInboundAdapter(@All List<InboundTranslator> translators,
                       Event<ReceivedMessage> receivedEvent) {
        this.translators = translators.stream()
            .collect(toMap(InboundTranslator::connectorType, identity()));
        this.receivedEvent = receivedEvent;
    }

    public void onMessage(@ObservesAsync InboundMessage msg) {
        InboundTranslator translator = translators.get(msg.connectorType());
        if (translator != null) {
            try {
                receivedEvent.fireAsync(translator.translate(msg));
            } catch (Exception e) {
                LOG.warnf("Translation failed for %s: %s", msg.connectorType(), e.getMessage());
            }
        }
    }
}
```

### ReceivedMessage

```java
public record ReceivedMessage(
    String platformId,           // matches ChatPlatform.id()
    ChatChannelRef channel,
    ChatMessageRef messageRef,   // may have synthetic ID on IRC
    ChatMessageRef parentRef,    // non-null if this is a thread reply
    MemberRef sender,
    ChatContent content,
    Instant receivedAt
) {}
```

Uses the same model types as outbound. `parentRef` enables bidirectional threading — an incoming thread reply carries context that consumers can correlate with the `ChatMessageRef` returned by `Threading.reply()`.

`ReceivedMessage` intentionally omits `tenancyId` and platform-specific `metadata` — the typed fields (`ChatMessageRef`, `ChatChannelRef`, `MemberRef`) replace the metadata values that matter for chat consumers. Consumers that need tenancy or raw platform metadata should observe `InboundMessage` instead — it remains on the CDI bus alongside `ReceivedMessage`.

### Per-Platform Inbound Strategy

| Platform | Inbound mechanism | InboundTranslator |
|----------|-------------------|-------------------|
| Quarkus ref | In-process `InboundConnector` (fires `InboundMessage`) | `RefInboundTranslator` in `chat-ref` |
| Slack | Existing `SlackInboundConnector` (untouched) | `SlackInboundTranslator` in `chat-slack` |
| Discord | New `DiscordInboundConnector implements InboundConnector` | `DiscordInboundTranslator` in `chat-discord` |
| IRC | New `IrcInboundConnector implements InboundConnector` | `IrcInboundTranslator` in `chat-irc` |

New platforms require `connectorType` constants in `InboundConnectorTypes` (core): add `DISCORD = "discord"` and `IRC = "irc"`.

New platforms (Discord, IRC) implement the existing `InboundConnector` SPI — proven lifecycle, managed by `InboundConnectorService`. Existing platforms (Slack) continue through their existing webhook connector. All platforms fire `InboundMessage`; `ChatInboundAdapter` derives `ReceivedMessage`.

---

## ChatPlatform Composition

```java
public interface ChatPlatform {
    String id();
    Messaging messaging();
    Threading threading();
    Discovery discovery();
    Reactions reactions();
    Presence presence();
    Members members();
    boolean supports(Class<?> capability);

    static Builder builder(String id) { return new Builder(id); }
}
```

`supports()` accepts the capability interface class — genuinely open for extension. Adding a new capability interface doesn't change any existing type.

### Construction: Builder or Direct CDI

Both patterns are valid:

**Builder pattern — declarative, visible capability profile:**
```java
ChatPlatform.builder("irc")
    .messaging(new IrcMessaging(client))
    .members(new IrcMembers(client))
    .build();
// threading, discovery, reactions, presence auto-degrade
// supports(Threading.class) returns false
```

The builder enforces one rule: `messaging()` is required. Everything else auto-degrades. `supports()` is computed from which capabilities were explicitly provided.

**Direct CDI implementation:**
```java
@ApplicationScoped
public class IrcChatPlatform implements ChatPlatform {
    @Override public String id() { return "irc"; }
    @Override public Messaging messaging() { return ircMessaging; }
    @Override public Threading threading() { return new ChannelFallbackThreading(ircMessaging); }
    @Override public Discovery discovery() { return new EmptyDiscovery(); }
    @Override public Reactions reactions() { return new NoOpReactions(); }
    @Override public Presence presence() { return new UnknownPresence(); }
    @Override public Members members() { return ircMembers; }
    @Override public boolean supports(Class<?> c) { ... }
}
```

Both produce the same result. The builder auto-computes `supports()` and auto-provides degradation; direct CDI requires explicit wiring but follows the pattern used everywhere else in the library.

### CDI Integration

`ChatPlatformService` mirrors `ConnectorService`:

```java
@ApplicationScoped
public class ChatPlatformService {
    private final Map<String, ChatPlatform> registry;

    ChatPlatformService(@All List<ChatPlatform> platforms) {
        // index by id, fail on duplicates
    }

    public ChatPlatform platform(String id) { ... }
    public boolean supports(String id) { ... }
    public Set<String> ids() { ... }
}
```

---

## Degradation Types

Reusable, named, testable implementations for unsupported capabilities:

```java
public class ChannelFallbackThreading implements Threading {
    private final Messaging messaging;
    public ChannelFallbackThreading(Messaging messaging) { this.messaging = messaging; }

    @Override
    public SendResult reply(ChatMessageRef parent, ChatContent content) {
        return messaging.send(parent.channel(), content);
    }
}

public class NoOpReactions implements Reactions {
    @Override public void add(ChatMessageRef message, String emoji) {}
    @Override public void remove(ChatMessageRef message, String emoji) {}
}

public class UnknownPresence implements Presence {
    @Override public PresenceStatus of(MemberRef member) { return PresenceStatus.UNKNOWN; }
}

public class EmptyMembers implements Members {
    @Override public List<Member> list(ChatChannelRef channel) { return List.of(); }
}

public class EmptyDiscovery implements Discovery {
    @Override public List<Channel> listChannels() { return List.of(); }
}
```

`ChannelFallbackThreading` is intentionally minimal — it delegates to `messaging.send()` without adding context. Whether to prepend a quote prefix is an implementation decision per platform's conventions.

---

## Relationship to Existing SPIs

### ChatPlatform vs Connector

Parallel SPIs, different purposes:
- `Connector` — fire-and-forget delivery to a destination string. Void return. For notifications (webhook, SMS, email).
- `ChatPlatform` — structured interaction with channels, threads, reactions. Result return. For conversations.

`ChatPlatform` does not implement `Connector`. Forcing one to implement the other creates a leaky abstraction.

### ID Namespace

`ChatPlatform.id()` and `Connector.id()` are separate namespaces in separate registries (`ChatPlatformService` vs `ConnectorService`). Both can use `"slack"` — no collision.

### Inbound Coexistence

All inbound flows through the existing `InboundMessage` CDI event bus. Existing `WebhookInboundConnector` implementations (Slack, Teams, WhatsApp, Twilio) are untouched. New chat platforms (Discord, IRC) implement `InboundConnector` — the existing pull-based SPI. `ChatInboundAdapter` observes `InboundMessage` and derives `ReceivedMessage` for chat-typed consumers. `ConnectorsCloudEventAdapter` sees all platforms with no blind spots.

---

## Platform Implementations

### v1 Platforms

| Platform | Messaging | Threading | Discovery | Reactions | Presence | Members | Inbound |
|----------|-----------|-----------|-----------|-----------|----------|---------|---------|
| Quarkus ref | Native (in-memory) | Native | Native | Native | Native | Native | `InboundConnector` (in-memory) |
| Slack | Native (SlackBotClient) | Native (thread_ts) | Native (conversations.list) | Native (reactions.add) | Native (users.getPresence) | Native (conversations.members) | Existing `SlackInboundConnector` (untouched) |
| Discord | Native (new DiscordClient) | Native (channel threads) | Native | Native | TBD — depends on gateway vs REST | Native | New `DiscordInboundConnector` |
| IRC | Native (PRIVMSG) | Degraded (channel fallback) | Degraded (empty) | Degraded (no-op) | Degraded (UNKNOWN) | Native (NAMES) | New `IrcInboundConnector` |

### Quarkus Reference Implementation

In-memory `ChatPlatform` implementation used as the SPI contract test target in `@QuarkusTest`. Supports all capabilities natively at full fidelity. Also provides an `InboundConnector` for contract-testing the `ReceivedMessage` path. No WebSocket UI, no Quinoa — demo server is a separate concern and separate issue.

### Slack

Adapts existing `SlackBotClient`. `SlackMessaging` and `SlackThreading` both delegate to `postMessage()` — messaging with `threadTs=null`, threading with `threadTs` set. New methods added to `SlackBotClient` for: `reactions.add`, `reactions.remove`, `users.getPresence`, `conversations.members`.

Inbound: existing `SlackInboundConnector` untouched. `SlackInboundTranslator` in `chat-slack` translates `InboundMessage` → `ReceivedMessage` using `slack-ts`/`slack-thread-ts` metadata. `ConnectorDiscovery` adapter bean bridges `Discovery` → `DiscoveredTarget` for MCP.

### Discord

New `DiscordClient` wrapping Discord REST API. Discord threads are explicit channel creation (different from Slack's ts model). New `DiscordInboundConnector implements InboundConnector` for gateway/webhook reception. `DiscordInboundTranslator` translates snowflake IDs. `ConnectorDiscovery` adapter bean for MCP.

### IRC

New `IrcClient` wrapping IRC protocol. Only messaging (PRIVMSG) and members (NAMES) are native; everything else auto-degrades. New `IrcInboundConnector implements InboundConnector` for TCP PRIVMSG reception. `IrcInboundTranslator` generates synthetic `ChatMessageRef` IDs. No `ConnectorDiscovery` needed (IRC channels are joined, not listed).

### Layer Build Order

Each capability implemented across all four platforms before the next:
1. Messaging (send)
2. Discovery (list channels)
3. Threading
4. Inbound (`InboundConnector` + `InboundTranslator`)
5. Reactions
6. Presence
7. Members

---

## Testing Strategy

### Tier 1 — SPI Contract Tests

Run against Quarkus reference implementation. `@QuarkusTest`. Every outbound capability exercised end-to-end. Inbound tested via ref impl's `InboundConnector` → `ChatInboundAdapter` → `ReceivedMessage`.

### Tier 2 — Adapter Translation Tests

Per platform, unit tests. Verify the mapping between model types (`ChatContent`, `SendResult`) and platform wire format. Also verify `InboundTranslator` — `InboundMessage` metadata → `ReceivedMessage` with typed refs. No HTTP calls.

### Tier 3 — Degradation Tests

Unit tests for `ChannelFallbackThreading`, `NoOpReactions`, `UnknownPresence`, `EmptyMembers`, `EmptyDiscovery`. Platform-independent, tested once.

### Integration Tests

- Quarkus ref impl — `@QuarkusTest`, in-process, automatic
- IRC — Ergo server started in test lifecycle, real protocol
- Slack/Discord — WireMock stubs for wire-protocol verification; not in default CI

---

## Module Structure

```
chat-spi/       — ChatPlatform, capability interfaces, model types, degradation impls,
                  ChatInboundAdapter, InboundTranslator, ReceivedMessage
                  depends on: core
chat-slack/     — SlackChatPlatform, SlackInboundTranslator, SlackConnectorDiscovery
                  depends on: chat-spi, slack-bot
chat-discord/   — DiscordChatPlatform, DiscordClient, DiscordInboundConnector,
                  DiscordInboundTranslator, DiscordConnectorDiscovery
                  depends on: chat-spi
chat-irc/       — IrcChatPlatform, IrcClient, IrcInboundConnector, IrcInboundTranslator
                  depends on: chat-spi
chat-ref/       — QuarkusChatPlatform, RefInboundConnector, RefInboundTranslator
                  depends on: chat-spi
```

Independent modules from day one. No transitive dependency leakage.

---

## Out of Scope for v1

- Message history / retrieval
- Message editing / deletion
- File upload (beyond `Attachment` in `ChatContent`)
- Typing indicators
- Read receipts
- MCP tool surface for chat (future module, mirrors `mcp/` pattern)
- Demo WebSocket UI (separate concern, separate issue)
