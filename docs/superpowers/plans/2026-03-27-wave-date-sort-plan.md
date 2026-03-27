# Wave Date Sort Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `orderby:datedesc` and `orderby:dateasc` order waves by the most recent blip edit time rather than an older wave/root creation timestamp.

**Architecture:** The investigation already shows that `QueryHelper` sorts by conversational wavelet last-modified time, so the fix must be driven by a reproduction that proves whether the wrong timestamp comes from comparator input or from stale timestamp propagation. The implementation should add a focused regression test in the search provider harness, confirm which timestamp path is wrong, and then change the narrowest server-side code path that makes search ordering follow the latest blip edit time.

**Tech Stack:** Java, Apache Wave server search, junit3, sbt

---

### Task 1: Reproduce the Ordering Bug in the Search Harness

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java`

- [ ] **Step 1: Write the failing regression test**

Add a test that creates at least two waves in ascending time order, then performs a later blip edit on the older wave and asserts that `orderby:datedesc` returns the edited older wave first and `orderby:dateasc` returns it last.

- [ ] **Step 2: Add the minimal test helpers needed for later edits**

Add helper methods for submitting a delta to an existing wavelet and for creating or editing a blip with a later timestamp source from the local wavelet container.

- [ ] **Step 3: Run the targeted test to verify it fails for the expected reason**

Run: `sbt "testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest -- -Dtest.name=*Date*"`

Expected: the new regression test fails because search ordering still follows the older timestamp path.

### Task 2: Fix the Server-Side Timestamp Path

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/QueryHelper.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`
- Modify if needed: timestamp propagation classes under `wave/src/main/java/org/waveprotocol/box/server/waveserver/` or `wave/src/main/java/org/waveprotocol/wave/model/`

- [ ] **Step 1: Confirm the exact bad timestamp source from the failing test**

Decide whether the wrong value comes from:
- `WaveViewData` missing the wavelet that contains the newest edit
- `ObservableWaveletData.getLastModifiedTime()` not reflecting the later blip edit
- `QueryHelper` using the wrong field even when the correct data is present

- [ ] **Step 2: Implement the narrowest fix**

Change only the code path proven by the failing test. Prefer preserving the existing `orderby` API and result shape. Do not add unrelated search behavior changes.

- [ ] **Step 3: Rerun the targeted test**

Run: `sbt "testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest"`

Expected: the new regression test passes and existing search-order tests stay green.

### Task 3: Verify, Review, and Finalize the Branch

**Files:**
- Modify: `docs/superpowers/plans/2026-03-27-wave-date-sort-plan.md` only if plan-review feedback requires updates

- [ ] **Step 1: Run compile verification**

Run: `sbt wave/compile`

Expected: success.

- [ ] **Step 2: Run external code review on the implementation diff**

Run the `claude-review` workflow against the task branch diff from `origin/main...HEAD` and capture findings.

- [ ] **Step 3: Address findings and reverify if needed**

Fix any important review findings, rerun the affected targeted test and `sbt wave/compile`, and record the review outcome in Beads.

- [ ] **Step 4: Commit and push**

Create a focused commit on the task branch and push `fix/wave-date-sort-lmt` to origin without opening a PR.
