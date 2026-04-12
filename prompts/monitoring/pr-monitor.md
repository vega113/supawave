# PR and Issue Monitor — Recurring Prompt

> Use with: `/loop 3m <this prompt>` or as a GitHub Actions workflow trigger

## Quick-exit guard (smart polling)

Before doing anything, check if there's any work:
```
count=$(gh search prs --author=@me --state=open --json number --jq length 2>/dev/null)
reviews=$(gh search prs --review-requested=@me --state=open --json number --jq length 2>/dev/null)
issues=$(for repo in vega113/supawave vega113/tube2web vega113/tubescribes vega113/slides-lab; do gh issue list -R "$repo" --state open --json number --jq length 2>/dev/null; done | paste -sd+ | bc)
if [ "${count:-0}" -eq 0 ] && [ "${reviews:-0}" -eq 0 ] && [ "${issues:-0}" -eq 0 ]; then echo "All clean"; exit 0; fi
```

## 1. Discover open PRs

Search for open PRs authored by me and PRs where review is requested, across all repos updated in the last 7 days.

## 2. For each PR — inspect and act

### a. CI status
- Failed checks → investigate logs, fix code issues, re-run flaky tests
- Stuck/pending > 30min → re-run

### b. Review threads
- Inspect thread-aware state with GraphQL, not flat PR comments
- Unresolved threads → fix code if valid, or reply with technical reasoning if no code change is needed
- Treat ALL threads as required work: inline, out-of-diff, P1/P2, nitpicks, chatgpt-codex-connector, coderabbitai
- Never resolve a thread just to clear the gate
- Only resolve after the fix is pushed or after posting the reply that explains why no code change is needed
- If a thread was already addressed by a commit, reply with the commit SHA or exact explanation before resolving it
- Before merge, inspect recently resolved threads too; a bare resolution with no reply or fix is not an acceptable disposition
- GraphQL resolve: `mutation { resolveReviewThread(input: {threadId: "ID"}) { thread { isResolved } } }`

### c. Merge readiness
- All checks pass + no conflicts + no unresolved threads + every nitpick has an explicit disposition + latest commit older than 10 minutes → merge
- For stacked PRs targeting a non-default branch, also verify explicit Codex coverage on the current `headRefOid` before merge
- supawave: `--merge`, tube2web/tubescribes/slides-lab: `--squash`
- Enable auto-merge: `gh pr merge NUM -R repo --merge --auto`

### d. Immediate cascade on merge
When ANY PR merges, immediately update ALL other BEHIND PRs. Don't wait for next cycle.

### e. Completion rule
- Do not stop at merge-ready
- Do not stop when auto-merge is armed
- Keep polling until GitHub reports the PR actually merged, or until the PR is truly blocked or closed without merge

## 3. Discover and fix open issues

Check all monitored repos for open issues. Spawn background agents to fix actionable ones.

## 4. Codex Review Gate handling
- Monitor PRs every ~3 minutes so review-state changes are noticed quickly even though the Actions schedule fallback only runs every 5 minutes.
- Gate baseline is the latest qualifying **CodeRabbit completion on the current head**, not `prUpdatedAt` and not the latest commit timestamp alone.
- Gate must stay red while any review thread is unresolved, including nitpicks.
- After CodeRabbit completes on the current head:
  - if no further code changes are needed, add a PR-level `+1` reaction from Codex immediately
  - then re-run the gate via a PR comment containing `/codex-review-gate`
- If Codex does not add that PR-level `+1`, the gate auto-passes after 10 minutes of silence, as long as no newer commit exists.
- New commits invalidate the previous CodeRabbit completion and require a fresh current-head CodeRabbit success.
- Comments/thread resolutions do NOT restart the 10-minute CodeRabbit-completion window.
- Re-run failed gates once the 10-minute grace period has elapsed if no thumbs-up was added; scheduled fallback uses the same PR comment trigger so it does not depend on the PR branch carrying the latest workflow file.
- For stacked PRs targeting a non-default branch, do not merge just because the 10-minute window elapsed and no threads exist yet; verify explicit Codex coverage on the current `headRefOid` first
- Do not treat `codex-reviewed` as sufficient for stacked PRs; labels are not commit-scoped
- If CodeRabbit says `Review skipped` because the base branch is not the default branch, treat that as missing review coverage, not as success
- After any late-arriving bot review, re-check unresolved threads before merging

## Monitored repos
- vega113/supawave
- vega113/tube2web
- vega113/tubescribes
- vega113/slides-lab

## Merge strategy
| Repo | Strategy |
|------|----------|
| supawave | `--merge` |
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
1. Gate baseline uses the latest qualifying current-head CodeRabbit completion
2. Keep the 10-minute Codex thumbs-up grace period aligned with the workflow
3. TODO: Enable GitHub merge queue to eliminate BEHIND cascade
4. TODO: Configure chatgpt-codex-connector to skip merge commits
