# Worktree Lane Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize how an already-created incubator-wave worktree boots locally and where a GitHub-Issues lane records local-verification evidence.

**Architecture:** Add one runbook that keeps worktree creation separate from boot, then add a small shell helper that stages the app, optionally reuses the shared file-store state, rewrites the staged config for the requested port, and reuses `scripts/wave-smoke.sh` for readiness checks. Update the agent-facing docs so GitHub-Issues lanes store local verification records under `journal/local-verification/` instead of relying on Beads comments.

**Tech Stack:** Bash, SBT staged distribution, `scripts/worktree-file-store.sh`, `scripts/wave-smoke.sh`, Markdown docs.

---

## File Structure

- Create: `docs/runbooks/worktree-lane-lifecycle.md`
- Create: `scripts/worktree-boot.sh`
- Modify: `scripts/wave-smoke.sh`
- Modify: `docs/DEV_SETUP.md`
- Modify: `docs/SMOKE_TESTS.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`

## Chunk 1: Runbook And Evidence Contract

### Task 1: Publish the canonical lane lifecycle

**Files:**
- Create: `docs/runbooks/worktree-lane-lifecycle.md`
- Modify: `docs/DEV_SETUP.md`
- Modify: `docs/SMOKE_TESTS.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`

- [ ] Define the lifecycle in one place: create worktree, enter worktree, optionally link file-store state, boot with `scripts/worktree-boot.sh`, verify locally, record evidence, and stop the server.
- [ ] Keep worktree creation in the runbook only; the script must assume the current checkout is already the target worktree.
- [ ] Document the default evidence contract for GitHub-Issues lanes: record commands, ports, and results in `journal/local-verification/<date>-issue-<number>-<slug>.md`, then summarize the same evidence in the issue/PR comments.
- [ ] Add an explicit port-conflict branch to the runbook: if `9898` is occupied, rerun with `--port 9899` or another free port instead of skipping verification.
- [ ] Update the adjacent docs to point back to the runbook instead of leaving partial boot instructions scattered across setup and smoke docs.

## Chunk 2: Boot Helper And Smoke Reuse

### Task 2: Add the existing-worktree boot helper

**Files:**
- Create: `scripts/worktree-boot.sh`
- Modify: `scripts/wave-smoke.sh`

- [ ] Add `scripts/worktree-boot.sh` with a small CLI: `--port`, `--shared-file-store`, `--file-store-source`, and `--help` are sufficient for this issue.
- [ ] Have the script verify it is running from a git checkout, stage the distribution with `sbt Universal/stage`, and optionally enable shared local state with `--shared-file-store --file-store-source <path>` before startup when the lane needs reused file-store contents.
- [ ] Rewrite only the staged `target/universal/stage/config/application.conf` port binding so alternate ports do not require tracked config edits.
- [ ] Reuse `scripts/wave-smoke.sh` for `start` and `check`, and print the exact base URL, log path, and stop command at the end of boot.
- [ ] Update `scripts/wave-smoke.sh` to honor `PORT` from the environment so the helper and the runbook can use non-`9898` ports routinely.

## Chunk 3: Verification

### Task 3: Prove the lifecycle works without new dependencies

**Files:**
- Verify only

- [ ] Run `bash -n scripts/worktree-boot.sh scripts/wave-smoke.sh`.
- [ ] Run `bash scripts/worktree-boot.sh --help` and confirm the documented options are exposed.
- [ ] Boot the worktree on a non-default port with `bash scripts/worktree-boot.sh --port 9900` (or another free port) and confirm `curl -sSf http://127.0.0.1:9900/healthz` succeeds.
- [ ] Stop the staged server with the exact command emitted by the helper and confirm the port is released.
- [ ] Capture the verification commands and results in `journal/local-verification/<date>-issue-585-worktree-lifecycle.md` for the GitHub-Issues workflow summary.

## Out Of Scope

- Browser-level verification standards beyond the base boot and health lifecycle
- New observability tooling, diagnostics bundles, or long-lived background services
- Reworking the separate PR-monitor workflow
