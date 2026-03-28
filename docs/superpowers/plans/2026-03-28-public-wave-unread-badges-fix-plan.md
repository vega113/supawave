# Public Wave Unread Badges Fix Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop `with:@` public-wave search results from showing fully unread badges in the search panel when the wave is already read.

**Architecture:** The current evidence points to a client-side live-digest regression, not the older unopened server digest bugs. The search panel swaps a static server digest for a `WaveBasedDigest` when a wave is opened, so the fix should bootstrap public/shared waves without a misleading all-unread local supplement state and keep the server-correct digest intact.

**Tech Stack:** Java, GWT client, Wave supplement/read-state model, JUnit3 tests, Beads, GitHub PR flow

---

## Chunk 1: Root Cause And Regression Test

### Task 1: Capture the client-side no-UDW public-wave failure

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`
- Create: `wave/src/main/java/org/waveprotocol/wave/model/supplement/PublicWaveReadStateBootstrap.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/model/supplement/PublicWaveReadStateBootstrapTest.java`

- [ ] **Step 1: Write the failing regression test**

Create a focused client-side test that builds a wave without a viewer UDW, treats the viewer as an implicit public/shared viewer, and asserts that the initial unread count is `0` rather than `blipCount`.

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `sbt -Dsbt.global.base=/tmp/public-wave-unread-sbt-global -Dsbt.boot.directory=/tmp/public-wave-unread-sbt-boot -Dsbt.ivy.home=/Users/vega/.ivy2 -Dcoursier.cache=/Users/vega/devroot/incubator-wave/.coursier-cache -Dsbt.ipcsocket.jni=false -Dsbt.ipcsocket.tmpdir=/tmp -batch "project wave" "set Test / fork := false" "testOnly org.waveprotocol.wave.model.supplement.PublicWaveReadStateBootstrapTest"`
Expected: FAIL because the bootstrap helper does not exist yet, or because the current client supplement path marks all blips unread for a no-UDW public/shared wave.

## Chunk 2: Narrow Fix

### Task 2: Bootstrap no-UDW public/shared client supplements as fully read

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`

- [ ] **Step 3: Implement the minimal fix**

Adjust the client supplement bootstrap path so that when a wave has no UDW and the signed-in user is not an explicit participant of the conversational wavelets, the initial local read-state marks existing conversational wavelet versions as read instead of defaulting every blip to unread.

- [ ] **Step 4: Re-run the focused test to verify it passes**

Run: `sbt -Dsbt.global.base=/tmp/public-wave-unread-sbt-global -Dsbt.boot.directory=/tmp/public-wave-unread-sbt-boot -Dsbt.ivy.home=/Users/vega/.ivy2 -Dcoursier.cache=/Users/vega/devroot/incubator-wave/.coursier-cache -Dsbt.ipcsocket.jni=false -Dsbt.ipcsocket.tmpdir=/tmp -batch "project wave" "set Test / fork := false" "testOnly org.waveprotocol.wave.model.supplement.PublicWaveReadStateBootstrapTest"`
Expected: PASS

## Chunk 3: Verification And Closeout

### Task 3: Regressions, changelog, and PR

**Files:**
- Modify: `wave/config/changelog.json`

- [ ] **Step 5: Run targeted verification**

Run: `sbt -Dsbt.global.base=/tmp/public-wave-unread-sbt-global -Dsbt.boot.directory=/tmp/public-wave-unread-sbt-boot -Dsbt.ivy.home=/Users/vega/.ivy2 -Dcoursier.cache=/Users/vega/devroot/incubator-wave/.coursier-cache -Dsbt.ipcsocket.jni=false -Dsbt.ipcsocket.tmpdir=/tmp -batch "project wave" "set Test / fork := false" "testOnly org.waveprotocol.wave.model.supplement.PublicWaveReadStateBootstrapTest" wave/compile compileGwt`
Expected: PASS

- [ ] **Step 6: Run local server sanity verification for the affected UI path**

Run: `sbt -Dsbt.global.base=/tmp/public-wave-unread-sbt-global -Dsbt.boot.directory=/tmp/public-wave-unread-sbt-boot -Dsbt.ivy.home=/Users/vega/.ivy2 -Dcoursier.cache=/Users/vega/devroot/incubator-wave/.coursier-cache -Dsbt.ipcsocket.jni=false -Dsbt.ipcsocket.tmpdir=/tmp -batch prepareServerConfig run`
Expected: server boots successfully so the client-side public-wave path is locally runnable before PR.

- [ ] **Step 7: Update user-facing release notes if verification is green**

Add a concise changelog entry describing the public-wave unread badge correction in the search panel.

- [ ] **Step 8: Commit and open the PR**

Commit the focused fix, open the PR against `main`, rename the lane pane with the PR number, and start the PR monitor.

## Acceptance Criteria

- `with:@` public-wave results do not show fully unread badges solely because the client opened a wave without a UDW.
- The regression test fails before the fix and passes after it.
- Focused client/server verification succeeds.

## Out Of Scope

- Reworking server-side search digest generation for inbox or unopened-wave queries.
- Broader OT search architecture changes unrelated to this public/shared live-digest path.
