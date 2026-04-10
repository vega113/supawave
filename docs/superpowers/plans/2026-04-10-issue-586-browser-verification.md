# Issue 586 Browser Verification Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize the documented browser-verification path for incubator-wave issue lanes on top of the existing `scripts/wave-smoke-ui.sh` baseline.

**Architecture:** Keep the current shell-based smoke baseline and worktree boot lifecycle. Add one browser-verification runbook that explains how the standalone `scripts/wave-smoke-ui.sh` baseline maps to worktree-lane verification, add one change-type matrix that defines when curl/smoke is enough versus when a real browser pass is required, and update adjacent docs so lanes discover the new guidance without introducing a new browser framework.

**Tech Stack:** Bash smoke scripts, existing worktree-lane runbooks, Markdown docs, local staged server verification.

---

## Scope And Decisions

- `scripts/wave-smoke-ui.sh` remains the standalone browser-smoke baseline.
- Worktree lanes should not invent ad-hoc browser verification commands; the new runbook will map them to the existing `scripts/worktree-boot.sh` plus `scripts/wave-smoke.sh` flow.
- The change-type matrix must distinguish at least server-only, servlet/auth, GWT client, packaging, and deployment-only changes.
- The runbook must define the default UI-affecting verification path and the cases where a browser pass is mandatory.
- A new wrapper script is only justified if the docs reveal a repeated command gap that cannot be explained cleanly with the current scripts. The implementation should default to no new wrapper.
- No Playwright, Puppeteer, or other repo-wide browser framework will be introduced in this task.

## Files

- Create: `docs/runbooks/browser-verification.md`
- Create: `docs/runbooks/change-type-verification-matrix.md`
- Modify: `docs/runbooks/README.md`
- Modify: `docs/runbooks/worktree-lane-lifecycle.md`
- Modify: `docs/SMOKE_TESTS.md`

## Risks And Non-Goals

- The repo already has multiple verification entry points, so the new docs must clarify ownership instead of duplicating every existing command reference.
- The runbook should standardize expectations, not over-specify feature-specific manual steps that belong in issue plans.
- If the matrix is too broad, lanes will keep falling back to ad-hoc interpretations; if it is too rigid, it will encourage cargo-cult browser checks. The matrix should define defaults and escalation criteria, not every UI scenario.
- This task does not add new browser automation, new smoke tasks in `build.sbt`, or diagnostic bundle collection.

## Task 1: Define The Standard Baseline

**Files:**
- Modify: `docs/runbooks/worktree-lane-lifecycle.md`
- Modify: `docs/SMOKE_TESTS.md`
- Create: `docs/runbooks/browser-verification.md`

- [ ] **Step 1: Capture the baseline decision in the runbook**

Document that:
- standalone validation can continue using `bash scripts/wave-smoke-ui.sh`
- issue worktrees should first prepare the port-specific staged app with `bash scripts/worktree-boot.sh --port <port>`
- issue worktrees should then use `PORT=<port> JAVA_OPTS='...' bash scripts/wave-smoke.sh start`, `PORT=<port> bash scripts/wave-smoke.sh check`, and `PORT=<port> bash scripts/wave-smoke.sh stop`
- browser verification, when required by the matrix, runs against that started worktree server instead of inventing a separate framework

- [ ] **Step 2: State the default browser-verification path for UI-affecting changes**

In `docs/runbooks/browser-verification.md`, define this default path:
1. prepare the worktree runtime with `bash scripts/worktree-boot.sh --port 9900`
2. start the worktree server with the printed `PORT=9900 JAVA_OPTS='...' bash scripts/wave-smoke.sh start` command
3. confirm the baseline checks with `PORT=9900 bash scripts/wave-smoke.sh check`
4. open `http://localhost:9900/` and perform the narrow browser checks required by the change-type matrix
5. stop the server with `PORT=9900 bash scripts/wave-smoke.sh stop`

- [ ] **Step 3: Cross-link the new guidance from the existing smoke docs**

Update `docs/SMOKE_TESTS.md` and `docs/runbooks/worktree-lane-lifecycle.md` so they point to `docs/runbooks/browser-verification.md` for the browser-verification decision after the base smoke/lifecycle commands succeed.

## Task 2: Add The Change-Type Verification Matrix

**Files:**
- Create: `docs/runbooks/change-type-verification-matrix.md`
- Create: `docs/runbooks/browser-verification.md`

- [ ] **Step 1: Add the matrix rows for each required change type**

Create a table covering:
- server-only changes
- servlet/auth changes
- GWT client/UI changes
- packaging/build/distribution changes
- deployment-only changes

For each row, define:
- the default scripted baseline
- whether browser verification is required, optional, or usually unnecessary
- the narrow browser focus when it is required
- the evidence that should be recorded in the issue comment or local-verification journal

- [ ] **Step 2: Explain curl/smoke-only sufficiency versus browser-required cases**

In `docs/runbooks/browser-verification.md`, summarize the matrix in prose:
- curl/smoke is enough when the change cannot alter rendered browser behavior
- browser verification is required when the change affects auth/session transitions, GWT client rendering, editor behavior, user-visible routing, or packaging that changes served client assets
- deployment-only changes rely on deployment/runbook verification unless they also modify browser-visible assets or auth behavior

## Task 3: Make The Runbooks Discoverable

**Files:**
- Modify: `docs/runbooks/README.md`

- [ ] **Step 1: Add the new runbook and matrix to the runbooks map**

Update `docs/runbooks/README.md` so lanes can find:
- `browser-verification.md` as the browser-verification entry point
- `change-type-verification-matrix.md` as the quick classification reference

## Task 4: Verification And Evidence

**Files:**
- Modify: `journal/local-verification/2026-04-10-issue-586-wave-smoke-ui-20260410.md`

- [ ] **Step 1: Run focused doc/script verification**

Run:
```bash
bash scripts/wave-smoke-ui.sh
```

Expected:
- the script exits `0`
- output includes `ROOT=200|302`, `WEBCLIENT=200`, and `UI smoke OK`

- [ ] **Step 2: Run the worktree-lane equivalent verification**

Run:
```bash
bash scripts/worktree-boot.sh --port 9900
PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/issue-586-wave-smoke-ui-20260410/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/issue-586-wave-smoke-ui-20260410/wave/config/jaas.config' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
PORT=9900 bash scripts/wave-smoke.sh stop
```

Expected:
- the helper prints a runtime config and evidence file for this branch
- `wave-smoke.sh check` reports `ROOT_STATUS=200|302`, `HEALTH_STATUS=200`, and `WEBCLIENT_STATUS=200`
- stop succeeds cleanly

- [ ] **Step 3: Review the docs for consistency**

Confirm the touched docs all agree on:
- `scripts/wave-smoke-ui.sh` as the standalone baseline
- `worktree-boot.sh` plus `wave-smoke.sh` as the worktree-lane equivalent
- the matrix-driven rule for when browser verification is required
- the evidence destination in issue comments and `journal/local-verification/...`
