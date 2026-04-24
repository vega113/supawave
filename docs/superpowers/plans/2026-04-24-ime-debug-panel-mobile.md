# IME Debug Panel Mobile Usability Plan

Issue: https://github.com/vega113/supawave/issues/1005
Branch: `codex/ime-debug-panel-mobile-1005`
Worktree: `$WORKTREE/codex-ime-debug-panel-mobile-1005`

## Root Cause

`ImeDebugTracer.ensureOverlayJsni()` creates a single fixed bottom overlay as soon as
the tracer is enabled. It starts fully expanded with `max-height:45%`, captures
pointer events, and appends log rows directly into the overlay. The only built-in
local control is hidden double-tap/Escape clearing, so on mobile the panel covers
the lower blips and offers no obvious minimize or copy-log action.

The server-rendered mobile chrome controls also render as visible text pills
(`Tags`, `Tag Pin`, `Pin`) and the state-sync script rewrites the full button
text on every update. That makes the tag controls look bolted on over the wave
content and destroys any icon markup that could make the controls compact.

## Acceptance Criteria

- The IME debug panel starts minimized when enabled.
- The minimized state is compact and touch-friendly on mobile: the collapsed
  strip is no taller than the standard 44px touch target and leaves the log body
  hidden. Only that collapsed strip should capture pointer events while minimized,
  and the page should reserve the same bottom space so bottom-most editor content
  can scroll above it, including any mobile safe-area inset.
- The user can expand and minimize the log from the panel.
- The user can copy the current debug log from the panel, with a fallback for
  clipboard API failures. The fallback will use a temporary textarea plus
  `document.execCommand('copy')`.
- Copying an empty log should not fail; it should show inline feedback that
  there are no log lines yet. Successful copy should also show inline feedback.
- Existing remote logging and console logging continue unchanged.
- Overlay log row pruning still limits retained rows.
- Persisting expanded/minimized state across reloads is out of scope; every new
  tracer session starts minimized.
- The previous hidden Escape-to-clear and double-tap-to-clear gestures are
  replaced by explicit Clear and Escape-to-minimize behavior.
- Mobile wave and tag chrome controls render as compact 44px icon buttons with
  hidden accessible text labels; state sync must preserve the icon markup.

## Implementation

- Add a focused source-contract regression test for the JSNI overlay contract at
  `wave/src/test/java/org/waveprotocol/box/server/util/ImeDebugOverlayContractTest.java`.
  This lives in the server util test package because normal JVM tests exclude
  most `org/waveprotocol/wave/client` test sources.
- Add a source-contract regression test for server-rendered mobile wave/tag
  controls at
  `wave/src/test/java/org/waveprotocol/box/server/util/MobileChromeControlsContractTest.java`.
- Update `ImeDebugTracer.ensureOverlayJsni()` so the overlay has a compact header,
  a hidden log body by default, and buttons for show/hide, copy, and clear.
  Native buttons handle keyboard focus and activation; no custom focus trap is
  needed.
- Update `ImeDebugTracer.appendToOverlayJsni()` to append rows into the log body,
  not the overlay chrome.
- Update `HtmlRenderer` mobile chrome controls to keep SVG icons in the button
  body while state sync updates `aria-label`, `title`, and hidden label text.
- Add `wave/config/changelog.d/2026-04-24-ime-debug-panel-mobile.json` because the
  diagnostic UI behavior changes.

## Verification

- `sbt --batch "testOnly org.waveprotocol.box.server.util.ImeDebugOverlayContractTest"`
- `sbt --batch "testOnly org.waveprotocol.box.server.util.MobileChromeControlsContractTest"`
- `python3 scripts/assemble-changelog.py`
- `python3 scripts/validate-changelog.py`
- Local server sanity before PR: boot the app from this worktree and verify the
  generated client response is served.
- Browser verification before PR: inspect the overlay behavior with
  `?ime_debug=on` in a mobile viewport and confirm it starts minimized, expands,
  minimizes again, and exposes copy/clear controls.
