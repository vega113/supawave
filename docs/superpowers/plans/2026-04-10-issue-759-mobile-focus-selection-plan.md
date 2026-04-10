# Mobile Android Edit Loss Focus Selection Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore mobile blip persistence by ensuring Android/mobile edit sessions start with a valid editor focus/selection state before typing begins.

**Architecture:** The failing boundary is before op generation. On Android mobile WebKit, edit sessions open with no active selection because two mobile-specific branches disable the normal setup path: `EditorImplWebkitMobile.focus()` is a no-op and `NativeSelectionUtil` routes mobile WebKit through `SelectionImplDisabled`. Typed text therefore lands only in browser DOM, while the editor model, websocket submit path, and server persistence remain unchanged. The minimal fix is to re-enable the normal focus/selection path for Android while keeping the older disabled behavior for non-Android mobile WebKit.

**Tech Stack:** GWT editor, WavePanel edit session lifecycle, GWTTestCase, Playwright mobile emulation, SBT.

---

## File Map

- Create: `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/MobileWebkitFocusGwtTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorImplWebkitMobile.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/selection/html/NativeSelectionUtil.java`
- Create: `wave/config/changelog.d/2026-04-10-mobile-edit-loss-focus.json`

### Task 1: Lock the Focus Boundary with a Failing Test

**Files:**
- Create: `wave/src/test/java/org/waveprotocol/wave/client/editor/integration/MobileWebkitFocusGwtTest.java`

- [ ] **Step 1: Write the mobile focus regression**

```java
public void testFocusRestoresSelectionForMobileEditor() throws Exception {
  editor.setContent(DocProviders.POJO.parse("<body><line/>mobile</body>").asOperation(),
      DocumentSchema.NO_SCHEMA_CONSTRAINTS);

  assertNull("Precondition: mobile editor starts without a selection",
      editor.getSelectionHelper().getSelectionRange());

  editor.focus(false);

  assertNotNull("Mobile focus must restore a caret/selection before typing",
      editor.getSelectionHelper().getSelectionRange());
}
```

- [ ] **Step 2: Run the focused regression and verify it fails**

Run: `sbt "testOnly org.waveprotocol.wave.client.editor.integration.MobileWebkitFocusGwtTest"`
Expected: FAIL because `EditorImplWebkitMobile.focus()` is currently a no-op, leaving selection unset.

### Task 2: Restore Android Mobile Focus And Selection Initialization

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorImplWebkitMobile.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/selection/html/NativeSelectionUtil.java`

- [ ] **Step 1: Restore focus initialization for Android mobile editors**

```java
@Override
public void focus(boolean collapsed) {
  if (UserAgent.isAndroid()) {
    super.focus(collapsed);
  }
}
```

- [ ] **Step 2: Re-enable W3C selection support on Android mobile WebKit**

```java
} else if (UserAgent.isMobileWebkit() && !UserAgent.isAndroid()) {
  impl = new SelectionImplDisabled();
  coordinateGetter = new SelectionCoordinatesHelperDisabled();
} else {
  SelectionImplW3C w3cImpl = new SelectionImplW3C();
  impl = w3cImpl;
  coordinateGetter = new SelectionCoordinatesHelperW3C(...);
}
```

- [ ] **Step 3: Leave blur behavior unchanged in this slice**

```java
@Override
public void blur() {
  // unchanged in this slice; the reopened failure is before op generation
}
```

- [ ] **Step 4: Re-run the regression and verify it passes**

Run: `sbt "testOnly org.waveprotocol.wave.client.editor.integration.MobileWebkitFocusGwtTest"`
Expected: PASS.

### Task 3: Record the User-Facing Fix

**Files:**
- Create: `wave/config/changelog.d/2026-04-10-mobile-edit-loss-focus.json`

- [ ] **Step 1: Add the changelog fragment**

```json
{
  "releaseId": "2026-04-10-mobile-edit-loss-focus",
  "title": "Mobile edit persistence follow-up",
  "summary": "Android/mobile edit sessions now restore a valid caret when opening a blip editor, preventing typed text from appearing only locally and disappearing after reload.",
  "date": "2026-04-10",
  "type": "fix"
}
```

- [ ] **Step 2: Assemble and validate changelog**

Run: `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
Expected: `assembled ...` then `changelog validation passed`.

### Task 4: Verify the Reopened Reproduction Narrowly

**Files:**
- Modify: none

- [ ] **Step 1: Run the focused regression**

Run: `sbt "testOnly org.waveprotocol.wave.client.editor.integration.MobileWebkitFocusGwtTest"`
Expected: PASS.

- [ ] **Step 2: Run compile sanity**

Run: `sbt "wave / compile"`
Expected: PASS.

- [ ] **Step 3: Re-run the live mobile trace**

Run the local mobile-emulated Playwright reproduction used during investigation.
Expected:
- existing-blip edit: editor model changes and fetch/reload contain the typed marker
- new reply blip: new blip still persists, and its typed marker also persists

- [ ] **Step 4: Run local server sanity**

Run: `bash scripts/wave-smoke.sh check`
Expected: `ROOT_STATUS=200` or `302`, `HEALTH_STATUS=200`, `WEBCLIENT_STATUS=200`.

## Out of Scope

- IME composition teardown changes already landed in #764
- Websocket transport or server persistence refactors
- Mobile toolbar/navigation UX changes
- General blur/save behavior outside the missing mobile focus/selection boundary
