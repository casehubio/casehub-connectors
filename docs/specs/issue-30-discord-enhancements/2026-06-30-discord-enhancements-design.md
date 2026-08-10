# Discord Enhancements — Attachment Downloading, Rich Embeds, MCP Tools

**Branch:** issue-30-discord-enhancements
**Issues:** #30 (attachments), #33 (embeds), #34 (MCP tools)
**Deferred:** #36 (history attachment download), #37 (generic rich content), #38 (advanced embed MCP params)

---

## Overview

Three extensions to the Discord infrastructure in `discord` and `mcp` modules:
1. Parse and download attachments from Gateway MESSAGE_CREATE events
2. Add rich embed support to `DiscordClient` outbound methods
3. Add `send_discord` and `list_discord_channels` MCP tools

No new modules. No new SPIs. No changes to chat-spi or core models.

---

## 1. Attachment Downloading (#30)

### New model

`DiscordAttachment` record in `discord/model/` — metadata only:

```java
public record DiscordAttachment(String id, String filename,
        String contentType, long size, String url) {}
```

`size` is `long` for consistency with Java byte-count conventions (`Content-Length` headers, `File.length()`, stream APIs all use `long`). Avoids narrowing casts when comparing against `Content-Length` values.

### DiscordMessage extension

Add `List<DiscordAttachment> attachments` as the 8th field on `DiscordMessage`.

### DiscordClient changes

- Update `parseMessage()` to parse the `attachments[]` JSON array.
- Extract attachment parsing to a **public** `parseAttachments(JsonNode)` method. `DiscordClient` is in `io.casehub.connectors.discord`; `DiscordInboundConnector` is in `io.casehub.connectors.chat.discord` — different packages, so package-private is inaccessible. `chat-discord` already has a compile-scope dependency on `discord`; the test-jar dependency is test-scope only and must not be used for production code.
- Add `downloadAttachment(DiscordAttachment attachment)` — HTTP GET via `HttpHelper.CLIENT`, returns `Attachment` (core record) or `null` on failure:
  - **URL validation (SSRF defense):** Before downloading, validate that the URL host matches `cdn.discordapp.com` or `media.discordapp.net`. Reject any other host with a WARNING log. Defense in depth — even though Gateway payloads come from Discord's authenticated WebSocket, URL validation is cheap and protects against compromised payloads or future integration changes.
  - **Size limit:** Configurable via `casehub.discord.attachment.max-bytes` (default 8388608 = 8MB, Discord free tier limit).
  - **Content-Length pre-check:** If the `Content-Length` header is present and exceeds the limit, abort immediately with WARNING (fast-fail optimization).
  - **Streaming byte-count enforcement:** Download using `BodyHandlers.ofInputStream()`, read in chunks, and count bytes. Abort and close the stream when the count exceeds `casehub.discord.attachment.max-bytes`. This is the primary limit enforcement; the Content-Length check is an optimization. `BodyHandlers.ofByteArray()` must not be used — it materializes the entire response body in memory before returning, so a 500MB attachment from a Nitro user would be fully downloaded into heap before any size check runs.
  - **CDN URL expiry:** Discord CDN URLs include signed authentication parameters (`ex`, `is`, `hm`) that expire. Downloads occur promptly during Gateway event processing (on the virtual thread offloaded in step 2 above), so expiry is not expected. If the CDN returns 403, treat as a non-retryable download failure — log WARNING and return `null`. Do not retry on 403 (the signed URL has expired and cannot be refreshed from the Gateway event).
- Add `guildId()` accessor — returns the configured guild ID. Used by `DiscordMcpTool` to validate configuration before calling `listGuildChannels()`.

### DiscordInboundConnector changes

In `handleMessageCreate()`:
1. Parse `data.get("attachments")` to `List<DiscordAttachment>` using `DiscordClient.parseAttachments()`.
2. If attachments are present, offload the rest of processing to a virtual thread (`Thread.ofVirtual().start(...)`) — `GatewayEventListener.onEvent()` runs on the Vert.x WebSocket listener thread and must not block. Attachment downloads via `HttpHelper.CLIENT.send()` are blocking calls; executing them on the event loop would stall heartbeat processing and cause gateway disconnection.
3. On the virtual thread: for each attachment, call `client.downloadAttachment()`.
4. Collect successful downloads into `List<Attachment>`.
5. Failed downloads log WARNING and are skipped — partial results preferred over message loss.
6. Add metadata: `discord-attachment-count` (total in Gateway event) and `discord-attachment-download-failures` (count of failures). This makes download failures distinguishable from messages with no attachments — consistent with the `attachment-count` metadata pattern in `EmailInboundConnector`.
7. Pass the attachment list to `InboundMessage` constructor (replacing `List.of()`).
8. Call `sink.receive(msg)`.

When no attachments are present, processing remains on the event loop (current path, non-blocking).

### Scope boundary

`DiscordChatPlatform.getMessageHistory()` continues to return `ChatContent(text, null, List.of())`. `DiscordMessage` carries attachment metadata but no download occurs on the history path. See #36 for future work.

---

## 2. Rich Embed Support (#33)

### New model

`DiscordEmbed` record in `discord/model/`:

```java
public record DiscordEmbed(
        String title, String description, String url, Integer color,
        List<Field> fields, String thumbnailUrl, String imageUrl,
        Footer footer, Author author) {

    public record Field(String name, String value, boolean inline) {}
    public record Footer(String text) {}
    public record Author(String name) {}

    public DiscordEmbed {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
```

### DiscordClient changes

Add overloads that accept embeds:
- `sendMessage(token, channelId, content, List<DiscordEmbed> embeds)` — original 3-arg delegates with `List.of()`.
- `sendReply(token, channelId, content, replyToMessageId, List<DiscordEmbed> embeds)` — original 4-arg delegates with `List.of()`.

Extract a private `buildMessageBody(String content, List<DiscordEmbed> embeds)` helper — uses the instance `mapper` field directly, consistent with every other method in the class. When `content` is null or empty and embeds are provided, sends an embed-only message.

Embed serialization is manual via `ObjectNode` — consistent with every other method in the class.

### Scope boundary

Embeds are not surfaced through `ChatContent` or chat-spi. They are a Discord-specific outbound concern. See #37 for future platform-agnostic rich content.

---

## 3. MCP Tools (#34)

### New class

`DiscordMcpTool` in `mcp/` — single class with both tools:

```java
@ApplicationScoped
public class DiscordMcpTool {

    @Inject
    public DiscordMcpTool(
            DiscordClient client,
            ConnectorMeshBridge meshBridge,
            @ConfigProperty(name = "casehub.connectors.discord.token",
                            defaultValue = "") String token) { ... }

    @Tool(name = "send_discord",
          description = "Posts a message to a Discord channel. ...")
    @Blocking
    public String sendDiscord(channel, text, replyToMessageId, embedTitle, embedDescription, embedColor) { ... }

    @Tool(name = "list_discord_channels",
          description = "Lists text channels in the configured Discord guild. ...")
    @Blocking
    public String listDiscordChannels() { ... }
}
```

### send_discord

- Required: `channel` (snowflake ID).
- Optional: `text` (message content), `replyToMessageId` (Discord message ID to reply to — use the `discord-message-id` from inbound message metadata), `embedTitle`, `embedDescription`, `embedColor` (decimal integer RGB color, e.g. 16711680 for red `#FF0000`, 65280 for green `#00FF00`).
- Validation: at least one of `text` or embed args must be present — returns `"Failed: text or embed required"` if all are blank/absent. This allows embed-only messages (a valid Discord message type) without requiring text.
- When `replyToMessageId` is present, calls `sendReply()` instead of `sendMessage()` — mirrors the `threadTs` parameter on `send_slack_bot`. Completes the inbound-to-outbound reply loop: inbound metadata carries `discord-message-id`, the MCP tool accepts it back as `replyToMessageId`.
- When any embed arg is present, constructs a single `DiscordEmbed` and calls the embeds-aware overload.
- On success: calls `meshBridge.notifyDelivered(DiscordDiscovery.ID, channel, sanitizedContent)` where `sanitizedContent` is `McpContentSanitizer.sanitize(text)` when text is present, falling back to `sanitize(embedTitle)` then `sanitize(embedDescription)` for embed-only messages. Returns `"Posted to <channel> (id=<messageId>)"`.
- On failure: returns `"Failed: <reason>"`.
- Blank token: returns `"Failed: casehub.connectors.discord.token is not configured"` without making HTTP calls.

### list_discord_channels

- Checks both token and `client.guildId()` — returns `"Failed: casehub.connectors.discord.token is not configured"` or `"Failed: casehub.discord.guild-id is not configured"` respectively. Without guild-id validation, a deployment that sets only the MCP token gets silent empty results with no indication of misconfiguration (the URL becomes `/guilds//channels` which returns an HTTP error caught as `List.of()`).
- Calls `client.listGuildChannels(token)`, filters to text channel types (0, 5, 10, 11, 12) matching `DiscordDiscovery`.
- Returns richer output than generic `list_channels`: includes topic and type per channel.
- Tool description clarifies the relationship with `list_channels`: *"For Discord-specific detail (topic, type) use this tool. For a cross-platform channel overview across all connectors, use list_channels."* `DiscordDiscovery` continues to implement `ConnectorDiscovery` so Discord channels appear in `list_channels` — removing them would break the contract that `list_channels` aggregates ALL discoverable channels.

### Module dependency

Add `casehub-connectors-discord` to `mcp/pom.xml`.

### Credential ownership

`casehub.connectors.discord.token` is the MCP tool's own config property, distinct from `casehub.discord.token` used by `DiscordInboundConnector` and `DiscordDiscovery`. Follows the credential-config-ownership protocol.

---

## Breaking Changes

Adding `List<DiscordAttachment> attachments` as the 8th field on `DiscordMessage` breaks all existing constructor calls. Call sites requiring update: `DiscordClient.parseMessage()` (new field), test constructors. `DiscordChatPlatform.toReceivedMessage()` uses accessor methods, not construction — unaffected.

## ARC42STORIES.MD Updates

- §4 Layer Taxonomy, L4 MCP Surface: update tool count (7 → 9) and add `send_discord`, `list_discord_channels`
- §5 Module Structure: add `discord` to `mcp` dependency list
- §12 Risks: add risk entry for attachment download blocking event loop (mitigated by virtual thread offloading) and CDN URL expiry (mitigated by prompt download + 403 fail-fast)
- New chapter entry or layer delta for Discord enhancements

## Protocol Compliance

| Protocol | Status |
|----------|--------|
| shared-http-client | All HTTP calls via `HttpHelper.CLIENT` |
| mcp-tool-blocking-annotation | `@Blocking` on all `@Tool` methods |
| credential-config-ownership | MCP tool owns its token config; passes at call time |
| paginating-client-fail-soft | N/A — no new paginating methods |
| spi-id-method-naming | No new SPIs |
| inbound-connector-id-constants | Using existing `DISCORD_INBOUND` |

## Platform Coherence

- Capability Ownership: `casehub-connectors-mcp` row in PLATFORM.md needs `send_discord`, `list_discord_channels` added.
- No new cross-repo dependencies.
- No boundary rule violations.
- `DiscordClient` remains a pure delivery client — no business logic.

## Testing

All features tested with WireMock (existing pattern):
- `DiscordClientTest` — attachment parsing, download success/failure/size-limit, embed serialization, embed+content, embed-only, SSRF URL validation (non-Discord host rejected with WARNING, valid `cdn.discordapp.com` host proceeds), streaming byte-count abort on oversized chunked response, CDN 403 treated as non-retryable failure
- `DiscordInboundConnectorTest` — MESSAGE_CREATE with attachments, partial download failure, no attachments, virtual-thread offloading (MESSAGE_CREATE with attachments returns to calling thread before downloads complete — validates the event-loop-safety fix), attachment metadata (`discord-attachment-count`, `discord-attachment-download-failures`)
- `DiscordMcpToolTest` — send success/failure, blank token, embed parameters, embed-only (no text), replyToMessageId routing to `sendReply()`, list channels, blank guild-id, ConnectorMeshBridge integration
