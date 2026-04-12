/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server.waveserver;

import static java.lang.String.format;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;

import org.waveprotocol.box.common.ListReceiver;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.common.comms.WaveClientRpc.WaveletSnapshot;
import org.waveprotocol.box.server.common.SnapshotSerializer;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.SnapshotRecord;
import org.waveprotocol.box.server.persistence.SnapshotStore;
import org.waveprotocol.box.server.persistence.protos.ProtoSnapshotStoreData.PersistedWaveletSnapshot;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simplistic {@link DeltaStore}-backed wavelet state implementation
 * which goes to persistent storage for every history request.
 *
 * TODO(soren): rewire this class to be backed by {@link WaveletStore} and
 * read the snapshot from there instead of computing it in the
 * DeltaStoreBasedWaveletState constructor.
 *
 * @author soren@google.com (Soren Lassen)
 * @author akaplanov@gmail.com (Andew Kaplanov)
 */
class DeltaStoreBasedWaveletState implements WaveletState {

  private static final Log LOG = Log.get(DeltaStoreBasedWaveletState.class);

  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());

  private static final HashedVersionFactory HASH_FACTORY =
      new HashedVersionFactoryImpl(URI_CODEC);

  private static final Function<WaveletDeltaRecord, TransformedWaveletDelta> TRANSFORMED =
      new Function<WaveletDeltaRecord, TransformedWaveletDelta>() {
        @Override
        public TransformedWaveletDelta apply(WaveletDeltaRecord record) {
          return record.getTransformedDelta();
        }
      };

  /**
   * @return An entry keyed by a hashed version with the given version number,
   *         if any, otherwise null.
   */
  private static <T> Map.Entry<HashedVersion, T> lookupCached(NavigableMap<HashedVersion, T> map,
      long version) {
    // Smallest key with version number >= version.
    HashedVersion key = HashedVersion.unsigned(version);
    Map.Entry<HashedVersion, T> entry = map.ceilingEntry(key);
    return (entry != null && entry.getKey().getVersion() == version) ? entry : null;
  }

  /**
   * Creates a new delta store based state (legacy, without snapshot support).
   *
   * The executor must ensure that only one thread executes at any time for each
   * state instance.
   *
   * @param deltasAccess delta store accessor
   * @param persistExecutor executor for making persistence calls
   * @return a state initialized from the deltas
   * @throws PersistenceException if a failure occurs while reading or
   *         processing stored deltas
   */
  public static DeltaStoreBasedWaveletState create(DeltaStore.DeltasAccess deltasAccess,
      Executor persistExecutor) throws PersistenceException {
    return create(deltasAccess, null, 0, persistExecutor);
  }

  /**
   * Creates a new delta store based state, optionally loading from a snapshot.
   *
   * If a valid snapshot is available in the snapshot store, only deltas after
   * the snapshot version are replayed. If no snapshot is found or validation
   * fails, falls back to full replay from version 0.
   *
   * @param deltasAccess delta store accessor
   * @param snapshotStore snapshot store (nullable; null disables snapshots)
   * @param snapshotInterval delta count between snapshot writes (0 = disabled)
   * @param persistExecutor executor for making persistence calls
   * @return a state initialized from the deltas
   * @throws PersistenceException if a failure occurs while reading or
   *         processing stored deltas
   */
  public static DeltaStoreBasedWaveletState create(DeltaStore.DeltasAccess deltasAccess,
      SnapshotStore snapshotStore, int snapshotInterval,
      Executor persistExecutor) throws PersistenceException {
    if (deltasAccess.isEmpty()) {
      return new DeltaStoreBasedWaveletState(deltasAccess, ImmutableList.<WaveletDeltaRecord>of(),
          null, snapshotStore, snapshotInterval, persistExecutor);
    }

    WaveletName waveletName = deltasAccess.getWaveletName();
    WaveletData snapshot = null;

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
          HashedVersion snapshotHashedVersion = snapshot.getHashedVersion();

          // Validate: the snapshot's hashed version must correspond to
          // an actual delta boundary in the delta store, with matching hash.
          if (!snapshotHashedVersion.equals(deltasAccess.getEndVersion())) {
            HashedVersion storedHash = deltasAccess.getAppliedAtVersion(
                snapshotHashedVersion.getVersion());
            if (storedHash == null || !storedHash.equals(snapshotHashedVersion)) {
              LOG.warning("Snapshot hashed version " + snapshotHashedVersion
                  + " does not match delta store boundary (stored: " + storedHash
                  + "), falling back to full replay for " + waveletName);
              snapshot = null;
            }
          }
        }
      } catch (Exception e) {
        LOG.warning("Failed to load snapshot for " + waveletName
            + ", falling back to full replay: " + e);
        snapshot = null;
      }
    }

    // Replay deltas from snapshot version (or zero) to end
    try {
      if (snapshot != null) {
        // Partial replay from snapshot version to delta store end
        try {
          HashedVersion startHash = snapshot.getHashedVersion();
          HashedVersion endHash = deltasAccess.getEndVersion();
          if (startHash.getVersion() < endHash.getVersion()) {
            ListReceiver<WaveletDeltaRecord> receiver = new ListReceiver<WaveletDeltaRecord>();
            readDeltasInRange(deltasAccess, null, startHash, endHash, receiver);
            for (WaveletDeltaRecord record : receiver) {
              WaveletDataUtil.applyWaveletDelta(record.getTransformedDelta(), snapshot);
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

      return new DeltaStoreBasedWaveletState(
          deltasAccess, snapshot,
          snapshotStore, snapshotInterval, persistExecutor);
    } catch (IOException e) {
      throw new PersistenceException("Failed to read stored deltas", e);
    } catch (OperationException e) {
      throw new PersistenceException("Failed to compose stored deltas", e);
    }
  }

  /**
   * Reads all deltas from persistent storage.
   */
  private static ImmutableList<WaveletDeltaRecord> readAll(WaveletDeltaRecordReader reader,
      ConcurrentNavigableMap<HashedVersion, WaveletDeltaRecord> cachedDeltas)
      throws IOException {
    HashedVersion startVersion = HASH_FACTORY.createVersionZero(reader.getWaveletName());
    HashedVersion endVersion = reader.getEndVersion();
    ListReceiver<WaveletDeltaRecord> receiver = new ListReceiver<WaveletDeltaRecord>();
    readDeltasInRange(reader, cachedDeltas, startVersion, endVersion, receiver);
    return ImmutableList.copyOf(receiver);
  }

  private static void readDeltasInRange(WaveletDeltaRecordReader reader,
      ConcurrentNavigableMap<HashedVersion, WaveletDeltaRecord> cachedDeltas,
      HashedVersion startVersion, HashedVersion endVersion, Receiver<WaveletDeltaRecord> receiver)
      throws IOException {
    WaveletDeltaRecord delta = getDelta(reader, cachedDeltas, startVersion);
    Preconditions.checkArgument(delta != null && delta.getAppliedAtVersion().equals(startVersion),
        "invalid start version");
    for (;;) {
      if (!receiver.put(delta)) {
        return;
      }
      if (delta.getResultingVersion().getVersion() >= endVersion.getVersion()) {
        break;
      }
      delta = getDelta(reader, cachedDeltas, delta.getResultingVersion());
      if (delta == null) {
        break;
      }
    }
    Preconditions.checkArgument(delta != null && delta.getResultingVersion().equals(endVersion),
        "invalid end version");
  }

  private static WaveletDeltaRecord getDelta(WaveletDeltaRecordReader reader,
      ConcurrentNavigableMap<HashedVersion, WaveletDeltaRecord> cachedDeltas,
      HashedVersion version) throws IOException {
    WaveletDeltaRecord delta = reader.getDelta(version.getVersion());
    if (delta == null && cachedDeltas != null) {
      delta = cachedDeltas.get(version);
    }
    return delta;
  }

  private final Executor persistExecutor;
  private final HashedVersion versionZero;
  private final DeltaStore.DeltasAccess deltasAccess;

  /** Optional snapshot store for periodic snapshot persistence. */
  private final SnapshotStore snapshotStore;

  /** Number of deltas between snapshot writes. 0 = disabled. */
  private final int snapshotInterval;

  /** Counts deltas appended since last snapshot was captured. */
  private int deltasSinceLastSnapshot = 0;

  /**
   * Immutable snapshot copy queued for persist-time serialization.
   * Set by appendDelta() under the container lock when the delta count
   * crosses a snapshot boundary. Consumed atomically by the persist
   * thread via getAndSet(null).
   */
  private final AtomicReference<ReadableWaveletData> pendingSnapshotCopy =
      new AtomicReference<>(null);

  /** The lock that guards access to persistence related state. */
  private final Object persistLock = new Object();

  /**
   * Indicates the version of the latest appended delta that was already requested to be
   * persisted.
   */
  private HashedVersion latestVersionToPersist = null;

  /** The persist task that will be executed next. */
  private ListenableFutureTask<Void> nextPersistTask = null;

  /**
   * Processes the persist task and checks if there is another task to do when
   * one task is done. In such a case, it writes all waiting to be persisted
   * deltas to persistent storage in one operation.
   */
  private final Callable<Void> persisterTask = new Callable<Void>() {
    @Override
    public Void call() throws PersistenceException {
      HashedVersion last;
      HashedVersion version;
      ListenableFutureTask<Void> queuedTask = null;
      boolean succeeded = false;
      synchronized (persistLock) {
        last = lastPersistedVersion.get();
        version = latestVersionToPersist;
      }
      try {
        if (last != null && version.getVersion() <= last.getVersion()) {
          LOG.info("Attempt to persist version " + version
              + " smaller than last persisted version " + last);
          // Done, version is already persisted.
          version = last;
        } else {
          HashedVersion persistBaseVersion = resolvePersistBaseVersion(last, version);
          if (persistBaseVersion.getVersion() >= version.getVersion()) {
            version = persistBaseVersion;
          } else {
            ImmutableList.Builder<WaveletDeltaRecord> deltas = ImmutableList.builder();
            HashedVersion v = persistBaseVersion;
            do {
              WaveletDeltaRecord d = getCachedDeltaOrThrow(v, version,
                  "while persisting up to ");
              deltas.add(d);
              v = d.getResultingVersion();
            } while (v.getVersion() < version.getVersion());
            Preconditions.checkState(v.equals(version));
            try {
              deltasAccess.append(deltas.build());
            } catch (PersistenceException e) {
              recoverableAppendFailureBase.set(persistBaseVersion);
              throw e;
            }
          }
        }
        // After successful delta persistence, store a pending snapshot if any.
        // Atomically consume the pending copy, then check whether its version
        // is covered by the deltas we just persisted. If the snapshot is ahead
        // of what is durable, put it back for the next persist task.
        if (snapshotStore != null) {
          ReadableWaveletData snapCopy = pendingSnapshotCopy.getAndSet(null);
          if (snapCopy != null) {
            if (snapCopy.getHashedVersion().getVersion() <= version.getVersion()) {
              // Safe to store: all referenced deltas are durable.
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
                    + snapCopy.getHashedVersion().getVersion() + " for "
                    + deltasAccess.getWaveletName() + ": " + e);
                // Non-fatal: snapshots are optimization only
              }
            } else {
              // Snapshot is ahead of persisted deltas -- put it back.
              // Use compareAndSet(null, snapCopy) so we don't clobber a
              // newer copy that appendDelta() may have set concurrently.
              // If CAS fails, the newer copy subsumes this one (and will
              // be picked up by the next persist task).
              pendingSnapshotCopy.compareAndSet(null, snapCopy);
            }
          }
        }
        succeeded = true;
      } finally {
        synchronized (persistLock) {
          if (succeeded) {
            Preconditions.checkState(last == lastPersistedVersion.get(),
                "lastPersistedVersion changed while we were writing to storage");
            lastPersistedVersion.set(version);
            recoverableAppendFailureBase.set(null);
          }
          queuedTask = nextPersistTask;
          nextPersistTask = null;
          if (queuedTask == null) {
            latestVersionToPersist = null;
          }
        }
        if (queuedTask != null) {
          persistExecutor.execute(queuedTask);
        }
      }
      return null;
    }
  };

  private HashedVersion resolvePersistBaseVersion(HashedVersion last, HashedVersion targetVersion)
      throws PersistenceException {
    HashedVersion expectedDurableVersion = (last == null) ? versionZero : last;
    HashedVersion durableEndVersion = deltasAccess.getEndVersion();
    if (durableEndVersion == null) {
      if (last != null) {
        throw new PersistenceException("Unexpected empty delta store for "
            + deltasAccess.getWaveletName() + ": expected durable end version "
            + expectedDurableVersion + " while persisting up to " + targetVersion);
      }
      return expectedDurableVersion;
    }
    if (durableEndVersion.equals(expectedDurableVersion)) {
      return expectedDurableVersion;
    }
    HashedVersion failedAppendBase = recoverableAppendFailureBase.get();
    if (failedAppendBase != null && failedAppendBase.equals(expectedDurableVersion)) {
      HashedVersion recoveredVersion = findRecoverableDurablePrefix(
          expectedDurableVersion, durableEndVersion, targetVersion);
      if (recoveredVersion != null) {
        LOG.warning("Recovering durable prefix for " + deltasAccess.getWaveletName()
            + ": advancing persist base from " + expectedDurableVersion + " to "
            + recoveredVersion + " after a previous append failure");
        return recoveredVersion;
      }
    }
    throw new PersistenceException("Refusing to persist stale wavelet state for "
        + deltasAccess.getWaveletName() + ": expected durable end version "
        + expectedDurableVersion + " but found " + durableEndVersion);
  }

  private HashedVersion findRecoverableDurablePrefix(HashedVersion expectedDurableVersion,
      HashedVersion durableEndVersion, HashedVersion targetVersion) throws PersistenceException {
    if (durableEndVersion.getVersion() <= expectedDurableVersion.getVersion()
        || durableEndVersion.getVersion() > targetVersion.getVersion()) {
      return null;
    }
    HashedVersion v = expectedDurableVersion;
    while (v.getVersion() < durableEndVersion.getVersion()) {
      WaveletDeltaRecord d = getCachedDeltaOrThrow(v, targetVersion,
          "while reconciling durable prefix " + durableEndVersion + " for ");
      v = d.getResultingVersion();
    }
    return v.equals(durableEndVersion) ? durableEndVersion : null;
  }

  private WaveletDeltaRecord getCachedDeltaOrThrow(HashedVersion appliedVersion,
      HashedVersion targetVersion, String contextPrefix) throws PersistenceException {
    WaveletDeltaRecord d = cachedDeltas.get(appliedVersion);
    if (d == null) {
      throw new PersistenceException("Missing cached delta for "
          + deltasAccess.getWaveletName() + " at applied version " + appliedVersion + " "
          + contextPrefix + targetVersion);
    }
    return d;
  }

  /** Keyed by appliedAtVersion. */
  private final ConcurrentNavigableMap<HashedVersion, ByteStringMessage<ProtocolAppliedWaveletDelta>> appliedDeltas =
      new ConcurrentSkipListMap<HashedVersion, ByteStringMessage<ProtocolAppliedWaveletDelta>>();

  /** Keyed by appliedAtVersion. */
  private final ConcurrentNavigableMap<HashedVersion, WaveletDeltaRecord> cachedDeltas =
      new ConcurrentSkipListMap<HashedVersion, WaveletDeltaRecord>();

  /**
   * Records the durable base version for the most recent append that threw, so
   * a later retry can distinguish a partial self-append from an unrelated stale writer.
   */
  private final AtomicReference<HashedVersion> recoverableAppendFailureBase =
      new AtomicReference<HashedVersion>(null);

  /** Is null if the wavelet state is empty. */
  private WaveletData snapshot;

  /**
   * Last version persisted with a call to persist(), or null if never called.
   * It's an atomic reference so we can set in one thread (which
   * asynchronously writes deltas to storage) and read it in another,
   * simultaneously.
   */
  private final AtomicReference<HashedVersion> lastPersistedVersion;

  /**
   * Constructs a wavelet state with the given deltas and snapshot (legacy, no snapshot store).
   * The deltas must be the contents of deltasAccess, and they
   * must be contiguous from version zero.
   * The snapshot must be the composition of the deltas, or null if there
   * are no deltas. The constructed object takes ownership of the
   * snapshot and will mutate it if appendDelta() is called.
   */
  @VisibleForTesting
  DeltaStoreBasedWaveletState(DeltaStore.DeltasAccess deltasAccess,
      List<WaveletDeltaRecord> deltas, WaveletData snapshot, Executor persistExecutor) {
    this(deltasAccess, deltas, snapshot, null, 0, persistExecutor);
  }

  /**
   * Constructs a wavelet state with the given deltas, snapshot and snapshot store.
   */
  @VisibleForTesting
  DeltaStoreBasedWaveletState(DeltaStore.DeltasAccess deltasAccess,
      List<WaveletDeltaRecord> deltas, WaveletData snapshot,
      SnapshotStore snapshotStore, int snapshotInterval,
      Executor persistExecutor) {
    Preconditions.checkArgument(deltasAccess.isEmpty() == deltas.isEmpty());
    Preconditions.checkArgument(deltas.isEmpty() == (snapshot == null));
    this.persistExecutor = persistExecutor;
    this.versionZero = HASH_FACTORY.createVersionZero(deltasAccess.getWaveletName());
    this.deltasAccess = deltasAccess;
    this.snapshot = snapshot;
    this.snapshotStore = snapshotStore;
    this.snapshotInterval = snapshotInterval;
    this.lastPersistedVersion = new AtomicReference<HashedVersion>(deltasAccess.getEndVersion());
  }

  /**
   * Constructs a wavelet state with a pre-built snapshot (no delta list required).
   * Used by the snapshot-aware create() factory method.
   */
  private DeltaStoreBasedWaveletState(DeltaStore.DeltasAccess deltasAccess,
      WaveletData snapshot, SnapshotStore snapshotStore, int snapshotInterval,
      Executor persistExecutor) {
    Preconditions.checkArgument(deltasAccess.isEmpty() == (snapshot == null));
    this.persistExecutor = persistExecutor;
    this.versionZero = HASH_FACTORY.createVersionZero(deltasAccess.getWaveletName());
    this.deltasAccess = deltasAccess;
    this.snapshot = snapshot;
    this.snapshotStore = snapshotStore;
    this.snapshotInterval = snapshotInterval;
    this.lastPersistedVersion = new AtomicReference<HashedVersion>(deltasAccess.getEndVersion());
  }

  @Override
  public WaveletName getWaveletName() {
    return deltasAccess.getWaveletName();
  }

  @Override
  public ReadableWaveletData getSnapshot() {
    return snapshot;
  }

  @Override
  public HashedVersion getCurrentVersion() {
    return (snapshot == null) ? versionZero : snapshot.getHashedVersion();
  }

  @Override
  public HashedVersion getLastPersistedVersion() {
    HashedVersion version = lastPersistedVersion.get();
    return (version == null) ? versionZero : version;
  }

  @Override
  public HashedVersion getHashedVersion(long version) {
    final Entry<HashedVersion, WaveletDeltaRecord> cachedEntry =
        lookupCached(cachedDeltas, version);
    if (version == 0) {
      return versionZero;
    } else if (snapshot == null) {
      return null;
    } else if (version == snapshot.getVersion()) {
      return snapshot.getHashedVersion();
    } else {
      WaveletDeltaRecord delta;
      try {
        delta = lookup(version);
      } catch (IOException e) {
        throw new RuntimeIOException(new IOException(format("Version : %d", version), e));
      }
      if (delta == null && cachedEntry != null) {
        return cachedEntry.getKey();
      } else {
       return delta != null ? delta.getAppliedAtVersion() : null;
      }
    }
  }

  @Override
  public TransformedWaveletDelta getTransformedDelta(
      final HashedVersion beginVersion) {
    WaveletDeltaRecord delta = cachedDeltas.get(beginVersion);
    if (delta != null) {
      return delta.getTransformedDelta();
    } else {
      WaveletDeltaRecord nowDelta;
      try {
        nowDelta = lookup(beginVersion.getVersion());
      } catch (IOException e) {
        throw new RuntimeIOException(new IOException(format("Begin version : %s",
            beginVersion.toString()), e));
      }
      return nowDelta != null ? nowDelta.getTransformedDelta() : null;
    }
  }

  @Override
  public TransformedWaveletDelta getTransformedDeltaByEndVersion(final HashedVersion endVersion) {
    Preconditions.checkArgument(endVersion.getVersion() > 0, "end version %s is not positive",
        endVersion);
    Entry<HashedVersion, WaveletDeltaRecord> transformedEntry =
        cachedDeltas.lowerEntry(endVersion);
    final WaveletDeltaRecord cachedDelta =
        transformedEntry != null ? transformedEntry.getValue() : null;
    if (snapshot == null) {
      return null;
    } else {
      WaveletDeltaRecord deltaRecord = getDeltaRecordByEndVersion(endVersion);
      TransformedWaveletDelta delta;
      if (deltaRecord == null && cachedDelta != null
          && cachedDelta.getResultingVersion().equals(endVersion)) {
        delta = cachedDelta.getTransformedDelta();
      } else {
        delta = deltaRecord != null ? deltaRecord.getTransformedDelta() : null;
      }
      return delta;
    }
  }

  @Override
  public void getTransformedDeltaHistory(final HashedVersion startVersion,
    final HashedVersion endVersion, final Receiver<TransformedWaveletDelta> receiver) {
    try {
      readDeltasInRange(deltasAccess, cachedDeltas, startVersion, endVersion,
          new Receiver<WaveletDeltaRecord>() {
            @Override
            public boolean put(WaveletDeltaRecord delta) {
              return receiver.put(delta.getTransformedDelta());
            }
          });
    } catch (IOException e) {
      throw new RuntimeIOException(new IOException(format("Start version : %s, end version: %s",
          startVersion.toString(), endVersion.toString()), e));
    }
  }

  @Override
  public ByteStringMessage<ProtocolAppliedWaveletDelta> getAppliedDelta(
      HashedVersion beginVersion) {
    WaveletDeltaRecord delta = cachedDeltas.get(beginVersion);
    if (delta != null) {
      return delta.getAppliedDelta();
    } else {
      WaveletDeltaRecord record = null;
      try {
        record = lookup(beginVersion.getVersion());
      } catch (IOException e) {
        throw new RuntimeIOException(new IOException(format("Begin version : %s",
            beginVersion.toString()), e));
      }
      return record != null ? record.getAppliedDelta() : null;
    }
  }

  @Override
  public ByteStringMessage<ProtocolAppliedWaveletDelta> getAppliedDeltaByEndVersion(
      final HashedVersion endVersion) {
    Preconditions.checkArgument(endVersion.getVersion() > 0,
        "end version %s is not positive", endVersion);
    Entry<HashedVersion, WaveletDeltaRecord> appliedEntry =
        cachedDeltas.lowerEntry(endVersion);
    final ByteStringMessage<ProtocolAppliedWaveletDelta> cachedDelta =
        appliedEntry != null ? appliedEntry.getValue().getAppliedDelta() : null;
    WaveletDeltaRecord deltaRecord = getDeltaRecordByEndVersion(endVersion);
    ByteStringMessage<ProtocolAppliedWaveletDelta> appliedDelta;
    if (deltaRecord == null && isDeltaBoundary(endVersion)) {
      appliedDelta = cachedDelta;
    } else {
      appliedDelta = deltaRecord != null ? deltaRecord.getAppliedDelta() : null;
    }
    return appliedDelta;
  }

  @Override
  public void getAppliedDeltaHistory(HashedVersion startVersion, HashedVersion endVersion,
      final Receiver<ByteStringMessage<ProtocolAppliedWaveletDelta>> receiver) {
    Preconditions.checkArgument(startVersion.getVersion() < endVersion.getVersion());
    try {
      readDeltasInRange(deltasAccess, cachedDeltas, startVersion, endVersion, new Receiver<WaveletDeltaRecord>() {
        @Override
        public boolean put(WaveletDeltaRecord delta) {
          return receiver.put(delta.getAppliedDelta());
        }
      });
    } catch (IOException e) {
      throw new RuntimeIOException(new IOException(format("Start version : %s, end version: %s",
          startVersion.toString(), endVersion.toString()), e));
    }
  }

  @Override
  public void appendDelta(WaveletDeltaRecord deltaRecord)
      throws OperationException {
    HashedVersion currentVersion = getCurrentVersion();
    Preconditions.checkArgument(currentVersion.equals(deltaRecord.getAppliedAtVersion()),
        "Applied version %s doesn't match current version %s", deltaRecord.getAppliedAtVersion(),
        currentVersion);

    if (deltaRecord.getAppliedAtVersion().getVersion() == 0) {
      Preconditions.checkState(lastPersistedVersion.get() == null);
      snapshot = WaveletDataUtil.buildWaveletFromFirstDelta(getWaveletName(), deltaRecord.getTransformedDelta());
    } else {
      WaveletDataUtil.applyWaveletDelta(deltaRecord.getTransformedDelta(), snapshot);
    }

    // Now that we built the snapshot without any exceptions, we record the delta.
    cachedDeltas.put(deltaRecord.getAppliedAtVersion(), deltaRecord);

    // Check if we should capture a snapshot for persistence.
    deltasSinceLastSnapshot++;
    if (snapshotStore != null && snapshotInterval > 0
        && deltasSinceLastSnapshot >= snapshotInterval) {
      // Take a defensive copy under the container lock.
      // If a previous copy has not yet been consumed, the newer one subsumes it.
      pendingSnapshotCopy.set(WaveletDataUtil.copyWavelet(snapshot));
      deltasSinceLastSnapshot = 0;
    }
  }

  @Override
  public ListenableFuture<Void> persist(final HashedVersion version) {
    Preconditions.checkArgument(version.getVersion() > 0,
        "Cannot persist non-positive version %s", version);
    Preconditions.checkArgument(isDeltaBoundary(version),
        "Version to persist %s matches no delta", version);
    synchronized (persistLock) {
      if (latestVersionToPersist != null) {
        // There's a persist task in flight.
        if (version.getVersion() <= latestVersionToPersist.getVersion()) {
          LOG.info("Attempt to persist version " + version
              + " smaller than last version requested " + latestVersionToPersist);
        } else {
          latestVersionToPersist = version;
        }
        if (nextPersistTask == null) {
          nextPersistTask = ListenableFutureTask.<Void>create(persisterTask);
        }
        return nextPersistTask;
      } else {
        latestVersionToPersist = version;
        ListenableFutureTask<Void> resultTask = ListenableFutureTask.<Void>create(persisterTask);
        persistExecutor.execute(resultTask);
        return resultTask;
      }
    }
  }

  @Override
  public void flush(HashedVersion version) {
    cachedDeltas.remove(cachedDeltas.lowerKey(version));
    if (LOG.isFineLoggable()) {
      LOG.fine("Flushed deltas up to version " + version);
    }
  }

  @Override
  public void close() {
  }

  /**
   * @return An entry keyed by a hashed version with the given version number,
   *         if any, otherwise null.
   */
  private WaveletDeltaRecord lookup(long version) throws IOException {
    return deltasAccess.getDelta(version);
  }

  private WaveletDeltaRecord getDeltaRecordByEndVersion(HashedVersion endVersion) {
    long version = endVersion.getVersion();
    try {
      return deltasAccess.getDeltaByEndVersion(version);
    } catch (IOException e) {
      throw new RuntimeIOException(new IOException(format("Version : %d", version), e));
    }
  }

  private boolean isDeltaBoundary(HashedVersion version) {
    Preconditions.checkNotNull(version, "version is null");
    return version.equals(getCurrentVersion()) || cachedDeltas.containsKey(version);
  }
}
