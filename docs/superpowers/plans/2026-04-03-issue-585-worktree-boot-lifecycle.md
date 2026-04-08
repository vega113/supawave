# Issue 585 Worktree Boot Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize how an already-created incubator-wave worktree boots into a usable local lane and where GitHub-Issues workflow evidence should be recorded.

**Architecture:** Add one runbook that owns the human workflow from existing worktree to shutdown, plus one shell helper that performs the repeatable boot steps inside the worktree. Keep worktree creation, tmux launch, and browser/manual checks in the runbook. Keep `scripts/worktree-boot.sh` focused on optional shared file-store wiring, stage/build prep, staged-config port overrides, and evidence-file setup, while reusing `scripts/wave-smoke.sh` for startup/readiness on the selected port.

**Tech Stack:** Bash, existing Wave bootstrap/smoke scripts, Markdown docs, gitignored `journal/` evidence records.

---

## Scope And Decisions

- Worktree creation stays documented in the runbook and remains a manual/operator step.
- `scripts/worktree-boot.sh` assumes it runs inside an existing incubator-wave worktree.
- Port conflicts are handled by selecting an explicit port override instead of silently killing unrelated listeners.
- The default GitHub-Issues evidence location will be `journal/local-verification/<date>-issue-<number>-<slug>.md` inside the worktree so the record is durable for the lane but stays out of git history.
- The evidence contract must include, at minimum, the lane branch, worktree path, date, exact commands run, observed results, and resulting PR or issue links.
- No new browser automation, tracing, or observability dependency will be introduced.

## Files

- Create: `docs/runbooks/worktree-lane-lifecycle.md`
- Create: `scripts/worktree-boot.sh`
- Modify: `scripts/wave-smoke.sh`
- Modify: `docs/DEV_SETUP.md`
- Modify: `docs/SMOKE_TESTS.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`

## Risks And Non-Goals

- The repo already has multiple boot paths (`wave-bootstrap.sh`, `wave-smoke.sh`, direct `sbt`), so the runbook must clarify ownership rather than duplicate every existing command reference.
- The new helper should avoid assuming a free port, but it also must not kill non-Wave processes automatically.
- The helper should only adjust staged/runtime config for routine lane boot, not rewrite tracked repo config just to switch ports.
- The helper must reject execution from the primary checkout by requiring a linked/non-primary incubator-wave git worktree; the plan does not require a hard-coded absolute-path check.
- This change does not standardize browser verification, collect diagnostic bundles, or replace task-specific runtime verification.

## Chunk 1: Runbook And Evidence Contract

- [ ] Write `docs/runbooks/worktree-lane-lifecycle.md` covering prerequisites, worktree creation, lane launch from the worktree directory, shared file-store setup when needed, boot command flow, port-conflict handling, shutdown, and the GitHub-Issues local-verification record contract.
- [ ] Make the runbook the canonical explanation of where to store local verification notes for Beads-free issue work: `journal/local-verification/<date>-issue-<number>-<slug>.md`.
- [ ] Include a minimal evidence template in the runbook with branch, worktree path, date, exact commands, observed results, and follow-up PR or issue links.
- [ ] Cross-link the runbook from adjacent docs instead of restating the full lifecycle in multiple places.

## Chunk 2: Boot Helper Script

- [ ] Add `scripts/worktree-boot.sh` as a Bash helper with `--help`, `--port`, and a flag for shared file-store setup from `/Users/vega/devroot/incubator-wave`.
- [ ] Make the script fail fast when it is not run from a non-primary incubator-wave git worktree or when the chosen port is already occupied by a non-Wave process.
- [ ] Reuse existing repo helpers where practical: optionally wire shared file-store state, build the staged app, patch only staged/runtime config for the selected port, initialize the evidence-file path, and print the exact start/check/stop commands for the selected port.
- [ ] Update `scripts/wave-smoke.sh` so `start`, `check`, `status`, and `stop` honor `PORT` from the environment; without that, alternate-port boot is not runnable.

## Chunk 3: Adjacent Docs

- [ ] Update `docs/DEV_SETUP.md` so first-run setup points readers at the new runbook and boot helper for existing-worktree lanes.
- [ ] Update `docs/SMOKE_TESTS.md` so smoke guidance references the same lifecycle and documents explicit port override usage.
- [ ] Update `AGENTS.md` where needed so the GitHub-Issues workflow points to the canonical runbook and evidence record location instead of Beads-only expectations.
- [ ] Update `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md` so its verification/closeout section acknowledges the journal-based evidence record when Beads is not part of the workflow.

## Chunk 4: Verification

- [ ] Run script-focused verification: `bash -n scripts/worktree-boot.sh`.
- [ ] Run script-focused verification: `PORT=9899 bash -n scripts/wave-smoke.sh`.
- [ ] Run a negative-path verification by binding a dummy non-Wave listener to a candidate port, executing `scripts/worktree-boot.sh --port <that-port>`, and confirming the helper exits with a clear “choose another --port” message without killing the listener.
- [ ] Run doc-adjacent verification by executing the helper in the lane with a non-default port, booting the app on that port, confirming `curl http://localhost:<port>/healthz` succeeds, and cleanly stopping the server.
- [ ] Review the touched docs for consistency: existing-worktree assumption, explicit port-conflict guidance, and `journal/local-verification/<date>-issue-<number>-<slug>.md` called out everywhere the GitHub-Issues workflow needs it.
