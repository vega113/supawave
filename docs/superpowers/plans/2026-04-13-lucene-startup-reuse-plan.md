# Lucene Startup Reuse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reuse an existing Lucene index at startup instead of repairing every wave, while still building an empty index and preserving explicit admin rebuilds.

**Architecture:** Narrow the change to `Lucene9WaveIndexerImpl.remakeIndex()` by extracting a helper that classifies startup behavior into initial build, forced full rebuild, or reuse-existing-index. Keep the admin-triggered rebuild path unchanged and update deploy comments to match the runtime behavior.

**Tech Stack:** Java, JUnit 4, Mockito, Lucene 9, Typesafe Config

---

## File Map

- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9WaveIndexerImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9WaveIndexerImplTest.java`
- Modify: `deploy/caddy/deploy.sh`

### Task 1: Add failing Lucene startup tests

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9WaveIndexerImplTest.java`

- [ ] **Step 1: Write the failing tests**

Add tests that prove:
- existing index + rebuild disabled skips startup repair
- empty index still performs a startup build

- [ ] **Step 2: Run test to verify it fails**

Run: `sbt "testOnly org.waveprotocol.box.server.waveserver.lucene9.Lucene9WaveIndexerImplTest"`
Expected: FAIL because current startup behavior still loads wavelets and rebuilds when docs already exist.

### Task 2: Implement minimal startup behavior change

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9WaveIndexerImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`

- [ ] **Step 1: Extract startup action helper**

Introduce a small helper/enum that classifies startup as:
- initial build
- full rebuild
- reuse existing index

- [ ] **Step 2: Use the helper from `remakeIndex()`**

Make `remakeIndex()` return early for the reuse-existing-index case and only call `loadAllWavelets()` / `doRebuild(...)` for initial build or forced full rebuild.

- [ ] **Step 3: Keep admin rebuild untouched**

Do not change `forceRemakeIndex(...)`; only avoid recording a startup reindex when startup reused the existing index and no rebuild actually ran.

### Task 3: Update operator-facing comments

**Files:**
- Modify: `deploy/caddy/deploy.sh`

- [ ] **Step 1: Fix the startup comment**

Update the deploy comment so it no longer claims startup always rebuilds Lucene from MongoDB and instead describes empty-index builds and lock handoff as the relevant startup delays.

### Task 4: Verify and review

**Files:**
- Modify: none

- [ ] **Step 1: Run focused tests**

Run: `sbt "testOnly org.waveprotocol.box.server.waveserver.lucene9.Lucene9WaveIndexerImplTest"`
Expected: PASS

- [ ] **Step 2: Self-review the diff**

Inspect the diff to confirm:
- skip path is explicit and readable
- admin rebuild path is unchanged
- comments match code behavior

- [ ] **Step 3: Run a narrow second check if needed**

If the Lucene test touched shared helpers or config-sensitive behavior, run:
`sbt "testOnly org.waveprotocol.box.server.waveserver.ReindexServiceTest org.waveprotocol.box.server.rpc.AdminServletTest"`

Expected: PASS
