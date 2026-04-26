# Wavy — Firefly Signal design packet

Status: F-0 (issue [#1035](https://github.com/vega113/supawave/issues/1035))
Owner: F-0 design foundation lane.
Consumers: F-2, F-3, F-4 mount the recipes; future robots/data-API
plugins fill the named slot points.

## Source of truth

The packet's aesthetic, palette, motion language, and roundness
choices are pinned to the Stitch design system **"Wavy — Firefly
Signal"** in project
[`15560635515125057401`](https://stitch.withgoogle.com/projects/15560635515125057401)
(asset `assets/6962139294633729409`, version 1). The full design
markdown is reproduced below verbatim so the packet stays
self-contained even if Stitch becomes unavailable.

> # Wavy — Firefly Signal Design System
>
> **Inspiration:** the 'send a wave' communication idiom from the
> Firefly TV series. Messages travel through space as living signals
> — distant, warm, and alive. The UI should feel like an open
> channel: the surface listens, glows when a wave arrives, and stays
> out of the way when it doesn't.
>
> ## Core principles
> - **Signal over chrome.** The UI is mostly negative space; the
>   wave content is the signal. Chrome recedes.
> - **Live-update presence.** When a wave arrives or a blip updates,
>   the surface acknowledges it with a brief pulse on the
>   signal-tone accent. Movement is purposeful, never decorative.
> - **Conversational warmth.** This is not a corporate inbox. Blips
>   feel like voices in a room — soft cards with breathing room,
>   not stacked rows.
> - **Conversational density.** Read-mode is reading-first: long-form
>   line lengths, optical reading rhythm, not a feed of tiles.
> - **Holographic depth.** Cards have a faint inner-glow border on
>   the signal tone; depth comes from light, not shadow stacks.
> - **Plugin slots are first-class.** Blip-extension,
>   compose-extension, rail-extension, toolbar-extension slots are
>   part of the visual rhythm, not afterthoughts.
>
> ## Palette
> - Background: deep midnight blue (`#0b1320`) — feels like looking
>   out a ship porthole.
> - Surface: a half-shade lighter, with a hairline signal-tone border.
> - Primary signal: cyan (`#22d3ee`) — the 'wave just landed' tone.
>   Use sparingly: focus frame, live-update pulse, unread badge,
>   primary action.
> - Secondary signal: violet (`#7c3aed`) — for mention chips and
>   reactions.
> - Tertiary signal: warm amber (`#fb923c`) — for tasks and
>   time-sensitive flags.
> - Text: neutral cool whites; never pure white. Body text at ~92%
>   opacity for warmth.
>
> ## Motion language
> - **Signal pulse**: 600ms outward glow on the signal tone when a
>   live-update applies. Limited to one pulse per blip per second to
>   avoid noise.
> - **Focus transition**: 180ms ease-out; focus frame slides between
>   blips like a cursor in a console.
> - **Collapse animation**: 240ms ease-in-out height + opacity;
>   never instant.
> - **Fragment-window load**: 300ms fade-in from a low-contrast
>   skeleton; skeleton uses the signal tone at low alpha.
>
> ## Iconography
> - Waveform / signal motif preferred over generic Material icons
>   where possible (reply = sound-wave-and-arrow, send =
>   chevron-with-trail).
> - Stroke-only icons; never filled.
>
> ## Typography
> - Headline: Space Grotesk — slightly geometric, gives the chrome a
>   'flight deck' feel without being silly.
> - Body: Inter — high readability at small sizes, the wave
>   content's home.
> - Label: Geist — used for chips, badges, timestamps.
> - Reading-density target: ~70 characters per line in the wave
>   panel.
>
> ## Roundness
> - Cards: 12px (subtle, not playful).
> - Chips and badges: full pill.
> - Inputs: 12px to match cards.
>
> ## Plugin-slot visual rhythm
> - A slot looks like a faintly outlined region with a slot-name
>   label in the corner when empty in design previews; in
>   production, an empty slot collapses to zero height.
> - Plugins inherit the design tokens by default but can opt out
>   per the plugin contract.
>
> ## Light mode
> - Inverted: pale near-white background, midnight ink for text,
>   same accents but at higher saturation. Maintain the 'living
>   signal' feel; do not flatten.

## Stitch screens (issue body §Acceptance bullet 2)

The Stitch project hosts three desktop screens that act as visual
references for the packet. They predate this lane in some cases
(the project was bootstrapped before F-0); use the live demo route
at `?view=j2cl-root&q=design-preview` (admin-or-owner gated, see
plan §6.1) as the canonical interactive reference for the recipes
implemented in this packet.

| Title | Stitch screen ID | Purpose |
| --- | --- | --- |
| SupaWave — Reading Surface | `projects/15560635515125057401/screens/f7867d161cfc4b7ba1c1af37e7b75d5b` | Read surface — focused blip in a wave thread with rail. |
| SupaWave — Reading Surface (alt) | `projects/15560635515125057401/screens/cbd4a9e7a9f54eae85f8304aedf95e41` | Compose surface and live-update pulse moment hints (alt composition of the read surface). |
| SupaWave — Depth-Navigation | `projects/15560635515125057401/screens/7f0b4a1dee01438997019e4ff7948cc1` | Depth-nav breadcrumb ("Inbox › Sample wave › Top thread"). |

Future bumps to the design system version will add screens for the
compose surface and the live-update pulse moment as standalone
generations; the demo route already mounts both as live recipes.

## Tokens

`wavy-tokens.css` ships every CSS custom property that F-2/F-3/F-4
consume. **Only `--wavy-*` is part of the F-0 contract**;
`--shell-*` is the legacy #962 packet, kept for back-compatibility
but not the design language for new surfaces.

The full token contract lives in
[`docs/superpowers/plans/2026-04-26-issue-1035-wavy-design-foundation.md`
§3](../../../docs/superpowers/plans/2026-04-26-issue-1035-wavy-design-foundation.md).
At a glance:

- Color (14 tokens): bg-base / bg-surface / border-hairline; text
  body / muted / quiet; signal cyan / violet / amber (each with a
  -soft alpha companion); focus-ring + pulse-ring derived tokens.
- Typography (10 tokens): three font-family stacks (Space Grotesk
  headline, Inter body, Geist label) and a seven-step type scale
  (display through meta).
- Motion (7 tokens): four durations (pulse 600ms, focus 180ms,
  collapse 240ms, fragment-fade 300ms) and three easings.
- Spacing + shape (10 tokens): an 8-step 4-px-base spacing scale
  and two roundness presets (`--wavy-radius-card` 12px,
  `--wavy-radius-pill` full).

The tokens emit as a separate esbuild entry
(`war/j2cl/assets/wavy-tokens.css`) and are loaded by the J2CL
root shell via `<link>` so the recipes can resolve them.

## Recipes

`wavy-tokens.css` is paired with six Lit recipe elements that
consume the tokens. Each is a small, tested LitElement that exposes
the appropriate plugin slot.

| Recipe | File | Plugin slot | Owning consumer |
| --- | --- | --- | --- |
| `<wavy-blip-card>` | `wavy-blip-card.js` | `blip-extension` (M.2) | F-2 |
| `<wavy-compose-card>` | `wavy-compose-card.js` | `compose-extension` (M.3) | F-3 |
| `<wavy-rail-panel>` | `wavy-rail-panel.js` | `rail-extension` (M.4) | F-2 (F-4 placeholder) |
| `<wavy-edit-toolbar>` | `wavy-edit-toolbar.js` | `toolbar-extension` (M.5) | F-3 |
| `<wavy-depth-nav>` | `wavy-depth-nav.js` | — (chrome only) | F-2 |
| `<wavy-pulse-stage>` | `wavy-pulse-stage.js` | — (live-update helper) | F-3 (and the demo route) |

See [`docs/j2cl-plugin-slots.md`](../../../docs/j2cl-plugin-slots.md)
for the full per-slot contract (mount point, slot context,
allowed children, owning slice).

## Variants (dark / light / contrast)

The packet ships three theme variants:

- **Dark** (default; matches Stitch `colorMode: DARK`). Selectors:
  `:root`, `[data-wavy-theme="dark"]`.
- **Light** (auto under `prefers-color-scheme: light`, override
  via `[data-wavy-theme="light"]`). Inverts surfaces and text;
  signal accents stay constant per Stitch.
- **High contrast** (auto under `prefers-contrast: more`, override
  via `[data-wavy-theme="contrast"]`). Issue body §Scope.1
  mandate. Surfaces go to true black, borders solidify, text raises
  to ≥7:1 contrast (WCAG AAA), focus-ring thickens to 3px.

A scope element (`<section data-wavy-theme="light">`) overrides the
media query inside its subtree, so the design preview can mount
all three side-by-side.

## How F-2 / F-3 / F-4 consume the packet

- **CSS tokens.** No action needed — the J2CL root shell HTML
  template loads `wavy-tokens.css` as a sibling stylesheet, and the
  recipes resolve `var(--wavy-*)` automatically when mounted under
  `?view=j2cl-root`.
- **Recipes.** Wrap per-blip / per-composer / per-rail / per-toolbar
  Lit elements in the corresponding F-0 recipe (or compose the
  recipe inside their elements) so the named slots are exposed.
- **Slot contract.** Read
  [`docs/j2cl-plugin-slots.md`](../../../docs/j2cl-plugin-slots.md)
  for the per-slot mount point, slot context, and allowed
  children.
