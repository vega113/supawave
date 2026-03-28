# PR #441 Missed Codex Review Investigation

## Scope

Investigate why Codex review comments on PR `#441` were not addressed before the PR progressed, using GitHub review history plus local PR monitor logs and worktree state.

## Executive Summary

PR `#441` merged before Codex posted its review. The monitor did not silently resolve comments, and it did not merge a stale or divergent head. The actual failure was a process gap:

- the monitor treated `CodeRabbit` success plus zero current review threads as sufficient to merge
- PR `#441` targeted `fix/tag-filter-regression`, not `main`, so the repository review gate did not block the initial open/synchronize path
- Codex review arrived after the merge, so there was nothing for the monitor to address at decision time

There was also a secondary monitor-worktree issue: the monitor branch tracked `origin/fix/tag-filter-regression` instead of the PR head branch, which made `git status` misleading and broke one helper script. That did not cause the missed review, but it made branch-state inspection less reliable.

## Evidence Collected

### GitHub PR state

Source commands:

```bash
gh pr view 441 -R vega113/incubator-wave --json number,title,baseRefName,headRefName,headRefOid,mergeCommit,isDraft,reviewDecision,mergeStateStatus,statusCheckRollup,updatedAt,commits
gh api graphql -f owner=vega113 -f repo=incubator-wave -F number=441 -f query='<timeline query>'
gh run view 23685979234 -R vega113/incubator-wave --json name,event,status,conclusion,createdAt,updatedAt,startedAt,jobs,url
```

Observed facts:

- PR `#441` title: `fix: support tag queries in OT search`
- Base branch: `fix/tag-filter-regression`
- Head branch: `fix/ot-search-tag-filter-native`
- Reviewed commit: `9cc8bd99b916dd5dd87a913ade71fb7bf0fe8a62`
- PR merged at `2026-03-28T13:11:26Z`
- Codex review submitted at `2026-03-28T13:13:24Z`
- Codex inline comments created at `2026-03-28T13:13:25Z`
- Codex Review Gate workflow run `23685979234` was triggered by `pull_request_review` at `2026-03-28T13:13:28Z`, after the PR was already merged
- CodeRabbit posted a `Review skipped` comment at `2026-03-28T13:05:37Z` because the base branch was not the default branch
- The `CodeRabbit` status context was still `SUCCESS` at `2026-03-28T13:05:38Z`

### Local monitor log

Source log:

```text
/Users/vega/devroot/worktrees/pr-monitors/logs/pr441-live-monitor.log
```

Observed monitor behavior:

- The monitor queried PR state and saw:
  - `mergeStateStatus=CLEAN`
  - `mergeable=MERGEABLE`
  - `reviewDecision=""`
  - only `CodeRabbit` status present
- It queried GitHub GraphQL review threads and got `reviewThreads.nodes=[]`
- It inspected top-level PR comments and saw only the CodeRabbit `Review skipped` comment
- It ran local verification, then executed:

```bash
gh pr merge 441 --merge
```

- The merge succeeded at `2026-03-28T13:11:26Z`
- Only after that did Codex review land on GitHub

### Monitor worktree state

Source log and local git inspection:

```bash
git -C /Users/vega/devroot/worktrees/ot-search-tag-filter-native/pr441-live-monitor status --short --branch
git -C /Users/vega/devroot/worktrees/ot-search-tag-filter-native/pr441-live-monitor log --oneline --decorate --graph -10 --all --branches='fix/ot-search-tag-filter-native' --branches='origin/fix/ot-search-tag-filter-native' --branches='origin/fix/tag-filter-regression'
```

Observed facts:

- `git status` reported:

```text
## pr441-live-head...origin/fix/tag-filter-regression [ahead 1]
```

- But direct commit comparison showed `HEAD` exactly matched `origin/fix/ot-search-tag-filter-native`
- The helper script `fetch_comments.py` failed because it assumes the local branch is directly associated with the PR:

```text
RuntimeError: Command failed: gh pr view --json number,headRepositoryOwner,headRepository
no pull requests found for branch "pr441-live-head"
```

This was a monitor-worktree configuration flaw, but not the root cause of the missed Codex review. The monitor fell back to direct GraphQL queries and still saw zero review threads before merge.

## Reconstructed Timeline

All timestamps UTC.

1. `2026-03-28T13:05:03Z`
   Commit `9cc8bd99` created for PR `#441`.
2. `2026-03-28T13:05:32Z`
   PR `#441` opened.
3. `2026-03-28T13:05:37Z`
   CodeRabbit posted `Review skipped` because the base branch was not the default branch.
4. `2026-03-28T13:05:38Z`
   `CodeRabbit` status context was `SUCCESS`.
5. Between `13:10Z` and `13:11Z`
   Local monitor inspected the PR, found no review threads, saw only the successful `CodeRabbit` status, and decided it was mergeable.
6. `2026-03-28T13:11:26Z`
   Local monitor merged PR `#441`.
7. `2026-03-28T13:13:24Z`
   Codex submitted a `COMMENTED` review on commit `9cc8bd99`.
8. `2026-03-28T13:13:25Z`
   Two unresolved Codex review threads were created:
   - `SearchWaveletSnapshotPublisher.java:124`
   - `SearchServlet.java:174`
9. `2026-03-28T13:13:28Z`
   `Codex Review Gate` workflow ran from the `pull_request_review` event.
10. `2026-03-28T13:13:36Z`
    That workflow succeeded, but the PR was already merged.

## Failure-Mode Assessment

### 1. PR monitor exiting too early

Partially yes, but not in the sense of crashing or stopping before merge.

The monitor completed the merge and considered the PR done before Codex review had actually arrived. The monitor did not continue to watch for late-arriving review after merge. That is a process gap in the merge rule.

### 2. PR monitor resolving comments without addressing them

No.

Evidence before merge showed `reviewThreads.nodes=[]`. The unresolved Codex threads did not exist yet, so the monitor could not have resolved them prematurely.

### 3. Monitor tracking stale state, wrong branch, or wrong pane

Not the root cause, but there was a real secondary flaw.

- The monitor worktree was on the correct head commit.
- The worktree branch tracked the base branch instead of the PR head branch.
- That caused misleading `git status` output and broke helper tooling that expected the local branch to map back to the PR.

This made the environment less trustworthy, but it did not cause the merge decision. The decisive state check still used the correct PR number and GitHub GraphQL.

### 4. Review comments arriving after the monitor considered the PR done

Yes. This is the primary root cause.

Codex review arrived about `1m58s` after merge. The monitor merged on the absence of current review threads, but did not require an actual Codex signal for the current head commit before merging a stacked PR.

### 5. Another process gap

Yes. Two additional process gaps contributed:

#### A. Stacked PRs were not protected the same way as `main` PRs

`codex-review-gate.yml` listens to `pull_request` only for `branches: [ main ]`. PR `#441` targeted `fix/tag-filter-regression`, so the gate did not block the initial open/synchronize path.

The gate only ran later because the late Codex review triggered the `pull_request_review` event, by which time the PR was already merged.

#### B. CodeRabbit `Review skipped` still surfaced as a successful status

The monitor saw:

- `CodeRabbit` status = `SUCCESS`
- no unresolved review threads

That combination looked merge-safe, even though CodeRabbit had explicitly skipped review and Codex had not yet replied.

## Root Cause

The root cause was a merge-readiness rule that was too weak for stacked PRs:

- it required zero currently visible threads
- it accepted `CodeRabbit` success even when CodeRabbit had skipped review
- it did not require an actual Codex review signal for the current head commit before merge

Because PR `#441` targeted a non-default base branch, the repository gate did not prevent this. The monitor merged during the window between PR open and Codex review arrival.

## Impact

The missed Codex findings were real review debt on the stacked OT-search branch:

1. `SearchWaveletSnapshotPublisher.pruneInactiveSubscription(...)` removed the indexer subscription and wavelet mapping, but did not clear per-wavelet publisher/cache state.
2. `SearchServlet.canonicalBootstrapSearchResult(...)` called the auxiliary canonical `performSearch(...)` directly, so a runtime failure in the bootstrap requery could fail an otherwise successful main search response.

As of this investigation worktree:

- commit `9cc8bd99` is contained by `origin/fix/tag-filter-regression`
- commit `9cc8bd99` is not contained by `origin/main`

So the missed comments still matter if the stacked OT-search-native line is revived or merged forward, but they are not on `main` in this checkout.

## Conclusions

1. The primary issue was not thread resolution abuse or stale PR-number targeting.
2. The primary issue was late-arriving Codex review after the monitor had already merged.
3. The monitor’s merge rule was insufficient for stacked PRs into non-default branches.
4. The monitor worktree branch-tracking setup was flawed and should be treated as a separate reliability issue.

## Actions Taken In This Investigation Lane

1. Added this investigation record.
2. Tightened the monitoring prompt so stacked PRs require an explicit Codex signal on the current head commit before merge, and CodeRabbit `Review skipped` is not treated as sufficient.
3. Updated orchestration documentation to call out that `main`-branch review gates do not protect stacked PRs.

## Recommended Follow-Up

1. If `fix/tag-filter-regression` or `fix/ot-search-tag-filter-native` is going to move forward again, address both Codex findings before the next merge step.
2. Fix monitor worktree creation so the local monitor branch tracks the PR head branch, not the PR base branch.
3. Keep using direct GraphQL for review-thread state; do not rely on branch-coupled helper wrappers inside synthetic monitor branches.
