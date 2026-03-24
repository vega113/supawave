# Snapshot Persistence for Apache Wave

**Date:** 2026-03-24
**Status:** Plan
**Author:** Wave Contributors

## Problem

Apache Wave currently replays ALL deltas from version 0 to reconstruct wavelet state on every wave load. The critical path is in `DeltaStoreBasedWaveletState.create()`:

```java
ImmutableList<WaveletDeltaRecord> deltas = readAll(deltasAccess, null);
WaveletData snapshot = WaveletDataUtil.buildWaveletFromDeltas(
    deltasAccess.getWaveletName(),
    Iterators.transform(deltas.iterator(), TRANSFORMED));
```

This is O(N) in the number of deltas. For active waves with thousands of deltas, server startup and wavelet loading become increasingly slow.

Wiab.pro addressed this through its "blocks" data layer (~11K lines, ~103 files), which was rejected in PR #74 as too complex. However, the key benefit -- snapshot persistence -- can be achieved with a much simpler design.

## Architecture

```text
                    ┌──────────────────────────────┐
                    │   DeltaStoreBasedWaveletState │
                    │         .create()             │
                    └──────────┬───────────────────┘
                               │
                    ┌──────────▼───────────────────┐
                    │   SnapshotStore               │
                    │   .getLatestSnapshot()        │
                    └──────────┬───────────────────┘
                               │
                  ┌────────────┼────────────────┐
                  │ found      │                │ not found
                  ▼            │                ▼
     ┌─────────────────┐      │    ┌────────────────────┐
     │ Deserialize      │      │    │ Replay all deltas  │
     │ snapshot at v=S  │      │    │ from version 0     │
     │ Replay deltas    │      │    │ (current behavior) │
     │ from v=S to v=N  │      │    └────────────────────┘
     └─────────────────┘      │
                               │
                    ┌──────────▼───────────────────┐
                    │  In appendDelta():           │
                    │  if (deltaCount % interval   │
                    │      == 0) take snapshot copy │
                    │  In persisterTask():         │
                    │  if (copy pending)           │
                    │    store snapshot after deltas│
                    └──────────────────────────────┘

Storage backends:

     ┌───────────────────┐     ┌───────────────────┐
     │ FileSnapshotStore  │     │ Mongo4SnapshotStore│
     │                    │     │                    │
     │ _snapshots/        │     │ 'snapshots'        │
     │  {wave}/{wavelet}/ │     │  collection        │
     │   v{NNN}.snapshot  │     │  {waveId,waveletId,│
     │                    │     │   version, data}   │
     └───────────────────┘     └───────────────────┘
```

## Design Decisions

### 1. Separate SnapshotStore (not extending DeltaStore)

The snapshot store is a **separate interface**, not an extension of `DeltaStore`. Rationale:

- `DeltaStore` returns `DeltasAccess` objects tied to wavelet lifecycle. Snapshots are simpler: write-once, read-latest.
- Keeps existing `DeltaStore` implementations (`FileDeltaStore`, `Mongo4DeltaStore`) untouched.
- Snapshots are optional -- if the store is absent or empty, the system falls back to full delta replay.
- Matches the Guice binding pattern: bind `SnapshotStore` alongside `DeltaStore` in `PersistenceModule`.

### 2. Proto-based Serialization via Existing WaveletSnapshot

The existing `WaveletSnapshot` proto in `waveclient-rpc.proto` (lines 121-135) already contains everything needed:

```protobuf
message WaveletSnapshot {
  required string wavelet_id = 1;
  repeated string participant_id = 2;
  repeated DocumentSnapshot document = 3;
  required federation.ProtocolHashedVersion version = 4;
  required int64 last_modified_time = 5;
  required string creator = 6;
  required int64 creation_time = 7;
}
```

The existing `SnapshotSerializer` class (`box/server/common/SnapshotSerializer.java`) already provides `serializeWavelet()` and `deserializeWavelet()` methods that convert between `ReadableWaveletData` and `WaveletSnapshot` proto. We reuse both directly -- no new proto definitions needed.

For the persistence envelope, we define a minimal new proto:

```protobuf
// In a new file: wave/src/proto/proto/org/waveprotocol/box/server/persistence/snapshot-store.proto
message PersistedWaveletSnapshot {
  required string wave_id = 1;
  required WaveletSnapshot snapshot = 2;
}
```

This wraps `WaveletSnapshot` with the `wave_id` needed for deserialization (since `WaveletSnapshot` only contains `wavelet_id`).

### 3. Interval-Based Snapshot Triggers (Not Time-Based)

Snapshots are created after every N deltas are persisted, configured via `core.snapshot_interval`. This matches Wiab.pro's `savingSnapshotPeriod` concept. Rationale:

- Delta count is deterministic and observable at the point of persistence.
- Time-based triggers add scheduling complexity and may miss fast-moving wavelets.
- Default of 100 deltas balances write amplification against load-time savings.

**Important**: Wave versions advance by operation count, not by delta count. A single delta may advance the version by multiple ops. Therefore, the snapshot trigger tracks **persisted delta count** explicitly via a counter (not `version % interval`), incrementing by the number of deltas in each persist batch. This avoids missed triggers from non-unit version steps.

### 4. Synchronous Snapshot Writes on Persist Thread

Snapshot writes happen **synchronously on the persist executor thread**, immediately after deltas are persisted. This is simpler than async and avoids concurrency issues:

- The persist executor already serializes writes per wavelet.
- Serialization cost is proportional to wavelet size, not delta count -- typically fast.
- No risk of a snapshot being written for a version whose deltas have not yet been persisted.

**Critical concurrency detail**: The `snapshot` field in `DeltaStoreBasedWaveletState` is a mutable `WaveletData` object that `appendDelta()` modifies on the container write thread, while `persisterTask` runs asynchronously on the persist executor. Reading `snapshot` from the persist thread without synchronization would risk capturing a partially-applied or ahead-of-persisted-version state.

**Solution**: When `appendDelta()` detects that the delta count has crossed a snapshot boundary, it takes a **defensive copy** via `WaveletDataUtil.copyWavelet(snapshot)` under the container lock and publishes it to the `pendingSnapshotCopy` `AtomicReference`. The persist thread atomically consumes this immutable copy via `getAndSet(null)` and serializes it after the deltas are durably stored. Because `appendDelta()` is serialized by the container lock, the copy is a consistent point-in-time view. The live `snapshot` field is never read from the persist thread, eliminating the concurrency hazard entirely.

### 5. Snapshot Reads Are Fallible and Non-Fatal

If a snapshot fails to deserialize (corrupt data, schema mismatch), the system logs a warning and falls back to full delta replay. Snapshots are a pure optimization -- deltas remain the authoritative source of truth.

## Interface Definitions

### SnapshotStore

```java
package org.waveprotocol.box.server.persistence;

import org.waveprotocol.wave.model.id.WaveletName;

/**
 * Stores periodic wavelet snapshots for fast load.
 * Snapshots are an optimization; deltas remain the source of truth.
 */
public interface SnapshotStore {

  /**
   * Returns the latest stored snapshot for the given wavelet, or null
   * if no snapshot exists.
   *
   * @param waveletName the wavelet to look up
   * @return the latest snapshot record, or null
   * @throws PersistenceException on storage failure
   */
  SnapshotRecord getLatestSnapshot(WaveletName waveletName)
      throws PersistenceException;

  /**
   * Stores a snapshot for the given wavelet at the given version.
   * If a snapshot already exists at this version, it is overwritten.
   *
   * @param waveletName the wavelet
   * @param snapshotData serialized PersistedWaveletSnapshot protobuf bytes
   *        (the canonical envelope format containing wave_id + WaveletSnapshot)
   * @param version the wavelet version this snapshot represents
   * @throws PersistenceException on storage failure
   */
  void storeSnapshot(WaveletName waveletName, byte[] snapshotData, long version)
      throws PersistenceException;

  /**
   * Deletes all snapshots for the given wavelet.
   * Called when the wavelet itself is deleted.
   */
  void deleteSnapshots(WaveletName waveletName) throws PersistenceException;
}
```

### SnapshotRecord

```java
package org.waveprotocol.box.server.persistence;

/**
 * A stored wavelet snapshot with its version metadata.
 */
public final class SnapshotRecord {
  private final long version;
  private final byte[] snapshotData;  // Serialized PersistedWaveletSnapshot

  public SnapshotRecord(long version, byte[] snapshotData) {
    this.version = version;
    this.snapshotData = snapshotData;
  }

  public long getVersion() { return version; }
  public byte[] getSnapshotData() { return snapshotData; }
}
```

## File List

### New Files (~350 lines total)

| File | Purpose | Est. Lines |
|------|---------|-----------|
| `wave/src/proto/proto/.../persistence/snapshot-store.proto` | `PersistedWaveletSnapshot` envelope proto | 15 |
| `wave/src/main/java/.../persistence/SnapshotStore.java` | Interface | 40 |
| `wave/src/main/java/.../persistence/SnapshotRecord.java` | Value class | 25 |
| `wave/src/main/java/.../persistence/file/FileSnapshotStore.java` | File-based implementation | 90 |
| `wave/src/main/java/.../persistence/mongodb4/Mongo4SnapshotStore.java` | MongoDB implementation | 80 |
| `wave/src/main/java/.../persistence/memory/MemorySnapshotStore.java` | In-memory (for tests) | 40 |
| `wave/src/test/java/.../persistence/SnapshotStoreTestBase.java` | Shared test logic | 60 |

### Modified Files (~100 lines of changes)

| File | Change | Est. Lines |
|------|--------|-----------|
| `wave/config/reference.conf` | Add `core.snapshot_interval = 100` | 5 |
| `.../persistence/PersistenceModule.java` | Bind `SnapshotStore` based on `delta_store_type` | 15 |
| `.../waveserver/WaveServerModule.java` | Inject `SnapshotStore`, pass to `loadWaveletState()` | 10 |
| `.../waveserver/DeltaStoreBasedWaveletState.java` | Use snapshot on `create()`, trigger snapshot on persist | 50 |
| `.../waveserver/DeltaStoreBasedSnapshotStore.java` | No changes needed (this class wraps DeltaStore for the WaveServer; snapshot store is orthogonal) | 0 |

**Total: ~450 lines** (well within the 500-line budget)

## Implementation Phases

### Phase 1: Core Infrastructure

1. Define `PersistedWaveletSnapshot` proto.
2. Create `SnapshotStore` interface and `SnapshotRecord` value class.
3. Implement `MemorySnapshotStore` for testing.
4. Add `core.snapshot_interval` config key (default: 100, 0 = disabled).

### Phase 2: DeltaStoreBasedWaveletState Integration

Modify `DeltaStoreBasedWaveletState.create()`:

```java
public static DeltaStoreBasedWaveletState create(
    DeltaStore.DeltasAccess deltasAccess,
    SnapshotStore snapshotStore,   // NEW parameter (nullable)
    int snapshotInterval,           // NEW parameter
    Executor persistExecutor) throws PersistenceException {

  if (deltasAccess.isEmpty()) {
    return new DeltaStoreBasedWaveletState(
        deltasAccess, ImmutableList.of(), null,
        snapshotStore, snapshotInterval, persistExecutor);
  }

  WaveletName waveletName = deltasAccess.getWaveletName();
  WaveletData snapshot = null;
  HashedVersion snapshotHashedVersion = null;

  // Try to load from snapshot store
  if (snapshotStore != null) {
    try {
      SnapshotRecord record = snapshotStore.getLatestSnapshot(waveletName);
      if (record != null && record.getVersion() <= deltasAccess.getEndVersion().getVersion()) {
        PersistedWaveletSnapshot persisted =
            PersistedWaveletSnapshot.parseFrom(record.getSnapshotData());
        snapshot = SnapshotSerializer.deserializeWavelet(
            persisted.getSnapshot(),
            WaveId.deserialise(persisted.getWaveId()));
        snapshotHashedVersion = snapshot.getHashedVersion();

        // Validate: the snapshot's hashed version must correspond to
        // an actual delta boundary in the delta store, with matching hash.
        if (!snapshotHashedVersion.equals(deltasAccess.getEndVersion())) {
          HashedVersion storedHash = deltasAccess.getAppliedAtVersion(
              snapshotHashedVersion.getVersion());
          if (storedHash == null || !storedHash.equals(snapshotHashedVersion)) {
            LOG.warning("Snapshot hashed version " + snapshotHashedVersion
                + " does not match delta store boundary (stored: " + storedHash
                + "), falling back to full replay");
            snapshot = null;
            snapshotHashedVersion = null;
          }
        }
      }
    } catch (Exception e) {
      LOG.warning("Failed to load snapshot for " + waveletName
          + ", falling back to full replay: " + e);
      snapshot = null;
      snapshotHashedVersion = null;
    }
  }

  // Replay deltas from snapshot version (or zero) to end
  try {
    if (snapshot != null) {
      // Partial replay: use readDeltasInRange with proper HashedVersion
      // validation to maintain the same integrity checks as full replay.
      // Wrapped in try-catch so any failure falls back to full replay
      // (preserving the "non-fatal snapshot reads" guarantee).
      try {
        HashedVersion startHash = snapshotHashedVersion;
        HashedVersion endHash = deltasAccess.getEndVersion();
        if (startHash.getVersion() < endHash.getVersion()) {
          ListReceiver<WaveletDeltaRecord> receiver = new ListReceiver<>();
          readDeltasInRange(deltasAccess, null, startHash, endHash, receiver);
          for (WaveletDeltaRecord record : receiver) {
            WaveletDataUtil.applyWaveletDelta(
                record.getTransformedDelta(), snapshot);
          }
        }
        // Final validation: snapshot version must match delta store end version
        if (!snapshot.getHashedVersion().equals(deltasAccess.getEndVersion())) {
          throw new IOException("Snapshot version " + snapshot.getHashedVersion()
              + " doesn't match expected end version " + deltasAccess.getEndVersion()
              + " after partial replay");
        }
      } catch (Exception e) {
        LOG.warning("Partial replay from snapshot failed for " + waveletName
            + ", falling back to full replay: " + e);
        snapshot = null;  // triggers full replay below
      }
    }

    if (snapshot == null) {
      // Full replay (current behavior, also used as fallback)
      ImmutableList<WaveletDeltaRecord> deltas = readAll(deltasAccess, null);
      snapshot = WaveletDataUtil.buildWaveletFromDeltas(
          waveletName, Iterators.transform(deltas.iterator(), TRANSFORMED));
    }

    // NOTE: The constructor precondition (deltasAccess.isEmpty() == deltas.isEmpty())
    // requires a new constructor overload that accepts a pre-built snapshot directly,
    // bypassing the delta list requirement. The new overload:
    //   DeltaStoreBasedWaveletState(DeltasAccess, WaveletData, SnapshotStore, int, Executor)
    // This constructor only checks (snapshot != null) when deltasAccess is non-empty,
    // and does not require a delta list parameter at all.
    return new DeltaStoreBasedWaveletState(
        deltasAccess, snapshot,
        snapshotStore, snapshotInterval, persistExecutor);
  } catch (IOException | OperationException e) {
    throw new PersistenceException("Failed to reconstruct wavelet state", e);
  }
}
```

**Constructor changes**: Add a new constructor overload that accepts only `deltasAccess` + `snapshot` (no delta list). The existing constructor with the delta list parameter remains for backward compatibility with tests. The new constructor validates `(deltasAccess.isEmpty()) == (snapshot == null)` and initializes `lastPersistedVersion` from `deltasAccess.getEndVersion()` as before.

Modify `appendDelta()` and `persisterTask` to trigger snapshot creation safely:

The snapshot trigger uses two mechanisms working together:

**In `appendDelta()` (container write thread, under container lock):**

```java
// New field: counts deltas since last snapshot
private int deltasSinceLastSnapshot = 0;

// New field: immutable snapshot copy queued for persist-time serialization.
// AtomicReference ensures atomic consume via getAndSet(null) -- no lost updates.
private final AtomicReference<ReadableWaveletData> pendingSnapshotCopy =
    new AtomicReference<>(null);

@Override
public void appendDelta(WaveletDeltaRecord deltaRecord) throws OperationException {
  // ... existing delta application logic ...

  deltasSinceLastSnapshot++;
  if (snapshotStore != null && snapshotInterval > 0
      && deltasSinceLastSnapshot >= snapshotInterval) {
    // Take a defensive copy under the container lock.
    // This is safe because appendDelta() is serialized by the container.
    // If a previous snapshot copy has not yet been consumed by the persist
    // thread, it is overwritten -- this is acceptable because the newer
    // snapshot subsumes the older one.
    pendingSnapshotCopy.set(WaveletDataUtil.copyWavelet(snapshot));
    deltasSinceLastSnapshot = 0;
  }
}
```

**In `persisterTask` (persist executor thread, after delta persistence succeeds):**

```java
// After successful delta persistence:
// Atomically consume the pending snapshot copy (returns null if none pending).
ReadableWaveletData snapCopy = pendingSnapshotCopy.getAndSet(null);
if (snapCopy != null) {
  try {
    WaveletSnapshot proto = SnapshotSerializer.serializeWavelet(
        snapCopy, snapCopy.getHashedVersion());
    PersistedWaveletSnapshot persisted = PersistedWaveletSnapshot.newBuilder()
        .setWaveId(snapCopy.getWaveId().serialise())
        .setSnapshot(proto)
        .build();
    snapshotStore.storeSnapshot(
        deltasAccess.getWaveletName(),
        persisted.toByteArray(),
        snapCopy.getHashedVersion().getVersion());
  } catch (Exception e) {
    LOG.warning("Failed to store snapshot at version "
        + snapCopy.getHashedVersion().getVersion(), e);
    // Non-fatal: snapshots are optimization only
  }
}
```

**Why this is correct:**

- **No concurrency hazard**: The `pendingSnapshotCopy` is an `AtomicReference` holding an **immutable deep copy** created under the container lock in `appendDelta()`. The persist thread atomically consumes it via `getAndSet(null)`, ensuring no lost updates. If `appendDelta()` overwrites a not-yet-consumed copy, the newer snapshot subsumes the older one (both are valid; the newer one is strictly better). The live `snapshot` field is never read from the persist thread.
- **Delta count tracking**: `deltasSinceLastSnapshot` is incremented per delta (not per version), correctly handling deltas with multi-op version advancement.
- **Snapshot version matches persisted deltas**: The snapshot copy is taken at the time the delta is appended. By the time the persist task runs, the deltas up to at least that version will be persisted (since persist processes all deltas up to `latestVersionToPersist`). In the rare case where the persist task runs before the snapshot's deltas are fully persisted, the snapshot is harmlessly ahead -- it will be validated on load via the delta store boundary check.

### Phase 3: File Backend

`FileSnapshotStore` stores snapshots as protobuf files:

```text
_snapshots/
  {encoded-wave-id}/
    {encoded-wavelet-id}/
      v{version}.snapshot       # serialized PersistedWaveletSnapshot
```

- `getLatestSnapshot()`: list files in directory, parse version from filename, return highest.
- `storeSnapshot()`: write protobuf bytes to new file, then optionally prune old snapshots (keep last 3).
- `deleteSnapshots()`: delete the wavelet's snapshot directory.

### Phase 4: MongoDB Backend

`Mongo4SnapshotStore` uses a `snapshots` collection:

```javascript
{
  "_id": ObjectId,
  "waveId": "example.com!w+abc",
  "waveletId": "example.com!conv+root",
  "version": NumberLong(500),
  "data": BinData(0, "...serialized PersistedWaveletSnapshot...")
}
```

- Index: `{waveId: 1, waveletId: 1, version: -1}` (unique compound, descending version for fast latest lookup).
- `getLatestSnapshot()`: `find({waveId, waveletId}).sort({version: -1}).limit(1)`.
- `storeSnapshot()`: `insertOne()`.
- Prune: keep at most 3 snapshots per wavelet (delete older ones after insert).

### Phase 5: Tests

- Unit tests for `SnapshotSerializer` round-trip (already partially tested in `SnapshotSerializerTest`).
- `SnapshotStoreTestBase`: shared tests for store/retrieve/delete lifecycle.
- Integration test: create wavelet with N deltas, verify snapshot created, verify load uses snapshot (mock to count delta reads).
- Regression test: verify corrupt snapshot triggers graceful fallback.

## Concurrency Considerations

1. **Snapshot reads in `create()`**: happen on the wavelet load executor, before the state object is shared. No contention.

2. **Snapshot writes -- avoiding the mutable `snapshot` race**: The `snapshot` field in `DeltaStoreBasedWaveletState` is a mutable `WaveletData` that `appendDelta()` modifies on the container write thread. The `persisterTask` runs asynchronously on the persist executor. **Reading `snapshot` directly from the persist thread is unsafe** because `appendDelta()` can mutate it concurrently, producing a snapshot that is ahead of the persisted delta version or in a partially-applied state.

   The solution is to take a **defensive immutable copy** via `WaveletDataUtil.copyWavelet(snapshot)` inside `appendDelta()` (which is called under the container's write lock), and publish it via `pendingSnapshotCopy` (an `AtomicReference<ReadableWaveletData>`). The persist thread atomically consumes it via `getAndSet(null)`, ensuring that a concurrent write from `appendDelta()` cannot be silently dropped. If `appendDelta()` overwrites a not-yet-consumed copy, the newer snapshot subsumes the older one. The live `snapshot` field is never read from the persist thread.

3. **Store-level concurrency**: `FileSnapshotStore` writes are per-wavelet-directory and non-overlapping. `Mongo4SnapshotStore` uses `insertOne()` which is atomic. Neither requires explicit locking beyond what the wavelet state already provides.

4. **Snapshot version vs. persisted delta version**: The snapshot copy is taken at the moment `appendDelta()` crosses the interval threshold. The persist task may not yet have persisted deltas up to the snapshot's version. However, `persist()` is always called with the version of the last appended delta, so by the time the persist task runs (which writes all deltas up to `latestVersionToPersist`), the deltas will be durably stored before the snapshot is written. The snapshot store write is placed **after** the delta persistence call in `persisterTask`, guaranteeing the ordering invariant: persisted deltas always cover at least up to the snapshot version.

## Performance Expectations

| Scenario | Current (O(N)) | With Snapshots (O(K)) | Improvement |
|----------|----------------|----------------------|-------------|
| Wavelet with 100 deltas | ~100 delta reads | ~100 delta reads (no snapshot yet if interval=100, snapshot created after) | Same |
| Wavelet with 500 deltas | ~500 delta reads | ~0 delta reads (snapshot at v=500) or ~99 reads (snapshot at v=400 + 99 deltas) | 5-500x |
| Wavelet with 10,000 deltas | ~10,000 delta reads | ~99 delta reads | ~100x |
| Server restart with 1,000 wavelets x 500 deltas each | ~500,000 delta reads | ~99,000 delta reads | ~5x |

**Storage overhead**: Each snapshot is roughly the size of the current wavelet state (participants + document content). For a typical wave with a few KB of content, snapshots are 2-10 KB each. Keeping 2-3 snapshots per wavelet adds negligible overhead.

**Write amplification**: One snapshot write per 100 delta persists. Snapshot writes are small relative to the delta batch writes they accompany.

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Snapshot deserialization fails (corrupt/incompatible) | Low | None -- falls back to full delta replay | Graceful fallback with warning log |
| Proto schema changes break stored snapshots | Low | Low -- snapshots are re-creatable | Version field in proto envelope; regenerate on load failure |
| Snapshot lags behind delta tip (stale data served) | None | N/A | Snapshots are only used as a starting point; remaining deltas are always replayed from delta store |
| Storage space from accumulated snapshots | Low | Low | Prune old snapshots (keep last 2-3) |
| Performance regression from snapshot serialization | Low | Low | Serialization happens on persist thread, after deltas are written; wavelet operations are not blocked |
| Concurrent access to snapshot files | Low | Medium | Persist executor is single-threaded per wavelet; file writes are atomic (write to temp file, rename) |

## Configuration

Add to `wave/config/reference.conf`:

```hocon
core {
  # ... existing settings ...

  # Number of deltas between automatic snapshot persistence.
  # 0 = disabled (full delta replay on every load, current behavior).
  # Recommended: 100-500 depending on wavelet activity patterns.
  snapshot_interval = 100
}
```

## Relationship to Wiab.pro

This design is inspired by Wiab.pro's snapshot persistence but is dramatically simpler:

| Aspect | Wiab.pro | This Design |
|--------|----------|-------------|
| Lines of code | ~11K across ~103 files | ~450 across ~10 files |
| Architecture | Blocks/segments layer | Simple store interface |
| Proto format | Custom `ProtoWaveletSnapshot` | Reuses existing `WaveletSnapshot` + thin envelope |
| Snapshot trigger | `savingSnapshotPeriod` | `snapshot_interval` |
| Storage | Integrated into blocks | Standalone store (file/MongoDB) |
| Snapshot history | Full history with index | Keep last 2-3 only |
| Coupling | Deeply coupled to blocks layer | Orthogonal to `DeltaStore` |

## Non-Goals

- **Incremental snapshots / diffs**: Out of scope. Full snapshots are simple and sufficient.
- **Snapshot compaction**: Not needed at current scale.
- **User-visible snapshot API**: Snapshots are internal optimization only.
- **Migration tooling**: No migration needed. Snapshots are created lazily. First load after upgrade does full replay; subsequent loads benefit from snapshots.
