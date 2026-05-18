# Issue #1262: J2CL Reply Continuation Indicator Compact

## Root Cause

- J2CL renders the same-level reply continuation affordance in wave-blip as a normal-flow `.continuation-row` below every blip.
- The row is opacity/visibility hidden, but it still has margins and the button's intrinsic height, so compact blip stacks always reserve extra vertical space.
- GWT's `ContinuationIndicator` renders only an icon/bar affordance whose children are absolutely positioned and whose disabled state can be `display:none`; it is not a persistent text badge row in normal flow.

## Implementation Plan

1. Keep the J2CL continuation request event and composer routing unchanged.
2. Restyle `.continuation-row` to have zero normal-flow height and position the trigger absolutely near the bottom of the blip body.
3. Restyle the trigger as a compact GWT-like icon/bar control and remove the visible text label while preserving the aria label and title.
4. Add focused Lit coverage that the continuation host has no layout height, exposes an icon-only affordance, and still emits `wave-blip-continuation-requested`.
5. Add a changelog fragment, run the focused Lit test/build checks, then PR and monitor.

## Self-Review Checkpoints

- Do not alter toolbar Reply behavior; it still creates an inline/nested reply.
- Do not change the continuation event name or detail payload.
- Preserve focus/hover reveal semantics and screen-reader naming.
- Keep the patch scoped to J2CL read-surface chrome.
