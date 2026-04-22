# J2CL GWT Parity Matrix

Status: Proposed  
Owner: Project Maintainers  
Updated: 2026-04-22  
Review cadence: on-change  

Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Task issue: [#961](https://github.com/vega113/supawave/issues/961)
Related: [`docs/j2cl-parity-architecture.md`](./j2cl-parity-architecture.md),
[`docs/j2cl-parity-issue-map.md`](./j2cl-parity-issue-map.md),
[`docs/j2cl-lit-implementation-workflow.md`](./j2cl-lit-implementation-workflow.md)
Template: [`docs/j2cl-parity-slice-packet-template.md`](./j2cl-parity-slice-packet-template.md)

## 1. Purpose

This document is the acceptance contract for every downstream J2CL/Lit parity
slice under `#904`. It freezes the GWT behavior the J2CL client must match
before any default-root cutover can be reconsidered.

The reviewed issue map (PR #972) defined *which* slices exist and *in what
order*. This matrix defines *what each slice must preserve* and *how parity is
proved*. Without it, `#963`–`#971` would each re-derive acceptance criteria,
silently drifting from observed GWT behavior.

Scope bounds:

- this is a behavior inventory, not an architecture memo
- it does not change the framework/runtime direction established in
  `docs/j2cl-parity-architecture.md`
- it does not open or re-scope any GitHub issue; the chain `#961`–`#971`
  remains governed by the issue map
- it does not perform any Lit/J2CL implementation

## 2. How To Use This Matrix

Every parity slice in Section 4 of the issue map must:

1. fill in the per-slice packet
   ([template](./j2cl-parity-slice-packet-template.md)) inside its own GitHub
   issue body or issue-scoped plan before implementation begins;
2. cite the exact matrix row IDs below under "Parity matrix rows claimed"
   (row IDs are stable anchors of the form `R-<section>.<n>`, e.g. `R-3.2`);
3. declare rollout flag, telemetry/observability checkpoints, browser-harness
   coverage, and verification shape consistent with the rows it claims;
4. treat any row not claimed by an existing slice as deferred, not dropped —
   new coverage must be added either to an existing packet or to the
   addendum in Section 10.

Row-sourcing rules enforced by review:

- each matrix section cites at least one concrete GWT seam (file +
  approximate line range) or a merged parity architecture reference in its
  "Origin" prefix; rows in that section inherit that sourcing unless a row
  adds a more specific citation
- rows describe observable behavior, not implementation detail
- visual latitude is expressed as what *may* change, so implementers do not
  default to pixel-level replication where the architecture memo explicitly
  allows modernization

Column legend (applies to every table below):

| Column | Meaning |
| --- | --- |
| ID | Stable anchor used by downstream slice packets |
| Target behavior | Observable capability the J2CL/Lit surface must expose |
| Required to match GWT | Behaviors/semantics that must not regress |
| Allowed to change visually | Latitude the Lit/design-system packet may use |
| Keyboard / focus | Keyboard, caret, and focus-frame expectations |
| Accessibility | Roles, labels, announcements, and contrast expectations |
| i18n | Locale/RTL/translation expectations |
| Browser harness | Expected coverage in the automated browser harness |
| Telemetry / observability | Signals required at the parity gate |
| Verification shape | Smoke / browser / harness combination required |
| Downstream slices | Issue numbers from the issue map that own this row |

Verification shapes reuse
[`docs/runbooks/browser-verification.md`](./runbooks/browser-verification.md)
and
[`docs/runbooks/change-type-verification-matrix.md`](./runbooks/change-type-verification-matrix.md):

- **smoke** — `worktree-boot.sh` + `wave-smoke.sh start|check|stop`
- **browser** — narrow manual or scripted path on `http://localhost:<port>/`
- **harness** — descendant of the GWT browser harness tracked in the
  GWTTestCase verification matrix

## 3. Read Surface (StageOne-Origin)

Origin: `wave/src/main/java/org/waveprotocol/wave/client/StageOne.java:44-197`.

The read surface owns wave-panel rendering, focus framing, collapse, thread
navigation, and the view provider that reads semantics from DOM. The J2CL
baseline currently has narrow selected-wave rendering but no durable read
container model. Downstream coverage: `#965`, `#966`, `#967`.

| ID | Target behavior | Required to match GWT | Allowed to change visually | Keyboard / focus | Accessibility | i18n | Browser harness | Telemetry / observability | Verification shape | Downstream slices |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R-3.1 | Open-wave rendering — wave panel assembles blip/thread DOM from conversation view | Blips, threads, and inline replies appear with stable identity; deep nesting renders without layout collapse; reply ordering matches conversation model | Shell chrome, spacing, typography, and component styling may change per the Lit design packet | Focus enters a predictable first blip on open; focus state is preserved through incremental updates | Conversation tree exposes landmark/list structure; blip regions carry reachable labels | Existing locale fallback and RTL mirroring for blip content preserved | New read-surface harness coverage descends from existing StageOne-era tests in the GWTTestCase matrix | First-render timing and blip count signal emitted via the existing client stats channel | smoke + browser + harness | `#965`, `#966` |
| R-3.2 | Focus framing — `FocusFramePresenter`-equivalent selection indicator across blips | Arrow/tab navigation moves the frame; frame survives incremental render and collapse toggles | Frame visuals (color, outline, animation) may change | Arrow-key, `j`/`k`-style, and shift+tab semantics preserved where they exist today | Focused blip is announced as the active region; focus outline meets contrast on light and dark themes | No new locale-specific behavior required | Keyboard-navigation path covered by harness fixtures | Focus-change events are observable (count/target) for regression detection | smoke + browser + harness | `#966` |
| R-3.3 | Collapse — thread collapse/expand toggles | Collapse preserves scroll anchor, read state, and child visibility rules; collapsed threads remain reachable via keyboard | Toggle affordance and animation may change | Space/enter on the toggle works; keyboard focus does not jump away on expand | Toggle announces collapsed/expanded state | Toggle label respects locale | Collapse/expand covered by harness fixtures | Collapse toggle count emitted | smoke + browser + harness | `#966` |
| R-3.4 | Thread navigation — next/previous unread and deep-reply jumps (`ThreadNavigationPresenter`) | Unread-aware navigation order matches GWT when unread state exists (after `#931`); wrap/clamp behavior preserved | Control placement and icon set may change | Existing shortcut bindings preserved | Navigation controls carry reachable labels and discoverable shortcuts | Labels translate | Harness fixture covers at least one multi-thread wave | Navigation events (direction, target blip id) emitted | smoke + browser + harness | `#931`, `#966` |
| R-3.5 | Visible-region container model — read surface can be composed from visible fragment sections | Panel can render when only a visible window is hydrated; scroll into unloaded range triggers load without layout thrash | Loading placeholders may use Lit design-packet styling | Scrolling does not steal focus; keyboard scroll shortcuts still work | Placeholders announce loading state to AT | Existing directional/RTL scroll preserved | Harness has a fragment-window fixture covering initial and grow cases | Visible-window extension and clamp events emitted | smoke + browser + harness | `#967` |
| R-3.6 | DOM-as-view provider — read surface can expose semantic views from DOM | Semantic queries used by keyboard, focus, collapse, and menu code work against the new container | Underlying element tagging may change, as long as the provider stays consistent | n/a (infrastructural) | n/a (infrastructural) | n/a | Harness continues to resolve semantic views for existing fixtures | Internal assertion/log signal when provider fails to resolve a view | smoke + harness | `#966`, `#967` |

## 4. Live Surface (StageTwo-Origin)

Origin: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java:194-469`
and `:1013-1184` for dynamic rendering/fragments wiring. Current J2CL
sidecar/root-shell seams are transitional and controller-local. Downstream
coverage: `#933`, `#936`, `#963`, `#967`, `#968`.

| ID | Target behavior | Required to match GWT | Allowed to change visually | Keyboard / focus | Accessibility | i18n | Browser harness | Telemetry / observability | Verification shape | Downstream slices |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R-4.1 | Socket/session lifecycle | Live socket opens on authenticated load; session identity matches server-issued session; disconnect is recoverable | n/a (non-visual) | n/a | n/a | n/a | Harness covers open + forced close + reopen | Connection open/close/error counters emitted | smoke + harness | `#933`, `#968` |
| R-4.2 | Bootstrap contract — route/session/socket metadata reaches the client without scraping arbitrary root HTML | J2CL no longer scrapes `/` HTML for session/bootstrap state; bootstrap JSON is stable and versioned; legacy GWT root continues to boot unchanged | Server HTML shell may change | n/a | n/a | n/a | Harness boots against both legacy and J2CL roots | Bootstrap success/failure signals with reason codes | smoke + browser + harness | `#963`, `#965` |
| R-4.3 | Reconnect | Transient socket loss recovers without user action; pending ops are not lost silently; route/selected-wave state is preserved across reconnect | Reconnect banner styling may change | Focus is not stolen by reconnect | Reconnect announces to AT (live region) | Banner label translates | Harness simulates transient drop + recover | Reconnect attempt/outcome counters emitted | smoke + browser + harness | `#968` |
| R-4.4 | Read/unread state live updates | Per-user read/unread state updates without page reload; digest counts match selected-wave state (`#931`) | Badge styling may change | n/a | Counts exposed to AT | Counts localize | Harness covers multi-wave unread transitions | Unread state change events emitted | smoke + browser + harness | `#931`, `#968` |
| R-4.5 | Route/history integration | Back/forward, deep links to wave/folder, and signed-in/out transitions work; state survives reload | Shell chrome may change | Focus lands predictably after navigation | Navigation announced | Route labels translate | Harness covers at least one deep-link path | Route transitions counted per type | smoke + browser + harness | `#968` |
| R-4.6 | Fragment fetch policy — viewport-scoped loading | Visible-region requests honor the existing client hints and server clamps; whole-wave fallback is not the default for large waves | Loading affordances may change | Scroll-driven fetch does not lose focus | Loading regions announced | n/a | Harness fragment fixture exercises initial + extension | Fragment fetch counts, hit/miss, clamp applied | smoke + browser + harness | `#967`, `#968` |
| R-4.7 | Feature activation and live-update application | Active features (search, supplement, diff controller, reader) remain activated in the live surface; live updates apply without full rerender | n/a (non-visual) | n/a | n/a | n/a | Harness smoke fixture exercises live update application | Active-feature presence assertions logged | smoke + harness | `#968` |
| R-4.8 | Selected-wave version/hash basis atomicity | Writes use an atomic version/hash basis; no silent drift between read snapshot and submit basis | n/a | n/a | n/a | n/a | Harness covers submit-under-race fixture | Version/hash mismatch assertions emitted | smoke + harness | `#936` |

## 5. Compose / Edit Surface (StageThree-Origin)

Origin: `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java:66-301`.
Current J2CL compose is a narrow write pilot. Downstream coverage:
`#969`, `#970`, `#971`.

| ID | Target behavior | Required to match GWT | Allowed to change visually | Keyboard / focus | Accessibility | i18n | Browser harness | Telemetry / observability | Verification shape | Downstream slices |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R-5.1 | Compose / reply flow | Daily compose and reply paths emit conversation-model ops equivalent to GWT; drafts do not corrupt document state; version/hash basis is atomic (`#936`) | Composer chrome may change per the design packet | Enter-to-send semantics, newline handling, and caret survival during live updates preserved | Composer is a reachable and labeled region; submission has an accessible control | Composer text direction respects user locale; placeholders translate | Harness has a compose-and-submit fixture | Compose open/submit counts with success/failure reason | smoke + browser + harness | `#969` |
| R-5.2 | View and edit toolbars | Daily formatting controls (bold/italic/list/link/heading) remain reachable and functional; state toggles mirror selection | Toolbar layout, grouping, and iconography may change | Shortcut bindings preserved where they exist; toolbar is keyboard-reachable | Toolbar controls carry roles/labels and respect pressed state | Labels and tooltips translate | Harness exercises at least one formatting and one view toggle | Toolbar action counts emitted | smoke + browser + harness | `#969` |
| R-5.3 | Mentions and autocomplete | Mention trigger, suggestion selection, and submit semantics preserved; mention identity round-trips through the model | Suggestion popover styling may change | Arrow-key navigation and enter/escape semantics preserved | Suggestions list exposes listbox semantics | Locale-aware matching preserved | Harness covers mention insertion fixture | Mention pick/abandon counts emitted | smoke + browser + harness | `#970` |
| R-5.4 | Tasks and related metadata overlays | Task toggle, completion state, and associated metadata overlays preserved; state persists through live updates | Overlay visuals may change | Keyboard toggling preserved | Overlays announce state changes | Labels translate | Harness covers task-toggle fixture | Task state-change counts emitted | smoke + browser + harness | `#970` |
| R-5.5 | Reactions and comparable interaction overlays | Reaction add/remove and counts preserved; overlays remain reachable and do not trap focus | Reaction set, chip styling, and placement may change | Keyboard add/remove preserved | Reaction counts announced | Counts localize | Harness covers at least one reaction fixture | Reaction add/remove counts emitted | smoke + browser + harness | `#970` |
| R-5.6 | Attachment workflow | Daily attachment upload/download/open paths preserved; failure modes surface user-visible errors; attachments round-trip through the model | Attachment tile styling may change | Attachment affordances are keyboard-reachable | Attachment regions labeled; errors announced | Error text translates | Harness covers one upload and one download fixture | Attachment upload/download counts with outcomes | smoke + browser + harness | `#971` |
| R-5.7 | Remaining rich-edit daily affordances | Daily-path rich-edit behaviors identified by the packet for `#971` (for example: lists, block quotes, inline links) preserved | Visuals may change per the design packet | Keyboard semantics for the affordances preserved | Affordances labeled | n/a | Harness covers at least one fixture per affordance | Action counts emitted | smoke + browser + harness | `#971` |

Non-daily editor edge cases that do not ship in `#971` are captured in
Section 10 rather than silently dropped.

## 6. Server-First First Paint And Shell Swap

Origin: `wave/src/main/java/org/waveprotocol/box/server/rpc/render/WavePreRenderer.java:40-161`
and the `enable_prerendering` flag in `wave/config/reference.conf:127-132`.
Downstream coverage: `#963`, `#965`.

| ID | Target behavior | Required to match GWT | Allowed to change visually | Keyboard / focus | Accessibility | i18n | Browser harness | Telemetry / observability | Verification shape | Downstream slices |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R-6.1 | Server-rendered read-only first paint | Route shell, signed-in/out chrome, and selected-wave/visible-fragment HTML arrive before full client activation; content is readable without JS for the visible region | Shell/chrome styling may change | First focus target is predictable and does not jump on upgrade | Server HTML alone is AT-usable for read-only content | Locale text respects user preference on server HTML | Harness measures first-paint content presence before client boot signal | First-paint timing and shell-swap success/failure events emitted | smoke + browser + harness | `#965` |
| R-6.2 | Bootstrap JSON contract (paired with R-4.2) | Bootstrap JSON carries user/session/route/wave ids and visible-fragment hints; versioned and validated; rollback-safe | n/a (non-visual) | n/a | n/a | n/a | Harness asserts bootstrap JSON shape for both roots | Bootstrap JSON version and reject reasons emitted | smoke + harness | `#963` |
| R-6.3 | Shell-swap upgrade path | J2CL boot upgrades server HTML in place; no unstyled flash; no duplicate roots during upgrade | Transition may restyle | Focus is not stolen by the upgrade | ARIA live regions quiesce on upgrade | n/a | Harness covers shell-swap fixture | Shell-swap event with success/failure + reason code | smoke + browser + harness | `#965` |
| R-6.4 | Rollback-safe coexistence | `/?view=j2cl-root` remains a direct route; legacy GWT at `/` remains the default until parity gate is met; operator-level toggle remains reversible | Styling of debug routes may change | n/a | n/a | n/a | Harness covers both roots | Route-selection and rollback-path signals emitted | smoke + harness | `#963`, `#965` |

## 7. Viewport-Scoped Fragment Windows

Origin: dynamic rendering/fragments wiring at
`wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java:1013-1184`,
transport viewport hints at
`wave/src/main/java/org/waveprotocol/box/webclient/client/RemoteViewServiceMultiplexer.java:183-224`,
server clamp at
`wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java:371-428`,
configuration knobs at `wave/config/reference.conf:405-441`. Downstream
coverage: `#967`, `#968`.

| ID | Target behavior | Required to match GWT | Allowed to change visually | Keyboard / focus | Accessibility | i18n | Browser harness | Telemetry / observability | Verification shape | Downstream slices |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R-7.1 | Initial visible window | Initial window size, clamp, and selection preserve existing server behavior | Placeholder styling may change | Initial focus is stable | Initial window is AT-usable | n/a | Harness covers initial-window fixture | Initial-window size/clamp events emitted | smoke + browser + harness | `#967` |
| R-7.2 | Extension on scroll | Extending into unloaded range fetches and applies fragments without losing scroll anchor | Extension affordances may restyle | Scroll does not steal focus | Extension announces loading | n/a | Harness extension fixture | Extension fetch counts and outcomes | smoke + browser + harness | `#967` |
| R-7.3 | Server clamp behavior | Server-applied clamps and limits remain explicit and visible through telemetry; client does not override server clamp silently | n/a (non-visual) | n/a | n/a | n/a | Harness clamp fixture | Clamp applied/rejected counters emitted | smoke + harness | `#967`, `#968` |
| R-7.4 | No regression to whole-wave bootstrap for large waves | Large-wave open never falls back to whole-wave payload when viewport-scoped path is available | n/a | n/a | n/a | n/a | Harness covers large-wave fixture | Fallback to whole-wave bootstrap emits a warning counter | smoke + harness | `#967` |

## 8. Parity Gate

The parity gate enumerates the rows that must all be closed before the
future opt-in default-root bootstrap item (issue-map §5.1) may even be
opened as a GitHub issue.

This section is descriptive only. It captures the parity threshold; it does
not grant authority to open the items in issue-map §5.1, §5.2, or §5.3.
Those items remain gated by the issue map and `#904`, and their section
numbers are not GitHub issue IDs.

Gate rows (must all be closed, with evidence linked from each slice packet):

- read surface: `R-3.1`, `R-3.2`, `R-3.3`, `R-3.4`, `R-3.5`
- live surface: `R-4.1`, `R-4.2`, `R-4.3`, `R-4.4`, `R-4.5`, `R-4.6`, `R-4.8`
- compose / edit: `R-5.1`, `R-5.2`, `R-5.3`, `R-5.4`, `R-5.5`, `R-5.6`
- server-first: `R-6.1`, `R-6.2`, `R-6.3`, `R-6.4`
- fragments: `R-7.1`, `R-7.2`, `R-7.3`, `R-7.4`

`R-3.6` and `R-4.7` are infrastructural enablers: they must be satisfied as a
by-product of their owning slices, and are not gate rows on their own.
Infrastructural rows are validated by their owning slice's harness fixture
rather than by a dedicated gate entry.

`R-5.7` must close only for the daily-path affordances the `#971` packet
enumerates; any other rich-edit affordances remain deferred per Section 10.

## 9. Cross-Slice Dependencies

| Row | Depends on |
| --- | --- |
| R-3.5 | R-4.6, R-7.1, R-7.2 |
| R-4.2, R-6.2 | each other — bootstrap JSON contract is a single change |
| R-4.4 | `#931` |
| R-4.8 | `#936` |
| R-4.1, R-4.3 | `#933` for HttpOnly-compatible auth |
| R-5.\* | R-3.\* and R-4.\* reaching gate state for the same slice |
| R-6.1, R-6.3 | R-4.2, R-6.2 |

These are behavior dependencies, not scheduling claims. The issue map owns the
scheduling chain.

## 10. Deferred Edge Cases

Legacy GWT behavior that is explicitly out of scope for the parity gate is
recorded here so it is not silently dropped:

- non-daily editor edge cases not captured by R-5.1, R-5.2, or R-5.7 (for
  example: rare legacy formatting paths, one-off keyboard shortcuts that do
  not appear in the `#969`/`#971` packet) — to be revisited only after the
  gate closes or via a dedicated addendum packet linked from this section
- browser-harness descendants whose GWT-only assumptions are incompatible with
  the Lit/J2CL surface — accounted for in the GWT retirement issue
  (future `#5.3` per the issue map), not in the parity-acquisition chain
- Lit SSR for any surface beyond the server-rendered first-paint read region —
  deferred per the parity architecture memo

Downstream slices that discover additional deferred behavior must add a
bullet here rather than silently omit it.

## 11. Change Policy

- New rows or amendments are made by adding an entry to this file in a
  reviewed PR. The review should be run under Claude Opus 4.7 consistent with
  the workflow defined in `AGENTS.md`.
- Row IDs are append-only. Removed behavior is marked deprecated in place so
  existing slice packets do not break their anchors.
- The `Updated:` metadata field must be refreshed on every edit.
