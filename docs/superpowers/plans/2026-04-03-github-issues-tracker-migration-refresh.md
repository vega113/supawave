# GitHub Issues Tracker Migration Refresh Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the existing issue-tracker migration branch onto the latest `main`, resolve any resulting documentation conflicts, and update the linked issue/PR evidence if follow-up edits are needed.

**Architecture:** Treat this as a doc-only branch maintenance slice. First verify the current PR and branch state, then rebase onto `origin/main`, keep the GitHub Issues workflow as the live tracker, and reconcile only the doc hunks that overlap with newer `main` changes. Finish with targeted doc verification and GitHub issue/PR updates.

**Tech Stack:** Git worktrees, git rebase, GitHub CLI, Markdown docs, ripgrep

---

## Chunk 1: Branch Refresh

### Task 1: Capture current state and fetch the latest base

**Files:**
- Modify: none
- Verify: existing worktree `/Users/vega/devroot/worktrees/codex-github-issues-tracker-migration`

- [ ] **Step 1: Confirm the worktree is clean**

Run: `git -C /Users/vega/devroot/worktrees/codex-github-issues-tracker-migration status --short --branch`
Expected: clean branch on `codex/github-issues-tracker-migration`

- [ ] **Step 2: Fetch the latest remote refs**

Run: `git -C /Users/vega/devroot/worktrees/codex-github-issues-tracker-migration fetch origin`
Expected: local refs updated successfully

- [ ] **Step 3: Attempt the refresh onto `origin/main`**

Run: `git -C /Users/vega/devroot/worktrees/codex-github-issues-tracker-migration rebase origin/main`
Expected: either a clean rebase or a bounded set of doc conflicts to resolve

### Task 2: Resolve only migration-related doc conflicts if they appear

**Files:**
- Modify: only conflicted docs already in the PR
- Verify: git conflict markers removed and branch rebased cleanly

- [ ] **Step 1: Inspect the conflicted files**

Run: `git -C /Users/vega/devroot/worktrees/codex-github-issues-tracker-migration status --short`
Expected: exact list of conflicted docs

- [ ] **Step 2: Resolve conflicts in favor of the intended tracker migration**

Keep:
- GitHub Issues as the live tracker for new work
- `.beads/` and `docs/epics/README.md` as historical archive material
- newer `main` wording where it does not contradict the migration

- [ ] **Step 3: Continue the rebase**

Run: `git -C /Users/vega/devroot/worktrees/codex-github-issues-tracker-migration rebase --continue`
Expected: rebase completes cleanly

## Chunk 2: Verification And Review

### Task 3: Verify the final doc set

**Files:**
- Modify: any rebased/conflict-resolved docs only if needed
- Test: repository docs checks

- [ ] **Step 1: Check for whitespace or patch formatting issues**

Run: `git -C /Users/vega/devroot/worktrees/codex-github-issues-tracker-migration diff --check`
Expected: no output

- [ ] **Step 2: Verify stale Beads-live wording is gone from the targeted docs**

Run: `rg -n "Use Beads as the live task tracker|repo-local Beads backlog|Beads task exists|record the exact command plus result in Beads|Add the PR link or number to the Beads|\\.beads/issues.jsonl for live task execution|live task tracker for implementation work" -S AGENTS.md README.md ORCHESTRATOR.md docs/current-state.md docs/github-issues.md docs/epics/README.md .beads/README.md docs/jetty-migration.md docs/blocks-adoption-plan.md docs/migrate-conversation-renderer-to-apache-wave.md docs/modernization-plan.md docs/j2cl-gwt3-decision-memo.md docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`
Expected: no matches

- [ ] **Step 3: Verify the expected GitHub Issues guidance remains present**

Run: `rg -n "docs/github-issues.md|GitHub Issues as the live task tracker|historical archive" -S AGENTS.md README.md ORCHESTRATOR.md docs/current-state.md docs/epics/README.md .beads/README.md docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`
Expected: expected matches in updated docs

### Task 4: Review and publish follow-up metadata

**Files:**
- Modify: PR branch tip if rebase or conflict resolution changed docs
- Update externally: GitHub Issue `#581`, PR `#595`

- [ ] **Step 1: Run a Codex `gpt-5.4` medium review against the final diff**

Scope:
- flag regressions, stale wording, or acceptance-criteria gaps
- prefer findings over summaries

- [ ] **Step 2: If the branch changed, commit and push the refreshed branch**

Run:
- `git -C /Users/vega/devroot/worktrees/codex-github-issues-tracker-migration status`
- `git -C /Users/vega/devroot/worktrees/codex-github-issues-tracker-migration add <changed-files>`
- `git -C /Users/vega/devroot/worktrees/codex-github-issues-tracker-migration commit -m "docs: refresh GitHub issues tracker migration branch"`
- `git -C /Users/vega/devroot/worktrees/codex-github-issues-tracker-migration push --force-with-lease origin codex/github-issues-tracker-migration`
Expected: PR `#595` updates successfully

- [ ] **Step 3: Update issue `#581` with the final outcome**

Include:
- plan path
- whether rebase/conflict resolution was required
- final commit SHA(s)
- verification commands/results
- PR status/link
