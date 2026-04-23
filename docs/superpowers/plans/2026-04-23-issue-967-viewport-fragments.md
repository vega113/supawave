# Issue #967 Viewport-Scoped Fragment Windows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drive the J2CL/Lit selected-wave read surface from explicit viewport-scoped fragment windows instead of the current coarse whole-wave selected-wave payload assumptions, while preserving the existing server clamp contract and leaving broader StageOne/read-surface behavior to `#966`.

**Architecture:** Reuse the existing repo split rather than inventing a new transport. Initial selected-wave open continues to go through the sidecar `ProtocolOpenRequest`, but the J2CL open envelope must grow to carry the same viewport hints the GWT client already sends. Scroll growth does not reopen the selected-wave socket; it uses the existing `/fragments` JSON endpoint to fetch additional windows and merges them into a J2CL-owned visible-region model. To satisfy parity row `R-7.4`, the server path must stop treating viewport fragments as additive-on-top-of-a-full-snapshot when viewport mode is active, otherwise the client can render a windowed container while still paying whole-wave bootstrap cost on the wire.

**Tech Stack:** J2CL Java client (`j2cl/src/main/java/...`), existing sidecar websocket transport, existing `/fragments` HTTP JSON endpoint, existing server-side `WaveClientRpcImpl` fragments attachment path, current root-shell/search/selected-wave J2CL surfaces, `sbt`-driven repo tasks (`j2clSearchBuild`, `j2clSearchTest`, targeted `testOnly`), and existing JUnit/J2CL test suites under `j2cl/src/test/java` and `wave/src/test/java`.

---

## 1. Goal / Baseline / Root Cause

### 1.1 Parity contract for `#967`

This plan is scoped to the rows already assigned to `#967`:

- `docs/j2cl-parity-issue-map.md:307-340` defines the issue scope as:
  - initial visible window
  - J2CL open-contract viewport hints
  - fragment expansion / scroll growth
  - read-surface container updates
  - explicit server limits / clamps
- `docs/j2cl-gwt-parity-matrix.md:102-103` assigns `R-3.5` and `R-3.6` to `#967`:
  - visible-region container model
  - DOM-as-view provider compatibility for the new container
- `docs/j2cl-gwt-parity-matrix.md:119` assigns `R-4.6` to `#967`:
  - fragment-fetch policy must honour existing hints/clamps
- `docs/j2cl-gwt-parity-matrix.md:168-171` assigns `R-7.1`–`R-7.4` to `#967`:
  - initial visible window
  - extension on scroll
  - server clamp behavior
  - no regression to whole-wave bootstrap
- `docs/j2cl-lit-design-packet.md:265-272,384-389,496` says `#967` consumes only the read-surface `visible-region-placeholder` visual primitive; Stitch is **Prohibited** for fragment viewport logic itself.

### 1.2 Current repo seams that already exist

The repo is not starting from zero. The existing GWT/server path already has the pieces `#967` must reuse:

- GWT initial open hint seam:
  - `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteWaveViewService.java:327-340`
  - `wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteViewServiceMultiplexer.java:183-224`
  - `RemoteWaveViewService` reads `initialViewportStartBlipId`, `initialViewportDirection`, and `initialViewportLimit` from client flags, then calls the multiplexer overload that sets `viewportStartBlipId`, `viewportDirection`, and `viewportLimit` on `ProtocolOpenRequest`.
- GWT dynamic growth seam:
  - `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java:1091-1185`
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/DynamicRendererImpl.java:755-813`
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/FragmentRequester.java:29-103`
  - `DynamicRendererImpl` builds `RequestContext` from the visible blips and current versions, then uses either `ViewChannelFragmentRequester` or `ClientFragmentRequester` based on the existing `fragmentFetchMode` / `enableFragmentFetchViewChannel` / `enableFragmentFetchForceLayer` flags.
- Server fragments/window seam:
  - `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java:371-428`
  - `wave/src/test/java/org/waveprotocol/box/server/frontend/WaveClientRpcViewportHintsTest.java:62-127`
  - `WaveClientRpcImpl` already reads `viewportStartBlipId`, `viewportDirection`, and `viewportLimit`; the current tests cover defaulting, max clamping, and invalid-direction tolerance.
- HTTP `/fragments` seam:
  - `wave/src/main/java/org/waveprotocol/box/server/rpc/FragmentsServlet.java:69-277`
  - the servlet already accepts `waveId`, `waveletId`, `startBlipId`, `direction`, `limit`, `startVersion`, and `endVersion`, and returns JSON containing:
    - `version.snapshot`, `version.start`, `version.end`
    - `ranges`
    - `fragments`
    - per-blip metadata

### 1.3 Current J2CL gaps that block `#967`

The current J2CL selected-wave path is still fundamentally sidecar-demo shaped:

- J2CL open envelope cannot carry viewport hints:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarOpenRequest.java:7-31`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java:13-31`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java:231-238`
  - `SidecarOpenRequest` only contains participant id, wave id, and wavelet prefixes; `encodeOpenEnvelope(...)` only writes numeric keys `1`, `2`, and `3`; `buildSelectedWaveOpenFrame(...)` hardcodes the minimal request.
- J2CL preserves fragment payloads only long enough to flatten them:
  - decode seam: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java:86-149`
  - projection seam: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java:46-54,259-317`
  - model seam: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java:20-68`
  - view seam: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:68-105`
  - the transport decoder already preserves ranges and fragment entries, but the projector reduces them to `List<String> contentEntries`; the model stores only those flattened strings; the view renders them as `<pre>` blocks. That discards:
    - segment identity
    - loaded vs unloaded window boundaries
    - placeholder positions
    - enough structure to implement `R-3.6` DOM-as-view compatibility
- J2CL has no fragment-growth transport seam:
  - there is no `fetchFragments(...)` equivalent anywhere under `j2cl/src/main/java`
  - a repository-wide search only finds viewport-aware logic in the GWT client and server path, not in J2CL
- The root-shell/search-selected-wave host seams are still destructive:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java:13-40`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java:48-152`
  - `J2clRootShellView` and `J2clSearchPanelView` clear `host.innerHTML`; that is exactly the kind of startup behavior `#965` must change before `#967` can safely consume preserved server-rendered fragment HTML.

### 1.4 Additional implementation risks that must be planned up front

These are the non-obvious seams that turn `#967` into a mixed server/client slice rather than a pure J2CL UI task:

- Current bootstrap JSON does **not** carry visible-window hints:
  - `wave/src/main/java/org/waveprotocol/box/common/J2clBootstrapContract.java:43-60`
  - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/J2clBootstrapServlet.java:103-136`
  - the current bootstrap contract is limited to `session`, `socket.address`, and shell metadata. There is no current typed source of initial viewport anchor/limit for the J2CL read surface.
- Current initial fragments still ride alongside full snapshot payloads:
  - `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java:192-218`
  - `docs/fragments-viewport-behavior.md:213-217`
  - `docs/migrate-conversation-renderer-to-apache-wave.md:21-31`
  - the server still emits a full snapshot on initial open when fragments are present, which means a client-only change cannot meet `R-7.4`.
- Current clamp semantics diverge between initial-open and `/fragments` growth:
  - open path: `WaveClientRpcImpl.resolveViewportLimit(...)` clamps to `wave.fragments.defaultViewportLimit` / `wave.fragments.maxViewportLimit`
  - HTTP growth path: `FragmentsServlet.clampLimit(...)` hardcodes default `50` and max `200`
  - if J2CL uses `/fragments` for growth without server cleanup, extension windows will not match initial-open limits.

### 1.5 Narrow root-cause summary

`#967` is blocked by three concrete gaps:

1. the J2CL sidecar open request cannot express viewport intent;
2. the J2CL selected-wave model/view cannot preserve or render a fragment-window container;
3. the current server transport still sends full-snapshot bootstrap data even when a viewport window exists, so a client-only fix cannot satisfy `R-7.4`.

## 2. Acceptance Criteria

`#967` is complete when all of the following are true:

- J2CL selected-wave open frames carry the same viewport hint fields the GWT client already uses (`start blip`, `direction`, `limit`), encoded in the numeric `ProtocolOpenRequest` shape.
- The J2CL selected-wave controller owns a typed viewport state (anchor, direction, limit, loaded ranges) instead of treating fragment payloads as raw display text.
- The selected-wave model/view can render:
  - loaded fragment sections
  - unloaded placeholder sections
  - a scrollable visible-region container that requests extension windows when the user enters an unloaded edge
- Scroll growth uses a dedicated J2CL fetch path that keeps the live selected-wave socket open; it must not “refresh the whole selected wave” just to grow the window.
- The server clamp rules used by initial open and by extension fetches are aligned and explicitly test-covered.
- When viewport mode is active and fragments are available, the server no longer boots the selected-wave read surface with a whole-wave snapshot payload; large-wave open remains windowed on the wire as well as in the DOM.
- The implementation records/uses the existing parity telemetry shape:
  - initial-window size/clamp
  - extension fetch counts/outcomes
  - clamp applied/rejected
  - whole-wave fallback warning if it still occurs
- The implementation stays inside `#967` scope:
  - no full StageOne parity work from `#966`
  - no live-surface promotion from `#968`
  - no new Stitch/design-system work beyond consuming the existing placeholder family
- Verification passes:
  - targeted J2CL tests
  - targeted server fragments/open tests
  - narrow local browser verification on a large-wave path against the merged `#965/#966` baseline

## 3. Scope And Non-Goals

### 3.1 In scope

- J2CL sidecar open-envelope viewport hints
- initial-window state derivation for selected-wave open
- J2CL fragment-window model + projector + view/container changes
- J2CL scroll-growth fetch path
- server clamp normalization between initial open and growth fetch
- server-side no-whole-wave-bootstrap behavior needed for `R-7.4`
- tests and issue evidence for the above

### 3.2 Explicit non-goals

- No full StageOne keyboard/focus/collapse/thread-navigation parity. Those remain `#966`.
- No live reconnect / route-history / shell-wide state work. Those remain `#968`.
- No compose/edit/reaction/task work.
- No visual redesign beyond consuming the already-approved `visible-region-placeholder`.
- No PR creation in this prep lane.

## 4. Dependency Readiness

### 4.1 Already merged and available on this branch

- `#961` parity matrix: merged
- `#962` Lit design packet: merged
- `#963` bootstrap JSON contract: merged
- `#964` Lit root shell/chrome primitives: merged
- `#931` unread/read selected-wave state: merged
- `#933` HttpOnly-safe sidecar websocket auth: merged
- `#936` selected-wave version/hash atomicity: merged

### 4.2 Current blockers for implementation

As of the current issue state on 2026-04-23:

- `#965` is in active implementation, not merged
- `#966` is still prep-only / not merged

That means `#967` is **plan-ready but not implementation-ready**.

### 4.3 Why `#965` is a hard blocker

`#965` owns the preserved server-first selected-wave HTML seam. Until that lands, the J2CL root-shell/search-selected-wave path still clears host DOM on startup (`J2clRootShellView`, `J2clSearchPanelView`), which means `#967` has no stable preserved server fragment container to upgrade or inspect for initial visible-window state.

### 4.4 Why `#966` is a hard blocker

`#966` owns the practical StageOne read-surface container and DOM/provider contract. `#967` only makes sense once the read surface is no longer a flat `<pre>` list. This slice should wire viewport windows into the merged `#966` container rather than grow the current placeholder sidecar view into a second competing read-surface architecture.

### 4.5 Required first implementation step once the blockers merge

Before any code task below:

- [ ] Run `git fetch origin && git rebase origin/main`
- [ ] Confirm `origin/main` includes the merged implementations for `#965` and `#966`
- [ ] Re-audit the actual merged file seams and update this plan in-branch if the merged file names differ from the current `J2clSelectedWave*` seams

## 5. Slice Parity Packet — Issue #967

**Title:** Drive the Lit read surface from viewport-scoped fragment windows instead of whole-wave payloads
**Dependencies:** `#965`, `#966`

### Parity matrix rows claimed

- `R-3.5` — visible-region container model
- `R-3.6` — DOM-as-view provider compatibility for the new container
- `R-4.6` — fragment-fetch policy
- `R-7.1` — initial visible window
- `R-7.2` — extension on scroll
- `R-7.3` — server clamp behavior
- `R-7.4` — no regression to whole-wave bootstrap for large waves

### Design-packet consumption

- Consume §5.2 `visible-region-placeholder`
- Do **not** open new Stitch work; `docs/j2cl-lit-design-packet.md:389` marks fragment viewport logic as design-tool-prohibited

### Existing seams to reuse

- Initial viewport flags / open transport:
  - `RemoteWaveViewService`
  - `RemoteViewServiceMultiplexer`
- Dynamic growth request context:
  - `FragmentRequester.RequestContext`
  - `DynamicRendererImpl.maybeRequestFragments(...)`
- Server viewport hints + clamp:
  - `WaveClientRpcImpl`
  - `WaveClientRpcViewportHintsTest`
- HTTP growth JSON:
  - `FragmentsServlet`

### J2CL seams to replace or extend

- `SidecarOpenRequest`
- `SidecarTransportCodec`
- `J2clSearchGateway`
- `J2clSelectedWaveController`
- `J2clSelectedWaveProjector`
- `J2clSelectedWaveModel`
- `J2clSelectedWaveView`
- root-shell / selected-wave host wiring touched by `#965/#966`

### Telemetry / observability checkpoints

- initial open records the requested and effective viewport limit
- extension fetches record request count, success/failure, and clamp-used
- a warning metric/log fires if the selected-wave open path still receives a full snapshot while viewport mode is active

### Verification shape

- smoke + browser + harness, consistent with `R-3.5`, `R-4.6`, `R-7.1`–`R-7.4`

## 6. File Structure

### 6.1 Existing files expected to change

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarOpenRequest.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java`
- `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java`
- `wave/src/main/java/org/waveprotocol/box/server/rpc/FragmentsServlet.java`

### 6.2 New files recommended by this plan

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarViewportHints.java`
  - tiny immutable holder for `startBlipId`, `direction`, and `limit`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarFragmentsResponse.java`
  - DTO for `/fragments` JSON (`version`, `ranges`, `fragments`, `blips`)
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewportState.java`
  - typed selected-wave window state preserved across projection and scroll growth
- `wave/src/main/java/org/waveprotocol/box/server/frontend/ViewportLimitPolicy.java`
  - shared config-backed clamp/default helper used by both `WaveClientRpcImpl` and `FragmentsServlet`

### 6.3 Existing tests expected to grow

- `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchGatewayAuthFrameTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/frontend/WaveClientRpcViewportHintsTest.java`

### 6.4 New tests recommended by this plan

- `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarFragmentsResponseTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/FragmentsServletViewportLimitTest.java`

## 7. Task Breakdown

### Task 1: Rebase gate and merged seam audit

**Files:**
- Read / confirm only:
  - `docs/superpowers/plans/2026-04-23-issue-967-viewport-fragments.md`
  - merged `#965` / `#966` files on `origin/main`

- [ ] Rebase the worktree after `#965` and `#966` merge

Run:
```bash
git fetch origin
git rebase origin/main
git log --oneline --max-count=8
```

Expected:
- the branch includes the merged commits for `#965` and `#966`
- the current selected-wave/root-shell file names still match this plan, or this plan is amended before code starts

- [ ] Confirm the merged `#965` seam that exposes the initial selected-wave window source

Run:
```bash
rg -n "selected-wave|fragment|visible|data-" j2cl/src/main/java wave/src/jakarta-overrides/java
```

Expected:
- one concrete merged seam exists for the initial visible-window seed:
  - preserved server DOM marker from `#965`, or
  - explicit bootstrap extension from upstream merged work

- [ ] Confirm the merged `#966` read-surface container seam

Run:
```bash
rg -n "selected-wave|wave-panel|thread-container|view provider|placeholder" j2cl/src/main/java
```

Expected:
- one concrete container/view seam is identified as the `#967` integration point
- there is no need to invent a second read-surface architecture in this slice

### Task 2: Extend the J2CL open contract for initial viewport hints

**Files:**
- Modify:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarOpenRequest.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- Create:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarViewportHints.java`
- Test:
  - `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
  - `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchGatewayAuthFrameTest.java`

- [ ] Add a typed viewport-hints value object and thread it through the selected-wave open path
- [ ] Extend `SidecarOpenRequest` to carry optional `startBlipId`, `direction`, and `limit`
- [ ] Extend `encodeOpenEnvelope(...)` to emit the viewport numeric fields only when present
- [ ] Keep the message type `ProtocolOpenRequest`; do not reintroduce any sidecar auth frame
- [ ] Update the selected-wave controller/gateway seam so the initial open call accepts viewport hints derived from merged `#965/#966` state

Run:
```bash
sbt -batch "testOnly org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest org.waveprotocol.box.j2cl.search.J2clSearchGatewayAuthFrameTest"
```

Expected:
- open-envelope tests assert the numeric hint fields are present when supplied
- auth-frame tests still prove the first outbound frame is `ProtocolOpenRequest`

### Task 3: Preserve fragment-window structure in the J2CL model instead of flattening it away

**Files:**
- Modify:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- Create:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewportState.java`
- Test:
  - `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`

- [ ] Replace `List<String> contentEntries` as the primary rendering model with a typed window model that preserves:
  - segment id
  - range
  - loaded vs placeholder state
  - ordering
- [ ] Keep enough compatibility in the projector/model for existing status/read/write-session assertions to keep passing
- [ ] Teach the view to render loaded entries and placeholders without losing the eventual `#966` DOM/view-provider seam
- [ ] Preserve fragment metadata even when snapshot documents are absent

Run:
```bash
sbt -batch "testOnly org.waveprotocol.box.j2cl.search.J2clSelectedWaveProjectorTest"
```

Expected:
- projector tests prove fragment ranges survive projection
- projector no longer depends on flattening raw snapshots into standalone `<pre>` blocks

### Task 4: Add J2CL fragment-growth fetches and align server clamp policy

**Files:**
- Modify:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
  - `wave/src/main/java/org/waveprotocol/box/server/rpc/FragmentsServlet.java`
  - `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java`
- Create:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarFragmentsResponse.java`
  - `wave/src/main/java/org/waveprotocol/box/server/frontend/ViewportLimitPolicy.java`
- Test:
  - `wave/src/test/java/org/waveprotocol/box/server/frontend/WaveClientRpcViewportHintsTest.java`
  - `wave/src/test/java/org/waveprotocol/box/server/rpc/FragmentsServletViewportLimitTest.java`
  - new J2CL decoder test for `/fragments` response

- [ ] Add a J2CL `/fragments` fetch method that decodes the existing servlet JSON shape into typed window data
- [ ] Keep the selected-wave websocket subscription open for live updates; do not close/reopen the wave just to extend the viewport
- [ ] Extract shared clamp/default behavior so the initial-open path and `/fragments` path honour the same limits
- [ ] Make the extension request carry the current anchor/direction/limit plus version bounds

Run:
```bash
sbt -batch "testOnly org.waveprotocol.box.server.frontend.WaveClientRpcViewportHintsTest org.waveprotocol.box.server.rpc.FragmentsServletViewportLimitTest"
sbt -batch "testOnly org.waveprotocol.box.j2cl.transport.SidecarFragmentsResponseTest"
```

Expected:
- the same configured limits are applied in both paths
- J2CL can decode and merge `/fragments` growth windows without using GWT-only code

### Task 5: Wire scroll growth into the merged read-surface container

**Files:**
- Modify:
  - the concrete merged selected-wave/read-surface controller/view files from `#966`
  - if `#966` retains the current seam names, expect:
    - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
    - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
    - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelView.java`
  - the concrete merged root-shell host seam from `#965`
- Test:
  - `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`

- [ ] Add scroll-edge callbacks from the merged read-surface container to the selected-wave controller
- [ ] Request growth only when the user approaches an unloaded edge; coalesce repeated scroll triggers
- [ ] Preserve scroll anchor and focus when new windows merge in
- [ ] Surface loading placeholders with the existing `visible-region-placeholder` visual vocabulary
- [ ] Keep the container shape compatible with the `#966` DOM-as-view provider path

Run:
```bash
sbt -batch "testOnly org.waveprotocol.box.j2cl.search.J2clSelectedWaveControllerTest"
```

Expected:
- controller tests cover:
  - initial open with viewport hints
  - extension request scheduling
  - duplicate-request suppression
  - stale-response drop behavior

### Task 6: Remove whole-wave bootstrap when viewport mode is active

**Files:**
- Modify:
  - `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java`
  - the concrete merged selected-wave/root-shell server-first seam from `#965` if that merge owns the final selected-wave bootstrap markup or metadata boundary
- Test:
  - `wave/src/test/java/org/waveprotocol/box/server/frontend/WaveClientRpcViewportHintsTest.java`
  - existing fragments/open tests that inspect the initial `ProtocolWaveletUpdate`

- [ ] Gate initial selected-wave snapshot emission when viewport hints are active and fragments are available
- [ ] Preserve the fields the J2CL selected-wave path still needs on initial open:
  - wavelet name
  - channel id
  - resulting version / hash
  - fragments payload
- [ ] Add a regression assertion that a viewport-hinted selected-wave open no longer includes a whole-wave snapshot payload
- [ ] Record a warning/metric only when the server truly has to fall back to full snapshot bootstrap

Run:
```bash
sbt -batch "testOnly org.waveprotocol.box.server.frontend.WaveClientRpcViewportHintsTest org.waveprotocol.box.server.frontend.WaveClientRpcFragmentsTest"
```

Expected:
- viewport-hinted initial open is fragment-window-first on the wire
- tests prove `R-7.4` instead of only rendering-level virtualization

### Task 7: Browser verification and issue evidence

**Files:**
- Update issue comment(s)
- Add journal verification note if the lane policy requires one

- [ ] Boot the rebased `#965/#966/#967` stack on a local port
- [ ] Open a large-wave path in the J2CL root shell
- [ ] Confirm:
  - initial render shows only the visible fragment window
  - scrolling grows the window
  - focus and scroll anchor stay stable
  - network / logs show fragment-window fetches instead of a whole-wave bootstrap

Run:
```bash
bash scripts/worktree-boot.sh --port 9967
PORT=9967 bash scripts/wave-smoke.sh start
PORT=9967 bash scripts/wave-smoke.sh check
PORT=9967 bash scripts/wave-smoke.sh stop
```

Plus browser verification on:
```text
http://localhost:9967/?view=j2cl-root
```

Expected:
- the issue log can cite the exact large-wave route used, the observed initial window size, and at least one successful extension fetch

## 8. Review Plan

Before implementation starts, the worker should be able to answer these review questions directly from the code and tests:

- Where does the initial viewport hint source come from after `#965` and `#966` merge?
- Does the J2CL open envelope match the existing GWT/server field contract exactly?
- Does the J2CL model still flatten fragment data anywhere after decode?
- What exact path performs scroll-growth fetches?
- Do initial open and `/fragments` growth share the same clamp/default logic?
- Is whole-wave snapshot bootstrap actually gone when viewport mode is active, or did the change only virtualize rendering?

## 9. Ready-To-Start Call

- `#967` is **not** ready for implementation on the current checkout.
- `#967` becomes implementation-ready only after:
  - `#965` merges
  - `#966` merges
  - this worktree rebases onto the merged `origin/main`
  - Task 1 confirms the actual merged read-surface and server-first seams

At that point the implementation should start with Task 2, not with view work first. The transport and server no-whole-wave-bootstrap seam are the hard constraints; if those do not land first, the container work will only paper over a transport regression.
