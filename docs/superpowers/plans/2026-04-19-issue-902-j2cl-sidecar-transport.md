# Issue #902 J2CL Sidecar Transport Salvage Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the JSO-dependent transport/codecs path for the new J2CL work only, while keeping the legacy GWT webclient transport/runtime path untouched and provably green.

**Architecture:** The repo now has an isolated J2CL sidecar scaffold under `j2cl/` plus opt-in `j2clSandbox*` and `j2clSearch*` SBT tasks. Issue `#902` should build on that scaffold instead of rewriting the active `wave/src/main/java/org/waveprotocol/box/webclient/**` transport stack. The safe salvage path is additive: keep the server JSON envelope and current legacy GWT JSO path intact, prove the sidecar can use the existing generated `impl`/`gson` families where possible, and add sidecar-only transport/codec code only where the sidecar still needs new seams.

**Tech Stack:** Java, SBT, Maven sidecar under `j2cl/`, PST code generation, Gson/POJO generated message families, existing `/socket` JSON envelope, JUnit 4, worktree boot/smoke scripts, manual browser sanity verification.

---

## 1. Goal / Root Cause

Issue `#902` still exists because the browser transport seam is currently tied to GWT-era generated `*JsoImpl` message classes plus the legacy webclient runtime path.

The failed earlier attempt went wrong because it tried to rewire the active legacy webclient transport path directly. That widened the issue from “make the sidecar transport/compiler-safe” into “change the running GWT app,” which broke `compileGwt` and staged-root boot.

The branch baseline is now narrower:

- the J2CL sidecar scaffold from `#900` already exists under `j2cl/`
- the server still speaks the same JSON-over-WebSocket protocol and should remain unchanged for this issue
- the only preserved branch-local progress is the strengthened `wave/src/test/java/org/waveprotocol/pst/PstCodegenContractTest.java`, which proves the required `impl`, `gson`, `jso`, and `proto` families exist for the transport/search message roots

The revised root cause is therefore not “missing full-client transport migration.” It is “the sidecar still lacks its own J2CL-safe transport/codec path, and the first implementation attempted to solve that by disturbing the wrong runtime seam.”

## 2. Scope And Non-Goals

### In Scope

- Keep the legacy GWT runtime path active and unchanged.
- Use the isolated J2CL sidecar path as the only runtime surface for new transport/codec work.
- Reuse the current server JSON envelope (`sequenceNumber`, `messageType`, `message`) and current `/socket` endpoint.
- Start from the existing generated `impl` and `gson` PST families as the preferred sidecar-safe transport representation.
- Add sidecar-only transport adapters, sidecar-only codec glue, and sidecar-only tests under `j2cl/`.
- Extend PST generation only if the existing `impl` + `gson` outputs are insufficient for the sidecar path.
- Keep `PstCodegenContractTest` as the minimum preserved proof that the required families continue to exist.

### Explicit Non-Goals

- No rewrite of the active legacy GWT transport/runtime path.
- No behavior change to the root `/` page, signin/bootstrap flow, or `/webclient/**` asset path.
- No replacement of `WaveWebSocketClient`, `RemoteViewServiceMultiplexer`, `RemoteWaveViewService`, or `JsoSearchBuilderImpl` as the live GWT runtime for this issue.
- No server protocol change, protobuf-js runtime, or alternate socket endpoint.
- No root-route or feature-flag cutover from GWT to J2CL.
- No PR if the sidecar path is green but the legacy root path regresses.

## 3. Exact Files Likely To Change

### Expected Primary Edits

- `build.sbt`
- `j2cl/pom.xml`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxBuildSmokeTest.java`
- New files under `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/`
- New files under `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/`
- New files under `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/`
- New files under `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/`
- `wave/src/test/java/org/waveprotocol/pst/PstCodegenContractTest.java`

### Conditional Only If Existing `impl` + `gson` Outputs Prove Insufficient

- `wave/src/main/java/org/waveprotocol/pst/templates/gson/**`
- `wave/src/main/java/org/waveprotocol/pst/templates/pojo/**`
- New template files under `wave/src/main/java/org/waveprotocol/pst/templates/` for a sidecar-safe codec target
- `gen/messages/org/waveprotocol/box/common/comms/**`
- `gen/messages/org/waveprotocol/wave/federation/**`
- `gen/messages/org/waveprotocol/wave/concurrencycontrol/**`
- `gen/messages/org/waveprotocol/box/search/**`

### Inspect-Only / No-Touch Legacy Runtime Files For This Salvage Plan

- `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveSocket.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveSocketFactory.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/WaveWebSocketClient.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteViewServiceMultiplexer.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteWaveViewService.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/SnapshotFetcher.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/common/SnapshotSerializer.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/common/WaveletOperationSerializer.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/JsoSearchBuilderImpl.java`
- `wave/src/main/java/org/waveprotocol/box/server/rpc/WebSocketChannel.java`
- `wave/src/main/java/org/waveprotocol/box/server/rpc/ProtoSerializer.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`

## 4. Concrete Task Breakdown

### Task 1: Freeze The Sidecar-Only Boundary Before Coding

**Files:**
- Inspect only: `docs/j2cl-gwt3-decision-memo.md`
- Inspect only: `docs/j2cl-preparatory-work.md`
- Inspect only: `docs/superpowers/plans/j2cl-full-migration-plan.md`
- Inspect only: legacy runtime files listed above

- [ ] Restate in the issue comment and PR notes that `#902` is no longer allowed to change the active legacy webclient transport/runtime path.
- [ ] Record the preserved branch baseline:
  - only `wave/src/test/java/org/waveprotocol/pst/PstCodegenContractTest.java` is intentionally carried forward from the failed earlier attempt
  - any transport/codec work must land behind the isolated sidecar routes
- [ ] Treat the legacy GWT transport files as parity references only, not as the implementation target for this issue.

### Task 2: Prove The Sidecar Can Stand On Existing Generated Families First

**Files:**
- Modify if needed: `wave/src/test/java/org/waveprotocol/pst/PstCodegenContractTest.java`
- Inspect and possibly wire through build/test flow: `build.sbt`
- Inspect generated roots:
  - `gen/messages/org/waveprotocol/box/common/comms/**`
  - `gen/messages/org/waveprotocol/wave/federation/**`
  - `gen/messages/org/waveprotocol/wave/concurrencycontrol/**`
  - `gen/messages/org/waveprotocol/box/search/**`

- [ ] Keep the contract test improvement and use it as the first gate for the salvage plan.
- [ ] Attempt the sidecar implementation against the existing generated `impl` + `gson` families before inventing a new generator target.
- [ ] Preserve `jso` generation and all legacy consumers untouched even if a new sidecar-safe target is later added.
- [ ] Only widen PST generation when a concrete sidecar gap is identified and documented.

### Task 3: Add A Sidecar-Only Transport Runtime Under `j2cl/`

**Files:**
- Modify or create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/**`
- Modify if needed: `j2cl/pom.xml`
- Test: `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/**`

- [ ] Add a sidecar-local WebSocket adapter that talks to the existing `/socket` endpoint without routing any live root traffic away from the GWT client.
- [ ] Keep the existing JSON envelope shape exactly:
  - `sequenceNumber`
  - `messageType`
  - `message`
- [ ] Keep the numeric JSON field-key compatibility expected by the current generated message families.
- [ ] Make the sidecar transport own its own open/update/submit/search codec wiring instead of patching the active GWT `WaveWebSocketClient` path.
- [ ] Keep the sidecar transport package parallel to, not intertwined with, `wave/src/main/java/org/waveprotocol/box/webclient/client/**`.

### Task 4: Add A Sidecar Consumer Proof Instead Of A Root-Path Rewrite

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`
- Modify or create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/**`
- Test: `j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxBuildSmokeTest.java`
- Test: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/**`

- [ ] Extend the current sandbox/search-sidecar proof so it exercises the sidecar-safe transport/codec path instead of acting as a static scaffold only.
- [ ] Keep the proof isolated to sidecar routes such as `/j2cl-search/**` or another sidecar-only page under `j2cl/`.
- [ ] Do not change `/`, `/webclient/**`, or the legacy login/bootstrap path while proving the sidecar transport.
- [ ] Make the first proof narrow:
  - open/auth handshake
  - update decoding
  - submit round-trip or a tightly scoped search request/response
- [ ] Prefer one deterministic sidecar proof path over broad UI migration in this issue.

### Task 5: Only Add New PST Output If The Sidecar Cannot Use The Existing Families

**Files:**
- Conditional modify: `wave/src/main/java/org/waveprotocol/pst/templates/gson/**`
- Conditional modify: `wave/src/main/java/org/waveprotocol/pst/templates/pojo/**`
- Conditional create: new sidecar-safe template files under `wave/src/main/java/org/waveprotocol/pst/templates/`
- Conditional regenerate:
  - `gen/messages/org/waveprotocol/box/common/comms/**`
  - `gen/messages/org/waveprotocol/wave/federation/**`
  - `gen/messages/org/waveprotocol/wave/concurrencycontrol/**`
  - `gen/messages/org/waveprotocol/box/search/**`

- [ ] Only do this task if the sidecar cannot safely consume the current `impl` + `gson` outputs.
- [ ] If a new sidecar-safe generator target is required, make it additive and parallel to `jso`, not a replacement for `jso`.
- [ ] Keep the new target limited to the transport/search families needed by this issue.
- [ ] Re-run the PST generation gate and preserve the existing contract test coverage.

### Task 6: Verify Both The Legacy Root Path And The Sidecar Path Before Any PR

**Files:**
- Verify only: `build.sbt`
- Verify only: `j2cl/pom.xml`
- Verify only: `journal/local-verification/<date>-issue-902-j2cl-transport-codecs.md`

- [ ] Run PST generation plus `PstCodegenContractTest`.
- [ ] Run the J2CL sidecar build/test tasks.
- [ ] Re-run `compileGwt` and staged-root packaging proof even though the implementation is sidecar-only.
- [ ] Boot the staged app from the worktree and prove both:
  - the root `/` page still serves the legacy GWT runtime
  - the isolated sidecar page loads and exercises the new sidecar transport/codec path
- [ ] Do not open a PR unless both paths are green.

## 5. Exact Verification Commands

Run these from `/Users/vega/devroot/worktrees/issue-902-j2cl-transport-codecs`.

### PST Generation And Contract Gate

```bash
sbt -batch generatePstMessages "testOnly org.waveprotocol.pst.PstCodegenContractTest"
```

Expected result:

- PST generation completes successfully
- `PstCodegenContractTest` passes
- the required transport/search family roots still expose `impl`, `gson`, `jso`, and `proto`

### J2CL Sidecar Build/Test Gates

```bash
sbt -batch j2clSandboxBuild j2clSandboxTest j2clSearchBuild j2clSearchTest
```

Expected result:

- sidecar build/test tasks pass without altering `war/webclient/**`
- sidecar outputs remain isolated under `war/j2cl-debug/**`, `war/j2cl-search/**`, and `war/j2cl/**`

### Legacy GWT Compile And Staged-Root Proof

```bash
sbt -batch compileGwt Universal/stage
```

Expected result:

- `compileGwt` stays green
- `Universal/stage` stays green
- the root staged app still contains the legacy `webclient` bootstrap assets

### Local Server Boot And Smoke

```bash
bash scripts/worktree-boot.sh --port 9900
```

Then run the exact printed start/check/stop commands from the helper. The standard shape must remain:

```bash
PORT=9900 JAVA_OPTS='...' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
```

### Root And Sidecar Route Presence Checks

```bash
curl -sS -I http://localhost:9900/
curl -sS -I http://localhost:9900/webclient/webclient.nocache.js
curl -sS -I http://localhost:9900/j2cl-search/index.html
curl -sS -I http://localhost:9900/j2cl/index.html
```

Expected result:

- `/` responds successfully
- `/webclient/webclient.nocache.js` is still present for the legacy root path
- `/j2cl-search/index.html` and `/j2cl/index.html` are present when the sidecar artifacts were built

### Browser Sanity Verification

Browser verification is required by `docs/runbooks/change-type-verification-matrix.md` because this issue touches client/runtime packaging and browser-visible sidecar assets.

Open:

- `http://localhost:9900/`
- `http://localhost:9900/j2cl-search/index.html`

Confirm:

- the root `/` page still boots the legacy GWT client rather than the sidecar
- the isolated sidecar page loads independently and exercises the new sidecar transport/codec proof path
- the sidecar page does not take over the root shell

Stop the server with the exact helper-printed command. The standard shape is:

```bash
PORT=9900 bash scripts/wave-smoke.sh stop
```

### Hard PR Gate

No PR is allowed unless all of the following are green in the same worktree:

- `sbt -batch generatePstMessages "testOnly org.waveprotocol.pst.PstCodegenContractTest"`
- `sbt -batch j2clSandboxBuild j2clSandboxTest j2clSearchBuild j2clSearchTest`
- `sbt -batch compileGwt Universal/stage`
- staged server boot/smoke
- browser sanity for both `/` and the isolated sidecar page

## 6. Acceptance Criteria

- The implementation stays sidecar-only and does not replace the active GWT runtime path.
- The legacy transport/runtime files listed above remain inspect-only or behavior-preserving references for this issue.
- The sidecar can speak the existing `/socket` JSON protocol using a J2CL-safe transport/codec path.
- The first sidecar proof uses the isolated sidecar routes and does not change `/` or `/webclient/**`.
- `PstCodegenContractTest` remains green and continues proving the required message families exist.
- If a new PST target is added, it is additive and does not remove or repurpose `jso`.
- `compileGwt` and `Universal/stage` stay green after the change.
- Local staged verification proves both:
  - legacy root path green
  - isolated sidecar path green
- No PR is opened unless both paths are green.

## 7. Issue / PR Traceability Notes

- Worktree: `/Users/vega/devroot/worktrees/issue-902-j2cl-transport-codecs`
- Branch: `issue-902-j2cl-transport-codecs`
- Plan path: `docs/superpowers/plans/2026-04-19-issue-902-j2cl-sidecar-transport.md`
- Record verification evidence in `journal/local-verification/<date>-issue-902-j2cl-transport-codecs.md` and mirror the important commands/results into the linked GitHub Issue comment.
- The issue comment and PR body should explicitly say that the earlier active-webclient rewrite was abandoned because it broke `compileGwt` / staged-root boot, and that this revision keeps the legacy GWT transport/runtime path intact.
- Commit summaries should make the sidecar-only boundary obvious, for example:
  - PST gate / sidecar codec prerequisite
  - sidecar transport runtime
  - sidecar transport tests
  - verification-only follow-up
- The PR summary must explicitly state: legacy root path green, isolated sidecar path green, no cutover performed.
