# Issue #971 Rich-Edit And Attachment Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the daily attachment and remaining rich-edit parity gap that blocks practical Lit/J2CL root cutover.

**Architecture:** Build on the #969 compose/toolbar surface instead of extending the current textarea-only J2CL write pilot. Keep the existing server attachment endpoints and attachment-id document model, add J2CL-side upload/metadata/rendering clients, and add only the daily rich-edit commands left unclaimed by #969; any non-daily legacy editor behavior is documented in the parity matrix addendum or a follow-up issue before #971 closes.

**Tech Stack:** J2CL Java, Elemental2 DOM, Lit shell primitives, existing `/attachment`, `/thumbnail`, and `/attachmentsInfo` servlets, current Wave `image@attachment` document model, Maven J2CL tests, Lit web-test-runner tests, `sbt` server/client verification, and worktree smoke/browser verification.

---

## Dependency Gates

- Do not implement #971 until #969 has landed or this branch is rebased onto the final #969 implementation branch. #971 depends on the compose/toolbar surface and must not create a parallel composer shell.
- Before implementation, compare the final #969 packet and PR diff against this plan. Remove any #971 rich-edit item already claimed and implemented by #969; keep #971 focused on `R-5.6` and only the `R-5.7` daily affordances that remain.
- Task 1 is not complete until the final #971-owned `R-5.7` command list is written either into this plan or into a linked #971 issue comment. Do not start Task 2 while the command list is still a candidate set.
- Before touching any compose toolbar, overlay host, focus-management, or shortcut files, inspect #970 status. If #970 modifies the same files, sequence #971 after #970 or split ownership with an issue comment that names the exact files and command IDs.
- Keep `/` serving legacy GWT by default. Verify #971 only through the explicit J2CL/Lit route or rollout flag seam inherited from #969.
- If #969 has not introduced a J2CL rich editor command model, Task 1 below must add that adapter as a narrow extension; do not reimplement GWT `StageThree`.
- GitHub commands in this plan use `--repo vega113/supawave`; this is verified against `origin=https://github.com/vega113/supawave.git`.

## Slice Parity Packet - Issue #971

**Title:** Port attachment and remaining rich-edit parity required for daily Wave use in Lit/J2CL

**Stage:** compose

**Dependencies:** #969, plus coordination with #970 where command palettes, popovers, overlay host, shortcuts, or focus restoration overlap.

### Parity Matrix Rows Claimed

- `R-5.6` - Attachment upload/download/open paths preserve daily behavior, errors are user-visible, and attachment content round-trips through the model.
- `R-5.7` - Only daily remaining rich-edit affordances not closed by #969. Initial candidate set: pasted image upload, attachment caption/display-size insertion, attachment tile rendering, attachment keyboard open/download, clear formatting, indent/outdent, block quote if still missing, and any daily inline-link gap not covered by #969.

### GWT Seams De-Risked

- `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java:187-194` creates the edit toolbar and `:226-274` installs editing, toolbar switching, overlays, reactions, draft mode, and diff upgrade.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java:301-309` creates the attachment ID generator and paste uploader, `:319-362` enumerates daily toolbar groups, `:524-603` uploads and inserts attachment XML, and `:647-737` covers link, heading, indent/outdent, and list commands.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java:94-160` owns selected-file state and upload URL constants, `:560-662` queues per-file uploads and inserts uploaded attachments, `:881-953` POSTs multipart uploads through XHR, and `:1000-1066` resets popup state and wave-ref binding.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/ClipboardImageUploader.java:77-113` detects pasted images and starts upload, `:119-154` inserts attachment XML only after upload success, and `:267-320` POSTs the pasted image to `/attachment/{id}`.
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/ImageThumbnail.java:55-92` defines `image@attachment`, `style`, and `display-size`; `:155-162` builds the captioned attachment XML used by uploads.
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/AttachmentManagerImpl.java:58-66` builds `/attachmentsInfo` requests and `:143-188` parses metadata responses.
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailWidget.java:551-627` chooses thumbnail vs original attachment URL and applies display-size classes.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AttachmentServlet.java:60-62` defines `/attachment` and `/thumbnail`, `:102-168` authorizes and serves attachment bytes, and `:180-227` accepts authorized multipart uploads.
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AttachmentInfoServlet.java:47-99` serves authorized attachment metadata JSON for `/attachmentsInfo`.
- `wave/src/main/java/org/waveprotocol/box/server/attachment/AttachmentService.java:85-135` stores uploads and metadata, attachment URLs, thumbnail URLs, image dimensions, and fallback thumbnail metadata.

### Current J2CL Gap

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeView.java:53-84` and `:103-134` expose create/reply as plain textareas plus submit buttons.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java:180-220` and `:253-301` submit only normalized text drafts.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactory.java:34-71` emits only text DocOp JSON for create and reply.
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java:174-210` preserves the write-session basis but has no attachment/rich-content document operation builder.

## Acceptance Criteria

- A J2CL/Lit daily attachment flow can select one or more files, upload to `/attachment/{id}`, insert `<image attachment="..."><caption>...</caption></image>` with `display-size` where applicable, fetch metadata from `/attachmentsInfo`, render the attachment tile/inline image, and open/download through the authorized `/attachment/{id}` URL.
- Pasted image upload in the J2CL/Lit composer uploads first and mutates the document only after upload success, preserving the GWT failure-safety behavior.
- Attachment upload failures, metadata failures, and unauthorized download/open failures produce user-visible and accessible error state rather than silent no-op.
- Remaining daily rich-edit commands not already delivered by #969 are reachable from the #969 toolbar/composer, preserve selection/caret where the GWT command did, and emit structured document operations rather than flattening to plain text.
- Keyboard/focus coverage includes toolbar reachability, escape/cancel for the attachment picker, focus restoration to the composer after picker close, and keyboard open/download for rendered attachments.
- Accessibility coverage includes `role=status` or an equivalent polite live region for upload progress, `role=alert` or assertive announcement for upload/download errors, reachable labels for file picker controls, and non-color-only failure indication.
- Security coverage confirms the J2CL clients preserve the existing session-cookie authorization path, do not add a client-side CSRF bypass, honor server-side size/mime rejection, and render malware-flagged or unauthorized attachments as blocked/error states instead of openable links.
- Verification evidence includes Maven/JVM tests for operation builders and transport parsing, Lit tests for UI state, worktree smoke, a narrow browser flow on the J2CL route, and a local attachment upload/open/download round trip.
- Drag-and-drop and client-side image compression are not required for the first daily parity claim unless #969/#971 acceptance is amended; if deferred, record them under `docs/j2cl-gwt-parity-matrix.md` Section 10 or a linked follow-up.
- Any discovered non-daily editor behavior left out of #971 is documented either as a `docs/j2cl-gwt-parity-matrix.md` Section 10 ("Deferred Edge Cases") addendum in the same #971 PR or as a linked follow-up issue before claiming the issue complete.

## File Ownership And Proposed Files

- Modify `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeModel.java` only if #969 leaves the model in `search`; otherwise modify the #969 final compose model file.
- Modify `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSidecarComposeController.java` only as an adapter to the #969 composer; do not add another independent compose surface.
- Replace or wrap `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clPlainTextDeltaFactory.java` with a structured builder such as `J2clRichContentDeltaFactory`. Keep the plain-text factory tests passing until #969 intentionally retires it.
- Create `j2cl/src/main/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentIdGenerator.java` for deterministic testable attachment IDs using the existing sidecar seed/counter pattern.
- Create `j2cl/src/main/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentUploadClient.java` for multipart XHR upload to `/attachment/{id}`.
- Create `j2cl/src/main/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentMetadataClient.java` for `/attachmentsInfo?attachmentIds=...` requests and parser ownership.
- Create `j2cl/src/main/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentMetadata.java` as the J2CL-safe DTO for id, filename, mime type, size, creator, attachment URL, thumbnail URL, image dimensions, thumbnail dimensions, and malware flag.
- Create `j2cl/src/main/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentComposerController.java` for file selection, paste handling, upload queue, progress, cancel, and model insertion callbacks.
- Create `j2cl/src/main/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentRenderModel.java` for rendered tile/inline-image decisions.
- Add Lit elements under `j2cl/lit/src/elements/` only for visual presentation inherited from #969: suggested names `compose-attachment-picker.js`, `compose-attachment-card.js`, and `compose-rich-toolbar-extra.js`. If #969 already created equivalent elements, extend those instead.
- Add tests under `j2cl/src/test/java/org/waveprotocol/box/j2cl/attachment/` and `j2cl/lit/test/`. Keep test fixtures independent of live server credentials.
- Add `wave/config/changelog.d/2026-04-23-issue-971-rich-edit-attachments.json` during implementation because this is user-facing behavior.
- Add local verification evidence under `journal/local-verification/2026-04-23-issue-971-rich-edit-attachments.md` during implementation.

## Phased Implementation Slices

### Task 1: Rebase And Freeze Ownership

- [ ] Fetch `origin/main`, #969, and #970 branch heads or PR heads.
- [ ] Rebase the implementation branch onto the final #969 baseline.
- [ ] Diff #969 for compose/toolbar file names and update the file ownership list in this plan if the final files differ.
- [ ] Diff #970 for shared overlay/toolbar/focus files. If any file overlaps, comment on #971 with `#970 overlap: <file list>` and do not edit those files until the owner sequence is clear.
- [ ] Write the final #971-owned `R-5.7` command list into this plan or a linked #971 issue comment. The list must explicitly say which candidate commands were absorbed by #969 and which remain for #971.
- [ ] Confirm no product code is changed before the implementation gate.

Expected command examples:

```bash
gh issue view 969 --repo vega113/supawave --json state,comments,url
gh issue view 970 --repo vega113/supawave --json state,comments,url
git fetch origin
git status --short --branch
```

### Task 2: Add Structured Rich-Content Operation Builder

- [ ] Write failing tests for a builder that emits create/reply DocOp JSON containing text, annotation boundaries for daily inline styles, and `<image attachment="..."><caption>...</caption></image>` element components.
- [ ] Preserve the atomic basis contract from the existing write session: reply operations must use `J2clSidecarWriteSession.getBaseVersion()`, `getHistoryHash()`, and `getChannelId()`.
- [ ] Implement the builder with a typed command input rather than string concatenation at call sites. Suggested minimum API:

```java
J2clRichContentDeltaFactory.CreateWaveRequest createWaveRequest(
    String address, J2clComposerDocument document);

SidecarSubmitRequest createReplyRequest(
    String address, J2clSidecarWriteSession session, J2clComposerDocument document);
```

- [ ] Keep plain-text create/reply behavior as a compatibility path by representing plain text as one `J2clComposerTextSegment`.
- [ ] Commit after tests pass.

Target tests:

```bash
cd j2cl && ./mvnw -Dtest=J2clRichContentDeltaFactoryTest test
```

### Task 3: Add Attachment Upload And Metadata Clients

- [ ] Write failing tests for request construction and metadata parsing. Include encoded attachment IDs with `+`, `/`, `=`, and `:` because prior attachment ID work made encoding a real seam.
- [ ] Implement `J2clAttachmentUploadClient` with multipart fields matching GWT: `attachmentId`, `waveRef`, and `uploadFormElement`.
- [ ] Treat file-picker uploads as successful only for HTTP 200-299 responses whose body contains `OK`, matching `AttachmentPopupWidget`. Treat pasted-image uploads as successful for HTTP 200 or 201, matching `ClipboardImageUploader`. Add one test per success rule.
- [ ] Implement `J2clAttachmentMetadataClient` parser for the numeric proto/Gson field names currently served by `AttachmentInfoServlet` through `ProtoSerializer`. Hand-author fixtures from the current generated field map and include at least one image, one non-image, one malware-flagged, and one missing-metadata fixture.
- [ ] Surface metadata failures as typed error results, not exceptions swallowed by UI.
- [ ] Commit after tests pass.

Target tests:

```bash
cd j2cl && ./mvnw -Dtest='J2clAttachmentUploadClientTest,J2clAttachmentMetadataClientTest' test
```

### Task 4: Add Attachment Composer Controller

- [ ] Write controller tests for selecting files, upload queue order, per-file ID generation, progress state, failure state, cancel/reset, and successful insertion callback.
- [ ] Add paste handling that mirrors GWT's success-before-mutation behavior: capture the insertion intent, upload, then call the rich-content builder insertion only after success.
- [ ] Keep image compression optional. If compression is not practical in the first J2CL slice, document it as non-daily unless the #971 issue explicitly requires mobile large-image compression before parity.
- [ ] Require display size choices `small`, `medium`, `large` and caption fallback to filename.
- [ ] Commit after tests pass.

Target tests:

```bash
cd j2cl && ./mvnw -Dtest=J2clAttachmentComposerControllerTest test
```

### Task 5: Wire Into The #969 Composer And Toolbar

- [ ] Extend the final #969 composer model/view instead of reusing the old `J2clSidecarComposeView` textareas directly.
- [ ] Add toolbar affordances only for #971-owned commands: attachment picker, pasted image, and remaining rich-edit commands not implemented by #969.
- [ ] Preserve focus restoration: opening a picker or link/block-format popover must return focus to the composer trigger or prior caret after close.
- [ ] Emit accessible labels and live-region status for upload progress and errors.
- [ ] Do not implement mentions, tasks, reactions, autocomplete listboxes, or comparable overlays here; those belong to #970.
- [ ] Commit after J2CL tests pass.

Target tests:

```bash
cd j2cl && ./mvnw -Dtest='J2clSidecarComposeControllerTest,J2clAttachmentComposerControllerTest,J2clRichContentDeltaFactoryTest' test
```

### Task 6: Add Attachment Rendering In The J2CL Selected-Wave Surface

- [ ] Add projector tests for selected-wave content containing `image@attachment` and `display-size`.
- [ ] Add `J2clAttachmentRenderModel` to select thumbnail vs original attachment source using the same user-visible rules as `ImageThumbnailWidget`: medium/large image attachments use original attachment URL when metadata says the file is an image; non-images stay tile/card based; missing dimensions use the existing small/tile fallback rather than stretching to zero.
- [ ] Render keyboard-reachable open/download controls with labels derived from filename/mime type.
- [ ] Surface malware or metadata-failure state if present in metadata.
- [ ] Commit after tests pass.

Target tests:

```bash
cd j2cl && ./mvnw -Dtest='J2clSelectedWaveProjectorTest,J2clAttachmentRenderModelTest' test
```

### Task 7: Add Lit Presentation Tests

- [ ] If #969 has Lit composer elements, extend them; otherwise add the three #971 elements listed in file ownership.
- [ ] Add web-test-runner tests for picker open/close, keyboard traversal, caption input, display-size selection, upload progress/error/success rendering, and rendered attachment open/download controls.
- [ ] Verify reduced-motion and focus-visible behavior through DOM assertions rather than screenshots.
- [ ] Commit after Lit tests pass.

Target tests:

```bash
cd j2cl/lit && npm test
cd j2cl/lit && npm run build
```

### Task 8: Add Changelog And Local Verification Record

- [ ] Add a changelog fragment under `wave/config/changelog.d/`.
- [ ] Run changelog assembly and validation:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
```

- [ ] Create `journal/local-verification/2026-04-23-issue-971-rich-edit-attachments.md` with exact commands and observed results.
- [ ] Commit docs/config after validation.

### Task 9: End-To-End Verification

- [ ] Run targeted J2CL/JVM tests:

```bash
cd j2cl && ./mvnw test
```

- [ ] Run Lit tests and build:

```bash
cd j2cl/lit && npm test && npm run build
```

- [ ] Run server/client compile verification:

```bash
sbt wave/compile
sbt compileGwt
```

- [ ] Run worktree smoke from the implementation worktree:

```bash
bash scripts/worktree-boot.sh --port 9900
PORT=9900 JAVA_OPTS='<printed by worktree-boot.sh>' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
```

- [ ] Browser verification required by `docs/runbooks/change-type-verification-matrix.md` because #971 changes J2CL/Lit UI and editor behavior. Exercise only this path: register/sign in a fresh local user, open `http://localhost:9900/?view=j2cl-root`, create/open a wave, attach an image and a non-image file, verify progress/success/error states, verify rendered attachment open/download, paste an image, apply each #971-owned rich-edit command, reload, and confirm the content persists.
- [ ] Stop smoke server:

```bash
PORT=9900 bash scripts/wave-smoke.sh stop
```

## Telemetry And Observability

- Add counters or structured client log events for `attachment.upload.started`, `attachment.upload.succeeded`, `attachment.upload.failed`, `attachment.metadata.failed`, `attachment.open.clicked`, and `richEdit.command.applied`.
- Include result reason fields for upload failure: `network`, `forbidden`, `server`, `unsupported-file`, `cancelled`, and `metadata`.
- If the existing J2CL stats/log channel from #969/#968 is not ready, implement local structured console logs behind the same rollout flag and document the gap in the issue comment. Also create or link a follow-up to migrate those console logs to the shared channel; do not leave console logging as the silent long-term telemetry plan.

## Rollback Plan

- The legacy GWT root remains the default `/` route.
- #971 behavior must sit behind the J2CL/Lit root route and any #969 rollout flag. Disable by turning off the J2CL compose/rich-edit flag or routing users back to legacy GWT.
- Server endpoints are existing shared endpoints. Do not introduce incompatible attachment schema or endpoint changes.
- Keep old plain-text J2CL create/reply path available until the rich-content builder has passed browser verification by routing empty-format documents through the existing plain-text adapter or by guarding rich-content submit behind the #969/#971 rollout flag.

## Non-Goals

- No default-root cutover.
- No legacy GWT root retirement.
- No rewrite of `StageThree`, GWT editor internals, or attachment servlets unless verification proves a shared endpoint bug.
- No mention/autocomplete, task metadata overlays, reactions, or overlay infrastructure owned by #970.
- No exhaustive historical editor parity. Rare formatting commands, old browser-specific keyboard paths, drag-and-drop, client-side compression, mobile/touch-specific toolbar gestures beyond file-picker and paste, and GWT-only harness assumptions are deferred through the parity matrix addendum or follow-up issue unless the final #971 command list names them as daily requirements.

## Risks

- #969 may land a different composer architecture than the current sidecar classes. Mitigation: freeze file ownership after rebasing onto #969 before implementation.
- #970 may touch overlay host and focus restoration. Mitigation: sequence overlapping files and keep #971's picker non-modal unless the #969/#970 host defines a shared modal contract.
- Low-level DocOp JSON is currently built manually in J2CL. Mitigation: typed builder tests must cover every emitted component and preserve version/hash/channel basis.
- `/attachmentsInfo` currently serves proto/Gson numeric fields. Mitigation: isolate parsing in `J2clAttachmentMetadataClient` and test against fixture JSON generated from current server shape.
- Browser file input, paste, drag/drop, and compression APIs differ across desktop/mobile. Mitigation: daily parity requires file-picker and paste first; drag/drop and client compression can be deferred only with explicit addendum/follow-up evidence.

## Deferred Edge-Case Criteria

Document a behavior in `docs/j2cl-gwt-parity-matrix.md` Section 10 ("Deferred Edge Cases") or a linked follow-up issue when all are true:

- It is not needed for daily create/reply/edit/attachment use by the parity matrix.
- It is not part of #969's accepted compose/toolbar row or #970's overlay rows.
- It would require old-browser-specific editor internals, broad GWT harness resurrection, or a separate design/architecture decision.
- The #971 browser path still passes without it.

Do not defer a behavior when it is required for one-file upload, pasted image upload, attachment rendering/open/download, caption/display-size preservation, or a daily rich-edit command explicitly left to #971 after #969.

## Implementation Readiness Verdict

Plan-ready, implementation-blocked. This plan is concrete enough for a worker after #969 lands, but code work must not begin until the #969 dependency gate is satisfied and #970 overlap is checked against the final file list.
