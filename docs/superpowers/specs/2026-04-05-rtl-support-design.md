# RTL Language Support Design

**Date:** 2026-04-05  
**Status:** Approved  
**Scope:** Right-to-left language rendering (Hebrew, Arabic, etc.) with auto-detection and manual override

## Problem

Wave renders all paragraph text left-to-right by default. Users can manually set text-align to right, but this is not true RTL — the bidirectional (bidi) algorithm is not applied, causing Hebrew/Arabic text to render incorrectly (e.g., punctuation appears on the wrong side of the line).

## Goal

Auto-detect RTL paragraphs and render them correctly, with a manual override in the toolbar — matching Gmail's behavior.

## Architecture & Data Flow

Wave already stores paragraph direction as a `d` attribute on paragraph elements (`d="r"` for RTL, `d="l"` for LTR). The `Direction` enum and `DIRECTION_ATTR` constant exist in `Paragraph.java`. The renderer (`DefaultParagraphHtmlRenderer.java`) already reads this attribute and applies CSS `direction` property.

The change extends the rendering to use the HTML `dir` attribute instead of (or alongside) the CSS property, and introduces `dir="auto"` when no explicit direction is stored:

```
Paragraph element in doc model
  ├── d="r" (explicit RTL)  → render dir="rtl"
  ├── d="l" (explicit LTR)  → render dir="ltr"
  └── (no d attr, default)  → render dir="auto"   ← new behavior
```

`dir="auto"` is a native HTML attribute that instructs the browser to determine direction from the first strong Unicode bidi character in the element's text — exactly the algorithm Gmail uses. No custom detection logic is required.

## Components & Changes

### 1. `DefaultParagraphHtmlRenderer.java`

**Current behavior:** When `direction != null`, sets CSS `style.direction = "rtl"/"ltr"`. When null, clears the property.

**New behavior:**
- `Direction.RTL` → set HTML attribute `dir="rtl"`, clear CSS `direction` (the `dir` attribute implies it)
- `Direction.LTR` → set HTML attribute `dir="ltr"`, clear CSS `direction`
- `null` (no stored direction) → set HTML attribute `dir="auto"`

The element reference in the renderer already supports `setAttribute`/`removeAttribute` via GWT's DOM API.

### 2. `EditToolbar.java`

Add two new mutually-exclusive toggle buttons after the existing alignment group:

| Button | Label/Icon | Action |
|--------|-----------|--------|
| RTL    | ⇐A        | Set `d="r"` on current paragraph(s) |
| LTR    | A⇒        | Set `d="l"` on current paragraph(s) |

Clicking the already-active button clears the `d` attribute (returns to auto-detection). Button highlight state reflects the current paragraph's `d` attribute. When no `d` attr is present, neither button is highlighted (auto state).

Implementation follows the exact same pattern as the existing alignment buttons (`Paragraph.Alignment`), using `Paragraph.Direction` instead.

### 3. Toolbar Icons

Add two text-direction icons to the existing GWT `Images` interface/bundle. SVG inline or simple Unicode text labels are acceptable for the initial implementation.

### 4. Files with No Changes Needed

- `Paragraph.java` — `Direction` enum and `DIRECTION_ATTR = "d"` already exist
- `ParagraphRenderer.java` — already listens for `DIRECTION_ATTR` changes and schedules re-render
- Server/model/OT layer — direction is a standard paragraph attribute; no protocol changes

## Error Handling & Edge Cases

| Case | Behavior |
|------|----------|
| Mixed Hebrew + Latin in one paragraph | `dir="auto"` + browser bidi algorithm handles embedded LTR runs correctly |
| Empty paragraph | `dir="auto"` defaults to LTR; resolves once user types |
| Existing docs with `a="r"` (right-aligned LTR) | Unchanged — these are text-align, not direction; no migration |
| Collaborative editing | Direction attribute changes flow through normal Wave OT pipeline; no special handling |
| Cursor in auto-direction paragraph | Neither RTL nor LTR button highlighted in toolbar |

## Testing Plan

1. **Auto-detection**: Type Hebrew text in a new paragraph → verify paragraph auto-aligns right with correct bidi (punctuation on correct side)
2. **Latin stays LTR**: Type English in a new paragraph → verify LTR, no regression
3. **Mixed content**: Type Hebrew then Latin in same paragraph → verify correct bidi embedding
4. **Manual RTL**: Click RTL button → paragraph locks RTL, button highlights
5. **Manual LTR override**: In auto-RTL paragraph, click LTR → forces LTR, button highlights
6. **Return to auto**: Click active RTL/LTR button again → clears override, returns to auto-detection
7. **Existing content**: Open document with right-aligned paragraphs → verify unchanged rendering
8. **Collaborative**: Two users editing same RTL paragraph → direction change syncs correctly
