# Issue #1027 J2CL DiskCache Noise Verification Journal

Worktree: `/Users/vega/devroot/worktrees/issue-1027-j2cl-diskcache-noise`
Branch: `codex/issue-1027-j2cl-diskcache-noise`
Plan: `docs/superpowers/plans/2026-04-25-issue-1027-j2cl-diskcache-noise.md`
Plan commit: `26503e796`

## Plan Review

- Self-review: passed placeholder scan and `git diff --check`.
- Claude Opus plan review: round 4 `pass`; no blockers, no important concerns, no required follow-ups.

## Classification

Classification: upstream-benign
State-dependence: flaky

## Environment

Command:

```bash
mkdir -p target/issue-1027 && { git status --short --branch; java -version; sbt --version; node --version; npm --version; } 2>&1 | tee target/issue-1027/environment.log
```

Result: passed.

Key output:

```text
## codex/issue-1027-j2cl-diskcache-noise...origin/main [ahead 1]
openjdk version "17.0.12" 2024-07-16 LTS
sbt version in this project: 1.10.2
sbt runner version: 1.12.8
node v25.8.0
npm 11.11.0
```

## Reproduction Evidence

Command:

```bash
bash -lc 'set -o pipefail; sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild 2>&1 | tee target/issue-1027/full-sequence.log; status=${PIPESTATUS[0]}; grep -n -A 8 -B 4 "DiskCacheThread\|RejectedExecutionException\|DiskCache\$PendingCacheResult" target/issue-1027/full-sequence.log | tee target/issue-1027/full-sequence-diskcache-excerpt.log || true; exit $status'
```

Result: passed, exit `0`.

Evidence:

- `target/issue-1027/full-sequence.log`: 98 lines.
- `target/issue-1027/full-sequence-diskcache-excerpt.log`: 0 lines.
- `rg "DiskCacheThread|RejectedExecutionException|DiskCache\\$PendingCacheResult" target/issue-1027/full-sequence.log`: no matches.

Observed task completion:

```text
[success] Total time: 3 s, completed 25 Apr 2026, 22:11:11
[success] Total time: 20 s, completed 25 Apr 2026, 22:11:31
[success] Total time: 4 s, completed 25 Apr 2026, 22:11:34
[success] Total time: 0 s, completed 25 Apr 2026, 22:11:35
```

Notes:

- `j2clLitBuild` prints esbuild output on stderr, so SBT prefixes the bundle-size lines with `[error]`, but the task ended with `[success]`.
- The issue warning did not reproduce in this first full-sequence run.

## Isolation Evidence

Command:

```bash
bash -lc 'set -o pipefail; failed=0; : > target/issue-1027/task-status.txt; for task in j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild; do sbt -batch "$task" 2>&1 | tee "target/issue-1027/${task}.log"; status=${PIPESTATUS[0]}; echo "$task exit=$status" | tee -a target/issue-1027/task-status.txt; if [ "$status" -ne 0 ]; then failed=1; fi; grep -n -A 8 -B 4 "DiskCacheThread\|RejectedExecutionException\|DiskCache\$PendingCacheResult" "target/issue-1027/${task}.log" > "target/issue-1027/${task}-diskcache-excerpt.log" || true; done; exit $failed'
```

Result: passed, exit `0`.

Per-task status:

```text
j2clSearchTest exit=0
j2clProductionBuild exit=0
j2clLitTest exit=0
j2clLitBuild exit=0
```

DiskCache excerpt logs:

```text
target/issue-1027/j2clSearchTest-diskcache-excerpt.log: 0 lines
target/issue-1027/j2clProductionBuild-diskcache-excerpt.log: 0 lines
target/issue-1027/j2clLitTest-diskcache-excerpt.log: 0 lines
target/issue-1027/j2clLitBuild-diskcache-excerpt.log: 0 lines
```

Conclusion: individual task isolation did not reproduce the warning.

## Cache-State Evidence

Command:

```bash
bash -lc 'set -o pipefail; sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild 2>&1 | tee target/issue-1027/full-sequence-warm.log; warm_status=${PIPESTATUS[0]}; grep -n -A 8 -B 4 "DiskCacheThread\|RejectedExecutionException\|DiskCache\$PendingCacheResult" target/issue-1027/full-sequence-warm.log | tee target/issue-1027/full-sequence-warm-diskcache-excerpt.log || true; test "$warm_status" -eq 0; rm -rf j2cl/target/gwt3BuildCache j2cl/target/j2cl-maven-plugin-local-cache j2cl/target war/j2cl war/j2cl-search war/j2cl-debug; sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild 2>&1 | tee target/issue-1027/full-sequence-clean.log; clean_status=${PIPESTATUS[0]}; grep -n -A 8 -B 4 "DiskCacheThread\|RejectedExecutionException\|DiskCache\$PendingCacheResult" target/issue-1027/full-sequence-clean.log | tee target/issue-1027/full-sequence-clean-diskcache-excerpt.log || true; exit "$clean_status"'
```

Result: passed, exit `0`.

Warm-cache result:

- `target/issue-1027/full-sequence-warm.log`: 93 lines.
- `target/issue-1027/full-sequence-warm-diskcache-excerpt.log`: 0 lines.
- All four tasks completed with `[success]`.

Clean-cache result:

- Removed `j2cl/target/gwt3BuildCache`, `j2cl/target/j2cl-maven-plugin-local-cache`, `j2cl/target`, `war/j2cl`, `war/j2cl-search`, and `war/j2cl-debug` before the clean run.
- `target/issue-1027/full-sequence-clean.log`: 92 lines.
- `target/issue-1027/full-sequence-clean-diskcache-excerpt.log`: 0 lines.
- All four tasks completed with `[success]`.

Conclusion: neither warm nor project-local clean cache state reproduced the warning.

## Root-Cause Evidence

Commands:

```bash
find ~/.m2/repository/com/vertispan/j2cl -maxdepth 5 -type f \( -name 'j2cl-maven-plugin-0.22.0.jar' -o -name 'build-caching-0.22.0.jar' -o -name '*sources.jar' \) | sort | tee target/issue-1027/vertispan-artifacts.txt
curl -fsS -o target/issue-1027/j2cl-maven-plugin-metadata.xml https://repo.maven.apache.org/maven2/com/vertispan/j2cl/j2cl-maven-plugin/maven-metadata.xml
unzip -p ~/.m2/repository/com/vertispan/j2cl/j2cl-maven-plugin/0.22.0/j2cl-maven-plugin-0.22.0.jar META-INF/maven/com.vertispan.j2cl/j2cl-maven-plugin/plugin-help.xml | rg -n -C 2 'gwt3BuildCacheDir|localBuildCache|default-value="\$\{project.build.directory\}/(gwt3BuildCache|j2cl-maven-plugin-local-cache)' | tee target/issue-1027/vertispan-cache-parameters.txt
javap -classpath ~/.m2/repository/com/vertispan/j2cl/build-caching/0.22.0/build-caching-0.22.0.jar -c -p com.vertispan.j2cl.build.DiskCache > target/issue-1027/DiskCache.javap.txt
javap -classpath ~/.m2/repository/com/vertispan/j2cl/build-caching/0.22.0/build-caching-0.22.0.jar -c -p 'com.vertispan.j2cl.build.DiskCache$PendingCacheResult' | tee target/issue-1027/DiskCache-PendingCacheResult.javap.txt
```

Result: evidence collected.

Findings:

- Local Vertispan artifacts available: `build-caching-0.22.0.jar`, `j2cl-maven-plugin-0.22.0.jar`, and unrelated `junit-emul-v20230718-1-sources.jar`; no local source jar for `build-caching` was present.
- Maven Central metadata reports `j2cl-maven-plugin` latest/release as `0.22.0`, matching the repo pin in `j2cl/pom.xml`.
- The plugin help declares default cache locations as `${project.build.directory}/gwt3BuildCache` and `${project.build.directory}/j2cl-maven-plugin-local-cache`, which are under `j2cl/target` for this repo's J2CL Maven sidecar.
- `DiskCache` creates a thread named `DiskCacheThread` and keeps an executor reference.
- `DiskCache$PendingCacheResult.success()` removes the pending result and then calls `executor.execute(...)` to notify `listener.onSuccess(...)`. The originally observed `RejectedExecutionException` shape is consistent with a success notification racing an executor shutdown.
- The final full sequence reproduced a related `DiskCacheThread` shutdown warning as `ClosedWatchServiceException`, after `j2clSearchTest` completed and before `j2clProductionBuild` reported success. That confirms the issue is a flaky Vertispan shutdown-noise family rather than a deterministic repo orchestration failure.

Decision:

- No repo-owned fix was found because every SBT sequence exited `0`, all requested tasks completed, and the reproduced warning came from Vertispan `DiskCacheThread` shutdown after a successful J2CL task.
- No dependency-owned action is available because the repo already uses the latest Maven Central `j2cl-maven-plugin` release (`0.22.0` as of the metadata fetched on 2026-04-25).
- I will add runbook guidance so future PR verification treats this warning as acceptable only when SBT exits `0` and every requested task completes.

## Final Verification

Commands:

```bash
bash -lc 'set -o pipefail; sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild 2>&1 | tee target/issue-1027/final-sequence.log; status=${PIPESTATUS[0]}; grep -n -A 8 -B 4 "DiskCacheThread\|RejectedExecutionException\|DiskCache\$PendingCacheResult" target/issue-1027/final-sequence.log | tee target/issue-1027/final-sequence-diskcache-excerpt.log || true; exit $status'
python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py
sbt -batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"
git diff --check
```

Results:

- `sbt -batch j2clSearchTest j2clProductionBuild j2clLitTest j2clLitBuild`: passed, exit `0`.
- Final sequence task completions: all four requested tasks reported `[success]`.
- Final sequence DiskCache excerpt: `target/issue-1027/final-sequence-diskcache-excerpt.log`, 13 lines.
- Warning shape reproduced in final sequence:

```text
[error] Exception in thread "DiskCacheThread" java.lang.Error: java.nio.file.ClosedWatchServiceException
[error] 	at com.vertispan.j2cl.build.DiskCache.checkForWork(DiskCache.java:175)
[error] Caused by: java.nio.file.ClosedWatchServiceException
[error] 	at io.methvin.watchservice.AbstractWatchService.check(AbstractWatchService.java:94)
[error] 	at io.methvin.watchservice.AbstractWatchService.take(AbstractWatchService.java:86)
[error] 	at io.methvin.watchservice.MacOSXListeningWatchService.take(MacOSXListeningWatchService.java:38)
[error] 	at com.vertispan.j2cl.build.DiskCache.checkForWork(DiskCache.java:131)
```

- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py`: passed after changing the fragment to the repo's `title`/`sections` format.
- First `sbt -batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"` attempt failed because `wave/config/changelog.json` was missing. This is the known repo precondition for SBT tests in fresh worktrees; assembling the changelog fixed it.
- `sbt -batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"` after changelog assembly: passed, 3 tests.
- `git diff --check`: passed.
