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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.wave.api.SearchResult;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;
import org.waveprotocol.box.common.DeltaSequence;
import org.waveprotocol.box.common.comms.WaveClientRpc.WaveletVersion;
import org.waveprotocol.box.server.frontend.ClientFrontend.OpenListener;
import org.waveprotocol.box.server.frontend.ClientFrontendImpl;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.frontend.SearchWaveletDispatcher;
import org.waveprotocol.box.server.frontend.WaveletInfo;
import org.waveprotocol.box.server.waveserver.SearchProvider;
import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SearchWaveletUpdaterTest extends TestCase {

  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY =
      new HashedVersionFactoryImpl(URI_CODEC);
  private static final java.util.Collection<WaveletVersion> NO_KNOWN_WAVELETS =
      Collections.<WaveletVersion>emptySet();

  public void testExecuteUpdateUsesSearchProviderTotalResults() throws Exception {
    SearchWaveletManager waveletManager = mock(SearchWaveletManager.class);
    SearchIndexer indexer = mock(SearchIndexer.class);
    SearchProvider searchProvider = mock(SearchProvider.class);
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    SearchWaveletUpdater updater =
        new SearchWaveletUpdater(waveletManager, indexer, searchProvider, dataProvider, null);

    ParticipantId user = ParticipantId.ofUnsafe("alice@example.com");
    String query = "in:inbox";
    String queryHash = SearchWaveletManager.md5Hex(query);
    SearchIndexer.SubscriptionKey key = new SearchIndexer.SubscriptionKey(user, queryHash);
    WaveletName searchWaveletName = WaveletName.of(
        WaveId.of("example.com", "search~alice"),
        WaveletId.of("example.com", "search+" + queryHash));
    SearchWaveletDataProvider.SearchResultEntry entry =
        new SearchWaveletDataProvider.SearchResultEntry(
            "example.com/w+abc",
            "Project standup",
            "Latest reply",
            1711411100000L,
            "bob@example.com",
            3,
            2,
            7);
    SearchResult searchResult = new SearchResult(query);
    searchResult.addDigest(new SearchResult.Digest(
        "Project standup",
        "Latest reply",
        "example.com/w+abc",
        Collections.singletonList("bob@example.com"),
        1711411100000L,
        1711411000000L,
        2,
        7));
    searchResult.setTotalResults(5);

    dataProvider.updateCurrentResults(searchWaveletName, Collections.singletonList(entry), 2);
    when(indexer.getRawQuery(key)).thenReturn(query);
    when(searchProvider.search(user, query, 0, 50)).thenReturn(searchResult);
    when(waveletManager.getOrCreateSearchWavelet(user, query)).thenReturn(searchWaveletName);

    try {
      Method executeUpdate = SearchWaveletUpdater.class.getDeclaredMethod(
          "executeUpdate", SearchIndexer.SubscriptionKey.class, String.class);
      executeUpdate.setAccessible(true);
      executeUpdate.invoke(updater, key, key.toString());

      assertEquals(5, dataProvider.getCurrentTotal(searchWaveletName));
    } finally {
      updater.shutdown();
    }
  }

  public void testExecuteUpdatePreservesRawTagQuery() throws Exception {
    SearchWaveletManager waveletManager = mock(SearchWaveletManager.class);
    SearchIndexer indexer = mock(SearchIndexer.class);
    SearchProvider searchProvider = mock(SearchProvider.class);
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    SearchWaveletUpdater updater =
        new SearchWaveletUpdater(waveletManager, indexer, searchProvider, dataProvider, null);

    ParticipantId user = ParticipantId.ofUnsafe("alice@example.com");
    String query = "tag:work";
    String queryHash = SearchWaveletManager.md5Hex(query);
    SearchIndexer.SubscriptionKey key = new SearchIndexer.SubscriptionKey(user, queryHash);
    WaveletName searchWaveletName = WaveletName.of(
        WaveId.of("example.com", "search~alice"),
        WaveletId.of("example.com", "search+" + queryHash));
    SearchResult searchResult = new SearchResult(query);
    searchResult.setTotalResults(0);

    when(indexer.getRawQuery(key)).thenReturn(query);
    when(searchProvider.search(user, query, 0, 50)).thenReturn(searchResult);
    when(waveletManager.getOrCreateSearchWavelet(user, query)).thenReturn(searchWaveletName);

    try {
      Method executeUpdate = SearchWaveletUpdater.class.getDeclaredMethod(
          "executeUpdate", SearchIndexer.SubscriptionKey.class, String.class);
      executeUpdate.setAccessible(true);
      executeUpdate.invoke(updater, key, key.toString());

      verify(searchProvider).search(user, query, 0, 50);
    } finally {
      updater.shutdown();
    }
  }

  public void testExecuteUpdatePushesSnapshotToSubscribedSearchWavelet() throws Exception {
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    when(waveletProvider.getWaveletIds(any())).thenReturn(ImmutableSet.of());

    WaveletInfo waveletInfo = WaveletInfo.create(HASH_FACTORY, waveletProvider);
    ClientFrontendImpl clientFrontend =
        ClientFrontendImpl.create(waveletProvider, mock(WaveBus.class), waveletInfo);
    SearchWaveletManager waveletManager = new SearchWaveletManager();
    SearchIndexer indexer = new SearchIndexer();
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    SearchWaveletDispatcher dispatcher = new SearchWaveletDispatcher();
    dispatcher.initialize(waveletInfo);
    SearchWaveletSnapshotPublisher publisher =
        new SearchWaveletSnapshotPublisher(dispatcher, waveletManager, indexer, dataProvider);
    SearchProvider searchProvider = mock(SearchProvider.class);
    SearchWaveletUpdater updater =
        new SearchWaveletUpdater(
            waveletManager, indexer, searchProvider, dataProvider, publisher);

    ParticipantId user = ParticipantId.ofUnsafe("alice@example.com");
    String query = "tag:work";
    WaveletName searchWaveletName = waveletManager.computeWaveletName(user, query);
    IdFilter filter = IdFilter.of(
        Collections.singleton(searchWaveletName.waveletId), Collections.<String>emptySet());
    OpenListener listener = mock(OpenListener.class);

    clientFrontend.openRequest(
        user, searchWaveletName.waveId, filter, NO_KNOWN_WAVELETS, listener);

    publisher.publishBootstrap(user, query, createSearchResult(query, "example.com/w+abc", 1));
    when(searchProvider.search(user, query, 0, 50))
        .thenReturn(createSearchResult(query, "example.com/w+def", 2));

    SearchIndexer.SubscriptionKey key =
        new SearchIndexer.SubscriptionKey(user, SearchWaveletManager.md5Hex(query));
    Method executeUpdate = SearchWaveletUpdater.class.getDeclaredMethod(
        "executeUpdate", SearchIndexer.SubscriptionKey.class, String.class);
    executeUpdate.setAccessible(true);
    executeUpdate.invoke(updater, key, key.toString());

    ArgumentCaptor<CommittedWaveletSnapshot> snapshotCaptor =
        ArgumentCaptor.forClass(CommittedWaveletSnapshot.class);
    ArgumentCaptor<HashedVersion> versionCaptor = ArgumentCaptor.forClass(HashedVersion.class);
    verify(listener, times(2)).onUpdate(
        eq(searchWaveletName),
        snapshotCaptor.capture(),
        eq(DeltaSequence.empty()),
        versionCaptor.capture(),
        eq(null),
        any(String.class));

    assertEquals(2, snapshotCaptor.getAllValues().size());
    assertEquals(
        snapshotCaptor.getAllValues().get(1).committedVersion,
        versionCaptor.getAllValues().get(1));

    updater.shutdown();
  }

  public void testHotPublicWaveCollapsesRepeatedEditsIntoOneSlowPathFlush() throws Exception {
    SearchWaveletManager waveletManager = mock(SearchWaveletManager.class);
    SearchIndexer indexer = mock(SearchIndexer.class);
    SearchProvider searchProvider = mock(SearchProvider.class);
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    SearchWaveletSnapshotPublisher snapshotPublisher =
        new SearchWaveletSnapshotPublisher(
            new SearchWaveletDispatcher(),
            new SearchWaveletManager(),
            indexer,
            new SearchWaveletDataProvider());
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    SearchWaveletUpdater.SearchUpdateBatchingPolicy batchingPolicy =
        new SearchWaveletUpdater.SearchUpdateBatchingPolicy(true, 40L, 25, 25);
    SearchWaveletUpdater updater =
        new SearchWaveletUpdater(
            waveletManager,
            indexer,
            searchProvider,
            dataProvider,
            snapshotPublisher,
            batchingPolicy,
            scheduler);

    try {
      WaveId changedWaveId = WaveId.of("example.com", "w+public");
      WaveletId changedWaveletId = WaveletId.of("example.com", "conv+root");
      ReadableWaveletData wavelet = mock(ReadableWaveletData.class);
      when(wavelet.getWaveId()).thenReturn(changedWaveId);
      when(wavelet.getWaveletId()).thenReturn(changedWaveletId);
      when(wavelet.getParticipants()).thenReturn(
          ImmutableSet.of(
              ParticipantId.ofUnsafe("@example.com"),
              ParticipantId.ofUnsafe("author@example.com")));
      when(waveletManager.isSearchWavelet(any(WaveletName.class))).thenReturn(false);

      Set<SearchIndexer.SubscriptionKey> affected = new HashSet<>();
      for (int i = 0; i < 100; i++) {
        ParticipantId user = ParticipantId.ofUnsafe("user" + i + "@example.com");
        affected.add(
            new SearchIndexer.SubscriptionKey(user, SearchWaveletManager.md5Hex("in:inbox")));
      }
      when(indexer.getAffectedSubscriptions(eq(changedWaveId), any())).thenReturn(affected);
      when(indexer.getRawQuery(any())).thenReturn("in:inbox");
      when(searchProvider.search(any(), eq("in:inbox"), eq(0), eq(50)))
          .thenAnswer(invocation -> createSearchResult("in:inbox", "example.com/w+abc", 1));

      for (int i = 0; i < 5; i++) {
        updater.waveletUpdate(wavelet, DeltaSequence.empty());
      }

      waitForRecomputeCount(updater, 100L, 1500L);
      assertEquals(5L, updater.getWaveUpdateCount());
      assertEquals(0L, updater.getLowLatencyWaveUpdateCount());
      assertEquals(5L, updater.getSlowPathWaveUpdateCount());
      assertEquals(1L, updater.getSlowPathFlushCount());
      verify(searchProvider, times(100)).search(any(), eq("in:inbox"), eq(0), eq(50));
    } finally {
      updater.shutdown();
      scheduler.shutdownNow();
    }
  }

  public void testDiversePrivateQueriesStayOnLowLatencyPath() throws Exception {
    SearchWaveletManager waveletManager = mock(SearchWaveletManager.class);
    SearchIndexer indexer = mock(SearchIndexer.class);
    SearchProvider searchProvider = mock(SearchProvider.class);
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    SearchWaveletSnapshotPublisher snapshotPublisher =
        new SearchWaveletSnapshotPublisher(
            new SearchWaveletDispatcher(),
            new SearchWaveletManager(),
            indexer,
            new SearchWaveletDataProvider());
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    SearchWaveletUpdater.SearchUpdateBatchingPolicy batchingPolicy =
        new SearchWaveletUpdater.SearchUpdateBatchingPolicy(true, 40L, 25, 25);
    SearchWaveletUpdater updater =
        new SearchWaveletUpdater(
            waveletManager,
            indexer,
            searchProvider,
            dataProvider,
            snapshotPublisher,
            batchingPolicy,
            scheduler);

    try {
      when(waveletManager.isSearchWavelet(any(WaveletName.class))).thenReturn(false);
      Map<SearchIndexer.SubscriptionKey, String> queries = new HashMap<>();
      when(indexer.getRawQuery(any()))
          .thenAnswer(invocation -> queries.get(invocation.getArgument(0)));
      when(searchProvider.search(any(), anyString(), eq(0), eq(50)))
          .thenAnswer(
              invocation ->
                  createSearchResult(
                      invocation.getArgument(1),
                      "example.com/w+" + SearchWaveletManager.md5Hex(invocation.getArgument(1)),
                      1));
      for (int i = 0; i < 10; i++) {
        WaveId changedWaveId = WaveId.of("example.com", "w+private" + i);
        WaveletId changedWaveletId = WaveletId.of("example.com", "conv+root" + i);
        ParticipantId user = ParticipantId.ofUnsafe("user" + i + "@example.com");
        String query = "tag:team" + i;
        SearchIndexer.SubscriptionKey key =
            new SearchIndexer.SubscriptionKey(user, SearchWaveletManager.md5Hex(query));
        queries.put(key, query);
        when(indexer.getAffectedSubscriptions(eq(changedWaveId), any()))
            .thenReturn(Collections.singleton(key));

        ReadableWaveletData wavelet = mock(ReadableWaveletData.class);
        when(wavelet.getWaveId()).thenReturn(changedWaveId);
        when(wavelet.getWaveletId()).thenReturn(changedWaveletId);
        when(wavelet.getParticipants()).thenReturn(ImmutableSet.of(user));
        updater.waveletUpdate(wavelet, DeltaSequence.empty());
      }

      waitForRecomputeCount(updater, 10L, 1500L);
      assertEquals(10L, updater.getWaveUpdateCount());
      assertEquals(10L, updater.getLowLatencyWaveUpdateCount());
      assertEquals(0L, updater.getSlowPathWaveUpdateCount());
      assertEquals(0L, updater.getSlowPathFlushCount());
      verify(searchProvider, times(10)).search(any(), anyString(), eq(0), eq(50));
    } finally {
      updater.shutdown();
      scheduler.shutdownNow();
    }
  }

  public void testDroppedQueuedUpdateClearsFirstSeenTimestamp() throws Exception {
    SearchWaveletManager waveletManager = mock(SearchWaveletManager.class);
    SearchIndexer indexer = mock(SearchIndexer.class);
    SearchProvider searchProvider = mock(SearchProvider.class);
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    SearchWaveletUpdater updater =
        new SearchWaveletUpdater(waveletManager, indexer, searchProvider, dataProvider, null);

    try {
      ParticipantId user = ParticipantId.ofUnsafe("user@example.com");
      SearchIndexer.SubscriptionKey key =
          new SearchIndexer.SubscriptionKey(user, SearchWaveletManager.md5Hex("in:inbox"));
      Object updateCounter = newUpdateCounter(10);
      for (int i = 0; i < 100; i++) {
        invokeMethod(updateCounter, "incrementQueue");
      }
      setUserCounter(updater, key.getUser().getAddress(), updateCounter);

      invokeEnqueueUpdate(updater, key);

      ConcurrentHashMap<?, ?> firstSeenTimestamps =
          (ConcurrentHashMap<?, ?>) getField(updater, "firstSeenTimestamps");
      assertFalse(firstSeenTimestamps.containsKey(key.toString()));
    } finally {
      updater.shutdown();
    }
  }

  public void testFailedRecomputeCleansPendingState() throws Exception {
    SearchWaveletManager waveletManager = mock(SearchWaveletManager.class);
    SearchIndexer indexer = mock(SearchIndexer.class);
    SearchProvider searchProvider = mock(SearchProvider.class);
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    SearchWaveletUpdater updater =
        new SearchWaveletUpdater(
            waveletManager,
            indexer,
            searchProvider,
            dataProvider,
            null,
            SearchWaveletUpdater.SearchUpdateBatchingPolicy.defaults(),
            scheduler);

    try {
      ParticipantId user = ParticipantId.ofUnsafe("user@example.com");
      String query = "in:inbox";
      SearchIndexer.SubscriptionKey key =
          new SearchIndexer.SubscriptionKey(user, SearchWaveletManager.md5Hex(query));
      when(indexer.getRawQuery(key)).thenReturn(query);
      when(searchProvider.search(user, query, 0, 50))
          .thenThrow(new RuntimeException("boom"));

      invokeEnqueueUpdate(updater, key);
      waitForPendingStateCleanup(updater, key.toString(), 1500L);

      ConcurrentHashMap<String, Object> userCounters =
          (ConcurrentHashMap<String, Object>) getField(updater, "userCounters");
      Object updateCounter = userCounters.get(user.getAddress());
      assertNotNull(updateCounter);

      ConcurrentHashMap<?, ?> pendingTasks =
          (ConcurrentHashMap<?, ?>) getField(updater, "pendingTasks");
      ConcurrentHashMap<?, ?> firstSeenTimestamps =
          (ConcurrentHashMap<?, ?>) getField(updater, "firstSeenTimestamps");

      assertFalse(pendingTasks.containsKey(key.toString()));
      assertFalse(firstSeenTimestamps.containsKey(key.toString()));
      assertEquals(0, invokeIntMethod(updateCounter, "getQueueSize"));
    } finally {
      updater.shutdown();
      scheduler.shutdownNow();
    }
  }

  public void testConcurrentReenqueueKeepsPendingTaskScheduled() throws Exception {
    SearchWaveletManager waveletManager = new SearchWaveletManager();
    SearchIndexer indexer = mock(SearchIndexer.class);
    SearchProvider searchProvider = mock(SearchProvider.class);
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    ControllableScheduler scheduler = new ControllableScheduler();
    SearchWaveletUpdater updater =
        new SearchWaveletUpdater(
            waveletManager,
            indexer,
            searchProvider,
            dataProvider,
            null,
            SearchWaveletUpdater.SearchUpdateBatchingPolicy.defaults(),
            scheduler);

    try {
      ParticipantId user = ParticipantId.ofUnsafe("user@example.com");
      String query = "in:inbox";
      SearchIndexer.SubscriptionKey key =
          new SearchIndexer.SubscriptionKey(user, SearchWaveletManager.md5Hex(query));
      SearchResult searchResult = createSearchResult(query, "example.com/w+abc", 1);
      CountDownLatch firstSearchStarted = new CountDownLatch(1);
      CountDownLatch releaseFirstSearch = new CountDownLatch(1);
      AtomicInteger searchCalls = new AtomicInteger();

      when(indexer.getRawQuery(key)).thenReturn(query);
      when(searchProvider.search(eq(user), eq(query), eq(0), eq(50)))
          .thenAnswer(
              invocation -> {
                if (searchCalls.incrementAndGet() == 1) {
                  firstSearchStarted.countDown();
                  awaitLatch(releaseFirstSearch);
                }
                return searchResult;
              });

      invokeEnqueueUpdate(updater, key);
      ControllableScheduledFuture firstFuture = scheduler.takeScheduledFuture();
      Thread firstExecutionThread = new Thread(firstFuture::runTask);
      firstExecutionThread.start();
      awaitLatch(firstSearchStarted);

      Thread secondEnqueueThread = new Thread(() -> {
        try {
          invokeEnqueueUpdate(updater, key);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
      secondEnqueueThread.start();
      scheduler.awaitSecondSchedule();
      releaseFirstSearch.countDown();
      firstExecutionThread.join(1500L);
      scheduler.releaseSecondSchedule();
      secondEnqueueThread.join(1500L);

      ControllableScheduledFuture secondFuture = scheduler.takeScheduledFuture();
      assertNotNull(secondFuture);
      assertFalse(secondFuture.isCancelled());
      secondFuture.runTask();

      verify(searchProvider, times(2)).search(eq(user), eq(query), eq(0), eq(50));

      ConcurrentHashMap<String, Object> userCounters =
          (ConcurrentHashMap<String, Object>) getField(updater, "userCounters");
      Object updateCounter = userCounters.get(user.getAddress());
      assertNotNull(updateCounter);
      assertEquals(0, invokeIntMethod(updateCounter, "getQueueSize"));

      ConcurrentHashMap<?, ?> pendingTasks =
          (ConcurrentHashMap<?, ?>) getField(updater, "pendingTasks");
      ConcurrentHashMap<?, ?> firstSeenTimestamps =
          (ConcurrentHashMap<?, ?>) getField(updater, "firstSeenTimestamps");
      assertFalse(pendingTasks.containsKey(key.toString()));
      assertFalse(firstSeenTimestamps.containsKey(key.toString()));
    } finally {
      updater.shutdown();
      scheduler.shutdownNow();
    }
  }

  private static void waitForRecomputeCount(
      SearchWaveletUpdater updater, long expectedCount, long timeoutMs) throws Exception {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline
        && updater.getSearchRecomputeCount() < expectedCount) {
      Thread.sleep(10L);
    }
    assertEquals(expectedCount, updater.getSearchRecomputeCount());
  }

  private static void waitForPendingStateCleanup(
      SearchWaveletUpdater updater, String taskKey, long timeoutMs) throws Exception {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      ConcurrentHashMap<?, ?> pendingTasks =
          (ConcurrentHashMap<?, ?>) getField(updater, "pendingTasks");
      ConcurrentHashMap<?, ?> firstSeenTimestamps =
          (ConcurrentHashMap<?, ?>) getField(updater, "firstSeenTimestamps");
      if (!pendingTasks.containsKey(taskKey) && !firstSeenTimestamps.containsKey(taskKey)) {
        return;
      }
      Thread.sleep(10L);
    }
    fail("Timed out waiting for pending state cleanup for " + taskKey);
  }

  private static SearchResult createSearchResult(String query, String waveId, int totalResults) {
    SearchResult searchResult = new SearchResult(query);
    searchResult.addDigest(new SearchResult.Digest(
        "Project standup",
        "Latest reply",
        waveId,
        Collections.singletonList("bob@example.com"),
        1711411100000L,
        1711411000000L,
        2,
        7));
    searchResult.setTotalResults(totalResults);
    return searchResult;
  }

  private static Object newUpdateCounter(int maxPerSecond) throws Exception {
    Class<?> updateCounterClass =
        Class.forName(SearchWaveletUpdater.class.getName() + "$UpdateCounter");
    Constructor<?> constructor = updateCounterClass.getDeclaredConstructor(int.class);
    constructor.setAccessible(true);
    return constructor.newInstance(maxPerSecond);
  }

  private static void invokeEnqueueUpdate(SearchWaveletUpdater updater,
      SearchIndexer.SubscriptionKey key) throws Exception {
    Method method =
        SearchWaveletUpdater.class.getDeclaredMethod(
            "enqueueUpdate", SearchIndexer.SubscriptionKey.class);
    method.setAccessible(true);
    method.invoke(updater, key);
  }

  private static void invokeMethod(Object target, String methodName) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    method.invoke(target);
  }

  private static int invokeIntMethod(Object target, String methodName) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName);
    method.setAccessible(true);
    return ((Integer) method.invoke(target)).intValue();
  }

  private static void setUserCounter(
      SearchWaveletUpdater updater, String userAddress, Object updateCounter) throws Exception {
    ConcurrentHashMap<String, Object> userCounters =
        (ConcurrentHashMap<String, Object>) getField(updater, "userCounters");
    userCounters.put(userAddress, updateCounter);
  }

  private static Object getField(SearchWaveletUpdater updater, String fieldName) throws Exception {
    Field field = SearchWaveletUpdater.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(updater);
  }

  private static void awaitLatch(CountDownLatch latch) {
    try {
      if (!latch.await(1500L, TimeUnit.MILLISECONDS)) {
        fail("Timed out waiting for latch");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  private static final class ControllableScheduler extends AbstractExecutorService
      implements ScheduledExecutorService {

    private final BlockingQueue<ControllableScheduledFuture> futures = new LinkedBlockingQueue<>();
    private final AtomicInteger scheduleCalls = new AtomicInteger();
    private final CountDownLatch secondScheduleEntered = new CountDownLatch(1);
    private final CountDownLatch releaseSecondSchedule = new CountDownLatch(1);
    private final AtomicBoolean shutdown = new AtomicBoolean();

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      if (shutdown.get()) {
        throw new RejectedExecutionException("scheduler is shut down");
      }
      ControllableScheduledFuture future = new ControllableScheduledFuture(command);
      futures.add(future);
      if (scheduleCalls.incrementAndGet() == 2) {
        secondScheduleEntered.countDown();
        awaitLatch(releaseSecondSchedule);
      }
      return future;
    }

    ControllableScheduledFuture takeScheduledFuture() throws InterruptedException {
      return futures.take();
    }

    void awaitSecondSchedule() {
      awaitLatch(secondScheduleEntered);
    }

    void releaseSecondSchedule() {
      releaseSecondSchedule.countDown();
    }

    @Override
    public void shutdown() {
      shutdown.set(true);
    }

    @Override
    public java.util.List<Runnable> shutdownNow() {
      shutdown.set(true);
      return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
      return shutdown.get();
    }

    @Override
    public boolean isTerminated() {
      return shutdown.get();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(Runnable command) {
      command.run();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class ControllableScheduledFuture implements ScheduledFuture<Object> {

    private final Runnable command;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean done = new AtomicBoolean();

    private ControllableScheduledFuture(Runnable command) {
      this.command = command;
    }

    void runTask() {
      if (!cancelled.get()) {
        command.run();
      }
      done.set(true);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      cancelled.set(true);
      done.set(true);
      return true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled.get();
    }

    @Override
    public boolean isDone() {
      return done.get();
    }

    @Override
    public Object get() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object get(long timeout, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return 0L;
    }

    @Override
    public int compareTo(java.util.concurrent.Delayed other) {
      return 0;
    }
  }
}
