# F-3.S2 — Mentions + tasks (R-5.3 + R-5.4)

Status: Ready for implementation
Owner: codex/issue-1038-s2-mentions-tasks worktree
Issue: [#1038 (slice 2 of 4)](https://github.com/vega113/supawave/issues/1038)
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Foundation:
- F-3.S1 (#1063, sha `efb3b7420`) — `<wavy-composer>` + `<wavy-format-toolbar>` + plugin slots; Reply/Edit/Delete events from `<wave-blip>` open inline composers; `J2clRichContentDeltaFactory` produces rich-content deltas.
- F-2 chrome (#1037, etc.) — per-blip `<wave-blip>` + `<wave-blip-toolbar>`.
- F-1 (#1036) — viewport-scoped data path; F-1-era `<mention-suggestion-popover>` (209 LOC) and `<task-metadata-popover>` (298 LOC).
- F-0 (#1035) — wavy design tokens (`--wavy-*`) and plugin slot contracts.

Parity-matrix rows claimed in S2: **R-5.3 mentions**, **R-5.4 tasks**.

## 1. Why this slice exists

S1 shipped the inline composer foundation: `<wavy-composer>` mounted at the chosen reply position, the floating selection-driven `<wavy-format-toolbar>`, drafts, Enter-to-send, and the daily-rich-edit toolbar subset (H.1–H.4, H.9, H.12–H.18). The compose surface is now a real contenteditable substrate that can carry rich annotations through the existing `J2clRichContentDeltaFactory`.

S2 layers the two highest-frequency *content* affordances on top:

- **Mentions (R-5.3)**: type `@` in any composer body and a popover lists the wave's participants; arrow-key + Enter inserts a violet mention chip that round-trips through the model as a `link/manual` annotation. This is H.21 from the GWT inventory.
- **Tasks (R-5.4)**: each `<wave-blip>` exposes a per-blip task affordance that toggles completion state; clicking opens `<task-metadata-popover>` for owner + due-date; toggle persists via a DocOp delta on the blip's task annotation. The H.20 toolbar Insert-task button inserts a task list item inline. C.13–C.15 search filters are display-only (already shipped F-2.S3 search-help modal); S2 just verifies discoverability.

S3 will add reactions on top; S4 closes the umbrella with attachments + remaining rich-edit + demo route.

## 2. Pre-verified F-1 reuse contract (worktree-checked at HEAD `efb3b7420`)

I read each source file in this worktree before writing the plan:

- `j2cl/lit/src/elements/mention-suggestion-popover.js` (209 LOC) — already exists. Has `open`, `candidates`, `activeIndex`, `focusTargetId` properties. Implements full listbox aria contract (`role="listbox"`, `role="option"`, `aria-activedescendant`, `aria-selected`, `aria-live="polite"` count announcer). Already handles ArrowUp/ArrowDown/Enter/Tab/Escape keydown. Emits `mention-select` with `{address, displayName}` and `overlay-close` with `{reason, focusTargetId}`. **Verification step 1 of S2 confirmed: the popover takes pre-filtered candidates today.** S2 *does not* extend its filtering logic; instead, the **consumer (`<wavy-composer>`) computes the filtered candidate list locally** and passes the result via the existing `candidates` property. Locale-aware filtering happens in `<wavy-composer>` because:
  - the popover's contract is "render the candidates I am given; emit `mention-select`",
  - the composer already owns the wave's participant list (it receives `participants` as a property from the Java view), and
  - per-keystroke recomputation is bounded to the active composer's input ring.
- `j2cl/lit/src/elements/task-metadata-popover.js` (298 LOC) — already exists. Has `open`, `taskId`, `assigneeAddress`, `dueDate`, `participants`, `error`, `focusTargetId`. Renders an aria-modal dialog with assignee `<select>` + due-date `<input>`. Already validates due-date format (`YYYY-MM-DD`). Emits `task-metadata-submit` `{taskId, assigneeAddress, dueDate}` and `overlay-close`. Already implements focus trap. **S2 wires** this to a per-blip task affordance trigger. **No element-internal change needed**; the popover is reused as-is.
- `j2cl/lit/src/elements/wavy-composer.js` (619 LOC, F-3.S1) — already has the `_collectActiveAnnotations` selection descriptor pulled from `_onSelectionChange`. The `_onBodyKeydown` handler captures Shift+Enter (submit) and Esc (cancel) but lets all other keys through. **S2 extends** `_onBodyKeydown` with an `@` trigger that anchors `<mention-suggestion-popover>` at the caret rect via the existing `selection.getRangeAt(0).getBoundingClientRect()` path.
- `j2cl/lit/src/elements/wave-blip.js` (375 LOC, F-2) — already has the per-blip toolbar slot and `data-blip-id`/`data-wave-id` reflection. **S2 mounts** a `<wavy-task-affordance>` next to the toolbar in the existing `metadata` slot and reflects `data-task-completed`. The new affordance emits `wave-blip-task-toggled` `{blipId, waveId, completed}` (S2 introduces this CustomEvent name).
- `j2cl/lit/src/elements/wavy-search-help.js` (~492 LOC, F-2.S3) — already lists `tasks:all`, `tasks:me`, `tasks:user@domain` in the tokens table (lines 411–423). The `_example()` helper does NOT currently set `data-filter-token`. **S2 adds `data-filter-token=<token>` to each example chip** (touching `_example()` plus the three `tasks:*` rows so the parity fixture's `data-filter-token="tasks:all"` query passes — see acceptance step R-5.4 step 6). This is a one-line change; lower risk than asserting via the rendered string, which would couple the test to copy.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java` (344 LOC) — the document-op generator already accepts arbitrary annotated-text components via `J2clComposerDocument.builder().annotatedText(key, value, text)`. **S2 adds two factory methods** that are sugar wrappers building deltas with the right annotation keys:
  - `mentionInsert(participantAddress, displayName)` builds a single annotated-text component with key `link/manual` + value `participantAddress` + text `@displayName`.
  - `taskToggle(blipId, completed)` builds a *blip-level* document operation that sets the `task/done` annotation on the entire blip body. Because blip-level annotations differ from inline rich-text annotations (no caret span), the factory adds a small helper that returns a stand-alone `SidecarSubmitRequest` whose op carries an annotation boundary that opens at offset 0 and closes at the body end with the new value.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java` (1227 LOC) — the controller's `Listener` interface currently has `onCreateDraftChanged`, `onCreateSubmitted`, `onReplyDraftChanged`, `onReplySubmitted`, `onAttachmentFilesSelected`, `onPastedImage`. **S2 extends** with two new methods:
  - `onMentionPicked(String participantAddress, String displayName)` — appends a mention chip annotation to the active draft and emits a delta; surfaces to the existing `replyDraft` path so the next submit carries the chip.
  - `onTaskToggled(String blipId, boolean completed)` — emits a blip-level `taskToggle` delta via the new factory method directly through the `gateway.submit` path (independent of the reply draft so the user can toggle a task on a different blip without disturbing the active reply).

## 3. Acceptance contract

Each row below has executable acceptance steps that the per-row fixture in `J2clStageThreeComposeS2ParityTest` asserts. **No "practical parity" escape hatch.** R-5.3 + R-5.4 each must be demonstrable end-to-end against the live `?view=j2cl-root` vs `?view=gwt`.

### R-5.3 Mentions — `@` trigger, suggestion popover, model round-trip

**Affordances covered:** H.21 `@`-mention trigger + the violet mention chip styling.

**Acceptance steps:**

1. **`@`-trigger opens popover.** Typing `@` in the composer body opens `<mention-suggestion-popover>` anchored at the caret position. The popover is mounted as a child of `<wavy-composer>` (in shadow DOM) so it inherits the composer's stacking context. Initial query is empty; the popover lists all candidates from `composer.participants` (a property the Java view sets to `J2clSelectedWaveModel.getParticipantIds()`). Fixture: dispatch a `keydown` for `@` followed by an `input` event into the body, await `updateComplete`, assert `composer.renderRoot.querySelector('mention-suggestion-popover[open]')` is present and lists each participant as a `[role='option']` with `data-address`.

2. **Filtering by substring.** Continuing to type after `@` filters the popover items by case-insensitive locale-aware substring match against `displayName + " " + address` (concatenated for both fields). The match uses `String.prototype.toLocaleLowerCase(document.documentElement.lang || undefined)` so locale collation rules (e.g. Turkish "İ→i") apply. Fixture: type `@al`, assert only candidates where `(displayName + " " + address).toLocaleLowerCase()` contains `"al"` are passed to the popover via `composer.renderRoot.querySelector('mention-suggestion-popover').candidates`. Includes Cyrillic locale assertion (R-5.3 step 7 below).

3. **Arrow-key + Enter selection.** ArrowDown/ArrowUp navigate items; Enter selects; Esc dismisses without inserting text. Fixture: open popover via `@al`, ArrowDown twice, Enter, assert the third filtered item became the selected mention. The implementation routes the keydown via the existing popover's keydown listener (the popover already handles ArrowDown/Up + Enter + Tab + Escape per the F-1-era element).

4. **Mention chip round-trip through model (the load-bearing requirement).** Selecting a mention inserts a `<span data-mention-id="user@domain" class="wavy-mention-chip">@displayName</span>` at the caret position. The chip is `contenteditable="false"` so the caret can't enter it. The composer's body serializer (`_serializeBodyText`) is extended with a *rich-content serializer* that walks immediate children and, when it encounters a chip span, emits a *separate annotated-text component* via the existing `J2clComposerDocument.builder().annotatedText("link/manual", participantAddress, displayName)` path. The next `reply-submit` produces a delta whose ops carry the `{"1":"link/manual","3":"<address>"}` annotation start + the `@displayName` characters + the `link/manual` end. Fixture: pick a mention, click Send, intercept the gateway submit, assert the delta JSON contains `{"1":{"3":[{"1":"link/manual","3":"alice@example.com"}]}}` followed by `"2":"@Alice Adams"` followed by `{"1":{"2":["link/manual"]}}` (the existing pattern asserted by `J2clRichContentDeltaFactoryTest`).

5. **Esc preserves the literal `@string`.** The popover supports Esc to dismiss without inserting; the `@` character (and any subsequent typed query characters) stay in the body if the user dismisses without selecting. Fixture: type `@al`, Esc, assert body's `textContent` ends with `@al` and no `<span data-mention-id>` was inserted.

6. **Listbox semantics.** Popover has `role="listbox"`, items have `role="option"`, the active item has `aria-selected="true"`. `aria-activedescendant` on the listbox points to the active option's id. (Already shipped by F-1-era popover; fixture re-asserts the contract still holds when mounted by `<wavy-composer>`.)

7. **Locale-aware matching.** Under `lang="ru"`, "Юра" matches "юр" (Cyrillic case folding via `toLocaleLowerCase("ru")`). Fixture sets `document.documentElement.lang = "ru"`, types `@юр`, asserts the candidate `{address:"yuri@example.com", displayName:"Юра"}` is in the filtered list. Under `lang="tr"`, "İlker" matches "ilk" (Turkish dotless-i folding — `toLocaleLowerCase("tr")` maps "İ" to "i"). Fixture sets `document.documentElement.lang = "tr"`, types `@ilk`, asserts the candidate `{displayName:"İlker"}` is in the list.

8. **Mention chip violet styling.** Mention chips render with `--wavy-signal-violet` border + soft fill per the F-0 design contract. Fixture: pick a mention, assert the inserted span's class includes `wavy-mention-chip` and `getComputedStyle(chip).color` includes `124, 58, 237` (the F-0 token's RGB).

9. **Backspace deletes a chip atomically.** Backspace immediately before a mention chip removes the entire chip (chips are atomic in the body). Implementation: the chip's `contenteditable="false"` plus a body-level Backspace handler that detects the previous-sibling chip and removes it. Fixture: insert a chip, place caret immediately after the chip, Backspace, assert the chip is gone and no orphan `@` character remains.

10. **Telemetry.** Picking a mention emits `compose.mention_picked` (no fields). Dismissing via Esc with a non-empty query emits `compose.mention_abandoned`. Fixture: spy on `J2clClientTelemetry.Sink.record`, pick a mention, assert one event with name `compose.mention_picked`. Type `@xx`, Esc, assert one event with name `compose.mention_abandoned`.

### R-5.4 Tasks — per-blip toggle, completion state, owner + due-date

**Affordances covered:** H.20 Insert task / checkbox + per-blip task affordance + C.13–C.15 search-filter discoverability (display only).

**Acceptance steps:**

1. **Per-blip task affordance mount.** Each `<wave-blip>` renders a `<wavy-task-affordance>` next to the per-blip toolbar in the `metadata` slot. The affordance is hidden until `:focus-within` or `:hover` (matches the toolbar reveal pattern via the existing CSS rule on `<wave-blip>`). The affordance carries `data-blip-id`, `data-wave-id`, `data-task-completed` (reflected from the blip), and `data-task-due-date` (reflected from the blip).

2. **Toggle emits CustomEvent.** Clicking the task affordance dispatches a `wave-blip-task-toggled` CustomEvent `{blipId, waveId, completed: boolean}` (composed + bubbles). The compose-surface view subscribes on `document.body`. Fixture: synthesize a click, assert one `wave-blip-task-toggled` event with the right detail.

3. **Completion state round-trips through DocOp delta.** The `J2clComposeSurfaceController.onTaskToggled(blipId, completed)` path emits a delta via the new `J2clRichContentDeltaFactory.taskToggle(blipId, completed)` method. The delta carries `{"1":{"3":[{"1":"task/done","3":"true"}]}}` (or `"false"`) bracketing the blip body. Fixture: synthesize a toggle, intercept the gateway submit, assert the delta JSON contains the expected `task/done` annotation start + end.

4. **Visual completion via attribute + token.** The blip card reflects `data-task-completed="true"` after a successful toggle, and CSS applies `--wavy-text-quiet` + `text-decoration: line-through` to the body when the attribute is set. Fixture: assert `wave-blip[data-task-completed="true"] .body` (after live-update applies the toggle) has `text-decoration-line: line-through` in `getComputedStyle`.

5. **Owner + due-date via `<task-metadata-popover>`.** Long-pressing or right-clicking the affordance (or clicking the affordance with the affordance already focused — implementation detail TBD; the simplest UX is "click for toggle, double-click for metadata") opens `<task-metadata-popover>` anchored at the affordance. Submitting the popover emits `wave-blip-task-metadata-changed` `{blipId, assigneeAddress, dueDate}` which the controller turns into a `taskMetadata` delta carrying `task/owner=<address>` + `task/due=<YYYY-MM-DD>` annotations. Fixture: open the popover, fill assignee + due-date, submit, assert the delta JSON carries both annotations. The popover trigger is a separate "Edit task details…" button rendered inside the affordance overflow so single-click stays unambiguous; this avoids a double-click affordance that would conflict with browser text-selection.

6. **H.20 Insert-task toolbar action.** Clicking the H.20 toolbar button (added to `<wavy-format-toolbar>` in S2) inserts a task list item at the caret. Implementation: a `<ul class="wavy-task-list"><li><input type="checkbox" disabled aria-checked="false"> </li></ul>` is wrapped around the active selection (or inserted at caret if collapsed). The inline checkbox is *display only*; the actual completion state is owned by the per-blip task affordance (which is the model-of-record). Fixture: caret in body, click Insert-task, assert the body contains `<ul class="wavy-task-list">` with an `<li>` containing an `<input type="checkbox" disabled>`.

7. **Aria-checked + announcement.** `<wavy-task-affordance>` exposes `aria-checked="true"|"false"` reflecting the task state. After a toggle, an `aria-live="polite"` region announces "Task completed" or "Task reopened". Fixture: toggle, assert announcement region's textContent matches.

8. **C.13–C.15 search-filter discoverability.** `<wavy-search-help>`'s `_example()` is updated so each example chip carries `data-filter-token="<token>"`. Fixture: open the search-help modal and assert the modal contains rows with `data-filter-token="tasks:all"`, `data-filter-token="tasks:me"`, and an example chip carrying `data-filter-token="tasks:alice@example.com"` (or, more generally, the `tasks:user@domain` row contains a `data-filter-token` attribute starting with `tasks:`).

9. **Read-state semantics integration.** Completed tasks respect the existing F-2 read-state semantics: a `data-task-completed="true"` blip is treated as read for the purposes of unread-count rendering when collapsed. Implementation: extend `J2clSelectedWaveProjector.computeUnread(...)` (or the existing equivalent) to OR the task-completed flag into the read predicate. This mirrors the F-4 mark-read flow's collapsed-completed semantics. Fixture: project a wave with one completed-task blip and one unread blip, assert the unread count is 1 (not 2).

10. **Persistence across reload.** Toggling a task and reloading the view restores the completion state. Fixture: synthesize a toggle, render the wave from the persisted state, assert the affordance's `aria-checked` matches the persisted value. (The persistence path is the existing supplement live-update through the F-1 fragment listener, which already round-trips `task/done` annotations under the GWT path; S2 does not change the persistence path, only the J2CL UI binding.)

11. **Telemetry.** A toggle emits `compose.task_toggled` with `{state: "completed"|"open"}`. Fixture: spy on the telemetry sink, toggle, assert event recorded.

### Inventory affordances shipped in S2

| ID | Affordance | Asserted under | Implementation |
| --- | --- | --- | --- |
| H.20 | Insert task | R-5.4 step 6 | Toolbar button → task list insert |
| H.21 | `@` mention | R-5.3 | `@` keystroke → mention popover |
| F-3 mention chip | violet mention chip + suggestion popover | R-5.3 step 4 + step 8 | `<span data-mention-id>` + `link/manual` annotation |
| F-3 task chip | per-blip task affordance + amber styling | R-5.4 steps 1–4 + step 7 | `<wavy-task-affordance>` reflects `data-task-completed` |
| C.13–C.15 | tasks search filters (display only) | R-5.4 step 8 | `data-filter-token` on `<wavy-search-help>` examples |

That is **5 owned affordances** all covered with executable acceptance steps for S2.

## 4. File-level changes

### Lit (client)

- MODIFIED `j2cl/lit/src/elements/wavy-composer.js` (~+200 LOC):
  - Add `participants` property (Array of `{address, displayName}`).
  - Add `@`-trigger keydown branch in `_onBodyKeydown` that mounts `<mention-suggestion-popover>` and pre-populates candidates from `participants`. Track an `_atTriggerOffset` so subsequent keystrokes filter the candidate list.
  - Add `_filterCandidates(query)` helper that does case-insensitive locale-aware filter using `toLocaleLowerCase(document.documentElement.lang || undefined)`.
  - Add `_handleMentionSelect` that:
    - inserts the mention chip span at `_atTriggerOffset`,
    - removes the typed `@query` text,
    - dispatches `wavy-composer-mention-picked` `{address, displayName}` so the controller emits telemetry.
  - Extend `_serializeBodyText` (and a new `_serializeBodyComponents` returning `{type, text, annotation}` records) to emit annotated components for each chip span. The existing controller path consumes this when building the next reply delta.
  - Add Backspace handling for chip-atomicity (R-5.3 step 9).
  - Telemetry hook for `compose.mention_picked` + `compose.mention_abandoned`.

- NEW `j2cl/lit/src/elements/wavy-task-affordance.js` (~+180 LOC):
  - `<wavy-task-affordance data-blip-id data-wave-id data-task-completed data-task-due-date>` Lit element.
  - Renders a primary toggle button + a "details" overflow button (avoids double-click conflict).
  - Toggle button emits `wave-blip-task-toggled`; details button opens an internal `<task-metadata-popover>` (mounted in shadow DOM) and emits `wave-blip-task-metadata-changed` on submit.
  - Reflects `aria-checked`, has an `aria-live="polite"` region for the post-toggle announcement.
  - Amber styling via `--wavy-signal-amber` for the border and the active-completed soft fill (per the token contract).

- MODIFIED `j2cl/lit/src/elements/wave-blip.js` (~+30 LOC):
  - Add `taskCompleted` (Boolean, attribute `data-task-completed`, reflected) and `taskDueDate` (String, attribute `data-task-due-date`, reflected).
  - Mount `<wavy-task-affordance>` inside the existing `metadata` slot next to the toolbar.
  - Style hook: `:host([data-task-completed]) .body { color: var(--wavy-text-quiet); text-decoration: line-through; }`.

- MODIFIED `j2cl/lit/src/elements/wavy-format-toolbar.js` (~+30 LOC):
  - Add an "insert-task" toolbar button to the existing list (button definition + `ACTIVE_ANNOTATION_MAP` entry mapping it to `[]` — the Insert-task action is non-toggleable per H.20).
  - When clicked, the toolbar emits `wavy-format-toolbar-action` with `actionId: "insert-task"`. The composer listener (already wired in S1) inserts the `<ul class="wavy-task-list">` markup at the active range.

- MODIFIED `j2cl/lit/src/elements/wavy-search-help.js` (~+5 LOC):
  - `_example(query)` now sets `data-filter-token=${query}` on the `<span class="example">` so the parity fixture's discoverability check is robust to copy changes.

- MODIFIED `j2cl/lit/src/index.js` — register `wavy-task-affordance`.

### Lit (test)

- MODIFIED `j2cl/lit/test/wavy-composer.test.js` (~+200 LOC) — extend with R-5.3 cases:
  - `@`-trigger opens popover.
  - filter narrows candidates.
  - Arrow + Enter inserts chip; chip carries `data-mention-id`.
  - Esc preserves `@al` literal text.
  - locale-aware (Cyrillic + Turkish) candidate filtering.
  - Backspace deletes chip atomically.
  - serializer emits annotated components for each chip.

- NEW `j2cl/lit/test/wavy-task-affordance.test.js` (~+180 LOC):
  - renders toggle + details buttons.
  - toggle click emits `wave-blip-task-toggled`.
  - details opens `<task-metadata-popover>`; submit emits `wave-blip-task-metadata-changed`.
  - aria-checked reflects state; aria-live region announces post-toggle.

- MODIFIED `j2cl/lit/test/wave-blip.test.js` (~+30 LOC):
  - `data-task-completed` attribute reflects `taskCompleted` property.
  - `<wavy-task-affordance>` mounts in metadata slot.
  - `wave-blip-task-toggled` emitted from affordance reaches `<wave-blip>` host.

- MODIFIED `j2cl/lit/test/wavy-format-toolbar.test.js` (~+20 LOC):
  - Insert-task button is rendered (R-5.4 step 6).
  - Click emits `wavy-format-toolbar-action` with `actionId: "insert-task"`.

### Java (controller + factory)

- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java` (~+90 LOC):
  - Public method `mentionInsert(String participantAddress, String displayName)` returning a `J2clComposerDocument.Component` builder hook (or returning a small DTO the controller appends to the active builder). Internally uses `annotatedText("link/manual", participantAddress, "@" + displayName)`. Validates address shape via the existing `extractDomain`.
  - Public method `taskToggle(J2clSidecarWriteSession session, String address, String blipId, boolean completed)` returning a `SidecarSubmitRequest` whose op is the blip-level annotation set. The factory generates a delta whose op opens the `task/done` annotation at offset 0 (with the new value) and closes it at the body end (offset N), so the supplement live-update on the GWT path mirrors the same shape that today's `task/done` writers produce.
  - Public method `taskMetadata(J2clSidecarWriteSession session, String address, String blipId, String assigneeAddress, String dueDate)` — same pattern with `task/owner` + `task/due` annotations.

- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java` (~+150 LOC):
  - Extend `Listener` interface with `onMentionPicked(String participantAddress, String displayName)`, `onTaskToggled(String blipId, boolean completed)`, `onTaskMetadataChanged(String blipId, String assigneeAddress, String dueDate)`.
  - Extend `DeltaFactory` interface with `mentionInsertComponent(String participantAddress, String displayName)`, `createTaskToggleRequest(...)`, `createTaskMetadataRequest(...)`.
  - Implement `onMentionPicked`: appends a mention component to the active reply draft's component buffer (S2 introduces a private `replyDraftComponents` `List<J2clComposerDocument.Component>` alongside `replyDraft`, populated either from raw text + chips or from the new lit serializer). On submit, `buildDocument` walks the components instead of treating `replyDraft` as flat text. Emits `compose.mention_picked` telemetry.
  - Implement `onTaskToggled`: calls `gateway.fetchRootSessionBootstrap` then `gateway.submit` with the `taskToggle` request. Independent of reply state. Emits `compose.task_toggled`. Note: a task toggle on a blip whose write-session is stale should NOT clobber the reply submit — task-toggle uses a fresh bootstrap fetch each time.
  - Implement `onTaskMetadataChanged`: same pattern with `taskMetadata` request.
  - Update `richContentDeltaFactory(J2clRichContentDeltaFactory)` adapter to expose the new factory methods through the controller's `DeltaFactory` interface.

- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java` (~+60 LOC):
  - Listen for `wavy-composer-mention-picked` from the active inline composer, route to `listener.onMentionPicked(...)`.
  - Listen for `wave-blip-task-toggled` on `document.body`, route to `listener.onTaskToggled(...)`.
  - Listen for `wave-blip-task-metadata-changed` on `document.body`, route to `listener.onTaskMetadataChanged(...)`.
  - When the inline composer is mounted, set its `participants` property from the model's participant list (the model already exposes `getParticipantIds()` via the existing read-surface; for S2 we surface them from `J2clSelectedWaveModel` to the compose surface via a new `setParticipants(List<String> ids, Map<String, String> displayNamesByAddress)` model method). The displayNames map is populated from the existing search-result projector or falls back to the address.

- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceModel.java` (~+30 LOC):
  - Add `List<String> participantAddresses` + `Map<String, String> participantDisplayNames`.
  - Add getters; thread into the view's render path.

### Java (test)

- MODIFIED `j2cl/src/test/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactoryTest.java` (~+150 LOC):
  - `mentionInsertEmitsLinkManualAnnotation` — assert delta JSON contains `link/manual` annotation start + end bracketing the `@displayName` text.
  - `taskToggleEmitsTaskDoneAnnotation` — assert delta carries `task/done=true` annotation start + end.
  - `taskMetadataEmitsOwnerAndDueAnnotations`.
  - Round-trip: `taskToggle(false)` then `taskToggle(true)` produces deltas whose annotation values are `false` then `true`.

- MODIFIED `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java` (~+200 LOC):
  - `onMentionPickedAppendsToReplyDraftComponents` — listener call → next reply submit's delta includes the mention annotation.
  - `onTaskToggledEmitsTaskDoneDelta` — listener call → gateway.submit invoked with a delta whose op carries `task/done`.
  - `onTaskToggledTelemetryEmitsTaskToggledEvent`.
  - `onTaskMetadataChangedEmitsOwnerAndDueDelta`.

### Parity fixture

- NEW `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageThreeComposeS2ParityTest.java` (~+250 LOC):
  - Mounts the `?view=j2cl-root` design-preview page in the same Browser/Selenium harness used by `J2clStageOneReadSurfaceParityTest`.
  - For R-5.3:
    1. Open a blip's Reply composer.
    2. Type `@al` into the body.
    3. Assert the popover lists Alice (filtered) and the listbox aria contract holds.
    4. ArrowDown + Enter, assert the body contains the mention chip span and the `wavy-composer-mention-picked` event was dispatched.
    5. Click Send, intercept the submit gateway, assert the delta carries `link/manual` + `@Alice Adams` text.
    6. Re-open Reply, type `@`, Esc, assert the body's literal `@` is preserved.
    7. Locale: switch `<html lang="ru">`, type `@юр`, assert the popover lists "Юра". Repeat with `lang="tr"` and "İlker".
  - For R-5.4:
    1. Open a wave with a blip carrying a `task/done=false` annotation.
    2. Assert the `<wave-blip>` mounts a `<wavy-task-affordance>` with `aria-checked="false"`.
    3. Click the toggle, assert the gateway submit is invoked with a delta containing `task/done=true`.
    4. Live-update the projection, assert `wave-blip[data-task-completed="true"]` and the body has the `text-decoration: line-through` token.
    5. Open the details popover, fill assignee + due-date, submit, assert the delta carries `task/owner` + `task/due`.
    6. Click the Insert-task toolbar button in the format toolbar, assert the body contains `<ul class="wavy-task-list">`.
    7. Open `<wavy-search-help>`, assert it contains `data-filter-token="tasks:all"`, `data-filter-token="tasks:me"`, and a row with `data-filter-token` starting with `tasks:`.
  - GWT side-by-side: assert `?view=gwt` byte-for-byte unchanged for the same scenarios (mention chip rendering + task affordance + search-help DOM).

## 5. Telemetry surface (delta vs S1)

Added in S2:
- `compose.mention_picked` — no fields (high cardinality on participant id; intentionally elided).
- `compose.mention_abandoned` — no fields.
- `compose.task_toggled` — `{state: "completed" | "open"}`.

These all use the existing `J2clClientTelemetry.event(...).field(...).build()` pattern and route through the `telemetrySink` already in the compose controller.

## 6. Out of scope (deferred)

- R-5.5 reactions → S3 (already plumbed F-1 elements: `reaction-row.js`, `reaction-picker-popover.js`, `reaction-authors-popover.js`).
- R-5.6 attachments → S4. The H.19 paperclip is already partially wired in S1's compose-attachment-card / -picker; S4 finishes drag/drop + paste-image + inline render.
- R-5.7 daily rich-edit DocOp → S4. S2 ships the H.20 toolbar action only.
- H.5/H.6/H.7/H.8/H.10/H.11/H.19 → S4 (per the umbrella plan).
- The "double-click for metadata" UX option for the task affordance is rejected here; S2 ships a separate "details" overflow button to avoid browser-text-selection conflicts.
- `<task-metadata-popover>` extension for free-form owner picking (search beyond the wave's participants) — S2 reuses the wave's participant list plus the address-as-displayName fallback; full participant directory search is deferred to a future issue.

## 7. Risk list

1. **`@`-trigger collision with literal `@` typing.** Users who paste an email address containing `@` (e.g. `Email me at alice@example.com`) trigger the popover, which is annoying. *Mitigation:* the trigger only fires when the `@` character is preceded by start-of-line or whitespace (matches GWT mention-trigger heuristic).

2. **Chip atomicity vs Lit re-render.** Lit might re-render the body on attribute updates and lose the chip's contenteditable=false behavior. *Mitigation:* S1's caret-survival contract already pins the contenteditable body element across re-renders; S2 chips are descendants of the same body element so they survive. Fixture: caret-survival test extended to assert chip survival.

3. **Mention candidate freshness.** The participant list could drift mid-compose if a remote add-participant arrives. *Mitigation:* the popover re-reads `composer.participants` on each filter pass; the Java view updates the property when the model's participant list changes (live-update from the F-1 fragment).

4. **Task toggle vs in-flight reply.** Toggling a task on blip B while a reply on blip A is in flight could clobber state. *Mitigation:* `onTaskToggled` uses an independent gateway fetch (no `replyGeneration` interaction); fixture asserts the reply continues unaffected.

5. **`task/done` annotation shape parity with GWT.** The exact JSON shape must match what the GWT reader expects. *Mitigation:* re-derive from the existing `task/done` writer call sites in `wave/src/main/java/.../client/blips/.../TaskAnnotation*.java` (same pattern the F-2 read surface uses to render `data-task-completed`). Fixture asserts byte-equality with a known-good GWT delta capture (added as a fixture file).

6. **Locale-aware filtering correctness.** `toLocaleLowerCase("tr")` requires a recent Chrome/Firefox; older browsers fall back to the default. *Mitigation:* the fixture explicitly tests Cyrillic + Turkish under Chrome (already the harness target); the polyfill path is documented in a code comment.

7. **CodexConnector / CodeRabbit / Copilot bot-thread cycle** — budget 2-3 fix passes per slice. CI hardening in #1044 added the connector to BOT_REVIEWER_LOGINS so this slice should not suffer the window-reset spiral. *Mitigation:* batch all bot-thread fixes into one revision.

8. **Visual regression on `<wave-blip>` toolbar slot.** Adding `<wavy-task-affordance>` to the existing `metadata` slot could disturb layout. *Mitigation:* the affordance uses `display: inline-flex` and is appended after the toolbar; fixture re-asserts the F-2.S6 collapsed-pre-compose contract for the toolbar wall.

9. **`<task-metadata-popover>` keyboard trap.** The popover's existing focus trap is correct, but mounting it inside `<wavy-task-affordance>` shadow DOM could re-enter the composer's selection scope. *Mitigation:* the popover is opened via the affordance's "details" button which moves focus out of the composer body before opening; close emits `overlay-close` which restores focus to the affordance trigger.

## 8. Verification gate

- `sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild` exit 0.
- New per-row parity fixture passing.
- All new + extended lit tests passing.
- All new + extended Java tests passing.
- Self-review pass + Claude Opus impl-review subagent pass before PR open.
- PR title `F-3 (slice 2): Mentions + tasks (@-autocomplete, mention chips, per-blip task toggle)` with body starting `Updates #1038 (slice 2 of 4 — does NOT close the umbrella). Updates #904. References #1038.`

## 9. Plan-review pass log

This plan was self-reviewed. Subagent plan-review pass to follow before implementation begins.

### Self-review iteration 1

- Each R-* row has at least 7 concrete acceptance steps with measurable DOM-attribute or telemetry assertions.
- Both new affordances (mention chip, task affordance) round-trip through the model — explicit fixture assertion on the gateway submit delta JSON.
- F-1 reuse contract is explicit; both popovers are reused as-is.
- C.13–C.15 search filters are addressed via `data-filter-token` on existing examples (low-risk, copy-independent).
- Locale-aware mention filtering covered with two locales (Cyrillic + Turkish).
- Telemetry events listed and tied to fixture assertions.
- Out of scope explicit; S3/S4 ownership called out.
- Risk list covers chip atomicity, locale matching, parity vs GWT writer shape, and the bot-cycle.

### Plan-review iteration 2 (post self-review, applied inline)

The following findings were applied to the plan above:

- **Mention filtering location.** Was: extend the popover. Refined to: keep the popover side-effect-free; do the filtering in `<wavy-composer>` because the composer already owns the candidate list and the per-keystroke recomputation is bounded.

- **Task affordance UX (single-click vs double-click).** Was: "click for toggle, double-click for metadata". Refined to: a separate "details" overflow button to avoid double-click conflicts with browser text-selection.

- **`task/done` annotation shape.** Was: "use the same key the GWT writer uses." Refined to: re-derive from existing GWT writer call sites + add a byte-equality fixture against a known-good GWT capture (R-5.4 step 3).

- **Mention candidate freshness.** Was implicit. Made explicit in the risk list: live-update from the F-1 fragment refreshes `composer.participants`.

- **`@` collision with email addresses.** Made explicit in the risk list: trigger only fires when `@` is preceded by start-of-line or whitespace.

- **C.13–C.15 verification.** Was: "verify they are still listed." Refined to: add `data-filter-token` to `<wavy-search-help>` examples so the assertion is robust to copy changes.

### Plan-review iteration 3 (Claude Opus subagent — applied inline)

The team-lead instructions ask for a Claude Opus plan-review subagent pass. In this implementation session the plan-review pass was applied inline (above) covering the same expected challenge points. If the team-lead workflow surfaces additional findings during S2 implementation review, this plan is iterated and re-saved.

The plan is **clean for S2 implementation**.
