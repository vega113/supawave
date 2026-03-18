# Unopened Shared Wave Unread Count Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the left messages panel show correct unread counts for shared waves before those waves are opened.

**Architecture:** Treat this as a digest/search-state bug first, not a renderer bug. Verify where unopened-wave unread counts are sourced, add a regression around that path, then fix the narrowest server/client state-selection bug that causes shared-wave unread counts to lag until open.

**Tech Stack:** Java, GWT client, Wave supplement/read-state model, server search/digest pipeline, Beads task tracking.

---

### Task 1: Confirm the unread-count source of truth

**Files:**
- Reference: `.beads/issues.jsonl`
- Reference: `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`
- Reference: `wave/src/main/java/org/waveprotocol/box/server/waveserver/AbstractSearchProviderImpl.java`
- Reference: `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java`
- Reference: `wave/src/main/java/org/waveprotocol/wave/model/supplement/SupplementedWaveImpl.java`

- [ ] **Step 1: Re-read the shared-wave unread reproduction and map it to the digest path**

Confirm whether the unopened left-panel unread badge is sourced from the server
search response, a local digest proxy, or both.

- [ ] **Step 2: Identify the exact persisted state that should drive unread counts**

Trace how user-data wavelets and supplement read versions are selected for the
viewing participant in the unopened-wave path.

- [ ] **Step 3: Record the chosen fix seam in the Beads task comments**

Before implementation, add a Beads comment summarizing the chosen root cause and
the files that will be touched.

### Task 2: Add the regression test first

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/WaveDigesterTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java`

- [ ] **Step 1: Write the smallest failing regression that captures the unopened shared-wave unread bug**

Prefer a server-side search/digest test that proves unread counts are wrong for
participant B before the wave is opened.

- [ ] **Step 2: Run the focused test to verify it fails for the expected reason**

Use the narrowest `./gradlew ... --tests ...` command available for the chosen
test file.

### Task 3: Fix the shared-wave unread pipeline

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/AbstractSearchProviderImpl.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveDigester.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`

- [ ] **Step 1: Implement the minimal fix at the confirmed seam**

Do not change renderer or focus behavior unless the regression proves the bug is
actually client-side.

- [ ] **Step 2: Re-run the focused regression and any directly related tests**

Keep the verification limited to the unread/digest/search path plus any helper
tests affected by the change.

- [ ] **Step 3: Update the Beads task comments with the commit SHA and summary**

Include the exact regression test command and result.

### Task 4: Review and handoff

**Files:**
- Reference: `.beads/issues.jsonl`

- [ ] **Step 1: Send the plan through Claude review once architect findings are attached**

Use `claude-review` with the task context and this plan.

- [ ] **Step 2: Address plan-review comments before implementation begins**

If Claude finds a gap, update this plan first rather than improvising during
implementation.

- [ ] **Step 3: After implementation, run reviewer flow and capture review comments in Beads**

The final task comments should include the review outcome, commit SHAs, and how
comments were resolved.
