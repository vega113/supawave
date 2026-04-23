# Issue #970 Interaction Overlays Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task after the dependency gates in section 4 are open. Steps use checkbox (`- [ ]`) syntax for implementation tracking.

**Goal:** Port the high-value StageThree interaction overlays that block practical Lit/J2CL daily use beyond compose and toolbar parity: mentions/autocomplete, task metadata overlays, reactions, authors/inspect overlays, and their keyboard/repeated-interaction behavior.

**Architecture:** Add a narrow J2CL interaction-overlay layer above the selected-wave/read model and the post-#969 editor/toolbar host. The layer should reuse existing selected-wave websocket transport and document snapshots where possible, preserving GWT behavior from `MentionTriggerHandler`, `TaskMetadataPopup`, `ReactionController`, `ReactionPickerPopup`, `ReactionPopupLifecycle`, and `ViewToolbar`. Lit custom elements own overlay rendering and focus behavior; Java/J2CL controllers own state projection and mutation framing.

**Tech Stack:** J2CL Java controllers and transport models, existing sidecar websocket gateway, Lit 3 JavaScript custom elements under `j2cl/lit/`, Web Test Runner for Lit behavior, JUnit/J2CL unit tests for projection and transport decoding, existing GWT/JVM tests for legacy behavior guardrails, and local browser verification against a running Wave server.

---

## 1. Planning Status And Ownership

This document is the planning artifact for issue #970 only.

- Planning worktree: `/Users/vega/devroot/worktrees/issue-970-interaction-overlays`
- Planning branch: `issue-970-interaction-overlays`
- Issue: `https://github.com/vega113/supawave/issues/970`
- Parent tracker: `#904` / `#960`
- Dependency: implementation starts only after #969 has merged and its compose/editor/toolbar host is available on the implementation branch.
- Write scope for this planning lane: this markdown file and GitHub issue comments only.

## 2. Baseline Evidence

### 2.1 Parity Rows Claimed By #970

`docs/j2cl-gwt-parity-matrix.md` assigns these StageThree rows to issue #970:

- `R-5.3` - mentions/autocomplete.
- `R-5.4` - task metadata overlays.
- `R-5.5` - reactions and comparable interaction overlays.

`docs/j2cl-parity-issue-map.md` marks #970 as dependent on #969 and coordinated with #971. The implementation lane must not claim attachment or rich-edit rows; those stay with #971.

### 2.2 GWT StageThree Integration Seams

`wave/src/main/java/org/waveprotocol/wave/client/StageThree.java` installs the current GWT behavior:

- `createEditSession(...)` creates `SelectionExtractor` and, when a root conversation exists, passes `new MentionTriggerHandler(rootConversation)` into the `EditSession` constructor.
- `createEditToolbar(...)` configures `TaskMetadataPopup.configure(root, signedInUser)` before creating `EditToolbar`.
- `install()` wires menu, toolbar, edit, participant, focus, tags, reactions, draft mode, and diff upgrade behavior.
- `install()` calls `ReactionController.install(stageTwo.getConversations(), stageTwo.getViewIdMapper(), profiles, user)`.

The J2CL implementation should treat these as the source behavior seams, not as code to duplicate blindly.

### 2.3 Current J2CL Selected-Wave Gap

The current J2CL selected-wave stack is text-oriented:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java` stores participant ids, content entries, write session, read state, and status fields. It has no mention ranges, task metadata, reaction summaries, overlay state, or editable blip identity model.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java` renders selected-wave content entries as `<pre class="sidecar-selected-entry">`. This gives no per-blip overlay anchor, no reaction row host, and no task/mention affordance target.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java` extracts participant ids, text content entries, and write session data from selected-wave updates. It does not project semantic interaction metadata.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java` decodes selected-wave documents and fragments, but `extractDocumentText(...)` keeps only textual content and line breaks.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveDocument.java` stores only document id, author, last modified metadata, and text content.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java` submits create/reply text drafts through `J2clSearchGateway`; it does not expose annotation/document mutation hooks for mentions, tasks, or reactions.

#969 must supply the editor and toolbar host that #970 attaches to. #970 must not rebuild compose or rich edit as a workaround.

## 3. Acceptance Criteria

Issue #970 is implementation-complete when all of the following are true:

- `R-5.3`, `R-5.4`, and `R-5.5` in the parity matrix can be marked implemented with file-backed evidence and local verification.
- Mention entry works in the Lit/J2CL editor host with `@` trigger, debounced participant filtering, arrow navigation, Enter/Tab selection, Escape dismissal, Backspace filter behavior, identifier-character filtering, trailing-space insertion, and caret restoration.
- Mention insertion persists equivalent mention annotation semantics to GWT's `AnnotationConstants.MENTION_USER` behavior and renders existing mentions from selected-wave data.
- Task checkboxes render with metadata affordances, and the metadata overlay edits assignee and due date using the same annotation/document contract as `TaskDocumentUtil` and `TaskAnnotationHandler`.
- Task metadata overlay preserves keyboard behavior: Enter submits valid edits, Escape cancels, invalid date text stays visible with an error state, and focus returns to the triggering task affordance after close.
- Reactions render per blip with add button, active chip state for the signed-in user, counts, toggle behavior, and an authors/inspect overlay for reaction counts.
- Reaction picker behavior preserves repeated-interaction semantics from `ReactionPopupLifecycle`: opening a picker hides the previous active picker, repeated add clicks do not leave stale popups, and focus moves to the first emoji then returns to the trigger on close.
- Reaction inspect behavior supports click on count, long press, context menu, and `Shift+F10` where the platform supports those inputs, without duplicate toggle events after inspect.
- Mobile/touch behavior handles long-press suppression without breaking normal click/tap reaction toggling.
- Mention filtering and reaction toggles expose screen-reader announcements for candidate-count changes, selection changes, and reaction count changes.
- Overlay styling follows `docs/j2cl-lit-design-packet.md` section 5.5 and its focus/overlay rules: modal task editor traps focus; non-modal suggestions and reaction popovers close on outside/Escape and restore focus.
- The implementation adds a changelog fragment under `wave/config/changelog.d/` and regenerates/validates `wave/config/changelog.json` because this changes user-facing behavior.
- A local browser verification record demonstrates mention, task, and reaction behavior against the running app, on desktop and at least one mobile viewport.

## 4. Dependency Gates

### 4.1 Hard Gate On #969

#970 implementation must not start until #969 has merged into the implementation branch because #970 requires:

- A J2CL editor host with stable caret/selection APIs.
- A toolbar integration seam for task affordances.
- A write-session mutation path that can express more than plain create/reply text.
- A selected-blip/editing context that overlays can anchor to.

If #969 merges with different file names or APIs than this plan assumes, the implementation worker must update this plan's file ownership section in the issue before writing code.

### 4.2 Upstream Gate Through #969 Dependencies

#969 is itself sequenced after earlier J2CL read/live surface work. The #970 worker must verify the branch includes the finalized selected-wave basis and live update behavior before adding overlay projection:

- Selected-wave open/update path must still flow through `J2clSearchGateway.openSelectedWave(...)`.
- `J2clSelectedWaveProjector` must still be the narrow projection seam for read-side selected-wave state, unless #969 replaces it with an explicitly documented successor.
- The editor host must be reachable from the selected-wave workflow without changing the default GWT `/` route.

### 4.3 Coordination Gate With #971

#971 owns attachments and remaining rich-edit parity. #970 must coordinate the shared overlay layer but not consume #971 scope.

- #970 may create a shared overlay-root/layer primitive only if it is generic and documented for #971 reuse.
- #970 must not implement attachment upload/preview popups, image thumbnail overlays, drag/drop attachment UI, or rich text formatting commands.
- #971 must not reimplement mention/task/reaction controllers; it should depend on #970's shared overlay placement/focus contract once #970 lands.

### 4.4 Re-read Gate Before Implementation

Before product-code changes, the implementation worker must re-read:

- `AGENTS.md`
- `docs/github-issues.md`
- `docs/agents/tool-usage.md`
- `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`
- The final #969 plan and merged diff
- The current #971 plan, if present
- `docs/j2cl-gwt-parity-matrix.md`
- `docs/j2cl-lit-design-packet.md`
- `docs/j2cl-lit-implementation-workflow.md`
- The dependency-resolved test runner for legacy GWT guardrails; use the repo's actual runner on that branch rather than assuming the planning-time command is still valid.
- The dependency-resolved J2CL entry route; use the actual route from #969 in browser scripts if it differs from `/?view=j2cl-root`.

## 5. Architecture Seams

### 5.1 Legacy Mention Behavior To Preserve

Reference files:

- `wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionTriggerHandler.java`
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionPopupWidget.java`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/ViewToolbar.java`

Behavior to port:

- `MentionTriggerHandler.onKeySignal(...)` detects `@` in normal mode.
- `handleNormalMode(...)` enters mention mode only for the trigger.
- `handleMentionMode(...)` owns Up/Down, Escape, Enter, Tab, Backspace, identifier filtering, whitespace dismissal, and text-based filtering.
- `enterMentionMode(...)` creates a popup anchored to the editor widget and calls `updateParticipantList()`.
- `onSelect(...)` deletes the typed trigger/filter span, inserts formatted mention text, annotates with `AnnotationConstants.MENTION_USER`, inserts a trailing unannotated space, and places the caret after that space.
- `MentionPopupWidget` exposes list update, up/down selection, show/hide, and click-to-select.
- `ViewToolbar` uses `MentionFocusOrder` for previous/next mention navigation. #970 should preserve these affordances in the J2CL toolbar if #969 exposes a toolbar slot for them.

### 5.2 Legacy Task Behavior To Preserve

Reference files:

- `wave/src/main/java/org/waveprotocol/wave/client/doodad/form/check/TaskAnnotationHandler.java`
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/form/check/TaskDocumentUtil.java`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/edit/TaskMetadataPopup.java`

Behavior to port:

- `TaskAnnotationHandler` listens for `AnnotationConstants.TASK_PREFIX` changes and refreshes task metadata display.
- `TaskDocumentUtil.generateTaskId(...)`, `constructTaskXml(...)`, `getTaskAssignee(...)`, `getTaskDueTimestamp(...)`, `setTaskAssignee(...)`, `setTaskDueTimestamp(...)`, and `insertTask(...)` define the document/annotation contract.
- `TaskMetadataPopup.show(...)` centers an overlay, masks the background, populates participant options with the current user first, focuses the assignee field, handles Enter/Escape, validates due-date text, writes annotations, refreshes the checkbox, and hides.

### 5.3 Legacy Reaction Behavior To Preserve

Reference files:

- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionController.java`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionPickerPopup.java`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionPopupLifecycle.java`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/reactions/ReactionRowRenderer.java`
- `wave/src/main/java/org/waveprotocol/wave/model/conversation/ReactionDocument.java`
- `wave/src/main/java/org/waveprotocol/wave/model/conversation/ReactionDataDocuments.java`

Behavior to port:

- `ReactionController.install(...)` binds conversations, blips, document listeners, preview handlers, picker, authors popup, long press, context menu, and cleanup.
- `ReactionController.render(...)` updates a per-blip reaction row from `ReactionDocument.getReactions()`.
- `ReactionController.toggleReaction(...)` delegates to `ReactionDataDocuments.getOrCreate(blip).toggleReaction(...)`.
- `ReactionController` opens authors popup on count click/inspect trigger and opens picker from the add button.
- `ReactionPickerPopup.show(...)` uses `ReactionPopupLifecycle.activate(...)`, focuses the first emoji after show, and calls the listener once on selection.
- `ReactionPopupLifecycle` guarantees only one active picker and clears state on hide.
- `ReactionRowRenderer.render(...)` emits stable data attributes for action, emoji, count, and inspect triggers.
- `ReactionDocument.toggleReaction(...)` enforces one reaction per user and purges duplicates.

### 5.4 J2CL Overlay Model Layer

Create pure data models under `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/`:

- `J2clInteractionBlipModel` - blip id, document id, author, text, participant context, editable flag, mention ranges, task items, reaction summaries.
- `J2clMentionRange` - start offset, end offset, user address, display text.
- `J2clMentionCandidate` - address, display name, avatar token if available, sort key, current-user flag.
- `J2clTaskItemModel` - task id, text offset or element anchor id, assignee address, due timestamp, checked state, editable flag.
- `J2clReactionSummary` - emoji, participant addresses, active-for-current-user flag, count, inspect label.
- `J2clOverlayFocusTarget` - stable target id used to restore focus after a popup closes.

These models should be independent of Lit and independent of GWT classes.

Profile data rule:

- Mention candidates should source display name/avatar data from the selected-wave participant/profile model exposed after #969.
- If #969 exposes addresses only, the first implementation should still satisfy keyboard/autocomplete parity with address labels and record the missing profile enrichment in issue #970 before adding any new profile endpoint.

### 5.5 J2CL Transport And Projection Layer

Extend the selected-wave transport path instead of creating a parallel fetch path first.

Primary files:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveDocument.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveFragment.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`

Implementation approach:

- Add document-level metadata containers to `SidecarSelectedWaveDocument`, preserving the existing `textContent()` API for current consumers.
- Decode annotation spans from selected-wave document operations before text extraction discards them.
- Detect task annotations using the same annotation keys as `TaskDocumentUtil`.
- Detect mention annotations using `AnnotationConstants.MENTION_USER`.
- Detect reaction data documents by document id convention used by `ReactionDataDocuments`/`IdUtil.reactionDataDocumentId(...)`.
- Keep raw decode tests in `SidecarTransportCodecTest` so failures identify transport drift before UI code is involved.
- Project decoded transport metadata into `J2clInteractionBlipModel` instances in `J2clSelectedWaveProjector`.
- Add `J2clSelectedWaveModel.interactionBlips()` while keeping `contentEntries()` available until the read surface no longer needs it.

Fallback rule:

- If tests prove selected-wave snapshots do not expose enough annotation/reaction data, add one explicit server-side sidecar overlay-metadata endpoint in a separate implementation slice. That endpoint must return the same pure overlay models and must not leak GWT widget types into J2CL. This fallback requires an issue comment before coding because it widens the server surface.

### 5.6 J2CL Controller Layer

Add one coordinator and focused subcontrollers:

- `J2clInteractionOverlayController` - owns selected-wave overlay state, delegates mention/task/reaction events, and binds to the post-#969 editor host.
- `J2clMentionOverlayController` - owns mention trigger/filter/selection state and produces editor mutation requests.
- `J2clTaskOverlayController` - owns task popup state, validation, and task annotation mutation requests.
- `J2clReactionOverlayController` - owns reaction rows, picker/authors popup state, long-press suppression, and reaction data-doc mutation requests.
- `J2clOverlayMutationFactory` - builds mutation intents using the post-#969 write-session/editor mutation contract.

The controllers should accept narrow interfaces rather than concrete Lit elements:

```java
interface J2clOverlayEditorHost {
  String currentBlipId();
  int caretOffset();
  void restoreFocus(J2clOverlayFocusTarget target);
  void applyOverlayMutation(J2clOverlayMutation mutation);
}
```

The final interface names may align with #969 if #969 already defines an editor host API. #970 must adapt to #969 rather than replacing it.

### 5.7 Lit Overlay Components

Add Lit elements under `j2cl/lit/src/elements/`:

- `mention-suggestion-popover.js` - non-modal listbox for mention candidates.
- `task-metadata-popover.js` - modal dialog/popover for assignee and due date editing.
- `reaction-row.js` - per-blip reaction chips and add button.
- `reaction-picker-popover.js` - emoji picker with single-active lifecycle.
- `reaction-authors-popover.js` - non-modal author list/inspect overlay.
- `interaction-overlay-layer.js` - shared placement/focus restoration shell, if #969 does not already supply one.

Component contracts:

- Lit components render only from model props and emit semantic custom events.
- Java/J2CL controllers own selected-wave state and mutation decisions.
- Components must not read `window.__session`, `window.__websocket_address`, or websocket state directly.
- Components must set ARIA roles and keyboard behavior locally where the behavior is DOM-specific.
- Overlay events must carry stable ids, not DOM nodes, across the Java/JS seam.

## 6. File Ownership

### 6.1 #970-Owned Future Files

These files are owned by the #970 implementation worker unless #969 changes the package layout:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clInteractionOverlayController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clMentionOverlayController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clTaskOverlayController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clReactionOverlayController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clInteractionBlipModel.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clMentionRange.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clMentionCandidate.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clTaskItemModel.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clReactionSummary.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/J2clOverlayMutationFactory.java`
- `j2cl/lit/src/elements/mention-suggestion-popover.js`
- `j2cl/lit/src/elements/task-metadata-popover.js`
- `j2cl/lit/src/elements/reaction-row.js`
- `j2cl/lit/src/elements/reaction-picker-popover.js`
- `j2cl/lit/src/elements/reaction-authors-popover.js`
- `j2cl/lit/src/elements/interaction-overlay-layer.js` if #969 does not already provide a generic overlay-layer primitive
- `j2cl/lit/test/mention-suggestion-popover.test.js`
- `j2cl/lit/test/task-metadata-popover.test.js`
- `j2cl/lit/test/reaction-row.test.js`
- `j2cl/lit/test/reaction-picker-popover.test.js`
- `j2cl/lit/test/reaction-authors-popover.test.js`

### 6.2 Shared Files #970 May Extend Narrowly

These files may be extended with tests and additive APIs:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveDocument.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveFragment.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSearchGateway.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`
- `j2cl/lit/src/index.js`

Rules for shared files:

- Keep existing public methods working unless #969 has already changed them.
- Prefer additive models and adapters over replacing `contentEntries()` during this slice.
- Do not change default GWT route behavior.
- Do not change server persistence or reaction document semantics.

### 6.3 #969-Owned Files

#969 owns compose/editor/toolbar foundation. #970 must consume the APIs #969 lands instead of redefining them.

Likely #969-owned areas:

- Editor host and caret/selection API.
- Compose and reply submit controller APIs.
- Toolbar primitive layout and command dispatch.
- Text mutation plumbing for the editor host.

If #970 needs a method on those APIs, open the smallest follow-up or coordinate through the issue before editing #969-owned files.

### 6.4 #971-Owned Files

#971 owns attachments and remaining rich-edit parity.

#970 must not implement or substantially edit:

- Attachment upload/preview overlays.
- Image thumbnail or file metadata overlays.
- Drag/drop or paste attachment behavior.
- Rich text formatting command overlays.
- Attachment persistence or upload server endpoints.

The shared overlay layer may expose placement/focus primitives for #971, but #971 feature logic stays out of #970.

## 7. Implementation Slices

### Slice 0 - Dependency Intake And Branch Setup

- [ ] Confirm #969 merged and includes the editor/toolbar host required by #970.
- [ ] Rebase or create the implementation worktree from the dependency-resolved base, not from this planning branch if it has gone stale.
- [ ] Re-read the docs listed in section 4.4.
- [ ] Read the final #969 plan and diff; record the editor host and toolbar APIs in the issue.
- [ ] Read #971's current plan; record the shared overlay coordination note in both issue threads if needed.
- [ ] Run `scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave` if local server verification will need file-based persistence.

Exit criteria:

- Issue #970 has an implementation-start comment with base commit, worktree path, branch name, and dependency readiness evidence.

### Slice 1 - Characterize Transport Metadata With Tests

- [ ] Add fixture-driven tests to `SidecarTransportCodecTest` for a selected-wave document containing a mention annotation.
- [ ] Add fixture-driven tests to `SidecarTransportCodecTest` for a task checkbox/document segment with assignee and due-date annotations.
- [ ] Add fixture-driven tests to `SidecarTransportCodecTest` for a reaction data document snapshot.
- [ ] Extend `SidecarSelectedWaveDocument` with additive metadata accessors while preserving `textContent()`.
- [ ] Extend `SidecarTransportCodec` to decode annotation ranges and reaction data-doc payloads before text extraction discards metadata.
- [ ] Add projection tests in `J2clSelectedWaveProjectorTest` for `J2clInteractionBlipModel` creation.

Exit criteria:

- Transport tests fail before implementation and pass after decode/projection changes.
- Existing selected-wave text projection tests still pass.

### Slice 1A - Conditional Overlay-Metadata Endpoint

Execute this slice only if Slice 1 proves selected-wave snapshots cannot expose the annotation or reaction metadata required for parity.

- [ ] Post an issue #970 comment with the exact missing payload evidence, decoded fixture path, and proposed server surface before coding.
- [ ] Add a narrow sidecar overlay-metadata endpoint that returns the same pure overlay models described in section 5.4.
- [ ] Keep the endpoint read-only; overlay mutations must still use the post-#969 editor/write-session path.
- [ ] Add server and J2CL transport tests for the endpoint contract.
- [ ] Document why the websocket selected-wave path was insufficient in the PR body.

Exit criteria:

- The widened server surface is approved in the issue thread and covered by contract tests.

### Slice 2 - Add Overlay Models And Projection

- [ ] Create the pure overlay model classes under `j2cl/src/main/java/org/waveprotocol/box/j2cl/overlay/`.
- [ ] Add `interactionBlips()` to `J2clSelectedWaveModel`.
- [ ] Project mention ranges, task items, and reaction summaries in `J2clSelectedWaveProjector`.
- [ ] Keep `contentEntries()` behavior intact for current read UI compatibility.
- [ ] Add null/empty-state tests for waves without overlays.

Exit criteria:

- Selected-wave model can represent a blip with text, mentions, task metadata, and reaction summaries without requiring Lit.

### Slice 3 - Add Lit Overlay Primitives

- [ ] Add `mention-suggestion-popover.js` with listbox semantics, active option state, and keyboard events.
- [ ] Add `task-metadata-popover.js` with dialog semantics, assignee list/input, due-date field, validation state, Enter/Escape handling, and focus restoration event.
- [ ] Add `reaction-row.js` with chip/add/count/inspect events and stable data attributes.
- [ ] Add `reaction-picker-popover.js` with first-emoji focus and single-active lifecycle behavior.
- [ ] Add `reaction-authors-popover.js` with author list semantics and Escape/outside dismissal.
- [ ] Add `interaction-overlay-layer.js` only if #969 does not already provide an overlay placement/focus primitive.
- [ ] Register components from `j2cl/lit/src/index.js`.
- [ ] Add Web Test Runner tests for keyboard, focus, repeated opening, event payloads, and empty states.
- [ ] Add ARIA/live-region tests for mention candidate-count changes and reaction count changes.

Exit criteria:

- Lit tests pass without a Java/J2CL runtime.
- Components emit events only; they do not mutate websocket or editor state directly.

### Slice 4 - Implement Mention Controller

- [ ] Add `J2clMentionOverlayController` and wire it through `J2clInteractionOverlayController`.
- [ ] Use #969's editor host for trigger detection, caret offset, typed filter text, replacement range, and focus restoration.
- [ ] Filter candidates from selected-wave participant ids and profile metadata available to J2CL.
- [ ] Preserve GWT keyboard behavior from `MentionTriggerHandler.handleMentionMode(...)`.
- [ ] Build mention insertion mutations using #969's mutation API and `AnnotationConstants.MENTION_USER`.
- [ ] Render existing mention annotations from selected-wave projection.
- [ ] Add controller tests for trigger, filtering, Escape, Enter, Tab, Backspace, whitespace dismissal, and repeated trigger after dismissal.

Exit criteria:

- A user can type `@`, pick a participant with keyboard or pointer, and the resulting document state round-trips as a mention annotation.

### Slice 5 - Implement Task Metadata Overlay

- [ ] Add `J2clTaskOverlayController` and wire it through the toolbar/task affordance from #969.
- [ ] Decode task id, assignee, due date, and checked state from selected-wave projection.
- [ ] Render task affordances next to task checkboxes in the J2CL read/edit surface.
- [ ] Implement task metadata overlay open/close with modal focus handling.
- [ ] Validate due-date text before mutation.
- [ ] Write task assignee/due-date changes using the same annotation keys as `TaskDocumentUtil`.
- [ ] Refresh task display after selected-wave update rather than relying on local optimistic-only state.
- [ ] Add tests for current-user-first assignee ordering, invalid due date, Enter submit, Escape cancel, and focus return.

Exit criteria:

- A user can edit task assignee and due date in Lit/J2CL and see the metadata persist through a selected-wave refresh.

### Slice 6 - Implement Reaction Rows, Picker, And Authors Overlay

- [ ] Add `J2clReactionOverlayController` and wire it into each projected interaction blip.
- [ ] Render `reaction-row` for every editable blip with reaction data or add capability.
- [ ] Decode reaction summaries from reaction data documents using the `ReactionDataDocuments` document-id convention.
- [ ] Implement toggle mutation equivalent to `ReactionDocument.toggleReaction(...)`.
- [ ] Implement add picker with single-active lifecycle and first-emoji focus.
- [ ] Implement authors overlay for count click, context menu, long press, and `Shift+F10`.
- [ ] Preserve click suppression after inspect/long press so one gesture does not both inspect and toggle.
- [ ] Add tests for toggle, active state, duplicate user reaction cleanup, picker repeated-open behavior, authors display, long-press suppression, and cleanup on selected-wave change.
- [ ] Add tests for screen-reader announcement text after add/remove reaction actions.

Exit criteria:

- Reaction behavior matches the GWT controller's add/toggle/inspect lifecycle and stays stable across repeated interactions.

### Slice 7 - Integrate With Selected-Wave View And Toolbar

- [ ] Replace the `<pre class="sidecar-selected-entry">` selected-wave rendering with a structure that exposes stable blip anchors while preserving readable fallback text.
- [ ] Attach overlay controllers during selected-wave selection/open and detach them on reset/error/no selection.
- [ ] Wire mention navigation buttons if #969 exposes the toolbar slots corresponding to `ViewToolbar` Prev @ / Next @.
- [ ] Keep sidecar search and selected-wave lifecycle intact.
- [ ] Add teardown tests so handlers do not survive wave changes, controller reset, or websocket reconnect while a popup is open.

Exit criteria:

- Opening a wave, switching waves, and losing/recovering websocket state do not leave stale overlay DOM or handlers.

### Slice 8 - Accessibility, Styling, And Browser Verification

- [ ] Apply design-packet overlay styling and focus ring rules.
- [ ] Verify keyboard-only mention, task, and reaction flows in browser.
- [ ] Verify repeated interactions: open mention then Escape then reopen; open reaction picker twice; inspect then toggle; edit task then reopen.
- [ ] Verify mobile viewport behavior for reaction long press and popover placement.
- [ ] Add the changelog fragment and regenerate/validate changelog JSON.
- [ ] Record exact commands and browser checks in the GitHub issue.

Exit criteria:

- Local verification evidence is posted to issue #970 and included in the PR body.

## 8. Verification Commands

Run the narrow checks as each slice lands, then run the full local verification before PR creation.

### 8.1 Static And Plan Hygiene

```bash
git diff --check
```

### 8.2 J2CL Java Tests

The overlay controller test classes listed here are introduced by slices 4-6; before those slices exist, run the subset that exists on the branch.

```bash
cd j2cl
./mvnw test -Dtest=SidecarTransportCodecTest,J2clSelectedWaveProjectorTest,J2clInteractionOverlayControllerTest,J2clMentionOverlayControllerTest,J2clTaskOverlayControllerTest,J2clReactionOverlayControllerTest
```

### 8.3 Lit Tests And Build

```bash
cd j2cl/lit
npm test
npm run build
```

### 8.4 Legacy Guardrail Tests

Use existing tests as guardrails for behavior that should remain compatible:

```bash
sbt -batch "testOnly org.waveprotocol.wave.client.wavepanel.impl.reactions.ReactionPickerPopupTest org.waveprotocol.wave.client.wavepanel.impl.reactions.ReactionRowRendererTest org.waveprotocol.wave.model.conversation.ReactionDocumentTest org.waveprotocol.wave.model.conversation.ReactionDataDocumentsTest"
```

If task or mention utility tests exist on the dependency-resolved branch, include them in the same `testOnly` command. If they do not exist, cover the behavior in new J2CL tests rather than adding broad GWT harness work to this slice.

If the dependency-resolved branch does not use `sbt` for these guardrails, replace the command with the repo's actual legacy test runner and record the substitution in issue #970.

### 8.5 Changelog

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
```

### 8.6 Local Server And Browser Sanity

Use the repo's current worktree boot/runbook commands after syncing file-store state:

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
bash scripts/worktree-boot.sh --port 9970
PORT=9970 bash scripts/wave-smoke.sh start
PORT=9970 bash scripts/wave-smoke.sh check
```

Browser checks against the dependency-resolved J2CL root route. Use `http://localhost:9970/?view=j2cl-root` unless #969 changes the route contract:

- Desktop viewport: create/open a wave, type `@`, filter candidates, pick with Enter, reopen and dismiss with Escape.
- Desktop viewport: insert/open a task metadata affordance, edit assignee and due date, validate invalid due date, submit with Enter, cancel with Escape.
- Desktop viewport: add a reaction, toggle it off/on, inspect authors with count click and `Shift+F10`, open picker repeatedly.
- Mobile viewport: reaction add/toggle and long-press inspect do not double-fire.
- Wave switch: overlays close and handlers detach when selecting another wave.

Stop the server after verification:

```bash
PORT=9970 bash scripts/wave-smoke.sh stop
```

## 9. Risks And Mitigations

- **Risk: #969 editor API lands differently than assumed.** Mitigation: require the re-read gate and adapt to #969's public host API before coding.
- **Risk: selected-wave transport lacks annotation or reaction data.** Mitigation: characterize with failing codec tests first; only add a server overlay-metadata endpoint after issue-comment approval.
- **Risk: reaction repeated-interaction behavior regresses.** Mitigation: port `ReactionPopupLifecycle` semantics explicitly and add repeated-open tests before controller integration.
- **Risk: long press causes duplicate inspect/toggle events.** Mitigation: preserve suppression-window logic from `ReactionController` and cover inspect-then-click flows in tests.
- **Risk: modal and non-modal overlays conflict.** Mitigation: centralize active overlay state in `J2clInteractionOverlayController`; task dialog is modal, mention/reaction popovers are non-modal.
- **Risk: #971 needs the same overlay layer.** Mitigation: keep `interaction-overlay-layer` generic and feature-free; document focus/placement events for #971 reuse.
- **Risk: default GWT root behavior changes accidentally.** Mitigation: keep all wiring behind the J2CL root path and local browser-check both `/?view=j2cl-root` and `/`.
- **Risk: profile data is unavailable for mention candidates.** Mitigation: ship address-label autocomplete first if the #969 read model lacks profile details; document profile enrichment as a follow-up instead of blocking overlay parity.
- **Risk: websocket reconnect occurs while a popup is open.** Mitigation: close non-modal overlays and preserve task dialog validation state only if the selected blip still exists after reconnect; otherwise restore focus to the selected-wave container.

## 10. Non-Goals

- No full editor rewrite.
- No attachment upload, attachment preview, attachment metadata, or image thumbnail overlay work.
- No rich-text formatting command parity beyond the overlay hooks needed for mention/task/reaction behavior.
- No replacement of legacy GWT StageThree.
- No default-route cutover.
- No broad server persistence migration.
- No new reaction/task document schema unless transport characterization proves the current data cannot express required parity.

## 11. Coordination Rules

### 11.1 With #969

- #969 owns compose/editor/toolbar foundation.
- #970 starts only after #969 merges.
- #970 may request additive editor host APIs, but must not fork or replace #969's editor.
- If #970 needs to modify #969-owned files, the worker must post the intended ownership overlap to issues #969 and #970 before editing.

### 11.2 With #971

- #971 owns attachments and rich-edit parity.
- #970 owns mention/task/reaction overlay feature logic.
- Shared overlay placement/focus primitives must be generic and documented.
- #970 must not add attachment-specific event names or data fields to shared overlay primitives.
- #971 must consume #970's overlay lifecycle rather than adding a second global popup lifecycle.

### 11.3 With Review And PR Workflow

- Implementation PR must reference issue #970 and parent tracker #904/#960.
- Issue #970 must receive comments for dependency readiness, implementation start, verification commands/results, review findings/resolution, commit SHA, and PR URL.
- Review conversations must be addressed technically before resolution.
- Do not create a PR from this planning lane.

## 12. Implementation Readiness Verdict

This plan is dependency-ready but not implementation-unblocked. The remaining blocker is #969: until the J2CL compose/editor/toolbar parity foundation has merged, #970 cannot safely bind mention trigger handling, task toolbar affordances, or overlay mutation framing without inventing temporary editor APIs.

After #969 merges, the implementation can proceed slice-by-slice from section 7.

## 13. Planning Self-Review

Planning-worker review result before external review:

- Dependency gate is explicit: implementation is blocked until #969 merges.
- Scope is bounded to mention, task, reaction, and generic interaction-overlay primitives.
- #971 attachment/rich-edit work is excluded and coordination rules are stated.
- Current GWT seams and current J2CL gaps are cited with file and method references.
- Verification commands include transport, projection, Lit, legacy guardrail, changelog, local server, and browser checks.
- No product code changes are part of this planning lane.
- External review follow-ups were incorporated: conditional transport fallback slice, dependency-resolved route/test-runner validation, tentative overlay-layer ownership, profile-data fallback, ARIA/live-region coverage, and reconnect-while-popup-open coverage.
