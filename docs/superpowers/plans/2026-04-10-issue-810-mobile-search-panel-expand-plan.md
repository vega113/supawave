# Issue 810 Mobile Search Panel Expand Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the mobile search panel off-canvas while entering blip edit mode so the typing area stays visible on Android/mobile.

**Architecture:** Investigation narrowed the regression to the Android focus path restored in `#796`, not the renderer's mobile drawer class toggles. `EditorImpl.focus()` already snapshots ancestor `scrollTop` to cancel WebKit auto-scroll, but it does not preserve `scrollLeft`; on mobile split-layout shells that leaves horizontal ancestor drift uncorrected and can reveal the sidebar while edit mode starts. The minimal fix is to preserve both axes in the editor focus helper, move the scroll capture/restore logic into a tiny testable helper with a normal JVM regression, and then re-run the live mobile browser path against the staged local server.

**Tech Stack:** Java client code, `EditorImpl`, plain JVM `TestCase`, Playwright mobile verification, SBT, staged local Wave server.

---

## File Map

- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorImpl.java`
- Create: `wave/src/main/java/org/waveprotocol/wave/client/editor/AncestorScrollPositions.java`
- Create: `wave/src/test/java/org/waveprotocol/wave/client/editor/AncestorScrollPositionsTest.java`
- Create: `wave/config/changelog.d/2026-04-10-mobile-search-panel-expand.json`
- Regenerate: `wave/config/changelog.json`

### Task 1: Lock the Scroll-Restoration Boundary with a Failing JVM Test

**Files:**
- Create: `wave/src/test/java/org/waveprotocol/wave/client/editor/AncestorScrollPositionsTest.java`

- [ ] **Step 1: Add a plain JVM regression for ancestor scroll restoration**

Create `AncestorScrollPositionsTest` with a fake parent/child chain that:
- captures scroll state from child through ancestors
- mutates both `scrollTop` and `scrollLeft`
- restores the captured state
- asserts both axes are restored for every node

Target assertion shape:

```java
assertEquals(120, parent.scrollLeft);
assertEquals(40, parent.scrollTop);
```

- [ ] **Step 2: Run the focused compile/test harness and verify it fails before the helper exists**

Run:

```bash
FULL_CP=$(cat target/streams/test/fullClasspath/_global/streams/export)
OUT=/tmp/issue810-ancestor-scroll-test-red
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 17 -cp "$FULL_CP" -d "$OUT" \
  wave/src/test/java/org/waveprotocol/wave/client/editor/AncestorScrollPositionsTest.java
```

Expected:
- FAIL because `AncestorScrollPositions` does not exist yet

### Task 2: Preserve Both Scroll Axes During Mobile Focus

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorImpl.java`
- Create: `wave/src/main/java/org/waveprotocol/wave/client/editor/AncestorScrollPositions.java`

- [ ] **Step 1: Add a tiny helper that captures and restores both axes**

Implement a small package-private helper that captures `{scrollTop, scrollLeft}` for a node and each ancestor through a narrow adapter interface, then restores those saved values later.

- [ ] **Step 2: Wire `EditorImpl` through the helper**

Replace the old single-axis `ancestorScrollTops` state so `maybeSaveAncestorScrollPositions(...)` / `maybeRestoreAncestorScrollPositions(...)` preserve both axes for real DOM `Element` ancestors.

- [ ] **Step 3: Re-run the focused compile/test harness and verify it passes**

Run:

```bash
FULL_CP=$(cat target/streams/test/fullClasspath/_global/streams/export)
OUT=/tmp/issue810-ancestor-scroll-test-green
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 17 -cp "$FULL_CP" -d "$OUT" \
  wave/src/main/java/org/waveprotocol/wave/client/editor/AncestorScrollPositions.java \
  wave/src/test/java/org/waveprotocol/wave/client/editor/AncestorScrollPositionsTest.java
java -cp "$OUT:$FULL_CP" org.junit.runner.JUnitCore \
  org.waveprotocol.wave.client.editor.AncestorScrollPositionsTest
```

Expected:
- `OK (1 test)`

### Task 3: Record the User-Facing Fix

**Files:**
- Create: `wave/config/changelog.d/2026-04-10-mobile-search-panel-expand.json`
- Regenerate: `wave/config/changelog.json`

- [ ] **Step 1: Add the changelog fragment**

Create:

```json
{
  "releaseId": "2026-04-10-mobile-search-panel-expand",
  "version": "PR #810",
  "title": "Mobile search panel no longer slides into blip edits",
  "date": "2026-04-10",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Android/mobile blip edit sessions now preserve horizontal scroll state when the editor focuses, preventing the search panel from sliding over the typing area."
      ]
    }
  ]
}
```

- [ ] **Step 2: Rebuild and validate the assembled changelog**

Run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected:
- `assembled ... -> wave/config/changelog.json`
- `changelog validation passed`

### Task 4: Verify the Live Mobile Path Narrowly

**Files:**
- Modify: none

- [ ] **Step 1: Re-run the focused regression**

Run:

```bash
FULL_CP=$(cat target/streams/test/fullClasspath/_global/streams/export)
OUT=/tmp/issue810-ancestor-scroll-test-green
java -cp "$OUT:$FULL_CP" org.junit.runner.JUnitCore \
  org.waveprotocol.wave.client.editor.AncestorScrollPositionsTest
```

Expected:
- PASS

- [ ] **Step 2: Rebuild the staged app**

Run:

```bash
sbt -batch Universal/stage
```

Expected:
- PASS

- [ ] **Step 3: Start the local staged server on the lane port and run smoke**

Run:

```bash
PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/mobile-search-panel-expand-20260410/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/mobile-search-panel-expand-20260410/wave/config/jaas.config' ./target/universal/stage/bin/wave
PORT=9900 bash scripts/wave-smoke.sh check
```

Expected:
- `ROOT_STATUS=200`
- `HEALTH_STATUS=200`
- `WEBCLIENT_STATUS=200`

- [ ] **Step 4: Re-run the mobile browser edit flow**

Use Playwright mobile emulation against `http://127.0.0.1:9900/`:
- sign in as the local test user
- open the welcome wave
- enter root-blip edit mode
- type a marker
- confirm the search panel stays off-canvas and the typing area remains visible

- [ ] **Step 5: Stop the local server after verification**

Stop the foreground staged server session (or run `PORT=9900 bash scripts/wave-smoke.sh stop` if using the wrapper path).

## Out of Scope

- Changing the renderer’s mobile drawer state machine unless the focus-path fix fails to resolve the issue
- Reworking SplitLayoutPanel mobile CSS
- General editor focus/selection changes outside ancestor scroll restoration
- Search result refresh behavior unrelated to mobile edit startup
