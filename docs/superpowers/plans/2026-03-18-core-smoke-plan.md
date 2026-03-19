# Core Smoke Verification Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify and record the current combined behavior of the dynamic renderer, fragments transport, and quasi-deletion UI on the `core-smoke` branch without implementing new feature code during the smoke task.

**Architecture:** Treat this as an evidence-collection task, not a feature-completion task. Use a layered verification pass: confirm the branch still compiles and serves the UI, run the branch in its documented default dev mode, exercise the combined renderer/fragments/quasi path in a browser with debug counters and server metrics, then repeat the highest-value checks under the stream override and the all-flags-off regression baseline. Record only what the branch actually does today, including known limitations that belong to follow-up tasks.

**Tech Stack:** Gradle 8.7, Java 17, GWT client, Jakarta Jetty dev server, Beads, shell smoke scripts, browser devtools, `/statusz?show=fragments`.

---

## Architect Findings

- The combined surface is already wired on this branch. `wave/config/application.conf` enables `enableDynamicRendering=true`, `fragmentFetchMode=http`, `enableFragmentFetchViewChannel=true`, `enableQuasiDeletionUi=true`, `enableFragmentsApplier=true`, and `forceClientFragments=true` for local dev.
- `StageTwo` is the integration seam. It initializes the quasi adapter, the fragments applier, and `DynamicRendererImpl`, and it selects `ClientFragmentRequester` for `http` mode or a stream requester with HTTP fallback for `stream` mode.
- `DynamicRendererImpl` still leaves the public `dynamicRendering(...)` entrypoints as TODOs. Smoke verification should document that gap, not attempt to fix it inside this task.
- `ClientFragmentRequester` treats 2xx responses as success but does not parse or apply fragment payloads. That means the smoke task can verify request issuance, counters, and non-breaking behavior, but it cannot honestly claim that HTTP mode performs full client-side fragment application.
- The current fragment-first story is still limited by snapshot gating. With `forceClientFragments=true`, the initial open still carries a full snapshot, so fragment windows are additive and the debug badge should be interpreted as virtualization evidence, not proof of deferred payload delivery.
- There is no existing combined automated smoke test for this feature stack. The closest reusable coverage is split between generic renderer tests, fragments servlet/RPC tests, server smoke scripts, and manual browser inspection.
- The executable preflight commands that work in this worktree today are `./gradlew -q :wave:compileJava` and `./gradlew -q :wave:smokeUi`.
- The default `./gradlew :wave:test ...` path is currently blocked by unrelated `compileTestJava` failures in the legacy test tree, including javax/jakarta mismatches and stale `ServerMain.applyFragmentsConfig(...)` references. Do not use the default JVM test task as the smoke gate until that separate test debt is fixed.

## Acceptance Criteria

- The worker records which combined behaviors are verified on the current branch in default dev mode.
- The worker captures whether stream override changes transport behavior in practice, including any HTTP fallback before the view channel is ready.
- The worker captures whether quasi-deletion visibly marks a deleted blip before removal and whether the observed dwell matches the configured delay.
- The worker records `/statusz?show=fragments` counter changes and the browser-side fragments badge behavior during the smoke pass.
- The worker explicitly documents the current limitations: TODO renderer entrypoints, metrics-only HTTP requester behavior, and snapshot-gated fragment loading.
- The worker updates the canonical docs and Beads task comments so `core.2`, `core.3`, and `product.1` can start from observed behavior instead of assumptions.

## Out Of Scope

- Implementing `DynamicRendererImpl.dynamicRendering(...)`.
- Making HTTP fragments parse and apply payloads.
- Changing the canonical transport decision for `http` vs `stream`.
- Fixing the unrelated `:wave:test` compilation failures unless they block the smoke pass from running at all.

## Required Environment

- Worktree: `/Users/vega/devroot/worktrees/incubator-wave/core-smoke`
- Java: OpenJDK 17 (`java -version` currently reports `17.0.12`)
- Gradle: wrapper on Gradle `8.7`
- Free local port: `9898`
- Browser with devtools network and console panels
- Optional but preferred for realistic verification: reuse the shared local file-store state before starting the server in this worktree

## Required Test And Verification Paths

- Compile and script smoke:
  - `./gradlew -q :wave:compileJava`
  - `./gradlew -q :wave:smokeUi`
- Worktree data reuse:
  - `scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave`
- Dev server:
  - `./gradlew :wave:runDev`
  - Default manual target: `http://127.0.0.1:9898/`
  - Metrics target: `http://127.0.0.1:9898/statusz?show=fragments`
- Core code and doc references:
  - `wave/config/application.conf`
  - `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/DynamicRendererImpl.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/ClientFragmentRequester.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/ViewChannelFragmentRequester.java`
  - `wave/src/main/java/org/waveprotocol/wave/model/conversation/quasi/QuasiConversationViewAdapter.java`
  - `wave/src/main/java/org/waveprotocol/box/server/rpc/FragmentsServlet.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/debug/FragmentsDebugIndicator.java`
  - `docs/current-state.md`
  - `docs/migrate-conversation-renderer-to-apache-wave.md`
  - `docs/fragments-viewport-behavior.md`

### Task 1: Lock The Smoke Envelope And Preflight What Still Runs

**Files:**
- Modify: `.beads/issues.jsonl` via `bd comments add`
- Reference: `docs/current-state.md`
- Reference: `docs/migrate-conversation-renderer-to-apache-wave.md`
- Reference: `docs/fragments-viewport-behavior.md`
- Reference: `wave/config/application.conf`

- [ ] **Step 1: Re-read the current-state and migration docs before running anything**

Confirm the smoke task is validating the branch as it exists today, especially
the documented limitations around snapshot gating, HTTP fragments, and renderer
TODO entrypoints.

- [ ] **Step 2: Run the compile and script smoke preflight that is known to work**

Run:

```bash
./gradlew -q :wave:compileJava
./gradlew -q :wave:smokeUi
```

Expected:
- `:wave:compileJava` exits `0`
- `:wave:smokeUi` reports `ROOT=302 WEBCLIENT=200` and `UI smoke OK`

- [ ] **Step 3: Record the current automated-test blocker before manual verification starts**

Run a focused `:wave:test` command only to capture the failure mode if the
branch state has changed since this plan was written. If it still fails at
`compileTestJava`, record the concrete error classes in Beads comments and do
not treat that as a blocker for the manual smoke pass.

### Task 2: Prepare A Realistic Local Verification Environment

**Files:**
- Reference: `scripts/worktree-file-store.sh`
- Reference: `wave/config/application.conf`
- Reference: `wave/build.gradle`

- [ ] **Step 1: Reuse the shared file-store state when historical waves are needed**

Run:

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
```

Expected:
- `wave/_accounts`
- `wave/_attachments`
- `wave/_deltas`

exist in the worktree, preferably as symlinks.

- [ ] **Step 2: Start the dev server in debug-friendly mode**

Run:

```bash
./gradlew :wave:runDev
```

Expected:
- The dev server starts on `127.0.0.1:9898`
- The client is served with the default debug-friendly flags from `application.conf`
- The debug panel / badge is available without extra URL hacking

- [ ] **Step 3: Identify one realistic smoke target wave and one deletion action**

Prefer a wave with at least `30` blips and ideally `500+` blips so viewport
paging is obvious during scroll. If realistic local data is unavailable,
create the smallest substitute wave that still supports:
- rapid scroll through many blips
- at least one deletion action
- fragment request activity visible in network logs and `/statusz`

### Task 3: Verify The Default Combined Dev Stack

**Files:**
- Modify: `.beads/issues.jsonl` via `bd comments add`
- Modify: `docs/current-state.md`
- Modify: `docs/migrate-conversation-renderer-to-apache-wave.md`
- Reference: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
- Reference: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/DynamicRendererImpl.java`
- Reference: `wave/src/main/java/org/waveprotocol/wave/client/debug/FragmentsDebugIndicator.java`

- [ ] **Step 1: Confirm the browser exposes the expected combined-mode signals on first open**

On a large wave in the running dev server, confirm:
- the fragments badge is visible
- the badge reports `Mode: http`, `Fetch: on`, and `Dyn: on`
- the wave opens without JS errors or blank-render failures

- [ ] **Step 2: Scroll aggressively and capture renderer plus fragments evidence**

During top-to-bottom and bottom-to-top scroll passes, record:
- whether placeholders appear and recover as blips page in/out
- whether the badge `Blips x/y` counts change
- whether `/fragments` requests appear in the network panel with `waveId`,
  `waveletId`, `startBlipId`, and `limit`
- whether `/statusz?show=fragments` shows increases in `httpRequests`,
  `httpOk`, `requesterSends`, and any `state*` counters
- whether server logs emit the expected transport messages while the client is
  scrolling

- [ ] **Step 3: Capture one direct endpoint sample while the server is still up**

Use a browser request copy or a manual `curl` against `/fragments` so the smoke
record contains at least one concrete example of the server response shape, even
though HTTP mode currently treats the payload as metrics-only success.

- [ ] **Step 4: Exercise quasi deletion and record the actual UI behavior**

Delete a blip while the default dev flags are active and confirm:
- the blip is visibly marked before removal
- the removal delay is approximately the configured `quasiDeletionDwellMs`
- the tooltip and styling match what the branch currently renders, not what old
  merge notes imply

- [ ] **Step 5: Write down the important caveat about what this does not prove**

Make the results explicit:
- the badge reflects virtualization, not necessarily deferred data delivery
- HTTP mode currently proves request/counter behavior, not payload application
- the public `dynamicRendering(...)` entrypoints remain unverified because they
  are still TODOs

### Task 4: Verify The Highest-Value Variants

**Files:**
- Modify: `.beads/issues.jsonl` via `bd comments add`
- Modify: `docs/migrate-conversation-renderer-to-apache-wave.md`
- Reference: `wave/config/application.conf`
- Reference: `wave/build.gradle`
- Reference: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`

- [ ] **Step 1: Stop the default dev server before starting any variant run**

If the server from Task 2 and Task 3 is still running, stop it before starting
the stream override. If the server was started in the foreground, stop it with
`Ctrl-C`. If it was started in the background, kill the prior process and make
sure port `9898` is free before launching the next command.

- [ ] **Step 2: Re-run the smoke with the stream transport override**

Restart the server with explicit flags:

```bash
./gradlew :wave:runDev -PclientFlags="fragmentFetchMode=stream,enableDynamicRendering=true,enableFragmentsApplier=true,enableViewportStats=true,enableFragmentFetchViewChannel=true,enableQuasiDeletionUi=true,quasiDeletionDwellMs=1000,fragmentsApplierMaxRanges=3,forceClientFragments=true"
```

Before treating the run as valid, confirm `wave/build.gradle` still wires
`-PclientFlags` into the server startup path and check the startup log for the
effective flags.

Record whether runtime behavior shows:
- HTTP fallback before the view channel is ready
- later switch to `ViewChannel.fetchFragments`
- no user-visible regression during the transport swap

- [ ] **Step 3: Stop the previous server before starting the next variant**

If the server was started in the foreground, stop it with `Ctrl-C`. If it was
started in the background, kill the prior process before launching the next
variant so the new `-PclientFlags` values actually take effect.

- [ ] **Step 4: Re-run the smoke with the feature flags effectively off**

Restart with a legacy-style baseline:

```bash
./gradlew :wave:runDev -PclientFlags="fragmentFetchMode=off,enableDynamicRendering=false,enableFragmentsApplier=false,enableViewportStats=false,enableFragmentFetchViewChannel=false,enableQuasiDeletionUi=false,forceClientFragments=false"
```

Confirm:
- no dynamic placeholder behavior
- no fragments badge or fetch activity
- no quasi-deletion dwell
- legacy rendering still loads the same wave
- the restarted client is using the new runtime flags rather than the previous
  run's state

- [ ] **Step 5: Decide whether this task should close or roll findings into follow-up blockers**

Close `core.1` only if the worker can state exactly what is verified in:
- default `http` dev mode
- stream override mode
- all-flags-off regression mode

If any of those modes expose a real behavior gap rather than a documented
limitation, attach the evidence to `core.2` or `core.3` instead of patching it
inside this smoke task.

### Task 5: Record The Observed Behavior In Canonical Places

**Files:**
- Modify: `.beads/issues.jsonl` via `bd comments add`
- Modify: `docs/current-state.md`
- Modify: `docs/migrate-conversation-renderer-to-apache-wave.md`

- [ ] **Step 1: Add a Beads comment with the exact commands, environments, and observations**

Include:
- worktree path and branch
- commands run
- which variant was tested
- observed results
- blocker notes

- [ ] **Step 2: Update `docs/current-state.md` so the top remaining gap is accurate**

If the smoke pass completes, replace the generic "end-to-end verification not
completed" statement with the actual verified scope and remaining caveats.

- [ ] **Step 3: Update `docs/migrate-conversation-renderer-to-apache-wave.md` with the verified behavior**

Prefer a short, factual update that captures:
- default verified dev mode
- stream override findings
- snapshot-gating caveat
- HTTP metrics-only caveat

- [ ] **Step 4: Preserve the test-debt blocker separately from the smoke result**

If `:wave:test` still fails at compile time, note that separately so the next
worker does not confuse test harness debt with smoke verification failure.
