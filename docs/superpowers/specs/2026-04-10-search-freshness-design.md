# Search Freshness Regression Design

## Goal

Restore search freshness for two production regressions:

1. A newly added participant must see the wave and its content in search.
2. A newly added tag must be searchable via `tag:<newtag>`.

## Investigation Summary

### Runtime path

- The runtime-active Jakarta wiring uses
  `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/SearchModule.java`.
- In `core.search_type=lucene`, no-text queries still flow through the legacy
  provider stack:
  `FeatureFlaggedSearchProviderImpl -> SimpleSearchProviderImpl ->
  MemoryPerUserWaveViewHandlerImpl`.
- `tag:<newtag>` is a no-text query, so it is not primarily decided by the
  Lucene full-text candidate index.
- The default inbox search used by login/search-panel bootstrap is also served
  by the legacy provider path.

### What is already covered

- `SimpleSearchProviderImplTest` already proves that the pure tag filter logic
  works when the provider is fed a correct per-user wave view and fresh wavelet
  data.
- `SimpleSearchProviderImplTest` also proves explicit participant visibility in
  isolation.
- Existing tests do not cover the combined runtime seam:
  `MemoryPerUserWaveViewHandlerImpl + SimpleSearchProviderImpl + real WaveMap`
  across create/search/share/content/tag mutations.

### Highest-risk recent change

- The most freshness-sensitive recent server change is the `30s` reload
  cooldown added in
  `wave/src/main/java/org/waveprotocol/box/server/waveserver/MemoryPerUserWaveViewHandlerImpl.java`
  by commit `c3fd48a23`.
- That optimization has no regression coverage for the issue scenario:
  cached search state, new wave mutations, a second user's first search, and a
  subsequent tag search on the same wave.

## Approaches Considered

### 1. Patch Lucene indexing only

Rejected.

- `tag:<newtag>` without free text bypasses Lucene candidate filtering.
- Inbox bootstrap/search visibility for a newly added participant also depends
  on the legacy provider path.

### 2. Patch only the webclient refresh behavior

Rejected as incomplete.

- A client refresh patch could mask stale data in an already-open search panel,
  but it would not fix server-side search bootstrap for a participant who logs
  in after being added.

### 3. Reproduce on the actual runtime server path, then fix the narrowest
search freshness seam

Recommended.

- Add regression coverage on the real server-side path first.
- Use the failing coverage to decide whether the bug is:
  - stale per-user wave-view rebuild state,
  - stale wavelet snapshot visibility during search evaluation,
  - or a separate live-search refresh seam that must also be covered.

## Design

### Server regression coverage

Add focused regression coverage that uses:

- real `WaveMap`
- real `MemoryPerUserWaveViewHandlerImpl`
- real `SimpleSearchProviderImpl`
- the exact issue sequence:
  - user A creates a wave
  - user A sees it in search
  - user A adds participant B and content
  - user B sees the wave/content in search
  - user A adds a tag
  - `tag:<newtag>` returns the wave

This closes the current test gap where search logic is tested without the
runtime per-user view freshness layer.

### Expected fix seam

The first fix target is the runtime freshness boundary, not the Lucene parser.

The implementation should prefer the smallest correction that makes the
combined regression pass, likely in one of these places:

- `MemoryPerUserWaveViewHandlerImpl` if the rebuild/warmup/cooldown logic can
  serve a stale participant view.
- `SimpleSearchProviderImpl` if the provider is reusing stale wave data during
  the same search lifecycle.
- Search freshness orchestration if the participant and tag regressions prove
  to be separate, but adjacent, server-side seams.

### End-to-end coverage

Extend the existing Java E2E suite to assert:

- Bob's search result includes the shared wave after Alice adds Bob and writes
  content.
- A direct `tag:<newtag>` search returns the wave after Alice adds a tag.

This is appropriate because the bug report is about user-visible freshness
through the running server, not only isolated helper behavior.

## Non-Goals

- No unrelated search UI cleanup.
- No Lucene ranking/query behavior changes outside the freshness fix.
- No broad refactor of search providers beyond the minimal seam needed for the
  regressions.

## Verification Plan

- Targeted JVM tests for the new combined regression.
- Targeted E2E run for the shared-wave search scenario.
- Narrow local server verification against `/healthz` plus the exact search
  flow exercised by the regression.
