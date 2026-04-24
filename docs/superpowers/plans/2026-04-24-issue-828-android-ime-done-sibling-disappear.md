# Issue #828 Android IME Done Sibling Disappearance Follow-Up Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:systematic-debugging` and focused regression tests before changing editor behavior.

**Goal:** Fix the remaining Android Chrome IME failure where typing `new blip` in a blip can display or persist as `ewlip`/`ew` after the keyboard Done action removes the temporary sibling text that carried the missing leading characters.

**Root Cause:** The previous Android IME recovery path kept captured ghost text only when the final adjacent DOM sibling still existed and had returned to the content-model baseline. The phone repro shows a harsher teardown path: Android Chrome can remove the adjacent sibling entirely before `flushPendingInput()` commits the composition. In that state the captured `n` / ` b` was no longer trusted, so only the scratch text (`ew` or `lip`) was committed.

**Files:**
- `wave/src/main/java/org/waveprotocol/wave/model/util/GhostTextReconciler.java`
- `wave/src/test/java/org/waveprotocol/wave/model/util/GhostTextReconcilerTest.java`
- `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorImpl.java`
- `wave/config/changelog.d/2026-04-24-android-ime-done-sibling-disappear.json`

## Acceptance Criteria

- Captured previous-sibling ghost text survives when the final sibling text node is absent.
- Captured second-word ghost text (`" b"` over model baseline `"new"`) survives the same Done teardown.
- Symmetric next-sibling ghost text remains covered.
- The editor's reported IME composition state uses the same effective recovered text that the flush path commits.
- Local mobile Chrome simulation against the staged app can type `new blip`, tap Done, reload, and still find `new blip` rather than `ew`.

## Verification

- `sbt "wave/testOnly org.waveprotocol.wave.model.util.GhostTextReconcilerTest"`
- `sbt "wave/testOnly org.waveprotocol.wave.client.editor.integration.MobileImeFlushGwtTest"`
- `bash scripts/worktree-boot.sh --port 9930`
- `PORT=9930 bash scripts/wave-smoke.sh check`
- Playwright Chromium with `devices['Galaxy S9+']` against `http://127.0.0.1:9930`: register/login, create wave, type `new blip`, tap Done, reload, assert body contains `new blip` and not an `ew`-only collapse.
