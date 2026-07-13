# Progressive Disclosure, Emoji Palette, Swipe Gestures — Design Spec

**Date:** 2026-07-12
**Status:** Approved
**Branch:** issue-63-progressive-disclosure-swipe-emoji
**Covers:** #63 (partial), #64, #55
**Repo:** casehubio/connectors (chat-demo module)
**Parent spec:** specs/2026-07-07-qhorus-chat-ui-design.md (§2, §4, §9 Q1)

---

## 1. Progressive Disclosure (#63)

### Interaction Model

Each message has an expand/collapse toggle button in its header. Clicking
the toggle flips the message's `expanded` state. The feed's current
`@click` → `MESSAGE_SELECTED` is removed. The message component manages
its own expanded state internally via `@state()` (Lit private reactive
state — not externally settable). Multiple messages may be expanded
simultaneously; this is intentional for exploration workflows where
comparing details across messages is useful.

### Expanded View Content

When expanded, `qhorus-message` renders an additional section below the
message content:

**Correlation context** — "In reply to [sender]: [content preview]" when
`inReplyTo` is set. Shows the parent message's sender and first ~80 chars
of content. Parent message data passed as `parentMessage?: QhorusMessage`
from the feed (which has all messages and can look up by ID).

**Artefact details** — Each artefact ref expands from a chip into a detail
row: type icon, label, URI, and selection scope preview (line range or
selected text) when present.

**Commitment details** — For COMMAND messages with a commitment state:
deadline (relative time), acknowledgedAt, delegation target (for DELEGATED
state). Only rendered when `commitmentState` is set and the message type is
obligation-creating.

**Topic and channel metadata** — Topic name and channel name. Channel name
passed as `channelName?: string` from the feed.

**Action bar** — A row of action buttons at the bottom of the expanded view:
- **Reply** button — emits `MESSAGE_SELECTED` (replaces the old click-to-reply)

The reaction bar (with its add button from #64) renders below the content
in both collapsed and expanded states — it is not duplicated in the action
bar.

### Component Changes

`qhorus-message`:
- New properties: `parentMessage?: QhorusMessage`, `channelName?: string`
- `expanded` reclassified from `@property()` to `@state()` (internal-only)
- Dedicated expand/collapse toggle button in the message header (after
  the timestamp). Follows the `qhorus-thread.ts` pattern: `<button
  class="expand-toggle" @click=${this._toggle} aria-expanded=${this.expanded}>`.
  The button is natively focusable and responds to Enter/Space — no host-
  level click handler, no `tabindex` on the host element. This avoids
  conflict with text selection, link clicking, and artefact chip
  interaction within the message content.
- Always renders `<qhorus-reaction-bar>` regardless of `reactions.length`
  (the current `reactions.length > 0` guard is removed — the add button
  from #64 must be reachable on unreacted messages)
- Passes `.messageId=${this.message.id}` to the reaction bar (currently
  missing — without this, `chat:react` events carry an empty messageId)
- New CSS for `.expanded-section` with smooth height transition
  (`prefers-reduced-motion` respected)

`qhorus-channel-feed`:
- New property: `channelName?: string`
- Remove `_selectMessage` click handler from message items
- Pass `parentMessage` by looking up `msg.inReplyTo` in the messages array
- Pass `.channelName=${this.channelName}` to each `<qhorus-message>`

`qhorus-workbench`:
- Pass `.channelName=${selectedChannelName}` to the feed, derived from
  `this._channels.find(c => c.id === this._selectedChannelId)?.name`

### Scope Boundary

No correlation *chain* visualization (future correlation panel, #62). Shows
only the immediate parent context — one level up. Issue #63's body
references "full correlation chain context" — this spec delivers the
progressive disclosure interaction model and immediate parent context;
the full chain visualization remains with #62.

---

## 2. Emoji Reaction Palette (#64)

### New Component: `qhorus-emoji-picker`

A thin wrapper around `emoji-picker-element` that:
- Imports and renders `<emoji-picker>` from the library
- Applies pages design token theming (maps `--pages-*` tokens to the
  picker's CSS custom properties for background, border, text color, accent)
- Emits `emoji-selected` (plain DOM custom event, not `pages-event`) with
  `{ emoji: string }` detail — the reaction bar handles translation to
  `chat:react`. Plain DOM event is intentional: the picker is an internal
  implementation detail of the reaction bar, not a cross-component
  boundary that participates in the `pages-event` contract. The reaction
  bar translates it to the proper `chat:react` pages-event.
- Supports dark mode automatically via the token mapping

### Reaction Bar Changes

`qhorus-reaction-bar` gains:
- An **add button** (`+` pill) that always renders — even when the reaction
  list is empty (this is the only way to add the first reaction)
- A `showPicker` boolean state tracking whether the picker is visible
- Picker uses the HTML Popover API (`popover` attribute, default "auto"
  mode) to render in the top layer, escaping all overflow clipping from
  ancestor containers (the feed's `overflow-y: auto` and the channel-feed
  host's `overflow: hidden` would clip a `position: absolute` picker).
  Positioned with `position: fixed` coordinates computed from the add
  button's `getBoundingClientRect()` on open. Collision detection: if
  insufficient space below the button, the picker flips above.
  Recalculated on open only (no resize observer; picker is short-lived)
- The add button calls `togglePopover()` on the picker element —
  open/close is atomic at the browser level, no custom toggle logic
- **Dismissal:** The Popover API's built-in light dismiss handles both
  click-outside and Escape. No custom `composedPath()` handler is needed.
  The reaction bar listens for the `toggle` event on the picker element
  to synchronize `showPicker` state with the popover's visibility. This
  is the single source of truth — `showPicker` is never set directly by
  click handlers, only by the `toggle` event callback
- Clicking an emoji: emits `chat:react` with the selected emoji and
  calls `hidePopover()` on the picker (the `toggle` event then syncs
  `showPicker = false`)

### Empty State Change

The reaction bar currently renders `nothing` when `reactions.length === 0`.
This changes: the bar always renders the add button. The empty-state guard
moves — pills render only when reactions exist, but the bar itself always
renders.

### Dependency

Add `emoji-picker-element` to `package.json` `dependencies` (not
`devDependencies` — it is imported and rendered at runtime) in
`chat-demo/src/main/webui/`.

### Backend Compatibility

The picker emits `chat:react` with `{ messageId, emoji }` — the same
payload shape the existing reaction bar uses. The chat-demo backend's
reactions REST API (`POST .../reactions`, `DELETE .../reactions/{emoji}`)
is sufficient for end-to-end functionality. Issue #64's declared
dependency on qhorus#328 is for qhorus-native integration (actor-
attributed reactions in the ledger); the picker itself works without it.

### Scope Boundary

No "recently used" persistence across sessions (#81). No skin tone
preference configuration (#82). These are `emoji-picker-element` features
that work out of the box if configured later.

---

## 3. Swipe-to-Reveal Gestures (#55)

### SwipeController

A Lit reactive controller attached to the workbench, active only in phone
mode.

**Edge detection** — Two swipe zones: 20px strip along the left edge of
the host element (channel drawer) and 20px strip along the right edge
(member drawer). Edge positions derived from the host element's
`getBoundingClientRect()`, ensuring correct behavior when the workbench
is embedded and inset from the viewport. `pointerdown` inside either
zone starts tracking.

**Gesture tracking** — On `pointermove`, calculates horizontal delta from
start position. Applies `transform: translateX()` directly to the drawer
element in real-time, clamped to `[0, drawerWidth]`. Backdrop opacity
interpolates proportionally.

**Snap decision** — On `pointerup`, snaps open or closed based on two
criteria (either triggers open):
- Distance threshold: dragged more than 30% of drawer width
- Velocity threshold: swipe speed exceeds 0.5px/ms, measured as a
  windowed average over the last 100ms of `pointermove` events.
  Timestamped position samples are stored in a ring buffer (last 4–6
  events at typical 60fps reporting); velocity computed from the window
  on `pointerup`. This detects terminal "flick" velocity without
  sensitivity to instantaneous jitter.

If neither met, snaps back closed. The existing CSS transition handles
the snap animation.

**Pointer capture** — Uses `setPointerCapture` on `pointerdown`. Prevents
scroll interference during horizontal swipe by calling `preventDefault` on
`pointermove` once horizontal intent is confirmed (delta-x > delta-y after
10px of movement).

### Integration with Workbench

- Controller instantiated in the workbench constructor (standard Lit
  controller lifecycle — not dynamically created/destroyed). Receives
  lazy element accessors via constructor options:
  `{ drawerQuery: (side) => HTMLElement | null, backdropQuery: () => HTMLElement | null }`.
  The host provides these as arrow functions wrapping
  `this.renderRoot.querySelector()`. Controller queries on each gesture
  start, never caches stale references.
- Attaches pointer listeners in `hostConnected` only when
  `_mode === 'phone'`. On mode change (detected via `hostUpdated`),
  detaches listeners if mode is no longer phone, reattaches if mode
  becomes phone. `hostDisconnected` detaches all listeners.
- **Cleanup on detach:** If a drag gesture is in progress when listeners
  are detached (e.g., viewport resize during drag triggers mode change),
  the controller: releases active pointer capture via
  `releasePointerCapture()`, clears inline `style.transform` on the
  drawer element, clears inline `style.opacity` on the backdrop, and
  resets all gesture tracking state.
- Controller calls the existing `_toggleNav()` / `_toggleMember()` methods
- During live drag, controller directly manipulates drawer element's
  `style.transform` and backdrop's `style.opacity` (bypasses Lit rendering
  for performance)
- **Snap handoff protocol:** On snap decision, the controller sets inline
  `style.transform` to the target position (`translateX(0)` for open,
  `translateX(-100%)` for closed-left, `translateX(100%)` for closed-
  right) — NOT clearing the style. Then calls `_toggleNav()` /
  `_toggleMember()`. After `this.host.updateComplete` resolves (CSS class
  now applied), clears the inline style. The CSS rule provides the same
  value, so no visual change occurs. This prevents a rebound glitch where
  clearing the inline style first would cause the drawer to jump to its
  CSS-rule position (closed) before Lit re-renders with the `.open` class

### Accessibility

Visible button alternatives already exist: phone header has hamburger
(channels) and members buttons. Swipe gestures are a shortcut, not the
only path.

- `prefers-reduced-motion`: Live `translateX` tracking during drag is
  disabled — the finger does not visually drag the drawer. Distance and
  velocity thresholds are still evaluated on `pointerup`. If either
  threshold is met, the drawer opens immediately with no transition
  animation. If neither is met, nothing happens.
- Horizontal intent must be confirmed before claiming the pointer;
  vertical scrolling is not interfered with

### Scope Boundary

No swipe-to-close (#83) — swiping a drawer shut. Closing is via backdrop
tap or buttons. Avoids conflicting with horizontal scroll inside drawer
content.

---

## 4. Architecture: Hybrid (Approach C)

Each feature owns its own state and rendering. The workbench provides the
coordination surface only where needed.

| Feature | State owner | Rendering owner | Workbench change |
|---------|-------------|-----------------|------------------|
| Progressive disclosure | `qhorus-message` (`@state() expanded`) | `qhorus-message` | None (feed passes props) |
| Emoji picker | `qhorus-reaction-bar` (`showPicker`) | `qhorus-reaction-bar` + `qhorus-emoji-picker` | None |
| Swipe gestures | `SwipeController` | Workbench (drawer DOM) | Controller attached in phone mode |

No new cross-component state or event channels. The workbench's existing
`_drawerOpen` and the message's internal `@state() expanded` are sufficient.

---

## 5. Implementation Order

1. **#63 — Progressive disclosure** — modifies `qhorus-message` layout;
   reaction bar and picker (#64) anchor to the expanded view's action bar
2. **#64 — Emoji reaction palette** — builds on stable message layout;
   picker anchoring accounts for expanded/collapsed states
3. **#55 — Swipe gestures** — independent of message rendering; only
   touches the workbench's phone layout

---

## 6. Testing Strategy

All features tested with vitest + happy-dom (existing test infrastructure).

**Existing tests impacted:**
- `qhorus-channel-feed.test.ts` `'emits chat:message-selected event when
  message clicked'` — changes: the feed no longer handles message click
  events. MESSAGE_SELECTED is emitted by the Reply button in the
  message's expanded view. Test updated to verify the feed does not
  attach a click handler to `.message-item`.
- `qhorus-reaction-bar.test.ts` `'renders nothing when reactions array
  is empty'` — changes: empty reactions now render the add button (not
  `nothing`). Test updated to verify the add button renders.

**Progressive disclosure (#63):**
- Toggle button click toggles expanded state
- Expanded section renders correlation context when `parentMessage` set
- Expanded section renders artefact details when refs present
- Expanded section renders commitment details for COMMAND with state
- Reply button emits `MESSAGE_SELECTED`
- Toggle button has `aria-expanded` reflecting state
- Toggle button is keyboard-focusable and activates on Enter/Space
- Collapsed state hides expanded section
- Text selection within message content works without triggering expand

**Emoji picker (#64):**
- Add button always renders (even with empty reactions)
- Click add button opens picker (via `togglePopover()`)
- Selecting emoji emits `chat:react` and closes picker
- Escape closes picker (Popover API light dismiss)
- Click outside picker closes picker (Popover API light dismiss)
- Click inside picker does NOT close picker
- Click add button while picker is open closes picker (no reopen flash)
- `showPicker` state stays in sync after light dismiss (`toggle` event)
- Picker is positioned correctly (above/below based on space)

**Swipe gestures (#55):**
- Pointer events in edge zones start tracking
- Horizontal drag past 30% threshold opens drawer
- Fast flick opens drawer (velocity threshold)
- Insufficient drag snaps back closed
- Pointer events outside edge zones are ignored
- Controller only active in phone mode
- `prefers-reduced-motion` disables live tracking
- Snap handoff: after snap-to-open completes, drawer element has no
  remaining inline `style.transform` AND the `.open` CSS class is applied
- Cleanup on mode change mid-drag: start drag in phone mode, change mode
  to tablet while drag is active — verify no remaining inline styles on
  drawer, no active pointer capture, controller ignores subsequent
  pointer events
