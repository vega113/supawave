# Outstanding Task Batching Plan

> **For agentic workers:** REQUIRED: Use this document together with `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md` when selecting the next batch of Beads tasks to execute. This file assigns task leads, worktree names, and parallel execution waves.

Status: Dated execution snapshot for the 2026-03-18 queue. Use `.beads/issues.jsonl`
as the live source of current task readiness before launching a lane.

**Goal:** Execute the current open Beads tasks in a parallel but controlled way, without oversubscribing the available agent budget or creating file-ownership conflicts.

**Architecture:** Parallelize across independent Beads tasks, but cap live execution to two team-lead streams at a time because each stream expands into architect, planner, worker, reviewer, and Claude review. Use later waves to queue the next ready tasks so every team lead already has a designated lane even if it is not launched immediately.

**Tech Stack:** Beads task tracking, worktrees under `/Users/vega/devroot/worktrees`, PRs into `main`, architect/planner on `gpt-5.4` xhigh, workers on `gpt-5.4-mini` high, reviewer + Claude review before PR.

---

## Current Ready Tasks

As of this plan, the ready non-epic tasks are:

- `incubator-wave-modernization.1`
  - Consolidate config hygiene and fragment flag paths
- `incubator-wave-modernization.3`
  - Close remaining library-upgrade debt
- `incubator-wave-modernization.4`
  - Bring SBT docs and behavior to parity with the additive server build
- `incubator-wave-modernization.8`
  - Harden browser websocket reconnect handling after disconnects
- `incubator-wave-wiab-core.1`
  - Run combined smoke verification for dynamic renderer, fragments, and quasi deletion

Tasks not ready yet because they are blocked:

- `incubator-wave-modernization.2`
  - blocked by `incubator-wave-modernization.1`
- `incubator-wave-modernization.5`
  - blocked by `incubator-wave-modernization.4`
- `incubator-wave-wiab-core.2`
  - blocked by `incubator-wave-wiab-core.1`
- `incubator-wave-wiab-core.3`
  - blocked by `incubator-wave-wiab-core.1`
- `incubator-wave-wiab-core.4`
  - blocked by `incubator-wave-wiab-core.3`
- `incubator-wave-wiab-product.1`
  - blocked by `incubator-wave-wiab-core.1`
- `incubator-wave-wiab-product.2`
  - blocked by `incubator-wave-wiab-product.1`
- `incubator-wave-wiab-product.3`
  - blocked by `incubator-wave-wiab-product.1`
- `incubator-wave-wiab-product.4`
  - blocked by `incubator-wave-wiab-core.4`

## Concurrency Budget

- Maximum simultaneous team-lead streams: `2`
- Reason:
  - each active task stream expands into architect, planner, worker, reviewer
  - each stream also uses Claude review on plan and implementation
  - a budget of two streams keeps the agent graph small enough to stay
    responsive while preserving parallelism

Practical rule:
- While one stream is in implementation/review, the other stream may be in
  architecture/planning or implementation, but do not launch a third stream
  until one of the first two reaches PR-ready state.

## Assignment Matrix

### Wave 1: Runtime Stabilization

These go first because they reduce noise for later verification work.

- `TL-config-hygiene`
  - Task: `incubator-wave-modernization.1`
  - Worktree: `/Users/vega/devroot/worktrees/incubator-wave/config-hygiene`
  - Branch: `config-hygiene`
  - Why first: unifies config source-of-truth and removes ambiguity in runtime
    behavior

- `TL-websocket-reconnect`
  - Task: `incubator-wave-modernization.8`
  - Worktree: `/Users/vega/devroot/worktrees/incubator-wave/websocket-reconnect`
  - Branch: `websocket-reconnect`
  - Why first: improves browser/session stability that affects later smoke
    verification

### Wave 2: Build And Planning Infrastructure

These are independent enough to run once one Wave 1 slot frees up.

- `TL-sbt-parity`
  - Task: `incubator-wave-modernization.4`
  - Worktree: `/Users/vega/devroot/worktrees/incubator-wave/sbt-parity`
  - Branch: `sbt-parity`
  - Why here: mostly build/doc/task-wiring work, low overlap with runtime fixes

### Wave 3: Broader Validation And Cleanup

These should start after the runtime baseline is calmer.

- `TL-library-upgrades`
  - Task: `incubator-wave-modernization.3`
  - Worktree: `/Users/vega/devroot/worktrees/incubator-wave/library-upgrades`
  - Branch: `library-upgrades`
  - Why here: can touch shared dependencies and should start after config and
    reconnect direction are known

- `TL-core-smoke`
  - Task: `incubator-wave-wiab-core.1`
  - Worktree: `/Users/vega/devroot/worktrees/incubator-wave/core-smoke`
  - Branch: `core-smoke`
  - Why here: its output should reflect the stabilized runtime instead of
    reporting already-known transport/session noise

### Wave 4: Dependency-Unlocked Follow-ons

These do not start until their blockers are closed.

- `TL-mongo4`
  - Task: `incubator-wave-modernization.2`
  - Starts after `incubator-wave-modernization.1`

- `TL-packaging-dx`
  - Task: `incubator-wave-modernization.5`
  - Starts after `incubator-wave-modernization.4`

- `TL-renderer-entrypoints`
  - Task: `incubator-wave-wiab-core.2`
  - Starts after `incubator-wave-wiab-core.1`

- `TL-fragment-transport`
  - Task: `incubator-wave-wiab-core.3`
  - Starts after `incubator-wave-wiab-core.1`

- `TL-product-audit`
  - Task: `incubator-wave-wiab-product.1`
  - Starts after `incubator-wave-wiab-core.1`

### Wave 5: Late Dependencies

- `TL-blocks-followup`
  - Task: `incubator-wave-wiab-core.4`
  - Starts after `incubator-wave-wiab-core.3`

- `TL-draft-mode`
  - Task: `incubator-wave-wiab-product.2`
  - Starts after `incubator-wave-wiab-product.1`

- `TL-contacts`
  - Task: `incubator-wave-wiab-product.3`
  - Starts after `incubator-wave-wiab-product.1`

- `TL-data-layer-decision`
  - Task: `incubator-wave-wiab-product.4`
  - Starts after `incubator-wave-wiab-core.4`

## Team-Lead Responsibilities

For every lead above, the execution workflow is identical:

- [ ] Create the task worktree and branch
- [ ] If realistic file persistence is needed, run:

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

- [ ] Add an opening Beads comment with task ownership, worktree path, and branch
- [ ] Spawn architect
- [ ] Spawn planner
- [ ] Run Claude review on the plan
- [ ] Spawn worker
- [ ] If the worker can split safely, spawn additional `gpt-5.4-mini` workers
      with disjoint ownership
- [ ] Spawn reviewer
- [ ] Run Claude review on the implementation
- [ ] Address findings
- [ ] Open PR into `main`
- [ ] Record commit SHAs, review findings, resolutions, and PR link in Beads

## Recommended Launch Order

Launch exactly two at a time:

1. `TL-config-hygiene`
2. `TL-websocket-reconnect`

Then, when one slot frees:

3. `TL-sbt-parity`

Then:

4. `TL-library-upgrades`

5. `TL-core-smoke`

This order front-loads runtime stability, then planning/build work, then
broader validation.

## Non-Parallel Combinations To Avoid

- Do not run `incubator-wave-wiab-core.1` before `incubator-wave-modernization.8`
  has at least completed architecture/planning; otherwise the smoke output may
  be dominated by already-known websocket/session instability.
- Do not run `incubator-wave-modernization.2` before
  `incubator-wave-modernization.1`; that dependency is real and should be
  preserved.
- Do not run `incubator-wave-wiab-product.1` before
  `incubator-wave-wiab-core.1`; product evaluation should not restart the same
  baseline verification work.

## Success Criteria For This Batching Plan

- Every ready task has a designated team lead, branch, and worktree path
- No live batch exceeds two simultaneous team-lead streams
- Later waves start only when blockers are closed
- Every completed task results in a PR to `main`
