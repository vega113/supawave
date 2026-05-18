# Issue 1255: J2CL Stable Wave Load

## Root Cause

- `J2clSelectedWaveController` renders the viewport immediately, then fetches attachment metadata asynchronously and calls `view.render()` again when metadata returns.
- `J2clReadSurfaceDomRenderer.renderWindow()` treats changed attachment render models as a full window mismatch, clears `host.innerHTML`, and recreates every `<wave-blip>`. That makes the read surface visibly flash and reflow for metadata-only updates.
- The renderer creates defined `<wave-blip>` elements with light-DOM body children. The existing `wave-blip:not(:defined)` skeleton only protects server-first/custom-element-not-yet-defined markup; it does not protect client-created, already-defined hosts during Lit's first render.
- Pending attachment tiles reserve only `72px`, then medium/large image attachments expand after metadata and image preview details arrive.

## Plan

- Add regression coverage for attachment metadata refresh preserving the existing blip host.
- Add Lit coverage for clearing a renderer-set pending-upgrade marker after `wave-blip` first renders.
- Add Lit coverage for medium/large pending attachment metadata reserving display-size-specific space.
- Update `J2clReadSurfaceDomRenderer` to stamp a pending-upgrade marker on new `<wave-blip>` hosts and add an attachment-only fast path that updates attachment tile subtrees without clearing the whole read surface.
- Update `wave-blip` to remove the pending-upgrade marker after first render.
- Update read-surface CSS so pending medium/large attachment placeholders reserve closer-to-final space.

## Acceptance Criteria

- Opening a wave in J2CL does not rebuild the full blip stream when only attachment metadata resolves.
- Client-created `<wave-blip>` hosts do not expose raw light-DOM content between host insertion and Lit's first render.
- Pending attachment placeholders reduce layout shift for medium and large attachments.
- Focused Maven and Lit tests pass, plus a narrow local J2CL browser sanity check is recorded.

## Non-Goals

- Do not redesign the J2CL read surface.
- Do not replace the sidecar attachment metadata protocol.
- Do not implement full rich-text annotation parity in this slice.
