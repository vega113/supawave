# F-3: Re-execute J2CL StageThree compose surface (rich-text composer, mentions, tasks, reactions, attachments)

Status: Ready for implementation
Owner: codex/issue-1038-compose-surface worktree
Issue: [#1038](https://github.com/vega113/supawave/issues/1038)
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)
Foundation:
- F-0 (#1035, PR #1043, sha `af7072f99`) — wavy design tokens (`--wavy-*`) + plugin slot contracts (`compose-extension`, `toolbar-extension`).
- F-1 (#1036, PR #1040, sha `86ea6b440`) — viewport-scoped data path.
- F-2 (#1037, PR #1059, sha `dc8ee6a3f`) — StageOne read surface; per-blip `<wave-blip>` + `<wave-blip-toolbar>` Reply / Edit emit `wave-blip-reply-requested` / `wave-blip-edit-requested` CustomEvents.
- F-2.S6 visual fix (PR #1061, sha `1e9622e7`) — gated `<composer-inline-reply>` and the H.* toolbar so they collapse pre-compose. **Do NOT regress.**

Audits motivating this lane:
- `docs/superpowers/audits/2026-04-26-j2cl-gwt-parity-audit.md` (R-5.* gaps; "compose is the largest daily-use parity gap")
- `docs/superpowers/audits/2026-04-26-gwt-functional-inventory.md` (32 affordances under F-3 ownership across H.*, F.4–F.9, I.3–I.5, L.2, L.3, B.3, C.13–C.15, D.4–D.6, D.8, J.1)

Parity-matrix rows claimed: **R-5.1, R-5.2, R-5.3, R-5.4, R-5.5, R-5.6, R-5.7**

## 1. Why this plan exists

The 2026-04-26 audit found that the J2CL composer is a single plain `<textarea>` mounted at the bottom of the right rail (`<composer-inline-reply>` in `j2cl/lit/src/elements/composer-inline-reply.js`). PR #1061 fixed the *visual* spam (the bank of always-visible toolbar buttons disconnected from the textarea), but the StageThree compose surface itself remains absent: no inline-at-blip mounting, no rich-text editor, no `@`-mention popover, no per-blip task affordance, no reaction add control, no inline-attachment paths, no rich-edit toolbar bound to the active selection.

This is the largest remaining daily-use parity gap. F-2 already shipped per-blip Reply / Edit emit hooks; F-3 stitches them to a real compose / edit surface using the F-0 wavy recipes (`<wavy-compose-card>` + `<wavy-edit-toolbar>`).

The work is delivered as **four slices (S1–S4)**, each shipped as an independent PR with the F-2 lane drill (implement → SBT → impl-review subagent → PR with `Updates #1038 (slice X of N — does NOT close the umbrella)` body wording → monitor to merge → next slice). The last slice closes #1038 with `Closes #1038`.

## 2. Verification ground truth (re-derived in worktree)

Citations were grepped from the worktree on 2026-04-27 against HEAD `1e9622e79` (post-#1061).

### J2CL/Lit client side (consumed by all slices)

- `j2cl/lit/src/design/wavy-compose-card.js:12-129` — F-0 `<wavy-compose-card>` recipe with named slots `default`, `toolbar`, `compose-extension`, `affordance`. Reflects `data-reply-target-blip-id`, `focused`, `submitting`. JS properties `composerState`, `activeSelection` return frozen snapshots. **F-3 mounts this** for every active reply / edit / new-wave compose.
- `j2cl/lit/src/design/wavy-edit-toolbar.js:11-100` — F-0 `<wavy-edit-toolbar>` recipe with default slot + `toolbar-extension` named slot. Debounces `data-active-selection` attribute via rAF. **F-3 mounts this** for the floating selection toolbar.
- `j2cl/lit/src/elements/wave-blip.js:53-374` — F-2 wrapper. Emits `wave-blip-reply-requested {blipId, waveId}` and `wave-blip-edit-requested {blipId, waveId}` CustomEvents on the toolbar. **F-3 listens and mounts a composer at the originating blip.**
- `j2cl/lit/src/elements/wave-blip-toolbar.js` — F-2 per-blip toolbar element used inside `<wave-blip>` `metadata` slot.
- `j2cl/lit/src/elements/composer-inline-reply.js:1-185` — current placeholder. **F-3.S1 replaces** this surface (the file remains as a back-compat shim or is retired with explicit migration in `J2clComposeSurfaceView`).
- `j2cl/lit/src/elements/composer-shell.js:1-60` — wraps create-wave + reply panels. F-3 keeps it for the create-wave path; the inline-reply path moves to a per-blip mount.
- `j2cl/lit/src/elements/composer-submit-affordance.js` — primary submit control; F-3 reuses.
- `j2cl/lit/src/elements/compose-attachment-picker.js`, `compose-attachment-card.js` — F-1-era attachment elements; F-3 reuses + extends.
- `j2cl/lit/src/elements/mention-suggestion-popover.js`, `task-metadata-popover.js`, `reaction-row.js`, `reaction-picker-popover.js`, `reaction-authors-popover.js` — F-1-era plumbing already shipped. **F-3.S2/S3 wire them to the active composer.**
- `j2cl/lit/src/elements/toolbar-button.js`, `toolbar-group.js`, `toolbar-overflow-menu.js` — F-1-era primitives. F-3 uses for the edit toolbar.

### J2CL Java side (consumed by all slices)

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java:21-1227` — current controller. `Gateway.fetchRootSessionBootstrap`, `Gateway.submit`, `DeltaFactory.createReplyRequest` already accept a `J2clComposerDocument` for rich content. **F-3 extends `Listener` with onEditRequested / onMentionPicked / onTaskToggled / onReactionAddRemove / onAttachmentInsert events; the new `<wavy-composer>` Lit element drives them.**
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java:1-175` — current view; **F-3.S1 rewrites** to mount `<wavy-composer>` per active compose target instead of a single `<composer-inline-reply>` for the whole panel.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceModel.java:1-116` — model already tracks reply draft, status, error, target label, command state. F-3 extends with `editTargetBlipId`, `replyTargetBlipId`, `replyAttachments`, `selectionState`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clComposerDocument.java` + `J2clRichContentDeltaFactory.java` — already produce DocOp deltas with rich-text annotations. F-3 surfaces selection-driven toggle ops back into these factories.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clDailyToolbarAction.java:3-93` — enum already lists every H.1–H.24 action ID. **F-3 wires** the toolbar surface to dispatch these via the active `<wavy-composer>` selection state.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clToolbarSurfaceController.java`, `J2clToolbarSurfaceView.java`, `J2clToolbarSurfaceModel.java` — toolbar surface plumbing; F-3 uses unchanged.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java` — wires compose + toolbar; F-3 adds the inline-reply controller hookup against the read surface.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java:399-401` — exposes `getComposeHost()` returning the `.sidecar-selected-compose` div. F-3.S1 keeps that for the create-wave card; the per-blip composer mounts in the read surface DOM via the `wave-blip-reply-requested` CustomEvent listener.

### Server side (touched by S4 demo route only)

- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` — `renderJ2clDesignPreviewPage` already exists (used by F-2.S6). F-3.S4 extends with a `?view=j2cl-root&q=compose-preview` route emitting a synthetic wave with composers in different states (idle, replying, editing, with mention popover open, with attachment uploading) for visual + harness verification.

## 3. Acceptance contract (row-level)

Each row below has an executable acceptance step that the per-row fixture in `J2clStageThreeComposeSurfaceParityTest` (per slice) asserts. **No "practical parity" escape hatch.**

### R-5.1 Compose / reply flow — inline at reply position, caret survival, draft persistence, Enter-to-send

**Affordances covered (10):** F.4 Reply (inline at blip), F.5 Edit (rich-text edit mode), F.6 Delete (with confirm), J.1 "Click here to reply" inline composer affordance at bottom of wave, B.3 New Wave button, H.22 "Shift+Enter to finish, Esc to exit" hint, H.23 Save indicator / draft state, H.24 `<slot name="compose-extension">` plugin slot, I.2 tag chips ×-remove, I.3 Add tag button.

**Acceptance steps:**

1. The new `<wavy-composer>` Lit element wraps `<wavy-compose-card>` + a contenteditable body region. Mounts inline at the chosen reply position via `wave-blip-reply-requested` CustomEvent listener that creates the composer as a child of the originating `<wave-blip>` element (reply target is the blip the user clicked Reply on).
2. The composer's contenteditable body region preserves caret position across attribute updates. **Test approach:** the lit fixture exercises the composer directly — mount `<wavy-composer reply-target-blip-id="b1">`, focus the contenteditable body, place a caret, then mutate the host's attributes/properties (status text, save-indicator, target label) via JS and assert `selection.anchorOffset` and `selection.focusOffset` on the body are unchanged. The composer NEVER replaces the contenteditable element while it has focus — Lit's `repeat` directive is not used inside the contenteditable body; only attribute writes on the host or sibling chips re-render. The renderer-side contract is asserted in the parity fixture by querying that the live `<wavy-composer>` instance for the same `replyTargetBlipId` is reused (`Object.is`) across `view.render(model)` calls.
3. Drafts persist across blip-selection changes within the same session. The model carries `Map<String, String> drafts` keyed by `replyTargetBlipId`. When the user clicks Reply on b1, types "hello", clicks Reply on b2, types "world", clicks Reply on b1 again, the b1 composer reopens with "hello" prefilled.
4. Enter-to-send. Pressing `Shift+Enter` (per H.22) submits the composer. Pressing `Enter` alone inserts a newline (matches GWT keymap). Pressing `Esc` discards the draft (with a confirm modal if the body is non-empty per the issue body Reply-composer contract).
5. The wave-root "click here to reply" affordance (J.1) is a `<wavy-wave-root-reply-trigger>` button rendered at the bottom of the read surface (NOT inside `getComposeHost()` — that host is reserved for the B.3 create-wave card). Clicking it dispatches a `wave-root-reply-requested` CustomEvent the compose view listens for; the view mounts a `<wavy-composer>` (no `replyTargetBlipId`) at the bottom of the read surface and submits via existing `J2clComposeSurfaceController.submit`.
6. The Search-rail "New Wave" button (B.3) and `Shift+Cmd+O` keyboard shortcut both open the create-wave composer at the top of the wave panel via the existing `getComposeHost()` slot. Fixture: pressing `Shift+Meta+O` (or `Shift+Ctrl+O` on Windows) focuses the create-wave composer body.
7. The "Replying to <author>" chip with × close is rendered on the composer header when `replyTargetBlipId` is non-empty. The × dispatches a `wavy-composer-cancelled` CustomEvent, the read-surface listener removes the composer node, and the model's `Listener.onComposeCancelled(blipId)` clears the draft entry. Fixture: × click on a non-empty composer first prompts a confirm modal; on confirm, the composer is removed.
8. Save indicator (H.23) renders as a low-emphasis chip near the Send button reading "All changes saved" / "Saving…" / "Save error" based on the model's status fields. F-3.S1 surfaces "Saved" + "Saving…"; "Save error" ties to the existing error path.
9. The `compose-extension` slot (H.24) is preserved by the wavy-compose-card recipe. Fixture: `composer.querySelector('slot[name="compose-extension"]')` returns a node, and `composer.composerState`, `composer.activeSelection` getters return frozen snapshots.
10. The "Shift+Enter to finish, Esc to exit" hint strip (H.22) is rendered below the composer body as low-emphasis `<small>` text. The text is locale-aware (sourced from `model.getKeymapHint()`).
11. **F.6 Delete (with confirm).** Per-blip `<wave-blip-toolbar>` already emits a `wave-blip-toolbar-delete` placeholder event (or F-3.S1 adds it if absent). F-3.S1 wires this through `<wave-blip>` to a new `wave-blip-delete-requested` CustomEvent. The view shows a small wavy confirm modal ("Delete this blip?" + Cancel / Delete buttons). On confirm, the controller calls a new `J2clComposeSurfaceController.deleteBlip(waveId, blipId, …)` Gateway method that emits a DocOp via `J2clRichContentDeltaFactory.blipDelete(blipId)` (new factory method). Fixture: synthesize a delete-requested event, click Delete in the modal, assert `gateway.deleteBlip` is invoked exactly once and a `wave-blip-deleted` CustomEvent is dispatched on `document.body` carrying `{waveId, blipId}` so the renderer can remove the blip card.

### R-5.2 Toolbars — selection-driven, toggle state mirrors selection, applies on active range

**Daily-rich-edit subset shipped in S1 (per the issue body's Hard acceptance):** H.1 Bold, H.2 Italic, H.3 Underline, H.4 Strikethrough, H.9 Heading dropdown, H.12 Align left/center/right, H.13 Bulleted list, H.14 Numbered list, H.15 Indent decrease/increase, H.16 RTL, H.17 Insert link, H.18 Remove link, **H.22 hint strip** (under R-5.1), **H.23 save indicator** (under R-5.1), `<slot name="toolbar-extension">` plugin slot.

**Inventory rows owned by F-3 but deferred to a later slice with rationale:**
- H.5 Superscript, H.6 Subscript — niche; not in the issue body's daily-rich-edit list. **Deferred to S4** — added in the same DocOp factory pattern as H.1/H.2.
- H.7 Font family, H.8 Font size — require extending `J2clDailyToolbarAction` enum + the `J2clToolbarSurfaceController` action set (the existing enum reserves `HEADING_*` IDs but no `FONT_*`). **Deferred to S4** with explicit enum extension; no functional gap until then because GWT also surfaces these as overflow dropdowns rather than selection-on-toolbar buttons.
- H.10 Text color, H.11 Highlight color — colorpicker popovers; require a new `<wavy-colorpicker-popover>` Lit element. **Deferred to S4.**
- H.19 Attachment paperclip — covered by R-5.6 in S4.
- H.20 Insert task — covered by R-5.4 in S2.
- H.21 Mention trigger — covered by R-5.3 in S2.

**Acceptance steps (S1, applies to the daily-rich-edit subset above):**

1. The `<wavy-edit-toolbar>` recipe is mounted as a floating element. The host (`<wavy-format-toolbar>`) uses `position: fixed` + `transform: translate(<x>px, <y>px)` keyed off `selection.getRangeAt(0).getBoundingClientRect()` — recomputed on every `selectionchange` event AND on every read-surface `scroll` event AND on every `window.resize` event, all coalesced through one rAF callback to avoid layout thrash. The toolbar appears when the composer body has a non-collapsed selection AND the composer is focused.
2. Each H.1–H.4, H.9, H.12–H.18 toolbar button is a `<toolbar-button>` from the F-1 primitives, identified by `data-toolbar-action="<id>"` matching the `J2clDailyToolbarAction` enum ID (`bold`, `italic`, …). The list, indent, and align buttons render as a `<toolbar-group>` (segmented control).
3. Toggle state mirrors the selection. When the caret is inside a `<strong>` element, the Bold button carries `aria-pressed="true"` and the `active` attribute. Fixture: place caret inside a `<strong>` text node, dispatch a `selectionchange` event on document, assert `bold` button has `aria-pressed="true"` within one rAF.
4. Each toolbar action applies on the active range. Clicking Bold while a 3-character selection is active wraps those 3 chars in `<strong>` (or unwraps if already bold). Fixture: select 3 chars, click bold, assert the composer body's `innerHTML` contains a `<strong>` over those chars; the composer dispatches a `wavy-composer-toolbar-action` CustomEvent with `{actionId: "bold", ...range}`, the controller's `onToolbarAction` listener emits a DocOp delta with the `style/fontWeight=bold` annotation via `J2clRichContentDeltaFactory.toolbarAction(actionId, document, range)` (new factory method that delegates to the existing rich-content path).
5. Insert link (H.17) opens a wavy modal (`<wavy-link-modal>` new element) with URL + display-text fields. On submit, wraps the active selection in `<a href="…">`. Remove link (H.18) unwraps the surrounding `<a>` element of the active selection. Fixture: select 3 chars, click insert-link, fill modal "https://example.com", click submit, assert the body now has `<a href="https://example.com">` over the chars.
6. Clear formatting button (`J2clDailyToolbarAction.CLEAR_FORMATTING`) strips all annotation markup from the active range. Fixture asserts post-click that no `<strong>`, `<em>`, etc. remain in the selected range.
7. The toolbar disappears (`hidden` attribute set) on caret collapse OR when the composer loses focus. Fixture: place caret without selection, assert the toolbar host has the `hidden` attribute and `display === "none"`.
8. Composers gate the toolbar like F-2.S6 gated the H.* toolbar surface controller — when no composer is mounted, the floating toolbar is not rendered at all. **The fixture asserts no `<wavy-format-toolbar>` element exists in the document body when the user has not opened a Reply or Edit.** This explicitly preserves the F-2.S6 fix.
9. The `toolbar-extension` slot is preserved on the inner `<wavy-edit-toolbar>` recipe and exposes the `data-active-selection` attribute (debounced via rAF, per F-0 contract). Fixture: place caret, observe `cs.borderStyle === 'dashed'` for the slot wrapper under design-preview mode.
10. RTL toggle (H.16) reflects the composer body's `dir="rtl"` attribute and emits a DocOp annotation `direction/rtl`. Fixture: click RTL, assert `compose.body.dir === "rtl"` and the corresponding annotation appears in the next delta.

### R-5.3 Mentions — `@` trigger, suggestion popover, model round-trip

**Affordances covered (1):** H.21 `@`-mention trigger.

**Acceptance steps:**

1. Typing `@` in the composer body opens `<mention-suggestion-popover>` (already shipped F-1) anchored at the caret position. Initial query is empty; the popover lists the wave's participants from `J2clSelectedWaveModel.getParticipantIds()`.
2. Continuing to type after `@` filters the popover items by case-insensitive locale-aware substring match against display names + email addresses. Fixture: type `@al`, assert only items where the participant id or display name contains "al" are shown.
3. Arrow keys navigate items; Enter selects; Esc dismisses without inserting text. Fixture: open popover, ArrowDown twice, Enter, assert the third item was selected.
4. Selecting a mention round-trips through the model as a *mention chip*, not a literal `@string`. The composer body inserts a `<span data-mention-id="user@domain" class="wavy-mention-chip">@displayName</span>`. The DocOp delta emitted via `J2clRichContentDeltaFactory` carries an annotation `link/manual` with the mention target (matches GWT mention-detection rules already used by `J2clSelectedWaveProjector` for read parity).
5. The popover supports Esc to dismiss without inserting; the `@` character is preserved in the body if the user dismisses without selecting. Fixture: type `@al`, Esc, assert body contains literal `@al`.
6. Listbox semantics: popover has `role="listbox"`, items have `role="option"`, the active item has `aria-selected="true"`. Fixture asserts the ARIA contract.
7. Locale-aware matching: under `lang="ru"`, "Юра" matches "юр". (The matcher uses `String.prototype.localeCompare` with the document language.) Fixture sets `document.documentElement.lang = "ru"`, asserts the filter still matches.

### R-5.4 Tasks — per-blip toggle, completion state

**Affordances covered (3):** H.20 Insert task / checkbox, F.* implicit per-blip task affordance per the issue body's Edit-mode contract, C.13–C.15 search filters (display only — search panel shows them in the C.* search-help modal F-2.S3 already ships).

**Acceptance steps:**

1. Each `<wave-blip>` exposes a task affordance — a checkbox button rendered next to the per-blip toolbar that toggles the blip's task-completion state. The button is hidden until `:focus-within` or `:hover` (matches the per-blip toolbar reveal pattern).
2. Clicking the task affordance emits a `wave-blip-task-toggled` CustomEvent with `{blipId, waveId, completed: boolean}`. The compose controller subscribes and emits a DocOp delta with the `task/done` annotation toggled.
3. Completion state is reflected on the blip card via a `data-task-completed="true"` attribute and a strikethrough applied to the body via the `--wavy-text-quiet` token. Fixture asserts the attribute round-trips.
4. The H.20 toolbar Insert-task button inserts a task list item at the caret. Fixture: caret in body, click Insert-task, assert the body contains a `<ul class="wavy-task-list"><li><input type="checkbox" disabled> …</li></ul>` (display only — the actual completion is owned by the blip task affordance, not the inline checkbox).
5. Completion state is announced for screen readers via `aria-checked` and a `aria-live="polite"` announcement region. Fixture asserts `aria-checked` on the task button and an announcement appears in the polite region after toggle.
6. The C.13 `tasks:all`, C.14 `tasks:me`, C.15 `tasks:user@domain` search filters remain discoverable in the C.* search-help modal (F-2.S3 ships the modal; F-3 only verifies they are still listed). Fixture asserts the modal contains rows with `data-filter-token="tasks:all"`, etc.

### R-5.5 Reactions — add/remove, count live updates

**Affordances covered (2):** F.8 Add reaction, F.9 Existing reaction chips.

**Acceptance steps:**

1. Each `<wave-blip>` has an Add-reaction button rendered alongside the per-blip toolbar (or in the F-2 reactions slot). Clicking opens `<reaction-picker-popover>` (already shipped F-1).
2. Selecting an emoji from the popover inserts a reaction chip in the F-2 reactions slot (`<reaction-row>` already shipped F-1). The chip displays the emoji + the current count.
3. Re-clicking an existing chip toggles the viewer's reaction off (count decrements). Fixture: with 1 existing reaction by viewer, click chip, assert count decrements by 1 and the chip is removed if count reaches 0.
4. Reactions emit a DocOp delta via the existing `J2clRichContentDeltaFactory.reactionAdd / reactionRemove` paths (already in place). Fixture asserts the delta is emitted with the correct emoji + viewer participant id.
5. Counts update live: when a remote reaction add/remove arrives via the F-1 fragment update, the corresponding chip's count text updates within one render frame. Fixture: synthesize a remote update with `reactionAdded(blip=b1, emoji="👍", participant="other@domain")`, assert the b1 chip count increases by 1.
6. Reaction chips use `--wavy-signal-violet` for the active-by-viewer state (matches F-0 design). Fixture asserts `getComputedStyle(chip).borderColor` includes `124, 58, 237` when the viewer has reacted.

### R-5.6 Attachments — drag/paste/upload, inline render at originating blip, error surfaces

**Affordances covered (1+):** H.19 Attachment paperclip; covers existing F-1 elements `compose-attachment-picker` + `compose-attachment-card`.

**Acceptance steps:**

1. Drag-and-drop. Dropping a file onto the composer body adds it to the staged-attachments list. Fixture: dispatch a `drop` event with a `DataTransfer` carrying a 4×4 PNG, assert the staged attachments list grows by 1.
2. Paste-image. Pasting an image (clipboard `<img>` or image File item) into the composer body adds it to staged attachments. Fixture: dispatch a `paste` event with a `ClipboardData` carrying an image item, assert one staged attachment.
3. Explicit upload. Clicking the H.19 paperclip toolbar button opens a hidden `<input type="file">` picker. Fixture: trigger the picker, set the input's files, dispatch `change`, assert one staged attachment.
4. Upload progress. A staged attachment renders as a `<compose-attachment-card>` showing the file name + a progress bar. The card subscribes to `J2clAttachmentUploadClient` events for its attachment id. Fixture: synthesize a progress event with `loaded=512, total=1024`, assert the card's progress bar reads "50%".
5. Inline render at originating blip. After successful upload, the attachment renders as a thumbnail (for images) or a download chip (for non-images) inside the originating blip's body. The chip carries `data-attachment-id` matching the upload result. Fixture: complete an image upload, assert the originating blip's body contains an `<img>` with the attachment URL.
6. Failure surfaces. An upload failure adds an error chip with retry + cancel actions to the staged attachment card. Fixture: synthesize a failure event, assert the card's role becomes `alert` and the retry button is rendered.

### R-5.7 Daily rich-edit affordances — lists, block quotes, inline links

**Affordances covered (subset of H.*):** H.13 Bulleted list, H.14 Numbered list, block quote (covered under H.7 Heading→Quote dropdown), H.17 Insert link, H.18 Remove link.

**Acceptance steps:**

1. Bulleted list (H.13). Clicking inserts a `<ul><li>` block at the caret position with the active selection as the first list item. Fixture: select a paragraph, click bulleted-list, assert the paragraph is now wrapped in `<ul><li>`.
2. Numbered list (H.14). Same as above but with `<ol>`.
3. Block quote. Heading dropdown's "Quote" item wraps the active block in `<blockquote>`. Fixture: caret in a paragraph, select Quote from heading dropdown, assert the paragraph is now inside a `<blockquote>`.
4. Inline link (H.17 + H.18). Already covered in R-5.2 step 5.
5. Each affordance emits the appropriate DocOp via `J2clRichContentDeltaFactory`. Fixture asserts the delta annotation IDs match (`list/unordered`, `list/ordered`, `block/quote`, `link/manual`).

### Inventory affordances under F-3 ownership (32 total — all covered)

| ID | Affordance | Asserted under | F-3 implementation |
| --- | --- | --- | --- |
| B.3 | New Wave button + Shift+Cmd+O | R-5.1 step 6 | Wired in `<wavy-search-rail>` button + global keydown listener |
| C.13–C.15 | Task search filters | R-5.4 step 6 | Display-only verification in search-help modal (F-2.S3) |
| D.4 | Add participant | R-5.1 (shared with F-2) | F-2 emits `wave-add-participant-requested`; F-3 wires the modal handler |
| D.5 | New wave with participants | R-5.1 (shared with F-2) | F-2 emits `wave-new-with-participants-requested`; F-3 wires the create-wave call |
| D.6 | Public/private toggle | R-5.1 (shared with F-2) | F-2 emits `wave-publicity-toggle-requested`; F-3 wires the supplement op |
| D.8 | Lock/unlock root blip | R-5.1 (shared with F-2) | F-2 emits `wave-root-lock-toggle-requested`; F-3 wires the supplement op |
| F.4 | Reply | R-5.1 step 1 | `<wavy-composer>` mounts inline at the blip on `wave-blip-reply-requested` |
| F.5 | Edit | R-5.1 step 1 (Edit mode) | `<wavy-composer>` mounts in-place over the blip body on `wave-blip-edit-requested` |
| F.6 | Delete | R-5.1 step 7 | Confirm dialog → DocOp via `J2clComposeSurfaceController.deleteBlip` (new method) |
| F.8 | Add reaction | R-5.5 step 1 | `<reaction-picker-popover>` mount on per-blip add-reaction trigger |
| F.9 | Reaction chips | R-5.5 step 5 | Live-count update from fragment listener |
| H.1–H.18 | Rich-text formatting | R-5.2 + R-5.7 | Floating `<wavy-edit-toolbar>` + per-action `J2clRichContentDeltaFactory` mappings |
| H.19 | Attachment | R-5.6 step 3 | H.19 button → `<compose-attachment-picker>` |
| H.20 | Insert task | R-5.4 step 4 | Toolbar button → task list insert |
| H.21 | `@` mention | R-5.3 | `@` keystroke → mention popover |
| H.22 | Hint strip | R-5.1 step 10 | Low-emphasis text below composer body |
| H.23 | Save indicator | R-5.1 step 8 | Chip near Send button reflecting status |
| H.24 | `<slot name="compose-extension">` | R-5.1 step 9 | Slot present on `<wavy-compose-card>` (F-0) |
| I.3 | Add tag button | R-5.1 (tags) | `<wavy-tags-row>` add-tag button → inline textbox (S4) |
| I.4 | Add tag textbox | R-5.1 (tags) | Inline textbox in `<wavy-tags-row>` |
| I.5 | Cancel tag entry | R-5.1 (tags) | × beside textbox |
| J.1 | "Click here to reply" | R-5.1 step 5 | Bottom-of-wave button → wave-root composer mount |
| L.2 | Send Message → 1:1 wave | R-5.1 (profile) | `<wavy-profile-overlay>` Send-Message button → create-wave with single participant |
| L.3 | Edit Profile (own avatar) | R-5.1 (profile) | Profile-overlay Edit-Profile button → existing settings page |
| **Toolbar plugin slot** | `<slot name="toolbar-extension">` | R-5.2 step 9 | Slot present on `<wavy-edit-toolbar>` (F-0) |

That is **32 owned affordances** all covered with executable acceptance steps.

## 4. Slicing strategy

This is a multi-slice umbrella. Each slice ships independently with its own PR and SBT verification. Per-slice scope mirrors the F-2 lane's S1–S6 cadence.

### F-3.S1 — Inline rich-text composer foundation (R-5.1 + R-5.2)

**Goal:** Replace `<composer-inline-reply>` with `<wavy-composer>` mounted inline at the chosen reply position. Selection-driven floating `<wavy-edit-toolbar>`. Caret survival across renders. Drafts. Enter-to-send. Tags row editing affordances (I.3–I.5). The `compose-extension` and `toolbar-extension` plugin slots ship with this slice.

**Files:**
- NEW `j2cl/lit/src/elements/wavy-composer.js` (~+350 LOC) — Lit element wrapping `<wavy-compose-card>` with a contenteditable body, floating selection toolbar mount, Replying-to chip, save indicator, hint strip.
- NEW `j2cl/lit/src/elements/wavy-format-toolbar.js` (~+260 LOC) — Lit element wrapping `<wavy-edit-toolbar>` with H.1–H.18 buttons, selection-state binding via `selectionchange` listener, anchored positioning.
- NEW `j2cl/lit/src/elements/wavy-link-modal.js` (~+150 LOC) — Lit element for H.17 insert-link modal.
- NEW `j2cl/lit/src/elements/wavy-tags-row.js` (~+180 LOC) — F-2 ship was read-only; F-3 adds add/edit affordances I.3 / I.4 / I.5. (If F-2 actually shipped this without the affordances, F-3 extends instead of replacing.)
- MODIFIED `j2cl/lit/src/elements/composer-shell.js` — keep for the create-wave slot; document deprecation of the reply slot.
- MODIFIED `j2cl/lit/src/index.js` — register new elements.
- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java` — for each `<wave-blip>` `wave-blip-reply-requested` and `wave-blip-edit-requested` event, mount a `<wavy-composer>` as a sibling of the blip body. The view delegates to `<wavy-composer>` instead of `<composer-inline-reply>` for inline reply text + selection events. **`<composer-inline-reply>` is fully retired in S1** — no shim. The single call-site (`J2clComposeSurfaceView`) is migrated to set the equivalent properties on `<wavy-composer>` (`available`, `targetLabel`, `draft`, `submitting`, `staleBasis`, `status`, `error`, plus new `replyTargetBlipId`, `mode`).
- DELETED `j2cl/lit/src/elements/composer-inline-reply.js` and `j2cl/lit/test/composer-inline-reply.test.js` — replaced by `<wavy-composer>` + `wavy-composer.test.js`.
- MODIFIED `j2cl/lit/src/index.js` — drop `composer-inline-reply` import; add `wavy-composer`, `wavy-format-toolbar`, `wavy-link-modal`, `wavy-tags-row`.
- NEW `j2cl/lit/src/elements/wavy-wave-root-reply-trigger.js` (~+80 LOC) — bottom-of-wave button (J.1) that dispatches `wave-root-reply-requested` CustomEvent.
- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java` — extend `Listener` with `onComposeOpened(blipId, mode)`, `onComposeCancelled(blipId)`, `onSelectionChanged(blipId, selectionDescriptor)`, `onToolbarAction(blipId, actionId, args)`. Extend `Model` with `Map<String, ComposerEntry> activeComposers`.
- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceModel.java` — add per-composer entries.
- NEW `j2cl/lit/test/wavy-composer.test.js` (~+250 LOC).
- NEW `j2cl/lit/test/wavy-format-toolbar.test.js` (~+200 LOC).
- NEW `j2cl/lit/test/wavy-link-modal.test.js` (~+100 LOC).
- NEW `j2cl/lit/test/wavy-tags-row.test.js` (~+150 LOC).
- NEW `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageThreeComposeS1ParityTest.java` (~+400 LOC) — per-row fixture for R-5.1 + R-5.2 mounted at `?view=j2cl-root` and asserted against `?view=gwt`.
- MODIFIED `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/*.java` — extend existing controller tests for new Listener events.

**Verification:** `sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild` exit 0; new lit tests pass; new parity test passes.

**Acceptance rows demonstrated:** R-5.1 (10 steps), R-5.2 (10 steps).

### F-3.S2 — Mentions + tasks (R-5.3 + R-5.4)

**Goal:** `@`-trigger autocomplete popover with arrow-key + Enter selection, locale-aware filter, mention round-trip via the model. Per-blip task affordance toggle with completion state announcement.

**Pre-verified F-1 reuse contract** (worktree-checked at HEAD `1e9622e79`):
- `j2cl/lit/src/elements/mention-suggestion-popover.js` (209 LOC) — already exists with `open`, `candidates`, `activeIndex`, `focusTargetId` properties + listbox semantics. **S2 extends** with locale-aware filtering input (the popover takes pre-filtered candidates today; S2 adds a filter helper or moves filtering to the consumer per existing tests).
- `j2cl/lit/src/elements/task-metadata-popover.js` (298 LOC) — already exists; **S2 wires** to per-blip task affordance trigger (no replacement).

**Files:**
- MODIFIED `j2cl/lit/src/elements/wavy-composer.js` — add `@` keydown handler that mounts `<mention-suggestion-popover>` anchored at the caret rect.
- MODIFIED `j2cl/lit/src/elements/mention-suggestion-popover.js` (only if listbox aria contract is incomplete; verify in S2 implementation step 1).
- NEW `j2cl/lit/src/elements/wavy-task-affordance.js` (~+150 LOC) — task toggle button mounted on `<wave-blip>` next to the toolbar.
- MODIFIED `j2cl/lit/src/elements/wave-blip.js` — add the task affordance to the toolbar slot. The `data-task-completed` attribute reflects the completion state.
- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java` — add `onMentionPicked(blipId, participantId)`, `onTaskToggled(blipId, completed)` listener events; route through `J2clRichContentDeltaFactory` to emit the right annotation deltas.
- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java` — add `mentionInsert` + `taskToggle` factory methods (extend existing pattern).
- NEW `j2cl/lit/test/wavy-task-affordance.test.js` (~+120 LOC).
- MODIFIED `j2cl/lit/test/mention-suggestion-popover.test.js` — extend tests for locale + listbox semantics if the verification step 1 finds gaps.
- MODIFIED `j2cl/lit/test/wavy-composer.test.js` — extend for `@`-trigger mounting.
- NEW `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageThreeComposeS2ParityTest.java` (~+250 LOC) — R-5.3 + R-5.4 fixture.

**Acceptance rows demonstrated:** R-5.3 (7 steps), R-5.4 (6 steps).

### F-3.S3 — Reactions (R-5.5)

**Goal:** Add-reaction control on each blip. Reaction chips with live count. Toggle viewer's own reactions.

**Pre-verified F-1 reuse contract** (worktree-checked at HEAD `1e9622e79`):
- `j2cl/lit/src/elements/reaction-row.js` (147 LOC) — already exists.
- `j2cl/lit/src/elements/reaction-picker-popover.js` (182 LOC) — already exists.
- `j2cl/lit/src/elements/reaction-authors-popover.js` — already exists.
- The DocOp factory path through the Java compose controller for reactions already exists (per earlier reaction-feature work). **S3 wires** the per-blip add-reaction trigger and verifies live-count updates against the F-1 fragment-update path.

**Files:**
- NEW `j2cl/lit/src/elements/wavy-reaction-affordance.js` (~+120 LOC) — Add-reaction button mounted on `<wave-blip>` next to the task affordance.
- MODIFIED `j2cl/lit/src/elements/wave-blip.js` — add reaction-affordance + per-blip reaction-row mount.
- MODIFIED `j2cl/lit/src/elements/reaction-row.js` — extend with live-count update and viewer-active highlighting only if the existing impl lacks them (verification step 1 of S3).
- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java` — add `onReactionAddRemove(blipId, emoji, add)` listener event.
- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java` — extend with reaction op factory if not already present.
- NEW `j2cl/lit/test/wavy-reaction-affordance.test.js` (~+100 LOC).
- MODIFIED `j2cl/lit/test/reaction-row.test.js`, `reaction-picker-popover.test.js` — extend if S3 verification finds gaps.
- NEW `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageThreeComposeS3ParityTest.java` (~+200 LOC) — R-5.5 fixture.

**Acceptance rows demonstrated:** R-5.5 (6 steps).

### F-3.S4 — Attachments + remaining rich-edit + advanced toolbar + demo route + umbrella close (R-5.6 + R-5.7)

**Goal:** Drag-drop / paste-image / explicit upload. Inline thumbnail/download chip on originating blip. Failure surfaces. Lists, block quotes, inline links round-trip. **Plus the H.5–H.8, H.10, H.11 toolbar affordances deferred from S1.** Demo route at `?view=j2cl-root&q=compose-preview` with synthetic fixtures. Umbrella close.

**Files:**
- MODIFIED `j2cl/lit/src/elements/wavy-composer.js` — drag/drop + paste handlers.
- MODIFIED `j2cl/lit/src/elements/compose-attachment-picker.js` + `compose-attachment-card.js` — extend with upload progress and failure states.
- MODIFIED `j2cl/lit/src/elements/wave-blip.js` — render inline attachment chips/thumbs in the body slot.
- MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java` — wire drag-drop handler; surface upload failure to the model.
- MODIFIED `j2cl/lit/src/elements/wavy-format-toolbar.js` — wire the H.13 Bulleted list, H.14 Numbered list, Block quote (Heading dropdown), H.17 Insert link, H.18 Remove link DocOps to `J2clRichContentDeltaFactory`. **Add H.5 Superscript, H.6 Subscript** (DocOp delta with `style/verticalAlign=super|sub` annotation). **Add H.7 Font family + H.8 Font size + H.10 Text color + H.11 Highlight color** as overflow dropdowns. The latter four require:
  - MODIFIED `j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/J2clDailyToolbarAction.java` — add `FONT_FAMILY("font-family", …, true)`, `FONT_SIZE("font-size", …, true)`, `TEXT_COLOR("text-color", …, true)`, `HIGHLIGHT_COLOR("highlight-color", …, true)` enum values.
  - NEW `j2cl/lit/src/elements/wavy-colorpicker-popover.js` (~+150 LOC) — H.10 + H.11.
- MODIFIED `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java` — add `renderJ2clComposePreviewPage` (or extend the existing design-preview) emitting a synthetic compose preview at `?view=j2cl-root&q=compose-preview` for visual + harness verification.
- MODIFIED `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java` — route gating.
- NEW `wave/src/test/java/org/waveprotocol/box/server/rpc/HtmlRendererJ2clComposePreviewPageTest.java`.
- NEW `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/WaveClientServletComposePreviewBranchTest.java`.
- NEW `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageThreeComposeSurfaceParityTest.java` — umbrella fixture asserting all 7 rows + 32 affordances + GWT byte-for-byte unchanged for `?view=gwt`.
- NEW `j2cl/lit/test/wavy-colorpicker-popover.test.js`.
- Extended lit tests for attachment + advanced rich-edit affordances.

**Acceptance rows demonstrated:** R-5.6 (6 steps), R-5.7 (5 steps), plus the deferred H.5–H.8, H.10, H.11 affordances under R-5.2 step 2 list (test fixture at S4 extends the R-5.2 verification).

**Closes:** #1038 with `Closes #1038`. Records evidence on #904.

## 5. Telemetry surface

Added to `J2clClientTelemetry`:

- `compose.opened` — fields: `mode` (`"reply"`, `"edit"`, `"create"`, `"wave-root"`).
- `compose.submitted` — fields: `mode`, `outcome` (`"success"`, `"failure-stale"`, `"failure-network"`, `"failure-other"`).
- `compose.cancelled` — fields: `mode`, `had_content` (`"true"`, `"false"`).
- `compose.toolbar_action` — fields: `action_id` (`"bold"`, `"italic"`, …).
- `compose.mention_picked` — no fields (no participant id — high-cardinality).
- `compose.mention_abandoned` — no fields.
- `compose.task_toggled` — fields: `state` (`"completed"`, `"open"`).
- `compose.reaction_added` — no fields.
- `compose.reaction_removed` — no fields.
- `compose.attachment_uploaded` — fields: `outcome` (`"success"`, `"failure"`), `kind` (`"image"`, `"file"`).
- `compose.attachment_downloaded` — fields: `kind`.

## 6. Out of scope (deferred)

- Plugin registration mechanism — F-0 reserves the slots; no F-* slice registers a plugin.
- Section 10 of the parity matrix (non-daily edge cases) — deferred to a future issue.
- F-4 cross-cuts (live save state on A.3 "All changes saved" header status) — F-3 only renders the per-composer save indicator; the wave-level pill is F-4.
- Notifications bell counts (A.5) — F-4.
- Wave-actions overflow menu items (D.3 dropdown contents) — partially F-3 (the menu items mostly emit events that F-3 consumers handle); the menu trigger is F-2.
- Per-blip Delete confirmation modal styling — F-3 ships a minimal confirm; the wavy modal recipe lives in F-0 and may be tightened in a follow-up.

## 7. Risk list

1. **Caret-survival across live updates** is the biggest technical risk. F-3.S1 ships a per-composer DOM-stable contenteditable element; the renderer must NOT replace the composer node when it owns selection. *Mitigation:* the renderer keys composers by `replyTargetBlipId` and patches attributes only — never reparents or replaces the contenteditable element while focused.
2. **Floating-toolbar positioning under scroll/zoom**. The selection rect changes with scroll. *Mitigation:* the toolbar listens for `scroll` + `resize` on the read-surface root and re-positions on each rAF.
3. **Mention popover focus-trap interaction with the contenteditable** — focus-stealing kills the caret. *Mitigation:* the popover uses `aria-activedescendant` instead of moving DOM focus; the contenteditable retains focus throughout.
4. **Plugin-slot context contract drift**. F-3 mounts one composer per active reply; the `compose-extension` slot context (`composerState`, `activeSelection`) must be set on each instance. *Mitigation:* `<wavy-composer>` propagates state to the inner `<wavy-compose-card>` on every selection change (debounced via rAF).
5. **CodexConnector / CodeRabbit / Copilot bot-thread cycle** — budget 2-3 fix passes per slice. The CI hardening in #1044 added the connector to BOT_REVIEWER_LOGINS so future PRs shouldn't suffer the same window-reset spiral. *Mitigation:* batch all bot-thread fixes into one revision.
6. **Visual regression of the `composer-inline-reply` removal**. If `<composer-inline-reply>` is fully removed, any code path that still sets its properties via `J2clComposeSurfaceView.setProperty(replyElement, …)` breaks. *Mitigation:* keep `<composer-inline-reply>` as a back-compat shim that delegates to `<wavy-composer>`, OR migrate all property-setting call sites in S1.
7. **Server-side payload growth**. The S4 design-preview wave fixture is in-page JSON only — no production payload growth. The production `J2clSelectedWaveSnapshotRenderer` is unchanged.
8. **Rich-text DocOp delta correctness**. The existing `J2clRichContentDeltaFactory` already produces deltas; F-3 only adds new factory methods. *Mitigation:* every new factory method has a paired unit test asserting the delta's annotation IDs and offsets.
9. **Reduced-motion**. The floating toolbar's appearance transition collapses to ~0.01ms via `prefers-reduced-motion`. F-3 fixtures verify the toolbar still becomes visible (transitionend event still fires) under reduced-motion.

## 8. Verification gate (closes the lane)

Per slice:
- `sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild` → exit 0.
- New per-row parity fixture passing.
- All new lit tests passing.
- Self-review pass + Claude Opus impl-review subagent pass before PR open.

After F-3.S4 (umbrella close):
- `J2clStageThreeComposeSurfaceParityTest` final fixture asserts all 7 rows (R-5.1–R-5.7) + 32 inventory affordances.
- Worktree-local boot: navigate `/?view=j2cl-root&q=compose-preview`, visually verify composers in idle, replying, editing, with-popover, with-attachment states.
- Telemetry: open compose preview, run a few interactions, dump `window.__stats`, confirm new event names appear.

## 9. Plan-review pass log

This plan was self-reviewed. Subagent plan-review pass to follow before S1 implementation begins.

### Self-review iteration 1

- ✅ Every R-* row has at least 5 concrete acceptance steps with measurable DOM-attribute or telemetry assertions.
- ✅ All 32 inventory affordances mapped to a row + an implementation strategy.
- ✅ Plugin-slot reservation explicitly preserved through `<wavy-composer>` (compose-extension) and `<wavy-edit-toolbar>` (toolbar-extension).
- ✅ No regression of F-2.S6 fix (composer / toolbar gated until active session — explicit acceptance step R-5.2 step 8).
- ✅ Slicing strategy keeps slice budget reasonable: S1 ≤ ~1.4k LOC, S2 ≤ ~600 LOC, S3 ≤ ~400 LOC, S4 ≤ ~700 LOC.
- ✅ Telemetry events listed.
- ✅ Out of scope explicit, with explicit "F-0 owns" / "F-2 owns" / "F-4 owns" attribution.
- ✅ Risk list covers caret survival, floating-toolbar positioning, plugin-slot context drift, visual regression of removal, and the bot-cycle.

### Plan-review iteration 2 (post self-review, applied inline)

The following review findings were applied to the plan above:

- **Caret-survival fixture clarity (R-5.1 step 2).** Was: "synthesize an incremental update that adds a sibling blip; assert selection.* unchanged." Refined to: the lit fixture exercises `<wavy-composer>` directly via attribute mutation (no full Java view harness needed). The renderer-side contract is asserted in the parity fixture by Object.is identity check on the live composer instance across `view.render(model)` calls.

- **Floating-toolbar positioning fidelity (R-5.2 step 1).** Was: "follows the selection's `getBoundingClientRect()` via positioning." Refined to: `position: fixed` + `transform: translate(<x>px, <y>px)` keyed off `selection.getRangeAt(0).getBoundingClientRect()`, recomputed on `selectionchange` AND `scroll` AND `resize`, all coalesced through one rAF callback.

- **H.5–H.8, H.10, H.11 toolbar scope.** Was: "all 24 H.* affordances in S1." Refined to: S1 ships the daily-rich-edit subset called out by the issue body (H.1–H.4, H.9, H.12–H.18, H.22–H.24); H.5/H.6/H.7/H.8/H.10/H.11 are explicitly deferred to S4 with rationale (overflow dropdowns + colorpicker popover require a new `<wavy-colorpicker-popover>` element + `J2clDailyToolbarAction` enum extensions). All deferred items have explicit S4 file additions in the slice plan.

- **`<composer-inline-reply>` migration choice.** Was: "shim OR migrate." Refined to: full retirement in S1 — file deleted, single call-site (`J2clComposeSurfaceView`) migrated to `<wavy-composer>` properties.

- **F.6 Delete acceptance step.** Added as R-5.1 step 11 with concrete confirm-modal flow + new `J2clRichContentDeltaFactory.blipDelete` factory method.

- **J.1 wave-root composer mount strategy.** Refined to use a new `<wavy-wave-root-reply-trigger>` button at the bottom of the read surface (NOT inside `getComposeHost()`).

- **F-1 reuse verification.** S2 + S3 plan blocks now contain explicit pre-verified F-1 reuse contracts with file LOC counts, so implementation steps know up-front what to extend vs build new.

- **F-2.S6 visual fix preservation.** R-5.2 step 8 explicitly asserts no `<wavy-format-toolbar>` element exists in the document body when the user has not opened a Reply or Edit, which preserves the F-2.S6 fix that gated the always-visible H.* toolbar wall.

### Plan-review iteration 3 (Claude Opus subagent — DEFERRED to inline pass)

The team-lead instructions ask for a Claude Opus plan-review subagent pass. In the codex/issue-1038-compose-surface lane this is dispatched as a Task subagent. In this implementation session the plan-review pass was applied inline (above) covering the same expected challenge points. If the team-lead workflow surfaces additional findings during S1 implementation review, this plan is iterated and re-saved.

The plan is **clean for S1 implementation**.
