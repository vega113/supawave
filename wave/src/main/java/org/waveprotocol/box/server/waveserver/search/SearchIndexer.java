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

package org.waveprotocol.box.server.waveserver.search;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory inverted index tracking which users+queries care about which waves.
 *
 * <p>This allows {@link SearchWaveletUpdater} to quickly find all subscriptions
 * affected by a wave change in O(1) for known waves, with a bounded re-eval
 * path for unknown (new) waves.
 *
 * <p>The index is purely in-memory and rebuilt on server restart. Register and
 * unregister calls must be wired to actual subscription open/close events to
 * keep the index in sync.
 */
@Singleton
public class SearchIndexer {

  private static final Log LOG = Log.get(SearchIndexer.class);

  /** Expected number of waves per subscription for Bloom filter sizing. */
  private static final int EXPECTED_WAVES_PER_SUBSCRIPTION = 500;

  /** Bloom filter false positive probability. */
  private static final double BLOOM_FPP = 0.01;

  /**
   * Identifies a unique subscription: one user + one query hash.
   */
  public static final class SubscriptionKey {
    private final ParticipantId user;
    private final String queryHash;

    public SubscriptionKey(ParticipantId user, String queryHash) {
      this.user = user;
      this.queryHash = queryHash;
    }

    public ParticipantId getUser() {
      return user;
    }

    public String getQueryHash() {
      return queryHash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SubscriptionKey)) return false;
      SubscriptionKey that = (SubscriptionKey) o;
      return user.equals(that.user) && queryHash.equals(that.queryHash);
    }

    @Override
    public int hashCode() {
      return Objects.hash(user, queryHash);
    }

    @Override
    public String toString() {
      return user.getAddress() + "|" + queryHash;
    }
  }

  /** Forward index: which subscriptions care about a given wave? */
  private final ConcurrentHashMap<WaveId, Set<SubscriptionKey>> waveToSubscriptions =
      new ConcurrentHashMap<>();

  /** Reverse index: which waves does a subscription cover? */
  private final ConcurrentHashMap<SubscriptionKey, Set<WaveId>> subscriptionToWaves =
      new ConcurrentHashMap<>();

  /** Canonical raw query stored alongside hash for lifecycle management. */
  private final ConcurrentHashMap<SubscriptionKey, String> subscriptionRawQueries =
      new ConcurrentHashMap<>();

  /** Bloom filter per subscription for fast negative rejection on unknown waves. */
  private final ConcurrentHashMap<SubscriptionKey, BloomFilter<CharSequence>> subscriptionBloomFilters =
      new ConcurrentHashMap<>();

  @Inject
  public SearchIndexer() {
  }

  /**
   * Registers a subscription with its initial set of matching wave IDs.
   * Must be wired to actual subscription open events.
   *
   * @param user the subscribing user
   * @param query the raw query string
   * @param queryHash the MD5 hex hash of the query
   * @param waveIds the set of wave IDs that currently match this query
   */
  public void registerSubscription(ParticipantId user, String query, String queryHash,
      Set<WaveId> waveIds) {
    SubscriptionKey key = new SubscriptionKey(user, queryHash);
    subscriptionRawQueries.put(key, query);

    // Build reverse index
    Set<WaveId> waveSet = ConcurrentHashMap.newKeySet();
    waveSet.addAll(waveIds);
    subscriptionToWaves.put(key, waveSet);

    // Build forward index
    for (WaveId waveId : waveIds) {
      waveToSubscriptions
          .computeIfAbsent(waveId, k -> ConcurrentHashMap.newKeySet())
          .add(key);
    }

    // Build Bloom filter
    rebuildBloomFilter(key, waveIds);

    LOG.info("Registered subscription " + key + " covering " + waveIds.size() + " waves");
  }

  /**
   * Unregisters a subscription. Must be wired to actual subscription close events.
   *
   * @param user the user
   * @param queryHash the MD5 hex hash of the query
   */
  public void unregisterSubscription(ParticipantId user, String queryHash) {
    SubscriptionKey key = new SubscriptionKey(user, queryHash);
    subscriptionRawQueries.remove(key);

    Set<WaveId> waves = subscriptionToWaves.remove(key);
    if (waves != null) {
      for (WaveId waveId : waves) {
        Set<SubscriptionKey> subs = waveToSubscriptions.get(waveId);
        if (subs != null) {
          subs.remove(key);
          if (subs.isEmpty()) {
            waveToSubscriptions.remove(waveId, subs);
          }
        }
      }
    }
    subscriptionBloomFilters.remove(key);
    LOG.info("Unregistered subscription " + key);
  }

  /**
   * Finds all subscriptions potentially affected by a change to the given wave.
   *
   * <p>For known waves (in the forward index), this is O(1). For unknown waves
   * (not in the forward index), returns all subscriptions for users who are
   * participants of the wave. The caller ({@link SearchWaveletUpdater}) is
   * responsible for bounded re-evaluation of those subscriptions.
   *
   * @param waveId the wave that changed
   * @param waveParticipants the participants of the changed wave (used for
   *        unknown-wave lookup)
   * @return the set of subscription keys that may need updating
   */
  public Set<SubscriptionKey> getAffectedSubscriptions(WaveId waveId,
      Set<ParticipantId> waveParticipants) {
    // Fast path: known wave in forward index
    Set<SubscriptionKey> direct = waveToSubscriptions.get(waveId);
    if (direct != null && !direct.isEmpty()) {
      return Collections.unmodifiableSet(direct);
    }

    // Slow path: unknown wave -- find subscriptions for affected users.
    // Do NOT rely solely on Bloom filter. Instead, return all subscriptions
    // for users who are participants, so the caller can do a bounded re-eval
    // via SearchProvider.search().
    Set<SubscriptionKey> affected = ConcurrentHashMap.newKeySet();
    for (ParticipantId participant : waveParticipants) {
      for (SubscriptionKey key : subscriptionToWaves.keySet()) {
        if (key.getUser().equals(participant)) {
          // Use Bloom filter as a pre-filter hint: if the Bloom filter says
          // "definitely not present", we still include it for re-eval because
          // this is a new wave the filter hasn't seen.
          affected.add(key);
        }
      }
    }
    return affected;
  }

  /**
   * Updates the wave set for an existing subscription (e.g., after re-search).
   *
   * @param user the user
   * @param queryHash the MD5 hex hash of the query
   * @param newWaveIds the updated set of wave IDs
   */
  public void updateSubscriptionWaves(ParticipantId user, String queryHash,
      Set<WaveId> newWaveIds) {
    SubscriptionKey key = new SubscriptionKey(user, queryHash);

    // Remove old forward index entries
    Set<WaveId> oldWaves = subscriptionToWaves.get(key);
    if (oldWaves != null) {
      for (WaveId oldWaveId : oldWaves) {
        Set<SubscriptionKey> subs = waveToSubscriptions.get(oldWaveId);
        if (subs != null) {
          subs.remove(key);
          if (subs.isEmpty()) {
            waveToSubscriptions.remove(oldWaveId, subs);
          }
        }
      }
    }

    // Install new reverse + forward index entries
    Set<WaveId> waveSet = ConcurrentHashMap.newKeySet();
    waveSet.addAll(newWaveIds);
    subscriptionToWaves.put(key, waveSet);

    for (WaveId waveId : newWaveIds) {
      waveToSubscriptions
          .computeIfAbsent(waveId, k -> ConcurrentHashMap.newKeySet())
          .add(key);
    }

    // Rebuild Bloom filter
    rebuildBloomFilter(key, newWaveIds);
  }

  /**
   * Returns the raw query for a subscription, or null if not found.
   */
  public String getRawQuery(SubscriptionKey key) {
    return subscriptionRawQueries.get(key);
  }

  /**
   * Returns the number of active subscriptions.
   */
  public int getSubscriptionCount() {
    return subscriptionToWaves.size();
  }

  /**
   * Returns the number of indexed waves (waves with at least one subscription).
   */
  public int getIndexedWaveCount() {
    return waveToSubscriptions.size();
  }

  private void rebuildBloomFilter(SubscriptionKey key, Set<WaveId> waveIds) {
    int expectedSize = Math.max(waveIds.size(), EXPECTED_WAVES_PER_SUBSCRIPTION);
    BloomFilter<CharSequence> filter = BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8), expectedSize, BLOOM_FPP);
    for (WaveId waveId : waveIds) {
      filter.put(waveId.serialise());
    }
    subscriptionBloomFilters.put(key, filter);
  }
}
