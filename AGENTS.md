# AGENTS.md — Repo Entry Point for Incubator Wave

This is the top-level operator entrypoint for `incubator-wave`.

Read in this order:
- `docs/github-issues.md` for the live issue workflow and Beads archive policy.
- `docs/agents/tool-usage.md` for Codex-specific model/tool/MCP routing.
- `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md` for the detailed multi-agent execution flow.

## Operating Model
- Keep the main thread focused on intake, routing, integration, and final verification.
- Route substantive planning, implementation, and review work to dedicated lanes in isolated worktrees.
- Use GitHub Issues as the live tracker for new work.
- Treat `.beads/` as archive-only for historical context.
- If this file and the orchestration plan disagree, the orchestration plan is authoritative.

## Session Memory
- At session start, read `MEMORY.md` from `.claude/projects/` for this repo.
- Read only the memory files relevant to the current task.
- Treat memory as persistent workflow/architecture guidance across sessions.

## Jakarta Dual-Source Rule
- Runtime-active Jakarta replacements live under `wave/src/jakarta-overrides/java/`.
- Legacy source remains under `wave/src/main/java/`.
- If an override exists, edit the override copy; main-tree-only edits will not change runtime behavior.

## Mongo Migrations
- Mongo schema and index changes for Mongo-backed startup must be versioned with Mongock change units under `wave/src/main/java/org/waveprotocol/box/server/persistence/migrations/changesets/`.
- Automated Mongo migrations must stay compatible across the `N`/`N-1` blue-green overlap window; breaking changes are manual-only.
- Authoring and verification instructions live in `docs/runbooks/mongo-migrations.md`.

## Role Summary
- Lead: intake, routing, synthesis, and final checks.
- Planner: create/verify issue-level plan and acceptance slices.
- Architect: investigate constraints and produce complex implementation plans.
- Worker: implement assigned slice in dedicated worktree.
- Reviewer: review implementation and produce actionable findings.

For detailed role behavior and sequencing, follow:
`docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`.

## Worktree And Branch Guardrails
- Every agent that edits code/docs must work in its own git worktree.
- Prefer multiple independent worktrees/lanes over single-threaded execution when the task can be safely parallelized.
- Do not implement from `/Users/vega/devroot/incubator-wave`.
- Use `/Users/vega/devroot/worktrees/<branch-name>` for worktree paths.
- When running `npm` tasks under `j2cl/lit/`, run from that directory; do not leak Node tooling into other packages.
- Never create worktrees under `.claude/worktrees/`.
- Do not run `git checkout` or `git switch` inside the main repo during lane execution; it flips shared HEAD for open sessions.
- For lane execution details (tmux launch sequence, model flags, etc.), see [docs/agents/tool-usage.md](docs/agents/tool-usage.md).
- Do not mix lane edits in the main working tree.

## Task Lifecycle
- Ensure the linked issue has an adequate plan before implementation.
- If no adequate plan exists, switch to plan mode, write plan, run Claude review, then implement.
- Keep issue comments current during execution, not only at the end.
- After implementation, run reviewer flow, address findings, then prepare PR.

## PR Readiness Rules
- Before PR for app-affecting changes, run a narrow local sanity verification relevant to changed area.
- Record exact verification command and result in the linked GitHub Issue.
- Address review conversations; do not resolve threads just to bypass monitoring or branch protection.
- Keep issue, commits, and PR traceability aligned.

## GitHub Issue Updates
- Capture the worktree path and branch.
- Include the plan path used for implementation.
- Log commit SHAs with one-line summaries.
- Document verification commands and outcomes.
- Summarize review findings and resolution notes.
- Provide the PR number/URL.

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
- Use `$HOME/devroot/worktrees` as the shared root for local worktrees.
- **CRITICAL — tmux lanes must always be launched FROM the worktree directory, never from
  the main repo checkout or any subdirectory (e.g. `war/static`).** Any
  `git checkout` or `git switch` run inside the main repo changes the shared HEAD and flips
  the branch for every open Claude Code session. The canonical launch sequence is:
  ```bash
  git worktree add $HOME/devroot/worktrees/<branch-name> -b <branch-name>
  # then launch claude from that directory:
  tmux send-keys -t "<session>:<window>.<pane>" \
    "cd $HOME/devroot/worktrees/<branch-name> && claude --model <model> --dangerously-skip-permissions < /tmp/lane-prompt.txt" Enter
  ```
- **NEVER** create worktrees under `.claude/worktrees/` inside the main repo tree. Always
  use `$HOME/devroot/worktrees/<branch-name>` as the target path for `git worktree add`.
- For the standard existing-worktree boot flow, follow
  `docs/runbooks/worktree-lane-lifecycle.md` from the assigned worktree.
- Before opening a PR for app-affecting changes, run a local server sanity
  verification appropriate to the area changed and record the exact command
  plus result in Beads, or in `journal/local-verification/<date>-issue-<number>-<slug>.md`
  for GitHub-Issues workflow lanes that are not using Beads.
- Keep that check narrow and relevant: boot the app and hit a local health or
  auth endpoint for server/runtime changes, or exercise the affected UI against
  the local server for client changes.
- Before merge, clear review conversations by actually addressing them. Nitpicks
  need an explicit fix or reply; they are not silently ignorable.
- Do not resolve review threads just to bypass monitoring or branch protection.
  If a thread is already addressed, reply with the fix commit or technical
  reasoning before resolving it.
- When implementation is complete and review is resolved, create a pull request
  from the reviewed worktree.
- Keep Beads, commits, and PRs aligned so the task status is always traceable.
- When Beads is not part of the workflow, mirror the important local
  verification commands and results from the journal record into the PR body
  and issue comment.

Use `docs/github-issues.md` as the canonical evidence format and workflow reference.

## Changelog And Code Rules
- Any PR changing user-facing behavior must add a new changelog fragment under `wave/config/changelog.d/`; do not hand-edit generated `wave/config/changelog.json`.
- Run the changelog assemble/validate workflow so `wave/config/changelog.json` is regenerated, and validate with `scripts/validate-changelog.py` before merge/deploy.
- Follow `CODE_GUIDELINES.md` for repo-wide style and contribution rules.
