# Issue 828 Local Verification

- Worktree: `/Users/vega/devroot/worktrees/android-ime-corruption-v3-20260410`
- Branch: `android-ime-corruption-v3-20260410`
- Plan: `docs/superpowers/plans/2026-04-11-issue-828-android-ime-corruption.md`

## Executed

- `sbt "testOnly org.waveprotocol.wave.common.util.DelayedCompositionMutationGuardTest"`
  - Result: PASS
  - Notes:
    - initial red run failed with `cannot find symbol: DelayedCompositionMutationGuard`
    - final green rerun passed `3` tests after the helper was implemented and narrowed back to the delayed-composition boundary only

- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json`
  - Result: PASS
  - Output:
    - `assembled 130 entries -> wave/config/changelog.json`
    - `changelog validation passed`

## Blocked / Environment Limits

- `sbt "testOnly org.waveprotocol.wave.client.editor.event.EditorEventHandlerGwtTest"`
  - Result: no executable coverage
  - Output included:
    - `No tests to run for Test / testOnly`
  - Reason:
    - this repo snapshot excludes `GWTTestCase` suites from the runnable SBT test path (`build.sbt` filters out `*GwtTest` and `org/waveprotocol/wave/client/**` test sources)
    - the new editor-event regression was still added in source for future hosted/browser coverage, but it is not executable via the current local SBT path

- `sbt compileGwt`
  - Result: FAIL
  - Representative output:
    - `org.waveprotocol.box.webclient.client.StagesProvider cannot be resolved to a type`
    - `org.waveprotocol.wave.client.wavepanel.impl.reader.Reader cannot be resolved to a type`
    - `org.waveprotocol.wave.client.wave.InteractiveDocument cannot be resolved to a type`
    - `org.waveprotocol.wave.client.editor.content.ContentDocument cannot be resolved to a type`
    - `[compileGwt] GWT Compiler failed (exit 1)`
  - Reason:
    - the failure is broad and precludes producing a fresh local client bundle for browser verification
    - because the updated client bundle could not be built, browser verification of the literal empty-blip `new blip` flow is blocked in this environment

## Practical Verification Status

- Verified:
  - the new delayed-composition guard logic behaves correctly in an executable JVM regression
  - changelog fragment assembles and validates

- Not verified locally:
  - hosted GWT regression execution
  - browser verification against a freshly built client bundle

- Follow-up evidence needed after toolchain is available:
  1. run the source-level `EditorEventHandlerGwtTest` under a working hosted GWT path
  2. rebuild the client successfully with `compileGwt`
  3. run the exact empty-blip Android/browser flow and confirm `new blip` persists exactly
