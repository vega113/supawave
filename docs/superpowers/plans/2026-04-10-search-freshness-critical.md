# Search Freshness Critical Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the shared-wave search freshness regressions so newly added participants see shared waves/content in search and newly added tags are immediately searchable via `tag:<newtag>`.

**Architecture:** Reproduce the bug on the runtime-active legacy search path first, then patch the narrowest server-side freshness seam and lock it down with focused JVM regression tests plus an E2E search assertion. Keep Lucene text-index changes out of scope unless the failing test proves they are needed.

**Tech Stack:** Java 17, sbt/JUnit, Jakarta runtime wiring, `SimpleSearchProviderImpl`, `MemoryPerUserWaveViewHandlerImpl`, Java E2E HTTP/WebSocket tests.

---

### Task 1: Add a Failing Combined Server-Side Regression

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java`

- [ ] **Step 1: Add a combined regression test that uses the issue sequence**

```java
public void testSearchFreshnessForSharedParticipantContentAndNewTag() throws Exception {
  WaveletName wave = WaveletName.of(WaveId.of(DOMAIN, "freshness"), WAVELET_ID);

  submitDeltaToNewWavelet(wave, USER1, addParticipantToWavelet(USER1, wave));

  SearchResult initialResults = searchProvider.search(USER1, "in:inbox", 0, 20);
  assertEquals(1, initialResults.getNumResults());

  submitDeltaToExistingWavelet(wave, USER1, addParticipantToWavelet(USER2, wave));
  appendBlipToWavelet(wave, USER1, "b+fresh", "freshness payload");

  SearchResult bobResults = searchProvider.search(USER2, "in:inbox", 0, 20);
  assertEquals(1, bobResults.getNumResults());
  assertTrue(bobResults.getDigests().get(0).getSnippet().contains("freshness"));

  addTagToWavelet(wave, USER1, "fresh-tag");

  SearchResult tagResults = searchProvider.search(USER1, "tag:fresh-tag", 0, 20);
  assertEquals(1, tagResults.getNumResults());
  assertEquals(wave.waveId.serialise(), tagResults.getDigests().get(0).getWaveId());
}
```

- [ ] **Step 2: Run only the new regression test to verify current behavior**

Run: `sbt 'testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest -- -z "SearchFreshnessForSharedParticipantContentAndNewTag"'`

Expected: FAIL if the runtime seam is still broken; otherwise refine the test so it specifically exposes the reported freshness gap.

- [ ] **Step 3: If the test passes, add a second failing test around the runtime view provider seam**

```java
public void testSearchFreshnessAcrossMemoryPerUserWaveViewReload() throws Exception {
  // Force a first search to warm the per-user view, mutate the wave, then
  // search through the runtime path again so the bug is exposed in the
  // provider/view freshness layer rather than the pure tag filter helper.
}
```

- [ ] **Step 4: Re-run the focused test target until the failure is on the intended seam**

Run: `sbt 'testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest'`

Expected: The new regression fails while the pre-existing search tests remain green.

### Task 2: Fix the Narrowest Runtime Freshness Seam

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/MemoryPerUserWaveViewHandlerImpl.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/SearchModule.java` only if wiring changes are required

- [ ] **Step 1: Implement the smallest server-side fix indicated by the failing test**

Likely seams to edit:

```java
private void ensureWaveMapLoaded(WaveMap waveMap, ParticipantId user) {
  synchronized (waveMapLoadLock) {
    if (/* freshness-safe reuse is proven */) {
      return;
    }
    waveMap.loadAllWavelets();
    lastWaveMapLoadMs = System.currentTimeMillis();
  }
}
```

or

```java
private LinkedHashMultimap<WaveId, WaveletId> createWavesViewToFilter(
    final ParticipantId user, final boolean isAllQuery) {
  // Rebuild or refresh the user-visible wave view when the runtime freshness
  // seam requires it instead of relying on stale cached state.
}
```

- [ ] **Step 2: Keep the fix scoped to freshness only**

Rules:
- do not change Lucene ranking/query behavior unless the failing test proves it is necessary
- do not refactor unrelated search UI or digest rendering
- prefer invalidation/reload correctness over speculative caching

- [ ] **Step 3: Run the focused regression again**

Run: `sbt 'testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest -- -z "SearchFreshnessForSharedParticipantContentAndNewTag"'`

Expected: PASS

- [ ] **Step 4: Run the relevant surrounding search tests**

Run: `sbt 'testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest'`

Expected: PASS

### Task 3: Add End-to-End Regression Coverage

**Files:**
- Modify: `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/WaveE2eTest.java`
- Modify: `wave/src/e2e-test/java/org/waveprotocol/wave/e2e/WaveApiClient.java` only if a small helper is needed

- [ ] **Step 1: Extend the existing ordered E2E flow with a tag-search assertion**

```java
@Test @Order(19)
void test19_aliceTagSearchFindsFreshTag() throws Exception {
  JsonObject result = client.search(E2eTestContext.aliceJsessionid, "tag:fresh-tag");
  assertTrue(result.toString().contains(E2eTestContext.modernWaveId));
}
```

- [ ] **Step 2: Make the Bob search assertion check result freshness, not just presence**

```java
assertTrue(rawSearchBody.contains(E2eTestContext.modernWaveId));
assertTrue(rawSearchBody.contains("Hello from E2E test!"));
```

- [ ] **Step 3: Run the E2E target only after the focused JVM tests are green**

Run: `WAVE_E2E_BASE_URL=http://localhost:9898 sbt 'e2eTest:testOnly org.waveprotocol.wave.e2e.WaveE2eTest'`

Expected: PASS

### Task 4: Verify, Document, and Prepare Review/PR Evidence

**Files:**
- Create: `journal/local-verification/2026-04-10-issue-826-search-freshness-critical.md`
- Modify: `wave/config/changelog.d/<new-fragment>.json` if the final fix changes user-visible search behavior

- [ ] **Step 1: Record exact verification commands and outcomes**

```text
sbt 'testOnly org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest'
WAVE_E2E_BASE_URL=http://localhost:9898 sbt 'e2eTest:testOnly org.waveprotocol.wave.e2e.WaveE2eTest'
```

- [ ] **Step 2: Add a changelog fragment if the fix changes user-visible behavior**

```json
{
  "type": "fix",
  "component": "search",
  "summary": "Restore search freshness for newly shared waves and newly added tags."
}
```

- [ ] **Step 3: Commit the work in logical units**

```bash
git add docs/superpowers/specs/2026-04-10-search-freshness-design.md \
        docs/superpowers/plans/2026-04-10-search-freshness-critical.md \
        wave/src/main/java/org/waveprotocol/box/server/waveserver/MemoryPerUserWaveViewHandlerImpl.java \
        wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java \
        wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java \
        wave/src/e2e-test/java/org/waveprotocol/wave/e2e/WaveE2eTest.java \
        journal/local-verification/2026-04-10-issue-826-search-freshness-critical.md
git commit -m "fix(search): restore participant and tag freshness"
```

- [ ] **Step 4: Update issue #826 with plan path, root cause, SHAs, verification, review notes, and PR URL**

Run: use the GitHub connector or `gh` after verification is complete.
