# Server-First Blocks Adoption Plan

Owner: Migration Engineering
Last updated: 2025-09-07

Statuses: planned | in_progress | completed

-------------------------------------------------------------------------------

## Delta Since Last Edit (2025-09-07)

Reference-centric summary (see docs/fragments-viewport-behavior.md and docs/fragments-config.md):
- Phase 3 (FragmentsFetcher & Request) and Phase 4 (Server Endpoint & Transport Prep) are completed (compat); associated code and tests are in place.
- Server emits fragments under a feature flag; HTTP endpoint and RPC wiring are gated. Defaults and operational guidance live in the fragments config reference.
- Failure modes fall back safely and are observable via metrics; details and diagrams live in the viewport behavior note.

### Verification Details (2025-09-07)

- Implemented features (server):
  - Fragments fetcher and helpers: `wave/src/main/java/org/waveprotocol/box/server/frontend/FragmentsFetcherCompat.java`
  - ViewChannel fragments handler (flag-gated): `wave/src/main/java/org/waveprotocol/box/server/frontend/FragmentsViewChannelHandler.java`
  - ViewChannel bridge (server wiring): `wave/src/main/java/org/waveprotocol/box/server/frontend/FragmentsFetchBridgeImpl.java`
  - RPC emission (ProtocolFragments): `wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java`
  - HTTP endpoint (gated): `wave/src/main/java/org/waveprotocol/box/server/rpc/FragmentsServlet.java`
  - Manifest-order cache (Caffeine: LRU + TTL): `wave/src/main/java/org/waveprotocol/box/server/frontend/ManifestOrderCache.java`
  - Segment state registry (LRU+TTL): `wave/src/main/java/org/waveprotocol/box/server/waveletstate/segment/SegmentWaveletStateRegistry.java`

- Unit/integration tests present (selected):
  - Request + ordering: `FragmentsRequestTest`, `FragmentsOrderingTest`
  - RPC emission path (fragments presence + viewport limits): `WaveClientRpcFragmentsTest`, `WaveClientRpcViewportHintsTest`
  - ViewChannel bridge: `FragmentsFetchBridgeImplTest`
  - Compat segment state: `SegmentWaveletStateCompatTest`
  - HTTP gating: `wave/src/test/java/org/waveprotocol/box/server/FragmentsHttpGatingTest.java`
  - Manifest-order cache: `wave/src/test/java/org/waveprotocol/box/server/frontend/ManifestOrderCacheTest.java`
  - Segment state registry: `wave/src/test/java/org/waveprotocol/box/server/waveletstate/segment/SegmentWaveletStateRegistryTest.java`, `.../SegmentWaveletStateRegistryConcurrencyTest.java`
  - Fragment requester shaping: `wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/FragmentRequesterTest.java`
  - Stress-style cache/registry tests (excluded from default test task; run via `:wave:testStress`): `wave/src/test/java/org/waveprotocol/box/server/frontend/FragmentsCachesStressTest.java` (see docs/fragments-stress-tests.md for expected outcomes).

- Logging (key points and levels):
  - Config read failures (flags, cache sizes/TTLs): INFO with defaults applied.
  - Viewport clamping/invalid inputs: FINE in `WaveClientRpcImpl`.
  - Fallbacks (selection/emit failures): WARN with wavelet context in `FragmentsViewChannelHandler` and `WaveClientRpcImpl`.
  - Boot log for applier wiring: INFO in `ServerMain` (enabled, impl, warnMs).

- Metrics (FragmentsMetrics counters):
  - Emission: `emissionCount`, `emissionRanges`, `emissionErrors`, `emissionFallbacks`.
  - Compute/fallbacks: `computeFallbacks`, `viewportAmbiguity`.
  - HTTP (when `/fragments` enabled): `httpRequests`, `httpOk`, `httpErrors`.
  - Applier: `applierEvents`, `applierDurationsMs`.
  - Definitions: `wave/src/main/java/org/waveprotocol/wave/concurrencycontrol/channel/FragmentsMetrics.java`.
  - Visibility: `/statusz?show=fragments` (implementation in `wave/src/main/java/org/waveprotocol/box/server/stat/StatuszServlet.java`).

- Gating and configuration locations:
  - Server gates and caches: `wave/config/reference.conf` under `server.*` and `wave.fragments.*` (see also docs/fragments-config.md).
  - Startup validation and wiring: `ServerMain.initializeFrontend` and `ServerMain.initializeServlets` (flags applied, invalid values fail fast).

New configuration and client API (viewport hints)
- Config (Typesafe):
  - `wave.fragments.defaultViewportLimit` (int, default 5): default number of blip segments to include when client hint limit is missing/invalid.
  - `wave.fragments.maxViewportLimit` (int, default 50): upper clamp for client-requested viewport limit.
  - Wired in ServerMain; values logged at startup and applied via `WaveClientRpcImpl.setViewportLimits`.
- Client API overload (backward-compatible):
  - `RemoteViewServiceMultiplexer.open(WaveId id, IdFilter filter, WaveWebSocketCallback stream, String viewportStartBlipId, String viewportDirection, int viewportLimit)`
    - `viewportStartBlipId`: blip id (e.g., "b+123") around which the server selects visible blips; null falls back to heuristics.
    - `viewportDirection`: "forward" or "backward"; invalid/blank treated as "forward".
    - `viewportLimit`: desired number of blip segments (excludes index/manifest); server clamps to configured range.
  - Older clients/servers ignore unknown fields; setters are called via reflection to remain compatible with older generated JSOs.

Code references
- Fragments & ranges: wave/src/main/java/org/waveprotocol/box/server/frontend/FragmentsFetcherCompat.java, FragmentsViewChannelHandler.java, FragmentsFetchBridgeImpl.java
- RPC emission: wave/src/main/java/org/waveprotocol/box/server/frontend/WaveClientRpcImpl.java
- Proto: wave/src/proto/proto/org/waveprotocol/box/common/comms/waveclient-rpc.proto
- Tests: wave/src/test/java/org/waveprotocol/box/server/frontend/*Fragments*.java; wave/src/test/java/org/waveprotocol/wave/concurrencycontrol/channel/RawFragmentsApplierTest.java

-------------------------------------------------------------------------------

## Why Blocks (Segments) Now

wiab.pro’s fragment fetch relies on a segment-oriented persistence API (blocks) to fetch only the portions of a wavelet needed for the current viewport. To achieve parity and long-term performance in Apache Wave, we will adopt a compatible server-side blocks interface first, then evolve transport and client application.

-------------------------------------------------------------------------------

## Phases Overview

1) Foundations & API Scaffolding (server-first)
2) Compatibility SegmentWaveletState (no storage migration yet)
3) FragmentsFetcher & Request (server logic)
4) Server Endpoint (HTTP JSON for bring-up) and Transport Prep
5) Client Applier (future) & RPC Evolution (WebSocket)
6) Rollout, Observability, and Cleanup

-------------------------------------------------------------------------------

## Phase 1 — Foundations & API Scaffolding (server-first)

Status: completed (compat)

Goal: Introduce core types and interfaces needed by a segment-based fetch path, behind flags and without changing runtime behavior.

### Task 1.1 — Define Core Types (SegmentId, VersionRange, Interval)

- Context:
  - wiab.pro declares SegmentId (INDEX, MANIFEST, PARTICIPANTS, TAGS, per-blip segments), VersionRange (from,to), and Interval (snapshot across a version range).
  - We add compatible types. Implementation will be minimal; behavior will be provided in Phase 2.

- Implementation:
  - Add `org.waveprotocol.wave.model.id.SegmentId` (static IDs for INDEX/MANIFEST/etc. and factory for blip segments; `isBlip()`), `equals/hashCode/compareTo`.
  - Add `org.waveprotocol.box.server.persistence.blocks.VersionRange` (inclusive `from()` and `to()`), static `of(long,long)`.
  - Add `org.waveprotocol.box.server.persistence.blocks.Interval` interface (marker + minimal `Object getSnapshot(long version)`; Phase 2 will add actual structure).

- Tests:
  - Unit: SegmentId equality/ordering; VersionRange boundary correctness; `of()` rejects invalid ranges (from > to).

- DoD:
  - Types compile; no references wired into runtime.

- Status: completed — commit 87383ee1
  - Summary: added SegmentId, VersionRange, Interval.

- AI implementer notes:
  - Keep API names aligned with wiab.pro to ease porting (even if implementations differ initially).

### Task 1.2 — SegmentWaveletState Interface

- Context:
  - wiab.pro provides a `SegmentWaveletState` that can return intervals for requested segments and version ranges.

- Implementation:
  - Add `org.waveprotocol.box.server.waveletstate.segment.SegmentWaveletState` with methods:
    - `Map<SegmentId, Interval> getIntervals(long version)`
    - `Map<SegmentId, Interval> getIntervals(Map<SegmentId, VersionRange> ranges, boolean onlyFromCache)`
    - Streaming variant with `Receiver<Pair<SegmentId, Interval>>`

- Tests:
  - Interface only; no tests required beyond compilation.

- DoD:
  - Interface compiles; no behavior wired.

- Status: completed — commit 87383ee1
  - Summary: added SegmentWaveletState and SegmentWaveletStateCompat scaffold.

### Task 1.3 — Flags & Config Defaults

- Context:
  - Keep blocks access fully gated. Config must live in `reference.conf` with overrides in `application.conf`.

- Implementation:
  - Add `client.flags.defaults` merging path already implemented for dynamic client flags.
  - Add server toggle key (future): `blocks.enable=true|false` (not yet used).

- Tests:
  - Config loads with default; overridden in `config/application.conf`.

- DoD:
  - Keys recognized by Typesafe Config; no behavior change when off.

-------------------------------------------------------------------------------

## Phase 2 — Compatibility SegmentWaveletState (no storage migration)

Status: in_progress

Goal: Provide a basic implementation that can return intervals reconstructed from the current store, sufficient for building fragment ranges and returning metadata.

### Task 2.1 — SegmentWaveletStateCompat

- Context:
  - Without the blocks backend, emulate minimal intervals from the existing `WaveletProvider`/snapshot and `Conversation` structures.

- Implementation:
  - Add `SegmentWaveletStateCompat` that:
    - Accepts a wavelet name and version; reconstructions are best-effort.
    - For INDEX/MANIFEST: produce pseudo-intervals from the latest snapshot.
    - For blip segments: record presence + creation “version” using `lastModifiedVersion` as an approximation.
  - This is for bring-up; real intervals will come later.

- Tests:
  - Unit: builds non-empty maps for INDEX/MANIFEST on a wavelet with content.
  - Integration (mock): fake a wavelet with a few blips; verify segment list contains corresponding blip segments.

- DoD:
  - Basic non-empty results for common cases; safe behavior when data is missing.

- Status: completed (compat) — commits 87383ee1, b25148d1
  - Summary: Added compat implementation returning pseudo intervals for INDEX/MANIFEST and blip segments based on snapshot author/mtime. Added unit test (SegmentWaveletStateCompatTest) to verify INDEX/MANIFEST presence and blip intervals.

- AI notes:
  - Emphasis on safety and logging; do not alter runtime unless called by Phase 3 explicitly.

-------------------------------------------------------------------------------

## Phase 3 — FragmentsFetcher & Request (server logic)

Status: in_progress

Goal: Port a compatible `FragmentsRequest` and `FragmentsFetcher` that compute VersionRanges using `SegmentWaveletState`.

### Task 3.1 — FragmentsRequest

- Context:
  - Mirror wiab.pro’s builder API to encode either (a) an explicit map of segment ranges, or (b) a common [start,end] version when no per-segment ranges exist.

- Implementation:
  - Port a builder with validation; apply Apache Wave package layout.

- Tests:
  - Unit: builder rejects conflicting inputs; produces the expected map; serializes toString sanely for logs.

- DoD:
  - Request object ready for fetcher; used only in Phase 3.2.

- Status: completed — commit 842be858
  - Summary: Added compat builder with validation and unit tests (FragmentsRequestTest) covering ranges/common versions and invalid inputs.

### Task 3.2 — FragmentsFetcher (compat)

- Context:
  - Using `SegmentWaveletState`, compute ranges for INDEX, MANIFEST, and a small set of blip segments around the viewport.

- Implementation:
  - Implemented `FragmentsFetcherCompat` helpers to:
    - List blips + metadata from a snapshot.
    - Compute manifest/document order and perform ordered slicing around a viewport.
    - Build `FragmentsRequest` and compute `VersionRange`s (INDEX/MANIFEST + visible blips) with `computeRangesForSegments`.
  - Added `FragmentsViewChannelHandler` (behind flag) and a `FragmentsFetchBridgeImpl` to expose ranges over ViewChannel for bring-up.

- Tests:
  - Unit: FragmentsRequestTest (builder validation), FragmentsOrderingTest (ordered slicing semantics).
  - Integration-lite: FragmentsFetchBridgeImplTest validates snapshotVersion propagation and range mapping.
  - RPC integration: WaveClientRpcFragmentsTest asserts fragments attach for both snapshot and delta-only updates when the handler is enabled.

- DoD:
  - Compat fetcher returns reasonable ranges for INDEX/MANIFEST and visible blips based on snapshot; safe under missing data. Emission over RPC is gated and logs failures.
  - Unit and integration-lite tests green.

-------------------------------------------------------------------------------

## Phase 4 — Server Endpoint & Transport Prep

Status: completed (compat)

Goal: Replace `/fragments` stub with real fetcher-backed JSON; spec the future WebSocket proto.

### Task 4.1 — HTTP `/fragments` backed by FragmentsFetcher

- Implementation:
  - Parse: waveRef (or path), startBlipId, direction, limit.
  - Call FragmentsFetcher; serialize JSON listing segment ids + version ranges + minimal metadata (author, lastModified, blip ids).
  - Config (current): endpoint is registered unconditionally in ServerMain and requires authentication.
    - Follow-up (recommended): add a server flag to gate HTTP fragments (e.g., `server.enableFragmentsHttp=false|true`) and register conditionally.

- Tests:
  - Contract: 200 OK for valid ref; JSON contains requested fields.
  - Error: invalid ref → 400; unauthorized → 403.

- DoD:
  - Endpoint returns structured results; safe to run in dev.

- Status: completed (compat) — commit d1aedf2b
  - Summary: Implemented FragmentsFetcherCompat (snapshot-based blip listing) and wired `/fragments` to return blip ids with author/mtime; accepts `ref`, `startBlipId`, `direction`, `limit`.
  - Follow-up: commit 51e7c932 (ordering/logging)
    - Added ASF header, robust ordering by (mtime,id), direction validation, and logging in compat fetcher.

### Task 4.2 — Proto & RPC handler (compat emission)

- Implementation:
  - Added ProtocolFragments/ProtocolFragmentRange in waveclient-rpc.proto; ProtocolWaveletUpdate now carries optional `fragments`.
  - Added viewport hints to ProtocolOpenRequest: `viewport_start_blip_id`, `viewport_direction`, `viewport_limit`.
  - WaveClientRpcImpl reads viewport hints, clamps `viewport_limit` against Typesafe config (see below), selects visible segments (manifest-order with time-based fallback), and emits ProtocolFragments for both snapshot and delta-only updates. Failures log and do not impact the stream.

- DoD:
  - Clients receive `fragments` payload in both snapshot and delta-only updates under the feature flag. Tests validate presence and shape (no duplicates, valid ranges, contains blip segment). Additional test verifies viewport limit clamping and robust handling of invalid directions.

#### Configuration (Typesafe) added in this phase
- `wave.fragments.defaultViewportLimit` (int, default 5): default number of blip segments when client hint is missing/invalid.
- `wave.fragments.maxViewportLimit` (int, default 50): upper clamp for client-requested viewport limit.
- Wired in `ServerMain` → `WaveClientRpcImpl.setViewportLimits(...)` at startup.

#### Client API overload (backward‑compatible)
- `RemoteViewServiceMultiplexer.open(WaveId id, IdFilter filter, WaveWebSocketCallback stream, String viewportStartBlipId, String viewportDirection, int viewportLimit)`
  - Parameters are optional hints; older clients/servers safely ignore these fields.

-------------------------------------------------------------------------------

## Phase 5 — Client Applier & Transport Evolution (Next)

Status: in_progress

### Next Task: 5.1 — Client RawFragment Applier (model)

- Scope (iteration 1):
  - Introduce a minimal `RawFragment` DTO (segment id + [from,to] + optional metadata) and a `RawFragmentsApplier` interface behind a client flag.
  - Provide a no-op default implementation and a skeleton implementation that just records the latest window per segment for observability.
  - Wire ViewChannel.Listener.onFragments(...) to the applier (gated by a client flag) with logging/metrics.
  - Surface fragments metrics in `/statusz?show=fragments`.
- Tests:
  - Unit tests for the applier: accepts well-formed ranges, rejects invalid (from>to), and maintains per-segment window state.
  - Basic integration test in the client layer to ensure onFragments triggers applier calls when the flag is enabled.
- DoD (iteration 1):
  - Compiles under a flag, does not mutate live wavelet data yet. Observability hooks (counters/logs) in place to validate flow end-to-end.

Verification (2025-09-07):
- DTOs (`FragmentsPayload`, `RawFragment`) and a skeleton applier exist with unit tests.
- ViewChannelImpl has hooks to call an applier when enabled via `client.flags.defaults.enableFragmentsApplier`, but an actual applier instance is not yet set at startup.

### Viewport Semantics and Edge Cases (server-side)
- Inputs: `viewport_start_blip_id` (string), `viewport_direction` ("forward"|"backward"), `viewport_limit` (int > 0).
- Validation/Clamping:
  - `viewport_limit` clamped to `[wave.fragments.defaultViewportLimit, wave.fragments.maxViewportLimit]`.
  - Ambiguity: if only `viewport_direction` is provided without `start_blip_id` or `viewport_limit`, server records a `viewportAmbiguity` metric and applies defaults (safe fallback).
  - Failures in viewport computation fall back to `[INDEX, MANIFEST]` only; server records `emissionFallbacks` and logs a warning.
- Observability:
  - Metrics (see Statusz → Fragments Metrics): `emissionCount`, `emissionRanges`, `emissionErrors`, `emissionFallbacks`, `computeFallbacks`, `viewportAmbiguity`, HTTP counters.
  - Logs: invalid/ambiguous inputs at fine level; computation failures at warning level.

-------------------------------------------------------------------------------

## Phase 5 — Client Applier & Transport Evolution (Future)

Status: planned

Goal: Port `RawFragment` types and client applier; switch client to RPC once server validated.

### Task 5.1 — Client RawFragment Applier (model)

- Implementation:
  - Port minimal `ObservableWaveletFragmentData.applyRawFragment(...)` and dependent types.
  - Add guardrails (sizes, version checks) and tests with fake fragments.

- DoD:
  - Apply path compiles + unit tests pass (no wiring yet).

### Task 5.2 — Client FragmentRequester over ViewChannel

- Implementation:
  - Port FragmentRequesterImpl (queueing, concurrency caps, listeners) and wire to ViewChannel RPC.

- DoD:
  - Works behind `enableFragmentFetch`; dynamic renderer requests segments near viewport.

-------------------------------------------------------------------------------

## Phase 6 — Rollout, Observability, Cleanup

Status: planned

### Task 6.1 — Observability

- Implementation:
  - Add debug counters/timers: fragment requests count, average reply size, cache hit rate.
  - Gate logs under `enableViewportStats`.

### What’s Done vs. Left (quick list)
- Done:
  - Phase 1, Phase 3, Phase 4 completed (compat); viewport hints over RPC; config‑driven viewport limits; Statusz fragments metrics; HTTP `/fragments` hardened; unit tests for clamping and robustness.
  - Phase 5 (part): DTOs + skeleton applier + metrics + hint‑aware client open overload; wiring hooks present.
- Left:
  - Phase 2 (real SegmentWaveletState), Phase 5 (wire applier at startup + requester), Phase 6 (metrics, integration tests, tuning, cleanup).

-------------------------------------------------------------------------------

## Configuration Notes (Fragments)

- `server.enableFragmentsHttp` (bool, default false):
  - When true, enables `/fragments/*` (auth required). Intended for dev/proto; data derived from snapshots.
- `server.enableFetchFragmentsRpc` (bool, default false):
  - When true, wires the RPC handler; `WaveClientRpcImpl` may emit `ProtocolFragments`.
- `server.preferSegmentState` (bool, default false):
  - When true, if a `SegmentWaveletState` exists, emitted ranges are filtered to known segments; falls back to compat otherwise.
- `server.segmentStateRegistry.maxEntries` (int, default 1024):
  - Max entries for in-memory `SegmentWaveletStateRegistry` (LRU eviction).
- `server.segmentStateRegistry.ttlMs` (long, default 300000):
  - TTL for registry entries; `0` disables TTL expiration.

-------------------------------------------------------------------------------

## Remaining Work (Ordered Checklist)

1) Wire client applier at startup (minimal)
- Set a default `SkeletonRawFragmentsApplier` in `ViewChannelImpl` at server startup when `client.flags.defaults.enableFragmentsApplier=true`.
- Add an opt-out `NoOpRawFragmentsApplier` when disabled.
- Tests: integration-lite to assert `onFragments` triggers `applier.apply(...)` when flag is on.

2) Gate HTTP fragments endpoint (optional hardening)
- Introduce `server.enableFragmentsHttp=false|true`.
- Register `/fragments/*` only when enabled; default off in `reference.conf`.
- Tests: verify 404/disabled behavior; existing JSON contract continues when enabled.

3) Real SegmentWaveletState (storage-backed)
- Design: interval schema and indices; migration strategy from snapshots/deltas.
- Implement read path with caching; write/migration task to prefill common intervals.
- Switch `FragmentsFetcher` to prefer real state when available (flag‑gated); keep compat as fallback.
- Tests: unit + integration for INDEX/MANIFEST/participants/tags and a handful of blips.

4) Client FragmentRequester over ViewChannel
- Implement requester (queueing, concurrency caps, backoff) and wire to `ViewChannel.fetchFragments`.
- Tests: request shaping near viewport; error handling.

5) Observability and metrics
- Add `wave.fragments.metrics.enabled` to `reference.conf`; expand counters/timers (requests, payload sizes, applier durations).
- Expose `/statusz?show=fragments` details; ensure values update under load.

6) Cleanup and deprecation
- Remove obsolete client flag paths; consolidate Typesafe defaults.
- Deprecate or remove compat code paths once the storage-backed state is stable.

### Task 6.2 — Cleanup deprecated flags/paths

- Implementation:
  - Remove JVM `-Dwave.clientFlags`; consolidate config–driven defaults.
  - Deprecate experimental tuning flags if unneeded.

-------------------------------------------------------------------------------

## Status Log (Commits)

- 2025-09-01 — Dynamic renderer MVP + flags + minimal fragments stub
  - Commits: see repo history (e.g., f7f02b6d, 55fdbf3d, …)
  - Summary: dynamic windowing, placeholders, resource cleanup; `/fragments` stub and HTTP client requester behind flag.

Each completed task will add its commit ids and a short summary here.
