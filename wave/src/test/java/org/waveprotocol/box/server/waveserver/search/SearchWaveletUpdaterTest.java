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
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

import java.lang.reflect.Method;
import java.util.Collections;

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
}
