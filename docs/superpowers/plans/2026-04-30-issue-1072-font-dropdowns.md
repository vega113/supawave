# Issue #1072: J2CL Font Family And Size Dropdowns

## Context

Issue #1072 closes the F-3 follow-up for H.7 and H.8 in the J2CL/Lit compose toolbar:

- H.7: Font family dropdown.
- H.8: Font size dropdown.
- Both must round-trip through rich submit components and the DocOp delta, not just mutate the live DOM.

The current toolbar already ships the daily rich edit actions, including H.5/H.6 superscript/subscript from #1071, but it only renders button actions. The composer serializer already maps inline tags to annotation components and the Java submit bridge already supports arbitrary annotation arrays via `SubmittedComponent.Annotation`.

## Current Findings

- `J2clDailyToolbarAction` has no `FONT_FAMILY` or `FONT_SIZE` entries yet.
- `<wavy-format-toolbar>` renders grouped `<toolbar-button>` actions and re-dispatches `wavy-format-toolbar-action` with `{actionId, selectionDescriptor}`.
- `wavy-composer` handles toolbar actions locally for inline wrappers, lists, links, clear formatting, and task insertion.
- `serializeRichComponents()` currently emits annotation components for inline tags such as `<strong>`, `<sup>`, and `<sub>`, but not `<font>` or font-size spans.
- `J2clComposerDocument.Builder.annotatedTextMulti(...)` and `J2clRichContentDeltaFactory` already support arbitrary annotation key/value pairs once the JS serializer supplies them.

## Implementation Plan

1. Toolbar action model
   - Add `FONT_FAMILY("font-family", "Font", "Edit", false, true)` and `FONT_SIZE("font-size", "Size", "Edit", false, true)` to `J2clDailyToolbarAction`.
   - Keep IDs lower-case and hyphenated to match Lit action ids and toolbar event payloads.

2. Lit toolbar dropdown UI
   - Add a small dropdown renderer in `<wavy-format-toolbar>` for `font-family` and `font-size` inside the text group, near the existing text formatting buttons.
   - Use conservative built-in options that map cleanly to CSS and annotations:
     - Font: Arial, Georgia, Courier New, Times New Roman, Verdana.
     - Size: 10px, 12px, 14px, 18px, 24px.
   - On change, dispatch `wavy-format-toolbar-action` with `{actionId, value, selectionDescriptor}`.
   - Preserve existing button event behavior for all current actions.

3. Composer mutation and serialization
   - Handle `font-family` and `font-size` in `_handleToolbarAction`.
   - Require a non-collapsed active selection. Wrap the selection in:
     - `<font face="...">` for family, matching the issue acceptance and legacy GWT shape.
     - `<span style="font-size: ...">` for size.
   - Sanitize values by allow-listing the dropdown options before mutating DOM.
   - Extend `inlineFormatAnnotation(...)` / serializer logic to emit:
     - `style/fontFamily=<name>` for `<font face>` and supported `style.fontFamily`.
     - `style/fontSize=<px>` for supported `style.fontSize`.
   - Ensure nested formatting composes through the existing `mergeOuterAnnotation(...)` path.

4. Tests
   - Add Lit toolbar tests:
     - Font and Size dropdowns render with accessible labels.
     - Selecting each emits `wavy-format-toolbar-action` with the matching `actionId` and `value`.
   - Add composer toolbar-action tests:
     - Font family action wraps selected text and serializes `style/fontFamily`.
     - Font size action wraps selected text and serializes `style/fontSize`.
     - Invalid/freeform values are ignored.
   - Add serializer tests for existing markup:
     - `<font face="Georgia">Hello</font>`.
     - `<span style="font-size: 18px">Hello</span>`.
   - Add Java tests:
     - `J2clDailyToolbarAction.fromId("font-family")` and `"font-size"` resolve correctly.
     - `J2clRichContentDeltaFactoryTest` confirms `style/fontFamily` and `style/fontSize` annotations appear at the expected offsets.

5. Verification
   - `cd j2cl/lit && npm test -- --files test/wavy-format-toolbar.test.js test/wavy-composer.test.js test/wavy-composer-toolbar-actions.test.js`
   - `cd j2cl/lit && npm run build`
   - `sbt --batch compile j2clSearchTest`
   - `python3 scripts/assemble-changelog.py`
   - `python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
   - `git diff --check`

## Self Review

- The plan avoids a visual-only dropdown: each action mutates the selected DOM and survives submit via serialized annotations.
- The value surface is allow-listed to avoid arbitrary CSS injection through toolbar event details.
- The chosen DOM shapes match the issue acceptance exactly: `<font>` for family and `<span style="font-size: ...">` for size.
- The Java delta factory likely does not need production changes because it already serializes arbitrary annotation pairs; tests should prove that instead of widening the implementation unnecessarily.
- The main risk is CSS font-family normalization adding quotes in browser style values. The implementation should normalize accepted family names before wrapping and when serializing DOM style values.
