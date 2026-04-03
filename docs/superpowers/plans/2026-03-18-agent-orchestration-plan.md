# Agent Orchestration Plan

> **For agentic workers:** REQUIRED: Treat this document as the execution model for GitHub Issue work in this repository. Use checkbox (`- [ ]`) steps when instantiating task-specific plans.

**Goal:** Execute GitHub Issue slices in parallel with clear ownership, reviewed plans, reviewed implementations, and predictable PR flow into `main`.

**Architecture:** Each ready GitHub Issue slice gets its own team-lead agent and its own git worktree. The team lead owns routing and synthesis, not implementation. Inside each task, work flows through architect -> planner -> worker -> reviewer, with Claude review on the plan and on the implementation before PR creation.

**Tech Stack:** Codex agents, GitHub Issue tracking, git worktrees under `/Users/vega/devroot/worktrees`, Claude review via `claude-review`, GitHub PRs against `main`.

---

Current batching and team-lead assignments for the open task queue are tracked
in [2026-03-18-outstanding-task-batching-plan.md](/Users/vega/devroot/incubator-wave/docs/superpowers/plans/2026-03-18-outstanding-task-batching-plan.md).

## Operating Rules

- `main` is the integration branch.
- Each GitHub Issue slice is implemented in its own worktree and branch.
- The team-lead agent owns the task lifecycle and delegates actual work.
- The architect and planner use `gpt-5.4` with `xhigh` reasoning.
- Workers use `gpt-5.4-mini` with `high` reasoning.
- Reviewers perform direct review and Claude review before the task is considered ready.
- GitHub Issues are the live source of task state, comments, and traceability.

## Worktree And Branch Model

- Worktree root: `/Users/vega/devroot/worktrees`
- Branch naming: use a short task-oriented branch name, e.g. `fix-unread-shared-wave`
- One task branch per GitHub Issue slice.
- One worktree per active task branch.
- If the worktree needs realistic file-based persistence state, run:

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

- Default to `symlink` mode. Use `--mode copy` only when isolated persistence state is required.

## Parallelism Model

- Parallelize across independent GitHub Issue slices, not just within a single task.
- Give each ready task one team-lead agent.
- Inside a task, do not parallelize until the plan is stable.
- After the plan is reviewed and accepted, the worker may spawn additional `gpt-5.4-mini`
  workers only for disjoint file ownership or clearly independent slices.
- If two task branches need to modify the same files, do not run them in parallel.

## Task Lifecycle

### Phase 1: Intake

- [ ] Confirm the GitHub Issue exists and is the right unit of work.
- [ ] Confirm the task is actually ready: blockers closed, dependencies satisfied.
- [ ] Create the task worktree and branch.
- [ ] Add a GitHub Issue comment noting the worktree path, branch name, and team-lead ownership.

### Phase 2: Architecture

- [ ] Spawn an architect agent for root-cause analysis, design options, and implementation direction.
- [ ] Architect inspects the codebase, identifies the narrowest correct fix, and proposes tests.
- [ ] Architect writes or updates the task plan input for the planner.

Required architect output:
- Root cause with confidence level
- File and method references
- Narrowest viable fix
- Suggested regression tests
- Risks and non-goals

### Phase 3: Planning

- [ ] Spawn a planner agent after architect findings are available.
- [ ] If no adequate task plan exists, create one under `docs/superpowers/plans/`.
- [ ] Update the GitHub Issue description or comments so the implementation plan is discoverable.
- [ ] Review the plan with Claude via `claude-review`.
- [ ] Address plan-review comments before implementation starts.

Required planner output:
- Task-specific plan file path
- Acceptance criteria
- File ownership and likely touched files
- Exact test command targets
- Explicit out-of-scope items

### Phase 4: Implementation

- [ ] Spawn one worker agent to own implementation.
- [ ] Worker writes the failing test first.
- [ ] Worker verifies the test fails for the expected reason.
- [ ] Worker implements the minimal root-cause fix.
- [ ] Worker reruns the targeted tests.
- [ ] If the task splits cleanly, worker spawns additional `gpt-5.4-mini` workers with disjoint ownership.
- [ ] Worker commits logically grouped code changes with clear commit messages.

Implementation constraints:
- No coding before a failing test
- No unrelated cleanup
- No cross-task edits
- No hidden changes outside the plan without updating GitHub Issue comments

### Phase 5: Review

- [ ] Spawn a reviewer agent once the worker finishes and the branch is green on targeted tests.
- [ ] Reviewer inspects the diff directly.
- [ ] Reviewer runs Claude review on the implementation diff.
- [ ] Reviewer produces a unified review result.
- [ ] If issues are found, return them to the worker, fix them, and re-review.

Required reviewer output:
- Findings first, ordered by severity
- Explicit approval or explicit blockers
- Residual risk or test gaps

### Phase 6: Task Closeout

- [ ] Add a GitHub Issue comment listing all commit SHAs and what each commit does.
- [ ] Add a GitHub Issue comment summarizing architect findings, plan-review feedback, code-review feedback, and how each important comment was addressed.
- [ ] Run a local server sanity verification appropriate to the changed area before opening the PR, and record the exact command plus result in the linked GitHub Issue.
- [ ] Run final verification on the agreed targeted test set.
- [ ] Open a PR from the task branch into `main`.
- [ ] Add the PR link or number to the issue comments.
- [ ] Close the GitHub Issue only after the implementation and review record are complete.

## GitHub Issue Comment Template

Use this shape for task comments:

```text
Worktree: /Users/vega/devroot/worktrees/incubator-wave/<branch>
Branch: <branch>
Lead: <agent/team-lead owner>

Architect summary:
- <root cause>
- <fix seam>

Plan:
- <plan file path>
- Claude plan review: <approved / blocked by overload / findings summary>

Implementation commits:
- <sha> <summary>
- <sha> <summary>

Verification:
- <command>
- <result>

Review:
- Claude code review: <summary>
- Reviewer findings: <summary>
- Resolutions: <summary>

Local server sanity:
- <exact command>
- <result>

PR:
- <url or PR number>
```

## Efficiency Rules

- Prefer a small number of high-quality parallel tasks over many partially-active ones.
- Do not spawn every role simultaneously for the same task.
- Use the team-lead only for coordination, decision-making, and synthesis.
- Keep worker write scopes disjoint.
- Reuse stable plan context instead of rediscovering the repo state on every task.

## Failure Handling

- Record provider overload in issue comments and continue with direct review instead of stalling the task indefinitely when Claude review is blocked.
- Stop and revisit the architect findings when a worker cannot produce a failing test.
- Reopen the architect phase before more coding when review finds a broader architectural issue.

## Definition Of Ready

A task is ready to start when:
- It is in GitHub Issues
- Dependencies are satisfied
- A worktree can be assigned
- The task has either an adequate plan already or a planner can write one immediately

## Definition Of Done

A task is done when:
- Code is implemented on its task branch
- Targeted tests pass
- Direct review is complete
- Claude implementation review is complete or explicitly documented as blocked
- GitHub Issue comments contain plan, commit, review, and PR traceability
- A PR to `main` is open
