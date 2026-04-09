# Issue #757 Toolbar Clip/Spacing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the remaining toolbar clipping and over-spacing defects by fixing the legacy `24px` toolbar height assumptions, tightening compact button width, and normalizing the search toolbar SVG wrapper/sizing so the search panel and wave/edit toolbars render consistently.

**Architecture:** The fix stays root-cause-first and keeps the shared toolbar stack as the primary seam. The shared inner toolbar row still clips because its sprite-backed container is `24px` tall with `overflow:hidden`; the search panel additionally reserves only `24px` in its own layout constants, and the top conversation view does the same for the wave toolbar. Compact buttons are also wider than intended because `min-width: 36px` is applied to the content box and then `8px` side padding is added on top, yielding `52px` total width. The search toolbar still uses the pre-polish SVG contract (`16px`, `2px` stroke, raw wrapper), so it needs to be brought onto the same wrapper/sizing contract as the wave/edit toolbar while preserving the correct task/RTL/icon semantics.

**Tech Stack:** GWT `CssResource`, GWT DOM helpers, Java `TestCase` tests, local staged server on port `9900`, Playwright/manual browser verification.

---

## Acceptance Criteria

- Search panel toolbar buttons are fully visible vertically; no button or icon is clipped by a `24px` parent.
- Wave view toolbar buttons are fully visible vertically.
- Edit toolbar buttons are fully visible vertically, including the wrapped second row on narrow widths.
- Compact toolbar buttons render noticeably denser than before; the total button box is about `36px` wide instead of `52px`.
- Search toolbar icons use the same `toolbar-svg-icon` wrapper and `18px / 1.75 stroke` SVG contract as the polished wave/edit toolbar.
- RTL and task icons remain semantically correct:
  - alignment uses alignment glyphs
  - RTL uses the dedicated text-direction icon
  - insert-task and search-task icons are not accidentally swapped with unrelated glyphs
- Local verification is completed against the branch server on `http://localhost:9900`, including login, creating/opening waves, editing blips, and checking both the search panel and wave/edit toolbars.

## Root Cause Summary (Validated)

- Browser inspection on the branch server (`http://localhost:9900`) shows shared toolbar buttons at `36px` tall inside an immediate toolbar parent that is still `24px` tall with `overflow:hidden`.
- Search toolbar buttons are `52px` wide because `.self.compact` currently combines `min-width: 36px` with `padding: 0 8px`; the `36px` minimum applies to the content box, so total width becomes `36 + 8 + 8`.
- Search panel layout still reserves `24px` for the toolbar via `SearchPanelWidget.CssConstants` and the outer search toolbar strip, so even a fixed inner toolbar would still overlap the wave-count/list layout unless the reserved height is updated too.
- The top conversation view still derives the thread offset from `toolbar_empty.png` height (`24px`), so the wave/edit toolbar area has the same legacy height assumption.
- Search toolbar icons still use the older SVG contract (`16px`, `2px`, raw wrapper) while wave/edit use `18px`, `1.75`, and `toolbar-svg-icon`.

## File Ownership / Likely Touch Set

- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/ToplevelToolbarWidget.css`
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java`
- Modify: `wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchPanel.css`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/TopConversationViewBuilder.java`
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Conversation.css`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`
- Modify only if icon audit requires correction: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/client/widget/toolbar/ToolbarCssContractTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/webclient/search/SearchPanelWidgetLayoutTest.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/TopConversationViewBuilderLayoutTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/webclient/search/SearchPresenterTest.java`
- Changelog: `wave/config/changelog.d/2026-04-09-issue-757-toolbar-clip-spacing.json`

## Task 1: Add Failing Regression Tests For The Verified Contracts

**Files:**
- Create: `wave/src/test/java/org/waveprotocol/wave/client/widget/toolbar/ToolbarCssContractTest.java`
- Create: `wave/src/test/java/org/waveprotocol/box/webclient/search/SearchPanelWidgetLayoutTest.java`
- Create: `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/TopConversationViewBuilderLayoutTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/webclient/search/SearchPresenterTest.java`

- [ ] **Step 1: Write a CSS contract test for toolbar row height and compact width**

```java
public void testCompactButtonsUseDenseWidthContract() throws Exception {
  String css = Files.readString(Path.of(
      "wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css"));
  assertTrue(css.contains("padding: 0 4px;"));
  assertTrue(css.contains("min-width: 28px;"));
}

public void testToolbarRowAllowsFullButtonHeight() throws Exception {
  String css = Files.readString(Path.of(
      "wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/ToplevelToolbarWidget.css"));
  assertTrue(css.contains("height: auto;"));
  assertTrue(css.contains("min-height: 36px;"));
}
```

- [ ] **Step 2: Write layout-constant tests for the search panel and top conversation toolbar reserve**

```java
public void testWaveCountStartsAfterThirtySixPixelToolbar() {
  assertEquals("87px", SearchPanelWidget.CssConstants.WAVE_COUNT_TOP);
  assertEquals("112px", SearchPanelWidget.CssConstants.LIST_TOP);
}

public void testThreadTopUsesThirtySixPixelToolbarReserve() {
  assertEquals(
      ParticipantsViewBuilder.COLLAPSED_HEIGHT_PX + 36 + "px",
      TopConversationViewBuilder.CssConstants.THREAD_TOP_CSS);
}
```

- [ ] **Step 3: Extend `SearchPresenterTest` with the SVG contract assertion**

```java
public void testSearchToolbarSvgContractMatchesPolishedToolbarSizing() throws Exception {
  Field field = SearchPresenter.class.getDeclaredField("SVG_OPEN");
  field.setAccessible(true);
  String svgOpen = (String) field.get(null);

  assertTrue(svgOpen.contains("width=\"18\""));
  assertTrue(svgOpen.contains("height=\"18\""));
  assertTrue(svgOpen.contains("stroke-width=\"1.75\""));
}
```

- [ ] **Step 4: Run the focused test set and confirm it fails for the expected reasons**

Run:
```bash
sbt "testOnly org.waveprotocol.wave.client.widget.toolbar.ToolbarCssContractTest org.waveprotocol.box.webclient.search.SearchPanelWidgetLayoutTest org.waveprotocol.wave.client.wavepanel.view.dom.full.TopConversationViewBuilderLayoutTest org.waveprotocol.box.webclient.search.SearchPresenterTest"
```

Expected:
- FAIL because toolbar CSS still reserves `24px`, compact buttons still use `8px` padding + `36px` min-width, and `SearchPresenter.SVG_OPEN` still uses the `16px / 2px` contract.

## Task 2: Implement The Minimal Root-Cause Fix

**Files:**
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/ToplevelToolbarWidget.css`
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.css`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPanelWidget.java`
- Modify: `wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchPanel.css`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/TopConversationViewBuilder.java`
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/Conversation.css`
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`
- Modify only if required by the icon audit: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`

- [ ] **Step 1: Fix the shared toolbar row contract**

```css
@sprite .toolbar {
  gwt-image: 'fillImage';
  overflow: hidden;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  height: auto;
  min-height: 36px;
}
```

- [ ] **Step 1a: Validate the `@sprite` override behavior before touching more callers**

Run:
```bash
PORT=9900 bash scripts/wave-smoke.sh check
```

Then verify in the browser devtools/Playwright inspector that the shared toolbar row reports:
- computed `height: 36px` (or taller when wrapped)
- computed `overflow: hidden`
- no remaining computed `24px` height on the shared toolbar row

If GWT keeps the sprite-generated `24px` height despite the explicit CSS properties, replace the shared toolbar fill background with a plain CSS background instead of relying on the sprite-generated rule.

- [ ] **Step 2: Tighten compact button width without dropping below a 36px total target**

```css
.self {
  height: auto;
  min-height: 36px;
}

.self.compact {
  padding: 0 4px;
  min-width: 28px;
  justify-content: center;
}
```

- [ ] **Step 3: Update the search panel’s reserved toolbar height**

```java
static class CssConstants {
  private static int SEARCH_HEIGHT_PX = 51;
  private static int TOOLBAR_HEIGHT_PX = 36;
  private static int TOOLBAR_TOP_PX = SEARCH_HEIGHT_PX;
  private static int WAVE_COUNT_HEIGHT_PX = 25;
  private static int WAVE_COUNT_TOP_PX = TOOLBAR_TOP_PX + TOOLBAR_HEIGHT_PX;
  private static int LIST_TOP_PX = WAVE_COUNT_TOP_PX + WAVE_COUNT_HEIGHT_PX;
}
```

- [ ] **Step 4: Make the outer search and conversation toolbar strips honor the taller row**

```css
@sprite .toolbar {
  gwt-image: 'emptyToolbar';
  height: auto;
  min-height: 36px;
}
```

- [ ] **Step 5: Update the top conversation Java layout reserve to match the taller toolbar**

```java
static class CssConstants {
  final static String THREAD_TOP_CSS =
      ParticipantsViewBuilder.COLLAPSED_HEIGHT_PX + 36 + "px";
}
```

- [ ] **Step 6: Bring the search toolbar SVG wrapper/sizing onto the polished contract**

```java
private static final String SVG_OPEN =
    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"18\" height=\"18\" "
    + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
    + "stroke-width=\"1.75\" stroke-linecap=\"round\" stroke-linejoin=\"round\" "
    + "style=\"display:block\">";

private static Element createSvgIcon(String svgHtml) {
  Element wrapper = DOM.createDiv();
  wrapper.setClassName("toolbar-svg-icon");
  wrapper.setInnerHTML(svgHtml);
  return wrapper;
}
```

- [ ] **Step 7: Audit task/RTL/icon semantics and only adjust markup that is actually wrong**

```java
// Keep dedicated glyph ownership:
// - alignment submenu => alignment glyph
// - RTL direction => ICON_RTL
// - insert task => ICON_TASK
// - search tasks => ICON_TASKS
```

If the audit shows a mistaken reuse, fix only that icon constant or call site.

## Task 3: Verify Green, Add Changelog Fragment, And Rebuild

**Files:**
- Create: `wave/config/changelog.d/2026-04-09-issue-757-toolbar-clip-spacing.json`

- [ ] **Step 1: Add the changelog fragment**

```json
{
  "releaseId": "2026-04-09-issue-757-toolbar-clip-spacing",
  "version": "PR #757",
  "date": "2026-04-09",
  "title": "Fix toolbar clipping and compact spacing",
  "summary": "Fixes legacy 24px toolbar height assumptions, tightens compact toolbar width, and aligns the search toolbar SVG contract with the wave/edit toolbar.",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Toolbar icons no longer clip in the search, wave, or edit toolbars",
        "Compact toolbar buttons render denser spacing without losing icon clarity"
      ]
    }
  ]
}
```

- [ ] **Step 2: Run the focused test set again**

Run:
```bash
sbt "testOnly org.waveprotocol.wave.client.widget.toolbar.ToolbarCssContractTest org.waveprotocol.box.webclient.search.SearchPanelWidgetLayoutTest org.waveprotocol.wave.client.wavepanel.view.dom.full.TopConversationViewBuilderLayoutTest org.waveprotocol.box.webclient.search.SearchPresenterTest"
```

Expected:
- PASS

- [ ] **Step 3: Reassemble and validate the changelog**

Run:
```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected:
- `assembled ... -> wave/config/changelog.json`
- `changelog validation passed`

## Task 4: Local Visual Verification On The Branch Server

**Files:**
- Update: `journal/local-verification/2026-04-09-issue-757-toolbar-clip-spacing-20260409.md`

- [ ] **Step 1: Start and check the branch server on port 9900**

Run:
```bash
PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=wave/config/wiab-logging.conf -Djava.security.auth.login.config=wave/config/jaas.config' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
```

Expected:
- root `200` or `302`
- `/healthz` `200`
- `/webclient/webclient.nocache.js` `200`

- [ ] **Step 2: Enable the local task-search/mentions-search flags if needed for toolbar coverage**

Run as needed:
```bash
scripts/feature-flag.sh set task-search "Enable tasks toolbar button" --local
scripts/feature-flag.sh set mentions-search "Enable mentions toolbar button" --local
```

- [ ] **Step 3: Browser verification flow**

Verify on `http://localhost:9900`:
- register/sign in
- open the inbox/search panel and inspect the search toolbar
- create a new wave
- open the wave
- reply/edit so the edit toolbar appears
- inspect the wave view toolbar and edit toolbar
- resize to a narrow viewport so the edit toolbar wraps to two rows and confirm the second row is visible
- insert or focus a task so the task icon path is exercised

Expected:
- no toolbar icon bottoms are clipped
- no second-row edit-toolbar icons are hidden
- compact buttons feel materially denser than the pre-fix `52px` width
- search icons match the polished wrapper/sizing contract
- RTL/task icons are visually distinct and semantically correct

- [ ] **Step 4: Record the exact commands and results in the local verification journal**

## Out Of Scope

- Replacing the entire legacy GWT toolbar system with a new component set.
- Redesigning non-toolbar chrome such as tags, participant chips, or top-bar action buttons.
- Adding unrelated toolbar features or new search filters.
