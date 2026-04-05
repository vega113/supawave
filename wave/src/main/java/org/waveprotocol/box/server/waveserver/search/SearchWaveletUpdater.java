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
import com.typesafe.config.Config;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveserver.SearchProvider;
import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

@Singleton
public class SearchWaveletUpdater implements WaveBus.Subscriber {

  private static final Log LOG = Log.get(SearchWaveletUpdater.class);
  private static final long DEBOUNCE_MS = 100;
  private static final long MAX_WAIT_MS = 500;
  private static final int MAX_UPDATES_PER_SEC = 10;
  private static final int MAX_QUEUE_PER_USER = 100;

  private final SearchWaveletManager waveletManager;
  private final SearchIndexer indexer;
  private final SearchProvider searchProvider;
  private final SearchWaveletDataProvider dataProvider;
  private final SearchWaveletSnapshotPublisher snapshotPublisher;
  private final SearchUpdateBatchingPolicy batchingPolicy;
  private final ScheduledExecutorService scheduler;
  private final ConcurrentHashMap<String, TaskHolder> pendingTasks =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> firstSeenTimestamps =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, UpdateCounter> userCounters =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<WaveId, SlowPathBatchState> slowPathBatches =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<WaveletName, Set<ParticipantId>> pendingMentionedUsers =
      new ConcurrentHashMap<>();
  private final AtomicLong waveUpdateCount = new AtomicLong();
  private final AtomicLong lowLatencyWaveUpdateCount = new AtomicLong();
  private final AtomicLong slowPathWaveUpdateCount = new AtomicLong();
  private final AtomicLong slowPathFlushCount = new AtomicLong();
  private final AtomicLong slowPathQueuedSubscriptionCount = new AtomicLong();
  private final AtomicLong searchRecomputeCount = new AtomicLong();

  @Inject
  public SearchWaveletUpdater(
      SearchWaveletManager waveletManager,
      SearchIndexer indexer,
      SearchProvider searchProvider,
      SearchWaveletDataProvider dataProvider,
      SearchWaveletSnapshotPublisher snapshotPublisher,
      Config config) {
    this(
        waveletManager,
        indexer,
        searchProvider,
        dataProvider,
        snapshotPublisher,
        new SearchUpdateBatchingPolicy(config),
        createScheduler());
  }

  SearchWaveletUpdater(
      SearchWaveletManager waveletManager,
      SearchIndexer indexer,
      SearchProvider searchProvider,
      SearchWaveletDataProvider dataProvider,
      SearchWaveletSnapshotPublisher snapshotPublisher) {
    this(
        waveletManager,
        indexer,
        searchProvider,
        dataProvider,
        snapshotPublisher,
        SearchUpdateBatchingPolicy.defaults(),
        createScheduler());
  }

  SearchWaveletUpdater(
      SearchWaveletManager waveletManager,
      SearchIndexer indexer,
      SearchProvider searchProvider,
      SearchWaveletDataProvider dataProvider,
      SearchWaveletSnapshotPublisher snapshotPublisher,
      SearchUpdateBatchingPolicy batchingPolicy,
      ScheduledExecutorService scheduler) {
    this.waveletManager = waveletManager;
    this.indexer = indexer;
    this.searchProvider = searchProvider;
    this.dataProvider = dataProvider;
    this.snapshotPublisher = snapshotPublisher;
    this.batchingPolicy = batchingPolicy;
    this.scheduler = scheduler;
  }

  private static ScheduledExecutorService createScheduler() {
    return Executors.newScheduledThreadPool(2, runnable -> {
      Thread thread = new Thread(runnable, "SearchWaveletUpdater-scheduler");
      thread.setDaemon(true);
      return thread;
    });
  }

  @Override
  public void waveletUpdate(ReadableWaveletData wavelet, DeltaSequence deltas) {
    WaveletName waveletName = WaveletName.of(wavelet.getWaveId(), wavelet.getWaveletId());
    boolean searchWavelet = waveletManager.isSearchWavelet(waveletName);
    if (!searchWavelet) {
      WaveId changedWaveId = wavelet.getWaveId();
      Set<ParticipantId> participants = Sets.newHashSet(wavelet.getParticipants());
      Set<SearchIndexer.SubscriptionKey> affected =
          indexer.getAffectedSubscriptions(changedWaveId, participants);
      if (!affected.isEmpty()) {
        waveUpdateCount.incrementAndGet();
        if (LOG.isFineLoggable()) {
          LOG.fine(
              "Wave " + changedWaveId + " change affects " + affected.size() + " subscriptions");
        }
        SearchUpdateBatchingPolicy.UpdateMode mode = batchingPolicy.classify(wavelet, affected.size());
        if (mode == SearchUpdateBatchingPolicy.UpdateMode.POLL_EQUIVALENT) {
          slowPathWaveUpdateCount.incrementAndGet();
          slowPathQueuedSubscriptionCount.addAndGet(affected.size());
          enqueueSlowPathBatch(changedWaveId, affected);
        } else {
          lowLatencyWaveUpdateCount.incrementAndGet();
          enqueueLowLatencyUpdates(affected);
        }
      }
      if (IdUtil.isConversationalId(wavelet.getWaveletId())) {
        Set<ParticipantId> mentioned = extractMentionedUsers(wavelet);
        mentioned.removeAll(participants);
        if (!mentioned.isEmpty()) {
          pendingMentionedUsers.put(waveletName, mentioned);
        }
      }
    }
  }

  @Override
  public void waveletCommitted(WaveletName waveletName, HashedVersion version) {
    Set<ParticipantId> mentioned = pendingMentionedUsers.remove(waveletName);
    if (mentioned != null && !mentioned.isEmpty()) {
      Set<SearchIndexer.SubscriptionKey> affected =
          indexer.getAffectedSubscriptions(waveletName.waveId, mentioned);
      if (!affected.isEmpty()) {
        enqueueLowLatencyUpdates(affected);
      }
    }
  }

  private static Set<ParticipantId> extractMentionedUsers(ReadableWaveletData wavelet) {
    Set<ParticipantId> mentioned = new HashSet<>();
    for (String docId : wavelet.getDocumentIds()) {
      ReadableBlipData blip = wavelet.getDocument(docId);
      if (blip == null) {
        continue;
      }
      blip.getContent().asOperation().apply(new DocInitializationCursor() {
        @Override
        public void annotationBoundary(AnnotationBoundaryMap map) {
          for (int i = 0; i < map.changeSize(); i++) {
            String key = map.getChangeKey(i);
            String value = map.getNewValue(i);
            if (AnnotationConstants.isMentionKey(key) && value != null && !value.isEmpty()) {
              try {
                mentioned.add(ParticipantId.ofUnsafe(value.trim().toLowerCase(Locale.ROOT)));
              } catch (IllegalArgumentException e) {
                LOG.warning("Skipping malformed mention annotation value: " + value);
              }
            }
          }
        }

        @Override
        public void characters(String s) {
        }

        @Override
        public void elementStart(String type, Attributes attrs) {
        }

        @Override
        public void elementEnd() {
        }
      });
    }
    return mentioned;
  }

  private void enqueueLowLatencyUpdates(Set<SearchIndexer.SubscriptionKey> affected) {
    for (SearchIndexer.SubscriptionKey key : affected) {
      enqueueUpdate(key);
    }
  }

  private void enqueueSlowPathBatch(WaveId changedWaveId, Set<SearchIndexer.SubscriptionKey> affected) {
    SlowPathBatchState batchState =
        slowPathBatches.computeIfAbsent(changedWaveId, ignored -> new SlowPathBatchState());
    boolean shouldSchedule;
    synchronized (batchState) {
      batchState.pendingKeys.addAll(affected);
      shouldSchedule = batchState.future == null || batchState.future.isDone();
      if (shouldSchedule) {
        batchState.future =
            scheduler.schedule(
                () -> flushSlowPathBatch(changedWaveId, batchState),
                batchingPolicy.getPublicBatchMs(),
                TimeUnit.MILLISECONDS);
      }
    }
  }

  private void flushSlowPathBatch(WaveId changedWaveId, SlowPathBatchState batchState) {
    Set<SearchIndexer.SubscriptionKey> keysToUpdate;
    synchronized (batchState) {
      batchState.future = null;
      keysToUpdate = new HashSet<>(batchState.pendingKeys);
      batchState.pendingKeys.clear();
    }
    if (!keysToUpdate.isEmpty()) {
      slowPathFlushCount.incrementAndGet();
      enqueueLowLatencyUpdates(keysToUpdate);
    }
    synchronized (batchState) {
      boolean idle = batchState.future == null && batchState.pendingKeys.isEmpty();
      if (idle) {
        slowPathBatches.remove(changedWaveId, batchState);
      }
    }
  }

  private void enqueueUpdate(SearchIndexer.SubscriptionKey key) {
    String taskKey = key.toString();
    long now = System.currentTimeMillis();
    long firstSeen = firstSeenTimestamps.computeIfAbsent(taskKey, ignored -> now);
    UpdateCounter counter = userCounters.computeIfAbsent(
        key.getUser().getAddress(), ignored -> new UpdateCounter(MAX_UPDATES_PER_SEC));
    TaskHolder taskHolder = pendingTasks.computeIfAbsent(taskKey, ignored -> new TaskHolder());
    long delay;
    long generation;
    synchronized (taskHolder) {
      boolean hasPendingTask = taskHolder.queued;
      if (!hasPendingTask && counter.getQueueSize() >= MAX_QUEUE_PER_USER) {
        LOG.warning("Dropping search update for " + key + " -- queue full");
        firstSeenTimestamps.remove(taskKey);
        pendingTasks.remove(taskKey, taskHolder);
        return;
      }
      long elapsed = now - firstSeen;
      delay = elapsed >= MAX_WAIT_MS ? 0 : Math.min(DEBOUNCE_MS, MAX_WAIT_MS - elapsed);
      generation = taskHolder.generation.incrementAndGet();
      if (hasPendingTask) {
        ScheduledFuture<?> existing = taskHolder.future;
        if (existing != null) {
          existing.cancel(false);
        }
      } else {
        taskHolder.queued = true;
        counter.incrementQueue();
      }
    }
    scheduleUpdate(key, taskKey, delay, taskHolder, generation);
  }

  private void executeUpdate(SearchIndexer.SubscriptionKey key, String taskKey) {
    executeSearchUpdate(key);
  }

  private void executePendingUpdate(
      SearchIndexer.SubscriptionKey key,
      String taskKey,
      TaskHolder taskHolder,
      long generation) {
    try {
      UpdateCounter counter = userCounters.get(key.getUser().getAddress());
      synchronized (taskHolder) {
        if (taskHolder.generation.get() != generation) {
          return;
        }
        boolean acquired = counter == null || counter.tryAcquire();
        if (!acquired) {
          long retryGeneration = taskHolder.generation.incrementAndGet();
          scheduleUpdate(key, taskKey, 100, taskHolder, retryGeneration);
          return;
        }
      }
      UpdateOutcome outcome = executeSearchUpdateIfCurrent(key, taskHolder, generation);
      if (outcome == UpdateOutcome.APPLIED || outcome == UpdateOutcome.FAILED) {
        completePendingUpdate(taskKey, taskHolder, generation, counter);
      }
    } catch (Exception e) {
      LOG.severe("Failed to update search wavelet for " + key, e);
    }
  }

  private void executeSearchUpdate(SearchIndexer.SubscriptionKey key) {
    try {
      String rawQuery = indexer.getRawQuery(key);
      if (rawQuery == null) {
        LOG.fine("Subscription " + key + " no longer registered, skipping update");
      } else {
        ParticipantId user = key.getUser();
        searchRecomputeCount.incrementAndGet();
        SearchResult searchResult =
            searchProvider.search(
                user, rawQuery, 0, SearchWaveletSnapshotPublisher.LIVE_SEARCH_NUM_RESULTS);
        if (snapshotPublisher != null) {
          snapshotPublisher.publishUpdate(user, rawQuery, searchResult);
        } else {
          updateCachedSearchWavelet(key, user, rawQuery, searchResult);
        }
      }
    } catch (Exception e) {
      LOG.severe("Failed to update search wavelet for " + key, e);
    }
  }

  private UpdateOutcome executeSearchUpdateIfCurrent(
      SearchIndexer.SubscriptionKey key,
      TaskHolder taskHolder,
      long generation) {
    try {
      String rawQuery = indexer.getRawQuery(key);
      if (rawQuery == null) {
        LOG.fine("Subscription " + key + " no longer registered, skipping update");
        return UpdateOutcome.APPLIED;
      }
      ParticipantId user = key.getUser();
      searchRecomputeCount.incrementAndGet();
      SearchResult searchResult =
          searchProvider.search(
              user, rawQuery, 0, SearchWaveletSnapshotPublisher.LIVE_SEARCH_NUM_RESULTS);
      synchronized (taskHolder) {
        if (taskHolder.generation.get() != generation) {
          return UpdateOutcome.STALE;
        }
        if (snapshotPublisher != null) {
          snapshotPublisher.publishUpdate(user, rawQuery, searchResult);
        } else {
          updateCachedSearchWavelet(key, user, rawQuery, searchResult);
        }
      }
      return UpdateOutcome.APPLIED;
    } catch (Exception e) {
      LOG.severe("Failed to update search wavelet for " + key, e);
      return UpdateOutcome.FAILED;
    }
  }

  private void completePendingUpdate(
      String taskKey,
      TaskHolder taskHolder,
      long generation,
      UpdateCounter counter) {
    synchronized (taskHolder) {
      if (taskHolder.generation.get() != generation) {
        return;
      }
      if (!pendingTasks.remove(taskKey, taskHolder)) {
        return;
      }
      taskHolder.queued = false;
      taskHolder.future = null;
      if (counter != null) {
        counter.decrementQueue();
      }
      firstSeenTimestamps.remove(taskKey);
    }
  }

  private void scheduleUpdate(
      SearchIndexer.SubscriptionKey key,
      String taskKey,
      long delayMs,
      TaskHolder taskHolder,
      long generation) {
    ScheduledFuture<?> future =
        scheduler.schedule(
            () -> executePendingUpdate(key, taskKey, taskHolder, generation),
            delayMs,
            TimeUnit.MILLISECONDS);
    synchronized (taskHolder) {
      if (taskHolder.generation.get() != generation || !taskHolder.queued) {
        future.cancel(false);
        return;
      }
      taskHolder.future = future;
    }
  }

  private void updateCachedSearchWavelet(
      SearchIndexer.SubscriptionKey key,
      ParticipantId user,
      String rawQuery,
      SearchResult searchResult) {
    List<SearchWaveletDataProvider.SearchResultEntry> newResults = convertSearchResult(searchResult);
    int newTotalCount =
        searchResult.getTotalResults() >= 0 ? searchResult.getTotalResults() : newResults.size();
    WaveletName searchWaveletName = waveletManager.getOrCreateSearchWavelet(user, rawQuery);
    List<SearchWaveletDataProvider.SearchResultEntry> oldResults =
        dataProvider.getCurrentResults(searchWaveletName);
    int oldTotalCount = dataProvider.getCurrentTotal(searchWaveletName);
    SearchWaveletDataProvider.SearchDiff diff =
        dataProvider.computeDiff(oldResults, oldTotalCount, newResults, newTotalCount);
    if (diff != null) {
      dataProvider.updateCurrentResults(searchWaveletName, newResults, newTotalCount);
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
        LOG.fine(
            "Updated search wavelet "
                + searchWaveletName
                + " for "
                + user.getAddress()
                + ": "
                + diff.getAddedCount()
                + " added, "
                + diff.getRemovedCount()
                + " removed, "
                + diff.getModifiedCount()
                + " modified");
      }
    }
  }

  private List<SearchWaveletDataProvider.SearchResultEntry> convertSearchResult(
      SearchResult searchResult) {
    List<SearchWaveletDataProvider.SearchResultEntry> entries = new ArrayList<>();
    if (searchResult != null && searchResult.getDigests() != null) {
      for (SearchResult.Digest digest : searchResult.getDigests()) {
        List<String> participants = digest.getParticipants();
        String creator =
            participants != null && !participants.isEmpty() ? participants.get(0) : "";
        entries.add(
            new SearchWaveletDataProvider.SearchResultEntry(
                digest.getWaveId(),
                digest.getTitle() != null ? digest.getTitle() : "",
                digest.getSnippet() != null ? digest.getSnippet() : "",
                digest.getLastModified(),
                creator,
                participants != null ? participants.size() : 0,
                digest.getUnreadCount(),
                digest.getBlipCount()));
      }
    }
    return entries;
  }

  public boolean isPublicBatchingEnabled() {
    return batchingPolicy.isPublicBatchingEnabled();
  }

  public long getPublicBatchMs() {
    return batchingPolicy.getPublicBatchMs();
  }

  public int getPublicFanoutThreshold() {
    return batchingPolicy.getPublicFanoutThreshold();
  }

  public int getHighParticipantThreshold() {
    return batchingPolicy.getHighParticipantThreshold();
  }

  public long getWaveUpdateCount() {
    return waveUpdateCount.get();
  }

  public long getLowLatencyWaveUpdateCount() {
    return lowLatencyWaveUpdateCount.get();
  }

  public long getSlowPathWaveUpdateCount() {
    return slowPathWaveUpdateCount.get();
  }

  public long getSlowPathFlushCount() {
    return slowPathFlushCount.get();
  }

  public long getSlowPathQueuedSubscriptionCount() {
    return slowPathQueuedSubscriptionCount.get();
  }

  public long getSearchRecomputeCount() {
    return searchRecomputeCount.get();
  }

  public int getActiveSubscriptionCount() {
    return indexer.getSubscriptionCount();
  }

  public int getIndexedWaveCount() {
    return indexer.getIndexedWaveCount();
  }

  public void shutdown() {
    scheduler.shutdownNow();
    pendingTasks.clear();
    slowPathBatches.clear();
    pendingMentionedUsers.clear();
  }

  static final class SearchUpdateBatchingPolicy {

    private static final boolean DEFAULT_PUBLIC_BATCHING_ENABLED = true;
    private static final long DEFAULT_PUBLIC_BATCH_MS = 15000;
    private static final int DEFAULT_PUBLIC_FANOUT_THRESHOLD = 25;
    private static final int DEFAULT_HIGH_PARTICIPANT_THRESHOLD = 25;

    enum UpdateMode {
      LOW_LATENCY,
      POLL_EQUIVALENT
    }

    private final boolean publicBatchingEnabled;
    private final long publicBatchMs;
    private final int publicFanoutThreshold;
    private final int highParticipantThreshold;

    SearchUpdateBatchingPolicy(Config config) {
      this(
          config.hasPath("search.ot_search_public_batching_enabled")
              ? config.getBoolean("search.ot_search_public_batching_enabled")
              : DEFAULT_PUBLIC_BATCHING_ENABLED,
          config.hasPath("search.ot_search_public_batch_ms")
              ? config.getLong("search.ot_search_public_batch_ms")
              : DEFAULT_PUBLIC_BATCH_MS,
          config.hasPath("search.ot_search_public_fanout_threshold")
              ? config.getInt("search.ot_search_public_fanout_threshold")
              : DEFAULT_PUBLIC_FANOUT_THRESHOLD,
          config.hasPath("search.ot_search_high_participant_threshold")
              ? config.getInt("search.ot_search_high_participant_threshold")
              : DEFAULT_HIGH_PARTICIPANT_THRESHOLD);
    }

    SearchUpdateBatchingPolicy(
        boolean publicBatchingEnabled,
        long publicBatchMs,
        int publicFanoutThreshold,
        int highParticipantThreshold) {
      this.publicBatchingEnabled = publicBatchingEnabled;
      this.publicBatchMs = Math.max(0L, publicBatchMs);
      this.publicFanoutThreshold = Math.max(1, publicFanoutThreshold);
      this.highParticipantThreshold = Math.max(1, highParticipantThreshold);
    }

    static SearchUpdateBatchingPolicy defaults() {
      return new SearchUpdateBatchingPolicy(
          DEFAULT_PUBLIC_BATCHING_ENABLED,
          DEFAULT_PUBLIC_BATCH_MS,
          DEFAULT_PUBLIC_FANOUT_THRESHOLD,
          DEFAULT_HIGH_PARTICIPANT_THRESHOLD);
    }

    UpdateMode classify(ReadableWaveletData wavelet, int affectedSubscriptionCount) {
      boolean highFanout = affectedSubscriptionCount >= publicFanoutThreshold;
      boolean publicWave = isPublicWavelet(wavelet);
      boolean highParticipantCount = hasHighParticipantCount(wavelet);
      boolean shouldUseSlowPath =
          publicBatchingEnabled && highFanout && (publicWave || highParticipantCount);
      return shouldUseSlowPath ? UpdateMode.POLL_EQUIVALENT : UpdateMode.LOW_LATENCY;
    }

    boolean isPublicBatchingEnabled() {
      return publicBatchingEnabled;
    }

    long getPublicBatchMs() {
      return publicBatchMs;
    }

    int getPublicFanoutThreshold() {
      return publicFanoutThreshold;
    }

    int getHighParticipantThreshold() {
      return highParticipantThreshold;
    }

    private boolean hasHighParticipantCount(ReadableWaveletData wavelet) {
      return wavelet != null && wavelet.getParticipants().size() >= highParticipantThreshold;
    }

    private boolean isPublicWavelet(ReadableWaveletData wavelet) {
      if (wavelet == null) {
        return false;
      }
      ParticipantId sharedDomainParticipant =
          ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(wavelet.getWaveId().getDomain());
      return WaveletDataUtil.isPublicWavelet(wavelet, sharedDomainParticipant);
    }
  }

  private static final class SlowPathBatchState {
    private final Set<SearchIndexer.SubscriptionKey> pendingKeys = new HashSet<>();
    private ScheduledFuture<?> future;
  }

  private static final class TaskHolder {
    private final AtomicLong generation = new AtomicLong();
    private ScheduledFuture<?> future;
    private boolean queued;
  }

  private enum UpdateOutcome {
    APPLIED,
    STALE,
    FAILED
  }

  private static final class UpdateCounter {
    private final int maxPerSecond;
    private long windowStart;
    private int count;
    private int queueSize;

    private UpdateCounter(int maxPerSecond) {
      this.maxPerSecond = maxPerSecond;
      this.windowStart = System.currentTimeMillis();
    }

    synchronized boolean tryAcquire() {
      long now = System.currentTimeMillis();
      if (now - windowStart >= 1000) {
        windowStart = now;
        count = 0;
      }
      boolean available = count < maxPerSecond;
      if (available) {
        count++;
      }
      return available;
    }

    synchronized int getQueueSize() {
      return queueSize;
    }

    synchronized void incrementQueue() {
      queueSize++;
    }

    synchronized void decrementQueue() {
      if (queueSize > 0) {
        queueSize--;
      }
    }
  }
}
