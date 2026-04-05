# RTL Language Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-detect RTL paragraphs (Hebrew, Arabic, etc.) using browser-native `dir="auto"`, with manual RTL/LTR override buttons in the editor toolbar.

**Architecture:** Replace the existing CSS `direction` property approach with the HTML `dir` attribute on paragraph divs. When no direction is stored in the document model (`d` attribute absent), render `dir="auto"` so the browser detects direction from the first strong Unicode bidi character. When the user explicitly sets direction via toolbar buttons, store `d="r"` (RTL) or `d="l"` (LTR) and render the corresponding `dir` attribute value.

**Tech Stack:** GWT 2.x (Java compiled to JS), Wave document model (OT-based XML), browser DOM API, sbt build.

---

## File Map

| File | Change |
|------|--------|
| `wave/src/main/java/org/waveprotocol/wave/client/editor/content/paragraph/Paragraph.java` | Modify `Direction.apply()` and `Direction.isApplied()` to support explicit LTR (`d="l"`) and auto state (no `d` attr = neither button active) |
| `wave/src/main/java/org/waveprotocol/wave/client/editor/content/paragraph/DefaultParagraphHtmlRenderer.java` | Switch from CSS `direction` property to HTML `dir` attribute; render `dir="auto"` when no direction stored |
| `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java` | Add `createDirectionButtons()` method, call from `init()` in the alignment group |
| `wave/src/test/java/org/waveprotocol/wave/client/editor/content/paragraph/DirectionTest.java` | New: unit tests for `Direction.isApplied()` and `Direction.cssValue()` semantics |

---

## Task 1: Update `Paragraph.Direction` semantics

**Goal:** Make `Direction.apply()` write explicit `d="l"` for LTR (instead of always null), and make `isApplied()` return false in auto state (no `d` attr) so neither toolbar button lights up when direction is auto-detected.

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/content/paragraph/Paragraph.java:168-191`
- Create: `wave/src/test/java/org/waveprotocol/wave/client/editor/content/paragraph/DirectionTest.java`

- [ ] **Step 1: Write the failing test**

Create `wave/src/test/java/org/waveprotocol/wave/client/editor/content/paragraph/DirectionTest.java`:

```java
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See NOTICE file for details.
 * Licensed under the Apache License, Version 2.0.
 */
package org.waveprotocol.wave.client.editor.content.paragraph;

import junit.framework.TestCase;
import org.waveprotocol.wave.client.editor.content.paragraph.Paragraph.Direction;

public class DirectionTest extends TestCase {

  // fromValue("l") must return LTR
  public void testFromValueLtr() {
    assertEquals(Direction.LTR, Direction.fromValue("l"));
  }

  // fromValue("r") must return RTL
  public void testFromValueRtl() {
    assertEquals(Direction.RTL, Direction.fromValue("r"));
  }

  // fromValue(null) must return null (auto state)
  public void testFromValueNull() {
    assertNull(Direction.fromValue(null));
  }

  // cssValue() returns the HTML dir attribute string
  public void testCssValues() {
    assertEquals("ltr", Direction.LTR.cssValue());
    assertEquals("rtl", Direction.RTL.cssValue());
  }

  // LTR value attribute
  public void testLtrValue() {
    assertEquals("l", Direction.LTR.value);
  }

  // RTL value attribute
  public void testRtlValue() {
    assertEquals("r", Direction.RTL.value);
  }
}
```

- [ ] **Step 2: Run test to verify it compiles and passes (tests enum constants, no model mocking needed)**

```
cd /Users/vega/devroot/incubator-wave && sbt "wave/testOnly *DirectionTest"
```

Expected: PASS (these test pure enum state, no GWT runtime needed).

If sbt can't resolve the test class yet, skip to Step 3 and come back.

- [ ] **Step 3: Modify `Direction.apply()` in `Paragraph.java`**

Find lines 168-178 in `Paragraph.java` (inside `Direction` enum):

**Before:**
```java
@Override public void apply(ContentElement e, boolean on) {
  e.getMutableDoc().setElementAttribute(e, DIRECTION_ATTR,
      this == LTR ? null : (on ? value : null));
  if (on && oppositeAlignment.isApplied(e)) {
    alignment.apply(e, true);
  }
}
```

**After:**
```java
@Override public void apply(ContentElement e, boolean on) {
  e.getMutableDoc().setElementAttribute(e, DIRECTION_ATTR, on ? value : null);
  if (on && oppositeAlignment.isApplied(e)) {
    alignment.apply(e, true);
  }
}
```

Explanation: Now `LTR.apply(e, true)` writes `d="l"` (explicit LTR override). `LTR.apply(e, false)` or `RTL.apply(e, false)` writes `d=null` (returns to auto). Previously LTR always wrote null regardless of `on`.

- [ ] **Step 4: Modify `Direction.isApplied()` in `Paragraph.java`**

Find lines 180-183 in `Paragraph.java` (inside `Direction` enum):

**Before:**
```java
@Override public boolean isApplied(ContentElement e) {
  Direction val = fromValue(e.getAttribute(DIRECTION_ATTR));
  return this == (val == null ? LTR : val);
}
```

**After:**
```java
@Override public boolean isApplied(ContentElement e) {
  return this == fromValue(e.getAttribute(DIRECTION_ATTR));
}
```

Explanation: With `d=null` (auto state), `fromValue(null)` returns `null`, and `this == null` is false for both LTR and RTL — so neither toolbar button lights up. With `d="l"`, only LTR button lights up. With `d="r"`, only RTL button lights up.

- [ ] **Step 5: Verify existing paragraph tests still pass**

```
cd /Users/vega/devroot/incubator-wave && sbt "wave/testOnly *Renumberer*"
```

Expected: All PASS. These tests don't involve Direction so they should be unaffected.

- [ ] **Step 6: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/editor/content/paragraph/Paragraph.java \
        wave/src/test/java/org/waveprotocol/wave/client/editor/content/paragraph/DirectionTest.java
git commit -m "feat(rtl): explicit LTR/RTL direction storage with auto-detect state

Direction.apply() now writes d=\"l\" for explicit LTR (previously wrote null).
Direction.isApplied() returns false when no d attr, so neither toolbar button
lights up in auto-detect state."
```

---

## Task 2: Render `dir` attribute in `DefaultParagraphHtmlRenderer`

**Goal:** Replace CSS `direction` property with HTML `dir` attribute on paragraph divs. When no direction is stored, render `dir="auto"` so the browser detects bidi from the first strong character.

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/content/paragraph/DefaultParagraphHtmlRenderer.java:183-200`

- [ ] **Step 1: Replace direction rendering block**

Find lines 183-200 in `DefaultParagraphHtmlRenderer.java`:

**Before:**
```java
    if (direction != null) {
      style.setProperty("direction", direction.cssValue());
    } else {
      style.clearProperty("direction");
    }

    if (margin == 0) {
      style.clearMarginLeft();
      style.clearMarginRight();
    } else {
      if (direction == Direction.RTL) {
        style.setMarginRight(margin, Unit.PX);
        style.clearMarginLeft();
      } else {
        style.setMarginLeft(margin, Unit.PX);
        style.clearMarginRight();
      }
    }
```

**After:**
```java
    // Use HTML dir attribute for bidi; dir="auto" enables browser-native RTL detection.
    style.clearProperty("direction"); // clear any legacy CSS direction value
    if (direction != null) {
      implNodelet.setAttribute("dir", direction.cssValue());
    } else {
      implNodelet.setAttribute("dir", "auto");
    }

    if (margin == 0) {
      style.clearMarginLeft();
      style.clearMarginRight();
    } else {
      if (direction == Direction.RTL) {
        style.setMarginRight(margin, Unit.PX);
        style.clearMarginLeft();
      } else {
        style.setMarginLeft(margin, Unit.PX);
        style.clearMarginRight();
      }
    }
```

Note: `direction.cssValue()` returns `"ltr"` or `"rtl"` — these are the valid values for the HTML `dir` attribute. The margin logic is unchanged (auto-detect case falls through to LTR margin, which is acceptable).

- [ ] **Step 2: Verify the file compiles**

```
cd /Users/vega/devroot/incubator-wave && sbt "wave/compile"
```

Expected: BUILD SUCCESS (no new imports needed — `setAttribute` is already available on GWT's `Element`).

- [ ] **Step 3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/editor/content/paragraph/DefaultParagraphHtmlRenderer.java
git commit -m "feat(rtl): render dir='auto' on paragraphs for bidi auto-detection

Replaces CSS direction property with HTML dir attribute. Paragraphs with no
stored direction now get dir=\"auto\", enabling browser-native detection of
RTL text (Hebrew, Arabic, etc.) based on first strong bidi character."
```

---

## Task 3: Add RTL/LTR toolbar buttons to `EditToolbar`

**Goal:** Add two toggle buttons labeled "RTL" and "LTR" into the alignment group of the toolbar. Clicking RTL sets `d="r"` (explicit RTL). Clicking LTR sets `d="l"` (explicit LTR). Clicking the active button again returns to auto (`d` removed). Neither button is highlighted in auto state.

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java:139-178` (init method) and add new method

- [ ] **Step 1: Add `createDirectionButtons()` method to `EditToolbar.java`**

Add this method after the `createAlignButtons()` method (after line 509):

```java
  private void createDirectionButtons(ToolbarView toolbar) {
    ToolbarToggleButton rtlButton = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setText("RTL")
        .applyTo(rtlButton, createParagraphApplicationController(rtlButton, Paragraph.Direction.RTL));

    ToolbarToggleButton ltrButton = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setText("LTR")
        .applyTo(ltrButton, createParagraphApplicationController(ltrButton, Paragraph.Direction.LTR));
  }
```

Explanation: `Paragraph.Direction.RTL` and `Paragraph.Direction.LTR` both implement `LineStyle`, so they plug directly into `createParagraphApplicationController()` — the same mechanism used by alignment buttons. The `ButtonUpdater` will call `isApplied()` on each to determine the active state.

- [ ] **Step 2: Call `createDirectionButtons()` from `init()`**

Find the block in `init()` that creates the alignment group (lines 167-169):

**Before:**
```java
    group = toolbarUi.addGroup();
    createAlignButtons(group);
    createClearFormattingButton(group);
```

**After:**
```java
    group = toolbarUi.addGroup();
    createAlignButtons(group);
    createDirectionButtons(group);
    createClearFormattingButton(group);
```

- [ ] **Step 3: Compile to verify no errors**

```
cd /Users/vega/devroot/incubator-wave && sbt "wave/compile"
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java
git commit -m "feat(rtl): add RTL/LTR toggle buttons to editor toolbar

Two text-labeled toggle buttons in the alignment group. RTL sets d=r (explicit
RTL), LTR sets d=l (explicit LTR), clicking the active button returns to auto.
Neither button highlights when direction is auto-detected."
```

---

## Task 4: Full build and local verification

**Goal:** Confirm the GWT client compiles and the feature works correctly in the browser.

- [ ] **Step 1: Run full test suite**

```
cd /Users/vega/devroot/incubator-wave && sbt test
```

Expected: All tests pass. Note: GWT compilation tests may be slow.

- [ ] **Step 2: Start dev server**

```
cd /Users/vega/devroot/incubator-wave && sbt run
```

Wait for "Server started" message (port 9898).

- [ ] **Step 3: Register and log in**

Open http://localhost:9898 in browser. Register a fresh test user (do not assume any existing user exists).

- [ ] **Step 4: Test auto-detection**

1. Create a new wave, open a blip for editing
2. Type Hebrew text: `שלום לך יורי` — the paragraph should auto-align right with correct bidi (the `!` or punctuation should appear on the LEFT end of the visual line, i.e. the terminal position in Hebrew)
3. Verify the toolbar shows neither RTL nor LTR button highlighted
4. On a new line, type English text — verify it aligns left (LTR auto-detected)

- [ ] **Step 5: Test manual override — RTL**

1. Place cursor in a new English paragraph
2. Click the RTL button in toolbar
3. Verify the paragraph gets `dir="rtl"` (inspect element in browser DevTools: right-click → Inspect → check div's dir attribute)
4. Verify RTL button is now highlighted, LTR is not
5. Type English text in this forced-RTL paragraph — it should start from the right

- [ ] **Step 6: Test manual override — LTR on a Hebrew paragraph**

1. Type Hebrew text in a new paragraph (it auto-detects RTL)
2. Click LTR button
3. Verify the paragraph gets `dir="ltr"` (Hebrew text will display incorrectly — that's correct behavior, it's a user override)
4. Verify LTR button is highlighted, RTL is not

- [ ] **Step 7: Test return to auto**

1. In an explicit RTL paragraph (from Step 5), click RTL button again
2. Verify RTL button becomes un-highlighted, neither button is active
3. Verify the div's `dir` attribute reverts to `"auto"` (check in DevTools)

- [ ] **Step 8: Test existing right-aligned paragraphs are unchanged**

1. In an existing paragraph, click "Right" in the alignment submenu
2. Verify alignment works as before (text-align: right CSS, no direction change)
3. Confirm this is NOT the same as RTL — the alignment-only paragraph should show `dir="auto"` (not `dir="rtl"`)

---

## Task 5: Create Pull Request

- [ ] **Step 1: Verify branch is clean**

```bash
git status
git log --oneline main..HEAD
```

Expected: 3 commits (Task 1, Task 2, Task 3).

- [ ] **Step 2: Push branch**

```bash
git push -u origin HEAD
```

- [ ] **Step 3: Create PR**

```bash
gh pr create \
  --title "feat(rtl): auto-detect RTL languages with dir=auto and manual override toolbar" \
  --body "$(cat <<'EOF'
## Summary

- Paragraphs with no explicit direction now render with `dir=\"auto\"`, enabling browser-native RTL auto-detection for Hebrew, Arabic, and other RTL scripts based on the first strong Unicode bidi character
- Added **RTL** and **LTR** toggle buttons to the editor toolbar (alignment group) for manual direction override
- Explicit LTR now stored as `d=\"l\"` in the document model (previously LTR was always null/default); toolbar shows neither button active in auto-detect state

## Behavior

| State | dir attribute | Toolbar |
|-------|--------------|---------|
| Auto (no `d` attr) | `dir="auto"` | Neither highlighted |
| Explicit RTL (`d="r"`) | `dir="rtl"` | RTL highlighted |
| Explicit LTR (`d="l"`) | `dir="ltr"` | LTR highlighted |

## Test plan

- [ ] Type Hebrew text → paragraph auto-aligns right with correct bidi punctuation placement
- [ ] Type English text → paragraph stays LTR, neither toolbar button lit
- [ ] Click RTL in toolbar → forces RTL on any paragraph, button highlights, attr is `dir="rtl"`
- [ ] Click RTL again → returns to auto, `dir="auto"` restored, neither button lit
- [ ] Click LTR on a Hebrew paragraph → forces LTR override
- [ ] Existing right-aligned (text-align only) paragraphs are unaffected
- [ ] `sbt test` passes

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: PR URL printed to stdout.
