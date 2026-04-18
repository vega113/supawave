# Issue #900 J2CL Sidecar Build Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the first isolated J2CL sidecar build and SBT entrypoints in the existing SBT-first SupaWave repo, while keeping all legacy runtime traffic on the current GWT web client.

**Architecture:** Add a dedicated `j2cl/` Maven sidecar project driven by `com.vertispan.j2cl:j2cl-maven-plugin`, with explicit `BUNDLE_JAR`, `BUNDLE`, and `ADVANCED_OPTIMIZATIONS` build modes and outputs isolated under `war/j2cl*`. SBT remains the top-level orchestrator, but the new tasks stay opt-in: do not wire J2CL into `Compile / run`, `compileGwt`, or the runtime bootstrap path for issue `#900`.

**Tech Stack:** SBT 1.10, Maven wrapper, `j2cl-maven-plugin`, existing `war/` static-asset staging, worktree boot/smoke scripts, browser verification runbooks.

---

## Goal / Root Cause
SupaWave is still pre-sidecar for J2CL: the repo has no `j2cl/` subtree, no Maven wrapper-backed J2CL build, and no isolated output path that can be built and staged without disturbing `war/webclient/**`. The narrow fix for `#900` is to create that build scaffold now, so later issues can migrate pure logic, transport, and a first UI slice onto a real sidecar instead of planning against a hypothetical toolchain.

## Scope and Non-Goals
- Scope: add the isolated `j2cl/` Maven project and wrapper; add one tiny sandbox entrypoint plus a minimal host page and static asset; configure explicit sidecar/debug/production plugin profiles with sourcemaps; add reproducible SBT tasks that invoke the wrapper; keep emitted assets under `war/j2cl-search/**`, `war/j2cl-debug/**`, and `war/j2cl/**`; verify the existing staged app still boots and serves the legacy client.
- Non-goals: no login/bootstrap cutover; no feature-flag wiring; no transport rewrite; no search presenter or UI migration; no changes to `Compile / run`, `compileGwt`, or `Universal / stage` dependencies that would make J2CL mandatory; no edits to smoke/browser runbook scripts unless the sidecar cannot be verified with the existing flow.

## Exact Files Expected to Be Added or Modified
Tracked source files expected in this issue:
- `build.sbt`
- `j2cl/pom.xml`
- `j2cl/mvnw`
- `j2cl/mvnw.cmd`
- `j2cl/.mvn/wrapper/maven-wrapper.properties`
- `j2cl/.mvn/wrapper/maven-wrapper.jar`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxBuildSmokeTest.java`
- `j2cl/src/main/webapp/index.html`
- `j2cl/src/main/webapp/assets/sidecar.css`
- `wave/config/changelog.d/2026-04-18-j2cl-sidecar-build.json`
- `wave/config/changelog.json`

Generated outputs expected during verification but not committed:
- `war/j2cl-search/**`
- `war/j2cl-debug/**`
- `war/j2cl/**`

Files that should remain untouched in `#900`:
- runtime bootstrap/login shell files that choose the main app bundle
- legacy GWT client source beyond whatever is already required for `Universal / stage`
- transport, websocket, search, or migrated J2CL feature code

## Concrete Task Breakdown
### Task 1: Add the isolated Maven sidecar scaffold
- [ ] Create `j2cl/pom.xml` with `com.vertispan.j2cl:j2cl-maven-plugin` as the build seam and keep the project self-contained under `j2cl/`.
- [ ] Add `j2cl/mvnw`, `j2cl/mvnw.cmd`, and `j2cl/.mvn/wrapper/**` so the sidecar build is reproducible from the worktree and CI without relying on a preinstalled Maven.
- [ ] Keep the sidecar inputs and outputs local to `j2cl/` plus `war/j2cl*`; do not add Maven assumptions to the root server compile graph.

### Task 2: Add the first sandbox entrypoint and isolated host page
- [ ] Add a tiny sandbox entrypoint at `j2cl/src/main/java/org/waveprotocol/box/j2cl/sandbox/SandboxEntryPoint.java`; it should prove the J2CL toolchain produces a usable browser bundle without pulling runtime traffic off the legacy client.
- [ ] Add a minimal smoke-style J2CL test under `j2cl/src/test/java/org/waveprotocol/box/j2cl/sandbox/SandboxBuildSmokeTest.java` so the new `*Test` SBT tasks have a real target.
- [ ] Add `j2cl/src/main/webapp/index.html` plus a small static asset such as `j2cl/src/main/webapp/assets/sidecar.css` so the emitted bundle has a stable isolated host page.
- [ ] Keep the host page obviously separate from the main app shell and point it at the generated sidecar script only; do not route `/` or the existing signin/bootstrap flow to J2CL.

### Task 3: Configure explicit plugin profiles and isolated output layout
- [ ] In `j2cl/pom.xml`, define explicit build configurations that match the strategic plan:
- [ ] `search-sidecar` (or `sandbox`, if that is the chosen profile id): `compilationLevel=BUNDLE_JAR`, `enableSourcemaps=true`, output under `war/j2cl-search/**`.
- [ ] `debug-single-project`: `compilationLevel=BUNDLE`, `enableSourcemaps=true`, output under `war/j2cl-debug/**`.
- [ ] `production`: `compilationLevel=ADVANCED_OPTIMIZATIONS`, `enableSourcemaps=true`, output under `war/j2cl/**`.
- [ ] Keep Closure defaults on `env=BROWSER`; do not invent manual browser extern management in this issue.
- [ ] Ensure all three profiles write to isolated directories and do not overwrite `war/webclient/**`.

### Task 4: Add reproducible SBT entrypoints without cutover
- [ ] In `build.sbt`, add opt-in task keys for the sidecar flow:
- [ ] `j2clSandboxBuild`
- [ ] `j2clSandboxTest`
- [ ] `j2clSearchBuild`
- [ ] `j2clSearchTest`
- [ ] Implement those tasks by invoking `j2cl/mvnw -f j2cl/pom.xml ...` from the repo root and failing hard on any non-zero exit code.
- [ ] Keep `Compile / run`, `compileGwt`, `smokeInstalled`, `smokeUi`, and `Universal / stage` behavior unchanged except for any cleanup needed to avoid stale `war/j2cl*` artifacts.
- [ ] Extend `cleanFiles` for `war/j2cl-search`, `war/j2cl-debug`, and `war/j2cl` so stale sidecar outputs do not survive between builds.

### Task 5: Add repo-required release notes and record traceability
- [ ] Add a changelog fragment at `wave/config/changelog.d/2026-04-18-j2cl-sidecar-build.json` because this issue adds new browser-reachable staged assets and build entrypoints.
- [ ] Regenerate `wave/config/changelog.json` with `scripts/assemble-changelog.py`; do not hand-edit it.
- [ ] Record the worktree path, branch, plan path, exact verification commands, and outcomes in issue `#900` before opening the PR.

## Exact Verification Commands
Run these from `/Users/vega/devroot/worktrees/issue-900-j2cl-sidecar-build`.

Direct Maven-wrapper verification:

```bash
./j2cl/mvnw -f j2cl/pom.xml -Psearch-sidecar -q test
./j2cl/mvnw -f j2cl/pom.xml -Pdebug-single-project -q package
./j2cl/mvnw -f j2cl/pom.xml -Pproduction -q package
```

SBT entrypoint verification:

```bash
sbt "j2clSandboxBuild" "j2clSandboxTest" "j2clSearchBuild" "j2clSearchTest"
```

Existing repo gates that must still pass:

```bash
sbt compile test
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --changelog wave/config/changelog.json
sbt Universal/stage
```

Isolated output checks:

```bash
find war/j2cl-search -maxdepth 2 -type f | sort
find war/j2cl-debug -maxdepth 2 -type f | sort
find war/j2cl -maxdepth 2 -type f | sort
test -f war/webclient/webclient.nocache.js
```

Expected outcomes:
- the direct `mvnw` commands succeed for sidecar, debug, and production profiles
- the SBT tasks succeed and fail hard if the wrapper build fails
- `sbt compile test` still passes
- `sbt Universal/stage` still succeeds on the legacy GWT path
- `war/webclient/webclient.nocache.js` still exists
- J2CL outputs exist only under `war/j2cl-search/**`, `war/j2cl-debug/**`, and `war/j2cl/**`

## Required Local Verification Before PR Creation
Because this is a packaging/build/distribution change, use the worktree boot flow plus a narrow browser pass on a non-conflicting port.

```bash
bash scripts/worktree-boot.sh --port 9900
PORT=9900 JAVA_OPTS='...' bash scripts/wave-smoke.sh start
PORT=9900 bash scripts/wave-smoke.sh check
curl -sS -I http://localhost:9900/webclient/webclient.nocache.js
curl -sS -I http://localhost:9900/j2cl-search/index.html
curl -sS -I http://localhost:9900/j2cl/index.html
PORT=9900 bash scripts/wave-smoke.sh stop
```

If port `9900` is busy, rerun `worktree-boot.sh` on the next free port and record the actual port used.

Required browser sanity after the staged server is up:
- open `http://localhost:<port>/` and confirm the legacy signin/bootstrap flow still serves the existing GWT app, not the sidecar
- open `http://localhost:<port>/j2cl-search/index.html` and confirm the isolated sidecar host page loads without taking over the main shell
- open `http://localhost:<port>/j2cl/index.html` and confirm the production-profile host page or emitted asset path is present and isolated

Record all of the following in `journal/local-verification/<date>-issue-900-j2cl-sidecar-build.md` and mirror the important results into the issue comment:
- the exact port used
- the printed `wave-smoke.sh start|check|stop` commands
- whether the browser-verification matrix required a browser pass (`Packaging/build/distribution`: yes)
- the URLs checked
- the observed result that the legacy root flow stayed intact while the sidecar pages loaded independently

## Acceptance Criteria
- A new `j2cl/` subtree exists with `pom.xml`, Maven wrapper files, one tiny sandbox entrypoint, one smoke-style test, and a minimal host page/assets.
- `j2cl-maven-plugin` is configured with explicit `BUNDLE_JAR`, `BUNDLE`, and `ADVANCED_OPTIMIZATIONS` profiles, each with sourcemaps and isolated output directories.
- `build.sbt` exposes reproducible `j2clSandbox*` and `j2clSearch*` tasks that invoke the Maven wrapper directly and do not pollute the root server classpath.
- Legacy `war/webclient/**` output and the current `/` bootstrap path remain the runtime source of truth.
- The staged app still boots locally, still serves `/webclient/webclient.nocache.js`, and also serves the isolated J2CL sidecar host pages under `j2cl*`.
- Changelog fragment creation, changelog assembly/validation, compile/tests, stage, and local browser verification are completed and recorded.
- No transport rewrite, no search/UI migration, and no runtime cutover land in this issue.

## Issue / PR Traceability Notes
- Use issue `#900` as the live execution log.
- Record these exact identifiers in the issue comments:
- Worktree: `/Users/vega/devroot/worktrees/issue-900-j2cl-sidecar-build`
- Branch: `issue-900-j2cl-sidecar-build`
- Plan: `docs/superpowers/plans/2026-04-18-issue-900-j2cl-sidecar-build.md`
- Include commit SHAs with one-line summaries, the full verification command list above, and the local browser sanity result.
- The PR body should stay concise and link back to `#900`; it should explicitly say that the legacy GWT runtime remains active and the J2CL outputs are sidecar-only scaffolding.
