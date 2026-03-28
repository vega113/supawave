# OT Search Tag Filter Native Implementation Plan

> **For agentic workers:** REQUIRED: Use the repo orchestration workflow in `docs/superpowers/plans/2026-03-18-agent-orchestration-plan.md`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the `tag:` query OT-search bypass and make OT search publish native tag-filtered results from the same raw-query search model that powers direct search responses.

**Architecture:** Treat `SearchProvider.search(user, rawQuery, ...)` as the single source of truth for both HTTP search responses and OT search wavelet snapshots. Use the existing deterministic `SearchWaveletManager.computeWaveletName(user, query)` mapping as the query-to-wavelet registry, add the missing server-side live-search bridge so bootstrap and incremental OT updates publish search wavelet snapshots for any supported raw query, and prune stale entries lazily when no active subscription remains. This keeps OT search additive and makes future filters use the same seam instead of adding per-filter fallbacks.

**Tech Stack:** Java server/frontend, WaveBus subscriptions, GWT search presenter, Jakarta override servlet/server path, JUnit 3 tests, Mockito, SBT.

---

## Root Cause

- `PR #439` fixes tag queries by short-circuiting `SearchPresenter.bootstrapOtSearch()` through `SearchQueryMode.supportsOtSearch()`.
- That bypass is only a symptom fix. The branch-local OT path has client subscription logic and a `SearchWaveletUpdater`, but it is missing the server-side snapshot/bootstrap bridge that publishes `SearchProvider` results into live search wavelet subscriptions.
- Because the bridge is missing, OT search cannot act as the real filter implementation. The correct seam is the raw query string itself, not a client-side allowlist.
- The deterministic `search~<user>` plus `search+<md5(query)>` naming scheme already gives us a stable registry key. What is missing is the publishing and cleanup flow around that key.

## File Map

**Create:**

- `wave/src/main/java/org/waveprotocol/box/server/frontend/SearchWaveletDispatcher.java`
- `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletSnapshotPublisher.java`
- `wave/src/test/java/org/waveprotocol/box/server/rpc/SearchServletTest.java`
- `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletSnapshotPublisherTest.java`

**Modify:**

- `docs/superpowers/plans/2026-03-28-ot-search-tag-filter-native-plan.md`
- `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`
- `wave/src/main/java/org/waveprotocol/box/server/frontend/UserManager.java`
- `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveViewSubscription.java`
- `wave/src/main/java/org/waveprotocol/box/server/rpc/SearchServlet.java`
- `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/SearchServlet.java`
- `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchIndexer.java`
- `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletUpdater.java`
- `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`
- `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletUpdaterTest.java`
- `wave/src/test/java/org/waveprotocol/box/webclient/search/SearchPresenterTest.java`

**Delete if left unused after implementation:**

- `wave/src/main/java/org/waveprotocol/box/common/search/SearchQueryMode.java`
- `wave/src/test/java/org/waveprotocol/box/common/search/SearchQueryModeTest.java`

## Chunk 1: Restore The Server-Side OT Snapshot Bridge

### Task 1: Add a dispatcher that can push synthetic search wavelet snapshots to active frontend subscriptions

**Files:**

- Create: `wave/src/main/java/org/waveprotocol/box/server/frontend/SearchWaveletDispatcher.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/frontend/UserManager.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveViewSubscription.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`

- [ ] **Step 1: Write the failing test**

Add a test that opens a filtered frontend subscription to a search wavelet and verifies a synthetic `CommittedWaveletSnapshot` can be delivered to the subscribed listener without going through `WaveletProvider.getSnapshot(...)`.

- [ ] **Step 2: Run the test to verify it fails**

Run: targeted JUnit test for the new dispatcher/subscription path  
Expected: missing dispatcher/snapshot hook methods

- [ ] **Step 3: Implement the minimal bridge**

Add:

- `SearchWaveletDispatcher` to hold `WaveletInfo` and publish snapshots to `UserManager`
- `UserManager.onSnapshot(...)` and `UserManager.hasSubscription(...)`
- `WaveViewSubscription.onSnapshot(...)` so snapshot versions become the subscription baseline
- `ServerMain.initializeFrontend(...)` in both main and Jakarta paths to initialize the dispatcher once `WaveletInfo` exists

- [ ] **Step 4: Run the test to verify it passes**

Run the same targeted JUnit test  
Expected: PASS

## Chunk 2: Publish Raw-Query Search Results Into OT Search Wavelets

### Task 2: Add a snapshot publisher that turns `SearchProvider` output into search wavelet snapshots and subscription index state

**Files:**

- Create: `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletSnapshotPublisher.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletSnapshotPublisherTest.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchIndexer.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletUpdater.java`

- [ ] **Step 1: Write the failing tests**

Add coverage for:

- bootstrap publishing only when a live subscription exists
- update publishing serializing concurrent result changes
- index registration/update using raw query plus query hash
- stale live-search entries being pruned when a mapped search wavelet no longer has an active subscription

- [ ] **Step 2: Run the tests to verify they fail**

Run: targeted publisher/indexer/updater tests  
Expected: missing publisher class and missing register-or-update behavior

- [ ] **Step 3: Implement the minimal publisher path**

Implement:

- `SearchWaveletSnapshotPublisher.publishBootstrap(...)`
- `SearchWaveletSnapshotPublisher.publishUpdate(...)`
- stable per-wavelet versioning for synthetic snapshots
- `SearchIndexer.registerOrUpdateSubscription(...)`
- `SearchWaveletUpdater` delegation to the publisher so wave changes re-run the same raw query and republish results
- recursion protection by reusing the existing `SearchWaveletManager.isSearchWavelet(...)` guard before any live re-search work is enqueued
- lazy pruning in the publisher/updater path:
  - if `SearchWaveletDispatcher.hasSubscription(...)` is false for a computed wavelet, remove the `SearchWaveletManager` mapping and `SearchIndexer` subscription for that `(user, queryHash)` pair instead of republishing
  - do this synchronously on bootstrap/update checks; do not add a background sweeper or new runtime config in this change

Constraint:

- do not add any legacy/simple-search fallback path for `tag:` or other OT-eligible queries

- [ ] **Step 4: Run the tests to verify they pass**

Run the same targeted publisher/indexer/updater tests  
Expected: PASS

## Chunk 3: Bootstrap OT Search From The Search Servlet For Any Raw Query

### Task 3: Make HTTP search bootstrap populate the subscribed OT search wavelet window

**Files:**

- Modify: `wave/src/main/java/org/waveprotocol/box/server/rpc/SearchServlet.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/SearchServlet.java`
- Create: `wave/src/test/java/org/waveprotocol/box/server/rpc/SearchServletTest.java`

- [ ] **Step 1: Write the failing tests**

Add servlet tests covering:

- no duplicate canonical live-window fetch when request already matches the live OT window
- canonical bootstrap republish when a live search subscription exists
- no extra bootstrap fetch when no live OT subscription exists

- [ ] **Step 2: Run the tests to verify they fail**

Run: targeted `SearchServletTest`  
Expected: constructor/signature or behavior mismatch

- [ ] **Step 3: Implement canonical OT bootstrap behavior**

Update both servlet variants to:

- accept `SearchWaveletSnapshotPublisher`
- perform the normal requested search response
- when the current query has an active OT search subscription, repopulate the canonical live OT window using the same raw query
- publish the bootstrap snapshot from that canonical result set

Design note:

- The canonical OT window should be owned server-side and independent of the currently requested page size so future filters such as unread or archive reuse the same publishing model.
- The client does not need the server to mint and return a new wavelet id; both sides already derive the same deterministic `WaveletName` from `(user, query)`.

- [ ] **Step 4: Run the tests to verify they pass**

Run the same targeted servlet tests  
Expected: PASS

## Chunk 4: Remove The Client Tag Bypass And Keep OT Search Query-Agnostic

### Task 4: Remove `SearchQueryMode` gating and let OT search subscribe for `tag:` queries

**Files:**

- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/webclient/search/SearchPresenterTest.java`
- Delete: `wave/src/main/java/org/waveprotocol/box/common/search/SearchQueryMode.java`
- Delete: `wave/src/test/java/org/waveprotocol/box/common/search/SearchQueryModeTest.java`

- [ ] **Step 1: Write the failing test**

Add/adjust `SearchPresenterTest` so `bootstrapOtSearch()` attempts OT subscription for `tag:` queries instead of forcing direct-search polling.

- [ ] **Step 2: Run the test to verify it fails**

Run: targeted `SearchPresenterTest`  
Expected: current client gate disables OT subscription for `tag:` queries

- [ ] **Step 3: Implement the minimal fix**

Remove:

- `SearchQueryMode`
- the `supportsOtSearch` gate in `bootstrapOtSearch()`

Keep:

- existing runtime fallbacks for genuinely broken OT transport/bootstrap conditions

Do not keep:

- query-type-specific fallback or bypass logic

- [ ] **Step 4: Run the test to verify it passes**

Run the same targeted `SearchPresenterTest`  
Expected: PASS

## Chunk 5: Regression Coverage And Verification

### Task 5: Prove native OT tag filtering works locally and record the exact commands

**Files:**

- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImplTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/waveserver/search/SearchWaveletUpdaterTest.java`
- Modify: PR summary / final lane output

- [ ] **Step 1: Keep the existing simple-search tag regression test**

Preserve the existing server-side tag filter regression so the raw-query search model still proves `tag:` itself is correct.

- [ ] **Step 2: Add OT-path regression coverage**

Add tests showing OT snapshot publication and updater re-evaluation work with a raw `tag:` query.

- [ ] **Step 3: Run targeted tests**

Run:

- `sbt test:compile`
- targeted JUnit for:
  - `org.waveprotocol.box.server.rpc.SearchServletTest`
  - `org.waveprotocol.box.server.waveserver.search.SearchWaveletSnapshotPublisherTest`
  - `org.waveprotocol.box.server.waveserver.search.SearchWaveletUpdaterTest`
  - `org.waveprotocol.box.webclient.search.SearchPresenterTest`
  - `org.waveprotocol.box.server.waveserver.SimpleSearchProviderImplTest`

Expected:

- all targeted tests PASS

- [ ] **Step 4: Run local server verification before PR**

Required local verification:

- boot the server in this worktree with OT search enabled
- load the client with the OT-search feature enabled
- create or update a wave tag
- confirm a `tag:<value>` search stays on the OT path and updates without the legacy bypass

Record:

- exact server start command
- exact manual or scripted verification steps
- exact observed result

## Out Of Scope

- adding a brand-new `unread:` query token or redesigning the full search query grammar
- solving all OT search transport/runtime fallback paths in this change
- broad refactors outside the OT search bootstrap/update seam

## Follow-Up Gaps To Call Out After Landing

- OT search still depends on `SearchProvider.search(...)` for result computation; that is correct for now, but future work may want a richer shared query capability model instead of implicit raw-query semantics.
- `unread` is only represented today as digest metadata, not a first-class query token. Supporting an explicit unread filter should use the same raw-query OT publishing seam added here rather than another client bypass.
- Archive, inbox, pinned, title, and content filters should be verified one by one against the same OT snapshot bridge now that the architecture is query-agnostic again.
- Search subscription cleanup is necessarily lazy because the current client/server view protocol has no explicit close RPC for search streams. This change should prune inactive search wavelets on bootstrap/update checks, and a future protocol-level close signal would let that cleanup become exact.
