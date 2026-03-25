# PR and Issue Monitor Prompt

## Overview
Proactive GitHub PR and Issue monitor that runs every 5 minutes via cron. Checks all open PRs across monitored repos, fixes issues, resolves review threads, and merges PRs when ready.

## Key Behaviors

### Immediate branch cascade on merge
When a PR merges, immediately update ALL other open PRs that are now BEHIND main. Don't wait for the next cron cycle.

### Review thread handling
- Address all unresolved review threads (inline, out-of-diff, P1/P2)
- Fix code issues when the review comment is valid
- Reply with justification and resolve when the suggestion is a P2 enhancement or already addressed
- Resolve threads via GraphQL: `mutation { resolveReviewThread(input: {threadId: "ID"}) { thread { isResolved } } }`

### Codex Review Gate
- Gate checks CodeRabbit status + grace period window
- Uses latest commit timestamp as baseline (not PR updatedAt)
- 5-minute grace period after latest commit
- Re-run failed gates after window expires

### Merge strategy
- incubator-wave: `--merge`
- tube2web/tubescribes: `--squash`
- slides-lab: `--squash`
- Always enable auto-merge: `gh pr merge NUM -R repo --merge --auto`
- Update BEHIND branches immediately after any merge

### Parallel execution
- Spawn background agents for each PR/issue that needs work
- One agent per PR fix for maximum parallelism
- Don't duplicate work between agents

## Monitored Repos
- vega113/incubator-wave
- vega113/tube2web
- vega113/tubescribes
- vega113/slides-lab

## Improvement Roadmap
1. Gate baseline uses commit SHA (not prUpdatedAt)
2. Reduced grace period to 5 minutes
3. TODO: Enable GitHub merge queue to eliminate BEHIND cascade
4. TODO: Configure chatgpt-codex-connector to skip merge commits
5. TODO: Auto-resolve P2/informational threads that don't need code changes
