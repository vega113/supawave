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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
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
import org.waveprotocol.box.server.waveserver.WaveBus;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class SearchWaveletSnapshotPublisherTest extends TestCase {

  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY =
      new HashedVersionFactoryImpl(URI_CODEC);
  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@example.com");
  private static final String QUERY = "tag:work";
  private static final java.util.Collection<WaveletVersion> NO_KNOWN_WAVELETS =
      Collections.<WaveletVersion>emptySet();

  public void testPublishBootstrapPushesSnapshotToExistingSearchSubscription() throws Exception {
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    when(waveletProvider.getWaveletIds(any())).thenReturn(ImmutableSet.of());

    WaveletInfo waveletInfo = WaveletInfo.create(HASH_FACTORY, waveletProvider);
    ClientFrontendImpl clientFrontend =
        ClientFrontendImpl.create(waveletProvider, mock(WaveBus.class), waveletInfo);
    SearchWaveletManager waveletManager = new SearchWaveletManager();
    SearchWaveletDispatcher dispatcher = new SearchWaveletDispatcher();
    dispatcher.initialize(waveletInfo);
    SearchWaveletSnapshotPublisher publisher =
        new SearchWaveletSnapshotPublisher(
            dispatcher, waveletManager, new SearchIndexer(), new SearchWaveletDataProvider());

    WaveletName searchWaveletName = waveletManager.computeWaveletName(USER, QUERY);
    IdFilter filter = IdFilter.of(
        Collections.singleton(searchWaveletName.waveletId), Collections.<String>emptySet());
    OpenListener listener = mock(OpenListener.class);

    clientFrontend.openRequest(
        USER, searchWaveletName.waveId, filter, NO_KNOWN_WAVELETS, listener);

    publisher.publishBootstrap(USER, QUERY, createSearchResult(QUERY, "example.com/w+abc", 1));

    ArgumentCaptor<CommittedWaveletSnapshot> snapshotCaptor =
        ArgumentCaptor.forClass(CommittedWaveletSnapshot.class);
    ArgumentCaptor<HashedVersion> versionCaptor = ArgumentCaptor.forClass(HashedVersion.class);
    verify(listener).onUpdate(
        eq(searchWaveletName),
        snapshotCaptor.capture(),
        eq(DeltaSequence.empty()),
        versionCaptor.capture(),
        eq(null),
        any(String.class));

    assertNotNull(snapshotCaptor.getValue());
    assertEquals(snapshotCaptor.getValue().committedVersion, versionCaptor.getValue());
  }

  public void testPublishBootstrapSkipsCappedSearchResult() throws Exception {
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    when(waveletProvider.getWaveletIds(any())).thenReturn(ImmutableSet.of());

    WaveletInfo waveletInfo = WaveletInfo.create(HASH_FACTORY, waveletProvider);
    ClientFrontendImpl clientFrontend =
        ClientFrontendImpl.create(waveletProvider, mock(WaveBus.class), waveletInfo);
    SearchWaveletManager waveletManager = new SearchWaveletManager();
    SearchIndexer indexer = new SearchIndexer();
    SearchWaveletDispatcher dispatcher = new SearchWaveletDispatcher();
    dispatcher.initialize(waveletInfo);
    SearchWaveletSnapshotPublisher publisher =
        new SearchWaveletSnapshotPublisher(
            dispatcher, waveletManager, indexer, new SearchWaveletDataProvider());

    WaveletName searchWaveletName = waveletManager.computeWaveletName(USER, QUERY);
    IdFilter filter = IdFilter.of(
        Collections.singleton(searchWaveletName.waveletId), Collections.<String>emptySet());
    OpenListener listener = mock(OpenListener.class);

    clientFrontend.openRequest(
        USER, searchWaveletName.waveId, filter, NO_KNOWN_WAVELETS, listener);

    publisher.publishBootstrap(USER, QUERY, createSearchResult(QUERY, "example.com/w+abc", 2));

    assertEquals(0, waveletManager.getActiveCount());
    assertEquals(0, indexer.getSubscriptionCount());
  }

  public void testPublishUpdateKeepsActiveSearchSubscriptionWhenResultIsCapped()
      throws Exception {
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
        new SearchWaveletSnapshotPublisher(
            dispatcher, waveletManager, indexer, dataProvider);

    WaveletName searchWaveletName = waveletManager.computeWaveletName(USER, QUERY);
    IdFilter filter = IdFilter.of(
        Collections.singleton(searchWaveletName.waveletId), Collections.<String>emptySet());
    OpenListener listener = mock(OpenListener.class);

    clientFrontend.openRequest(
        USER, searchWaveletName.waveId, filter, NO_KNOWN_WAVELETS, listener);

    publisher.publishBootstrap(USER, QUERY, createSearchResult(QUERY, "example.com/w+abc", 1));
    publisher.publishUpdate(USER, QUERY, createSearchResult(QUERY, "example.com/w+def", 2));

    assertEquals(1, waveletManager.getActiveCount());
    assertEquals(1, indexer.getSubscriptionCount());
    assertEquals("example.com/w+def",
        dataProvider.getCurrentResults(searchWaveletName).get(0).getWaveId());
  }

  public void testPublishBootstrapSkipsInactiveSearchWavelet() {
    SearchWaveletManager waveletManager = new SearchWaveletManager();
    SearchIndexer indexer = new SearchIndexer();
    SearchWaveletSnapshotPublisher publisher =
        new SearchWaveletSnapshotPublisher(
            new SearchWaveletDispatcher(),
            waveletManager,
            indexer,
            new SearchWaveletDataProvider());

    publisher.publishBootstrap(USER, QUERY, createSearchResult(QUERY, "example.com/w+abc", 1));

    assertEquals(0, waveletManager.getActiveCount());
    assertEquals(0, indexer.getSubscriptionCount());
  }

  public void testPublishUpdateSerializesConcurrentStateTransitions() throws Exception {
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    when(waveletProvider.getWaveletIds(any())).thenReturn(ImmutableSet.of());

    WaveletInfo waveletInfo = WaveletInfo.create(HASH_FACTORY, waveletProvider);
    ClientFrontendImpl clientFrontend =
        ClientFrontendImpl.create(waveletProvider, mock(WaveBus.class), waveletInfo);
    SearchWaveletManager waveletManager = new SearchWaveletManager();
    SearchIndexer indexer = new SearchIndexer();
    BlockingSearchWaveletDataProvider dataProvider =
        new BlockingSearchWaveletDataProvider("example.com/w+old", "example.com/w+new");
    SearchWaveletDispatcher dispatcher = new SearchWaveletDispatcher();
    dispatcher.initialize(waveletInfo);
    SearchWaveletSnapshotPublisher publisher =
        new SearchWaveletSnapshotPublisher(
            dispatcher,
            waveletManager,
            indexer,
            dataProvider);
    AtomicReference<Throwable> failure = new AtomicReference<>();

    WaveletName searchWaveletName = waveletManager.computeWaveletName(USER, QUERY);
    IdFilter filter = IdFilter.of(
        Collections.singleton(searchWaveletName.waveletId),
        Collections.<String>emptySet());
    clientFrontend.openRequest(
        USER, searchWaveletName.waveId, filter, NO_KNOWN_WAVELETS, mock(OpenListener.class));
    Thread olderPublish = new Thread(
        () -> runPublish(
            publisher,
            createSearchResult(QUERY, "example.com/w+old", 1),
            failure),
        "older-publish");
    Thread newerPublish = new Thread(
        () -> runPublish(
            publisher,
            createSearchResult(QUERY, "example.com/w+new", 1),
            failure),
        "newer-publish");

    olderPublish.start();
    assertTrue(dataProvider.awaitOlderUpdateReady());
    newerPublish.start();
    try {
      assertFalse(dataProvider.awaitNewerUpdateReady());
    } finally {
      dataProvider.allowOlderUpdate();
      olderPublish.join();
      newerPublish.join();
    }

    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }

    assertEquals(
        "example.com/w+new",
        dataProvider.getCurrentResults(searchWaveletName).get(0).getWaveId());
  }

  public void testPublishUpdatePrunesRetainedStateAfterSubscriptionDropsBeforeLockAcquisition()
      throws Exception {
    WaveletProvider waveletProvider = mock(WaveletProvider.class);
    when(waveletProvider.getWaveletIds(any())).thenReturn(ImmutableSet.of());

    WaveletInfo waveletInfo = WaveletInfo.create(HASH_FACTORY, waveletProvider);
    ClientFrontendImpl clientFrontend =
        ClientFrontendImpl.create(waveletProvider, mock(WaveBus.class), waveletInfo);
    SearchWaveletManager waveletManager = new SearchWaveletManager();
    SearchIndexer indexer = new SearchIndexer();
    SearchWaveletDispatcher dispatcher = new SearchWaveletDispatcher();
    dispatcher.initialize(waveletInfo);
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    SearchWaveletSnapshotPublisher publisher =
        new SearchWaveletSnapshotPublisher(
            dispatcher,
            waveletManager,
            indexer,
            dataProvider);
    AtomicReference<Throwable> failure = new AtomicReference<>();

    WaveletName searchWaveletName = waveletManager.computeWaveletName(USER, QUERY);
    IdFilter filter = IdFilter.of(
        Collections.singleton(searchWaveletName.waveletId),
        Collections.<String>emptySet());
    clientFrontend.openRequest(
        USER,
        searchWaveletName.waveId,
        filter,
        NO_KNOWN_WAVELETS,
        mock(OpenListener.class));

    publisher.publishUpdate(USER, QUERY, createSearchResult(QUERY, "example.com/w+live", 1));

    assertEquals(1, waveletManager.getActiveCount());
    assertEquals(1, indexer.getSubscriptionCount());
    assertEquals("example.com/w+live",
        dataProvider.getCurrentResults(searchWaveletName).get(0).getWaveId());
    assertEquals(1, getPublisherMap(publisher, "waveletVersions").size());
    assertEquals(1, getPublisherMap(publisher, "publishLocks").size());

    Object publishLock = getPublisherMap(publisher, "publishLocks").get(searchWaveletName.toString());
    assertNotNull(publishLock);

    Thread publishThread =
        new Thread(
            () -> runPublish(
                publisher,
                createSearchResult(QUERY, "example.com/w+stale", 1),
                failure),
            "publish-update");

    synchronized (publishLock) {
      clearSubscriptions(waveletInfo, searchWaveletName);
      publishThread.start();
      awaitThreadState(publishThread, Thread.State.BLOCKED);
    }

    publishThread.join(TimeUnit.SECONDS.toMillis(5));
    assertFalse(publishThread.isAlive());

    if (failure.get() != null) {
      throw new AssertionError(failure.get());
    }

    assertFalse(publisher.hasLiveSubscription(USER, QUERY));
    assertEquals(0, waveletManager.getActiveCount());
    assertEquals(0, indexer.getSubscriptionCount());
    assertTrue(dataProvider.getCurrentResults(searchWaveletName).isEmpty());
    assertEquals(-1, dataProvider.getCurrentTotal(searchWaveletName));
    assertEquals(0, getPublisherMap(publisher, "waveletVersions").size());
    assertEquals(0, getPublisherMap(publisher, "publishLocks").size());
  }

  public void testPublishUpdateWithoutLiveSubscriptionDoesNotRetainState() throws Exception {
    SearchWaveletManager waveletManager = new SearchWaveletManager();
    SearchIndexer indexer = new SearchIndexer();
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    SearchWaveletSnapshotPublisher publisher =
        new SearchWaveletSnapshotPublisher(
            new SearchWaveletDispatcher(),
            waveletManager,
            indexer,
            dataProvider);

    publisher.publishUpdate(USER, QUERY, createSearchResult(QUERY, "example.com/w+inactive", 1));

    WaveletName searchWaveletName = waveletManager.computeWaveletName(USER, QUERY);
    assertEquals(0, waveletManager.getActiveCount());
    assertEquals(0, indexer.getSubscriptionCount());
    assertTrue(dataProvider.getCurrentResults(searchWaveletName).isEmpty());
    assertEquals(0, getPublisherMap(publisher, "waveletVersions").size());
    assertEquals(0, getPublisherMap(publisher, "publishLocks").size());
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentHashMap<String, Object> getPublisherMap(
      SearchWaveletSnapshotPublisher publisher,
      String fieldName) throws Exception {
    java.lang.reflect.Field field =
        SearchWaveletSnapshotPublisher.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (ConcurrentHashMap<String, Object>) field.get(publisher);
  }

  private static void runPublish(
      SearchWaveletSnapshotPublisher publisher,
      SearchResult searchResult,
      AtomicReference<Throwable> failure) {
    try {
      publisher.publishUpdate(USER, QUERY, searchResult);
    } catch (Throwable t) {
      failure.compareAndSet(null, t);
    }
  }

  private static void clearSubscriptions(
      WaveletInfo waveletInfo,
      WaveletName searchWaveletName) throws Exception {
    Method getUserManager =
        WaveletInfo.class.getMethod("getUserManager", ParticipantId.class);
    Object userManager = getUserManager.invoke(waveletInfo, USER);
    Field subscriptionsField = userManager.getClass().getDeclaredField("subscriptions");
    subscriptionsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    ListMultimap<org.waveprotocol.wave.model.id.WaveId, ?> subscriptions =
        (ListMultimap<org.waveprotocol.wave.model.id.WaveId, ?>) subscriptionsField.get(userManager);
    subscriptions.removeAll(searchWaveletName.waveId);
  }

  private static void awaitThreadState(Thread thread, Thread.State expectedState)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      if (thread.getState() == expectedState) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError(
        "Timed out waiting for thread state " + expectedState + ", was " + thread.getState());
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

  private static final class BlockingSearchWaveletDataProvider extends SearchWaveletDataProvider {
    private final String olderWaveId;
    private final String newerWaveId;
    private final CountDownLatch olderUpdateReady = new CountDownLatch(1);
    private final CountDownLatch allowOlderUpdate = new CountDownLatch(1);
    private final CountDownLatch newerUpdateReady = new CountDownLatch(1);

    private BlockingSearchWaveletDataProvider(String olderWaveId, String newerWaveId) {
      this.olderWaveId = olderWaveId;
      this.newerWaveId = newerWaveId;
    }

    @Override
    public void updateCurrentResults(
        WaveletName waveletName,
        List<SearchResultEntry> results,
        int totalCount) {
      String waveId = results.isEmpty() ? "" : results.get(0).getWaveId();
      if (olderWaveId.equals(waveId)) {
        olderUpdateReady.countDown();
        await(allowOlderUpdate);
      }
      if (newerWaveId.equals(waveId)) {
        newerUpdateReady.countDown();
      }
      super.updateCurrentResults(waveletName, results, totalCount);
    }

    private boolean awaitOlderUpdateReady() throws InterruptedException {
      return olderUpdateReady.await(5, TimeUnit.SECONDS);
    }

    private boolean awaitNewerUpdateReady() throws InterruptedException {
      return newerUpdateReady.await(200, TimeUnit.MILLISECONDS);
    }

    private void allowOlderUpdate() {
      allowOlderUpdate.countDown();
    }

    private void await(CountDownLatch latch) {
      try {
        if (!latch.await(5, TimeUnit.SECONDS)) {
          throw new AssertionError("Timed out waiting for publish interleaving");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for publish interleaving", e);
      }
    }
  }
}
