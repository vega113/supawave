# Wave Panel Toolbar Icon Redesign

**Date:** 2026-04-08  
**Branch:** fix/wave-toolbar-action-icons  
**Status:** Approved

---

## Problem

The ViewToolbar renders text labels in a single group:

```
Recent | Next Unread | Prev @ | Next @ | Previous | Next | Last | To Archive | To Inbox | Pin | History
```

This wastes horizontal space and has poor visual hierarchy. Two separate folder buttons (Archive/Inbox) do the inverse of the same operation.

---

## Goal

Replace all text buttons with compact SVG action icons (Lucide-style, 16×16) with hover tooltips. Reorganize into three logical groups with visual separators. Merge Archive/Inbox into one stateful toggle button.

---

## Architecture

No new files. Four targeted file changes:

| File | Change |
|------|--------|
| `ViewToolbar.java` | SVG constants, `createSvgIcon()`, new `init()` layout, archive toggle state |
| `StageThree.java` | Pass `isArchived` to `ViewToolbar.create()` |
| `StagesProvider.java` | `wireArchiveState()` method to set initial archive visual state |
| `ToolbarMessages.java` + `_en.properties` | Tooltip messages for archive, fix `"NextUnread"` typo |

---

## Button Groups

### Group 1 — Navigation
| Action | Icon | Tooltip |
|--------|------|---------|
| Recent | clock (rotate-ccw) | "Recent" |
| Next Unread | bell | "Next Unread" |
| Previous | chevron-up | "Previous" |
| Next | chevron-down | "Next" |
| Last | chevrons-down | "Go to last message (End)" |

### Group 2 — Mentions (only when `mentionFocusOrder != null`)
| Action | Icon | Tooltip |
|--------|------|---------|
| Prev @ | at-sign + chevron-left composite | "Prev @" |
| Next @ | at-sign + chevron-right composite | "Next @" |

### Group 3 — Actions (only when `waveId != null`)
| Action | Icon | Tooltip |
|--------|------|---------|
| Archive (toggle) | archive box | "To Archive" (default) / "To Inbox" (when archived) |
| Pin (toggle) | pin/thumbtack | "Pin" (default) / "Unpin" (when pinned) |
| History | clock | "Version History (H)" |

---

## SVG Icons (Lucide, MIT)

Defined as string constants in `ViewToolbar`, using the same pattern as `SearchPresenter`:

```java
private static final String SVG_OPEN =
    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" "
    + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
    + "stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">";

private static Element createSvgIcon(String svgHtml) {
  Element wrapper = DOM.createDiv();
  wrapper.setInnerHTML(svgHtml);
  return wrapper;
}
```

Icons:
- **Recent**: rotate-ccw (`<polyline points="1 4 1 10 7 10"></polyline><path d="M3.51 15a9 9 0 1 0 .49-3.51"></path>`)
- **Next Unread**: bell (`<path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 01-3.46 0"></path>`)
- **Previous**: chevron-up
- **Next**: chevron-down
- **Last**: chevrons-down
- **Prev @**: custom at+left composite
- **Next @**: custom at+right composite
- **Archive**: archive box (already in SearchPresenter as `ICON_ARCHIVE`)
- **Pin**: pin (already in SearchPresenter as `ICON_PIN`)
- **History**: clock

---

## Archive Toggle State Machine

```
archived = false (inbox state)
  → archiveButton shows normal (not down)
  → tooltip: "To Archive"
  → on click: toggleArchive() → moves to FOLDER_ARCHIVE → wave navigates away on success

archived = true (archive state)
  → archiveButton shows highlighted (setDown(true))
  → tooltip: "To Inbox"
  → on click: toggleArchive() → moves to FOLDER_INBOX → wave navigates away on success
```

Button is disabled during async request, re-enabled on failure. Since `onFolderActionCompleted()` calls `History.newItem("", true)` (navigates away), no post-success state update is needed in `ViewToolbar`.

New fields:
```java
private boolean archived = false;
private ToolbarClickButton archiveButton;
```

New methods:
```java
public void setArchived(boolean archived) { ... }  // for StagesProvider
private void toggleArchive() { ... }               // mirrors togglePin()
private void updateArchiveButtonState() { ... }    // updates setDown + tooltip
```

---

## Pin Toggle Changes

The existing `updatePinButtonLabel()` which changes the text is replaced by `updatePinButtonState()` which:
- Calls `pinButton.getButton().setDown(pinned)` to show/hide the pressed visual
- Sets tooltip to "Unpin" when pinned, "Pin" when not pinned
- No text shown (icon-only)

---

## StageThree Changes

```java
protected ViewToolbar createViewToolbar() {
  boolean isPinned = stageTwo.getSupplement().isPinned();
  boolean isArchived = stageTwo.getSupplement().isArchived();
  return ViewToolbar.create(..., isPinned, isArchived, signedInUser);
}
```

New `ViewToolbar.create()` overload added (backwards-compatible).

---

## StagesProvider Changes

```java
private void wireArchiveState(StageThree three) {
  ViewToolbar viewToolbar = three.getViewToolbar();
  try {
    boolean archived = two.getSupplement().isArchived();
    viewToolbar.setArchived(archived);
  } catch (Exception e) {
    // default to not archived
  }
}
```

---

## Touch Target Size

The `HorizontalToolbarButtonWidget` CSS `compact` class already provides adequate hit area. No CSS changes required — the existing `padding: 0 4px` compact style gives ~32px height (inherited from parent) which is close to the 36px mobile target. We accept this as-is (further CSS tuning is out of scope).

---

## Build

```
sbt compile
```

No new Java files, no new resource files (SVGs are inline strings).
