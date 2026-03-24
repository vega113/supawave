# Fragments Configuration Reference

Last updated: 2026-03-23

This is a concise, reference-first guide to fragments-related configuration.
Defaults are shown; see reference.conf for inline comments.

## Canonical Fragment Transport Path (Decision Record)

**Decision**: Stream mode (`fragmentFetchMode = "stream"`) is the canonical
transport path for delivering fragment data from server to client.

**Rationale** (evaluated 2026-03-23):

The system has two fragment transport mechanisms:

1. **Stream mode** (ViewChannel WebSocket): The `ViewChannelFragmentRequester`
   calls `ViewChannel.fetchFragments()`, which invokes `FragmentsFetchBridge`
   server-side, returns a `FragmentsPayload`, and delivers it through the
   existing `ViewChannel.Listener.onFragments()` callback.  The payload then
   flows into the `RawFragmentsApplier` pipeline (flag-gated, clamped, and
   fully metered).  This path is **complete end-to-end**.

2. **HTTP mode** (GET /fragments): `ClientFragmentRequester` sends a GET
   request to `FragmentsServlet`, which returns rich JSON (blips, ranges,
   raw fragment operations).  However, the client **discards the response
   payload** -- it only checks the HTTP status code for success/failure.
   Finishing the HTTP parsing path would require new GWT JSON overlay code
   and a callback bridge into the applier pipeline.

Stream mode was chosen because:
- It reuses the existing WebSocket connection (no extra TCP/TLS handshake).
- The full request-to-apply pipeline already works.
- Fragment delivery is ordered with respect to delta updates on the same channel.
- The applier hook is wired with flag gating, range clamping, and metrics.

HTTP mode is retained for:
- Server-side smoke testing and metrics (`httpRequests`, `httpOk`, `httpErrors`).
- Operator diagnostics (curl the endpoint to verify servlet registration).
- Future: potential REST API for non-WebSocket clients.

**Migration**: All config files (reference.conf, application.conf, deploy configs)
now default to `fragmentFetchMode = "stream"`.  The `enableFragmentFetchViewChannel`
flag defaults to `true` in reference.conf.  Setting `fragmentFetchMode = "http"`
still works for the metrics-only path; setting `"off"` disables fragment fetching.

> **Server-side requirement for `fragmentFetchMode = "http"`**: Because the
> default server transport is now `server.fragments.transport = "stream"`, the
> HTTP `/fragments` endpoint is **not** enabled unless the server is also
> configured with `server.fragments.transport = "http"` or `"both"`. Changing
> only the client-side `fragmentFetchMode` to `"http"` without updating the
> server will point at a disabled endpoint. Operators must ensure both sides
> agree on the transport.

Context and Source of Truth
- Server reads these keys via `ServerMain` during startup:
  - `initializeServlets(...)` (HTTP endpoint gating)
  - `initializeFrontend(...)` (client applier wiring + metrics)
  - `applyFragmentsConfig(...)` (cache/registry/applier validation)
- Client UI flags are merged from Typesafe config (`client.flags.defaults.*`) and may also
  be provided at runtime via `-PclientFlags` when running the dev app; the Typesafe config is
  the canonical source for defaults in production.
- Unless otherwise stated, all flags default to safe (off) and are backward‑compatible. When a
  feature is off, the system behaves as it did prior to fragments work.

Gating & Test Execution Policy
- Stubs and experimental code paths are always guarded by explicit gates (below). Do not depend on
  them being active in default builds/CI.
- Tests that require these gates must either:
  - be integration‑lite (pure JVM, no server endpoints) and avoid depending on gates, or
  - be excluded from default `:wave:test` (e.g., stress or endpoint‑dependent tests) and documented
    with how to enable the gate(s) to run them locally.
- Wire descriptors (ProtocolOpenRequest viewport hints; ProtocolFragments) exist for compatibility,
  but emission/consumption remains gated at runtime via the flags below.

## Server flags (gates)
- `server.enableFragmentsHttp` (bool, default `false`)
  - Enables `/fragments/*` (auth required). Dev/proto only.
- `server.enableFetchFragmentsRpc` (bool, default `false`)
  - Enables RPC fragments emission and handler wiring.
- `server.preferSegmentState` (bool, default `false`)
  - Filters emitted ranges to known segments when state exists; compat fallback.

## Server caches/state
- `server.segmentStateRegistry.maxEntries` (int, default `1024`)
- `server.segmentStateRegistry.ttlMs` (long, default `300000`)
  - Size/TTL for in-memory SegmentWaveletState registry (LRU + TTL).
- `server.enableStorageSegmentState` (bool, default `false`)
  - When true, build a `StorageSegmentWaveletState` from snapshots as a scaffold and cache
    it in the registry (to be evolved to a true storage-backed implementation).

### Storage‑Backed SegmentWaveletState (flag details)

Purpose
- Enable a server path that prefers a storage‑backed SegmentWaveletState when
  available, reducing recomputation and preparing for blocks‑style state.
- While the storage implementation is developed, the flag wires a scaffolded
  `StorageSegmentWaveletState` (derived from snapshots) into the same code path
  so plumbing and observability can be validated end‑to‑end.

Behavior and compatibility
- Off (default):
  - Compat mode only (snapshot‑derived intervals via `SegmentWaveletStateCompat`).
  - No change in emitted ranges; existing clients/protocol remain unchanged.
- On:
  - The handler constructs a `StorageSegmentWaveletState` and caches it in the
    registry. As the real storage is implemented, this class will return true
    persisted intervals rather than snapshot reconstructions.
  - If storage lookup fails or is partially available, the system merges storage
    results with compat results for missing segments, without duplicates.
  - Errors or timeouts fall back to compat; the update/RPC stream continues.

Practical rollout (migration from snapshots)
- Phase 0 — Dry run (flag off):
  - Validate metrics/caches and keep using compat only.
- Phase 1 — Shadow/Canary (flag on for a small cohort):
  - Build `StorageSegmentWaveletState` from snapshots, store in registry,
    and compare storage vs compat outputs in metrics (e.g., `statePartial`).
  - Watch `computeFallbacks`, `stateErrors`, GC pressure, and registry hit rates.
- Phase 2 — Real storage reads (flag still on, storage wired):
  - Add bounded timeouts (e.g., 100 ms) and fail over to compat on error.
  - Expose counters: `stateHits`, `stateMisses`, `stateErrors`, `statePartial`.
- Phase 3 — Ramp up:
  - Increase flag coverage; keep compat as fallback for resiliency.
- Phase 4 — Stabilize:
  - When storage quality is sufficient, keep the flag on by default and
    optionally deprecate the compat path later.

Monitoring and validation
- Counters to track (Statusz → Fragments):
  - Storage: `stateHits`, `stateMisses`, `statePartial`, `stateErrors`.
  - Fallbacks: `computeFallbacks` increments when compat is used.
  - Caches: registry `hits/misses/evictions/expirations` and manifest cache stats.
- Healthy signals:
  - `stateErrors ≈ 0`, `statePartial` declining as backfill/migration completes.
  - Compat fallbacks become rare except during deploys or targeted outages.

Resource and tuning considerations
- Registry sizing (see guidance above) should cover peak concurrently active
  wavelets per node; storage reads should be bounded and cached.
- Keep `server.segmentStateRegistry.ttlMs` aligned with typical revisits; too
  short TTLs increase read load; too long TTLs increase memory.

Sample HOCON configs
```
# Shadow canary on a single node or small percentage
server.enableStorageSegmentState = true
server.segmentStateRegistry.maxEntries = 2048
server.segmentStateRegistry.ttlMs = 600000  # 10m

# Rollback (immediate)
server.enableStorageSegmentState = false
```

Limitations (while scaffolded)
- Until true storage is wired, `StorageSegmentWaveletState` mirrors compat
  behavior but exercises the same registry and wiring. This allows validating
  flag behavior and counters without protocol changes.

Code example (integration)
```java
// Resolve a snapshot and populate a storage state (scaffold) into the registry
CommittedWaveletSnapshot snap = provider.getSnapshot(waveletName);
if (snap != null && snap.snapshot != null) {
  SegmentWaveletState state = new StorageSegmentWaveletState(snap.snapshot);
  SegmentWaveletStateRegistry.put(waveletName, state);
}
// Later: fetch intervals for INDEX/MANIFEST + some blips
Map<SegmentId, VersionRange> req = new HashMap<>();
req.put(SegmentId.INDEX_ID, VersionRange.of(0, 0));
req.put(SegmentId.MANIFEST_ID, VersionRange.of(0, 0));
req.put(SegmentId.ofBlipId("b+1"), VersionRange.of(1, 10));
Map<SegmentId, Interval> intervals = state.getIntervals(req, false);
```

### Sizing Guidance and Examples (segment state registry)

What it is
- One entry per wavelet (WaveletName) holding a SegmentWaveletState.
- LRU on capacity; TTL is enforced from insertion time (a fresh `put` sets the timestamp; `get` does not refresh TTL).

What to watch (Statusz → Fragments Caches)
- `segmentStateRegistry: hits/misses/evictions/expirations`.
- Healthy steady state: primarily hits; evictions/expirations should be non-zero but not dominate.

Heuristics
- Start with `maxEntries` >= peak concurrently active wavelets per node.
- Choose `ttlMs` to cover the typical “interaction session” length (how long you want a wavelet’s state to remain warm after last build).
- If memory is tight, bias toward smaller `maxEntries` and shorter `ttlMs` but accept more misses and rebuilds.

Back-of-the-envelope
- If a node sees ~A active wavelets concurrently, set `maxEntries ≈ 1.5 × A` to absorb short spikes without constant churn.
- If users typically bounce between wavelets within T minutes, set `ttlMs ≈ T × 60_000` so recently used state isn’t discarded too soon.

Scenario examples
- Local/dev (low traffic)
  - Goal: keep things simple, avoid noisy churn.
  - Suggested: `maxEntries = 128`, `ttlMs = 300_000` (5 minutes).
  - Expectation: very few evictions; occasional expirations between runs.

- Staging / small team (≤ 200 concurrent wavelets/node)
  - Suggested: `maxEntries = 512–1024`, `ttlMs = 300_000–600_000` (5–10 minutes).
  - Watch: if `misses` grow and `evictions` spike during load tests, increase `maxEntries`.

- Production (read‑heavy, moderate churn; ~1k concurrent wavelets/node)
  - Suggested starting point: `maxEntries = 2048`, `ttlMs = 600_000` (10 minutes).
  - If `expirations` dominate and users revisit the same waves within 10 minutes, increase `ttlMs` to 15–20 minutes.
  - If GC/memory pressure increases, reduce `maxEntries` by 25–30% and re‑evaluate hit/miss ratio.

- Production (write/churn heavy or memory‑constrained)
  - Suggested: `maxEntries = 1024–1536`, `ttlMs = 120_000–300_000` (2–5 minutes).
  - Trade‑off: more rebuilds (higher `misses`), but lower memory footprint and shorter retention.

Sample configs
```
# Dev/local
server.segmentStateRegistry.maxEntries = 128
server.segmentStateRegistry.ttlMs = 300000  # 5m

# Staging (burstier than dev, still modest)
server.segmentStateRegistry.maxEntries = 1024
server.segmentStateRegistry.ttlMs = 600000  # 10m

# Production read‑heavy
server.segmentStateRegistry.maxEntries = 2048
server.segmentStateRegistry.ttlMs = 900000  # 15m

# Production churn‑heavy / constrained memory
server.segmentStateRegistry.maxEntries = 1280
server.segmentStateRegistry.ttlMs = 180000  # 3m
```

Tuning loop
- Increase `maxEntries` if `evictions` correlate with traffic spikes and hit ratio drops.
- Increase `ttlMs` if users frequently revisit the same wavelets within the TTL and you observe `expirations` immediately followed by rebuilds (misses).
- Decrease `maxEntries`/`ttlMs` if memory pressure rises or registry holds data longer than needed.


## Viewport and manifest order
- `wave.fragments.defaultViewportLimit` (int, default `5`)
- `wave.fragments.maxViewportLimit` (int, default `50`)
- `wave.fragments.manifestOrderCache.maxEntries` (int, default `1024`)
- `wave.fragments.manifestOrderCache.ttlMs` (long, default `120000`)

## Client applier
- `wave.fragments.applier.impl` (string, default `skeleton`)
  - Which client-side applier to wire when `client.flags.defaults.enableFragmentsApplier=true`.
  - Supported: `skeleton` (records windows + history), `real` (merges coverage ranges), `noop` (disabled).
  - Value is validated at startup; invalid values fail fast with a clear message.

## Client flags (merged into client.flags.defaults)
- `client.flags.defaults.fragmentFetchMode` (string, default `"stream"`)
  - `"stream"` (canonical): fragments delivered via ViewChannel WebSocket.
  - `"http"`: fires GET /fragments for metrics; response payload is not applied.
  - `"off"`: disables fragment fetching entirely.
- `client.flags.defaults.enableFragmentFetchViewChannel` (bool, default `true`)
  - Allows the ViewChannel stream path.  Deprecated: stream mode now implies this.
  - Kept for backward compatibility; setting to `false` while mode is `"stream"`
    no longer prevents stream activation.
- `client.flags.defaults.enableFragmentFetchForceLayer` (bool, default `false`)
  - Forces the ViewChannel requester even when the channel is not yet open.
  - Dev/testing only.
- `client.flags.defaults.enableFragmentsApplier` (bool, default `false`)
  - Enables client-side RawFragmentsApplier hook in ViewChannelImpl.

## Observability (Statusz → Fragments Metrics)
- Emission: `emissionCount`, `emissionRanges`, `emissionErrors`, `emissionFallbacks`
- Compute: `computeFallbacks`, `viewportAmbiguity`
- HTTP: `httpRequests`, `httpOk`, `httpErrors`
- Applier: `applierEvents`, `applierDurationsMs`, `applierRejected`
  - `applierRejected`: count of invalid fragments dropped by the client applier
    (null segment id, negative bounds, or `from > to`). Healthy systems should
    keep this near zero; spikes suggest malformed payloads or an ongoing
    migration/canary.
- Requester: `requesterSends`, `requesterCoalesced`
- Storage state: `stateHits`, `stateMisses`, `statePartial`, `stateErrors`

Storage metrics — expected interpretations and operator actions
- `stateHits`
  - Meaning: Storage returned intervals for all requested segments on the prefer‑state path; no compat fallback was needed.
  - Expectation: Increase as storage coverage improves. High values indicate effective caching/storage availability.
  - Action: None; this is the desired outcome.
- `stateMisses`
  - Meaning: Storage returned no intervals for the requested set; compat path handled the request entirely.
  - Expectation: Common early in migration or when TTLs evict entries. Should trend downward as storage warms up.
  - Action: If sustained at high levels together with high registry `misses`, consider increasing TTL/size, or investigate storage readiness/backfill.
- `statePartial`
  - Meaning: Storage returned a subset of requested segments; compat filled the rest.
  - Expectation: Normal during canaries/backfill. Should decline as coverage improves.
  - Action: Monitor trend. If persistent for specific waves, validate backfill or request shapes (overly large / sparse segment sets).
- `stateErrors`
  - Meaning: Exceptions/timeouts while querying storage state; compat continued the stream.
  - Expectation: Near zero. Spikes indicate transient outages or regressions.
  - Action: Treat as an operational alarm. Check storage health, timeouts, and error logs. Because the stream falls back, users should not see hard failures, but efficiency degrades.

Runtime impact notes
- The prefer‑state path never blocks update delivery: on miss/partial/error, compat results are used and metrics record the condition for visibility.
- Elevated `stateErrors` or `stateMisses` increase CPU/IO because compat recomputes intervals; watch GC and cache hit ratios in parallel (registry/manifest stats).
Run policy
- Default CI runs only fast, integration‑lite tests that do not require server gates.
- Stress tests: `:wave:testStress` (documented in docs/fragments-stress-tests.md).
- Endpoint‑dependent tests require enabling `server.enableFragmentsHttp=true` and/or
  `server.enableFetchFragmentsRpc=true` locally; keep them out of default CI.

## Startup Validation
- On startup, the server validates key cache/registry settings and aborts with a clear error if invalid values are provided:
  - `server.segmentStateRegistry.maxEntries` must be > 0
  - `server.segmentStateRegistry.ttlMs` must be >= 0 (0 disables TTL)
  - `wave.fragments.manifestOrderCache.maxEntries` must be > 0
  - `wave.fragments.manifestOrderCache.ttlMs` must be >= 0 (0 disables TTL)
- Invalid values throw `ConfigurationInitializationException` during initialization; see `ServerMain.applyFragmentsConfig`.

## Cache Metrics (Statusz → Fragments Caches)
- Manifest order cache: `hits`, `misses`, `evictions`, `expirations`.
- Segment state registry: `hits`, `misses`, `evictions`, `expirations`.
- View at `/statusz?show=fragments` under “Fragments Caches”.
