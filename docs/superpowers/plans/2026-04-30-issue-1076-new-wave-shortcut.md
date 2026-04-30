# Issue #1076: Shift+Cmd/Ctrl+O New Wave Shortcut

## Context

Issue #1076 closes the F-3 follow-up for B.3: the search rail already renders the New Wave button and advertises `Shift+Meta+O Shift+Control+O`, but the shortcut behavior must match the issue acceptance before the lane can close.

Current code already has a partial G-PORT-7 implementation:

- `j2cl/lit/src/shortcuts/keybindings.js` maps Shift+Cmd+O on Mac and Shift+Ctrl+O elsewhere to `OPEN_NEW_WAVE`.
- `j2cl/lit/src/elements/shell-root.js` listens for window keydown and dispatches `wavy-new-wave-requested` with `source="keyboard-shortcut"`.
- `J2clRootShellController` listens for `wavy-new-wave-requested` on `document.body` and calls `composeController.focusCreateSurface()`.
- `J2clComposeSurfaceView.focusCreateSurface()` focuses the title input, which is correct for the button path from #1081 but not enough for #1076's shortcut-body-focus acceptance.

## Gaps

1. The current keybinding marks `OPEN_NEW_WAVE` as global, so it fires from inputs/contenteditable surfaces. #1076 explicitly says the shortcut must not fire when an editable element owns focus.
2. The shortcut currently reuses the button path and focuses the create title input. #1076 asks the shortcut to focus the create-wave composer body and scroll it into view.
3. There is no `compose.opened` telemetry event with `trigger="shortcut"` for the shortcut path.
4. Existing Lit shortcut tests assert dispatch but not the editable suppression required by #1076.

## Implementation Plan

1. Tighten Lit shortcut semantics.
   - Change `matchShortcut()` so `OPEN_NEW_WAVE` returns `global: false`.
   - Update comments to state that Esc remains global, but Shift+Cmd/Ctrl+O is suppressed from editable targets.
   - Update keybindings tests for the new `global: false` contract.
   - Add a `shell-root` integration test proving Shift+Cmd/Ctrl+O from an input/contenteditable target does not dispatch `wavy-new-wave-requested`.

2. Preserve event bridge and source.
   - Keep `shell-root` dispatching `wavy-new-wave-requested` with `detail.source="keyboard-shortcut"` so the Java root shell can distinguish the shortcut from the button path.
   - Change the search rail button click to emit `detail.source="button"` for explicit telemetry parity and future menu-trigger symmetry.

3. Add trigger-aware create focus in Java.
   - Add `J2clComposeSurfaceController.focusCreateSurface(String trigger)`.
   - Keep existing `focusCreateSurface()` delegating to trigger `"button"` and focusing the title input.
   - For trigger `"shortcut"`, call a new view method `focusCreateComposer()` that focuses the create body textarea and scrolls it into view.
   - Record `compose.opened` telemetry with fields `mode="create"` and `trigger=<button|shortcut|menu>`; sanitize unknown values to `"button"`.

4. Wire Java root event detail to trigger.
   - In `J2clRootShellController`, read `detail.source` from `wavy-new-wave-requested`.
   - Map `keyboard-shortcut` to telemetry trigger `"shortcut"`.
   - Call `composeController.focusCreateSurface(trigger)`.

5. Tests and verification.
   - Lit: `j2cl/lit/test/shortcuts/keybindings.test.js`, `j2cl/lit/test/shortcuts/shell-root-keys.test.js`, and `j2cl/lit/test/wavy-search-rail.test.js`.
   - Java: `J2clComposeSurfaceControllerTest` for button vs shortcut focus calls and `compose.opened` telemetry trigger fields.
   - Java root pure helper test for source-to-trigger mapping if direct DOM event testing is too brittle.
   - Verification:
     - `cd j2cl/lit && npm test -- --files test/shortcuts/keybindings.test.js test/shortcuts/shell-root-keys.test.js test/wavy-search-rail.test.js`
     - `cd j2cl/lit && npm run build`
     - `sbt --batch compile j2clSearchTest`
     - changelog assemble/validate
     - `git diff --check`

## Self Review

- The plan treats the existing G-PORT-7 code as a partial implementation instead of rewriting it.
- It preserves the button/title-focus behavior added by #1081 while giving the shortcut its requested body-focus behavior.
- It makes the editable suppression testable in the Lit layer where the keydown target path is available.
- It keeps telemetry in the Java compose controller so both button and shortcut paths are recorded consistently.
