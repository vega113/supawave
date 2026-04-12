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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import junit.framework.TestCase;

import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.federation.Proto.ProtocolAppliedWaveletDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolSignedDelta;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.security.AccessControlException;
import java.util.Map;

public class MemoryPerUserWaveViewHandlerImplTest extends TestCase {

  private static final String DOMAIN = "example.com";
  private static final WaveId WAVE_ID = WaveId.of(DOMAIN, "abc123");
  private static final WaveletId WAVELET_ID = WaveletId.of(DOMAIN, "conv+root");
  private static final WaveletName WAVELET_NAME = WaveletName.of(WAVE_ID, WAVELET_ID);
  private static final ParticipantId PARTICIPANT = ParticipantId.ofUnsafe("user1@" + DOMAIN);
  private static final ParticipantId SECOND_PARTICIPANT = ParticipantId.ofUnsafe("user2@" + DOMAIN);
  private static final Config CONFIG = ConfigFactory.parseMap(ImmutableMap.<String, Object>of(
      "core.wave_cache_size", 1000,
      "core.wave_cache_expire", "60m"));
  private static final WaveletNotificationSubscriber NO_OP_NOTIFIEE =
      new WaveletNotificationSubscriber() {
        @Override
        public void waveletUpdate(ReadableWaveletData wavelet,
            ImmutableList<WaveletDeltaRecord> deltas, ImmutableSet<String> domainsToNotify) {
        }

        @Override
        public void waveletCommitted(WaveletName waveletName, HashedVersion version,
            ImmutableSet<String> domainsToNotify) {
        }
      };

  private WaveMap waveMap;

  @Override
  protected void setUp() throws Exception {
    LocalWaveletContainer.Factory localFactory = new LocalWaveletContainer.Factory() {
      @Override
      public LocalWaveletContainer create(WaveletNotificationSubscriber notifiee,
          WaveletName waveletName, String domain) {
        return new FakeLocalWavelet(waveletName);
      }
    };
    RemoteWaveletContainer.Factory remoteFactory = new RemoteWaveletContainer.Factory() {
      @Override
      public RemoteWaveletContainer create(WaveletNotificationSubscriber notifiee,
          WaveletName waveletName, String domain) {
        throw new UnsupportedOperationException();
      }
    };

    SettableFuture<ImmutableSet<WaveletId>> lookedupWavelets = SettableFuture.create();
    lookedupWavelets.set(ImmutableSet.of(WAVELET_ID));

    Wave wave = new Wave(WAVE_ID, lookedupWavelets, NO_OP_NOTIFIEE, localFactory, remoteFactory,
        DOMAIN);
    wave.getOrCreateLocalWavelet(WAVELET_ID);

    waveMap = new ReloadingWaveMap(
        new DeltaStoreBasedSnapshotStore(new org.waveprotocol.box.server.persistence.memory.MemoryDeltaStore(), null),
        localFactory, remoteFactory, ImmutableMap.of(WAVE_ID, wave));
  }

  public void testRetrievePerUserWaveViewReloadsColdWaveMap() {
    MemoryPerUserWaveViewHandlerImpl handler = new MemoryPerUserWaveViewHandlerImpl(waveMap);

    Multimap<WaveId, WaveletId> waveView = handler.retrievePerUserWaveView(PARTICIPANT);

    assertEquals(1, waveView.size());
    assertTrue(waveView.containsEntry(WAVE_ID, WAVELET_ID));
  }

  public void testRapidCacheMissesShareSingleLoad() {
    ReloadingWaveMap reloadingWaveMap = (ReloadingWaveMap) waveMap;
    MemoryPerUserWaveViewHandlerImpl handler = new MemoryPerUserWaveViewHandlerImpl(reloadingWaveMap);

    handler.retrievePerUserWaveView(PARTICIPANT);
    handler.retrievePerUserWaveView(SECOND_PARTICIPANT);

    assertEquals(1, reloadingWaveMap.getLoadAllWaveletsCallCount());
  }

  public void testRetrievePerUserWaveViewCacheHitSkipsReload() {
    ReloadingWaveMap reloadingWaveMap = (ReloadingWaveMap) waveMap;
    MemoryPerUserWaveViewHandlerImpl handler = new MemoryPerUserWaveViewHandlerImpl(reloadingWaveMap);

    handler.retrievePerUserWaveView(PARTICIPANT);
    handler.retrievePerUserWaveView(PARTICIPANT);

    assertEquals(1, reloadingWaveMap.getLoadAllWaveletsCallCount());
  }

  public void testWaveletCommittedSkipsInvalidateWhenCommittedVersionCheckRequiresWriteLock()
      throws Exception {
    FakeLocalWavelet cachedWavelet =
        new FakeLocalWavelet(
            WAVELET_NAME,
            PARTICIPANT,
            null,
            new IllegalStateException("should not hold write lock"),
            true);
    ReloadingWaveMap reloadingWaveMap =
        new ReloadingWaveMap(
            new DeltaStoreBasedSnapshotStore(
                new org.waveprotocol.box.server.persistence.memory.MemoryDeltaStore(), null),
            new LocalWaveletContainer.Factory() {
              @Override
              public LocalWaveletContainer create(
                  WaveletNotificationSubscriber notifiee,
                  WaveletName waveletName,
                  String domain) {
                throw new UnsupportedOperationException();
              }
            },
            new RemoteWaveletContainer.Factory() {
              @Override
              public RemoteWaveletContainer create(
                  WaveletNotificationSubscriber notifiee,
                  WaveletName waveletName,
                String domain) {
                throw new UnsupportedOperationException();
              }
            },
            createLoadedWaves(cachedWavelet));
    MemoryPerUserWaveViewHandlerImpl handler =
        new MemoryPerUserWaveViewHandlerImpl(reloadingWaveMap);

    handler.retrievePerUserWaveView(PARTICIPANT);
    assertEquals(1, reloadingWaveMap.getLoadAllWaveletsCallCount());

    handler.waveletCommitted(WAVELET_NAME, HashedVersion.unsigned(2L));

    assertEquals(0, cachedWavelet.getLastCommittedVersionCallCount());
    assertEquals(0, reloadingWaveMap.getInvalidateWaveCallCount());
  }

  public void testWaveletCommittedMarksWaveMapDirtyWhenInvalidateThrowsRuntimeException()
      throws Exception {
    ExplodingInvalidateWaveMap reloadingWaveMap =
        new ExplodingInvalidateWaveMap(createLoadedWaves(new FakeLocalWavelet(
            WAVELET_NAME, PARTICIPANT, HashedVersion.unsigned(1L))));
    MemoryPerUserWaveViewHandlerImpl handler =
        new MemoryPerUserWaveViewHandlerImpl(reloadingWaveMap);

    handler.retrievePerUserWaveView(PARTICIPANT);
    assertEquals(1, reloadingWaveMap.getLoadAllWaveletsCallCount());

    handler.explicitPerUserWaveViews.invalidate(PARTICIPANT);

    handler.waveletCommitted(WAVELET_NAME, HashedVersion.unsigned(2L));
    assertEquals(1, reloadingWaveMap.getInvalidateWaveCallCount());

    handler.retrievePerUserWaveView(PARTICIPANT);
    assertEquals(2, reloadingWaveMap.getLoadAllWaveletsCallCount());
  }

  private static ImmutableMap<WaveId, Wave> createLoadedWaves(LocalWaveletContainer container)
      throws Exception {
    SettableFuture<ImmutableSet<WaveletId>> lookedupWavelets = SettableFuture.create();
    lookedupWavelets.set(ImmutableSet.of(WAVELET_ID));

    LocalWaveletContainer.Factory localFactory = new LocalWaveletContainer.Factory() {
      @Override
      public LocalWaveletContainer create(WaveletNotificationSubscriber notifiee,
          WaveletName waveletName, String domain) {
        return container;
      }
    };
    RemoteWaveletContainer.Factory remoteFactory = new RemoteWaveletContainer.Factory() {
      @Override
      public RemoteWaveletContainer create(WaveletNotificationSubscriber notifiee,
          WaveletName waveletName, String domain) {
        throw new UnsupportedOperationException();
      }
    };

    Wave wave = new Wave(
        WAVE_ID, lookedupWavelets, NO_OP_NOTIFIEE, localFactory, remoteFactory, DOMAIN);
    wave.getOrCreateLocalWavelet(WAVELET_ID);
    return ImmutableMap.of(WAVE_ID, wave);
  }

  private static class ReloadingWaveMap extends WaveMap {
    private final ImmutableMap<WaveId, Wave> loadedWaves;
    private boolean loaded;
    private int loadAllWaveletsCallCount;
    private int invalidateWaveCallCount;

    private ReloadingWaveMap(DeltaAndSnapshotStore store,
        LocalWaveletContainer.Factory localFactory, RemoteWaveletContainer.Factory remoteFactory,
        ImmutableMap<WaveId, Wave> loadedWaves) {
      super(store, NO_OP_NOTIFIEE, localFactory, remoteFactory, DOMAIN, CONFIG,
          MoreExecutors.directExecutor());
      this.loadedWaves = loadedWaves;
    }

    @Override
    public void loadAllWavelets() {
      loaded = true;
      loadAllWaveletsCallCount++;
    }

    int getLoadAllWaveletsCallCount() {
      return loadAllWaveletsCallCount;
    }

    int getInvalidateWaveCallCount() {
      return invalidateWaveCallCount;
    }

    @Override
    Map<WaveId, Wave> getWaves() {
      return loaded ? loadedWaves : ImmutableMap.of();
    }

    protected void recordInvalidateWaveCall() {
      invalidateWaveCallCount++;
    }

    @Override
    public void invalidateWave(WaveId waveId) {
      recordInvalidateWaveCall();
      super.invalidateWave(waveId);
    }

    @Override
    public WaveletContainer getCachedWavelet(WaveletName waveletName) {
      if (!loaded) {
        return null;
      }
      Wave wave = loadedWaves.get(waveletName.waveId);
      if (wave == null) {
        return null;
      }
      return wave.getCachedWavelet(waveletName.waveletId);
    }

    @Override
    public ImmutableSet<WaveletId> lookupWavelets(WaveId waveId) throws WaveletStateException {
      Wave wave = loadedWaves.get(waveId);
      if (wave == null) {
        return ImmutableSet.of();
      }
      try {
        return FutureUtil.getResultOrPropagateException(
            wave.getLookedupWavelets(),
            org.waveprotocol.box.server.persistence.PersistenceException.class);
      } catch (Exception e) {
        throw new WaveletStateException("Failed to look up wave " + waveId, e);
      }
    }

    @Override
    public WaveletContainer getWavelet(WaveletName waveletName) throws WaveletStateException {
      Wave wave = loadedWaves.get(waveletName.waveId);
      if (wave == null) {
        return null;
      }
      WaveletContainer c = wave.getLocalWavelet(waveletName.waveletId);
      if (c == null) {
        c = wave.getRemoteWavelet(waveletName.waveletId);
      }
      return c;
    }
  }

  private static final class ExplodingInvalidateWaveMap extends ReloadingWaveMap {
    private ExplodingInvalidateWaveMap(ImmutableMap<WaveId, Wave> loadedWaves) {
      super(
          new DeltaStoreBasedSnapshotStore(
              new org.waveprotocol.box.server.persistence.memory.MemoryDeltaStore(), null),
          new LocalWaveletContainer.Factory() {
            @Override
            public LocalWaveletContainer create(
                WaveletNotificationSubscriber notifiee,
                WaveletName waveletName,
                String domain) {
              throw new UnsupportedOperationException();
            }
          },
          new RemoteWaveletContainer.Factory() {
            @Override
            public RemoteWaveletContainer create(
                WaveletNotificationSubscriber notifiee,
                WaveletName waveletName,
                String domain) {
              throw new UnsupportedOperationException();
            }
          },
          loadedWaves);
    }

    @Override
    public void invalidateWave(WaveId waveId) {
      recordInvalidateWaveCall();
      throw new RuntimeException("boom");
    }
  }

  private static final class FakeLocalWavelet implements LocalWaveletContainer {
    private final WaveletName waveletName;
    private final ParticipantId participant;
    private final HashedVersion lastCommittedVersion;
    private final RuntimeException lastCommittedVersionException;
    private final boolean writeLockHeldByCurrentThread;
    private int lastCommittedVersionCallCount;

    private FakeLocalWavelet(WaveletName waveletName) {
      this(waveletName, PARTICIPANT, null, null, false);
    }

    private FakeLocalWavelet(
        WaveletName waveletName, ParticipantId participant, HashedVersion lastCommittedVersion) {
      this(waveletName, participant, lastCommittedVersion, null, false);
    }

    private FakeLocalWavelet(
        WaveletName waveletName,
        ParticipantId participant,
        HashedVersion lastCommittedVersion,
        RuntimeException lastCommittedVersionException) {
      this(
          waveletName,
          participant,
          lastCommittedVersion,
          lastCommittedVersionException,
          false);
    }

    private FakeLocalWavelet(
        WaveletName waveletName,
        ParticipantId participant,
        HashedVersion lastCommittedVersion,
        RuntimeException lastCommittedVersionException,
        boolean writeLockHeldByCurrentThread) {
      this.waveletName = waveletName;
      this.participant = participant;
      this.lastCommittedVersion = lastCommittedVersion;
      this.lastCommittedVersionException = lastCommittedVersionException;
      this.writeLockHeldByCurrentThread = writeLockHeldByCurrentThread;
    }

    @Override
    public WaveletName getWaveletName() {
      return waveletName;
    }

    @Override
    public ObservableWaveletData copyWaveletData() {
      throw new UnsupportedOperationException();
    }

    @Override
    public CommittedWaveletSnapshot getSnapshot() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T applyFunction(com.google.common.base.Function<ReadableWaveletData, T> function) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void requestHistory(HashedVersion versionStart, HashedVersion versionEnd,
        Receiver<ByteStringMessage<ProtocolAppliedWaveletDelta>> receiver) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void requestTransformedHistory(HashedVersion versionStart, HashedVersion versionEnd,
        Receiver<TransformedWaveletDelta> receiver) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean checkAccessPermission(ParticipantId participantId) {
      return true;
    }

    @Override
    public HashedVersion getLastCommittedVersion() {
      lastCommittedVersionCallCount++;
      if (lastCommittedVersionException != null) {
        throw lastCommittedVersionException;
      }
      return lastCommittedVersion;
    }

    @Override
    public boolean isWriteLockedByCurrentThread() {
      return writeLockHeldByCurrentThread;
    }

    @Override
    public boolean hasParticipant(ParticipantId participant) {
      return this.participant.equals(participant);
    }

    @Override
    public ParticipantId getCreator() {
      return PARTICIPANT;
    }

    @Override
    public ParticipantId getSharedDomainParticipant() {
      return null;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public WaveletDeltaRecord submitRequest(WaveletName waveletName, ProtocolSignedDelta delta)
        throws OperationException, InvalidProtocolBufferException, InvalidHashException,
        PersistenceException, WaveletStateException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDeltaSigner(HashedVersion hashedVersion, ByteString signerId) {
      return false;
    }

    @Override
    public HashedVersion getHashedVersion(long version) {
      throw new UnsupportedOperationException();
    }

    int getLastCommittedVersionCallCount() {
      return lastCommittedVersionCallCount;
    }
  }
}
