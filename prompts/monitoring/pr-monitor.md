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
