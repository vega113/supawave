# Fragments Configuration Reference

Last updated: 2025-09-07

This is a concise, reference-first guide to fragments-related configuration.
Defaults are shown; see reference.conf for inline comments.

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

## Viewport and manifest order
- `wave.fragments.defaultViewportLimit` (int, default `5`)
- `wave.fragments.maxViewportLimit` (int, default `50`)
- `wave.fragments.manifestOrderCache.maxEntries` (int, default `1024`)
- `wave.fragments.manifestOrderCache.ttlMs` (long, default `120000`)

## Client flags (merged into client.flags.defaults)
- `client.flags.defaults.enableFragmentsApplier` (bool, default `false`)
  - Enables client-side RawFragmentsApplier hook in ViewChannelImpl.

## Observability (Statusz → Fragments Metrics)
- Emission: `emissionCount`, `emissionRanges`, `emissionErrors`, `emissionFallbacks`
- Compute: `computeFallbacks`, `viewportAmbiguity`
- HTTP: `httpRequests`, `httpOk`, `httpErrors`
- Applier: `applierEvents`, `applierDurationsMs`

