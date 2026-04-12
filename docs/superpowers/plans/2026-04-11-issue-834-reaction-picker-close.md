# Issue 834 Reaction Picker Close Fix Plan

> Scope: narrow follow-up to the merged reactions UI from issue `#798` / PR `#799`.

## Context

- Issue: `#834` "Fix add-reaction popup not closing after selecting a reaction"
- Worktree: `<local worktree path>`
- Branch: `reaction-picker-close-20260411`
- Note: this lane was created from a stale local `main`; the branch has been merged with `origin/main` so implementation targets the reactions code that is already live on current main.

## Root Cause Hypothesis

- `ReactionPickerPopup.show(...)` creates a fresh popup on every add-button activation.
- Neither `ReactionPickerPopup` nor `ReactionController` tracks an active picker instance.
- If the add button is triggered twice before selection, choosing an emoji hides only the newest popup while the older popup remains visible.
- The centered popup also does not explicitly move focus into the picker, which makes repeated keyboard activation more likely to re-open a second popup instead of selecting from the first one.

## Fix Shape

- Add a narrow popup-lifecycle seam in the reactions popup/controller code so only one picker can be active at a time.
- Close and clear the active picker reliably before applying the selected reaction.
- Preserve existing outside-click / escape dismissal and existing reaction-toggle behavior.
- Avoid widening scope beyond reactions popup lifecycle and focus handling.

## Files

- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionPickerPopup.java`
- Modify if needed: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionController.java`
- Add test: `wave/src/test/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionPickerPopupTest.java`
- Add changelog fragment: `wave/config/changelog.d/2026-04-11-reaction-picker-close.json`
- Add verification note: `journal/local-verification/2026-04-11-issue-834-reaction-picker-close.md`

## Steps

- [x] Write a failing regression test for duplicate-open / select-close lifecycle behavior.
- [x] Implement the minimal popup lifecycle fix.
- [x] Re-run the focused reactions test target and compile target.
- [x] Run a local browser sanity check covering:
  - single-click add reaction closes popup
  - quick repeated activation still leaves only one popup and closes after selection
  - keyboard open/select flow still works
- [x] Record verification evidence in the journal file and issue comment.

## Verification Targets

- manual `javac` + `org.junit.runner.JUnitCore` harness for `ReactionPickerPopupTest` against `wave / Test / fullClasspath` (the repo excludes this `wave/client` seam from normal `testOnly` discovery)
- `sbt wave/compile`
- `python3 scripts/assemble-changelog.py`
- `python3 scripts/validate-changelog.py`
- `bash scripts/worktree-boot.sh --port 9901`
