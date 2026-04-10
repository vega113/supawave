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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author yurize@apache.org (Yuri Zelikov)
 */
@Singleton
public class MemoryPerUserWaveViewHandlerImpl
    implements PerUserWaveViewHandler, WaveBus.Subscriber {

  private static final Log LOG = Log.get(MemoryPerUserWaveViewHandlerImpl.class);
  private static final String SEARCH_WAVELET_PREFIX = "search+";

  /**
   * The period of time in minutes the per user waves view should be actively
   * kept up to date after last access.
   *
   * The initial cache load is expensive (full wavelet scan), but incremental
   * updates via {@link #onParticipantAdded}/{@link #onParticipantRemoved}
   * keep the view current between loads. A longer expiry avoids redundant
   * full-scan rebuilds while the event-driven updates maintain correctness.
   */
  private static final int PER_USER_WAVES_VIEW_CACHE_MINUTES = 60;

  /** The loading cache that holds wave viev per each online user.*/
  public LoadingCache<ParticipantId, Multimap<WaveId, WaveletId>> explicitPerUserWaveViews;

  /** Guards concurrent calls to {@code loadAllWavelets()} during cache rebuilds. */
  private final Object waveMapLoadLock = new Object();

  /**
   * Minimum interval (ms) between consecutive full wave-map reloads.
   * Prevents back-to-back scans when many users' caches miss around the same time.
   */
  private static final long WAVE_MAP_RELOAD_COOLDOWN_MS = 30_000;

  /**
   * Timestamp of the last successful {@code loadAllWavelets()} call.
   * Guarded by {@link #waveMapLoadLock}.
   */
  private long lastWaveMapLoadMs = 0;
  /**
   * Timestamp of the last non-search wavelet mutation observed on the wave bus.
   * Guarded by {@link #waveMapLoadLock}.
   */
  private long lastWaveMutationMs = 0;

  @Inject
  public MemoryPerUserWaveViewHandlerImpl(final WaveMap waveMap) {
    // Let the view expire if it not accessed for some time.
    explicitPerUserWaveViews =
        CacheBuilder.newBuilder().expireAfterAccess(PER_USER_WAVES_VIEW_CACHE_MINUTES,
            TimeUnit.MINUTES).<ParticipantId, Multimap<WaveId, WaveletId>>build(
                new CacheLoader<ParticipantId, Multimap<WaveId, WaveletId>>() {

          @Override
          public Multimap<WaveId, WaveletId> load(final ParticipantId user) {
            long startMs = System.currentTimeMillis();
            Multimap<WaveId, WaveletId> userView = HashMultimap.create();
            ensureWaveMapLoaded(waveMap, user);

            Map<WaveId, Wave> waves = waveMap.getWaves();
            for (Map.Entry<WaveId, Wave> entry : waves.entrySet()) {
              WaveId waveId = entry.getKey();
              Wave wave = entry.getValue();

              // Collect wavelet IDs from both persisted storage and in-memory
              // containers.  lookupWavelets() returns IDs from the DB snapshot
              // taken when the Wave was first loaded — this covers wavelets
              // that exist after a server restart.  wave.iterator() returns
              // containers that were created during the current session (e.g.
              // via submitRequest) but may not yet be in the stored lookup.
              java.util.Set<WaveletId> waveletIds = new java.util.HashSet<>();
              try {
                waveletIds.addAll(waveMap.lookupWavelets(waveId));
              } catch (WaveletStateException e) {
                LOG.warning("Failed to look up wavelets for wave " + waveId, e);
              }
              for (WaveletContainer wc : wave) {
                waveletIds.add(wc.getWaveletName().waveletId);
              }

              for (WaveletId waveletId : waveletIds) {
                WaveletName waveletName = WaveletName.of(waveId, waveletId);
                try {
                  WaveletContainer c = waveMap.getWavelet(waveletName);
                  if (c != null && c.hasParticipant(user)) {
                    userView.put(waveId, waveletId);
                  }
                } catch (WaveletStateException e) {
                  LOG.warning("Failed to access wavelet " + waveletName, e);
                }
              }
            }
            long elapsedMs = System.currentTimeMillis() - startMs;
            LOG.info("Initialized waves view for user: " + user.getAddress()
                + ", number of waves in view: " + userView.size()
                + ", took " + elapsedMs + " ms");
            return userView;
          }
        });
  }

  /**
   * Ensures all waves are loaded in the WaveMap before iterating.
   *
   * <p>The WaveMap's internal cache evicts entries after a period of inactivity
   * ({@code core.wave_cache_expire}). When the per-user view cache also expires
   * and needs to be rebuilt, the WaveMap may have evicted most of its entries.
   * {@code getWaves()} only returns what is currently cached, so we must
   * reload from storage to get a complete view.
   *
   * <p>A {@link #WAVE_MAP_RELOAD_COOLDOWN_MS} cooldown prevents redundant
   * back-to-back scans: threads that acquire the lock within the cooldown window
   * after a completed scan skip the reload and use the already-warm WaveMap.
   */
  private void ensureWaveMapLoaded(WaveMap waveMap, ParticipantId user) {
    synchronized (waveMapLoadLock) {
      long now = System.currentTimeMillis();
      boolean withinCooldown = now - lastWaveMapLoadMs < WAVE_MAP_RELOAD_COOLDOWN_MS;
      boolean sawMutationSinceLastLoad = lastWaveMutationMs > lastWaveMapLoadMs;
      if (withinCooldown && !sawMutationSinceLastLoad) {
        return;
      }
      try {
        waveMap.loadAllWavelets();
        lastWaveMapLoadMs = System.currentTimeMillis();
      } catch (WaveletStateException e) {
        throw new RuntimeException("Failed to load waves for " + user.getAddress(), e);
      }
    }
  }

  @Override
  public ListenableFuture<Void> onParticipantAdded(WaveletName waveletName, ParticipantId user) {
    markWaveMapDirty(waveletName);
    Multimap<WaveId, WaveletId> perUserView = explicitPerUserWaveViews.getIfPresent(user);
    if (perUserView != null) {
      if (!perUserView.containsEntry(waveletName.waveId, waveletName.waveletId)) {
        perUserView.put(waveletName.waveId, waveletName.waveletId);
        if(LOG.isFineLoggable()) {
          LOG.fine("Added wavelet: " + waveletName + " to the view of user: " + user.getAddress());
          LOG.fine("View size is now: " + perUserView.size());
        }
      }
    }
    SettableFuture<Void> task = SettableFuture.create();
    task.set(null);
    return task;
  }

  @Override
  public ListenableFuture<Void> onParticipantRemoved(WaveletName waveletName, ParticipantId user) {
    markWaveMapDirty(waveletName);
    Multimap<WaveId, WaveletId> perUserView = explicitPerUserWaveViews.getIfPresent(user);
    if (perUserView != null) {
      if (perUserView.containsEntry(waveletName.waveId, waveletName.waveletId)) {
        perUserView.remove(waveletName.waveId, waveletName.waveletId);
        LOG.fine("Removed wavelet: " + waveletName
            + " from the view of user: " + user.getAddress());
      }
    }
    SettableFuture<Void> task = SettableFuture.create();
    task.set(null);
    return task;
  }

  @Override
  public Multimap<WaveId, WaveletId> retrievePerUserWaveView(ParticipantId user) {
    try {
      return explicitPerUserWaveViews.get(user);
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public ListenableFuture<Void> onWaveInit(WaveletName waveletName) {
    markWaveMapDirty(waveletName);
    SettableFuture<Void> task = SettableFuture.create();
    task.set(null);
    return task;
  }

  @Override
  public void waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas) {
    if (wavelet != null) {
      markWaveMapDirty(WaveletName.of(wavelet.getWaveId(), wavelet.getWaveletId()));
    }
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    markWaveMapDirty(waveletName);
  }

  private void markWaveMapDirty(WaveletName waveletName) {
    if (waveletName != null && isSearchWavelet(waveletName)) {
      return;
    }
    synchronized (waveMapLoadLock) {
      lastWaveMutationMs = System.currentTimeMillis();
    }
  }

  private boolean isSearchWavelet(WaveletName waveletName) {
    return waveletName.waveletId.getId().startsWith(SEARCH_WAVELET_PREFIX);
  }
}
