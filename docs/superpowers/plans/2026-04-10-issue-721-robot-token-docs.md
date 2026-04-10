# Issue #721 Robot Token Docs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining documentation gaps around robot Data API JWT issuance, refresh, revocation, and event-bundle callback fields without widening the scope beyond docs and doc-backed tests.

**Architecture:** Keep the implementation doc-only. Update the generated API docs (`/api-docs` and `/api/llm.txt`) so they describe the live JWT contract and passive-event payload semantics, update the robot dashboard onboarding prompt so it stops teaching outdated token behavior, and add/update markdown docs so the same guidance exists outside servlet-rendered pages.

**Tech Stack:** Jakarta servlet string-rendered docs, JUnit servlet tests, markdown docs under `docs/`.

---

## Audit Summary

- Already covered by merged work:
  - `#436` added the `Build with AI` section in `/api-docs` and `/api/llm.txt`.
  - `#717` populated `rpcServerUrl` in live `EventMessageBundle` payloads.
- Still missing or misleading:
  - `/api-docs` and `/api/llm.txt` do not fully explain token claims, refresh-on-401 guidance, permanent secret vs expiring JWT, or token-version revocation.
  - `RobotDashboardServlet` still teaches outdated token behavior (`never expire`, callback URL required for both token types) in its AI onboarding prompt.
  - There is no durable markdown doc in `docs/` that explains robot token handling and passive bundle field expectations.

## File Scope

**Create:**
- `docs/robot-data-api-authentication.md`

**Modify:**
- `docs/gpt-bot.md`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`

**Verification targets:**
- `sbt "testOnly org.waveprotocol.box.server.rpc.ApiDocsServletTest org.waveprotocol.box.server.robots.RobotDashboardServletTest"`
- `rg -n "tokenVersion|401|rpcServerUrl|robotAddress|threads" docs/robot-data-api-authentication.md docs/gpt-bot.md`

### Task 1: Lock the missing generated-doc requirements with tests

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/robots/RobotDashboardServletTest.java`

- [ ] Add or update tests so `/api-docs` and `/api/llm.txt` must mention:
  - permanent robot secret vs expiring JWT
  - refresh after `401`
  - `tokenVersion` / JWT version revocation
  - `rpcServerUrl`, `robotAddress`, and nullable/optional `threads` guidance for fetch/event bundles
- [ ] Add or update a dashboard test so the AI onboarding prompt must stop claiming Data API tokens default to never expire and instead mention refresh/re-auth guidance.
- [ ] Run `sbt "testOnly org.waveprotocol.box.server.rpc.ApiDocsServletTest org.waveprotocol.box.server.robots.RobotDashboardServletTest"` and confirm the new assertions fail before production edits.

### Task 2: Update generated API docs to match the live contract

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java`

- [ ] Add a focused robot-auth section that documents the live client-credentials flow, JWT claims/audience/scopes, `tokenExpirySeconds`, `401` refresh expectations, and token-version invalidation.
- [ ] Add passive-event/fetch bundle notes that call out `rpcServerUrl`, `robotAddress`, and that clients must tolerate missing or empty `threads`.
- [ ] Keep the wording aligned with the actual code path (`POST /robot/dataapi/token` with `client_credentials`), not the stale issue phrasing.
- [ ] Re-run `ApiDocsServletTest` and confirm it passes.

### Task 3: Fix the robot dashboard onboarding prompt

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotDashboardServlet.java`

- [ ] Update the embedded AI onboarding prompt so it reflects short-lived Data API tokens, secret-based re-authentication, refresh after `401`, and `rpcServerUrl` bundle usage.
- [ ] Remove or replace any prompt text that says Data API tokens default to never expire or that both token types require the same callback-URL assumptions.
- [ ] Re-run `RobotDashboardServletTest` and confirm it passes.

### Task 4: Add durable markdown docs

**Files:**
- Create: `docs/robot-data-api-authentication.md`
- Modify: `docs/gpt-bot.md`

- [ ] Add a standalone markdown doc covering robot Data API authentication, token refresh/revocation, and passive bundle field expectations.
- [ ] Update `docs/gpt-bot.md` to point operators to the new auth doc and explain how the example robot actually gets and refreshes tokens.
- [ ] Run the planned `rg` checks to confirm the expected guidance exists in markdown form.

### Task 5: Final verification and issue/PR evidence

**Files:**
- No additional code changes unless verification finds a doc/test defect

- [ ] Run `sbt "testOnly org.waveprotocol.box.server.rpc.ApiDocsServletTest org.waveprotocol.box.server.robots.RobotDashboardServletTest"`.
- [ ] Run `rg -n "tokenVersion|401|rpcServerUrl|robotAddress|threads" docs/robot-data-api-authentication.md docs/gpt-bot.md`.
- [ ] Review the diff for scope control (`git diff --stat`, `git diff`).
- [ ] Record audit findings, plan path, verification commands, and final disposition on GitHub issue `#721`.
- [ ] Open a PR only if tracked files changed.
