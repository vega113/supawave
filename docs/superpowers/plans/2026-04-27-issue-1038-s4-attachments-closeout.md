# F-3.S4 (slice 4 of 4): Attachments + DocOp round-trip + umbrella closeout

Status: Ready for implementation
Owner: codex/issue-1038-s4-attachments-closeout worktree
Issue: [#1038](https://github.com/vega113/supawave/issues/1038) — closes
Parent tracker: [#904](https://github.com/vega113/supawave/issues/904)

Foundation merged on `origin/main` at write-time:
- F-0 `af7072f9` — wavy design tokens + plugin slot contracts
- F-1 `86ea6b44` — viewport-scoped data path
- F-2 (full umbrella + visual fix #1061)
- F-3.S1 #1063 `efb3b742` — inline rich-text composer + selection-driven format toolbar
- F-3.S2 #1066 + S2-fu #1069 `8d69686b` — mentions + tasks
- F-3.S3 #1070 `f7016d77` — reactions

Parity-matrix rows owned by this slice: **R-5.6** (attachments core) and **R-5.7**
(daily rich-edit DocOp round-trip).

This slice is the **narrowed must-ship + closeout**. The original S4 plan listed
H.5/H.6/H.7/H.8/H.10/H.11 toolbar additions, the `<wavy-colorpicker-popover>`,
header/profile affordances (D.4/D.6/D.8/L.2/L.3), and the B.3 keyboard shortcut
as F-3 work. Per the team-lead 2026-04-27 brief, those are deferred to focused
follow-up issues filed before the PR opens (FU-1..FU-6 below) so the umbrella
can close honestly against today's matrix gate.

## 1. Why this plan exists (narrowed scope)

The previous F-3.S4 attempt asked for clarification on whether to expand or
narrow. The team-lead answer was option C (narrowed scope) + option A
(delegated lane). Slicing decisions:

- **Must ship in S4** to close #1038 honestly: R-5.6 attachments core + F.6
  Delete gateway wiring (S1 deferred the actual delete RPC; only the event
  existed) + R-5.7 DocOp round-trip verification + final-parity rollup +
  umbrella close.
- **Defer to FU-1..FU-6** with row-level acceptance: H.5/H.6, H.7/H.8,
  H.10/H.11+colorpicker, D.4/D.5/D.6/D.8, L.2/L.3, B.3 shortcut. None of these
  is a daily-blocker for the GWT→J2CL parity floor we ship today; each is a
  small, focused, schedulable follow-up.

## 2. Verification ground truth (worktree-checked at `f7016d77`)

### J2CL/Lit client side

- `j2cl/lit/src/elements/wavy-composer.js` (lines 1–1306) — F-3.S1/S2 inline
  rich-text composer. Already has `_onBodyPaste` for clipboard images (R-5.6
  step 2). **Has no drop / dragover handlers** — S4 adds them. Body
  contenteditable carries text, mention chips, task lists; rich-content guard
  in `_bodyHasRichContent`/`_isControllerReset` already exists.
- `j2cl/lit/src/elements/wavy-format-toolbar.js` (lines 1–246) — F-3.S1 toolbar
  with H.1–H.4, H.9, H.12–H.18 + H.20 + clear-formatting. **S4 adds H.19
  (attachment paperclip)** as a dedicated toolbar action that re-emits
  `wavy-format-toolbar-action {actionId: "attachment-insert"}`.
- `j2cl/lit/src/elements/wave-blip-toolbar.js` (1–109) — F-2 toolbar with
  Reply/Edit/Link/Overflow buttons. **No Delete button today.** S4 adds an
  explicit Delete button + emits `wave-blip-toolbar-delete`.
- `j2cl/lit/src/elements/wave-blip.js` (1–434) — F-2 wrapper. S4 listens for
  `wave-blip-toolbar-delete` and re-emits `wave-blip-delete-requested {blipId,
  waveId}` (mirrors the existing reply/edit re-emit pattern).
- `j2cl/lit/src/elements/compose-attachment-picker.js` (1–323) — F-1 element
  used by `J2clComposeSurfaceController.openAttachmentPicker`. Already shipped.
  Used unchanged.
- `j2cl/lit/src/elements/compose-attachment-card.js` (1–207) — F-1 inline card
  with Open + Download buttons. Already shipped. Used unchanged.
- `j2cl/lit/src/index.js` — registers the existing elements; no new elements
  in S4.

### J2CL Java side

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
  (1–1936). Already has:
  - `Listener.onAttachmentFilesSelected(selections)` (line 57)
  - `Listener.onPastedImage(imagePayload)` (line 59)
  - `onAttachmentFilesSelected` impl (line 640) wiring through
    `J2clAttachmentComposerController.selectFiles`
  - `onPastedImage` impl (line 690) wiring through `pasteImage`
  - `handleAttachmentToolbarAction` (line 1586) — opens picker via
    `view.openAttachmentPicker()`
  - `isAttachmentAction` (line 1809) — gates rich-edit toolbar actions vs
    attachment toolbar actions

  **S4 adds:**
  - `Listener.onDroppedFiles(List<AttachmentFileSelection>)` — symmetric with
    the paste path; reuses the same `selectFiles` plumbing.
  - `Listener.onDeleteBlipRequested(blipId)` — wired to a new
    `J2clRichContentDeltaFactory.blipDeleteRequest(...)` that emits a
    `<delete>`/blip-removal supplement op.
  - `compose.attachment_dropped` telemetry event (mirrors
    `compose.attachment_pasted`).
  - `compose.blip_deleted` telemetry event with `outcome` field.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java`
  (1–636). Already listens for `attachment-paste-image` (line 122/347). **S4
  adds:** body-level listeners for `wave-blip-delete-requested` and
  `wavy-composer-attachment-dropped` (the new event the composer dispatches
  on file drop). Both route through the new listener methods.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java`
  (1–937). Already has `imageAttachment` builder + `appendImageAttachment` ops
  emitter. **S4 adds:** `blipDeleteRequest(address, session, blipId)` that
  builds a SidecarSubmitRequest carrying a deletion supplement op (matches
  the existing GWT delete-blip path used by F.6).
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clComposerDocument.java`
  (1–125). Existing `imageAttachment(attachmentId, caption, displaySize)`
  builder is unchanged.

### Server side

- No server changes in this slice. The Wave attachment storage gateway is
  already production-wired through `J2clAttachmentComposerController` and the
  upload-client paths shipped pre-F-3 (`PR #1024` hydrate, `PR #1023` Lit
  primitives, `PR #1017` toolbar wiring, `PR #1022` rendering).

### Existing tests we extend

- `wave/src/jakarta-test/java/.../J2clStageThreeComposeS3ParityTest.java` —
  existing source-level pin pattern; S4 follows the same pattern for the new
  per-row test.
- `wave/src/jakarta-test/java/.../J2clStageOneFinalParityTest.java` (1–227) —
  existing F-2 rollup with `@Suite`, `ROW_OWNERS` map, summary report. S4
  produces an isomorphic `J2clStageThreeFinalParityTest`.

## 3. Scope (must-ship, sections A–D from the brief)

### Section A. R-5.6 Attachments (CORE)

#### A.1 H.19 paperclip in `<wavy-format-toolbar>`

The S1 toolbar lists `attachment-insert` as a toolbar-action ID but does NOT
render a button for it (the daily-rich-edit subset that S1 shipped excluded
attachments). S4 adds a dedicated **toolbar-button[data-toolbar-action="attachment-insert"]**
to the `DAILY_RICH_EDIT_ACTIONS` list, after `clear-formatting` and before
`insert-task`. The button bubbles `wavy-format-toolbar-action {actionId:
"attachment-insert"}` like every other action.

The composer controller already routes that action to
`view.openAttachmentPicker()` → `attachmentInput.click()` (the hidden file
input on the reply host) via `handleAttachmentToolbarAction`. The picker
emits `change` → `Listener.onAttachmentFilesSelected` → existing upload path.
No new wiring on the Java side; the toolbar button is the only new surface.

#### A.2 Drag-drop into the composer body

`<wavy-composer>` adds two body event listeners:

- `dragover` — `event.preventDefault()` + reflect a `data-droptarget="true"`
  attribute on the body so CSS can show a drop hint. (S1's existing
  `[data-composer-body]` styling carries the focus ring; S4 adds a soft
  border-tint variant when `[data-droptarget="true"]` is set.)
- `drop` — collect `event.dataTransfer.files` (filtering out empty drops),
  call `event.preventDefault()`, then dispatch a single
  `wavy-composer-attachment-dropped` CustomEvent with `{detail: {files,
  replyTargetBlipId}}`. The view re-routes through
  `Listener.onDroppedFiles(selections)` which calls the existing
  `selectFiles` path. The `data-droptarget` attribute is removed on `drop`
  AND on `dragleave` (the latter only when leaving the body, not bubbling
  out of nested children — guard with `event.relatedTarget`).

CSS:
```
[data-composer-body][data-droptarget="true"] {
  border-color: var(--wavy-signal-cyan, #22d3ee);
  background: var(--wavy-signal-cyan-soft, rgba(34, 211, 238, 0.12));
}
```

#### A.3 Paste-image (already shipped — verified end-to-end in S4 test)

The `_onBodyPaste` handler in `<wavy-composer>` (line 965) already detects
image-MIME clipboard items + image files and dispatches
`attachment-paste-image`. The view forwards to
`Listener.onPastedImage(imagePayload)` which calls
`J2clAttachmentComposerController.pasteImage`. **Verified by an explicit
parity test in S4** that pins both the lit handler shape AND the controller
path so a regression on either side is caught.

#### A.4 Inline render at originating blip

`J2clAttachmentComposerController` already inserts the attachment node into
the composer document via the `insertionCallback`. After successful upload
the existing `J2clRichContentDeltaFactory.appendImageAttachment` writes the
attachment into the next reply submit. The renderer-side path
(`J2clReadSurfaceDomRenderer` / `attachment-display-sizes.js`) already
mounts `<compose-attachment-card>` on the originating blip with the
`thumbnail-url` set for image MIME types and falls back to a download chip
for non-images.

S4 does NOT introduce new render paths. The S4 parity test asserts the
production read renderer mounts an `<compose-attachment-card>` per
attachment with the correct `data-attachment-id`, `mime-type`, and (for
images) `thumbnail-url`. That keeps the single round-trip claim load-bearing
without churning the renderer.

#### A.5 Failure-mode error UI via the F-0 toast/alert recipe

`compose-attachment-card` already renders `[role="alert"]` with the upload
error text when `error` is set, and `data-state="attachment-error-state"`
appears on the picker overlay (line 185 of `compose-attachment-picker.js`).
The compose controller already routes upload failures into
`activeCommandId = ATTACHMENT_ERROR_STATE_ID` + `commandErrorText` (line
682 / 718) which surfaces on the inline composer's alert region.

S4 adds:
- A retry button on the failure surface that re-emits
  `attachment-files-selected` for the failed selection (the picker is
  already mounted; the controller treats the second selection as a fresh
  upload).
- A test asserting the alert region shows the error text and the retry
  button is present in the `error` state.

#### A.6 F.6 Delete gateway wiring (deferred from S1)

S1 plan said "F.6 Delete: confirm modal → DocOp via
`J2clComposeSurfaceController.deleteBlip`" but only the event scaffold (no
button, no controller method, no factory method). S4 ships:

- **`<wave-blip-toolbar>` Delete button** with
  `data-toolbar-action="delete"` and `aria-label="Delete this blip"`.
  Emits `wave-blip-toolbar-delete`.
- **`<wave-blip>` re-emit:** listen for `wave-blip-toolbar-delete` and
  re-emit `wave-blip-delete-requested {blipId, waveId}` on the document
  body.
- **Confirm modal:** the Java view shows a small wavy confirm dialog
  ("Delete this blip?" Cancel / Delete). To keep the slice small, the
  confirm uses `window.confirm(...)` is **NOT** acceptable per memory
  guidance (`feedback_no_browser_popups.md` — never Window.alert/confirm).
  Instead the view dispatches a body-level `wavy-confirm-requested
  {message, confirmLabel, cancelLabel, requestId}` event; a new tiny
  `<wavy-confirm-dialog>` element listens, renders an inline modal using
  the F-0 wavy recipe, and responds with a `wavy-confirm-resolved
  {requestId, confirmed}` event.
- **Controller:** new `Listener.onDeleteBlipRequested(blipId)` calls a new
  `Gateway.deleteBlip(...)` method (added to the Gateway interface). The
  default Gateway impl translates that into a standard `submit(...)` call
  carrying the new `J2clRichContentDeltaFactory.blipDeleteRequest(...)`
  output.
- **Delta factory:** `blipDeleteRequest(address, session, blipId)` emits a
  supplement op that toggles the wavelet's blip-deletion state via the
  same `<delete>` semantic the GWT path uses. The op shape is pinned by
  the new parity test (existing `<delete>` writer paths in
  `J2clRichContentDeltaFactory` already build retain + delete-element
  ops; the new helper composes them with the blip id).
- **Telemetry:** `compose.blip_deleted {outcome}` event recorded after
  the confirm resolves.
- **Read renderer:** on remote blip-deletion fragment update, the
  existing F-1 fragment patcher already removes the blip node — no new
  renderer path is needed.

### Section B. R-5.7 DocOp round-trip verification for daily affordances

The S1 lit fixtures asserted the **DOM** result of clicking the bulleted-list
/ numbered-list / heading→quote / link buttons (the body's `<ul>`/`<ol>`/
`<blockquote>`/`<a>` markup). The model **round-trip** assertion was deferred:
the controller's DocOp delta path through `J2clRichContentDeltaFactory` was
not pinned with a focused test that asserts a list-item op flows through
on submit.

S4 adds **focused unit tests** (in the existing
`j2cl/src/test/java/.../richtext/J2clRichContentDeltaFactoryTest.java` style
or a sibling test file) that:

- Build a `J2clComposerDocument` with `text("Item 1\n")`, `text("Item 2\n")`,
  and a `list/unordered` annotated wrapper; assert
  `createReplyRequest(...)` returns a `SidecarSubmitRequest` whose JSON
  delta carries the expected `list/unordered` annotation start and end ops
  in the right offsets.
- Same for `list/ordered`, `block/quote`, and `link/manual` (the link case
  is a thin extension of the existing `appendMentionInsert` path: the
  link-modal submit produces the same annotated-text component shape with
  key `link/manual`).

The tests **fail on `origin/main`** because no current test asserts the
`list/unordered` annotation key actually appears in a delta (the S1 lit
fixtures only assert DOM markup). After this slice's controller wiring lands
the tests pass — that's the proof the round-trip works.

The brief is explicit: "no new UI for this row — just round-trip
assertions." Accordingly S4 does NOT add a list-builder helper to
`J2clComposerDocument`; instead the controller's `buildDocument(...)` path
already serializes annotated text components, and the lit composer's
`serializeRichComponents()` (line 557 of `wavy-composer.js`) already walks
mention chips. **S4 adds an analogous walker for `<ul>`, `<ol>`,
`<blockquote>`, `<a>` elements** (in the lit composer) that produces a
matching annotated component that the controller forwards to the existing
`annotatedText(...)` builder method. That's the closing of the round-trip
loop; the tests assert the loop closes.

For each of the four affordances, the round-trip test asserts:
1. Clicking the toolbar button produces the expected DOM in the body
   (already covered by S1 lit tests — re-asserted in the parity test for
   completeness).
2. Submitting produces a `SidecarSubmitRequest` whose JSON delta carries
   the expected annotation key + value at the expected offsets.
3. The annotation key matches what the GWT write path uses (pinned at the
   factory source so a future renaming surfaces in CI).

### Section C. Final parity roll-up

New `J2clStageThreeFinalParityTest` modeled on
`J2clStageOneFinalParityTest`:

- **`@Suite.SuiteClasses`** chains the per-row test classes: 
  - `J2clStageThreeComposeS1ParityTest` (R-5.1 + R-5.2 base) — created if
    not present today; if present, used as-is.
  - `J2clStageThreeComposeS2ParityTest` (R-5.3 + R-5.4) — exists.
  - `J2clStageThreeComposeS3ParityTest` (R-5.5) — exists.
  - `J2clStageThreeComposeS4ParityTest` (R-5.6 + R-5.7) — created in S4.
- **`ROW_OWNERS`** map enumerates R-5.1..R-5.7 and asserts each row has at
  least one passing assertion in its owner class. Same `everyOwnedGateRowHasAPassingAssertionClass`
  shape as F-2.
- **Closeout summary report** printed at test end:
  ```
  F-3 (umbrella #1038) per-row parity roll-up
  ===========================================
    [PASS] R-5.1 compose / reply flow            ←  J2clStageThreeComposeS1ParityTest
    [PASS] R-5.2 selection-driven toolbars        ←  J2clStageThreeComposeS1ParityTest
    [PASS] R-5.3 mentions                         ←  J2clStageThreeComposeS2ParityTest
    [PASS] R-5.4 tasks                            ←  J2clStageThreeComposeS2ParityTest
    [PASS] R-5.5 reactions                        ←  J2clStageThreeComposeS3ParityTest
    [PASS] R-5.6 attachments                      ←  J2clStageThreeComposeS4ParityTest
    [PASS] R-5.7 daily rich-edit DocOp round-trip ←  J2clStageThreeComposeS4ParityTest
  Total rows covered: 7 / 7
  ```
- Verifies the chained suite passes via the same `Assume.assumeTrue(...)`
  opt-in flag as F-2 (`-Dj2cl.run.chained.parity=true`).

If the per-row S1 test class is missing (worktree-checked: only S2 and S3
exist as separate per-row tests today), S4 adds a small
`J2clStageThreeComposeS1ParityTest` that pins the source-level contracts S1
already shipped (composer registration, format toolbar registration,
listener events). This is a thin source-pin test, not a re-execution of the
S1 lit suite — that suite runs independently via `j2clLitTest`.

### Section D. Closeout

- PR title: `F-3 (slice 4 of 4): Attachments + DocOp round-trip + umbrella closeout`.
- PR body starts with `Closes #1038. Updates #904.` followed by a
  Follow-ups section linking FU-1..FU-6 (issue numbers filled in at PR
  creation time).
- After merge, the PR auto-merge action posts the celebration comment
  template (in the brief) to #904 with the merge SHA + ISO timestamp +
  the FU issue numbers; this slice's implementation includes the comment
  template wired through the existing post-merge action.
- Slice plan checklist on #1038 is fully checked at PR open time (the
  closeout itself is the last unchecked box; checking it via `gh issue
  edit --body` is part of the post-merge action).

## 4. Out of scope (deferred to FU-1..FU-6)

Each follow-up issue gets row-level acceptance and references #1038 as
historical context.

- **FU-1 — H.5 Superscript + H.6 Subscript** (with DocOp round-trip
  through `style/verticalAlign=super|sub` annotation). Toolbar buttons
  added to `wavy-format-toolbar` between strikethrough and heading.
- **FU-2 — H.7 Font family + H.8 Font size dropdowns**. Requires
  extending `J2clDailyToolbarAction` with `FONT_FAMILY` and `FONT_SIZE`
  enum entries and wiring the controller to emit
  `style/fontFamily=<name>` and `style/fontSize=<px>` annotations.
- **FU-3 — H.10 Text color + H.11 Highlight color** with the new
  `<wavy-colorpicker-popover>` element. Requires `TEXT_COLOR` +
  `HIGHLIGHT_COLOR` enum entries and a colorpicker popover with the
  wavy palette.
- **FU-4 — D.4 Add participant + D.5 New wave with current participants
  + D.6 Public/private toggle + D.8 Lock/unlock root blip**. Owned by
  the F-3 umbrella per the issue body, but each is a wave-header
  affordance that is not on the daily-blocker path. The F-2 wave-header
  emits the underlying CustomEvents already; FU-4 wires the modal +
  supplement-op handlers.
- **FU-5 — L.2 Send Message → 1:1 wave + L.3 Edit Profile**. Profile
  overlay actions; Send-Message kicks off the existing create-wave
  path with a single participant; Edit-Profile opens the existing
  GWT settings page.
- **FU-6 — B.3 Shift+Cmd+O New Wave keyboard shortcut**. Global
  keydown listener that focuses the create-wave composer body. The
  New-Wave button itself is already on the search rail.

## 5. Files

### S4 NEW

- `j2cl/lit/src/elements/wavy-confirm-dialog.js` (~120 LOC) — small
  inline modal that listens for body-level
  `wavy-confirm-requested` and answers `wavy-confirm-resolved`. Uses
  the F-0 wavy recipe (no Window.confirm per
  `feedback_no_browser_popups.md`).
- `j2cl/lit/test/wavy-confirm-dialog.test.js` (~80 LOC).
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageThreeComposeS1ParityTest.java`
  (~100 LOC) — source-level pins for R-5.1 + R-5.2, used by the rollup.
  (Optional if a sibling already exists — current tree has only S2 +
  S3 per-row tests; adding S1 closes the rollup gap.)
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageThreeComposeS4ParityTest.java`
  (~250 LOC) — R-5.6 + R-5.7 source-level pins.
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/J2clStageThreeFinalParityTest.java`
  (~180 LOC) — rollup with `@Suite` and `ROW_OWNERS` map.
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactoryListsRoundTripTest.java`
  (~150 LOC) — R-5.7 model round-trip assertions for `list/unordered`,
  `list/ordered`, `block/quote`, `link/manual`.

### S4 MODIFIED

- `j2cl/lit/src/elements/wavy-composer.js`:
  - Add `dragover` / `drop` / `dragleave` body listeners.
  - Add `serializeRichComponents` walker arms for `<ul>`/`<ol>`/
    `<blockquote>`/`<a>` (R-5.7 closing the round-trip loop).
  - Add `[data-droptarget]` CSS state.
- `j2cl/lit/src/elements/wavy-format-toolbar.js`:
  - Add the `attachment-insert` toolbar button (H.19) to the
    `DAILY_RICH_EDIT_ACTIONS` list.
- `j2cl/lit/src/elements/wave-blip-toolbar.js`:
  - Add the Delete button + emit `wave-blip-toolbar-delete`.
- `j2cl/lit/src/elements/wave-blip.js`:
  - Listen for `wave-blip-toolbar-delete` and re-emit
    `wave-blip-delete-requested {blipId, waveId}`.
- `j2cl/lit/src/elements/compose-attachment-card.js`:
  - Add a `retry` action button to the `[role="alert"]` region; emits
    `attachment-retry {attachmentId}`.
- `j2cl/lit/src/index.js`:
  - Register `wavy-confirm-dialog`.
- `j2cl/lit/test/wavy-composer.test.js`:
  - Add tests for drag-drop end-to-end + the rich serializer arms.
- `j2cl/lit/test/wavy-format-toolbar.test.js`:
  - Add a test asserting the H.19 button renders + bubbles
    `wavy-format-toolbar-action {actionId: "attachment-insert"}`.
- `j2cl/lit/test/wave-blip-toolbar.test.js` (existing or new):
  - Add a test asserting the Delete button emits the event.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceView.java`:
  - Body-level listener for `wavy-composer-attachment-dropped` →
    `Listener.onDroppedFiles`.
  - Body-level listener for `wave-blip-delete-requested` →
    show the wavy confirm dialog → on confirm, call
    `Listener.onDeleteBlipRequested(blipId)`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`:
  - Add `Listener.onDroppedFiles(List<AttachmentFileSelection>)` (default
    delegates to `onAttachmentFilesSelected` so test doubles compile
    unchanged).
  - Add `Listener.onDeleteBlipRequested(String blipId)` (default no-op).
  - Add a `Gateway.deleteBlip(...)` interface method (default impl
    delegates to `submit(...)` with a `blipDeleteRequest` payload).
  - Wire telemetry: `compose.attachment_dropped`, `compose.blip_deleted`.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java`:
  - Add `blipDeleteRequest(address, session, blipId)` factory method
    that builds a SidecarSubmitRequest with the blip-deletion
    supplement op.
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`
  (existing) — extend with onDroppedFiles + onDeleteBlipRequested tests.

### S4 verification commands

```
sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild
```

Each target must exit 0. The new S4 parity test must FAIL on `origin/main`
(verified by reverting the wiring and re-running) and PASS after this slice's
implementation lands. The R-5.7 round-trip test must FAIL on `origin/main` to
prove the assertion is real, not vacuous.

## 6. Telemetry surface

Added on top of the F-3.S1/S2/S3 telemetry (already shipped):

- `compose.attachment_dropped` — fields: `outcome` (`success` | `blocked`),
  `kind` (`image` | `file`).
- `compose.attachment_retried` — fields: `outcome`, `kind`. Recorded when
  the user clicks the retry button on a failure surface.
- `compose.blip_deleted` — fields: `outcome` (`success` | `cancelled` |
  `failure`).

The existing `compose.attachment_uploaded` (S1) and
`compose.attachment_pasted` (S1) cover the upload + paste outcomes; S4 does
NOT rename them.

## 7. Risk list

1. **`window.confirm` ban** (`feedback_no_browser_popups.md`). The S1 plan
   showed a confirm dialog as the F.6 Delete UX; this slice MUST use a
   styled modal. *Mitigation:* the new `<wavy-confirm-dialog>` element is
   small, isolated, and pinned by a unit test. The Java view dispatches a
   body-level `wavy-confirm-requested` event and awaits
   `wavy-confirm-resolved` — no synchronous blocking.
2. **CSS template literal back-tick footgun bit S3** (per the brief). All
   new CSS strings in this slice are template literals inside `static
   styles = css\`\`` blocks; comments inside use `/* ... */` only, never
   back-tick characters. *Mitigation:* explicit lint pass on every new
   CSS block.
3. **Drag-drop nested-element flicker.** A naive `dragleave` handler clears
   the drop hint when entering a nested child. *Mitigation:* the handler
   guards on `event.relatedTarget` not being a descendant of the body.
4. **Round-trip test claim load-bearing.** The R-5.7 test must fail on
   `origin/main`. *Mitigation:* explicit pre-implementation `git stash` +
   re-run check documented in the impl-review subagent prompt; if the
   test passes vacuously, the test is rewritten.
5. **F-3.S1 per-row parity test missing.** The rollup's `@Suite.SuiteClasses`
   chain needs an `S1ParityTest` to span R-5.1+R-5.2. *Mitigation:* add a
   thin source-level pin (composer registration, format toolbar
   registration, listener events) that takes ≤ 100 LOC. Same shape as
   `J2clStageThreeComposeS3ParityTest`.
6. **Bot review cycle.** Reserve 2-3 fix passes per slice (per F-2
   pattern). *Mitigation:* batch all bot-thread fixes into one revision.

## 8. Plan-review pass log

Self-review iteration 1:
- [x] Sections A, B, C, D each have a concrete file list + ≥3 acceptance
      bullets that yield a measurable assertion.
- [x] FU-1..FU-6 are listed in §4 with row-level acceptance notes.
- [x] Verification gate uses the SBT-only target set.
- [x] CSS-template-literal footgun called out (risk 2).
- [x] No `window.confirm` (risk 1).
- [x] Telemetry surface listed.
- [x] R-5.7 test must fail on `origin/main` to prove non-vacuous.

Plan-review subagent pass: scheduled to run inline at the start of the
implementation phase. If the subagent surfaces additional findings during
plan review, this plan is iterated and re-saved before any code changes
land.
