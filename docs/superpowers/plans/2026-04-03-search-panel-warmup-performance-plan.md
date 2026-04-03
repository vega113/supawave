# Search Panel Warmup Performance Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop server startup from synchronously warming the search wave view by default so deploy/reload becomes responsive again without changing lazy search correctness.

**Architecture:** Keep the existing lazy `PerUserWaveViewProvider.retrievePerUserWaveView(...)` behavior for real search requests, but move the startup warmup behind an explicit config gate that defaults to off. Add a small package-visible Jakarta `ServerMain` helper so the startup decision and both warmup branches are unit-testable before changing production behavior.

**Tech Stack:** Jakarta `ServerMain`, Typesafe Config, Guice, JUnit 4 + Mockito, SBT, local Wave runtime.

---

## Acceptance Criteria

- Default startup no longer blocks on `warmUpWaveView()` in `ServerMain.initializeSearch()`.
- An explicit config opt-in can still trigger the existing owner/no-owner warmup path unchanged.
- Jakarta unit coverage proves:
  - warmup is skipped when the new flag is false and when it is absent
  - the owner-address branch still calls `PerUserWaveViewProvider.retrievePerUserWaveView(...)` when enabled
  - a bare owner name still appends `@` + `core.wave_server_domain`
  - the no-owner branch still calls `WaveMap.loadAllWavelets()` when enabled
  - warmup exceptions remain non-fatal and do not abort startup
- Local runtime verification shows startup reaches `/healthz` without logging `Pre-warmed wave view...` when using default config.
- Verification runs in this worktree do not leave `_indexes/` or `gwt-unitCache/` stageable by git.
- Scope stays limited to startup warmup; do not change Lucene reindex behavior or search query semantics in this issue.

## File Map

- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- Modify: `wave/config/reference.conf`
- Modify: `.gitignore`
- Create: `wave/src/jakarta-test/java/org/waveprotocol/box/server/ServerMainWarmupTest.java`
- Modify if the change lands: `wave/config/changelog.json`

## Constraints And Non-Goals

- Do not change `MemoryPerUserWaveViewHandlerImpl` cache behavior in this issue.
- Do not change `Lucene9WaveIndexerImpl.remakeIndex()` in this issue.
- Do not add async/background warmup in this issue.
- Do not widen scope into client-side search bootstrap UX.
- Do not add unrelated ignore rules beyond the root build artifacts created by this workflow.

## Chunk 1: Lock The Startup Decision With Tests

### Task 0: Guard Worktree Build Artifacts

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Ignore the root verification artifacts created by this issue workflow**

Add root-level ignore entries for:
- `/_indexes/`
- `/gwt-unitCache/`

- [ ] **Step 2: Confirm the worktree no longer reports those directories as stageable**

Run:
```bash
git status --short --ignored
```

Expected:
- `_indexes/` and `gwt-unitCache/` appear as ignored, not untracked

### Task 1: Add Jakarta Coverage For The Warmup Gate

**Files:**
- Create: `wave/src/jakarta-test/java/org/waveprotocol/box/server/ServerMainWarmupTest.java`

- [ ] **Step 1: Write the failing tests for disabled startup warmup**

Create a Jakarta unit test that calls a package-visible `ServerMain` helper with:
- a mock `Injector`
- config with `search.startup_wave_view_warmup = false`
- config with the warmup key absent

Expected behavior:
- `Injector.getInstance(...)` is never called

- [ ] **Step 2: Write the failing test for the enabled owner warmup branch**

Use config with:
- `search.startup_wave_view_warmup = true`
- `core.owner_address = "vega@local.net"`

Stub `injector.getInstance(PerUserWaveViewProvider.class)` to return a mock `PerUserWaveViewProvider`, then assert:
- `retrievePerUserWaveView(ParticipantId.ofUnsafe("vega@local.net"))` is invoked once

- [ ] **Step 3: Write the failing test for the bare-owner normalization branch**

Use config with:
- `search.startup_wave_view_warmup = true`
- `core.owner_address = "vega"`
- `core.wave_server_domain = "local.net"`

Mock `PerUserWaveViewProvider` and assert:
- `retrievePerUserWaveView(ParticipantId.ofUnsafe("vega@local.net"))` is invoked once

- [ ] **Step 4: Write the failing test for the enabled no-owner fallback branch**

Use config with:
- `search.startup_wave_view_warmup = true`
- `core.owner_address = ""`

Mock `WaveMap` and assert:
- `loadAllWavelets()` is invoked once

- [ ] **Step 5: Write the failing test for the non-fatal exception path**

Use config with:
- `search.startup_wave_view_warmup = true`
- `core.owner_address = ""`

Mock `WaveMap.loadAllWavelets()` or `PerUserWaveViewProvider.retrievePerUserWaveView(...)` to throw and assert:
- the package-visible helper returns without propagating the exception

- [ ] **Step 6: Run the Jakarta test target and confirm it fails before implementation**

Run:
```bash
sbt "JakartaTest/testOnly org.waveprotocol.box.server.ServerMainWarmupTest"
```

Expected:
- test compilation or execution fails because the helper does not exist yet

## Chunk 2: Implement The Config-Gated Warmup

### Task 2: Add The Narrowest Production Fix

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- Modify: `wave/config/reference.conf`

- [ ] **Step 1: Add the new config key to reference defaults**

Add:
- `search.startup_wave_view_warmup = false`

Document that:
- the flag controls synchronous startup warmup
- default is off because eager wave-view warmup can block startup on a full wave scan
- missing-key reads should be treated as disabled

- [ ] **Step 2: Extract one package-visible startup helper in Jakarta ServerMain**

Add a minimal helper that:
- reads `search.startup_wave_view_warmup` using `config.hasPath(...)` before `config.getBoolean(...)`
- returns immediately when disabled
- delegates to the existing warmup logic when enabled

Implementation detail:
- keep it `static` and package-visible for the Jakarta unit test
- keep the helper signature as `(Injector injector, Config config)` to minimize production churn
- make the current warmup entrypoint itself the package-visible helper rather than adding a second wrapper method
- preserve the existing owner/no-owner warmup behavior and the current non-fatal exception handling once the flag is enabled

- [ ] **Step 3: Switch initializeSearch() to the gated helper**

Replace the unconditional startup warmup call with the helper from Step 2.

- [ ] **Step 4: Re-run the Jakarta test target and confirm it passes**

Run:
```bash
sbt "JakartaTest/testOnly org.waveprotocol.box.server.ServerMainWarmupTest"
```

Expected:
- all new tests pass

## Chunk 3: Verification And Release Hygiene

### Task 3: Verify The Startup Regression Is Gone

**Files:**
- Modify if the change lands: `wave/config/changelog.json`

- [ ] **Step 1: Add a changelog entry for the startup performance fix**

Add a top entry describing:
- startup no longer blocks on eager search wave-view warmup by default
- lazy search correctness is preserved

- [ ] **Step 2: Run focused compile verification**

Run:
```bash
sbt wave/compile
```

Expected:
- compile succeeds

- [ ] **Step 3: Run a real local startup sanity check in this worktree**

Run from this worktree with default config:
```bash
sbt -batch prepareServerConfig run
```

Verification:
- `/healthz` returns `200`
- startup logs do not contain `Pre-warmed wave view for owner`
- startup logs do not contain `Wave map pre-warmed: loaded all wavelets`
- use substring matching for those two log lines rather than exact full-line equality

- [ ] **Step 4: Verify the server still serves the app shell after startup**

With the server running:
```bash
curl -I http://127.0.0.1:9898/
curl -s http://127.0.0.1:9898/healthz
```

Expected:
- `/` responds with a redirect or success page
- `/healthz` returns `ok`

- [ ] **Step 5: Record verification and review evidence in issue #598 before PR**

Capture:
- worktree path and branch
- plan path
- test command results
- local startup sanity result
- review findings and resolutions

## Exact Verification Commands

- `sbt "JakartaTest/testOnly org.waveprotocol.box.server.ServerMainWarmupTest"`
- `sbt wave/compile`
- `sbt -batch prepareServerConfig run`
- `curl -s http://127.0.0.1:9898/healthz`
