---
id: PP-20260607-9794cb
title: "All outbound HTTP calls use HttpHelper.CLIENT, not new HttpClient instances"
type: rule
scope: repo
applies_to: "Any class in casehub-connectors-* that makes outbound HTTP calls"
severity: important
refs:
  - core/src/main/java/io/casehub/connectors/http/HttpHelper.java
violation_hint: "A new connector or client calls HttpClient.newHttpClient() or HttpClient.newBuilder().build() — creates a second connection pool with no connect timeout"
created: 2026-06-07
---

`HttpHelper.CLIENT` is a shared `java.net.http.HttpClient` singleton defined in `connectors-core`. It carries a 5-second connect timeout and is reused across all outbound connectors (`SlackConnector`, `TeamsConnector`, `TwilioSmsConnector`, `WhatsAppConnector`, `SlackBotClient`). Any new class that makes outbound HTTP calls must use `HttpHelper.CLIENT.send(...)` rather than constructing its own client. Creating a new `HttpClient` instance produces a duplicate connection pool, bypasses the shared timeout configuration, and introduces inconsistent behaviour if timeout values drift between instances.
