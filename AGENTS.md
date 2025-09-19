# AGENTS.md — Using MCP Servers with Codex CLI

This file gives the agent concise, practical rules for using Model Context Protocol (MCP) servers with Codex CLI. Keep actions safe, explain choices briefly, and favor tool use over guesswork.

## Why this doc
- Clarifies which MCP tools are available and how to invoke them
- Standardizes how to plan, search, and document decisions
- Minimizes risky actions by following Codex approval/sandbox policies

## Agent rules for tool use
- Prefer MCP tools over free-form browsing when available.
- Discover tool schema before use:
    - List available tools; read names, input fields, and descriptions.
    - If inputs are unclear, ask for clarification or request tool introspection.
- Keep calls minimal and purposeful. Avoid large, unfocused fetches.
- Document key calls (inputs/goal/outcome) succinctly in the private journal.

### Safe usage patterns
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
- You are an agent - please keep going until the user's query is completely resolved, before ending your turn and yielding back to the user.
- Only terminate your turn when you are sure that the problem is solved.
- Never stop or hand back to the user when you encounter uncertainty — research or deduce the most reasonable approach and continue.
- Do not ask the human to confirm or clarify assumptions, as you can always adjust later — decide what the most reasonable assumption is, proceed with it, and document it for the user's reference after you finish acting
- Use the memento tool frequently to capture technical insights, failed approaches, and user preferences.
- Before starting complex tasks, search the memento tool for relevant past experiences and lessons learned.
- Document architectural decisions and their outcomes for future reference.
- Track patterns in user feedback to improve collaboration over time.
- When you notice something that should be fixed but is unrelated to your current task, document it.

## Working with Git
- Before you finish your turn, if you have made any changes to files in a git repository, you MUST run `git status` and `git diff` to review your changes.
- If you have made changes that you want to keep, you MUST run commit all your changes with a clear, concise commit message describing what you have done.
- If needed group changes by relevant topics into separate commits.
- If you have made changes that you do not want to keep, you MUST revert those changes

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