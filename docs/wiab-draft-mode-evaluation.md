# Wiab.pro Draft Mode -- Evaluation and Port Assessment

## Feature Description

Draft mode in Wiab.pro allows a user to toggle a blip's editor into a "draft"
state where edits are accumulated locally without being sent to the server. The
user can then choose to **save** (flush all buffered ops upstream) or **discard**
(invert the composed ops and revert the document). This gives collaborative
editing a "compose then send" workflow similar to email drafts.

### How it works

1. **Editor core (`EditorImpl`)** -- A `boolean draftMode` flag gates the
   outgoing operation sink. When draft mode is on, ops produced by user edits
   are appended to `ContentDocument.draftOps` (a `LinkedList<DocOp>`) instead of
   being sent to `innerOutputSink`. `flushSink()` and the main outgoing sink
   both check `isDraftMode()`.

2. **ContentDocument** -- Stores a `LinkedList<DocOp> draftOps` list. Also has
   `addIncomingOpToDraft(DocOp)` which OT-transforms buffered draft ops against
   incoming server ops so the draft stays valid while remote edits arrive.

3. **Leave draft mode** -- `EditorImpl.leaveDraftMode(saveChanges)`:
   - If `saveChanges == true`: iterates `draftOps` and sends each through
     `innerOutputSink` (flushing to the server).
   - If `saveChanges == false`: composes all `draftOps`, inverts the composed
     op, clears the list, and applies the inverse locally (reverting the
     document).

4. **EditSession** -- Delegates `enterDraftMode()` / `leaveDraftMode()` to the
   editor. Adds `isDraftMode()` / `isDraftModified()` query methods. Provides a
   `Finisher` interface that pops up a save/discard/continue dialog when the
   user tries to leave a modified draft (e.g., pressing Escape, moving focus,
   or closing the wave).

5. **Actions interface** -- Adds `enterDraftMode()` and `leaveDraftMode(boolean
   saveChanges)`. `ActionsImpl` delegates to `EditSession`.

6. **UI controls** -- `DraftModeControlsWidget` (GWT UiBinder): a checkbox
   labeled "Draft" plus Done/Cancel buttons. `DraftModeController` listens to
   `EditSession.Listener` and attaches/shows/hides the controls widget in the
   blip meta area when an edit session starts/ends.

7. **View plumbing** -- `BlipMetaView` interface gains `DraftModeControls`
   inner interface, attach/detach/show/hide methods. `BlipMetaViewImpl.Helper`
   and `FullStructure` connect the DOM element to the widget.
   `BlipMetaDomImpl` and `BlipMetaViewBuilder` add a `DRAFTMODECONTROLS`
   component and a `draftModeControls` CSS class. `Blip.css` styles the
   controls container (hidden by default, shown when editing).

8. **Interactive finisher** -- `InteractiveEditSessionFinisher` shows a modal
   popup (Save / Discard / Continue editing) when the user has unsaved draft
   changes and tries to navigate away or stop editing.

9. **i18n** -- `DraftModeControlsMessages` (Draft, Done, Cancel, hints) and
   `EditSessionFinisherMessages` (Save, Discard, Continue editing, Save draft?).

## Files Touched in Wiab.pro

### New files (not in Apache Wave)
| File | Layer |
|------|-------|
| `DraftModeController.java` | Client controller |
| `DraftModeControlsWidget.java` | GWT widget |
| `DraftModeControlsWidget.ui.xml` | GWT UiBinder |
| `DraftModeControlsWidget.css` (referenced but inlined in ui.xml Resources) | CSS |
| `DraftModeControlsMessages.java` | i18n |
| `InteractiveEditSessionFinisher.java` | Dialog controller |
| `EditSessionFinisherMessages.java` | i18n |
| `EditSessionFinisherMessages_ru.properties` | i18n (Russian) |

### Modified files (differ from Apache Wave)
| File | Changes |
|------|---------|
| `Editor.java` | +4 methods: `isDraftMode`, `isDraftModified`, `enterDraftMode`, `leaveDraftMode` |
| `EditorImpl.java` | +`draftMode` flag, draft gating in `outgoingOperationSink.consume()` and `flushSink()`, `leaveDraftMode` save/discard logic |
| `ContentDocument.java` | +`draftOps` list, `getDraftOps()`, `addIncomingOpToDraft()` (OT transform against incoming ops) |
| `Actions.java` | +`enterDraftMode()`, `leaveDraftMode(boolean)` |
| `ActionsImpl.java` | +delegation to `EditSession` |
| `EditSession.java` | Substantially reworked: +`Finisher` interface, `QuietFinisher`, `isDraftMode()`, `isDraftModified()`, `enterDraftMode()`, `leaveDraftMode()`, `finishEditing()`, `canMoveFocus()` draft guard |
| `BlipMetaView.java` | +`DraftModeControls` inner interface, +attach/detach/show/hide methods |
| `BlipMetaViewImpl.java` | +Helper methods for draft controls, delegate implementations |
| `BlipMetaDomImpl.java` | +`draftModeControls` element field and accessor |
| `BlipMetaViewBuilder.java` | +`DRAFTMODECONTROLS` component enum entry, HTML output |
| `FullStructure.java` | +draft controls widget attach/detach/show/hide in Helper |
| `Blip.css` | +`.draftModeIndicator`, `.draftModeControls` styles |
| `StageThreeProvider.java` | +`DraftModeController.install(...)` call |

## What Exists in Apache Wave

- **CSS stubs only**: `FocusFrameIE.css` has `.draftCheckbox` and `.draftLabel`
  classes (margin/font styling). `FocusFrame.java`'s inner `Css` interface
  declares `draftCheckbox()` and `draftLabel()`. These are unused vestigial
  placeholders -- there is no functional draft mode code.
- The `Editor` interface, `EditorImpl`, `ContentDocument`, `Actions`,
  `EditSession`, `BlipMetaView`, and all view structures have **zero** draft
  mode code.

## Implementation Scope

| Category | Count | Effort |
|----------|-------|--------|
| New Java files | 5 | Small (each is 30-110 lines) |
| New GWT resource files | 2 (ui.xml, css inline) | Trivial |
| Modified Java files | 12 | Moderate -- several are core editor/document classes |
| Server changes | 0 | None |
| Test changes | 0 new tests in Wiab.pro; should add | TBD |

### Dependency analysis

- **No server changes required.** Draft mode is entirely client-side. Ops are
  buffered in the browser and either flushed or reverted locally.
- **No new libraries.** Uses existing GWT UiBinder, DocOp Composer/Transformer/
  Inverter (all present in Apache Wave).
- **Core editor changes are small but surgical.** The `EditorImpl` and
  `ContentDocument` modifications touch the operation pipeline. The OT transform
  in `addIncomingOpToDraft` is critical for correctness when remote ops arrive
  while a draft is open.
- **Wiab.pro EditSession diverges significantly from Apache Wave.** Wiab.pro's
  EditSession uses `ConversationBlip` directly (not `BlipView`), adds the
  `Finisher` pattern, `FocusMoveValidator`, and richer lifecycle. Porting draft
  mode requires either (a) adapting the draft logic to Apache Wave's simpler
  `EditSession` or (b) also porting the EditSession rework (which pulls in
  focus-frame model changes).

### Risk assessment

- **Medium risk in EditorImpl/ContentDocument**: The outgoing op sink and
  document consume path are critical. The draft gating adds conditional branches
  in hot paths. The OT transform in `addIncomingOpToDraft` must be correct or
  data loss/corruption can occur.
- **Low risk in UI layer**: The controls widget, controller, and CSS are
  self-contained.
- **Wiab.pro EditSession is heavily reworked**: The Apache Wave `EditSession`
  takes `BlipView` parameters, uses `FocusFramePresenter.Listener`, and has a
  simpler lifecycle. The Wiab.pro version takes `ConversationBlip`, uses
  `FocusMoveValidator`, and adds the `Finisher` pattern. A direct port would
  require reconciling these differences.

## Recommendation: GO (phased)

Draft mode is a valuable editing UX improvement. It is client-only, requires no
server changes, and the core mechanism (op buffering + compose/invert) is sound
and well-contained. However, the EditSession divergence means a direct
copy-paste port is not feasible. Instead:

### Phase 1: Core op buffering (Editor + ContentDocument)
- Add `isDraftMode`, `isDraftModified`, `enterDraftMode`, `leaveDraftMode` to
  `Editor` interface.
- Implement in `EditorImpl`: `draftMode` flag, sink gating, save/discard logic.
- Add `draftOps` list and `addIncomingOpToDraft` OT transform to
  `ContentDocument`.

### Phase 2: EditSession integration
- Add `enterDraftMode()`, `leaveDraftMode()`, `isDraftMode()`,
  `isDraftModified()` to `EditSession`.
- Add `Finisher` interface adapted to Apache Wave's `BlipView`-based session.
- Wire `finishEditing(boolean endWithDone)` with draft awareness.
- Guard focus moves when draft is modified.

### Phase 3: Actions + UI
- Add `enterDraftMode()`, `leaveDraftMode(boolean)` to `Actions` interface and
  `ActionsImpl`.
- Port `DraftModeControlsWidget`, `DraftModeController`, i18n messages.
- Add view plumbing: `BlipMetaView` draft methods, `BlipMetaDomImpl`,
  `BlipMetaViewBuilder` component, `FullStructure` helper, CSS.
- Wire `DraftModeController.install()` in stage provider.
- Port `InteractiveEditSessionFinisher` for the save/discard dialog.

### Phase 4: Testing and polish
- Add unit tests for op buffering, compose/invert, OT transform against
  incoming ops.
- Manual QA: enter draft, type, save; enter draft, type, discard; enter draft,
  receive remote edit, save; move focus while draft modified.
- Feature flag gating for safe rollout.
