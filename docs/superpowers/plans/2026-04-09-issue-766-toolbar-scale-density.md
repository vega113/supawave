# Issue #766 Toolbar Scale Density Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the search panel and wave panel toolbars so the recent SVG icons feel comfortably contained without reopening the earlier clipping/layout fix.

**Architecture:** Keep the existing `36px` toolbar-row containment and layout reserves from `#763`. The remaining issue is optical density: the shared icon wrappers render `18px` SVGs inside `36px` compact buttons, which still reads slightly cramped in live UI. The narrowest fix is to reduce the shared display size to `17px` through the existing `.toolbar-svg-icon` CSS contract in `WebClient`, so search, wave, and related toolbar icons stay visually aligned without changing bar height, button hit area, or panel offsets.

**Tech Stack:** GWT/Java client code, source-contract JUnit test, changelog fragments, local server verification on port `9900`, Playwright/manual browser inspection.

---

## Acceptance Criteria

- Search panel toolbar icons feel slightly less cramped while keeping the same overall bar height.
- Wave panel toolbar icons feel visually centered and comfortable in the existing toolbar chrome.
- Recent icon styling, hover behavior, and existing `36px` containment remain intact.
- A regression test protects the shared display-size contract.
- Local verification is completed against the worktree server with both toolbars visible.

## Root Cause Summary (Validated)

- Live inspection on `http://localhost:9900` shows both the search and wave icon rows at `36px` button height with `18px` SVG display size.
- A temporary in-browser comparison showed:
  - `18px` remains slightly dense in the wave toolbar.
  - `16px` looks too small and starts to lose the recent icon presence.
  - `17px` is the best visual balance and does not require reopening layout reserves.
- Increasing toolbar height would force follow-up layout changes in the search panel and wave panel for marginal visual benefit, so it is unnecessary scope for this issue.

## File Ownership / Likely Touch Set

- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`
- Create: `wave/config/changelog.d/2026-04-09-issue-766-toolbar-scale-density.json`

## Non-Goals

- No changes to toolbar row height, panel offsets, or compact button hit targets.
- No redesign of the icon set, hover color, or toolbar grouping.
- No unrelated edit-toolbar behavior changes beyond the shared icon-size contract that this shared CSS seam already affects.

### Task 1: Add A Failing Regression Test For The Shared Icon Display Contract

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`

- [ ] **Step 1: Add a source-contract test for the shared 17px icon display size**

Add a new test near the other toolbar contract assertions:

```java
  public void testSharedToolbarIconCssUsesSeventeenPixelDisplaySize() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java");

    assertTrue(javaSource.contains(".toolbar-svg-icon {"));
    assertTrue(javaSource.contains("width: 17px;"));
    assertTrue(javaSource.contains("height: 17px;"));
    assertTrue(javaSource.contains(".toolbar-svg-icon svg {"));
  }
```

- [ ] **Step 2: Run the focused test and confirm it fails for the expected reason**

Run:
```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected:
- FAIL because `WebClient.injectToolbarIconCss()` still renders the shared toolbar SVG display contract without the new `17px` sizing.

### Task 2: Implement The Minimal Shared Icon-Scale Fix

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java`

- [ ] **Step 1: Update the injected shared toolbar icon CSS to a 17px display contract**

Inside `injectToolbarIconCss()`, update the CSS string so the wrapper and nested SVG both use the polished `17px` display size while keeping the existing hover/active behavior:

```java
    String css =
        ".toolbar-svg-icon {"
      + "  display: inline-flex;"
      + "  align-items: center;"
      + "  justify-content: center;"
      + "  width: 17px;"
      + "  height: 17px;"
      + "  transition: color 0.15s ease, transform 0.15s ease;"
      + "}"
      + ".toolbar-btn-enabled .toolbar-svg-icon {"
      + "  color: #4a5568;"
      + "}"
      + ".toolbar-btn-enabled .toolbar-svg-icon:hover {"
      + "  color: #0077b6;"
      + "}"
      + ".toolbar-btn-enabled .toolbar-svg-icon:active {"
      + "  transform: scale(0.92);"
      + "}"
      + ".toolbar-svg-icon svg {"
      + "  display: block;"
      + "  width: 17px;"
      + "  height: 17px;"
      + "}";
```

- [ ] **Step 2: Re-run the focused regression test**

Run:
```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected:
- PASS.

### Task 3: Add Changelog Fragment And Verify Against The Running Worktree Server

**Files:**
- Create: `wave/config/changelog.d/2026-04-09-issue-766-toolbar-scale-density.json`

- [ ] **Step 1: Add the changelog fragment**

Create:

```json
{
  "releaseId": "2026-04-09-issue-766-toolbar-scale-density",
  "version": "PR #766",
  "date": "2026-04-09",
  "title": "Polish toolbar icon scale and containment",
  "summary": "Reduces the shared toolbar SVG display size so search and wave toolbars feel better centered inside the existing compact bars.",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Search and wave toolbar icons render with slightly lighter visual density while keeping the same compact toolbar height"
      ]
    }
  ]
}
```

- [ ] **Step 2: Assemble and validate the changelog**

Run:
```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected:
- `assembled ... -> wave/config/changelog.json`
- `changelog validation passed`

- [ ] **Step 3: Run the narrow automated verification**

Run:
```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected:
- PASS.

- [ ] **Step 4: Run the local server sanity check from this worktree**

Run:
```bash
PORT=9900 bash scripts/wave-smoke.sh check
```

Expected:
- `ROOT_STATUS=200`
- `HEALTH_STATUS=200`
- `WEBCLIENT_STATUS=200`

- [ ] **Step 5: Perform local visual verification with both toolbars visible**

Use the worktree server at `http://localhost:9900`:

1. Sign in with a local test account.
2. Confirm the search panel toolbar is visible with the compact icon row.
3. Open a wave so the wave panel toolbar is visible at the same time.
4. Put the wave into edit mode once and confirm the edit toolbar still looks balanced, since it shares the same `.toolbar-svg-icon` contract.
5. Compare against the baseline:
   - icons should read slightly lighter and better centered
   - no clipping should be reintroduced
   - the bar height should remain unchanged
   - search badge/task variants should still render correctly with the shared width/height override

- [ ] **Step 6: Commit the scoped change**

```bash
git add wave/src/main/java/org/waveprotocol/box/webclient/client/WebClient.java \
  wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java \
  wave/config/changelog.d/2026-04-09-issue-766-toolbar-scale-density.json
git commit -m "fix(toolbar): polish shared icon scale density"
```
