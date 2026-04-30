# Final J2CL/GWT Parity Acceptance Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a current, evidence-backed acceptance audit for J2CL/GWT parity after the concrete #904 child slices have closed, then reconcile the remaining umbrella trackers.

**Architecture:** Treat this as an audit and tracker-closeout lane, not as a feature implementation lane. Collect GitHub issue/PR evidence, verify the current `main` behavior with the existing SBT and Playwright parity harnesses, compare the J2CL and GWT roots on one local server session, and publish a repo markdown audit that either authorizes closing stale umbrellas or creates new concrete gap issues.

**Tech Stack:** GitHub CLI, Markdown docs under `docs/superpowers/`, SBT-only Java/J2CL verification, Playwright parity harness under `wave/src/e2e/j2cl-gwt-parity`, local staged server via `scripts/worktree-boot.sh` and `scripts/wave-smoke.sh`, optional browser screenshots, Claude review via `claude-review` when quota permits.

---

## File Map

- Create: `docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md`
  - Final audit artifact for issue #1159 and parent tracker #904.
- Create: `journal/local-verification/2026-04-30-issue-1159-final-parity-audit.md`
  - Exact local commands, results, server port, browser evidence paths, and any review blocker.
- Modify: `docs/superpowers/plans/2026-04-30-issue-1159-final-parity-audit.md`
  - Keep this plan checked as work proceeds.
- Modify if the audit supports it: `docs/j2cl-parity-issue-map.md`
  - Replace stale proposed/open wording with the final issue disposition summary.
- Modify if the audit supports it: `docs/j2cl-gwt-parity-matrix.md`
  - Add a short status note pointing readers to the final acceptance audit, without weakening parity criteria.
- Modify if the audit supports it: `docs/current-state.md`
  - Keep the repo status page aligned with the final J2CL/GWT tracker state.

## Task 1: Collect Live GitHub Tracker Evidence

**Files:**
- Create: `journal/local-verification/2026-04-30-issue-1159-final-parity-audit.md`
- Create: `docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md`

- [ ] **Step 1: Record the lane context**

Append this block to the local verification journal:

```markdown
# Issue #1159 Final J2CL/GWT Parity Audit Verification

Worktree: `/Users/vega/devroot/worktrees/issue-1159-final-parity-audit-20260430`
Branch: `codex/issue-1159-final-parity-audit-20260430`
Parent tracker: `#904`
Audit issue: `#1159`
Base branch: `origin/main`
Base commit: `2b2a083d1`
```

- [ ] **Step 2: Capture open tracker state**

Run:

```bash
gh issue list --state open --label j2cl-parity --json number,title,labels,updatedAt,url --limit 100 > /tmp/issue-1159-open-j2cl-parity.json
jq -r '.[] | "- #" + (.number|tostring) + " " + .title + " " + .url' /tmp/issue-1159-open-j2cl-parity.json
```

Expected: the list contains #904, #1078, #1098, #1109, and #1159 unless the remote state changed after this plan was written. Any additional open `j2cl-parity` issue must be classified in the audit as either an unblocked concrete gap or a stale tracker.

- [ ] **Step 3: Capture all closed concrete parity slices**

Run:

```bash
gh issue list --state closed --label j2cl-parity --json number,title,closedAt,url --limit 200 > /tmp/issue-1159-closed-j2cl-parity.json
jq -r 'sort_by(.number)[] | "- #" + (.number|tostring) + " " + .title + " closed " + .closedAt + " " + .url' /tmp/issue-1159-closed-j2cl-parity.json
```

Expected: output includes the closed #931/#933/#936/#961-#971 lineage, the F/J/V/G-PORT follow-up issues, #978, #1091, #1092, and the #1110-#1118 G-PORT slices.

- [ ] **Step 4: Map merged PR evidence for each concrete slice**

Run:

```bash
gh pr list --state merged --search 'label:j2cl-parity' --json number,title,mergedAt,mergeCommit,url --limit 200 > /tmp/issue-1159-merged-j2cl-prs.json
jq -r 'sort_by(.number)[] | "- PR #" + (.number|tostring) + " " + .title + " merged " + .mergedAt + " " + .mergeCommit.oid + " " + .url' /tmp/issue-1159-merged-j2cl-prs.json
```

This search is a convenience check only and may return an empty list if merged
PRs did not carry the `j2cl-parity` label. Treat issue closure references as
the primary source of truth. For each closed issue, resolve the closing PR with:

```bash
gh issue view <issue-number> --json closedByPullRequestsReferences --jq '.closedByPullRequestsReferences[] | [.number, .title, .url] | @tsv'
```

If `closedByPullRequestsReferences` is empty, fall back to timeline/source
inspection and record the exception explicitly in the audit table.

## Task 2: Run Static And Harness Verification

**Files:**
- Modify: `journal/local-verification/2026-04-30-issue-1159-final-parity-audit.md`
- Modify: `docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md`

- [ ] **Step 1: Verify the worktree is clean before tests**

Run:

```bash
git status --short --branch
```

Expected: only this plan and later audit/journal docs are modified.

- [ ] **Step 2: Run SBT compile and J2CL search gate**

Run:

```bash
sbt --batch compile j2clSearchTest
```

Expected: command exits `0`. Record the final success line and elapsed time in the journal.

- [ ] **Step 3: Type-check the parity Playwright harness**

Run:

```bash
cd wave/src/e2e/j2cl-gwt-parity
npm ci
npx tsc --noEmit
```

Expected: dependency install succeeds from `package-lock.json` and TypeScript exits `0`.

- [ ] **Step 4: Stage and start one local server for both roots**

Run from the repo root:

```bash
bash scripts/worktree-boot.sh --port 9959
PORT=9959 WAVE_SERVER_HOST=127.0.0.1 WAVE_SERVER_PORT=9959 bash scripts/wave-smoke.sh start
PORT=9959 bash scripts/wave-smoke.sh check
```

Expected: `wave-smoke.sh check` exits `0` and the local server is reachable at `http://127.0.0.1:9959/`.

- [ ] **Step 5: Run the existing J2CL/GWT parity E2E harness**

Run:

```bash
cd wave/src/e2e/j2cl-gwt-parity
WAVE_E2E_BASE_URL=http://127.0.0.1:9959 npx playwright test --project=chromium
```

Expected: all currently committed parity tests pass. If a test fails, capture the failing spec, failure message, artifact path, and whether it is a real parity gap or an environmental setup issue.

- [ ] **Step 6: Stop the local server after browser work completes**

Run from the repo root:

```bash
PORT=9959 bash scripts/wave-smoke.sh stop
```

Expected: server process stops cleanly.

## Task 3: Compare J2CL And GWT Daily Flows In Browser

**Files:**
- Modify: `journal/local-verification/2026-04-30-issue-1159-final-parity-audit.md`
- Modify: `docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md`

- [ ] **Step 1: Register or reuse a local test user**

Use the parity harness registration helper where possible. If manual browser registration is needed, create a local account scoped to this audit, for example:

```text
Email: issue1159-<timestamp>@example.test
Username: issue1159-<timestamp>
Password: generated local-only password recorded only in the browser session, not in the repo
```

Expected: the same browser session can open both `http://127.0.0.1:9959/?view=j2cl-root` and `http://127.0.0.1:9959/?view=gwt`.

- [ ] **Step 2: Verify both roots bootstrap**

Open:

```text
http://127.0.0.1:9959/?view=j2cl-root
http://127.0.0.1:9959/?view=gwt
```

Expected:
- J2CL root shows the Lit/J2CL shell marker and no GWT `webclient.nocache.js` bootstrap.
- GWT root shows the legacy GWT bootstrap and remains available for rollback.

- [ ] **Step 3: Compare the key daily flows**

Record PASS, PARTIAL, FAIL, or NOT TESTED for each row:

```markdown
| Flow | J2CL result | GWT result | Verdict | Evidence |
| --- | --- | --- | --- | --- |
| Search panel and inbox digest | | | | |
| Open selected wave and read first visible blips | | | | |
| Viewport-scoped rendering for a large wave | | | | |
| Keyboard navigation with `j`/`k` and focus frame | | | | |
| Inline reply and submit | | | | |
| Rich toolbar applies formatting to active composer selection | | | | |
| Mention autocomplete opens and accepts a participant | | | | |
| Task toggle and done state | | | | |
| Wave actions and top-level chrome | | | | |
| Profile/version/admin overlays | | | | |
| Attachments visible inline when test data includes attachments | | | | |
```

Expected: the audit doc contains an honest result for every flow. If local test data cannot exercise a row, mark `NOT TESTED`, explain the missing fixture, and decide whether that absence itself needs a new issue.

- [ ] **Step 4: Save browser evidence**

Save screenshots or trace artifact paths under:

```text
docs/superpowers/screenshots/issue-1159/
```

Expected: at least one J2CL root screenshot and one GWT root screenshot from the same local server run are referenced in the audit. If screenshots are generated by Playwright, keep the artifact paths rather than copying transient traces into the repo unless they are small and reviewable.

## Task 4: Publish The Final Audit Artifact

**Files:**
- Create: `docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md`
- Modify if supported: `docs/j2cl-parity-issue-map.md`
- Modify if supported: `docs/j2cl-gwt-parity-matrix.md`
- Modify if supported: `docs/current-state.md`

- [ ] **Step 1: Write the audit header and verdict**

Create `docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md` with:

```markdown
# J2CL/GWT Final Parity Acceptance Audit (2026-04-30)

Status: Draft for PR review
Audit issue: #1159
Parent tracker: #904
Audited branch: `codex/issue-1159-final-parity-audit-20260430`
Audited base: `origin/main` at `2b2a083d1`

## Verdict

<Use one of these exact verdicts after evidence is complete:
- "No remaining concrete parity implementation gaps were found; stale umbrellas can be closed with links to this audit."
- "Remaining parity gaps were found; new concrete agent-task issues are listed below and umbrellas must remain open until those issues close."
- "Audit inconclusive; required local verification could not complete for the reasons listed below.">
```

Before committing, replace the angle-bracket instruction with the selected verdict and evidence.

- [ ] **Step 2: Add the tracker inventory table**

Use this table shape:

```markdown
## Tracker Inventory

| Tracker | Current state | Audit disposition | Evidence |
| --- | --- | --- | --- |
| #904 | Open parent tracker | | |
| #1078 | Open umbrella | | |
| #1098 | Open umbrella | | |
| #1109 | Open umbrella | | |
| #1159 | Open audit lane | | |
```

Expected: every open tracker has a concrete close, keep-open, or rewrite recommendation.

- [ ] **Step 3: Add the child issue and PR evidence table**

Use this table shape:

```markdown
## Closed Slice Evidence

| Area | Issue | PR | Merge commit | Acceptance evidence | Audit result |
| --- | --- | --- | --- | --- | --- |
| Bootstrap/auth/live runtime | | | | | |
| Server-first and viewport fragments | | | | | |
| StageOne read surface | | | | | |
| StageThree compose/edit | | | | | |
| Visual polish and shell chrome | | | | | |
| G-PORT 1:1 parity slices | | | | | |
| Bootstrap JSON cleanup | #978 | #1158 | `2b2a083d1` | `/bootstrap.json` only, merged checks green | |
```

Expected: each row contains real issue and PR numbers, not generic ranges.

- [ ] **Step 4: Add verification evidence**

Include exact command outcomes from Tasks 2 and 3:

```markdown
## Verification

| Command or check | Result | Notes |
| --- | --- | --- |
| `sbt --batch compile j2clSearchTest` | | |
| `cd wave/src/e2e/j2cl-gwt-parity && npm ci && npx tsc --noEmit` | | |
| `PORT=9959 bash scripts/wave-smoke.sh check` | | |
| `WAVE_E2E_BASE_URL=http://127.0.0.1:9959 npx playwright test --project=chromium` | | |
| Browser: `/?view=j2cl-root` | | |
| Browser: `/?view=gwt` | | |
```

- [ ] **Step 5: Create new concrete issues for any remaining gaps**

For each remaining gap, run:

```bash
gh issue create \
  --title "<specific J2CL parity gap title>" \
  --label enhancement \
  --label agent-task \
  --label j2cl-parity \
  --body-file /tmp/issue-1159-gap-<slug>.md
```

The issue body must include:

```markdown
Parent: #904
Found by: #1159 final parity acceptance audit

## Gap
<one concrete behavior that is missing or regressed>

## Acceptance
- The same local account/session can demonstrate the behavior on `?view=j2cl-root` and `?view=gwt`.
- The Playwright parity harness gains or updates a spec that fails before the fix and passes after it.
- SBT-only Java/J2CL verification remains green.
```

Expected: no vague umbrella issue is created. Every gap has a clear user-visible behavior, an acceptance test, and a link back to #1159.

- [ ] **Step 6: Update repo status docs only if the audit supports it**

If no remaining gaps are found, update `docs/j2cl-parity-issue-map.md`, `docs/j2cl-gwt-parity-matrix.md`, and `docs/current-state.md` with a short pointer to the final audit and the closing disposition.

If gaps are found, keep the parity criteria intact and update only the issue map/current-state text needed to point at the new gap issues.

Expected: docs do not weaken the parity bar and do not claim completion if verification did not prove it.

## Task 5: Review, Issue Updates, PR, And Merge Monitoring

**Files:**
- Modify: `journal/local-verification/2026-04-30-issue-1159-final-parity-audit.md`
- All changed audit/status docs.

- [ ] **Step 1: Self-review the plan and audit artifact**

Check:

```bash
rg -n "TB[D]|TO[D]O|f[i]ll in|l[a]ter|<[U]se one of|<[s]pecific|<[o]ne concrete|\\| \\| \\|" docs/superpowers/plans/2026-04-30-issue-1159-final-parity-audit.md docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md journal/local-verification/2026-04-30-issue-1159-final-parity-audit.md
```

Expected after implementation: no unresolved placeholders remain in the final audit or journal. The plan may contain example issue-template angle brackets only until Task 4 executes.

- [ ] **Step 2: Attempt Claude Opus review**

Run the repo's Claude review helper with fallback disabled so the evidence is honest:

```bash
REVIEW_PLATFORM=claude \
REVIEW_MODEL=opus \
CLAUDE_REVIEW_LIMIT_FALLBACK_MODEL=off \
CLAUDE_REVIEW_LIMIT_SECONDARY_FALLBACK_MODEL=off \
REVIEW_TEMPLATE=task \
REVIEW_DIFF_MODE=worktree-all \
REVIEW_TASK="Issue #1159 final J2CL/GWT parity audit plan and docs" \
REVIEW_GOAL="Review the final parity acceptance audit lane for correctness, missing evidence, stale claims, and unsafe tracker-closure recommendations." \
REVIEW_ACCEPTANCE=$'- Audit must not declare parity without current verification\\n- Tracker disposition must be evidence-backed\\n- Any remaining gap must become a concrete agent-task issue\\n- Claude quota blockers must be recorded honestly' \
/Users/vega/.codex/skills/public/claude-review/scripts/review_task.sh
```

Expected: either Claude review returns no blocking comments, or the journal records the exact quota/error blocker. If Claude is quota-blocked, do not write "Claude approved" anywhere.

- [ ] **Step 3: Commit the plan after plan review evidence is recorded**

Run:

```bash
git add docs/superpowers/plans/2026-04-30-issue-1159-final-parity-audit.md
git commit -m "docs: plan final j2cl parity audit"
```

Expected: one plan commit exists before the audit implementation commit.

- [ ] **Step 4: Commit the audit and status docs**

Run:

```bash
git add docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md journal/local-verification/2026-04-30-issue-1159-final-parity-audit.md docs/j2cl-parity-issue-map.md docs/j2cl-gwt-parity-matrix.md docs/current-state.md
git commit -m "docs: audit final j2cl parity acceptance"
```

Expected: commit contains only the audit/journal/status docs that were actually changed.

- [ ] **Step 5: Update GitHub issues with evidence**

Post a #1159 comment containing:

```text
Plan: docs/superpowers/plans/2026-04-30-issue-1159-final-parity-audit.md
Audit: docs/superpowers/audits/2026-04-30-j2cl-gwt-final-parity-acceptance.md
Verification: <exact commands and results>
Claude review: <approved / blocked with exact error>
New gap issues: <issue list or none>
PR: <url after creation>
```

Post a #904 comment with the audit verdict and next tracker action. Do not close #904 or umbrellas until the PR has merged and the audit evidence supports closure.

- [ ] **Step 6: Open and monitor the PR**

Run:

```bash
git push -u origin codex/issue-1159-final-parity-audit-20260430
gh pr create --base main --head codex/issue-1159-final-parity-audit-20260430 --title "docs: audit final J2CL/GWT parity acceptance" --body-file /tmp/issue-1159-pr-body.md
```

Expected: PR links #1159 and #904, includes verification evidence, and stays open until checks and review threads are clean.

## Self-Review

Spec coverage:
- Issue #1159 requires a fresh worktree, plan, final audit doc, SBT-only Java/J2CL verification, parity E2E, browser evidence for both roots, Claude Opus attempt, PR, and tracker updates. Tasks 1-5 cover each requirement.
- The plan preserves the user requirement that GWT remain a comparison baseline and rollback path rather than being retired by assumption.
- The plan does not introduce Maven or any new dependency manager outside the existing Playwright harness `npm ci`.

Placeholder scan:
- The implementation-facing commands are concrete.
- The only angle-bracket examples appear in templates for future gap issues and audit verdict selection; Task 5 includes an explicit placeholder scan before commit.
- No task relies on cross-referenced duplicate instructions or unspecified tests.

Type and path consistency:
- All repo paths are under the #1159 worktree and use existing directories.
- The local server port is consistently `9959`.
- GitHub tracker references match the current issue set: #904, #1078, #1098, #1109, #1159, and #978/#1158.
