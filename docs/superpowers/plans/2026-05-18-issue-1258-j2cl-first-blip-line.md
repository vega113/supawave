# Issue #1258: J2CL First-Blip Focus Line

## Root Cause

The J2CL read renderer programmatically selects the initial blip on first wave
open so keyboard navigation and mark-read state start from the same place as
GWT. That path currently dispatches `wavy-focus-changed` with an empty key,
which causes the absolute `<wavy-focus-frame>` overlay to paint immediately.
On the first blip, the overlay edge reads as a horizontal line through the blip.

## Plan

1. Add a focused Lit regression test proving the focus-frame stays hidden for
   the initial programmatic focus event (`key=""`) and still appears for
   explicit keyboard navigation.
2. Update `<wavy-focus-frame>` so an empty-key first focus is tracked but not
   painted; once the user has explicitly navigated, empty-key geometry refreshes
   for the same focused blip continue to update the visible frame.
3. Keep renderer focus markers, tabindex, mark-read, and scroll-to-initial
   behavior unchanged.
4. Add a changelog fragment because this changes visible J2CL behavior.
5. Run focused J2CL renderer/Lit tests plus narrow local smoke/browser
   verification before opening the PR.

## Self-Review

- Scope is limited to the focus-frame presentation policy; no GWT code or task
  rendering behavior changes.
- The regression should fail on current `origin/main` because any non-empty
  focused blip id currently makes the frame visible, regardless of key source.
- Existing focus-frame tests should still pass after updating the visible-frame
  expectation to use a keyboard key, because keyboard navigation remains the
  explicit visible focus affordance.
