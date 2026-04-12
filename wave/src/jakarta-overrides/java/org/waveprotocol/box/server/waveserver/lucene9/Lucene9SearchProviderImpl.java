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
package org.waveprotocol.box.server.waveserver.lucene9;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.wave.api.SearchResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.waveprotocol.box.server.waveserver.QueryHelper.InvalidQueryException;
import org.waveprotocol.box.server.waveserver.SearchProvider;
import org.waveprotocol.box.server.waveserver.SimpleSearchProviderImpl;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

@Singleton
public class Lucene9SearchProviderImpl implements SearchProvider {

  private static final Log LOG = Log.get(Lucene9SearchProviderImpl.class);
  private static final int MAX_CANDIDATES = 10000;

  private final SimpleSearchProviderImpl legacySearchProvider;
  private final Lucene9QueryParser queryParser;
  private final Lucene9QueryCompiler queryCompiler;
  private final Lucene9WaveIndexerImpl indexer;

  @Inject
  public Lucene9SearchProviderImpl(SimpleSearchProviderImpl legacySearchProvider,
      Lucene9QueryParser queryParser, Lucene9QueryCompiler queryCompiler,
      Lucene9WaveIndexerImpl indexer) {
    this.legacySearchProvider = legacySearchProvider;
    this.queryParser = queryParser;
    this.queryCompiler = queryCompiler;
    this.indexer = indexer;
  }

  @Override
  public SearchResult search(ParticipantId user, String query, int startAt, int numResults) {
    Lucene9QueryModel model;
    try {
      model = queryParser.parse(query);
    } catch (InvalidQueryException e) {
      LOG.warning("Invalid Lucene9 query. " + e.getMessage());
      return new SearchResult(query);
    }

    SearchResult legacyResult = legacySearchProvider.search(user, model.toLegacyQuery(), 0,
        MAX_CANDIDATES);
    // Task-only queries stay on the legacy path because task semantics are defined against
    // authoritative wave annotations, not Lucene task-assignee terms.
    if (!model.usesLuceneIndex()) {
      return paginate(query, legacyResult.getDigests(), startAt, numResults);
    }

    Set<WaveId> candidateWaveIds;
    try {
      candidateWaveIds = indexer.searchWaveIds(queryCompiler.compile(model, user),
          queryCompiler.compileSort(model), MAX_CANDIDATES);
    } catch (InvalidQueryException e) {
      LOG.warning("Invalid Lucene9 query. " + e.getMessage());
      return new SearchResult(query);
    }

    Map<WaveId, SearchResult.Digest> legacyDigestMap = new LinkedHashMap<>();
    for (SearchResult.Digest digest : legacyResult.getDigests()) {
      legacyDigestMap.put(WaveId.deserialise(digest.getWaveId()), digest);
    }
    List<SearchResult.Digest> filteredDigests = new ArrayList<>();
    for (WaveId waveId : candidateWaveIds) {
      SearchResult.Digest digest = legacyDigestMap.get(waveId);
      if (digest != null) {
        filteredDigests.add(digest);
      }
    }
    return paginate(query, filteredDigests, startAt, numResults);
  }

  private SearchResult paginate(String query, List<SearchResult.Digest> digests, int startAt,
      int numResults) {
    SearchResult result = new SearchResult(query);
    int total = digests.size();
    int safeStartAt = Math.max(0, startAt);
    int safeNumResults = Math.max(0, numResults);
    int start = Math.min(safeStartAt, total);
    int end = (int) Math.min((long) total, (long) start + safeNumResults);
    for (int i = start; i < end; i++) {
      result.addDigest(digests.get(i));
    }
    result.setTotalResults(total);
    return result;
  }
}
