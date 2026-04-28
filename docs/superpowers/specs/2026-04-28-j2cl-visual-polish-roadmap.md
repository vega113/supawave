# J2CL Visual Polish Roadmap (V-1..V-5) — 2026-04-28

Status: Active
Owner: J2CL visual-polish sprint
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Functional sprint: [J-UI-1..J-UI-8 umbrella #1078](https://github.com/vega113/supawave/issues/1078) (closed — all 8 slices merged)
Mockups: [`docs/superpowers/mockups/2026-04-28-j2cl-functional-ui/`](../mockups/2026-04-28-j2cl-functional-ui/)

## 1. Why this exists

The J-UI-1..J-UI-8 sprint shipped matrix-row acceptance — every wiring works,
every DocOp round-trips, every flag is right. But the surface at
`?view=j2cl-root` does **not** match the Wavy mockups. Concretely:

- Developer-facing strings render as product UI: `Read.`, `Live updates
  connected.`, the channel/snapshot URL, `OPENED WAVE`, `Reply target:
  b+<id>`.
- The format toolbar is 22 text-labelled buttons stacked like a debug palette;
  mocks specified a compact icon row.
- The shell composes as a narrow column of stacked cards, not the rail+pane
  layout from `01-shell-inbox-with-waves.svg`.
- The open wave shows blip body without per-blip author/avatar/timestamp
  chrome; J-UI-4 wired the data path but the visual chrome never landed.
- A `Plugins` debug strip and a giant top whitespace gap appear on the
  product route.

This roadmap fixes that. Acceptance is **screenshot diff vs. mockup**, not
matrix rows.

## 2. Hard acceptance contract

Every slice issue must include in its PR body:

1. **Mockup reference** — the exact SVG file and section under
   `docs/superpowers/mockups/2026-04-28-j2cl-functional-ui/`.
2. **Before screenshot** — captured on `?view=j2cl-root` against `main` HEAD
   prior to the slice.
3. **After screenshot** — captured on the slice branch with the same window
   size and signed-in user.
4. **Side-by-side annotation** — explicit list of what visually changed and
   how it maps to the mockup.

A reviewer is allowed to reject a PR purely on visual-fidelity grounds, even
if every test passes. This is the missing gate from the prior sprint.

## 3. Slices

### V-1. Shell chrome rebuild
- **Goal**: Match `01-shell-inbox-with-waves.svg` — header bar with logo +
  search inline + user menu; rail on the left with digest cards; wave panel
  centered; no narrow-column stacked-card layout; no `Plugins` debug strip on
  the user route.
- **Files**: `j2cl/lit/src/elements/shell-root.js`,
  `shell-nav-rail.js`, `shell-main-region.js`, top-level CSS.
- **Out of scope**: V-2 string scrubbing, V-3 toolbar icons.

### V-2. Strip developer strings from product UI
- **Goal**: Move every dev-facing string to a debug overlay behind an
  experimental flag (`j2cl-debug-overlay`), default off in prod.
- **Strings to remove from the user route**: `Read.`, `Live updates
  connected.`, the channel/snapshot URL, `OPENED WAVE`, `Reply target:
  b+<id>`, the `supawave.ai/w+…/conv+root · channel chN · snapshot vNNN`
  status row, the version-history "Recent / Next unread / Previous / Next /
  End / Archive / Unpin" tab strip in its current text-button form (kept
  feature, but redesigned per V-1).
- **Files**: `J2clRootLiveSurfaceModel.java`, `shell-status-strip.js`,
  `wavy-blip-card.js`, any element rendering raw blip IDs as user content.
- **Out of scope**: V-1 layout, V-3 toolbar.

### V-3. Real format toolbar with icons
- **Goal**: Match `03-inline-rich-text-composer.svg` — compact icon row,
  selection-driven toggle state, grouped (text formatting / lists / alignment
  / link / clear). Replace the 22 text-labelled buttons with icons.
- **Files**: `j2cl/lit/src/elements/wavy-format-toolbar.js`,
  `wavy-composer.js`, icons under `j2cl/lit/src/icons/` (add if missing).
- **Out of scope**: attachment buttons (V-1 places them; their visual
  upgrade can be a follow-up if needed).

### V-4. Per-blip chrome on open wave
- **Goal**: Match `02-open-wave-threaded-reading.svg` — every blip in the
  open wave has author avatar + display name + relative timestamp row; focus
  frame visible on the focused blip; collapse chevrons on threads with replies.
- **Files**: `j2cl/lit/src/elements/wavy-blip-card.js`,
  `wavy-blip-toolbar.js`, read-surface DOM renderer.
- **Out of scope**: thread-collapse functionality (already in J-UI-4 — only
  visual chrome here).

### V-5. Density tuning
- **Goal**: Reduce wave-list card height; widen wave panel; remove the giant
  top whitespace gap; align card / pane proportions with
  `01-shell-inbox-with-waves.svg`.
- **Files**: `wavy-tokens.css`, `shell-root.js` layout, rail / pane width
  tokens.
- **Out of scope**: V-1's overall layout (V-5 is fine-tuning on top).

## 4. Sequencing

V-1 first (foundational layout). Then V-2 (strings) and V-3 (toolbar icons)
in parallel. Then V-4 (blip chrome). Then V-5 (density tuning) as final
polish.

```
V-1 → V-2  ┐
       └─ V-4 → V-5
   → V-3  ┘
```

## 5. Workflow per slice

Same full drill as J-UI sprint (plan → review → implement → review → QA →
flag → changelog → PR), with the additional **mockup-screenshot diff** as
hard PR-body acceptance. Each PR receives a `wave-pr-monitor` pane until
merged.

## 6. Out of scope for this sprint

- New features beyond what's in the mockups
- Attachment-rendering visual polish (defer to follow-up)
- Mobile / dark-mode polish (mocks `06-mobile-inbox-and-wave.svg` and
  `07-dark-mode-shell.svg` exist but aren't in this sprint — file
  separately if surfaced)
- Plugin slot redesign
- Animations / motion (defer)
