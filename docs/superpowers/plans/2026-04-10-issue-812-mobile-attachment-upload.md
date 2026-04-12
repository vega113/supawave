# Issue 812 Mobile Attachment Upload Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reproduce and fix the mobile image attachment regression so mobile picker-selected images upload into the wave, and pasted images either upload correctly or fail in an explicit supported way.

**Architecture:** Treat this as a boundary-debugging task, not a speculative client patch. First prove where the flow breaks across mobile picker/paste -> client state -> upload request -> Jakarta servlet -> attachment persistence -> wave insertion. Then add the narrowest regression test at the failing seam, implement the minimal fix in the runtime-active path, and finish with targeted tests plus a narrow mobile browser verification pass on the local worktree server.

**Tech Stack:** SBT, GWT client code, Jakarta servlet runtime, JUnit/Jakarta IT, local browser verification against a staged/worktree server.

---

## Scope

- In scope:
  - mobile attachment picker upload path
  - mobile image paste path
  - the shared upload request contract to `/attachment/{id}`
  - runtime-active Jakarta servlet behavior
  - the client insertion path that adds uploaded attachments back into the wave
- Out of scope:
  - unrelated attachment UX redesign
  - non-image attachment behavior unless the failing seam proves it is shared
  - server deployment/config cleanup beyond what is required for the fix

## Expected Files

- Investigate / likely modify:
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/ClipboardImageUploader.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`
  - `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AttachmentServlet.java`
- Tests / likely modify or create:
  - `wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/AttachmentServletJakartaIT.java`
  - `wave/src/test/java/org/waveprotocol/wave/client/editor/EditorContextAdapterHandlerTest.java`
  - `wave/src/test/java/...` new focused client-side regression only if the failing seam is client-only and cannot be locked down server-side
- Required release note:
  - `wave/config/changelog.d/2026-04-10-issue-812-mobile-attachment-upload.json`

## Acceptance Criteria

- Mobile file-picker-selected image attachments upload end-to-end and insert into the wave.
- Mobile paste-to-upload either works end-to-end or the unsupported mobile path is detected and surfaced explicitly instead of failing silently.
- A targeted automated regression covers the identified failing seam.
- Local verification evidence records the exact commands run and the observed browser result on the issue worktree server.

## Task 1: Reproduce And Isolate The Failing Boundary

**Files:**
- Read: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java`
- Read: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/ClipboardImageUploader.java`
- Read: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AttachmentServlet.java`
- Evidence: `journal/local-verification/2026-04-10-issue-812-mobile-attachment-upload.md` or issue comment summary

- [ ] Start the worktree server for browser repro on a dedicated port and record the exact start/check/stop commands.
- [ ] Reproduce the picker flow in a mobile-emulated browser and capture:
  - whether the selection event fires
  - whether preview/queue state updates
  - whether an upload request is issued
  - the request payload fields
  - the server response status/body
  - whether the uploaded attachment is inserted into the wave
- [ ] Reproduce the paste flow in the same browser session and capture the same boundary data.
- [ ] Write a short architect summary in the GitHub issue comment naming the failing seam and the narrowest viable fix.

## Task 2: Write The Failing Regression First

**Files:**
- Modify: the smallest test file that can prove the confirmed seam
- Prefer:
  - `wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/AttachmentServletJakartaIT.java` if the request contract / receipt seam is broken
  - a focused client-side test near the upload/insertion code if the request never leaves the browser or the response is mishandled client-side

- [ ] Add one failing regression that matches the confirmed broken mobile behavior at the identified seam.
- [ ] Run only that test first and verify it fails for the expected reason.

Run target:

```bash
sbt "JakartaIT/testOnly org.waveprotocol.box.server.jakarta.AttachmentServletJakartaIT"
```

Client-side alternative only if needed:

```bash
sbt "testOnly <focused client test class>"
```

## Task 3: Implement The Minimal Fix

**Files:**
- Modify only the runtime seam proven by Task 1 and locked down by Task 2

- [ ] Implement the smallest code change that fixes the failing boundary.
- [ ] Avoid unrelated cleanup or popup redesign changes.
- [ ] If picker and paste share the same seam, keep the fix shared.
- [ ] If mobile paste is fundamentally unsupported in the browser, add explicit user-visible handling instead of a silent no-op.

## Task 4: Verify Green And Add Release Note

**Files:**
- Modify: the regression test file from Task 2
- Create: `wave/config/changelog.d/2026-04-10-issue-812-mobile-attachment-upload.json`

- [ ] Re-run the targeted regression and confirm it passes.
- [ ] Re-run a focused neighboring test set for any touched server/client seam.
- [ ] Add the changelog fragment for the user-visible attachment upload fix.

Likely commands:

```bash
sbt "JakartaIT/testOnly org.waveprotocol.box.server.jakarta.AttachmentServletJakartaIT"
sbt "testOnly org.waveprotocol.wave.client.editor.EditorContextAdapterHandlerTest"
```

## Task 5: Local Mobile Verification

**Files:**
- Update: `journal/local-verification/2026-04-10-issue-812-mobile-attachment-upload.md`

- [ ] Run the standard worktree boot / smoke flow for the fixed branch-local server.
- [ ] Perform a narrow mobile browser verification against the fixed server:
  - register/sign in to a local test account if needed
  - open a wave
  - verify picker-selected image upload inserts into the wave
  - verify pasted image behavior on mobile is either fixed or explicitly surfaced
- [ ] Record the exact commands, route checked, and observed result in the local verification journal and GitHub issue comment.

## Task 6: Review, Commit, And PR

- [ ] Run direct review on the final diff.
- [ ] Run `claude-review` on the implementation diff and address any actionable findings.
- [ ] Commit the fix in logical units.
- [ ] Update GitHub issue `#812` with:
  - worktree path and branch
  - plan path
  - root cause summary
  - commit SHA(s)
  - test commands and results
  - local browser verification result
  - PR URL
- [ ] Open the PR against `main`.
