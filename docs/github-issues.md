Status: Canonical
Updated: 2026-04-03
Owner: Project Maintainers
Review cadence: quarterly and when workflow conventions change

# GitHub Issues Workflow

This document defines the live planning and execution tracker for
`incubator-wave`.

## System Of Record

- GitHub Issues are the live tracker for new work.
- Use issue comments as the canonical execution log.
- Use pull requests for code review and merge flow.
- Treat `.beads/` and `docs/epics/README.md` as historical archive material
  only.

## Core Filters

- Live implementation slices:
  - `is:issue repo:vega113/supawave label:agent-task`
- Active in-flight issue work:
  - `is:issue repo:vega113/supawave label:agent-task label:in-progress`
- Harness migration slices:
  - `is:issue repo:vega113/supawave label:harness-engineering`

## Label Conventions

- `agent-task`
  - Default label for issues that are intended to be executed directly by an
    agent lane.
- `in-progress`
  - Add when a lane is actively working the issue. Remove or replace when the
    lane completes or hands off.
- `harness-engineering`
  - Use for harness-roadmap and tracker-migration work.
- Existing domain labels such as `bug`, `enhancement`, or `question`
  - Keep using them when they clarify issue type.

## Default Execution Record

Record the following in the linked GitHub Issue comments as work progresses:

- worktree path and branch name
- plan path if a repo plan was used or created
- architect/planner summary when relevant
- commit SHAs with one-line summaries
- verification commands and results
- review findings and how they were addressed
- PR number or URL

When a PR exists, keep the PR body or summary concise and derived from the
issue. Do not maintain two separate long-form execution logs.

## Planning And Implementation Flow

1. Create or identify the GitHub Issue for the slice of work.
2. Ensure there is an adequate plan in the repo docs or linked from the issue.
3. Execute from an isolated worktree.
4. Record execution evidence in issue comments as the work progresses.
5. Open a PR that links back to the issue.
6. Close the issue once implementation, verification, and review traceability
   are complete.

## Historical Beads Retention

- Keep `.beads/issues.jsonl` unchanged unless explicitly doing archive cleanup.
- Keep `.beads/README.md` and `docs/epics/README.md` as historical references.
- Do not create new tasks or comments in Beads.
