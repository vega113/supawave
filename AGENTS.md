# AGENTS.md — Repo Entry Point for Incubator Wave

This is the top-level repo guidance entrypoint for `incubator-wave`. Read this
first, then open:

- `docs/github-issues.md` for the live GitHub Issues workflow and historical
  Beads archive policy.
- `docs/agents/tool-usage.md` for Codex tool routing, model tiers, and MCP guidance.
- `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md` for the detailed
  multi-agent execution flow.

This repository uses a delegation-first operating model. The main Codex thread
acts as team lead: it coordinates, routes work, and verifies outcomes.
Planning, implementation, and review should move to dedicated agents working in
isolated git worktrees.

## Operating Model
- Keep the main thread focused on intake, routing, integration, and final
  verification.
- Route substantive investigation, planning, implementation, and review to the
  appropriate agent type instead of doing the work directly in the lead thread.
- Use GitHub Issues as the live task tracker for new implementation work.
- Treat `.beads/` as a historical archive only. Do not create or update Beads
  tasks or comments for new work.
- Follow [docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md](docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md)
  as the canonical detailed execution model for multi-agent task delivery.
- When that orchestration plan applies, follow it directly and do not invent
  an alternative workflow in the middle of task execution.
- Favor tool use over guesswork. Keep calls minimal, scoped, and purposeful.

## Session Memory
- At the start of every session, read `MEMORY.md` (the index) from the Claude
  project memory directory (`.claude/projects/` under the repo or user config
  root).
- After reading the index, read only the memory files that are relevant to the
  current task.
- Memory contains workflow rules, coding patterns, model selection, drill
  procedure, and project-specific lessons.
- This memory persists across sessions, so check it before starting work.

## Jakarta Dual-Source Rule
- The codebase keeps runtime-active Jakarta replacements under
  `wave/src/jakarta-overrides/java/` while the legacy source remains under
  `wave/src/main/java/`.
- When a matching override exists, edit the Jakarta override copy. The override
  version is what runs, so changes made only in `wave/src/main/java/` will not
  take effect.

## Agent Roles

### Lead
- Acts as team lead and dispatcher.
- Converts the user request into the right agent flow, then waits for agent
  output instead of doing the work itself.
- Uses the lead thread only for coordination, synthesis, and final checks.

### Planner
- Receives requirements and turns them into GitHub Issues and implementation
  slices.
- Breaks the work into independently executable pieces with clear acceptance
  criteria.
- If the task is complex enough to need a real implementation plan, the
  planner spawns an architect agent first.
- If a task does not already have an adequate plan in the linked GitHub Issue
  or the repo docs, the planner must enter plan mode, write the plan, run a
  Claude review (`claude-review`) on that plan, address the comments, and only
  then hand the task off for implementation.

### Architect
- Investigates the codebase, docs, and constraints for complex tasks.
- Produces the implementation plan, risk list, and sequencing strategy.
- Must run a Claude review (`claude-review`) on the plan before concluding,
  then address the review comments and update the plan as needed.
- See `docs/agents/tool-usage.md` for Codex model routing.

### Worker
- Implements the assigned GitHub Issue slice in a dedicated git worktree.
- Keeps the change set narrow and reports the files changed plus verification
  performed.
- If the task can be split safely, the worker should spawn additional
  subagents for independent implementation slices.
- See `docs/agents/tool-usage.md` for Codex model routing.

### Reviewer
- Reviews worker output in a separate git worktree.
- Reviews the implementation directly and also runs a Claude review
  (`claude-review`) on the same change set.
- Produces one unified review that combines both perspectives and lists the
  final actionable findings.

## Git Worktrees And PRs
- Every agent that edits code or docs must work in its own git worktree.
- Prefer multiple worktrees with multiple agents over single-threaded work in
  the main tree whenever the task can be parallelized safely.
- When a new worktree needs access to the existing file-based persistence
  state, use the Codex skill `incubator-wave-worktree-file-store`.
- That skill should run `scripts/worktree-file-store.sh --source $HOME/devroot/incubator-wave`
  from the target worktree before testing.
- Prefer the script's default `symlink` mode so worktrees reuse the same
  `_accounts`, `_attachments`, and `_deltas` state. Use `--mode copy` only
  when isolated persistence state is explicitly needed.
- Do not mix agent edits in the main working tree.
- Use `/Users/vega/devroot/worktrees` as the shared root for local worktrees.
- **CRITICAL — tmux lanes must always be launched FROM the worktree directory, never from
  `/Users/vega/devroot/incubator-wave` or any subdirectory (e.g. `war/static`).** Any
  `git checkout` or `git switch` run inside the main repo changes the shared HEAD and flips
  the branch for every open Claude Code session. The canonical launch sequence is:
  ```bash
  git worktree add /Users/vega/devroot/worktrees/<branch-name> -b <branch-name>
  # then launch claude from that directory:
  tmux send-keys -t "<session>:<window>.<pane>" \
    "cd /Users/vega/devroot/worktrees/<branch-name> && claude --model <model> --dangerously-skip-permissions < /tmp/lane-prompt.txt" Enter
  ```
- **NEVER** create worktrees under `.claude/worktrees/` inside the main repo tree. Always
  use `/Users/vega/devroot/worktrees/<branch-name>` as the target path for `git worktree add`.
- Before opening a PR for app-affecting changes, run a local server sanity
  verification appropriate to the area changed and record the exact command
  plus result in the linked GitHub Issue.
- Keep that check narrow and relevant: boot the app and hit a local health or
  auth endpoint for server/runtime changes, or exercise the affected UI against
  the local server for client changes.
- Before merge, clear review conversations by actually addressing them.
- Do not resolve review threads just to bypass monitoring or branch protection.
- When implementation is complete and review is resolved, create a pull request
  from the reviewed worktree.
- Keep GitHub Issues, commits, and PRs aligned so the task status is always
  traceable.

## Task Workflow
- Any agent working on a GitHub Issue slice must first ensure there is a
  current plan for that issue.
- If no adequate plan exists, the agent must switch to plan mode, create the
  plan, run a Claude review (`claude-review`) on the plan, address the review
  comments, then switch back to work mode and implement.
- For the full orchestration sequence, branch/worktree rules, issue comment
  template, and PR flow, follow
  [docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md](docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md).
- If any short-form rule in `AGENTS.md` is ambiguous, the orchestration plan is
  the authoritative source for task execution behavior.
- After implementation, review the code, run the reviewer flow, address review
  comments, and only then prepare the pull request.

## GitHub Issue Updates
- Update the linked GitHub Issue with comments throughout execution, not only
  at the end.
- Include every commit SHA created for the task and a short summary of what
  each commit does.
- Capture review feedback from code review or Claude review, plus the follow-up
  comment that explains how each important finding was addressed.
- Before opening a pull request, the issue comments should reflect the final
  implementation summary, the review outcome, and the commit list that landed
  the work.
- Use `docs/github-issues.md` for the default label/filter conventions and the
  evidence-recording workflow.

## Changelog Guidelines
- Every PR that changes user-facing behavior MUST update
  `wave/config/changelog.json` before merging.
- Add a new entry at the top of the array with `version`, `date`
  (`YYYY-MM-DD`), `title`, `summary`, and `sections` (`feature` / `fix`).
- Keep entries concise: a 1-2 sentence summary and short bullet-style change
  lists.
- Treat `scripts/validate-changelog.py` as mandatory before merge or deploy,
  and run it against both changelog file paths before landing user-facing
  changes.

## Code Guidelines
- Follow [CODE_GUIDELINES.md](CODE_GUIDELINES.md) for repo-wide code style and
  contribution rules.
- Repo-specific addenda: avoid one-line code blocks inside bracketed lists,
  prefer extracting behavior into named functions instead of adding
  explanatory comments in code, and avoid mutable variables or mid-function
  returns when practical.
