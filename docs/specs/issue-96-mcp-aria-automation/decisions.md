# Decisions — issue-96-mcp-aria-automation

## D1: sentMessages recording mechanism

**Choice:** CDI event capture
**Alternatives:**
- SPI + ref impl — adds a test-only SPI to the core contract, mixes delivery and verification responsibilities
- Defer sentMessages — drops useful verification capability from the issue scope
**Rationale:** Mirrors the inbound pattern (`Event<InboundMessage>`). `ConnectorService.send()` fires `Event<SentMessage>` on successful delivery. A profile-gated observer in the graphql module records messages in an in-memory ring buffer. Keeps core focused on delivery; recording is an observer concern.
**Trade-offs:** Observer must be active for recording to happen — no recording in production unless the profile is enabled. Ring buffer has finite capacity.
**Sources:** `InboundConnectorService.java` (CDI event pattern), `ConnectorService.java` (outbound routing)
**Exploration:** quick
**Status:** captured

## D2: Resolver generation strategy

**Choice:** Generate by default, hand-write when necessary
**Alternatives:**
- All hand-written — follows engine-graphql/work-graphql as-is, but produces boilerplate for operations that are pure delegation
**Rationale:** `GraphQLResolverProcessor` generates resolvers from `@McpDomain` interfaces with `@PlatformQuery`/`@PlatformMutation`. `scanHandWrittenMethods` skips operations that already have a hand-written `@GraphQLApi` resolver, so both coexist cleanly. Maximizes generation, minimises boilerplate.
**Trade-offs:** Generated code is less visible during debugging — the resolver class lives in `target/generated-sources/`.
**Sources:** `GraphQLResolverProcessor.java` (generation logic), `GraphQLModelScanner.java` (discovery)
**Exploration:** quick
**Status:** captured

## D3: Module placement

**Choice:** New `graphql` module (`casehub-connectors-graphql`)
**Alternatives:**
- Existing `mcp` module — fewer modules but mixes two MCP surfaces (`@Tool` vs `@McpDomain`), diverges from the established convention
**Rationale:** Follows the `casehub-engine-graphql` / `casehub-work-graphql` convention. Clean separation: `mcp` module = `@Tool` (Quarkus MCP server direct tools), `graphql` module = `@McpDomain` (platform MCP dispatch via `casehub_action`).
**Trade-offs:** One more module to maintain. Build time marginally increases.
**Sources:** `casehub-engine-graphql/pom.xml`, `casehub-work-graphql/` module structure
**Exploration:** quick
**Status:** captured

## D4: injectChat identity model

**Choice:** `connectorType` = caller-specified `platform` parameter (required); `connectorId` = constant `"chat-inject"`
**Alternatives:**
- Dedicated "mcp-inject" connectorType — defeats purpose; observers that route on connectorType never trigger for injected messages
- Caller-specified both connectorType and connectorId — unnecessary flexibility; connectorId has no meaningful real-connector equivalent for injection
- Default + override (optional connectorType) — ambiguity; defaulting implies "it doesn't matter" when it does matter for routing
**Rationale:** Faithful simulation requires observers to see the correct `connectorType` for routing (e.g., "slack"). The scenario engine already knows which platform it's simulating. `connectorId = "chat-inject"` (new constant in `InboundConnectorIds`) distinguishes injected from real messages without overloading the routing field. Provenance goes in metadata (`"source": "mcp-inject"`), not in routing fields. Validates that `platform` matches a registered `ChatPlatform` to prevent silent drops.
**Trade-offs:** Requires the caller to know the platform identifier. No support for non-chat injection (email, SMS) — scoped to chat per the operation name.
**Sources:** `InboundMessage.java` (record shape), `InboundConnectorService.java` (receive chain), `ChatPlatformMcpTool.java` (platform param pattern), `InboundConnectorIds.java` (ID constants protocol)
**Exploration:** deep-analysis
**Status:** captured
