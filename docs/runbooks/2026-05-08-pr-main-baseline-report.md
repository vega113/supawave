Status: Current
Owner: Project Maintainers
Updated: 2026-05-08
Review cadence: on-change

# PR-to-`main` Baseline Report — 2026-05-08

This is the first executed snapshot of the
[`pr-main-metrics-baseline`](pr-main-metrics-baseline.md) runbook. It captures
the throughput, latency, and review-engagement baseline for completed
non-draft PRs targeting `main` in `vega113/incubator-wave`.

This report is data-only. It does not propose merge-policy changes. It
defines the comparison window for the next snapshot and the threshold values
that would trigger a separate policy-review issue.

## Window

Fixed UTC range:

- `WINDOW_START = 2026-04-08T00:00:00Z`
- `WINDOW_END   = 2026-05-08T00:00:00Z`

Treated as `[WINDOW_START, WINDOW_END)`. The lane gate condition (≥ 20
qualifying merged PRs) is satisfied by a wide margin.

## Filters Applied

- merged PRs targeting `main` with `mergedAt` in the window and
  `isDraft === false`
- excluded: non-`main` base, `release/**` / `hotfix/**` head/title,
  closed-but-unmerged `main`, direct-push deploy events
- branch/title release-hotfix matcher and direct-push justification are
  defined in [`pr-main-metrics-baseline.md`](pr-main-metrics-baseline.md)
- `.github/CODEOWNERS` is **absent** at snapshot time, so reviewer-routing is
  not yet measurable from repo files

## Metrics

| Metric | Value | Source |
| --- | ---: | --- |
| `qualifying_merged_main_pr_count` | 298 | REST `gh pr list --state merged --base main` filtered to window + `isDraft === false` |
| `qualifying_merged_main_prs_per_day` | 9.93 | derived: `298 / 30` |
| `p50_open_to_merge_hours` | 0.53 | REST: median of `(mergedAt − createdAt)` |
| `p90_open_to_merge_hours` | 1.81 | REST: 90th percentile of same series |
| `p95_open_to_merge_hours` | 3.93 | REST: 95th percentile of same series |
| `merged_within_1h` | 230 / 298 | REST: bucket count |
| `merged_within_6h` | 291 / 298 | REST: bucket count |
| `merged_within_24h` | 297 / 298 | REST: bucket count |
| `max_open_to_merge_hours` | 24.92 | REST: max of same series |
| `pct_with_reviews` | 99.33% | GraphQL `pullRequest.reviews.totalCount > 0` |
| `pct_with_review_threads` | 90.94% | GraphQL `pullRequest.reviewThreads.totalCount > 0` |
| `total_review_threads` | 2015 | GraphQL `reviewThreads.nodes` summed |
| `threads_per_pr_mean` | 6.76 | derived: `2015 / 298` |
| `currently_unresolved_threads_total` | 38 | GraphQL `reviewThreads.nodes.isResolved === false` (collection-time state, not merge-time state) |
| `currently_unresolved_threads_per_pr_mean` | 0.13 | derived: `38 / 298` |
| `max_review_threads_per_pr` | 33 | GraphQL: max `reviewThreads.totalCount` across qualifying set |
| `prs_with_threads_truncated_at_100` | 0 | GraphQL: `totalCount > nodes.length` |
| `status_check_rollup_distribution` | `FAILURE: 285, SUCCESS: 13` | GraphQL `commits.last(1).commit.statusCheckRollup.state` |

## Exclusion Counts

| Class | Count | Source |
| --- | ---: | --- |
| merged with `baseRefName !== "main"` | 0 | REST `gh pr list --state merged` minus base-main rows |
| `release/**` / `hotfix/**` PRs in `main` set | 0 | REST + branch/title matcher |
| draft-at-collection `main` merged PRs | 0 | REST `isDraft === true` filter |
| closed-but-unmerged `main` PRs | 4 | REST `gh pr list --state closed --base main` filtered to window with `mergedAt == null` |
| direct-push deploy events | n/a (excluded by definition) | repo: `.github/workflows/build.yml`, `.github/workflows/deploy-contabo.yml` |

## Findings

- **Throughput is high relative to the gate.** 298 qualifying PRs in 30 days
  is roughly 15× the gate threshold. This makes p90 / p95 latency stable
  enough to use as the trigger metric.
- **Latency is dominated by fast merges.** 230 / 298 (77.2%) of qualifying
  PRs merge within an hour of opening; only 1 PR exceeds 24h
  (`max_open_to_merge_hours = 24.92`). The shape of the open-to-merge
  distribution is right-skewed but compact.
- **Review coverage is near-universal.** 99.33% of qualifying PRs have at
  least one recorded review and 90.94% have at least one review thread,
  meaning the baseline reflects PRs that did go through the review pipeline,
  not unreviewed merges.
- **Some review threads remain unresolved at collection time.** 38 currently
  unresolved threads across the qualifying set (mean 0.13 / PR; collection-time
  state, not necessarily merge-time state). This is low in absolute terms but
  high enough that it is worth tracking — a future window where the per-PR mean
  triples should trigger policy-review without further argument.
- **`statusCheckRollup` is not a usable trigger today.** 285 / 298 (95.6%) of
  qualifying PRs ended in a `FAILURE` rollup state. That ratio is dominated
  by non-required advisory checks and post-merge status events, so it cannot
  serve as the baseline merge-readiness signal. The runbook records this as
  a known limitation; a later phase needs to carve out required vs. advisory
  checks before promoting it.
- **CODEOWNERS is absent.** Reviewer-routing data is not yet derivable from
  repo files. Adding `.github/CODEOWNERS` later would unlock a
  reviewer-coverage metric without new infrastructure.

## Threshold Trigger Values for the Next Window

The **next** 30-day baseline (snapshot #2) opens a policy-review issue when
it satisfies the gate (≥ 20 qualifying merged PRs) and any of the following
holds against the values in **this** report:

- `p90_open_to_merge_hours` > **3.62 h** (= 2 × 1.81)
- `currently_unresolved_threads_per_pr_mean` > **0.39** (= 3 × 0.13)

If snapshot #2 does not trigger, it publishes its own values and snapshot #3
is tested against snapshot #2's numbers (not against this report). The
threshold rule always reaches back exactly one published baseline.

## Reproducibility

This report's values were generated by following Sections 3-4 of
[`pr-main-metrics-baseline.md`](pr-main-metrics-baseline.md) with
`WINDOW_END=2026-05-08T00:00:00Z`. The reduce step's JSON output for that run
matches the table above value-for-value.

Raw export row counts from this run (none hit the `--limit`; GraphQL data was
collected with the pre-c8460c0 runbook version that stopped pagination on
`oldest createdAt < WINDOW_START`):

| Export | Rows | Limit | Truncation? |
| --- | ---: | ---: | --- |
| `/tmp/issue-590-main-merged.json` | 857 | 1000 | no |
| `/tmp/issue-590-all-merged.json`  | 864 | 1000 | no |
| `/tmp/issue-590-main-closed.json` | 915 | 1000 | no |
| `/tmp/issue-590-review-threads.json` (GraphQL) | 300 | n/a (paginated) | no — stopped on `oldest createdAt < 2026-04-08T00:00:00Z` after 3 pages (pre-c8460c0 behaviour) |

> **Note:** The runbook was updated in commit c8460c0 to remove the
> `createdAt`-based early-stop and fetch pages until `hasNextPage=false` or
> the 30-page safety cap (3 000 PRs). Rerunning the current runbook against
> this window will fetch more GraphQL rows than the 300 recorded here; the
> values in the table above reflect the original collection run.

The widest review-thread footprint in the qualifying set was 33 threads
(`max_review_threads_per_pr`), well below the GraphQL `reviewThreads(first: 100)`
selection cap. A future window with a PR exceeding that cap will surface as
`prs_with_threads_truncated_at_100 > 0`; raise the GraphQL `first` argument
before relying on `currently_unresolved_threads_*` for that window.

## Previous Baseline

None. This is the first published baseline. The next snapshot's threshold
section must reference this report explicitly.
