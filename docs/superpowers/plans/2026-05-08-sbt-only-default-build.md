# SBT-Only Default Build Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the normal app build path use SBT only, without transitively depending on the J2CL Maven wrapper.

**Architecture:** Keep the existing J2CL sidecar tasks available as explicit SBT entrypoints, because the current upstream J2CL toolchain is still exposed through Vertispan Maven plugin goals. Remove the sidecar runtime build from `sbt run`, `Universal/stage`, and `Universal/packageBin` so normal app boot/stage/package no longer invokes Maven unless an operator deliberately runs a J2CL task.

**Tech Stack:** SBT 1.10 build definition, sbt-native-packager, J2CL sidecar tasks under `build.sbt`, contract tests under `wave/src/test/java`, docs under `docs/`.

---

### Task 1: Pin the default-build contract before changing wiring

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/util/J2clBuildStageContractTest.java`

- [ ] **Step 1: Write the failing contract assertions**

Replace the current body of `testBuildSbtSerializesStageJ2clBuilds` so it asserts `j2clRuntimeBuild` remains explicit but is absent from normal run/stage/package wiring. Do not leave any old assertions that require `j2clRuntimeBuild` in `Compile / run`, `Universal / stage`, or `Universal / packageBin`.

```java
    String normalizedBuild = buildSbt.replaceAll("\\s+", " ");

    assertTrue(buildSbt.contains("lazy val j2clSearchBuild = taskKey[Unit]"));
    assertTrue(buildSbt.contains("lazy val j2clSearchTest = taskKey[Unit]"));
    assertTrue(buildSbt.contains("lazy val j2clLitBuild = taskKey[Unit]"));
    assertTrue(buildSbt.contains("lazy val j2clProductionBuild = taskKey[Unit]"));
    assertTrue(buildSbt.contains("lazy val j2clRuntimeBuild = taskKey[Unit]"));
    assertTrue(buildSbt.contains("ThisBuild / j2clRuntimeBuild := Def.sequential("));

    assertTrue(normalizedBuild.contains(
        "Compile / run := (Compile / run).dependsOn(prepareServerConfig, compileGwt).evaluated"));
    assertFalse(normalizedBuild.contains(
        "Compile / run := (Compile / run).dependsOn(prepareServerConfig, j2clRuntimeBuild, compileGwt).evaluated"));
    assertTrue(normalizedBuild.contains(
        "Universal / stage := (Universal / stage).dependsOn(compileGwt, verifyGwtAssets).value"));
    assertFalse(normalizedBuild.contains(
        "Universal / stage := (Universal / stage).dependsOn(j2clRuntimeBuild, compileGwt, verifyGwtAssets).value"));
    assertTrue(normalizedBuild.contains(
        "Universal / packageBin := (Universal / packageBin).dependsOn(compileGwt, verifyGwtAssets).value"));
    assertFalse(normalizedBuild.contains(
        "Universal / packageBin := (Universal / packageBin).dependsOn(j2clRuntimeBuild, compileGwt, verifyGwtAssets).value"));
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
sbt --batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"
```

Expected: the test fails because current `build.sbt` still wires `j2clRuntimeBuild` into run/stage/package.

### Task 2: Remove Maven-backed sidecar builds from the normal app path

**Files:**
- Modify: `build.sbt`

- [ ] **Step 1: Change only the default task dependencies**

Replace these wiring lines:

```scala
Compile / run := (Compile / run).dependsOn(prepareServerConfig, j2clRuntimeBuild, compileGwt).evaluated
Universal / stage := (Universal / stage).dependsOn(j2clRuntimeBuild, compileGwt, verifyGwtAssets).value
Universal / packageBin := (Universal / packageBin).dependsOn(j2clRuntimeBuild, compileGwt, verifyGwtAssets).value
```

with:

```scala
Compile / run := (Compile / run).dependsOn(prepareServerConfig, compileGwt).evaluated
Universal / stage := (Universal / stage).dependsOn(compileGwt, verifyGwtAssets).value
Universal / packageBin := (Universal / packageBin).dependsOn(compileGwt, verifyGwtAssets).value
```

Keep `j2clRuntimeBuild`, `j2clSearchBuild`, `j2clSearchTest`, `j2clProductionBuild`, and `j2clLitBuild` intact as explicit SBT tasks.

- [ ] **Step 2: Scan for other default-path Maven call sites**

Run:

```bash
rg -n "j2clRuntimeBuild|j2cl/mvnw|mvnw" \
  -g '!target/**' \
  -g '!**/target/**' \
  -g '!j2cl/mvnw' \
  -g '!j2cl/mvnw.cmd' \
  .
```

Expected: any remaining `j2clRuntimeBuild` references are task definitions, explicit J2CL docs, historical plan files, or explicit J2CL commands. No normal app build, deploy, Docker, CI, or stage workflow should call `j2cl/mvnw` or depend on `j2clRuntimeBuild`.

- [ ] **Step 3: Run the focused test and verify it passes**

Run:

```bash
sbt --batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"
```

Expected: the contract test passes.

### Task 3: Update operator docs for the new build boundary

**Files:**
- Modify: `docs/BUILDING-sbt.md`
- Modify: `docs/runbooks/j2cl-sidecar-testing.md`

- [ ] **Step 1: Update `docs/BUILDING-sbt.md`**

Document that normal `sbt run`, `Universal/stage`, and `Universal/packageBin` no longer build J2CL sidecar assets. Keep the explicit J2CL SBT commands listed for J2CL parity/debug work.

- [ ] **Step 2: Update `docs/runbooks/j2cl-sidecar-testing.md`**

Replace the direct-Maven guidance section with an SBT-only operator guidance section. Include the exact explicit task names:

```markdown
## SBT-Only Operator Boundary

Normal app boot, stage, and package commands do not build the J2CL sidecar:
`sbt run`, `sbt Universal/stage`, and `sbt Universal/packageBin` stay on
the GWT/default app path.

Run J2CL sidecar builds explicitly through SBT when the task touches J2CL
or when a local smoke check needs `/j2cl/**` or `/j2cl-search/**` assets:

```bash
sbt -batch j2clSearchBuild j2clSearchTest
sbt -batch j2clProductionBuild j2clLitBuild
```

Do not use `j2cl/mvnw` as routine verification evidence. The wrapper remains
an implementation detail of the current Vertispan sidecar toolchain until the
repo migrates J2CL itself to a non-Maven build path.
```

### Task 4: Verify and record evidence

**Files:**
- Create: `journal/local-verification/2026-05-08-issue-1209-sbt-only-default-build.md`

- [ ] **Step 1: Run focused verification**

Run:

```bash
git diff --check
sbt --batch "testOnly org.waveprotocol.box.server.util.J2clBuildStageContractTest"
sbt --batch "inspect tree Universal/stage" 2>&1 | tee target/issue-1209-inspect-stage.log
! grep -F "j2clRuntimeBuild" target/issue-1209-inspect-stage.log
sbt --batch Universal/stage
sbt --batch "show j2clSearchBuild" "show j2clSearchTest" "show j2clProductionBuild" "show j2clLitBuild"
sbt --batch j2clRuntimeBuild
```

Expected: all commands exit 0. The `inspect tree Universal/stage` output should not include `j2clRuntimeBuild`. The explicit `j2clRuntimeBuild` command should still work, proving the J2CL path remains available when deliberately requested.

- [ ] **Step 2: Record verification**

Write the exact command results in the journal file and mirror these outcomes into issue `#1209` and the PR body: contract test result, grep-backed absence of `j2clRuntimeBuild` from `Universal/stage`, `Universal/stage` result, explicit J2CL task availability result, and explicit `j2clRuntimeBuild` result.

## Plan Self-Review

- Spec coverage: covers investigation result, normal build decoupling, explicit J2CL task preservation, tests, docs, issue traceability, and PR verification.
- Scope control: does not attempt a full J2CL toolchain rewrite because the official J2CL build/test entrypoints currently used by this repo are Vertispan Maven plugin goals.
- Test gap: this plan uses a build-contract test plus real `Universal/stage`; it does not run the full J2CL sidecar suite because the narrow default-path change should not modify sidecar behavior.
