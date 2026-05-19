# Issue 1299: J2CL Wave Controls Overlap User Menu

## Root Cause

`wavy-wave-controls-toggle` is emitted as a fixed-position floating mount after `</shell-root>`. Its viewport `top/right` coordinates place it in the same 40px compact topbar band as the right-aligned `wavy-header` user-menu/avatar cluster. On narrow widths, those independent positioning systems overlap.

## Plan

- Add a Lit geometry regression for the compact header plus floating wave-controls toggle.
- Keep the existing floating mount and `wavy-wave-controls-toggled` event behavior unchanged.
- Move the floating toggle below the compact topbar so it cannot occupy the user-menu hitbox.
- Add a changelog fragment because this is user-visible J2CL chrome behavior.
- Verify with the focused Lit test, changelog validation, and `git diff --check`.

## Acceptance Criteria

- The regression fails on the current overlapping geometry.
- The focused `wavy-wave-controls-toggle` test passes after the fix.
- Existing toggle labels, pressed state, and event behavior still pass.
- The changelog fragment validates.
