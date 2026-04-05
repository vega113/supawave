# J2CL Phase 0 Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute the J2CL migration Phase 0 preparatory cleanup with no J2CL toolchain work and no intended runtime behavior changes, while proving which acceptance criteria are feasible on the current repo baseline.

**Architecture:** Keep the work as source cleanup plus bootstrap/module hygiene. Treat `sbt compile test` as the hard acceptance gate from the prompt. Because the untouched baseline currently also passes `sbt compileGwt`, rerun `compileGwt` after client/module-heavy tasks as a regression check so Phase 0 does not make the legacy GWT app worse, but do not redefine it as a new prompt-level acceptance criterion. For files that have both legacy and Jakarta runtime copies, update both where the prompt explicitly requires it, while remembering the Jakarta override is the runtime-active copy.

**Tech Stack:** Java 17, SBT, GWT 2.10, Jetty 12 Jakarta overrides, Guava, Apache Wave client/server split.

---

## Baseline Findings

- `sbt compile test` passes on the baseline (`827` tests passed).
- `sbt compileGwt` also passes on the baseline, so the stronger "GWT app still compiles" gate is currently achievable.
- Current `.gwt.xml` count is `139`, so Phase 0 must retire at least `9` modules net to meet the `<= 130` target.
- Gadget, htmltemplate, gadget-state supplement, gadget servlet, gadget flags, and deferred-binding surfaces named in the Phase 0 prompt all still exist on this branch.
- `wave/src/main/java/org/waveprotocol/wave/model/document/WaveContext.java` still imports `org.waveprotocol.wave.client.state.BlipReadStateMonitor`.
- `build.sbt` still includes `guava-gwt`, and the prompt's replacement scope reaches beyond `wave/client/**` into transitively compiled `wave/model/**`, `wave/communication/**`, and `wave/concurrencycontrol/**`.

## Scope

- Delete gadget/OpenSocial/htmltemplate source, resources, and tests listed in the Phase 0 prompt.
- Remove gadget bootstrap, flags, supplement APIs, server endpoints, and robot gadget-state handling.
- Move the `BlipReadStateMonitor` contract out of `wave/client/**`.
- Remove the targeted deferred-binding and `guava-gwt` dependencies.
- Reduce `.gwt.xml` modules to `130` or fewer.

## Non-Goals

- No J2CL sidecar, Maven scaffold, JsInterop, or Closure work.
- No new runtime feature flags.
- No attempt to redesign or replace gadgets with new product behavior.
- No unrelated refactors outside the Phase 0 cleanup seam.

## Task Sequence

### Task 1: Remove gadget and htmltemplate trees first

**Likely files/directories:**
- Delete `wave/src/main/java/org/waveprotocol/wave/client/gadget/**`
- Delete `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/gadget/**`
- Delete `wave/src/main/java/org/waveprotocol/wave/model/gadget/**`
- Delete `wave/src/main/resources/org/waveprotocol/wave/client/gadget/**`
- Delete `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/impl/toolbar/gadget/**`
- Delete `wave/src/test/java/org/waveprotocol/wave/client/gadget/**`
- Delete `wave/src/test/resources/org/waveprotocol/wave/client/gadget/**`
- Delete `wave/src/main/java/org/waveprotocol/wave/client/doodad/experimental/htmltemplate/**`
- Delete `wave/src/main/resources/org/waveprotocol/wave/client/doodad/experimental/htmltemplate/**`
- Delete prompt-listed leaf files such as `Gadget.gwt.xml` and `GadgetInfoProviderTest.java`

- [ ] Delete the prompt-listed gadget/htmltemplate directories and leaf files only.
- [ ] Run `sbt compile`.
- [ ] Recount modules with `find wave/src/main/resources wave/src/test -name "*.gwt.xml" | wc -l`.

### Task 2: Remove gadget coupling from client bootstrap and doodad wiring

**Likely files:**
- `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/suggestion/misc/GadgetCommand.java`
- `wave/src/main/java/org/waveprotocol/wave/client/doodad/suggestion/plugins/video/VideoLinkPlugin.java`
- `wave/src/main/resources/org/waveprotocol/wave/client/doodad/Doodad.gwt.xml`
- `wave/src/main/resources/org/waveprotocol/box/webclient/WebClient.gwt.xml`
- `wave/src/main/java/org/waveprotocol/wave/client/util/ClientFlagsBase.java`
- `wave/src/main/java/org/waveprotocol/wave/common/bootstrap/FlagConstants.java`
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`

- [ ] Remove `Gadget.install(...)`, gadget inherits, gadget-only suggestion/plugin classes, and gadget-only client flags/constants.
- [ ] Remove any dead gadget comments or toolbar references left after deletions.
- [ ] Verify with:
  - `sbt compile`
  - `rg -n 'gadget|Gadget' wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java wave/src/main/java/org/waveprotocol/wave/client/doodad/suggestion --glob '*.java'`

### Task 3: Delete gadget-state supplement APIs and align tests

**Likely files:**
- `wave/src/main/java/org/waveprotocol/wave/model/supplement/GadgetState.java`
- `wave/src/main/java/org/waveprotocol/wave/model/supplement/GadgetStateCollection.java`
- `wave/src/main/java/org/waveprotocol/wave/model/supplement/DocumentBasedGadgetState.java`
- `wave/src/main/java/org/waveprotocol/wave/model/supplement/{ReadableSupplement,WritableSupplement,PrimitiveSupplement,ObservablePrimitiveSupplement,ReadableSupplementedWave,WritableSupplementedWave,ObservableSupplementedWave,PrimitiveSupplementImpl,PartitioningPrimitiveSupplement,SupplementImpl,SupplementedWaveImpl,SupplementedWaveWrapper,LiveSupplementedWaveImpl,WaveletBasedSupplement}.java`
- `wave/src/main/java/org/waveprotocol/wave/model/schema/supplement/UserDataSchemas.java`
- `wave/src/main/java/org/waveprotocol/wave/model/image/ImageConstants.java`
- Gadget-specific supplement tests under `wave/src/test/java/org/waveprotocol/wave/model/supplement/**`

- [ ] Remove the gadget-state API in dependency order so interfaces shrink before implementations.
- [ ] Delete or rewrite gadget-state-specific tests that no longer make sense once the API is gone.
- [ ] Verify with:
  - `sbt compile test`
  - `rg -n 'GadgetState|gadgetState|GADGET' wave/src/main/java/org/waveprotocol/wave/model/supplement --glob '*.java'`

### Task 4: Remove server gadget endpoints and deprecate robot API gadget types in place

**Likely files:**
- `wave/src/main/java/org/waveprotocol/box/server/rpc/GadgetProviderServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/GadgetProviderServlet.java`
- `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/passive/EventGenerator.java`
- `wave/src/main/java/org/waveprotocol/box/server/robots/operations/DocumentModifyService.java`
- Prompt-listed `com/google/wave/api/**` gadget classes and methods
- `wave/src/main/java/com/google/wave/api/oauth/impl/PopupLoginFormHandler.java`
- `wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/GadgetProviderServletJakartaIT.java`
- `wave/src/test/java/org/waveprotocol/box/server/robots/operations/DocumentModifyServiceTest.java`

- [ ] Delete gadget servlet endpoints and proxy wiring from both `ServerMain` copies.
- [ ] Remove gadget event handling from robot server code and mark published robot API gadget types deprecated in place.
- [ ] Delete obsolete servlet/login/test files named in the prompt.
- [ ] Verify with:
  - `sbt compile test`
  - `rg -n 'gadgetlist|GadgetProvider|GadgetState' wave/src/main/java/org/waveprotocol/box/server --glob '*.java'`

### Task 5: Move `BlipReadStateMonitor` into model/shared code

**Likely files:**
- `wave/src/main/java/org/waveprotocol/wave/model/document/WaveContext.java`
- `wave/src/main/java/org/waveprotocol/wave/model/document/BlipReadStateMonitor.java` (new)
- `wave/src/main/java/org/waveprotocol/wave/client/state/BlipReadStateMonitor.java`
- `wave/src/main/java/org/waveprotocol/wave/client/state/BlipReadStateMonitorImpl.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/WaveBasedDigest.java`
- `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`

- [ ] Introduce the shared interface in `wave/model/document` and switch `WaveContext` to it.
- [ ] Keep a deprecated client-side re-export only if needed to avoid a large caller blast radius.
- [ ] Verify with:
  - `sbt compile`
  - `rg -n 'wave\\.client\\.state\\.BlipReadStateMonitor' wave/src/main/java/org/waveprotocol/wave/model/document/WaveContext.java`

### Task 6: Remove `guava-gwt` by replacing the narrow remaining client-facing usage

**Likely files:**
- `build.sbt`
- Files using `Preconditions`, `VisibleForTesting`, `Joiner`, `BiMap`, or `HashBiMap` in:
  - `wave/src/main/java/org/waveprotocol/wave/client/**`
  - `wave/src/main/java/org/waveprotocol/wave/model/**`
  - `wave/src/main/java/org/waveprotocol/wave/communication/**`
  - `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/**`

- [ ] Replace Guava `Preconditions` calls with `org.waveprotocol.wave.model.util.Preconditions`.
- [ ] Remove or replace `@VisibleForTesting` imports in the transitive compile surface.
- [ ] Replace `Joiner` and `BiMap`/`HashBiMap` usages with local/JDK equivalents.
- [ ] Remove `guava-gwt` from `build.sbt`.
- [ ] Verify with:
  - `sbt compile`
  - `sbt compileGwt`
  - `rg -n 'guava-gwt' build.sbt`
  - `rg -n 'com\\.google\\.common' wave/src/main/java/org/waveprotocol/wave/client wave/src/main/java/org/waveprotocol/wave/model wave/src/main/java/org/waveprotocol/wave/communication wave/src/main/java/org/waveprotocol/wave/concurrencycontrol --glob '*.java'`

### Task 7: Remove deferred binding and property-provider module branching

**Likely files:**
- `wave/src/main/resources/org/waveprotocol/wave/client/widget/popup/Popup.gwt.xml`
- `wave/src/main/java/org/waveprotocol/wave/client/widget/popup/**`
- `wave/src/main/resources/org/waveprotocol/wave/client/common/util/Util.gwt.xml`
- `wave/src/main/resources/org/waveprotocol/wave/client/common/util/useragents.gwt.xml`
- `wave/src/main/java/org/waveprotocol/wave/client/common/util/UserAgentStaticProperties*.java`
- `wave/src/main/resources/org/waveprotocol/wave/client/debug/logger/Logger.gwt.xml`
- `wave/src/main/java/org/waveprotocol/wave/client/debug/logger/LogLevel*.java`
- `wave/src/main/resources/org/waveprotocol/wave/client/editor/harness/EditorHarness.gwt.xml`
- `wave/src/main/resources/org/waveprotocol/wave/client/testing/UndercurrentHarness.gwt.xml`

- [ ] Replace `<replace-with>` and `property-provider` logic with explicit runtime detection/config.
- [ ] Flatten or simplify harness module properties where they no longer need permutation dispatch.
- [ ] Verify with:
  - `sbt compile`
  - `sbt compileGwt`
  - `rg -n 'replace-with|generate-with' wave/src/main/resources --glob '*.gwt.xml'`

### Task 8: Retire dead modules until the count reaches `130` or below

**Likely files:**
- Remaining `.gwt.xml` leaf modules under `wave/src/main/resources/**`
- Any prompt-driven test-only harness modules left after earlier deletions

- [ ] Recount `.gwt.xml` modules after tasks 1-7.
- [ ] Delete only modules that are provably unused or dead leaves.
- [ ] Verify with:
  - `find wave/src/main/resources wave/src/test -name "*.gwt.xml" | wc -l`
  - `sbt compile`
  - `sbt compileGwt`

## Final Verification

- [ ] Prompt-required gates:
  - `sbt compile test`
  - `find wave/src/main/resources wave/src/test -name "*.gwt.xml" | wc -l` (must be `130` or below)
- [ ] Additional regression checks because the untouched baseline is currently green:
  - `sbt compileGwt`
  - `rg -n 'gadgetlist|GadgetProvider|GadgetState' wave/src/main/java/org/waveprotocol/box/server --glob '*.java'`
  - `rg -n 'replace-with|generate-with' wave/src/main/resources --glob '*.gwt.xml'`
  - `rg -n 'guava-gwt' build.sbt`

## Pre-PR Local Server Sanity

- [ ] Start the app locally from the task worktree and verify the server still boots:
  - `WAVE_PORT=9899 sbt prepareServerConfig run >/tmp/j2cl-phase0-server.log 2>&1 &`
  - `SERVER_PID=$!`
  - `curl -sf http://localhost:9899/healthz`
  - `kill $SERVER_PID`
- [ ] Record the exact command/result in issue `#601` before opening the PR.

## Expected Risks / Suspected Blockers

- The `.gwt.xml` target is not close yet on the current baseline (`139`), so additional dead-leaf cleanup beyond the obvious gadget removals is likely required.
- Removing `guava-gwt` may force edits outside the headline `wave/client/**` paths because `VisibleForTesting` and other Guava imports still appear in transitively compiled `wave/model/**` and `wave/concurrencycontrol/**`.
- Supplement cleanup will require coordinated test cleanup because multiple current tests explicitly assert gadget-state behavior.
- Server cleanup must respect the Jakarta dual-source rule: where both legacy and Jakarta copies exist, the Jakarta copy is the runtime-active one and cannot be left behind.
