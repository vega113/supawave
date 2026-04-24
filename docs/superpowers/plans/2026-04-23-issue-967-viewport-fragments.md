# Issue #967 Viewport-Scoped Fragment Windows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drive the J2CL/Lit selected-wave read surface from explicit viewport-scoped fragment windows instead of the current coarse whole-wave selected-wave payload assumptions, while preserving the existing server clamp contract and consuming the merged StageOne/read-surface container from `#966`.

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
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java:44-65,231-238`
  - `SidecarOpenRequest` only contains participant id, wave id, and wavelet prefixes; `encodeOpenEnvelope(...)` only writes numeric keys `1`, `2`, and `3`; `buildSelectedWaveOpenFrame(...)` hardcodes the minimal request.
- J2CL preserves fragment payloads only long enough to project blip text, not a window model:
  - decode seam: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java:86-149`
  - projection seam: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java:47-60,268-340`
  - model seam: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java:21-24,69-115,290-295`
  - view seam: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:28-40,104-132,152-174`
  - read-surface seam: `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java:24-64,101-154,204-229`
  - the transport decoder already preserves ranges and fragment entries, and `#966` now projects `J2clReadBlip` instances into the StageOne read surface. The model still lacks a typed viewport/window state, and the read-surface renderer currently receives only a complete list of loaded blips plus fallback strings. That still discards:
    - segment identity
    - loaded vs unloaded window boundaries
    - placeholder positions
    - enough structure to implement `R-3.6` DOM-as-view compatibility
- J2CL has no fragment-growth transport seam:
  - there is no `fetchFragments(...)` equivalent anywhere under `j2cl/src/main/java`
  - a repository-wide search only finds viewport-aware logic in the GWT client and server path, not in J2CL
- The root-shell/search-selected-wave host seam is now server-first, but `#967` must not bypass it:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clServerFirstRootShellDom.java:5-61`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:28-40,104-145`
  - `#965/#966` have landed the preserved server-first selected-wave card and the enhanced read-surface renderer. `#967` must derive its initial viewport hint from that preserved DOM when present and attach scroll growth to the merged renderer/container rather than creating a second selected-wave surface.

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
- Snapshot suppression is request-scoped: only `ProtocolOpenRequest`s with at least one viewport hint field (`viewportStartBlipId`, `viewportDirection`, or `viewportLimit`) may skip the whole-wave snapshot. No-hint GWT/legacy opens must keep their current snapshot behavior.
- The implementation records/uses the existing parity telemetry shape:
  - `FragmentsMetrics.j2clViewportInitialWindows`
  - `FragmentsMetrics.j2clViewportClampApplied`
  - `FragmentsMetrics.j2clViewportExtensionRequests`
  - `FragmentsMetrics.j2clViewportExtensionOk`
  - `FragmentsMetrics.j2clViewportExtensionErrors`
  - `FragmentsMetrics.j2clViewportSnapshotFallbacks`
  - warning log marker `J2CL_VIEWPORT_FULL_SNAPSHOT_FALLBACK` if a viewport-hinted open still has to send a whole-wave snapshot
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
- `#965` server-first selected-wave HTML / shell-swap seam: merged via PR `#989`
- `#931` unread/read selected-wave state: merged
- `#933` HttpOnly-safe sidecar websocket auth: merged
- `#936` selected-wave version/hash atomicity: merged

### 4.2 Dependency merge status

As of the current issue state on 2026-04-24:

- `#965` has merged and this plan branch has been rebased onto an `origin/main` containing it
- `#966` has merged via PR `#991` and this plan branch has been rebased onto `origin/main` at `867be56ee6dd6999e92a06d6517c72745c0a3243`

That means `#967` is implementation-ready after this Task 1 seam-audit update is reviewed and pushed.

### 4.3 Why `#965` was a hard blocker

`#965` owns the preserved server-first selected-wave HTML seam. That dependency is now satisfied, but Task 1 must still re-audit the exact merged files before implementation because `#967` needs to seed viewport state from the real preserved selected-wave surface, not from the pre-`#965` placeholder assumptions.

### 4.4 Why `#966` was a hard blocker

`#966` owns the practical StageOne read-surface container and DOM/provider contract. `#967` only makes sense once the read surface is no longer a flat `<pre>` list. This slice should wire viewport windows into the merged `#966` container rather than grow the current placeholder sidecar view into a second competing read-surface architecture.

### 4.5 Completed rebase and seam-audit gate

Before any code task below, following the `#966` merge:

- [x] Run `git fetch origin && git rebase origin/main`
- [x] Confirm `origin/main` includes the merged implementations for `#965` and `#966`
- [x] Refresh the file:line citations in §1 after the rebase so review evidence points at the merged code, not the planning snapshot
- [x] Re-audit the actual merged file seams and update this plan in-branch if the merged file names differ from the current `J2clSelectedWave*` seams
- [x] If `#966` changed the selected-wave/read-surface seam enough that this task list no longer maps cleanly, stop implementation, amend this plan, and rerun the plan-review loop before code work

Task 1 audit result:

- `#966` kept the expected `J2clSelectedWave*` seams and added the real read-surface container at `J2clSelectedWaveView.contentList` / `J2clReadSurfaceDomRenderer`.
- Verified container citation: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:88-91` creates `.sidecar-selected-content` and constructs `J2clReadSurfaceDomRenderer`; `J2clSelectedWaveView.java:28-40` reuses the same selector when preserving server-first DOM.
- The implementation can start with Task 2. Task 5 must wire scroll growth into `J2clReadSurfaceDomRenderer` through the `J2clSelectedWaveView`/`J2clSelectedWaveController` seam, not through a parallel DOM surface.
- The initial viewport hint source is the preserved server-first card's first visible `[data-blip-id]` inside `J2clSelectedWaveView.contentList` when present. If no preserved DOM exists, the selected-wave open should send explicit `viewportLimit=0` only; Task 2 must prove this stays field-present through the JSON/protobuf boundary.

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

- initial open increments `FragmentsMetrics.j2clViewportInitialWindows` and records requested/effective viewport limit in a sampled log line
- clamp changes increment `FragmentsMetrics.j2clViewportClampApplied`
- extension fetches increment `FragmentsMetrics.j2clViewportExtensionRequests`, `j2clViewportExtensionOk`, or `j2clViewportExtensionErrors`
- a warning metric/log fires if the selected-wave open path still receives a full snapshot while viewport mode is active: `FragmentsMetrics.j2clViewportSnapshotFallbacks` plus `J2CL_VIEWPORT_FULL_SNAPSHOT_FALLBACK`

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
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
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

- [x] Rebase the worktree after `#966` merges
- [x] Refresh §1 file:line citations after rebase so every cited seam points at the merged `origin/main`
- [x] If the merged `#966` read-surface seam does not expose a stable container for viewport windows, stop and amend this plan before implementation
- [x] Rewrite Task 5's file list and integration assertions to name the actual merged `#966` container/controller API before Task 5 starts; do not leave the container attachment test as a generic prose assertion

Run:
```bash
git fetch origin
git rebase origin/main
git log --oneline --max-count=8
```

Expected:
- the branch includes the merged commits for `#965` and `#966`
- the current selected-wave/root-shell file names still match this plan, or this plan is amended before code starts

- [x] Confirm the merged `#965` seam that exposes the initial selected-wave window source

Run:
```bash
rg -n "selected-wave|fragment|visible|data-" j2cl/src/main/java wave/src/jakarta-overrides/java
```

Expected:
- one concrete merged seam exists for the initial visible-window seed:
  - preserved server DOM marker from `#965`, or
  - explicit bootstrap extension from upstream merged work

- [x] Confirm the merged `#966` read-surface container seam

Run:
```bash
rg -n "selected-wave|wave-panel|thread-container|view provider|placeholder" j2cl/src/main/java
```

Expected:
- one concrete container/view seam is identified as the `#967` integration point
- there is no need to invent a second read-surface architecture in this slice

- [x] Confirm the initial viewport hint source using this ordered rule:
  - first choice: the preserved server-first `#966` read surface's first visible `[data-blip-id]`, sent as `viewportStartBlipId` with `viewportDirection="forward"`
  - fallback: when a selected wave is opened without preserved DOM, send `viewportLimit=0` only, using the existing server rule where an explicit non-positive limit means "use configured default" and also opts into viewport mode
  - legacy/no-selected-wave path: send no viewport hint fields, preserving current whole-snapshot behavior
- [x] Confirm `ProtocolOpenRequest` field-presence source semantics before coding:
  - server-side viewport detection must use `hasViewportLimit()` / field presence, not `viewportLimit > 0`
  - `wave/src/proto/proto/org/waveprotocol/box/common/comms/waveclient-rpc.proto:90-95` declares `viewport_limit` as an `optional int32`, so source-level presence semantics exist
  - Task 2 must still prove the runtime JSON/protobuf path preserves explicit zero with a boundary test; if it cannot, introduce an explicit sentinel field/value before implementing snapshot suppression

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
- [ ] Update the selected-wave controller/gateway seam so the initial open call accepts viewport hints derived from the Task 1 source decision:
  - preserved server DOM first visible `data-blip-id` -> `startBlipId`, `direction="forward"`, no explicit limit
  - no preserved DOM but selected wave present -> explicit `limit=0` and no `startBlipId`/`direction`
  - no selected wave / legacy call path -> no viewport fields
- [ ] Add a narrow `J2clSelectedWaveController.View` query seam, or an equivalent injected source, so `J2clSelectedWaveView` can expose initial viewport hints without the controller depending on DOM classes directly
- [ ] Add backwards-compatibility tests proving absent hints are not encoded and still produce the existing open envelope
- [ ] Add a codec/server-boundary test proving a request carrying only `viewportLimit=0` is encoded/decoded as viewport-hinted via field presence

Run:
```bash
sbt -batch "testOnly org.waveprotocol.box.j2cl.transport.SidecarTransportCodecTest org.waveprotocol.box.j2cl.search.J2clSearchGatewayAuthFrameTest"
```

Expected:
- open-envelope tests assert the numeric hint fields are present when supplied
- open-envelope tests assert no-hint requests omit the viewport fields entirely
- open-envelope tests assert `viewportLimit=0` is still present on the wire and detectable by `hasViewportLimit()`
- auth-frame tests still prove the first outbound frame is `ProtocolOpenRequest`

### Task 3: Preserve fragment-window structure in the J2CL model instead of flattening it away

**Files:**
- Modify:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
  - `j2cl/src/main/webapp/assets/sidecar.css`
- Create:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewportState.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadWindowEntry.java`
- Test:
  - `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`
  - `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`

- [x] Replace `List<String> contentEntries` as the primary rendering model with a typed window model that preserves:
  - segment id
  - range
  - loaded vs placeholder state
  - ordering
- [x] Keep enough compatibility in the projector/model for existing status/read/write-session assertions to keep passing
- [x] Teach the view to render loaded entries and placeholders without losing the eventual `#966` DOM/view-provider seam
- [x] Preserve fragment metadata even when snapshot documents are absent
- [x] Keep compatibility coverage for existing selected-wave tests most likely to churn:
  - `J2clSelectedWaveProjectorTest`
  - `J2clSelectedWaveViewServerFirstLogicTest`
  - `J2clReadSurfaceDomRendererTest`
  - any existing selected-wave model/view tests added by `#966`
- [x] Preserve `#936` version/hash atomicity when a live selected-wave update races a `/fragments` growth response; stale growth windows must be dropped or re-anchored based on version/hash

Task 3 result:
- Added `J2clSelectedWaveViewportState` with ordered segment/range entries, loaded/placeholder state, raw snapshots, operation counts, and snapshot/start/end versions.
- Kept legacy `contentEntries` and `readBlips` as compatibility projections while moving the primary selected-wave read model to typed viewport state.
- Added `J2clReadWindowEntry` plus read-surface rendering for loaded viewport blips and non-focusable placeholders, with stable no-op rerenders and safe transitions back to the classic renderer.
- Document-only updates merge into prior viewport state, same-update documents append missing segments or fill unloaded placeholders, and loaded fragment snapshots continue to win over same-update document fallbacks.
- No `/fragments` growth transport was added in Task 3; the state now preserves the version fields needed for Task 4/5 stale-growth handling, while the existing #936 write-session version/hash coupling remains covered by the selected-wave projector tests.

Run:
```bash
sbt -batch j2clSearchBuild j2clSearchTest
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

- [x] Add a J2CL `/fragments` fetch method that decodes the existing servlet JSON shape into typed window data
- [x] Keep the selected-wave websocket subscription open for live updates; do not close/reopen the wave just to extend the viewport
- [x] Extract shared clamp/default behavior so the initial-open path and `/fragments` path honour the same limits
  - existing `wave.fragments.defaultViewportLimit` and `wave.fragments.maxViewportLimit` config keys are the source of truth
  - do not introduce new config keys in this slice
  - replace `FragmentsServlet`'s hardcoded `50/200` only with the same effective defaults already used by `WaveClientRpcImpl`
  - add a regression note/test for the GWT `DynamicRendererImpl`/`/fragments` path so the shared servlet behavior change is explicit for non-J2CL callers
  - add an explicit baseline/default test proving the servlet uses the shared policy and does not drift back to an HTTP-only `50/200` clamp
- [x] Make the extension request carry the current anchor/direction/limit plus version bounds
- [x] Add concrete telemetry counters to `FragmentsMetrics`:
  - `j2clViewportInitialWindows`
  - `j2clViewportClampApplied`
  - `j2clViewportExtensionRequests`
  - `j2clViewportExtensionOk`
  - `j2clViewportExtensionErrors`
  - `j2clViewportSnapshotFallbacks`

Task 4 result:
- Added `ViewportLimitPolicy` and routed both `WaveClientRpcImpl` initial-open limit resolution and `FragmentsServlet` `/fragments` limit resolution through the same default/max policy (`5/50` unless startup config overrides it).
- Replaced the servlet's legacy `50/200` hardcoded clamp with the shared policy. This intentionally means legacy non-J2CL `/fragments` callers now use the same effective max (`50`) as initial open instead of the old HTTP-only max (`200`); the changelog fragment calls out this bounded-window change.
- Added `J2clSearchGateway.fetchFragments(...)` plus a deterministic `/fragments` URL builder that carries `waveId`, root `waveletId`, `client=j2cl`, anchor, direction, limit, and version bounds without closing or reopening the selected-wave websocket.
- Added `SidecarFragmentsResponse` to decode the existing servlet JSON shape into `SidecarSelectedWaveFragments`, with deterministic failures for non-`ok` status, missing `version`, and malformed range entries. Operation bodies remain intentionally deferred to Task 5; this slice preserves operation counts only.
- Added the required `FragmentsMetrics.j2clViewport*` counters. The extension and clamp metrics are scoped to J2CL-marked HTTP growth fetches (`client=j2cl`) so legacy `/fragments` callers are not counted as J2CL; the initial-window and snapshot-fallback counters are declared for the later Task 5/6 seams that can identify and gate those events correctly.
- Review note: Claude Opus 4.7 round 2b passed with no blockers. It flagged the legacy `/fragments` max reduction and the deferred initial-window metric as notes to document, not code blockers.

Run:
```bash
sbt -batch "testOnly org.waveprotocol.box.server.frontend.WaveClientRpcViewportHintsTest org.waveprotocol.box.server.rpc.FragmentsServletViewportLimitTest"
sbt -batch "testOnly org.waveprotocol.box.j2cl.transport.SidecarFragmentsResponseTest"
```

Expected:
- the same configured limits are applied in both paths
- J2CL can decode and merge `/fragments` growth windows without using GWT-only code
- non-J2CL `/fragments` callers use the same configured default/max policy as initial open; the former HTTP-only `50/200` clamp is intentionally removed and documented
- the FragmentsServlet baseline/default test prevents accidental divergence between GWT, J2CL, and other `/fragments` callers

### Task 5: Wire scroll growth into the merged read-surface container

**Files:**
- Modify:
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewportState.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
  - `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clServerFirstRootShellDom.java` only if the initial hint source needs another server-first marker helper
- Test:
  - `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`

- [x] Add scroll-edge callbacks from `J2clReadSurfaceDomRenderer` / `J2clSelectedWaveView.contentList` to the selected-wave controller
- [x] Request growth only when the user approaches an unloaded edge; coalesce repeated scroll triggers
- [x] Preserve scroll anchor and focus when new windows merge in
- [x] Surface loading placeholders with the existing `visible-region-placeholder` visual vocabulary
- [x] Keep the container shape compatible with the `#966` DOM-as-view provider path
- [x] Define `/fragments` growth failure behavior:
  - timeout/error keeps the existing window visible
  - edge placeholder switches to retry/error state without clearing loaded blips
  - duplicate requests for the same edge are coalesced while one is in flight
  - stale responses whose version/hash no longer matches the selected-wave state are dropped
  - a later scroll or retry can request the edge again
- [x] Add tests for timeout/error display, stale-response drop, duplicate-request suppression, and retry after failure
- [x] Add a direct assertion that the implementation attaches to the merged `#966` `J2clReadSurfaceDomRenderer` / `.sidecar-selected-content` container rather than introducing a second competing selected-wave surface

Task 5 result:
- Added scroll-edge callback plumbing from `J2clReadSurfaceDomRenderer` through `J2clSelectedWaveView` into `J2clSelectedWaveController`, using the existing `.sidecar-selected-content` / `#966` read-surface container instead of creating a parallel selected-wave surface.
- Added `J2clViewportGrowthDirection` and routed renderer, controller, viewport-state, view, and gateway direction handling through the shared forward/backward normalizer.
- Added keyed in-flight fragment-growth coalescing by `direction:anchor`; both success and error callbacks retain the in-flight key through `view.render(...)` so synchronous same-edge render re-entry is coalesced, then clear it to allow later retry/growth.
- Merged successful `/fragments` responses into `J2clSelectedWaveViewportState`, using loaded blips only for null-anchor fallback. Stale growth responses are dropped when write-session version/hash changes; null-to-present write-session transitions remain accepted, while present-to-null is documented as stale.
- Added scroll-anchor preservation for full `renderWindow(...)` rebuilds, one-shot edge auto-triggering when the user remains near an unloaded edge, and wave/classic transitions that clear stale viewport scroll memory while flat no-op renders preserve it.
- Added soft failure behavior: failures keep loaded content visible, surface a non-blocking status, allow retry after render, and successful retry clears the fragment-growth banner with `"More selected-wave content loaded."`.
- Claude Opus 4.7 review loop cleared in round 9 with no blockers or important required comments. Non-blocking notes were limited to follow-up considerations such as future copy/a11y polish and no-op-path micro-optimizations.

Run:
```bash
sbt -batch j2clSearchBuild j2clSearchTest && git diff --check && python3 scripts/validate-changelog.py
```

Expected:
- controller tests cover:
  - initial open with viewport hints
  - extension request scheduling
  - duplicate-request suppression
  - stale-response drop behavior
  - failure placeholder/retry behavior
- renderer tests cover:
  - forward/backward edge callbacks on unloaded placeholders
  - flat no-op scroll-memory preservation
  - one-shot post-render edge auto-triggering
  - backward prepend scroll-anchor preservation

### Task 6: Remove whole-wave bootstrap when viewport mode is active

**Files:**
- Modify:
  - `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java`
  - the concrete merged selected-wave/root-shell server-first seam from `#965` if that merge owns the final selected-wave bootstrap markup or metadata boundary
- Test:
  - `wave/src/test/java/org/waveprotocol/box/server/frontend/WaveClientRpcViewportHintsTest.java`
  - existing fragments/open tests that inspect the initial `ProtocolWaveletUpdate`

- [x] Gate initial selected-wave snapshot emission when viewport hints are active and fragments are available
  - the gate must be conditional on request-level viewport hints (`viewportStartBlipId`, `viewportDirection`, or `viewportLimit`)
  - hint detection must use field presence (`hasViewportStartBlipId()`, `hasViewportDirection()`, `hasViewportLimit()`), not positive numeric values
  - no-hint open requests, including existing GWT/legacy callers, must continue receiving the current whole-wave snapshot payload
- [x] Preserve the fields the J2CL selected-wave path still needs on initial open:
  - wavelet name
  - channel id
  - resulting version / hash
  - fragments payload
- [x] Add a regression assertion that a viewport-hinted selected-wave open no longer includes a whole-wave snapshot payload
- [x] Add a regression assertion that a no-hint selected-wave open still includes the current whole-wave snapshot payload
- [x] Record a warning/metric only when the server truly has to fall back to full snapshot bootstrap
  - metric: `FragmentsMetrics.j2clViewportSnapshotFallbacks`
  - warning marker: `J2CL_VIEWPORT_FULL_SNAPSHOT_FALLBACK`
  - this counter is incremented server-side once per wavelet after attempted callback delivery; the client must not double-count it
- [x] Add a regression test for viewport-hinted open with fragments unavailable:
  - server falls back to the whole-wave snapshot
  - `FragmentsMetrics.j2clViewportSnapshotFallbacks` increments exactly once
  - one `J2CL_VIEWPORT_FULL_SNAPSHOT_FALLBACK` warning is emitted with an operator-facing reason

Run:
```bash
sbt -batch "testOnly org.waveprotocol.box.server.frontend.WaveClientRpcViewportHintsTest org.waveprotocol.box.server.frontend.WaveClientRpcFragmentsTest"
```

Expected:
- viewport-hinted initial open is fragment-window-first on the wire
- no-hint GWT/legacy open remains snapshot-compatible
- viewport-hinted no-fragments fallback is observable through the named metric/log marker
- tests prove `R-7.4` instead of only rendering-level virtualization

Task 6 result:

- `WaveClientRpcImpl` now defers full snapshot serialization until after the fragments decision and suppresses the full snapshot only for viewport-hinted opens that attach a non-empty fragments payload.
- Viewport initial-window metrics count any viewport-window delivery once per wavelet, including snapshot-less synthetic/dev fragments; snapshot fallback metrics count full-snapshot fallback once per wavelet after attempted callback delivery.
- `WaveClientRpcViewportHintsTest` covers success suppression, no-hint compatibility, snapshot fallback, snapshot-less no-fragments behavior, snapshot-less fragments metrics, repeated same-wavelet metric de-duplication, multi-wavelet metric counting, and fallback reason labels.
- Verification passed: `sbt -batch "testOnly org.waveprotocol.box.server.frontend.WaveClientRpcViewportHintsTest org.waveprotocol.box.server.frontend.WaveClientRpcFragmentsTest" && git diff --check && python3 scripts/validate-changelog.py`.
- Claude Opus 4.7 implementation review cleared in round 7 with no blockers, important concerns, required nits, or required coverage gaps. Final output: `/tmp/claude-review-967-task6-r7.out`.

### Task 7: Browser verification and issue evidence

**Files:**
- Update issue comment(s)
- Add journal verification note if the lane policy requires one

- [x] Boot the rebased `#965/#966/#967` stack on a local port
- [x] Verify the scripts in this section exist after rebase; if any are absent, substitute the repo's current worktree boot/smoke equivalent and record the substitution in the issue
- [x] Open a large-wave path in the J2CL root shell
- [x] Confirm:
  - initial render shows only the visible fragment window
  - scrolling grows the window
  - focus and scroll anchor stay stable
  - network / logs show fragment-window fetches instead of a whole-wave bootstrap
- [x] Capture issue evidence:
  - exact local route and selected wave id
  - requested/effective initial window size
  - browser network entry or log line for at least one `/fragments` extension fetch
  - server/client log evidence that no full-wave snapshot was sent for the viewport-hinted open
  - value of `FragmentsMetrics.j2clViewportSnapshotFallbacks` or the absence of `J2CL_VIEWPORT_FULL_SNAPSHOT_FALLBACK`
  - method used to create or identify the large-wave fixture so the verification is reproducible

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

Task 7 result:

- Fresh staged server booted on `127.0.0.1:9967`; boot log line at `2026-04-24T05:06:56.313561+03:00` confirmed `Configured WaveClientRpc fragments handler: enabled=true`.
- Smoke verification passed with `ROOT_STATUS=200`, `J2CL_ROOT_STATUS=200`, `SIDECAR_STATUS=200`, and `WEBCLIENT_STATUS=200`.
- Browser verification used the reproducible large-wave fixture created with `WAVE_PERF_BASE_URL=http://localhost:9967 sbt --batch 'GatlingTest / runMain org.waveprotocol.wave.perf.WaveDataSeeder 1 80'`: user `perfuser@local.net`, wave `local.net/w+perf0001`, 80 seeded blips.
- Exact route verified: `http://127.0.0.1:9967/?view=j2cl-root&q=in%3Ainbox&wave=local.net%2Fw%2Bperf0001`.
- Initial J2CL selected-wave render loaded only `b+perf0001b1` through `b+perf0001b5`, rendered one viewport placeholder, and used `.sidecar-selected-content` as the scroll surface with `overflow-y: auto`.
- Selected-wave websocket open included explicit viewport mode: `{"1":"perfuser@local.net","2":"local.net/w+perf0001","3":["conv+root"],"7":0}`.
- Selected-wave websocket update for `local.net/w+perf0001/~/conv+root` had `hasSnapshot=false`, `hasFragments=true`, `fragmentRangeCount=8`, and `fragmentEntryCount=6`, so the initial open did not send a full-wave bootstrap snapshot.
- Scroll growth requested `GET /fragments?waveId=local.net%2Fw%2Bperf0001&waveletId=local.net%2Fconv%2Broot&client=j2cl&direction=forward&limit=5&startVersion=81&endVersion=81&startBlipId=b%2Bperf0001b6`, returned HTTP 200, and expanded the rendered blips to `b+perf0001b1` through `b+perf0001b10` while keeping focus on `b+perf0001b1`.
- Server logs after the fresh restart emitted fragments for `local.net/w+perf0001/local.net/conv+root` with `ranges=8 snapshotVersion=81 endVersion=81` and had zero `J2CL_VIEWPORT_FULL_SNAPSHOT_FALLBACK` entries.
- Final verification after review follow-ups passed: `sbt -batch "testOnly org.waveprotocol.box.server.frontend.WaveClientRpcViewportHintsTest org.waveprotocol.box.server.rpc.FragmentsServletViewportLimitTest org.waveprotocol.box.server.ServerMainStructuredLoggingTest org.waveprotocol.box.server.frontend.WaveClientRpcFragmentsTest org.waveprotocol.box.server.frontend.ViewportLimitPolicyTest" j2clSearchBuild j2clSearchTest` (`35` targeted server tests plus J2CL build/tests), `sbt -batch Universal/stage`, `git diff --check`, and `python3 scripts/validate-changelog.py`.
- Claude Opus 4.7 implementation review cleared in round 7 with no remaining blockers, important concerns, minor nits, coverage gaps, UX/a11y concerns, or performance pitfalls. Final output: `/tmp/claude-review-967-task7-r7.out`.

## 8. Review Plan

Before implementation starts, the worker should be able to answer these review questions directly from the code and tests:

- Where does the initial viewport hint source come from after `#965` and `#966` merge?
- Does the J2CL open envelope match the existing GWT/server field contract exactly?
- Does the J2CL model still flatten fragment data anywhere after decode?
- What exact path performs scroll-growth fetches?
- Do initial open and `/fragments` growth share the same clamp/default logic?
- Is whole-wave snapshot bootstrap actually gone when viewport mode is active, or did the change only virtualize rendering?

## 9. Ready-To-Start Call

- `#967` is ready for implementation on this rebased checkout once this seam-audit plan update has passed the review loop.
- Implementation should start with Task 2, not with view work first. The transport and server no-whole-wave-bootstrap seam are the hard constraints; if those do not land first, the container work will only paper over a transport regression.
