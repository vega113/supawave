# Issue #1163 J2CL Wave Panel Chrome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the J2CL selected-wave panel layout, splitter/resize affordance, status chrome, and wave-navigation actions to practical GWT parity.

**Architecture:** Keep GWT as the baseline and preserve the existing J2CL selected-wave data pipeline. Add the missing J2CL shell chrome in Lit/CSS, bridge visible controls to the existing Java selected-wave view/controller, and keep raw route/channel/debug strings hidden unless the debug overlay is enabled. Navigation actions should operate on rendered `<wave-blip>` elements in the current selected-wave viewport, matching GWT's toolbar behavior where feasible.

**Tech Stack:** J2CL Java, Lit custom elements, CSS shell tokens, selected-wave read-surface DOM, SBT verification, Lit unit tests.

---

## Files

- Modify `j2cl/lit/src/tokens/shell-tokens.css`: add the desktop splitter track between search rail and wave panel, full-width main behavior, and GWT-like spacing.
- Modify `j2cl/src/main/webapp/assets/sidecar.css`: align server-first selected-wave width, gutters, scroll container, and debug/status visibility.
- Modify `j2cl/lit/src/elements/shell-status-strip.js`: convert raw text status into compact icon/status chips with accessible labels.
- Modify `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellView.java`: publish live status into chip attributes instead of visible raw prose.
- Modify `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootLiveSurfaceModel.java`: expose normalized connection/route/selection status values for icon chrome.
- Modify `j2cl/lit/src/elements/wavy-wave-nav-row.js`: add disabled/available state and event semantics needed by controller wiring.
- Modify `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`: wire wave-nav events to concrete scrolling/focus behavior on the selected-wave content.
- Modify or add tests:
  - `j2cl/lit/test/shell-status-strip.test.js`
  - `j2cl/lit/test/shell-root.test.js`
  - `j2cl/lit/test/wavy-wave-nav-row.test.js`
  - `j2cl/src/test/java/org/waveprotocol/box/j2cl/root/J2clRootShellViewTest.java`
  - `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewChromeTest.java`
- Add `wave/config/changelog.d/2026-05-01-j2cl-wave-panel-chrome-parity.json`.

## Current Findings

- GWT uses `SplitLayoutPanel` in `WebClient` so the search panel and wave panel are separated by a real splitter and the wave panel consumes the remaining width.
- GWT status is icon chrome: `netstatus` uses Wi-Fi icons and `SavedStateIndicator` renders saved/unsaved icons with tooltips.
- J2CL currently has a fixed two-column root grid, no draggable splitter, and status text is still appended into `shell-status-strip`.
- J2CL selected-wave nav buttons render SVG icons, but `J2clSelectedWaveView.bindChromeEvents()` only records telemetry; recent/next/previous/end/mention clicks do not move focus or scroll.
- J2CL debug strings are partially hidden through `data-j2cl-debug-only`, but status surfaces still leak prose such as "Selected wave is active." and runtime details instead of compact chrome.

## Tasks

### Task 1: Layout And Splitter Tests

- [ ] Add Lit tests that mount `shell-root` with nav/main slots and assert the desktop layout includes a splitter column via CSS custom properties.
- [ ] Add a test that verifies the selected-wave main slot is not max-width constrained and can consume the remaining viewport.
- [ ] Add a test for a `data-j2cl-rail-width` attribute or CSS variable update so resize state has a concrete observable contract.

### Task 2: Implement GWT-Like Split Layout

- [ ] Add a root-shell splitter element between nav and main in the server-rendered J2CL shell markup.
- [ ] Add a small Lit or vanilla controller for pointer/keyboard resizing that updates `--j2cl-search-rail-width` and persists it to `localStorage`.
- [ ] Clamp the rail width to a practical range matching the existing tokens: minimum `260px`, maximum `420px`, and viewport-safe maximum of `50vw`.
- [ ] Keep mobile behavior single-column with no visible splitter under the current `860px` breakpoint.

### Task 3: Status Icon Chrome

- [ ] Change `shell-status-strip` to render named chips for `connection`, `saved`, and selected-wave route state using icons and `aria-label`; visible raw status text must be hidden from normal users.
- [ ] Keep a screen-reader live region for state changes without showing route/channel/snapshot prose in the UI.
- [ ] Update `J2clRootLiveSurfaceModel` and `J2clRootShellView` so the Java publisher sets status attributes such as `data-route-state="selected-wave"` and live text separately.
- [ ] Preserve visible error text for selected-wave failures; only normal debug/status prose is hidden.

### Task 4: Navigation Action Behavior

- [ ] In `J2clSelectedWaveView`, implement handlers for:
  - `wave-nav-recent-requested`: focus/scroll to the most recently timestamped rendered blip, falling back to last rendered blip.
  - `wave-nav-next-unread-requested`: focus/scroll to the next rendered unread blip, falling back to first unread.
  - `wave-nav-previous-requested`: focus/scroll to previous rendered `wave-blip`.
  - `wave-nav-next-requested`: focus/scroll to next rendered `wave-blip`.
  - `wave-nav-end-requested`: focus/scroll to last rendered `wave-blip`.
  - `wave-nav-prev-mention-requested` and `wave-nav-next-mention-requested`: focus/scroll between rendered `wave-blip[has-mention]`.
- [ ] Use `focus({ preventScroll: true })` followed by `scrollIntoView({ block: "center" })` so the action does not jump to the bottom unexpectedly.
- [ ] Keep archive/pin/version-history behavior owned by the existing `wave-action-bar-controller.js`; do not duplicate it in Java.
- [ ] Add tests that verify events move a focused marker across synthetic `wave-blip` elements.

### Task 5: Server-First And Debug Surface Parity

- [ ] Update `HtmlRenderer` server-first root markup so normal J2CL route/channel/snapshot details are emitted as debug-only attributes or hidden diagnostic text, not visible body copy.
- [ ] Add/adjust Jakarta renderer tests to assert the public J2CL root shell has icon-status chrome and no visible raw `channel`/`snapshot` status copy in the normal path.
- [ ] Keep the read-surface preview fixture diagnostic strings gated by `data-j2cl-debug-only`.

### Task 6: Verification And Evidence

- [ ] Run focused Lit tests:
  - `cd j2cl/lit && npm test -- --files test/shell-status-strip.test.js test/shell-root.test.js test/wavy-wave-nav-row.test.js`
- [ ] Run focused JVM tests:
  - `sbt --batch j2clSearchTest`
- [ ] Run broader SBT verification:
  - `sbt --batch compile j2clSearchTest j2clLitTest`
- [ ] Run changelog validation:
  - `python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
- [ ] Run `git diff --check`.
- [ ] Update #1163 with worktree path, branch, plan path, commit SHAs, verification, review-comment handling, and PR URL.

## Self-Review

- Spec coverage: The plan covers #1163 layout width, search/wave spacing, splitter/resize, status icons, hidden raw status/debug text, and navigation action behavior. It intentionally leaves participants, editor buttons, attachment sizing, and inline blip/thread rendering to #1164-#1167.
- Placeholder scan: No task says "TBD" or relies on an unspecified framework. All implementation seams and verification commands are named.
- Risk: The hardest part is "recent activity" because the J2CL rendered viewport may not contain the whole wave. The task defines a viewport-local behavior with a fallback to last rendered blip; if full-wave navigation is required, it should become a follow-up that asks the backend for off-viewport anchors.
- Review policy: Claude Code is not used for this lane. Review will be self-review plus GitHub PR review comments only.
