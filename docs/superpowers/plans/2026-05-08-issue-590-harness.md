# Harness Phase 4 PR-to-`main` Metrics Baseline Implementation Plan

> **For agentic workers:** Doc-only deliverable. Steps use `- [ ]` syntax. Use superpowers:executing-plans for inline execution.

**Goal:** Deliver a reproducible 30-day throughput baseline for completed non-draft PRs targeting `main`, plus the first data-backed policy-review threshold for later merge-flow or guardrail changes (issue [#590](https://github.com/vega113/incubator-wave/issues/590)).

**Architecture:** Documentation only. Two new docs under `docs/runbooks/`: a methodology runbook the team can re-run, and a snapshot report containing the 2026-04-08 → 2026-05-08 numbers. No code, no workflows, no metrics store. Sources: GitHub REST `pulls` and `issues`, GraphQL for review threads + status check rollup, repo workflow files for the direct-push exclusion class.

**Tech Stack:** `gh` CLI, `node`, GraphQL, repo workflow files under `.github/workflows/`.

---

## Files

- Create: `docs/runbooks/pr-main-metrics-baseline.md` — methodology, scope, filters, queries to reproduce
- Create: `docs/runbooks/2026-05-08-pr-main-baseline-report.md` — the 2026-04-08 → 2026-05-08 snapshot results
- Modify: `docs/runbooks/README.md` — add both docs to the "CI Guardrails" / new "Harness" section index
- Modify: `docs/DOC_REGISTRY.md` — register both new docs so the freshness guardrail covers them

## Window

Fixed UTC window `[2026-04-08T00:00:00Z, 2026-05-08T00:00:00Z)`. Anchored to today's lane start (2026-05-08), 30-day lookback. Re-runs against a different window must use a different report file; the methodology runbook stays window-agnostic.

## Inclusion / Exclusion Filters

- **Include:** `gh pr list --state merged --base main` rows whose `mergedAt` falls inside the window and `isDraft === false`.
- **Exclude (and count):**
  - `baseRefName !== "main"` — caught by the broader `gh pr list --state merged` export. 0 in this window.
  - `headRefName` matches `release/**` or `hotfix/**`, or `title` starts with `release:` / `hotfix:`. 0 in this window — repo does not currently use those branch prefixes.
  - direct-push deploy events. Justified by `.github/workflows/deploy-contabo.yml` (push to `main`) and `.github/workflows/build.yml` (push). These are workflow events, not PR rows, so they cannot appear in PR exports. The exclusion is documented for completeness.
  - closed-but-unmerged PRs targeting `main`. Counted but excluded from throughput. 4 in this window.

## Initial Metric Set (each with named source)

| Metric | Source |
| --- | --- |
| `merged_pr_count` | REST `gh pr list --state merged --base main` filtered to window + `isDraft === false` |
| `merged_prs_per_day` | derived: `merged_pr_count / 30` |
| `p50/p90/p95_open_to_merge_hours` | REST: `(mergedAt − createdAt)` over qualifying set |
| `merged_within_{1h,6h,24h}` | REST: bucket counts from same series |
| `max_open_to_merge_hours` | REST: max of same series |
| `pct_with_reviews` | GraphQL `pullRequest.reviews.totalCount > 0` |
| `pct_with_review_threads` | GraphQL `pullRequest.reviewThreads` |
| `unresolved_threads_at_merge_total` | GraphQL `reviewThreads.nodes.isResolved === false` |
| `status_check_rollup_distribution` | GraphQL `pullRequest.commits.last(1).commit.statusCheckRollup.state` |
| `excluded_non_main_merged_count` | REST `gh pr list --state merged` minus base-main rows |
| `excluded_release_hotfix_main_count` | REST + branch/title matcher |
| `excluded_closed_unmerged_main_count` | REST `gh pr list --state closed --base main` filtered to window with `mergedAt == null` |
| Direct-push deploy class | repo files: `.github/workflows/build.yml`, `.github/workflows/deploy-contabo.yml` |
| Reviewer routing source (or absence) | repo: `.github/CODEOWNERS` (absent at snapshot — recorded as a finding) |

## Policy-Review Threshold (proposal)

Trigger condition for opening a future policy-review issue:

> A future 30-day `main` baseline that meets the gate (≥ 20 qualifying merged PRs) and has either of:
>
> - `p90_open_to_merge_hours` exceeds **3.62 h** (= 2× the 2026-05-08 baseline `p90` of 1.81 h), or
> - `unresolved_threads_at_merge_total / qualifying` exceeds **0.39** (= 3× the 2026-05-08 mean of 0.13).

Both metrics are conservative: `p90` is the queue-pressure tail-latency signal, and the unresolved-threads ratio is the review-quality signal. Either alone is enough to open a policy-review issue.

The status-check-rollup distribution is **observed but not used as a trigger** in baseline v1, because the 2026-05-08 snapshot shows `FAILURE` for 285 / 298 (95.6%) of qualifying PRs. That ratio is dominated by non-required advisory checks and post-merge status updates, so it is unreliable as a trigger today. The runbook records this as a known limitation and defers any threshold to a later phase that first carves out required vs. advisory checks.

## Steps

### Phase 1 — Snapshot the data

- [ ] **Step 1:** Export REST PR data into `/tmp/issue-590-*.json`. Record commands and counts in the report.
  ```bash
  export WINDOW_END="2026-05-08T00:00:00Z"
  export WINDOW_START="2026-04-08T00:00:00Z"
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

- [ ] **Step 2:** Verify each export is JSON and not truncated.
  ```bash
  node -e '
    const fs=require("fs"), ws=Date.parse(process.env.WINDOW_START);
    for (const [p,lim,key] of [
      ["/tmp/issue-590-main-merged.json",1000,"mergedAt"],
      ["/tmp/issue-590-all-merged.json",1000,"mergedAt"],
      ["/tmp/issue-590-main-closed.json",1000,"closedAt"],
    ]) {
      const r=JSON.parse(fs.readFileSync(p,"utf8"));
      const oldest=Date.parse(r.at(-1)?.[key]??"");
      if (r.length===lim && Number.isFinite(oldest) && oldest>=ws)
        throw new Error(`${p} may be truncated; raise --limit`);
    }
    console.log("ok");
  '
  ```

- [ ] **Step 3:** Pull review-thread + status-rollup data via GraphQL pagination, stopping when oldest `mergedAt` falls before `WINDOW_START`.
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
  const query = fs.readFileSync('/tmp/issue-590-graphql.gql','utf8');
  const stopBefore = process.env.WINDOW_START;
  const all = [];
  let cursor = null, pages = 0;
  while (true) {
    pages++;
    const args=['api','graphql','-f',`query=${query}`,'-F','owner=vega113','-F','repo=incubator-wave'];
    if (cursor) args.push('-F',`cursor=${cursor}`);
    const out=execFileSync('gh',args,{encoding:'utf8',maxBuffer:64*1024*1024});
    const conn=JSON.parse(out).data.repository.pullRequests;
    all.push(...conn.nodes);
    const oldest=conn.nodes.at(-1)?.mergedAt;
    if (!conn.pageInfo.hasNextPage) break;
    if (oldest && oldest < stopBefore) break;
    cursor=conn.pageInfo.endCursor;
    if (pages>30) break;
  }
  console.log(JSON.stringify(all));
  EOF
  ```

### Phase 2 — Compute and record metrics

- [ ] **Step 4:** Run the reduce script (in the runbook) and capture the JSON output. Paste numbers into the snapshot report table.
- [ ] **Step 5:** Record exclusion counts and the CODEOWNERS / direct-push notes in the report.

### Phase 3 — Author docs

- [ ] **Step 6:** Write `docs/runbooks/pr-main-metrics-baseline.md` (window-agnostic). Sections: Status header, Purpose, Inclusion/Exclusion, Metric Definitions and Sources, Reproducibility (full export + reduce script), Threshold Rule, Limitations.
- [ ] **Step 7:** Write `docs/runbooks/2026-05-08-pr-main-baseline-report.md`. Sections: Status header, Window, Filters Applied, Metrics Table, Exclusion Counts, Findings, Threshold Trigger Values for this window.
- [ ] **Step 8:** Add both files to `docs/DOC_REGISTRY.md` under "Covered docs".
- [ ] **Step 9:** Add a "Harness" section to `docs/runbooks/README.md` linking both files.

### Phase 4 — Verify

- [ ] **Step 10:** Run guardrails locally; both must exit 0.
  ```bash
  bash scripts/check-doc-links.sh
  bash scripts/check-doc-freshness.sh
  ```
- [ ] **Step 11:** Re-run the export + reduce flow and diff the JSON output against the report numbers. They must match exactly.
- [ ] **Step 12:** Copilot review (`copilot -p '<diff prompt>' --model gpt-5.4 --effort high --silent`). Address findings.

### Phase 5 — Ship

- [ ] **Step 13:** Commit (`Co-Authored-By: Claude Opus 4.7 …`), push, `gh pr create` with `Closes #590`.
- [ ] **Step 14:** Watch CI. Fix any guardrail failure with a follow-up commit. Done when CI is green.

## Out of Scope

- Any change to `.github/workflows/`, `.github/scripts/`, or product code
- Any feature flag, changelog entry, or policy-rule change in this PR
- Any persistent metrics store, scheduled exporter, or new dashboard
- Any expansion to stacked PRs / non-`main` bases
