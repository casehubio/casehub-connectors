# Notification UI Components — Composable Web Components for CaseHub

**Date:** 2026-07-06
**Epic:** casehubio/platform#147 (notification system)
**Related issues:** platform#135 (store, done), platform#142 (subscriptions, done), platform#143 (preferences), platform#145 (mute/snooze), platform#146 (frontend), platform#148 (target resolution), platform#155 (event type registry), pages#125 (event-mode push epic), pages#126 (EventBroadcaster), pages#127 (Lit EventStreamController), blocks-ui#22 (pages-data-table)

---

## Overview

Composable Lit web components for the CaseHub notification system. Covers notification inbox (real-time feed with filtering), subscription management (CRUD with constraint builder), contextual subscribe buttons, and mute/snooze controls.

**Reuse-first:** Components are built on existing pages-primitives (`PagesFilterChips`, `PagesScopeSelector`, a11y mixins), pages-ui-tokens (`--pages-*` design tokens), blocks-ui-core (`BlocksConfirmDialog`), and the upcoming `pages-data-table` (blocks-ui#22) for row display. Only notification-specific orchestration and domain logic is new.

Lives in casehub-blocks-ui as a new `notification-inbox` component package — alongside `work-item-inbox`, which follows the identical pattern (Lit orchestrator + reused primitives + SSE push). The components share the same dependency set (`@casehubio/blocks-ui-core`, `@casehubio/pages-primitives`, `@casehubio/pages-ui-tokens`) and design conventions.

**Implementation gate:** `pages-data-table` (blocks-ui#22) must land before implementation begins. The spec designs column definitions against its known API. A proposed `pages-summary-bar` primitive (to be filed on pages) should also land first.

---

## Design Decisions

### Reuse pages-primitives and blocks-ui-core — don't rebuild

The following already exist and must be imported, not rebuilt:

| Primitive | Source | Replaces |
|-----------|--------|----------|
| `PagesFilterChips` | `@casehubio/pages-primitives` | Custom filter bar — generic multi-select toggle with counts + keyboard nav |
| `PagesScopeSelector` | `@casehubio/pages-primitives` | Custom Inbox/Archive tabs — single-select radio pills with badge support |
| `RovingTabindexMixin` | `@casehubio/pages-primitives` | Keyboard navigation mixin |
| `KeyboardShortcutMixin` | `@casehubio/pages-primitives` | Keyboard shortcuts mixin |
| `FocusTrapMixin` | `@casehubio/pages-primitives` | Focus containment for modals/popovers |
| `LiveRegionMixin` | `@casehubio/pages-primitives` | Screen reader announcements for new notifications |
| `BlocksConfirmDialog` | `@casehubio/blocks-ui-core` | Confirmation dialogs for delete/mute |
| `--pages-*` tokens | `@casehubio/pages-ui-tokens` | Spacing, typography, colour, motion, radius |

### Use `pages-data-table` for notification rows

Notification rows are rendered via the upcoming `<pages-data-table>` (blocks-ui#22), not a custom `notification-row` component. The table provides virtual scrolling, row selection, keyboard navigation, column sorting — all things the notification inbox would otherwise rebuild.

**Requirement for blocks-ui#22:** The table must support custom cell/row rendering (cell renderer / row template). Notification rows need a two-line layout (title + body preview), severity left border, and unread dot — not plain text columns. This should be raised on blocks-ui#22 if not already planned.

### Propose `pages-summary-bar` as a new primitive

The badge-count summary bar pattern is identical between `inbox-summary-bar` (blocks-ui work items) and the notification summary bar: a horizontal bar of clickable badge counts with colour coding. The only difference is labels and data. A generic `pages-summary-bar` in pages-primitives serves both. To be proposed as a new issue on casehub-pages.

### SSE for push, REST for queries and mutations

Real-time notification push via `SSEManager` from `@casehubio/pages-data` — extracted from blocks-ui-core with named event support (pages#131, landed as `@casehubio/pages-data@0.2.1`). The notification components subscribe with `eventNames: ['notification', 'notification-updated', 'unread-count']`, which uses `EventSource.addEventListener` per name — directly compatible with `NotificationSseResource`'s named SSE events (no server-side refactoring needed). The SSE endpoint captures `CurrentPrincipal` at stream establishment, so each connection is inherently scoped to the authenticated user — no client-side topic authorization needed.

REST API for queries (cursor-paginated list, unread count), mutations (mark read, dismiss), and CRUD (subscriptions, preferences, mute, snooze).

**Future migration:** When EventBroadcaster (pages#126) ships and provides a WebSocket endpoint for notifications, the components can migrate from `SSEManager` to `createEventConnection` from pages-data. This migration replaces the transport layer only — the component orchestration logic and state management remain unchanged. EventBroadcaster must enforce topic-level authorization (validating `notification:{userId}` against the principal) before this migration.

### Use `WorkIdentity` from blocks-ui-core for user context

All components accept `identity?: WorkIdentity` — the same type `work-item-inbox` uses. Defined in `@casehubio/blocks-ui-core` as `{ userId: string; displayName: string; groups: readonly string[] }`. The `userId` drives SSE connection scoping (the server captures `CurrentPrincipal` at stream establishment, so the identity is verified server-side). The `displayName` is available for display purposes. The `groups` field is unused by notification components but carried for type consistency with the blocks-ui pattern.

### Personal subscriptions fully editable, system subscriptions read-only

Personal subscriptions (ownerId-scoped) support full inline editing and detail panel. System subscriptions (#150, future) render read-only with a "System" badge. System subscription admin is a separate entry point — not part of these components.

---

## Section 1: Component Inventory

Six new components plus reused primitives:

### Notification Bell (application shell entry point)

| Component | Tag | Source | Role |
|-----------|-----|--------|------|
| `notification-bell` | `<notification-bell>` | **New** | Persistent toolbar icon — bell with unread badge, click opens inbox dropdown |

### Notification Inbox Group

| Component | Tag | Source | Role |
|-----------|-----|--------|------|
| `notification-inbox` | `<notification-inbox>` | **New** | Container — orchestrates all children, manages data + EventConnection |
| Tab selector | `<pages-scope-selector>` | **pages-primitives** | Inbox/Archive tabs with unread badge on Inbox |
| Filter pills | `<pages-filter-chips>` | **pages-primitives** | Category + severity + read-state filters with counts |
| Summary badges | `<pages-summary-bar>` | **pages-primitives (proposed)** | Unread/urgent/warning badge counts, clickable |
| Notification rows | `<pages-data-table>` | **blocks-ui#22** | Row display with custom cell renderers for notification layout |

### Subscription Management Group

| Component | Tag | Source | Role |
|-----------|-----|--------|------|
| `subscription-list` | `<subscription-list>` | **New** | Container — lists personal subscriptions, inline enable/disable, edit/delete |
| `subscription-editor` | `<subscription-editor>` | **New** | Detail panel — event type picker, constraint builder, target config |

### Contextual

| Component | Tag | Source | Role |
|-----------|-----|--------|------|
| `subscribe-button` | `<subscribe-button>` | **New** | Embeddable — contextual subscribe from entity or filter state |

### Shared Controls (not standalone, from existing primitives)

| Control | Source | Used by |
|---------|--------|---------|
| Confirmation dialog | `BlocksConfirmDialog` from blocks-ui-core | Delete subscription, mute confirmation |
| Focus trap | `FocusTrapMixin` from pages-primitives | Snooze/mute popovers |
| Screen reader | `LiveRegionMixin` from pages-primitives | Announce new notifications |

**New components to build: 5** (`notification-bell`, `notification-inbox`, `subscription-list`, `subscription-editor`, `subscribe-button`).
**Reused from existing: 8+** (PagesFilterChips, PagesScopeSelector, pages-summary-bar, pages-data-table, BlocksConfirmDialog, SSEManager, a11y mixins).

---

## Section 2: Data Flow & Real-Time Architecture

### Push channel (SSE)

`SSEManager` from `@casehubio/pages-data` subscribing to `GET /notifications/stream` with named event support (pages#131).

```
Server (platform)                          Client (notification-inbox / notification-bell)
─────────────────                          ──────────────────────────────────────────────
NotificationSseResource                    SSEManager.subscribe(url, handler, {
  stream() captures CurrentPrincipal         eventNames: ['notification',
  → userId + tenancyId stored                             'notification-updated',
  → sends initial unread-count                            'unread-count']
                                           })
                                           → EventSource to /notifications/stream
                                           → addEventListener per named event

Named SSE: event: unread-count             → SSEEvent { type: 'unread-count', data }
data: {"count":N}                            → bell sets badge from server count on connect

CDI: @ObservesAsync NotificationCreated
  → .name("notification")                  Named SSE: event: notification
  → send to user's emitters                  → SSEEvent { type: 'notification', data }
                                               → prepend to local items
  → .name("unread-count")                 Named SSE: event: unread-count
  → send to user's emitters                  → SSEEvent { type: 'unread-count', data }
                                               → badge updated to authoritative count

CDI: @ObservesAsync NotificationStatusChanged
  → .name("notification-updated")          Named SSE: event: notification-updated
  → send to user's emitters                  → SSEEvent { type: 'notification-updated', data }
                                               → replace in local state
  → .name("unread-count")                 Named SSE: event: unread-count
  → send to user's emitters                  → badge updated to authoritative count

CDI: @ObservesAsync AllNotificationsRead
  → .name("unread-count")                 Named SSE: event: unread-count
  → send to user's emitters                  → update badge count, mark all local items read
```

No server-side refactoring needed — `NotificationSseResource` already sends named SSE events, and `SSEManager` now supports them via the `eventNames` parameter.

Authorization is implicit: `NotificationSseResource.stream()` captures `CurrentPrincipal` at connection establishment and only sends events for that user's `tenancyId::userId` key. No topic-level authorization model needed.

Reconnection handled by `SSEManager`'s built-in reconnect logic (rebuilds all named event listeners on the new EventSource). On reconnect, the component refetches the notification list from REST to reconcile any events missed during disconnection.

### Queries & mutations via REST

| Operation | Method | Endpoint |
|-----------|--------|----------|
| List notifications | GET | `/notifications?status=&category=&cursor=&limit=` |
| Unread count | GET | `/notifications/unread-count` |
| Mark read | PATCH | `/notifications/{id}/read` |
| Dismiss | PATCH | `/notifications/{id}/dismiss` |
| Mark all read | POST | `/notifications/mark-all-read` |
| Subscription CRUD | GET/POST/PATCH/DELETE | `/subscriptions` |
| Enable/disable | PATCH | `/subscriptions/{id}/enable` or `/disable` |
| Event type discovery | GET | `/subscriptions/event-types` |
| Channel preferences | GET/PUT | `/notifications/preferences` |
| Mute rules | GET/POST/DELETE | `/notifications/mute` |
| Snooze | GET/POST/DELETE | `/notifications/snooze` |
| Available channels | GET | `/notifications/channels` |

### State management

`notification-inbox` is the single orchestrator (same pattern as `work-item-inbox`). It owns all state (items, filters, counts) and passes slices to children via properties. Children emit events up. No shared state store.

---

## Section 3: Notification Bell Component Detail

### `notification-bell` — application shell entry point

The bell icon is the persistent notification surface in the application toolbar. It shows the unread count badge and toggles the inbox dropdown on click.

**Properties (external API):**

```typescript
@property() endpoint?: string;          // Base URL for REST + SSE
@property() identity?: WorkIdentity;    // Current user context
@property({ type: Boolean }) open = false;  // Dropdown visibility
```

**Internal state:**

```typescript
@state() unreadCount = 0;
@state() connectionStatus: 'connected' | 'reconnecting' | 'disconnected' = 'disconnected';
```

**SSE subscription:** On `connectedCallback`, subscribes to `GET /notifications/stream` via `SSEManager` from `@casehubio/pages-data` with `eventNames: ['notification', 'unread-count']`. The server sends an initial `unread-count` named event on connection establishment (no separate REST call needed for the initial badge value). The SSE handler processes:
- `type === 'unread-count'` → set badge to the server's authoritative count. This event arrives on connect, after every `NotificationCreated`, and after every `NotificationStatusChanged` — the badge is always corrected to the true server count.
- `type === 'notification'` → optimistically increment the local count by 1 for immediate visual feedback. The server's follow-up `unread-count` event (sent after every `NotificationCreated`) replaces this with the authoritative value.

The bell component owns its own SSE connection independent of `notification-inbox` — the bell is always visible in the toolbar even when the inbox dropdown is closed.

**Render structure:**

```html
<button
  class="bell-trigger"
  aria-label="${this.unreadCount > 0 ? `${this.unreadCount} unread notifications` : 'Notifications'}"
  aria-expanded="${this.open}"
  aria-haspopup="true"
  @click=${this.toggleDropdown}>
  <svg class="bell-icon"><!-- bell SVG --></svg>
  ${this.unreadCount > 0
    ? html`<span class="badge" aria-hidden="true">${this.unreadCount > 99 ? '99+' : this.unreadCount}</span>`
    : nothing}
</button>
${this.open
  ? html`<div class="dropdown-panel">
      <notification-inbox
        .endpoint=${this.endpoint}
        .identity=${this.identity}>
      </notification-inbox>
    </div>`
  : nothing}
```

**Badge behaviour:**
- Hidden when count is 0
- Shows numeric count up to 99, then "99+"
- Uses `--pages-danger-9` background for urgency
- `aria-hidden="true"` on the badge span — the count is conveyed via the button's `aria-label`

**Dropdown:** Click toggles a positioned dropdown panel containing `<notification-inbox>`. Click outside or Escape closes it. `FocusTrapMixin` contains focus within the dropdown when open.

**Keyboard:** Enter/Space toggles dropdown. Escape closes. Focus moves into the inbox on open, returns to the bell button on close.

**configure():**

```typescript
configure(props: { endpoint?: string; identity?: WorkIdentity }): void
```

---

## Section 4: Notification Inbox Component Detail (formerly §3)

### `notification-inbox` — container orchestrator

**Properties (external API):**

```typescript
@property() endpoint?: string;          // Base URL for REST + EventConnection
@property() data?: Notification[];      // Static data (bypass fetch, for testing)
@property() identity?: WorkIdentity;    // Current user context
```

**Internal state:**

```typescript
@state() activeTab: 'inbox' | 'archive' = 'inbox';
@state() items: Notification[] = [];
@state() categoryFilter: Set<string> = new Set();
@state() readStateFilter: 'all' | 'unread' = 'all';
@state() severityFilter: Set<string> = new Set();
@state() loading = false;
@state() error: string | null = null;
@state() snoozeUntil: Instant | null = null;
@state() selectedItems: Set<string> = new Set();
@state() batchProcessing = false;
@state() batchError: string | null = null;
```

**Render structure:**

```html
<div class="inbox-container">
  <div class="header">
    <pages-scope-selector
      .scopes=${[
        {id: 'inbox', label: 'Inbox', badge: unreadCount},
        {id: 'archive', label: 'Archive'}
      ]}
      .selected=${'inbox'}
      @pages-scope-change=${this.onTabChange}>
    </pages-scope-selector>
    <!-- Snooze indicator + control (when active) -->
  </div>
  <pages-summary-bar
    .items=${this.summaryItems}
    @summary-click=${this.onSummaryFilter}>
  </pages-summary-bar>
  <pages-filter-chips
    .chips=${this.filterChips}
    .selected=${this.activeFilterIds}
    @pages-filter-chips-change=${this.onFilterChange}>
  </pages-filter-chips>
  <pages-data-table
    .columns=${notificationColumns}
    .data=${this.filteredItems}
    .rowRenderer=${this.renderNotificationRow}
    @row-click=${this.onRowClick}
    @selection-change=${this.onSelectionChange}>
  </pages-data-table>
</div>
```

**Tab behaviour:**
- **Inbox** — UNREAD + READ notifications. Default. Badge shows unread count.
- **Archive** — DISMISSED notifications. Read-only browsing.
- Tab switch re-fetches from REST with appropriate `status` filter.

**Filtering:**

Server-side filters drive the fetch (status, category passed as query params). Client-side filtering supplements for fast toggling within the fetched page without re-fetching:

1. Tab (inbox vs archive) → server-side `status` filter on fetch
2. Category chips → server-side `category` filter on fetch, client-side toggle within page
3. Read-state toggle (All / Unread) → server-side `status` filter
4. Severity chips → client-side filter within fetched set (severity not a server query param currently — add if volume demands it)

**Actions (single row):**
- **Click row** → emits `pages-event` topic `notification.selected` with `{notificationId, actionUrl}`. Consumer handles navigation.
- **Mark read** → PATCH `/notifications/{id}/read`, optimistic local update
- **Dismiss** → PATCH `/notifications/{id}/dismiss`, item moves to archive, optimistic update
- **Mute** → mute popover (entity or category scope), POST `/notifications/mute`. Uses `FocusTrapMixin` + `BlocksConfirmDialog` for confirmation.

**Selection and batch actions (same pattern as `work-item-inbox`):**

Internal state for selection:
```typescript
@state() selectedItems: Set<string> = new Set();
@state() batchProcessing = false;
@state() batchError: string | null = null;
```

Selection interactions:
- **Click row checkbox** → toggle selection for that item
- **Shift-click** → range selection from last selected to clicked item
- **Clear button** → deselect all

Batch actions appear in a floating action bar when `selectedItems.size >= 2`:
- **Batch mark read** → parallel `PATCH /notifications/{id}/read` for each selected item. On success: clear selection, announce count. On partial failure: retain failed items in selection, show error banner with success/failure counts.
- **Batch dismiss** → parallel `PATCH /notifications/{id}/dismiss` for each selected item. Confirmation via `BlocksConfirmDialog` before execution. Same success/failure handling as batch mark read.

Batch action bar render:
```html
${this.selectedItems.size >= 2
  ? html`<div class="batch-action-bar">
      <span class="batch-count">${this.selectedItems.size} selected</span>
      <button class="batch-button primary" @click=${this.handleBatchMarkRead}
        ?disabled=${this.batchProcessing}>
        ${this.batchProcessing ? 'Processing...' : 'Mark Read'}
      </button>
      <button class="batch-button danger" @click=${this.handleBatchDismiss}
        ?disabled=${this.batchProcessing}>
        ${this.batchProcessing ? 'Processing...' : 'Dismiss'}
      </button>
      <button class="batch-button secondary" @click=${this.handleClearSelection}
        ?disabled=${this.batchProcessing}>Clear</button>
      ${this.batchError ? html`<span class="batch-error">${this.batchError}</span>` : nothing}
    </div>`
  : nothing}
```

**Error handling and optimistic rollback:**

- **Optimistic update rollback:** Before applying an optimistic mutation (mark read, dismiss), the component snapshots the affected item(s). If the PATCH request fails (network error, 4xx, 5xx), the snapshot is restored and an error banner is shown for 5 seconds (same pattern as `work-item-inbox`'s `_claimError`).
- **Response shape validation:** Per garden gotcha GE-20260705-557ee5, all REST responses are validated before use: check for expected fields (`id`, `status`) before casting. A shape mismatch logs a warning and treats the response as a failure (rollback applies). This prevents a single TypeError from locking the render pipeline.
- **SSE connection status:** When `SSEManager` is reconnecting (EventSource enters `CONNECTING` state), a subtle connection indicator appears in the inbox header: "Reconnecting..." with a spinner. On reconnect, the component refetches the full notification list from REST to reconcile missed events.
- **Initial load failure:** If the initial REST fetch fails, the `error` state is set and displayed as a full-height error panel with a "Retry" button. The SSE subscription is not started until the initial load succeeds.
- **Error banner:** The `error: string | null` state renders as a dismissible banner above the notification list (same style as `work-item-inbox`'s `.error-banner`).

**Pagination:**

- **Page size:** 25 items (matching server default). Configurable via a `pageSize` property.
- **Trigger:** Intersection observer on a sentinel element below the last row. When visible, fetches the next page using the cursor from the previous response.
- **Loading indicator:** A "Loading more..." row appended below the last item during page fetch.
- **End of data:** No sentinel rendered when the server returns fewer items than `pageSize` (no more pages).
- **Real-time push + pagination reconciliation:** New notifications arriving via SSE are prepended to the local `items` array regardless of cursor position. This means:
  - Users viewing the first page see new items appear at the top.
  - Users who have scrolled to page 2+ see new items prepend above the current view (the scroll position is preserved, so the user's viewport stays stable).
  - The cursor for "load more" remains valid because the server's cursor-based pagination is keyed on `createdAt` + `id`, not on absolute position.
  - Duplicate detection: SSE-delivered items are matched by `id` before prepending — if the item is already in the local array (from a REST fetch), the SSE event updates it in place instead of duplicating.

**Keyboard:** Handled by `pages-data-table`'s built-in keyboard navigation. Additional shortcuts via `KeyboardShortcutMixin`: `d` to dismiss, `m` to mute.

**Accessibility:** `LiveRegionMixin` announces new notifications arriving via SSE ("3 new notifications").

### Notification row rendering (via `pages-data-table` custom row renderer)

The table's row renderer produces this layout per notification:

```
┌─────────────────────────────────────────────────────┐
│▌ Title text (truncated)     │ Category │ ● │  2m   │
│▌ Body preview (muted, one line)                     │
└─────────────────────────────────────────────────────┘
 ↑                                        ↑     ↑
 severity border                    unread dot  age
```

- **Left border:** 3px coloured by severity (red=urgent via `--pages-danger-9`, amber=warning via `--pages-warning-9`, accent=info via `--pages-accent-9`)
- **Unread dot:** Filled circle for UNREAD, absent for READ
- **Title:** Primary text, truncated with ellipsis
- **Body preview:** Secondary text, muted colour, single line
- **Category:** Small text label
- **Age:** Relative time (2m, 3h, 5d)
- **Hover:** Subtle background shift

### Notification table column definitions

```typescript
const notificationColumns: ColumnDef<Notification>[] = [
  {
    id: 'title', label: 'Notification', sortable: true, width: '1fr',
    getValue: (n) => n.title,
    render: (_val, n) => html`
      <div class="notification-content">
        <span class="notification-title">${n.title}</span>
        ${n.body ? html`<span class="notification-body">${n.body}</span>` : nothing}
      </div>`,
  },
  {
    id: 'category', label: 'Category', sortable: true, width: '140px',
    getValue: (n) => n.category,
  },
  {
    id: 'status', label: '', sortable: false, width: '24px',
    getValue: (n) => n.status,
    render: (status) => status === 'UNREAD'
      ? html`<span class="unread-dot" aria-label="Unread"></span>`
      : nothing,
  },
  {
    id: 'createdAt', label: 'Age', sortable: true, width: '50px', type: 'date',
    getValue: (n) => n.createdAt,
    render: (val) => html`${relativeTime(val as string)}`,
  },
];
```

Custom cell rendering uses `ColumnDef.render` (returns `TemplateResult | string`). Row-level severity border uses `getRowClass`: `(n) => \`severity-${n.severity.toLowerCase()}\``. Column visibility and sorting handled by `pages-data-table`.

### Filter chips configuration

```typescript
// Category chips — dynamic from data
const categoryChips = uniqueCategories.map(cat => ({
  id: `cat:${cat}`, label: formatCategory(cat), count: countByCategory[cat]
}));

// Severity chips — static set
const severityChips = [
  { id: 'sev:URGENT', label: 'Urgent', count: urgentCount },
  { id: 'sev:WARNING', label: 'Warning', count: warningCount },
  { id: 'sev:INFO', label: 'Info', count: infoCount },
];

// Read-state chips (inbox tab only)
const readStateChips = activeTab === 'inbox'
  ? [{ id: 'read:unread', label: 'Unread', count: unreadCount }]
  : [];
```

All chips passed to a single `<pages-filter-chips>` instance. The `id` prefix (`cat:`, `sev:`, `read:`) distinguishes filter types in the change handler.

### Summary bar configuration (proposed `pages-summary-bar`)

```typescript
const summaryItems = [
  { id: 'total', label: 'Unread', count: unreadCount, color: 'accent' },
  { id: 'urgent', label: 'Urgent', count: urgentCount, color: 'danger' },
  { id: 'warning', label: 'Warning', count: warningCount, color: 'warning' },
];
```

Clickable badges toggle corresponding severity filter.

**Proposed `pages-summary-bar` API:**

```typescript
interface SummaryItem {
  id: string;
  label: string;
  count: number;
  color: 'accent' | 'danger' | 'warning' | 'success' | 'neutral';
  active?: boolean;
}
```

Generic enough for both notification summary and work-item inbox summary (`inbox-summary-bar` in blocks-ui would migrate to this primitive).

**Coordination note:** The blocks-ui repo has a concurrent spec (`2026-07-06-ui-primitives-batch-design.md`) that touches `inbox-summary-bar` hardcoded spacing (§5.8). The `pages-summary-bar` issue must be filed on casehub-pages before implementation begins, and the blocks-ui batch spec's §5.8 token migration should target the new primitive rather than patching the old one.

---

## Section 5: Subscription Management Components (formerly §4)

### `subscription-list` — personal subscription container

**Properties:**

```typescript
@property() endpoint?: string;
@property() identity?: WorkIdentity;
```

**Internal state:**

```typescript
@state() subscriptions: Subscription[] = [];
@state() editing: string | null = null;  // subscription ID or 'new'
@state() loading = false;
```

**Per-row layout** (rendered via `pages-data-table`):
- Subscription name
- Event type pill
- Constraint count ("3 filters")
- Enable/disable toggle — PATCH `/subscriptions/{id}/enable` or `/disable`
- Edit button → opens `subscription-editor`
- Delete button → `BlocksConfirmDialog` confirmation, then DELETE `/subscriptions/{id}`

**System subscriptions (#150, future):** Read-only section with "System" badge. No edit/delete/toggle. Separate admin entry point for management.

### `subscription-editor` — create/edit detail panel

**Properties:**

```typescript
@property({ type: Object }) subscription?: Subscription;  // null = new
@property() endpoint?: string;
@property() identity?: WorkIdentity;
```

**Sections:**

**1. Name** — text input.

**2. Event Type** — dropdown from `GET /subscriptions/event-types` (#155). Shows `displayName`, stores `eventType`. On selection, loads field descriptors for the constraint builder.

**3. Constraint Builder** — dynamic rows:

```
┌──────────────────┬──────────────┬──────────────────┬───┐
│ Field (dropdown) │ Op (dropdown)│ Value (text)     │ ✕ │
└──────────────────┴──────────────┴──────────────────┴───┘
[+ Add filter]
```

- Field dropdown from `EventFieldDescriptor` for the selected event type
- Op: EQ, NEQ, GT, LT, GTE, LTE, IN, STARTS_WITH, CONTAINS
- Value: free text, `$me` for current user
- AND semantics across constraints

**4. Targets** — who gets notified:

```
┌──────────────────┬─────────────────────┬───┐
│ Type (dropdown)  │ ID (text/dropdown)  │ ✕ │
└──────────────────┴─────────────────────┴───┘
[+ Add target]
```

- USER → userId text input (default: current user)
- GROUP → group name text input
- EVENT_FIELD → dropdown from event type fields

**5. Exclude Actor** — checkbox, default true.

**6. Template Preview** — read-only preview of notification appearance.

**7. Save/Cancel** — POST (new) or PATCH (existing).

**Events emitted:** `save` with `{subscription}`, `cancel`.

---

## Section 6: Contextual Subscribe Button & Mute/Snooze Controls (formerly §5)

### `subscribe-button` — embeddable contextual trigger

**Properties:**

```typescript
@property() endpoint?: string;
@property() identity?: WorkIdentity;
@property() entityType?: string;        // entity context
@property() entityId?: string;
@property({ type: Array }) constraints?: Constraint[];  // filter context
@property() eventType?: string;
```

**Entity mode** (`entityType` + `entityId`): Click opens compact popover. Pre-fills constraint `{field: entityType+"Id", op: EQ, value: entityId}`. User picks preferences, confirms.

**Filter mode** (`constraints` + `eventType`): Click opens popover pre-filled with passed constraints. User names subscription, adjusts, confirms.

Shows filled/active state if user already has a matching subscription.

**Events emitted:** `subscription-created` with `{subscription}`.

### Snooze control (inline in notification-inbox)

Part of the inbox header, not a standalone component.

- **Inactive:** Snooze button in header bar
- **Click:** Popover with presets (30min, 1h, 2h, 4h, "Until tomorrow") + custom picker. Uses `FocusTrapMixin`.
- **Active:** "Snoozed until [time]" indicator with cancel button
- **API:** POST/DELETE `/notifications/snooze`

### Mute action (context action on rows)

Triggered from row actions or context menus, not standalone.

- **From notification row:** "Mute this [entityType]" or "Mute [category]"
  - Entity: POST `/notifications/mute` with `{scope: ENTITY, entityType, scopeId: entityId}`
  - Category: POST `/notifications/mute` with `{scope: CATEGORY, scopeId: category}`
- **Optional expiry:** 1 hour / 1 day / permanently
- **Confirmation:** `BlocksConfirmDialog`
- **Active mutes list:** Visible in subscription management area with remove buttons

### Channel preferences (section in subscription management)

Not standalone — a section or tab alongside "My Subscriptions."

- Per-channel row: name (from GET `/notifications/channels`), enable/disable toggle, severity threshold dropdown
- Quiet hours: start/end time pickers + timezone selector
- Save: PUT `/notifications/preferences`

---

## Section 7: Module Structure & Dependencies (formerly §6)

**Location:** `casehub-blocks-ui/components/notification-inbox/` — follows the same package structure as `work-item-inbox` and all other blocks-ui components.

```
components/notification-inbox/
├── package.json
├── tsconfig.json
├── tsconfig.build.json
├── vitest.config.ts
├── src/
│   ├── index.ts                     # Component registration + exports
│   ├── types.ts                     # Notification, Subscription, Constraint, etc.
│   ├── api.ts                       # REST client (authenticatedFetch wrappers)
│   ├── events.ts                    # Event topics + helpers (pages-event contract)
│   ├── notification-bell.ts         # Bell icon + unread badge
│   ├── notification-inbox.ts        # Container orchestrator
│   ├── subscription-list.ts         # Personal subscription list
│   ├── subscription-editor.ts       # Subscription detail editor
│   ├── subscribe-button.ts          # Contextual subscribe trigger
│   └── test/
│       ├── notification-bell.test.ts
│       ├── notification-inbox.test.ts
│       ├── subscription-list.test.ts
│       ├── subscription-editor.test.ts
│       └── subscribe-button.test.ts
└── dist/
```

**Package:** `@casehubio/blocks-ui-notification-inbox`

**Dependencies:**

```json
{
  "dependencies": {
    "lit": "^3.0.0",
    "@casehubio/pages-data": "workspace:*",
    "@casehubio/pages-primitives": "workspace:*",
    "@casehubio/pages-ui-tokens": "workspace:*",
    "@casehubio/blocks-ui-core": "workspace:*"
  },
  "devDependencies": {
    "typescript": "^5.6.0",
    "vitest": "^4.0.0",
    "@open-wc/testing": "^4.0.0"
  }
}
```

TypeScript project references in `tsconfig.json`: `../../packages/blocks-ui-core`, plus any pages-primitives references per blocks-ui convention. Root `tsconfig.json` updated with `components/notification-inbox` reference.

**Build:** Standard blocks-ui component build — `vitest run` for tests, TypeScript project references for type checking, esbuild via the monorepo's shared config.

**Styling:** Lit scoped CSS with `--blocks-*` and `--pages-*` design tokens. Container queries for responsive density. `prefers-reduced-motion` media queries.

---

## Reuse Map

Summary of what is reused vs what is new:

| What | Source | Status |
|------|--------|--------|
| Filter chips (category, severity, read-state) | `PagesFilterChips` from pages-primitives | Available now |
| Tab selector (Inbox/Archive) | `PagesScopeSelector` from pages-primitives | Available now |
| Summary badges | `pages-summary-bar` — proposed new primitive | To be filed on pages |
| Notification rows / table | `pages-data-table` from blocks-ui#22 | In progress — implementation gate |
| Confirmation dialogs | `BlocksConfirmDialog` from blocks-ui-core | Available now |
| Keyboard navigation | `RovingTabindexMixin` from pages-primitives | Available now |
| Keyboard shortcuts | `KeyboardShortcutMixin` from pages-primitives | Available now |
| Focus containment | `FocusTrapMixin` from pages-primitives | Available now |
| Screen reader announcements | `LiveRegionMixin` from pages-primitives | Available now |
| Design tokens | `--pages-*` from pages-ui-tokens | Available now |
| Real-time push (SSE) | `SSEManager` from pages-data (named event support) | Available now |
| `notification-bell` | **New** | — |
| `notification-inbox` | **New** | — |
| `subscription-list` | **New** | — |
| `subscription-editor` | **New** | — |
| `subscribe-button` | **New** | — |

---

## Known Garden Gotchas (from blocks-ui implementation)

Relevant garden entries from recent blocks-ui work — apply directly to these components:

- **GE-20260705-7c80f2:** Lit `@state()` Set/Map mutation in-place does not trigger re-render. Must create new Set/Map on every state change: `this.categoryFilter = new Set([...this.categoryFilter, value])`.
- **GE-20260705-557ee5:** REST response shape mismatch in SSE/event handler crashes the filter pipeline. Validate response shape before casting — a single TypeError in the render pipeline locks the UI with no visible error.
- **GE-20260705-1cda0b:** Empty string is a valid URL base but fails JavaScript truthiness checks. Guard endpoint prop with `this.endpoint != null` not `if (this.endpoint)`.

---

## Dependencies & Blockers

| Dependency | Status | Impact |
|-----------|--------|--------|
| `pages-data-table` (blocks-ui#22) | **In progress — GATE** | Must land before implementation. Notification rows rendered via table. Custom cell renderer support required. |
| `pages-summary-bar` (proposed) | **To be filed** | Generic summary bar for pages-primitives. File issue, propose API. |
| SSEManager named event support (pages#131) | **Done** | Extracted to `@casehubio/pages-data@0.2.1` with `eventNames` parameter. No server-side refactoring needed. |
| Notification store (platform#135) | Done | REST + SSE endpoints available |
| Subscription management (platform#142) | Done | CRUD + engine available |
| Target resolution (platform#148) | **Done** | Subscription model changes (userId→ownerId, targets) — endpoints implemented |
| Channel preferences (platform#143) | **Done** | Preferences REST endpoints (`NotificationPreferenceResource`) — implemented |
| Mute/snooze (platform#145) | **Done** | Suppression REST endpoints (`SuppressionResource`) — implemented |
| Event type registry (platform#155) | **Open — GATE for subscription-editor** | `GET /subscriptions/event-types` required for event type picker and constraint builder. Without this, subscription-editor's core workflow (pick event type → build constraints) cannot function. |
| EventBroadcaster (pages#126) | Open | Server push convenience. Fallback: existing SSE endpoint |
| EventStreamController (pages#127) | Open | Lit reactive controller. Fallback: manual createEventConnection |

**Implementation sequence:** Wait for blocks-ui#22 (table) and pages-summary-bar. Then implement `notification-bell` → `notification-inbox` → `subscription-list` → `subscription-editor` → `subscribe-button`. The bell ships first as the simplest component (SSE subscription + badge only) and provides immediate user-visible value.

---

## Upstream Contributions Required

| Contribution | Target repo | Description |
|-------------|------------|-------------|
| `pages-summary-bar` primitive | casehub-pages | Generic clickable badge-count bar. API: `SummaryItem{id, label, count, color, active}`. Replaces blocks-ui `inbox-summary-bar` too. |
| Custom cell renderer support | blocks-ui#22 | `pages-data-table` needs row/cell template rendering for rich content (two-line layout, severity border, status indicators). |

---

## Deferred

- **Chat-demo migration** — port chat-demo to the same primitives (PagesFilterChips, PagesScopeSelector, pages-data-table). Separate issue after notification components stabilise.
- **System subscription admin UI** — separate entry point with ACL. Depends on #150.
- **Digest configuration UI** — depends on #144 (digest/batching platform work). **Note:** platform#146 acceptance criteria include "digest schedule" under preference management. This spec does not address digest schedule configuration — #146 cannot be fully closed until digest UI ships.
- **Browser push connector** — casehub-connectors delivery target. No issue until routing layer exists.
