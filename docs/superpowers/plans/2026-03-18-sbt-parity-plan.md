# SBT Parity Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the additive SBT port back into line with the current Gradle-backed server build by fixing the broken SBT bootstrap/compile path and rewriting the SBT docs to describe the behavior that actually exists.

**Architecture:** Treat this as a build-and-doc parity task, not a product feature task. First make the SBT entry points (`compile`, `prepareServerConfig`, artifact naming, default mode) reflect the current repository layout and the Jakarta-first server baseline. Then update the SBT docs and top-level references so they describe the verified commands rather than historical Phase 1 assumptions.

**Tech Stack:** SBT 1.10, Java 17, sbt-protoc, vendored `third_party` jars, Jakarta/Jetty 12 server baseline, repo docs.

---

## Findings Summary

Current mismatches between Gradle reality and the SBT surface:

- `sbt compile` is currently broken.
  - Verified failure: `google/protobuf/descriptor.proto: File not found` during `Compile / protocGenerate`.
  - The SBT doc currently presents `sbt compile` as a working primary path.

- `sbt prepareServerConfig` is currently broken.
  - Verified failure: `Missing server.config.example; cannot bootstrap config`.
  - The doc still presents this as the first-time setup path.

- SBT runtime defaults point at files that do not exist at repo root.
  - `Compile / javaOptions` uses `server.config`, `wiab-logging.conf`, and `jaas.config` under the checkout root.
  - The real checked-in files are under `wave/config/`.

- The SBT default mode is still `jakartaMode=false`, while Gradle and the repo docs treat Jakarta/Jetty 12 as the default runtime.
  - Verified via `sbt -batch 'show jakartaMode'` -> `false`.

- The SBT assembly artifact name is not stable across worktrees.
  - Verified via `sbt -batch 'show name' 'show assembly / assemblyJarName'` -> `sbt-parity-server-0.1.0-SNAPSHOT.jar` in this worktree.
  - `build.sbt` never pins `name := "incubator-wave"`, so the artifact name depends on the checkout directory.

- `docs/BUILDING-sbt.md` overstates codegen automation.
  - It says SBT "now performs basic code generation before compiling".
  - In `build.sbt`, `compile` still has `generatePstMessages`, `generateFlags`, and `generateGxp` dependencies commented out.
  - Only the protobuf staging/protoc path is wired automatically, and that path is currently failing.

- The doc still frames the SBT port as a viable server build path without clearly separating verified behavior from legacy/offline fallback behavior.
  - `testBackend` still depends on Ant and is explicitly legacy.
  - The doc should stop implying parity that the current build does not actually provide.

## Recommended Direction

- Do not try to make SBT a second canonical build.
- Make it an honest additive server-only path that:
  - compiles reliably,
  - boots with checked-in config locations,
  - uses stable artifact naming,
  - matches the current Jakarta-first baseline when verification proves it safe,
  - and documents any remaining gaps explicitly.

---

### Task 1: Fix SBT Bootstrap Paths To Match The Current Repo Layout

**Files:**
- Modify: `build.sbt`
- Possibly create: `server.config.example`
- Possibly create or modify: `docs/BUILDING-sbt.md`

- [ ] **Step 1: Decide whether to keep root-level `server.config` generation or switch SBT to the existing `wave/config/*` files**
- [ ] **Step 1a: Record the chosen config-path direction in the task notes before editing files**
- [ ] **Step 2: Prefer the narrower parity path**
  - Keep SBT aligned with the checked-in config layout under `wave/config/` if that avoids inventing new root-level config files.
- [ ] **Step 3: Update `Compile / javaOptions` so runtime defaults point at real checked-in files**
  - Remove the broken assumptions around root-level `wiab-logging.conf` and `jaas.config`.
- [ ] **Step 4: Either repair `prepareServerConfig` or explicitly retire it from the documented happy path**
- [ ] **Step 5: Verify the bootstrap command chosen for the docs actually works**

**Verification:**
- `sbt -batch 'show Compile / javaOptions'`
- `sbt -batch prepareServerConfig` if the task is retained
- If `prepareServerConfig` is removed from the happy path, verify the replacement command sequence instead

**Commit:**
```bash
git add build.sbt docs/BUILDING-sbt.md
git commit -m "Align SBT bootstrap paths with repo layout"
```

### Task 2: Repair The Protobuf Compile Path

**Files:**
- Modify: `build.sbt`
- Possibly modify: `docs/BUILDING-sbt.md`

- [ ] **Step 1: Write down the failing reproduction in the task notes**
  - `sbt -batch compile`
  - Expected current failure: missing `google/protobuf/descriptor.proto`
- [ ] **Step 2: Fix the protobuf staging/include path so `descriptor.proto` is available to `protoc`**
- [ ] **Step 3: Keep the fix scoped to the staging/include configuration**
  - Do not rewrite unrelated codegen tasks in the same change.
- [ ] **Step 4: Re-run `sbt -batch compile` and verify protobuf generation succeeds**
- [ ] **Step 5: Record whether compile now reaches Java compilation or still stops at the next build gap**

**Verification:**
- `sbt -batch clean compile`

**Commit:**
```bash
git add build.sbt docs/BUILDING-sbt.md
git commit -m "Fix SBT protobuf staging"
```

### Task 3: Make The SBT Identity And Default Mode Match The Current Baseline

**Files:**
- Modify: `build.sbt`
- Modify: `docs/BUILDING-sbt.md`
- Modify: `README.md`
- Modify: `docs/current-state.md`

- [ ] **Step 1: Pin the SBT project name explicitly**
  - Set `ThisBuild / name := "incubator-wave"` or equivalent so assembly output is stable across worktrees.
- [ ] **Step 1a: Verify which scope controls `assembly / assemblyJarName` before changing `name`**
- [ ] **Step 2: Evaluate whether SBT can safely default to Jakarta mode after Task 2**
- [ ] **Step 2a: Verify the exact property wiring for `jakartaMode` in `build.sbt`**
  - Record whether the supported override is `-Djakarta=true`, `-DjakartaMode=true`, or another property name before running the verification command.
- [ ] **Step 2b: Only attempt a default-mode flip if Task 2 reaches a useful green compile**
  - If Task 2 still stops on another compile blocker, document the gap and do not attempt a mode flip in this task.
- [ ] **Step 2c: If the verified Jakarta override command is green or close enough to stabilize in-scope, flip the default**
- [ ] **Step 2d: Otherwise keep the default as-is, document the mismatch explicitly, and open a follow-up Beads task instead of hiding it**
- [ ] **Step 3: Align the docs with the verified default mode**
  - The docs must stop implying Jakarta-default parity unless the build actually supports it.
- [ ] **Step 4: Verify the resulting compile mode explicitly**

**Verification:**
- `sbt -batch 'show jakartaMode'`
- `rg -n "jakartaMode|sys\\.props|System\\.getProperty" build.sbt`
- `sbt -batch 'show name' 'show assembly / assemblyJarName'`
- `sbt -batch compile`
- `sbt -batch -D<verified-jakarta-property>=true compile` if Jakarta-default is being considered

**Commit:**
```bash
git add build.sbt docs/BUILDING-sbt.md README.md docs/current-state.md
git commit -m "Stabilize SBT mode and artifact naming"
```

### Task 4: Rewrite The SBT Docs To Match Verified Behavior

**Files:**
- Modify: `docs/BUILDING-sbt.md`
- Modify: `README.md`
- Modify: `docs/current-state.md`
- Possibly modify: `docs/modernization-plan.md`

- [ ] **Step 1: Rewrite `docs/BUILDING-sbt.md` around verified commands only**
- [ ] **Step 2: Separate supported commands from legacy or partial commands**
  - Mark `testBackend` as legacy/offline fallback if it remains
  - Mark any still-disabled codegen tasks (`generatePstMessages`, `generateFlags`, `generateGxp`) as manual or unsupported
- [ ] **Step 3: Update README and current-state references so they do not oversell parity**
- [ ] **Step 4: If Task 3 cannot safely flip Jakarta default, document that explicitly as a remaining gap**
- [ ] **Step 5: Verify every command shown in the final doc block actually works in the updated build**

**Verification:**
- Re-run every documented happy-path command at least once
- `rg -n "SBT|sbt|BUILDING-sbt" README.md docs`

**Commit:**
```bash
git add docs/BUILDING-sbt.md README.md docs/current-state.md docs/modernization-plan.md
git commit -m "Document verified SBT behavior"
```

### Task 5: Close The Task With Explicit Residual Scope

**Files:**
- Modify: `.beads/issues.jsonl`

- [ ] **Step 1: Update the Beads task comments with the failing reproductions found during investigation**
- [ ] **Step 2: Record each implementation commit SHA and summary**
- [ ] **Step 3: Record review findings and how they were addressed**
- [ ] **Step 4: If any parity gap remains intentionally out of scope, capture it as a follow-up task instead of leaving an implicit gap**

**Verification:**
- `jq -r 'select(.id=="incubator-wave-modernization.4")' .beads/issues.jsonl`

**Commit:**
```bash
git add .beads/issues.jsonl
git commit -m "Record SBT parity task history"
```

## Risks

- Fixing protobuf staging may expose the next compile blocker immediately after `protoc` succeeds.
- Flipping SBT to Jakarta-default may pull in additional source-selection issues that are currently hidden by `jakartaMode=false`.
- Aligning runtime config paths may require choosing between legacy `server.config` token replacement and the current `wave/config/application.conf` model.
- The vendored-jar strategy may hide dependency drift until compile/test is retried on a clean machine.

## Non-goals

- Do not make SBT the canonical build system.
- Do not add GWT client compilation to SBT in this task.
- Do not port MongoDB, robots, or other currently partial Jakarta exclusions unless they are directly required to make the documented SBT happy path truthful.
- Do not refactor unrelated Gradle build logic.
