# Issue #1027 J2CL DiskCache Noise Investigation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Determine whether the Vertispan `DiskCacheThread` `RejectedExecutionException` emitted by the SBT J2CL build sequence is repo-owned, upstream-benign, or upstream-actionable, then leave the J2CL parity verification path with clear SBT-only evidence.

**Architecture:** Keep SBT as the only operator-facing entrypoint. Treat the current `build.sbt` J2CL wrapper as the orchestration seam, the `j2cl/pom.xml` Vertispan plugin pin as the dependency seam, and `docs/runbooks/j2cl-sidecar-testing.md` plus `journal/local-verification/` as the evidence seams. Do not run direct Maven commands as verification; any Maven invocation must remain behind the existing SBT task wrappers.

**Tech Stack:** SBT 1.10, Scala build definition in `build.sbt`, Vertispan `j2cl-maven-plugin` `0.22.0`, Maven wrapper invoked only by SBT tasks, Lit npm tasks under `j2cl/lit/`, existing Java contract tests under `wave/src/test/java/org/waveprotocol/box/server/util/`.

---

## Scope

- Investigate issue: [#1027](https://github.com/vega113/supawave/issues/1027).
- Worktree: `/Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise`.
- Branch: `codex/issue-1027-j2cl-diskcache-noise`.
- Required reproduction command:

```bash
sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild
```

- Required final verification must use SBT-only commands from the repo root.
- Do not change product UI behavior for this issue.
- Do not replace SBT with direct Maven in documentation or operator instructions.

## File Ownership

- Modify: `docs/superpowers/plans/2026-04-25-issue-1027-j2cl-diskcache-noise.md`
  - This reviewed execution plan.
- Create: `journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md`
  - Reproduction attempts, command output summaries, classification, and final SBT-only verification.
- Modify if repo-owned orchestration is the fix: `build.sbt`
  - Keep `runJ2clWrapper(...)` as the only SBT-to-wrapper seam.
  - Preserve hard failure on non-zero wrapper exit.
- Modify if a dependency pin change is the fix: `j2cl/pom.xml`
  - Change only the Vertispan J2CL plugin/tooling version fields needed to remove the warning.
  - Preserve existing profiles and output directories.
- Modify if build contract needs coverage: `wave/src/test/java/org/waveprotocol/box/server/util/J2clBuildStageContractTest.java`
  - Add string-contract coverage for any changed SBT orchestration behavior.
- Modify if the warning is upstream-benign or still unavoidable: `docs/runbooks/j2cl-sidecar-testing.md`
  - Add a short known-warning section that says the warning is acceptable only when the SBT process exits `0` and all requested tasks complete.
- Create if repository files beyond the plan/journal change: `wave/config/changelog.d/2026-04-25-issue-1027-j2cl-diskcache-noise.json`
  - Use this when changing build behavior or operator-visible verification docs.

## Acceptance Criteria

- [ ] Clean-worktree reproduction is attempted with the exact issue command and recorded in the journal.
- [ ] Each J2CL subtask in the issue command is isolated enough to classify where the warning starts.
- [ ] Local source or dependency evidence explains why `DiskCache$PendingCacheResult.success(...)` can submit after shutdown, or proves the current repo cannot reproduce it.
- [ ] Plan review is already complete before implementation starts; rerun the plan review only after changing this plan.
- [ ] If repo-owned, a narrow fix removes the warning while preserving non-zero failure behavior.
- [ ] If upstream-benign or not locally reproducible, the runbook documents exactly when the warning can be ignored and when it is still a failure.
- [ ] Final evidence is mirrored into #1027 and #904.
- [ ] Claude Opus review is run on the plan before implementation and on the implementation before PR.

## Task 1: Baseline Reproduction And Isolation

**Files:**
- Create: `journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md`
- Read: `build.sbt`
- Read: `j2cl/pom.xml`

- [ ] **Step 1: Capture environment and clean git state**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
mkdir -p target/issue-1027
{
  git status --short --branch
  java -version
  sbt --version
  node --version
  npm --version
} 2>&1 | tee target/issue-1027/environment.log
```

Expected:

- Branch is `codex/issue-1027-j2cl-diskcache-noise`.
- No tracked code changes except this plan before implementation starts.
- Tool versions are recorded in the journal.

- [ ] **Step 2: Run the exact issue reproduction command**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
mkdir -p target/issue-1027
sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild 2>&1 | tee target/issue-1027/full-sequence.log
test "${PIPESTATUS[0]}" -eq 0
grep -n -A 8 -B 4 'DiskCacheThread\|RejectedExecutionException\|DiskCache\$PendingCacheResult' \
  target/issue-1027/full-sequence.log \
  | tee target/issue-1027/full-sequence-diskcache-excerpt.log || true
```

Expected:

- Exit is `0` if the issue remains "noise"; if this command exits non-zero, stop the #1027 noise classification, record `Classification: repo-owned` only if the failure is clearly caused by repo orchestration, otherwise open a separate build-failure issue and do not document the warning as benign.
- `target/issue-1027/full-sequence.log` records whether `Exception in thread "DiskCacheThread"` appears.
- `target/issue-1027/full-sequence-diskcache-excerpt.log` records the relevant stack context when the warning appears.
- The journal records the exact result and whether all four tasks completed.

- [ ] **Step 3: Isolate the warning by task**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
failed=0
for task in j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild; do
  sbt -batch "$task" 2>&1 | tee "target/issue-1027/${task}.log"
  status="${PIPESTATUS[0]}"
  echo "$task exit=$status" | tee -a target/issue-1027/task-status.txt
  if [ "$status" -ne 0 ]; then
    failed=1
  fi
done
test "$failed" -eq 0
```

Expected:

- The journal states which task log first contains `DiskCacheThread`.
- If only the full sequence reproduces the warning, the journal states that individual tasks did not reproduce it.

- [ ] **Step 4: Compare repeated warm-cache and clean-cache runs**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild 2>&1 | tee target/issue-1027/full-sequence-warm.log
test "${PIPESTATUS[0]}" -eq 0
rm -rf j2cl/target/gwt3BuildCache
rm -rf j2cl/target/j2cl-maven-plugin-local-cache
rm -rf j2cl/target
rm -rf war/j2cl war/j2cl-search war/j2cl-debug
sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild 2>&1 | tee target/issue-1027/full-sequence-clean.log
test "${PIPESTATUS[0]}" -eq 0
```

Expected:

- The journal records whether cache state affects the warning.
- The clean run removes the plugin's default project-local cache directories, `j2cl/target/gwt3BuildCache` and `j2cl/target/j2cl-maven-plugin-local-cache`, plus generated J2CL war output.
- The command still exits `0` if the warning is non-fatal.

## Task 2: Root-Cause Classification

**Files:**
- Read: `build.sbt`
- Read: `j2cl/pom.xml`
- Read if available: local Maven artifacts under `~/.m2/repository/com/vertispan/j2cl/`
- Modify: `journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md`

- [ ] **Step 1: Inspect the local Vertispan artifact source shape**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
find ~/.m2/repository/com/vertispan/j2cl -maxdepth 5 -type f \
  \( -name 'j2cl-maven-plugin-0.22.0.jar' -o -name 'build-caching-0.22.0.jar' -o -name '*sources.jar' \) \
  | sort | tee target/issue-1027/vertispan-artifacts.txt
```

Expected:

- The journal states whether source jars are available locally.
- If only class jars are available, decompile only the specific `DiskCache` class needed for diagnosis.

- [ ] **Step 2: Inspect current Maven metadata for Vertispan plugin versions**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
curl -fsS \
  -o target/issue-1027/j2cl-maven-plugin-metadata.xml \
  https://repo.maven.apache.org/maven2/com/vertispan/j2cl/j2cl-maven-plugin/maven-metadata.xml \
  || echo "Maven Central metadata fetch failed; proceeding with local pin evidence only." \
    | tee target/issue-1027/j2cl-maven-plugin-metadata-error.txt
test -s target/issue-1027/j2cl-maven-plugin-metadata.xml \
  && sed -n '1,80p' target/issue-1027/j2cl-maven-plugin-metadata.xml
```

Expected:

- The journal records the current Maven Central latest/release values and the existing repo pin.
- If the metadata fetch fails, the journal records the failure and classification proceeds from local plugin pin and local reproduction evidence.
- `dependency-owned actionable` is not allowed when the metadata fetch fails; without remote-version evidence, choose `repo-owned`, `upstream-benign`, or `not reproduced` based on local evidence.
- No dependency change is made just because a newer version exists; the version metadata is evidence for the decision.

- [ ] **Step 3: Classify ownership**

Record one of these decisions in the journal:

- `repo-owned`: SBT wrapper orchestration, stale output deletion, environment, or task sequencing causes the warning and can be fixed in this repo.
- `dependency-owned actionable`: a newer Vertispan plugin version removes the warning without broad migration risk and passes all targeted SBT checks.
- `upstream-benign`: the warning comes from Vertispan shutdown timing, exits `0`, all requested tasks complete, and no repo change can suppress it safely.
- `not reproduced`: clean and warm runs do not reproduce the warning; keep a runbook note only if the original issue evidence remains useful for future triage.

Write these exact parser lines in the journal before any issue comment or PR body command:

```text
Classification: upstream-benign
State-dependence: stable
```

Replace `upstream-benign` with exactly one of `repo-owned`, `dependency-owned actionable`, `upstream-benign`, or `not reproduced`. Replace `stable` with exactly one of `stable`, `warm-only`, `clean-only`, `flaky`, or `not reproduced`.

Run after writing the journal classification:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
classification="$(
  sed -n 's/^Classification: //p' \
    journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md \
    | tail -1
)"
test -n "$classification"
case "$classification" in
  repo-owned|dependency-owned\ actionable|upstream-benign|not\ reproduced) ;;
  *) echo "Unexpected classification: $classification" >&2; exit 1 ;;
esac
gh issue comment 1027 --repo vega113/supawave --body "$(cat <<EOF
#1027 classification checkpoint

Classification: $classification

Evidence so far:
- Full sequence log: \`target/issue-1027/full-sequence.log\`
- Warm sequence log: \`target/issue-1027/full-sequence-warm.log\`
- Clean sequence log: \`target/issue-1027/full-sequence-clean.log\`
- Journal: \`journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md\`
EOF
)"
```

Expected:

- Classification includes the command evidence and file/class evidence that supports it.
- The journal contains one line in the form `Classification: <repo-owned|dependency-owned actionable|upstream-benign|not reproduced>`.
- The journal also contains one line in the form `State-dependence: <stable|warm-only|clean-only|flaky|not reproduced>` so warm-vs-clean differences are explicit without adding a fifth classification.
- Classification is mirrored in a #1027 issue comment before implementation changes.

## Task 3A: Repo-Owned Or Dependency-Owned Fix

Use this task only if Task 2 classifies the problem as `repo-owned` or `dependency-owned actionable`.

**Files:**
- Modify: `build.sbt` if the fix is SBT orchestration.
- Modify: `j2cl/pom.xml` if the fix is a Vertispan plugin/tooling pin.
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/J2clBuildStageContractTest.java` if the fix changes build task sequencing or wrapper arguments.
- Create: `wave/config/changelog.d/2026-04-25-issue-1027-j2cl-diskcache-noise.json`
- Modify: `journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md`

- [ ] **Step 1: Write the narrow regression proof**

For SBT orchestration fixes, add a string-contract assertion to `J2clBuildStageContractTest` that checks the new wrapper behavior is present and the existing non-zero failure guard remains present.

For dependency pin fixes, record the before/after Vertispan version in the journal. Do not force a failing source test for a pin-only change; the regression proof is the before/after SBT sequence log plus the final full sequence.

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
sbt -batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"
```

Expected:

- The test fails before the SBT orchestration implementation when a contract assertion is added.
- The journal records the failing-first contract-test output when a contract assertion is added.
- The journal records whether a contract test was applicable.

- [ ] **Step 2: Implement the minimal fix**

Implementation constraints:

- Preserve `Process(cmd, base).!(...)` hard failure semantics or replace them with equivalent hard failure semantics.
- Preserve `j2clSearchTest`, `j2clProductionBuild`, `j2clLitTest`, and `j2clLitBuild` task names.
- Preserve `war/j2cl-search`, `war/j2cl-debug`, and `war/j2cl` output directories.
- Do not introduce direct Maven instructions outside SBT.

Expected:

- The original reproduction sequence no longer emits `DiskCacheThread` when the classification is repo-owned or dependency-owned actionable.
- All changed behavior is documented in the journal.

- [ ] **Step 3: Add changelog fragment**

Create `wave/config/changelog.d/2026-04-25-issue-1027-j2cl-diskcache-noise.json` with:

```json
{
  "releaseId": "2026-04-25-issue-1027-j2cl-diskcache-noise",
  "date": "2026-04-25",
  "version": "Issue #1027",
  "summary": "Clarifies or hardens the J2CL SBT build path so Vertispan DiskCache shutdown noise does not obscure successful parity verification.",
  "categories": [
    {
      "name": "Build and verification",
      "items": [
        "Kept J2CL verification behind SBT while documenting or removing the Vertispan DiskCacheThread shutdown warning seen during parity builds"
      ]
    }
  ]
}
```

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
git restore -- wave/config/changelog.json 2>/dev/null || true
```

Expected:

- Changelog validation passes.
- Only the fragment is staged for commit; `wave/config/changelog.json` is generated for validation and is not committed.

## Task 3B: Upstream-Benign Or Not-Reproduced Documentation

Use this task only if Task 2 classifies the problem as `upstream-benign` or `not reproduced`.

**Files:**
- Modify: `docs/runbooks/j2cl-sidecar-testing.md`
- Create: `wave/config/changelog.d/2026-04-25-issue-1027-j2cl-diskcache-noise.json`
- Modify: `journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md`

- [ ] **Step 1: Add a known-warning section to the runbook**

Add a section near the J2CL build commands:

````markdown
## Known Vertispan DiskCache Shutdown Warning

During chained SBT J2CL tasks, Vertispan `j2cl-maven-plugin` 0.22.0 may emit a background `DiskCacheThread` `RejectedExecutionException` after a successful task completes. Treat it as benign only when the SBT command exits `0`, every requested J2CL/Lit task reports success, and there is no preceding compile, test, Closure, npm, or wrapper failure. Treat it as a build failure if the SBT exit code is non-zero or any requested task is skipped or incomplete.

The canonical reproduction check is:

```bash
sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild
```
````

Expected:

- The runbook distinguishes acceptable shutdown noise from real build failure.
- The runbook keeps SBT as the verification entrypoint.

- [ ] **Step 2: Add changelog fragment**

Create `wave/config/changelog.d/2026-04-25-issue-1027-j2cl-diskcache-noise.json` with the same JSON from Task 3A Step 3.

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
git restore -- wave/config/changelog.json 2>/dev/null || true
```

Expected:

- Changelog validation passes.

## Task 4: Final Verification And Review

**Files:**
- Modify: `journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md`
- Read: changed files from Task 3A or Task 3B

- [ ] **Step 1: Run targeted final verification**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild 2>&1 | tee target/issue-1027/final-sequence.log
test "${PIPESTATUS[0]}" -eq 0
grep -n -A 8 -B 4 'DiskCacheThread\|RejectedExecutionException\|DiskCache\$PendingCacheResult' \
  target/issue-1027/final-sequence.log \
  | tee target/issue-1027/final-sequence-diskcache-excerpt.log || true
sbt -batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
git restore -- wave/config/changelog.json 2>/dev/null || true
git diff --check
```

Expected:

- Targeted SBT sequence exits `0`.
- `J2clBuildStageContractTest` passes even on the docs-only path, preserving the existing J2CL build contract guard.
- The journal states whether the final sequence emitted `DiskCacheThread`.
- Changelog validation and diff whitespace checks pass.

- [ ] **Step 2: Run Claude Opus implementation review**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
REVIEW_HELPER="${REVIEW_HELPER:-/Users/vega/.codex/skills/public/claude-review/scripts/review_task.sh}"
test -x "$REVIEW_HELPER"
REVIEW_TASK='Issue #1027 J2CL DiskCache noise implementation review' \
REVIEW_GOAL='Review the reproduction/classification evidence and any build or runbook changes for Vertispan DiskCacheThread warning handling.' \
REVIEW_ACCEPTANCE=$'- SBT remains the only verification entrypoint\n- The warning classification is evidence-backed\n- Real build failures are not normalized as benign\n- Changed files match #1027 scope\n- Final SBT sequence and contract guard passed' \
REVIEW_RUNTIME='SBT, Scala build.sbt, Vertispan j2cl-maven-plugin, J2CL/Lit build tasks' \
REVIEW_RISKY='Masking real J2CL build failures, committing direct Maven workflow, widening into product UI behavior, or documenting a warning without enough evidence.' \
REVIEW_TEST_COMMANDS='sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild; sbt -batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"; python3 scripts/assemble-changelog.py; python3 scripts/validate-changelog.py; git diff --check' \
REVIEW_TEST_RESULTS='Copy exact pass/fail summary from journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md' \
REVIEW_TEMPLATE='task' \
REVIEW_DIFF_SPEC='origin/main...HEAD' \
REVIEW_FALLBACK_DIFF_MODE='worktree-all' \
REVIEW_PLATFORM='claude' \
REVIEW_MODEL='opus' \
CLAUDE_REVIEW_TIMEOUT_SECONDS=900 \
CLAUDE_REVIEW_MAX_TOTAL_SECONDS=1200 \
"$REVIEW_HELPER"
```

Review scope:

- Issue: #1027.
- Diff: `origin/main...HEAD`.
- Acceptance: reproduction/classification evidence, SBT-only final verification, and either narrow fix or explicit runbook guidance.

Expected:

- Claude review reports no blockers or required follow-ups before PR creation.
- Any actionable comments are fixed and review is rerun.

- [ ] **Step 3: Author the PR body**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
classification="$(
  sed -n 's/^Classification: //p' \
    journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md \
    | tail -1
)"
test -n "$classification"
case "$classification" in
  '<'*|'') echo "Journal classification is missing or still placeholder text: $classification" >&2; exit 1 ;;
  repo-owned|dependency-owned\ actionable|upstream-benign|not\ reproduced) ;;
  *) echo "Unexpected classification: $classification" >&2; exit 1 ;;
esac
cat > /tmp/issue-1027-pr-body.md <<EOF
Closes #1027
Updates #904

Classification: $classification

Scope:
- Reproduces or disproves the Vertispan DiskCacheThread warning from the SBT J2CL task sequence.
- Keeps SBT as the only supported J2CL verification entrypoint.
- Documents or fixes the warning without changing product UI behavior.

Verification:
- sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild
- sbt -batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"
- python3 scripts/assemble-changelog.py
- python3 scripts/validate-changelog.py
- git diff --check

Evidence:
- journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md
- target/issue-1027/final-sequence.log
EOF
```

Expected:

- `/tmp/issue-1027-pr-body.md` exists before `gh pr create`.
- The classification comes from the journal, not from a hand-edited placeholder.

- [ ] **Step 4: Commit and open PR**

Run:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
git status --short
classification="$(
  sed -n 's/^Classification: //p' \
    journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md \
    | tail -1
)"
case "$classification" in
  repo-owned|dependency-owned\ actionable) commit_message='build(#1027): harden J2CL DiskCache build verification'; pr_title='Harden J2CL DiskCache build verification' ;;
  upstream-benign|not\ reproduced) commit_message='docs(#1027): classify J2CL DiskCache build noise'; pr_title='Classify J2CL DiskCache build noise' ;;
  *) echo "Unexpected classification: $classification" >&2; exit 1 ;;
esac
git add docs/superpowers/plans/2026-04-25-issue-1027-j2cl-diskcache-noise.md
git add journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md
git add wave/config/changelog.d/2026-04-25-issue-1027-j2cl-diskcache-noise.json
git diff --quiet -- docs/runbooks/j2cl-sidecar-testing.md || git add docs/runbooks/j2cl-sidecar-testing.md
git diff --quiet -- build.sbt || git add build.sbt
git diff --quiet -- j2cl/pom.xml || git add j2cl/pom.xml
git diff --quiet -- wave/src/test/java/org/waveprotocol/box/server/util/J2clBuildStageContractTest.java || git add wave/src/test/java/org/waveprotocol/box/server/util/J2clBuildStageContractTest.java
git diff --cached --name-only
git commit -m "$commit_message"
git push -u origin codex/issue-1027-j2cl-diskcache-noise
pr_url="$(
  gh pr create --repo vega113/supawave --base main --head codex/issue-1027-j2cl-diskcache-noise \
    --title "$pr_title" \
    --body-file /tmp/issue-1027-pr-body.md
)"
echo "$pr_url"
pr_number="${pr_url##*/}"
if ! gh pr merge "$pr_number" --repo vega113/supawave --auto --squash; then
  echo "Auto-merge enablement failed for PR #$pr_number; continue monitoring manually and record the failure in the issue journal." \
    | tee -a journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md
fi
```

Expected:

- Only files relevant to #1027 are staged.
- The PR body links #1027 and includes final verification commands/results.
- Auto-merge is enabled with squash merge after the PR is created, or the auto-merge failure is recorded as recoverable and PR monitoring continues manually.

## Task 5: Merge Monitoring

**Files:**
- No required file changes unless review comments require fixes.

- [ ] **Step 1: Monitor PR checks and review threads**

Run:

```bash
pr_number="$(gh pr view --repo vega113/supawave --json number --jq .number)"
gh pr view "$pr_number" --repo vega113/supawave --json number,mergeStateStatus,reviewDecision,statusCheckRollup,url
gh api graphql \
  -f owner=vega113 \
  -f name=supawave \
  -F number="$pr_number" \
  -f query='
query($owner:String!, $name:String!, $number:Int!) {
  repository(owner:$owner, name:$name) {
    pullRequest(number:$number) {
      reviewThreads(first:100) {
        nodes {
          id
          isResolved
          isOutdated
          path
          line
          comments(first:1) {
            nodes {
              author { login }
              body
              url
            }
          }
        }
      }
    }
  }
}' \
  | jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false)] | length'
```

Use GraphQL review-thread state to confirm unresolved review threads are `0`.
Repeat this monitor command until the PR is merged or a concrete failing check/review thread requires a fix.

Expected:

- Address actionable CodeRabbit/Codex/Copilot comments with commits.
- Do not resolve review threads without a fix or technical reply.
- Re-run targeted SBT verification after any code/doc change that can affect the build path.

- [ ] **Step 2: Close issue and update #904 after merge**

Run after the PR is merged:

```bash
cd /Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise
pr_number="$(gh pr view --repo vega113/supawave --json number --jq .number)"
merge_commit="$(gh pr view "$pr_number" --repo vega113/supawave --json mergeCommit --jq .mergeCommit.oid)"
classification="$(
  sed -n 's/^Classification: //p' \
    journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md \
    | tail -1
)"
test -n "$merge_commit"
test -n "$classification"
gh issue comment 1027 --repo vega113/supawave --body "$(cat <<EOF
#1027 complete via PR #$pr_number.

Classification: $classification
Merge commit: \`$merge_commit\`

Final evidence:
- Journal: \`journal/local-verification/2026-04-25-issue-1027-j2cl-diskcache-noise.md\`
- Verification: \`sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild\`
- Contract guard: \`sbt -batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"\`
- Review threads: \`0\` unresolved before merge
EOF
)"
gh issue close 1027 --repo vega113/supawave --comment "Closed after PR #$pr_number merged with classification: $classification."
gh issue comment 904 --repo vega113/supawave --body "$(cat <<EOF
J2CL parity pipeline update under #904:

#1027 is complete via PR #$pr_number.

Classification: $classification
Merge commit: \`$merge_commit\`

Final gate state:
- SBT J2CL sequence verified.
- J2CL build contract guard verified.
- Review threads resolved to \`0\` before merge.

Next lead action: select the next unblocked J2CL parity issue and continue in its own worktree with reviewed plan, SBT verification, Claude Opus implementation review, PR, and merge monitoring.
EOF
)"
```

Expected:

- #1027 issue comment includes final classification, commits, verification, review, PR URL, and merge commit.
- #904 issue comment records that #1027 is complete and names the next unblocked J2CL parity issue.
