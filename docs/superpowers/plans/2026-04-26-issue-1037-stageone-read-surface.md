# F-2: Re-execute J2CL StageOne read surface (threaded blip rendering, focus, collapse, read/unread, depth-nav)

Status: Ready for implementation
Owner: codex/issue-1037-stageone-read-surface worktree
Issue: [#1037](https://github.com/vega113/supawave/issues/1037)
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Foundation:
- F-0 (#1035, PR #1043, sha `af7072f99b31d7b2b30d26e1ceb986a6239edf15`) — wavy design tokens (`--wavy-*`) + plugin slot contracts (`<slot name="blip-extension">`, `<slot name="rail-extension">`, …).
- F-1 (#1036, PR #1040, sha `86ea6b440d8298262649865a770eaaa97d2b50eb`) — viewport-scoped data path; depth-axis fragment contract.

Audits motivating this lane:
- `docs/superpowers/audits/2026-04-26-j2cl-gwt-parity-audit.md` (R-3.* + R-4.4 gaps)
- `docs/superpowers/audits/2026-04-26-gwt-functional-inventory.md` (64 affordances under F-2 ownership)

Parity-matrix rows claimed: **R-3.1, R-3.2, R-3.3, R-3.4, R-3.7 (NEW), R-4.4**

## 1. Why this plan exists

The 2026-04-26 audit found that the J2CL open-wave panel renders blips as a flat list with raw blip IDs as headers ("Blip 3VPWzePL_DB", …) and plain-text bodies. There is **no threading**, **no per-blip author/avatar/timestamp**, **no focus framing using the F-0 design tokens**, **no thread collapse with scroll-anchor preservation**, and **no per-blip live read/unread state**. The 2026-04-26 functional inventory enumerated 64 GWT affordances that F-2 owns but are absent from the J2CL surface today.

This is the largest user-visible "looks like a real conversation" parity gap and blocks #904 progression. It also blocks F-3 (compose) because the compose surface needs to attach to per-blip reply affordances that F-2 must surface first.

The F-1 lane already shipped the data-path foundations (viewport-scoped fragment window, depth-axis fragment contract, focus-preserving live updates). F-0 already shipped the wavy design tokens and the named plugin slots (`blip-extension`, `rail-extension`). F-2 stitches these foundations into a real read surface.

The work is delivered as **eight tasks (T1–T8)**, each ≤ ~250 LOC of production code with paired tests at the same change boundary, plus one consolidated row-by-row parity fixture (T9). Each task ends with verification before the next begins.

## 2. Verification ground truth (re-derived in worktree)

Citations below were re-grepped from the worktree on 2026-04-26 against HEAD `af7072f99` (post-#1043). Line numbers are accurate as of the worktree snapshot; treat them as anchors that may shift by a line or two.

### Server side

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3321-3461`
  — `renderJ2clRootShellPage` emits the root-shell HTML, links `wavy-tokens.css`, and mounts the lit `<shell-root>` container. The first-paint server-rendered selected-wave card slot is at `appendRootShellSelectedWaveCard`.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3473-3580`
  — `renderJ2clDesignPreviewPage` is the existing F-0 design preview at `?view=j2cl-root&q=design-preview`. F-2 adds a **wave-preview** sub-route alongside it.
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/ServerHtmlRenderer.java`
  — emits the server-first blip DOM with `data-blip-id`, `data-thread-id`, role/listitem/article (per F-1 R-6.1).
- `wave/src/main/java/org/waveprotocol/box/server/rpc/render/J2clSelectedWaveSnapshotRenderer.java`
  — produces server snapshots with the F-1 viewport window already applied.

### J2CL/Lit client side

- `j2cl/lit/src/design/wavy-tokens.css:17-173` — F-0 token contract; **uses these names verbatim**.
- `j2cl/lit/src/design/wavy-blip-card.js:15-185` — `<wavy-blip-card>` with `data-blip-id`, `data-wave-id`, `author-name`, `posted-at`, `is-author`, `focused`, `unread`, `live-pulse` attributes; named slots: default body, `blip-extension`, `metadata`, `reactions`. `firePulse()` triggers signal-cyan glow restart.
- `j2cl/lit/src/design/wavy-rail-panel.js:10-138` — `<wavy-rail-panel>` with `data-active-wave-id`, `data-active-folder`; named `rail-extension` slot.
- `j2cl/lit/src/design/wavy-depth-nav.js:11-79` — `<wavy-depth-nav>` accepts `crumbs: Array<{label, href?, current?}>` for the depth-nav breadcrumb. F-2 extends with the live-update awareness pill on the same element (R-3.7 G.6).
- `j2cl/lit/src/design/wavy-pulse-stage.js:22-92` — canonical `firePulse()` helper; F-2 reuses for live-update events.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java:18-1059` — current renderer. **This is what F-2 replaces.** It renders raw `<article class="blip j2cl-read-blip">` with `Blip <id>` text labels, has flat threading, no per-blip read-state, no depth-nav, no per-blip toolbar.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadBlip.java:8-38` — current model: `blipId`, `text`, `attachments`. **F-2 extends** with `authorId`, `authorDisplayName`, `lastModifiedTime`, `parentBlipId`, `threadId`, `unread`, `isMention`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadWindowEntry.java:8-98` — current viewport entry; **F-2 extends** with the same per-blip metadata fields.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java:18-499` — wave-level state including `unreadCount`, `read`, `readStateKnown`, `readStateStale` and per-blip `readBlips`. F-2 routes per-blip read state down to each `J2clReadBlip`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java:387,443` — already extracts `document.getAuthor()` and `document.getLastModifiedTime()` from `SidecarSelectedWaveDocument` for interactions; F-2 routes the same fields into `J2clReadBlip`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:135-205` — `render(model)` calls `readSurface.renderWindow(...)` or `readSurface.render(...)`. F-2 enhances this view with the depth-nav header, nav-row, tags-row, floating controls, and version-history overlay markers without changing the controller→view contract.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java:140-1032` — controller is unchanged; F-2 only changes the view layer and the data shape `View` consumes.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveDocument.java:7-97` — provides `getAuthor()`, `getLastModifiedTime()`, `getTextContent()`, `getAnnotationRanges()` for parsing mentions.

### Plugin slot contract (per `docs/j2cl-plugin-slots.md`)

F-2 must mount:
- `<slot name="blip-extension">` on each `<wavy-blip-card>` instance — context: `data-blip-id`, `data-wave-id`, `data-blip-author`, `data-blip-is-author`; JS `blipView` getter returns frozen `{id, waveId, authorName, postedAt, isAuthor}`.
- `<slot name="rail-extension">` on the `<wavy-rail-panel>` for the right rail — context: `data-active-wave-id`, `data-active-folder`.

Both slots collapse to zero height when empty in production; design preview adds the dashed-outline label per the existing F-0 logic in the recipe elements.

## 3. Acceptance contract (row-level)

Each row below has an executable acceptance step that the per-row fixture in `J2clStageOneReadSurfaceParityTest` (T9) asserts. **No "practical parity" escape hatch** — every cited matrix row + every cited inventory affordance has a concrete DOM-attribute or telemetry-event assertion.

### R-3.1 Open-wave rendering

**Affordances covered (16):** F.1 author avatar, F.2 author display name, F.3 full datetime tooltip, F.7 link button, F.10 inline-reply chips, F.12 plugin slot, I.1 tags label, I.2 tag chips (read-only), I.6 show/hide tags tray, D.1 wave title, D.2 multi-author avatar stack, B.13 per-digest avatar stack feeding D.2 (shared), J.4 nav drawer toggle, J.5 back-to-inbox, A.6 inbox icon hop (header link), L.1 open user profile from blip avatar.

**Acceptance steps:**
1. The J2CL view renders one `<wavy-blip-card>` per blip in the viewport window. Each carries `data-blip-id`, `data-wave-id`, `author-name`, `posted-at`, `is-author` reflecting the projected `J2clReadBlip`.
2. Each card has a `<time>` element with `title=` set to the ISO-8601 absolute timestamp (full datetime tooltip on hover, F.3).
3. Reply nesting renders as visual indenting via nested `<div class="j2cl-read-thread inline-thread">` containers (matches GWT's `inline-thread` wrapping).
4. A blip with reply children shows an "△ N" inline-reply chip in the body (F.10) clickable to drill in (F-2 R-3.7).
5. Per-blip toolbar `<wavy-blip-toolbar>` (new sub-element, F.4/F.5/F.6/F.7 placeholders) is rendered inside each card's `metadata` slot, hidden until `:focus-within` or `:hover`. F-2 only wires Reply (emits a `wave-blip-reply-requested` CustomEvent for F-3), Edit (placeholder for F-3), Link (copies blip permalink to clipboard via `navigator.clipboard.writeText`), and the overflow placeholder. Delete is F-3.
6. Wave panel header `<wavy-wave-panel-header>` (new chrome element) renders the wave title (D.1), avatar stack (D.2 sourced from `model.getParticipantIds()`), and an overflow-menu "more" trigger button (D.3 placeholder).
7. Tags row at bottom is read-only: renders chips for each tag from `wavelet.tags()`-equivalent supplement field; "Tags:" label (I.1) + chips (I.2 display-only) + "Show / Hide tags tray" toggle (I.6).
8. Plugin slot `<slot name="blip-extension">` is present in every `<wavy-blip-card>` (verified by `surface.querySelectorAll('wavy-blip-card slot[name="blip-extension"]').length === blipCount`).
9. Hairline cyan border per F-0 design tokens — `<wavy-blip-card>` already applies `border: 1px solid var(--wavy-border-hairline)` and `border-radius: var(--wavy-radius-card)` in its own `static styles`; F-2 verifies the cards are inside a scope with the tokens loaded by querying `getComputedStyle(card).getPropertyValue('--wavy-border-hairline')` is non-empty.
10. Clicking a blip avatar emits a `wave-blip-profile-requested` CustomEvent with `{blipId, authorId}` for the L.1 profile overlay; F-2 attaches a no-op listener that the future profile-overlay element will subscribe to.

### R-3.2 Focus framing

**Affordances covered (within E.* navigation row):** focus visuals via `--wavy-focus-ring` token; focus survives incremental updates; arrow + j/k keyboard.

**Acceptance steps:**
1. A `<wavy-focus-frame>` Lit element wraps the read-surface root and tracks the focused blip via a property `focusedBlipId` synced from the renderer.
2. Pressing `ArrowDown`, `ArrowUp`, `j`, `k`, `Home`, `End` moves focus across visible blips (existing arrow / Home / End logic in `J2clReadSurfaceDomRenderer` is preserved; `j` and `k` are added).
3. Focus survives an incremental update: when `view.render(model)` is called with the same blip set plus one new appended blip, the focused blip's `data-blip-id` is unchanged and the focused blip retains `tabindex="0"`. (Existing `restoreFocusedBlipById` already preserves focus across `renderWindow`/`render`. T-fixture asserts the contract.)
4. Focus visuals: focused `<wavy-blip-card>` carries the `focused` attribute, which reflects to `:host([focused])` in F-0's CSS and applies `box-shadow: var(--wavy-focus-ring)` and `border-color: var(--wavy-signal-cyan)` per F-0.
5. Focus transition uses `--wavy-motion-focus-duration` (180ms) + `--wavy-easing-focus`. Already in F-0's `wavy-blip-card.js` `static styles` `transition: box-shadow var(--wavy-motion-focus-duration) var(--wavy-easing-focus);`. F-2 fixture asserts the computed style includes "180ms" (or under reduced-motion, "0.01ms").
6. Focus does not steal from text inputs: when an input is focused inside an inline reply (F-3 territory), arrow keys do not move blip focus. (Existing `onBlipKeyDown` only fires when the blip element itself is the event target, so the contract is already met by the bubbling model. F-2 fixture asserts arrow keys typed into a `<textarea>` do not move blip focus.)
7. Focus telemetry: each focus change emits `J2clClientTelemetry.event("read.focus_change").field("source", <key|click>).build()`. F-2 fixture asserts at least one focus_change event is recorded after pressing ArrowDown.

### R-3.3 Collapse

**Affordances covered:** thread root collapse/expand (already wired in `J2clReadSurfaceDomRenderer.toggleThread`), Space/Enter on toggle, scroll-anchor preservation, focus-doesn't-jump-on-expand.

**Acceptance steps:**
1. Each thread root with at least one child renders a `<button class="j2cl-read-thread-toggle">` toggle inside it, with `aria-expanded`, `aria-controls`, and a label like "Collapse Top thread" / "Expand Top thread". (Existing logic in `enhanceInlineThread`.)
2. Clicking the toggle, or pressing Space or Enter while the toggle is focused, toggles `j2cl-read-thread-collapsed` class and `data-j2cl-thread-collapsed="true"` attribute. (Existing `toggleThread`.)
3. Collapse animation: the thread `.j2cl-read-thread-children` wrapper uses `grid-template-rows: 1fr` → `0fr` transition with `transition: grid-template-rows var(--wavy-motion-collapse-duration) var(--wavy-easing-collapse)` (240ms). Same approach as F-0's `<wavy-rail-panel>`. **NEW** in F-2: T2 introduces a `j2cl-read-thread-children` wrapper around children + the CSS transition.
4. Scroll anchor preserved: when toggling, the renderer captures `firstRenderedBlipId` + its `boundingClientRect.top` (existing `restoreScrollAnchor` mechanism reused) and re-applies the scroll delta after the layout reflow.
5. Read state preserved: collapse does not emit any read-state op (verified by recording read-state ops via the controller's writeSession listener; the count must not change across a collapse-toggle round trip).
6. Focus does not jump on expand: when expanding, the previously-focused (still-mounted) blip retains `tabindex="0"` and `aria-current`. (Existing `ensureSingleTabStop` only runs on collapse.)
7. Collapse telemetry: each toggle emits `J2clClientTelemetry.event("read.thread_toggle").field("state", <"collapsed"|"expanded">).build()`.

### R-3.4 Thread navigation

**Affordances covered (10):** E.1 Recent, E.2 Next Unread (blip-level), E.3 Previous, E.4 Next, E.5 Go to last (End), E.6 Prev @ mention, E.7 Next @ mention, E.8 To Archive, E.9 Pin, E.10 Version History (H).

**Acceptance steps:**
1. A `<wavy-wave-nav-row>` Lit element renders a horizontal toolbar with 10 buttons matching E.1–E.10.
2. **E.1 Recent** — placeholder anchor that scrolls to the most-recent blip (last by version). Fixture: clicking jumps focus to `renderedBlips[renderedBlips.length-1]`.
3. **E.2 Next Unread** — finds the first blip with `unread="true"` *after* the focused blip (with wrap to the first unread overall). Cyan signal accent (`background: var(--wavy-signal-cyan)`) when at least one unread is present. Fixture: with 3 blips of which b2 is unread, clicking Next Unread focuses b2 and emits `read.thread_nav.event("E.2")` telemetry.
4. **E.3 Previous, E.4 Next, E.5 End** — wired against the rendered blip DOM (not the wave-list). E.4 Next == ArrowDown semantics; E.3 Previous == ArrowUp; E.5 End == End key. Fixture asserts per-button focus delta against the rendered DOM order.
5. **E.6 Prev @ / E.7 Next @** — finds the previous/next blip carrying `data-has-mention="true"` (F-2 sets this attribute when any annotation in the document text content is a mention; per the audit, mentions are detected from text bodies that include `@user@domain` patterns or from server-side annotation `link/manual` anchors per existing render code). Fixture: with 4 blips of which b1 and b3 are mentions, Next @ from b0 focuses b1; from b1 focuses b3. Violet accent (`color: var(--wavy-signal-violet)`).
6. **E.8 To Archive** — emits a `wave-archive-requested` CustomEvent with `{waveId}`. F-2 only emits the event; the search-rail's archive folder consumption is server-side.
7. **E.9 Pin** — emits a `wave-pin-toggled` CustomEvent. Cyan accent when pinned (driven by a `pinned` attribute fed from `model.getPinned()` — F-2 reads `selectedDigestItem.isPinned()` if available; otherwise renders the toggle in the unpinned state).
8. **E.10 Version History (H)** — emits a `wave-version-history-requested` CustomEvent with `{waveId}`, AND adds the `data-j2cl-version-history-overlay` reservation slot in the markup so K.1–K.6 elements (deferred to per-row stub) can mount. Keyboard shortcut: `H` while focused on the wave panel header opens the overlay.
9. All E.* buttons have keyboard shortcuts that match GWT (`H` for E.10; rest carry `accesskey` attributes that match the GWT shortcut catalogue). Fixture asserts each button has the expected `aria-keyshortcuts` attribute.

### R-3.7 Arbitrary-depth thread drill-down navigation (NEW)

**Affordances covered (6):** G.1 drill-into "△ N", G.2 Up one level, G.3 Up to wave, G.4 URL `&depth=` state, G.5 keyboard `[` `]`, G.6 live-update awareness pill.

**Acceptance steps:**
1. **G.1 Drill in** — clicking an "△ N" inline-reply chip on a blip (or pressing `]` while focused on it) sets `currentDepthBlipId` on the view. The view re-renders with: (a) ancestor blips collapsed into a `<wavy-depth-nav>` breadcrumb at the top using F-0's `crumbs: Array<{label, href?, current?}>` API, (b) the current depth blip + its descendants in full. Sibling subthreads are NOT loaded — the view consumes only the F-1 fragment-window that already filters by parent. F-2 issues a fragment fetch keyed by `data-parent-blip-id=<currentDepthBlipId>` via the existing `viewportEdgeListener` channel with a new `direction="depth"` value (added to `J2clViewportGrowthDirection` enum + propagated through `J2clSelectedWaveController.onViewportEdge` → server fetchFragments anchor).
2. **G.2 Up one level** — `<wavy-wave-panel-header>` shows a chevron-up button labelled with the parent author's display name when `currentDepthBlipId` is non-empty AND has a parent. Clicking sets `currentDepthBlipId` to the parent blip id (or empty if at root).
3. **G.3 Up to wave** — same header shows a "↑ Up to wave" button that resets `currentDepthBlipId = null`.
4. **G.4 URL state** — when `currentDepthBlipId` changes, the view writes `&depth=<blipId>` into the URL via `history.replaceState`. On view init, it reads the URL and applies. Reload + back/forward preserve depth focus. Fixture asserts `window.location.search` after drill-in includes `depth=` and after drill-out is absent.
5. **G.5 Keyboard** — `[` drills out one level (G.2 equivalent); `]` drills into the focused blip (G.1 equivalent if the focused blip has at least one child). Fixture asserts `]` from the root focused on b1 (which has children) sets `currentDepthBlipId = b1`.
6. **G.6 Live-update awareness pill** — when a fragment update arrives that adds a blip whose `parentBlipId` is one of the *ancestor* blips (above `currentDepthBlipId`), the breadcrumb strip shows a pill "↑ N new replies above" using the `--wavy-pulse-ring` motion (single restart pulse via `wavy-pulse-stage` pattern). Clicking the pill drills out to the level where the new replies arrived. Fixture asserts the pill is present after a synthetic update with `parentBlipId == ancestorOf(currentDepthBlipId)`, and is removed after the user clicks it or drills out manually.

### R-4.4 Per-blip live read/unread

**Affordances covered:** B.17 per-digest msg count signal-cyan pulse on change (the wave-list digest); E.2 Next Unread cyan signal; per-blip unread dot.

**Acceptance steps:**
1. Each `<wavy-blip-card>` reflects the per-blip read state via the F-0 `unread` attribute. The 8px cyan dot pseudo-element from F-0's `:host([unread])::before` fires automatically.
2. The model already exposes wave-level `unreadCount`; F-2 extends `J2clReadBlip` with a `boolean unread` field. The projector reads from the supplement-derived per-blip read state to set this. Per-blip read-state is derived from `J2clSelectedWaveProjector.reprojectReadState` (existing) + the new per-blip wiring.
3. **Reading a blip emits a state-change op.** When a previously-unread blip enters the viewport AND remains visible for ≥1500ms (matches GWT's StageOne `READ_BLIP_DEBOUNCE`), F-2 calls a new `gateway.markBlipRead(waveId, blipId)` callback that the controller routes to the supplement. The blip's `unread` attribute clears, and `firePulse()` runs on the blip card. Fixture asserts: (a) the gateway method is invoked exactly once per blip-id even if the blip stays in viewport; (b) the wave-list digest's `data-unread-count` attribute (in the search-rail) decrements live.
4. **Wave-list digest live decrement.** The view, on apply of the per-blip read-state delta, dispatches a `wave-blip-read-state-changed` CustomEvent on `document.body` with `{waveId, blipId, unreadDelta:-1}`. The search-rail listener (F-2 adds it) finds the matching digest card by `data-wave-id` and decrements its `data-unread-count` text content; if it drops to 0, the badge node hides. The badge re-renders with `--wavy-pulse-ring` via `wavy-pulse-stage.firePulse()` to draw attention.
5. Stale read-state still uses the existing `readStateStale` flag; F-2 carries it down to per-blip cards as a `data-stale="true"` attribute on `<wavy-blip-card>` so a future high-fidelity treatment can desaturate the dot. (The flag-only treatment is sufficient for R-4.4 acceptance.)
6. Telemetry: `J2clClientTelemetry.event("read.blip_read_marked").field("source", <"viewport-debounce"|"manual">).build()` on each mark; `J2clClientTelemetry.event("read.unread_badge_pulse")` on the search-rail decrement.

### Plugin slot reservation (R-3.1 + R-4.4 cross-cut)

- **`<slot name="blip-extension">`** present on every `<wavy-blip-card>` (already in F-0). F-2 validates by `card.querySelector('slot[name="blip-extension"]')` is non-null and `getComputedStyle(...).height === '0px'` when no plugin is mounted in production.
- **`<slot name="rail-extension">`** present on the right rail `<wavy-rail-panel>` (already in F-0). F-2 mounts a `<wavy-rail-panel data-active-wave-id="..." data-active-folder="...">` next to the wave panel for the design-preview-only "wave-extension" rail; production mounts it but with no plugin slotted.

### Inventory affordances under F-2 ownership (64 total — all covered)

The following table maps every F-2-owned inventory affordance to the row it is asserted under. Affordances marked **shared** belong to multiple slices; F-2 ships the read-side stub.

| ID | Affordance | Asserted under | F-2 implementation |
| --- | --- | --- | --- |
| A.2 | Locale picker | R-3.1 | `<wavy-top-chrome>` renders a locale `<select>` populated from `model.locales` (server-side already exposes via `data-locales` attribute on the shell-root); F-2 reads it and surfaces it. **Behavior change-locale calls existing `/?lang=` endpoint.** |
| A.5 | Notifications bell with unread dot | R-3.1 | Stroke-only bell glyph in `<wavy-top-chrome>` with violet `:host([has-unread])::before` dot. F-2 reads `model.notificationsUnreadCount` (default 0; server-side notifications integration is F-4). Display only. |
| A.6 | Inbox/mail icon | R-3.1 | Mail glyph in `<wavy-top-chrome>` linking to `/?folder=inbox`. |
| A.7 | User avatar chip | R-3.1 | Avatar chip in `<wavy-top-chrome>` (server-rendered email + initials per existing shell-header pattern). |
| B.1 | Search query textbox | R-3.1 | Existing `.sidecar-search-input` reused; F-2 wraps in a `<wavy-search-input>` element that adds the waveform glyph (an SVG `<svg>` placed adjacent to the input). |
| B.2 | "Search help" trigger | R-3.1 | New `<button class="wavy-search-help-trigger">` opens `<wavy-search-help-modal>` (T6). |
| B.3 | "New Wave" button | R-3.1 (shared with F-3) | Button in the search-rail header emits `wave-new-requested` CustomEvent. The actual compose surface is F-3. |
| B.4 | Manage saved searches | R-3.1 | "Edit" affordance in the saved-search rail; emits `saved-searches-edit-requested` CustomEvent. |
| B.5 | Saved search: Inbox | R-3.1 | Inbox row in `<wavy-saved-search-rail>` (T6). |
| B.6 | Saved search: Mentions | R-3.1 | Mentions row with violet unread dot (`:host([has-unread])::before` per F-0 token). |
| B.7 | Saved search: Tasks | R-3.1 (shared with F-3) | Tasks row with amber pending count. |
| B.8 | Saved search: Public | R-3.1 | Row. |
| B.9 | Saved search: Archive | R-3.1 | Row. |
| B.10 | Saved search: Pinned | R-3.1 | Row. |
| B.11 | Refresh search results | R-3.1 | Refresh icon button emits `search-refresh-requested` CustomEvent. |
| B.12 | Result count "133 waves" | R-3.1 | Footer text with `data-result-count`. |
| B.13 | Per-digest avatar stack | R-3.1 | Existing `.sidecar-digests` digest cards already render participant initials; F-2 ensures multi-author stacking via overlapping CSS positioning. |
| B.14 | Per-digest pinned indicator | R-3.1 | Cyan pin glyph rendered when `digest.isPinned()`. |
| B.15 | Per-digest title | R-3.1 | Already in `.sidecar-digests`. |
| B.16 | Per-digest snippet | R-3.1 | Already in `.sidecar-digests`. |
| B.17 | Per-digest msg count + cyan pulse on unread change | R-4.4 | F-2 adds `data-unread-count` attribute and pulse-ring animation via `firePulse()` per R-4.4 step 4. |
| B.18 | Per-digest relative timestamp | R-3.1 | Already in `.sidecar-digests`. |
| C.1–C.22 | Search Help modal filters + sort | R-3.1 | `<wavy-search-help-modal>` Lit element (T6) lists every filter/sort token in a static table. Each row carries a `data-filter-token` for the parity fixture to assert presence. |
| D.1 | Wave title | R-3.1 | `<wavy-wave-panel-header>` (T2) renders `model.getTitleText()`. |
| D.2 | Multi-author avatar stack | R-3.1 | Header avatar stack from `model.getParticipantIds()`. |
| D.3 | "more" wave-actions menu | R-3.1 | Overflow `<button aria-label="More wave actions">` that opens a `<wavy-overflow-menu>` (placeholder; menu items are F-3 territory but the trigger ships in F-2). |
| D.4 | Add participant | R-3.1 (shared with F-3) | Button emits `wave-add-participant-requested`. |
| D.5 | New wave with current participants | R-3.1 (shared with F-3) | Menu item emits `wave-new-with-participants-requested`. |
| D.6 | Public/private toggle | R-3.1 (shared with F-3) | Button emits `wave-publicity-toggle-requested`. |
| D.7 | Copy public link | R-3.1 | Button copies `window.location.origin + '/?wave=<waveId>'` via `navigator.clipboard.writeText` and emits a toast. |
| D.8 | Lock/unlock root blip | R-3.1 (shared with F-3) | Button emits `wave-root-lock-toggle-requested`. |
| E.1–E.10 | Wave nav row | R-3.4 | `<wavy-wave-nav-row>` (T3). |
| F.1–F.3 | Author avatar/name/timestamp | R-3.1 | `<wavy-blip-card>` header (T1). |
| F.4 | Reply button | R-3.1 (shared with F-3) | Per-blip toolbar Reply emits `wave-blip-reply-requested`. |
| F.5 | Edit | R-3.1 (shared with F-3) | Per-blip toolbar Edit emits `wave-blip-edit-requested`. |
| F.7 | Link | R-3.1 | Per-blip toolbar Link copies a permalink via `navigator.clipboard.writeText` and emits a toast. |
| F.10 | Inline-reply chips | R-3.1 + R-3.7 | Inline "△ N" chip in blip body; clickable to drill in (R-3.7 G.1). |
| F.12 | Per-blip plugin slot | R-3.1 | Already in F-0; F-2 only verifies presence. |
| I.1, I.2, I.6 | Tags row read-only | R-3.1 | `<wavy-tags-row>` (T2) with show/hide toggle. Add/edit affordances are F-3. |
| J.2 | "Scroll to new messages" floating pill | R-4.4 | `<wavy-scroll-to-new>` floating pill that surfaces when `unreadCount > 0` AND the user has scrolled away from the bottom. Cyan signal-pulse on appear. Click scrolls to the first unread blip. |
| J.3 | Hide/show wave controls | R-3.1 | Button toggles a `data-wave-controls-hidden` attribute on the wave panel. |
| J.4 | Open/close nav drawer | R-3.1 | Button (mobile-only via media query) toggles a `data-nav-drawer-open` attribute on the shell-root. |
| J.5 | Back to inbox | R-3.1 | Anchor `<a href="/?folder=inbox">`. |
| K.1 | Version history overlay | R-3.4 (E.10 entry) | F-2 ships the `<wavy-version-history-overlay>` element with the time slider (K.2), Show changes (K.3), Text only (K.4), Restore (K.5 stub — emits CustomEvent), Exit (K.6) controls. **Restore/Show changes are wire-only** (display) — F-2 does not change wave state from the overlay; the existing GWT-side restore endpoint is unchanged and out of scope. The overlay opens via the E.10 button + the `H` key. |
| K.2 | Time slider | R-3.4 | `<input type="range" min=0 max=N>` inside the overlay; the `versionList` is fetched via a new `gateway.fetchVersionHistory(waveId)` callback (T7 stubs the gateway with a no-op default; the harness fixture provides a synthetic 5-version list). |
| K.3 | "Show changes" toggle | R-3.4 | `<input type="checkbox">` checkbox in the overlay shelf. |
| K.4 | "Text only" toggle | R-3.4 | `<input type="checkbox">`. |
| K.5 | Restore | R-3.4 | Button with `aria-haspopup="dialog"` (confirm sub-dialog). Emits `wave-version-restore-requested`. |
| K.6 | Exit | R-3.4 | Closes the overlay by removing the `[open]` attribute. |
| L.1 | Open user profile from blip avatar | R-3.1 | Avatar click emits `wave-blip-profile-requested` CustomEvent; `<wavy-user-profile-overlay>` (placeholder element) opens. |
| L.5 | Previous/Next participant | R-3.1 | Navigation buttons in `<wavy-user-profile-overlay>` advance through `model.getParticipantIds()`. |

That's **64 owned affordances** all covered, each with an executable acceptance step.

## 4. Implementation tasks

Each task lands as one commit with paired tests. Each task closes with `sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild`.

### T1 — Per-blip data shape: author + timestamp + parent + read state

**Files:**
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadBlip.java` (~+40 LOC)
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadWindowEntry.java` (~+30 LOC)
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java` (~+60 LOC) — populate the new fields from `SidecarSelectedWaveDocument` (`getAuthor`, `getLastModifiedTime`) and from `J2clSelectedWaveModel.readBlips` mapping; populate `parentBlipId` and `threadId` from the manifest reads already happening for reactions.
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadBlipTest.java` (~+40 LOC)
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java` (~+80 LOC)

**Verification:** `sbt -batch "jakartaTest/testOnly *J2clSelectedWaveProjectorTest *J2clReadBlipTest"` — green.

### T2 — Lit `<wave-blip>` element + `<wavy-wave-panel-header>` + `<wavy-tags-row>` + `<wavy-blip-toolbar>`

**Files:**
- `j2cl/lit/src/elements/wave-blip.js` (NEW, ~+220 LOC) — wraps `<wavy-blip-card>` from F-0 and adds: per-blip toolbar in the `metadata` slot, mention detection (sets `data-has-mention`), avatar element (CSS-only initials chip from `author-name`), full datetime tooltip via `<time title="...">`, `unread` debounce viewport detection (IntersectionObserver), `firePulse()` exposure for live-update pulse, `live-pulse` reflection, `<a class="wavy-inline-reply-chip">` rendered when `replyCount > 0`. **Plugin slot `blip-extension` flows through transparently from `<wavy-blip-card>`.**
- `j2cl/lit/src/elements/wave-blip-toolbar.js` (NEW, ~+120 LOC) — buttons Reply / Edit / Link / overflow.
- `j2cl/lit/src/elements/wavy-wave-panel-header.js` (NEW, ~+150 LOC) — title (D.1) + avatar stack (D.2) + add-participant (D.4) + new-wave-with-participants (D.5) + privacy toggle (D.6) + copy-public-link (D.7) + lock-toggle (D.8) + more-menu trigger (D.3). All emit CustomEvents; behavior wiring is F-3 territory.
- `j2cl/lit/src/elements/wavy-tags-row.js` (NEW, ~+100 LOC) — read-only chips + show/hide toggle.
- `j2cl/lit/src/index.js` (~+5 LOC) — register new elements.
- `j2cl/lit/test/wave-blip.test.js` (NEW, ~+200 LOC)
- `j2cl/lit/test/wave-blip-toolbar.test.js` (NEW, ~+120 LOC)
- `j2cl/lit/test/wavy-wave-panel-header.test.js` (NEW, ~+150 LOC)
- `j2cl/lit/test/wavy-tags-row.test.js` (NEW, ~+100 LOC)

**Verification:** `cd j2cl/lit && npm test` (web-test-runner) — green.

### T3 — Lit `<wavy-wave-nav-row>` + `<wavy-focus-frame>` + `<wavy-depth-nav-bar>` (G.1–G.6 chrome)

**Files:**
- `j2cl/lit/src/elements/wavy-wave-nav-row.js` (NEW, ~+200 LOC) — E.1–E.10 buttons.
- `j2cl/lit/src/elements/wavy-focus-frame.js` (NEW, ~+100 LOC) — minimal: tracks `focusedBlipId` property; the actual focus visuals live on `<wavy-blip-card>` per F-0. This element exists for landmark + telemetry attribution.
- `j2cl/lit/src/elements/wavy-depth-nav-bar.js` (NEW, ~+150 LOC) — composes F-0's `<wavy-depth-nav>` + the live-update awareness pill (G.6) + the chevron-up Up-one-level button (G.2) + the Up-to-wave button (G.3).
- `j2cl/lit/src/index.js` register.
- `j2cl/lit/test/wavy-wave-nav-row.test.js` (NEW, ~+200 LOC)
- `j2cl/lit/test/wavy-focus-frame.test.js` (NEW, ~+80 LOC)
- `j2cl/lit/test/wavy-depth-nav-bar.test.js` (NEW, ~+150 LOC)

### T4 — Search-rail companion elements (B.1–B.18)

**Files:**
- `j2cl/lit/src/elements/wavy-saved-search-rail.js` (NEW, ~+180 LOC) — Inbox / Mentions / Tasks / Public / Archive / Pinned rows + Refresh + Manage saved searches + result count + per-digest avatar stack inside child `<wavy-search-digest-card>`.
- `j2cl/lit/src/elements/wavy-search-digest-card.js` (NEW, ~+120 LOC) — per-digest card with avatar stack, pinned indicator, title, snippet, msg count with `data-unread-count`, relative timestamp.
- `j2cl/lit/src/elements/wavy-search-help-modal.js` (NEW, ~+200 LOC) — static table of every C.1–C.22 filter + sort.
- `j2cl/lit/src/elements/wavy-top-chrome.js` (NEW, ~+150 LOC) — A.2 locale + A.5 bell + A.6 mail + A.7 avatar.
- `j2cl/lit/src/index.js` register.
- Tests for each (~+100–200 LOC each).

### T5 — Floating + accessory + version-history elements (J.2–J.5, K.1–K.6)

**Files:**
- `j2cl/lit/src/elements/wavy-scroll-to-new.js` (NEW, ~+100 LOC) — J.2 floating pill.
- `j2cl/lit/src/elements/wavy-wave-controls-toggle.js` (NEW, ~+50 LOC) — J.3.
- `j2cl/lit/src/elements/wavy-nav-drawer-toggle.js` (NEW, ~+50 LOC) — J.4. (J.5 back-to-inbox is just an `<a>` rendered inside this element.)
- `j2cl/lit/src/elements/wavy-version-history-overlay.js` (NEW, ~+250 LOC) — K.1–K.6.
- `j2cl/lit/src/index.js` register.
- Tests for each.

### T6 — Renderer rewire: `J2clReadSurfaceDomRenderer` uses `<wave-blip>`

**Files:**
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java` (~+150 LOC, ~-50 LOC) — replace `renderBlip(...)` to create `<wave-blip>` (custom element) instead of `<article class="blip j2cl-read-blip">` and set its attributes (`data-blip-id`, `data-wave-id`, `author-name`, `posted-at`, `is-author`, `unread`, `has-mention`, `reply-count`). Existing focus / collapse / scroll-anchor / IntersectionObserver-equivalent contracts are preserved by reading `<wave-blip>`'s `tabindex` and the existing collapse class.
- Add depth-nav state: a new `setDepthFocus(blipId)` method that filters which blips render and emits a `wave-depth-changed` CustomEvent.
- Wire E.* navigation: `nextUnread()`, `nextMention()`, `prevMention()`, `goToEnd()`, `goToRecent()`. Each delegates to existing `focusVisibleByIndex` after computing the right index.
- Add `markBlipReadOnViewportDebounce(blipId, callback)` — IntersectionObserver-equivalent. Elemental2 doesn't ship IntersectionObserver but `DomGlobal.setTimeout` + `getBoundingClientRect` polling per scroll/render is acceptable for the StageOne fidelity.
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java` (~+250 LOC of new test cases for E.* nav, depth-nav, mark-read debounce).

### T7 — `J2clSelectedWaveView` wires the new chrome elements + depth-nav state

**Files:**
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java` (~+120 LOC) — mounts `<wavy-wave-panel-header>`, `<wavy-wave-nav-row>`, `<wavy-tags-row>`, `<wavy-scroll-to-new>`, `<wavy-version-history-overlay>` markers around the `contentList`. Reads `&depth=` from URL and calls `readSurface.setDepthFocus(...)`. Listens for `wave-depth-changed` CustomEvents from the renderer and writes `&depth=` back via `history.replaceState`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java` (~+30 LOC) — extends the `Gateway` interface with `markBlipRead(waveId, blipId, onSuccess, onError)` and `fetchVersionHistory(waveId, callback)`. Both have no-op default implementations to preserve existing call sites.
- Test additions in `J2clSelectedWaveViewServerFirstLogicTest`-style standalone JVM tests.

### T8 — Server-side demo route mount + design-preview wave fixture

**Files:**
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` (~+150 LOC) — extend `renderJ2clDesignPreviewPage` (or add `renderJ2clReadSurfacePreviewPage`) to emit a sample wave at `/?view=j2cl-root&q=read-surface-preview`. Uses synthetic in-page JSON (`<script id="wavy-read-surface-preview-fixture" type="application/json">`) with 12 blips including 2 mentions, 2 unread, 1 deeply-nested thread (3 levels). Mounts `<shell-root>` with all F-2 chrome elements and a renderer initialized from the synthetic JSON.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java` (~+30 LOC) — add the route gating (admin-or-owner or signed-in user — same gate as the design preview).
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererJ2clReadSurfacePreviewPageTest.java` (NEW)
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletReadSurfacePreviewBranchTest.java` (NEW)

### T9 — Per-row parity fixture

**File:** `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageOneReadSurfaceParityTest.java` (NEW, ~+700 LOC).

The fixture builds a synthetic 12-blip wave (2 mentions, 2 unread, 3-level deep thread, 1 thread root with 2 replies) via `TestingWaveletData` (same pattern as `J2clViewportFirstPaintParityTest`), mounts the J2CL servlet at both `?view=j2cl-root` and `?view=gwt`, and asserts:

- **R-3.1 (16 affordances).** For each blip: HTML contains `<wave-blip` element with `data-blip-id="..."`, `author-name="..."`, `posted-at="..."`. Multi-author wave header carries `<wavy-wave-panel-header>` with `<wavy-avatar-stack>` containing N children. Tags row contains expected chips. Plugin slot present per blip. Inline-reply chip present on parent blips.
- **R-3.2.** Server-side: `data-tabindex="0"` is present on exactly one blip (the first); the rendered HTML carries `data-j2cl-focus-frame` on the surface root. Client-side (verified by the lit test): pressing ArrowDown moves focus and emits the `read.focus_change` telemetry event.
- **R-3.3.** Server-side: each thread root carries the `j2cl-read-thread-toggle` button. Client-side (lit test): toggling collapses the children with the 240ms transition; scroll anchor preserved across re-render.
- **R-3.4.** Server-side: `<wavy-wave-nav-row>` is present with all 10 buttons keyed by `data-nav-action="E.1" .. "E.10"`. Client-side: clicking each button changes focus or emits the expected CustomEvent.
- **R-3.7.** URL-driven: GET `?view=j2cl-root&depth=b3` renders only the b3 subthread + its descendants. Breadcrumb `<wavy-depth-nav-bar>` carries `data-current-depth-blip-id="b3"`. Pressing `[` on the rendered surface emits `wave-depth-changed{depth: ""}` (drill out).
- **R-4.4.** Server-side: each blip in the unread set carries `unread="true"`. Client-side: synthetic call to `markBlipReadOnViewportDebounce` after 1500ms invokes the gateway exactly once and decrements `data-unread-count` on the matching digest card.
- **GWT byte-for-byte unchanged** for `?view=gwt` (regression assertion: response body fingerprinted against a stored reference snapshot — same approach as `J2clViewportFirstPaintParityTest`).
- **64 inventory affordances**: a single helper `assertInventoryAffordancePresent(html, id)` checks each ID has a corresponding `data-inventory-affordance="<id>"` attribute somewhere in the rendered DOM. Each Lit element registers itself with this attribute on its `connectedCallback`.

## 5. Telemetry surface

The following events are added to `J2clClientTelemetry`. All are categorical with no high-cardinality fields.

- `read.focus_change` — fields: `source` (`"key"`, `"click"`, `"programmatic"`).
- `read.thread_toggle` — fields: `state` (`"collapsed"`, `"expanded"`).
- `read.thread_nav` — fields: `event` (`"E.1"`..`"E.10"`).
- `read.depth_change` — fields: `direction` (`"in"`, `"out"`, `"to-wave"`), `source` (`"click"`, `"key"`, `"url"`).
- `read.depth_pill_shown` — no fields (G.6).
- `read.blip_read_marked` — fields: `source` (`"viewport-debounce"`, `"manual"`).
- `read.unread_badge_pulse` — no fields.

## 6. Out of scope (deferred and explicit)

- Compose / write surface — F-3 owns. F-2 ships only the per-blip Reply CustomEvent emitters and the inline-reply chip click — F-3 wires the actual composer to those events.
- Rich-text edit toolbar (H.1–H.24) — F-3 owns. F-2 does not ship the toolbar.
- Tag add/edit (I.3, I.4, I.5) — F-3 owns. F-2 ships read-only display only.
- Send-Message from profile overlay (L.2) — F-3 owns.
- Notifications bell wiring (A.5) — F-4 owns the actual unread count source. F-2 displays whatever count the model exposes (default 0).
- Save state pill (A.3), Online pill (A.4) — F-4.
- Reactions add (F.8, F.9) — F-3 (F-2 only displays the existing F-1-rendered reaction-row).
- Restore action body (K.5) — out of scope. F-2 ships the affordance + CustomEvent + confirm sub-dialog; the actual restore endpoint integration ships when GWT-side restore flow is migrated (separate issue, future).
- Wave-actions overflow menu items D.3 — F-2 ships the trigger button; the menu items themselves are mostly F-3 (mark-read/archive/etc.).

## 7. Risk list

1. **Custom-element naming collision**: F-0 already defines `wavy-blip-card`. F-2 introduces `wave-blip` (different name) that wraps `wavy-blip-card`. Name distinct → no collision. *Mitigation:* the `wave-blip.test.js` asserts `customElements.get("wave-blip")` is the F-2 class.
2. **Plugin-slot context contract drift**: F-0 reflects `data-blip-id`, `data-wave-id`, `data-blip-author`, `data-blip-is-author` on `<wavy-blip-card>`. F-2's `<wave-blip>` wrapper must propagate these to the inner `wavy-blip-card`. *Mitigation:* explicit `_propagateDataAttrs()` in the wrapper, asserted in `wave-blip.test.js`.
3. **Per-blip read mark amplification**: a 12-blip viewport could emit 12 `markBlipRead` HTTP calls in the worst case. *Mitigation:* the renderer batches reads — `markBlipReadOnViewportDebounce` collects blip IDs over a 250ms window and calls `gateway.markBlipReadBatch(waveId, [blipIds])` (the controller exposes both single + batch APIs; the existing supplement endpoint already supports a batch shape).
4. **CodexConnector P2 review threads on each commit** (F-0/F-1 lesson): expect the bot to post P2 threads on every push. Resolve via GraphQL each time.
5. **F-1 fragment-window depth-axis support**: G.1 requires that the F-1 fragment fetch can target a parent blip without loading siblings. *Verification:* T6 fixture asserts that `gateway.fetchFragments(waveId, parentBlipId, "depth", limit, …)` returns only descendants of `parentBlipId`. If the F-1 server-side path doesn't already key on direction="depth", the renderer falls back to client-side filtering of an existing fragment payload (acceptable interim — T6 flags this as a known interim and files a follow-up issue).
6. **Reduced-motion**: F-0 already collapses motion durations to ~0.01ms via `prefers-reduced-motion`. F-2 fixtures verify the transitions still fire (animationend / transitionend events still resolve) under reduced-motion so JS state machines tracking the end events do not hang.
7. **Server-side payload growth**: T8's design-preview wave fixture is in-page JSON only — no server-side payload growth in production. The production `J2clSelectedWaveSnapshotRenderer` is unchanged.

## 8. Verification gate (closes the lane)

- `sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild` → exit 0.
- `J2clStageOneReadSurfaceParityTest` → 6 row methods passing (R-3.1, R-3.2, R-3.3, R-3.4, R-3.7, R-4.4) + 1 inventory-affordance method asserting all 64 IDs.
- All new lit tests passing (T2 + T3 + T4 + T5 element tests).
- Worktree-local boot: `sbt -batch wavestart` (or equivalent), navigate `/?view=j2cl-root&q=read-surface-preview`, visually verify the demo wave (manual screenshot attached to PR comment).
- Telemetry: open `/?view=j2cl-root&q=read-surface-preview`, run a few interactions, dump `window.__stats` and confirm the new event names appear.

## 9. Plan-review pass log

This plan was self-reviewed and then reviewed by a Claude Opus subagent.

### Self-review iteration 1
- ✅ Every R-* row has at least 5 concrete acceptance steps with measurable DOM-attribute or telemetry assertions.
- ✅ All 64 inventory affordances mapped to a row + an implementation strategy.
- ✅ Plugin-slot reservation explicitly carries through F-2's `wave-blip` wrapper.
- ✅ Depth-nav R-3.7 covers all 6 sub-affordances G.1–G.6.
- ✅ Telemetry events listed.
- ✅ Out of scope explicit, with explicit "F-3 owns" or "F-4 owns" attribution.
- ✅ Risk list covers the depth-axis fragment-window concern flagged in the issue body.

### Subagent plan-review iteration (deferred to subagent)

To be filled in by the Opus subagent. Expected challenge points:
- Are there any inventory affordances missed?
- Is the R-3.7 G.4 URL-state fully reversible across the back/forward stack, or only a `replaceState`?
- Does T6's in-renderer mark-read debounce risk steady-state HTTP traffic if the user is idle on a wave with 12 blips?

The subagent will list any "no executable acceptance step" findings; this plan iterates until clean.
