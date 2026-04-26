# J2CL plugin slots

Status: Draft (F-0, issue [#1035](https://github.com/vega113/supawave/issues/1035))
Owner: F-0 design foundation lane.
Consumers: F-2 ([#1037](https://github.com/vega113/supawave/issues/1037)) mounts blip + rail; F-3 ([#1038](https://github.com/vega113/supawave/issues/1038)) mounts compose + toolbar.

## Overview

The J2CL parity client reserves four named Lit slots so plugins
registered against the forthcoming robots/data-API plugin registry
can render content into specific seams in the read, compose, rail,
and toolbar surfaces.

F-0 specifies **only** the slot contract (mount point, slot context,
allowed children, styling/lifecycle/accessibility/rollback). The
plugin **registration** mechanism is a separate, future
robots/data-API issue. The four slots exist whether or not any
plugin is registered against them; in production they collapse to
zero height when empty, and in design preview (`<html
data-wavy-design-preview>` set) they render a faint dashed outline
with the slot name so designers can see where plugins land.

The four slots map to inventory rows M.2–M.5 from the 2026-04-26
GWT functional inventory
(`docs/superpowers/audits/2026-04-26-gwt-functional-inventory.md`).

## The four slots

### `blip-extension` — per blip card (M.2)

- **Mount point.** Inside the `<wavy-blip-card>` recipe element
  (`j2cl/lit/src/design/wavy-blip-card.js`), in the slot wrapper
  named `blip-extension` rendered between the blip body and the
  reactions row.
- **Slot context (read-only).** Plugins read four data attributes
  reflected on the host element:
  - `data-blip-id` — the blip's wave-relative ID.
  - `data-wave-id` — the parent wave ID.
  - `data-blip-author` — the author's display name.
  - `data-blip-is-author` — `"true"` when the viewer authored the
    blip, `"false"` otherwise.
  Plugins read these via
  `slot.assignedElements()[0].closest('wavy-blip-card').dataset`.
  Richer plugins can also read the host element's `blipView`
  property, which returns a frozen snapshot
  `Object.freeze({ id, waveId, authorName, postedAt, isAuthor })`.
  Mutation throws under strict mode.
- **Allowed children.** Any HTML or custom-element subtree. The
  slot does not impose tag restrictions, but plugins are expected
  to keep DOM size small (one anchor element, a chip, an icon
  button — not a nested editor).
- **Owning F-* slice.** F-2 ([#1037](https://github.com/vega113/supawave/issues/1037)).

### `compose-extension` — on the inline composer (M.3)

- **Mount point.** Inside `<wavy-compose-card>`
  (`j2cl/lit/src/design/wavy-compose-card.js`), in the slot wrapper
  named `compose-extension` placed in the toolbar row, to the
  right of the H.* edit toolbar.
- **Slot context (read-only).** One data attribute on the host:
  - `data-reply-target-blip-id` — when the composer is a reply,
    the parent blip's ID; absent when composing top-of-thread.
  Richer plugins can read two frozen JS properties on the host:
  - `composerState` — frozen snapshot of the composer state
    (drafts, attachments-staged, etc — populated by F-3).
  - `activeSelection` — frozen snapshot of the current selection
    descriptor.
  Setting either property re-freezes the new value.
- **Allowed children.** Inline-block children that fit in a
  toolbar row (poll button, code-block toggle, inline-diagram
  trigger). Long-form floating UIs should anchor a popover off
  the slotted button rather than render inline.
- **Owning F-* slice.** F-3 ([#1038](https://github.com/vega113/supawave/issues/1038)).

### `rail-extension` — on the right rail (M.4)

- **Mount point.** Inside `<wavy-rail-panel>`
  (`j2cl/lit/src/design/wavy-rail-panel.js`), in the slot wrapper
  named `rail-extension` rendered after the panel body and before
  the footer.
- **Slot context (read-only).** Two data attributes on the host:
  - `data-active-wave-id` — the currently selected wave's ID.
  - `data-active-folder` — the currently selected folder
    (`inbox`, `archive`, etc).
- **Allowed children.** Block-level panels (assistant chat, tasks
  roll-up, integrations status). The host element handles its own
  collapse animation; plugins should not assume their content
  remains visible if the panel is collapsed.
- **Owning F-* slice.** F-2 ([#1037](https://github.com/vega113/supawave/issues/1037)). F-4 may render a placeholder
  here when active-feature surfaces appear.

### `toolbar-extension` — on the H.* edit toolbar (M.5)

- **Mount point.** Inside `<wavy-edit-toolbar>`
  (`j2cl/lit/src/design/wavy-edit-toolbar.js`), in the slot wrapper
  named `toolbar-extension` placed at the right end of the
  formatting controls row.
- **Slot context (read-only).** One data attribute on the host:
  - `data-active-selection` — JSON-encoded selection descriptor.
    The recipe debounces writes to this attribute at ~60 fps so
    noisy selection updates from F-3 do not thrash the DOM. Plugins
    should `JSON.parse` the value lazily on read.
- **Allowed children.** Inline-block children that look at home in
  a pill-shaped toolbar (icon buttons with stroke-only icons,
  small chips). Long-form affordances should anchor a popover.
- **Owning F-* slice.** F-3 ([#1038](https://github.com/vega113/supawave/issues/1038)).

## Visual rhythm

- **Production.** An empty slot collapses to zero height — the
  surface above the slot sits flush with the surface below. No
  plugin = no visual artefact.
- **Design preview.** When `<html data-wavy-design-preview>` is
  set (the design-preview route at
  `?view=j2cl-root&q=design-preview` does this), each empty slot
  renders a 1px dashed hairline border with the slot name as a
  small label in the upper-left corner. This lets a designer see
  exactly where plugins will land before any plugin ships.

## Styling contract

- **Default theming.** Plugins inherit the `--wavy-*` design
  tokens through the cascade — slotted content lives in light DOM
  and inherits host styles. A plugin that uses
  `color: var(--wavy-text-body)` and
  `background: var(--wavy-bg-surface)` looks at home in dark,
  light, and contrast variants automatically.
- **Opt-out.** A plugin that wants to paint its own visual sets
  `data-wavy-plugin-untheme` on its slotted root element. The host
  recipe drops the inner-glow border around the slot wrapper so
  the plugin can present its own surface. The opt-out uses the
  CSS `:has()` selector and requires Chromium ≥105, Safari ≥15.4,
  Firefox ≥121; on engines below this the opt-out gracefully
  no-ops (the inner glow remains visible behind the plugin
  content but does not break it).

## Accessibility contract

- Plugin content must respect the F-0 focus frame. Apply
  `:focus-visible` styles using `box-shadow:
  var(--wavy-focus-ring)` so focus feels consistent with the host
  surface.
- Plugin content must announce its own role/label. The host
  recipe does not add an ARIA role to the slot wrapper, so a
  plugin's elements are responsible for their own `role`,
  `aria-label`, and `aria-current` markup.
- Plugin content must not steal the host's `tabindex=0`. The host
  recipe owns keyboard navigation between blips / compose
  affordances / rail panels; a plugin that reroutes Tab will
  break the parity surface.

## Lifecycle contract

- Slots mount with their host element. A plugin's content is
  inserted into the slot when the host first renders.
- Slots unmount with their host element. When the host element is
  removed from the DOM (wave switch, collapse), the plugin's
  slotted content is removed with it. Plugins must not assume
  persistence across wave-selection.
- A plugin that fails to render (throws during construction or
  first render) must not break the host. The slot stays empty and
  the host renders normally.

## Rollback semantics

- F-0 does not implement a per-slot kill switch. The future
  plugin registry will expose one (so a misbehaving plugin can be
  disabled without a redeploy).
- A plugin that does not render falls through to the empty-slot
  visual rhythm above (zero height in production, dashed outline
  in design preview).
- The host recipe never throws on slotted content — the slot is a
  pass-through. So a plugin that renders broken HTML does not
  break the host's render either; the broken HTML simply paints
  inside the slot.
