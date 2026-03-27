# AGENTS.md — Delegation-First Workflow for Codex CLI

This repository uses a delegation-first operating model. The main Codex thread
acts as team lead: it coordinates, routes work, and verifies outcomes. Planning,
implementation, and review should move to dedicated agents working in isolated
git worktrees.

## Operating Model
- Keep the main thread focused on intake, routing, integration, and final
  verification.
- Route substantive investigation, planning, implementation, and review to the
  appropriate agent type instead of doing the work directly in the lead thread.
- Use repo-local Beads as the live task tracker for implementation work.
- Use [docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md](docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md)
  as the canonical detailed execution model for multi-agent task delivery.
- When that orchestration plan applies, follow it directly and do not invent an
  alternative workflow in the middle of task execution.
- Favor tool use over guesswork. Keep calls minimal, scoped, and purposeful.

## Session Memory
- At the start of every session, read `MEMORY.md` (the index) from the Claude
  project memory directory (`.claude/projects/` under the repo or user config root).
- After reading the index, read only the memory files that are relevant to the
  current task — do not read every linked file unconditionally.
- Memory contains: workflow rules, coding patterns, model selection, drill procedure, and project-specific lessons.
- This memory persists across sessions — always check it before starting work.

## Agent Roles

### Lead
- Acts as team lead and dispatcher.
- Converts the user request into the right agent flow, then waits for agent
  output instead of doing the work itself.
- Uses the lead thread only for coordination, synthesis, and final checks.

### Planner
- Receives requirements and turns them into Beads epics and implementation
  tasks.
- Breaks the work into independently executable pieces with clear acceptance
  criteria.
- If the task is complex enough to need a real implementation plan, the planner
  spawns an architect agent first.
- If a task does not already have an adequate plan in Beads or the repo docs,
  the planner must enter plan mode, write the plan, run a Claude review
  (`claude-review`) on that plan, address the comments, and only then hand the
  task off for implementation.

### Architect
- Investigates the codebase, docs, and constraints for complex tasks.
- Produces the implementation plan, risk list, and sequencing strategy.
- Uses `gpt-5.4` with `xhigh` reasoning.
- Must run a Claude review (`claude-review`) on the plan before concluding,
  then address the review comments and update the plan as needed.

### Worker
- Implements the assigned Beads task in a dedicated git worktree.
- Uses `gpt-5.4-mini` with `high` reasoning.
- Keeps the change set narrow and reports the files changed plus verification
  performed.
- If the task can be split safely, the worker should spawn additional
  `gpt-5.4-mini` subagents for independent implementation slices.

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
- That skill should run `scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave`
  from the target worktree before testing.
- Prefer the script's default `symlink` mode so worktrees reuse the same
  `_accounts`, `_attachments`, and `_deltas` state. Use `--mode copy` only
  when isolated persistence state is explicitly needed.
- Do not mix agent edits in the main working tree.
- Use `/Users/vega/devroot/worktrees` as the shared root for local worktrees.
- Before opening a PR for app-affecting changes, run a local server sanity
  verification appropriate to the area changed and record the exact command
  plus result in Beads.
- Keep that check narrow and relevant: boot the app and hit a local health or
  auth endpoint for server/runtime changes, or exercise the affected UI against
  the local server for client changes.
- When implementation is complete and review is resolved, create a pull request
  from the reviewed worktree.
- Keep Beads, commits, and PRs aligned so the task status is always traceable.

## Task Workflow
- Any agent working on a Beads task must first ensure there is a current plan
  for that task.
- If no adequate plan exists, the agent must switch to plan mode, create the
  plan, run a Claude review (`claude-review`) on the plan, address the review
  comments, then switch back to work mode and implement.
- For the full orchestration sequence, branch/worktree rules, Beads comment
  template, and PR flow, follow
  [docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md](docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md).
- If any short-form rule in `AGENTS.md` is ambiguous, the orchestration plan is
  the authoritative source for task execution behavior.
- Implementation should follow the reviewed plan rather than improvised changes
  made in the middle of execution.
- After implementation, review the code, run the reviewer flow, address review
  comments, and only then prepare the pull request.

## Beads Updates
- Agents working on tasks must update the Beads task with comments throughout
  execution, not only at the end.
- Beads comments must include every commit SHA created for the task and a short
  summary of what each commit does.
- Beads comments must also capture review feedback received from code review or
  Claude review, plus the follow-up comment that explains how each important
  finding was addressed.
- Before opening a pull request, the task comments should reflect the final
  implementation summary, the review outcome, and the commit list that landed
  the work.

## Changelog
- Every PR that changes user-facing behavior MUST update `wave/src/main/resources/config/changelog.json`
  before merging, and keep `wave/config/changelog.json` aligned for staged runtime config.
- Add a new entry at the top of the array (newest first) with `version`, `date`
  (ISO `YYYY-MM-DD`, usually matching `version`), `title`, `summary`, and
  `sections` (`feature` / `fix`).
- The changelog is displayed at `/changelog` and in the deploy upgrade banner.
- Keep entries concise: a 1-2 sentence summary and short bullet-style change
  lists.

## Tool Usage Rules
- Prefer MCP tools over free-form browsing when available.
- Discover tool schema before use:
  - List available tools; read names, input fields, and descriptions.
  - If inputs are unclear, ask for clarification or request tool introspection.
- Keep calls minimal and purposeful. Avoid large, unfocused fetches.
- Document key calls (inputs, goal, outcome) succinctly in the private journal.

### Safe Usage Patterns
- Be explicit about scope: domains, max size, and format.
- Respect `robots.txt` and site terms when applicable.
- Avoid fetching sensitive or private URLs without explicit user intent.
- Cache or summarize results to reduce repeated calls.

## Tool Usage Guidance (for this setup)

Below are practical, action-focused rules for each MCP server defined in `~/.codex/config.toml`.

### Context7 (Up-to-date Docs)
- When to use: You need the latest, version-specific API docs or examples for a library/framework.
- How to use: Resolve the library name/version, then request focused docs by topic/section. Keep token limits reasonable; prefer concise, relevant excerpts.
- Good prompts: “Fetch docs for `/org/project@version` focused on ‘routing’”, “Get usage for `functionX` with examples for `v14`”.
- Output handling: Summarize key APIs, cite the version explicitly, and link doc URLs when provided.
- Safety: Prefer official sources; avoid relying on outdated snippets. Double-check versions before applying changes.

### Memento
- You have access to the Memento MCP knowledge graph memory system, which provides you with persistent memory capabilities.
- Your memory tools are provided by Memento MCP, a sophisticated knowledge graph implementation.
- When asked about past conversations or user information, always check the Memento MCP knowledge graph first.
- You should use semantic_search to find relevant information in your memory when answering questions.
- YOU MUST use the memento tool frequently to capture technical insights, failed approaches, and user preferences.
- Before starting complex tasks, search the memento tool for relevant past experiences and lessons learned.
- Document architectural decisions and their outcomes for future reference.
- Track patterns in user feedback to improve collaboration over time.
- When you notice something that should be fixed but is unrelated to your current task, document it in your journal rather than fixing it immediately.


## Agent Guidelines
- Keep going until the user's request is fully resolved.
- Only terminate a turn when the problem is solved or the requested output is
  produced.
- When uncertain, research or deduce the most reasonable approach and continue.
- If a task worktree picks up an unexpected git diff or commit, do not stop to ask the user what to do first. Review the unexpected diff, consult Claude review on the diff when the preservation choice is not obvious, keep the useful commits, and continue with the task.
- Only escalate after that review path if the branch state is still ambiguous or recovery would require destructive history edits.
- Do not ask the human to confirm assumptions unless the task is blocked on
  unavailable information.
- Use the memory toolchain frequently to capture technical insights, failed
  approaches, and user preferences.
- Before complex tasks, search for relevant past experiences and lessons
  learned.
- Document architectural decisions and their outcomes for future reference.
- Track patterns in user feedback to improve collaboration over time.
- When you notice an unrelated issue, document it instead of changing scope.

## Working with Git
- Before finishing a turn with file changes, run `git status` and `git diff` to
  review the exact delta.
- Commit changes you intend to keep with a clear, concise message.
- Group unrelated changes into separate commits when that improves traceability.
- Revert changes you do not want to keep.

## Code Guidelines
- Do not use FQN in your code, instead import from the appropriate module.
- Do not write one line code blocks inside brackets.
- Make sure to follow the [Codex Code Guidelines](CODE_GUIDELINES.md)
- Write self-documenting code.
- Do not write comments in the code. Instead if you need to explain something, extract the code into a function and write a comment for the function.
- Avoid using return statements in the middle of a function.
- Avoid using mutable variables/parameters/arguments.


## When you need to call tools from the shell, use this rubric:
- Find Files: `fd`
- Find Text: `rg` (ripgrep)
- Find Code Structure (TS/TSX): `ast-grep`
    - **Default to TypeScript:**
        - `.ts` → `ast-grep --lang ts -p '<pattern>'`
        - `.tsx` (React) → `ast-grep --lang tsx -p '<pattern>'`
    - For other languages, set `--lang` appropriately (e.g., `--lang rust`).
- Select among matches: pipe to `fzf`
- JSON: `jq`
- YAML/XML: `yq`
If ast-grep is available avoid tools `rg` or `grep` unless a plain‑text search is explicitly requested.
