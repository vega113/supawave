# Issue 828 Android IME Live Preview Follow-Up

## Goal

Fix the remaining real Android Chrome IME symptom after PR #1009: typing `new blip`
now commits correctly after Done, but the active composition still visibly shows
Chrome's raw scratch text such as `ew lip` while the user is typing.

## Root Cause

The Android `compositionupdate.data` stream contains enough information to
recover `new` and `blip`, and `ImeExtractor.getEffectiveContent()` uses that
stream at composition flush time. During active typing, however, Android fires
`compositionupdate` before its `input` event rewrites the IME scratch span, so
the DOM visible to the user remains the browser's shortened raw scratch until
composition end.

## Plan

- Add a separate live-preview path to `ImeCompositionTextTracker`.
- Keep `effectiveText()` conservative for final commit semantics.
- Render the Android live preview as a visual overlay on the IME scratch span
  instead of mutating the IME's real text node.
- Clear the overlay when the raw scratch catches up, the extractor deactivates,
  or the scratch resets.

## Acceptance

- The tracker preview returns `ne` for the stream `n -> e` with raw scratch `e`.
- Final effective text still returns raw scratch for an unconfirmed pending
  replacement.
- Focused tracker and ghost text tests pass.
- GWT client compilation passes.
- Local mobile browser verification still types, commits, and reloads `new blip`
  without regressing the PR #1009 persistence path.
