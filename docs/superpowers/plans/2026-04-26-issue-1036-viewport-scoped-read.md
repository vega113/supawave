# F-1: Re-execute viewport-scoped J2CL read-surface data path

Status: Ready for implementation
Owner: codex/issue-1036-viewport-scoped-read worktree
Issue: [#1036](https://github.com/vega113/supawave/issues/1036)
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Audit motivating this lane:
`/Users/vega/devroot/worktrees/j2cl-parity-audit/docs/superpowers/audits/2026-04-26-j2cl-gwt-parity-audit.md`
Parity-matrix rows claimed: **R-3.5, R-3.6, R-4.6, R-6.1, R-6.3, R-7.1, R-7.2, R-7.3, R-7.4**

## 1. Why this plan exists

The 2026-04-26 audit (`§3.5, §4.6, §6.1, §6.3, §7.1–§7.4`) found that the J2CL
open-wave surface today renders the entire wave as a flat snapshot list with
**no viewport hint observable on the wire**, **no fragment extension on
scroll**, **no server clamp telemetry visible to the client**, and **no
server-rendered first paint of selected-wave content**. The closed slices
`#965`/`#967` shipped against narrow "practical parity" acceptance and never
demonstrated row-level GWT parity in a fixture.

Almost every infrastructure piece this plan needs already exists in the repo
— this plan stitches the loose ends so that `?view=j2cl-root` against a
representative wave actually emits a viewport hint, surfaces server-rendered
HTML for the selected wave on first paint, swaps the shell upgrade in place
without losing focus, and emits the four telemetry counters required by the
issue contract.

The work is delivered as five tasks (T1–T5), each ≤200 LOC of production code
with a paired test at the same change boundary. Each task ends with a
verification step before the next begins.

## 2. Verification ground truth (re-derived in worktree)

Citations below were re-grepped from the worktree on 2026-04-26 against
HEAD `e66ce6013` (post-#1033). Line numbers are accurate as of the worktree
snapshot; treat them as anchors to be re-confirmed during implementation
because reformatting can shift them by a line or two.

### Server side (already-extant seams)

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java:167-196`
  — J2CL root shell GET path, calls
  `J2clSelectedWaveSnapshotRenderer#renderRequestedWave(wave, viewer)` and
  passes the result to `HtmlRenderer.renderJ2clRootShellPage(...)`.
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/J2clSelectedWaveSnapshotRenderer.java:163-243`
  — viewer + wave-id resolution, render-budget guard, payload-cap guard,
  result modes (`SNAPSHOT`, `NO_WAVE`, `SIGNED_OUT`, `DENIED`,
  `RENDER_ERROR`, `BUDGET_EXCEEDED`, `PAYLOAD_EXCEEDED`).
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WaveContentRenderer.java:113-233`
  — `renderWaveContent(...)` builds the entire conversation HTML (no
  viewport bound today). This is the primary place to wire the
  initial-window clamp.
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/ServerHtmlRenderer.java:142,200`
  — emits `data-blip-id` on `<div class="blip">` and `data-thread-id` on
  `<div class="thread">`. The DOM-as-view contract for R-3.6 already exists
  in markup; the J2CL renderer reads exactly these attributes.
- `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java:222-375`
  — server clamp + viewport hint honouring on `Open` wavelet path; emits
  the `J2CL_VIEWPORT_FULL_SNAPSHOT_FALLBACK` warning when a hint arrives but
  the server falls back to a whole-snapshot payload.
- `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/FragmentsMetrics.java:58-65`
  — server-side counters already exist:
  `j2clViewportInitialWindows`, `j2clViewportClampApplied`,
  `j2clViewportExtensionRequests`, `j2clViewportExtensionOk`,
  `j2clViewportExtensionErrors`, `j2clViewportSnapshotFallbacks`.
- `wave/src/main/java/org/waveprotocol/box/server/rpc/FragmentsServlet.java:179-241`
  — `/fragments?…&client=j2cl` is the J2CL extension fetch path; clamp +
  J2CL counters are already wired.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3473-3585`
  — `appendRootShellWorkflowMarkup` and `appendRootShellSelectedWaveCard`
  emit the server-first card with `data-j2cl-server-first-selected-wave`,
  `data-j2cl-server-first-mode`, and `data-j2cl-upgrade-placeholder`. The
  snapshot HTML is appended into `.sidecar-selected-content`.

### J2CL/Lit client side (already-extant seams)

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarOpenRequest.java:18-30`
  + `SidecarViewportHints.java:14-26` — viewport-hint payload type already
  encoded into `ProtocolOpenRequest` field 5/6/7 by
  `SidecarTransportCodec.encodeOpenEnvelope` (T = field tag 5: anchor
  blip id, 6: direction, 7: limit).
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java:64-135`
  — `openSelectedWave` builds the open frame including viewport hints.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:383-529`
  — `openSelectedWave` calls `resolveInitialViewportHints()` and an
  `onViewportEdge` debouncer drives `gateway.fetchFragments(...)` extension.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:192-269`
  — `setViewportEdgeHandler` wires `J2clReadSurfaceDomRenderer` scroll edges
  to the controller; `initialViewportHints` returns either a server-first
  blip anchor or `SidecarViewportHints.defaultLimit()`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java:454-683`
  — placeholder rendering, edge detection (forward / backward), scroll-anchor
  preservation, `requestReachablePlaceholderAfterRender()`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clServerFirstRootShellDom.java:6-61`
  — selectors that locate the server-first card inside the J2CL shell.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/telemetry/J2clClientTelemetry.java:36-126`
  — `browserStatsSink()` posts every J2CL telemetry event into the same
  `window.__stats` channel the GWT panels already consume.

### Gaps (the actual delivery surface for this lane)

1. **R-6.1 — server-rendered visible-window first paint**:
   `J2clSelectedWaveSnapshotRenderer.renderRequestedWave` calls
   `WaveContentRenderer.renderWaveContent(waveView, viewer, …)` which
   currently renders the *entire* conversation tree. There is no
   `initialWindowSize` parameter, no clamp, and no telemetry emission for
   the initial-window event. The server therefore sometimes hits the
   payload-cap and silently degrades to no-wave (BUDGET_EXCEEDED /
   PAYLOAD_EXCEEDED).
2. **R-3.6 — DOM-as-view provider against the fragmented DOM**:
   `J2clReadSurfaceDomRenderer.enhanceExistingSurface()` resolves blips by
   `[data-blip-id]` but does not assert it can locate a *fragment-bounded*
   subset. We have to demonstrate the provider continues to resolve the
   same set of semantic queries (blip enumeration, focus first blip,
   inline-thread enumeration) when the rendered DOM contains only the
   visible window plus terminal placeholders.
3. **R-4.6 / R-7.1 — viewport hint visible on first socket open in
   production**: today `J2clSelectedWaveView#initialViewportHints` returns
   `SidecarViewportHints.defaultLimit()` only when there is no preserved
   server snapshot. In the no-server-first case (no wave id in the URL on
   first paint, then user clicks a digest) the hint *is* sent. But the
   wire shape is currently invisible to telemetry — there is no client
   counter that proves "I sent a viewport hint with limit N at time T."
4. **R-7.2 / R-7.3 — extension on scroll with server-clamp visibility**:
   the J2CL controller already coalesces edge fetches, but there is no
   client-side telemetry event for the extension fetch and no telemetry
   event when the server clamps the requested limit. The audit explicitly
   calls these out as required: `viewport.initial_window`,
   `viewport.extension_fetch`, `viewport.clamp_applied`,
   `viewport.fallback_to_whole_wave`.
5. **R-7.4 — no-fallback assertion**: the `j2clViewportSnapshotFallbacks`
   server counter exists, but no test pins a fixture wave to assert the
   counter stays at zero through a representative open. We need both a
   server unit test (Jakarta) and a J2CL test that asserts the client emits
   `viewport.fallback_to_whole_wave` only on the explicit fallback path.
6. **R-6.3 — shell-swap upgrade path**: `J2clSelectedWaveView` already
   emits `j2cl.root_shell` `shell_swap` events when it swaps the
   server-first card, but only on `live-update` reasons. We need to
   confirm the swap event also fires on the initial enhance step (the
   transition from static HTML to the live Lit/J2CL surface) and keep the
   focus invariant: the first-blip tabindex from server-rendered HTML is
   the focus target after enhancement.

## 3. Tasks

Each task lists files to edit, expected diff, paired tests, the parity row
it satisfies, and a verification command.

### T1 — Server: viewport-bounded `WaveContentRenderer` overload

**Rows: R-3.5, R-6.1, R-7.1**

Add a viewport-aware overload to `WaveContentRenderer`:

```java
public static String renderWaveContent(
    WaveViewData waveView,
    ParticipantId viewer,
    int initialWindowSize)        // 0 == "render whole wave" (legacy GWT path)
```

Implementation outline:

1. Threading. The existing
   `renderWaveContent(WaveViewData, ParticipantId, RenderBudget)` worker
   currently delegates the entire conversation to
   `ServerHtmlRenderer` via `ReductionBasedRenderer.renderWith(rules,
   conversations)` (`WaveContentRenderer.java:171`). The new overload
   forwards `initialWindowSize` to a sibling worker that calls
   `ReductionBasedRenderer.renderWith(...)` against a per-blip subset.
   Concretely: walk the root thread blips in document order (the same DFS
   as `countBlipsAndThreads` at line 325), keep the first
   `initialWindowSize` blips, render *only* those plus their inline reply
   threads. The participants and title/tags wrapper is unchanged.
2. Server-side terminal placeholder. After the rendered slice append a
   `<div class="visible-region-placeholder"
   data-j2cl-server-placeholder="true" data-segment="placeholder-tail"
   role="listitem" aria-busy="true">Additional blips will load on
   scroll.</div>`. The marker is **for visual continuity / AT
   announcement on the static HTML only**. It is not promoted into the
   J2CL `renderedWindowEntries` list — extension on scroll is driven
   exclusively by `J2clSelectedWaveProjector.projectViewportState` after
   the live socket update lands and `view.render(model)` calls
   `readSurface.renderWindow(...)`. The data-shape contract for that path
   is documented in §2 above and in `J2clSelectedWaveViewportState.java`
   line 363.
3. Attribute the wrapper with `data-j2cl-initial-window-size` (numeric
   value of the server-applied window size) on the existing
   `<div class="wave-content">` element so the J2CL renderer and tests
   can verify "the server window matches the limit we asked for." Add
   `data-j2cl-server-first-surface="true"` to the same wrapper so T2/T4
   can branch on it without introducing a new outer element (the J2CL
   `findExistingSurface` selector at
   `J2clReadSurfaceDomRenderer.java:481` already matches `.wave-content`).
4. `J2clSelectedWaveSnapshotRenderer.renderRequestedWave` adopts a small
   `INITIAL_WINDOW_SIZE` constant (set equal to the existing
   `wave.fragments.defaultViewportLimit = 5` from `reference.conf:468`)
   and threads it into `renderWaveContent`. Increment
   `FragmentsMetrics.j2clViewportInitialWindows` once on the snapshot
   path. The existing `WaveClientRpcImpl` socket-open increment is
   independent — both call sites contribute to the counter.

Paired tests:

- New `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/render/WaveContentRendererWindowTest.java`:
  * Builds a fake `WaveViewData` with 12 blips, asserts that
    `renderWaveContent(..., 5)` emits exactly five `data-blip-id` blocks
    and one `visible-region-placeholder` element.
  * Asserts `initialWindowSize=0` preserves whole-wave behavior (back-compat
    for `WavePreRenderer`).
- New `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/render/J2clSelectedWaveSnapshotRendererWindowTest.java`:
  * With a 12-blip wave, asserts the rendered HTML carries
    `data-j2cl-initial-window-size="5"` and contains exactly one
    placeholder marker.
  * Asserts the `j2clViewportInitialWindows` counter advances by 1 on the
    snapshot path (using `FragmentsMetrics.setEnabled(true)` in the test).

### T2 — Server: HTML-renderer keyboard contract for the visible window

**Rows: R-6.1 (keyboard navigability), R-3.6, R-6.3 (swap focus invariant)**

`ServerHtmlRenderer` currently emits `data-blip-id` but no `tabindex`,
`role`, or `aria-label`. The R-6.1 contract requires the server HTML alone
to be readable and keyboard-focusable before client boot. Without `tabindex`
the server HTML is technically AT-readable but not tab-reachable, and
`J2clReadSurfaceDomRenderer.enhanceSurface` then has to choose the focus
target after upgrade — exactly the focus-theft path R-6.3 forbids.

Changes:

1. Modify `ServerHtmlRenderer.openBlip(...)` (search for the existing
   `data-blip-id=` emission near line 142): add `tabindex="-1"` on every
   blip and `role="article"` for inline-thread blips, `role="listitem"` for
   root-thread blips. Add `tabindex="0"` *only* on the first root-thread
   blip rendered. This matches the J2CL renderer's `enhanceBlips` choice
   and means the upgrade does not have to reassign tabindex.
2. Modify `ServerHtmlRenderer.startThread(...)` (existing `data-thread-id=`
   emission near line 200): add `role="list"` for the root thread and
   `role="group"` for inline threads, plus `aria-label` for the inline
   thread (mirroring `enhanceInlineThread`).
3. The `data-j2cl-server-first-surface="true"` attribute set on the
   `wave-content` wrapper in T1 step 3 allows
   `J2clReadSurfaceDomRenderer.enhanceExistingSurface` (after the T4
   focus-preserving change) to know the surface is server-rendered and
   keep the focused element stable.

Paired tests:

- Extend the new `WaveContentRendererWindowTest` from T1 to assert:
  * Exactly one element has `tabindex="0"` and it is the first rendered
    blip.
  * Every other blip has `tabindex="-1"`.
  * Root thread carries `role="list"`; inline-thread blocks carry
    `role="group"` and a non-empty `aria-label`.
  * The wave content wrapper sets
    `data-j2cl-server-first-surface="true"`.
- Add J2CL test
  `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomEnhanceWindowTest.java`:
  * Mounts a fixture HTML string from T1's expected output (5 blips +
    placeholder) and calls `enhanceExistingSurface()`. Asserts:
    - Initially-focused blip is the same element the server marked
      `tabindex="0"`.
    - The placeholder element retains its `data-j2cl-viewport-placeholder`
      semantics so the existing `requestReachablePlaceholderAfterRender`
      path triggers an extension fetch on near-edge scroll.

### T3 — J2CL: client-side viewport telemetry events

**Rows: R-4.6, R-7.1, R-7.3, R-7.4**

Wire the four counters required by the issue contract into the existing
`J2clClientTelemetry.browserStatsSink()` channel. They are emitted at
points the controller already runs through:

1. `viewport.initial_window` — emitted in
   `J2clSelectedWaveController.openSelectedWave` after
   `resolveInitialViewportHints()` returns and the open frame is built.
   Fields: `direction`, `limit` (string-encoded; never the wave id).
2. `viewport.extension_fetch` — emitted in
   `J2clSelectedWaveController.onViewportEdge` right before the
   `gateway.fetchFragments(...)` call. Field: `direction`. Also emit
   `viewport.extension_fetch.outcome` on success/error in the response
   handler with field `outcome=ok|error|stale`.
3. `viewport.clamp_applied` — emitted on the success handler when the
   merged viewport state's blip count is *less* than the requested
   `FRAGMENT_GROWTH_LIMIT`. The server clamp policy is observable to the
   client from the response shape: requested limit minus actual loaded
   blip count yields the clamp delta.
4. `viewport.fallback_to_whole_wave` — emitted in
   `J2clSelectedWaveController.openSelectedWave`'s update handler when the
   first non-establishment update carries no viewport-shaped fragments
   (i.e. the server returned a snapshot despite the hint, mirroring the
   server-side `J2CL_VIEWPORT_FULL_SNAPSHOT_FALLBACK` log). Field:
   `reason=server-snapshot`.

Implementation seam:

- Add `private void emitViewport(String name, ...)` next to the existing
  `emitMetadataFailed` (which is already a parallel pattern in the same
  file).
- Reuse `J2clClientTelemetry.event(name).field(...).build()`. The
  telemetry helper rejects sensitive field names (waveid, address,
  attachmentid) so we only emit numeric/categorical fields.

Paired tests:

- Extend
  `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`:
  * `viewportTelemetryEventsEmittedOnOpen` — installs a recording sink,
    selects a wave, asserts a single `viewport.initial_window` event with
    the expected `direction=forward` and a non-empty `limit`.
  * `viewportTelemetryEventsEmittedOnExtension` — drives an `onViewportEdge`
    plus a successful response, asserts `viewport.extension_fetch` and
    `viewport.extension_fetch.outcome=ok` events were recorded in order.
  * `viewportClampAppliedTelemetry` — drives an extension whose response
    contains fewer blips than requested, asserts
    `viewport.clamp_applied` with `delta=2`.
  * `viewportFallbackToWholeWaveTelemetry` — drives the no-fragments path
    (server returned snapshot but no fragment payload) and asserts
    `viewport.fallback_to_whole_wave` is emitted exactly once.
- Add a `RecordingTelemetrySink` reuse line if the existing test sink is
  not already imported.

### T4 — J2CL: shell-swap upgrade preserves first-blip focus

**Rows: R-6.3, R-3.6**

`J2clSelectedWaveView` already records a `shell_swap` event on
`live-update` reason. After T2 the server emits `tabindex="0"` on the
first blip; the upgrade path needs to commit to *not stealing focus*.

Changes:

1. In `J2clReadSurfaceDomRenderer.enhanceExistingSurface()` (line 229):
   when the existing surface carries `data-j2cl-server-first-surface="true"`,
   record the currently-focused element on the document. After
   `enhanceSurface(surface)` runs, if `document.activeElement` was changed
   to something else by the enhancement, restore it. Today
   `restoreFocusedBlip` runs but it can pick the first focusable blip
   when no prior focus existed — which is fine pre-boot but causes a
   focus jump if the user had hovered the search input before boot
   completes.
2. In `J2clSelectedWaveView.handleSelectedWaveModelRender` (the existing
   path that calls `emitRootShellSwap`) — guarantee `emitRootShellSwap`
   fires on the *first* live-update transition out of server-first mode,
   and on the *cold* live-update mount when no server-first card was
   present (currently only the `live-update` reason fires; we want a
   single explicit event whose `reason` field disambiguates: `cold-mount`
   when no server snapshot was present, `live-update` when one was).

Paired tests:

- Extend
  `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewTelemetryTest.java`
  with:
  * `shellSwapEmitsColdMountReasonWhenNoServerFirstCardPresent` — asserts
    a `shell_swap` event with `reason=cold-mount` on first render when
    `J2clServerFirstRootShellDom.findSelectedWaveCard(host)` returns
    null.
  * `shellSwapPreservesFocusedSearchInput` — sets focus to a sibling
    element before render, asserts after the swap that
    `document.activeElement` is unchanged and the
    `shell_swap.focus_preserved` flag is `true`.

### T5 — Fixture: side-by-side viewport assertion

**Rows: All claimed rows; demonstrates the hard acceptance.**

The issue contract calls for a fixture that mounts the J2CL root on a
representative large wave and asserts:
(a) initial DOM contains only the visible-window blip count,
(b) scroll triggers extension,
(c) extension applies without scroll-anchor loss,
(d) total payload over the wire stays well below the whole-wave payload
    size, and
(e) reciprocal smoke confirming `?view=gwt` is unchanged.

We split this into two layers:

1. **JVM/Jakarta layer**
   (`wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clViewportFirstPaintParityTest.java`):
   * Builds a fixture wave with 12 blips via the existing test helpers
     (`WaveServerMock` if available, else direct `ObservableWaveletData`
     stubs as in `WavePreRendererTest`).
   * Calls `J2clSelectedWaveSnapshotRenderer.renderRequestedWave(waveId,
     viewer)` and asserts:
     - Exactly 5 `data-blip-id` blocks plus one
       `visible-region-placeholder` element in the rendered HTML.
     - The HTML payload size is ≤ 75 % of the whole-wave HTML size
       produced by `renderWaveContent(view, viewer)` (no window).
     - `j2clViewportInitialWindows` advanced by 1.
     - `j2clViewportSnapshotFallbacks` did not advance.

2. **J2CL DOM layer**
   (`j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clViewportExtensionDomFixtureTest.java`):
   * Mounts the same expected HTML output (5 blips + placeholder) into a
     `<div>` host, attaches a `J2clReadSurfaceDomRenderer`, calls
     `enhanceExistingSurface`, and asserts:
     - 5 `[data-j2cl-read-blip='true']` items + 1 placeholder.
     - First blip carries `tabindex="0"`.
     - Faking a near-bottom scroll triggers a single
       `viewportEdgeListener.onViewportEdge(anchor, "forward")` call.
     - After applying a fake fragment response with 5 more blips, the
       rendered list grows to 10 + 0 placeholders and the scroll anchor
       (the first rendered blip's bounding box top) is preserved within
       1 px.

The fixture is *not* a browser harness (those run separately in `j2clLitTest`);
it asserts the data-shape contract through the same DOM API the live shell
uses. This is the same pattern already used by
`J2clReadSurfaceDomRendererTest` (existing), so the test infrastructure is
in place.

The reciprocal `?view=gwt` smoke is satisfied by the existing
`WaveClientServletTest` and `WaveClientServletJ2clRootShellTest` matrix:
T1's edits do not touch the GWT-side response. We add an explicit assert
in `WaveClientServletTest` (or a new test class) confirming the GWT path
does not include `data-j2cl-server-first-surface` markers — i.e. T1 leaves
the legacy GWT skeleton response byte-for-byte unchanged.

## 4. Verification discipline

Per task, before moving on:

```
sbt -batch \
  "jakartaTest:testOnly *WaveContentRendererWindowTest *J2clSelectedWaveSnapshotRendererWindowTest *J2clViewportFirstPaintParityTest" \
  j2clSearchTest j2clLitTest
```

After all tasks land, run the full SBT bundle from the issue contract:

```
sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild
```

Plus targeted Jakarta tests for any new server-side test classes:

```
sbt -batch "jakartaTest:testOnly *WaveContentRendererWindowTest *J2clSelectedWaveSnapshotRendererWindowTest *J2clViewportFirstPaintParityTest"
```

And confirm `git diff --check` is clean.

Telemetry validation via the worktree-local boot is recorded only as the
final check (the test layers above are the contract-binding evidence):

```
bash scripts/worktree-boot.sh --port <free>
# In a browser, visit /?view=j2cl-root&wave=<wave-id-with-12-blips>
# Inspect window.__stats[] for events whose evtGroup ∈
#   {viewport.initial_window, viewport.extension_fetch,
#    viewport.clamp_applied, viewport.fallback_to_whole_wave}
# Then visit /?view=gwt&wave=<same-wave> and confirm unchanged.
```

## 5. Out-of-scope respect (audit alignment)

- **Per-blip rendering shape** (avatar, author, timestamp, threading per
  blip): F-2 (#1037). This plan does not change the per-blip HTML beyond
  adding `tabindex` and `role` attributes already required by R-6.1.
- **Compose / write surface**: F-3 (#1038). No compose-side changes.
- **Live read/unread per blip**: F-4 (#1039). The existing
  `currentReadState` plumbing in `J2clSelectedWaveController` is left
  untouched.
- **Design polish beyond the F-0 placeholder recipe**: F-0 (#1035). The
  placeholder uses the existing `visible-region-placeholder` class so F-0
  can restyle without touching this lane.
- **Plugin slot implementations**: this lane reserves the slot points
  (existing markers) but does not register plugins.

## 6. Risk and rollback

The change is additive on the server side (`renderWaveContent` gains an
overload, callers opt in) and the J2CL telemetry events are best-effort
(wrapped in try/catch). The legacy GWT path (`WaveClientServlet`'s
non-J2CL-root branch) is not touched — the prerendering call site
`HtmlRenderer.renderWaveClientPage(..., null)` is unchanged. Rollback is
to revert the four T1–T4 commits; T5 is fixture-only.

Feature-flag gating: the server-side initial-window size is hard-coded to
the existing `wave.fragments.defaultViewportLimit` so an operator can lift
the window via config without a code change. No new flag is introduced
because the issue explicitly forbids "practical parity / partial
delivery" gates.

## 7. Self-review

This section is the gate for moving from plan to implementation.

**Spec coverage.** Every claimed row maps to at least one concrete change
plus a paired test:
- R-3.5 ← T1 (window) + T5 (fixture)
- R-3.6 ← T2 (server attributes) + T2 (J2CL enhance test)
- R-4.6 ← T3 (initial-window telemetry) + T3 (extension telemetry)
- R-6.1 ← T1 (server-rendered window) + T2 (keyboard navigability)
- R-6.3 ← T4 (focus-preserving swap) + T4 (cold-mount swap event)
- R-7.1 ← T1 + T3 (initial-window event)
- R-7.2 ← T3 (extension fetch event) + T5 (DOM-fixture extension assert)
- R-7.3 ← T3 (clamp telemetry) — server clamp itself unchanged; client now
  observes it
- R-7.4 ← T3 (fallback telemetry stays at zero) + T5 (counter assertion)

**Type / import consistency.** `WaveContentRenderer.renderWaveContent`
already takes `WaveViewData, ParticipantId, RenderBudget`; the new overload
adds an `int` and delegates to a private worker — no cross-package
type changes. `J2clClientTelemetry.event(...).field(...).build()` is the
existing builder; rejected-field guard already covers the field names we
will not be using (waveid, address, etc.).

**Verification discipline.** Each task ends with a focused
`jakartaTest:testOnly` or `j2clSearchTest` invocation before moving on,
and the full SBT bundle from the issue contract runs at the end.

**Rollback safety.** The legacy GWT skeleton response
(`WaveClientServlet.doGet` non-J2CL branch, lines 211-249) is unchanged
because `renderWaveClientPage(..., null)` is the third-arg-`null` call.
The new `WaveContentRenderer` overload is purely additive; the existing
single-arg overload still renders the whole wave for `WavePreRenderer`'s
GWT-bound use.

**Out-of-scope respect.** None of the five tasks touch per-blip rendering
shape (no avatar/author/timestamp), compose surface, live read/unread, or
design tokens beyond the existing placeholder class. The parity-matrix
rows owned by F-2/F-3/F-4 are explicitly *not* claimed.

**No "practical parity" escape hatch.** Each row's acceptance is
demonstrated by a paired test, and the fixture (T5) re-confirms the
end-to-end shape against both `?view=j2cl-root` and `?view=gwt`. The
demonstrability of every row is exactly the thing the audit said the
closed slices skipped, so this plan refuses to ship without it.

**Open question I cannot resolve in plan time.** The audit notes
`enableDynamicRendering = false` is the production default
(`reference.conf:435`). That flag governs the *legacy GWT* dynamic
rendering wave panel and is independent of the J2CL fragments transport
(`server.fragments.transport = stream`, also default). The J2CL
viewport-extension path is gated on the J2CL transport, so flipping
`enableDynamicRendering` is **not** required for F-1's contract to hold.
If the demo wave does not visibly extend on scroll, the diagnostic chain
is: (a) does the J2CL controller emit `viewport.initial_window`?, (b) does
the server-side `J2CL_VIEWPORT_FULL_SNAPSHOT_FALLBACK` log fire?, (c) is
`server.fragments.transport=stream` set? — none of those need a new code
change in F-1 if defaults are honoured.

**Late-review correctness check (round 2).** Re-reading the plan against
the actual J2CL data flow:

- The server-side `data-j2cl-server-placeholder` marker is **decorative
  only**. Extension on scroll is driven by
  `J2clSelectedWaveProjector.projectViewportState` →
  `J2clSelectedWaveViewportState.fromFragments` (line 56) →
  `J2clSelectedWaveModel.getViewportState().getReadWindowEntries()` (line
  363) → `view.render(model)` calling
  `readSurface.renderWindow(readWindowEntries)` →
  `J2clReadSurfaceDomRenderer.requestReachablePlaceholderAfterRender`
  (line 645). The plan no longer claims the server placeholder triggers
  fetch; T1 step 2 spells this out.
- `J2clReadSurfaceDomRenderer.findExistingSurface` (line 474) matches
  `[data-j2cl-read-surface='true']` first then `.wave-content`. T1's
  attribute sits on the existing `wave-content` wrapper so the matcher
  picks it up unchanged.
- `J2clSelectedWaveView.shouldPreserveServerSnapshot` (line 203) decides
  whether to keep the server-first card when the live update has empty
  read-blip / read-window state. With T1's window-bounded snapshot, the
  card *will* have rendered blips, so the model's
  `getReadBlips()/getReadWindowEntries()` will be populated by the live
  update and the swap proceeds normally. T4's "cold-mount" reason fires
  only when no card was present.
- Telemetry field guard in `J2clClientTelemetry.rejectSensitiveOrReservedField`
  (line 142) blocks `waveid`, `address`, `attachmentid`, `caption`, etc.
  T3's events use `direction`, `limit`, `outcome`, `delta`, `reason` —
  none of these are on the rejected list.
- `FragmentsMetrics.setEnabled(boolean)` (line 30) defaults false.
  Tests must call `FragmentsMetrics.setEnabled(true)` in `@Before` and
  reset to false in `@After` to avoid leaking state across the test
  suite (the existing `WaveClientRpcViewportHintsTest` is the precedent).
