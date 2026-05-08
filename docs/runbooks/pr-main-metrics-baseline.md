Status: Current
Owner: Project Maintainers
Updated: 2026-05-08
Review cadence: quarterly

# PR-to-`main` Metrics Baseline

Use this runbook to capture a reproducible 30-day throughput baseline for
completed non-draft PRs targeting `main`, and to derive the policy-review
threshold for later merge-flow or guardrail changes.

This runbook is window-agnostic. Each execution writes a separate dated
snapshot report under `docs/runbooks/YYYY-MM-DD-pr-main-baseline-report.md`.
The first such snapshot lives at
[`2026-05-08-pr-main-baseline-report.md`](2026-05-08-pr-main-baseline-report.md).

The runbook is report-only. It does not change merge policy, reviewer routing,
workflow behavior, or deployment behavior, and it does not require new
infrastructure or a persistent metrics store.

## 1. Scope

The baseline counts **completed non-draft PRs targeting `main`** in a fixed
30-day UTC window `[WINDOW_START, WINDOW_END)`.

`WINDOW_END` is anchored to the snapshot date at `00:00:00Z`. `WINDOW_START`
is exactly 30 days earlier. Pick the values once at the top of a run and reuse
them for every query.

The first baseline excludes these classes and counts each separately so a
later report can show whether they entered the window:

- merged PRs whose `baseRefName !== "main"`
- `release/**` and `hotfix/**` PRs, detected by `headRefName` prefix or by a
  `release:` / `hotfix:` title prefix
- direct-push deploy events, because they are workflow-triggered events rather
  than PR rows. Justification:
  - `.github/workflows/build.yml` triggers on `push`, `pull_request`,
    `release`, and `workflow_dispatch`
  - `.github/workflows/deploy-contabo.yml` triggers on `push` to `main` and
    `workflow_dispatch`
- closed-but-unmerged `main` PRs, because they are not completed throughput

A single `gh pr list` export cannot capture direct-push events; the runbook
documents that they exist as a separate event class and excludes them by
definition.

## 2. Required tools and rate limits

- `gh` CLI authenticated against `vega113/incubator-wave` with at least
  `repo` and `read:org` scopes
- `node` for the JSON reduce step
- GitHub REST API: 5 000 requests/hour per authenticated user (request-count
  based). GraphQL API: separate point-based quota (~5 000 points/hour); a
  simple paginated query costs roughly 1 point per page.
  This runbook makes ~3 REST calls and ~3 GraphQL pages per run, well under
  either rate-limit threshold

## 3. Snapshot the data

Run from the repository root.

```bash
export WINDOW_END="YYYY-MM-DDT00:00:00Z"   # snapshot date
export WINDOW_START="$(node -e '
  const e = Date.parse(process.argv[1]);
  console.log(new Date(e - 30*24*60*60*1000).toISOString());
' "$WINDOW_END")"
printf 'WINDOW_START=%s\nWINDOW_END=%s\n' "$WINDOW_START" "$WINDOW_END"

gh pr list -R vega113/incubator-wave --state merged --base main --limit 1000 \
  --json number,title,createdAt,mergedAt,closedAt,isDraft,baseRefName,headRefName,url \
  > /tmp/issue-590-main-merged.json

gh pr list -R vega113/incubator-wave --state merged --limit 1000 \
  --json number,title,createdAt,mergedAt,closedAt,isDraft,baseRefName,headRefName,url \
  > /tmp/issue-590-all-merged.json

gh pr list -R vega113/incubator-wave --state closed --base main --limit 1000 \
  --json number,title,createdAt,closedAt,mergedAt,isDraft,headRefName,url \
  > /tmp/issue-590-main-closed.json
```

Verify each export is JSON and has not hit the `--limit` while still inside
the fixed window:

```bash
node -e '
  const fs = require("fs");
  const ws = Date.parse(process.env.WINDOW_START);
  for (const [p, lim, key] of [
    ["/tmp/issue-590-main-merged.json", 1000, "mergedAt"],
    ["/tmp/issue-590-all-merged.json",  1000, "mergedAt"],
    ["/tmp/issue-590-main-closed.json", 1000, "closedAt"],
  ]) {
    const r = JSON.parse(fs.readFileSync(p, "utf8"));
    const oldest = Date.parse(r.at(-1)?.[key] ?? "");
    if (r.length === lim && Number.isFinite(oldest) && oldest >= ws) {
      throw new Error(p + " may be truncated for the fixed window; raise --limit");
    }
  }
  console.log("export limits cover the fixed window");
'
```

Pull the GraphQL slice for review-thread engagement and status-check rollup.
Save the query in a file to avoid shell-escape pitfalls:

```bash
cat > /tmp/issue-590-graphql.gql <<'GQL'
query($owner: String!, $repo: String!, $cursor: String) {
  repository(owner: $owner, name: $repo) {
    pullRequests(first: 100, after: $cursor, states: MERGED, baseRefName: "main",
                  orderBy: {field: UPDATED_AT, direction: DESC}) {
      pageInfo { hasNextPage endCursor }
      nodes {
        number createdAt mergedAt isDraft headRefName title
        author { login }
        reviews(first: 1) { totalCount }
        reviewThreads(first: 100) { totalCount nodes { isResolved } }
        commits(last: 1) { nodes { commit { statusCheckRollup { state } } } }
      }
    }
  }
}
GQL

node - <<'EOF' > /tmp/issue-590-review-threads.json
const { execFileSync } = require('child_process');
const fs = require('fs');
const query = fs.readFileSync('/tmp/issue-590-graphql.gql', 'utf8');
const stopBefore = process.env.WINDOW_START;
const all = [];
let cursor = null;
let pages = 0;
let stopReason = 'unknown';
while (true) {
  pages++;
  const args = ['api','graphql','-f',`query=${query}`,
                '-F','owner=vega113','-F','repo=incubator-wave'];
  if (cursor) args.push('-F', `cursor=${cursor}`);
  const out = execFileSync('gh', args, { encoding: 'utf8', maxBuffer: 64*1024*1024 });
  const conn = JSON.parse(out).data.repository.pullRequests;
  all.push(...conn.nodes);
  const oldest = conn.nodes.at(-1)?.mergedAt;
  process.stderr.write(`page ${pages}: oldest mergedAt=${oldest} total=${all.length}\n`);
  if (!conn.pageInfo.hasNextPage) { stopReason = 'no-more-pages'; break; }
  if (oldest && oldest < stopBefore) { stopReason = `oldest<${stopBefore}`; break; }
  cursor = conn.pageInfo.endCursor;
  if (pages > 30) { stopReason = 'safety-cap-30-pages'; break; }
}
process.stderr.write(`stopped after ${pages} pages (reason=${stopReason}); fetched ${all.length} PRs\n`);
console.log(JSON.stringify(all));
EOF
```

A PR that has more than 100 review threads will appear truncated under the
`reviewThreads(first: 100)` selection. The reduce step prints
`prs_with_threads_truncated_at_100`; if that count is non-zero, raise the
GraphQL `first` value before relying on the unresolved-threads metric.

## 4. Reduce to metrics

```bash
node - <<'EOF'
const fs = require('fs');
const ws = Date.parse(process.env.WINDOW_START);
const we = Date.parse(process.env.WINDOW_END);

const mainMerged = JSON.parse(fs.readFileSync('/tmp/issue-590-main-merged.json', 'utf8'));
const allMerged  = JSON.parse(fs.readFileSync('/tmp/issue-590-all-merged.json',  'utf8'));
const closedMain = JSON.parse(fs.readFileSync('/tmp/issue-590-main-closed.json', 'utf8'));
const gql        = JSON.parse(fs.readFileSync('/tmp/issue-590-review-threads.json', 'utf8'));

const isReleaseHotfix = (pr) =>
  pr.headRefName?.startsWith('release/') ||
  pr.headRefName?.startsWith('hotfix/')  ||
  pr.title?.startsWith('release:')       ||
  pr.title?.startsWith('hotfix:');

const inWindowRest = mainMerged.filter((pr) => {
  const m = Date.parse(pr.mergedAt ?? '');
  return Number.isFinite(m) && m >= ws && m < we && pr.isDraft === false && !isReleaseHotfix(pr);
});

const hours = inWindowRest
  .map((pr) => (Date.parse(pr.mergedAt) - Date.parse(pr.createdAt)) / 36e5)
  .sort((a, b) => a - b);

const quantile = (p) => {
  if (!hours.length) return null;
  const i = (hours.length - 1) * p;
  const lo = Math.floor(i), hi = Math.ceil(i), f = i - lo;
  return hours[lo] + ((hours[hi] ?? hours[lo]) - hours[lo]) * f;
};
const within = (lim) => hours.filter((v) => v <= lim).length;
const round2 = (v) => v == null ? null : Math.round(v * 100) / 100;

const inWindowGql = gql.filter((pr) => {
  const m = Date.parse(pr.mergedAt ?? '');
  return Number.isFinite(m) && m >= ws && m < we && pr.isDraft === false && !isReleaseHotfix(pr);
});

let withReviews = 0, withReviewThreads = 0, totalThreads = 0, unresolvedAtMerge = 0;
let maxThreadsPerPr = 0;
const rollup = {};
let truncated = 0;
for (const pr of inWindowGql) {
  if ((pr.reviews?.totalCount ?? 0) > 0) withReviews++;
  const threads = pr.reviewThreads?.nodes ?? [];
  const total   = pr.reviewThreads?.totalCount ?? 0;
  if (threads.length > 0) withReviewThreads++;
  totalThreads += threads.length;
  unresolvedAtMerge += threads.filter((t) => !t.isResolved).length;
  if (total > maxThreadsPerPr) maxThreadsPerPr = total;
  if (total > threads.length) truncated++;
  const state = pr.commits?.nodes?.[0]?.commit?.statusCheckRollup?.state ?? 'NONE';
  rollup[state] = (rollup[state] ?? 0) + 1;
}

const mergedInWindow = allMerged.filter((pr) => {
  const m = Date.parse(pr.mergedAt ?? '');
  return Number.isFinite(m) && m >= ws && m < we;
});
const nonMain = mergedInWindow.filter((pr) => pr.baseRefName !== 'main');
const releaseHotfixMain = mainMerged.filter((pr) => {
  const m = Date.parse(pr.mergedAt ?? '');
  return Number.isFinite(m) && m >= ws && m < we && isReleaseHotfix(pr);
});
const draftMain = mainMerged.filter((pr) => {
  const m = Date.parse(pr.mergedAt ?? '');
  return Number.isFinite(m) && m >= ws && m < we && pr.isDraft === true;
});
const closedMainInWindow = closedMain.filter((pr) => {
  const c = Date.parse(pr.closedAt ?? '');
  const m = Date.parse(pr.mergedAt ?? '');
  return Number.isFinite(c) && c >= ws && c < we && !Number.isFinite(m);
});

const gqlCount = inWindowGql.length;
if (gqlCount === 0) {
  process.stderr.write('WARNING: GraphQL pass returned 0 qualifying PRs — check pagination or window bounds\n');
}
const divGql = (n) => gqlCount > 0 ? round2(n / gqlCount) : null;

console.log(JSON.stringify({
  window_start: new Date(ws).toISOString(),
  window_end:   new Date(we).toISOString(),

  qualifying_merged_main_pr_count:    inWindowRest.length,
  qualifying_merged_main_prs_per_day: round2(inWindowRest.length / 30),

  p50_open_to_merge_hours: round2(quantile(0.5)),
  p90_open_to_merge_hours: round2(quantile(0.9)),
  p95_open_to_merge_hours: round2(quantile(0.95)),
  merged_within_1h:        within(1),
  merged_within_6h:        within(6),
  merged_within_24h:       within(24),
  max_open_to_merge_hours: round2(hours.at(-1) ?? null),

  pct_with_reviews:                        gqlCount > 0 ? round2(withReviews       / gqlCount * 100) : null,
  pct_with_review_threads:                 gqlCount > 0 ? round2(withReviewThreads / gqlCount * 100) : null,
  total_review_threads:                    totalThreads,
  threads_per_pr_mean:                     divGql(totalThreads),
  unresolved_threads_at_merge_total:       unresolvedAtMerge,
  unresolved_threads_per_pr_mean:          divGql(unresolvedAtMerge),
  max_review_threads_per_pr:               maxThreadsPerPr,
  prs_with_threads_truncated_at_100:       truncated,
  status_check_rollup_distribution:        rollup,

  total_merged_in_window:               mergedInWindow.length,
  excluded_non_main_merged_count:       nonMain.length,
  excluded_release_hotfix_main_count:   releaseHotfixMain.length,
  excluded_draft_main_count:            draftMain.length,
  excluded_closed_unmerged_main_count:  closedMainInWindow.length,
}, null, 2));
EOF
```

The REST and GraphQL passes count the qualifying set independently and the
report records both. They agreed on count for the 2026-05-08 window
(`298` PRs in each). A future report whose two counts diverge is a signal that
the GraphQL pagination stopped too early or that an edit changed `mergedAt`
between the two passes; rerun the snapshot before publishing.

## 5. Initial metric set and named sources

| Metric | Source |
| --- | --- |
| `merged_pr_count` / `merged_prs_per_day` | REST `gh pr list --state merged --base main` |
| `p50/p90/p95_open_to_merge_hours` | REST: `(mergedAt − createdAt)` over qualifying set |
| `merged_within_{1h,6h,24h}` / `max_open_to_merge_hours` | REST: same series |
| `pct_with_reviews` | GraphQL `pullRequest.reviews.totalCount` |
| `pct_with_review_threads`, `threads_per_pr_mean` | GraphQL `pullRequest.reviewThreads` |
| `unresolved_threads_at_merge_total` | GraphQL `reviewThreads.nodes.isResolved` |
| `status_check_rollup_distribution` | GraphQL `commits.last(1).commit.statusCheckRollup.state` |
| `excluded_non_main_merged_count` | REST `gh pr list --state merged` |
| `excluded_release_hotfix_main_count` | REST + branch/title matcher |
| `excluded_closed_unmerged_main_count` | REST `gh pr list --state closed --base main` |
| Direct-push deploy class | repo: `.github/workflows/build.yml`, `.github/workflows/deploy-contabo.yml` |
| Reviewer routing source (or absence) | repo: `.github/CODEOWNERS` |

`status_check_rollup_distribution` is recorded but is **not** used as a
trigger today. The rollup mixes required and advisory checks (codex review
gate, e2e, perf, doc guardrails, deploy attempts) and may include status
events committed after merge, so the surface number is noisy. A later phase
that filters down to the required-check subset can promote it to a trigger.

## 6. Policy-review threshold rule

Open a policy-review issue when a future 30-day `main` baseline meets the
gate (≥ 20 qualifying merged PRs) and any of the following holds:

- `p90_open_to_merge_hours` exceeds **2× the latest published baseline `p90`**
- `unresolved_threads_at_merge_total / qualifying` exceeds **3× the latest
  published baseline mean**

The 2× / 3× multipliers are conservative on purpose. The first baseline does
not propose a tighter rule because there is no prior window to compare
against. Subsequent reports update the rule to reference the previous
baseline's actual values, not the 2026-05-08 numbers, and explicitly cite
which prior report they compare against.

The threshold rule does not change merge policy on its own. A policy-review
issue is the unit of work that may then propose a change.

## 7. Limitations

- Baseline v1 uses `isDraft` at collection time, not historical draft-to-ready
  transitions. A PR that was a draft for most of its lifetime but flipped to
  ready before merge is included; the report records this caveat.
- `status_check_rollup_distribution` is informational only (see above).
- Direct-push deploy events are out of scope by definition; promoting them
  into the baseline requires a different data source (workflow runs API
  filtered to `push` events on `main`).
- The repo currently has no `.github/CODEOWNERS`, so reviewer-routing data is
  not yet baselineable. The runbook records the absence so a future addition
  is easy to surface.

## 8. Where to write the snapshot

After running steps 3-4, write the JSON output and the narrative findings into
a dated report file:

```text
docs/runbooks/<YYYY-MM-DD>-pr-main-baseline-report.md
```

Add the new file to `docs/DOC_REGISTRY.md`. Use the same metadata header
format as this runbook (`Status`, `Owner`, `Updated`, `Review cadence`).
Reference the report from the new file's "Previous baseline" section so the
threshold rule has an explicit prior-window number to compare against.
