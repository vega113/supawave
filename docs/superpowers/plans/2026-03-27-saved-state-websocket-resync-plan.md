# Saved State Websocket Resync Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the web client from getting stuck in the unsaved warning state after a websocket reconnect by forcing the wave view to resubscribe and by accepting the new reopened stream channel id.

**Architecture:** Keep the fix in the client transport layer instead of the saved-state widget. On websocket disconnect, explicitly fail the active view-open callback so the existing `ViewChannelImpl` and `OperationChannelMultiplexerImpl` reconnect path reissues `viewOpen`. When the stream reopens, clear stale per-wave channel-id filtering so the new resync stream is not rejected.

**Tech Stack:** Java, GWT client, Wave concurrency-control stack, SBT, JUnit 3-style tests

---

## Task 1: Add Focused Reconnect-Bridge and Channel-Reset Regressions

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteViewServiceMultiplexer.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteWaveViewService.java`
- Create: `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/WaveStreamChannelTracker.java`
- Create: `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/WaveViewDisconnectTracker.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/WaveStreamChannelTrackerTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/rpc/WaveViewDisconnectTrackerTest.java`

- [ ] **Step 1: Write the failing regression test**

Create plain-Java tests around extracted transport helpers that prove:
- the first observed channel id for a wave is accepted
- mismatched channel ids are rejected while the original stream is still active
- reopening the same wave clears the stale remembered channel id so a new channel id is accepted
- a websocket disconnect fails the active `WaveViewService.OpenCallback` exactly once
- disconnect does nothing after the stream has already been closed

- [ ] **Step 2: Run the focused test to verify it fails for the expected reason**

Run:

```bash
sbt -Dsbt.global.base=/tmp/saved-state-sbt-global -Dsbt.boot.directory=/tmp/saved-state-sbt-boot -Dsbt.ivy.home=/Users/vega/.ivy2 -Dcoursier.cache=/Users/vega/devroot/incubator-wave/.coursier-cache -Dsbt.ipcsocket.jni=false -Dsbt.ipcsocket.tmpdir=/tmp -batch "testOnly org.waveprotocol.box.server.rpc.WaveStreamChannelTrackerTest"
```

Expected: FAIL because `WaveStreamChannelTracker` and `WaveViewDisconnectTracker` do not exist yet.

- [ ] **Step 3: Implement the minimal transport-layer fix**

Change `RemoteWaveViewService` so websocket disconnect fails the active open callback and enters the existing view/multiplexer reconnect path. Change `RemoteViewServiceMultiplexer` so reopening a wave stream clears any previously remembered channel id for that wave before the next resync/open handshake.

- [ ] **Step 4: Run the focused test to verify it passes**

Run:

```bash
sbt -Dsbt.global.base=/tmp/saved-state-sbt-global -Dsbt.boot.directory=/tmp/saved-state-sbt-boot -Dsbt.ivy.home=/Users/vega/.ivy2 -Dcoursier.cache=/Users/vega/devroot/incubator-wave/.coursier-cache -Dsbt.ipcsocket.jni=false -Dsbt.ipcsocket.tmpdir=/tmp -batch "set Test / fork := false" "testOnly org.waveprotocol.box.server.rpc.WaveStreamChannelTrackerTest org.waveprotocol.box.server.rpc.WaveViewDisconnectTrackerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit the scoped regression + fix**

```bash
git add wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteViewServiceMultiplexer.java wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteWaveViewService.java wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/WaveStreamChannelTracker.java wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/WaveViewDisconnectTracker.java wave/src/test/java/org/waveprotocol/box/server/rpc/WaveStreamChannelTrackerTest.java wave/src/test/java/org/waveprotocol/box/server/rpc/WaveViewDisconnectTrackerTest.java
git commit -m "fix: clear stale wave channel ids on reconnect"
```

## Task 2: Verify the Narrow Client Save/Resync Path

**Files:**
- Modify: `.beads/issues.jsonl`
- Update if needed: `docs/superpowers/plans/2026-03-27-saved-state-websocket-resync-plan.md`

- [ ] **Step 1: Run focused build verification**

Run:

```bash
sbt -batch wave/compile
sbt -batch compileGwt
```

Expected: both commands pass.

- [ ] **Step 2: Run a narrow local sanity check for the reconnect path**

Start the app from this worktree and verify that reopening a wave after a forced websocket reconnect no longer leaves the saved-state indicator stuck on the unsaved warning when no ops remain pending.

- [ ] **Step 3: Record Beads implementation notes**

Add Beads comments with:
- the plan path
- root-cause summary
- commit SHA and summary
- exact verification commands and results
- review outcome and any residual risks

- [ ] **Step 4: Push the task branch**

```bash
git push -u origin fix/saved-state-websocket-resync-task
```

## Acceptance Criteria

- Websocket disconnect causes the active wave view to re-enter the existing reconnect/resubscribe path.
- Reopening a wave after reconnect does not keep rejecting legitimate updates solely because the server assigned a new channel id.
- The targeted regression covers the stale-channel-id reopen case.
- `sbt -batch wave/compile` passes.
- `sbt -batch compileGwt` passes.
- The branch is committed and pushed, with no PR opened yet.

## Out Of Scope

- Rewriting websocket reconnect backoff behavior in `WaveWebSocketClient`
- Redesigning `SavedStateIndicator` visuals or toast timing
- Broad JWT/session-auth changes unless evidence during implementation proves they are the direct root cause
