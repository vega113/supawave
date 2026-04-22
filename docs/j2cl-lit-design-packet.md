# J2CL Lit Design Packet

Status: Proposed
Owner: Project Maintainers
Updated: 2026-04-22
Review cadence: on-change

Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Task issue: [#962](https://github.com/vega113/supawave/issues/962)
Related:
[`docs/j2cl-parity-architecture.md`](./j2cl-parity-architecture.md),
[`docs/j2cl-lit-implementation-workflow.md`](./j2cl-lit-implementation-workflow.md),
[`docs/j2cl-parity-issue-map.md`](./j2cl-parity-issue-map.md),
[`docs/j2cl-gwt-parity-matrix.md`](./j2cl-gwt-parity-matrix.md),
[`docs/j2cl-parity-slice-packet-template.md`](./j2cl-parity-slice-packet-template.md)

## 1. Purpose

This is the first parity-safe Lit design packet. It is the design-side
companion to the parity matrix: the matrix freezes *what behavior must be
preserved*, this packet freezes *what visual vocabulary and component
families Lit slices may build from*.

Every downstream Lit/J2CL parity slice in issue map §4.4–§4.11 (`#964`,
`#965`, `#966`, `#967`, `#968`, `#969`, `#970`, `#971`) must cite this
packet when the slice touches visual structure. A slice that is not on the
visual critical path (e.g. `#963` bootstrap JSON, existing follow-ups
`#933`/`#936`) does not need to consume this packet.

What this packet **is**:

- a committed design-token vocabulary (semantic slots + intent) for Lit work
- a committed component-family inventory grouped to match the parity matrix
  stages (read, live, compose, server-first, fragments)
- a committed Stitch artifact policy: Required, Optional, or Prohibited per
  family, with the consuming slice named when required
- a restatement of cross-cutting rules (accessibility, keyboard/focus, i18n,
  RTL, motion/reduced-motion) that downstream component slices inherit

What this packet **is not**:

- not a replacement for the parity matrix; it cites matrix rows, it does
  not define behavior
- not an implementation artifact; it does not ship Lit source, CSS, token
  files, or build plumbing
- not a Stitch project; it defines the *rules* the first Stitch projects
  (owned by `#964`, `#966`, and — per issue map §7 — potentially `#969`)
  must follow
- not authoritative over transport, auth, bootstrap, socket, unread
  modeling, version/hash, fragment-window logic, route state, or feature
  flags; those remain J2CL-owned per the parity architecture memo and
  issue map §7
- not a declaration that any slice is ready to implement; the slice packet
  template still governs slice-level acceptance

## 2. How To Use This Packet

1. A slice planner consulting this packet picks:
   - the component families the slice depends on (§5)
   - the parity matrix row anchors the slice claims (from
     [`docs/j2cl-gwt-parity-matrix.md`](./j2cl-gwt-parity-matrix.md))
   - the Stitch artifact requirement for each family (§6)
2. The slice packet (per
   [`docs/j2cl-parity-slice-packet-template.md`](./j2cl-parity-slice-packet-template.md))
   records: the matrix rows claimed, the component families consumed from
   this packet, and — if any family is Stitch-Required — the pinned Stitch
   project/screen/variant identifiers in the packet's "Server / client surface
   list" or "Required-match behaviors" blocks.
3. Any visual modernization a slice performs is bounded by what this packet
   declares as *allowed*. New tokens, families, or variants must go through
   a PR against this packet before a slice can consume them.

Stability contract:

- token slot names and component-family anchors are append-only while the
  packet is `Proposed`; deprecated slots or families are marked in place so
  downstream slice packets do not break their citations
- the `Updated:` metadata field is refreshed on every edit
- Required-Stitch rows must name a consuming slice; rows without a named
  consumer are demoted to Optional

## 3. Visual Direction

"Wavy-modern" at the packet level means:

- **progressive-enhancement-friendly** — every visual primitive works on
  server-rendered read-only HTML before Lit upgrades it; no unstyled flash
  between server HTML and the Lit-upgraded surface
- **read-first** — the highest-fidelity styling is spent on the read
  surface (open-wave rendering, focus, thread navigation) because that is
  the daily path
- **discreet chrome** — shell/header/nav surfaces defer visually to wave
  content; they do not compete for attention
- **component boundaries over global flourish** — modernization lives in
  reusable component primitives, not in layout-level one-offs that cannot
  be adopted by future slices
- **accessible by default** — focus, labels, and contrast are tokenized at
  the packet level so no component slice has to re-derive them

Image-generation artifacts (moodboards, material/atmosphere studies) may
*inform* this direction but never *ratify* it. See §7.

## 4. Design Tokens

Tokens are named as semantic slots with intent. Numeric values are chosen
by the first downstream slice that adopts the slot, pinned into a Stitch
design system, and reviewed under that slice's PR. The goal here is a
stable vocabulary, not a values table.

### 4.1 Typography

- `type-family-reading` — default body/reading font family; optimized for
  wave content legibility at body-text density
- `type-family-ui` — UI chrome font family; may equal reading family for
  the first slice but remains a distinct slot
- `type-family-mono` — code/inline-code/attachment-metadata font family
- `type-scale-body` — default reading size
- `type-scale-body-dense` — denser reading size for fragment-region
  placeholders and lists
- `type-scale-heading-1`..`type-scale-heading-3` — wave/section/thread
  headings
- `type-scale-meta` — metadata and timestamp size
- `type-scale-caption` — caption/overline size
- `type-weight-regular`, `type-weight-medium`, `type-weight-bold`
- `type-lineheight-body`, `type-lineheight-tight`, `type-lineheight-loose`
- `type-letterspacing-default`, `type-letterspacing-allcaps`

### 4.2 Color

Colors are expressed as semantic roles; each role resolves to a light and
dark surface value. Contrast expectations are recorded in §8.1.

- `color-surface-page` — application background behind shell
- `color-surface-shell` — header/nav/footer chrome
- `color-surface-wave` — wave panel and blip card background
- `color-surface-overlay` — menu/popover/toast background
- `color-surface-skeleton` — server-rendered read-only skeleton background
- `color-text-primary`, `color-text-secondary`, `color-text-muted`
- `color-text-link`, `color-text-link-hover`, `color-text-link-visited`
- `color-accent-brand` — primary brand/accent slot
- `color-accent-focus` — focus ring tint
- `color-accent-selection` — focused blip and caret selection tint
- `color-state-unread`, `color-state-read` — unread/read indicator tints
  (visual only; live state ownership stays in J2CL; see §5.3)
- `color-state-success`, `color-state-warning`, `color-state-error`,
  `color-state-info`
- `color-divider-strong`, `color-divider-subtle`
- `color-scrim` — modal scrim tint

### 4.3 Spacing

- `space-0` through `space-8` — stepwise spacing scale; exact ratio pinned
  by the first consuming slice
- `space-inline-tight`, `space-inline-default`, `space-inline-loose` —
  inline gap variants for toolbar-like groups
- `space-stack-tight`, `space-stack-default`, `space-stack-loose` —
  vertical stack gap variants
- `space-inset-panel`, `space-inset-card`, `space-inset-toolbar`,
  `space-inset-overlay` — padding insets keyed to container role

### 4.4 Shape

- `radius-surface-sm`, `radius-surface-md`, `radius-surface-lg`
- `radius-control-sm`, `radius-control-md`, `radius-control-pill`
- `border-width-hairline`, `border-width-default`, `border-width-strong`

### 4.5 Elevation

- `elevation-flat` — server HTML / in-flow surfaces
- `elevation-raised` — shell chrome
- `elevation-overlay` — menu/popover/toast
- `elevation-modal` — modal/dialog
- `shadow-overlay`, `shadow-modal` — paired shadow slots

### 4.6 Motion

- `motion-duration-instant` — under-threshold feedback (<=80ms)
- `motion-duration-quick` — chrome transitions
- `motion-duration-standard` — collapse/expand, panel enter/exit
- `motion-duration-slow` — full-page or shell-level transitions
- `motion-easing-standard`, `motion-easing-emphasized`,
  `motion-easing-decelerated`
- Reduced-motion rule: every motion slot MUST collapse to the zero-motion
  variant when `prefers-reduced-motion: reduce`; downstream slices inherit
  this without re-derivation (§8.3).

### 4.7 Density

- `density-comfortable` — default for desktop wave reading
- `density-compact` — default for list/digest rails and dense fragment
  windows
- `density-mobile` — touch-first density for small viewports

### 4.8 Iconography

- `icon-family-primary` — primary icon set; outlined style preferred for
  parity-safe chrome; filled style reserved for state-on affordances
- `icon-size-sm`, `icon-size-md`, `icon-size-lg`
- `icon-stroke-default`, `icon-stroke-emphasized`

Icon inventory per component family is owned by the consuming slice's
Stitch project.

### 4.9 Focus And Selection

- `focus-ring-style` — focus outline treatment; must meet contrast in both
  light and dark (§8.1) and must remain visible on
  `color-surface-wave`/`color-surface-shell`
- `focus-ring-offset`, `focus-ring-width`
- `selection-indicator-style` — focused blip indicator (paired with
  `color-accent-selection`)

### 4.10 Z-Index Layers

- `z-surface-base` — in-flow content
- `z-surface-shell` — pinned shell chrome
- `z-overlay-menu` — menus/popovers/tooltips/suggestions
- `z-overlay-toast` — transient toasts
- `z-overlay-modal` — modal/dialog
- `z-overlay-scrim` — modal scrim (below its dialog)

## 5. Component Families

Each family lists: purpose, variants in scope for first adoption, parity
matrix rows supported, and consuming slice(s). Families are **visual
vocabulary only**; state, transport, and lifecycle stay in J2CL per the
parity architecture memo.

### 5.1 Shell / Chrome Primitives

Purpose: shared root shell, signed-in/signed-out chrome, navigation rail,
skip links, and status strip. Visual container for every J2CL route.

Variants:
- `shell-root` — default signed-in root shell
- `shell-root-signed-out` — landing/signed-out variant
- `shell-header` — top chrome, density-aware
- `shell-nav-rail` — primary navigation rail
- `shell-main-region` — reading-focused main region
- `shell-status-strip` — ambient status/connection strip (visual surface
  for R-4.3 indicators; behavior in J2CL)
- `shell-skip-link` — skip-to-main affordance (accessibility-mandated)

Matrix rows supported: `R-6.1`, `R-6.3`, `R-6.4` (server-first and
coexistence chrome), and the visual container for `R-3.1`–`R-3.5` and
`R-4.5` content.

Consuming slice(s): `#964`, `#965`, `#968`.

### 5.2 Read-Surface Primitives

Purpose: the wave panel and its in-flow constituents. This family is the
highest-value parity surface and the one that most directly replaces the
StageOne DOM vocabulary.

Variants:
- `wave-panel` — wave-level container
- `blip-card` — single blip with avatar/author/meta slots
- `thread-container` — ordered blip list with nesting affordance
- `inline-reply-affordance` — "reply here" inline control
- `focus-frame` — focused-blip indicator (paired with
  `selection-indicator-style` and `color-accent-selection`)
- `collapse-toggle` — thread collapse/expand control
- `thread-nav-control` — next/previous unread/jump control
- `visible-region-placeholder` — loading skeleton for unloaded fragment
  windows
- `read-only-skeleton` — server-rendered read-only skeleton (no JS path)

Matrix rows supported: `R-3.1`, `R-3.2`, `R-3.3`, `R-3.4`, `R-3.5`, and
the visual contract for `R-3.6` (infrastructural enabler).

Consuming slice(s): `#966`, `#967`, `#965`.

### 5.3 Live-Surface Visual Indicators

**Visual only; state ownership stays in J2CL.** These primitives only
*render* state that J2CL publishes through narrow controller boundaries.
They do not own reconnect logic, unread modeling, version/hash state,
route state, or feature activation.

Variants:
- `live-reconnect-banner` — visual surface for R-4.3 reconnect
  announcement; paired with an ARIA live region (see §8.2)
- `live-update-chip` — transient "new activity" chip (visual inventory
  only; no matrix row anchor yet — a future matrix amendment may add one)
- `unread-badge` — unread indicator (paired with `color-state-unread`)
- `read-badge` — read confirmation indicator (paired with
  `color-state-read`)
- `session-presence-dot` — optional presence indicator slot

Matrix rows supported: `R-4.3` (visual only), `R-4.4` (visual only).
The route-transition chrome aspect of `R-4.5` is carried by §5.1 rather
than duplicated here.

Consuming slice(s): `#968`, and `#931` for the `unread-badge`/`read-badge`
visual slots only — unread/read modeling stays J2CL-owned and remains
Prohibited for Stitch per §6.

### 5.4 Compose / Toolbar Primitives (Inventory Only)

Purpose: placeholder for the daily compose/reply and formatting-toolbar
surfaces. Inventory only — variants are named so later slices do not
re-derive the vocabulary; full variant expansion happens under the
consuming slice's own design packet amendment PR.

Variants:
- `composer-shell` — top-level compose container
- `composer-inline-reply` — inline-reply variant
- `toolbar-group` — formatting/view toolbar grouping
- `toolbar-button` — single toolbar affordance (state toggle visuals)
- `toolbar-overflow-menu` — "more" menu for density-compact
- `composer-submit-affordance` — send control (paired with accessibility
  rules in §8.2)

Matrix rows supported: `R-5.1`, `R-5.2` (visual container only; behavior
lives in `#969`).

Consuming slice(s): `#969`. Issue map §7 permits Stitch use here; the
first `#969` packet amendment promotes the relevant variants from
Optional to Required Stitch when it lands.

### 5.5 Interaction-Overlay Primitives

Purpose: popovers, menus, toasts, tooltips, and suggestion listboxes
consumed by mentions, tasks, reactions, and overlay-driven affordances.

Variants:
- `overlay-menu` — contextual menu
- `overlay-popover` — anchored popover
- `overlay-tooltip` — tooltip
- `overlay-toast` — transient toast (paired with `z-overlay-toast`)
- `overlay-suggestion-listbox` — mention/autocomplete listbox with
  listbox semantics (§8.2)
- `overlay-modal` — dialog surface (paired with `z-overlay-modal` and
  `z-overlay-scrim`)

Matrix rows supported: `R-5.3`, `R-5.4`, `R-5.5` (visual surface only;
behavior in `#970`).

Consuming slice(s): `#970`. Also consumed by `#969` toolbar overflow.

### 5.6 Server-First First-Paint Primitives

Purpose: the visual surface used by server-rendered read-only HTML before
the Lit/J2CL upgrade attaches. These primitives must render without JS.

Variants:
- `server-shell-skeleton` — server-rendered shell frame
- `server-wave-skeleton` — server-rendered selected-wave/visible-fragment
  snapshot container
- `server-upgrade-placeholder` — placeholder region that the client
  upgrades without unstyled flash
- `server-rollback-chrome` — visual affordance used when the operator
  toggles back to the legacy GWT root via the existing coexistence seam
  (rendering only; route semantics remain in Java/J2CL)

Matrix rows supported: `R-6.1`, `R-6.3`, `R-6.4`.

Consuming slice(s): `#965`.

## 6. Stitch Artifact Policy

Stitch belongs where visual structure is in scope. Stitch does **not**
belong in transport, auth, bootstrap JSON, unread modeling, version/hash
correctness, fragment transport/window logic, route state, or feature
activation (issue map §7). Those rows appear as **Prohibited** below and
are enforced at slice-packet review time.

For each row:

- **Required**: the consuming slice may not start implementation without a
  committed Stitch artifact. The slice packet must pin (a) the Stitch
  project id, (b) the screen id(s) that cover the family's variants, and
  (c) the design-system id applied to the project. Missing any of those
  three blocks implementation until resolved.
- **Optional**: Stitch exploration is encouraged when visual ambiguity is
  high, but a written component spec in the slice packet is sufficient.
- **Prohibited**: Stitch has no role; design tools must not be used to
  propose behavior.

| Family / domain | Class | Consuming slice(s) | Required artifacts (if Required) | Rationale |
| --- | --- | --- | --- | --- |
| §5.1 Shell / Chrome | Required | `#964` | Stitch project with shell/header/nav/main-region/status-strip screens; at least one variant per density slot in §4.7; design system applied for §4.1–§4.10 slot coverage | Shell is the first Lit visual seam every later slice inherits; ambiguity here compounds across slices. |
| §5.2 Read-Surface | Required | `#966` | Stitch project with wave-panel, blip-card, thread-container, focus-frame, collapse-toggle, thread-nav-control, visible-region-placeholder, read-only-skeleton screens; variants for density and visible-region states | Read-surface is the largest user-visible parity gap. |
| §5.6 Server-First First-Paint | Required | `#965` | Stitch project with server-shell-skeleton, server-wave-skeleton, server-upgrade-placeholder, server-rollback-chrome screens; upgrade-transition walkthrough (no unstyled flash) | The upgrade path is parity-critical and must not be reverse-engineered from a screenshot. |
| §5.3 Live-Surface Indicators | Optional | `#968`, `#931` (unread/read visual parity) | n/a | Behavior lives in J2CL; visual indicator styling rarely needs exploratory variants. Slice packet spec is enough. |
| §5.4 Compose / Toolbar | Optional | `#969` | Promotion under `#969` must pin: Stitch project with composer-shell, composer-inline-reply, toolbar-group, toolbar-button, toolbar-overflow-menu, composer-submit-affordance screens; state toggle variants | Issue map §7 permits Stitch for compose/toolbar where behavior is already frozen by the slice packet. Promotes to Required under `#969` via a packet amendment PR, per §10. |
| §5.5 Interaction Overlays | Optional | `#970`, `#969` | n/a | Overlay visuals benefit from Stitch but most behavior is spec-driven. |
| Fragment viewport logic (`R-7.*`) | Prohibited | `#967` | n/a | Transport/clamp/window logic is J2CL-owned (issue map §7). Visual affordances (loading placeholders) are already covered under §5.2 `visible-region-placeholder`. |
| Bootstrap JSON contract (`R-4.2`, `R-6.2`) | Prohibited | `#963` | n/a | Not on the visual critical path; transport/contract work. |
| Socket/auth hardening | Prohibited | `#933` | n/a | Security/transport work (issue map §7). |
| Unread/read modeling | Prohibited | `#931` (modeling side) | n/a | Modeling is J2CL-owned; visual `unread-badge`/`read-badge` slots are the only design-side exposure. |
| Version/hash basis atomicity | Prohibited | `#936` | n/a | Correctness bug, not a visual surface. |
| Route state / history | Prohibited | `#968` (state side) | n/a | Route semantics are J2CL-owned; shell-chrome visuals are covered under §5.1. |
| Feature flags | Prohibited | any | n/a | Feature activation is configuration/J2CL, not a visual surface. |

Governance of the Required class:

- a Required row without a named consuming slice is **automatically
  demoted to Optional** during review; this keeps the policy from
  accumulating orphan requirements
- promoting an Optional row to Required is a PR against this packet
- demoting a Required row to Optional is a PR against this packet with
  reviewer rationale

## 7. Image-Generation Policy

Per [`docs/j2cl-lit-implementation-workflow.md`](./j2cl-lit-implementation-workflow.md)
§§5–6, image-generation tools are restricted to:

- moodboards and visual direction
- material/atmosphere studies
- iconography/background treatment experiments

Image generation is **not authoritative** for:

- screen specs
- component spacing or layout rules
- multi-screen flows
- token values
- text rendering

If an image study conflicts with a slice spec, the spec wins. If an image
study conflicts with this packet, this packet wins. Image artifacts do
not belong in the slice packet's "Parity matrix rows claimed" block.

## 8. Cross-Cutting Rules

Downstream component slices inherit these rules without re-derivation.

### 8.1 Accessibility And Contrast

- Text on `color-surface-page`/`color-surface-shell`/`color-surface-wave`
  meets WCAG 2.2 AA contrast in both light and dark; meta and caption
  text must still meet AA for non-large text or be treated as decorative
  with an accessible alternative.
- `focus-ring-style` is never the sole indicator of selection; pair it
  with `selection-indicator-style` and/or a text affordance.
- Every variant in §5.\* that exposes an interactive control must carry
  a reachable label and an announced role.
- Modal overlays (e.g., modal dialogs) must trap focus while open and
  restore focus to the trigger element on close. Non-modal overlays must
  not trap focus and must allow the normal tab order to continue.
- Server-rendered read-only HTML (§5.6) must be AT-usable on its own, per
  matrix row `R-6.1`.

### 8.2 Keyboard And Focus

- Shell and read-surface families must support the existing GWT keyboard
  bindings for their scope (arrow/`j`/`k`, space/enter on toggles,
  shift+tab for backward nav, escape to close overlays). Specific row
  coverage lives in the parity matrix (`R-3.2`, `R-3.3`, `R-3.4`,
  `R-5.3`).
- Focus does not jump on incremental render or shell-swap upgrade
  (`R-3.2`, `R-6.3`).
- Overlays close on escape and return focus to their trigger.
- Suggestion listbox (§5.5) uses the listbox/option role pattern and
  preserves arrow/enter/escape semantics (`R-5.3`).

### 8.3 Motion And Reduced Motion

- Every motion slot in §4.6 respects `prefers-reduced-motion: reduce`
  and collapses to the zero-motion variant.
- Live-surface indicators (§5.3) must not flash more than the WCAG 2.2
  flash threshold allows.

### 8.4 i18n And RTL

- Typography slots must accept locale fallback fonts.
- Spacing and shape slots must mirror correctly under RTL; any
  direction-aware icon must have a mirrored variant.
- Translatable strings stay with the consuming slice's message bundle;
  this packet does not ship strings.
- Locale-aware matching behavior for §5.5 suggestion listbox stays in
  J2CL (`R-5.3`).

### 8.5 Progressive Enhancement And Coexistence

- Every family must define a visual state for "server-rendered, not yet
  upgraded" and a visual state for "Lit-upgraded". The transition must
  not introduce an unstyled flash.
- The legacy GWT root remains the default `/` experience until the
  parity gate closes (matrix §8); `server-rollback-chrome` (§5.6) exists
  so the rollback path is visually first-class.
- This packet claims no authority over the coexistence contract; it only
  provides visuals for the seams the coexistence contract already
  defines.

## 9. Downstream Consumption Map

| Slice | Consumes families | Required Stitch | Optional Stitch |
| --- | --- | --- | --- |
| `#964` Lit root shell and shared chrome primitives | §5.1 | §5.1 | — |
| `#965` Server-rendered selected-wave first paint + upgrade | §5.1, §5.2, §5.6 | §5.6 | §5.2 (consumed, not authored here) |
| `#966` StageOne read-surface parity | §5.1, §5.2 | §5.2 | — |
| `#967` Viewport-scoped fragment windows | §5.2 (`visible-region-placeholder`) | — | — |
| `#968` Root-shell live surface | §5.1, §5.3 | — | §5.3 |
| `#969` Compose / toolbar parity | §5.4, §5.5 | promoted under `#969` amendment | §5.5 |
| `#970` Mention / task / reaction / overlay parity | §5.5, §5.3 | — | §5.5 |
| `#971` Attachment and remaining rich-edit daily parity | §5.4, §5.5 | — | §5.4 (via `#969` amendment), §5.5 |
| `#931` Live unread/read state | §5.3 (`unread-badge`, `read-badge`) | — | — |
| `#933` HttpOnly socket auth | — (Prohibited) | — | — |
| `#936` Version/hash basis atomicity | — (Prohibited) | — | — |
| `#963` Bootstrap JSON contract | — (Prohibited) | — | — |

Slices absent from this map consume no design-packet families.

## 10. Change Policy

- New tokens, component families, variants, or Stitch policy rows are
  added by PR against this file; review runs under Claude Opus 4.7
  consistent with AGENTS.md.
- Token slot names and component-family anchors are append-only while
  status is `Proposed`; deprecations are marked in place so downstream
  slice packets do not break citations.
- Promoting an Optional Stitch row to Required requires a named consuming
  slice and a packet amendment in the same PR.
- Demoting a Required Stitch row to Optional requires explicit reviewer
  rationale.
- The `Updated:` metadata field is refreshed on every edit.
- This packet does not open, close, or re-scope GitHub issues.
