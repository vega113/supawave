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

import java.util.Collections;

public final class SearchWaveletSnapshotPublisherTest extends TestCase {

  private static final IdURIEncoderDecoder URI_CODEC =
      new IdURIEncoderDecoder(new JavaUrlCodec());
  private static final HashedVersionFactory HASH_FACTORY =
      new HashedVersionFactoryImpl(URI_CODEC);
  private static final ParticipantId USER = ParticipantId.ofUnsafe("user@example.com");
  private static final String QUERY = "in:inbox";
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
