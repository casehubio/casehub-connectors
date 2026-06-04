# 0003 — ConnectorMeshBridge SPI placement in core, not mcp

Date: 2026-06-04
Status: Accepted

## Context and Problem Statement

The `ConnectorMeshBridge` SPI introduced in connectors#1 needs to be accessible to
two parties: the `mcp` module (the caller, in this repo) and `qhorus/connector-backend`
(the future implementor, in the qhorus repo). The SPI can live in either module.

## Decision Drivers

* `qhorus/connector-backend` already depends on `casehub-connectors-core` (for `InboundMessage`)
* `ConnectorMeshBridge` uses only `String` parameters — no types from MCP infrastructure
* Avoiding unnecessary dependency propagation into the qhorus build graph

## Considered Options

* **Option A** — SPI in `core`
* **Option B** — SPI in `mcp`
* **Option C** — New `casehub-connectors-api` module

## Decision Outcome

Chosen option: **Option A — SPI in `core`**, because `qhorus/connector-backend` can
implement `ConnectorMeshBridge` by depending only on `casehub-connectors-core`, which it
already does. No new cross-repo dependency is introduced.

### Positive Consequences

* Qhorus bridge implementation requires zero new Maven dependencies
* `NoOpConnectorMeshBridge @DefaultBean` can live alongside the SPI in `core`, keeping the
  no-op next to the interface it implements
* Follows the established `casehub-connectors-core` dependency pattern already present in
  `qhorus/connector-backend`

### Negative Consequences / Tradeoffs

* `core` gains a SPI (`ConnectorMeshBridge`) that is only injected by `mcp` module code.
  `@Unremovable` is required on the no-op default because ARC would otherwise eliminate it
  at augmentation time when `core` is used without `mcp` (no visible injection point in `core`
  itself).

## Pros and Cons of the Options

### Option A — SPI in `core`

* ✅ Zero new cross-repo dependency for the Qhorus bridge implementor
* ✅ `@DefaultBean` no-op co-located with the SPI
* ❌ `@Unremovable` annotation required on the default (ARC would otherwise eliminate it)

### Option B — SPI in `mcp`

* ✅ SPI co-located with its sole caller
* ❌ `qhorus/connector-backend` must add `casehub-connectors-mcp` as a new dependency,
  pulling in `quarkus-mcp-server-core` — MCP infrastructure with no value to a CDI bridge module

### Option C — New `casehub-connectors-api` module

* ✅ Clean separation of SPI from implementation modules
* ❌ Over-engineered for a single interface with one foreseeable implementor
* ❌ Adds a fourth module and another artifact to publish

## Links

* casehubio/connectors#1 — connectors MCP tools feature
* casehubio/qhorus#249 — Qhorus implementation of `ConnectorMeshBridge` (forthcoming)
* `docs/protocols/casehub/bridge-module-spi-placement.md` — protocol that informed this decision
