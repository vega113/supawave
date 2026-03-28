# Lucene9 Flag Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the `lucene9` rollout flag discoverable and usable from the admin feature flags UI when the rollout code exists but the flag record has not been created yet.

**Architecture:** Keep the Lucene 9 rollout semantics unchanged: the search path remains gated by `FeatureFlagService.isEnabled("lucene9", ...)`, and persisted flag records still control the actual rollout. Fix the admin visibility gap by merging a small set of code-known rollout flags into the `/admin/flags` list response, so missing-but-supported flags show up in the UI and can be created through the existing save/toggle actions.

**Tech Stack:** Jakarta servlet code, feature-flag persistence/service layer, JUnit 4 Jakarta tests, SBT, local Wave admin tooling.

---

## Chunk 1: Admin Flag Visibility

### Task 1: Reproduce the missing lucene9 listing with a failing servlet test

**Files:**
- Create: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/FeatureFlagServletTest.java`
- Reference: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/FeatureFlagServlet.java`

- [ ] **Step 1: Write the failing test**

Add a test that authenticates as an owner/admin, stubs `FeatureFlagStore.getAll()` to return an empty list, calls `FeatureFlagServlet.doGet()` for `/admin/flags`, and asserts the JSON response contains a `lucene9` entry with a rollout description.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
sbt jakartaTest:testOnly org.waveprotocol.box.server.rpc.FeatureFlagServletTest
```

Expected: FAIL because the current servlet returns an empty `flags` array when the store has no persisted records.

### Task 2: Merge known rollout flags into the admin listing

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/FeatureFlagServlet.java`
- Create: `wave/src/main/java/org/waveprotocol/box/server/persistence/KnownFeatureFlags.java`
- Test: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/FeatureFlagServletTest.java`

- [ ] **Step 3: Add the minimal production code**

Create a small known-flags helper that exposes the default `lucene9` flag metadata and a merge operation that overlays persisted store entries on top of those defaults. Update `FeatureFlagServlet.handleList()` to emit the merged list instead of raw `store.getAll()`.

- [ ] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
sbt jakartaTest:testOnly org.waveprotocol.box.server.rpc.FeatureFlagServletTest
```

Expected: PASS, with the servlet returning `lucene9` even when the store is empty.

### Task 3: Guard the persisted-record behavior

**Files:**
- Modify: `wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/FeatureFlagServletTest.java`

- [ ] **Step 5: Add an override test**

Add a second test showing that if `FeatureFlagStore.getAll()` already contains `lucene9`, the servlet returns the stored enabled/allowed-users state rather than the default placeholder.

- [ ] **Step 6: Run the targeted test suite**

Run:

```bash
sbt jakartaTest:testOnly org.waveprotocol.box.server.rpc.FeatureFlagServletTest
```

Expected: PASS for both the missing-flag and persisted-flag scenarios.

- [ ] **Step 7: Commit**

```bash
git add docs/superpowers/plans/2026-03-28-lucene9-flag-followup-plan.md \
  wave/src/main/java/org/waveprotocol/box/server/persistence/KnownFeatureFlags.java \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/FeatureFlagServlet.java \
  wave/src/jakarta-test/java/org/waveprotocol/box/server/rpc/FeatureFlagServletTest.java
git commit -m "fix: surface lucene9 rollout flag in admin UI"
```

## Chunk 2: User-Facing Verification And Release Trail

### Task 4: Run focused build verification

**Files:**
- Verify only

- [ ] **Step 8: Run the focused Jakarta test**

Run:

```bash
sbt jakartaTest:testOnly org.waveprotocol.box.server.rpc.FeatureFlagServletTest
```

Expected: PASS

- [ ] **Step 9: Run the server compile**

Run:

```bash
sbt wave/compile
```

Expected: PASS

### Task 5: Verify the flag path locally with the existing admin tooling

**Files:**
- Verify only

- [ ] **Step 10: Start the local server**

Run:

```bash
sbt prepareServerConfig run
```

Expected: Server starts on `http://localhost:9898`

- [ ] **Step 11: Confirm the empty store still lists lucene9**

Use the existing session cookie and admin endpoint:

```bash
curl -sS --cookie "$(cat ~/.wave-session)" http://localhost:9898/admin/flags
```

Expected: JSON response contains a `lucene9` flag entry before manual creation.

- [ ] **Step 12: Confirm the existing flag tooling still works**

Run:

```bash
scripts/feature-flag.sh --local set lucene9 "Lucene 9.x full-text search" --allowed vega@supawave.ai
scripts/feature-flag.sh --local get lucene9
```

Expected: PASS, with the persisted local flag reflecting the allowed-user rollout state.

### Task 6: Record the user-facing release note

**Files:**
- Modify: `wave/config/changelog.json`

- [ ] **Step 13: Add a new top entry if the fix is ready to land**

Add a `2026-03-28` changelog entry explaining that rollout flags like `lucene9` now appear in admin feature flags before first enablement.

- [ ] **Step 14: Re-run the targeted verification after the changelog edit**

Run:

```bash
sbt jakartaTest:testOnly org.waveprotocol.box.server.rpc.FeatureFlagServletTest
sbt wave/compile
```

Expected: PASS
