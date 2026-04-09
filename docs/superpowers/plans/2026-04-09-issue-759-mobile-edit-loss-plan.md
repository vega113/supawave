# Mobile Android Edit Loss Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure Android/mobile blip edits survive session end and persist after reload by committing active IME composition state before the editor is detached.

**Architecture:** The failure is at the client editor/session boundary, not transport or persistence. Mobile WebKit editors can end a session while IME composition text still exists only in the editor’s transient composition container, so the fix is to add an explicit editor hook that flushes pending input and wire `EditSession.endSession()` to call it before draft-save, blur, detach, and reset.

**Tech Stack:** GWT client editor, WavePanel edit session lifecycle, JUnit 3, GWTTestCase, Mockito, SBT.

---

## File Map

- Create: `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/edit/EditSessionTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/Editor.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorImpl.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorTestingUtil.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/EditSession.java`
- Modify: `wave/config/changelog.d/2026-04-09-mobile-edit-loss.json`
- Test: `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/MobileImeFlushGwtTest.java`

### Task 1: Lock the Failing Boundary with Tests

**Files:**
- Create: `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/edit/EditSessionTest.java`
- Create: `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/MobileImeFlushGwtTest.java`

- [ ] **Step 1: Write the session regression test**

```java
public void testStopEditingFlushesPendingInputBeforeDetach() {
  InOrder ordered = inOrder(editor, selectionExtractor, container);
  ordered.verify(editor).flushPendingInput();
  ordered.verify(selectionExtractor).stop(editor);
  ordered.verify(container).doOrphan(editor.getWidget());
  ordered.verify(editor).removeContent();
  ordered.verify(editor).reset();
}
```

- [ ] **Step 2: Run the session test and verify it fails**

Run: `sbt "testOnly org.waveprotocol.wave.client.wavepanel.impl.edit.EditSessionTest"`
Expected: FAIL because `EditSession.endSession()` does not yet call `editor.flushPendingInput()`.

- [ ] **Step 3: Write the editor regression test**

```java
public void testFlushPendingInputCommitsActiveImeComposition() {
  setContent(editor, "<body><line/>|</body>");
  EditorTestingUtil.startImeComposition(editor);
  EditorTestingUtil.setActiveImeText(editor, "mobile");
  editor.flushPendingInput();
  assertEditorContent("IME text should be committed", "<body><line/>mobile|</body>", editor);
}
```

- [ ] **Step 4: Run the editor test and verify it fails**

Run: `sbt "testOnly org.waveprotocol.wave.client.editor.integration.MobileImeFlushGwtTest"`
Expected: FAIL because active IME composition is not yet committed by a teardown-safe flush path.

### Task 2: Add the Minimal Editor Flush Hook

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/Editor.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorImpl.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorTestingUtil.java`

- [ ] **Step 1: Add the editor contract for pending-input flush**

```java
/**
 * Flushes any pending browser input that has not yet been turned into
 * document operations, including active IME composition state.
 */
void flushPendingInput();
```

- [ ] **Step 2: Implement the editor flush in `EditorImpl`**

```java
@Override
public void flushPendingInput() {
  if (imeExtractor.isActive()) {
    flushActiveImeComposition();
  }
  flushSynchronous();
}
```

- [ ] **Step 3: Extract the IME commit helper instead of duplicating logic**

```java
private FocusedContentRange flushActiveImeComposition() {
  // move the existing compositionEnd() body here so it can be reused safely
}
```

- [ ] **Step 4: Add test-only helpers through `EditorTestingUtil`**

```java
public static void startImeComposition(Editor editor) { ... }
public static void setActiveImeText(Editor editor, String text) { ... }
```

- [ ] **Step 5: Run the editor regression and verify it passes**

Run: `sbt "testOnly org.waveprotocol.wave.client.editor.integration.MobileImeFlushGwtTest"`
Expected: PASS.

### Task 3: Wire Session Teardown to the New Flush Path

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/EditSession.java`
- Modify: `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/edit/EditSessionTest.java`

- [ ] **Step 1: Flush pending input before draft-save and detach**

```java
private void endSession() {
  if (isEditing()) {
    editor.flushPendingInput();
    if (editor.isDraftMode()) {
      editor.leaveDraftMode(true);
    }
    ...
  }
}
```

- [ ] **Step 2: Re-run the session regression and verify it passes**

Run: `sbt "testOnly org.waveprotocol.wave.client.wavepanel.impl.edit.EditSessionTest"`
Expected: PASS.

- [ ] **Step 3: Run both focused regressions together**

Run: `sbt "testOnly org.waveprotocol.wave.client.editor.integration.MobileImeFlushGwtTest org.waveprotocol.wave.client.wavepanel.impl.edit.EditSessionTest"`
Expected: PASS for both tests.

### Task 4: Record User-Facing Change and Focused Verification

**Files:**
- Create: `wave/config/changelog.d/2026-04-09-mobile-edit-loss.json`

- [ ] **Step 1: Add the changelog fragment**

```json
{
  "releaseId": "2026-04-09-mobile-edit-loss",
  "title": "Mobile edit persistence fix",
  "summary": "Android/mobile blip edits now commit pending IME text before the editor closes, preventing text that looked saved locally from disappearing after reload.",
  "date": "2026-04-09",
  "type": "fix"
}
```

- [ ] **Step 2: Assemble and validate changelog**

Run: `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
Expected: `assembled ...` then `changelog validation passed`.

- [ ] **Step 3: Run lane verification**

Run: `sbt "testOnly org.waveprotocol.wave.client.editor.integration.MobileImeFlushGwtTest org.waveprotocol.wave.client.wavepanel.impl.edit.EditSessionTest"`
Expected: PASS.

Run: `PORT=9901 bash scripts/wave-smoke.sh check`
Expected: `ROOT_STATUS=200` or `302`, `HEALTH_STATUS=200`, `WEBCLIENT_STATUS=200`.

## Out of Scope

- Transport or websocket reconnect refactors
- Save-indicator redesign
- General mobile typing UX changes outside the teardown boundary
- Non-IME editor refactors
