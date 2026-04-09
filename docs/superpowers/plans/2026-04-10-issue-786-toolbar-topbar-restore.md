# Issue #786 Toolbar Topbar Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the search/top-level toolbar treatment so the search row and wave toolbar look intentional again: keep refresh in the search toolbar, remove the visible `...` overflow affordance for that row, and bring back the contained toolbar chrome around the icons.

**Architecture:** Treat the most recent toolbar-containment polish (`#783`) as the regression seam. The search and wave toolbars share the compact button contract in `HorizontalToolbarButtonWidget.css`, and that change introduced a white inset idle canvas plus wider compact geometry that visually replaced the older toolbar-strip treatment. The top-level overflow affordance also still comes from `ToplevelToolbarWidget`, so the narrowest fix is to restore the earlier compact-button contract, keep refresh defined in `SearchPresenter`, and add an explicit search-toolbar-only switch that disables the `...` overflow path while letting the row wrap naturally.

**Tech Stack:** GWT/Java client code, shared toolbar CSS, JUnit 3 source-contract tests, staged local Wave server on port `9900`, browser verification against the running UI.

---

## Acceptance Criteria

- The search/top-level toolbar shows the intended toolbar-strip treatment again instead of white inset icon tiles.
- The search toolbar still contains the refresh action.
- The visible `...` overflow button is gone from the search toolbar.
- The search toolbar and wave toolbar both look visually coherent after the restore.
- Local browser verification is recorded from the running worktree UI.

## Root Cause Summary

- `#783` widened compact buttons from `28px/4px` to `32px/6px` and added a shared `.enabled.compact > .overlay` idle canvas, which visually covers the toolbar strip in both the search and wave toolbars.
- `ToplevelToolbarWidget` still mounts the overflow submenu and `...` icon unconditionally, so the search toolbar can still render an unnecessary overflow affordance even when the preferred behavior is a wrapped row.
- `SearchPresenter` still owns the refresh button in the search toolbar already; the restore work should preserve that placement and guard it with a narrow contract check rather than moving behavior elsewhere.

## File Ownership / Likely Touch Set

- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/ToplevelToolbarWidget.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`
- Create: `wave/config/changelog.d/2026-04-10-issue-786-toolbar-topbar-restore.json`
- Create/Update: `journal/local-verification/2026-04-10-issue-786-toolbar-topbar-restore.md`

## Out Of Scope

- No redesign of the wave toolbar icon set or button grouping.
- No changes to the search query semantics behind Inbox/Public/Archive/Pinned/Tasks/Mentions.
- No broad rewrite of the legacy toolbar overflow system outside the search toolbar path.

### Task 1: Add Failing Contract Coverage For The Restore

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`

- [ ] **Step 1: Add the compact restore assertions**

Update `ToolbarLayoutContractTest` so it expects the pre-`#783` compact contract and rejects the shared idle-canvas overlay:

```java
  public void testCompactButtonsUseDenseWidthContract() throws Exception {
    String css = normalized(read(
        "wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css"));

    assertTrue(css.contains("padding: 0 4px;"));
    assertTrue(css.contains("min-width: 28px;"));
  }

  public void testCompactButtonsDoNotRenderInsetIdleCanvas() throws Exception {
    String css = normalized(read(
        "wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css"));

    assertFalse(css.contains(".enabled.compact > .overlay {"));
    assertFalse(css.contains("background-color: rgba(255,255,255,0.72);"));
    assertFalse(css.contains("border: 1px solid rgba(176,196,216,0.55);"));
  }
```

- [ ] **Step 2: Add search-toolbar ownership assertions for refresh and overflow disablement**

Extend the same test class with narrow source-contract checks:

```java
  public void testSearchToolbarStillDeclaresRefreshAction() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java");

    assertTrue(javaSource.contains("setTooltip(\"Refresh search results\")"));
    assertTrue(javaSource.contains("forceRefresh(false);"));
    assertTrue(javaSource.contains("createSvgIcon(ICON_REFRESH)"));
  }

  public void testSearchPanelDisablesToolbarOverflowButton() throws Exception {
    String javaSource = read(
        "wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java");

    assertTrue(javaSource.contains("toolbar.setOverflowEnabled(false);"));
  }
```

- [ ] **Step 3: Run the focused test class and confirm it fails for the expected reasons**

Run:
```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected:
- FAIL because the CSS still contains the widened `32px / 6px` contract and the inset idle canvas.
- FAIL because `SearchPanelWidget` does not yet disable the overflow button.

### Task 2: Implement The Minimal Toolbar Restore

**Files:**
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/ToplevelToolbarWidget.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java`

- [ ] **Step 1: Restore the pre-`#783` compact button geometry and remove the idle canvas**

In `HorizontalToolbarButtonWidget.css`, change the compact block back to:

```css
.self.compact {
  padding: 0 4px;
  min-width: 28px;
  justify-content: center;
}
```

and remove the compact idle-canvas rules:

```css
.enabled.compact > .overlay { ... }
.enabled.compact:hover > .overlay { ... }
.enabled.down.compact > .overlay { ... }
```

Leave the shared pressed/hover overlay behavior for non-compact buttons unchanged.

- [ ] **Step 2: Add a shared overflow toggle to `ToplevelToolbarWidget`**

Add a boolean flag and setter so callers can disable the visible overflow affordance without rewriting the whole widget:

```java
  private boolean overflowEnabled = true;

  public void setOverflowEnabled(boolean overflowEnabled) {
    this.overflowEnabled = overflowEnabled;
    overflowButton.setVisible(overflowEnabled);
    if (!overflowEnabled) {
      restoreItemsToToplevel();
      overflowSubmenu.setState(State.INVISIBLE);
    } else {
      overflowLogic.updateStateEventually();
    }
  }
```

Add a small private helper that restores all items to their top-level delegates:

```java
  private void restoreItemsToToplevel() {
    for (Item item : items) {
      item.asAbstractButton.setParent(this);
      item.proxy.setDelegate(item.onToplevel);
      item.onOverflow.setState(State.INVISIBLE);
      item.onToplevel.setState(item.proxy.hackGetState());
    }
  }
```

Guard the overflow callbacks so they no-op when overflow is disabled:

```java
  @Override
  public boolean hasOverflowed(int index) {
    if (!overflowEnabled) {
      return false;
    }
    return items.get(index).onToplevel.getElement().getOffsetTop() > 0;
  }
```

Apply the same early return/no-op pattern to:
- `moveToOverflowBucket(int index)`
- `onBeginOverflowLayout()`
- `onEndOverflowLayout()`
- `showMoreButton()`
- `onResizeDone()`
- `onChildStateChanged(SubmenuItem item, State newState)`

- [ ] **Step 3: Disable overflow for the search toolbar only**

In the `SearchPanelWidget` constructor, immediately after `initWidget(...)`, add:

```java
    toolbar.setOverflowEnabled(false);
```

This keeps the search row free of the `...` affordance while leaving the wave/edit toolbar behavior unchanged unless explicitly opted out later.

- [ ] **Step 4: Keep refresh in the search toolbar**

Do not move refresh out of `SearchPresenter.initToolbarMenu()`. Review the diff and confirm the refresh action remains in the search filter group and no new refresh button is introduced elsewhere.

### Task 3: Verify Green And Add Changelog Evidence

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/ToolbarLayoutContractTest.java`
- Create: `wave/config/changelog.d/2026-04-10-issue-786-toolbar-topbar-restore.json`

- [ ] **Step 1: Re-run the focused test class**

Run:
```bash
sbt "testOnly org.waveprotocol.box.server.util.ToolbarLayoutContractTest"
```

Expected:
- PASS.

- [ ] **Step 2: Add the changelog fragment**

Create:

```json
{
  "releaseId": "2026-04-10-issue-786-toolbar-topbar-restore",
  "version": "PR #786",
  "date": "2026-04-10",
  "title": "Restore the search toolbar chrome and remove temporary overflow affordance",
  "summary": "Restores the intended toolbar-strip treatment for search and wave action rows, keeps refresh in the search toolbar, and removes the visible search-toolbar overflow button.",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Search and wave toolbars render with the intended contained toolbar chrome again",
        "The search toolbar keeps its refresh action while dropping the temporary ... overflow control"
      ]
    }
  ]
}
```

- [ ] **Step 3: Assemble and validate the changelog**

Run:
```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected:
- `assembled ... -> wave/config/changelog.json`
- `changelog validation passed`

### Task 4: Run Local UI Verification From This Worktree

**Files:**
- Create/Update: `journal/local-verification/2026-04-10-issue-786-toolbar-topbar-restore.md`

- [ ] **Step 1: Prepare the staged worktree server**

Run:
```bash
bash scripts/worktree-boot.sh --port 9900 --shared-file-store
```

Expected:
- staged distribution exists
- port-specific runtime config is written
- a local verification evidence file is created or updated

- [ ] **Step 2: Start and check the local server**

Run:
```bash
PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/toolbar-topbar-restore-20260410/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/toolbar-topbar-restore-20260410/wave/config/jaas.config' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
```

Expected:
- root `200` or `302`
- `/healthz` `200`
- `/webclient/webclient.nocache.js` `200`

- [ ] **Step 3: Verify the actual toolbars in the browser**

Using `http://localhost:9900`, confirm:
- the search/top-level toolbar no longer shows the `...` button
- the search toolbar still contains the refresh icon/button
- the search toolbar reads as a single contained strip again instead of isolated white icon tiles
- the wave toolbar still looks visually coherent with the restored shared compact-button styling

- [ ] **Step 4: Record exact commands and results**

Update `journal/local-verification/2026-04-10-issue-786-toolbar-topbar-restore.md` with:
- worktree boot command
- server start/check command
- test command
- concise browser-verification notes for both toolbars

- [ ] **Step 5: Stop the local server after verification**

Run:
```bash
PORT=9900 bash scripts/wave-smoke.sh stop
```

## Self-Review Notes

- The restore stays intentionally narrow: shared compact-button styling plus a search-toolbar-specific overflow switch.
- If live browser verification shows the search toolbar still looks oversized after removing the idle canvas, revisit the shared icon display size only after the first restore pass; do not widen scope before checking the actual UI.
