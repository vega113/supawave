# Issue #1165 Editor, Mentions, And Task Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the J2CL editor-toolbar, mention autocomplete, and task-behavior parity gaps reported against GWT.

**Architecture:** Keep the current Lit/J2CL inline composer architecture. Treat toolbar visibility/icon mode and mention autocomplete as current-code verification slices, then narrow implementation to the remaining task semantics gap: J2CL must not expose a per-blip "Task" toggle that marks the whole blip body as complete when the user did not create a GWT-style task item.

**Tech Stack:** J2CL Java views/controllers, Lit elements under `j2cl/lit`, SBT-only verification, and changelog fragments under `wave/config/changelog.d/`.

---

## Current Findings

- `wavy-format-toolbar` already hides by default and only shows from `selectionDescriptor` with a non-collapsed bounding rect; its toolbar buttons already render SVG icons.
- `wavy-composer` already opens `mention-suggestion-popover` for `@`, filters the `participants` property, inserts mention chips, and serializes them as `mention/user` rich components.
- `J2clComposeSurfaceView` already forwards `model.getParticipantAddresses()` into the legacy reply element and active inline composer, including the fresh mount path.
- `wave-blip` still mounts `wavy-task-affordance` on every blip, and `wavy-task-affordance` exposes a visible `Task` toggle. Clicking it emits `wave-blip-task-toggled`; `wave-blip` mirrors that into `data-task-completed`, and CSS applies `line-through` to `.body`. This matches the user report that clicking `Task` strikes the whole blip text.
- `J2clRichContentDeltaFactory.taskToggleRequest()` currently writes `task/done` over the entire blip body. That is not the same as a GWT-style inline task item created in edit mode.

## Task 1: Toolbar Visibility And Icon Parity Guard

**Files:**
- Modify: `j2cl/lit/test/wavy-format-toolbar.test.js`
- Optionally modify: `j2cl/lit/src/elements/wavy-format-toolbar.js`

- [ ] Add a regression test that a newly mounted `wavy-format-toolbar` is hidden before any selection descriptor is supplied.
- [ ] Add a regression test that a collapsed selection hides the toolbar after it was visible.
- [ ] Add a regression test that all non-select toolbar actions use SVG icon buttons and keep accessible labels without visible text labels.
- [ ] Run `cd j2cl/lit && npm test -- --files test/wavy-format-toolbar.test.js`.
- [ ] If any assertion fails, fix only the failing toolbar behavior without changing action ids or `wavy-format-toolbar-action` event payloads.

## Task 2: Mention Participant Source Guard

**Files:**
- Modify: `j2cl/lit/test/wavy-composer.test.js`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceViewTest.java`
- Optionally modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java`

- [ ] Add or confirm Lit coverage that typing `@` opens suggestions sourced from the `participants` property, not from global contacts.
- [ ] Add or confirm Java view coverage that a freshly opened inline composer receives `model.getParticipantAddresses()` before the next full render.
- [ ] Add a regression assertion that a participant selected from the mention popover emits `wavy-composer-mention-picked` with `address`, `displayName`, and `chipTextOffset`.
- [ ] Run `cd j2cl/lit && npm test -- --files test/wavy-composer.test.js`.
- [ ] Run `sbt --batch Test/compile j2clSearchTest`.
- [ ] If current coverage already proves the behavior, keep code unchanged and document the evidence in the issue/PR.

## Task 3: Remove Whole-Blip Task Toggle Semantics

**Files:**
- Modify: `j2cl/lit/src/elements/wave-blip.js`
- Modify: `j2cl/lit/src/elements/wavy-task-affordance.js`
- Modify: `j2cl/lit/test/wave-blip.test.js`
- Modify: `j2cl/lit/test/wavy-task-affordance.test.js`
- Optionally modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
- Optionally modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`

- [ ] Write failing Lit tests that a default blip does not show a visible `Task` text toggle.
- [ ] Write failing Lit tests that toggling any remaining task control does not apply `line-through` to the whole `.body`.
- [ ] Keep persisted task metadata visible only as task metadata/status affordance for blips that actually carry task annotations.
- [ ] Remove or disable the whole-blip `wave-blip-task-toggled` path for generic blips. If a control remains, make it an icon-only metadata/details affordance and do not emit a completion toggle for the entire body.
- [ ] Keep task insertion in edit mode inside `wavy-composer` via `insert-task`; this is the GWT-like path for creating task content.
- [ ] Run `cd j2cl/lit && npm test -- --files test/wave-blip.test.js test/wavy-task-affordance.test.js test/wavy-composer.test.js`.
- [ ] Run `sbt --batch Test/compile j2clSearchTest`.

## Task 4: Changelog And Browser Verification

**Files:**
- Create: `wave/config/changelog.d/2026-05-01-j2cl-editor-mentions-tasks-parity.json`
- Update issue comments with verification evidence.

- [ ] Add a new changelog fragment under `wave/config/changelog.d/`; do not hand-edit `wave/config/changelog.json`.
- [ ] Run `python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json && git diff --check`.
- [ ] Run broader verification: `sbt --batch compile Test/compile j2clSearchTest j2clLitTest`.
- [ ] Run local browser verification on `/?view=j2cl-root`:
  - Select text in an active inline composer and confirm the formatting toolbar appears only in edit/selection context.
  - Type `@` in the composer and confirm wave participants appear as mention suggestions.
  - Confirm normal blips no longer expose a `Task` text button that strikes through the whole blip.
- [ ] Post the exact verification commands and browser evidence to #1165 before opening the PR.

## Self-Review

- Scope stays inside #1165. Attachment thumbnail sizing remains #1166; inline blip/thread structure remains #1167.
- The plan does not remove mention or rich-format features that already exist; it guards them with tests and fixes only observed regressions.
- The risky behavior is task semantics, because current J2CL stores and renders a whole-blip task/done annotation. The conservative fix is to stop exposing that as a generic visible task toggle and keep task creation in the editor path.
- Changelog handling follows repo policy: add a fragment under `wave/config/changelog.d/`, never hand-edit generated `wave/config/changelog.json`.
