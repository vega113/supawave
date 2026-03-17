# Fragments Viewport Behavior — Semantics, Rules, and Examples

Last updated: 2025-09-07

This note describes how the server interprets viewport hints for fragments
emission and selection, and which fallbacks/metrics apply.

## Inputs
- `viewport_start_blip_id` (string): anchor blip id, e.g. `b+123`.
- `viewport_direction` (string): `forward` | `backward`.
- `viewport_limit` (int): desired number of blip segments, > 0.

## Clamping and Validation
- `viewport_limit` is clamped to `[wave.fragments.defaultViewportLimit, wave.fragments.maxViewportLimit]`.
- If only `viewport_direction` is present (no start/limit), the request is treated as ambiguous:
  - Server records the `viewportAmbiguity` metric.
  - Defaults are applied (direction respected, limit defaults, anchor may be heuristics).

## Selection Order
1. Include `INDEX` and `MANIFEST` segments first.
2. Preferred: manifest order (conversation tree) near the anchor.
3. Fallback: time-based blip ordering (mtime/id) if manifest order isn’t available.
4. Final fallback: `INDEX` and `MANIFEST` only when selection fails.

## Fallbacks and Metrics

### Deterministic Fallback Order
The server always evaluates viewport hints in the following order, recording
metrics at the first point where a deterministic outcome is no longer possible:

1. **Manifest order window** – attempt to build a window centred on the anchor
   using cached manifest order. Failure increments `computeFallbacks` **only** if
   we cannot assemble a contiguous manifest slice.
2. **Time-based (mtime/id) ordering** – if manifest order is unavailable or
   incomplete, fall back to metadata ordering. When this path succeeds the
   range is still considered deterministic; `computeFallbacks` remains untouched.
3. **Safety window** – when both manifest and time-based selections fail, the
   handler emits INDEX/MANIFEST segments only and increments `computeFallbacks`
   and `emissionFallbacks`.

### Ambiguity vs. Fallback Metrics
- `viewportAmbiguity`: increments when user-provided hints are insufficient or
  invalid (missing anchor, non-existent anchor, direction without limit). This
  happens **before** any selection attempt and is independent from
  `computeFallbacks`.
- `computeFallbacks`: indicates the selection logic exhausted deterministic
  orderings (manifest/time). This does not imply ambiguity—well-formed hints can
  still trigger a fallback when caches are cold or the wavelet lacks ordering
  metadata.
- `emissionFallbacks`: emitted payload contained only INDEX/MANIFEST segments.
- HTTP counters (when `/fragments` is enabled): `httpRequests`, `httpOk`,
  `httpErrors`.

You can inspect all counters via `/statusz?show=fragments`.

## Client Flag & Transport Logging
- Fragment fetching is gated by `enableFragmentFetchViewChannel=true`. The
  override `enableFragmentFetchForceLayer=true` forces stream requests even
  before cache warm-up and should be used only for focused testing.
- When both flags are false the renderer remains virtualised and does not issue
  fragment requests; this is logged as
  `Dynamic fragments: client fetch disabled; using NO_OP requester`.
- Runtime logs use the following phrases for clarity during warm-up:
  - `Dynamic fragments: client fetch disabled; using NO_OP requester`
  - `Dynamic fragments: ViewChannel not ready, using HTTP fallback`
  - `Dynamic fragments: switching to ViewChannel fetch`
  These logs make the active transport explicit whenever the client changes
  behaviour.


### Pseudo Handling API
The simplified server flow looks like this:

```pseudo
function selectSegments(hints, snapshot):
  order = manifestCache.lookup(snapshot.wavelet)
  if order.present:
    window = order.takeWindow(hints.anchor, hints.direction, hints.limit)
    if window.exists:
      return Result(window, metrics())

  // Manifest miss triggers manifestation of computeFallbacks if we entered here
  metrics.computeFallbacks++
  window = metadataOrder(snapshot).takeWindow(hints.anchor, hints.direction, hints.limit)
  if window.exists:
    return Result(window, metrics())

  metrics.computeFallbacks++
  metrics.emissionFallbacks++
  return Result([INDEX, MANIFEST], metrics())
```

Ambiguity detection happens before this routine and increments
`metrics.viewportAmbiguity` when hints cannot be normalised.

### Debug/Test Checklist
1. **Ambiguous input** – omit `startBlipId` and assert `viewportAmbiguity` rises
   while `computeFallbacks` stays zero, verifying defaults were applied.
2. **Manifest miss** – prime the cache with `ManifestOrderCache.reset()` (test
   only) and request a window; expect `computeFallbacks` to increment once while
   the fallback window still returns blips in metadata order.
3. **Safety window** – craft a wavelet with no blips (or filter all out) and
   ensure both `computeFallbacks` and `emissionFallbacks` increment; payload
   should contain only INDEX/MANIFEST.
4. **HTTP fallback** – when running with `/fragments` enabled, validate that 404
   responses bump `httpErrors` and do not interfere with ambiguity metrics.
5. **Mixed scenarios** – combine ambiguous hints with manifest cache misses; the
   expected metrics are `viewportAmbiguity` **and** `computeFallbacks` to capture
   both phases independently.

Documenting these explicit combinations in integration tests prevents ambiguity
regressions and makes dashboards easier to interpret.

## Text Diagrams

```
Manifest order (root → leaves):

  [INDEX]   [MANIFEST]
      |         |
  b+1 → b+2 → b+3 → b+4 → ...

Anchor = b+2, direction=forward, limit=2
→ Visible: [INDEX, MANIFEST, b+2, b+3]

Anchor = b+4, direction=backward, limit=3
→ Visible: [INDEX, MANIFEST, b+2, b+3, b+4]
```

## Worked Examples and Pseudo‑Cases

The server combines the three hints to choose a window around an anchor. When the
inputs are incomplete or cannot be resolved deterministically, the server applies
defaults and increments the `viewportAmbiguity` metric.

Assume manifest order: [b+1, b+2, b+3, b+4, b+5, …]. In all cases INDEX and
MANIFEST are included first.

- Example A — Full hints (no ambiguity)
  - Input: start=b+10, direction=forward, limit=5
  - Action: select [b+10 … b+14] (clamped to end)
  - Metrics: no `viewportAmbiguity` increment

- Example B — Backward window (no ambiguity)
  - Input: start=b+4, direction=backward, limit=3
  - Action: select [b+2, b+3, b+4]
  - Metrics: none

- Example C — Missing start (ambiguous)
  - Input: start=null, direction=forward, limit=4
  - Action: choose a heuristic anchor (e.g., most recently visible blip or
    manifest head), then select [anchor …] with limit=4
  - Metrics: `viewportAmbiguity`++

- Example D — Missing start and limit (ambiguous)
  - Input: start=null, direction=backward, limit=null
  - Action: choose heuristic anchor; apply default limit; move backward
  - Metrics: `viewportAmbiguity`++

- Example E — Start not found (ambiguous)
  - Input: start=b+999 (absent), direction=forward, limit=3
  - Action: treat as ambiguous; choose nearest valid anchor (e.g., head)
  - Metrics: `viewportAmbiguity`++

- Example F — Invalid/edge limit (no ambiguity)
  - Input: start=b+2, direction=forward, limit=0
  - Action: clamp limit to default; select forward
  - Metrics: none (clamping alone does not imply ambiguity)

- Example G — Oversized limit (no ambiguity)
  - Input: start=b+2, direction=forward, limit=10000
  - Action: clamp to max; select forward
  - Metrics: none

- Example H — Prefer‑state filter (independent of ambiguity)
  - Input: any window; preferSegmentState=true; state has only INDEX/MANIFEST
  - Action: filter emitted ranges to known segments (INDEX/MANIFEST)
  - Metrics: no `viewportAmbiguity`; may see `emissionFallbacks` if only
    INDEX/MANIFEST remain after filtering.

### Quick Reference: When does `viewportAmbiguity` increment?
- Yes:
  - Missing anchor with no deterministic default (C, D)
  - Anchor cannot be resolved (E)
- No:
  - Limit clamping (F, G)
  - Any fully specified triplet (A, B)
  - Prefer‑state filtering (H)

### Pseudo‑API Examples
- HTTP (conceptual):
  - GET /fragments?wavelet=example.com/w+1/conv+root&start=b+10&dir=forward&limit=5
- Multiplexer open (conceptual):
  - open(waveId, filter, stream, startBlipId="b+10", direction="forward", limit=5)


## Operational Notes
- Manifest order is cached with TTL to reduce recomputation on hot wavelets:
  - Config: `wave.fragments.manifestOrderCache.{maxEntries,ttlMs}`
  - Cache is LRU + TTL bounded.
- Prefer segment state when available (experimental):
  - Config: `server.preferSegmentState`
  - When enabled, ranges are filtered to known segments from the state; compat remains the fallback.

## Examples of Clamping
- `viewport_limit=0` → clamped to default; log fine-level note.
- `viewport_limit=10_000` → clamped to max; log fine-level note.

## Failure Transparency
- Fallbacks log at WARN with the wavelet name to ease debugging.
- Config read failures log at INFO with defaults applied.

## Current Limitations (2025-09-18)
- When `forceClientFragments=true` the server still sends a full `WaveletSnapshot` on the initial `ViewChannel` update. The fragments window is additive, so the browser renders all blips immediately despite the clamp.
- `fragmentsApplierMaxRanges` only trims how many ranges the client applier processes per batch; it does not reduce the payload size the server sends.
- Stream mode now issues `ViewChannel.fetchFragments` with the current viewport segment list when `enableFragmentFetchViewChannel=true`; if the flag is off it falls back to the HTTP `/fragments` anchor request. Snapshot gating remains the limiting factor for incremental load.
- Observability via the fragments badge (`FragmentsDebugIndicator`) shows paged-in counts (e.g., `Blips 3/5`) even when all blips have been fetched; treat it as a UI virtualization indicator rather than evidence of deferred data load.
