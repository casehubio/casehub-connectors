# Chat-Demo Interactive Features Design

**Issues:** #49 (threading), #50 (reactions), #51 (channel creation), #52 (user identity)
**Date:** 2026-07-02
**Status:** Approved

## Overview

Four interactive features for the chat-demo module that bring the demo from a
read-only message viewer to a usable multi-user chat application. All features
are demo-only (`-Pdemo -Pui` profile gate) and build on existing backend APIs
and WebSocket infrastructure.

---

## 1. User Identity (#52)

### Authentication — Platform Dev Auth (SmallRye JWT)

Identity uses real JWT authentication via a new platform module
`casehub-platform-dev-auth`. This is not chat-demo-specific — it serves
all platform apps that need lightweight dev/test-mode auth without
Keycloak.

**Provided by: `casehub-pages` server runtime (casehubio/casehub-pages#88)**

- Depends on `quarkus-smallrye-jwt` (already in the Quarkus BOM, ~tiny).
- Uses the auto-generated RSA keypair that SmallRye JWT provides in dev
  and test modes since Quarkus 3.22+ — no PEM files to manage.
- Exposes `POST /dev/auth/login` — accepts `{ "name": "alice" }`, returns
  a signed JWT with `sub` = the name, configurable roles, and a matching
  issuer claim.
- Configures `mp.jwt.verify.issuer` to match the self-issued tokens.
- Profile-gated: `%dev` and `%test` only. In prod, the endpoint does not
  exist and `casehub-platform-oidc` handles auth via real OIDC.
- CDI priority: between `MockCurrentPrincipal` (displaced) and
  `casehub-platform-oidc` (displaces this in prod).
- Login gate and identity widget UI components also provided by pages-ui.

**Client flow:**

1. `POST /dev/auth/login` with `{ "name": "alice" }` → receives JWT.
2. Store JWT in `sessionStorage`.
3. All subsequent requests send `Authorization: Bearer <token>`.
4. Quarkus validates the JWT signature automatically and populates
   `SecurityIdentity` with the principal name and roles.

### Login Gate (pages-ui component, casehubio/casehub-pages#88)

On first load with no JWT in `sessionStorage`, the pages-ui `<pages-dev-auth>`
component renders a centered overlay card. Chat-demo populates it with known
member names sourced by deduplicating the `members` dataset (delivered in the
WebSocket snapshot) by `memberId`.

- **Dropdown** of known identities.
- **Text field** for typing a name. If it matches an existing member,
  authenticate as them. If not, create the identity.

Both paths call `POST /dev/auth/login`, store the returned JWT in
`sessionStorage`, and dismiss the gate. On login, also call
`PUT /api/presence/{memberId}` (status `ONLINE`) with the new Bearer
token.

### Identity Widget (pages-ui component, casehubio/casehub-pages#88)

A clickable name display in the message input area (provided by pages-ui).
Click opens the same picker as the login gate (as a popover). Switching
identity:

1. Calls `POST /dev/auth/login` with the new name → new JWT.
2. Updates `sessionStorage` with the new JWT.
3. Sends presence `OFFLINE` for the old identity, `ONLINE` for the new one
   via REST (`PUT /api/presence/{memberId}`).
4. All subsequent messages use the new identity.

### Backend Changes

- **Identity from `SecurityIdentity`:** `ChatResource` injects Quarkus
  `SecurityIdentity` and reads `getPrincipal().getName()` to determine
  the sender. No custom headers — standard Bearer token auth.
- **Bypass SPI for send/reply:** `ChatResource.postMessage()` and
  `ChatResource.postReply()` call `ChatBackend.storeMessage()` directly,
  passing `new MemberRef(identity)` as the sender. They do NOT go through
  `ChatPlatform.messaging().send()` or `ChatPlatform.threading().reply()`.

  Rationale: the Messaging and Threading SPIs have no sender parameter —
  correctly, because real platforms (Slack, Discord, IRC) determine sender
  from credentials/tokens. Adding a sender parameter would be a dead
  parameter on every real implementation. The demo is not a client of an
  external platform; it IS the platform. Direct backend access is
  architecturally correct. `ChatBackend.storeMessage()` already accepts
  `MemberRef sender`.

  `ChatResource` injects both `ChatPlatform` (for reactions, presence,
  members, channels, discovery, history) and `ChatBackend` (for
  send/reply with caller-specified identity).
- **Auto-membership:** On message send, if the identity is not already a
  member of the target channel, `ChatResource` auto-adds them via
  `ChatPlatform.memberManagement().add()` before storing the message.
  The `displayName` is the principal name from the JWT.
  Auto-membership also broadcasts the new member via
  `broadcaster.broadcastMemberAppend()`.
- **Identity lifecycle sequence:**
  1. User picks or types name in login gate → `POST /dev/auth/login`
     returns a signed JWT.
  2. Client calls `PUT /api/presence/{memberId}` (status `ONLINE`)
     with the new Bearer token.
  3. `sessionStorage` stores the JWT.
  4. Every REST request sends `Authorization: Bearer <token>`.
  5. On message send: `ChatResource` reads identity from
     `SecurityIdentity`, validates the channel exists (404 if not),
     checks membership (auto-adds if missing), then calls
     `chatBackend.storeMessage()` with the identity as sender.
  6. If identity has no presence entry (e.g. stale session), create it
     on-the-fly before processing the request.

### WebSocket Authentication

The WebSocket connection at `/ws/chat` also needs the JWT. The client
passes the token as a query parameter: `/ws/chat?token=<jwt>`. The
WebSocket `@OnOpen` handler validates the token and associates the
connection with the identity. This is standard practice — WebSocket
APIs cannot send custom headers from the browser.

---

## 2. Threading — Inline Replies (#49)

Follows the message-level `parentId` model already in the SPI and data layer.
No channel-level thread mode — this matches how real platforms work (Slack,
Discord, Teams all treat threading as a message-level capability, not a channel
configuration).

### Reply Rendering (Discord-style)

When a message has `parentId` set, a reference bar appears above it:

- Styled with a light accent background.
- Content: `↩ replying to **SenderName**: *truncated parent text...*`
- Click scrolls to and briefly highlights the parent message.

Replies appear in chronological order in the main message flow. No grouping,
nesting, or collapsing. A reply to a reply shows a reference bar pointing to
its immediate parent.

### Reply Composition

- Hover over any message reveals a small action bar (shared with reactions).
- Click the reply icon: the message input area shows a "Replying to
  **SenderName**" banner with an X to cancel.
- Submit calls `POST /api/channels/{channelId}/messages/{messageId}/replies`
  (endpoint already exists).
- Banner clears after send.

### Reply Composition — Inter-Panel Protocol

Reply composition requires coordination between `message-list` (action bar)
and `message-input` (banner + submit). This uses the existing `pages-event`
custom event bus with a new topic.

**Entering reply mode:**

`message-list` emits a `pages-event` with topic `reply-to` when the reply
icon in the action bar is clicked:

```
{ topic: "reply-to", payload: { channelId, messageId, senderName } }
```

`message-input` listens for `reply-to` and sets two new state fields:
- `replyToMessageId: string | null`
- `replyToSenderName: string | null`

When reply state is set, `message-input` renders a banner above the input:
"Replying to **SenderName**" with an X button.

**Endpoint routing:**

When `replyToMessageId` is set, submit POSTs to:
`/api/channels/${channelId}/messages/${replyToMessageId}/replies`

When `replyToMessageId` is null, submit POSTs to the default:
`/api/channels/${channelId}/messages`

**After send:** `replyToMessageId` and `replyToSenderName` are cleared.
Banner is removed.

**Cancel:** Clicking the X button clears reply state locally. No event
emitted — `message-list` doesn't need to respond to cancellation (no
visual state on the parent message during reply composition).

**Channel switch:** If a `channel-selected` event arrives while reply
state is set, clear the reply state — the target message is no longer
visible.

---

## 3. Reactions (#50)

### Emoji Palette

- The hover action bar (shared with reply) includes a smiley face icon.
- Click opens a small popup with 6-8 common emojis: 👍 ❤️ 😂 🎉 👀 🔥
- Click an emoji to toggle it. Palette dismisses on selection or outside click.

### Display

Reactions appear as small pills below the message text. Each pill shows the
emoji. Clicking an existing pill toggles it off (`DELETE` endpoint).

### Backend Changes

- **Reaction removal broadcast:** Currently `append` is broadcast on add, but
  removal is not broadcast via WebSocket. Add `broadcastReactionRemove()`
  to `ChatWebSocketBroadcaster` and call it from
  `ChatResource.removeReaction()` after the SPI call succeeds.
- **Reactions snapshot:** Add a `reactions` dataset to
  `ChatWebSocketBroadcaster.buildSnapshot()`. The broadcaster already
  iterates all channels and messages for the snapshot — collect reactions
  per message in the same pass. `REACTION_COLUMNS` is already defined.

### Limitation

The data model is `(messageId, emoji)` — no per-user tracking. A reaction is
either present or absent on a message. No count or "who reacted" tooltip. This
is acceptable for a demo.

---

## 4. Channel Management (#51)

### Create

"+" button in the channel sidebar header. Click opens a modal with fields:

- Channel name (required)
- Topic (optional)
- Description (optional)
- Private toggle (default off)

Submit calls `POST /api/channels`. The WebSocket broadcasts the new channel via
the existing `append` event on the `channels` dataset. Modal dismisses and the
new channel auto-selects.

### Delete

Trash icon on hover for each channel in the sidebar. Click shows a confirmation:
"Delete #channel-name?" with Cancel / Delete buttons.

### Backend Changes

- **SPI addition:** Add `void delete(String channelId)` to
  `ChannelManagement`. Channel deletion is a legitimate management
  operation — every real platform supports it.
- **ChatBackend addition:** Add `void deleteChannel(String channelId)` to
  `ChatBackend`. Implements manual cascade delete in a single transaction:
  delete reactions for the channel's messages, delete messages, delete
  members, delete channel. Both `InMemoryChatBackend` and
  `SqliteChatBackend` implement this.
- **New endpoint:** `DELETE /api/channels/{channelId}` — calls
  `chatPlatform.channelManagement().delete(channelId)`.
- **WebSocket broadcast:** Add `broadcastChannelRemove()` to
  `ChatWebSocketBroadcaster`. `ChatResource` calls it after successful
  deletion. Sends a `remove` event on the `channels` dataset with the
  channel ID as the key.
- **Active channel guard (all clients):** When any connected client
  receives a `remove` event on the `channels` dataset via WebSocket:
  1. `channel-sidebar` removes the channel from its list and re-renders.
  2. If the removed channel was selected, `channel-sidebar` auto-selects
     the next available channel and emits `channel-selected`.
  3. `message-list` already listens for `channel-selected` and re-renders
     for the new channel. No additional coordination needed.
  4. If no channels remain, `message-list` shows its empty state.

  This is standard event-driven Web Component coordination using the
  existing `channel-selected` event pattern.

---

## Shared UI Pattern: Message Action Bar

Both reply and reaction triggers share a single hover action bar on messages.
This bar appears on mouse hover over a message and contains:

- Reply icon (↩) — enters reply composition mode
- Smiley icon (😊) — opens emoji palette

The bar is positioned at the top-right corner of the message, styled to match
the dark theme. It avoids cluttering the message list while providing
discoverable interaction points.

---

## WebSocket Protocol Changes

| Dataset | New Operations | Trigger |
|---------|---------------|---------|
| reactions | `snapshot` (initial load) | WebSocket connect |
| reactions | `remove` (key: `messageId:emoji`) | Reaction deleted via REST |
| channels | `remove` (key: `channelId`) | Channel deleted via REST |

Existing operations (snapshot, append, replace) are unchanged.

### Frontend `remove` Event Handling

`channel-sidebar.ts` and `message-list.ts` currently handle only `snapshot`
and `append` operations. Both panels need new `remove` event handlers:

- **`channel-sidebar`:** On `channels` `remove` — filter out the deleted
  channel, re-render, and if the deleted channel was selected, auto-select
  the next and emit `channel-selected`.
- **`message-list`:** On `reactions` `remove` — update reaction pills for
  the affected message. On `channels` `remove` — no direct handling needed
  (responds to `channel-selected` from sidebar).

---

## Data Model Changes

| Change | Table | Detail |
|--------|-------|--------|
| No schema changes | messages | `parent_id` already exists |
| No schema changes | reactions | `(message_id, emoji)` already exists |
| No schema changes | channels | All fields already exist |
| Code-level cascade | channels | `deleteChannel()` implements multi-step DELETE in a single transaction: reactions → messages → members → channel. No FK `ON DELETE CASCADE` changes — cascade handled in application code |

### SPI Additions

| Interface | Method | Detail |
|-----------|--------|--------|
| `ChannelManagement` | `void delete(String channelId)` | New method on existing SPI — breaking change. Implementations: `NoOpChannelManagement` (throws), `SlackChannelManagement`, `DiscordChannelManagement`, `RefChatPlatform` (delegates to `ChatBackend`) |
| `ChatBackend` | `void deleteChannel(String channelId)` | Cascade delete. Implementations: `InMemoryChatBackend`, `SqliteChatBackend` |

---

## Non-Goals

- Per-user reaction tracking (who reacted)
- Thread grouping, collapsing, or side panels
- Keycloak or external OIDC provider (dev-auth uses self-issued JWTs)
- Passwords (login is name-only, JWT is issued without password)
- Persistent identity across browser sessions (sessionStorage only)
- Channel editing (rename, update topic) — create and delete only
