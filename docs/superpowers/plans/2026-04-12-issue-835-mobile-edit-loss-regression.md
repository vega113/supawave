# Issue 835 Mobile Edit-Loss Regression Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the reopened Android/mobile regression where a newly created blip can show typed text locally but fail to persist it after Done.

**Architecture:** Keep the fix inside the client editor event pipeline. The narrow seam is the delayed IME composition-end guard in `EditorEventHandler`: when `compositionEnd()` returns no selection, the trailing DOM mutation must still be allowed to reach the typing extractor so browser-owned text becomes a document operation instead of disappearing on teardown/reload.

**Tech Stack:** GWT client editor event handling, browser DOM mutation fallback, existing local worktree verification, changelog fragments.

---

## File Map

- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/event/EditorEventHandler.java`
- Modify: `wave/src/test/java/org/waveprotocol/wave/client/editor/event/EditorEventHandlerGwtTest.java`
- Create: `wave/config/changelog.d/2026-04-12-mobile-edit-loss-regression.json`
- Update: `journal/local-verification/2026-04-11-branch-android-edit-regression-20260411.md`

## Acceptance

- The null-selection delayed-composition path is covered by a focused regression in `EditorEventHandlerGwtTest`.
- The editor only suppresses the trailing DOM mutation when `compositionEnd()` actually restored a selection.
- Local browser verification covers the exact user path: mobile-emulated new blip edit, Done, reload/persist check.
- Changelog and local verification evidence are recorded in-repo.

## Notes

- The current reconnect/save-state code already contains explicit fixes for stale socket callbacks and reconnect resubscribe; no reconnect patch should be added without a dedicated failing seam.
- The existing uncommitted editor/test changes in this branch are in scope for this issue because they directly address the reported mobile non-persist path.
