# Config Hygiene Implementation Plan

> **For agentic workers:** REQUIRED: Use the repo orchestration workflow in `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove fragment and segment-state config drift so server-side code reads one canonical `Config` source instead of mixing `ConfigFactory.load()` and `System.getProperty(...)`.

**Architecture:** Keep `ServerMain` as the place that builds effective config precedence, then make `StatuszServlet` and `WaveClientServlet` consume that injected or derived config instead of reconstructing it from separate sources. Preserve compatibility keys, but route them through `Config`.

**Tech Stack:** Java server code, Typesafe Config, Jetty/Jakarta server wiring, Beads task tracking, focused JUnit tests.

---

## Task 1: Lock The Shared Config Seams

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/stat/StatuszServlet.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`

- [ ] **Step 1: Add the failing test coverage**

Add focused tests for:
- status output reflects injected/effective config for `server.fragments.transport`
- status output reflects `server.preferSegmentState`
- status output reflects `server.enableStorageSegmentState`
- client flags default fragment-related values from config when `wave.clientFlags` is absent

- [ ] **Step 2: Run the focused tests and verify they fail for the current mixed-precedence behavior**

- [ ] **Step 3: Replace direct JVM-property fallback in `StatuszServlet` with config-based reads**

- [ ] **Step 4: Replace fragment-related `wave.clientFlags` defaulting in `WaveClientServlet` with config-derived defaults**

- [ ] **Step 5: Remove no-longer-needed fragment/segment-state property mirroring from `ServerMain`**

- [ ] **Step 6: Re-run focused tests and verify they pass**

- [ ] **Step 7: Commit**

Commit message:

```bash
git commit -m "Unify fragment config resolution"
```

## Task 2: Guard Compatibility

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/ServerMainConfigValidationTest.java`
- Modify or Create: focused tests under `wave/src/test/java/org/waveprotocol/box/server/`

- [ ] **Step 1: Keep legacy fragment config keys supported through `Config`**

- [ ] **Step 2: Add or extend a regression proving config precedence remains stable**

- [ ] **Step 3: Re-run the focused test set**

- [ ] **Step 4: If green, include this in the same branch commit set or a second small commit**

Verification target:

```bash
./gradlew -q :wave:test --tests org.waveprotocol.box.server.ServerMainConfigValidationTest
```

## Non-goals

- Do not touch unrelated direct property reads such as `wave.forceDebugPanel` or websocket transport client code in this task.
- Do not redesign the entire client flag system.
