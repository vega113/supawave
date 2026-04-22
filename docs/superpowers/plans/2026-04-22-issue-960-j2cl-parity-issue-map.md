# Issue #960 J2CL Parity Issue Map Plan

> **For agentic workers:** Treat this as a documentation-and-tracker slice. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a repo-backed Markdown doc that decomposes the remaining J2CL parity work into concrete GitHub issues, then create those issues in GitHub with clear dependency order and acceptance boundaries.

**Architecture:** Start from the current `origin/main` baseline plus the merged `docs/j2cl-parity-architecture.md` recommendation. This task does not implement parity work. It refreshes the execution map underneath `#904`: preserve already-closed work, retain existing open follow-ups (`#931`, `#933`, `#936`), and define the missing issue set needed to reach practical GWT parity while preserving rollback-ready coexistence, server-rendered first paint, viewport-scoped fragment loading, and optional Stitch-driven design work where visual modernization is in scope.

**Tech Stack:** Markdown docs under `docs/`, current J2CL parity memo on `origin/main`, current open/closed GitHub issues under the `#904` lineage, local repo code seams (`StageOne`, `StageTwo`, `StageThree`, current J2CL search/root-shell/compose paths), GitHub issue creation via `gh`, and Claude Opus 4.7 review of the final doc diff.

---

## 1. Why This Exists

Issue `#904` is still the umbrella tracker, but its pending checklist reflects an older sequence and no longer matches the current repo state precisely enough for execution.

This task exists to answer:

- which previously-created issues are still the right execution slices
- which gaps are already covered by open issues
- which missing issues must be created now
- in what order those issues should execute to reach practical parity with the legacy GWT client

The output should be a doc and issue set that a team-lead lane can use directly.

## 2. Scope And Non-Goals

### In Scope

- inspect the current issue tree and code/docs baseline
- write one issue-map doc under `docs/`
- define exact new issue titles, scopes, and dependency order
- create the corresponding GitHub issues after review

### Explicit Non-Goals

- no implementation of the parity issues in this task
- no change to the merged framework recommendation
- no default-root cutover in this task
- no GWT retirement in this task

## 3. Expected Inputs

- `docs/j2cl-parity-architecture.md`
- current `#904` tracker
- current open J2CL issues (`#931`, `#933`, `#936`)
- current closed issue lineage (`#920`, `#921`, `#922`, `#923`, `#924`, `#925`, `#928`)
- code seams:
  - `wave/src/main/java/org/waveprotocol/wave/client/StageOne.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/StageThree.java`
  - current `j2cl/src/main/java/org/waveprotocol/box/j2cl/**`

## 4. Questions The Doc Must Answer

- [ ] Which open issues should remain as-is?
- [ ] Which closed issues are truly complete versus historically closed but no longer the right forward plan?
- [ ] What new issues are required to reach practical GWT parity under the current Lit/J2CL architecture?
- [ ] What dependencies should govern those issues?
- [ ] Where should optional Stitch-driven design work appear, and where is it irrelevant?
- [ ] Which issues belong before any default-root cutover or GWT retirement can be reconsidered?

## 5. Concrete Task Breakdown

### Task 1: Freeze The Tracker Boundary

- [ ] Keep `#904` as the parent umbrella.
- [ ] Treat `#931`, `#933`, and `#936` as existing children unless the code/docs prove they should be replaced instead of retained.
- [ ] Do not create duplicate issues for already-open follow-ups.

### Task 2: Gather Current Execution Evidence

- [ ] Inspect the current open/closed J2CL issue lineage.
- [ ] Inspect the merged parity-architecture doc.
- [ ] Inspect the current StageOne/StageTwo/StageThree and J2CL seams enough to group the remaining work into executable slices.

### Task 3: Write The Issue Map Doc

- [ ] Create `docs/j2cl-parity-issue-map.md`.
- [ ] Structure it roughly as:
  - current baseline
  - existing open issues to retain
  - new issues to create
  - dependency order
  - what must be complete before cutover/retirement
  - where Stitch-driven design work belongs
- [ ] For each proposed issue, include:
  - title
  - why it exists
  - tight scope
  - acceptance focus
  - dependencies

### Task 4: Review The Doc

- [ ] Self-review for overlap, missing dependencies, and stale assumptions.
- [ ] Run `git diff --check`.
- [ ] Run Claude Opus 4.7 review on the final diff.
- [ ] Address valid comments until the review is clean.

### Task 5: Create The GitHub Issues

- [ ] Create the reviewed issue set in GitHub.
- [ ] Link each issue back to `#904`.
- [ ] Record the issue numbers in the doc or issue trail.

### Task 6: Traceability

- [ ] Comment on `#960` with worktree path, plan path, review outcome, and final PR/issue links.
- [ ] Commit the doc, plan, index updates, and any changelog fragment together.
- [ ] Open a PR for the issue-map doc.

## 6. Verification / Review

Run these from the issue worktree:

```bash
git diff --check
```

Expected result:

- no whitespace or patch-format issues

Then run Claude Opus 4.7 review on the final diff and resolve valid comments.

## 7. Definition Of Done

- `docs/j2cl-parity-issue-map.md` exists and is committed
- the doc clearly separates existing retained issues from newly-created ones
- the new GitHub issues are created from the reviewed doc
- the issue set has explicit dependency order
- `git diff --check` passes
- Claude Opus 4.7 review passes on the final diff
- `#960` and the PR both contain plan path, review outcome, and final issue links
