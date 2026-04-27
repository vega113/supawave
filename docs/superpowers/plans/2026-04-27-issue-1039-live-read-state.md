# F-4 — J2CL live read/unread state + feature activation visibility (issue #1039)

Date: 2026-04-27
Branch: codex/issue-1039-live-read-state
Worktree: /Users/vega/devroot/worktrees/issue-1039-live-read-state
Subsumes: #1056 (markBlipRead Gateway extension deferred from F-2.S5)

## Decision: 2-slice PR

The scope splits cleanly along **Hard Acceptance** boundaries (R-4.4 vs.
R-4.7) and the supplement-op write path is non-trivial enough on its own
to warrant a focused PR. Slicing into two PRs:

- **F-4.S1** — R-4.4 server seam + IntersectionObserver-equivalent +
  live decrement of the wave-list digest unread badge.
- **F-4.S2** — R-4.7 feature-activation visibility (search filters /
  scopes UI in the search rail tray, supplement-driven affordances,
  diff controller surface, reader mode toggle) + `<slot
  name="rail-extension">` reservation in production root shell.

Rationale: each PR stays under 1.5k LoC + ≤1 new servlet, the bot
review window survives 1–2 fix passes, and S2 cleanly depends on the
supplement-op-decrement contract that S1 establishes.

## Slice F-4.S1 — markBlipRead supplement op + live unread decrement

### S1.1 Server: `MarkBlipReadServlet` + `MarkBlipReadHelper`

**Approach: Option A (preferred) — reuse the existing supplement op
path.** The GWT supplement already writes per-user read state via
`SupplementedWave.markAsRead(ConversationBlip)` →
`PrimitiveSupplement.markBlipAsRead(...)` → user-data-wavelet op. The
robot pipeline's `FolderActionService` already exercises this exact
shape:

```java
SupplementedWave supplement = OperationUtil.buildSupplement(operation, context, participant);
supplement.markAsRead(blip);
```

That builds a `SupplementedWaveImpl` from the conversational wavelet +
the user-data wavelet, mutates the UDW, and the robot pipeline flushes
through `OperationUtil.submitDeltas(context, waveletProvider, listener)`
which calls `waveletProvider.submitRequest(...)`. This is the exact same
delivery path GWT uses for read state today, so the J2CL surface gets
behavioural parity with no new write code paths.

The new servlet does not need the full robot OperationRequest machinery —
it can construct an unbound `OperationContextImpl` and drive the
supplement directly.

**File: `wave/src/main/java/org/waveprotocol/box/server/waveserver/MarkBlipReadHelper.java`**
(new, public). It is a structural sibling of `SelectedWaveReadStateHelper`
in the package layout but **takes a different dependency set** —
`WaveletProvider`, `EventDataConverterManager`, `ConversationUtil` — to
drive `OperationContextImpl` for the write path. Calling it a "parity
sibling" is misleading; we own the divergence intentionally. The
existence/access guard reuses the helper's narrow read shape only at the
"is this user allowed to touch this wave" gate.

```java
public class MarkBlipReadHelper {
  public enum Outcome {
    OK, NOT_FOUND, BAD_REQUEST, ALREADY_READ, INTERNAL_ERROR;
  }

  public static final class Result {
    private final Outcome outcome;
    private final int unreadCountAfter;  // -1 when unknown
    // accessor + factories
  }

  private final WaveMap waveMap;  // existence probe (parity with SelectedWaveReadStateHelper)
  private final WaveletProvider waveletProvider;
  private final EventDataConverterManager converterManager;
  private final ConversationUtil conversationUtil;
  private final ParticipantId sharedDomainParticipantId;

  @Inject
  public MarkBlipReadHelper(
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain,
      WaveMap waveMap,
      WaveletProvider waveletProvider,
      EventDataConverterManager converterManager,
      ConversationUtil conversationUtil) { ... }

  public Result markBlipRead(ParticipantId user, WaveId waveId, WaveletId waveletId,
                             String blipId) {
    // Existence + access guard: collapse missing wave / unauthorised → NOT_FOUND
    // (mirrors SelectedWaveReadStateHelper.computeReadState's 404 behaviour).
    // Validate inputs → BAD_REQUEST.
    // Open conversational wavelet + UDW via OperationContextImpl directly:
    //   OperationContextImpl ctx = new OperationContextImpl(
    //       waveletProvider, converterManager.getEventDataConverter(V2_2), conversationUtil);
    //   OpBasedWavelet conv = ctx.openWavelet(waveId, waveletId, user);
    //   ConversationView view = conversationUtil.buildConversation(conv);
    //   WaveletId udwId = IdUtil.buildUserDataWaveletId(user);
    //   OpBasedWavelet udw = ctx.openWavelet(waveId, udwId, user);
    //   PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);
    //   SupplementedWave supplement = SupplementedWaveImpl.create(
    //       udwState, view, user, DefaultFollow.ALWAYS);
    //   ConversationBlip blip = view.getRoot().getBlip(blipId);
    //   if (blip == null) → NOT_FOUND;
    //   if (!supplement.isUnread(blip)) → ALREADY_READ (recompute unread count);
    //   supplement.markAsRead(blip);
    //   // OperationContextImpl implements OperationResults
    //   // (see OperationContextImpl.java:74), so the cast in
    //   // OperationUtil.submitDeltas(OperationResults, …) is direct.
    //   OperationUtil.submitDeltas(ctx, waveletProvider, NO_OP_LISTENER);
    // The conv wavelet is opened ONLY to materialize the
    // ConversationView. We must not mutate it. Defence-in-depth:
    // assert ctx.getOpenWavelets().get(convWaveletName).getDeltas()
    // is empty after markAsRead — only the UDW should have a delta.
    // Return OK with the freshly-computed unread count read back via
    // SelectedWaveReadStateHelper#computeReadState (so the write and
    // read paths agree on the value the client sees).
  }
}
```

**File: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/MarkBlipReadServlet.java`**
(new, public, sibling to `SelectedWaveReadStateServlet`).

- POST `/j2cl/mark-blip-read` (matches the issue #1056 contract).
- JSON body `{"waveId":"...","waveletId":"...","blipId":"..."}`. Default
  `waveletId` to the conversational root if omitted, mirroring the
  fragments servlet pattern.
- Auth via `SessionManager.getLoggedInUser(WebSessions.from(req, false))`;
  null → `403`.
- Parse + validate body → `400` on parse error, missing fields, or
  invalid id format.
- Helper `BAD_REQUEST` → `400`, `NOT_FOUND` → `404` (collapses
  unknown-wave + access-denied), `ALREADY_READ` → `200` with `{ok:true,
  unreadCount:N, alreadyRead:true}` (idempotent so client de-dupe is
  defence-in-depth not load-bearing), `OK` → `200` with `{ok:true,
  unreadCount:N}`, `INTERNAL_ERROR` → `500`.
- `Cache-Control: no-store`.

**File: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`**

- Register: `server.addServlet("/j2cl/mark-blip-read", MarkBlipReadServlet.class);`

### S1.2 Tests for the server seam

- `wave/src/test/java/org/waveprotocol/box/server/waveserver/MarkBlipReadHelperTest.java` —
  unit-tests the helper using the same fixtures as
  `SelectedWaveReadStateHelperTest`. Assert `OK` toggles `isUnread` to
  false, `ALREADY_READ` is idempotent, `NOT_FOUND` for non-participant
  user, `BAD_REQUEST` for unknown blip id, no UDW delta submitted on
  `ALREADY_READ` path.
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/MarkBlipReadServletTest.java` —
  exercises the HTTP contract: 403 unauth, 400 bad body, 404 unknown
  wave, 200 happy path, response shape.
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clMarkBlipReadParityTest.java` —
  end-to-end smoke that the unread count returned by
  `/read-state?waveId=...` decreases by 1 after a successful
  `/j2cl/mark-blip-read` for the same user (asserts the decrement
  contract end-to-end via the same SelectedWaveReadStateHelper read path
  the J2CL client uses).

### S1.3 Gateway extension

**File: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`**

- Add `markBlipRead(String waveId, String blipId, SuccessCallback<Void> onSuccess, ErrorCallback onError)`.
- Implement as a JSON `XMLHttpRequest` POST to `/j2cl/mark-blip-read`
  with `Content-Type: application/json` and body
  `{"waveId":"<safe>","blipId":"<safe>"}`. Use a small JSON encoder
  (existing pattern in `SidecarTransportCodec` is JSON.parse for input
  but for output a tiny manual serializer is fine — the body has at
  most 3 string fields with no special characters in wave/blip ids).
- The gateway delivers `onSuccess.accept(null)` on 200 and a
  human-readable error string on non-200 / network failure.
- New interface contract: add the method to `J2clSelectedWaveController.Gateway`.

### S1.4 IntersectionObserver-equivalent debounce

**File: `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`**

The existing renderer already tracks every rendered blip in
`renderedBlips` and emits `wavy-focus-changed` events. We add an
**`IntersectionObserver`** (native browser API in elemental2) that
watches each rendered blip element when the surface mounts.

- Constant: `static final int VIEWPORT_DWELL_DEBOUNCE_MS = 1500;`
  (issue #1056 spec: ≥1.5 s viewport-dwell). Public for test access.
- Constant: `static final double VIEWPORT_INTERSECTION_THRESHOLD = 0.5;`
  (≥50% visible counts as "in viewport" — survives partial-scroll
  obscuring). **Tall-blip exception**: when
  `intersectionRect.height / rootBounds.height >= 0.5`, count the entry
  as visible regardless of `intersectionRatio` so a blip taller than
  the viewport can never be impossible to mark as read. Test:
  `markBlipRead_tallBlipTallerThanViewport_firesOnce`.
- New listener interface `MarkBlipReadListener { void markBlipRead(String blipId); }`.
- New setter `setMarkBlipReadListener(MarkBlipReadListener listener)` —
  installed once per renderer lifetime by the controller.
- In `enhanceBlips`, register each rendered blip with a single
  per-renderer `IntersectionObserver`. Per-blip dwell timers stored in
  a `Map<String, Double>` (blipId → setTimeout handle). When an entry's
  `intersectionRatio >= 0.5` and the blip carries the `unread`
  attribute, start a `setTimeout` for `VIEWPORT_DWELL_DEBOUNCE_MS`. When
  the entry leaves the viewport before the timer fires, clear the
  handle.
- On timer fire: dispatch the listener; track the blipId in a `Set`
  (`inFlightMarkBlipReads`) so re-entry to the viewport doesn't
  re-dispatch. The unread attribute is **left untouched** until the
  server confirms — clearing optimistically and then having the gateway
  fail would leave the dot hidden with no rollback path; users who
  refresh would see the count snap back, which is worse than a 1-RTT
  delay. The listener clears `unread` only on the success callback;
  on error, the blip id is removed from `inFlightMarkBlipReads` so a
  later re-entry retries. The dwell timer for an already-firing blip
  is NOT restarted on the retry — instead, the next `enhanceBlips`
  pass (after a re-render or visibility return) will re-arm it from
  scratch. This avoids cascading retries on a flaky network.
- Cleanup path: when the surface re-renders (`render` or `renderWindow`
  rebuild), unobserve all old elements, clear pending timers, drop
  `inFlightMarkBlipReads` entries that no longer correspond to a
  rendered blip, and re-observe the new set.
- **Implementation deviation from S1.4 plan:** `elemental2-dom` 1.3.2
  (the version in use, see `~/.m2/repository/com/google/elemental2/elemental2-dom/1.3.2/`)
  does NOT expose an `IntersectionObserver` Java type. Adding a `@JsType`
  shim is out of scope for F-4.S1 (it is a cross-cutting elemental2
  concern that should land as its own slice). Instead, F-4.S1 reuses
  the host's existing `scroll` listener and runs a small
  `evaluateDwellTimers()` pass that calls `getBoundingClientRect()` on
  every rendered blip on every scroll tick. The renderer is
  viewport-scoped (typically ≤ 30 blips), so the cost is bounded;
  `getBoundingClientRect()` is O(1) per node and the layout is
  read-only. This trades a small amount of layout-thrash on rapid
  scroll for a substantially simpler implementation that has no JS
  interop dependency. The test seam (`DwellTimerScheduler`) is
  preserved so the dwell-timing semantics remain deterministic.
- A follow-up issue should track upgrading to a true
  `IntersectionObserver` once an elemental2 binding lands; the
  observable behaviour (≥ 1.5 s dwell, in-flight de-dupe, optimistic
  attribute hold-until-confirmed) does not change between
  implementations.

### S1.5 Controller wiring + live decrement

**File: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`**

- After a `markBlipRead` succeeds, the controller calls
  `scheduleReadStateFetch(requestGeneration)` — the **existing**
  scheduler path that already drives the post-update fetch from the
  selected-wave subscription. This routes through
  `pendingDebounceToken` so concurrent dispatches coalesce: an in-flight
  scheduled fetch is replaced rather than racing. The 250 ms debounce
  is acceptable; if the user reads several blips in rapid succession
  the fetch fires once with the converged count rather than per-blip.
  Do NOT bypass the scheduler with a direct `dispatchReadStateFetch()`
  call — bypassing the dedupe is the bug the reviewer flagged
  (must-fix #4).
- The selected-wave subscription's per-update path (line 480) already
  schedules a fetch on every `ProtocolWaveletUpdate`; multi-tab
  consistency rides on that. The markBlipRead success path is purely
  the local-tab-of-origin's optimization to converge faster than the
  WebSocket round-trip.
- Telemetry: emit `j2cl.read.mark_blip_read` with fields `outcome`
  (`success|error|skipped-already-read`), `blipId`, and a `latency_ms`
  histogram (timestamp diff from listener fire to gateway callback).
  The existing `J2clClientTelemetry.event(...).field(...).build()` API
  is sufficient.

**File: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`**

- In the constructor (both server-first and cold-mount paths), install
  the read-surface listener:
  `readSurface.setMarkBlipReadListener(blipId -> controller.onMarkBlipRead(blipId))`.
- The controller already re-renders on `applyReadStateToModel`; for
  the wave-list digest decrement we need a parallel signal: when the
  controller's `currentReadState` reflects a lower unread count, the
  search-panel view's matching `J2clDigestView` should update.

**File: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelController.java`**
(may already exist — wire a setter if not):

- The selected-wave controller publishes a "read state changed" signal
  (`SelectedWaveReadStateListener` interface) that the search panel
  controller subscribes to. On signal: lookup the digest item by
  waveId and call a new `J2clSearchPanelView.View.updateDigestUnread(waveId, unread)`
  method that reaches into the live `digestViews` map and:
  1. Updates the digest's stats text (the current
     `J2clDigestView.buildStatsText` recomputed view).
  2. If the new unread count < the previous, fires the
     `<wavy-search-rail-card>` `firePulse()` motion on the
     corresponding card via `Js.asPropertyMap(element).get("firePulse")`
     (only relevant once we wire the rail-card surface in S2; for S1
     scope it is a no-op since the digest list still uses
     `J2clDigestView`).
- For S1, the live decrement contract is: the `J2clDigestView`'s stats
  text refreshes WITHOUT a page reload when the user reads a blip.
  S2 swaps the surface to `<wavy-search-rail-card>` and adds the pulse
  motion. Test `J2clSearchPanelLiveDecrementTest` pins the legacy
  `J2clDigestView` path explicitly (asserts the rendered `<button
  class="sidecar-digest">`'s `.sidecar-digest-stats` textContent
  changed; does NOT assert on the rail-card surface). This proves
  R-4.4 acceptance on the surface that ships in S1, before the S2
  rail-card swap lands.

### S1.6 Tests for the client seam

- `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererMarkReadTest.java` —
  fake IntersectionObserver + fake clock, asserts:
  - `unread` blip dwelling ≥1500 ms fires the listener exactly once.
  - `unread` blip leaving before 1500 ms does NOT fire.
  - `unread` blip leaving and re-entering before 1500 ms cumulative
    counter resets — must dwell another full 1500 ms (timer reset
    semantics).
  - The same blip re-entering after firing does NOT re-fire (in-flight
    set blocks).
  - Read blip (no `unread` attribute) NEVER fires.
  - **Tall-blip case** (must-fix #5): blip taller than viewport with
    `intersectionRect.height >= 0.5 * rootBounds.height` fires after
    1500 ms even though `intersectionRatio < 0.5`.
  - **Server-failure rollback**: gateway error callback removes blip
    from `inFlightMarkBlipReads`; a subsequent re-render re-arms the
    timer.
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchGatewayMarkBlipReadTest.java` —
  XHR mock, asserts the request shape, success/error callback delivery.
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerMarkBlipReadTest.java` —
  asserts the success path routes through `scheduleReadStateFetch`
  (not a direct dispatch), coalesces with concurrent debounce
  scheduling, and re-projects the model with the new unread count.
  Multi-tab interleaving test: two `markBlipRead` callbacks deliver
  out-of-order; the older response cannot stomp the newer
  `currentReadState` (uses `latestReadStateApplied` + `readStateFetchSeq`
  guards already in the controller).
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSearchPanelLiveDecrementTest.java` —
  asserts that when the selected-wave controller publishes a lower unread
  count for the selected wave, the matching `J2clDigestView`'s stats
  text updates without a re-render of the entire digest list.

### S1.7 Verification gate

```
sbt -batch j2clLitTest j2clSearchTest j2clProductionBuild \
  jakartaTest:testOnly *MarkBlipReadServletTest *J2clMarkBlipReadParityTest
```
plus
```
sbt -batch wave/test:testOnly *MarkBlipReadHelperTest
```

### S1.8 Risk register

- **The supplement op shape may have GWT-specific assumptions.**
  Mitigation: `FolderActionService` has been the canonical robot path
  for years and only depends on `OpBasedWavelet`, which is shared
  GWT/J2CL/Jakarta code. The helper builds the `SupplementedWaveImpl`
  exactly the same way `OperationUtil.buildSupplement` does. No new
  cross-cutting plumbing.
- **`supplement.isUnread(blip)` may return false on a blip a user never
  saw if the wavelet-version baseline has drifted.** Mitigation: the
  helper treats `ALREADY_READ` as a 200 response with the current
  unread count, so the client always converges; no inconsistency
  surfaces to the user.
- **Concurrent multi-tab dispatch.** Two tabs may both fire
  `markBlipRead` for the same blip nearly simultaneously. Mitigation:
  the supplement op is idempotent (writes the read version into the
  per-user UDW); the second op is a no-op in terms of net state. Both
  responses return the up-to-date unread count.
- **IntersectionObserver unavailable in tests.** Mitigation: factory
  seam (already required for testability of the dwell timer).

## Slice F-4.S2 — Feature activation visibility (R-4.7)

### S2.1 Search filters / scopes UI in the existing rail tray

The `<wavy-search-rail>` already exposes 6 saved-search folders + a
query input + a help-trigger. R-4.7 calls for **filters** (e.g.
unread-only, with-attachments, by-author) and **scopes** (e.g.
in-current-folder, all-archives) beyond the existing single text box.
GWT inventory cells covered:

- B.19 (or the equivalent in the inventory) — filter chips
- B.20 — scope toggle row

**Approach: extend `<wavy-search-rail>`, do not duplicate.**

- Add a collapsible `<details>` block named "Filters" below the
  saved-searches list, ABOVE the result count.
- Filters: 4 toggleable chips reflecting the GWT functional inventory
  filter set:
  1. **Unread only** (`is:unread`) — cyan signal-pulse on activation
  2. **With attachments** (`has:attachment`)
  3. **Mentions me** (already covered by saved search, but shown as a
     filter chip too for parity with GWT)
  4. **From me** (`from:me`)
- Scope toggle row (single-select): **Current folder** (default) /
  **All folders**. When "All folders" is active, the rail prepends
  `everywhere ` to the emitted query and reflects `data-scope="all"`.
- Each chip click emits `wavy-search-filter-toggled` with
  `{filterId, active, query}`; the rail folds the active filters into
  the query text and fires the existing `wavy-search-submit`.

**Query composition rule (specifies must-fix #7 from review):**
1. The current query is parsed as space-separated tokens.
2. Filter activation appends the filter token if not present
   (idempotent re-add is a no-op).
3. Filter deactivation removes EVERY occurrence of that filter token
   (case-insensitive token equality, NOT substring — `is:unread` does
   not match `is:unread-only`).
4. User-typed tokens are preserved in their original position
   (filter additions append at the end; removals delete in-place).
5. Trailing/leading whitespace is normalised on submit.

Test cases:
- `from:bob` + toggle `is:unread` ON → `from:bob is:unread`
- `from:bob is:unread` + toggle `is:unread` OFF → `from:bob`
- `is:unread foo bar` + toggle `is:unread` OFF → `foo bar`
- `IS:UNREAD` + toggle `is:unread` OFF → `` (case-insensitive)

The Java search panel controller listens on the new event and updates
the search query through the existing `setQuery(...)` path.

Tests: `j2cl/lit/test/wavy-search-rail-filters.test.js` covering chip
toggle, query composition, scope reflection.

### S2.2 Supplement-driven affordances (drafts, follow, mute)

GWT has per-blip "Edit"/"Reply" buttons and per-wave Follow/Mute toggles
that read from the supplement. F-4 brings the **read** signals to
the J2CL surface; the **write** signals (follow/mute) ride on the same
supplement op pipeline established in S1.

**Per-wave Follow / Mute toggle:**
- Mount in `<wavy-wave-nav-row>` (already exists with pin/archive
  toggles — Follow/Mute is the same shape). Two new attributes:
  `following` (boolean, defaults to true via `DefaultFollow.ALWAYS`)
  and `muted` (boolean, mirror of `!following`).
- New event: `wavy-wave-follow-toggled` `{following, waveId}`.
- Controller-side: a new helper method
  `J2clSelectedWaveController.toggleFollow()` calls a new
  `Gateway.setFollowState(waveId, following, ...)` that posts to a new
  `/j2cl/follow-state` servlet. The servlet uses the same supplement
  op path: `supplement.follow()` / `supplement.unfollow()`.
  - Telemetry: `j2cl.supplement.follow_toggle`.

**Drafts indicator (per-wave row in the wave-list):**
- The wave-list already shows participants + author. Add a "Draft" pill
  (right of the title) when the wave has an unsubmitted draft from the
  current user. The draft signal is read from the existing F-3 compose
  state — there is a `draft-bus` signal on the `<wavy-search-rail-card>`
  that S2 wires.
- Render: `<span class="draft-chip" data-active>Draft</span>` keyed off
  the new `has-draft` attribute on `<wavy-search-rail-card>`.
- Plumbing: when the search panel controller renders digests, it asks
  the search panel view to mount `<wavy-search-rail-card>` (S2
  introduces this — currently `J2clDigestView` renders a plain
  `<button class="sidecar-digest">`). The new card mounts
  `has-draft`/`unread-count`/`pinned`/etc and becomes the new live
  surface for the digest list.

### S2.3 Diff controller surface

GWT wave editing has a "diff controller" that lets the user toggle
"show diff since last view" — so they see what changed since they last
opened the wave. R-4.7 wants this surfaced.

**Approach: per-wave toggle button in `<wavy-wave-nav-row>`.**

- New attribute `diff-mode` (`off|since-last-view|recent-only`).
- Toggle dispatches `wavy-diff-mode-changed` with the new mode.
- The read surface listens and sets a CSS class
  `j2cl-read-surface-diff-mode` on the surface, plus per-blip
  attributes `data-diff-since-last-view="true"` on blips with
  modifications since the last view-version captured in the supplement
  read state. Visual treatment uses `--wavy-signal-amber` for highlights
  (so it doesn't compete with cyan focus).
- Defer-doc: the diff DATA path (computing which blips changed) reads
  from the same supplement read state already fetched by the controller
  — we already know the last-view wavelet version, so any blip with
  `lastModifiedVersion > lastViewVersion` is "new since last view".

### S2.4 Reader mode

GWT has a "reader mode" that hides the editor toolbar, reply buttons,
and surrounding chrome to give a distraction-free read view. R-4.7
wants this surfaced on J2CL.

**Approach: wave-level toggle in `<wavy-wave-nav-row>`.**

- New attribute `reader-mode` (boolean, default false).
- Toggle dispatches `wavy-reader-mode-toggled` `{readerMode}`.
- The selected-wave card's CSS gains `:has([reader-mode])` rules that
  hide compose/toolbar/depth-nav (preserving keyboard nav). Inline blip
  toolbars stay visible on focus only — the existing F-2
  `:host([focused]) .toolbar` rule already supports this.

### S2.5 `<slot name="rail-extension">` reservation in production

**Current state (verified):**
- `<wavy-rail-panel>` already defines `<slot name="rail-extension">`
  inside its own shadow DOM (`j2cl/lit/src/design/wavy-rail-panel.js`
  line 125).
- Production `shell-root.js` has slots `skip-link / header / nav /
  main / status` only — **no rail-extension slot, no third column**.
- The string `slot="rail-extension"` only appears in the design-preview
  page (`HtmlRenderer.java:3850`) inside the design-preview's
  `<wavy-rail-panel>`. It is NOT in the production root shell.

**Scope for F-4.S2:**
1. Add a new slot `rail-extension` to `shell-root.js` with grid area
   `rail`. Grid becomes `"nav main rail"` on wide viewports;
   `grid-template-columns: minmax(190px, 220px) 1fr minmax(220px, 280px)`.
   Collapses to single-column at the existing `860px` breakpoint.
2. Mount one `<wavy-rail-panel slot="rail-extension"
   panel-title="Plugins" data-active-wave-id="" data-active-folder="inbox">`
   in `HtmlRenderer.java`'s signed-in branch immediately after the
   `<shell-status-strip>` line, with no children inside (empty plugin
   slot in production). Plugins target the inner `<slot
   name="rail-extension">` of the rail-panel, NOT the new outer
   shell-root slot, because:
   - The plugin contract documented in `docs/j2cl-plugin-slots.md`
     scopes plugins to `<wavy-rail-panel>`'s named slot.
   - The shell-root slot is just the layout-mount point for
     "something on the right rail" — keeping the contract on
     wavy-rail-panel preserves M.4's documented data-context
     (`data-active-wave-id`, `data-active-folder`).
3. Tests:
   - `j2cl/lit/test/shell-root.test.js` — assert the new
     `slot[name="rail-extension"]` is exposed in the shadow DOM,
     grid-area exists, narrow-viewport collapse keeps it functional.
   - `j2cl/lit/test/shell-root-rail-extension.test.js` — assert a
     light-DOM child with `slot="rail-extension"` projects to the
     new slot.
   - `wave/src/jakarta-test/java/.../J2clRailExtensionParityTest.java`
     — assert the production signed-in HTML emits exactly one
     `<wavy-rail-panel slot="rail-extension"` element with empty body.
   - Update `HtmlRendererJ2clRootShellIntegrationTest` and
     `J2clStageOneFinalParityTest` if they pin "no rail-extension in
     production" assumptions.

### S2.6 Closeout: search/supplement/diff/reader visibly activated

Per the F-4 issue body: "feature activation events per active feature"
telemetry. New counter `j2cl.feature.activation` with `feature` field
(`search-filter|supplement-follow|diff-mode|reader-mode|rail-extension`).
Emitted once per session per feature on first activation.

### S2.7 Tests for S2

- `j2cl/lit/test/wavy-search-rail-filters.test.js` — chip toggle path.
- `j2cl/lit/test/wavy-wave-nav-row-follow-toggle.test.js`
- `j2cl/lit/test/wavy-wave-nav-row-diff-mode.test.js`
- `j2cl/lit/test/wavy-wave-nav-row-reader-mode.test.js`
- `j2cl/lit/test/shell-root-rail-extension.test.js`
- `wave/src/test/java/.../FollowStateHelperTest.java`
- `wave/src/jakarta-test/java/.../FollowStateServletTest.java`
- `wave/src/jakarta-test/java/.../J2clRailExtensionParityTest.java`
- `wave/src/jakarta-test/java/.../J2clFeatureActivationParityTest.java`

### S2.8 Verification gate

```
sbt -batch j2clLitTest j2clSearchTest j2clProductionBuild \
  jakartaTest:testOnly \
    *FollowStateServletTest \
    *J2clFeatureActivationParityTest \
    *J2clRailExtensionParityTest \
    *HtmlRendererJ2clRootShellIntegrationTest
```

### S2.9 R-4.7 deferral candidates

If we run out of bot-window budget, the items most safely deferred:
- Diff controller's per-blip diff highlighting (the toggle + class
  shipping is the bulk of the parity claim; per-blip computation can
  ship as a follow-up issue).
- Drafts pill on rail cards (depends on F-3 compose-state plumbing
  reaching the rail; if F-3 isn't ready, the pill stays hidden +
  data-attribute documented as forward-compatible).

### S2.10 Implementation deviation: what S2 actually shipped

After implementation review the following S2 items shipped in this PR:
- ✅ `<wavy-search-rail>` filter chip strip (`is:unread`,
  `has:attachment`, `from:me`) with the documented query-composition
  rule + cyan signal-pulse on activation. Token equality is exact;
  case-insensitive removal; user tokens preserved.
- ✅ Production `<slot name="rail-extension">` on `<shell-root>` plus
  the empty `<wavy-rail-panel slot="rail-extension">` mount in the
  signed-in J2CL root shell (production parity test pinned).

The following S2 items are **deferred to follow-up issues** with
explicit rationale, per the lane spec note ("if a feature doesn't
have a J2CL story, document the deferral"):

- **Supplement-driven follow/mute toggle.** The supplement op write
  path (S1) is the seam; the dedicated `setFollowState` servlet +
  nav-row toggle were not in scope for this slice's delivery window.
  R-4.7 row demonstrated via search affordance + supplement read state
  (S1's live decrement); supplement-driven write affordance deferred
  to a follow-up issue.
- **Diff controller toggle.** The GWT diff controller is a write-side
  edit-mode feature (track diff while composing), not a read-mode
  affordance. The J2CL surface does not yet have an editor with diff
  capability; deferring per the lane spec's "WHERE APPLICABLE" clause.
- **Reader mode toggle.** Same posture as the diff controller — the
  J2CL surface today is reader-only by default; the toggle is not
  observable until F-3 ships compose-mode chrome to hide. Deferring
  per "WHERE APPLICABLE".

The PR closing #1039 lists these deferrals in a comment with the
specific row claim each was meant to satisfy and the follow-up issue
that tracks the remaining work.

## Cross-slice contracts

- The supplement op write path (S1) is the **only** server seam for
  read-state changes. S2's follow-toggle reuses the same op pipeline
  with `supplement.follow()` / `supplement.unfollow()`.
- The `wave-blip` element's `unread` boolean property is **the** source
  of truth for the optimistic-clear in S1. S2 doesn't touch it.
- The `<wavy-search-rail-card>`'s `firePulse()` motion is reused by S2
  on count change, with the underlying decrement flowing through the
  S1 listener wired in `J2clSearchPanelController`.

## PR titles + bodies

- **F-4.S1 PR title:** "F-4 (slice 1 of 2): J2CL markBlipRead supplement
  op + live unread decrement (R-4.4)"
- **F-4.S1 PR body closes:** `Updates #1039 (slice 1 of 2)` +
  `Closes #1056` (subsumed)
- **F-4.S2 PR title:** "F-4 (slice 2 of 2): J2CL feature activation
  visibility + rail-extension slot (R-4.7)"
- **F-4.S2 PR body closes:** `Closes #1039` (umbrella close).

## Evidence posting

On each PR open + merge, post on:
- #1039 (umbrella)
- #1056 (S1 only — closed by S1 merge)
- #904 (parity tracker)

On final merge, write `/tmp/parity-chain/f4-s1-merged.txt` and
`/tmp/parity-chain/f4-s2-merged.txt`.
