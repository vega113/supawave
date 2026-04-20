# Migration Plan: Conversation Renderer (wiab.pro ➜ Apache Wave)

Owner: Migration Engineering
Last updated: 2025-09-27

Status note (2026-03-23)
- Historical implementation ledger for the renderer, quasi-deletion, and
  fragments import work.
- The canonical current state lives in `docs/current-state.md`; active work is
  tracked in GitHub Issues via the workflow documented in
  `docs/github-issues.md`.
- Core-smoke verification on this branch is green for
  `./gradlew -q :wave:compileJava` and `./gradlew -q :wave:smokeUi`, with
  `smokeUi` reporting `ROOT=200 ROOT_SHELL=present LANDING=200 HEALTH=200
  J2CL_ROOT=200 J2CL_ROOT_SHELL=present J2CL_INDEX=200 SIDECAR=200
  WEBCLIENT=404` and `UI smoke OK`.
- The current `./gradlew -q :wave:test` blocker is still
  `:wave:compileTestJava`, which fails with 24 legacy test errors centered on
  Jetty session API drift, javax/jakarta servlet mismatches, and stale
  `ServerMain.applyFragmentsConfig(...)` references.
- Wiab core smoke verification (2026-03-23) completed code-path analysis:
  - Dynamic renderer: MVP windowing fully wired in StageTwo; `dynamicRendering()`
    TODO stubs are targeted-navigation entrypoints, not core windowing.
  - Fragments HTTP: `FragmentsServlet` was missing from Jakarta `ServerMain`
    registration -- now fixed. Client `ClientFragmentRequester` issues GET to
    `/fragments`; server returns JSON with blip metadata, ranges, and raw
    fragment payloads. HTTP mode currently metrics-only (payload not consumed).
  - Quasi-deletion: fully wired client-side via `QuasiConversationViewAdapter`
    and `BlipViewDomImpl.setQuasiDeleted()`; CSS class + tooltip applied.
  - Snapshot gating caveat: `forceClientFragments=true` still delivers full
    snapshots on initial open; fragment windows are additive.
  - Browser variant sweep (devtools observation) not yet executed.

-------------------------------------------------------------------------------

## Delta Since Last Edit (2025-09-27)

- Dev defaults now use typed object-form overrides in `application.conf`. Local runs
  ship with `fragmentFetchMode="http"` (stream remains available via override),
  `enableDynamicRendering=true`, `enableFragmentsApplier=true`,
  `enableViewportStats=true`, and `enableQuasiDeletionUi=true`; the server keeps
  `server.fragments.transport="stream"` enabled so the client can upgrade to
  view-channel fetch once the flag flips.
- Flags (Task A): Added `enableDynamicRendering`, `enableQuasiDeletionUi`, and transport enum `fragmentFetchMode` (off|http|stream); server/gradle plumbing to pass flags via `-PclientFlags` and merge defaults.
- Quasi (Phase 2 / Task B): Implemented `QuasiConversationViewAdapter` and `QuasiDeletable`; StageTwo wiring behind `enableQuasiDeletionUi`.
- Deleted UI (Phase 3 / Task C): Added `BlipViewDomImpl#setQuasiDeleted(boolean)` and `.blip.deleted` styles; renderer marks quasi state before final removal.
- Viewport plumbing (Phase 4 / Task D): Implemented `ScreenController` + `ScreenControllerImpl`; added `DomScrollerImpl` with clamped, throttled scroll writes sharing `dynamicScrollThrottleMs` with renderer.
- Dynamic renderer (Phase 5 / Task E): Implemented MVP windowing with page-in/out, placeholders, robust DOM reads, and resource cleanup on page-out via `BlipResourceCleaner` + `BlipAsyncRegistry`.
- Resources: Added `Render.css` and loader; optimized placeholder toggling to avoid redundant DOM churn.
- Fragment fetch (Phase 6 / Task F): the client now sends wave/wavelet/anchor hints to `/fragments`, sharing the same requester for `stream` and `http` modes while retaining metrics (`requesterSends`, `requesterCoalesced`).
- Client HTTP requester currently treats 2xx responses as success without consuming
  payloads; fragment metadata is exposed via metrics only until the applier path
  is wired.
- Dynamic viewport fetch now builds a `FragmentRequester.RequestContext` (wave/wavelet, anchor, limit, `SegmentId` list). When `enableFragmentFetchViewChannel=true` the client can fetch fragments (starting with HTTP fallback and upgrading to `ViewChannel.fetchFragments` once the mux is ready).
- Setting `enableFragmentFetchForceLayer` forces the stream requester even when the cache is cold,
  logging `Dynamic fragments: force-layer override active…` to highlight the override.
- Client fragments applier: `RealRawFragmentsApplier` (coverage merge) wired behind `wave.fragments.applier.impl` when `client.flags.defaults.enableFragmentsApplier=true`.
- Observability: `/statusz?show=fragments` now includes requester metrics along with emission and applier counters.
- Security hardening: server-side redirect validation (SignOutServlet, DataApiOAuthServlet) and safe Content‑Disposition for downloads (AttachmentServlet) via new HttpSanitizers helpers. Unit tests cover sanitization and header construction.
- Stream/HTTP fragment fetch: dynamic renderer now surfaces visible blip ids, and the GWT requester issues `/fragments` calls using `waveId`, `waveletId`, `startBlipId`, and `limit`.

- 2025-09-18 investigation: when forcing `fragmentFetchMode="stream"` together with
  `forceClientFragments=true`, the server still emits full snapshots on initial
  open. The fragments payload is additive, so dynamic rendering sees all blips
  immediately; fetcher callbacks never trigger additional ranges. Clamp-only
  changes reduce client apply work but not payload size. Measurement plan
  captured in docs/blocks-adoption-plan.md (snapshot gating follow-up).

Status note (2026-03-18)
- The repository no longer lacks the renderer, quasi-deletion, or fragment
  scaffolding described below. `DynamicRendererImpl`, `ObservableDynamicRenderer`,
  `ScreenController`, `QuasiConversationViewAdapter`, and the fragment requester
  / applier path are already present in the active tree.
- Treat the phased narrative below as historical migration context and a detailed
  implementation log.
- Remaining gaps are narrower:
  - finish the public `dynamicRendering(...)` entrypoints,
  - decide and document the canonical fragment transport path,
  - verify the combined renderer + fragments + quasi-deletion path on the
    merged branch,
  - keep the deeper blocks / snapshot gating follow-up in sync with
    `docs/blocks-adoption-plan.md`.
- Use `docs/current-state.md` and the live GitHub Issues tracker for the
  current backlog.


This document outlines how to port wiab.pro’s Conversation Renderer improvements into Apache Wave (incubator-wave), with a focus on:

- Dynamic rendering of only the visible screen area (virtualized/viewport rendering)
- Highlighting of deleted blips (quasi-deletion UX)

It includes context, dependency analysis, phased tasks, test plans, and Definitions of Done (DoD) tailored for an AI agent implementing each task.

NOTE: wiab.pro is based on an older code layout (Ant/NetBeans). Apache Wave has Gradle and a diverged structure. Paths below reference concrete files observed in both codebases to anchor changes.

-------------------------------------------------------------------------------

## 1) High-Level Summary

Wiab.pro adds a viewport-aware “dynamic renderer” and a “quasi-deletion” model/UI:

- Dynamic rendering (client):
  - Interfaces and implementations under `org.waveprotocol.wave.client.wavepanel.render.*`:
    - `DynamicRenderer`, `DynamicRendererImpl`, `DynamicDomRenderer`
    - `RenderUtil`, `ElementRenderer`, `ElementDomRenderer`, measurements, scroller adapters
    - Integrates with an “undercurrent” `ScreenController` to track visible area and scroll speed
  - Only blips inside (or near) the visible window are rendered; others are represented by placeholders and (optionally) fetched/attached on demand

- Quasi-deletion (model + UI):
  - Model wrappers in `org.waveprotocol.wave.model.conversation.quasi.*` (e.g., `ObservableQuasiConversationView`, `ObservableQuasiConversation(Thread|Blip)`, `QuasiDeletable`)
  - DOM helpers and styling:
    - `DomUtil.setQuasiDeleted(...)` attribute and CSS class on blip nodes
    - `BlipViewDomImpl.setQuasiDeleted(...)` updates visuals (background/border/title), cascades to children where needed

The text below starts from the pre-import baseline that existed when the
migration began. The current repository already contains most of this
infrastructure; use the status note above for the live state.

-------------------------------------------------------------------------------

## 2) Key Code References (Observed)

Wiab.pro (this repo):

- Dynamic viewport/rendering
  - `src/org/waveprotocol/wave/client/wavepanel/render/DynamicRenderer.java`
  - `src/org/waveprotocol/wave/client/wavepanel/render/DynamicRendererImpl.java`
  - `src/org/waveprotocol/wave/client/wavepanel/render/DynamicDomRenderer.java`
  - `src/org/waveprotocol/wave/client/wavepanel/render/RenderUtil.java`
  - `src/org/waveprotocol/wave/client/wavepanel/render/DomScrollerImpl.java`
  - `src/org/waveprotocol/wave/client/render/undercurrent/ScreenController.java`
  - `src/org/waveprotocol/wave/client/render/undercurrent/ScreenControllerImpl.java`

- Quasi-deletion (model + UI)
  - Model: `src/org/waveprotocol/wave/model/conversation/quasi/*`
  - DOM util: `src/org/waveprotocol/wave/client/wavepanel/view/dom/DomUtil.java` (deleted attribute)
  - View: `src/org/waveprotocol/wave/client/wavepanel/view/dom/BlipViewDomImpl.java` (deleted styling)
  - Editor diff integration: `src/org/waveprotocol/wave/client/editor/content/DiffHighlightingFilter.java`
  - Dynamic renderer uses quasi: see `deletifyBlipAndChildren(...)` in `DynamicDomRenderer.java`

Apache Wave (incubator-wave):

- Current rendering
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/LiveConversationViewRenderer.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/UndercurrentShallowBlipRenderer.java`
  - No `DynamicRenderer*` or `ScreenController*`

- Stage contracts
  - `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java` (no ScreenController in interface)

-------------------------------------------------------------------------------

## 3) Dependencies and Risks

- Dynamic rendering in wiab.pro optionally coordinates with server “fragments” to upload blips into the visible area (`FragmentsRequest`, `FragmentRequester`). Apache Wave does not expose the same API. We must decouple the viewport engine from server fragment fetching (feature flag + fallback to fully-loaded conversations).
- Quasi-deletion is a model-layer addition (wrappers + events) and a DOM-layer styling change. Apache Wave currently removes blips on delete; to match wiab.pro UX we need a staging state (quasi) signaled in the model and rendered in UI.
- UI assets: CSS classes and GWT module entries (e.g., `Render.gwt.xml`, `Render.css`) must be included in the Gradle build and GWT compilation.
- Stage wiring differs. Wiab.pro: `StageThree` exposes `ScreenController`; Apache Wave does not. We need a non-breaking integration path guarded by flags.

-------------------------------------------------------------------------------

## 4) Migration Strategy (Phased)

We will migrate in phases to minimize breakage and isolate dependencies. Each task is self-contained with context, steps, tests, and DoD.

Phases overview:

1. Baseline & Build Prep
2. Quasi Model Layer (minimal viable)
3. Deleted Blip Highlighting (UI)
4. Screen Controller + Scroller Abstractions
5. Dynamic Renderer Core (no server fragments)
6. Optional: Server Fragment Fetch Integration
7. Rollout & Performance

-------------------------------------------------------------------------------

## Phase 1 — Baseline & Build Prep

Goal: Confirm current Apache Wave build, add feature flags and module placeholders to accept new code paths without changing default behavior.

- Context
  - Apache Wave Gradle project under `incubator-wave/wave/`
  - GWT modules compile client UI; ensure we can add new packages/resources

- Steps
  - Add feature flags (client side):
    - `ClientFlags` (or similar) booleans
      - `enableQuasiDeletionUi` (default false)
      - `enableDynamicRendering` (default false)
      - `fragmentFetchMode` (default "off")
  - Add empty shells/modules:
    - Create `org.waveprotocol.wave.client.render.undercurrent` package (interfaces only)
    - Create empty `Render.gwt.xml` with no side-effects; include in GWT module if necessary
  - Ensure Gradle picks up new sources/resources; run GWT compile

- Tests
  - Build-only: `./gradlew :wave:build` (or project’s standard build)
  - GWT compilation step passes; app runs with no behavior change

- DoD
  - Build green (CI or local)
  - Flags are visible in code with default=false and no code path change when disabled

-------------------------------------------------------------------------------

## Phase 2 — Quasi Model Layer (Minimal Viable)

Goal: Introduce a wrapper view with quasi-deletion semantics, without server protocol changes. When the app receives a blip/thread deletion, surface a “quasi” event before final removal, allowing UI to render a deleted state.

- Context
  - Wiab.pro model wrappers under `org.waveprotocol.wave.model.conversation.quasi.*`
    - `ObservableQuasiConversationView`, `ObservableQuasiConversation(Thread|Blip)`, `QuasiDeletable`
  - Apache Wave lacks these wrappers; current flow calls `onBlipDeleted` and removes the view

- Implementation Notes
  - Introduce `QuasiDeletable` interface and minimal `ObservableQuasiConversationView` wrapper that proxies `ObservableConversationView`, intercepts delete events, and emits `onBefore*QuasiRemoved` and `on*QuasiRemoved` callbacks before actual deletion completes.
  - When quasi deletion triggered, attach a synthetic `WaveletOperationContext` (if none is present) so UI can format a tooltip similar to wiab.pro (`DiffAnnotationHandler.formatOperationContext`).
  - Provide `ConversationNavigator` equivalent if missing (wiab.pro uses navigator APIs). Minimal navigator helpers may live in the renderer layer initially to avoid reshaping the model package too much.
  - Guard activation with `enableQuasiDeletionUi`.

- Steps
  1) Add `quasi/QuasiDeletable.java`, minimal `quasi/ObservableQuasiConversation(View|Thread|Blip)` interfaces mirroring wiab.pro (reduced to events used by UI)
  2) Implement a lightweight adapter: `QuasiConversationViewAdapter` that takes `ObservableConversationView`, emits quasi events on `onBlipDeleted/onThreadDeleted` before bubbling original removal
  3) Wire adapter in StageTwo provider where conversations are exposed to the UI, behind flag

- Tests
  - Unit: verify adapter emits quasi events before removal, and that original removal still occurs
  - Unit: verify `getQuasiDeletionContext()` non-null and stable
  - Integration (headless): simulate delete events and assert sequencing

- DoD
  - With `enableQuasiDeletionUi=true`, quasi callbacks fire in renderer; with flag off, behavior unchanged

- Status
  - Completed — 2025-09-01. Implemented `QuasiConversationViewAdapter` and `QuasiDeletable`; wired behind `enableQuasiDeletionUi` in StageTwo. UI consumers receive pre/post quasi-delete events with no baseline behavior change when the flag is off.

-------------------------------------------------------------------------------

## Phase 3 — Deleted Blip Highlighting (UI)

Goal: Visually mark blips as deleted (quasi) and prevent interaction, consistent with wiab.pro UX.

- Context (wiab.pro)
  - DOM util: `DomUtil.setQuasiDeleted(...)` sets a boolean `deleted` attribute
  - DOM view: `BlipViewDomImpl.setQuasiDeleted(title, isRowOwnerDeleted)` paints background/borders, sets margins, disables cursors, sets tooltip
  - CSS: `Blip.css` contains `.deleted` styles

- Steps
  1) Port minimal DOM helpers into Apache Wave:
     - `DomUtil` additions: boolean attribute helpers + `setQuasiDeleted(Element)`
  2) Extend Apache Wave’s `BlipViewDomImpl` (path: `wave/.../view/dom/BlipViewDomImpl.java`) with `setQuasiDeleted(...)` method akin to wiab.pro (use local CSS class and color token)
  3) Add CSS rules (`.deleted`) into existing blip stylesheet or a small new CSS included by the GWT module
  4) Update renderer to call `setQuasiDeleted` upon quasi events. If `LiveConversationViewRenderer` remains in use, handle in its listener hooks (before final removal). When dynamic renderer (Phase 5) is on, it will use the wiab.pro flow

- Tests
  - UI (GWT test): assert that a blip marked quasi has the `deleted` attribute and CSS class applied
  - Interaction smoke: context menu and editing actions are disabled on quasi blips (no JS exception)

- DoD
  - With `enableQuasiDeletionUi=true`, deleting a blip shows a deleted state momentarily (or until server confirms removal) with proper tooltip; no regressions when flag is off

- Status
  - Completed (initial) — 2025-09-01. Added `BlipViewDomImpl#setQuasiDeleted(boolean)` and `.blip.deleted` styles in `Blip.css`. Subscribed to quasi adapter in `StageTwo` to mark DOM just before deletion. Tooltip/context wiring deferred.

-------------------------------------------------------------------------------

## Phase 4 — Screen Controller + Scroller Abstractions

Goal: Add a stable abstraction for observing viewport changes, used by dynamic rendering to compute visible ranges.

- Context (wiab.pro)
  - `render/undercurrent/ScreenController{,Impl}.java` observes scroll, resize, and records scroll speed/direction; provides `onScreenChanged` events
  - `wavepanel/render/DomScrollerImpl.java` bridges to DOM scroll panel and exposes `getPosition()/setPosition()`

- Steps
  1) Add `ScreenController` interface (methods: listener registration, `getScrollPosition`, direction/speed getters, `setScrollPosition(int, boolean)`), with no implementation connected by default
  2) Port `ScreenControllerImpl` with minimal dependencies (uses `DomUtil.getMainElement()` and finds the scroll panel under `View.Type.SCROLL_PANEL`). If Apache Wave DOM structure differs, adapt the element query (guard with null checks)
  3) Add `DomScrollerImpl` implementing the existing `Scroller.Impl` (if present) or a small adapter used by the renderer
  4) Expose a `ScreenController` instance from `StageThree` when `enableDynamicRendering=true`; keep interface additions binary-compatible (e.g., via a provider/factory rather than breaking interface if necessary)

- Tests
  - Unit/lightweight GWT: simulate `scrollTop` updates and verify direction/speed calculations
  - Integration: registering a listener yields `onScreenChanged` callbacks when changing `scrollTop`

- DoD
  - `ScreenController` instantiated behind flag, does not change behavior when disabled

- Status
  - Completed (minimal) — 2025-09-01. Added `ScreenController` and `ScreenControllerImpl` to observe scroll/resize and notify listeners. Introduced `DomScrollerImpl` with clamped, throttled `setScrollTop` using the unified `dynamicScrollThrottleMs` knob.

-------------------------------------------------------------------------------

## Phase 5 — Dynamic Renderer Core (Client-only)

Goal: Port the core dynamic renderer (no server fragment fetch), render only visible blips plus a prerender buffer above/below.

- Context (wiab.pro)
  - `DynamicRenderer`, `DynamicRendererImpl`, `DynamicDomRenderer` compute visible intervals, maintain placeholder clusters, and perform incremental (task-based) updates
  - Uses `ScreenController` and DOM measurements (`DomUtil`, `ElementDomMeasurer`)
  - Integrates with profiles/supplement renderers (can be wired later; keep initial MVP small)

- Steps
  1) Port `DynamicRenderer` interface and a trimmed `DynamicRendererImpl` (remove fragment-request code paths; keep placeholders + rerender logic). Ensure compilation against Apache Wave’s view model types
  2) Port `DynamicDomRenderer` with guarded features:
     - Keep `deletifyBlipAndChildren(...)` (works with Phase 2–3 quasi events)
     - Integrate only `ShallowBlipRenderer`, not optional extras (profile/supplement) initially
     - Implement `isBlipVisible(...)` via `visibleScreenSize.contains(top|bottom)` with a prerender margin flag (`prerenderUpper/LowerPx`)
  3) Wire dynamic renderer activation in the stage wiring (StageTwo/StageThree provider): when `enableDynamicRendering=true`, install dynamic renderer instead of (or alongside) `LiveConversationViewRenderer`
  4) Ensure CSS/resources from `Render.gwt.xml` and `Render.css` are included in the GWT module (copy minimal set)

- Tests
  - Unit: `isBlipVisible(...)` for various layout values
  - Integration (GWT): create a synthetic thread with many blips; assert only a window’s worth of blips have DOM nodes; scrolling triggers re-rendering
  - Regression: with flag off, behavior identical to baseline

- DoD
  - With `enableDynamicRendering=true`, large threads render quickly and only within viewport bounds (plus buffer). No functional regressions observed in navigation/editing in basic flows

- Status
  - Completed (MVP) — 2025-09-01. Implemented `DynamicRenderer` with viewport windowing, page-in/out, and placeholder visuals; wired behind `enableDynamicRendering` in StageTwo. Added robustness (null-safe DOM reads), resource cleanup for paged-out blips, and unified scroll throttling.

-------------------------------------------------------------------------------

## Phase 6 — Optional Server Fragment Fetch Integration

Goal: If desired, integrate wiab.pro’s fragment-fetch flow to reduce payload/CPU by fetching only visible parts server-side.

- Context (wiab.pro)
  - Server: `box/server/frontend/*` introduces `FragmentsRequest`, `FragmentsFetcher`, and client responses that include map of `SegmentId ➜ RawFragment`
  - Client: `FragmentRequester` used by dynamic renderer to fetch missing content lazily

- Steps (optional; coordinated server+client change)
  1) Add client `FragmentRequester` with selection based on `fragmentFetchMode` (off|http|stream); no-op when `off`
  2) If server changes are approved, implement `FragmentsRequest`/fetcher on Apache Wave server compatible with existing APIs; expose endpoint to client
  3) Gate usage via `fragmentFetchMode`; when `off`, renderer remains client-only

- Tests
  - Contract test: fetch returns fragments for a requested range; merging is idempotent
  - End-to-end: scroll triggers fetch, DOM updates, and view remains responsive

- DoD
  - With `fragmentFetchMode=stream|http`, bandwidth/latency improves on large waves; with `off`, dynamic renderer still functions client-only

- Status
  - Implemented (stream + client surface) — 2025-09-11.
    - Server: `WaveClientRpcImpl` attaches `ProtocolFragments` when `server.enableFetchFragmentsRpc=true`.
    - Client: `RemoteWaveViewService` now surfaces `ProtocolFragments` as a `FragmentsPayload` to `ViewChannelImpl`, which forwards it to listeners and, when enabled, to a global applier.
    - New enum: `fragmentFetchMode` = `off|http|stream` (client). StageTwo picks:
      - `stream` → `ViewChannelFragmentRequester` (falls back to HTTP until mux ready) when `enableFragmentFetchViewChannel=true` or `enableFragmentFetchForceLayer=true`; otherwise no fetches are issued.
      - `http` → `ClientFragmentRequester` (`/fragments` endpoint) when `enableFragmentFetchViewChannel=true` (or force-layer); otherwise no fetches are issued.
      - `off` → no requester
    - Runtime logs highlight transitions: `Dynamic fragments: client fetch disabled; using NO_OP requester`, `Dynamic fragments: ViewChannel not ready, using HTTP fallback`, `Dynamic fragments: switching to ViewChannel fetch`, and `Dynamic fragments: force-layer override active…` when the override is in play.
    - Server transport: keep `server.fragments.transport = "stream"` enabled. Dev
      config currently pins `client.flags.defaults.fragmentFetchMode="http"` so
      runs stay on the HTTP fallback until the stream path is production ready.
      Override with `-PclientFlags="fragmentFetchMode=stream"` (or set the
      Typesafe default) when validating view-channel fetch behavior.
    - Current limitation: the HTTP requester treats 2xx responses as success and
      discards the body. Fragment payloads are therefore only exercised via
      view-channel fetch and metrics plumbing until the client-side parser and
      applier path are ported.

-------------------------------------------------------------------------------

## Phase 7 — Rollout & Performance

- Gradual rollout switches:
  - Stage 1: `enableQuasiDeletionUi=true` in canary
  - Stage 2: `enableDynamicRendering=true` for small cohorts
  - Stage 3: `fragmentFetchMode=stream` (if implemented)

- Metrics (optional):
  - First paint time for a large wave
  - Max heap during scroll
  - DOM node count while idle vs. during scroll

-------------------------------------------------------------------------------

## 5) Detailed Task Breakdowns

Each task below is self-contained for an AI agent, includes context, concrete steps, tests, and DoD.

### Task A — Add Feature Flags and Module Hooks

- Files (Apache Wave):
  - `wave/src/main/java/.../client/util/ClientFlags.java` (or similar central flags)
  - GWT module xml if needed

- Steps
  - Add booleans: `enableQuasiDeletionUi`, `enableDynamicRendering` (false by default)
  - Add enum: `fragmentFetchMode` ("off" by default)
  - Expose getters and integrate into stage providers (no behavior change yet)

- Tests
  - Build + runtime smoke: flags accessible; defaults false

- DoD
  - Code compiles; nothing changes visually/functionally until flags/enum are set

- Status
  - Completed — 2025-09-01. Added `enableQuasiDeletionUi`, `enableDynamicRendering` and later `fragmentFetchMode` (off|http|stream) to `FlagConstants` and `ClientFlagsBase`. Updated `__NAME_MAPPING__` so `WaveClientServlet` reflects defaults to the client. Verified compilation via `:wave:compileJava`.

---

### Task B — Quasi Model Adapter (MVP)

- Files (new in Apache Wave):
  - `wave/src/main/java/org/waveprotocol/wave/model/conversation/quasi/QuasiDeletable.java`
  - `.../quasi/ObservableQuasiConversation{View,Thread,Blip}.java` (interfaces)
  - `.../quasi/QuasiConversationViewAdapter.java` (wraps `ObservableConversationView`)

- Steps
  - Define interfaces mirroring needed callbacks: `onBeforeBlipQuasiRemoved`, `onBlipQuasiRemoved`, etc.
  - Implement adapter emitting quasi events just before dispatching existing delete events
  - Synthesize `WaveletOperationContext` when missing (store in adapter for tooltip formatting)
  - Integrate in stage wiring (behind `enableQuasiDeletionUi`), so renderers receive the quasi-capable view

- Tests
  - Unit: verify quasi events order vs. delete events
  - Ensure no NPEs when context is null

- DoD
  - Renderer can subscribe to quasi events; baseline unchanged when flag off

- Status
  - Completed — 2025-09-01. Added `quasi/QuasiConversationViewAdapter` (pre/post quasi-delete callbacks for blips/threads) and `QuasiDeletable` marker. Wired adapter creation in `StageTwo.install()` behind `ClientFlags.get().enableQuasiDeletionUi()`.

---

### Task C — Deleted Blip Styling and DOM Helpers

- Files (Apache Wave):
  - Update `.../view/dom/DomUtil.java` with boolean attribute helpers + `setQuasiDeleted`
  - Update `.../view/dom/BlipViewDomImpl.java` to add `setQuasiDeleted(String title, boolean rowOwnerDeleted)`
  - CSS: add `.deleted` styling (copy minimal from wiab.pro `Blip.css` or adapt existing theme)

- Steps
  - Add methods and wire into renderer where a blip is marked quasi
  - Disable interactions on quasi blips (cursor, menu color change)

- Tests
  - GWT/UI: DOM reflects the attribute/class; menu actions disabled

- DoD
  - Deleting a blip shows deleted highlight under the flag; no regressions otherwise

---

### Task D — ScreenController + DomScroller

- Files (Apache Wave):
  - `.../client/render/undercurrent/ScreenController.java`
  - `.../client/render/undercurrent/ScreenControllerImpl.java`
  - `.../client/wavepanel/render/DomScrollerImpl.java`

- Steps
  - Port interfaces and a safe implementation
  - Bind instance in StageThree provider when `enableDynamicRendering=true`

- Tests
  - Simulate scroll; verify `onScreenChanged` and direction/speed APIs

- DoD
  - Scroller signals viewport changes; not used elsewhere yet

- Status
  - Completed (minimal) — 2025-09-01. Added `render/undercurrent/ScreenController` and `ScreenControllerImpl` with window scroll/resize notifications and simple getters. Not yet exposed via stages; dynamic renderer will request/create as needed.
  - Addendum — 2025-09-01. Added minimal `DomScrollerImpl` and `RenderUtil` helpers for absolute measurements and class toggling.

---

### Task E — Dynamic Renderer (Core, no fragments)

- Files (Apache Wave):
  - `.../wavepanel/render/DynamicRenderer.java`
  - `.../wavepanel/render/DynamicRendererImpl.java` (pruned)
  - `.../wavepanel/render/DynamicDomRenderer.java`
  - Support: `RenderUtil.java`, element renderer/measurer minimal ports
  - Resources: `Render.gwt.xml`, `Render.css`

- Steps
  - Implement visible-interval computation using `ScreenController`
  - Render placeholders for offscreen regions; render real blips within [visibleTop - prerenderUpper, visibleBottom + prerenderLower]
  - Maintain readiness lookups: `isBlipReady`, `getElementByBlip`, `getBlipIdByElement`
  - Wire into StageTwo/Three to replace or augment `LiveConversationViewRenderer` when flag enabled

- Tests
  - Integration/GWT: long thread ➜ only a window’s worth of blips in DOM; scrolling updates DOM windows
  - Latency smoke: no long jank during scroll

- DoD
  - Feature usable behind flag; stable in basic navigation/editing flows

- Status
  - Completed — 2025-09-18. Dynamic renderer pages visible blips, applies prerender slack with flag tunables, emits badge stats, and integrates cleanup/placeholder hooks. GWT compile passes and the feature is guarded behind `enableDynamicRendering`.

- How to run with flags via Gradle
  - `./gradlew :wave:run -PclientFlags="enableDynamicRendering=true,enableQuasiDeletionUi=true,enableViewportStats=true,dynamicPrerenderUpperPx=600,dynamicPrerenderLowerPx=800,dynamicPageOutSlackPx=1200,dynamicScrollThrottleMs=50"`
  - Flags can also be toggled at request time by adding query parameters to the J2CL root-shell URL; the Gradle property sets server-wide defaults for convenience.

- Resource integration
  - Integrated `Render.css` (animations/placeholders) via `RenderCssLoader` and inject when dynamic rendering is enabled.
- Placeholder behavior: when dynamic rendering is on, `BlipPager` toggles `.placeholder` on page-in/out to show visual flaps; `DynamicDomRenderer.setPlaceholder(...)` is available for future explicit calls.
- Cleanup hook: `BlipPager` now exposes `setResourceCleaner(...)` invoked during page-out for deeper resource detachment (widgets, listeners). Future phase will implement a concrete cleaner.
  - ResourceCleaner implemented — 2025-09-01: `BlipResourceCleaner` orphans any widgets under the blip DOM in `LogicalPanel` and cancels timers registered via `BlipAsyncRegistry`. Hook attached when `enableDynamicRendering=true`.
- Robustness: extra null-safety in placeholder toggling and defensive DOM reads; `DomScrollerImpl` clamps scroll values. `FragmentRequester` now uses a callback for error handling.
  - Unified throttle: `DomScrollerImpl` now uses the same `dynamicScrollThrottleMs` knob as the dynamic renderer to coalesce scroll writes.
  - Tuning: speed-based prerender boost (flags: `dynamicSpeedBoostThresholdPx`, `dynamicSpeedBoostFactor`) temporarily enlarges prerender margins during fast scrolls to reduce visible pop-in.

---

### Task F — Optional Server Fragments

- Files (server + client):
  - Server: endpoints similar to wiab.pro (`FragmentsRequest`, fetcher, wiring into `ClientFrontend`)
  - Client: `FragmentRequester` and hooks in `DynamicRendererImpl`

- Steps
  - Add no-op client stub first; then server support; finally enable fetching behind flag

- Tests
  - Contract + e2e fetch

- DoD
  - When enabled, reduces initial load and keeps dynamic rendering responsive on very large waves

- Status
  - Completed — 2025-09-18. Client stream/http modes now share the same anchor-based requester, issuing `/fragments` queries with wave id, wavelet id, and start blip hints; the servlet resolves anchors and returns ranges.
  - Follow-up — 2025-09-18. Stream mode still emits full `WaveletSnapshot`s alongside fragments when `forceClientFragments` is true; snapshot gating remains deferred in docs/blocks-adoption-plan.md.

-------------------------------------------------------------------------------

## 6) Validation & Test Matrix

- Functional
  - Create/delete blips (single-level and threads)
  - Inline thread add/remove
  - Scroll through 1k blips wave and verify steady DOM count with dynamic rendering

- Visual
  - Deleted blips use the correct background/border; title shows deletion context
  - Read/unread indicators unaffected

- Performance
  - CPU usage during fast scroll acceptable; no GC thrash obvious
  - Memory footprint drops vs. full-render baseline on large waves

-------------------------------------------------------------------------------

## 6.1) End-to-End Test Plan (Detailed)

- Environments
  - Dev (local): run via `./gradlew :wave:run -PclientFlags="enableDynamicRendering=true,enableQuasiDeletionUi=true"`
- Dev (with fragments over HTTP): set `fragmentFetchMode=http` (requires `/fragments`)

- Smoke Tests
  - Open a wave with many blips (≥ 500). Verify initial render completes without freezing.
  - Scroll top→bottom→top rapidly. Observe no JS errors and steady responsiveness.

- Functional Tests
  - Quasi deletion: delete a blip with `enableQuasiDeletionUi=true`. Confirm deleted styling appears momentarily and then is removed when the blip is finally removed.
  - Placeholder behavior: while scrolling, confirm offscreen blips show `.placeholder` and are replaced with real content on page-in.
  - Resource cleanup: attach a gadget or widget under a blip (if available), scroll the blip far offscreen, and confirm it is orphaned on page-out (no lingering timers, exceptions).

- Performance Tests
  - DOM steady-state: use browser devtools to record Node count at rest with dynamic rendering on vs. baseline (off). Expect drop proportional to viewport window size.
  - CPU during scroll: record JS CPU (Performance tab) for a 5-second fast scroll. Expect minimal jank (few long tasks) with `dynamicScrollThrottleMs=30–50`.
  - Memory: compare heap snapshots idle vs. scrolled; ensure no growth trend after multiple scroll passes.

- Fragment Fetch (stub) Tests
- With `fragmentFetchMode=http` or `stream`, verify network calls to `/fragments` include `waveId`, `waveletId`, and `startBlipId` parameters and return 200 OK during scroll.
  - Confirm UI remains responsive even if `/fragments` returns non-200 (we log errors, no user-facing breakage).

- Regression Tests (flag-off)
  - With all new flags false, verify legacy behavior: no placeholders, all blips render as before; no new errors.

-------------------------------------------------------------------------------

## 6.2) Manual QA Checklist

- Rendering
  - [ ] Initial viewport renders quickly with large waves
  - [ ] Page-in only affects visible window + buffer
  - [ ] Page-out removes content and leaves placeholder

- Quasi
  - [ ] Deleted blips show proper styling before removal
  - [x] No interaction allowed with quasi blips

- Stability
  - [ ] No console errors during long scrolls
  - [ ] No runaway timers or retained widgets after page-out

- Flags
  - [x] Reference defaults remain off; dev `application.conf` overrides enable dynamic/quasi/applier by default
  - [ ] Each flag works independently (quasi only; dynamic only; fragments only)

-------------------------------------------------------------------------------

## 11) Remaining Work to Complete Implementation

- Quasi polish
  - Tooltip/context data on quasi deleted blips (source op context) — Completed (initial): show "Deleted by <author> at <time>" on blip root element; future enhancement is to pass true operation context if available.

- Dynamic renderer polish
  - Optional: expose `isBlipReady`/lookup helpers if needed by future features
  - Optional: adjust prerender/window margins per device profile

- Fragments (Phase 6)
  - Replace stub `/fragments` with real server logic: compute visible segments, return fragments, merge on client
  - Define contract and error semantics; add integration tests

- Observability
  - Optional metrics (DOM node count, scroll latency) gated by flags

-------------------------------------------------------------------------------

## 12) Flag Reference and Cleanup Plan

- Client flags (new)
  - `enableFragmentsApplier` (bool, default false; dev override true): enable lightweight fragment-window stats and optional client applier wiring.
  - `enableDynamicRendering` (bool, default false; dev override true): enable viewport windowing renderer.
  - `enableQuasiDeletionUi` (bool, default false; dev override true): enable quasi-deletion visual state.
  - `fragmentFetchMode` (enum: off|http|stream; default off; dev override stream): pick transport for fragments.
  - `enableFragmentFetchViewChannel` (bool, default false; dev override true): allow the renderer to invoke fragment fetchers (HTTP fallback first, then stream when ready).
  - `enableFragmentFetchForceLayer` (bool, default false; dev override false): force stream fetches without waiting for cache warm-up (testing only).
  - `dynamicPrerenderUpperPx` (int, default 600): prerender margin above viewport
  - `dynamicPrerenderLowerPx` (int, default 800): prerender margin below viewport
  - `dynamicPageOutSlackPx` (int, default 1200): offscreen slack before page-out
  - `dynamicScrollThrottleMs` (int, default 50): unified throttle window for scroll updates
  - `dynamicSpeedBoostThresholdPx` (int, default 800): scroll delta threshold to boost prerender margins
  - `dynamicSpeedBoostFactor` (double, default 1.8): multiplier for boosted prerender margins

- How to set flags during development
  - Gradle property: `-PclientFlags="k=v,k2=v2"` (applies to run/gwtDev/compileGwt tasks)
  - Request params: append `?flag=value` to the J2CL root-shell URL for ad hoc overrides
  - JVM property (dev only): `-Dwave.clientFlags="k=v,..."` (merged by WaveClientServlet)
  - Server config (preferred long-term): reference.conf with overrides in application.conf (merged by Typesafe Config) under `client.flags.defaults`

- Example reference.conf (defaults remain off; legacy CSV is discouraged)
  ```
  client.flags.defaults {
    enableFragmentsApplier = false
    fragmentFetchMode = "off"
    enableDynamicRendering = false
    enableQuasiDeletionUi = false
    enableFragmentFetchViewChannel = false
    enableFragmentFetchForceLayer = false
    quasiDeletionDwellMs = 400
  }
  ```

- Example application.conf (dev overrides merged automatically)
  ```
  client.flags.defaults {
    fragmentFetchMode = "stream"
    enableDynamicRendering = true
    enableFragmentsApplier = true
    enableFragmentFetchViewChannel = true
    enableFragmentFetchForceLayer = false
    enableViewportStats = true
    enableQuasiDeletionUi = true
    quasiDeletionDwellMs = 1000
  }
  ```

- Cleanup plan
  - Before GA: remove JVM `-Dwave.clientFlags` path, keep only config-based defaults and URL overrides
  - Consolidate tuning into sane defaults; remove `dynamicSpeedBoost*` if not needed
  - Downgrade verbose logs; ensure `enableViewportStats` gates all debug logging
  - Document final flag set in user/admin docs and deprecate experimental flags

-------------------------------------------------------------------------------

-------------------------------------------------------------------------------

## 7) Rollback Plan

- All features are flag-guarded. To rollback:
  - Disable `enableDynamicRendering` and/or `enableQuasiDeletionUi` in client flags
  - Remove server fragment endpoint from routing if Phase 6 deployed

-------------------------------------------------------------------------------

## 8) Notes on Divergences & Adapting Code

- File structure and package names differ; prefer small adapters and keep ports minimal
- If `ConversationNavigator` is absent in Apache Wave, inline the few traversal helpers needed by the dynamic renderer, then consider extracting a shared navigator later
- When porting CSS, prefer tokens/variables; avoid hard-coded colors if Apache Wave has a theme system

-------------------------------------------------------------------------------

## 9) Suggested Task Order (Short)

1) Task A (flags)
2) Task B (quasi adapter)
3) Task C (deleted UI)
4) Task D (screen controller)
5) Task E (dynamic renderer core)
6) Task F (optional server fragments)

Each task is independently mergeable and off-by-default.

Dev defaults and flags
- Local dev inherits overrides from `wave/config/application.conf`; review `client.flags.defaults` for the current set (dynamic renderer, fragments applier/fetch, viewport stats on).
- CLI overrides remain available: append `-PclientFlags="k=v,..."` for experiments. Values merge on top of config/system defaults.

-------------------------------------------------------------------------------

## 10) Appendix — Concrete Diffs to Study in wiab.pro

- Dynamic viewport: `DynamicRendererImpl` (visible intervals, placeholders)
- Quasi deletion UX: `DynamicDomRenderer#deletifyBlipAndChildren`, `BlipViewDomImpl#setQuasiDeleted`, `DomUtil#setQuasiDeleted`
- Scroll tracking: `ScreenControllerImpl` (direction/speed heuristics)

These provide runnable reference logic for the Apache Wave port, with server-fragments code paths skipped initially.
