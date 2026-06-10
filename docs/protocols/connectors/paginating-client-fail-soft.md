---
id: PP-20260610-83747b
title: "Paginating HTTP client methods return partial results + WARNING on mid-loop failure"
type: rule
scope: repo
applies_to: "Any method in casehub-connectors-* that issues multiple HTTP requests to enumerate a paginated resource"
severity: important
refs:
  - slack-bot/src/main/java/io/casehub/connectors/slack/bot/SlackBotClient.java
violation_hint: "A paginating method that returns List.of() or throws on mid-loop failure — the caller (ConnectorDiscovery, MCP tool) receives an empty list and cannot distinguish failure from an empty resource"
created: 2026-06-10
---

When a paginating HTTP client method encounters a failure mid-loop (API error, interrupt,
page cap reached), it must return the pages already successfully accumulated as a
`List.copyOf(accumulated)` and log a WARNING that includes: the number of complete pages
fetched, the number of items accumulated, and the error string (e.g. `"ratelimited"`,
`"parse-error"`, or the exception message). Never return an empty list on mid-loop failure
— the caller cannot distinguish failure from a genuinely empty resource. A bounded page cap
(`MAX_PAGES`) must be declared as a named constant, and cap-hit must produce its own
WARNING distinct from an error WARNING. Reference: `SlackBotClient.listChannels()`.
