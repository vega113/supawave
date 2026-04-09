# Toolbar Icon Canvas Fit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce vertical dead space around toolbar icons by increasing `TOOLBAR_ICON_DISPLAY_PX` from `17px` to `20px`, so icons fill more of the 36px button height and feel more compact.

**Architecture:** A single constant `TOOLBAR_ICON_DISPLAY_PX` in `WebClient.java` controls the CSS injected at startup for both `.toolbar-svg-icon` wrapper and its nested `svg`. Updating it from `"17px"` to `"20px"` is the narrowest possible fix: no layout reserves, no toolbar heights, no button hit areas change. A source-contract test in `ToolbarLayoutContractTest` needs to be updated in sync. A changelog fragment records the change.

**Tech Stack:** GWT/Java client, JUnit 3 source-contract tests (`TestCase`), local server on port `9900` for visual verification.

---

## Acceptance Criteria

- `.toolbar-svg-icon` wrapper and its nested `svg` both render at 20×20 px.
- Search panel toolbar and wave panel toolbar icons both look more compact in the browser.
- `ToolbarLayoutContractTest` passes with the new 20px assertion.
- No toolbar heights, panel offsets, or button hit areas changed.
- Changelog fragment added.

## File Ownership

| File | Change |
|---|---|
| `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java` | Change constant from `"17px"` to `"20px"` |
| `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java` | Update assertion from `17px` to `20px`, rename test method |
| `wave/config/changelog.d/2026-04-09-toolbar-icon-canvas-fit.json` | New changelog fragment |

---

### Task 1: Update the icon display size constant

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java`

The constant is near line 130. Change only the string value; the rest of `injectToolbarIconCss()` is unchanged.

- [ ] **Step 1: Verify the current constant value**

```bash
grep -n "TOOLBAR_ICON_DISPLAY_PX" wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java
```

Expected output includes a line like:
```text
  private static final String TOOLBAR_ICON_DISPLAY_PX = "17px";
```

- [ ] **Step 2: Change the constant from `"17px"` to `"20px"`**

In `WebClient.java`, find:
```java
  private static final String TOOLBAR_ICON_DISPLAY_PX = "17px";
```

Replace with:
```java
  private static final String TOOLBAR_ICON_DISPLAY_PX = "20px";
```

No other lines in the file need changing — both the wrapper and SVG CSS rules already reference the constant via string concatenation.

- [ ] **Step 3: Verify no stray `17px` references remain in the injected CSS comment**

```bash
grep -n "17px\|17 px" wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java
```

Expected: zero matches (the only occurrence was the constant string itself).

---

### Task 2: Update the source-contract test

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`

The test `testSharedToolbarIconCssUsesSeventeenPixelDisplaySize` asserts `TOOLBAR_ICON_DISPLAY_PX = "17px"`. Rename it and update the assertion.

- [ ] **Step 1: Run the existing test and confirm it now fails after Task 1**

From the repo root:
```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected: one test fails because the constant is now `"20px"` but the test still checks for `"17px"`.

- [ ] **Step 2: Update the test method name and assertion**

In `ToolbarLayoutContractTest.java`, find the method:
```java
  public void testSharedToolbarIconCssUsesSeventeenPixelDisplaySize() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java");

    // Single source of truth: the constant must declare 17px
    assertTrue(javaSource.contains("TOOLBAR_ICON_DISPLAY_PX = \"17px\""));
```

Replace the method signature and that assertion with:
```java
  public void testSharedToolbarIconCssUsesTwentyPixelDisplaySize() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java");

    // Single source of truth: the constant must declare 20px
    assertTrue(javaSource.contains("TOOLBAR_ICON_DISPLAY_PX = \"20px\""));
```

Leave the rest of the method body unchanged — it already checks that the constant is referenced at least twice in the wrapper rule and twice in the svg rule, which remains correct.

- [ ] **Step 3: Run the test suite and confirm all tests pass**

```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected: all tests in this class pass, including the renamed method.

---

### Task 3: Add changelog fragment

**Files:**
- Create: `wave/config/changelog.d/2026-04-09-toolbar-icon-canvas-fit.json`

- [ ] **Step 1: Create the changelog fragment**

```json
{
  "releaseId": "2026-04-09-toolbar-icon-canvas-fit",
  "version": "PR #<number>",
  "date": "2026-04-09",
  "title": "Increase toolbar icon canvas size for tighter vertical fit",
  "summary": "Bumps the shared toolbar icon display size from 17px to 20px.",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Search and wave toolbar icons render at 20px inside the existing action buttons"
      ]
    }
  ]
}
```

---

### Task 4: Run all ToolbarLayout tests and commit

**Files:** (no new file changes, just verification + commit)

- [ ] **Step 1: Run the full ToolbarLayoutContractTest class**

```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected: all 5 tests pass.

- [ ] **Step 2: Run a broader test pass to check for regressions**

```bash
sbt test 2>&1 | tail -20
```

Expected: build succeeds, no test failures.

- [ ] **Step 3: Commit all changes**

```bash
git add wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java
git add wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java
git add wave/config/changelog.d/2026-04-09-toolbar-icon-canvas-fit.json
git commit -m "fix(toolbar): increase icon canvas size to 20px for tighter vertical fit (#778)

Reduces vertical dead space around toolbar icons from ~9.5px to ~8px per side
by bumping TOOLBAR_ICON_DISPLAY_PX from 17px to 20px. No toolbar heights,
panel offsets, or button hit areas changed.

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

---

### Task 5: Local verification

- [ ] **Step 1: Start the local server (if not already running)**

From the repo/worktree root:
```bash
sbt "~compile" &
# Or start the full server:
sbt run
```

Server runs on `http://localhost:9900`.

- [ ] **Step 2: Open the search panel toolbar and wave panel toolbar in the browser**

Navigate to `http://localhost:9900` and inspect both toolbars. Icons should appear more compact vertically — visually filling more of the button height with less space above and below compared to the previous 17px size.

- [ ] **Step 3: Record evidence in the issue**

Post a comment on GitHub issue #778 summarizing the change (icon size 17px → 20px) and the result of local verification.

---

## Self-Review Notes

- The constant `TOOLBAR_ICON_DISPLAY_PX` is used in exactly 4 places in the injected CSS string (width and height of `.toolbar-svg-icon`, width and height of `.toolbar-svg-icon svg`). The test verifies exactly these 4 usages. No other files reference the literal pixel value.
- The `testSearchToolbarSvgContractMatchesPolishedToolbarSizing` test checks that SVG markup uses `width=\"18\"` and `height=\"18\"` in markup — those are the SVG element attributes that GWT overrides via CSS, so they are unaffected by this CSS change.
- No toolbar height constants (`TOOLBAR_HEIGHT_PX = 36`, `min-height: 36px`) need updating — this fix only changes icon rendering size, not button or bar dimensions.
