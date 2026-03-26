# PR and Issue Monitor — Recurring Prompt

> Use with: `/loop 5m <this prompt>` or as a GitHub Actions workflow trigger

## Quick-exit guard (smart polling)

Before doing anything, check if there's any work:
```
count=$(gh search prs --author=@me --state=open --json number --jq length 2>/dev/null)
issues=$(for repo in vega113/incubator-wave vega113/tube2web vega113/tubescribes vega113/slides-lab; do gh issue list -R "$repo" --state open --json number --jq length 2>/dev/null; done | paste -sd+ | bc)
if [ "${count:-0}" -eq 0 ] && [ "${issues:-0}" -eq 0 ]; then echo "All clean"; exit 0; fi
```

## 1. Discover open PRs

Search for open PRs authored by me and PRs where review is requested, across all repos updated in the last 7 days.

## 2. For each PR — inspect and act

### a. CI status
- Failed checks → investigate logs, fix code issues, re-run flaky tests
- Stuck/pending > 30min → re-run

### b. Review threads
- Unresolved threads → fix code if valid, reply + resolve via GraphQL if already addressed
- Resolve ALL threads: inline, out-of-diff, P1/P2, chatgpt-codex-connector, coderabbitai
- GraphQL resolve: `mutation { resolveReviewThread(input: {threadId: "ID"}) { thread { isResolved } } }`

### c. Merge readiness
- All checks pass + no conflicts + no unresolved threads + updatedAt > 5min → merge
- incubator-wave: `--merge`, tube2web/tubescribes/slides-lab: `--squash`
- Enable auto-merge: `gh pr merge NUM -R repo --merge --auto`

### d. Immediate cascade on merge
When ANY PR merges, immediately update ALL other BEHIND PRs. Don't wait for next cycle.

## 3. Discover and fix open issues

Check all monitored repos for open issues. Spawn background agents to fix actionable ones.

## 4. Codex Review Gate handling
- Gate checks CodeRabbit status + grace period window
- Gate uses latest **commit timestamp** as baseline (not prUpdatedAt)
- 5-minute grace period after latest commit push
- Comments/thread resolutions do NOT restart the timer
- Re-run failed gates after window expires

## Monitored repos
- vega113/incubator-wave
- vega113/tube2web
- vega113/tubescribes
- vega113/slides-lab

## Merge strategy
| Repo | Strategy |
|------|----------|
| incubator-wave | `--merge` |
| tube2web | `--squash` |
| tubescribes | `--squash` |
| slides-lab | `--squash` |

## Integration Safeguards

### Deploy-broken gate
- Before merging ANY PR, check if the latest deploy succeeded: `gh run list -R repo --workflow="deploy-contabo.yml" --limit 1 --json conclusion`
- If deploys are failing, STOP merging new feature PRs. Only merge PRs that fix the deploy issue.
- This prevents piling up untested changes on a broken deploy pipeline.

### One-at-a-time merge with verification
- Merge PRs ONE at a time, not in rapid succession
- After each merge, wait for the CI build on main to pass before merging the next PR
- This catches integration issues early instead of after 20 PRs have merged

### Rebase before merge (not just update-branch)
- Before merging, ensure the PR branch has been rebased on the LATEST main
- The "update branch" API merges main into the PR, but this can mask conflicts
- If a PR was created from an old main, it may silently reintroduce code that was already fixed

### Agent worktree freshness
- When spawning agents for issues, they should always clone from the latest main
- If an agent takes >10 minutes and main has changed, the PR should be rebased before merge
- Never auto-merge a PR whose base is >2 commits behind main without rebase

### Conflict detection after merge
- After merging a PR, check if the deploy workflow succeeds before merging the next one
- If deploy fails after a merge, investigate immediately — don't continue merging other PRs
- Check `gh run list --workflow="deploy-contabo.yml" --limit 1` after each merge

### Duplicate resource detection
- Before merging PRs that modify servlet registrations, route mappings, or Guice modules:
  - Check for duplicate path registrations
  - Search for conflicting @Singleton bindings
  - Verify no two servlets map to the same URL path

## Improvement Roadmap
1. Gate baseline uses commit SHA (not prUpdatedAt)
2. Reduced grace period to 5 minutes
3. TODO: Enable GitHub merge queue to eliminate BEHIND cascade
4. TODO: Configure chatgpt-codex-connector to skip merge commits
5. TODO: Auto-resolve P2/informational threads that don't need code changes
