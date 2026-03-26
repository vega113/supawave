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

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.wave.api.SearchResult;

import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.waveserver.SearchProvider;
import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Listens for wave changes on the {@link WaveBus} and pushes updates to
 * affected search wavelets.
 *
 * <p>This subscriber is registered <strong>after</strong>
 * {@code PerUserWaveViewDistpatcher} in {@code ServerMain.initializeSearch()}
 * so that the per-user wave view index is current before search wavelet
 * updates are computed.
 *
 * <p>Batching: updates are debounced with a 100ms delay and a 500ms max-wait
 * ceiling per user+query. A per-user token bucket (10 updates/sec, queue
 * capacity 100) provides backpressure.
 *
 * <p>Follows the same WaveBus.Subscriber pattern as {@code ContactsRecorder}.
 */
@Singleton
public class SearchWaveletUpdater implements WaveBus.Subscriber {

  private static final Log LOG = Log.get(SearchWaveletUpdater.class);

  /** Debounce delay for batching search wavelet updates. */
  private static final long DEBOUNCE_MS = 100;

  /** Maximum wait time before a batched update must fire. */
  private static final long MAX_WAIT_MS = 500;

  /** Maximum search wavelet updates per second per user. */
  private static final int MAX_UPDATES_PER_SEC = 10;

  /** Maximum queued updates per user before oldest are dropped. */
  private static final int MAX_QUEUE_PER_USER = 100;

  /** Maximum number of search results to fetch per query. */
  private static final int MAX_SEARCH_RESULTS = 50;

  private final SearchWaveletManager waveletManager;
  private final SearchIndexer indexer;
  private final SearchProvider searchProvider;
  private final SearchWaveletDataProvider dataProvider;

  /** Scheduled executor for debounced update tasks. */
  private final ScheduledExecutorService scheduler;

  /** Pending debounced tasks keyed by "user|queryHash". */
  private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingTasks =
      new ConcurrentHashMap<>();

  /** First-seen timestamp for max-wait tracking, keyed by "user|queryHash". */
  private final ConcurrentHashMap<String, Long> firstSeenTimestamps =
      new ConcurrentHashMap<>();

  /** Per-user update counter for token bucket, keyed by user address. */
  private final ConcurrentHashMap<String, UpdateCounter> userCounters =
      new ConcurrentHashMap<>();

  @Inject
  public SearchWaveletUpdater(
      SearchWaveletManager waveletManager,
      SearchIndexer indexer,
      SearchProvider searchProvider,
      SearchWaveletDataProvider dataProvider) {
    this.waveletManager = waveletManager;
    this.indexer = indexer;
    this.searchProvider = searchProvider;
    this.dataProvider = dataProvider;
    this.scheduler = Executors.newScheduledThreadPool(2, r -> {
      Thread t = new Thread(r, "SearchWaveletUpdater-scheduler");
      t.setDaemon(true);
      return t;
    });
  }

  @Override
  public void waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas) {
    WaveletName waveletName = WaveletName.of(wavelet.getWaveId(), wavelet.getWaveletId());

    // Guard: skip recursive updates from search wavelets themselves.
    if (waveletManager.isSearchWavelet(waveletName)) {
      return;
    }

    WaveId waveId = wavelet.getWaveId();

    // Gather participants from the wavelet snapshot.
    Set<ParticipantId> participants = Sets.newHashSet(wavelet.getParticipants());

    // Find all subscriptions that may be affected by this wave change.
    Set<SearchIndexer.SubscriptionKey> affected =
        indexer.getAffectedSubscriptions(waveId, participants);

    if (affected.isEmpty()) {
      return;
    }

    if (LOG.isFineLoggable()) {
      LOG.fine("Wave " + waveId + " change affects " + affected.size() + " subscriptions");
    }

    // Enqueue a debounced update for each affected subscription.
    for (SearchIndexer.SubscriptionKey key : affected) {
      enqueueUpdate(key);
    }
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    // No action needed on commit.
  }

  /**
   * Enqueues a debounced update for the given subscription key.
   * Implements 100ms debounce with 500ms max-wait.
   */
  private void enqueueUpdate(SearchIndexer.SubscriptionKey key) {
    String taskKey = key.toString();
    long now = System.currentTimeMillis();

    // Track first-seen time for max-wait
    firstSeenTimestamps.putIfAbsent(taskKey, now);
    long firstSeen = firstSeenTimestamps.get(taskKey);

    // Check per-user rate limit
    UpdateCounter counter = userCounters.computeIfAbsent(
        key.getUser().getAddress(), k -> new UpdateCounter(MAX_UPDATES_PER_SEC));
    if (counter.getQueueSize() >= MAX_QUEUE_PER_USER) {
      LOG.warning("Dropping search update for " + key + " -- queue full");
      return;
    }

    // Calculate delay: respect max-wait ceiling
    long elapsed = now - firstSeen;
    long delay;
    if (elapsed >= MAX_WAIT_MS) {
      // Max-wait exceeded -- fire immediately
      delay = 0;
    } else {
      delay = Math.min(DEBOUNCE_MS, MAX_WAIT_MS - elapsed);
    }

    // Cancel any existing pending task for this key
    ScheduledFuture<?> existing = pendingTasks.get(taskKey);
    if (existing != null && !existing.isDone()) {
      existing.cancel(false);
    }

    // Schedule the update
    ScheduledFuture<?> future = scheduler.schedule(
        () -> executeUpdate(key, taskKey), delay, TimeUnit.MILLISECONDS);
    pendingTasks.put(taskKey, future);
  }

  /**
   * Executes the actual search re-evaluation and diff computation for
   * a single subscription.
   */
  private void executeUpdate(SearchIndexer.SubscriptionKey key, String taskKey) {
    try {
      // Clear batch tracking state
      pendingTasks.remove(taskKey);
      firstSeenTimestamps.remove(taskKey);

      // Rate-limit per user
      UpdateCounter counter = userCounters.get(key.getUser().getAddress());
      if (counter != null && !counter.tryAcquire()) {
        // Re-enqueue with a short delay
        scheduler.schedule(() -> executeUpdate(key, taskKey), 100, TimeUnit.MILLISECONDS);
        return;
      }

      // Look up the raw query for this subscription
      String rawQuery = indexer.getRawQuery(key);
      if (rawQuery == null) {
        // Subscription was unregistered between enqueue and execute
        LOG.fine("Subscription " + key + " no longer registered, skipping update");
        return;
      }

      ParticipantId user = key.getUser();

      // Re-run the search to get current results
      SearchResult searchResult = searchProvider.search(user, rawQuery, 0, MAX_SEARCH_RESULTS);

      // Convert SearchResult digests to our SearchResultEntry list
      List<SearchWaveletDataProvider.SearchResultEntry> newResults =
          convertSearchResult(searchResult);

      // Get or create the search wavelet
      WaveletName searchWaveletName = waveletManager.getOrCreateSearchWavelet(user, rawQuery);

      // Compute current results stored in the search wavelet (get from data provider cache)
      List<SearchWaveletDataProvider.SearchResultEntry> oldResults =
          dataProvider.getCurrentResults(searchWaveletName);

      // Compute the diff
      SearchWaveletDataProvider.SearchDiff diff =
          dataProvider.computeDiff(oldResults, newResults);

      if (diff == null) {
        // No changes needed
        return;
      }

      // Update the data provider's cached state
      dataProvider.updateCurrentResults(searchWaveletName, newResults);

      // Update the indexer's wave set for this subscription
      Set<WaveId> newWaveIds = new HashSet<>();
      for (SearchWaveletDataProvider.SearchResultEntry entry : newResults) {
        try {
          newWaveIds.add(WaveId.deserialise(entry.getWaveId()));
        } catch (Exception e) {
          LOG.warning("Failed to parse wave ID: " + entry.getWaveId(), e);
        }
      }
      indexer.updateSubscriptionWaves(user, key.getQueryHash(), newWaveIds);

      if (LOG.isFineLoggable()) {
        LOG.fine("Updated search wavelet " + searchWaveletName + " for " + user.getAddress()
            + ": " + diff.getAddedCount() + " added, " + diff.getRemovedCount() + " removed, "
            + diff.getModifiedCount() + " modified");
      }

    } catch (Exception e) {
      // Log and continue -- never let one bad update block others
      LOG.severe("Failed to update search wavelet for " + key, e);
    }
  }

  /**
   * Converts a SearchResult from the SearchProvider into our internal
   * SearchResultEntry list.
   */
  private List<SearchWaveletDataProvider.SearchResultEntry> convertSearchResult(
      SearchResult searchResult) {
    List<SearchWaveletDataProvider.SearchResultEntry> entries = new ArrayList<>();
    if (searchResult == null || searchResult.getDigests() == null) {
      return entries;
    }
    for (SearchResult.Digest digest : searchResult.getDigests()) {
      // Digest has no getAuthor(); use first participant as creator if available.
      List<String> participants = digest.getParticipants();
      String creator = (participants != null && !participants.isEmpty())
          ? participants.get(0) : "";
      entries.add(new SearchWaveletDataProvider.SearchResultEntry(
          digest.getWaveId(),
          digest.getTitle() != null ? digest.getTitle() : "",
          digest.getSnippet() != null ? digest.getSnippet() : "",
          digest.getLastModified(),
          creator,
          participants != null ? participants.size() : 0,
          digest.getUnreadCount(),
          digest.getBlipCount()));
    }
    return entries;
  }

  /**
   * Shuts down the background scheduler. Called during server shutdown.
   */
  public void shutdown() {
    scheduler.shutdownNow();
  }

  /**
   * Simple per-user rate limiter using a sliding window counter.
   */
  private static class UpdateCounter {
    private final int maxPerSecond;
    private long windowStart;
    private int count;
    private int queueSize;

    UpdateCounter(int maxPerSecond) {
      this.maxPerSecond = maxPerSecond;
      this.windowStart = System.currentTimeMillis();
      this.count = 0;
      this.queueSize = 0;
    }

    synchronized boolean tryAcquire() {
      long now = System.currentTimeMillis();
      if (now - windowStart >= 1000) {
        windowStart = now;
        count = 0;
      }
      if (count >= maxPerSecond) {
        return false;
      }
      count++;
      return true;
    }

    synchronized int getQueueSize() {
      return queueSize;
    }

    synchronized void incrementQueue() {
      queueSize++;
    }

    synchronized void decrementQueue() {
      if (queueSize > 0) queueSize--;
    }
  }
}
