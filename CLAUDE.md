# connectors Workspace
**Name:** casehub-connectors
**Project repo:** /Users/mdproctor/claude/casehub/connectors
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/connectors` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| java-update-design / update-primary-doc | `design/JOURNAL.md` (created by `epic`) |
| adr | `adr/` |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (superpowers output)
- `plans/` — implementation plans (superpowers output)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records with INDEX.md
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Git Discipline

Two git repositories are active in every session: a **workspace** (methodology artifacts: handover, blog, specs, plans, ADRs) and the **project repo** (source code).

Before any git operation, run `git rev-parse --show-toplevel` to confirm which repo is currently active. Do not assume — the session may have opened in either. cd to the correct repo before staging:
- Source code commits → project repo
- Methodology artifacts → workspace


## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `docs/adr/` — promoted at epic close |
| specs      | project     | lands in `docs/specs/` — promoted at epic close |
| blog       | workspace   | staged here; published to mdproctor.github.io via publish-blog |
| plans      | workspace   | stay in workspace permanently |
| design     | project     | journal file lives in workspace design/; merge target is project ARC42STORIES.MD (§10 for ADRs; was docs/DESIGN.md — now retired pending cleanup) |
| snapshots  | workspace   | stay in workspace permanently |
| handover   | workspace   | |

---

# casehub-connectors — Claude Code Project Guide

## Platform Docs
- [Platform Index](https://raw.githubusercontent.com/casehubio/parent/main/docs/INDEX.md) — discovery index (start here)
- [Building Platform](https://raw.githubusercontent.com/casehubio/parent/main/docs/guides/building-platform.md) — platform contributor guide
- [This repo's deep-dive](https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-connectors.md)

## Reference Documents (casehub-parent)

| Document | What it covers |
|----------|---------------|
| `../garden/docs/protocols/casehub/FOUNDATION-INDEX.md` | CaseHub foundation protocols |

---

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2

---

## What This Project Is

Outbound and inbound message connector library for the casehubio platform. Provides a `Connector` CDI SPI (outbound) and `InboundConnector`/`WebhookInboundConnector` SPIs (inbound) with built-in implementations for Slack, Teams, Twilio SMS, WhatsApp, and email. Also provides a `ChatPlatform` SPI (`chat-spi`) for structured interaction with chat systems (channels, threads, reactions, presence, members, channel management, member management, message history) with graceful degradation across platforms. ChatPlatform model includes `RichCard` for platform-agnostic rich content and `Channel` with `memberCount`. ChatPlatform implementations: `chat-ref` (in-memory reference), `chat-irc` (IRC with 3 native capabilities), `chat-discord` (Discord with 8 native capabilities + Gateway inbound + attachment downloading + rich embed support), `chat-slack` (Slack with 9 native capabilities — most capable implementation; batch user fetch for members, full ts-precision message history). Shared HTTP clients: `slack-bot` (Slack Web API — 15 methods: messaging, channel listing, reactions, presence, members, users, channel management incl. archive, member management, message history), `discord` (Discord Bot REST API v10 + Gateway WebSocket + CDN attachment download with SSRF defense + rich embed serialization + channel delete). MCP tools: `send_slack`, `send_teams`, `send_sms`, `send_whatsapp`, `send_email`, `send_chat`, `list_channels`, `list_chat_channels`. `ChannelManagement` SPI includes `delete()` — Slack archives via `conversations.archive`, Discord calls `DELETE /channels/{id}`. Includes a profile-gated demo chat service (`chat-demo`, `-Pdemo`) with SQLite persistence, REST/WebSocket endpoints, a pre-populated seed database, JWT-based user identity via `casehub-pages-auth` (dev-auth login, `@Authenticated` REST, `HttpUpgradeCheck` WebSocket auth, auto-membership, presence auto-create), and an optional casehub-pages Quinoa frontend (`-Pdemo -Pui`) with responsive layout (phone drawers, tablet single-sidebar with tabs, desktop three-column), dockable panels, dark mode, real-time WebSocket data, channel create/delete UI, emoji reactions with palette, Discord-style inline reply threading, auto-expanding multiline textarea input, login gate (`<pages-dev-auth>`), and identity widget (`<pages-identity>`).

**This is the canonical connector infrastructure for the platform.** Any casehubio repo that needs to send outbound messages or receive inbound webhook messages must use these SPIs, not implement its own connector.

---

## Key Rule

Do not add business logic, orchestration, or domain knowledge here. This library is pure delivery infrastructure — it sends outbound messages and receives inbound ones, firing a CDI event. Callers decide when, what, and to whom; observers decide what to do with received messages.

---

## Build and Test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install
```

**With demo module:**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -Pdemo
```

**With demo module + UI:**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -Pdemo -Pui
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

**Chat-demo webui tests (vitest + happy-dom):**
```bash
cd chat-demo/src/main/webui && npx vitest run
```

---

## Java on This Machine

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26)    # Java 26, use for dev and tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home  # GraalVM 25, native only
```

---

## Ecosystem Conventions

**Quarkus version:** All projects use `3.32.2`. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:** Add to `pom.xml` `<repositories>`:
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```
CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

**Cross-project SNAPSHOT versions:** All casehubio artifacts are `0.2-SNAPSHOT` resolved from GitHub Packages.


## Work Tracking

Issue tracking: enabled
GitHub repo: casehubio/connectors

## Development Workflow

Before designing: `superpowers:brainstorming`
Before implementing: `superpowers:test-driven-development`
Before committing: `superpowers:requesting-code-review`

Living docs — check for drift after significant changes:
- `ARC42STORIES.MD` — primary design doc; check §9–10 after SPI, module, or connector changes
- `docs/adr/INDEX.md`

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.