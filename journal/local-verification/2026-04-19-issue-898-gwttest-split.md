# Local Verification

- Branch: issue-898-gwttest-split
- Worktree: /Users/vega/devroot/worktrees/issue-898-gwttest-split
- Date: 2026-04-19

## Commands

- `printf 'direct GWTTestCase java files: '; rg -l 'extends GWTTestCase' wave/src/test/java | wc -l`
- `printf 'all Java files mentioning GWTTestCase: '; rg -l 'GWTTestCase' wave/src/test/java | wc -l`
- `printf 'editor/test-base descendants: '; rg -n 'extends (EditorGwtTestCase|ContentTestBase|ElementTestBase|TestBase)' wave/src/test/java/org/waveprotocol/wave/client | wc -l`
- `rg -n 'com\.google\.gwt\.dom|com\.google\.gwt\.user|JavaScriptObject|native .*/\*' wave/src/test/java/org/waveprotocol/wave/client/common/util/FastQueueGwtTest.java wave/src/test/java/org/waveprotocol/wave/client/scheduler/DelayedJobRegistryTest.java wave/src/test/java/org/waveprotocol/wave/client/util/UrlParametersTest.java`
- `python3 scripts/assemble-changelog.py`
- `sbt -batch "wave / Test / testOnly org.waveprotocol.wave.client.scheduler.DelayedJobRegistryTest org.waveprotocol.wave.client.util.UrlParametersTest"`
- `sbt -batch compile`
- `sbt -batch Test/compile`
- `bash scripts/worktree-boot.sh --port 9900`
- `PORT=9900 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/issue-898-gwttest-split/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/issue-898-gwttest-split/wave/config/jaas.config' bash scripts/wave-smoke.sh start`
- `PORT=9900 bash scripts/wave-smoke.sh check`
- `PORT=9900 bash scripts/wave-smoke.sh stop`

## Results

- Inventory before conversion was reconciled to `21` direct `GWTTestCase` Java files.
- Current inventory after the lane is `19` direct `GWTTestCase` Java files and `11` inherited editor/test-base descendants.
- Scope guard command returned no matches for DOM/widget/JSNI/native-browser patterns in `FastQueueGwtTest`, `DelayedJobRegistryTest`, or `UrlParametersTest`.
- `python3 scripts/assemble-changelog.py` succeeded: `assembled 196 entries -> wave/config/changelog.json`.
- `sbt -batch "wave / Test / testOnly org.waveprotocol.wave.client.scheduler.DelayedJobRegistryTest org.waveprotocol.wave.client.util.UrlParametersTest"` passed with `8` tests run, `0` failed.
- `sbt -batch compile` succeeded.
- `sbt -batch Test/compile` succeeded.
- `bash scripts/worktree-boot.sh --port 9900` succeeded and produced the staged runtime config plus the standard smoke command sequence.
- `PORT=9900 bash scripts/wave-smoke.sh check` returned `ROOT_STATUS=200`, `HEALTH_STATUS=200`, and `WEBCLIENT_STATUS=200`.
- Browser verification was not required by `docs/runbooks/change-type-verification-matrix.md` because the lane changed JVM test coverage and documentation, not browser-visible packaging behavior; the staged asset check above confirmed the compiled web client still served correctly.

## Follow-up

- PR: #914
- Issue: #898

## PR #914 Remediation

### Commands

- `sbt "testOnly org.waveprotocol.wave.client.util.UrlParametersTest"`
- `sbt compile`
- `sbt test`

### Results

- Added focused regression coverage for JVM query encoding parity, UTF-8 round-trips, and malformed UTF-8 rejection in `UrlParametersTest`.
- `sbt "testOnly org.waveprotocol.wave.client.util.UrlParametersTest"` initially failed on three review-driven regressions:
  - `testBuildQueryStringPreservesEncodeComponentSafeCharacters`
  - `testTruncatedUtf8SequenceRejected`
  - `testInvalidUtf8ContinuationRejected`
- After fixing `UrlParameters`, the focused test suite passed with `11` tests run, `0` failed.
- `sbt compile` succeeded.
- `sbt test` succeeded with `2498` total tests, `0` failed, `0` errors, and `2` skipped.
