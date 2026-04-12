# Issue 828 Android IME Corruption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the exact Android empty-blip reproduction so typing `new blip` persists exactly `new blip`, with no dropped leading letters and no missing space after Done.

**Architecture:** The reopened bug is not the same as the earlier “no selection on focus” or “flush active composition on teardown” failures. On WebKit, the browser `compositionend` event can leave the editor in application-side `State.COMPOSITION` until the next non-composition event arrives; then `handleOtherEvent()` flushes the delayed `compositionEnd()` inside that later mutation event, and the code continues into the legacy `DOMCharacterDataModified` fallback in the same `handleEventInner()` pass. That “composition ended during the current mutation event” double-application is the narrowest boundary that matches the real-device symptom where the first letter of each word disappears and the inter-word space is lost by the time editing is finished.

**Tech Stack:** GWT editor event pipeline, Android/WebKit IME composition handling, JUnit 3 GWT tests, staged local Wave server verification, browser verification against the running worktree UI.

---

## File Map

- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/event/EditorEventHandler.java`
- Modify: `wave/src/test/java/org/waveprotocol/wave/client/editor/event/EditorEventHandlerGwtTest.java`
- Create: `wave/config/changelog.d/2026-04-11-android-ime-corruption.json`
- Create: `journal/local-verification/2026-04-11-issue-828-android-ime-corruption.md`

### Task 1: Lock the Reopened IME Boundary with a Failing Regression

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/wave/client/editor/event/EditorEventHandlerGwtTest.java`

- [ ] **Step 1: Add a regression for `compositionend` followed by trailing DOM mutation in the same event cycle**

```java
public void testDomMutationImmediatelyAfterCompositionEndSkipsTypingExtractor() {
  assertTrue(QuirksConstants.MODIFIES_DOM_AND_FIRES_TEXTINPUT_AFTER_COMPOSITION);

  FakeEditorEvent[] composition = FakeEditorEvent.compositionSequence(0);
  FakeEditorEvent mutation = FakeEditorEvent.create(BrowserEvents.DOMCharacterDataModified);

  Point<ContentNode> caret = Point.inText(
      new ContentTextNode(Document.get().createTextNode("new"), null), 3);
  FocusedContentRange collapsedSelection = new FocusedContentRange(caret);

  FakeEditorInteractor interactor = setupFakeEditorInteractor(collapsedSelection);
  FakeEditorEventsSubHandler subHandler = new FakeEditorEventsSubHandler();
  subHandler.call(FakeEditorEventsSubHandler.HANDLE_DOM_MUTATION).anyOf();
  interactor.call(FakeEditorInteractor.COMPOSITION_START).nOf(1).withArgs(caret);
  interactor.call(FakeEditorInteractor.COMPOSITION_END).nOf(1).returns(collapsedSelection);

  EditorEventHandler handler = createEditorEventHandler(interactor, subHandler);

  assertFalse(handler.handleEvent(composition[0]));
  assertEquals(EditorEventHandler.State.COMPOSITION, handler.getState());
  assertFalse(handler.handleEvent(composition[1]));
  assertEquals(EditorEventHandler.State.COMPOSITION, handler.getState());
  assertFalse(handler.handleEvent(mutation));
  assertEquals(EditorEventHandler.State.NORMAL, handler.getState());

  interactor.checkExpectations();
}
```

- [ ] **Step 2: Add a control regression that ordinary collapsed DOM mutations still reach the typing extractor when no IME flush happened in this event**

```java
public void testCollapsedDomMutationOutsideCompositionStillNotifiesTypingExtractor() {
  FakeEditorEvent mutation = FakeEditorEvent.create(BrowserEvents.DOMCharacterDataModified);

  Point<ContentNode> caret = Point.inText(
      new ContentTextNode(Document.get().createTextNode("new"), null), 3);
  FocusedContentRange collapsedSelection = new FocusedContentRange(caret);

  FakeEditorInteractor interactor = setupFakeEditorInteractor(collapsedSelection);
  FakeEditorEventsSubHandler subHandler = new FakeEditorEventsSubHandler();
  subHandler.call(FakeEditorEventsSubHandler.HANDLE_DOM_MUTATION).anyOf();
  interactor.call(FakeEditorInteractor.NOTIFYING_TYPING_EXTRACTOR)
      .nOf(1)
      .withArgs(caret, false);

  EditorEventHandler handler = createEditorEventHandler(interactor, subHandler);

  assertFalse(handler.handleEvent(mutation));
  interactor.checkExpectations();
}
```

- [ ] **Step 3: Run the focused editor-event test and verify the new boundary test fails for the expected reason**

Run:

```bash
sbt "testOnly org.waveprotocol.wave.client.editor.event.EditorEventHandlerGwtTest"
```

Expected:
- `testDomMutationImmediatelyAfterCompositionEndSkipsTypingExtractor` fails because the current code still reaches `NOTIFYING_TYPING_EXTRACTOR` during the trailing mutation event that also flushes the delayed application-side `COMPOSITION_END`
- the control regression stays green, proving the failure is specific to the post-composition path rather than all DOM mutation fallback

### Task 2: Add a Same-Event Guard Around the Legacy Mutation Fallback

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/event/EditorEventHandler.java`

- [ ] **Step 1: Introduce an event-scoped flag that records whether this `handleEventInner()` pass already flushed IME composition**

```java
private boolean compositionEndedDuringCurrentEvent;

private boolean handleEventInner(EditorEvent event) throws SelectionLostException {
  compositionEndedDuringCurrentEvent = false;
  invalidateSelection();
  ...
}
```

- [ ] **Step 2: Mark the flag only when the application-side composition end actually runs**

```java
private void compositionEnd() {
  cachedSelection = editorInteractor.compositionEnd();
  state = State.NORMAL;
  compositionEndedDuringCurrentEvent = true;
}
```

- [ ] **Step 3: Skip the DOM-mutation typing fallback when the current event already ended IME composition**

```java
if (event.isDOMCharacterEvent()) {
  if (compositionEndedDuringCurrentEvent) {
    logger.trace().logPlainText(
        "Ignoring DOM character mutation immediately after IME composition end");
    subHandler.handleDomMutation(event);
    return false;
  }

  cachedSelection = editorInteractor.getSelectionPoints();
  if (cachedSelection != null) {
    if (!cachedSelection.isCollapsed()) {
      logger.trace().logPlainText("Ignoring DOM character mutation on non-collapsed "
          + "selection; probable IME composition owns this range");
      return false;
    }
    editorInteractor.notifyTypingExtractor(cachedSelection.getFocus(), false, false);
  }
}
```

- [ ] **Step 4: Keep the fix narrow**

```java
// This task does not modify EditorImplWebkitMobile focus(), NativeSelectionUtil,
// or flushPendingInput(). The only production seam is the post-composition
// DOM-mutation fallback inside EditorEventHandler.
```

- [ ] **Step 5: Re-run the focused editor-event test and verify it passes**

Run:

```bash
sbt "testOnly org.waveprotocol.wave.client.editor.event.EditorEventHandlerGwtTest"
```

Expected: PASS, with the new regression proving the trailing DOM mutation no longer re-enters `TypingExtractor` after `compositionEnd()`.

### Task 3: Record the User-Facing Fix

**Files:**
- Create: `wave/config/changelog.d/2026-04-11-android-ime-corruption.json`

- [ ] **Step 1: Add the changelog fragment**

```json
{
  "releaseId": "2026-04-11-android-ime-corruption",
  "version": "Issue #828",
  "date": "2026-04-11",
  "title": "Fix Android IME word-boundary corruption",
  "summary": "Android/mobile editing no longer double-applies post-composition DOM mutations that dropped the first letter of each word and removed spaces after Done.",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Stopped the editor from feeding the trailing DOM mutation from an ended Android IME composition back into TypingExtractor in the same event cycle",
        "Preserved exact word starts and inter-word spaces for the real-device empty-blip reproduction where typing `new blip` previously collapsed to `ewlip`"
      ]
    }
  ]
}
```

- [ ] **Step 2: Assemble and validate changelog**

Run:

```bash
python3 scripts/assemble-changelog.py && \
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected: assembled changelog updates successfully, then `changelog validation passed`.

### Task 4: Verify the Exact Reproduction Narrowly and Record Evidence

**Files:**
- Create: `journal/local-verification/2026-04-11-issue-828-android-ime-corruption.md`

- [ ] **Step 1: Build the worktree runtime**

Run:

```bash
bash scripts/worktree-boot.sh --port 9903
```

Expected: the script prints the exact `wave-smoke.sh start`, `check`, and `stop` commands for this worktree lane.

- [ ] **Step 2: Start and smoke-check the staged server**

Run the exact `start` command printed by `worktree-boot.sh`, then:

```bash
PORT=9903 bash scripts/wave-smoke.sh check
```

Expected:
- `ROOT_STATUS=200` or `302`
- `HEALTH_STATUS=200`
- `WEBCLIENT_STATUS=200`

- [ ] **Step 3: Run the exact empty-blip browser verification against the running worktree UI**

Verify only this path in the browser:

1. open an empty blip
2. type `new blip`
3. press `Done`
4. confirm the visible blip text is exactly `new blip`
5. reload or re-open the same wave and confirm the persisted text is still exactly `new blip`

Record:
- whether browser verification was required (`GWT client/UI` yes)
- the exact URL opened
- whether the first `n` and `b` remained present
- whether the inter-word space survived after `Done`
- whether the local environment reproduced or only the real device did

- [ ] **Step 4: Stop the server**

Run:

```bash
PORT=9903 bash scripts/wave-smoke.sh stop
```

Expected: the local staged server stops cleanly and the command exits `0`.

- [ ] **Step 5: Copy the verification evidence into the issue comment and PR body**

```markdown
- worktree path and branch
- reviewed plan path
- exact test command + result
- exact smoke commands + result
- exact browser reproduction steps and observed result
- note any emulator-vs-real-device limit explicitly
```

## Out of Scope

- Reopening the earlier Android selection/focus path from `#796`
- Reopening the teardown-only IME flush path from `#764`
- Transport, websocket, or server persistence refactors
- Broad TypingExtractor redesign beyond the post-composition same-event guard
