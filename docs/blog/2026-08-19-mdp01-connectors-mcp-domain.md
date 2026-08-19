---
layout: post
title: "Closing the MCP gap — connectors get a platform domain"
date: 2026-08-19
entry_type: note
subtype: diary
projects: [casehub-connectors]
tags: [mcp, connectors, graphql, cdi-events, scenario-automation]
---

The connectors repo had no presence on the platform MCP surface. Engine and work domains exposed their operations through `casehub_model`/`casehub_action` — cases, work items, lifecycle — but the connector layer was invisible. If you wanted to simulate a customer sending a Slack message in a scenario, you had to call a REST endpoint. Every other backend operation could go through MCP dispatch. This one couldn't.

The fix is a new `casehub-connectors-graphql` module with `@McpDomain("connectors")`. Four operations: `injectChat` fires an `InboundMessage` CDI event as if a real customer sent a message on a specific platform; `sendNotification` routes outbound delivery through `ConnectorService`; `connectorStatus` aggregates the full connector ecosystem — outbound connectors, chat platforms with their capabilities, and both pull and webhook inbound connectors; `sentMessages` queries a ring buffer of recent deliveries for verification in dev/test.

The interesting design question was identity. When `injectChat` constructs an `InboundMessage`, what `connectorType` should it carry? A dedicated "mcp-inject" type would be clean but useless — observers route on `connectorType`, and none of them handle "mcp-inject". The whole point is faithful simulation: if a scenario says "customer sends a Slack message," observers need to see `connectorType = "slack"` to trigger the right path. So the caller specifies the platform, and the `connectorId` gets a constant (`"chat-inject"`) that distinguishes injected messages from real ones without polluting the routing field. Provenance goes in metadata where it belongs.

The outbound side gained a symmetric event. `ConnectorService.send()` now fires `Event<SentMessage>` on every delivery — success or failure — following the same pattern as `InboundConnectorService` on the inbound side. A profile-gated observer (`@UnlessBuildProfile("prod")`) captures these in memory. The observer is invisible in production; in dev and test it provides a verification query without touching the connector SPI.

The module follows the established pattern exactly. `@McpDomain("connectors")` on an SPI interface, `@PlatformQuery`/`@PlatformMutation` annotations, `GraphQLResolverProcessor` generates the resolver at compile time. `GraphQLModelScanner` discovers it automatically at startup — no platform-mcp changes required. The `ConnectorsModelEnricher` reports outbound connector count, chat platform count, and inbound connector count as domain state.

One thing the self-review caught: `connectorStatus` was only reporting pull-based inbound connectors via `InboundConnectorService.pullIds()`. That's one out of seven. All the webhook-based connectors — Slack, Discord, Teams, WhatsApp, SMS — were invisible. The fix was injecting `@All List<WebhookInboundConnector>` alongside the pull IDs and tagging each with its transport type.

The scenario engine can now dispatch connector operations entirely through `casehub_action`. The REST gap is closed.
