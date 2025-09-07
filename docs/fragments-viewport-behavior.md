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
- `computeFallbacks`: manifest/time-based computation failed; selection fell back to safe defaults.
- `emissionFallbacks`: only `INDEX`/`MANIFEST` emitted in the update.
- `viewportAmbiguity`: ambiguous hints seen; defaults applied.
- HTTP counters (if `/fragments` is enabled): `httpRequests`, `httpOk`, `httpErrors`.

View under `/statusz?show=fragments`.

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

