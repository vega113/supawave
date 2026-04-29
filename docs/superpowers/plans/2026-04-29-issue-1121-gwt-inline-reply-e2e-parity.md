# Issue #1121 GWT Inline Reply E2E Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `inline-reply-parity.spec.ts` so the GWT half drives the same reply, bold, send, and new-blip assertions that the J2CL half already covers.

**Architecture:** Add stable, inert GWT DOM hooks for the legacy blip menu and edit toolbar, then use those hooks from the parity E2E instead of hover-only or debounced accessibility selectors. Keep the change test-infrastructure-only: no command behavior, wave loading strategy, or production data flow changes.

**Tech Stack:** GWT client Java, existing toolbar/button view abstractions, Playwright parity harness under `wave/src/e2e/j2cl-gwt-parity`, SBT verification only for JVM/build gates.

---

## Scope And Non-Goals

- In scope: GWT `?view=gwt` inline reply automation for the existing Welcome wave flow in `wave/src/e2e/j2cl-gwt-parity/tests/inline-reply-parity.spec.ts`.
- In scope: stable `data-e2e-action` hooks for GWT blip menu `REPLY`, GWT edit-done `EDIT_DONE`, and the GWT Bold toolbar button.
- In scope: small unit coverage proving the generated menu/toolbar hook contract does not regress.
- Out of scope: J2CL UI behavior changes, GWT visual redesign, toolbar command rewrites, whole-wave loading changes, server-first HTML work, or changing default root routing.

## File Map

- Modify `wave/src/e2e/j2cl-gwt-parity/pages/GwtPage.ts`: keep the GWT shell sentinel and existing editor locator knowledge reusable from the page object.
- Modify `wave/src/e2e/j2cl-gwt-parity/tests/inline-reply-parity.spec.ts`: replace the GWT baseline-only assertion with full reply/bold/send flow and helper functions.
- Modify `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/BlipMetaViewBuilder.java`: add stable action attributes for `REPLY` and `EDIT_DONE` menu spans.
- Modify `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/BlipViewBuilderTest.java`: assert rendered menu HTML contains the stable GWT reply and edit-done hooks.
- Modify `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonView.java`: add a toolbar-view method for stable E2E action metadata.
- Modify `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/AbstractToolbarButton.java`: delegate stable E2E action metadata to the concrete button UI.
- Modify `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonViewProxy.java`: preserve/copy stable E2E action metadata when toolbar items move between top-level and overflow delegates.
- Modify `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonUiProxy.java`: forward stable E2E action metadata through the UI proxy used by top-level and overflow toolbar buttons.
- Modify `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.java`: emit `data-e2e-action` on the horizontal toolbar button root.
- Modify `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/VerticalToolbarButtonWidget.java`: emit `data-e2e-action` on the vertical/overflow toolbar button root.
- Add `wave/src/test/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonViewProxyTest.java`: prove `setE2eAction` copies and clears across delegate swaps.
- Modify `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`: mark the Bold button with `data-e2e-action="bold"`.
- Add `wave/config/changelog.d/2026-04-29-gwt-inline-reply-e2e-parity.json`: changelog fragment for the harness-visible parity coverage.

## Acceptance Criteria

- The GWT parity test registers a fresh user, opens the Welcome wave, confirms the GWT shell sentinel, records the current GWT blip count, clicks a real per-blip Reply action, types a unique `hello <suffix> world` reply, applies Bold to `world`, clicks the real GWT edit-done action, and observes a new reply blip in the GWT wave body.
- The J2CL half remains unchanged except for shared helper cleanup if needed; it must continue passing in the same spec file.
- The test must not use fixed sleeps as the primary synchronization for the new reply/bold/send flow; use locator waits and `expect.poll` for GWT deferred DOM work.
- The implementation must not rely on hidden text labels, hover-only menu reveal, or unstable obfuscated GWT CSS classes.
- Local verification evidence must include TypeScript no-emit, focused parity Playwright against local server, changelog validation, `sbt --batch j2clSearchTest`, and `sbt --batch compile`.

---

### Task 1: Prove The Current GWT Full Flow Has No Stable Path

**Files:**
- Modify: `wave/src/e2e/j2cl-gwt-parity/tests/inline-reply-parity.spec.ts`

- [ ] **Step 1: Add temporary GWT helper calls behind the existing GWT test**

Add helpers with the target selectors before implementing Java hooks. Keep them local to the spec only for the RED proof and do not commit this intermediate state:

```ts
async function clickReplyOnFirstBlipGwt(page: Page) {
  const reply = page.locator("[data-e2e-action='reply']").first();
  await expect(reply, "GWT reply action must expose a stable data-e2e-action hook").toBeVisible({
    timeout: 5_000
  });
  await reply.click({ timeout: 10_000 });
}
```

Change the GWT test temporarily to call `await clickReplyOnFirstBlipGwt(page);` immediately after the welcome-wave body assertion.

- [ ] **Step 2: Assert the GWT sentinel before the RED click**

The GWT test already uses `const gwt = new GwtPage(page, BASE_URL); await gwt.goto("/"); await gwt.assertInboxLoaded();`. Keep that sentinel in the RED run and in the final test so a future J2CL selector cannot accidentally satisfy the GWT path.

- [ ] **Step 3: Run the focused RED proof**

Run:

```bash
cd wave/src/e2e/j2cl-gwt-parity
CI=true WAVE_E2E_BASE_URL=http://127.0.0.1:9931 npx playwright test tests/inline-reply-parity.spec.ts --project=chromium --grep "GWT:"
```

Expected before Java hooks:

```text
FAIL ... GWT reply action must expose a stable data-e2e-action hook
```

If the failure is server startup, auth, or welcome-wave load instead of missing `[data-e2e-action='reply']`, stop and fix the harness setup before continuing.

- [ ] **Step 4: Revert the temporary RED-only edit before starting Java changes**

Use `git diff` to confirm the only remaining planned changes after the RED proof are intentional plan or implementation edits. The final commit must contain the permanent GWT helpers from Task 4, not this temporary RED-only helper.

Run this guard before the final implementation commit:

```bash
rg -n "GWT reply action must expose a stable data-e2e-action hook" wave/src/e2e/j2cl-gwt-parity/tests/inline-reply-parity.spec.ts && exit 1 || true
```

Expected: no match. The final spec may contain Task 4 helper names, but not the temporary RED-only assertion text.

---

### Task 2: Add Stable GWT Blip Menu Hooks

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/BlipMetaViewBuilder.java`
- Modify: `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/BlipViewBuilderTest.java`

- [ ] **Step 1: Add an action-name helper**

Add this helper near `menuBuilder` in `BlipMetaViewBuilder.java`:

```java
  private static String e2eActionFor(MenuOption option) {
    switch (option) {
      case REPLY:
        return "reply";
      case EDIT_DONE:
        return "edit-done";
      default:
        return null;
    }
  }
```

- [ ] **Step 2: Emit stable attributes in `menuBuilder`**

Update the `extra` construction inside `menuBuilder`:

```java
          String extra = OPTION_ID_ATTRIBUTE + "='" + MENU_CODES.get(option).asString() + "'"
              + " title='" + title + "'"
              + " data-option='" + dataOption + "'"
              + (selected.contains(option) ? " " + OPTION_SELECTED_ATTRIBUTE + "='s'" : "");
          String e2eAction = e2eActionFor(option);
          if (e2eAction != null) {
            extra += " data-e2e-action='" + e2eAction + "'";
          }
```

Do not change `OPTION_ID_ATTRIBUTE`, menu option codes, click handling, or menu ordering.

- [ ] **Step 3: Add unit coverage for menu hooks**

Add this test to `BlipViewBuilderTest.java`:

```java
  public void testMenuActionsExposeStableE2eHooks() {
    Set<MenuOption> options = CollectionUtils.newHashSet();
    options.add(MenuOption.REPLY);
    options.add(MenuOption.EDIT_DONE);
    String html = UiBuilderTestHelper.render(
        BlipMetaViewBuilder.menuBuilder(options, CollectionUtils.<MenuOption>newHashSet(), css));

    assertTrue(html.contains("data-e2e-action='reply'"));
    assertTrue(html.contains("data-e2e-action='edit-done'"));
    assertTrue(html.contains("data-option='reply'"));
    assertTrue(html.contains("data-option='edit_done'"));
  }
```

Required imports:

```java
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipMetaView.MenuOption;
import org.waveprotocol.wave.model.util.CollectionUtils;
import java.util.Set;
```

- [ ] **Step 4: Run the focused JVM test**

Run:

```bash
sbt --batch "testOnly org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilderTest"
```

Expected: test exits `0`. If the SBT task cannot isolate this legacy JUnit test, record the exact SBT limitation in #1121 and rely on `sbt --batch compile` plus the Playwright GREEN proof later.

---

### Task 3: Add Stable GWT Bold Toolbar Hook

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonView.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/AbstractToolbarButton.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonViewProxy.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonUiProxy.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/VerticalToolbarButtonWidget.java`
- Add: `wave/src/test/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonViewProxyTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`

- [ ] **Step 1: Extend the toolbar view contract**

Add to `ToolbarButtonView.java`:

```java
  /**
   * Sets a stable E2E action identifier on the rendered button, or clears it
   * when {@code action} is null.
   */
  void setE2eAction(String action);
```

Also add the no-op implementation to `ToolbarButtonView.EMPTY`.

```java
    @Override
    public void setE2eAction(String action) {
    }
```

- [ ] **Step 2: Delegate and preserve the metadata through toolbar proxies**

Add to `AbstractToolbarButton.java`:

```java
  @Override
  public void setE2eAction(String action) {
    button.setE2eAction(action);
  }
```

Add to `ToolbarButtonViewProxy.java`:

```java
  private String e2eAction;

  @Override
  public void setE2eAction(String action) {
    this.e2eAction = action;
    if (delegate != null) {
      delegate.setE2eAction(action);
    }
  }
```

Then call it unconditionally from `copyInto` so stale metadata is also cleared if a future button sets the action to null:

```java
    display.setE2eAction(e2eAction);
```

Add to `ToolbarButtonUiProxy.java` in the trivial delegation section:

```java
  @Override
  public void setE2eAction(String action) {
    proxy.setE2eAction(action);
  }
```

Current code facts to verify before editing:

```bash
rg -n "interface ToolbarButtonUi extends ToolbarButtonView|private final ToolbarButtonViewProxy proxy|void setDelegate" \
  wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonUi.java \
  wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonUiProxy.java
```

Expected:

```text
ToolbarButtonUi extends ToolbarButtonView.
ToolbarButtonUiProxy has a private final ToolbarButtonViewProxy field named proxy.
ToolbarButtonUiProxy.setDelegate(...) calls proxy.setDelegate(delegate), so delegate swaps route through ToolbarButtonViewProxy.copyInto(...).
```

- [ ] **Step 3: Emit attributes from concrete toolbar widgets**

Add to `HorizontalToolbarButtonWidget.java`:

```java
  @Override
  public void setE2eAction(String action) {
    if (action == null || action.isEmpty()) {
      getElement().removeAttribute("data-e2e-action");
    } else {
      getElement().setAttribute("data-e2e-action", action);
    }
  }
```

Add the same method to `VerticalToolbarButtonWidget.java`.

- [ ] **Step 4: Mark the Bold button**

Update `createBoldButton` in `EditToolbar.java`:

```java
  private void createBoldButton(ToolbarView toolbar) {
    ToolbarToggleButton b = toolbar.addToggleButton();
    new ToolbarButtonViewBuilder()
        .setTooltip("Bold")
        .applyTo(b, createTextSelectionController(b, "fontWeight", "bold"));
    b.setE2eAction("bold");
    b.setVisualElement(createSvgIcon(ICON_BOLD));
  }
```

Do not add hooks to every toolbar button in this issue. Keep the selector contract to the one GWT action needed by #1121.

- [ ] **Step 5: Add proxy unit coverage for action copy and clear**

Create `ToolbarButtonViewProxyTest.java`:

```java
package org.waveprotocol.wave.client.widget.toolbar.buttons;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;
import junit.framework.TestCase;

public final class ToolbarButtonViewProxyTest extends TestCase {
  // Plain JUnit3 stub; ToolbarButtonUi extends ToolbarButtonView in the current code.
  private static class RecordingView implements ToolbarButtonView {
    String action;

    @Override public void setState(State state) {}
    @Override public void setText(String text) {}
    @Override public void setVisualElement(Element element) {}
    @Override public void setTooltip(String tooltip) {}
    @Override public void setShowDropdownArrow(boolean showDropdown) {}
    @Override public void setShowDivider(boolean showDivider) {}
    @Override public void addDebugClass(String dc) {}
    @Override public void removeDebugClass(String dc) {}
    @Override public Widget hackGetWidget() { return null; }
    @Override public void setE2eAction(String action) { this.action = action; }
  }

  private static final class RecordingUi extends RecordingView implements ToolbarButtonUi {
    @Override public void setDown(boolean isDown) {}
    @Override public void setListener(Listener listener) {}
  }

  public void testE2eActionCopiesAndClearsAcrossDelegates() {
    RecordingView first = new RecordingView();
    ToolbarButtonViewProxy proxy = new ToolbarButtonViewProxy(first);

    proxy.setE2eAction("bold");
    assertEquals("bold", first.action);

    RecordingView second = new RecordingView();
    proxy.setDelegate(second);
    assertEquals("bold", second.action);

    proxy.setE2eAction(null);
    assertNull(second.action);

    RecordingView third = new RecordingView();
    third.action = "stale";
    proxy.setDelegate(third);
    assertNull(third.action);
  }

  public void testUiProxyForwardsE2eActionToDelegates() {
    RecordingUi first = new RecordingUi();
    ToolbarButtonUiProxy proxy = new ToolbarButtonUiProxy(first);

    proxy.setE2eAction("bold");
    assertEquals("bold", first.action);

    RecordingUi second = new RecordingUi();
    proxy.setDelegate(second);
    assertEquals("bold", second.action);

    proxy.setE2eAction(null);
    assertNull(second.action);

    RecordingUi third = new RecordingUi();
    third.action = "stale";
    proxy.setDelegate(third);
    assertNull(third.action);
  }
}
```

Run:

```bash
sbt --batch "testOnly org.waveprotocol.wave.client.widget.toolbar.buttons.ToolbarButtonViewProxyTest"
```

Expected: test exits `0`. If legacy SBT filtering cannot isolate the test, record the exact limitation in #1121 and rely on `sbt --batch compile` plus focused Playwright.

- [ ] **Step 6: Compile the changed Java contract**

Run:

```bash
sbt --batch compile
```

Expected: compilation exits `0`; any missing interface method errors must be fixed before E2E work continues. Keep this compile even though `j2clSearchTest` also compiles part of the tree, because this task changes legacy GWT toolbar interfaces outside the J2CL-focused test target.

---

### Task 4: Drive Full GWT Inline Reply In The Parity Spec

**Files:**
- Modify: `wave/src/e2e/j2cl-gwt-parity/pages/GwtPage.ts`
- Modify: `wave/src/e2e/j2cl-gwt-parity/tests/inline-reply-parity.spec.ts`

- [ ] **Step 1: Probe the live GWT editor and bold wrapper before coding final helpers**

Run the GWT baseline locally and inspect the live DOM after clicking Reply. Confirm these facts before writing the final helper:

```ts
const editorCandidates = await page.evaluate(() =>
  [
    '[kind="document"] [editabledocmarker="true"]',
    '[kind="document"] .wave-editor-on',
    '[kind="document"][contenteditable]',
    '[kind="document"] [contenteditable]'
  ].flatMap((selector) =>
    Array.from(document.querySelectorAll(selector)).map((node) => ({
      selector,
      tag: node.nodeName,
      text: (node.textContent || "").slice(0, 80),
      editable: (node as HTMLElement).getAttribute("contenteditable"),
      visible: !!((node as HTMLElement).offsetWidth || (node as HTMLElement).offsetHeight)
    }))
  )
);
```

Expected decision:

```text
Use the narrowest selector that points at the active reply editor after Reply.
Prefer the existing GwtPage selector list if it matches; otherwise document the observed selector in #1121 before implementing.
Confirm the union selector count and ordering; `.last()` is allowed only if the last candidate is the active reply editor after Reply.
```

Also verify whether selecting `world` with a programmatic `Range` changes the editor DOM after Bold. If not, use keyboard selection by making `world` the last word of the phrase and selecting it with `Shift+ArrowLeft`.

Record the probe outcome in #1121 before coding the final helpers:

```text
Live DOM probe:
- Active editor selector chosen: <selector or GwtPage method>
- Selection strategy: Range path accepted / keyboard fallback required
- Bold wrapper assertion: enabled with observed wrapper / omitted with residual-risk note
```

- [ ] **Step 2: Reuse GWT page-object shell sentinel and selector knowledge**

Keep `GwtPage.assertInboxLoaded()` in the GWT test as the route sentinel. Put reusable GWT selectors in `GwtPage.ts`; do not duplicate these selectors as local functions in the spec:

```ts
gwtBlips(): Locator {
  return this.page.locator("[kind='b'][data-blip-id]");
}

gwtActiveEditableDocument(): Locator {
  return this.page.locator(
    [
      '[kind="document"] [editabledocmarker="true"]',
      '[kind="document"] .wave-editor-on',
      '[kind="document"][contenteditable]',
      '[kind="document"] [contenteditable]'
    ].join(", ")
  ).last();
}
```

If the live probe requires a selector adjustment, update `GwtPage.ts` and keep the spec helpers calling these page-object methods.

- [ ] **Step 3: Replace the GWT baseline-only follow-up with full flow helpers**

Add helpers below the J2CL helpers. These helpers must call `GwtPage` locator methods instead of duplicating GWT selectors:

```ts
async function clickReplyOnFirstBlipGwt(gwt: GwtPage) {
  const firstBlip = gwt.gwtBlips().first();
  await expect(firstBlip, "GWT welcome wave must expose at least one rendered blip").toBeVisible({
    timeout: 15_000
  });
  await firstBlip.hover();
  const reply = firstBlip.locator("[data-e2e-action='reply']").first();
  await expect(reply, "GWT reply action must be reachable through a stable hook").toBeVisible({
    timeout: 15_000
  });
  await reply.click({ timeout: 10_000 });
  await expect(
    gwt.gwtActiveEditableDocument(),
    "GWT editor must open after Reply"
  ).toBeVisible({ timeout: 15_000 });
}

async function typeInComposerGwt(page: Page, gwt: GwtPage, phrase: string) {
  const editor = gwt.gwtActiveEditableDocument();
  await editor.click({ timeout: 10_000 });
  await editor.evaluate((el) => (el as HTMLElement).focus());
  await page.keyboard.insertText(phrase);
  await expect
    .poll(
      async () => editor.evaluate((el) => (el.textContent || "").trim()),
      { message: "GWT editor must contain the typed draft", timeout: 10_000 }
    )
    .toContain(phrase);
  return editor;
}

async function selectWordInGwtEditor(page: Page, gwt: GwtPage, word: string): Promise<boolean> {
  const editor = gwt.gwtActiveEditableDocument();
  const selected = await editor.evaluate((el, targetWord) => {
    (el as HTMLElement).focus();
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    let node = walker.nextNode();
    while (node) {
      const text = node.textContent || "";
      const index = text.indexOf(targetWord);
      if (index >= 0) {
        const range = document.createRange();
        range.setStart(node, index);
        range.setEnd(node, index + targetWord.length);
        const selection = window.getSelection();
        selection?.removeAllRanges();
        selection?.addRange(range);
        document.dispatchEvent(new Event("selectionchange", { bubbles: true }));
        return true;
      }
      node = walker.nextNode();
    }
    return false;
  }, word);
  if (!selected) {
    return false;
  }
  return await page.evaluate(
    (targetWord) => window.getSelection()?.toString() === targetWord,
    word
  );
}

async function selectTrailingWordWithKeyboard(page: Page, gwt: GwtPage, word: string) {
  const editor = gwt.gwtActiveEditableDocument();
  await editor.click({ timeout: 10_000 });
  await expect
    .poll(
      async () => await editor.evaluate((el) => (el.textContent || "").trim().endsWith(word)),
      { message: `GWT editor text must end with '${word}' before keyboard selection`, timeout: 5_000 }
    )
    .toBe(true);
  await page.keyboard.press(process.platform === "darwin" ? "Meta+ArrowRight" : "Control+ArrowRight");
  for (let i = 0; i < word.length; i += 1) {
    await page.keyboard.press("Shift+ArrowLeft");
  }
  await expect
    .poll(
      async () => await page.evaluate(() => window.getSelection()?.toString() || ""),
      { message: `GWT keyboard fallback must select trailing '${word}'`, timeout: 5_000 }
    )
    .toBe(word);
}

async function applyBoldToWordGwt(page: Page, gwt: GwtPage, word: string) {
  const selectedByRange = await selectWordInGwtEditor(page, gwt, word);
  if (!selectedByRange) {
    await selectTrailingWordWithKeyboard(page, gwt, word);
  }
  await expect
    .poll(
      async () => await page.evaluate(() => window.getSelection()?.toString() || ""),
      { message: `GWT selection must target '${word}' before Bold`, timeout: 5_000 }
    )
    .toBe(word);

  const bold = page.locator("[data-e2e-action='bold']").first();
  await expect(bold, "GWT bold toolbar action must be stable").toBeVisible({ timeout: 10_000 });
  await bold.click({ timeout: 10_000 });
}

async function finishInlineReplyGwt(page: Page, gwt: GwtPage, initialBlipCount: number, draftText: string) {
  const done = page.locator("[data-e2e-action='edit-done']").last();
  await expect(done, "GWT edit-done action must be stable").toBeVisible({ timeout: 10_000 });
  await done.click({ timeout: 10_000 });
  await expect
    .poll(
      async () => await gwt.gwtBlips().count(),
      { message: "GWT reply submit must add a new blip", timeout: 25_000 }
    )
    .toBeGreaterThan(initialBlipCount);
  await expect(
    gwt.gwtBlips().filter({ hasText: draftText }).last(),
    "the newly submitted GWT reply blip must carry the draft text"
  ).toBeVisible({ timeout: 20_000 });
}
```

If live DOM probing shows the active editor is not reachable by the existing selector list, replace `GwtPage.gwtActiveEditableDocument()` with the narrowest stable selector discovered from the GWT DOM, and record the reason in the issue comment.

- [ ] **Step 4: Replace the GWT test body after the welcome-wave assertion**

Remove the follow-up annotation and baseline-only participant toolbar assertion from the existing GWT test. Specifically delete the `test.info().annotations.push({ type: "follow-up", ... "Full GWT bold-and-send drive tracked at #1121." })` block and the final participant-control assertion using `page.locator("[aria-label*='participant']").first()`. Use this flow instead:

```ts
    const phrase = `hello ${Date.now().toString(36)} world`;
    await expect(
      gwt.gwtBlips().filter({ hasText: phrase }),
      "unique reply payload must not already exist in the welcome wave"
    ).toHaveCount(0);
    const initialBlipCount = await gwt.gwtBlips().count();
    await clickReplyOnFirstBlipGwt(gwt);
    await typeInComposerGwt(page, gwt, phrase);
    await applyBoldToWordGwt(page, gwt, "world");
    await finishInlineReplyGwt(page, gwt, initialBlipCount, phrase);
```

- [ ] **Step 5: Treat bold-wrapper assertion as contingent on live wrapper proof**

Mandatory bold proof for #1121 is: the test selects `world`, confirms the selection is `world`, clicks the real `[data-e2e-action='bold']` toolbar action, and then submits a new blip. Do not add a brittle wrapper assertion unless the Task 4 Step 1 probe proves the GWT editor emits a stable wrapper. If it does, use this broadened matcher:

```ts
  const editorHtml = await gwt.gwtActiveEditableDocument().evaluate((el) => el.innerHTML);
  const boldMatcher = /<(strong|b)\b[^>]*>world<\/\1>|<span\b[^>]*(?:font-weight\s*:\s*(?:bold|[6-9]00)|fontWeight\s*:\s*(?:bold|[6-9]00))[^>]*>world<\/span>/i;
  expect(boldMatcher.test(editorHtml), `GWT editor must wrap 'world' as bold; saw: ${editorHtml}`).toBe(true);
```

If GWT serializes formatting into non-obvious internal spans before submit, omit this wrapper assertion and record the observed wrapper shape or absence in #1121 as residual test risk.

The selection-success check in `applyBoldToWordGwt` is required. It deterministically triggers the keyboard fallback before the Bold click when the programmatic Range path is not honored by GWT.

- [ ] **Step 6: Run TypeScript validation**

Run:

```bash
cd wave/src/e2e/j2cl-gwt-parity
npx tsc --noEmit
```

Expected: TypeScript exits `0`.

---

### Task 5: Add Changelog Fragment And Run Full Local Verification

**Files:**
- Add: `wave/config/changelog.d/2026-04-29-gwt-inline-reply-e2e-parity.json`
- Modify: generated `wave/config/changelog.json` only through `scripts/assemble-changelog.py`

- [ ] **Step 1: Add changelog fragment**

Create:

```json
{
  "date": "2026-04-29",
  "type": "fixed",
  "title": "Covered legacy GWT inline replies in the J2CL parity harness",
  "body": "The parity E2E suite now drives the legacy GWT inline reply, bold toolbar, and send path with stable selectors so J2CL/GWT compose parity can be checked end to end."
}
```

- [ ] **Step 2: Assemble and validate changelog**

Run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
```

Expected: validation exits `0`.

- [ ] **Step 3: Run local server sanity**

Run from the worktree:

```bash
bash scripts/worktree-boot.sh --port 9931
PORT=9931 bash scripts/wave-smoke.sh start
PORT=9931 bash scripts/wave-smoke.sh check
```

Expected: server starts and `wave-smoke.sh check` exits `0`.

- [ ] **Step 4: Run focused parity Playwright**

Run:

```bash
cd wave/src/e2e/j2cl-gwt-parity
CI=true WAVE_E2E_BASE_URL=http://127.0.0.1:9931 npx playwright test tests/inline-reply-parity.spec.ts --project=chromium
```

Expected: J2CL and GWT tests both pass.

- [ ] **Step 5: Run SBT-only repo gates**

Run:

```bash
sbt --batch j2clSearchTest
sbt --batch compile
git diff --check
```

Expected: each command exits `0`.

- [ ] **Step 6: Stop local server**

Run:

```bash
PORT=9931 bash scripts/wave-smoke.sh stop
```

Expected: server stops without killing unrelated lane ports.

---

### Task 6: Review, Issue Evidence, PR, And Monitor

**Files:**
- No new files unless review requires fixes.

- [ ] **Step 1: Run direct self-review**

Checklist:

```text
- No J2CL behavior changes.
- GWT menu action codes and MenuController behavior unchanged.
- Toolbar data action metadata survives proxy delegate changes.
- GWT E2E no longer contains #1121 follow-up annotation.
- GWT E2E asserts the GWT shell sentinel before clicking Reply.
- GWT E2E drives real Reply, Bold, and Done/Send path.
- GWT E2E proves a new blip count increase and phrase visibility inside a GWT blip.
- GWT selectors live in `GwtPage.ts`; the spec does not duplicate the selector union.
- Bold selection is verified before clicking Bold; keyboard fallback is used if Range selection is not honored.
- Temporary Task 1 RED helper is deleted; final spec contains only Task 4 helpers.
- Live DOM probe outcome is recorded in #1121 before final implementation proceeds.
- `ToolbarButtonViewProxy.setDelegate(...)` remains the public delegate-swap API, and its private `copyInto(...)` path propagates `setE2eAction` unconditionally.
- `ToolbarButtonUiProxy.setDelegate(...)` also forwards `setE2eAction` through its inner `ToolbarButtonViewProxy`, so the marker survives top-level and overflow delegate swaps.
- Verification commands and outputs are captured for issue comments.
```

- [ ] **Step 2: Run Claude Opus implementation review**

Run:

```bash
export REVIEW_TASK="Issue #1121 GWT inline reply E2E parity"
export REVIEW_GOAL="Ensure the GWT parity harness drives the same inline reply + bold + send flow as J2CL without production behavior drift."
export REVIEW_ACCEPTANCE=$'- GWT test clicks stable Reply, Bold, and Done hooks\n- J2CL half still passes\n- No product behavior changes beyond inert DOM test hooks\n- SBT-only verification passes'
export REVIEW_RUNTIME="GWT Java + Playwright + SBT"
export REVIEW_RISKY="Legacy GWT editor DOM, toolbar proxy metadata, parity E2E flake risk"
export REVIEW_TEST_COMMANDS="<fill with exact commands run>"
export REVIEW_TEST_RESULTS="<fill with exact pass/fail results>"
export REVIEW_TEMPLATE="task"
export REVIEW_DIFF_SPEC="$(git merge-base origin/main HEAD)..HEAD"
/Users/vega/.codex/skills/public/claude-review/scripts/review_task.sh
```

Address all blockers and important comments, then rerun the review until there are no required follow-ups.

- [ ] **Step 3: Commit and push**

Use narrow commits:

```bash
git add docs/superpowers/plans/2026-04-29-issue-1121-gwt-inline-reply-e2e-parity.md \
  wave/src/e2e/j2cl-gwt-parity/pages/GwtPage.ts \
  wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/BlipMetaViewBuilder.java \
  wave/src/test/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/BlipViewBuilderTest.java \
  wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonView.java \
  wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/AbstractToolbarButton.java \
  wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonViewProxy.java \
  wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonUiProxy.java \
  wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/HorizontalToolbarButtonWidget.java \
  wave/src/main/java/org/waveprotocol/wave/client/widget/toolbar/buttons/VerticalToolbarButtonWidget.java \
  wave/src/test/java/org/waveprotocol/wave/client/widget/toolbar/buttons/ToolbarButtonViewProxyTest.java \
  wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java \
  wave/src/e2e/j2cl-gwt-parity/tests/inline-reply-parity.spec.ts \
  wave/config/changelog.d/2026-04-29-gwt-inline-reply-e2e-parity.json \
  wave/config/changelog.json
git commit -m "test(g-port-4): automate GWT inline reply parity"
git push -u origin codex/g-port-4-gwt-inline-reply-20260429
```

- [ ] **Step 4: Update issues and open PR**

Add a #1121 comment containing:

```text
Plan: docs/superpowers/plans/2026-04-29-issue-1121-gwt-inline-reply-e2e-parity.md
Worktree: /Users/vega/devroot/worktrees/g-port-4-gwt-inline-reply-20260429
Branch: codex/g-port-4-gwt-inline-reply-20260429
Verification:
- <exact command> -> <result>
Review:
- Claude Opus final round: <summary>
```

Open a PR against `main` that references `#1121` and parent `#904`, then enable auto-merge if checks allow it.

- [ ] **Step 5: Monitor until merged**

Use GitHub GraphQL review threads as the source of truth. Required closeout state:

```text
- PR merged.
- Review threads: 0 unresolved.
- #1121 has final merge evidence.
- #904 has concise lane completion update.
```

## Self-Review

Spec coverage:
- Issue task 1 is covered by Task 2: `REPLY` gets `data-e2e-action="reply"` directly in the GWT menu builder.
- Issue task 2 is covered by Task 3: Bold gets `data-e2e-action="bold"` through the toolbar abstraction, not a one-off current-delegate mutation.
- Issue task 3 is covered by Task 4: the GWT spec path drives reply, type, bold, finish, and new-blip visibility.
- The need to finish/send the GWT edit is not explicitly listed in the issue tasks but is required by the stated "bold + send" goal; Task 2 adds `EDIT_DONE` as the minimum stable hook for that.

Placeholder scan:
- No placeholder implementation steps remain. Runtime-dependent review result placeholders are explicitly filled during Task 6 with actual execution evidence.

Type and selector consistency:
- Java selectors use `data-e2e-action` consistently for GWT menu and toolbar hooks.
- Playwright helper names use the existing J2CL naming pattern with `Gwt` suffixes.
- The plan avoids relying on obfuscated CssResource classes or debug-class `dc` attributes, because debug classes are disabled unless explicitly turned on.
- GWT blip selectors use the existing `[kind='b'][data-blip-id]` contract from G-PORT-3, and the GWT route sentinel uses the existing `GwtPage.assertInboxLoaded()` checks.
- `GwtPage.ts` is the single home for reusable GWT selectors; the spec helpers accept a `GwtPage` instance.

Risk notes:
- The active GWT editor selector must be verified by the required live DOM probe before final helper implementation. The plan defaults to the selector list already documented in `GwtPage.ts`.
- GWT may serialize bold formatting through internal spans rather than obvious `<b>` or `<strong>`. The mandatory assertion remains that the real Bold control is clicked and the reply is submitted as a new blip; wrapper-shape assertion is added only if live DOM proves it stable.
- If programmatic Range selection is not honored by the GWT editor, the fallback keyboard-selection path uses a trailing `world` token so the selection can be driven by real key events.
- `applyBoldToWordGwt` must assert `window.getSelection().toString() === "world"` before clicking Bold, either through the Range path or the keyboard fallback.
- Current production scope only labels Bold and never clears it, but the proxy tests still cover null clearing so the interface remains safe for future callers.
