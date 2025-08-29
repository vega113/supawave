# Gemini Integration Guide (Non‑Duplicative)

This document focuses on how to use Gemini-specific tooling with this repository.
For all general project information (overview, structure, setup, building, running,
dependencies, configuration), please refer to README.md. This avoids duplication
and keeps a single source of truth.


## Gemini Agent Journal Tool Guidelines

When using a Gemini-powered agent with the Journal MCP tool enabled, the agent must actively
document its work so activity is auditable, decisions are explainable, and progress is trackable.

- Start-of-turn context refresh: Before beginning any new work in a turn, review the most recent
  journal entries to restore context and ensure continuity.
- Frequency: Journal frequently throughout a turn (at minimum after planning, after key decisions,
  after completing a task, and before ending the turn).
- Content of entries:
  - Thoughts and feelings: Briefly capture current understanding, uncertainties, confidence level,
    and any concerns or risks.
  - Decisions: Whenever a choice is made, record the options considered, pros/cons, and why the
    specific option was chosen.
  - Plan and tasks: Record the current plan, task list, owners (if applicable), and status for
    each task.
  - Progress updates: Note what was attempted, what worked, what failed, and any blockers.
  - End-of-turn summary: Summarize what changed since the start of the turn, what was completed,
    what remains, and the next intended action.

Recommended structure for each journal update:

1. Context: What I am working on now and why (include “start-of-turn context refresh” when applicable).
2. Plan: Current plan and tasks (with statuses: Not started / In progress / Blocked / Done).
3. Decision Log: Alternatives considered and rationale for any choices made.
4. Progress: Actions taken, results, and evidence (links/paths/commits/tests if relevant).
5. Feelings/Confidence: Confidence level, risks/unknowns, and mitigation ideas.
6. Next Step: The very next concrete action.
7. End-of-Turn Summary: Brief recap before yielding control.

Examples (prompts to the agent or tool-invocation intent):

- Start-of-turn context refresh: "Journal: Reviewed last two entries (build failure and fix attempt).
  Resuming work on CI config. Context restored; proceeding with task 2."
- Start-of-turn planning: "Journal: Planning current work on <issue>. Tasks: [1) Analyze files,
  2) Implement change, 3) Test]. Initial status: all Not started. Confidence: medium; risk: unclear
  config format."
- Decision rationale: "Journal: Considered option A (low effort, partial coverage) vs option B
  (more robust, higher effort). Chose B due to long-term maintainability and testability."
- Progress update: "Journal: Completed task 1 (analysis). Findings: file X requires section Y.
  Starting task 2."
- End-of-turn: "Journal: Summary – Implemented section addition in GEMINI.md, no code changes
  required. Remaining: team review. Next step: integrate feedback."

Notes:

- Keep entries concise but specific; prefer bullet points and checklists when appropriate.
- If a task is blocked, clearly state what is needed to unblock it.
- Include references to files, paths, or commits for traceability when applicable.
- Follow any organization-specific retention or privacy policies when journaling sensitive information.