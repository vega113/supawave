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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.wave.api.SearchResult;
import java.util.Collections;
import java.util.LinkedHashSet;
import junit.framework.TestCase;
import org.mockito.Mockito;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.box.server.waveserver.SimpleSearchProviderImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class Lucene9SearchProviderImplTest extends TestCase {

  public void testPureTasksQueryReturnsLegacyResultsWithoutLuceneSearch() {
    SimpleSearchProviderImpl legacySearchProvider = Mockito.mock(SimpleSearchProviderImpl.class);
    Lucene9WaveIndexerImpl indexer = Mockito.mock(Lucene9WaveIndexerImpl.class);
    SearchResult legacyResult = new SearchResult("tasks:all");
    legacyResult.setTotalResults(0);

    when(legacySearchProvider.search(ParticipantId.ofUnsafe("user@example.com"), "tasks:all", 0,
        10000)).thenReturn(legacyResult);

    Lucene9SearchProviderImpl provider = new Lucene9SearchProviderImpl(
        legacySearchProvider,
        new Lucene9QueryParser(),
        new Lucene9QueryCompiler(),
        indexer);

    SearchResult result = provider.search(ParticipantId.ofUnsafe("user@example.com"),
        "tasks:all", 0, 10);

    assertEquals(0, result.getTotalResults());
    verify(indexer, never()).searchWaveIds(any(), any(), anyInt());
  }

  public void testPureTagQueryKeepsLegacyTagFilterForDigestHydration() throws Exception {
    SimpleSearchProviderImpl legacySearchProvider = Mockito.mock(SimpleSearchProviderImpl.class);
    Lucene9WaveIndexerImpl indexer = Mockito.mock(Lucene9WaveIndexerImpl.class);
    SearchResult legacyResult = new SearchResult("tag:project-x");
    legacyResult.addDigest(new SearchResult.Digest(
        "Project X", "snippet", "example.com!w+project-x", Collections.singletonList(
        "user@example.com"), 123L, 123L, 0, 1));
    legacyResult.setTotalResults(1);

    when(legacySearchProvider.search(ParticipantId.ofUnsafe("user@example.com"),
        "tag:project-x", 0, 10000)).thenReturn(legacyResult);
    when(indexer.searchWaveIds(any(), any(), anyInt())).thenReturn(
        new LinkedHashSet<>(Collections.singletonList(WaveId.of("example.com", "w+project-x"))));

    Lucene9SearchProviderImpl provider = new Lucene9SearchProviderImpl(
        legacySearchProvider,
        new Lucene9QueryParser(),
        new Lucene9QueryCompiler(),
        indexer);

    SearchResult result = provider.search(ParticipantId.ofUnsafe("user@example.com"),
        "tag:project-x", 0, 10);

    assertEquals(1, result.getTotalResults());
    assertEquals(1, result.getNumResults());
    assertEquals("example.com!w+project-x", result.getDigests().get(0).getWaveId());
    verify(legacySearchProvider).search(
        ParticipantId.ofUnsafe("user@example.com"), "tag:project-x", 0, 10000);
    verify(indexer).searchWaveIds(any(), any(), eq(10000));
  }
}
