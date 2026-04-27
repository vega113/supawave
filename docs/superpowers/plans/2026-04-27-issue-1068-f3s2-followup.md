# 2026-04-27 — Issue #1068 F-3.S2 follow-up

## Goal

Two real bugs landed in F-3.S2 (#1066) but the auto-merge fired before
chatgpt-codex-connector posted them. This is a focused follow-up that
plugs both holes with regression tests.

## Bugs

### Bug 1 (P1) — `J2clComposeSurfaceController` task-write silent discard

`onTaskToggled` and `onTaskMetadataChanged` capture
`writeSession` into `submitSession`, then re-check
`writeSession != submitSession` after the bootstrap callback fires.
Reference equality breaks if the same wave re-publishes its session
(e.g. a no-op refresh after a tag-set update). The writes are silently
dropped.

**Fix**: replace the reference-identity check with a logical-session
check that compares `getSelectedWaveId()` of the captured session and
the current `writeSession`. A no-op refresh produces a new object with
the same wave id, so the submit goes through. A genuine wave switch or
sign-out still drops the write (the `signedOut` check + null-session
check already guard those).

The fix lives entirely inside the two callbacks at
`J2clComposeSurfaceController.java:700` and `:744`.

### Bug 2 (P2) — `wavy-task-affordance` popover stuck open after submit

`_onMetadataSubmit` mutates `_popoverOpen = false` and toggles the
`data-popover-open` attribute, but never calls `requestUpdate()`. If
both `assigneeAddress` and `dueDate` are unchanged, no reactive
property mutates and Lit never re-renders, so the
`task-metadata-popover` stays in the DOM. (`_openDetails` and
`_onPopoverClose` already call `requestUpdate()` — the omission is a
local oversight.)

**Fix**: add `this.requestUpdate()` after the close mutation in
`_onMetadataSubmit`.

## Test plan

### Java side (jakartaTest + j2clSearchTest)

Add a regression test in
`J2clComposeSurfaceControllerTest`:

1. `onTaskToggledSurvivesNoOpSessionRefresh` — uses
   `autoResolveBootstrap = false`, captures the pending bootstrap, then
   pushes a new `J2clSidecarWriteSession` instance with the same
   `selectedWaveId` (no-op refresh), then resolves the bootstrap and
   asserts `submitCalls` incremented.
2. `onTaskMetadataChangedSurvivesNoOpSessionRefresh` — same pattern for
   `onTaskMetadataChanged`.
3. (Defensive) `onTaskToggledDropsWriteOnWaveSwitch` — same pattern but
   the refreshed session has a different wave id; asserts no submit.

### Lit side (j2clLitTest)

Add a test in `wavy-task-affordance.test.js`:

- Open the popover, dispatch `task-metadata-submit` with the same
  assignee + due date that's already set, then await
  `el.updateComplete` and assert the popover is gone from the
  renderRoot and `data-popover-open` is false.

## Verification

- `sbt -batch j2clLitTest j2clSearchTest j2clProductionBuild`
- `sbt -batch jakartaTest:testOnly *J2clStageThreeComposeS2ParityTest *J2clComposeSurfaceControllerTest`

## Out of scope

Anything beyond the two bug locations + their regression tests. Don't
re-touch other F-3.S2 code, parity test, or umbrella docs.

## Branch

`codex/issue-1068-f3s2-followup` from latest `origin/main`.
