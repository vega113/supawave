# Issue 779 Task Metadata UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the missing task metadata UX on top of the merged Wave Tasks base feature so users can assign a task to any current wave participant, set or edit a due date, and see task ownership clearly in the wave UI.

**Architecture:** Keep the existing document model intact: tasks remain `<check name="task:<id>"/>` plus `task/*` annotations, with `task/assignee` and `task/dueTs` staying authoritative. Add a narrow client-side metadata layer: a styled task-details popup for editing assignee/due date, DOM-only ownership/due-date pills rendered next to task checkboxes, and an annotation refresh handler so the pills stay synced when task annotations change.

**Tech Stack:** GWT client widgets, Wave editor doodads (`CheckBox`, annotation handlers, `EditToolbar`), existing `Conversation` participant model, existing popup infrastructure (`UniversalPopup`), targeted JUnit tests, GWT compile, local Wave server sanity verification.

---

## Context Snapshot

- Base task support from issue `#737` / PR `#739` already shipped:
  - `task/id`, `task/assignee`, `task/dueTs`, `task/reminders` annotations
  - task insertion from the edit toolbar
  - task search / unread badges / rendering
- Current UX gaps in `main`:
  - inserting a task hard-codes the signed-in user as assignee
  - due date metadata is stored in the model but cannot be set from the UI
  - task ownership is not rendered in-wave, so it is easy to confuse with normal text or mentions
- Non-goals that stay unchanged:
  - no background reminder delivery
  - no new task document primitive
  - no search redesign beyond what is needed for correctness

## Acceptance Criteria

- [ ] A user can assign a task to any participant currently in the root conversation, including robots.
- [ ] A user can set or edit a due date on a task without changing the underlying annotation schema.
- [ ] The wave UI shows task ownership in a way that is visually distinct from `@mentions`.
- [ ] Existing tasks update their metadata pills when annotations change locally.
- [ ] User-facing changes include a changelog fragment and regenerated `wave/config/changelog.json`.

## File Ownership / Likely Touch Points

- Modify: `wave/src/main/java/org/waveprotocol/wave/client/doodad/form/check/CheckBox.java`
  - render metadata pills next to task checkboxes
  - attach chip click handling
  - expose a narrow refresh hook for task metadata redraws
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/doodad/form/check/CheckBase.css`
  - style owner / due-date pills distinctly from mention highlights
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/doodad/form/check/TaskDocumentUtil.java`
  - centralize task annotation reads/writes plus date parsing/formatting helpers
- Add: `wave/src/main/java/org/waveprotocol/wave/model/conversation/TaskMetadataUtil.java`
  - shared owner-label / due-date parsing / formatting logic that both JVM tests and GWT client code can use
- Add: `wave/src/main/java/org/waveprotocol/wave/client/doodad/form/check/TaskAnnotationHandler.java`
  - refresh task checkbox metadata UI when `task/*` annotations change
- Add: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/TaskMetadataPopup.java`
  - styled popup for assignee picker + due date editor
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`
  - open task metadata popup immediately after inserting a task
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
  - register the task annotation refresh handler
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`
  - configure popup context from the root conversation and signed-in user
- Add: `wave/src/test/java/org/waveprotocol/wave/model/conversation/TaskMetadataUtilTest.java`
  - cover shared owner-label / due-date helper logic in the normal JVM test runner
- Add: `wave/src/test/java/org/waveprotocol/wave/client/doodad/form/check/TaskAnnotationHandlerTest.java`
  - cover task-annotation-to-checkbox refresh behaviour if feasible without heavy GWT setup; otherwise keep this logic in `TaskDocumentUtilTest`
- Add: `wave/config/changelog.d/2026-04-09-task-metadata-ui.json`
  - user-facing summary for assignee picker / due date / ownership pills

## Task 1: Lock The Metadata Contract Before UI Work

**Files:**
- Add: `wave/src/main/java/org/waveprotocol/wave/model/conversation/TaskMetadataUtil.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/model/conversation/TaskMetadataUtilTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/doodad/form/check/TaskDocumentUtil.java`

- [ ] Add shared helper methods in `TaskMetadataUtil` for:
  - `formatTaskOwnerLabel(...)`
  - `formatTaskDueLabel(...)`
  - `parseDateInputValue(...)`
  - `formatDateInputValue(...)`
- [ ] Add client-side `TaskDocumentUtil` helpers that treat the checkbox element location as the single authoritative task metadata location:
  - `getTaskAssignee(...)`
  - `getTaskDueTimestamp(...)`
  - `setTaskAssignee(...)`
  - `setTaskDueTimestamp(...)`
  - `clearTaskDueTimestamp(...)`
- [ ] Write failing unit tests first for:
  - owner label formatting from full participant addresses
  - empty / invalid due-date annotation handling
  - round-tripping `yyyy-MM-dd` input values to epoch millis and back
  - clearing due-date metadata
- [ ] Run the focused test command and verify the new assertions fail for the expected missing-helper reason.

Run:
```bash
sbt "testOnly org.waveprotocol.wave.model.conversation.TaskMetadataUtilTest"
```

Expected before implementation:
- the new test methods fail because the helper methods / behaviours do not exist yet

## Task 2: Build The Popup UI For Assignee + Due Date Editing

**Files:**
- Add: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/TaskMetadataPopup.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`

- [ ] Implement a styled popup using existing `UniversalPopup` infrastructure, not browser prompts.
- [ ] Configure it from `StageThree` with:
  - root conversation participant set
  - signed-in user
- [ ] Popup requirements:
  - assignee picker lists current participants plus an explicit unassigned option
  - due date field uses a styled popup input with an explicit `yyyy-MM-dd` value contract
  - implementation may use a DOM input with `type="date"` when available, but must still validate and round-trip plain `yyyy-MM-dd` values through `TaskDocumentUtil`
  - save applies `task/assignee` and `task/dueTs` annotations on the clicked task element
  - cancel makes no document change
- [ ] Owner pill format is explicit, not mention-like:
  - local-domain addresses render as `Owner alice`
  - non-local or already-non-email identifiers may render the full identifier
- [ ] Removed/stale assignees remain displayable:
  - if an existing task annotation points at an address no longer in `Conversation.getParticipantIds()`, the popup still preserves and shows the current value until the user changes it
- [ ] Keep popup state local to the selected task element; do not add new session-global task state.

Run:
```bash
sbt "testOnly org.waveprotocol.wave.model.conversation.TaskMetadataUtilTest"
sbt wave/compile
```

Expected after popup helper integration:
- contract/helper tests pass and no popup code regresses helper compilation

## Task 3: Render Clear Ownership / Due-Date Pills Inline Next To Tasks

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/doodad/form/check/CheckBox.java`
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/doodad/form/check/CheckBase.css`

- [ ] Extend task checkbox rendering so every task checkbox wrapper owns a metadata container.
- [ ] Render DOM-only pills for:
  - owner: `Owner alice`
  - due date: `Due Apr 12`
  - empty state: `Add details`
- [ ] Keep pill styling visually separate from mentions:
  - no `@` prefix
  - neutral/utility pill styles instead of mention-highlight background
- [ ] Attach a click handler on the pill container that opens the popup without toggling the checkbox.
- [ ] Keep existing checked-task strikethrough logic intact.

Run:
```bash
sbt "testOnly org.waveprotocol.wave.model.conversation.TaskMetadataUtilTest"
```

Expected:
- focused helper tests still pass after rendering integration

## Task 4: Keep Metadata UI Synced When Annotations Change

**Files:**
- Add: `wave/src/main/java/org/waveprotocol/wave/client/doodad/form/check/TaskAnnotationHandler.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/doodad/form/check/CheckBox.java`

- [ ] Register a task annotation handler for the `task/` namespace during doodad installation.
- [ ] Follow the existing annotation registration pattern:
  - expose a static `TaskAnnotationHandler.register(Registries ...)`
  - call that registration from the same doodad-install path used by `MentionAnnotationHandler`
- [ ] On task annotation changes, traverse from the changed annotation offset back to the owning `<check>` `ContentElement` and refresh only that checkbox's metadata pills.
- [ ] Treat the refresh path as DOM-specific, not painter-based:
  - `MentionAnnotationHandler` uses `AnnotationPainter.scheduleRepaint()` for inline painted spans
  - task pills are checkbox-owned DOM nodes, so the handler must call a narrow `CheckBox.refreshTaskMetadata(...)`-style hook instead
- [ ] Make sure popup saves refresh the UI immediately without requiring checkbox toggles or full document rerender.
- [ ] Explicitly verify popup save -> annotation write -> handler fire -> pill redraw in manual sanity, because this is the riskiest interaction path.
- [ ] Keep the refresh path narrow; do not repaint unrelated annotation families.

Run:
```bash
sbt "testOnly org.waveprotocol.wave.model.conversation.TaskMetadataUtilTest"
sbt wave/compile
```

Expected:
- helper tests remain green; no compile/runtime regression from annotation refresh wiring

## Task 5: Wire Popup Launch Into New Task Creation

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`

- [ ] After inserting a new task, immediately open the task metadata popup for that inserted checkbox.
- [ ] Preserve the existing checkbox insertion path and task ID generation.
- [ ] Do not change search or reminder behaviour in this step.

Run:
```bash
sbt "testOnly org.waveprotocol.wave.model.conversation.TaskMetadataUtilTest" "testOnly org.waveprotocol.wave.client.wavepanel.impl.edit.EditSessionTest"
```

Expected:
- targeted tests pass

## Task 6: Verification, Changelog, And Local Sanity

**Files:**
- Add: `wave/config/changelog.d/2026-04-09-task-metadata-ui.json`
- Regenerate: `wave/config/changelog.json`

- [ ] Add a changelog fragment covering:
  - assignee picker from current wave participants
  - due date editing UI
  - inline ownership / due-date pills on task rows
- [ ] Run changelog assembly / validation.
- [ ] Run the focused client/server verification commands.
- [ ] Run a local server sanity check that exercises task insertion and metadata editing in the browser.
- [ ] Record exact commands and outcomes in issue `#779` and in `journal/local-verification/...`.

Verification commands:
```bash
sbt "testOnly org.waveprotocol.wave.model.conversation.TaskMetadataUtilTest" "testOnly org.waveprotocol.wave.client.wavepanel.impl.edit.EditSessionTest"
sbt wave/compile
sbt compileGwt
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
```

Local sanity target:
```bash
sbt prepareServerConfig run
```

Manual/browser sanity checklist:
- create a new local user if needed
- open or create a wave with at least one additional participant/robot
- insert a task
- verify popup opens on insert
- change assignee to another participant
- set a due date
- confirm owner / due pills update in-wave and remain distinct from mentions
- reopen the popup from the task pill and edit values again

## Out Of Scope / Guardrails

- [ ] Do not add background reminder scheduling or delivery.
- [ ] Do not change the server-side task annotation schema.
- [ ] Do not widen search semantics beyond existing task metadata correctness.
- [ ] Do not add mention-style profile popups for task owners in this issue.
- [ ] Do not use browser-native `alert` / `confirm` / `prompt`.
