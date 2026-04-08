# Wave Panel Toolbar Icon Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all text buttons in the wave panel toolbar with compact SVG icons, reorganize into three logical groups, and merge the Archive/Inbox buttons into a single stateful toggle.

**Architecture:** Add inline SVG constants + `createSvgIcon()` helper to `ViewToolbar.java` (same pattern as `SearchPresenter`). Rewrite `init()` into three `toolbarUi.addGroup()` sections. Archive toggle mirrors the existing pin toggle: `ToolbarClickButton` + `setDown()` for visual state + async `folderService.execute()` with disable-during-request. Pass `isArchived` from `StageThree` (reads supplement) and wire it in `StagesProvider` (mirrors existing `wirePinState()`).

**Tech Stack:** GWT 2.x, Java 8, Apache Wave `ToolbarButtonView`/`ToolbarClickButton`, SVG inline strings, `sbt compile` for type-check (no GWT unit tests for DOM widgets)

---

## File Map

| File | Change |
|------|--------|
| `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java` | SVG constants, `createSvgIcon()`, rewrite `init()`, add `archived`/`archiveButton` fields, `toggleArchive()`, `updateArchiveButtonState()`, `setArchived()`, update `togglePin()`, rename `updatePinButtonLabel()→updatePinButtonState()`, add `isArchived` constructor/factory param, remove dead `moveToFolder()` |
| `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/i18n/ToolbarMessages.java` | Fix `@DefaultMessage("NextUnread")` typo |
| `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java` | Pass `isArchived` to `ViewToolbar.create()` |
| `wave/src/main/java/org/waveprotocol/box/webclient/client/StagesProvider.java` | Add `wireArchiveState()`, call it from `onStageThreeLoaded()` |

---

## Task 1: Fix i18n typo

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/i18n/ToolbarMessages.java:33`

- [ ] **Step 1: Fix `@DefaultMessage` typo for `nextUnread`**

In `ToolbarMessages.java`, change line 33:
```java
// Before
@DefaultMessage("NextUnread")
String nextUnread();

// After
@DefaultMessage("Next Unread")
String nextUnread();
```

- [ ] **Step 2: Compile to verify**

```bash
cd /Users/vega/devroot/worktrees/fix-wave-toolbar-icons
sbt "project wave" "compile"
```
Expected: `[success]`

- [ ] **Step 3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/i18n/ToolbarMessages.java
git commit -m "fix(toolbar): correct NextUnread i18n default message typo"
```

---

## Task 2: Add `isArchived` constructor parameter and public `setArchived()` to ViewToolbar

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java`

This task adds plumbing only (no visual changes yet). The full `init()` rewrite is in Task 3.

- [ ] **Step 1: Add new fields after `pinned`**

In `ViewToolbar.java`, after line 83 (`private boolean pinned = false;`), add:

```java
/** Whether the currently open wave is archived. */
private boolean archived = false;

/** Reference to the archive button for state toggling. */
private ToolbarClickButton archiveButton;
```

- [ ] **Step 2: Add `initiallyArchived` constructor parameter**

Update the private constructor signature (currently at line 85) from:
```java
private ViewToolbar(ToplevelToolbarWidget toolbarUi, FocusFramePresenter focusFrame,
    ModelAsViewProvider views, ConversationView wave, Reader reader, WaveId waveId,
    boolean initiallyPinned, ParticipantId signedInUser) {
  this.toolbarUi = toolbarUi;
  this.focusFrame = focusFrame;
  this.reader = reader;
  this.waveId = waveId;
  this.folderService = new FolderOperationServiceImpl();
  this.pinned = initiallyPinned;
```
to:
```java
private ViewToolbar(ToplevelToolbarWidget toolbarUi, FocusFramePresenter focusFrame,
    ModelAsViewProvider views, ConversationView wave, Reader reader, WaveId waveId,
    boolean initiallyPinned, boolean initiallyArchived, ParticipantId signedInUser) {
  this.toolbarUi = toolbarUi;
  this.focusFrame = focusFrame;
  this.reader = reader;
  this.waveId = waveId;
  this.folderService = new FolderOperationServiceImpl();
  this.pinned = initiallyPinned;
  this.archived = initiallyArchived;
```

- [ ] **Step 3: Update primary factory method and backward-compat overloads**

Replace all four `create()` factory methods (lines 115–133) with:

```java
public static ViewToolbar create(FocusFramePresenter focus, ModelAsViewProvider views,
    ConversationView wave, Reader reader, WaveId waveId, boolean isPinned, boolean isArchived,
    ParticipantId signedInUser) {
  return new ViewToolbar(new ToplevelToolbarWidget(), focus, views, wave, reader, waveId,
      isPinned, isArchived, signedInUser);
}

public static ViewToolbar create(FocusFramePresenter focus, ModelAsViewProvider views,
    ConversationView wave, Reader reader, WaveId waveId, boolean isPinned,
    ParticipantId signedInUser) {
  return create(focus, views, wave, reader, waveId, isPinned, false, signedInUser);
}

public static ViewToolbar create(FocusFramePresenter focus, ModelAsViewProvider views,
    ConversationView wave, Reader reader, WaveId waveId, boolean isPinned) {
  return create(focus, views, wave, reader, waveId, isPinned, false, null);
}

/**
 * Overload for backward compatibility (assumes not pinned, not archived).
 */
public static ViewToolbar create(FocusFramePresenter focus, ModelAsViewProvider views,
    ConversationView wave, Reader reader, WaveId waveId) {
  return create(focus, views, wave, reader, waveId, false, false, null);
}
```

- [ ] **Step 4: Add `setArchived()` public method**

After the existing `setPinned()` method (around line 326), add:

```java
/**
 * Sets the archive state of the currently displayed wave. Called from
 * StagesProvider after the wave is loaded so the button visual is correct.
 */
public void setArchived(boolean archived) {
  this.archived = archived;
  updateArchiveButtonState();
}
```

- [ ] **Step 5: Compile to verify**

```bash
sbt "project wave" "compile"
```
Expected: `[success]`

- [ ] **Step 6: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java
git commit -m "feat(toolbar): add isArchived constructor param and setArchived() to ViewToolbar"
```

---

## Task 3: Rewrite ViewToolbar.init() with SVG icons and three groups

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java`

This is the main change. It replaces the entire `init()` method, removes the dead `moveToFolder()` method, adds SVG constants, adds `createSvgIcon()`, adds `toggleArchive()`, `updateArchiveButtonState()`, renames `updatePinButtonLabel()` to `updatePinButtonState()`, and updates `togglePin()`.

- [ ] **Step 1: Add GWT DOM import**

Verify `com.google.gwt.user.client.DOM` is imported. The existing imports in `ViewToolbar.java` start at line 22. Add if not present:
```java
import com.google.gwt.user.client.DOM;
import com.google.gwt.dom.client.Element;
```
(Check first — `Element` may already be imported via other toolbar button imports. `DOM` needs to be explicitly imported.)

- [ ] **Step 2: Add SVG constants after the `messages` static field (line 53)**

Insert after `private final static ToolbarMessages messages = GWT.create(ToolbarMessages.class);`:

```java
// Inline SVG icon constants (Lucide icon set, MIT license).
// Explicit close tags used for GWT HTML-parser compatibility.
private static final String SVG_OPEN =
    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" "
    + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
    + "stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">";

/** Recent: rotate-ccw — counterclockwise arrow around a clock face. */
private static final String ICON_RECENT = SVG_OPEN
    + "<polyline points=\"1 4 1 10 7 10\"></polyline>"
    + "<path d=\"M3.51 15a9 9 0 1 0 .49-3.51\"></path></svg>";

/** Next Unread: bell — notification alert. */
private static final String ICON_NEXT_UNREAD = SVG_OPEN
    + "<path d=\"M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9\"></path>"
    + "<path d=\"M13.73 21a2 2 0 01-3.46 0\"></path></svg>";

/** Previous: chevron-up. */
private static final String ICON_PREV = SVG_OPEN
    + "<polyline points=\"18 15 12 9 6 15\"></polyline></svg>";

/** Next: chevron-down. */
private static final String ICON_NEXT = SVG_OPEN
    + "<polyline points=\"6 9 12 15 18 9\"></polyline></svg>";

/** Last: chevrons-down — double downward chevron. */
private static final String ICON_LAST = SVG_OPEN
    + "<polyline points=\"7 13 12 18 17 13\"></polyline>"
    + "<polyline points=\"7 6 12 11 17 6\"></polyline></svg>";

/** Prev @: chevron-left + at-sign (composite). */
private static final String ICON_PREV_MENTION = SVG_OPEN
    + "<polyline points=\"9 7 5 12 9 17\"></polyline>"
    + "<g transform=\"translate(8,0)\">"
    + "<circle cx=\"8\" cy=\"12\" r=\"3.5\"></circle>"
    + "<path d=\"M11.5 9.5v2.5a1.5 1.5 0 01-3 0V11a3.5 3.5 0 10-1.37 2.78\"></path>"
    + "</g></svg>";

/** Next @: at-sign + chevron-right (composite). */
private static final String ICON_NEXT_MENTION = SVG_OPEN
    + "<g transform=\"translate(-2,0)\">"
    + "<circle cx=\"8\" cy=\"12\" r=\"3.5\"></circle>"
    + "<path d=\"M11.5 9.5v2.5a1.5 1.5 0 01-3 0V11a3.5 3.5 0 10-1.37 2.78\"></path>"
    + "</g>"
    + "<polyline points=\"15 7 19 12 15 17\"></polyline></svg>";

/** Archive: archive box. */
private static final String ICON_ARCHIVE = SVG_OPEN
    + "<polyline points=\"21 8 21 21 3 21 3 8\"></polyline>"
    + "<rect x=\"1\" y=\"3\" width=\"22\" height=\"5\"></rect>"
    + "<line x1=\"10\" y1=\"12\" x2=\"14\" y2=\"12\"></line></svg>";

/** Pin: thumbtack. */
private static final String ICON_PIN = SVG_OPEN
    + "<line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"22\"></line>"
    + "<path d=\"M5 17h14v-1.76a2 2 0 00-1.11-1.79l-1.78-.9A2 2 0 0115 10.76V6h1"
    + " a2 2 0 000-4H8a2 2 0 000 4h1v4.76a2 2 0 01-1.11 1.79l-1.78.9A2 2 0 005"
    + " 15.24z\"></path></svg>";

/** History: clock face. */
private static final String ICON_HISTORY = SVG_OPEN
    + "<circle cx=\"12\" cy=\"12\" r=\"10\"></circle>"
    + "<polyline points=\"12 6 12 12 16 14\"></polyline></svg>";
```

- [ ] **Step 3: Add `createSvgIcon()` helper method**

Add as a `private static` method before `init()`:

```java
/**
 * Creates an icon element from inline SVG markup for use in toolbar buttons.
 */
private static Element createSvgIcon(String svgHtml) {
  com.google.gwt.dom.client.Element wrapper = DOM.createDiv();
  wrapper.setInnerHTML(svgHtml);
  return wrapper;
}
```

- [ ] **Step 4: Replace the entire `init()` method**

Replace the existing `init()` method (lines 135–254) with:

```java
public void init() {
  // Group 1 — Navigation
  ToolbarView group = toolbarUi.addGroup();

  new ToolbarButtonViewBuilder()
      .setTooltip(messages.recent())
      .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
        @Override
        public void onClicked() {
          focusActions.focusMostRecentlyModified();
        }
      }).setVisualElement(createSvgIcon(ICON_RECENT));

  new ToolbarButtonViewBuilder()
      .setTooltip(messages.nextUnread())
      .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
        @Override
        public void onClicked() {
          focusActions.focusNextUnread();
        }
      }).setVisualElement(createSvgIcon(ICON_NEXT_UNREAD));

  new ToolbarButtonViewBuilder()
      .setTooltip(messages.previous())
      .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
        @Override
        public void onClicked() {
          focusFrame.moveUp();
        }
      }).setVisualElement(createSvgIcon(ICON_PREV));

  new ToolbarButtonViewBuilder()
      .setTooltip(messages.next())
      .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
        @Override
        public void onClicked() {
          focusFrame.moveDown();
        }
      }).setVisualElement(createSvgIcon(ICON_NEXT));

  new ToolbarButtonViewBuilder()
      .setTooltip(messages.lastTooltip())
      .applyTo(group.addClickButton(), new ToolbarClickButton.Listener() {
        @Override
        public void onClicked() {
          BlipView lastBlip = blipSelector.selectLast();
          if (lastBlip != null) {
            focusFrame.focus(lastBlip);
          }
        }
      }).setVisualElement(createSvgIcon(ICON_LAST));

  // Group 2 — Mentions (only when signed-in user is available)
  if (mentionFocusOrder != null) {
    ToolbarView mentionsGroup = toolbarUi.addGroup();
    new ToolbarButtonViewBuilder()
        .setTooltip(messages.prevMention())
        .applyTo(mentionsGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            BlipView current = focusFrame.getFocusedBlip();
            if (current != null) {
              BlipView prev = mentionFocusOrder.getPrevious(current);
              if (prev != null) {
                focusFrame.focus(prev);
              }
            }
          }
        }).setVisualElement(createSvgIcon(ICON_PREV_MENTION));

    new ToolbarButtonViewBuilder()
        .setTooltip(messages.nextMention())
        .applyTo(mentionsGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            BlipView current = focusFrame.getFocusedBlip();
            if (current != null) {
              BlipView next = mentionFocusOrder.getNext(current);
              if (next != null) {
                focusFrame.focus(next);
              }
            }
          }
        }).setVisualElement(createSvgIcon(ICON_NEXT_MENTION));
  }

  // Group 3 — Actions (archive toggle, pin toggle, history)
  if (waveId != null) {
    ToolbarView actionsGroup = toolbarUi.addGroup();

    archiveButton = new ToolbarButtonViewBuilder()
        .applyTo(actionsGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            toggleArchive();
          }
        });
    archiveButton.setVisualElement(createSvgIcon(ICON_ARCHIVE));
    updateArchiveButtonState();

    pinButton = new ToolbarButtonViewBuilder()
        .applyTo(actionsGroup.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            togglePin();
          }
        });
    pinButton.setVisualElement(createSvgIcon(ICON_PIN));
    updatePinButtonState();
  }

  // History button (always shown; visibility toggled externally via setHistoryButtonVisible)
  ToolbarView historyGroup = toolbarUi.addGroup();
  historyButton = new ToolbarButtonViewBuilder()
      .setTooltip(messages.historyTooltip())
      .applyTo(historyGroup.addClickButton(), new ToolbarClickButton.Listener() {
        @Override
        public void onClicked() {
          if (historyButtonListener != null) {
            historyButtonListener.onClicked();
          }
        }
      });
  historyButton.setVisualElement(createSvgIcon(ICON_HISTORY));
}
```

- [ ] **Step 5: Add `toggleArchive()` and `updateArchiveButtonState()` methods**

After the existing `moveToFolder()` method, add:

```java
/**
 * Toggles the archive state of the currently open wave.
 * Moves the wave to Archive (if in inbox) or back to Inbox (if archived).
 */
private void toggleArchive() {
  final String targetFolder = archived
      ? FolderOperationBuilder.FOLDER_INBOX
      : FolderOperationBuilder.FOLDER_ARCHIVE;
  final boolean newArchivedState = !archived;
  String url = new FolderOperationBuilderImpl()
      .addParameter(FolderOperationBuilder.PARAM_OPERATION, FolderOperationBuilder.OPERATION_MOVE)
      .addParameter(FolderOperationBuilder.PARAM_FOLDER, targetFolder)
      .addParameter(FolderOperationBuilder.PARAM_WAVE_ID, waveId.serialise())
      .getUrl();
  LOG.trace().log(newArchivedState ? "Archiving" : "Moving to inbox", " wave ", waveId.serialise());
  archiveButton.setState(ToolbarButtonView.State.DISABLED);
  folderService.execute(url, new FolderOperationService.Callback() {
    @Override
    public void onSuccess() {
      LOG.trace().log("Successfully moved wave to: ", targetFolder);
      if (folderActionListener != null) {
        folderActionListener.onFolderActionCompleted(targetFolder);
      }
      // Note: folderActionListener navigates away (History.newItem), so no
      // need to re-enable or update local state on success.
    }

    @Override
    public void onFailure(String message) {
      archiveButton.setState(ToolbarButtonView.State.ENABLED);
      LOG.error().log("Failed to move wave to ", targetFolder, ": ", message);
    }
  });
}

/**
 * Updates the archive button visual state and tooltip to reflect the current
 * archived/inbox state.
 */
private void updateArchiveButtonState() {
  if (archiveButton != null) {
    archiveButton.getButton().setDown(archived);
    archiveButton.setTooltip(archived ? messages.toInbox() : messages.toArchive());
  }
}
```

- [ ] **Step 6: Remove `moveToFolder()` (now dead code)**

Delete the entire `moveToFolder()` method (which previously handled the old "To Archive" and "To Inbox" buttons). It was only ever called from the old `init()`, which is now replaced.

- [ ] **Step 7: Rename `updatePinButtonLabel()` to `updatePinButtonState()` and switch from setText to setDown**

Replace the existing `updatePinButtonLabel()` method:
```java
// BEFORE
private void updatePinButtonLabel() {
  if (pinButton != null) {
    pinButton.setText(pinned ? messages.unpin() : messages.pin());
  }
}
```
with:
```java
/**
 * Updates the pin button visual state (pressed/unpressed) and tooltip to
 * reflect the current pin state. Uses setDown() instead of setText() since
 * the button is now icon-only.
 */
private void updatePinButtonState() {
  if (pinButton != null) {
    pinButton.getButton().setDown(pinned);
    pinButton.setTooltip(pinned ? messages.unpin() : messages.pin());
  }
}
```

- [ ] **Step 8: Update `togglePin()` to call `updatePinButtonState()` instead of `updatePinButtonLabel()`**

In `togglePin()` (around line 285), find the success callback and change:
```java
// BEFORE
pinned = newPinState;
updatePinButtonLabel();
pinButton.setState(ToolbarButtonView.State.ENABLED);

// AFTER
pinned = newPinState;
updatePinButtonState();
pinButton.setState(ToolbarButtonView.State.ENABLED);
```

- [ ] **Step 9: Update `setPinned()` to call `updatePinButtonState()` instead of `updatePinButtonLabel()`**

In `setPinned()` (around line 326), change:
```java
// BEFORE
public void setPinned(boolean pinned) {
  this.pinned = pinned;
  updatePinButtonLabel();
}

// AFTER
public void setPinned(boolean pinned) {
  this.pinned = pinned;
  updatePinButtonState();
}
```

- [ ] **Step 10: Compile to verify**

```bash
sbt "project wave" "compile"
```
Expected: `[success]` — if there are missing imports, add `import com.google.gwt.user.client.DOM;` and `import com.google.gwt.dom.client.Element;` at the top of `ViewToolbar.java`.

- [ ] **Step 11: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java
git commit -m "feat(toolbar): replace text buttons with SVG icons, add archive toggle, reorganize groups"
```

---

## Task 4: Pass `isArchived` from StageThree

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java:189-195`

- [ ] **Step 1: Update `createViewToolbar()` to pass `isArchived`**

In `StageThree.DefaultProvider.createViewToolbar()` (around line 189), replace:
```java
protected ViewToolbar createViewToolbar() {
  ModelAsViewProvider views = stageTwo.getModelAsViewProvider();
  ConversationView wave = stageTwo.getConversations();
  boolean isPinned = stageTwo.getSupplement().isPinned();
  ParticipantId signedInUser = stageTwo.getSignedInUser();
  return ViewToolbar.create(stageTwo.getStageOne().getFocusFrame(), views, wave,
      stageTwo.getReader(), stageTwo.getWave().getWaveId(), isPinned, signedInUser);
}
```
with:
```java
protected ViewToolbar createViewToolbar() {
  ModelAsViewProvider views = stageTwo.getModelAsViewProvider();
  ConversationView wave = stageTwo.getConversations();
  boolean isPinned = stageTwo.getSupplement().isPinned();
  boolean isArchived = stageTwo.getSupplement().isArchived();
  ParticipantId signedInUser = stageTwo.getSignedInUser();
  return ViewToolbar.create(stageTwo.getStageOne().getFocusFrame(), views, wave,
      stageTwo.getReader(), stageTwo.getWave().getWaveId(), isPinned, isArchived, signedInUser);
}
```

- [ ] **Step 2: Compile to verify**

```bash
sbt "project wave" "compile"
```
Expected: `[success]`

- [ ] **Step 3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/StageThree.java
git commit -m "feat(toolbar): pass isArchived from supplement to ViewToolbar in StageThree"
```

---

## Task 5: Wire archive state in StagesProvider

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/client/StagesProvider.java:207-225`

- [ ] **Step 1: Add `wireArchiveState()` method**

In `StagesProvider.java`, after the `wirePinState()` method (around line 259), add:

```java
/**
 * Sets the initial archive state on the view toolbar so the Archive button
 * visual is correct when the wave first opens.
 */
private void wireArchiveState(StageThree three) {
  ViewToolbar viewToolbar = three.getViewToolbar();
  try {
    boolean archived = two.getSupplement().isArchived();
    viewToolbar.setArchived(archived);
  } catch (Exception e) {
    // Supplement may not be available for all waves; default to not archived.
  }
}
```

- [ ] **Step 2: Call `wireArchiveState()` from `onStageThreeLoaded()`**

In `onStageThreeLoaded()` (around line 220), after `wirePinState(x);`, add the new call:
```java
wireToolbarButtons(x);
wirePinState(x);
wireArchiveState(x);   // <-- add this line
install();
wireHistoryMode();
```

- [ ] **Step 3: Compile to verify**

```bash
sbt "project wave" "compile"
```
Expected: `[success]`

- [ ] **Step 4: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/webclient/client/StagesProvider.java
git commit -m "feat(toolbar): wire initial archive state to ViewToolbar in StagesProvider"
```

---

## Task 6: Build, push, and open PR

- [ ] **Step 1: Full compile to confirm no errors**

```bash
sbt "project wave" "compile"
```
Expected: `[success]`

- [ ] **Step 2: Push branch**

```bash
git push -u origin fix/wave-toolbar-action-icons
```

- [ ] **Step 3: Open PR**

```bash
gh pr create \
  --title "feat(toolbar): replace text buttons with SVG icons and add archive toggle" \
  --body "$(cat <<'EOF'
## Summary

- Replaces all text labels in the wave panel view toolbar with compact Lucide SVG icons (16×16) with hover tooltips
- Reorganises toolbar into three logical groups: Navigation | Mentions | Actions
- Merges the separate "To Archive" and "To Inbox" buttons into a single stateful archive toggle (icon highlighted when wave is archived, `setDown()` visual state)
- Pin button migrated from text labels to icon + `setDown()` visual state
- `isArchived` initial state wired from supplement in both `StageThree` and `StagesProvider`
- Removes dead `moveToFolder()` method (replaced by `toggleArchive()`)
- Fixes `@DefaultMessage("NextUnread")` typo in `ToolbarMessages`

## Test plan

- [ ] Open a wave: toolbar should show icon buttons with correct tooltips on hover
- [ ] Navigate with Previous/Next/Last buttons to verify focus movement
- [ ] Open a wave with unread content: Next Unread bell icon navigates to unread blip
- [ ] Recent (rotate-ccw) icon jumps to most-recently-modified blip
- [ ] Mention buttons (Prev @, Next @) only visible when signed in; navigate between @mentions
- [ ] Archive icon not highlighted for inbox wave → click → wave closes (navigates to search)
- [ ] Re-open the same wave (now in archive): archive icon highlighted (setDown=true)
- [ ] Click archive icon again → moves back to inbox, wave closes
- [ ] Pin icon not highlighted for unpinned wave → click → icon becomes highlighted
- [ ] History icon shows/hides based on ownership (existing behavior unchanged)
- [ ] Overflow ("...") menu still works when toolbar is narrow

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review Checklist

- [x] **Spec coverage**: All three groups ✓, icon-only buttons ✓, tooltips ✓, archive toggle ✓, pin toggle icon ✓, group separators (auto-divider from `GroupingToolbar`) ✓, `isArchived` wired from supplement ✓
- [x] **No placeholders**: All code blocks are complete
- [x] **Type consistency**: `archiveButton` typed as `ToolbarClickButton` throughout; `archiveButton.getButton().setDown()` uses `ToolbarButtonUi.setDown()` ✓; `archiveButton.setTooltip()` via `AbstractToolbarButton → ToolbarButtonView` ✓; `updateArchiveButtonState()` and `updatePinButtonState()` match their call sites ✓
