# Issue #920 J2CL Selected-Wave Sidecar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing J2CL search sidecar so selecting a digest opens a read-only selected-wave panel with live updates and one reconnect proof, while keeping route state in-memory only and leaving the legacy GWT root path untouched.

**Architecture:** Keep the current J2CL search slice as the left-hand search rail and add a sidecar-only selected-wave panel beside it. Reuse the existing root-session bootstrap fetch and `/search` query path, then add a sidecar-only open/reconnect flow that turns `ProtocolWaveletUpdate` payloads into a read-only view model for the selected wave. Preserve the current isolated `/j2cl-search/**` route, keep selection state in memory only, and treat deeper route/history persistence as explicitly deferred to `#921`.

**Tech Stack:** Java, SBT, J2CL Maven sidecar under `j2cl/`, Elemental2 DOM, sidecar transport codec helpers, generated `gen/messages/org/waveprotocol/box/common/comms/**` protocol models, `scripts/worktree-file-store.sh`, `scripts/worktree-boot.sh`, manual browser verification.

---

## 1. Goal / Root Cause

Issue `#920` exists because the current sidecar search slice stops at list selection instead of opening or rendering the chosen wave:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java:41-50` mounts the search-sidecar mode, but passes a no-op selection callback into `J2clSearchPanelController`, so the selected digest cannot trigger any follow-on wave load.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelController.java:44-113` tracks `selectedWaveId`, highlights the selected digest, and notifies a selection handler, but it has no selected-wave fetch, reconnect, or render path.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java:29-115` builds only the search card, search form, digest list, empty state, and show-more button, and `:152-189` only re-renders digests plus selection highlight. There is no DOM seam for a selected-wave panel.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java:9-43` only supports `fetchRootSessionBootstrap(...)` and `search(...)`. It has no wave-open or wave-refresh operation.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java:64-73` currently reduces `ProtocolWaveletUpdate` to `SidecarWaveletUpdateSummary`, dropping the richer payload even though `gen/messages/org/waveprotocol/box/common/comms/ProtocolWaveletUpdate.java:219-322` exposes `snapshot` and `fragments`.

The narrow root cause is therefore not “selection missing.” Selection already exists. The missing seam is that the sidecar still lacks a selected-wave read model and a sidecar-owned open/update/reconnect flow that can feed a read-only panel.

## 2. Scope And Non-Goals

### In Scope

- Add a selected-wave panel inside the J2CL search sidecar route.
- Reuse the current browser session bootstrap from `/` for the selected-wave flow.
- Add a sidecar-only wave-open/update seam for read-only rendering and live updates.
- Keep the selected-wave state in memory only for this issue.
- Prove one disconnect/recovery cycle without full-page reset.
- Keep the legacy root GWT route compiling, staging, and booting exactly as before.

### Explicit Non-Goals

- No durable route state or deep-link persistence. That belongs to `#921`.
- No create/reply/editor/write flow. That belongs to `#922`.
- No root shell work. That belongs to `#928`.
- No root bootstrap flag or cutover work. That belongs to `#923` and `#924`.
- No retirement of the GWT root client or `compileGwt`. That belongs to `#925`.
- No silent widening into full `WavePanelImpl` migration.

## 3. Exact Files Likely To Change

### Primary Sidecar Files

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarWaveletUpdateSummary.java`
- `j2cl/src/main/webapp/assets/sidecar.css`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`

### Likely New Sidecar-Only Files

These filenames are an inference based on the existing `j2cl/search` structure and may shift slightly during implementation, but the responsibilities should remain separate:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`

### Shared / Generated Inspect-Only References

- `gen/messages/org/waveprotocol/box/common/comms/ProtocolWaveletUpdate.java`
- `gen/messages/org/waveprotocol/box/common/comms/gson/ProtocolWaveletUpdateGsonImpl.java`
- `gen/messages/org/waveprotocol/box/common/comms/WaveletSnapshot.java`
- `gen/messages/org/waveprotocol/box/common/comms/ProtocolFragments.java`
- `build.sbt:831-920`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java:816-843`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteWaveViewService.java:337-339`
- `wave/src/main/java/org/waveprotocol/box/webclient/client/HistoryChangeListener.java:74`
- `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java:155-251`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/WavePanelImpl.java`

Those legacy files are parity references only. `#920` should not patch the live GWT runtime to make the sidecar work.

## 4. Concrete Task Breakdown

### Task 1: Freeze The Issue Boundary Before Coding

- [ ] Keep `/j2cl-search/index.html` as the only runtime surface for this issue.
- [ ] Preserve the current tracker order from `#904`: `#920 -> #921 -> #922 -> #928 -> #923 -> #924 -> #925`.
- [ ] Record in the issue comments that `#920` owns read-only selected-wave rendering only, with route persistence explicitly deferred to `#921`.
- [ ] Treat the GWT root client as a parity reference, not an implementation target.

### Task 2: Add A Sidecar Shell Layout That Can Host A Selected Wave

- [ ] Extend `J2clSearchPanelView` so the sidecar route can render a split layout: search rail plus selected-wave panel.
- [ ] Keep the new layout responsive in `j2cl/src/main/webapp/assets/sidecar.css`, with a stacked mobile fallback.
- [ ] Preserve the existing digest-selection highlight, wave count, empty states, and show-more affordance while adding the selected-wave panel.
- [ ] Add explicit empty/loading/error states for the selected-wave panel so selection and recovery failures are visible without falling back to devtools.

### Task 3: Turn Digest Selection Into A Real Sidecar Wave-Open Flow

- [ ] Replace the no-op `waveId -> { }` hook in `SandboxEntryPoint` with a sidecar-owned selection controller.
- [ ] Extend `J2clSearchGateway` with the narrow operations needed for `#920`:
  - reuse root bootstrap/session resolution
  - open the selected wave over the existing sidecar socket path
  - reconnect or re-open the selected wave after a forced disconnect
- [ ] Keep selection state in memory only. Do not add URL/history state in this issue.
- [ ] Ensure re-running a search clears the selected wave when the selected digest falls out of the result set, matching the current controller behavior.
- [ ] Add explicit error behavior for at least:
  - unauthorized or failed selected-wave open
  - selected wave disappearing or becoming inaccessible
  - updates arriving after the user has switched to a different wave

### Task 4: Extend The Sidecar Transport Decode Beyond Summary-Only Updates

- [ ] Start with a go/no-go J2CL-safety probe for `ProtocolWaveletUpdateGsonImpl`:
  - if it compiles cleanly into the sidecar and can decode a representative selected-wave payload in a focused test, prefer reusing it
  - if it fails the probe, stop and commit to the fallback path below instead of widening scope ad hoc mid-implementation
- [ ] If it is J2CL-safe, reuse it to decode the selected-wave payload into sidecar-friendly projections.
- [ ] If it is not J2CL-safe, extend `SidecarTransportCodec` narrowly so it can decode the `snapshot` and/or `fragments` fields needed for read-only rendering without disturbing the legacy transport path.
- [ ] Preserve the current summary-only test coverage while adding richer decode coverage for the selected-wave path.
- [ ] Keep all transport changes sidecar-only under `j2cl/**`.

### Task 5: Add A Read-Only Selected-Wave Projection And View

- [ ] Build a sidecar-owned read-only wave model that is intentionally smaller than the legacy `WavePanelImpl` stack.
- [ ] Prefer a narrow rendering target for `#920`:
  - wave title / identity
  - participant / unread metadata needed for visible read-state proof
  - a read-only conversation content projection good enough to prove the selected wave is really opened and kept live
- [ ] Treat read-state proof as a first-class seam:
  - first confirm whether the selected-wave payload already surfaces enough data to prove read/unread movement
  - if it does not, add the smallest sidecar-visible status projection needed for `#920` rather than widening into legacy wavepanel parity
- [ ] Do not attempt editor parity, toolbar parity, or write affordances here.
- [ ] Keep the selected-wave rendering code independent from route persistence so `#921` can layer on URL/history later without redoing the read-only panel.
- [ ] Build all visible text with DOM `textContent` / equivalent safe text insertion, not raw HTML interpolation.

### Task 6: Prove Live Update And One Disconnect / Recovery Cycle

- [ ] Add a deterministic controller-level test for selected-wave open + state transitions.
- [ ] Add transport tests that cover at least one update payload carrying the fields the new read-only projector needs.
- [ ] Add at least one deterministic reopen-path test that exercises the selected-wave recovery flow after a simulated disconnect or close event.
- [ ] In local browser verification, intentionally force one socket disconnect and prove the selected wave resumes without a full page reload.
- [ ] Verify that the opened wave demonstrates live unread/read movement, not only a static initial snapshot.

### Task 7: Preserve The Legacy Root Path And Record Traceability

- [ ] Re-run the legacy root compile/stage gates even though the implementation is sidecar-only.
- [ ] Boot the app from the issue worktree, with shared file-store state prepared via:

```bash
cd /Users/vega/devroot/worktrees/issue-920-j2cl-selected-wave
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

- [ ] Record the exact local verification commands and observed results in `journal/local-verification/`.
- [ ] Mirror the important verification and review results into the GitHub issue comment and PR body.

## 5. Exact Verification Commands

Run these from `/Users/vega/devroot/worktrees/issue-920-j2cl-selected-wave`.

### Targeted Sidecar Unit / Integration Gates

```bash
sbt -batch j2clSearchBuild j2clSearchTest
```

Expected result:

- the sidecar search build remains green
- the new selected-wave panel tests pass
- the sidecar output remains isolated under `war/j2cl-search/**`
- `build.sbt:831-920` remains the authoritative definition of `j2clSearchBuild` / `j2clSearchTest`

### Legacy Root Compile / Stage Gate

```bash
sbt -batch compileGwt Universal/stage
```

Expected result:

- the legacy GWT root client still compiles
- staged packaging remains green
- the root `/` path remains served by the legacy runtime

### Fresh Worktree File-Store Prep

```bash
cd /Users/vega/devroot/worktrees/issue-920-j2cl-selected-wave
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

Expected result:

- `wave/_accounts`, `wave/_attachments`, and `wave/_deltas` are linked into the issue worktree
- local browser verification can use realistic current-user wave data

### Local Boot / Smoke

```bash
bash scripts/worktree-boot.sh --port 9910
```

Then run the exact printed helper commands, typically:

```bash
PORT=9910 JAVA_OPTS='...' bash scripts/wave-smoke.sh start
PORT=9910 bash scripts/wave-smoke.sh check
```

### Route Presence Checks

```bash
curl -sS -I http://localhost:9910/
curl -sS -I http://localhost:9910/j2cl-search/index.html
```

Expected result:

- `/` responds successfully and remains the legacy GWT route
- `/j2cl-search/index.html` responds successfully and mounts the J2CL sidecar

### Manual Browser Verification

Verify all of the following against the local booted app:

- legacy `/` still loads and remains usable
- `/j2cl-search/index.html` still supports search and digest selection
- selecting a digest opens a read-only selected-wave panel beside the search results
- the opened wave continues receiving live updates
- unread/read state changes are visibly proven on the selected wave path
- one forced disconnect/recovery cycle resumes the selected wave without a full page reset
- reload/deep-link persistence is still out of scope and therefore not claimed here

Use browser devtools offline/network disable as the canonical disconnect method for this issue unless implementation constraints force a different approach, and record the exact method used plus the observed recovery behavior in `journal/local-verification/<date>-issue-920-j2cl-selected-wave.md`.

## 6. Review / PR Expectations

- Run Claude plan review before implementation starts.
- After implementation, run direct review plus Claude implementation review.
- Address all review comments that make technical sense, including nitpicks and out-of-diff feedback when they are valid.
- Resolve review threads only after replying with the fix commit or technical reasoning.
- If a later rebase or conflict occurs, inspect both sides carefully and do not overwrite newer behavior with older code.

## 7. Definition Of Done For This Slice

- `/j2cl-search/index.html` supports search plus a read-only selected-wave panel.
- The selected wave stays live after open and through one disconnect/recovery proof.
- Selection state remains in-memory only; no deep-link persistence is claimed.
- The legacy root GWT path still compiles, stages, boots, and smokes green.
- Claude plan review and Claude implementation review outcomes are recorded in the issue or PR traceability.
- Issue comments contain worktree, plan path, verification commands/results, review summary, and PR link.
