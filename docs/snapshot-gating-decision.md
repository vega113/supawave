# Snapshot Gating and Storage-Backed Segment State: Decision Record

Status: Decided
Date: 2026-03-24
Owner: Project Maintainers
Task: incubator-wave-wiab-core.4

## Decision Summary

1. **Snapshot gating is deferred indefinitely.** The server will continue
   sending full `WaveletSnapshot` payloads on wave open. No code changes
   are needed.

2. **The deeper Wiab.pro blocks data layer will not be adopted.** The
   current delta-based storage with the compat segment-state shim is
   sufficient for the foreseeable use case.

3. **The existing scaffold flags (`preferSegmentState`,
   `enableStorageSegmentState`) are retained as-is** for future
   optionality but remain off by default with no planned activation
   timeline.

4. **The `forceClientFragments` client flag is reclassified as a
   dev/debug flag**, not a production gating mechanism.

## Context

Stream mode is now canonical (PR #65). Fragment transport is decided.
DynamicRenderer entrypoints are finished (PR #63). This decision closes
the remaining open question from `docs/blocks-adoption-plan.md` item 6
("Snapshot gating for fragment-first opens") and item 1 ("Real
SegmentWaveletState, storage-backed").

## Analysis

### What snapshot gating would require

Snapshot gating means skipping the initial `WaveletSnapshot` on wave
open so the client renders only from fragment ranges. This would require:

1. **Server-side**: Modifying `ClientFrontendImpl` and
   `WaveClientRpcImpl` to conditionally omit the snapshot from the
   initial `ProtocolWaveletUpdate`. Currently there is no server-side
   flag or code path for this; the snapshot is always emitted.

2. **Client-side**: Making `ViewChannelImpl`, the renderer, and all
   downstream consumers tolerate a snapshot-absent open. Today the
   client assumes a wavelet snapshot arrives before any delta or
   fragment payload. Removing this assumption would touch the
   concurrency control layer, the conversation model initialization,
   and the renderer bootstrap sequence.

3. **Fragment completeness**: Fragments would need to carry enough data
   to reconstruct the initial wavelet state (participants, document
   content, metadata). The current fragment payloads carry version
   ranges and opaque metadata only -- not document operations or
   participant lists.

4. **Measurement infrastructure**: WebSocket frame size comparison,
   client scripting time, and `/statusz` metrics before and after the
   change.

### Why snapshot gating is not justified now

- **The problem snapshot gating solves is large-wavelet open latency.**
  The full snapshot is expensive only when a wavelet contains hundreds
  of blips. The current deployment does not have wavelets at that
  scale.

- **Fragments are additive, not substitutive.** With
  `forceClientFragments=true`, the server still sends the full snapshot
  *and* fragment payloads. The client renders all blips immediately
  from the snapshot; fragments arrive alongside and are applied by the
  `RealRawFragmentsApplier` for observability, but they do not defer
  any data load. The investigation of 2025-09-18 (recorded in
  `docs/blocks-adoption-plan.md`) confirmed this behavior.

- **The engineering cost is high relative to the benefit.** Snapshot-
  absent opens would require changes across the server frontend,
  protocol, concurrency control, and renderer initialization -- a
  cross-cutting change with significant regression risk for a problem
  that does not yet exist at the current deployment scale.

### Why the deeper blocks data layer is not needed

Wiab.pro's blocks layer (`org.waveprotocol.box.server.persistence.blocks`)
is a substantial subsystem:

- **80 Java source files, ~7,700 lines** in the `blocks` package alone.
- **~2,700 lines** in the `waveletstate` segment package.
- A custom protobuf schema (`block-store.proto`) for segment snapshots,
  fragment indices, version markers, far-forward/backward links, and
  operation aggregation.
- Block-level I/O with `FileBlockStore`, `MemoryBlockStore`, and
  `BlockAccess` abstractions.
- A `SegmentCache` with soft-reference eviction and a `BlockCache`.
- `SegmentWaveletStateImpl` (799 lines) that coordinates block I/O,
  segment caching, delta indexing, and read/write locking.

Adopting this layer would mean:

1. Porting or reimplementing ~10,000 lines of tightly coupled storage
   code.
2. Designing and executing a data migration from the existing
   delta/snapshot store to the blocks format.
3. Maintaining two parallel storage backends during the transition.
4. Ensuring the blocks layer works with the existing MongoDB and file
   persistence modules.

**The current compat shim is sufficient.** Both `SegmentWaveletStateCompat`
and `StorageSegmentWaveletState` derive pseudo-intervals from the
existing snapshot. They are simple (~120 lines each), safe (no external
I/O), and provide the segment metadata the fragment pipeline needs for
viewport selection and observability. The fragments system works
end-to-end with this approach.

The blocks layer would become valuable only if:

- The deployment reaches a scale where per-segment storage and retrieval
  (avoiding full-wavelet reads) provides a measurable latency or
  resource benefit.
- Historical version navigation (reading a wavelet at an arbitrary past
  version) becomes a product requirement.

Neither condition applies today.

## What stays in place

| Component | Status | Notes |
|-----------|--------|-------|
| `SegmentWaveletStateCompat` | Active (default) | Derives intervals from snapshots; used by fragment pipeline |
| `StorageSegmentWaveletState` | Scaffold (off) | Same behavior as compat; distinct type for future evolution |
| `SegmentWaveletStateRegistry` | Active | LRU+TTL cache; works with either state implementation |
| `server.preferSegmentState` | Off (default) | Can be turned on to prefer registry-cached state |
| `server.enableStorageSegmentState` | Off (default) | Can be turned on to wire the scaffold storage state |
| `forceClientFragments` | Dev flag | Forces applier enablement; does not gate snapshots |
| Fragment stream pipeline | Active | ViewChannel-based delivery, applier, metrics |
| Full snapshot on open | Always sent | No gating planned |

## Future triggers for revisiting

- **Scale**: If wavelets routinely exceed 100 blips, measure open
  latency and WebSocket frame sizes to determine if snapshot gating
  becomes worthwhile.
- **History navigation**: If per-version wavelet access becomes a
  product feature, the blocks layer becomes the natural path.
- **Storage efficiency**: If delta/snapshot storage grows beyond
  practical bounds, segment-oriented storage may reduce I/O.

Until one of these triggers fires, the current architecture is the
right trade-off.

## References

- `docs/blocks-adoption-plan.md` -- Phase-by-phase adoption log
- `docs/fragments-config.md` -- Configuration reference
- `docs/current-state.md` -- Repository state and gaps
- `Wiab.pro/src/org/waveprotocol/box/server/persistence/blocks/` -- Reference blocks implementation
- `Wiab.pro/src/org/waveprotocol/box/server/waveletstate/segment/SegmentWaveletStateImpl.java` -- Reference segment state
