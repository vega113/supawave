# Issue 1074 Wave Header Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the J2CL selected-wave header actions to GWT parity for D.4 Add participant, D.5 New wave with current participants, D.6 public/private toggle, and D.8 wave lock controls.

**Architecture:** Add a focused Lit header-action component mounted by `J2clSelectedWaveView`, then bridge its CustomEvents through `J2clRootShellController` into `J2clComposeSurfaceController` so writes reuse the existing root-session bootstrap and sidecar submit pipeline. Match the current GWT/server semantics: public/private is shared-domain participant add/remove, and lock state is the `m/lock` data document with `mode="root|all"` instead of a `lock/state` annotation.

**Tech Stack:** J2CL Java, Lit web components, sidecar `SidecarSubmitRequest` delta JSON, existing `wavy-confirm-dialog`, SBT `j2clSearchTest`, `j2cl/lit` Web Test Runner.

---

## Discovery Notes

- The issue body says F-2 already wired `wave-add-participant-requested`, `wave-publicity-toggle-requested`, and `wave-root-lock-toggle-requested`. Current code does not contain these runtime events; only `wave-new-with-participants-requested` exists via `wavy-profile-overlay` and `J2clRootShellController`.
- The selected-wave surface currently renders participant text and `wavy-wave-nav-row` from `J2clSelectedWaveView`; there is no dedicated selected-wave header action component.
- GWT public/private behavior lives in `ParticipantController.handleTogglePublicClicked`: it toggles the shared-domain participant, e.g. `@example.com`, not a supplement flag.
- GWT lock behavior lives in `ParticipantController.handleToggleLockClicked`, `Conversation.setLockState`, `LockDocument`, and `WaveLockValidator`: lock state is stored in document `m/lock` as `<lock mode="root"/>` or `<lock mode="all"/>`.
- `J2clRichContentDeltaFactory` already has the right stand-alone writer pattern for task metadata, reactions, and tombstone delete. Reuse that pattern for participant and lock writes.

## File Map

- Create: `j2cl/lit/src/elements/wavy-wave-header-actions.js`
- Create: `j2cl/lit/test/wavy-wave-header-actions.test.js`
- Modify: `j2cl/lit/src/index.js`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewChromeTest.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveDocument.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/root/J2clRootShellControllerTest.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactoryTest.java`
- Create: `wave/config/changelog.d/2026-04-30-j2cl-wave-header-actions.json`

## Scope Decisions

- Implement all four issue affordances in this lane; keep each task separately testable and commit after each green slice.
- Use a new `wavy-wave-header-actions` component instead of overloading `wavy-wave-nav-row`, because `wavy-wave-nav-row` is already the E.1-E.10 GWT toolbar parity strip.
- Use the existing `wavy-confirm-dialog` for public/private and lock transitions. Confirm both public->private and private->public because both change visibility semantics.
- For D.5, the PR implementation uses the current participant set by default and starts a fresh wave with that set. The GWT participant-pruning popup is a separate enhancement and is not required for this issue's acceptance.
- For D.8, implement the GWT lock cycle `unlocked -> root -> all -> unlocked`; the label can emphasize root lock for the first transition, but the stored state must match `WaveLockState`.

## Task 1: Lit Header-Actions Component

**Files:**
- Create: `j2cl/lit/src/elements/wavy-wave-header-actions.js`
- Create: `j2cl/lit/test/wavy-wave-header-actions.test.js`
- Modify: `j2cl/lit/src/index.js`

- [ ] **Step 1: Write failing Lit tests for render and event contracts**

Test cases in `wavy-wave-header-actions.test.js`:
- renders Add participant, New wave with participants, Public/private, and Lock buttons when `sourceWaveId` is set.
- disables write buttons when `sourceWaveId` is empty.
- Add participant opens an inline dialog, accepts comma-separated addresses, and emits `wave-add-participant-requested` with `{sourceWaveId, addresses}`.
- New wave emits `wave-new-with-participants-requested` with `{sourceWaveId, participants}` and filters the shared-domain participant out of the new-wave participant list.
- Public/private opens `wavy-confirm-requested` and emits `wave-publicity-toggle-requested` only after confirm with `{sourceWaveId, currentlyPublic, nextPublic}`.
- Lock opens `wavy-confirm-requested` and emits `wave-root-lock-toggle-requested` only after confirm with `{sourceWaveId, currentLockState, nextLockState}`.

Run:
```bash
cd j2cl/lit
npm test -- --files test/wavy-wave-header-actions.test.js
```

Expected before implementation: FAIL because the element is not registered.

- [ ] **Step 2: Implement `wavy-wave-header-actions`**

Element contract:
- properties: `sourceWaveId`, `participants`, `public`, `lockState`, `disabled`.
- participant values are plain address strings from Java.
- compute `regularParticipants` by excluding addresses beginning with `@`.
- lock cycle: `unlocked -> root -> all -> unlocked`.
- dispatch events with `bubbles: true` and `composed: true`.
- use `wavy-confirm-requested` for public/private and lock confirmation so the existing body-mounted confirm dialog owns UI consistency.

- [ ] **Step 3: Import the component**

Add this import near the other F-3/F-2 selected-wave component imports in `j2cl/lit/src/index.js`:
```js
import "./elements/wavy-wave-header-actions.js";
```

- [ ] **Step 4: Verify Lit slice**

Run:
```bash
cd j2cl/lit
npm test -- --files test/wavy-wave-header-actions.test.js
npm run build
```

Expected: PASS.

- [ ] **Step 5: Commit slice**

```bash
git add j2cl/lit/src/elements/wavy-wave-header-actions.js j2cl/lit/test/wavy-wave-header-actions.test.js j2cl/lit/src/index.js
git commit -m "feat: add j2cl wave header actions component"
```

## Task 2: Mount Header Actions In Selected Wave View

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewChromeTest.java`

- [ ] **Step 1: Write failing J2CL view tests**

Add tests in `J2clSelectedWaveViewChromeTest`:
- cold mount creates one `wavy-wave-header-actions` after `.sidecar-selected-participants` and before `wavy-wave-nav-row`.
- render with participants stamps the JS property or JSON attribute that the Lit component consumes.
- render with selected wave id stamps `source-wave-id`.
- render with no selection clears `source-wave-id`, `public`, and `lock-state`.
- server-first rebind creates the element if SSR did not include it.

Run:
```bash
sbt --batch j2clSearchTest
```

Expected before implementation: FAIL on missing element.

- [ ] **Step 2: Add `waveHeaderActions` handle**

In `J2clSelectedWaveView`, add:
- field `private final HTMLElement waveHeaderActions;`
- helper `ensureWaveHeaderActions(HTMLElement card)` that inserts the element after `.sidecar-selected-participants`.
- cold-mount creation next to `participantSummary`.
- server-first rebind using `ensureWaveHeaderActions(existingCard)`.

- [ ] **Step 3: Publish selected-wave state**

During `render(J2clSelectedWaveModel model)`:
- if no selection: clear `source-wave-id`, `participants`, `public`, and `lock-state`.
- if selection: set `source-wave-id`, set participants from `model.getParticipantIds()`, set `lock-state` from `model.getLockState()`, and set `public` when participant ids contain a shared-domain address starting with `@`.

- [ ] **Step 4: Verify view slice**

Run:
```bash
sbt --batch j2clSearchTest
```

Expected: PASS.

- [ ] **Step 5: Commit slice**

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveViewChromeTest.java
git commit -m "feat: mount j2cl wave header actions"
```

## Task 3: Project Lock State From The Selected-Wave Transport

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveDocument.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java`

- [ ] **Step 1: Write failing transport/projector tests**

Transport tests:
- decoding a selected-wave update with document id `m/lock` and component `<lock mode="root"/>` yields document lock state `"root"`.
- `<lock mode="all"/>` yields `"all"`.
- missing or unknown mode yields `"unlocked"`.

Projector tests:
- `J2clSelectedWaveModel` carries `"root"` or `"all"` when the selected update includes `m/lock`.
- loading/no-update state preserves previous lock state for the same wave.

- [ ] **Step 2: Extend document extraction**

In `SidecarTransportCodec.extractDocument`, while processing element starts:
- if `documentId.equals("m/lock")` and element type is `lock`, read attr `mode`.
- normalize mode to `unlocked`, `root`, or `all`.
- carry the value through `DocumentExtraction` into `SidecarSelectedWaveDocument`.

- [ ] **Step 3: Extend model/projector**

Add `lockState` to `J2clSelectedWaveModel` with getter `getLockState()`.
In `J2clSelectedWaveProjector`, derive lock state from the `m/lock` document and preserve previous same-wave lock state when the update is incomplete.

- [ ] **Step 4: Verify projection slice**

Run:
```bash
sbt --batch j2clSearchTest
```

Expected: PASS.

- [ ] **Step 5: Commit slice**

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarSelectedWaveDocument.java j2cl/src/main/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodec.java j2cl/src/test/java/org/waveprotocol/box/j2cl/transport/SidecarTransportCodecTest.java j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveModel.java j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjector.java j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveProjectorTest.java
git commit -m "feat: project j2cl wave lock state"
```

## Task 4: Add Delta Writers For Participants, Publicity, And Lock State

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactoryTest.java`

- [ ] **Step 1: Write failing delta factory tests**

Add tests:
- `addParticipantRequest` emits one or more AddParticipant operations (`{"1":"address"}`) on the current selected wavelet and normalizes/filters duplicates.
- `publicityToggleRequest(..., true)` emits AddParticipant for `@domain`.
- `publicityToggleRequest(..., false)` emits RemoveParticipant for `@domain` (`{"2":"@domain"}`).
- `lockStateRequest(..., "unlocked", "root")` emits a document operation on `m/lock` inserting `<lock mode="root"/>`.
- `lockStateRequest(..., "root", "all")` replaces the old lock element with mode `all`.
- `lockStateRequest(..., "all", "unlocked")` removes the lock element and leaves the document empty.

- [ ] **Step 2: Implement participant writers**

Add public methods:
- `SidecarSubmitRequest addParticipantRequest(String address, J2clSidecarWriteSession session, List<String> participantsToAdd)`
- `SidecarSubmitRequest publicityToggleRequest(String address, J2clSidecarWriteSession session, String sharedDomainParticipant, boolean makePublic)`

Use `buildDeltaJson(session.getBaseVersion(), session.getHistoryHash(), normalizedAddress, operations)` and `buildWaveletName(session.getSelectedWaveId())`.

- [ ] **Step 3: Implement lock writer**

Add:
- `SidecarSubmitRequest lockStateRequest(String address, J2clSidecarWriteSession session, String currentLockState, String nextLockState)`

Use document id `m/lock` and the existing document-operation JSON helpers. The server already authorizes lock writes in `WaveLockValidator`, so the client writer only needs to submit the `m/lock` op and surface server errors.

- [ ] **Step 4: Verify delta slice**

Run:
```bash
sbt --batch j2clSearchTest
```

Expected: PASS.

- [ ] **Step 5: Commit slice**

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactory.java j2cl/src/test/java/org/waveprotocol/box/j2cl/richtext/J2clRichContentDeltaFactoryTest.java
git commit -m "feat: add j2cl wave header delta writers"
```

## Task 5: Bridge Events Into Compose Controller

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/root/J2clRootShellControllerTest.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Root-shell helper tests:
- parse `wave-add-participant-requested` detail addresses from JS arrays and trim/filter invalid non-strings.
- parse `wave-publicity-toggle-requested` booleans and source wave id.
- parse `wave-root-lock-toggle-requested` current/next lock state strings and normalize to `unlocked|root|all`.

Compose-controller tests:
- add participant submit calls gateway once and records telemetry success.
- public toggle to public submits shared-domain AddParticipant.
- public toggle to private submits shared-domain RemoveParticipant.
- lock toggle submits `m/lock` request.
- all three drop stale writes when selected wave changes before bootstrap returns.
- server submit errors record failure telemetry and do not clear active draft state.

- [ ] **Step 2: Add DeltaFactory methods**

Extend `J2clComposeSurfaceController.DeltaFactory` with default methods for:
- `createAddParticipantRequest`
- `createPublicityToggleRequest`
- `createLockStateRequest`

Wire `richContentDeltaFactory` to delegate to the new `J2clRichContentDeltaFactory` methods.

- [ ] **Step 3: Add compose-controller write methods**

Add public methods:
- `onAddParticipantsRequested(String expectedWaveId, List<String> rawAddresses)`
- `onPublicityToggleRequested(String expectedWaveId, boolean makePublic)`
- `onLockStateToggleRequested(String expectedWaveId, String currentLockState, String nextLockState)`

Each method mirrors `onTaskToggled` / `onDeleteBlipRequested`: signed-out guard, selected-wave guard, expected wave guard, bootstrap, same-logical-session guard, build request, submit, telemetry.

- [ ] **Step 4: Wire root-shell events**

In `J2clRootShellController.start()`, add document-body listeners:
- `wave-add-participant-requested`
- `wave-publicity-toggle-requested`
- `wave-root-lock-toggle-requested`

Keep the existing `wave-new-with-participants-requested` path and add source-wave validation so stale selected-wave header clicks do not write into a newly selected wave.

- [ ] **Step 5: Verify controller slice**

Run:
```bash
sbt --batch j2clSearchTest
```

Expected: PASS.

- [ ] **Step 6: Commit slice**

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java j2cl/src/test/java/org/waveprotocol/box/j2cl/root/J2clRootShellControllerTest.java j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java
git commit -m "feat: wire j2cl wave header actions"
```

## Task 6: User Feedback, Changelog, And End-To-End Verification

**Files:**
- Create: `wave/config/changelog.d/2026-04-30-j2cl-wave-header-actions.json`

- [ ] **Step 1: Add user-visible feedback**

Use existing visible status surfaces first:
- public/private and lock confirmations are handled through `wavy-confirm-dialog`.
- submit failures surface through the compose status text already used by other J2CL write paths.
- if the controller does not currently expose status for these new write paths, add short status strings on failure/success rather than introducing a generic toast subsystem in this PR.

- [ ] **Step 2: Add changelog fragment**

Create `wave/config/changelog.d/2026-04-30-j2cl-wave-header-actions.json`:
```json
{
  "date": "2026-04-30",
  "type": "changed",
  "title": "Added J2CL wave header actions",
  "description": "The J2CL selected-wave surface now supports adding participants, creating a wave from the current participants, toggling public/private visibility, and cycling wave lock state."
}
```

- [ ] **Step 3: Full local verification**

Run:
```bash
cd j2cl/lit
npm test -- --files test/wavy-wave-header-actions.test.js test/wavy-wave-nav-row.test.js
npm run build
cd ../..
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
sbt --batch compile j2clSearchTest
git diff --check
```

Expected: all PASS.

- [ ] **Step 4: Browser sanity**

Boot the app using the repo-standard local workflow for this worktree, open `/?view=j2cl-root`, select a wave, and verify visually:
- header action row is visible and Wavy-styled.
- Add participant dialog opens and can be cancelled without side effects.
- New wave with current participants focuses the create surface.
- Public/private and lock actions show styled confirmations.

Record exact command/result in issue #1074 before PR.

- [ ] **Step 5: Final commit**

```bash
git add wave/config/changelog.d/2026-04-30-j2cl-wave-header-actions.json wave/config/changelog.json
git commit -m "chore: record j2cl wave header actions changelog"
```

## PR And Monitoring

- Push branch `codex/issue-1074-wave-header-actions-20260430`.
- Open a ready PR linked to #1074 and #904.
- PR body must include: worktree path, plan path, commit SHAs, Lit verification, SBT verification, changelog verification, and browser sanity evidence.
- Monitor until merged:
```bash
gh pr view <PR> --json number,state,mergedAt,mergeStateStatus,reviewDecision,headRefOid,url
gh pr checks <PR> --watch=false || true
gh api graphql -f owner=vega113 -f name=supawave -F number=<PR> -f query='query($owner:String!, $name:String!, $number:Int!) { repository(owner:$owner, name:$name) { pullRequest(number:$number) { reviewThreads(first:50) { nodes { id isResolved path line comments(first:20) { nodes { author { login } body url createdAt } } } } } } }'
```

## Self-Review

- Spec coverage: D.4 is covered by Task 1, Task 4, Task 5; D.5 is covered by Task 1 and existing `onCreateRequestedWithParticipants`; D.6 is covered by Task 1, Task 4, Task 5 using GWT shared-domain participant semantics; D.8 is covered by Task 1, Task 3, Task 4, Task 5 using existing GWT/server lock document semantics.
- Parity correction: the plan intentionally replaces the issue body's stale `lock/state` annotation wording with `m/lock` document writes because that is what current GWT and `WaveLockValidator` enforce.
- False-premise check: the plan includes creating the missing event source and listeners because current runtime lacks three of the four claimed F-2 CustomEvents.
- Placeholder scan: the plan names concrete files, commands, and expected outcomes for each task.
- Risk: if the selected-wave stream does not include `m/lock`, Task 3 must extend the selected-wave server payload to include the lock document or an explicit lock-state field before Task 2 can render authoritative lock state. That is not a human blocker; choose the narrower payload extension and cover it with codec/projector tests.

## External Review Evidence

- Claude Opus review attempt on 2026-04-30 was blocked by quota: "You've hit your limit - resets May 2 at 9pm (Asia/Jerusalem)". Fallback models were disabled by lane policy, so implementation proceeds with the self-review above and must rerun the Claude review loop when quota is available.
