# J2CL Functional UI — Wavy Mockups

Status: Draft, 2026-04-28
Roadmap: [`../../specs/2026-04-28-j2cl-functional-ui-roadmap.md`](../../specs/2026-04-28-j2cl-functional-ui-roadmap.md)
Audit basis: [`../../audits/2026-04-26-j2cl-gwt-parity-audit.md`](../../audits/2026-04-26-j2cl-gwt-parity-audit.md)
Parity matrix: [`../../../j2cl-gwt-parity-matrix.md`](../../../j2cl-gwt-parity-matrix.md)

## 1. Why these exist

The 2026-04-26 audit verified that `/?view=j2cl-root` is a placeholder shell:
4/26 matrix rows pass, the wave list does not render, blips appear as raw IDs,
and the composer is detached from a non-rich textarea. The roadmap turns
that into eight slice issues (J-UI-1..8). These mockups show, concretely, what
each slice is meant to look like once it lands — using the exact terminology
and component names already present in `j2cl/lit/src/`.

The point of the set is to remove ambiguity for impl lanes: instead of
"renders with a focus frame", the implementer sees the focus frame, the
toolbar layout, the chip styles, and the DOM positions they have to hit.

## 2. Index of surfaces

| # | File | Slice | Matrix rows |
| - | --- | --- | --- |
| 01 | [`01-shell-inbox-with-waves.svg`](01-shell-inbox-with-waves.svg) | J-UI-1, J-UI-2 | R-3.5, R-4.5 |
| 02 | [`02-open-wave-threaded-reading.svg`](02-open-wave-threaded-reading.svg) | J-UI-4 | R-3.1, R-3.2, R-3.3, R-3.6 |
| 03 | [`03-inline-rich-text-composer.svg`](03-inline-rich-text-composer.svg) | J-UI-5 | R-5.1, R-5.2, R-5.7 |
| 04 | [`04-task-toggle-and-done-state.svg`](04-task-toggle-and-done-state.svg) | J-UI-6 | R-5.4 |
| 05 | [`05-new-wave-create-flow.svg`](05-new-wave-create-flow.svg) | J-UI-3 | R-5.1 |
| 06 | [`06-mobile-inbox-and-wave.svg`](06-mobile-inbox-and-wave.svg) | J-UI-1, J-UI-4, J-UI-5, J-UI-6 (mobile) | R-3.5, R-4.5, R-5.1, R-5.4 |
| 07 | [`07-dark-mode-shell.svg`](07-dark-mode-shell.svg) | J-UI-1 (dark variant) | R-3.5, R-4.5 |
| 08 | [`08-server-first-paint.svg`](08-server-first-paint.svg) | J-UI-8 | R-6.1, R-6.3 |

J-UI-7 ("mark-as-read + live unread decrement") is not its own dedicated
mockup — it shows up in #01 as the cyan unread badge with the pulse-ring
animation, and in #07 (dark) as the same badge against the dark surface. The
visual contract is "badge count drops, ring pulses once on mutation, then
the card de-emphasises" — calling that out in the slice issue is enough; a
dedicated SVG would only repeat #01.

## 3. Component mapping (what each mockup is implementable with)

All component names below already exist in `j2cl/lit/src/`. The mockups
are intentionally constrained to that set so the implementer never has to
invent a component to ship a slice.

### Shell skeleton
- `shell-root` — top-level shell, owns route state and theme attribute
- `shell-header` — top bar with logo, search, +New Wave, profile
- `shell-nav-rail` / `wavy-search-rail` — left rail with saved searches and
  filter strip
- `shell-main-region` — the wave reading panel
- `shell-status-strip` — bottom-of-rail status text (used in #01 dark/light)
- `shell-skip-link` — implied by header but not visible in the mockups

### Search rail
- `wavy-search-rail` — saved-search folder list (Inbox/Mentions/Tasks/
  Public/Archive/Pinned), filter strip, +Manage saved searches
- `wavy-search-rail-card` — digest card: avatar stack (max 3 + overflow
  chip), title, snippet, msg count, unread badge, relative timestamp
- `wavy-search-help` — helper text on empty state (referenced by status
  strip in #01)

### Wave reading
- `wave-blip` — author + timestamp + body, indented by depth
- `wave-blip-toolbar` — per-blip toolbar (reply / edit / delete / link /
  task)
- `wavy-focus-frame` — cyan ring + glow shown on the focused blip in #02
  and #06
- `wavy-thread-collapse` (`wavy-thread-collapse.css`) — chevron + collapsed
  count line ("3 collapsed replies")
- `wavy-floating-scroll-to-new` — the cyan "↓ N new blips below" pill
- `wavy-back-to-inbox` — mobile-only back button in #06
- `wavy-depth-nav-bar` — breadcrumb under deep threads (latent, not all
  mockups show it)

### Composer
- `composer-shell` — modal composer wrapper used by #05
- `composer-inline-reply` — inline composer dropping into the thread at
  the parent blip in #03
- `wavy-composer` — shared contenteditable surface (replaces the legacy
  textarea)
- `wavy-format-toolbar` — selection-driven floating toolbar with bold /
  italic / underline / strike / heading / list / alignment / link /
  inline code
- `composer-submit-affordance` — Send / Cancel pair in #03 and #05
- `compose-attachment-picker` / `compose-attachment-card` — attachment row
  in #05
- `mention-suggestion-popover` — `@yuri` autocompletion in the participants
  field of #05

### Tasks, mentions, reactions
- `wavy-task-affordance` — toggle button on the per-blip toolbar
- `task-metadata-popover` — assignee / due / priority editor in #04
- `reaction-row` / `reaction-picker-popover` / `reaction-authors-popover`
  — reaction strip in the dark wave panel of #07

### Misc
- `wavy-confirm-dialog` — referenced by Delete in toolbar, not drawn
- `wavy-link-modal` — referenced by 🔗 Link in toolbar, not drawn
- `wavy-version-history` — referenced but out of scope
- `wavy-profile-overlay` — referenced by header avatar, not drawn
- `wavy-tags-row` — tags chips in the right info panel of #02

### Server-side companion (J-UI-8)
- `J2clSelectedWaveSnapshotRenderer` (Java, server) — produces the
  pre-boot HTML in #08. Expected to share class names with the Lit shell
  so customElements upgrade in place.

## 4. Gap analysis

Most of #01..#07 should be implementable with the components already in
`j2cl/lit/src/elements/`. Gaps the mockups make explicit:

1. **Wave list materialisation (J-UI-1)** — `wavy-search-rail-card` exists
   but is not being instantiated for J2CL search results. Slice 1 wires
   the search-result presenter to render the cards instead of the
   `Showing search results for in:inbox.` text-only fallback.
2. **Filter chip composition (J-UI-2)** — `wavy-search-rail` already owns
   the chip strip; the gap is that the chip toggles do not affect the
   query the search-result presenter consumes. Slice 2 closes that loop.
3. **Threaded reading DOM (J-UI-4)** — `wave-blip` exists but in the lived
   J2CL surface today the wave panel renders raw blip IDs. Slice 4 wires
   the conversation-DOM walker through `shell-main-region` so each blip
   becomes a `wave-blip` instance. The focus frame component
   (`wavy-focus-frame`) is already there — it just needs the keyboard
   presenter that moves it.
4. **Inline contenteditable composer (J-UI-5)** — `wavy-composer` and
   `wavy-format-toolbar` exist; today the composer is a `<textarea>`.
   Slice 5 swaps to `composer-inline-reply` anchored to the focused blip,
   wires the toolbar to selection events, and routes Send to the correct
   parent blip.
5. **Task DocOp round-trip (J-UI-6)** — `wavy-task-affordance` exists but
   today the toggle is a UI flag that resets on reload. Slice 6 routes
   the toggle through the wavelet's task DocOp so the state survives
   reload and propagates to other clients.
6. **Server-first first-paint (J-UI-8)** — `J2clSelectedWaveSnapshotRenderer`
   is the named server-side seam. The mockup pins the contract:
   pre-boot HTML must place the same DOM nodes (matching tag names and
   IDs) that the customElements upgrade expects, so hydration is in-place
   and there is no flash.
7. **Dark-token coverage** — `wavy-tokens.css` already ships dark and
   high-contrast variants. The dark mockup #07 reuses those tokens
   verbatim and does not propose new ones. The page-level theme is
   selected via `[data-wavy-theme="dark"]` on the shell-root scope or via
   `prefers-color-scheme: dark`. No new design tokens are required for
   this roadmap.

## 5. Wavy aesthetic principles applied

The Wavy "Firefly Signal" packet (Stitch project 15560635515125057401, asset
6962139294633729409) is the source of truth — these mockups use it
verbatim, never a re-interpretation:

- **Colour.** Cyan `#22d3ee` is the primary signal — used for focus rings,
  unread badges, primary action chips, +New Wave, and the cyan glow on
  hydrated state. Violet `#7c3aed` carries identity (avatars, mention
  dots, person chips). Amber `#fb923c` carries pending / task state.
  Surface and text triads flip wholesale between light and dark; accents
  do not flip.
- **Typography.** Space Grotesk for display and headings (wave titles,
  shell logo wordmark, section headers). Inter for body. Geist for
  labels, meta, and keyboard-cap glyphs. Sizes follow `--wavy-type-*`:
  display for the page-level wave title, h2/h3 for section titles, body
  for blip text, label for chip text, meta for relative timestamps and
  letter-spaced eyebrows.
- **Spacing.** 4px base. Rail width 296px (= 8 × 37 ≈ tied to the 4px
  rhythm with the saved-search row standardised to 40px). Card radius
  `--wavy-radius-card: 12px`. Pill radius `--wavy-radius-pill` for chips.
- **Motion.** Pulse ring on the just-mutated unread badge
  (`--wavy-motion-pulse-duration` 600ms, `--wavy-easing-pulse`).
  Focus-frame transitions at 180ms with `--wavy-easing-focus`. Thread
  collapse at 240ms. Reduced-motion media query collapses all four to
  ~0 — the SVGs use SMIL animations that the browser will skip when
  `prefers-reduced-motion: reduce` is set.
- **Hairline borders.** Cyan-alpha hairlines `rgba(34,211,238,0.18)` carry
  the surface seams in dark; black-alpha `rgba(11,19,32,0.10)` in light.
  Edges, not light, do the contrast work in the high-contrast variant.
- **Glow vs flat.** Cyan glow filter is reserved for state-change moments
  (just-pushed action, just-upgraded shell, just-arrived blip). Steady
  state is flat fills + hairlines.

## 6. Terminology guarantees

Every visible string in the mockups uses the canonical terminology:

- **Wave**, never "thread" / "conversation"
- **Blip**, never "message" / "post"
- **Inbox / Mentions / Tasks / Public / Archive / Pinned**, the canonical
  six saved searches as defined in `wavy-search-rail.js`
- **Reactions**, **Mentions**, **Tasks**, **Tags** at the wave level
- **Mark read / Mark unread**, never "Mark seen"
- **Pin** at the wave level (the "Pinned" folder is its destination)

## 7. Out of scope

- **Reactions add/remove flow** ships visible in #02 and #07 (reaction
  strip) but the picker popover walkthrough is deferred — it lives behind
  F-3 follow-ups #1073–#1076 and is not blocking "actually usable
  interface" per roadmap §5.
- **Mentions autocomplete** is shown in #05 (suggested chips below the
  participants field) but the per-blip @-autocomplete popover is the
  same mention-suggestion-popover and is not separately mocked.
- **Attachments inline rendering** is shown as a single attachment card
  in #05; the broader gallery / lightbox surface is a follow-up.
- **Default-root cutover** (#923 / #924) is gated by the parity matrix
  §8 vote and remains a separate decision after this roadmap closes.
- **Visual latitude beyond the Wavy "Firefly Signal" packet** is
  explicitly deferred — these mockups commit to the existing tokens
  and do not introduce new design directions.

## 8. Verifying the mockups locally

```
cd docs/superpowers/mockups/2026-04-28-j2cl-functional-ui
python3 -m http.server 8080
# open http://localhost:8080/01-shell-inbox-with-waves.svg ...
```

All eight files are pure SVG — no external assets, no JS, no fonts beyond
the system stack listed in `wavy-tokens.css`. Browsers will fall back to
Inter / system if Space Grotesk and Geist are not installed; the mockups
remain readable.
