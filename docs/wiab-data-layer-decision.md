# Wiab.pro Data Layer (Blocks, History, Segments): Go/No-Go Decision

Date: 2026-03-24

## 1. Current Apache Wave Storage Architecture

### 1.1 Delta-based storage

Apache Wave stores wavelet history as a linear sequence of **deltas** -- each
delta is a `WaveletDeltaRecord` containing the applied operations, the
transformed result, and version metadata.

Key interfaces and implementations:

- **`DeltaStore`** (`wave/.../waveserver/DeltaStore.java`) -- top-level store
  interface. Provides `DeltasAccess` per wavelet, supporting `append()` and
  sequential read of delta records.
- **`Mongo4DeltaStore`** -- MongoDB v4 implementation. One document per delta
  record in a `deltas` collection.
- **`FileDeltaStore`** -- File-based implementation. One file per wavelet with
  appended protobuf records.
- **`MemoryDeltaStore`** -- In-memory implementation for tests.

### 1.2 Snapshot computation

Snapshots are computed by **replaying all deltas from version zero**:

```java
DeltaStoreBasedWaveletState.create():
  ImmutableList<WaveletDeltaRecord> deltas = readAll(deltasAccess);
  WaveletData snapshot = WaveletDataUtil.buildWaveletFromDeltas(
      waveletName, Iterators.transform(deltas.iterator(), TRANSFORMED));
```

`DeltaStoreBasedSnapshotStore.buildWaveletFromDeltaReader()` does the same
thing -- iterates every delta and applies it to construct the current state.

There is no stored snapshot; every wavelet open requires a full replay.

### 1.3 In-memory caching

`DeltaStoreBasedWaveletState` keeps:
- `ConcurrentSkipListMap<HashedVersion, WaveletDeltaRecord> cachedDeltas` --
  all deltas in memory after initial load
- `WaveletData snapshot` -- the computed current state
- Persistence is batched via an async executor

### 1.4 Performance characteristics

| Operation | Complexity |
|-----------|-----------|
| Open wavelet | O(N) -- replay all N deltas |
| Get snapshot at version V | O(N) -- replay from 0 to V |
| Append delta | O(1) amortized |
| History range [A,B] | O(B-A) sequential scan |
| Memory per wavelet | O(N) -- all deltas cached |

**Limitations:**
- **Startup time** scales linearly with wavelet size
- **No partial loading** -- must load entire wavelet to serve any part
- **No stored snapshots** -- every restart replays everything
- **No per-segment access** -- cannot fetch just one blip's history

---

## 2. Wiab.pro Blocks Architecture

### 2.1 Core concepts

Wiab.pro replaces the flat delta log with a three-tier structure:

**Segments** -- logical partitions of a wavelet's state. Each segment tracks
one concern independently:
- `SegmentId.INDEX` -- wavelet metadata index
- `SegmentId.PARTICIPANTS` -- participant list
- `SegmentId.ofBlipId("b+xyz")` -- one segment per blip document

**Blocks** -- fixed-size (500KB-1MB) storage containers that hold fragments
from one or more segments. Blocks are the unit of I/O.
- `Block.LOW_WATER = 500KB` -- threshold for starting a new fragment elsewhere
- `Block.HIGH_WATER = 1MB` -- hard limit for writing to a block

**Fragments** -- a contiguous slice of a segment's history stored within one
block. A segment's full history is a linked list of fragments across blocks.

### 2.2 Key data structures

**VersionNode** -- Each version within a fragment is a node in a skip-list-like
structure with:
- Version info (version number, author, timestamp)
- Operation from previous version (the delta)
- **Far backward links** -- aggregated operations jumping back by powers of
  the base (10, 100, 1000, 10000 versions)
- **Far forward links** -- pointers forward by the same distances
- Optional **snapshot marker** -- a full segment snapshot at this version

**OperationAggregator** -- Automatically builds the skip-list links as
operations are appended. Uses a configurable base (10) and max levels (4),
giving jump distances of 10, 100, 1000, 10000 versions. Also aggregates by
author and time interval (1 hour for blips).

**BlockIndex** -- Per-wavelet protobuf index mapping `(SegmentId, VersionRange)
-> BlockId`. Stored alongside blocks; enables O(1) lookup of which blocks
contain a given segment's version range.

**FragmentIndex** -- Per-fragment index within a block header. Contains marker
offsets, snapshot index, and linked-list pointers.

### 2.3 Snapshot strategy

Wiab.pro stores snapshots **inline in the version stream** with a density
heuristic:
- `SNAPSHOT_RECORDING_DENSITY = 0.25` -- a snapshot is written when the ratio
  `snapshot_size / (ops_size + snapshot_size)` drops below 25%
- The attempt interval grows by 1.5x if the density check fails, avoiding
  constant rechecking
- The last snapshot per fragment is also cached in the fragment index for fast
  access

This means getting a snapshot at any version requires at most replaying from
the nearest stored snapshot -- typically a few operations, not thousands.

### 2.4 History navigation

`HistoryNavigator` traverses the version node graph using far-forward and
far-backward links. For a wavelet with N versions, navigating to any version
within a segment is O(log N) instead of O(N), because the skip-list jumps by
powers of 10.

### 2.5 Caching

- **`BlockCache`** -- Guava `Cache` with weak values; blocks are evicted when
  no strong references remain
- **`SegmentCache`** -- Guava `Cache` with soft values; segments stay in memory
  under memory pressure but can be collected
- Fragments use `SoftReference` within the segment's `TreeRangeMap`

### 2.6 Performance characteristics

| Operation | Complexity |
|-----------|-----------|
| Open wavelet | O(1) -- read block index only |
| Get segment snapshot at version V | O(log N) -- skip-list to nearest snapshot, replay a few ops |
| Get single blip | O(1) block reads -- only the relevant block(s) |
| Append operation | O(log N) amortized -- aggregator builds skip-list links |
| History range [A,B] for one segment | O(log N + B-A) |
| Memory per wavelet | O(active segments) -- only loaded segments cached |

### 2.7 File counts and code size

| Layer | Files | Lines |
|-------|-------|-------|
| `persistence/blocks/` interfaces | 44 | ~1,400 |
| `persistence/blocks/impl/` implementations | 36 | ~6,300 |
| `waveletstate/` (block + segment + delta states) | 13 | ~2,700 |
| Segment operations (wave model) | 8 | ~680 |
| Proto definitions | 2 | ~210 |
| **Total** | **~103** | **~11,300** |

---

## 3. Cost/Benefit Analysis

### 3.1 Benefits of the blocks architecture

| Benefit | Impact | When needed |
|---------|--------|-------------|
| **Partial loading** -- fetch only the blips in the viewport | High | Waves with >50 blips |
| **O(log N) history access** via skip-list navigation | High | Waves with >10K versions |
| **Stored snapshots** -- no full replay on open | High | Any wave over a few hundred versions |
| **Bounded block I/O** -- 500KB-1MB reads instead of full wavelet | Medium | Large waves (>1MB total) |
| **Per-segment caching** with soft references | Medium | Memory-constrained deployments |
| **Aggregated operations** for efficient playback | Medium | History browsing/scrubbing features |

### 3.2 Costs of porting

| Cost | Estimate | Notes |
|------|----------|-------|
| **Code volume** | ~11,300 lines across ~103 files | Substantial but self-contained |
| **New model concepts** | SegmentId, segment operations (AddSegment, RemoveSegment, StartModifyingSegment, EndModifyingSegment) | Changes to the wave operation model |
| **Protobuf definitions** | 2 new proto files (block-store.proto, block-index.proto) | Plus generated code |
| **Storage migration** | All existing data must be converted or dual-read supported | Risk of data loss during migration |
| **Testing surface** | New unit + integration tests for blocks, fragments, aggregation, navigation | Significant test investment |
| **Operational complexity** | Block compaction, index rebuild, new failure modes | New operational procedures |
| **Compatibility risk** | Wiab.pro code uses older Guava patterns, pre-Jakarta patterns | Modernization needed during port |

### 3.3 What already exists in Apache Wave

The `blocks-adoption-plan.md` documents extensive work already done to bring
**fragments** (the client-facing concept) into Apache Wave without the full
blocks storage backend:

- **SegmentId, VersionRange, Interval** -- core types already ported (Phase 1)
- **SegmentWaveletState interface** -- defined and compatibility implementation
  (`SegmentWaveletStateCompat`) working
- **FragmentsFetcher** -- compat implementation computing ranges from snapshots
- **HTTP `/fragments` endpoint** -- serving fragment metadata
- **RPC emission** -- ProtocolFragments over WebSocket with viewport hints
- **Client applier** -- skeleton + real implementations behind flags
- **FragmentRequester** -- client-side request shaping with metrics

This means the **consumer side** (transport, client, viewport) is already built
on a segment abstraction, using a compatibility shim over the existing delta
store.

---

## 4. Assessment

### 4.1 Is the current delta-based storage sufficient for production?

**For small-to-medium deployments (< 100 users, < 1000 waves, < 10K versions
per wave): Yes.** The delta replay approach works. Memory is cheap and startup
time is tolerable.

**For larger deployments: No.** The O(N) startup replay and O(N) memory
footprint per wavelet become prohibitive. A wave with 100K edits would require
replaying every edit on every server restart.

### 4.2 What scale requires the blocks architecture?

The inflection point is approximately:
- **Waves with >1K versions** -- snapshot replay starts to be noticeable (>1s)
- **Waves with >50 blips** -- partial loading becomes valuable
- **>100 active wavelets** -- memory pressure from caching all deltas
- **Server restarts** -- the most painful scaling issue; all wavelets re-replay

### 4.3 How much work to port?

**Very large.** The blocks layer is ~11,300 lines across ~103 files and touches:
1. The persistence layer (new storage backend)
2. The wave model (new segment operations)
3. The wavelet state management (three-tier state: delta, block, segment)
4. Protobuf definitions (2 new proto files)
5. Serialization/deserialization (custom binary block format)

A straight port would take an estimated **4-8 engineer-weeks** of focused work,
plus testing and migration tooling.

---

## 5. Recommendation: NO-GO on full port; PARTIAL extraction of key ideas

### 5.1 Rationale

1. **The compat shim already works.** The fragments infrastructure (transport,
   client applier, viewport hints) is already built and operating against the
   existing delta store. The blocks backend would improve performance at scale,
   but is not required for correctness.

2. **The code is large and tightly coupled.** The 103-file blocks layer has
   deep interdependencies: the skip-list aggregator, the fragment linking, the
   block sizing heuristics, and the custom binary format all depend on each
   other. Porting partial pieces would break invariants.

3. **Higher-value work exists.** Tags, archive, stored searches, the
   conversation renderer migration, and Jetty/Jakarta modernization all deliver
   more user-visible value per engineering hour.

4. **Snapshot storage alone solves the worst pain.** The single highest-impact
   improvement -- avoiding O(N) replay on startup -- can be achieved by adding
   **periodic snapshot persistence** to the existing delta store without the
   full blocks layer.

5. **The Wiab.pro code needs modernization.** It uses proto2, pre-Jakarta
   patterns, older Guava APIs, and a custom binary format. A direct port would
   accumulate technical debt.

### 5.2 Specific pieces worth extracting

| Idea | Difficulty | Value | Action |
|------|-----------|-------|--------|
| **Snapshot persistence** -- store periodic snapshots alongside deltas | Low | High | Implement natively in DeltaStore (add a `snapshots` collection/file alongside deltas; store every N deltas or on shutdown) |
| **SegmentId partitioning** -- already extracted | Done | Medium | Already in the codebase via the compat layer |
| **Skip-list history navigation concept** | Medium | Low (for now) | Defer until history browsing is a priority |
| **Block-level I/O bounding** | High | Medium | Defer; requires full storage redesign |
| **Operation aggregation** | Medium | Low | Defer; only valuable with the full blocks layer |

### 5.3 Recommended next steps

1. **Implement snapshot persistence** for the existing DeltaStore:
   - Add a `snapshots` side-table/file that stores `(waveletName, version,
     serializedSnapshot)` periodically (every 100 deltas, or on graceful
     shutdown)
   - On startup, load the latest snapshot and replay only the remaining deltas
   - This eliminates the O(N) startup problem without the blocks architecture

2. **Continue evolving the compat SegmentWaveletState** as documented in the
   blocks-adoption-plan.md remaining work items:
   - Storage-backed segment state (item 1 in the checklist)
   - Client FragmentRequester over ViewChannel (item 2)
   - Observability and metrics (item 3)

3. **Revisit the full blocks port** if/when:
   - Wavelets routinely exceed 100K versions
   - History browsing/scrubbing becomes a product priority
   - The compat layer's performance is measurably insufficient

---

## 6. Decision Summary

| Question | Answer |
|----------|--------|
| Full blocks/segments/history port? | **No-go** |
| Snapshot persistence (extracted idea)? | **Go** -- implement natively |
| Continue compat SegmentWaveletState? | **Go** -- already in progress |
| Revisit later? | **Yes** -- when scale demands it |

The Wiab.pro blocks architecture is a sophisticated and well-designed system
for large-scale wave storage. However, its ~11K-line footprint, tight internal
coupling, and the existence of a working compatibility shim make a full port
a poor investment at this stage. The highest-value idea -- snapshot persistence
-- should be extracted and implemented natively against the current DeltaStore,
delivering the primary scaling benefit at a fraction of the cost.
