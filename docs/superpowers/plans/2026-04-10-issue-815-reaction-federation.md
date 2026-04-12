# Issue 815 Reaction Federation Verification Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove that reactions are real wavelet state, not local-only UI state, and lock that contract in with the smallest necessary docs/tests.

**Architecture:** On `origin/main`, reactions live in deterministic per-blip data documents (`react+<blipId>`), mutate through ordinary `WaveletBlipOperation` document ops, and are serialized as normal wavelet documents. The likely work for issue `#815` is not a storage redesign, but explicit regression coverage and durable documentation of the federation-relevant semantics and non-goals.

**Tech Stack:** Java wavelet/document model, protobuf wavelet operation serialization, wavelet snapshot serialization, Jakarta history servlet, JUnit/SBT, GitHub issue workflow.

---

## Findings Snapshot

- Reactions are stored in per-blip data documents, not browser-local state.
- Document ids are deterministic: `react+<blipId>`.
- Reaction edits are ordinary `WaveletBlipOperation` mutate-document deltas, so they already flow through the same persistence and protocol serialization path as other wavelet document ops.
- Wavelet snapshots serialize every document returned by `wavelet.getDocumentIds()`, so reaction documents are part of snapshot state today.
- Version-history snapshot JSON intentionally hides reaction docs from the text-oriented history view, but does not remove them from actual delta/snapshot state.
- The design intentionally does **not** surface reactions as first-class robot/Data API fields in v1. That is separate from federation and should not be “fixed” here.

## Acceptance Criteria

- [ ] The repo contains a short issue-815 plan/findings document that explains why the current reaction model is federatable Wave state.
- [ ] There is regression coverage proving reaction docs survive normal protocol/snapshot serialization paths.
- [ ] Existing reaction id/history behavior remains covered.
- [ ] No speculative federation-specific machinery is introduced.
- [ ] Issue `#815` is updated with findings, review outcome, verification, and PR link if a PR is opened.

## Likely Touch Points

- Modify: `docs/superpowers/specs/2026-04-10-wave-reactions-design.md`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/common/WaveletOperationSerializerTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/common/SnapshotSerializerTest.java`
- Maybe modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/WaveDigesterTest.java`
- Maybe remove after branch sync: `docs/superpowers/specs/2026-04-10-reactions-design.md`

## Task 1: Sync The Lane To Current Main

**Files:**
- No intentional code changes yet; resolve branch-state drift first.

- [ ] Bring `reaction-federation-20260410` onto current `origin/main` without switching the main checkout.
- [ ] If the old pre-merge spec file `docs/superpowers/specs/2026-04-10-reactions-design.md` remains only because this lane diverged before `#799`, remove it as part of this issue branch cleanup.
- [ ] Confirm the branch now contains the merged reaction implementation from `#799` and follow-up icon fix from `#808`.

Run:
```bash
git fetch origin
git log --oneline --left-right HEAD...origin/main
```

Expected:
- the branch state is understood and reconciled before test/docs edits begin

## Task 2: Lock The Federation-Relevant Contract In Tests

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/common/WaveletOperationSerializerTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/common/SnapshotSerializerTest.java`

- [ ] Add a serializer regression proving a `WaveletBlipOperation` against `react+b+abc` round-trips through `CoreWaveletOperationSerializer` unchanged.
- [ ] Add a snapshot regression proving a wavelet containing a reaction data document round-trips through `SnapshotSerializer` with that document still present.
- [ ] Reuse existing reaction id helpers (`IdUtil.reactionDataDocumentId(...)`) rather than hard-coding alternate semantics.
- [ ] Only add additional unread/search tests if review or verification uncovers a real gap beyond the already-covered non-blip and history behavior.

Run:
```bash
sbt "testOnly org.waveprotocol.box.server.common.WaveletOperationSerializerTest org.waveprotocol.box.server.common.SnapshotSerializerTest"
```

Expected:
- the new tests fail before implementation and pass after the minimal regression additions

## Task 3: Make The Intent Explicit In Docs

**Files:**
- Modify: `docs/superpowers/specs/2026-04-10-wave-reactions-design.md`

- [ ] Add a concise “federation/protocol compatibility” section that states:
  - reactions are ordinary wavelet documents and deltas today
  - future federation work can reuse generic document propagation rather than inventing special reaction protocol
  - robot/Data API omissions are deliberate scope limits, not evidence that reactions are local-only
- [ ] Keep the documentation factual and current; do not speculate about protocol extensions that do not exist.

Run:
```bash
rg -n "federat|protocol compatibility|Data API" docs/superpowers/specs/2026-04-10-wave-reactions-design.md
```

Expected:
- the design doc makes the issue-815 conclusion explicit

## Task 4: Review, Verify, And Record Evidence

**Files:**
- Modify if needed: `journal/local-verification/2026-04-10-issue-815-reaction-federation.md`

- [ ] Run Claude review on this plan before implementation and incorporate any actionable feedback.
- [ ] After implementation, run targeted verification for the touched reaction/protocol/history tests.
- [ ] Add an issue comment with:
  - worktree path and branch
  - plan path
  - findings summary
  - commit SHA(s)
  - verification commands/results
  - Claude review outcome
  - PR number/link if changes are committed

Verification commands:
```bash
sbt "testOnly org.waveprotocol.box.server.common.WaveletOperationSerializerTest org.waveprotocol.box.server.common.SnapshotSerializerTest org.waveprotocol.wave.model.conversation.ReactionDocumentTest org.waveprotocol.box.server.rpc.VersionHistoryServletTest"
```

## Out Of Scope

- [ ] Do not redesign reactions around annotations or a new federation-specific schema.
- [ ] Do not add robot/Data API reaction fields or events in this issue.
- [ ] Do not change reaction UI behavior except for narrowly related regression fallout.
