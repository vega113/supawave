# Mobile Text Corruption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop Android/mobile blip edits from corrupting persisted text by dropping word-leading characters and spaces during IME-driven editing.

**Architecture:** Current evidence places this after the `#796` selection fix and before transport/persistence. Android-codepath editing now opens with a valid caret and ordinary synthetic typing persists cleanly, so the remaining seam is the IME/mutation fallback in `EditorEventHandler`: WebKit can surface post-composition DOM character mutations while the selection is a transient non-collapsed range, and the current fallback still feeds that state into the typing extractor even though the code already documents it as “not handled”. The minimal fix is to lock that boundary with a focused regression, then stop the typing extractor from consuming non-collapsed IME mutation ranges that belong to the composition flow rather than to normal replacement typing.

**Tech Stack:** GWT editor, Android/WebKit IME composition path, GWTTestCase sources, Playwright Android emulation, SBT.

---

## File Map

- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/event/EditorEventHandler.java`
- Modify: `wave/src/test/java/org/waveprotocol/wave/client/editor/event/EditorEventHandlerGwtTest.java`
- Create: `wave/config/changelog.d/2026-04-10-mobile-text-corruption.json`
- Verify: `journal/local-verification/2026-04-10-branch-mobile-text-corruption-20260410.md`

### Task 1: Lock The IME Mutation Boundary

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/wave/client/editor/event/EditorEventHandlerGwtTest.java`

- [ ] **Step 1: Add a regression that models a DOM character mutation with a non-collapsed selection**

```java
public void testDomCharacterMutationWithNonCollapsedSelectionSkipsTypingExtractor() {
  FakeEditorEvent mutation = FakeEditorEvent.create(BrowserEvents.DOMCharacterDataModified);

  final Point<ContentNode> start = Point.inText(
      new ContentTextNode(Document.get().createTextNode("hello"), null), 1);
  final Point<ContentNode> end = Point.inText(
      new ContentTextNode(Document.get().createTextNode("hello"), null), 5);

  FakeEditorInteractor interactor = setupFakeEditorInteractor(new FocusedContentRange(start, end));
  EditorEventHandler handler = createEditorEventHandler(interactor, new FakeEditorEventsSubHandler());

  assertFalse(handler.handleEvent(mutation));
  interactor.checkExpectations();
}
```

- [ ] **Step 2: Run the focused source-compile and confirm the new regression is present**

Run: `sbt "Test / compile"`
Expected: PASS for source compilation, with the new regression source included in the compiled test tree.

- [ ] **Step 3: Re-run the live Android-emulated reproduction before the fix**

Run the local Playwright lane against `http://127.0.0.1:9902/` and capture:
- valid selection (`startSelection` / `endSelection` not `-1`)
- ordinary synthetic typing persists cleanly
- real-device symptom remains unproven locally, so the code regression boundary is the non-collapsed IME mutation fallback

Expected: evidence recorded in the local-verification note showing this bug is distinct from the prior “DOM-only typing” boundary.

### Task 2: Stop Feeding Non-Collapsed IME Mutation Ranges Into Typing Extraction

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/event/EditorEventHandler.java`

- [ ] **Step 1: Guard the DOM character mutation fallback**

```java
if (event.isDOMCharacterEvent()) {
  cachedSelection = editorInteractor.getSelectionPoints();
  if (cachedSelection != null) {
    if (!cachedSelection.isCollapsed()) {
      logger.trace().logPlainText(
          "Ignoring DOM character mutation on non-collapsed selection; IME composition owns it");
      return false;
    }
    editorInteractor.notifyTypingExtractor(cachedSelection.getFocus(), false, false);
  }
}
```

- [ ] **Step 2: Keep the change narrow**

```java
// Do not touch the earlier keyCode=229 guard or compositionStart() flush path in this slice
// unless the regression shows they are still wrong.
```

- [ ] **Step 3: Rebuild the app**

Run: `sbt "wave / compile"`
Expected: PASS.

### Task 3: Record The User-Facing Fix

**Files:**
- Create: `wave/config/changelog.d/2026-04-10-mobile-text-corruption.json`

- [ ] **Step 1: Add the changelog fragment**

```json
{
  "releaseId": "2026-04-10-mobile-text-corruption",
  "title": "Fix Android mobile text corruption while editing",
  "summary": "Android/mobile IME edits no longer corrupt persisted text by dropping leading characters and spaces during composition-driven editing.",
  "date": "2026-04-10",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Android/mobile IME edits no longer corrupt persisted text by dropping leading characters and spaces during composition-driven editing."
      ]
    }
  ]
}
```

- [ ] **Step 2: Assemble and validate changelog**

Run: `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
Expected: `assembled ...` then `changelog validation passed`.

### Task 4: Verify Narrowly And Record Evidence

**Files:**
- Modify: `journal/local-verification/2026-04-10-branch-mobile-text-corruption-20260410.md`

- [ ] **Step 1: Compile verification**

Run: `sbt "wave / compile" "Test / compile"`
Expected: PASS.

- [ ] **Step 2: Stage and smoke**

Run: `bash scripts/worktree-boot.sh --port 9902`
Expected: prints the start/check/stop commands for the lane.

Run: `PORT=9902 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/mobile-text-corruption-20260410/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/mobile-text-corruption-20260410/wave/config/jaas.config' bash scripts/wave-smoke.sh start`
Expected: `READY`.

Run: `PORT=9902 bash scripts/wave-smoke.sh check`
Expected: `ROOT_STATUS=200`, `HEALTH_STATUS=200`, `WEBCLIENT_STATUS=200`.

- [ ] **Step 3: Browser verification**

Run the Android-emulated Playwright flow against `http://127.0.0.1:9902/`.
Expected:
- existing-blip edit opens with valid selection
- ordinary synthetic typing still persists correctly
- no local evidence remains of word-boundary corruption in the editor model after the fix

- [ ] **Step 4: Shutdown**

Run: `PORT=9902 bash scripts/wave-smoke.sh stop`
Expected: server stops cleanly.

## Out of Scope

- Reopening the `#759` no-selection / DOM-only typing boundary
- Transport, websocket, or server persistence refactors
- Keyboard-hint or other unrelated mobile UI cleanup
- Large-scale typing extractor redesign beyond the non-collapsed IME mutation seam
