# F-0: Refresh Lit design packet for wavy aesthetic + plugin slot contracts

Status: Ready for implementation
Owner: codex/issue-1035-wavy-design-foundation worktree
Issue: [#1035](https://github.com/vega113/supawave/issues/1035)
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Audit motivating this lane:
`/Users/vega/devroot/worktrees/j2cl-parity-audit/docs/superpowers/audits/2026-04-26-j2cl-gwt-parity-audit.md`
Stitch design source of truth: project `15560635515125057401`, design system
`assets/6962139294633729409` ("Wavy — Firefly Signal", v1).

## 1. Why this plan exists

The 2026-04-26 J2CL ↔ GWT parity audit and the accompanying functional
inventory (`docs/superpowers/audits/2026-04-26-gwt-functional-inventory.md`,
rows A.1–A.18 + M.1–M.5) found that the existing Lit design packet (#962)
plus the closed slice chain (#931, #966–#971) shipped against narrow
per-issue acceptance and did not deliver a recognisable parity surface.
The current packet ships only seven flat tokens in
`j2cl/lit/src/tokens/shell-tokens.css` (a teal `--shell-color-accent-brand`
surface family with one shadow) and no component recipes or plugin seams.

F-2/F-3/F-4 will redo the read/compose/live work against row-level matrix
acceptance — but they need a coherent visual + extensibility seam first,
otherwise each slice will reinvent its own tokens and slot conventions.

This plan delivers the **F-0 design packet** as five tasks (T1–T5):

1. T1 — Wavy token files under `j2cl/lit/src/design/` (color, typography,
   motion, spacing/shape) with dark + light + **high-contrast** variants
   (issue body §Scope.1 mandates all three "from day one").
2. T2 — Six Lit recipe elements (blip card, compose card, rail panel,
   edit toolbar, depth-nav breadcrumb, plus a pulse-stage helper that
   doubles as the F-3 live-update primitive) that consume the tokens
   and expose the plugin slots.
3. T3 — `docs/j2cl-plugin-slots.md` formalising the slot-context contract
   for `blip-extension`, `compose-extension`, `rail-extension`,
   `toolbar-extension`, **plus** `j2cl/lit/src/design/README.md` which
   serves as the design packet README and links the three Stitch screens
   (issue body §Acceptance bullet 2).
4. T4 — Storybook-style demo route reachable at
   `/?view=j2cl-root&q=design-preview` (per issue body §Verification)
   that mounts every recipe in dark + light + high-contrast variants
   plus a live-update pulse moment.
5. T5 — A.10 user-menu wiring: rename the "Automation / APIs" group to
   "Plugins / Integrations" in both topbars (standalone + wave-client)
   and **update the existing `HtmlRendererTopBarTest.java:38` assertion**
   that still hard-codes the old label, add changelog entry, and confirm
   SBT verification.

Each task ends with a paired Lit web-test-runner test or HtmlRenderer test
at the same change boundary. SBT verification (`sbt -batch j2clLitTest
j2clLitBuild j2clProductionBuild`) gates the lane.

This issue ships **no behavior changes**. It produces a reviewed design
packet + a documented plugin-extension contract. F-2/F-3/F-4 mount the
slot points but do not register plugins.

## 2. Verification ground truth (re-derived in worktree)

Citations re-grepped in the worktree on 2026-04-26 against HEAD
`86ea6b440` (post-#1040). Line numbers are accurate as of the worktree
snapshot; treat them as anchors.

### Existing Lit packet seams (the things F-0 extends)

- `j2cl/lit/src/tokens/shell-tokens.css:1-258` — current token file.
  Defines `--shell-color-*`, `--shell-space-*`, `--shell-shell-radius`,
  `--shell-shell-shadow`, `--shell-font-ui`, plus per-element layout
  rules. F-0 keeps every existing token (so #962 callers don't break)
  and **adds** the `--wavy-*` token namespace alongside.
- `j2cl/lit/src/index.js:1-30` — registers all 22 existing custom
  elements and selects the JSON or inline shell-input bridge. F-0 adds
  the six new recipe element imports here. **No** CSS import is added
  to `index.js` — the wavy CSS is emitted as a separate esbuild entry
  (see next bullet) so server templates can decide independently
  whether to load it.
- `j2cl/lit/esbuild.config.mjs:7-21` — bundles `src/index.js` and
  `src/tokens/shell-tokens.css` into `war/j2cl/assets/shell.{js,css}`
  with sourcemaps. F-0 adds a third entry point
  `{ in: ".../src/design/wavy-tokens.css", out: "wavy-tokens" }` so
  the wavy tokens emit as a sibling `war/j2cl/assets/wavy-tokens.css`
  asset. **Critical:** the recipe `.js` files do **not** `import` the
  CSS file; only the `<link>` tag in server-rendered HTML loads it.
  This avoids double-emission and keeps the J2CL-root path's bundle
  size unchanged when wavy is not loaded by F-2/F-3/F-4.
- `j2cl/lit/src/elements/shell-header.js:1-58`,
  `shell-nav-rail.js`, `shell-main-region.js`, `composer-shell.js`,
  `composer-inline-reply.js`, `toolbar-button.js`, `toolbar-group.js`
  — pattern reference for new recipe elements (LitElement, `static
  styles`, `static properties`, named `<slot>`s, `customElements.get`
  guard).
- `j2cl/lit/test/shell-header.test.js:1-22` — pattern reference for
  `@open-wc/testing` fixture-driven tests.

### Existing J2CL-root shell asset emission (must also load wavy)

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:3378-3380`
  — `renderJ2clRootShellPage` emits `<link>`s for `sidecar.css` and
  `shell.css` plus `<script>` for `shell.js`. F-0 **must** add a third
  `<link rel="stylesheet" href="…j2cl/assets/wavy-tokens.css">` here
  immediately after the `shell.css` link. Otherwise F-2/F-3/F-4
  recipes mounted under `?view=j2cl-root` render with no tokens
  loaded. The new `HtmlRendererJ2clRootShellTest` assertion path adds
  one more `assertTrue(html.contains("/j2cl/assets/wavy-tokens.css"))`.

### Existing user-menu seam (the A.10 / M.1 wiring)

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:4467-4476`
  — the standalone-topbar dropdown emits a `menu-section` titled
  "Automation / APIs" containing `/account/robots` ("Robot & Data API")
  + `/api-docs`. F-0 renames the section label to **"Plugins /
  Integrations"** and keeps the same two anchor links + their CSS
  classes. Existing tests must still pass.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:4656-4665`
  — same pattern for the wave-client-page topbar. Same rename.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java:2535`
  — there is also a static "Robot & Data API" `<h3>` inside the
  account-shell page. **Out of scope** for F-0 (that is the dedicated
  account page, not a menu entry).
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererJ2clRootShellTest.java`
  — exists; verifies `/j2cl/assets/n` (the bundle URL). F-0 extends it
  to assert the new `wavy-tokens.css` link as well.
- `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java:38`
  — currently asserts the literal `"section-label\">Automation /
  APIs"`. **Will fail under T5 unless updated.** F-0 rewrites this
  assertion to `"section-label\">Plugins / Integrations"`. The same
  test file at line 156 invokes `renderJ2clRootShellPage`, which is
  unaffected by the menu rename.
- F-0 adds a new `HtmlRendererPluginsMenuTest` (per §7) that asserts
  both topbars contain the new label, do **not** contain the old label,
  and still link to `/account/robots` and `/api-docs`.

### Server-side asset emission for the design preview route

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java:167-196`
  is the existing `?view=j2cl-root` branch. F-0 adds a sub-branch
  inside it that checks `request.getParameter("q")` for
  `"design-preview"` (per issue body §Verification:
  `/?view=j2cl-root&q=design-preview`). When matched, the servlet
  calls `HtmlRenderer.renderJ2clDesignPreviewPage(...)` instead of
  `renderJ2clRootShellPage(...)` — same `?view` param, same auth
  shape, same asset path, no new servlet branch.
- The design preview is **admin-or-owner gated** to keep a designer-
  facing route off the public surface. The branch:
  `if ("design-preview".equals(qParam) && (id != null && isAdminOrOwner(id))) renderDesignPreview() else fall-through to existing renderJ2clRootShellPage`.
  Non-admins requesting `?q=design-preview` get the regular root shell.
- `HtmlRenderer.renderJ2clDesignPreviewPage` reuses the same asset
  links emitted by `renderJ2clRootShellPage` (sidecar.css, shell.css,
  **wavy-tokens.css**, shell.js) so the preview rides the production
  loader; no duplicate `<link>` plumbing.

### Stitch design source of truth (do not re-invent the aesthetic)

The "Wavy — Firefly Signal" design system in Stitch project
`15560635515125057401` (asset `6962139294633729409`, v1) is the source
of truth. Its `designMd` field encodes:

- **Inspiration & principles** — Firefly "send a wave" idiom; signal
  over chrome; live-update presence; conversational warmth and density;
  holographic depth (border glow, not shadow stacks); plugin slots are
  first-class.
- **Palette** — bg `#0b1320` (deep midnight blue), surface a half-shade
  lighter with hairline signal-tone border, primary signal cyan
  `#22d3ee`, secondary signal violet `#7c3aed`, tertiary signal amber
  `#fb923c`, body text at ~92% opacity (never pure white).
- **Motion** — signal pulse 600ms (max 1/sec/blip), focus transition
  180ms (cursor-in-console), collapse 240ms, fragment-window load 300ms
  fade-in from low-alpha signal-tone skeleton.
- **Typography** — Space Grotesk (headline), Inter (body), Geist
  (label), ~70 char target line in the wave panel.
- **Roundness** — cards 12px, chips/badges full pill, inputs 12px.
- **Plugin-slot visual rhythm** — empty slot collapses to 0 in
  production; in design preview, faintly outlined dashed region with
  slot-name label.
- **Light mode** — pale near-white background, midnight ink, same
  accents at higher saturation. Maintain the living-signal feel.

F-0 reproduces these values exactly as CSS custom properties (no
re-interpretation) so a side-by-side check against the Stitch design
markdown stays one-to-one.

## 3. Design — token contract

All tokens live in `j2cl/lit/src/design/wavy-tokens.css`. The Stitch
source of truth declares `colorMode: DARK`, so **dark is the default**:
`:root` carries the dark values. `@media (prefers-color-scheme: light)`
overrides them with the light values. Explicit overrides via
`:root[data-wavy-theme="dark"]`, `:root[data-wavy-theme="light"]`,
and `:root[data-wavy-theme="contrast"]` (or scoped wrappers carrying
the same data attribute) win over the media query — the recipes use
this attribute to demonstrate side-by-side variants in the demo route.

Recipes consume tokens via `var(--wavy-*, fallback)` so a recipe still
degrades sanely if loaded without `wavy-tokens.css`.

### 3.1 Color tokens (exact names — F-2/F-3/F-4 reference these)

Surface and chrome:
- `--wavy-bg-base` — page background. Dark: `#0b1320`. Light: `#f6f7fb`.
- `--wavy-bg-surface` — card / panel surface. Dark: `#11192a`. Light:
  `#ffffff`.
- `--wavy-border-hairline` — 1px hairline border on surfaces. Dark:
  `rgba(34, 211, 238, 0.18)`. Light: `rgba(11, 19, 32, 0.10)`.

Text:
- `--wavy-text-body` — body text. Dark: `rgba(232, 240, 255, 0.92)`.
  Light: `rgba(11, 19, 32, 0.92)`.
- `--wavy-text-muted` — secondary text. Dark: `rgba(232, 240, 255,
  0.62)`. Light: `rgba(11, 19, 32, 0.62)`.
- `--wavy-text-quiet` — tertiary text (timestamps, labels). Dark:
  `rgba(232, 240, 255, 0.42)`. Light: `rgba(11, 19, 32, 0.55)` (the
  light alpha was raised from the symmetric 0.42 to 0.55 so the
  composited color clears WCAG ≥3:1 on the `#ffffff` surface — at
  0.42 it landed at 2.75:1).

Signal accents (named after Firefly "wave" tones — see Stitch design
markdown for usage rules):
- `--wavy-signal-cyan` — primary signal (focus frame, pulse, primary
  CTA, unread badge). Both modes: `#22d3ee`.
- `--wavy-signal-cyan-soft` — alpha-tinted cyan for inner glow + pulse
  ring. Dark: `rgba(34, 211, 238, 0.22)`. Light: `rgba(34, 211, 238,
  0.18)`.
- `--wavy-signal-violet` — mention/reaction tone. Both modes: `#7c3aed`.
- `--wavy-signal-violet-soft` — alpha-tinted violet for chip
  backgrounds. Dark: `rgba(124, 58, 237, 0.22)`. Light: `rgba(124, 58,
  237, 0.16)`.
- `--wavy-signal-amber` — task / time-sensitive tone. Both modes:
  `#fb923c`.
- `--wavy-signal-amber-soft` — alpha-tinted amber for background fills.
  Dark: `rgba(251, 146, 60, 0.22)`. Light: `rgba(251, 146, 60, 0.16)`.

Focus + pulse derived tokens:
- `--wavy-focus-ring` — focus frame outline. Both modes:
  `0 0 0 2px var(--wavy-signal-cyan)` (consumed via `box-shadow`).
- `--wavy-pulse-ring` — outer glow on live-update pulse. Both modes:
  `0 0 0 4px var(--wavy-signal-cyan-soft)`.

### 3.2 Typography tokens

Font family stacks (web-safe fallbacks for environments without the
named fonts loaded — F-0 does not add a webfont loader; the recipes
gracefully fall back):

- `--wavy-font-headline` — `"Space Grotesk", "Inter", -apple-system,
  BlinkMacSystemFont, "Segoe UI", sans-serif`.
- `--wavy-font-body` — `"Inter", -apple-system, BlinkMacSystemFont,
  "Segoe UI", Roboto, sans-serif`.
- `--wavy-font-label` — `"Geist", "Inter", -apple-system,
  BlinkMacSystemFont, "Segoe UI", sans-serif`.

Type scale (line-height baked in; ratios picked to give the
~70-char-per-line target in the read surface at the default 16px root):

- `--wavy-type-display` — `clamp(1.875rem, 1.4rem + 1.6vw, 2.25rem) /
  1.18 var(--wavy-font-headline)`.
- `--wavy-type-h1` — `1.5rem / 1.25 var(--wavy-font-headline)`.
- `--wavy-type-h2` — `1.25rem / 1.3 var(--wavy-font-headline)`.
- `--wavy-type-h3` — `1.0625rem / 1.35 var(--wavy-font-headline)`.
- `--wavy-type-body` — `0.9375rem / 1.55 var(--wavy-font-body)`.
- `--wavy-type-label` — `0.75rem / 1.35 var(--wavy-font-label)` with
  `letter-spacing: 0.04em` applied via the recipes' selectors (token
  carries the `font` shorthand only).
- `--wavy-type-meta` — `0.6875rem / 1.4 var(--wavy-font-label)`.

**Caveat — `font` shorthand reset.** Each `--wavy-type-*` value is a
CSS `font` shorthand, which when applied via
`font: var(--wavy-type-display);` resets `font-style`, `font-variant`,
`font-weight`, `font-stretch`, and `font-size-adjust` to initial.
Recipes that need a non-default weight or italic must declare it
**after** the shorthand in the same rule:
```
font: var(--wavy-type-display);
font-weight: 600;
```
The token test asserts `getComputedStyle(...).fontWeight` matches the
recipe's declared weight to catch accidental overrides via the
shorthand.

### 3.3 Motion tokens

- `--wavy-motion-pulse-duration` — `600ms` (signal pulse on
  live-update).
- `--wavy-motion-focus-duration` — `180ms` (focus frame slide).
- `--wavy-motion-collapse-duration` — `240ms` (collapse/expand height
  + opacity).
- `--wavy-motion-fragment-fade-duration` — `300ms` (fragment-window
  load fade-in).

Easings:
- `--wavy-easing-pulse` — `cubic-bezier(0.32, 0.72, 0.32, 1)` (outward
  bloom that decays).
- `--wavy-easing-focus` — `cubic-bezier(0.2, 0.0, 0.2, 1)` (ease-out;
  cursor-in-console feel).
- `--wavy-easing-collapse` — `cubic-bezier(0.4, 0.0, 0.2, 1)` (standard
  ease-in-out).

Reduced-motion: a `@media (prefers-reduced-motion: reduce)` block in
`wavy-tokens.css` resets all four `--wavy-motion-*-duration` tokens to
`0.01ms` so animations are effectively skipped while still firing
`animationend` / `transitionend` so JS state machines tracking the
end event still resolve. The blip-card live-pulse is implemented as a
CSS `@keyframes` animation (so it fires `animationend`); other
recipes use `transition` (firing `transitionend`).

**Pulse restart pattern.** A CSS animation does not restart when the
same attribute value is re-applied without a layout flush. The
recipe-side helper for `live-pulse` removes the attribute, forces
layout via `void this.offsetWidth`, then re-adds — so back-to-back
pulses (from F-3 live-update) animate every time. The
`<wavy-pulse-stage>` test asserts this pattern.

### 3.4 Spacing + shape tokens

Spacing scale (4-px-base):
- `--wavy-spacing-1` = `4px`
- `--wavy-spacing-2` = `8px`
- `--wavy-spacing-3` = `12px`
- `--wavy-spacing-4` = `16px`
- `--wavy-spacing-5` = `20px`
- `--wavy-spacing-6` = `24px`
- `--wavy-spacing-7` = `32px`
- `--wavy-spacing-8` = `40px`

Shape:
- `--wavy-radius-card` = `12px` (cards, inputs).
- `--wavy-radius-pill` = `9999px` (chips, badges, signal dots).

### 3.5 Light + dark variant strategy

`wavy-tokens.css` declares dark as the default (matches Stitch
`colorMode: DARK`). `@media (prefers-color-scheme: light)` overrides
the surface + text + soft-accent triads to the light values. Explicit
override via the `data-wavy-theme="dark"` / `data-wavy-theme="light"`
attribute on `<html>` (or any ancestor scope element) wins over the
media query.

The recipe elements never read raw colors; they only consume
`--wavy-*` tokens, so flipping the attribute swaps the whole packet.

### 3.6 High-contrast variant (issue body §Scope.1 mandate)

Issue body §Scope.1: "dark-mode and high-contrast variants from day
one". F-0 ships a third variant that swaps the chrome for WCAG-AAA
contrast under `prefers-contrast: more` and the explicit
`data-wavy-theme="contrast"` attribute:

- Surfaces: `--wavy-bg-base: #000000`, `--wavy-bg-surface: #0b1320`.
- Borders: `--wavy-border-hairline` becomes a solid 1.5px
  `rgba(255, 255, 255, 0.85)` border (no hairline tinting).
- Text: `--wavy-text-body: #ffffff`, `--wavy-text-muted:
  rgba(255, 255, 255, 0.85)`, `--wavy-text-quiet:
  rgba(255, 255, 255, 0.7)` — none drop below WCAG AAA (7:1) on the
  black surface.
- Signal accents: same hex values, but the `*-soft` companions become
  more saturated (`rgba(34, 211, 238, 0.45)` etc) so the inner-glow
  does not blend into the surface.
- Inner-glow border on cards is suppressed (replaced by the solid
  border above) so contrast is carried by edges, not light.
- `--wavy-focus-ring` thickens to `0 0 0 3px var(--wavy-signal-cyan)`
  for visibility without inner glow.

A recipe test mounts each recipe under `data-wavy-theme="contrast"`
and asserts the computed `outline` / `border` values reflect the
solid-border treatment.

### 3.7 Plugin-slot styling contract (renumbered from 3.6)

Plugins inherit `--wavy-*` tokens by default through the cascade
(plugin content rendered into a slot lives in light DOM and inherits
host styles). Plugins opt out by setting `data-wavy-plugin-untheme`
on their slotted root element; recipes add a
`:has([data-wavy-plugin-untheme])` guard on the slot wrapper that
suppresses the inner-glow border so an opt-out plugin can present its
own visual. Note: the `:has()` selector requires Chromium ≥105,
Safari ≥15.4, Firefox ≥121; on engines below this the opt-out
gracefully no-ops (the inner-glow remains visible behind the plugin
content but does not break it).

## 4. Component recipes (T2)

Five Lit elements added under `j2cl/lit/src/design/`. Each is a
self-contained recipe consumable by F-2/F-3/F-4. Existing shell
elements stay untouched (#962 callers continue to work).

### 4.1 `<wavy-blip-card>` (F-2 consumer)

- File: `j2cl/lit/src/design/wavy-blip-card.js`.
- Properties: `focused: Boolean`, `unread: Boolean`, `live-pulse:
  Boolean`, `author-name: String`, `posted-at: String` (ISO),
  `blip-id: String` (reflected to `data-blip-id`), `wave-id: String`.
- Slots:
  - default — blip body content (text, attachments, embeds);
  - `name="reactions"` — reaction-chip rail;
  - `name="metadata"` — timestamp + read-status row;
  - **`name="blip-extension"`** — the M.2 plugin slot, rendered between
    the body and the reactions rail (per inventory M.2 spec).
- Visual: holographic card with hairline cyan-soft border; focused
  state shifts the border to `--wavy-signal-cyan` and adds the focus
  ring. Live-update pulse triggered by toggling the `live-pulse`
  attribute for one frame; CSS animation runs once and the attribute
  is dropped by F-2 telemetry.
- Plugin-slot context (documented in `docs/j2cl-plugin-slots.md`):
  `{ blipId, blipView (read-only proxy), waveId, isAuthor }`. The
  recipe surfaces the contract two ways:
  1. **Data attributes** (string-only, for declarative plugins):
     `data-blip-id`, `data-wave-id`, `data-blip-author`,
     `data-blip-is-author`. Plugins read these from
     `slot.assignedElements()[0].closest('wavy-blip-card').dataset`.
  2. **`blipView` JS property** (structured, for richer plugins): the
     host element exposes `wavyBlipCard.blipView`, returning a frozen
     read-only `Object.freeze({ id, authorName, postedAt, body, ... })`
     proxy. Mutating the proxy throws in strict mode (default for
     custom elements). The property accessor lazily snapshots the
     view from the host's internal state on read so plugins always
     see fresh data.
- Test: `j2cl/lit/test/wavy-blip-card.test.js` — asserts the four data
  attributes, the four named slot names exist, the focused state
  applies the focus ring class, the empty `blip-extension` slot
  collapses to zero height when `data-wavy-design-preview` is **not**
  set on the document and renders a dashed outline label when it is,
  the `blipView` property returns a frozen object, and mutating the
  property throws under strict mode. The recipe is mounted under
  three theme attributes (`dark`, `light`, `contrast`) and the test
  asserts at least one computed style differs across all three (e.g.,
  `getComputedStyle(card).backgroundColor` differs between dark and
  light).

### 4.2 `<wavy-compose-card>` (F-3 consumer)

- File: `j2cl/lit/src/design/wavy-compose-card.js`.
- Properties: `focused: Boolean`, `submitting: Boolean`, `reply-target-blip-id:
  String`.
- Slots: default (composer surface), `name="toolbar"` (the H.* edit
  toolbar), **`name="compose-extension"`** (M.3, right of the
  toolbar), `name="affordance"` (submit button row).
- Plugin-slot context: `{ composerState, activeSelection,
  replyTargetBlipId }`. Recipe reflects `data-reply-target-blip-id`
  on the host; `composerState` and `activeSelection` are exposed as
  frozen JS properties on the host element (`wavyComposeCard
  .composerState`, `wavyComposeCard.activeSelection`).
- Test: assert slot names, focused style, submitting state disables
  pointer events on the affordance slot wrapper, all three theme
  variants (`dark`/`light`/`contrast`) flip a computed style, the
  empty `compose-extension` slot collapses in production and shows
  the dashed outline under `data-wavy-design-preview`, and
  `composerState` / `activeSelection` properties return frozen
  objects.

### 4.3 `<wavy-rail-panel>` (F-2 consumer)

- File: `j2cl/lit/src/design/wavy-rail-panel.js`.
- Properties: `panel-title: String`, `collapsed: Boolean`,
  `active-wave-id: String`, `active-folder: String`.
- Slots: default (panel body), `name="header-actions"`,
  **`name="rail-extension"`** (M.4), `name="footer"`.
- Visual: column of stacked surface cards with collapse animation
  using `--wavy-motion-collapse-duration` + `--wavy-easing-collapse`.
- Plugin-slot context: `{ activeWaveId, activeFolder }` exposed via
  `data-active-wave-id` / `data-active-folder`.
- Test: assert the rail-extension slot exists, collapse toggles
  `aria-expanded`, the data attributes propagate, all three theme
  variants flip a computed style, and the empty `rail-extension`
  slot collapses in production and shows the dashed outline under
  `data-wavy-design-preview`.

### 4.4 `<wavy-edit-toolbar>` (F-3 consumer)

- File: `j2cl/lit/src/design/wavy-edit-toolbar.js`.
- Properties: `active-selection: String` (JSON-encoded selection
  descriptor — opaque to the recipe; plugins parse it).
- Slots: default (formatting controls — bold/italic/etc),
  **`name="toolbar-extension"`** (M.5, right of the formatting
  controls).
- Visual: pill-shaped row, signal-cyan accent on the active control;
  uses `--wavy-radius-pill`, `--wavy-spacing-2`.
- Plugin-slot context: `{ activeSelection }`. The recipe writes the
  attribute only when `active-selection` changes, debounced at 60 fps
  (≈16 ms minimum gap), so a noisy selection update from F-3 does not
  thrash the DOM. For long selections, plugins should re-read from
  the data attribute lazily.
- Test: assert the toolbar-extension slot exists, the data attribute
  is reflected after the debounce window, the active control class
  swaps on `aria-pressed`, all three theme variants flip a computed
  style, and the empty `toolbar-extension` slot collapses in
  production / dashed-outlines under `data-wavy-design-preview`.

### 4.5 `<wavy-depth-nav>` (no plugin slot — purely chrome)

- File: `j2cl/lit/src/design/wavy-depth-nav.js`.
- Property: `crumbs: Array` of `{ label: string, href?: string,
  current?: boolean }`.
- Renders a breadcrumb row with chevron separators using
  `--wavy-font-label` and `--wavy-text-muted`. Current crumb uses
  `--wavy-text-body` and `aria-current="page"`. Used by F-2 to show
  Inbox › Wave › Thread context.
- Test: asserts crumb count and that exactly one crumb carries
  `aria-current`.

### 4.6 `<wavy-pulse-stage>` (demo route helper — also used as a F-3 helper)

- File: `j2cl/lit/src/design/wavy-pulse-stage.js`.
- Tiny LitElement that, on a button press, sets the `live-pulse`
  attribute on a target child for 600ms and then removes it. Used by
  the design preview to demonstrate the live-update pulse moment;
  F-3 may reuse this as the canonical "fire a pulse on an arriving
  blip" helper rather than re-implementing the timing.
- Test: resolves `--wavy-motion-pulse-duration` at test time via
  `getComputedStyle(document.documentElement).getPropertyValue(...)`
  and asserts the attribute toggles within `(resolvedDuration + 50)
  ms`. This makes the test stable on CI hosts that announce
  `prefers-reduced-motion: reduce` (where the duration collapses to
  ~0ms and the budget is ~50ms) without flaking.

## 5. Plugin-slot doc + design packet README (T3)

Two markdown files. The plugin-slot doc is the slot contract; the
design packet README is the design-packet "front page" that the
issue body's §Acceptance bullet 2 ("at least three Stitch screens
generated and linked from the design packet") needs.

### 5.1 `docs/j2cl-plugin-slots.md` (plugin-slot contract)

Single doc owned by F-0 that F-2/F-3/F-4 link from their plans. Sections:

1. **Overview** — what a plugin slot is, why it exists, and the
   relationship to the forthcoming robots/data-API plugin registry
   (F-0 specifies the slot contract only; registration is a separate
   future issue).
2. **The four slots** — one subsection each for `blip-extension`,
   `compose-extension`, `rail-extension`, `toolbar-extension`. Each
   subsection covers:
   - **Mount point** — the F-* recipe element that owns the slot
     and where in its render tree the slot sits.
   - **Slot context (read-only)** — the data attributes a plugin can
     read from the host element. Exact attribute names listed.
   - **Allowed children** — markup the slot accepts (HTML elements,
     custom elements that respect the styling contract).
   - **Owning F-* slice** — F-2 (#1037) for blip + rail; F-3 (#1038)
     for compose + toolbar.
3. **Visual rhythm** — empty slots collapse to zero height in
   production; in design preview (set `data-wavy-design-preview`
   attribute on `<html>`), each empty slot renders a dashed outline
   with the slot name label so designers can see where plugins land.
4. **Styling contract** — plugins inherit `--wavy-*` tokens by default;
   to opt out, the plugin sets `data-wavy-plugin-untheme` on its root
   element, which suppresses the recipe's inner-glow border and lets
   the plugin paint its own visual.
5. **Accessibility contract** — plugin content must respect the F-0
   focus-frame (use `:focus-visible` with the `--wavy-focus-ring`
   custom property if drawing focus state) and announce its own
   role/label. Plugins must not steal the parent's `tabindex=0`.
6. **Lifecycle contract** — slots mount with their host element and
   unmount on host removal. Plugins must not assume persistence
   across wave-selection: if the host element is destroyed, the
   plugin's content is destroyed with it.
7. **Rollback semantics** — a plugin that fails to render must not
   break the host; the slot stays empty and the host renders normally.
   The future plugin registry will expose a per-slot kill switch; F-0
   does not implement it.

The doc is reviewable in isolation (no code references that would rot)
and is the source of truth that F-2/F-3/F-4 quote from in their plans.

### 5.2 `j2cl/lit/src/design/README.md` (design packet README)

Front-page doc for the wavy design packet, satisfying issue body
§Acceptance bullets 1 + 2. Sections:

1. **Source of truth** — the Stitch project / asset IDs (project
   `15560635515125057401`, design system `assets/6962139294633729409`,
   v1 "Wavy — Firefly Signal"), with the design markdown reproduced
   verbatim so the packet stays self-contained even if Stitch
   becomes unavailable.
2. **Stitch screens** — three screens generated via
   `mcp__stitch__generate_screen_from_text` (or pulled from the
   project) with the IDs/URLs listed:
   - **Read surface** — focused blip in a wave thread with rail.
   - **Compose surface** — composer with edit toolbar and the four
     plugin-slot dashed outlines visible.
   - **Live-update pulse moment** — incoming blip mid-animation with
     the cyan signal glow.
   The screens are generated by T3 (the `mcp__stitch__*` tool calls
   are implementation tasks for the implementer subagent).
3. **Token reference** — pointer to `wavy-tokens.css` and the
   namespacing rule (only `--wavy-*` is part of the F-0 contract;
   `--shell-*` is the legacy #962 packet, kept for back-compat).
4. **Recipe reference** — pointer to the six recipe elements and
   their per-recipe README sections (one paragraph each).
5. **Variant reference** — dark / light / contrast switching rules
   and the `data-wavy-theme` attribute.
6. **How F-2/F-3/F-4 consume the packet** — a copy-paste of §11
   from this plan.

## 6. Demo route (T4)

A new server route renders the design preview without auth coupling:
the page is a static HTML doc that imports `shell.js` + the wavy CSS
files and mounts each recipe with sample content.

### 6.1 Server seam

`HtmlRenderer.renderJ2clDesignPreviewPage(...)` (signature mirrors
`renderJ2clRootShellPage` — same context path, build commit, build
time, release id) returns the full HTML page as a String. It:

- emits `<link rel="stylesheet" href="<base>j2cl/assets/sidecar.css">`,
  `<link rel="stylesheet" href="<base>j2cl/assets/shell.css">`, and
  `<link rel="stylesheet" href="<base>j2cl/assets/wavy-tokens.css">`
  (same three the production root shell loads after T1),
- emits `<script type="module" src="<base>j2cl/assets/shell.js">`,
- sets `<html data-wavy-design-preview>` so empty slots render with
  the dashed outline,
- mounts three `<section>`s in order: dark variant (no theme
  attribute), light variant (`<section data-wavy-theme="light">`),
  and contrast variant (`<section data-wavy-theme="contrast">`).
  The recipes pick up the scope override at the section level.

`WaveClientServlet` (Jakarta override) extends its existing
`?view=j2cl-root` branch (line 167) with a sub-branch. The role check
mirrors the existing pattern at `HtmlRenderer.java:3347-3348`
(`HumanAccountData.ROLE_ADMIN.equals(role) || HumanAccountData.ROLE_OWNER.equals(role)`):
the servlet fetches `AccountData` for `id` via the existing
`accountStore.getAccount(id)` call (already used at
`WaveClientServlet.java:222-228`) and checks the role on the
`HumanAccountData`:

```
boolean designPreviewRequested =
    "design-preview".equals(request.getParameter("q"));
if (designPreviewRequested && id != null) {
  AccountData acct = accountStore.getAccount(id);
  if (acct != null && acct.isHuman()) {
    String role = acct.asHuman().getRole();
    if (HumanAccountData.ROLE_ADMIN.equals(role)
        || HumanAccountData.ROLE_OWNER.equals(role)) {
      w.write(HtmlRenderer.renderJ2clDesignPreviewPage(...));
      return;
    }
  }
}
// fall through to existing renderJ2clRootShellPage(...) call
```

The route is **admin-or-owner gated** to keep a designer-facing
surface off the public menu. A non-admin requesting
`?view=j2cl-root&q=design-preview` gets the regular root shell with
no error, no leak. Discovery is via the documentation in
`docs/j2cl-plugin-slots.md` and the README. The route is **not**
added to the user menu (which keeps the menu rename in T5 minimal
and on-spec).

### 6.2 What the demo route mounts (per issue acceptance)

Each of the three theme sections (dark / light / contrast) renders
the same content block:

1. A `<wavy-depth-nav>` showing `Inbox › Sample wave › Top thread`.
2. A `<wavy-blip-card>` in the **focused** state with the focus ring
   and an example `blip-extension` slot child rendered as a sample
   plugin.
3. A `<wavy-blip-card>` in the **unfocused / unread** state with no
   plugin (so the empty slot's design-preview dashed outline shows).
4. A `<wavy-compose-card>` with a `<wavy-edit-toolbar>` in its toolbar
   slot, an example `compose-extension` plugin chip, and an example
   `toolbar-extension` plugin chip in the inner toolbar slot.
5. A `<wavy-rail-panel>` titled "Saved searches" with one
   `rail-extension` sample plugin chip.
6. A `<wavy-pulse-stage>` wrapping a third blip card with a "Fire
   pulse" button — this demonstrates the live-update pulse moment
   required by the issue acceptance.

Above the three sections, the page includes a header strip that
identifies the page as the design preview and links to
`docs/j2cl-plugin-slots.md` (rendered as a relative path comment) so
a designer reaching the route knows the context.

### 6.3 Demo route test

A new server-side test
`wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererJ2clDesignPreviewPageTest.java`
asserts:

- the page references `/j2cl/assets/sidecar.css`,
  `/j2cl/assets/shell.css`, `/j2cl/assets/wavy-tokens.css`,
  `/j2cl/assets/shell.js`,
- the page contains every recipe element name (`<wavy-blip-card`,
  `<wavy-compose-card`, `<wavy-rail-panel`, `<wavy-edit-toolbar`,
  `<wavy-depth-nav`, `<wavy-pulse-stage`),
- the page contains the `data-wavy-design-preview` attribute,
- the page contains all three section markers
  (`data-wavy-theme="light"` and `data-wavy-theme="contrast"`; the
  dark section is the default with no attribute),
- the page contains the four plugin-slot sample plugins.

A second test `WaveClientServletDesignPreviewBranchTest` asserts the
admin-gating: a non-admin GET returns the regular root shell HTML
(no design-preview markers), and an admin GET returns the design
preview HTML (with markers).

The demo route is **server-rendered HTML only** — no new J2CL or Lit
behavior; the recipes' web-test-runner tests cover the client-side
rendering.

## 7. A.10 user-menu wiring (T5)

In `HtmlRenderer.java`:

- Lines `4467-4476` (standalone topbar): replace
  `"Automation / APIs"` section label with `"Plugins / Integrations"`.
  Keep the same two anchor links (`/account/robots`, `/api-docs`)
  and CSS classes intact. **No** new "Design preview" link in the
  user menu — the design preview is admin-or-owner gated and reached
  via the documented URL only (see §6.1).
- Lines `4656-4665` (wave-client topbar): same rename, no other
  changes.

In `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererTopBarTest.java`:

- Line `38` currently asserts the literal `"section-label\">Automation
  / APIs"`. **Update** the assertion to
  `"section-label\">Plugins / Integrations"`. Without this update the
  test fails immediately under T5.

A new test
`wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererPluginsMenuTest.java`
asserts:

- Both topbars (`renderTopBar(...)` and the standalone-topbar
  fragment from `renderJ2clRootShellPage(...)`) render the section
  label `"Plugins / Integrations"`.
- Both topbars **do not** contain the literal `"Automation / APIs"`
  anywhere (assertFalse).
- Both topbars still link to `/account/robots` and `/api-docs` and
  preserve the `section-link-strong` CSS class on the
  `/account/robots` anchor.

A changelog entry under
`wave/config/changelog.d/2026-04-26-issue-1035-wavy-design-foundation.json`
captures the design-packet bump and the user-menu rename.

## 8. Test plan

### 8.1 Lit web-test-runner (T1, T2, T4 client-side)

- `j2cl/lit/test/wavy-tokens.test.js` (new) — mounts a probe element
  under each of the three themes (default-dark, `data-wavy-theme=
  "light"`, `data-wavy-theme="contrast"`) and asserts:
  - every token name from §3 is non-empty,
  - every token uses the `--wavy-` prefix (no
    accidental `--shell-` overlap),
  - text-on-surface contrast for `--wavy-text-body` and
    `--wavy-text-muted` against `--wavy-bg-surface` exceeds the
    per-theme floor (WCAG AA = 4.5 for dark/light, AAA = 7 for
    contrast). `--wavy-text-quiet` (the tertiary tier reserved for
    timestamps / decorative meta) is intentionally low-contrast and
    only required to clear 3:1 on dark/light (WCAG AA for incidental
    UI / large text) and 4.5:1 on contrast. The test ships a small
    `relativeLuminance(rgb)` + composite helper; ~30 lines.
- `j2cl/lit/test/wavy-blip-card.test.js` (new) — see §4.1.
- `j2cl/lit/test/wavy-compose-card.test.js` (new) — see §4.2.
- `j2cl/lit/test/wavy-rail-panel.test.js` (new) — see §4.3.
- `j2cl/lit/test/wavy-edit-toolbar.test.js` (new) — see §4.4.
- `j2cl/lit/test/wavy-depth-nav.test.js` (new) — see §4.5.
- `j2cl/lit/test/wavy-pulse-stage.test.js` (new) — see §4.6.

Each recipe test mounts the recipe under all three theme attributes
and asserts at least one computed style differs across each pair, so
a recipe that hardcodes a hex value trips CI.

All existing tests under `j2cl/lit/test/*.test.js` must continue to
pass unchanged. F-0 does not edit any existing Lit element; it only
adds new files in `j2cl/lit/src/design/`.

### 8.2 Server-side JUnit (T4, T5)

All under `wave/src/test/java/org/waveprotocol/box/server/rpc/`:

- `HtmlRendererJ2clDesignPreviewPageTest.java` (new) — see §6.3.
- `WaveClientServletDesignPreviewBranchTest.java` (new) — admin
  gating, see §6.3.
- `HtmlRendererPluginsMenuTest.java` (new) — see §7.
- `HtmlRendererTopBarTest.java` (existing, **edited**) — line 38
  assertion updated to `"Plugins / Integrations"`.
- `HtmlRendererJ2clRootShellTest.java` (existing, **edited**) — adds
  one assertion that the page references `/j2cl/assets/wavy-tokens.css`.

### 8.3 SBT verification commands (run in order)

```
sbt -batch j2clLitTest
sbt -batch j2clLitBuild
sbt -batch j2clProductionBuild
```

All three must pass with zero new warnings introduced by F-0. The
production build re-bundles the J2CL assets; F-0 only adds new CSS +
JS so the bundle should grow but not regress.

## 9. Implementation order (T1 → T5)

1. **T1 — Tokens.** Add `j2cl/lit/src/design/wavy-tokens.css` with
   every `--wavy-*` token from §3 (dark default, light + contrast
   overrides, reduced-motion override); wire it into
   `j2cl/lit/esbuild.config.mjs` as a third output entry; add
   `wavy-tokens.test.js` covering all three themes + WCAG contrast
   floors; **also add the wavy-tokens.css `<link>` to
   `renderJ2clRootShellPage` (HtmlRenderer.java:3378-3380)** and
   extend `HtmlRendererJ2clRootShellTest` with the new assertion.
   Run `sbt -batch j2clLitTest j2clLitBuild` and confirm
   `war/j2cl/assets/wavy-tokens.css` exists. ≤120 LOC production +
   ≤80 LOC test.
2. **T2 — Recipes.** Add the six recipe elements under
   `j2cl/lit/src/design/` (blip card, compose card, rail panel, edit
   toolbar, depth-nav breadcrumb, pulse stage helper); register them
   in `src/index.js`; add the six paired tests covering all three
   themes, slot data attributes, frozen JS properties, and the
   pulse-restart pattern. Run `sbt -batch j2clLitTest j2clLitBuild`.
   ≤500 LOC production + ≤350 LOC test (six small recipes, ~80 LOC
   each).
3. **T3 — Docs.** Add `docs/j2cl-plugin-slots.md` per §5.1 and
   `j2cl/lit/src/design/README.md` per §5.2. T3 also makes the three
   Stitch screen calls (`mcp__stitch__generate_screen_from_text`)
   for read surface, compose surface, and live-update pulse moment;
   the resulting screen IDs are pasted into the README. No
   verification beyond build (markdown + Stitch only).
4. **T4 — Demo route.** Add `HtmlRenderer.renderJ2clDesignPreviewPage`
   + the admin-gated `q=design-preview` sub-branch in
   `WaveClientServlet`; add `HtmlRendererJ2clDesignPreviewPageTest`
   and `WaveClientServletDesignPreviewBranchTest`. Run
   `sbt -batch j2clProductionBuild` and the JUnit tests via the
   established Ant path used by other HtmlRenderer tests. ≤220 LOC
   production + ≤140 LOC test.
5. **T5 — User-menu rename.** Edit the two `menu-section` blocks in
   `HtmlRenderer.java` (lines 4467-4476 + 4656-4665). Update
   `HtmlRendererTopBarTest.java:38`. Add
   `HtmlRendererPluginsMenuTest`. Add the changelog entry. Run the
   full SBT chain again. ≤20 LOC production + ≤80 LOC test.

After T5, the lane is implementation-complete. Self-review the diff,
run the implementation-review subagent, address feedback, then open
the PR.

## 10. Risks + non-goals

### Risks

- **Token name collisions.** F-0 uses the `--wavy-*` namespace
  exclusively; the existing `--shell-*` tokens are untouched. F-2/F-3/F-4
  consume `--wavy-*` directly. **Mitigation:** namespace check in the
  token test (`wavy-tokens.test.js` asserts the prefix on every probe).
- **Plugin-slot context drift.** The four data-attribute names are the
  contract; renaming any of them is a breaking change for plugins.
  **Mitigation:** the per-recipe test asserts the exact attribute
  names, so an accidental rename trips CI before it ships.
- **Demo route being mistaken for a feature flag surface.** The demo
  route is documented in `docs/j2cl-plugin-slots.md` as a design
  scaffold, not a user-facing surface. **Mitigation:** the
  user-menu link is labelled "Design preview" (not "Plugins") so
  end-users won't expect plugin-management UX there.
- **Light-mode contrast regressions.** The light variant inverts
  surfaces and text but keeps the same accent saturation, which can
  reduce contrast for the muted text. **Mitigation:** the
  `wavy-tokens.test.js` test in §8.1 mechanically computes the WCAG
  relative luminance for body / muted / quiet text against the
  surface for all three themes and asserts each pair clears the
  per-theme floor (4.5:1 for dark/light, 7:1 for contrast). The demo
  route lets a reviewer eyeball it as well, but CI catches drift.

### Non-goals

- **No** behavior changes to the read / compose / live surfaces. F-2,
  F-3, F-4 own those.
- **No** plugin-registration mechanism. F-0 specifies the slot
  contract only. The robots/data-API plugin registry is a separate
  future issue.
- **No** modernization of the legacy GWT root. Out of scope per
  parity matrix §1.
- **No** default-root cutover. Gated by the parity gate per issue
  map §5.
- **No** webfont loader. The font-family stacks gracefully fall back
  to the system stack already used by the legacy chrome. A future
  issue can add @font-face declarations for Space Grotesk / Inter /
  Geist.

## 11. Closeout

- PR title: `F-0: Refresh Lit design packet for wavy aesthetic +
  plugin slot contracts`.
- PR body: closes #1035, updates #904.
- Auto-merge: squash on green CI.
- Evidence comments on #1035 (plan, implementation summary, PR opened,
  merged) and on #904 (PR opened, merged).
- F-2/F-3/F-4 lanes consume the packet by:
  - importing `j2cl/lit/src/design/wavy-tokens.css` via the bundle
    (already wired by F-0; consumers do nothing extra),
  - wrapping their per-blip / per-composer / per-rail / per-toolbar
    Lit elements in the corresponding F-0 recipe (or composing the
    recipe inside their elements) so the named slots are exposed,
  - reading the slot-context contract from `docs/j2cl-plugin-slots.md`.
