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
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

@Singleton
public class SearchIndexer {

  private static final Log LOG = Log.get(SearchIndexer.class);
  private static final int EXPECTED_WAVES_PER_SUBSCRIPTION = 500;
  private static final double BLOOM_FPP = 0.01;

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
      if (this == o) {
        return true;
      }
      if (!(o instanceof SubscriptionKey)) {
        return false;
      }
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

  private final ConcurrentHashMap<WaveId, Set<SubscriptionKey>> waveToSubscriptions =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<SubscriptionKey, Set<WaveId>> subscriptionToWaves =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<SubscriptionKey, String> subscriptionRawQueries =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ParticipantId, Set<SubscriptionKey>> userToSubscriptions =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<SubscriptionKey, BloomFilter<CharSequence>>
      subscriptionBloomFilters = new ConcurrentHashMap<>();

  @Inject
  public SearchIndexer() {
  }

  public void registerSubscription(
      ParticipantId user, String query, String queryHash, Set<WaveId> waveIds) {
    SubscriptionKey key = new SubscriptionKey(user, queryHash);
    subscriptionRawQueries.put(key, query);
    Set<WaveId> waveSet = ConcurrentHashMap.newKeySet();
    waveSet.addAll(waveIds);
    userToSubscriptions.computeIfAbsent(user, ignored -> ConcurrentHashMap.newKeySet()).add(key);
    for (WaveId waveId : waveIds) {
      waveToSubscriptions.computeIfAbsent(waveId, ignored -> ConcurrentHashMap.newKeySet()).add(key);
    }
    subscriptionToWaves.put(key, waveSet);
    rebuildBloomFilter(key, waveIds);
    LOG.info("Registered subscription " + key + " covering " + waveIds.size() + " waves");
  }

  public void registerOrUpdateSubscription(
      ParticipantId user, String query, String queryHash, Set<WaveId> waveIds) {
    SubscriptionKey key = new SubscriptionKey(user, queryHash);
    String previousQuery = subscriptionRawQueries.put(key, query);
    if (previousQuery == null) {
      registerSubscription(user, query, queryHash, waveIds);
    } else {
      updateSubscriptionWaves(user, queryHash, waveIds);
    }
  }

  public void unregisterSubscription(ParticipantId user, String queryHash) {
    SubscriptionKey key = new SubscriptionKey(user, queryHash);
    subscriptionRawQueries.remove(key);
    Set<SubscriptionKey> userSubscriptions = userToSubscriptions.get(user);
    if (userSubscriptions != null) {
      userSubscriptions.remove(key);
      if (userSubscriptions.isEmpty()) {
        userToSubscriptions.remove(user, userSubscriptions);
      }
    }
    Set<WaveId> waves = subscriptionToWaves.get(key);
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
      subscriptionToWaves.remove(key, waves);
    }
    subscriptionBloomFilters.remove(key);
    LOG.info("Unregistered subscription " + key);
  }

  public Set<SubscriptionKey> getAffectedSubscriptions(
      WaveId waveId, Set<ParticipantId> waveParticipants) {
    Set<SubscriptionKey> affected = ConcurrentHashMap.newKeySet();
    Set<SubscriptionKey> direct = waveToSubscriptions.get(waveId);
    if (direct != null && !direct.isEmpty()) {
      affected.addAll(direct);
    }
    for (ParticipantId participant : waveParticipants) {
      Set<SubscriptionKey> participantSubscriptions = userToSubscriptions.get(participant);
      if (participantSubscriptions != null && !participantSubscriptions.isEmpty()) {
        affected.addAll(participantSubscriptions);
      }
    }
    return affected;
  }

  public void updateSubscriptionWaves(ParticipantId user, String queryHash, Set<WaveId> newWaveIds) {
    SubscriptionKey key = new SubscriptionKey(user, queryHash);
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
    Set<WaveId> waveSet = ConcurrentHashMap.newKeySet();
    waveSet.addAll(newWaveIds);
    userToSubscriptions.computeIfAbsent(user, ignored -> ConcurrentHashMap.newKeySet()).add(key);
    for (WaveId waveId : newWaveIds) {
      waveToSubscriptions.computeIfAbsent(waveId, ignored -> ConcurrentHashMap.newKeySet()).add(key);
    }
    subscriptionToWaves.put(key, waveSet);
    rebuildBloomFilter(key, newWaveIds);
  }

  public String getRawQuery(SubscriptionKey key) {
    return subscriptionRawQueries.get(key);
  }

  public int getSubscriptionCount() {
    return subscriptionToWaves.size();
  }

  public int getIndexedWaveCount() {
    return waveToSubscriptions.size();
  }

  private void rebuildBloomFilter(SubscriptionKey key, Set<WaveId> waveIds) {
    int expectedSize = Math.max(waveIds.size(), EXPECTED_WAVES_PER_SUBSCRIPTION);
    BloomFilter<CharSequence> filter =
        BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8), expectedSize, BLOOM_FPP);
    for (WaveId waveId : waveIds) {
      filter.put(waveId.serialise());
    }
    subscriptionBloomFilters.put(key, filter);
  }
}
