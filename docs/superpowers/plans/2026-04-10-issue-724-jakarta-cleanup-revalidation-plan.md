# Issue 724 Jakarta Cleanup Revalidation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-check issue `#724` against the current repo state, keep the stale deletion/rename scope out of this branch, and fix only the one confirmed stale inline build-reference comment.

**Architecture:** Treat the current dual-source Jakarta layout as authoritative. The implementation branch does not delete any duplicate main-tree classes or rename `wave/src/jakarta-overrides/`; it only corrects the main-tree `ServerMain` comment so it points readers to the active source-selection file (`build.sbt`) and leaves the runtime guidance intact.

**Tech Stack:** Java source comments, Markdown issue evidence, `rg`, `git diff`

---

### Task 1: Fix The Only Confirmed Stale Inline Reference

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`
- Verify: `docs/architecture/jakarta-dual-source.md`
- Verify: `build.sbt`

- [ ] **Step 1: Confirm the stale reference is still present**

Run: `rg -n "mainExactExcludes in build\\.gradle|jakarta-overrides variant of ServerMain" wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`

Expected: two matches: one line that still says `build.gradle` and one line that still explains the active `jakarta-overrides` variant.

- [ ] **Step 2: Replace only the stale build-file reference**

Update the adjacent inline comment block in `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java` from:

```java
    // Note: this javax ServerMain is excluded from compilation (see mainExactExcludes in build.gradle).
    // The active registration lives in the jakarta-overrides variant of ServerMain.
```

to:

```java
    // Note: this javax-era ServerMain is excluded from compilation (see mainExactExcludes in build.sbt).
    // The active registration lives in the jakarta-overrides variant of ServerMain.
```

- [ ] **Step 3: Verify the targeted cleanup stayed narrow**

Run: `rg -n "mainExactExcludes in build\\.sbt|mainExactExcludes in build\\.gradle" wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`

Expected: the `build.sbt` reference remains and the `build.gradle` reference no longer appears in that file.

Run: `git diff -- wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`

Expected: only the two-line inline comment block is touched, with no runtime logic changes.

- [ ] **Step 4: Record the stale-vs-current findings in the issue and PR summary**

Capture:
- the 48 duplicate paths still exist but are intentionally preserved by the current dual-source architecture
- `build.sbt` still actively references `wave/src/jakarta-overrides/java/`
- deleting the duplicate main-tree files and renaming `wave/src/jakarta-overrides/` are out of scope because those parts of the issue text are stale in the current repo

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-04-10-issue-724-jakarta-cleanup-revalidation-plan.md \
  wave/src/main/java/org/waveprotocol/box/server/ServerMain.java
git commit -m "docs: revalidate issue 724 jakarta cleanup scope"
```
