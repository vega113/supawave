# OT Real-Time Search Wavelet Implementation Plan

> **For agentic workers:** REQUIRED: Use the repo orchestration workflow in `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 15-second polling search with real-time per-user search wavelets, achieving <100ms update latency via the existing WebSocket delta channel.

**Architecture:** Each user+query pair gets a dedicated wavelet (`search+<md5-of-query>`) in the user's wave domain. The server updates these wavelets when underlying waves change; clients subscribe via the standard `openRequest()` mechanism and receive search result deltas over the existing WebSocket connection. This reuses the entire OT delta pipeline -- no new transport needed.

**Tech Stack:** Jakarta servlet (jakarta-overrides as primary for server classes, main tree as fallback), Guice DI, WaveBus subscriber pattern (matching `ContactsRecorder`), DocOp XML mutations, GWT client-side delta handling, JUnit 3 tests (matching existing `SimpleSearchProviderImplTest` conventions). **Build:** SBT (`sbt wave/compile`, `sbt compileGwt`, `sbt "testOnly ..."`); no Gradle.

---

## Executive Summary

The current search implementation in `SearchPresenter` polls the server every 15 seconds (`POLLING_INTERVAL_MS = 15000`). This creates a poor user experience: new waves, participant changes, and unread count updates are invisible for up to 15 seconds. Reducing the polling interval increases server load proportionally (every connected client × every query × poll frequency).

The fix: represent search results as ordinary wavelets. When a wave changes (new blip, participant added/removed, read state change), the server computes the effect on each active search query and pushes a DocOp delta to the corresponding search wavelet. The client already knows how to subscribe to wavelets via `openRequest()` and apply incoming deltas -- so search updates flow through the same WebSocket channel with the same <100ms latency as collaborative editing.

**Key numbers:**
- **Latency:** <100ms (vs 15,000ms polling)
- **Bandwidth:** >80% reduction (deltas vs full result sets)
- **Server CPU:** <5% increase (batching + Bloom filter)
- **New server code:** ~1,100 LOC across 4 files
- **Client changes:** ~150 LOC in SearchPresenter, gated behind feature flag

---

## Existing Code Inventory

| Component | Path | Role |
|-----------|------|------|
| `SearchPresenter` | `wave/src/main/java/.../webclient/search/SearchPresenter.java` | Client-side search: 15s polling, renders digest list |
| `SearchProvider` | `wave/src/main/java/.../waveserver/SearchProvider.java` | Server interface: `search(user, query, startAt, numResults)` |
| `SimpleSearchProviderImpl` | `wave/src/main/java/.../waveserver/SimpleSearchProviderImpl.java` | Default search impl: reads from `PerUserWaveViewProvider` |
| `SearchModule` | `wave/src/main/java/.../server/SearchModule.java` | Guice module: binds `SearchProvider` based on config |
| `WaveServerModule` | `wave/src/main/java/.../waveserver/WaveServerModule.java` | Core Guice module for wave server bindings |
| `WaveBus` | `wave/src/main/java/.../waveserver/WaveBus.java` | Subscription bus: `waveletUpdate()`, `waveletCommitted()` |
| `WaveBus.Subscriber` | (inner interface) | Receives `waveletUpdate(ReadableWaveletData, DeltaSequence)` |
| `WaveletNotificationSubscriber` | `wave/src/main/java/.../waveserver/WaveletNotificationSubscriber.java` | Lower-level: receives `WaveletDeltaRecord` list + domain routing |
| `WaveletNotificationDispatcher` | `wave/src/main/java/.../waveserver/WaveletNotificationDispatcher.java` | Dispatches to WaveBus subscribers + notification subscribers |
| `PerUserWaveViewBus` | `wave/src/main/java/.../waveserver/PerUserWaveViewBus.java` | Per-user events: participant added/removed, wave init |
| `ClientFrontendImpl` | `wave/src/main/java/.../frontend/ClientFrontendImpl.java` | Server-side frontend: handles `openRequest()` subscriptions |
| `WaveClientRpcImpl` | `wave/src/main/java/.../frontend/WaveClientRpcImpl.java` | RPC layer: routes protobuf open/submit to `ClientFrontend` |
| `ContactsRecorder` | `wave/src/main/java/.../contact/ContactsRecorder.java` | Example WaveBus subscriber: auto-records contacts on wave changes |
| `ServerMain` (jakarta) | `wave/src/jakarta-overrides/java/.../server/ServerMain.java` | Jakarta entry point: registers WaveBus listeners |
| `ServerMain` (javax) | `wave/src/main/java/.../server/ServerMain.java` | Javax entry point: registers WaveBus listeners |

---

## Wavelet Design

### WaveletId Convention

Each search wavelet is identified by:

```
WaveId:     <user-domain>/search~<user-local-part>
WaveletId:  <user-domain>/search+<md5-hex-of-query>
```

Example for user `alice@example.com` searching `in:inbox`:

```
WaveId:     example.com/search~alice
WaveletId:  example.com/search+5d41402abc4b2a76b9719d911017c592
```

The `search~` prefix in the wave ID prevents collision with conversation waves (`w+`). The MD5 hash in the wavelet ID ensures a stable, fixed-length identifier for any query string. Multiple queries by the same user live as separate wavelets under the same wave.

### Document Schema

The search wavelet contains a single document (`main`) with this XML structure:

```xml
<body>
  <metadata query="in:inbox" updated="1711411200000" total="42"/>
  <results>
    <result id="example.com/w+abc123"
            title="Project standup notes"
            snippet="Let's discuss the Q2 roadmap..."
            modified="1711411100000"
            creator="bob@example.com"
            participants="3"
            unread="2"
            blips="7"/>
    <result id="example.com/w+def456"
            title="Design review feedback"
            snippet="The new mockups look great..."
            modified="1711410900000"
            creator="carol@example.com"
            participants="5"
            unread="0"
            blips="12"/>
  </results>
</body>
```

### DocOp Mutation Strategy

Updates are expressed as standard DocOps against the `main` document:

- **New result:** `retain` to insertion point inside `<results>`, then `elementStart("result", attrs)` + `elementEnd()`. Insertion position maintains sort order (most-recently-modified first).
- **Updated result:** `retain` to the `<result>` element, then `updateAttributes(oldAttrs, newAttrs)` to change `modified`, `unread`, `snippet`, etc.
- **Removed result:** `retain` to the `<result>` element, then `deleteElementStart()` + `deleteElementEnd()` to remove it.
- **Reordered result:** Delete from old position + insert at new position (two operations composed into a single delta).

This approach means the client receives granular, composable changes rather than full result-set replacements.

---

## Phase 1: SearchWaveletManager (~300 LOC)

### Goal
Manage the lifecycle of per-user search wavelets: creation, lookup, and document initialization.

### Files to Create

- [ ] **`wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletManager.java`**
  - Package: `org.waveprotocol.box.server.waveserver.search`
  - `@Singleton`, Guice `@Inject` constructor taking `WaveMap` and `WaveletProvider`
  - Core methods:
    ```java
    /** Get or create the search wavelet for a user+query pair. */
    WaveletName getOrCreateSearchWavelet(ParticipantId user, String query);

    /** Compute the WaveletName for a user+query without creating it. */
    WaveletName computeWaveletName(ParticipantId user, String query);

    /** Check if a wavelet name is a search wavelet. */
    boolean isSearchWavelet(WaveletName waveletName);

    /** Remove a search wavelet (cleanup on unsubscribe). */
    void removeSearchWavelet(ParticipantId user, String query);
    ```
  - `getOrCreateSearchWavelet()`:
    1. Compute `WaveId` as `WaveId.of(user.getDomain(), "search~" + user.getAddress().split("@")[0])`
    2. Compute `WaveletId` as `WaveletId.of(user.getDomain(), "search+" + md5Hex(query))`
    3. Check if wavelet already exists in `WaveMap`
    4. If not, create it with initial empty document:
       ```xml
       <body><metadata query="..." updated="0" total="0"/><results/></body>
       ```
    5. Add user as sole participant
    6. Return `WaveletName.of(waveId, waveletId)`
  - Thread safety: `ConcurrentHashMap<String, WaveletName>` keyed by `user + "|" + query` for fast lookup; wavelet creation synchronized per key
  - MD5 helper: use `java.security.MessageDigest` (already available in the codebase), hex-encode to 32-char string

### Files to Modify

- [ ] **`wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveServerModule.java`**
  - Add binding: `bind(SearchWaveletManager.class).in(Singleton.class)`
  - Import: `org.waveprotocol.box.server.waveserver.search.SearchWaveletManager`

### Verification
- `sbt wave/compile` succeeds
- Unit test: `SearchWaveletManagerTest` (see Phase 5)

---

## Phase 2: SearchWaveletUpdater + SearchIndexer (~600 LOC)

### Goal
Listen for wave changes on the `WaveBus` and push updates to affected search wavelets.

### Files to Create

- [ ] **`wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletUpdater.java`**
  - Package: `org.waveprotocol.box.server.waveserver.search`
  - Implements `WaveBus.Subscriber`
  - `@Singleton`, Guice `@Inject` constructor taking `SearchWaveletManager`, `SearchIndexer`, `SearchProvider`, `SearchWaveletDataProvider`
  - `waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas)`:
    1. Extract `WaveletName` from the wavelet data
    2. Skip if `SearchWaveletManager.isSearchWavelet(waveletName)` (avoid recursive updates)
    3. Query `SearchIndexer` for affected `(ParticipantId, queryHash)` pairs
    4. For each affected pair, enqueue an update task with 100ms batching window
    5. When batch fires: call `SearchProvider.search()` for the user+query, compute diff via `SearchWaveletDataProvider`, submit delta to the search wavelet
  - Batching implementation (concrete backpressure):
    - `ScheduledExecutorService` with 100ms debounce delay and 500ms max-wait ceiling
    - `ConcurrentHashMap<String, ScheduledFuture<?>>` keyed by `user|query` with `firstSeenTimestamp` tracking per key
    - New update for same key cancels pending future and reschedules, unless `500ms - (now - firstSeenTimestamp) <= 0` in which case the batch fires immediately (max-wait guarantee)
    - Per-user token bucket: max 10 search wavelet updates/second per user, excess updates queued (bounded queue of 100 entries, oldest dropped on overflow)
    - This bounds write amplification: rapid edits to a single wave produce at most one search wavelet update per 100ms window, and max-wait ensures updates are never delayed more than 500ms
  - Error handling: log and continue on individual update failures; never let one bad wavelet block others

- [ ] **`wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchIndexer.java`**
  - Package: `org.waveprotocol.box.server.waveserver.search`
  - `@Singleton`, Guice `@Inject` constructor (no dependencies -- pure in-memory data structure)
  - Purpose: track which users+queries care about which waves, so `SearchWaveletUpdater` can quickly find affected subscriptions
  - Data structures:
    ```java
    // Forward index: which subscriptions care about a wave?
    ConcurrentHashMap<WaveId, Set<SubscriptionKey>> waveToSubscriptions;

    // Reverse index: which waves does a subscription cover?
    ConcurrentHashMap<SubscriptionKey, Set<WaveId>> subscriptionToWaves;

    // Canonical raw query stored alongside hash for lifecycle management
    ConcurrentHashMap<SubscriptionKey, String> subscriptionRawQueries;

    // Bloom filter per subscription for fast negative rejection
    ConcurrentHashMap<SubscriptionKey, BloomFilter<WaveId>> subscriptionBloomFilters;
    ```
    where `SubscriptionKey` is a value record of `(ParticipantId user, String queryHash)`
  - **Query hash lifecycle:** Store canonical raw query alongside the hash via `subscriptionRawQueries`. Wire `registerSubscription` to actual open-subscription events and `unregisterSubscription` to actual close-subscription events. This ensures the indexer state is always in sync with active client subscriptions.
  - Core methods:
    ```java
    /** Register that a subscription covers a set of wave IDs. */
    void registerSubscription(ParticipantId user, String query, Set<WaveId> waveIds);

    /** Unregister a subscription (user closed search or disconnected). */
    void unregisterSubscription(ParticipantId user, String query);

    /** Find all subscriptions potentially affected by a wave change. */
    Set<SubscriptionKey> getAffectedSubscriptions(WaveId waveId);

    /** Update the wave set for a subscription (after re-search). */
    void updateSubscriptionWaves(ParticipantId user, String query, Set<WaveId> waveIds);
    ```
  - `getAffectedSubscriptions()`:
    1. Look up `waveToSubscriptions.get(waveId)` -- O(1)
    2. For unknown waves (not in forward index): do **not** rely solely on Bloom filter. Instead, perform a bounded re-evaluation: identify affected users from the wave's participant list, then for each user's active queries, run `SearchProvider.search()` to check membership. This avoids Bloom filter false positives silently corrupting results. Cap re-eval to the user's active subscription set (typically 1-3 queries).
    3. Return the union
  - Bloom filter: Guava `BloomFilter.create(Funnels.unencodedCharsFunnel(), expectedWaves, 0.01)` -- 1% false positive rate, rebuilt on `updateSubscriptionWaves()`
  - Memory estimate: 1000 users x 3 queries each x (64 bytes per entry + 1KB Bloom filter) = ~3.2 MB

### Files to Modify

- [ ] **`wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveServerModule.java`**
  - Add bindings:
    ```java
    bind(SearchWaveletUpdater.class).in(Singleton.class);
    bind(SearchIndexer.class).in(Singleton.class);
    ```

- [ ] **`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`**
  - Register `SearchWaveletUpdater` **after** `PerUserWaveViewDistpatcher` (in `initializeSearch()`) so that the per-user wave view index is current before search wavelet updates are computed:
    ```java
    // After waveBus.subscribe(waveViewDistpatcher):
    if (config.hasPath("search.ot_search_enabled") && config.getBoolean("search.ot_search_enabled")) {
      SearchWaveletUpdater searchUpdater = injector.getInstance(SearchWaveletUpdater.class);
      waveBus.subscribe(searchUpdater);
      LOG.info("SearchWaveletUpdater subscribed to WaveBus (ot-search enabled)");
    }
    ```
  - Feature flag: use `config.hasPath("search.ot_search_enabled") && config.getBoolean("search.ot_search_enabled")` for server-side gating

- [ ] **`wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`**
  - Same WaveBus registration as jakarta override (keep both in sync)

### Verification
- `sbt wave/compile` succeeds
- Unit tests: `SearchIndexerTest`, `SearchWaveletUpdaterTest` (see Phase 5)

---

## Phase 3: SearchWaveletDataProvider (~200 LOC)

### Goal
Compute the DocOp diff between old and new search results, generating the minimal mutation to update a search wavelet's document.

### Files to Create

- [ ] **`wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletDataProvider.java`**
  - Package: `org.waveprotocol.box.server.waveserver.search`
  - `@Singleton`, no injected dependencies (pure function)
  - Core method:
    ```java
    /**
     * Compute the DocOp that transforms oldResults into newResults.
     *
     * @param oldResults the current search result list in the wavelet
     * @param newResults the desired search result list
     * @return a DocOp that when applied to the document produces newResults,
     *         or null if no changes are needed
     */
    @Nullable DocOp computeDiff(List<SearchResultEntry> oldResults,
                                 List<SearchResultEntry> newResults);
    ```
  - `SearchResultEntry` value class:
    ```java
    record SearchResultEntry(
        String waveId,     // stable @id attribute
        String title,
        String snippet,
        long modified,
        String creator,
        int participants,
        int unread,
        int blips
    ) {}
    ```
  - Diff algorithm:
    1. Build `Map<String, SearchResultEntry>` for old results keyed by `waveId`
    2. Build `Map<String, SearchResultEntry>` for new results keyed by `waveId`
    3. Compute three sets: `added` (in new, not in old), `removed` (in old, not in new), `modified` (in both, attributes differ)
    4. Also detect `reordered` entries (position changed)
    5. Generate DocOp using `DocOpBuilder`:
       - For removed entries: `retain` to position, `deleteElementStart`, `deleteElementEnd`
       - For added entries: `retain` to insertion point, `elementStart("result", attrs)`, `elementEnd`
       - For modified entries: `retain` to position, `updateAttributes(oldAttrs, newAttrs)`
       - For reordered entries: delete at old position + insert at new position
    6. Operations are composed in document order (front-to-back) to maintain valid retain counts
  - Also update `<metadata>` element's `updated` and `total` attributes if they changed
  - **Important:** DocOp `characters()` calls can be split by `annotationBoundary` -- always use `StringBuilder` when accumulating text. However, search wavelets are attribute-only (no text content), so this is low risk. Include a comment noting the caveat for future maintainers.

### Verification
- `sbt wave/compile` succeeds
- Unit test: included in `SearchWaveletDataProviderTest` (see Phase 5)

---

## Phase 4: Client Integration (~150 LOC)

### Goal
When the `ot-search` feature flag is enabled, subscribe to search wavelets via `openRequest()` instead of polling. Fall back to polling if subscription fails.

### Client API Architecture

Create a dedicated `OtSearchService` that uses `RemoteViewServiceMultiplexer` for wavelet subscriptions. Either extend the existing `Search` interface with an `OtSearch` implementation, or create a parallel `OtSearch` impl that wraps `Search` and delegates rendering to the existing `Search.Listener` / `SearchPresenter` pipeline. The `OtSearchService` must:
- Register/unregister wavelet subscriptions tied to actual open/close events
- Store the canonical raw query string alongside its MD5 hash so that on reconnect or query change the correct subscription can be identified and torn down
- Handle the OT delta stream and project it into the `Search` model

### Files to Modify

- [ ] **`wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`**
  - Add feature-flag check at initialization:
    ```java
    // In create() or constructor:
    if (Session.get().hasFeature("ot-search")) {
      subscribeToSearchWavelet(queryText);
    } else {
      startPolling();
    }
    ```
  - New method `subscribeToSearchWavelet(String query)`:
    1. Compute the `WaveletName` for the current user + query (same MD5 logic as server)
    2. Call `openRequest()` via the existing `WaveClientRpcImpl` RPC channel
    3. Register an `OpenListener` that receives deltas
    4. On delta received: parse the search wavelet document, extract `<result>` elements, update the `Search` model
    5. The existing `Search.Listener` / rendering pipeline handles the rest
  - New method `onSearchWaveletDelta(...)`:
    1. Apply delta to local copy of search wavelet document
    2. Extract result list from updated document
    3. Call `search.setResults(results)` or equivalent to update the model
    4. Trigger re-render via existing `renderer` task
  - Fallback: if `openRequest()` fails or connection drops:
    1. Log warning
    2. Set `useOtSearch = false`
    3. Call `startPolling()` to resume 15s polling
    4. On reconnect, attempt to re-subscribe
  - On query change (`onQuerySubmitted`):
    1. If OT mode: unsubscribe from old wavelet, subscribe to new one (wire unsubscribe to actual close event)
    2. If polling mode: existing behavior (no change)
  - **Keep all polling code intact.** Do not delete or refactor the existing `searchUpdater` IncrementalTask or `POLLING_INTERVAL_MS`. The feature flag gates between the two paths.

### UI Changes
- None. The search panel, digest views, and rendering are unchanged. Only the data source changes (push vs poll).

### Verification
- `sbt compileGwt` succeeds
- Manual test: enable `ot-search` flag, verify search updates appear in <1s after wave change
- Manual test: disable flag, verify 15s polling still works

---

## Phase 5: Testing & Rollout

### Unit Tests

- [ ] **`wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletManagerTest.java`**
  - JUnit 3 (matching `ContactManagerImplTest` conventions)
  - Tests:
    - `testGetOrCreateSearchWavelet_createsNewWavelet` -- verifies wavelet is created with correct ID and initial document
    - `testGetOrCreateSearchWavelet_returnsSameForSameQuery` -- idempotency
    - `testGetOrCreateSearchWavelet_differentQueriesDifferentWavelets` -- isolation
    - `testComputeWaveletName_deterministicMd5` -- MD5 stability
    - `testIsSearchWavelet_trueForSearchWavelets` -- prefix matching
    - `testIsSearchWavelet_falseForConversationWavelets` -- negative case
    - `testRemoveSearchWavelet_cleansUp` -- removal

- [ ] **`wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchIndexerTest.java`**
  - Tests:
    - `testRegisterSubscription_forwardAndReverseIndex` -- both indexes populated
    - `testGetAffectedSubscriptions_returnsCorrectSet` -- known wave returns correct subscriptions
    - `testGetAffectedSubscriptions_unknownWave_usesBloomFilter` -- new wave checked against Bloom filters
    - `testUnregisterSubscription_removesFromBothIndexes` -- cleanup
    - `testUpdateSubscriptionWaves_updatesIndexes` -- re-index after search result change
    - `testBloomFilter_lowFalsePositiveRate` -- statistical test with 1000 entries

- [ ] **`wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletUpdaterTest.java`**
  - Tests:
    - `testWaveletUpdate_triggersSearchWaveletUpdate` -- end-to-end: wave change -> search wavelet delta
    - `testWaveletUpdate_skipsSearchWavelets` -- no recursive loop
    - `testBatching_multipleUpdatesCoalesced` -- two rapid updates within 100ms produce one search update
    - `testBatching_updatesAfterWindowFireSeparately` -- updates after batch window are separate
    - `testErrorInOneUpdate_doesNotBlockOthers` -- resilience
    - `testFeatureFlagGating_disabledSkipsRegistration` -- when config flag is off, updater is not subscribed
    - `testOtToPollingFallback_onError` -- on update error, client should fallback to polling gracefully
    - `testQueryChangeUnsubscribe` -- changing query unsubscribes old wavelet before subscribing new one
    - `testPagination_largeResultSets` -- search results beyond page size are correctly paginated in wavelet
    - `testNewWaveVisibility_appearsInResults` -- newly created wave with user as participant appears in search

- [ ] **`wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletDataProviderTest.java`**
  - Tests:
    - `testComputeDiff_addedResult` -- new entry generates elementStart/elementEnd
    - `testComputeDiff_removedResult` -- removed entry generates deleteElementStart/deleteElementEnd
    - `testComputeDiff_modifiedResult` -- changed attributes generate updateAttributes
    - `testComputeDiff_reorderedResult` -- position change generates delete+insert
    - `testComputeDiff_noChanges_returnsNull` -- identity case
    - `testComputeDiff_metadataUpdate` -- total/updated attributes change

### Integration Test

- [ ] **`wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletIntegrationTest.java`**
  - Wires up `SearchWaveletManager`, `SearchWaveletUpdater`, `SearchIndexer`, and `SearchWaveletDataProvider` with mock `WaveMap` and `SearchProvider`
  - Test flow:
    1. User subscribes to `in:inbox` search
    2. A new wave is created with user as participant
    3. `waveletUpdate()` is fired on the updater
    4. Verify the search wavelet receives a delta containing the new result
    5. Wave is modified (new blip)
    6. Verify the search wavelet receives an update delta with changed `modified` timestamp
    7. User is removed from wave
    8. Verify the search wavelet receives a delete delta removing the result

### Rollout Plan

| Stage | % Users | Duration | Gate |
|-------|---------|----------|------|
| 1. Internal dogfood | 0% (team only) | 1 week | Manual flag override |
| 2. Canary | 10% | 1 week | No error rate increase, <100ms p99 |
| 3. Partial rollout | 50% | 1 week | Bandwidth reduction confirmed, CPU within budget |
| 4. Full rollout | 100% | -- | All success criteria met |
| 5. Polling removal | 100% | After 2 weeks stable | No fallback activations in 7 days |

### A/B Metrics

- **Latency:** Time from wave change to search panel update (OT path vs polling path)
- **Bandwidth:** Bytes transferred per minute for search (delta size vs full response size)
- **CPU:** Server CPU utilization (search wavelet maintenance overhead)
- **Fallback rate:** How often OT subscribers fall back to polling
- **Error rate:** Failed search wavelet updates / total updates

---

## New Files Summary

| # | File | LOC (est.) | Phase |
|---|------|-----------|-------|
| 1 | `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletManager.java` | ~300 | 1 |
| 2 | `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletUpdater.java` | ~350 | 2 |
| 3 | `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchIndexer.java` | ~250 | 2 |
| 4 | `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletDataProvider.java` | ~200 | 3 |
| 5 | `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletManagerTest.java` | ~150 | 5 |
| 6 | `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchIndexerTest.java` | ~200 | 5 |
| 7 | `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletUpdaterTest.java` | ~200 | 5 |
| 8 | `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletDataProviderTest.java` | ~150 | 5 |
| 9 | `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletIntegrationTest.java` | ~250 | 5 |

**Total new code:** ~2,050 LOC (1,100 production + 950 test)

## Modified Files Summary

| # | File | Change | Phase |
|---|------|--------|-------|
| 1 | `wave/src/main/java/org/waveprotocol/box/server/waveserver/WaveServerModule.java` | Guice bindings for `SearchWaveletManager`, `SearchWaveletUpdater`, `SearchIndexer`, `SearchWaveletDataProvider` | 1-3 |
| 2 | `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java` | Register `SearchWaveletUpdater` as `WaveBus` subscriber | 2 |
| 3 | `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java` | Same WaveBus registration (javax build) | 2 |
| 4 | `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java` | Feature-flag gated OT subscription path, fallback to polling | 4 |

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|-----------|
| **Write amplification** -- a single popular wave change triggers updates to many search wavelets | Medium | High CPU spike | 100ms batching window coalesces rapid changes; Bloom filter rejects non-matching subscriptions in O(1); per-user throttle of 1 update/second |
| **OT operation bugs** -- malformed DocOps corrupt search wavelet documents | Medium | Data corruption (search only, not conversation data) | Extensive unit tests for `SearchWaveletDataProvider`; server-side DocOp validation before submit; client-side validation on receive; search wavelets are ephemeral and can be recreated |
| **Index consistency** -- `SearchIndexer` in-memory state drifts from actual wave state | Low | Missing or stale search results | Periodic full reindex (every 5 minutes); dual-path validation in canary phase (compare OT results with polling results); index rebuilt on server restart |
| **Subscription leaks** -- user disconnects but search wavelet subscription is not cleaned up | Low | Memory leak, wasted CPU | 5-minute idle timeout on subscriptions; cleanup on WebSocket close; bounded subscription count per user (max 10) |
| **Recursive update loop** -- search wavelet update triggers another WaveBus event that triggers another search update | Low | Infinite loop, stack overflow | `isSearchWavelet()` guard in `SearchWaveletUpdater.waveletUpdate()` skips all wavelets with `search+` prefix |
| **Feature flag dependency** -- `Session.get().hasFeature()` | Low | Cannot gate client behavior | Feature flags (#374) is merged; server-side uses Typesafe Config boolean |

---

## Dependencies

| Dependency | Status | Blocking? | Notes |
|------------|--------|-----------|-------|
| Feature flags system (#374) | Merged | No | Client-side gating uses `Session.get().hasFeature("ot-search")`. Server-side gating uses `config.hasPath("search.ot_search_enabled") && config.getBoolean("search.ot_search_enabled")`. |
| `WaveBus` subscriber registration | Exists | No | Already used by `ContactsRecorder`; same pattern |
| `SearchProvider.search()` | Exists | No | `SimpleSearchProviderImpl` already provides this |
| `DocOpBuilder` / DocOp infrastructure | Exists | No | Mature OT infrastructure in `wave/src/main/java/org/waveprotocol/wave/model/document/operation/` |
| Guava `BloomFilter` | Exists in deps | No | Already a transitive dependency via Guava |

---

## Success Criteria

| Metric | Target | Measurement |
|--------|--------|-------------|
| Search update latency (p50) | <100ms | Timestamp diff: wave change event -> search wavelet delta received by client |
| Search update latency (p99) | <500ms | Same measurement, 99th percentile |
| Bandwidth reduction | >80% | Compare bytes/minute for search traffic: OT deltas vs polling JSON responses |
| Server CPU increase | <5% | Compare CPU utilization with and without `ot-search` enabled |
| Data correctness | 100% match | In canary phase: compare OT search results with polling results for same query; zero divergence |
| Fallback rate | <1% | Percentage of OT subscribers that fall back to polling |
| Zero data corruption | 0 incidents | No corrupted search wavelet documents in production |
| Graceful degradation | Transparent | Users on polling path see no behavior change; users on OT path seamlessly fall back to polling on error |

---

## Implementation Order

```
Phase 1 (SearchWaveletManager)          ← no dependencies, start here
    ↓
Phase 2 (SearchWaveletUpdater +         ← depends on Phase 1
         SearchIndexer)
    ↓
Phase 3 (SearchWaveletDataProvider)     ← depends on Phase 1-2
    ↓
Phase 5a (Unit + Integration Tests)     ← depends on Phase 1-3
    ↓
Phase 4 (Client Integration)            ← depends on Phase 1-3 (feature flags #374 is merged)
    ↓
Phase 5b (Rollout)                      ← depends on all phases
```

Phases 1-3 and 5a can be developed and merged independently. Phase 4 depends on Phases 1-3; feature flags (#374) is already merged.
