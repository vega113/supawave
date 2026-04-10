# Issue 824 Mobile Picker Types Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the remaining Android/mobile attachment picker gaps so existing photos/videos and general files can be selected and uploaded without regressing the working camera path.

**Architecture:** Build on top of the already-merged mobile chooser-recovery baseline from `#821`, then narrow the fix to the attachment popup's chooser/input state management. Treat this as a client-side picker-state bug unless local repro proves the request contract or server receipt is broken.

**Tech Stack:** SBT, GWT client code, Jakarta servlet runtime, JUnit, local browser/mobile-emulation verification.

---

## Scope

- In scope:
  - `AttachmentPopupWidget` chooser/input behavior on mobile
  - preserving the working camera chooser path
  - ensuring photos/videos selection yields an uploadable file
  - allowing general file selection where the browser/platform exposes it
  - narrow regression coverage for the picker-state seam
- Out of scope:
  - unrelated attachment UI redesign
  - clipboard/paste behavior unless investigation proves the seam is shared
  - server-side attachment changes unless browser repro proves the client is already sending a correct file

## Expected Files

- Investigate / likely modify:
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java`
  - `wave/src/main/java/org/waveprotocol/wave/model/util/AttachmentUploadMobileSupport.java`
- Tests / likely modify:
  - `wave/src/test/java/org/waveprotocol/wave/model/util/AttachmentUploadMobileSupportTest.java`
- Evidence / release notes:
  - `journal/local-verification/2026-04-10-issue-824-mobile-picker-types.md`
  - `wave/config/changelog.d/2026-04-10-issue-824-mobile-picker-types.json`

## Acceptance Criteria

- Selecting `Camera` from the mobile chooser still works.
- Selecting `Photos & videos` yields a file that reaches the preview/upload path.
- Selecting a general file works on browsers/platforms that expose a document/file picker for the input.
- A focused automated regression covers the identified mobile chooser/input seam.
- The linked GitHub issue records the exact local verification commands and any remaining platform/tooling limits.

## Task 1: Baseline And Reproduce

**Files:**
- Read: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java`
- Read: `wave/src/main/java/org/waveprotocol/wave/model/util/AttachmentUploadMobileSupport.java`
- Evidence: `journal/local-verification/2026-04-10-issue-824-mobile-picker-types.md`

- [ ] Update the branch to include merged mobile recovery work from `#821` so the camera-path baseline is preserved.
- [ ] Reproduce the chooser flow against a local worktree server and capture whether the failure occurs before preview creation, before XHR upload, or after the request is sent.
- [ ] Record a short architect summary in issue `#824` naming the failing client seam and the minimal fix.

## Task 2: Write The Narrow Failing Regression

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/wave/model/util/AttachmentUploadMobileSupportTest.java`
- Modify or create a small helper in `wave/src/main/java/org/waveprotocol/wave/model/util/AttachmentUploadMobileSupport.java` only if a pure-Java seam is needed

- [ ] Add the smallest regression that locks down the stale chooser/input-state condition proven in Task 1.
- [ ] Run only the focused test target first and verify it fails for the expected reason.

Run target:

```bash
sbt "testOnly org.waveprotocol.wave.model.util.AttachmentUploadMobileSupportTest"
```

## Task 3: Implement The Minimal Popup/Input Fix

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/model/util/AttachmentUploadMobileSupport.java` only if required by the regression seam

- [ ] Change only the chooser/input behavior needed to clear stale native-input state and/or correctly arm recovery for mobile picker returns.
- [ ] Preserve the existing camera path behavior from `#821`.
- [ ] Keep general file selection available; do not narrow the input back to images-only unless local repro proves that is required and acceptable.

## Task 4: Verify Green And Add Release Note

**Files:**
- Modify: the regression test file from Task 2
- Create: `wave/config/changelog.d/2026-04-10-issue-824-mobile-picker-types.json`

- [ ] Re-run the focused regression and confirm it passes.
- [ ] Run the narrow neighboring verification for the touched client seam.
- [ ] Add the changelog fragment for the user-visible mobile picker fix.

Likely commands:

```bash
sbt "testOnly org.waveprotocol.wave.model.util.AttachmentUploadMobileSupportTest"
sbt "compileGwt"
```

## Task 5: Local Mobile Verification, Review, And PR

**Files:**
- Update: `journal/local-verification/2026-04-10-issue-824-mobile-picker-types.md`

- [ ] Boot the worktree server on a dedicated port and run the narrow smoke check.
- [ ] Verify the attachment popup against a mobile-emulated browser, explicitly checking `Camera`, `Photos & videos`, and a general file path if the browser exposes one.
- [ ] Record exact commands, observed results, and any remaining non-emulatable mobile-picker limitations.
- [ ] Run direct review and `claude-review` on the final diff, address findings, then update issue `#824` with plan path, commits, verification, and PR URL.
