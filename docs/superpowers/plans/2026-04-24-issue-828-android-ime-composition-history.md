# Issue #828 Android IME Composition History Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:systematic-debugging` and focused regression tests before changing editor behavior.

**Goal:** Fix the real Galaxy/Chrome failure where typing `new blip` in a blip displays blank after Done and reloads as only `ew`.

**Root Cause:** The supplied device trace shows Android Chrome replacing the active composition scratch text with shortened values before commit. The first word reports `n -> e -> e -> ew`, so reading the final scratch span commits only `ew`. The second word reports `b -> l -> l -> li -> lip`, so reading the final scratch span commits only `lip`. The trace also shows the space after the first word arriving as a standalone Android `textInput` after composition has ended, without a normal soft-key `keydown` to activate `TypingExtractor`.

**Files:**
- `wave/src/main/java/org/waveprotocol/wave/model/util/ImeCompositionTextTracker.java`
- `wave/src/main/java/org/waveprotocol/wave/client/editor/extract/ImeExtractor.java`
- `wave/src/main/java/org/waveprotocol/wave/client/editor/event/EditorEventHandler.java`
- `wave/src/main/java/org/waveprotocol/wave/client/editor/event/CompositionEventHandler.java`
- `wave/src/main/java/org/waveprotocol/wave/client/common/util/SignalEvent.java`
- `wave/src/main/java/org/waveprotocol/wave/model/util/GhostTextReconciler.java`
- `wave/src/test/java/org/waveprotocol/wave/model/util/ImeCompositionTextTrackerTest.java`
- `wave/src/test/java/org/waveprotocol/wave/model/util/GhostTextReconcilerTest.java`
- `wave/config/changelog.d/2026-04-24-android-ime-composition-history.json`

## Acceptance Criteria

- Reconstruct Android replacement streams like `n, e, e, ew` as `new`.
- Reconstruct replacement streams like `b, l, l, li, lip` as `blip`.
- Preserve standalone Android `textInput` separators such as the space between words.
- Do not restore an unrelated empty-baseline previous sibling to empty after the event-history tracker has already recovered the current word.
- GWT dev compile succeeds.
- Local mobile Chrome simulation can type `new blip`, tap Done, reload, and still find `new blip`.

## Verification

- `sbt "Test/runMain org.junit.runner.JUnitCore org.waveprotocol.wave.model.util.ImeCompositionTextTrackerTest org.waveprotocol.wave.model.util.GhostTextReconcilerTest"`
- `sbt compileGwtDev`
- `python3 scripts/assemble-changelog.py`
- `python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
- `bash scripts/worktree-boot.sh --port 9933`
- `PORT=9933 bash scripts/wave-smoke.sh check`
- Playwright Chromium with `devices['Galaxy S9+']` against `http://127.0.0.1:9933`: register/login, create wave, type `new blip`, tap Done, reload, assert the page still contains `new blip`.
