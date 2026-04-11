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

package org.waveprotocol.box.server.waveserver;

import static org.mockito.Mockito.when;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.collect.Maps;
import com.google.wave.api.SearchResult;
import com.google.wave.api.SearchResult.Digest;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import junit.framework.TestCase;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.common.CoreWaveletOperationSerializer;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.box.server.persistence.memory.MemoryDeltaStore;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.SimpleSearchProviderImpl.WaveSupplementContext;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.impl.AnnotationBoundaryMapImpl;
import org.waveprotocol.wave.model.document.operation.impl.AttributesImpl;
import org.waveprotocol.wave.federation.Proto.ProtocolSignedDelta;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletIdSerializer;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionZeroFactoryImpl;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.box.server.robots.RobotWaveletData;
import org.waveprotocol.box.server.robots.util.RobotsUtil;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class SimpleSearchProviderImplTest extends TestCase {

  private static final String DOMAIN = "example.com";
  private static final WaveId WAVE_ID = WaveId.of(DOMAIN, "abc123");
  private static final WaveletId WAVELET_ID = WaveletId.of(DOMAIN, "conv+root");
  private static final WaveletName WAVELET_NAME = WaveletName.of(WAVE_ID, WAVELET_ID);

  private static final ParticipantId USER1 = ParticipantId.ofUnsafe("user1@" + DOMAIN);
  private static final ParticipantId USER2 = ParticipantId.ofUnsafe("user2@" + DOMAIN);
  private static final ParticipantId SHARED_USER = ParticipantId.ofUnsafe("@" + DOMAIN);

  private static final WaveletOperationContext CONTEXT =
      new WaveletOperationContext(USER1, 1234567890, 1);

  private static final HashedVersionFactory V0_HASH_FACTORY =
      new HashedVersionZeroFactoryImpl(new IdURIEncoderDecoder(new JavaUrlCodec()));

  private final HashMultimap<WaveId,WaveletId> wavesViewUser1 = HashMultimap.create();
  private final HashMultimap<WaveId,WaveletId> wavesViewUser2 = HashMultimap.create();
  private final HashMultimap<WaveId,WaveletId> wavesViewUser3 = HashMultimap.create();

  private final Map<ParticipantId, HashMultimap<WaveId,WaveletId>> wavesViews = Maps.newHashMap();

  /** Sorts search result in ascending order by LMT. */
  static final Comparator<SearchResult.Digest> ASCENDING_DATE_COMPARATOR =
      new Comparator<SearchResult.Digest>() {
        @Override
        public int compare(SearchResult.Digest arg0, SearchResult.Digest arg1) {
          long lmt0 = arg0.getLastModified();
          long lmt1 = arg1.getLastModified();
          return Long.signum(lmt0 - lmt1);
        }
      };

  /** Sorts search result in descending order by LMT. */
  static final Comparator<SearchResult.Digest> DESCENDING_DATE_COMPARATOR =
      new Comparator<SearchResult.Digest>() {
        @Override
        public int compare(SearchResult.Digest arg0, SearchResult.Digest arg1) {
          return -ASCENDING_DATE_COMPARATOR.compare(arg0, arg1);
        }
      };

  /** Sorts search result in ascending order by creation time. */
  static final Comparator<SearchResult.Digest> ASC_CREATED_COMPARATOR =
      new Comparator<SearchResult.Digest>() {
        @Override
        public int compare(SearchResult.Digest arg0, SearchResult.Digest arg1) {
          long time0 = arg0.getCreated();
          long time1 = arg1.getCreated();
          return Long.signum(time0 - time1);
        }
      };

  /** Sorts search result in descending order by creation time. */
  static final Comparator<SearchResult.Digest> DESC_CREATED_COMPARATOR =
      new Comparator<SearchResult.Digest>() {
        @Override
        public int compare(SearchResult.Digest arg0, SearchResult.Digest arg1) {
          return -ASC_CREATED_COMPARATOR.compare(arg0, arg1);
        }
      };

  /** Sorts search result in ascending order by author. */
  static final Comparator<SearchResult.Digest> ASC_CREATOR_COMPARATOR =
      new Comparator<SearchResult.Digest>() {
        @Override
        public int compare(SearchResult.Digest arg0, SearchResult.Digest arg1) {
          ParticipantId author0 = computeAuthor(arg0);
          ParticipantId author1 = computeAuthor(arg1);
          return author0.compareTo(author1);
        }

        private ParticipantId computeAuthor(SearchResult.Digest digest) {
          ParticipantId author;
          author = ParticipantId.ofUnsafe(digest.getParticipants().get(0));
          assert author != null : "Cannot find author for the wave: " + digest.getWaveId();
          return author;
        }
      };

  /** Sorts search result in descending order by author. */
  static final Comparator<SearchResult.Digest> DESC_CREATOR_COMPARATOR =
      new Comparator<SearchResult.Digest>() {
        @Override
        public int compare(SearchResult.Digest arg0, SearchResult.Digest arg1) {
          return -ASC_CREATOR_COMPARATOR.compare(arg0, arg1);
        }
      };

  private WaveletOperation addParticipantToWavelet(ParticipantId user, WaveletName name) {
    addWaveletToUserView(name, user);
    return new AddParticipant(CONTEXT, user);
  }

  @Mock private IdGenerator idGenerator;
  @Mock private WaveletNotificationDispatcher notifiee;
  @Mock private RemoteWaveletContainer.Factory remoteWaveletContainerFactory;
  @Mock private PerUserWaveViewProvider waveViewProvider;

  private SearchProvider searchProvider;
  private MemoryPerUserWaveViewHandlerImpl runtimeWaveViewProvider;
  private SearchProvider runtimeSearchProvider;
  private WaveMap waveMap;

  @Override
  protected void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    wavesViews.put(USER1, wavesViewUser1);
    wavesViews.put(USER2, wavesViewUser2);
    wavesViews.put(SHARED_USER, wavesViewUser3);

    when(waveViewProvider.retrievePerUserWaveView(USER1)).thenReturn(wavesViewUser1);
    when(waveViewProvider.retrievePerUserWaveView(USER2)).thenReturn(wavesViewUser2);
    when(waveViewProvider.retrievePerUserWaveView(SHARED_USER)).thenReturn(wavesViewUser3);

    ConversationUtil conversationUtil = new ConversationUtil(idGenerator);
    WaveDigester digester = new WaveDigester(conversationUtil);

    final MemoryDeltaStore deltaStore = new MemoryDeltaStore();
    final DeltaAndSnapshotStore inMemoryWaveletStore =
        new DeltaStoreBasedSnapshotStore(deltaStore, null);
    final Executor persistExecutor = MoreExecutors.directExecutor();
    final Executor storageContinuationExecutor = MoreExecutors.directExecutor();
    final Executor lookupExecutor = MoreExecutors.directExecutor();
    LocalWaveletContainer.Factory localWaveletContainerFactory =
        new LocalWaveletContainer.Factory() {
          @Override
          public LocalWaveletContainer create(WaveletNotificationSubscriber notifiee,
              WaveletName waveletName, String domain) {
            WaveletState waveletState;
            try {
              waveletState = DeltaStoreBasedWaveletState.create(deltaStore.open(waveletName),
                  persistExecutor);
            } catch (PersistenceException e) {
              throw new RuntimeException(e);
            }
            return new LocalWaveletContainerImpl(waveletName, notifiee,
                Futures.immediateFuture(waveletState), DOMAIN, storageContinuationExecutor);
          }
        };

    Config config = ConfigFactory.parseMap(ImmutableMap.<String, Object>of(
      "core.wave_cache_size", 1000,
      "core.wave_cache_expire", "60m")
    );

    waveMap =
        new WaveMap(inMemoryWaveletStore, notifiee, localWaveletContainerFactory,
            remoteWaveletContainerFactory, DOMAIN, config, lookupExecutor);

    searchProvider = new SimpleSearchProviderImpl(DOMAIN, digester, waveMap, waveViewProvider);
    runtimeWaveViewProvider = new MemoryPerUserWaveViewHandlerImpl(waveMap);
    runtimeSearchProvider =
        new SimpleSearchProviderImpl(DOMAIN, digester, waveMap, runtimeWaveViewProvider);
  }

  @Override
  protected void tearDown() throws Exception {
    wavesViews.clear();
  }

  public void testSearchEmptyInboxReturnsNothing() {
    SearchResult results = searchProvider.search(USER1, "in:inbox", 0, 20);

    assertEquals(0, results.getNumResults());
  }

  public void testSearchInboxReturnsWaveWithExplicitParticipant() throws Exception {
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER2, WAVELET_NAME));

    SearchResult results = searchProvider.search(USER2, "in:inbox", 0, 20);

    assertEquals(1, results.getNumResults());
    assertEquals(WAVELET_NAME.waveId.serialise(), results.getDigests().get(0).getWaveId());
  }

  public void testSearchInboxExcludesArchivedWaveWithoutManifest() throws Exception {
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER1, WAVELET_NAME));
    archiveWaveForUser(WAVELET_NAME, USER1);

    SearchResult inboxResults = searchProvider.search(USER1, "in:inbox", 0, 20);
    SearchResult archiveResults = searchProvider.search(USER1, "in:archive", 0, 20);

    assertEquals(0, inboxResults.getNumResults());
    assertEquals(1, archiveResults.getNumResults());
    assertEquals(
        WAVELET_NAME.waveId.serialise(), archiveResults.getDigests().get(0).getWaveId());
  }

  public void testSearchArchiveKeepsMutedWave() throws Exception {
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER1, WAVELET_NAME));
    muteWaveForUser(WAVELET_NAME, USER1);

    SearchResult inboxResults = searchProvider.search(USER1, "in:inbox", 0, 20);
    SearchResult archiveResults = searchProvider.search(USER1, "in:archive", 0, 20);

    assertEquals(0, inboxResults.getNumResults());
    assertEquals(1, archiveResults.getNumResults());
    assertEquals(
        WAVELET_NAME.waveId.serialise(), archiveResults.getDigests().get(0).getWaveId());
  }

  public void testSearchInboxIncludesWaveAfterArchiveStateCleared() throws Exception {
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER1, WAVELET_NAME));
    archiveWaveForUser(WAVELET_NAME, USER1);
    clearArchiveStateForUser(WAVELET_NAME, USER1);

    SearchResult inboxResults = searchProvider.search(USER1, "in:inbox", 0, 20);
    SearchResult archiveResults = searchProvider.search(USER1, "in:archive", 0, 20);

    assertEquals(1, inboxResults.getNumResults());
    assertEquals(WAVELET_NAME.waveId.serialise(), inboxResults.getDigests().get(0).getWaveId());
    assertEquals(0, archiveResults.getNumResults());
  }

  public void testSearchInboxIncludesWaveAfterLegacyClearedStateWithoutAttr() throws Exception {
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER1, WAVELET_NAME));
    archiveWaveForUser(WAVELET_NAME, USER1);
    clearArchiveStateForUserWithoutAttr(WAVELET_NAME, USER1);

    SearchResult inboxResults = searchProvider.search(USER1, "in:inbox", 0, 20);
    SearchResult archiveResults = searchProvider.search(USER1, "in:archive", 0, 20);

    assertEquals(1, inboxResults.getNumResults());
    assertEquals(WAVELET_NAME.waveId.serialise(), inboxResults.getDigests().get(0).getWaveId());
    assertEquals(0, archiveResults.getNumResults());
  }

  public void testSearchInboxExcludesArchivedWaveWhenArchiveEntriesRepeatLowerVersion()
      throws Exception {
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER1, WAVELET_NAME));

    long currentVersion = waveMap.getOrCreateLocalWavelet(WAVELET_NAME).copyWaveletData().getVersion();
    archiveWaveForUserWithVersions(WAVELET_NAME, USER1, currentVersion, currentVersion - 1L);

    SearchResult inboxResults = searchProvider.search(USER1, "in:inbox", 0, 20);
    SearchResult archiveResults = searchProvider.search(USER1, "in:archive", 0, 20);

    assertEquals(0, inboxResults.getNumResults());
    assertEquals(1, archiveResults.getNumResults());
    assertEquals(
        WAVELET_NAME.waveId.serialise(), archiveResults.getDigests().get(0).getWaveId());
  }

  /**
   * Verifies that the robot framework path (RobotWaveletData + WaveletBasedSupplement)
   * used by FolderServlet actually produces deltas for pin operations.
   */
  public void testRobotFrameworkPinProducesDeltas() throws Exception {
    WaveletName udwName = userDataWaveletName(WAVE_ID, USER1);
    RobotWaveletData robotWavelet =
        RobotsUtil.createEmptyRobotWavelet(USER1, udwName);
    OpBasedWavelet udw = robotWavelet.getOpBasedWavelet(USER1);

    PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);
    udwState.addFolder(SupplementedWaveImpl.PINNED_FOLDER);

    java.util.List<WaveletDelta> deltas = robotWavelet.getDeltas();
    assertFalse("Expected non-empty deltas from pin operation, but got none", deltas.isEmpty());
  }

  /**
   * Verifies that the robot framework path produces deltas for archive operations.
   */
  public void testRobotFrameworkArchiveProducesDeltas() throws Exception {
    WaveletName udwName = userDataWaveletName(WAVE_ID, USER1);
    RobotWaveletData robotWavelet =
        RobotsUtil.createEmptyRobotWavelet(USER1, udwName);
    OpBasedWavelet udw = robotWavelet.getOpBasedWavelet(USER1);

    PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);
    udwState.archiveAtVersion(WAVELET_ID, 5);

    java.util.List<WaveletDelta> deltas = robotWavelet.getDeltas();
    assertFalse("Expected non-empty deltas from archive operation, but got none", deltas.isEmpty());
  }

  /**
   * End-to-end test: pin via robot framework (like FolderServlet), then search for in:pinned.
   * This exercises the full path: create deltas via WaveletBasedSupplement → serialize →
   * submit to WaveMap → search reads the UDW.
   */
  public void testPinViaRobotFrameworkThenSearchFindsIt() throws Exception {
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER1, WAVELET_NAME));

    WaveletName udwName = userDataWaveletName(WAVE_ID, USER1);
    RobotWaveletData robotWavelet = RobotsUtil.createEmptyRobotWavelet(USER1, udwName);
    OpBasedWavelet udw = robotWavelet.getOpBasedWavelet(USER1);

    PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);
    udwState.addFolder(SupplementedWaveImpl.PINNED_FOLDER);

    java.util.List<WaveletDelta> deltas = robotWavelet.getDeltas();
    assertFalse("Pin should produce deltas", deltas.isEmpty());

    LocalWaveletContainer pinUdwContainer = waveMap.getOrCreateLocalWavelet(udwName);
    for (WaveletDelta delta : deltas) {
      ProtocolWaveletDelta protoDelta = CoreWaveletOperationSerializer.serialize(delta);
      ProtocolSignedDelta signedDelta =
          ProtocolSignedDelta.newBuilder().setDelta(protoDelta.toByteString()).build();
      pinUdwContainer.submitRequest(udwName, signedDelta);
    }

    addWaveletToUserView(udwName, USER1);

    SearchResult pinnedResults = searchProvider.search(USER1, "in:pinned", 0, 20);
    assertEquals("Expected 1 pinned wave", 1, pinnedResults.getNumResults());
    assertEquals(WAVE_ID.serialise(), pinnedResults.getDigests().get(0).getWaveId());
  }

  /**
   * End-to-end test: archive via robot framework (like FolderServlet), then search.
   */
  public void testArchiveViaRobotFrameworkThenSearchFindsIt() throws Exception {
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER1, WAVELET_NAME));

    WaveletName udwName = userDataWaveletName(WAVE_ID, USER1);
    long convVersion = waveMap.getOrCreateLocalWavelet(WAVELET_NAME).copyWaveletData().getVersion();

    RobotWaveletData robotWavelet = RobotsUtil.createEmptyRobotWavelet(USER1, udwName);
    OpBasedWavelet udw = robotWavelet.getOpBasedWavelet(USER1);

    PrimitiveSupplement udwState = WaveletBasedSupplement.create(udw);
    udwState.archiveAtVersion(WAVELET_ID, Math.toIntExact(convVersion));

    java.util.List<WaveletDelta> deltas = robotWavelet.getDeltas();
    assertFalse("Archive should produce deltas", deltas.isEmpty());

    LocalWaveletContainer archiveUdwContainer = waveMap.getOrCreateLocalWavelet(udwName);
    for (WaveletDelta delta : deltas) {
      ProtocolWaveletDelta protoDelta = CoreWaveletOperationSerializer.serialize(delta);
      ProtocolSignedDelta signedDelta =
          ProtocolSignedDelta.newBuilder().setDelta(protoDelta.toByteString()).build();
      archiveUdwContainer.submitRequest(udwName, signedDelta);
    }

    addWaveletToUserView(udwName, USER1);

    SearchResult inboxResults = searchProvider.search(USER1, "in:inbox", 0, 20);
    SearchResult archiveResults = searchProvider.search(USER1, "in:archive", 0, 20);
    assertEquals("Wave should not be in inbox", 0, inboxResults.getNumResults());
    assertEquals("Wave should be in archive", 1, archiveResults.getNumResults());
    assertEquals(WAVE_ID.serialise(), archiveResults.getDigests().get(0).getWaveId());
  }

  public void testSearchInboxDoesNotReturnWaveWithoutUser() throws Exception {
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER1, WAVELET_NAME));

    SearchResult results = searchProvider.search(USER2, "in:inbox", 0, 20);
    assertEquals(0, results.getNumResults());
  }

  public void testSearchWaveReturnsWaveWithImplicitParticipant() throws Exception {
    ParticipantId sharedDomainParticipantId =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(DOMAIN);
    WaveletName waveletName =
      WaveletName.of(WaveId.of(DOMAIN, String.valueOf(1)), WAVELET_ID);
    // Implicit participant in this wave.
    submitDeltaToNewWavelet(waveletName, USER1,
        addParticipantToWavelet(sharedDomainParticipantId, waveletName));
    waveletName = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(2)), WAVELET_ID);
    // Explicit participant in this wave.
    submitDeltaToNewWavelet(waveletName, USER1, addParticipantToWavelet(USER2, waveletName));

    SearchResult results = searchProvider.search(USER2, "", 0, 20);
    // Should return both waves.
    assertEquals(2, results.getNumResults());
  }

  public void testSearchInAllReturnsWaveWithImplicitParticipant() throws Exception {
    ParticipantId sharedDomainParticipantId =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(DOMAIN);
    WaveletName waveletName =
      WaveletName.of(WaveId.of(DOMAIN, String.valueOf(1)), WAVELET_ID);
    // Implicit participant in this wave.
    submitDeltaToNewWavelet(waveletName, USER1,
        addParticipantToWavelet(sharedDomainParticipantId, waveletName));
    waveletName = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(2)), WAVELET_ID);
    // Explicit participant in this wave.
    submitDeltaToNewWavelet(waveletName, USER1, addParticipantToWavelet(USER2, waveletName));

    // "in:all" should behave the same as empty query - return both waves.
    SearchResult results = searchProvider.search(USER2, "in:all", 0, 20);
    assertEquals(2, results.getNumResults());
  }

  public void testSearchAllReturnsWavesOnlyWithSharedDomainUser() throws Exception {
    WaveletName waveletName =
      WaveletName.of(WaveId.of(DOMAIN, String.valueOf(1)), WAVELET_ID);
    submitDeltaToNewWavelet(waveletName, USER1, addParticipantToWavelet(USER1, waveletName));
    waveletName = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(2)), WAVELET_ID);
    submitDeltaToNewWavelet(waveletName, USER1, addParticipantToWavelet(USER2, waveletName));

    SearchResult results = searchProvider.search(USER2, "", 0, 20);
    assertEquals(1, results.getNumResults());
  }

  public void testSearchLimitEnforced() throws Exception {
    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, "w" + i), WAVELET_ID);
      submitDeltaToNewWavelet(name, USER1, addParticipantToWavelet(USER1, name));
    }

    SearchResult results = searchProvider.search(USER1, "in:inbox", 0, 5);

    assertEquals(5, results.getNumResults());
  }

  public void testSearchIndexWorks() throws Exception {
    // For this test, we'll create 10 waves with wave ids "0", "1", ... "9" and then run 10
    // searches using offsets 0..9. The waves we get back can be in any order, but we must get
    // all 10 of the waves back exactly once each from the search query.

    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(i)), WAVELET_ID);
      submitDeltaToNewWavelet(name, USER1, addParticipantToWavelet(USER1, name));
    }

    // The number of times we see each wave when we search
    int[] saw_wave = new int[10];

    for (int i = 0; i < 10; i++) {
      SearchResult results = searchProvider.search(USER1, "in:inbox", i, 1);
      assertEquals(1, results.getNumResults());
      WaveId waveId = WaveId.deserialise(results.getDigests().get(0).getWaveId());
      int index = Integer.parseInt(waveId.getId());
      saw_wave[index]++;
    }

    for (int i = 0; i < 10; i++) {
      // Each wave should appear exactly once in the results
      assertEquals(1, saw_wave[i]);
    }
  }

  public void testSearchOrderByAscWorks() throws Exception {
    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(i)), WAVELET_ID);
      submitDeltaToNewWavelet(name, USER1, addParticipantToWavelet(USER1, name));
    }
    SearchResult results = searchProvider.search(USER1, "in:inbox orderby:dateasc", 0, 10);
    Ordering<SearchResult.Digest> ascOrdering = Ordering.from(ASCENDING_DATE_COMPARATOR);
    assertTrue(ascOrdering.isOrdered(results.getDigests()));
  }

  public void testSearchOrderByDescWorks() throws Exception {
    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(i)), WAVELET_ID);
      submitDeltaToNewWavelet(name, USER1, addParticipantToWavelet(USER1, name));
    }
    SearchResult results = searchProvider.search(USER1, "in:inbox orderby:datedesc", 0, 10);
    Ordering<SearchResult.Digest> descOrdering = Ordering.from(DESCENDING_DATE_COMPARATOR);
    assertTrue(descOrdering.isOrdered(results.getDigests()));
  }

  public void testSearchOrderByDateUsesLatestBlipActivity() throws Exception {
    WaveletName olderWave = WaveletName.of(WaveId.of(DOMAIN, "older"), WAVELET_ID);
    WaveletName newerWave = WaveletName.of(WaveId.of(DOMAIN, "newer"), WAVELET_ID);

    submitDeltaToNewWavelet(olderWave, USER1, addParticipantToWavelet(USER1, olderWave));
    appendBlipToWavelet(olderWave, USER1, "b+older-1", "older wave");

    waitForDistinctTimestamp();

    submitDeltaToNewWavelet(newerWave, USER1, addParticipantToWavelet(USER1, newerWave));
    appendBlipToWavelet(newerWave, USER1, "b+newer-1", "newer wave");

    waitForDistinctTimestamp();

    appendBlipToWavelet(olderWave, USER1, "b+older-2", "latest activity on older wave");

    SearchResult descResults = searchProvider.search(USER1, "in:inbox orderby:datedesc", 0, 10);
    assertEquals("older",
        WaveId.deserialise(descResults.getDigests().get(0).getWaveId()).getId());

    SearchResult ascResults = searchProvider.search(USER1, "in:inbox orderby:dateasc", 0, 10);
    assertEquals("newer", WaveId.deserialise(ascResults.getDigests().get(0).getWaveId()).getId());
  }

  public void testSearchOrderByDateUsesLatestConversationalWaveletActivity() throws Exception {
    WaveletName olderRoot = WaveletName.of(WaveId.of(DOMAIN, "older-conv"), WAVELET_ID);
    WaveletName newerRoot = WaveletName.of(WaveId.of(DOMAIN, "newer-conv"), WAVELET_ID);
    WaveletName olderReply =
        WaveletName.of(olderRoot.waveId, WaveletId.of(DOMAIN, "conv+reply"));

    submitDeltaToNewWavelet(olderRoot, USER1, addParticipantToWavelet(USER1, olderRoot));
    appendBlipToWavelet(olderRoot, USER1, "b+older-root", "older root");

    waitForDistinctTimestamp();

    submitDeltaToNewWavelet(newerRoot, USER1, addParticipantToWavelet(USER1, newerRoot));
    appendBlipToWavelet(newerRoot, USER1, "b+newer-root", "newer root");

    waitForDistinctTimestamp();

    submitDeltaToNewWaveletWithoutView(olderReply, USER1, new AddParticipant(CONTEXT, USER1));
    appendBlipToWavelet(olderReply, USER1, "b+older-reply", "latest reply activity");

    SearchResult descResults = searchProvider.search(USER1, "in:inbox orderby:datedesc", 0, 10);
    assertEquals("older-conv",
        WaveId.deserialise(descResults.getDigests().get(0).getWaveId()).getId());

    SearchResult ascResults = searchProvider.search(USER1, "in:inbox orderby:dateasc", 0, 10);
    assertEquals("newer-conv",
        WaveId.deserialise(ascResults.getDigests().get(0).getWaveId()).getId());
  }

  public void testSearchOrderByCreatedAscWorks() throws Exception {
    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(i)), WAVELET_ID);
      submitDeltaToNewWavelet(name, USER1, addParticipantToWavelet(USER1, name));
    }
    SearchResult results = searchProvider.search(USER1, "in:inbox orderby:createdasc", 0, 10);
    Ordering<SearchResult.Digest> ascOrdering = Ordering.from(ASC_CREATED_COMPARATOR);
    assertTrue(ascOrdering.isOrdered(results.getDigests()));
  }

  public void testSearchOrderByCreatedDescWorks() throws Exception {
    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(i)), WAVELET_ID);
      submitDeltaToNewWavelet(name, USER1, addParticipantToWavelet(USER1, name));
    }
    SearchResult results = searchProvider.search(USER1, "in:inbox orderby:createddesc", 0, 10);
    Ordering<SearchResult.Digest> descOrdering = Ordering.from(DESC_CREATED_COMPARATOR);
    assertTrue(descOrdering.isOrdered(results.getDigests()));
  }

  public void testSearchOrderByAuthorAscWithCompundingWorks() throws Exception {
    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(i)), WAVELET_ID);
      // Add USER2 to two waves.
      if (i == 1 || i == 2) {
        WaveletOperation op1 = addParticipantToWavelet(USER1, name);
        WaveletOperation op2 = addParticipantToWavelet(USER2, name);
        submitDeltaToNewWavelet(name, USER1, op1, op2);
      } else {
        submitDeltaToNewWavelet(name, USER2, addParticipantToWavelet(USER2, name));
      }
    }
    SearchResult resultsAsc =
        searchProvider.search(USER2, "in:inbox orderby:creatorasc orderby:createddesc", 0, 10);
    assertEquals(10, resultsAsc.getNumResults());
    Ordering<SearchResult.Digest> ascAuthorOrdering = Ordering.from(ASC_CREATOR_COMPARATOR);
    assertTrue(ascAuthorOrdering.isOrdered(resultsAsc.getDigests()));
    Ordering<SearchResult.Digest> descCreatedOrdering = Ordering.from(DESC_CREATED_COMPARATOR);
    // The whole list should not be ordered by creation time.
    assertFalse(descCreatedOrdering.isOrdered(resultsAsc.getDigests()));
    // Each sublist should be ordered by creation time.
    assertTrue(descCreatedOrdering.isOrdered(Lists.newArrayList(resultsAsc.getDigests()).subList(0,
        2)));
    assertTrue(descCreatedOrdering.isOrdered(Lists.newArrayList(resultsAsc.getDigests()).subList(2,
        10)));
  }

  public void testSearchOrderByAuthorDescWorks() throws Exception {
    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(i)), WAVELET_ID);
      // Add USER2 to two waves.
      if (i == 1 || i == 2) {
        WaveletOperation op1 = addParticipantToWavelet(USER1, name);
        WaveletOperation op2 = addParticipantToWavelet(USER2, name);
        submitDeltaToNewWavelet(name, USER1, op1, op2);
      } else {
        submitDeltaToNewWavelet(name, USER2, addParticipantToWavelet(USER2, name));
      }
    }
    SearchResult resultsAsc =
        searchProvider.search(USER2, "in:inbox orderby:creatordesc", 0, 10);
    assertEquals(10, resultsAsc.getNumResults());
    Ordering<SearchResult.Digest> descAuthorOrdering = Ordering.from(DESC_CREATOR_COMPARATOR);
    assertTrue(descAuthorOrdering.isOrdered(resultsAsc.getDigests()));
  }

  public void testSearchFilterByWithWorks() throws Exception {
    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(i)), WAVELET_ID);
      // Add USER2 to two waves.
      if (i == 1 || i == 2) {
        WaveletOperation op1 = addParticipantToWavelet(USER1, name);
        WaveletOperation op2 = addParticipantToWavelet(USER2, name);
        submitDeltaToNewWavelet(name, USER1, op1, op2);
      } else {
        submitDeltaToNewWavelet(name, USER1, addParticipantToWavelet(USER1, name));
      }
    }
    SearchResult results =
        searchProvider.search(USER1, "in:inbox with:" + USER2.getAddress(), 0, 10);
    assertEquals(2, results.getNumResults());
    results = searchProvider.search(USER1, "in:inbox with:" + USER1.getAddress(), 0, 10);
    assertEquals(10, results.getNumResults());
  }

  /**
   * If query contains invalid search param - it should return empty result.
   */
  public void testInvalidWithSearchParam() throws Exception {
    WaveletName name = WaveletName.of(WAVE_ID, WAVELET_ID);
    submitDeltaToNewWavelet(name, USER1, addParticipantToWavelet(USER1, name));
    SearchResult results =
        searchProvider.search(USER1, "in:inbox with@^^^@:" + USER1.getAddress(), 0, 10);
    assertEquals(0, results.getNumResults());
  }

  public void testInvalidOrderByParam() throws Exception {
    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(i)), WAVELET_ID);
      submitDeltaToNewWavelet(name, USER1, addParticipantToWavelet(USER1, name));
    }
    SearchResult results =
        searchProvider.search(USER1, "in:inbox orderby:createddescCCC", 0, 10);
    assertEquals(0, results.getNumResults());
  }

  public void testSearchFilterByCreatorWorks() throws Exception {
    for (int i = 0; i < 10; i++) {
      WaveletName name = WaveletName.of(WaveId.of(DOMAIN, String.valueOf(i)), WAVELET_ID);
      // Add USER2 to two waves as creator.
      if (i == 1 || i == 2) {
        WaveletOperation op1 = addParticipantToWavelet(USER1, name);
        WaveletOperation op2 = addParticipantToWavelet(USER2, name);
        submitDeltaToNewWavelet(name, USER2, op1, op2);
      } else {
        submitDeltaToNewWavelet(name, USER1, addParticipantToWavelet(USER1, name));
      }
    }
    SearchResult results =
        searchProvider.search(USER1, "in:inbox creator:" + USER2.getAddress(), 0, 10);
    assertEquals(2, results.getNumResults());
    results = searchProvider.search(USER1, "in:inbox creator:" + USER1.getAddress(), 0, 10);
    assertEquals(8, results.getNumResults());
    results =
        searchProvider.search(USER1,
            "in:inbox creator:" + USER1.getAddress() + " creator:" + USER2.getAddress(), 0, 10);
    assertEquals(0, results.getNumResults());
  }

  public void testSearchFilterByImplicitContentWorks() throws Exception {
    WaveletName matchingWave = WaveletName.of(WaveId.of(DOMAIN, "matching"), WAVELET_ID);
    WaveletName partialWave = WaveletName.of(WaveId.of(DOMAIN, "partial"), WAVELET_ID);

    submitDeltaToNewWavelet(matchingWave, USER1, addParticipantToWavelet(USER1, matchingWave));
    appendBlipToWavelet(matchingWave, USER1, "b+matching", "meeting notes and action items");

    submitDeltaToNewWavelet(partialWave, USER1, addParticipantToWavelet(USER1, partialWave));
    appendBlipToWavelet(partialWave, USER1, "b+partial", "meeting recap only");

    SearchResult results = searchProvider.search(USER1, "in:inbox meeting notes", 0, 10);

    assertEquals(1, results.getNumResults());
    assertEquals("matching",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
  }

  public void testSearchFilterByUnreadWorks() throws Exception {
    WaveletName unreadWave = WaveletName.of(WaveId.of(DOMAIN, "unread"), WAVELET_ID);
    WaveletName readWave = WaveletName.of(WaveId.of(DOMAIN, "read"), WAVELET_ID);

    submitDeltaToNewWavelet(unreadWave, USER1, addParticipantToWavelet(USER1, unreadWave));
    appendBlipToWavelet(unreadWave, USER1, "b+unread", "project update");

    submitDeltaToNewWavelet(readWave, USER1, addParticipantToWavelet(USER1, readWave));
    appendBlipToWavelet(readWave, USER1, "b+read", "project update");

    SearchProvider unreadFilterProvider =
        newUnreadAwareSearchProvider(ImmutableMap.of("read", 0, "unread", 2));

    SearchResult results = unreadFilterProvider.search(USER1, "in:inbox unread:true", 0, 10);

    assertEquals(1, results.getNumResults());
    assertEquals("unread",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
  }

  public void testSearchFilterByUnreadAppliesBeforePagination() throws Exception {
    WaveletName readFirst = WaveletName.of(WaveId.of(DOMAIN, "read-first"), WAVELET_ID);
    WaveletName unreadLater = WaveletName.of(WaveId.of(DOMAIN, "unread-later"), WAVELET_ID);

    submitDeltaToNewWavelet(readFirst, USER1, addParticipantToWavelet(USER1, readFirst));
    appendBlipToWavelet(readFirst, USER1, "b+read-first", "project update");

    waitForDistinctTimestamp();

    submitDeltaToNewWavelet(unreadLater, USER1, addParticipantToWavelet(USER1, unreadLater));
    appendBlipToWavelet(unreadLater, USER1, "b+unread-later", "project update");

    SearchProvider unreadFilterProvider =
        newUnreadAwareSearchProvider(ImmutableMap.of("read-first", 0, "unread-later", 2));

    SearchResult firstPage = unreadFilterProvider.search(
        USER1, "in:inbox unread:true orderby:createdasc", 0, 1);
    assertEquals(1, firstPage.getNumResults());
    assertEquals("unread-later",
        WaveId.deserialise(firstPage.getDigests().get(0).getWaveId()).getId());
    assertEquals(1, firstPage.getTotalResults());

    SearchResult secondPage = unreadFilterProvider.search(
        USER1, "in:inbox unread:true orderby:createdasc", 1, 1);
    assertEquals(0, secondPage.getNumResults());
    assertEquals(1, secondPage.getTotalResults());
  }

  public void testSearchFilterByUnreadCombinesWithContent() throws Exception {
    WaveletName unreadWave = WaveletName.of(WaveId.of(DOMAIN, "unread-match"), WAVELET_ID);
    WaveletName readWave = WaveletName.of(WaveId.of(DOMAIN, "read-match"), WAVELET_ID);

    submitDeltaToNewWavelet(unreadWave, USER1, addParticipantToWavelet(USER1, unreadWave));
    appendBlipToWavelet(unreadWave, USER1, "b+unread", "sprint retro");

    submitDeltaToNewWavelet(readWave, USER1, addParticipantToWavelet(USER1, readWave));
    appendBlipToWavelet(readWave, USER1, "b+read", "sprint retro");

    SearchProvider unreadFilterProvider =
        newUnreadAwareSearchProvider(ImmutableMap.of("read-match", 0, "unread-match", 1));

    SearchResult results =
        unreadFilterProvider.search(USER1, "in:inbox unread:true sprint", 0, 10);

    assertEquals(1, results.getNumResults());
    assertEquals("unread-match",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
  }

  public void testSearchUnreadTrueReturnsOnlyUnreadWaves() throws Exception {
    WaveletName unreadWave = WaveletName.of(WaveId.of(DOMAIN, "unread"), WAVELET_ID);
    WaveletName readWave = WaveletName.of(WaveId.of(DOMAIN, "read"), WAVELET_ID);

    submitDeltaToNewWavelet(unreadWave, USER1, addParticipantToWavelet(USER1, unreadWave));
    appendBlipToWavelet(unreadWave, USER1, "b+unread", "project update");

    submitDeltaToNewWavelet(readWave, USER1, addParticipantToWavelet(USER1, readWave));
    appendBlipToWavelet(readWave, USER1, "b+read", "project update");

    SearchProvider unreadFilterProvider =
        newUnreadAwareSearchProvider(ImmutableMap.of("read", 0, "unread", 2));

    SearchResult results = unreadFilterProvider.search(USER1, "unread:true", 0, 10);

    assertEquals(1, results.getNumResults());
    assertEquals("unread",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
  }

  public void testSearchUnreadTrueReturnsNothingWhenAllWavesAreRead() throws Exception {
    WaveletName readWave = WaveletName.of(WaveId.of(DOMAIN, "read-only"), WAVELET_ID);

    submitDeltaToNewWavelet(readWave, USER1, addParticipantToWavelet(USER1, readWave));
    appendBlipToWavelet(readWave, USER1, "b+read", "project update");

    SearchProvider unreadFilterProvider =
        newUnreadAwareSearchProvider(ImmutableMap.of("read-only", 0));

    SearchResult results = unreadFilterProvider.search(USER1, "unread:true", 0, 10);

    assertEquals(0, results.getNumResults());
  }

  public void testSearchFilterByTagReturnsOnlyTaggedWaves() throws Exception {
    WaveletName taggedWave = WaveletName.of(WaveId.of(DOMAIN, "tagged"), WAVELET_ID);
    WaveletName untaggedWave = WaveletName.of(WaveId.of(DOMAIN, "untagged"), WAVELET_ID);

    submitDeltaToNewWavelet(taggedWave, USER1, addParticipantToWavelet(USER1, taggedWave));
    addTagToWavelet(taggedWave, USER1, "work");

    submitDeltaToNewWavelet(untaggedWave, USER1, addParticipantToWavelet(USER1, untaggedWave));

    SearchResult results = searchProvider.search(USER1, "tag:work", 0, 10);

    assertEquals(1, results.getNumResults());
    assertEquals("tagged",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
  }

  public void testRuntimeSearchFreshnessAcrossReloadCooldownForParticipantAndTagQueries()
      throws Exception {
    WaveletName wave = WaveletName.of(WaveId.of(DOMAIN, "freshness-runtime"), WAVELET_ID);

    assertEquals(0, runtimeSearchProvider.search(USER1, "in:inbox", 0, 20).getNumResults());

    submitDeltaToNewWaveletWithoutView(wave, USER1, runtimeAddParticipant(USER1));
    runtimeWaveViewProvider.onParticipantAdded(wave, USER1).get();

    SearchResult aliceInbox = runtimeSearchProvider.search(USER1, "in:inbox", 0, 20);
    assertEquals(1, aliceInbox.getNumResults());
    assertEquals(wave.waveId.serialise(), aliceInbox.getDigests().get(0).getWaveId());

    SearchResult bobWarmup = runtimeSearchProvider.search(USER2, "in:inbox", 0, 20);
    assertEquals(0, bobWarmup.getNumResults());

    submitDeltaToExistingWavelet(wave, USER1, runtimeAddParticipant(USER2));
    runtimeWaveViewProvider.onParticipantAdded(wave, USER2).get();
    appendBlipToWavelet(wave, USER1, "b+fresh-runtime", "freshness payload");
    addTagToWavelet(wave, USER1, "fresh-tag");

    waveMap.unloadAllWavelets();
    runtimeWaveViewProvider.waveletCommitted(wave, null);

    SearchResult bobInbox = runtimeSearchProvider.search(USER2, "in:inbox", 0, 20);
    assertEquals(1, bobInbox.getNumResults());
    SearchResult bobContentSearch = runtimeSearchProvider.search(USER2, "in:inbox freshness", 0, 20);
    assertEquals(1, bobContentSearch.getNumResults());
    assertEquals(wave.waveId.serialise(), bobContentSearch.getDigests().get(0).getWaveId());

    runtimeWaveViewProvider.explicitPerUserWaveViews.invalidate(USER1);
    runtimeWaveViewProvider.waveletCommitted(wave, null);

    SearchResult tagResults = runtimeSearchProvider.search(USER1, "tag:fresh-tag", 0, 20);
    assertEquals(1, tagResults.getNumResults());
    assertEquals(wave.waveId.serialise(), tagResults.getDigests().get(0).getWaveId());
  }

  public void testSearchFilterByMentionsReturnsOnlyMentionedWaves() throws Exception {
    WaveletName mentionedWave = WaveletName.of(WaveId.of(DOMAIN, "mentioned"), WAVELET_ID);
    WaveletName unmentionedWave = WaveletName.of(WaveId.of(DOMAIN, "unmentioned"), WAVELET_ID);

    submitDeltaToNewWavelet(mentionedWave, USER1, addParticipantToWavelet(USER1, mentionedWave));
    addMentionAnnotationToBlip(mentionedWave, USER1, "b+1", "@user1", USER1);

    submitDeltaToNewWavelet(unmentionedWave, USER1, addParticipantToWavelet(USER1, unmentionedWave));

    SearchResult results = searchProvider.search(USER1, "mentions:me", 0, 10);

    assertEquals(1, results.getNumResults());
    assertEquals("mentioned",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
  }

  public void testSearchMentionsOfOtherUserNotReturnedForCurrentUser() throws Exception {
    WaveletName wave = WaveletName.of(WaveId.of(DOMAIN, "wave1"), WAVELET_ID);

    submitDeltaToNewWavelet(wave, USER1, addParticipantToWavelet(USER1, wave));
    // Mention USER2, not USER1
    addMentionAnnotationToBlip(wave, USER1, "b+1", "@user2", USER2);

    SearchResult results = searchProvider.search(USER1, "mentions:me", 0, 10);

    assertEquals(0, results.getNumResults());
  }

  public void testSearchOrderByDateAscRespectsSortOrderEvenWithPinnedWave() throws Exception {
    WaveletName oldest = WaveletName.of(WaveId.of(DOMAIN, "oldest"), WAVELET_ID);
    WaveletName middle = WaveletName.of(WaveId.of(DOMAIN, "middle"), WAVELET_ID);
    WaveletName newest = WaveletName.of(WaveId.of(DOMAIN, "newest"), WAVELET_ID);

    submitDeltaToNewWavelet(oldest, USER1, addParticipantToWavelet(USER1, oldest));
    waitForDistinctTimestamp();
    submitDeltaToNewWavelet(middle, USER1, addParticipantToWavelet(USER1, middle));
    waitForDistinctTimestamp();
    submitDeltaToNewWavelet(newest, USER1, addParticipantToWavelet(USER1, newest));

    // Pin the newest wave — without the fix it would be promoted to front, breaking dateasc order.
    pinWaveForUser(newest, USER1);

    SearchResult ascResults = searchProvider.search(USER1, "in:inbox orderby:dateasc", 0, 10);
    assertEquals(3, ascResults.getNumResults());
    // The pinned wave must NOT be promoted when orderby: is explicit.
    assertEquals("oldest", WaveId.deserialise(ascResults.getDigests().get(0).getWaveId()).getId());
    assertEquals("newest", WaveId.deserialise(ascResults.getDigests().get(2).getWaveId()).getId());
  }

  public void testSearchOrderByDateDescRespectsSortOrderEvenWithPinnedWave() throws Exception {
    WaveletName oldest = WaveletName.of(WaveId.of(DOMAIN, "oldest2"), WAVELET_ID);
    WaveletName middle = WaveletName.of(WaveId.of(DOMAIN, "middle2"), WAVELET_ID);
    WaveletName newest = WaveletName.of(WaveId.of(DOMAIN, "newest2"), WAVELET_ID);

    // Pin oldest2 immediately after creation so its UDW timestamp is older than middle2/newest2,
    // avoiding timestamp contamination in unknownDigest (which includes all wavelet LMTs).
    submitDeltaToNewWavelet(oldest, USER1, addParticipantToWavelet(USER1, oldest));
    pinWaveForUser(oldest, USER1);
    waitForDistinctTimestamp();
    submitDeltaToNewWavelet(middle, USER1, addParticipantToWavelet(USER1, middle));
    waitForDistinctTimestamp();
    submitDeltaToNewWavelet(newest, USER1, addParticipantToWavelet(USER1, newest));

    SearchResult descResults = searchProvider.search(USER1, "in:inbox orderby:datedesc", 0, 10);
    assertEquals(3, descResults.getNumResults());
    // The pinned wave must NOT be promoted when orderby: is explicit.
    assertEquals("newest2",
        WaveId.deserialise(descResults.getDigests().get(0).getWaveId()).getId());
    assertEquals("oldest2",
        WaveId.deserialise(descResults.getDigests().get(2).getWaveId()).getId());
  }

  // *** Helpers

  private void addMentionAnnotationToBlip(WaveletName name, ParticipantId author,
      String blipId, String text, ParticipantId mentionedUser) throws Exception {
    WaveletOperationContext context = new WaveletOperationContext(author, 0, 1);
    WaveletOperation blipOp = new WaveletBlipOperation(blipId, new BlipContentOperation(context,
        new DocOpBuilder()
            .annotationBoundary(AnnotationBoundaryMapImpl.builder()
                .initializationValues(AnnotationConstants.MENTION_USER,
                    mentionedUser.getAddress())
                .build())
            .characters(text)
            .annotationBoundary(AnnotationBoundaryMapImpl.builder()
                .initializationEnd(AnnotationConstants.MENTION_USER)
                .build())
            .build()));
    submitDeltaToExistingWavelet(name, author, blipOp);
  }

  private void addTaskAnnotationToBlip(WaveletName name, ParticipantId author,
      String blipId, String text, ParticipantId assignee) throws Exception {
    WaveletOperationContext context = new WaveletOperationContext(author, 0, 1);
    WaveletOperation blipOp = new WaveletBlipOperation(blipId, new BlipContentOperation(context,
        new DocOpBuilder()
            .annotationBoundary(AnnotationBoundaryMapImpl.builder()
                .initializationValues(AnnotationConstants.TASK_ASSIGNEE,
                    assignee.getAddress())
                .build())
            .characters(text)
            .annotationBoundary(AnnotationBoundaryMapImpl.builder()
                .initializationEnd(AnnotationConstants.TASK_ASSIGNEE)
                .build())
            .build()));
    submitDeltaToExistingWavelet(name, author, blipOp);
  }

  private void addUnassignedTaskToBlip(WaveletName name, ParticipantId author,
      String blipId, String taskId, String text) throws Exception {
    WaveletOperationContext context = new WaveletOperationContext(author, 0, 1);
    WaveletOperation blipOp = new WaveletBlipOperation(blipId, new BlipContentOperation(context,
        new DocOpBuilder()
            .annotationBoundary(AnnotationBoundaryMapImpl.builder()
                .initializationValues(AnnotationConstants.TASK_ID, taskId)
                .build())
            .characters(text)
            .annotationBoundary(AnnotationBoundaryMapImpl.builder()
                .initializationEnd(AnnotationConstants.TASK_ID)
                .build())
            .build()));
    submitDeltaToExistingWavelet(name, author, blipOp);
  }

  private SearchProvider newUnreadAwareSearchProvider(final Map<String, Integer> unreadCounts) {
    ConversationUtil conversationUtil = new ConversationUtil(idGenerator);
    WaveDigester digester = new WaveDigester(conversationUtil) {
      @Override
      int countUnread(ParticipantId participant, WaveSupplementContext context,
          Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
        String waveId = context.convWavelet.getWaveId().getId();
        Integer unreadCount = unreadCounts.get(waveId);
        if (unreadCount == null) {
          return super.countUnread(participant, context, waveletAdapters);
        }
        return unreadCount.intValue();
      }
    };
    return new SimpleSearchProviderImpl(DOMAIN, digester, waveMap, waveViewProvider);
  }

  private void submitDeltaToNewWavelet(WaveletName name, ParticipantId user,
      WaveletOperation... ops) throws Exception {

    HashedVersion version = V0_HASH_FACTORY.createVersionZero(name);
    addWaveletToUserView(name, user);
    submitDelta(name, user, version, ops);
  }

  private void submitDeltaToExistingWavelet(WaveletName name, ParticipantId user,
      WaveletOperation... ops) throws Exception {
    LocalWaveletContainer wavelet = waveMap.getOrCreateLocalWavelet(name);
    HashedVersion version = wavelet.copyWaveletData().getHashedVersion();
    submitDelta(name, user, version, ops);
  }

  private void submitDeltaToNewWaveletWithoutView(WaveletName name, ParticipantId user,
      WaveletOperation... ops) throws Exception {
    HashedVersion version = V0_HASH_FACTORY.createVersionZero(name);
    submitDelta(name, user, version, ops);
  }

  private WaveletOperation runtimeAddParticipant(ParticipantId user) {
    return new AddParticipant(CONTEXT, user);
  }

  private void submitDelta(WaveletName name, ParticipantId user, HashedVersion version,
      WaveletOperation... ops) throws Exception {
    WaveletDelta delta = new WaveletDelta(user, version, Arrays.asList(ops));

    ProtocolWaveletDelta protoDelta = CoreWaveletOperationSerializer.serialize(delta);

    // Submitting the request will require the certificate manager to sign the delta. We'll just
    // leave it unsigned.
    ProtocolSignedDelta signedProtoDelta =
        ProtocolSignedDelta.newBuilder().setDelta(protoDelta.toByteString()).build();

    LocalWaveletContainer wavelet = waveMap.getOrCreateLocalWavelet(name);
    wavelet.submitRequest(name, signedProtoDelta);
  }

  private void appendBlipToWavelet(WaveletName name, ParticipantId user, String blipId, String text)
      throws Exception {
    WaveletOperationContext context = new WaveletOperationContext(user, 0, 1);
    WaveletOperation appendOp =
        new WaveletBlipOperation(blipId, new BlipContentOperation(context,
            new DocOpBuilder().characters(text).build()));
    submitDeltaToExistingWavelet(name, user, appendOp);
  }

  private void addTagToWavelet(WaveletName name, ParticipantId user, String tag) throws Exception {
    WaveletOperationContext context = new WaveletOperationContext(user, 0, 1);
    WaveletOperation addTagOp =
        new WaveletBlipOperation(IdConstants.TAGS_DOC_ID, new BlipContentOperation(context,
            new DocOpBuilder()
                .elementStart("tag", Attributes.EMPTY_MAP)
                .characters(tag)
                .elementEnd()
                .build()));
    submitDeltaToExistingWavelet(name, user, addTagOp);
  }

  private void waitForDistinctTimestamp() throws InterruptedException {
    Thread.sleep(5L);
  }

  private void archiveWaveForUser(WaveletName name, ParticipantId user) throws Exception {
    long version = waveMap.getOrCreateLocalWavelet(name).copyWaveletData().getVersion();
    archiveWaveForUserWithVersions(name, user, version);
  }

  private void muteWaveForUser(WaveletName name, ParticipantId user) throws Exception {
    WaveletOperation muteOperation =
        new WaveletBlipOperation(
            WaveletBasedSupplement.MUTED_DOCUMENT,
            new BlipContentOperation(
                new WaveletOperationContext(user, 0, 1),
                new DocOpBuilder()
                    .elementStart(
                        WaveletBasedSupplement.MUTED_TAG,
                        new AttributesImpl(
                            WaveletBasedSupplement.MUTED_ATTR,
                            String.valueOf(true)))
                    .elementEnd()
                    .build()));
    submitDeltaToNewWaveletWithoutView(
        userDataWaveletName(name.waveId, user), user, muteOperation);
  }

  private void archiveWaveForUserWithVersions(WaveletName name, ParticipantId user,
      long... versions) throws Exception {
    DocOpBuilder builder = new DocOpBuilder();
    for (long version : versions) {
      builder.elementStart(
          WaveletBasedSupplement.ARCHIVE_TAG,
          new AttributesImpl(
              WaveletBasedSupplement.ID_ATTR,
              WaveletIdSerializer.INSTANCE.toString(name.waveletId),
              WaveletBasedSupplement.VERSION_ATTR,
              String.valueOf(version)));
      builder.elementEnd();
    }
    WaveletOperation archiveOperation =
        new WaveletBlipOperation(
            WaveletBasedSupplement.ARCHIVING_DOCUMENT,
            new BlipContentOperation(
                new WaveletOperationContext(user, 0, 1),
                builder.build()));
    submitDeltaToNewWaveletWithoutView(
        userDataWaveletName(name.waveId, user), user, archiveOperation);
  }

  private void clearArchiveStateForUser(WaveletName name, ParticipantId user) throws Exception {
    WaveletName userDataWaveletName = userDataWaveletName(name.waveId, user);
    WaveletOperation clearOperation =
        new WaveletBlipOperation(
            WaveletBasedSupplement.CLEARED_DOCUMENT,
            new BlipContentOperation(
                new WaveletOperationContext(user, 0, 1),
                new DocOpBuilder()
                    .elementStart(
                        WaveletBasedSupplement.CLEARED_TAG,
                        new AttributesImpl(
                            WaveletBasedSupplement.CLEARED_ATTR,
                            String.valueOf(true)))
                    .elementEnd()
                    .build()));
    submitDeltaToExistingWavelet(userDataWaveletName, user, clearOperation);
  }

  private void clearArchiveStateForUserWithoutAttr(WaveletName name, ParticipantId user)
      throws Exception {
    WaveletName userDataWaveletName = userDataWaveletName(name.waveId, user);
    WaveletOperation clearOperation =
        new WaveletBlipOperation(
            WaveletBasedSupplement.CLEARED_DOCUMENT,
            new BlipContentOperation(
                new WaveletOperationContext(user, 0, 1),
                new DocOpBuilder()
                    .elementStart(
                        WaveletBasedSupplement.CLEARED_TAG,
                        new AttributesImpl())
                    .elementEnd()
                    .build()));
    submitDeltaToExistingWavelet(userDataWaveletName, user, clearOperation);
  }

  private void pinWaveForUser(WaveletName name, ParticipantId user) throws Exception {
    WaveletOperation pinOperation =
        new WaveletBlipOperation(
            WaveletBasedSupplement.FOLDERS_DOCUMENT,
            new BlipContentOperation(
                new WaveletOperationContext(user, 0, 1),
                new DocOpBuilder()
                    .elementStart(
                        WaveletBasedSupplement.FOLDER_TAG,
                        new AttributesImpl(
                            WaveletBasedSupplement.ID_ATTR,
                            String.valueOf(SupplementedWaveImpl.PINNED_FOLDER)))
                    .elementEnd()
                    .build()));
    submitDeltaToNewWaveletWithoutView(userDataWaveletName(name.waveId, user), user, pinOperation);
  }

  private WaveletName userDataWaveletName(WaveId waveId, ParticipantId user) {
    WaveletId userDataWaveletId =
        WaveletId.of(
            waveId.getDomain(),
            IdUtil.join(IdConstants.USER_DATA_WAVELET_PREFIX, user.getAddress()));
    return WaveletName.of(waveId, userDataWaveletId);
  }

  private void addWaveletToUserView(WaveletName name, ParticipantId user) {
    HashMultimap<WaveId,WaveletId> wavesView = wavesViews.get(user);
    if (!wavesView.containsEntry(name.waveId, name.waveletId)) {
      wavesViews.get(user).put(name.waveId, name.waveletId);
    }
  }

  /**
   * Verifies that a search with active filters emits exactly one INFO log line (the combined
   * summary) rather than separate before/after lines for each filter stage.
   */
  public void testSearchEmitsExactlyOneInfoLogLinePerQuery() throws Exception {
    // Add a wave so there is at least one candidate going through the filter pipeline.
    submitDeltaToNewWavelet(WAVELET_NAME, USER1, addParticipantToWavelet(USER1, WAVELET_NAME));

    Logger logger = Logger.getLogger(SimpleSearchProviderImpl.class.getName());
    final List<LogRecord> infoRecords = new ArrayList<>();
    Handler captureHandler = new Handler() {
      @Override public void publish(LogRecord record) {
        if (Level.INFO.equals(record.getLevel())) infoRecords.add(record);
      }
      @Override public void flush() {}
      @Override public void close() throws SecurityException {}
    };
    Level savedLevel = logger.getLevel();
    boolean savedUseParent = logger.getUseParentHandlers();
    logger.addHandler(captureHandler);
    logger.setLevel(Level.ALL);
    logger.setUseParentHandlers(false);

    try {
      // "mentions:me unread:true" would normally emit 4 per-filter INFO lines plus a summary = 5.
      // After the fix it should emit exactly 1 (the combined summary).
      searchProvider.search(USER1, "mentions:me unread:true", 0, 20);
    } finally {
      logger.removeHandler(captureHandler);
      logger.setLevel(savedLevel);
      logger.setUseParentHandlers(savedUseParent);
    }

    assertEquals("Search must emit exactly one INFO log line per query",
        1, infoRecords.size());
    String msg = infoRecords.get(0).getMessage();
    assertTrue("Summary must contain user address; got: " + msg, msg.contains(USER1.getAddress()));
    assertTrue("Summary must contain query text; got: " + msg, msg.contains("mentions:me"));
  }

  /**
   * Verifies pinned waves appear first in a plain in:inbox query.
   */
  public void testPinnedWavesAppearFirstInPlainInbox() throws Exception {
    // Wave A — older, will be pinned
    WaveletName olderPinned = WaveletName.of(WaveId.of(DOMAIN, "old-pinned"), WAVELET_ID);
    submitDeltaToNewWavelet(olderPinned, USER1, addParticipantToWavelet(USER1, olderPinned));
    pinWaveForUser(olderPinned, USER1);

    waitForDistinctTimestamp();

    // Wave B — newer, not pinned
    WaveletName newerUnpinned = WaveletName.of(WaveId.of(DOMAIN, "new-unpinned"), WAVELET_ID);
    submitDeltaToNewWavelet(newerUnpinned, USER1, addParticipantToWavelet(USER1, newerUnpinned));

    SearchResult results = searchProvider.search(USER1, "in:inbox", 0, 10);

    assertEquals(2, results.getNumResults());
    // Pinned wave must be first regardless of date.
    assertEquals(olderPinned.waveId.serialise(), results.getDigests().get(0).getWaveId());
  }

  public void testSearchFilterByTasksReturnsOnlyWavesWithTaskAssignee() throws Exception {
    WaveletName taskWave = WaveletName.of(WaveId.of(DOMAIN, "task-wave"), WAVELET_ID);
    WaveletName noTaskWave = WaveletName.of(WaveId.of(DOMAIN, "no-task-wave"), WAVELET_ID);

    submitDeltaToNewWavelet(taskWave, USER1, addParticipantToWavelet(USER1, taskWave));
    addTaskAnnotationToBlip(taskWave, USER1, "b+1", "do the thing", USER1);

    submitDeltaToNewWavelet(noTaskWave, USER1, addParticipantToWavelet(USER1, noTaskWave));

    SearchResult results = searchProvider.search(USER1, "tasks:me", 0, 10);

    assertEquals(1, results.getNumResults());
    assertEquals("task-wave",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
  }

  public void testSearchFilterByTasksOfOtherUserNotReturnedForCurrentUser() throws Exception {
    WaveletName wave = WaveletName.of(WaveId.of(DOMAIN, "wave-other"), WAVELET_ID);

    submitDeltaToNewWavelet(wave, USER1, addParticipantToWavelet(USER1, wave));
    // Task assigned to USER2, not USER1
    addTaskAnnotationToBlip(wave, USER1, "b+1", "do the thing", USER2);

    SearchResult results = searchProvider.search(USER1, "tasks:me", 0, 10);

    assertEquals(0, results.getNumResults());
  }

  public void testSearchFilterByTasksWithExplicitAddress() throws Exception {
    WaveletName taskWave = WaveletName.of(WaveId.of(DOMAIN, "task-explicit"), WAVELET_ID);

    submitDeltaToNewWavelet(taskWave, USER1, addParticipantToWavelet(USER1, taskWave));
    addTaskAnnotationToBlip(taskWave, USER1, "b+1", "review this", USER2);

    SearchResult results = searchProvider.search(USER1, "tasks:" + USER2.getAddress(), 0, 10);

    assertEquals(1, results.getNumResults());
    assertEquals("task-explicit",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
  }

  public void testSearchFilterByTasksAllReturnsWavesWithAnyTaskAssignee() throws Exception {
    WaveletName mine = WaveletName.of(WaveId.of(DOMAIN, "task-all-mine"), WAVELET_ID);
    WaveletName others = WaveletName.of(WaveId.of(DOMAIN, "task-all-other"), WAVELET_ID);
    WaveletName unassigned = WaveletName.of(WaveId.of(DOMAIN, "task-all-unassigned"), WAVELET_ID);
    WaveletName none = WaveletName.of(WaveId.of(DOMAIN, "task-all-none"), WAVELET_ID);

    submitDeltaToNewWavelet(mine, USER1, addParticipantToWavelet(USER1, mine));
    addTaskAnnotationToBlip(mine, USER1, "b+1", "follow up", USER1);

    submitDeltaToNewWavelet(others, USER1, addParticipantToWavelet(USER1, others));
    addTaskAnnotationToBlip(others, USER1, "b+2", "review this", USER2);

    submitDeltaToNewWavelet(unassigned, USER1, addParticipantToWavelet(USER1, unassigned));
    addUnassignedTaskToBlip(unassigned, USER1, "b+3", "task-unassigned-1", "triage this");

    submitDeltaToNewWavelet(none, USER1, addParticipantToWavelet(USER1, none));

    SearchResult results = searchProvider.search(USER1, "tasks:all", 0, 10);

    assertEquals(3, results.getNumResults());
    List<String> waveIds = Lists.newArrayList();
    for (Digest digest : results.getDigests()) {
      waveIds.add(WaveId.deserialise(digest.getWaveId()).getId());
    }
    assertTrue(waveIds.contains("task-all-mine"));
    assertTrue(waveIds.contains("task-all-other"));
    assertTrue(waveIds.contains("task-all-unassigned"));
    assertFalse(waveIds.contains("task-all-none"));
  }

  public void testSearchFilterByTasksAllComposesWithUnreadFilter() throws Exception {
    WaveletName unreadTask = WaveletName.of(WaveId.of(DOMAIN, "task-all-unread"), WAVELET_ID);
    WaveletName readTask = WaveletName.of(WaveId.of(DOMAIN, "task-all-read"), WAVELET_ID);
    WaveletName unreadNoTask = WaveletName.of(WaveId.of(DOMAIN, "task-all-unread-no-task"), WAVELET_ID);

    submitDeltaToNewWavelet(unreadTask, USER1, addParticipantToWavelet(USER1, unreadTask));
    addTaskAnnotationToBlip(unreadTask, USER1, "b+1", "task item", USER2);

    submitDeltaToNewWavelet(readTask, USER1, addParticipantToWavelet(USER1, readTask));
    addTaskAnnotationToBlip(readTask, USER1, "b+2", "task item", USER1);

    submitDeltaToNewWavelet(unreadNoTask, USER1, addParticipantToWavelet(USER1, unreadNoTask));
    appendBlipToWavelet(unreadNoTask, USER1, "b+3", "plain message");

    SearchProvider provider = newUnreadAwareSearchProvider(
        ImmutableMap.of("task-all-unread", 2, "task-all-read", 0, "task-all-unread-no-task", 3));

    SearchResult results = provider.search(USER1, "tasks:all unread:true", 0, 10);

    assertEquals(1, results.getNumResults());
    assertEquals("task-all-unread",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
  }

  /**
   * Verifies unread mentioned waves appear before read mentioned waves in mentions:me results,
   * even when the unread wave is older (i.e., would normally sort lower by date).
   */
  public void testUnreadMentionsAppearFirstInMentionsSearch() throws Exception {
    // Wave A — older, mentioned, unread
    WaveletName olderUnread = WaveletName.of(WaveId.of(DOMAIN, "older-unread-mention"), WAVELET_ID);
    submitDeltaToNewWavelet(olderUnread, USER1, addParticipantToWavelet(USER1, olderUnread));
    addMentionAnnotationToBlip(olderUnread, USER1, "b+1", "@user1", USER1);

    waitForDistinctTimestamp();

    // Wave B — newer, mentioned, read (0 unread)
    WaveletName newerRead = WaveletName.of(WaveId.of(DOMAIN, "newer-read-mention"), WAVELET_ID);
    submitDeltaToNewWavelet(newerRead, USER1, addParticipantToWavelet(USER1, newerRead));
    addMentionAnnotationToBlip(newerRead, USER1, "b+2", "@user1", USER1);

    SearchProvider provider = newUnreadAwareSearchProvider(
        ImmutableMap.of("older-unread-mention", 2, "newer-read-mention", 0));

    SearchResult results = provider.search(USER1, "mentions:me", 0, 10);

    assertEquals(2, results.getNumResults());
    // Unread mention wave must appear first even though it is older.
    assertEquals("older-unread-mention",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
    assertEquals("newer-read-mention",
        WaveId.deserialise(results.getDigests().get(1).getWaveId()).getId());
  }

  /**
   * Verifies unread mentions are NOT promoted when an explicit orderby: is present.
   */
  public void testMentionsUnreadNotPromotedWhenOrderByPresent() throws Exception {
    // Wave A — older, mentioned, unread
    WaveletName olderUnread = WaveletName.of(WaveId.of(DOMAIN, "older-unread-ob"), WAVELET_ID);
    submitDeltaToNewWavelet(olderUnread, USER1, addParticipantToWavelet(USER1, olderUnread));
    addMentionAnnotationToBlip(olderUnread, USER1, "b+1", "@user1", USER1);

    waitForDistinctTimestamp();

    // Wave B — newer, mentioned, read
    WaveletName newerRead = WaveletName.of(WaveId.of(DOMAIN, "newer-read-ob"), WAVELET_ID);
    submitDeltaToNewWavelet(newerRead, USER1, addParticipantToWavelet(USER1, newerRead));
    addMentionAnnotationToBlip(newerRead, USER1, "b+2", "@user1", USER1);

    SearchProvider provider = newUnreadAwareSearchProvider(
        ImmutableMap.of("older-unread-ob", 2, "newer-read-ob", 0));

    // Explicit orderby:datedesc — no unread promotion should occur.
    SearchResult results = provider.search(USER1, "mentions:me orderby:datedesc", 0, 10);

    assertEquals(2, results.getNumResults());
    // Newer wave must be first because explicit orderby overrides promotion.
    assertEquals("newer-read-ob",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
    assertEquals("older-unread-ob",
        WaveId.deserialise(results.getDigests().get(1).getWaveId()).getId());
  }

  /**
   * Verifies that unread promotion is NOT applied when the mentions query targets a different
   * participant — only self-mentions (mentions:me) should trigger unread-first reordering.
   * Results must stay in default date order.
   */
  public void testNonSelfMentionSearchDoesNotPromoteUnread() throws Exception {
    // Wave A — older, mentions USER2, USER1 has unread on it
    WaveletName olderUnread = WaveletName.of(WaveId.of(DOMAIN, "older-unread-other"), WAVELET_ID);
    submitDeltaToNewWavelet(olderUnread, USER1, addParticipantToWavelet(USER1, olderUnread));
    addMentionAnnotationToBlip(olderUnread, USER1, "b+1", "@user2", USER2);

    waitForDistinctTimestamp();

    // Wave B — newer, mentions USER2, USER1 has 0 unread on it
    WaveletName newerRead = WaveletName.of(WaveId.of(DOMAIN, "newer-read-other"), WAVELET_ID);
    submitDeltaToNewWavelet(newerRead, USER1, addParticipantToWavelet(USER1, newerRead));
    addMentionAnnotationToBlip(newerRead, USER1, "b+2", "@user2", USER2);

    SearchProvider provider = newUnreadAwareSearchProvider(
        ImmutableMap.of("older-unread-other", 2, "newer-read-other", 0));

    // USER1 searches for waves mentioning USER2; unread promotion must not apply.
    SearchResult results = provider.search(USER1, "mentions:user2@" + DOMAIN, 0, 10);

    assertEquals(2, results.getNumResults());
    // Results must remain in date order (newer first), not re-ranked by USER1's unread state.
    assertEquals("newer-read-other",
        WaveId.deserialise(results.getDigests().get(0).getWaveId()).getId());
    assertEquals("older-unread-other",
        WaveId.deserialise(results.getDigests().get(1).getWaveId()).getId());
  }

  /**
   * Verifies pinned waves are NOT forced to the top when orderby: is present.
   */
  public void testPinnedWavesNotPromotedWhenOrderByPresent() throws Exception {
    // Wave A — older, pinned
    WaveletName olderPinned = WaveletName.of(WaveId.of(DOMAIN, "old-pinned-ob"), WAVELET_ID);
    submitDeltaToNewWavelet(olderPinned, USER1, addParticipantToWavelet(USER1, olderPinned));
    pinWaveForUser(olderPinned, USER1);

    waitForDistinctTimestamp();

    // Wave B — newer, not pinned
    WaveletName newerUnpinned = WaveletName.of(WaveId.of(DOMAIN, "new-unpinned-ob"), WAVELET_ID);
    submitDeltaToNewWavelet(newerUnpinned, USER1, addParticipantToWavelet(USER1, newerUnpinned));

    // With orderby:datedesc, newest should be first regardless of pin state.
    SearchResult results = searchProvider.search(USER1, "in:inbox orderby:datedesc", 0, 10);

    assertEquals(2, results.getNumResults());
    // Newer wave must come first because orderby:datedesc is specified.
    assertEquals(newerUnpinned.waveId.serialise(), results.getDigests().get(0).getWaveId());
  }

}
