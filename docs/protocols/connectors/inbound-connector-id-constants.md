---
id: PP-20260607-d4ee52
title: "Inbound connector IDs must be constants in InboundConnectorIds, not string literals"
type: rule
scope: repo
applies_to: "Any inbound connector implementation and any downstream module routing on connector IDs"
severity: important
refs:
  - core/src/main/java/io/casehub/connectors/InboundConnectorIds.java
violation_hint: "A class compares or returns a connector ID as a raw string literal (e.g. \"slack-inbound\") instead of referencing InboundConnectorIds.SLACK_INBOUND — silently breaks if the ID is renamed"
created: 2026-06-07
---

Every inbound connector ID (`SlackInboundConnector.ID`, `EmailInboundConnector.ID`, etc.) must be declared as a `public static final String` constant in `InboundConnectorIds` (in `casehub-connectors-core`) and all consumers — including connector implementations, downstream routing code in `casehub-qhorus`, and tests — must reference the constant rather than a hardcoded string. `InboundConnectorIds` is the single source of truth; it sits in `core` so that modules outside the specific connector module (e.g. `casehub-qhorus-connector-backend`, `casehub-qhorus-slack-channel`) can depend on it without depending on the implementation module. Hardcoded string literals silently diverge from the constant on rename and are not caught by the compiler.
