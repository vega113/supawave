# Codex Tool Usage for incubator-wave

This document is Codex-specific guidance for `incubator-wave`. Use it together
with [AGENTS.md](../../AGENTS.md) for repo operating rules and with
[docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md](../superpowers/plans/2026-03-18-agent-orchestration-plan.md)
for the execution flow.

## Tool Routing
- Prefer MCP tools over free-form browsing when available.
- Discover tool schema before use: list available tools, read names, input
  fields, and descriptions, and ask for clarification or tool introspection if
  inputs are unclear.
- Keep calls minimal and purposeful. Avoid large, unfocused fetches.
- Document key calls, including inputs, goal, and outcome, succinctly in the
  private journal.
- Treat tool use as the default way to gather repo and environment context
  instead of guessing.

## Model Tiers
- Complex bugs, architecture, and planning: `gpt-5.4` with `xhigh` reasoning.
- General code writing and implementation: `gpt-5.4` with `high` reasoning.
- Small or moderate tasks that do not need large context: `gpt-5.4-mini` with
  `high` reasoning.
- Use Codex for reviews in this repository; do not use implementation lanes for
  self-review.

## MCP Guidance
- `Context7`: Use when you need current, version-specific docs for a library or
  framework. Prefer official sources, resolve the package or version first, and
  cite the version explicitly when you rely on it.
- `Memento`: When the environment exposes the Memento knowledge graph, search
  it for relevant past experiences before complex tasks, and capture technical
  insights, failed approaches, and user preferences as you work.
- Keep MCP usage narrow and task-specific. If a tool is not clearly relevant,
  do not use it.

## Safety And Scope
- Be explicit about scope: domains, max size, and format.
- Respect `robots.txt` and site terms when applicable.
- Avoid fetching sensitive or private URLs without explicit user intent.
- Cache or summarize results to reduce repeated calls.
- Do not broaden a task with unrelated exploration just because a tool is
  available.

## Shell Tool Rubric
- Find files: `fd`
- Find text: `rg`
- Find code structure in TypeScript or TSX: `ast-grep`
  - `.ts` -> `ast-grep --lang ts -p '<pattern>'`
  - `.tsx` -> `ast-grep --lang tsx -p '<pattern>'`
  - For other languages, set `--lang` accordingly.
- Select among matches: `fzf`
- JSON: `jq`
- YAML/XML: `yq`
- If `ast-grep` is available, prefer it for supported structural queries.
  Use `rg` or `grep` when parser coverage is missing or the task is plain-text
  or regex search.
