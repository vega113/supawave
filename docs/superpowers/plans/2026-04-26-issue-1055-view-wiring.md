# F-2 (slice 5) — view wiring + integration cleanup (issue #1055)

Date: 2026-04-26
Branch: claude/hungry-albattani-103e9a

This plan executes F-2 slice 5 in two parts. Part A is the user-visible
duplicate-removal pass. Part B is URL state, depth-nav wiring, keyboard
plumbing, focus-trap, and pin/archive prop binding.

## Part A — Integration cleanup (blocking, non-deferrable)

### A.1 Remove the legacy "Hosted workflow" intro card content

`HtmlRenderer.appendRootShellWorkflowMarkup` currently emits a centered
`<div class="sidecar-search-card">` that contains:
- a `<p class="sidecar-eyebrow">J2CL root shell</p>` eyebrow
- `<h1 class="sidecar-title">Hosted workflow</h1>` headline
- a `<p class="sidecar-detail">` with the long workflow description
- `<form class="sidecar-search-toolbar">` with `<input class="sidecar-search-input">` + `<button class="sidecar-search-submit">`

`J2clSearchPanelView` adopts this card via `queryRequired(card, ".sidecar-search-toolbar")`
+ several other selectors, so we can NOT delete the wrapper without breaking
the search panel adoption path. Plan:

- Drop the eyebrow, title, and detail entirely from the SSR emit (they were
  pure decoration; no client code reads them).
- Keep the `<div class="sidecar-search-card">` wrapper itself.
- Keep the `<form class="sidecar-search-toolbar">` AND inputs but flag the
  whole legacy form/eyebrow as `data-j2cl-legacy-search-form="true"` and
  apply CSS `display:none` so the visible chrome is gone — `<wavy-search-rail>`
  is the canonical surface. `J2clSearchPanelView` still finds the elements
  via querySelector and binds `onsubmit`; the search query is fed via the
  rail and forwarded to `setQuery()` so callers see the same outcome.
- Hide the legacy `<p class="sidecar-search-session">` and `<p class="sidecar-search-status">`
  text bodies (kept in DOM, hidden by CSS on the wrapper so element queries
  still resolve). The status surface migrates to inline messages on the rail
  in a future slice; today the status updates are observational.

### A.2 Replace "Select a wave" placeholder with wavy empty-state

`appendRootShellSelectedWaveCard` currently emits a `<div class="sidecar-empty-state">`
with the text "Select a wave to open it here." plus the eyebrow "Opened wave"
and an `<h2>Select a wave</h2>` placeholder.

Plan:
- Wrap the empty-state content in a wavy empty-state recipe: centered ghost
  waveform mark (re-use the SVG glyph from the search rail at 64px), an
  `<h3 class="wavy-empty-state-headline">Open a wave to read</h3>` headline,
  and the placeholder text moves into a `<p class="wavy-empty-state-subhead">`.
- Quick-pick chips are a stretch goal — defer to a follow-up if budget runs
  out.
- Keep `<div class="sidecar-empty-state">` as the outer wrapper so existing
  view code (`emptyState.hidden = ...`) continues to work; only restructure
  the inside.
- The headline text and CSS class names must round-trip through
  `J2clSelectedWaveViewChromeTest`.

### A.3 Suppress legacy editor-toolbar wall when no compose active

`J2clToolbarSurfaceController.render()` always calls `addViewActions` and
emits Recent / Next-unread / Previous / Next / Last / Previous-mention /
Next-mention / Archive|Inbox / Pin|Unpin / History — these duplicate the
brand-new `<wavy-wave-nav-row>` (E.1–E.10). When `editState.editable=false`,
no edit actions render but the view actions still wall the bottom of the
shell.

Plan: introduce an `enableViewActions` flag on `J2clToolbarSurfaceController`
(default `true` to preserve sidecar-mode behaviour). The root shell turns it
off via the controller wiring in `J2clRootShellController` so view actions
are drained from the legacy toolbar wall — `<wavy-wave-nav-row>` is the
canonical view chrome.

The host stays mounted (and visible) ONLY when there are edit actions to
render (i.e. `editState.editable=true` AND `selectedWaveState.selectedWavePresent=true`).
That collapses the wall entirely until the user is composing.

### A.4 Fix `<wavy-search-rail>` waveform glyph sizing

Pre-upgrade, `<wavy-search-rail>` is a slot-less custom element. The Lit
`:host { display: block }` rule lives in the shadow DOM, which does not
attach until `customElements.define` runs. The SSR-emitted SVG inside the
slot is `<svg viewBox="0 0 14 14" ...>` without explicit `width`/`height`
attributes; SVG defaults to `300 × 150` per CSS-SVG spec, so it overflows.

Plan:
- Add explicit `width="14" height="14"` attributes to the SVG element in
  `appendWavySearchRail` (HtmlRenderer line 3740).
- Add the same explicit width/height to the Lit element render to belt-and-
  suspenders post-upgrade.
- Add a defensive global CSS rule (in `wavy-thread-collapse.css` since it
  already loads) clamping any svg directly inside `wavy-search-rail .waveform`
  to `width:14px; height:14px`. Pre-upgrade clamp.

### A.5 Mount the new chrome as canonical surface in `J2clSelectedWaveView`

S2 already wires `<wavy-depth-nav-bar>` and `<wavy-wave-nav-row>` into the
selected-wave card. S5 makes them load-bearing by:
- driving `currentDepthBlipId` / `parentDepthBlipId` / `parentAuthorName`
  from the read surface's `data-current-depth-blip-id` attributes (already
  written by `setDepthFocus`).
- toggling the `hidden` attribute on `<wavy-depth-nav-bar>` based on
  whether `currentDepthBlipId` is non-empty.

### A.6 New parity test `J2clRootShellIntegrationTest`

Asserts (signed-in route):
- exactly one `<wavy-search-rail>` element in the rendered HTML
- exactly one `<wavy-wave-nav-row>` element
- exactly one `<wavy-depth-nav-bar>` element
- no `Hosted workflow` literal in the body
- no `<h1 class="sidecar-title">` literal anywhere
- no `<p class="sidecar-eyebrow">J2CL root shell</p>`
- the legacy form is marked `data-j2cl-legacy-search-form="true"`
- the SVG inside `<wavy-search-rail>` has explicit width="14" height="14"
- the wavy empty-state recipe markup is present:
  `class="wavy-empty-state-mark"` + `class="wavy-empty-state-headline"`
- `j2cl-root-toolbar-host` carries `hidden` initially OR is wrapped in a
  `display:none` container

## Part B — View wiring

### B.1 URL `&depth=<blip-id>`

Extend `J2clSidecarRouteState` with a third field `depthBlipId`. Update
`J2clSidecarRouteCodec.parse`/`toUrl` to round-trip it. `J2clSelectedWaveView`
exposes a `setDepthBlipId(...)` setter that calls `setDepthFocus` on the
read surface. The route controller pushes the depth on user drill events
(see B.3 below).

### B.2 Pin/archive nav-row props (S2 deferral)

`J2clSelectedWaveController` already receives `J2clSearchDigestItem` on
`onWaveSelected(waveId, digestItem)`. Plumb `digestItem.isPinned()` through
to the nav-row's `pinned` attribute. The `archived` attribute defaults to
the search query containing `in:archive` until F-4 wires the live folder
state.

### B.3 Keyboard `[` / `]` drill-out / drill-in

The read surface already owns the keymap. Extend
`J2clReadSurfaceDomRenderer.handleKeyDown` to dispatch:
- `[` -> `wavy-depth-up` event (G.2 — drill out)
- `]` -> drill into the focused blip's reply subthread (emit a custom
  `wavy-depth-drill-in` event with `{ blipId: focusedBlip.dataset.blipId }`)

The view listens for both events on the card and updates the URL state +
`<wavy-depth-nav-bar>` props.

### B.4 R-3.7 G.6 awareness pill

When a reply lands inside the currently-focused subthread's ancestor chain,
surface a "↑ N new replies above" pill. Implementation: J2clSelectedWaveView
adds an `<output class="wavy-awareness-pill" hidden>` element to the
card, and the view's `render(...)` updates its text from
`model.getViewportState().getReadWindowEntries()` deltas. Defer the live
pulse animation to `--wavy-pulse-ring` token usage.

### B.5 Focus-trap + `inert` for `<wavy-version-history>` and `<wavy-profile-overlay>`

S4 ships these floating overlays already. S5 closes the focus-trap deferral:
each overlay must:
- on open, set `inert` on every sibling under `<body>`
- on open, focus the first focusable child
- on close, restore focus to the previously-focused element and remove
  `inert` from siblings

### B.6 `markBlipRead` Gateway extension — DEFERRED

R-4.4 (mark-blip-read debounce + supplement op gateway) is the heaviest
piece. Per token-budget guidance, file a follow-up issue and explicitly
defer R-4.4 carrying `j2cl.read.mark_blip_read` telemetry counter. The
deferred pieces:
- Server: `MarkBlipReadServlet` (POST /j2cl/mark-blip-read) that emits
  the supplement op via `SelectedWaveReadStateHelper`.
- Client: IntersectionObserver-equivalent in `J2clReadSurfaceDomRenderer`.
- Decrement of unread badge via supplement subscription.

Follow-up issue link will be added to the PR body.

## Verification gate

```bash
sbt -batch j2clLitTest j2clSearchTest j2clProductionBuild \
  jakartaTest:testOnly *J2clRootShellIntegrationTest *J2clStageOneReadSurfaceParityTest
```
must exit 0.
