# Migration Plan: Conversation Renderer (wiab.pro ➜ Apache Wave)

Owner: Migration Engineering
Last updated: 2025-08-31

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

Apache Wave (incubator-wave) currently lacks these modules. It uses `LiveConversationViewRenderer` (paging in/out) without virtualized DOM and does not expose a quasi-deletion UI.

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
      - `enableFragmentFetch` (default false)
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

-------------------------------------------------------------------------------

## Phase 6 — Optional Server Fragment Fetch Integration

Goal: If desired, integrate wiab.pro’s fragment-fetch flow to reduce payload/CPU by fetching only visible parts server-side.

- Context (wiab.pro)
  - Server: `box/server/frontend/*` introduces `FragmentsRequest`, `FragmentsFetcher`, and client responses that include map of `SegmentId ➜ RawFragment`
  - Client: `FragmentRequester` used by dynamic renderer to fetch missing content lazily

- Steps (optional; coordinated server+client change)
  1) Add client `FragmentRequester` with no-op stub when `enableFragmentFetch=false`
  2) If server changes are approved, implement `FragmentsRequest`/fetcher on Apache Wave server compatible with existing APIs; expose endpoint to client
  3) Gate usage behind `enableFragmentFetch`, ensure fallback to fully loaded waves

- Tests
  - Contract test: fetch returns fragments for a requested range; merging is idempotent
  - End-to-end: scroll triggers fetch, DOM updates, and view remains responsive

- DoD
  - With `enableFragmentFetch=true`, bandwidth/latency improves on large waves; with flag off, dynamic renderer still functions client-only

-------------------------------------------------------------------------------

## Phase 7 — Rollout & Performance

- Gradual rollout switches:
  - Stage 1: `enableQuasiDeletionUi=true` in canary
  - Stage 2: `enableDynamicRendering=true` for small cohorts
  - Stage 3: `enableFragmentFetch=true` (if implemented)

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
  - Add booleans: `enableQuasiDeletionUi`, `enableDynamicRendering`, `enableFragmentFetch` (all false by default)
  - Expose getters and integrate into stage providers (no behavior change yet)

- Tests
  - Build + runtime smoke: flags accessible; defaults false

- DoD
  - Code compiles; nothing changes visually/functionally

- Status
  - Completed — 2025-09-01. Added `enableQuasiDeletionUi`, `enableDynamicRendering`, `enableFragmentFetch` to `FlagConstants` and `ClientFlagsBase` (default false). Updated `__NAME_MAPPING__` so `WaveClientServlet` reflects flags to the client. Verified compilation via `:wave:compileJava`.

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
  - In Progress — 2025-09-01. Implemented dynamic renderer with page-in/out: pages in visible blips (+upper/lower buffer) and pages out those far outside a slack window. Tunables via flags: `dynamicPrerenderUpperPx`, `dynamicPrerenderLowerPx`, `dynamicPageOutSlackPx`, `dynamicScrollThrottleMs`. Throttled scroll handling and basic metrics (logs gated by `enableViewportStats`). Wired in `StageTwo`. GWT compile passes.

- How to run with flags via Gradle
  - `./gradlew :wave:run -PclientFlags="enableDynamicRendering=true,enableQuasiDeletionUi=true,enableViewportStats=true,dynamicPrerenderUpperPx=600,dynamicPrerenderLowerPx=800,dynamicPageOutSlackPx=1200,dynamicScrollThrottleMs=50"`
  - Flags can also be toggled at request time by adding query parameters to the webclient URL; the Gradle property sets server-wide defaults for convenience.

- Resource integration
  - Integrated `Render.css` (animations/placeholders) via `RenderCssLoader` and inject when dynamic rendering is enabled.
- Placeholder behavior: when dynamic rendering is on, `BlipPager` toggles `.placeholder` on page-in/out to show visual flaps; `DynamicDomRenderer.setPlaceholder(...)` is available for future explicit calls.
- Cleanup hook: `BlipPager` now exposes `setResourceCleaner(...)` invoked during page-out for deeper resource detachment (widgets, listeners). Future phase will implement a concrete cleaner.
  - ResourceCleaner implemented — 2025-09-01: `BlipResourceCleaner` orphans any widgets under the blip DOM in `LogicalPanel` and cancels timers registered via `BlipAsyncRegistry`. Hook attached when `enableDynamicRendering=true`.
  - Robustness: extra null-safety in placeholder toggling and defensive DOM reads; `DomScrollerImpl` clamps scroll values. `FragmentRequester` now uses a callback for error handling.

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
  - Scaffolded (client) — 2025-09-01. Added `FragmentRequester` (no-op) and integrated call from `DynamicRendererImpl` under `enableFragmentFetch`. Server path not implemented yet.
  - Addendum — 2025-09-01. `FragmentRequester` now includes `Callback` with `onSuccess/onError` for failures.

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
- To run server with dynamic rendering on by default: `./gradlew :wave:run -PclientFlags="enableDynamicRendering=true,enableQuasiDeletionUi=true"`
- For GWT dev or dev compile: append the same `-PclientFlags=...` to `gwtDev`, `compileGwtDev`, or `compileGwt`.

-------------------------------------------------------------------------------

## 10) Appendix — Concrete Diffs to Study in wiab.pro

- Dynamic viewport: `DynamicRendererImpl` (visible intervals, placeholders)
- Quasi deletion UX: `DynamicDomRenderer#deletifyBlipAndChildren`, `BlipViewDomImpl#setQuasiDeleted`, `DomUtil#setQuasiDeleted`
- Scroll tracking: `ScreenControllerImpl` (direction/speed heuristics)

These provide runnable reference logic for the Apache Wave port, with server-fragments code paths skipped initially.
