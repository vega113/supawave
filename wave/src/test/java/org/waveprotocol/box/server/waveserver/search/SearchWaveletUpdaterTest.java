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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.wave.api.SearchResult;

import junit.framework.TestCase;

import org.waveprotocol.box.server.waveserver.SearchProvider;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.lang.reflect.Method;
import java.util.Collections;

public final class SearchWaveletUpdaterTest extends TestCase {

  public void testExecuteUpdateUsesSearchProviderTotalResults() throws Exception {
    SearchWaveletManager waveletManager = mock(SearchWaveletManager.class);
    SearchIndexer indexer = mock(SearchIndexer.class);
    SearchProvider searchProvider = mock(SearchProvider.class);
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    SearchWaveletUpdater updater =
        new SearchWaveletUpdater(waveletManager, indexer, searchProvider, dataProvider);

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

  public void testExecuteUpdatePreservesUnreadFilterQuery() throws Exception {
    SearchWaveletManager waveletManager = mock(SearchWaveletManager.class);
    SearchIndexer indexer = mock(SearchIndexer.class);
    SearchProvider searchProvider = mock(SearchProvider.class);
    SearchWaveletDataProvider dataProvider = new SearchWaveletDataProvider();
    SearchWaveletUpdater updater =
        new SearchWaveletUpdater(waveletManager, indexer, searchProvider, dataProvider);

    ParticipantId user = ParticipantId.ofUnsafe("alice@example.com");
    String query = "in:inbox unread:true";
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
}
