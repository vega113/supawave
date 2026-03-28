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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.wave.api.SearchResult;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.frontend.SearchWaveletDispatcher;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public final class SearchWaveletSnapshotPublisher {

  private static final Log LOG = Log.get(SearchWaveletSnapshotPublisher.class);
  private static final String SEARCH_DOCUMENT_ID = "main";
  public static final int LIVE_SEARCH_NUM_RESULTS = 50;

  private final SearchWaveletDispatcher dispatcher;
  private final SearchWaveletManager waveletManager;
  private final SearchIndexer indexer;
  private final SearchWaveletDataProvider dataProvider;
  private final ConcurrentHashMap<String, AtomicLong> waveletVersions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Object> publishLocks = new ConcurrentHashMap<>();

  @Inject
  public SearchWaveletSnapshotPublisher(
      SearchWaveletDispatcher dispatcher,
      SearchWaveletManager waveletManager,
      SearchIndexer indexer,
      SearchWaveletDataProvider dataProvider) {
    this.dispatcher = dispatcher;
    this.waveletManager = waveletManager;
    this.indexer = indexer;
    this.dataProvider = dataProvider;
  }

  public void publishBootstrap(ParticipantId user, String query, SearchResult searchResult) {
    publish(user, query, searchResult, true);
  }

  public void publishUpdate(ParticipantId user, String query, SearchResult searchResult) {
    publish(user, query, searchResult, false);
  }

  public boolean hasLiveSubscription(ParticipantId user, String query) {
    return dispatcher.hasSubscription(user, waveletManager.computeWaveletName(user, query));
  }

  private void publish(ParticipantId user, String query, SearchResult searchResult,
      boolean forceSnapshot) {
    WaveletName computedWaveletName = waveletManager.computeWaveletName(user, query);
    String searchWaveletKey = computedWaveletName.toString();
    Object publishLock = publishLocks.computeIfAbsent(searchWaveletKey, ignored -> new Object());
    synchronized (publishLock) {
      if (!dispatcher.hasSubscription(user, computedWaveletName)) {
        if (!forceSnapshot) {
          pruneInactiveSubscription(user, query, computedWaveletName, publishLock);
        } else {
          publishLocks.remove(searchWaveletKey, publishLock);
        }
        return;
      }

      List<SearchWaveletDataProvider.SearchResultEntry> newResults =
          convertSearchResult(searchResult);
      int newTotalCount = searchResult != null && searchResult.getTotalResults() >= 0
          ? searchResult.getTotalResults()
          : newResults.size();

      WaveletName searchWaveletName = waveletManager.getOrCreateSearchWavelet(user, query);
      List<SearchWaveletDataProvider.SearchResultEntry> oldResults =
          dataProvider.getCurrentResults(searchWaveletName);
      int oldTotalCount = dataProvider.getCurrentTotal(searchWaveletName);
      SearchWaveletDataProvider.SearchDiff diff =
          dataProvider.computeDiff(oldResults, oldTotalCount, newResults, newTotalCount);

      dataProvider.updateCurrentResults(searchWaveletName, newResults, newTotalCount);
      indexer.registerOrUpdateSubscription(
          user, query, SearchWaveletManager.md5Hex(query), collectWaveIds(newResults));

      if (!forceSnapshot && diff == null) {
        return;
      }

      CommittedWaveletSnapshot snapshot =
          createSnapshot(searchWaveletName, user, newResults, newTotalCount);
      dispatcher.publishSnapshot(user, searchWaveletName, snapshot);
    }
  }

  private void pruneInactiveSubscription(
      ParticipantId user,
      String query,
      WaveletName searchWaveletName,
      Object publishLock) {
    indexer.unregisterSubscription(user, SearchWaveletManager.md5Hex(query));
    waveletManager.removeSearchWavelet(user, query);
    dataProvider.clearResults(searchWaveletName);
    String searchWaveletKey = searchWaveletName.toString();
    waveletVersions.remove(searchWaveletKey);
    publishLocks.remove(searchWaveletKey, publishLock);
  }

  private Set<WaveId> collectWaveIds(List<SearchWaveletDataProvider.SearchResultEntry> results) {
    Set<WaveId> waveIds = ConcurrentHashMap.newKeySet();
    for (SearchWaveletDataProvider.SearchResultEntry entry : results) {
      try {
        waveIds.add(WaveId.deserialise(entry.getWaveId()));
      } catch (Exception e) {
        LOG.warning("Failed to parse search result wave id: " + entry.getWaveId(), e);
      }
    }
    return waveIds;
  }

  private CommittedWaveletSnapshot createSnapshot(WaveletName searchWaveletName, ParticipantId user,
      List<SearchWaveletDataProvider.SearchResultEntry> results, int totalCount) {
    long now = System.currentTimeMillis();
    long version = nextVersion(searchWaveletName);
    HashedVersion hashedVersion = HashedVersion.unsigned(version);
    ObservableWaveletData wavelet =
        WaveletDataUtil.createEmptyWavelet(searchWaveletName, user, hashedVersion, now);
    wavelet.addParticipant(user);
    DocInitialization document =
        DocOpUtil.asInitialization(dataProvider.buildFullRebuildDocOp(results, totalCount));
    wavelet.createDocument(
        SEARCH_DOCUMENT_ID,
        user,
        Collections.singleton(user),
        document,
        now,
        version);
    return new CommittedWaveletSnapshot(wavelet, hashedVersion);
  }

  private long nextVersion(WaveletName searchWaveletName) {
    String key = searchWaveletName.toString();
    AtomicLong counter = waveletVersions.computeIfAbsent(key, ignored -> new AtomicLong(-1));
    return counter.incrementAndGet();
  }

  private List<SearchWaveletDataProvider.SearchResultEntry> convertSearchResult(
      SearchResult searchResult) {
    List<SearchWaveletDataProvider.SearchResultEntry> entries = new ArrayList<>();
    if (searchResult == null || searchResult.getDigests() == null) {
      return entries;
    }
    for (SearchResult.Digest digest : searchResult.getDigests()) {
      List<String> participants = digest.getParticipants();
      String creator = participants != null && !participants.isEmpty() ? participants.get(0) : "";
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
}
