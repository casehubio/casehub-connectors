# Design: Paginate `SlackBotClient.listChannels()` — fetch all channels across pages

**Issue:** casehubio/connectors#18  
**Date:** 2026-06-09  
**Branch:** `issue-18-paginate-conversations-list`

---

## Problem

`SlackBotClient.listChannels()` issues a single `conversations.list` request with `limit=200`.
When the Slack API signals additional pages via `response_metadata.next_cursor`, the method
logs a WARNING and returns only the first 200 channels. The `list_channels` MCP tool therefore
returns an incomplete channel list for workspaces with more than 200 channels.

---

## Approach: Auto-paginate inside `SlackBotClient.listChannels()`

Pagination is an implementation detail of the Slack REST API. It must not surface in the
`ConnectorDiscovery` SPI or the `list_channels` MCP tool. Callers ask "what channels exist?"
and expect a complete answer.

---

## What Changes

### New private record: `PageResult`

`parseChannels(String body)` currently returns `List<DiscoveredTarget>` and discards the cursor.
The loop needs both the channel list and the cursor in one pass — parsing the body twice is not
acceptable. Rename `parseChannels` to `parsePage` and change its return type to:

```java
private record PageResult(boolean ok, List<DiscoveredTarget> channels, String nextCursor, String error) {}
```

Field semantics:

| Scenario | `ok` | `channels` | `nextCursor` | `error` |
|---|---|---|---|---|
| Successful page with more pages | `true` | parsed channels | cursor value | `""` |
| Successful last page | `true` | parsed channels | `""` | `""` |
| `ok:false` API error | `false` | `List.of()` | `""` | Slack `error` field (e.g. `"ratelimited"`) |
| Null or blank body | `false` | `List.of()` | `""` | `"empty-response"` |
| Body parse failure | `false` | `List.of()` | `""` | `"parse-error"` or exception message |

`parsePage` implementation rules (all three must be present):

- **Null/blank body guard** — check `body == null || body.isBlank()` before entering the JSON
  parser. Return `PageResult(false, List.of(), "", "empty-response")`. This is a distinct path
  from a parse exception: a blank body is most likely an HTTP 500 with an empty body, and the
  WARNING should say `"empty-response"` rather than `"parse-error"`.
- **Absent `response_metadata` guard** — use `obj.containsKey("response_metadata")` before
  calling `obj.getJsonObject("response_metadata")`. A workspace with fewer than 200 channels
  returns no `response_metadata` key at all; this is normal and must produce `nextCursor = ""`
  (not a parse failure). Without the guard, `getJsonObject` on a missing key throws
  `NullPointerException`, which the catch block would misreport as `"parse-error"`.
- **Error field extraction** — when `ok = false`, extract via `obj.getString("error", "")`.
  Use empty-string default (not `null`) because `PageResult.error` is a `String` concatenated
  directly into WARNING messages.

`parsePage` is exception-safe: its internal catch block returns
`PageResult(false, List.of(), "", "parse-error")` on any JSON parsing exception. It never throws.

The `ok` field is required. Without it, an `ok:false` API error returns the same
`PageResult(List.of(), "")` shape as a normal last page — the loop cannot distinguish them
and silently terminates as if pagination completed, returning an incomplete list with no warning.

The `error` field is required. Step (e) of the loop must include the Slack error string in the
WARNING so operators see `"ratelimited"` vs `"invalid_auth"` vs a parse failure. Without
a dedicated field, the implementer would need to re-parse the body to recover it.

### `SlackBotClient.listChannels(String token)` — cursor loop

Replace the single-request implementation with:

1. Initialise `accumulated = new ArrayList<>()`, `cursor = ""`, `pageNum = 0`.
2. **Loop** while `pageNum < MAX_PAGES`:
   - **Try-catch boundary:** the try-catch wraps steps (a) through (g) inclusive — URL
     construction, `HttpRequest` construction, `HttpHelper.CLIENT.send()`, `parsePage()`,
     accumulation, and cursor extraction. `parsePage` catches its own exceptions internally
     and never throws. `InterruptedException` is caught first; broad `Exception` catches
     everything else.
   - a. Build the query string:
     - First request (`cursor` blank): `?types=public_channel,private_channel&limit=200`
     - Subsequent requests: `?types=public_channel,private_channel&limit=200&cursor=<encoded>`
     - Encode with `URLEncoder.encode(cursor, StandardCharsets.UTF_8)` before appending.
       Slack uses URL-safe base64 (no `+` or `/`), but this is undocumented. `application/x-www-form-urlencoded`
       decoding on the server side would corrupt a `+` to a space. Encoding is unconditional.
   - b. Construct the `HttpRequest`.
   - c. Call `HttpHelper.CLIENT.send(request, BodyHandlers.ofString())`.
   - d. `result = parsePage(response.body())`.
   - e. If `!result.ok()`:
     - Log `WARNING "SlackBotClient: listChannels stopped after %d complete page(s) — returned %d channels: %s"`
       where the three values are `pageNum` (successfully completed pages before failure),
       `accumulated.size()`, and `result.error()`.
     - Return `List.copyOf(accumulated)`.
   - f. `accumulated.addAll(result.channels()); pageNum++;`
   - g. `cursor = result.nextCursor()`. If `cursor.isBlank()` → `break`.
   - **On `InterruptedException`:** `Thread.currentThread().interrupt()`;
     log `WARNING "SlackBotClient: listChannels interrupted after %d complete page(s) — returned %d channels"`
     (`pageNum`, `accumulated.size()`); return `List.copyOf(accumulated)`.
   - **On any other `Exception`:**
     log `WARNING "SlackBotClient: listChannels error after %d complete page(s) — returned %d channels: %s"`
     (`pageNum`, `accumulated.size()`, `e.getMessage()`); return `List.copyOf(accumulated)`.

3. **After the loop** — distinguish normal completion from cap hit by checking `cursor`:
   - `cursor.isBlank()` → last page was reached cleanly (loop exited via `break`). No warning.
   - `!cursor.isBlank()` → loop exited because `pageNum == MAX_PAGES` with a pending cursor.
     Log `WARNING "SlackBotClient: listChannels capped at %d pages — returned %d channels (workspace may have more)"`
     (`MAX_PAGES`, `accumulated.size()`).

4. Return `List.copyOf(accumulated)`.

**`MAX_PAGES = 50`** (`private static final int`; 10,000 channels at 200 per page). Bounds
unbounded loops caused by cursor cycles (Slack API bug) and very large Enterprise Grid workspaces.

**Rate limiting (429):** Deferred. `conversations.list` is Tier 2 (~20 req/min); discovery is
called on-demand (MCP tool invocation), not in a hot path, so burst-rate exhaustion from
pagination is unusual. A 429 response arrives as `{"ok":false,"error":"ratelimited"}` and
surfaces through `result.ok() = false` with `result.error() = "ratelimited"` — the WARNING
will include the string `"ratelimited"`. Full Retry-After handling is a known deferred gap.

**Remove** the "Pagination not yet supported" WARNING from the old `parseChannels`.

### What Does Not Change

| Component | Status |
|---|---|
| `ConnectorDiscovery` interface | Unchanged |
| `SlackBotDiscovery.discover()` | Unchanged |
| `ChannelDiscoveryMcpTool.listChannels()` | Unchanged |
| `DiscoveredTarget` record | Unchanged |
| First-page URL format | Unchanged (`limit=200`, no cursor param) |

---

## Tests

All changes in `SlackBotClientTest`. WireMock scenarios for multi-request flows, following the
existing rate-limit retry test pattern.

### New tests

| Test | What it verifies |
|---|---|
| `listChannels_twoPagesWithCursor_returnsBothPages` | First response has `next_cursor`; second does not. Channels from both pages returned. Second request URL contains `&cursor=<encoded-value>`. |
| `listChannels_threePagesWithCursor_returnsAllPages` | Three-page chain; full accumulation across all three requests. |
| `listChannels_cursorPresentInUrl_onlyOnSubsequentRequests` | First request has no `cursor` param; second has `cursor=<encoded-value>`. Guards against sending `cursor=` on page 1. |
| `listChannels_withCursor_paginatesWithoutWarning` | Replaces `listChannels_responseIsTruncated_logsWarning`. Stubs two pages. Verifies exactly two GET requests issued AND no warnings logged. Positive test. |
| `listChannels_midLoopApiError_returnsPartialWithWarning` | First page succeeds; second returns `{"ok":false,"error":"api_error"}`. Verifies only first page's channels returned AND WARNING mentioning accumulated channel count is logged. |
| `listChannels_midLoopRateLimited_returnsPartialWithRatelimitedWarning` | First page succeeds; second returns `{"ok":false,"error":"ratelimited"}`. Verifies WARNING contains `"ratelimited"`. |
| `listChannels_pageCapReached_returnsAccumulatedWithWarning` | Uses a **catch-all WireMock stub** (`urlMatching("/api/conversations.list.*")`) that always returns one channel and a non-empty cursor. After the call returns, assert `wireMock.verify(50, getRequestedFor(urlMatching("/api/conversations.list.*")))` — confirms exactly MAX_PAGES requests were issued. Assert WARNING contains the cap indicator. Assert 50 channels returned (one per page). This avoids needing 50 individual stubs. |

### Modified tests

`listChannels_responseIsTruncated_logsWarning` — **deleted**; replaced by
`listChannels_withCursor_paginatesWithoutWarning` above.

### Unchanged tests

`listChannels_returnsDiscoveredTargets`, `listChannels_sendsAuthorizationHeader`,
`listChannels_slackReturnsNotOk_returnsEmptyList`, `listChannels_responseIsNotTruncated_noWarningLogged`,
`listChannels_responseMetaPresentButNoCursor_noWarningLogged`.

`SlackBotDiscoveryTest` and `ChannelDiscoveryMcpToolTest` need no changes.

---

## Protocol Compliance

| Protocol | Status |
|---|---|
| `mcp-tool-blocking-annotation` | `@Blocking` already present on `ChannelDiscoveryMcpTool.listChannels()` — no new tools added |
| `spi-id-method-naming` | No new SPIs |
| `credential-config-ownership` | `listChannels(token)` still takes token at call time |
| `ConnectorDiscovery` contract | "Must return quickly" — virtual-thread offloading in place via `@Blocking` |
