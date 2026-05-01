# Issue #1167 Follow-Up: Inline Anchor And Preview Hydration Parity

## Goal

Close the next concrete J2CL/GWT threading parity gap after PR #1173:

- The read-surface preview route must stay visible after JavaScript hydration.
- The J2CL read pipeline must stop losing inline-reply anchor evidence before the renderer can place inline threads like GWT.

This lane is a follow-up under #1167, not a replacement for the merged thread-chrome slice.

## Current Findings

- `HtmlRenderer.renderJ2clReadSurfacePreviewPage()` documents `/?view=j2cl-root&q=read-surface-preview` as a server-rendered fixture that needs no websocket activity to see the visual state.
- The regular J2CL root controller still starts the live search/selected-wave controllers on that preview route, so the fixture can be replaced by an empty selected-wave state during client boot.
- `J2clSelectedWaveView.shouldPreserveServerSnapshot()` already preserves real server-first selected-wave DOM while an empty/loading live model arrives, but preview mode is synthetic and should not enter the live sidecar route at all.
- `SidecarConversationManifest` parses parent/thread structure and reply insert positions from the manifest, but it does not currently expose whether a thread is an inline thread.
- `J2clReadBlipContent.parseRawSnapshot()` parses text, attachments, and task annotations from raw blip snapshots, but strips `<reply id="...">` anchors before the renderer can place an inline reply at the anchor position.
- Existing shared local file-store samples previously had no raw `<reply id="...">` anchors, so production-data-safe inline placement needs a regression fixture before we claim full parity.

## Scope

### In Scope

- Add a root-shell preview-mode guard so `data-j2cl-read-surface-preview="true"` enhances the static preview DOM without opening live sidecar search/selected-wave controllers.
- Add tests proving preview mode is detected and does not route through live startup.
- Extend the J2CL read-content parsing seam with inline-reply anchor metadata from raw snapshots.
- Add regression tests that prove `<reply id="...">` anchors are retained as metadata while visible text remains unchanged.
- Thread the anchor metadata far enough into the renderer/model to enable a follow-up placement change without another transport redesign.

### Out Of Scope

- Do not redesign the whole selected-wave transport.
- Do not change GWT behavior.
- Do not claim all #1167 acceptance is complete unless browser verification proves real inline blips render at their GWT anchor positions.
- Do not use Claude Code review in this lane; self-review and PR review comments are the review gates.

## Implementation Plan

1. **Red tests: preview hydration guard**
   - Add a focused `J2clRootShellControllerTest` case for preview-mode detection.
   - Add a DOM-capable test when feasible that creates a synthetic preview host and verifies `start()` does not remove server-first preview content.

2. **Green: preview startup path**
   - Add `J2clRootShellController.isReadSurfacePreviewHost(...)`.
   - At the start of `J2clRootShellController.start()`, if preview mode is active:
     - construct `J2clRootShellView`;
     - construct `J2clSearchPanelView` in root-shell mode to adopt the existing workflow;
     - construct `J2clSelectedWaveView` so `enhanceExistingSurface()` wires existing blips and compact thread controls;
     - publish a non-live preview status if possible;
     - return before creating `J2clSearchGateway`, route controller, websocket-driven controllers, or compose/live startup.

3. **Red tests: inline anchor metadata**
   - Add `J2clReadBlipContentTest` coverage for a raw blip snapshot containing `<reply id="t+inline"></reply>` inside body text.
   - Expected result:
     - visible text does not include the raw reply id;
     - parsed content exposes one anchor with `threadId=t+inline`;
     - anchor offset is the visible-text offset at which the marker appeared.

4. **Green: anchor parser seam**
   - Add a small immutable inline-anchor value type under `j2cl/read`.
   - Extend `J2clReadBlipContent` to expose `getInlineReplyAnchors()`.
   - Keep existing visible-text, attachment, task, and deleted parsing behavior unchanged.

5. **Thread anchor metadata toward rendering**
   - Extend `J2clReadBlip` and `J2clReadWindowEntry` with immutable inline-anchor lists.
   - Preserve equality/cache checks so a changed anchor list triggers repaint.
   - Add renderer tests proving a blip created from parsed content carries anchor metadata into the rendered host as data attributes, without changing placement yet.

6. **Self-review**
   - Re-read the diff for scope creep, XSS safety, null/default handling, and compatibility with viewport-window rendering.
   - Confirm no Maven commands are introduced.

7. **Verification**
   - Focused J2CL/Lit or J2CL Java tests relevant to touched files.
   - Full SBT-only gate before PR:
     - `python3 scripts/assemble-changelog.py`
     - `python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
     - `git diff --check`
     - `sbt --batch compile Test/compile j2clSearchTest j2clLitTest`
   - Local browser sanity on `/?view=j2cl-root&q=read-surface-preview` proving the fixture still has rendered `wave-blip` elements after hydration.

## Risks

- If preview-mode startup skips too much, custom elements might still upgrade but Java-owned event bridges might not bind. The preview is explicitly documented as non-live, so the acceptable target is visual/hydrated fixture stability, not live search/write behavior.
- Anchor metadata alone does not produce full GWT inline placement. It is the minimum safe data contract needed before moving thread DOM to exact body offsets.
- If real selected-wave updates do not carry raw blip snapshots with `<reply>` anchors, true anchor placement remains blocked on a server payload extension.

## Self-Review Notes

- The plan keeps the next PR small enough to review: one preview hydration bug plus one anchor-data seam.
- It does not overclaim #1167 completion.
- It obeys the updated review instruction: no Claude Code review; rely on self-review and PR review comments.
- It preserves SBT-only verification.
