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

import com.google.wave.api.SearchResult.Digest;
import junit.framework.TestCase;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.IdGeneratorImpl;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.SilentOperationSink;
import org.waveprotocol.wave.model.operation.wave.BasicWaveletOperationContextFactory;
import org.waveprotocol.wave.model.schema.SchemaCollection;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipationHelper;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;
import org.waveprotocol.wave.model.wave.data.impl.ObservablePluggableMutableDocument;
import org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;

public final class UnreadSharedWaveDigestTest extends TestCase {
  private static final WaveId WAVE_ID = WaveId.of("local.net", "w+shared");
  private static final WaveletId CONVERSATION_WAVELET_ID =
      WaveletId.of("local.net", "conv+root");
  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("test10@local.net");
  private static final ParticipantId OTHER_USER = ParticipantId.ofUnsafe("vega@local.net");

  public void testWaveDigesterFindsViewerUserDataWaveletByIdOwnership() {
    ObservableWaveletData viewerUserDataWavelet =
        createWavelet(IdUtil.buildUserDataWaveletId(VIEWER), OTHER_USER);
    ObservableWaveletData otherUserDataWavelet =
        createWavelet(IdUtil.buildUserDataWaveletId(OTHER_USER), VIEWER);

    assertSame(
        viewerUserDataWavelet,
        WaveDigester.findViewerUserDataWavelet(
            VIEWER,
            java.util.Arrays.asList(viewerUserDataWavelet, otherUserDataWavelet)));
  }

  public void testSearchProviderTreatsViewerUserDataWaveletAsMatchingByIdOwnership()
      throws Exception {
    TestSearchProvider providerUnderTest = new TestSearchProvider();
    ObservableWaveletData viewerUserDataWavelet =
        createWavelet(IdUtil.buildUserDataWaveletId(VIEWER), OTHER_USER);

    assertTrue(providerUnderTest.matchesViewerUserDataWavelet(viewerUserDataWavelet, VIEWER));
  }

  public void testWaveDigesterBuildUsesPersistedUnreadStateFromViewerUserDataWavelet() {
    ObservableWaveletData conversationWavelet = createWritableWaveletData(CONVERSATION_WAVELET_ID, OTHER_USER);
    ObservableWaveletData viewerUserDataWavelet =
        createWritableWaveletData(IdUtil.buildUserDataWaveletId(VIEWER), OTHER_USER);
    ConversationUtil conversationUtil =
        new ConversationUtil(new IdGeneratorImpl("local.net", () -> "shared-unread"));
    OpBasedWavelet conversationModel = createWritableWavelet(conversationWavelet, OTHER_USER);
    OpBasedWavelet userDataModel = createWritableWavelet(viewerUserDataWavelet, OTHER_USER);

    org.waveprotocol.wave.model.conversation.WaveletBasedConversation.makeWaveletConversational(
        conversationModel);
    org.waveprotocol.wave.model.conversation.ObservableConversationView conversations =
        conversationUtil.buildConversation(conversationModel);
    org.waveprotocol.wave.model.conversation.ObservableConversation conversation =
        conversations.getRoot();
    conversation.addParticipant(VIEWER);
    org.waveprotocol.wave.model.conversation.ConversationBlip unreadBlip =
        conversation.getRootThread().appendBlip();

    PrimitiveSupplement primitive = org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.create(
        userDataModel);
    SupplementedWave supplement =
        SupplementedWaveImpl.create(primitive, conversations, VIEWER, SupplementedWaveImpl.DefaultFollow.ALWAYS);
    supplement.markAsRead(unreadBlip);

    WaveViewData wave =
        WaveViewDataImpl.create(
            WAVE_ID,
            java.util.Arrays.asList(
                WaveletDataUtil.copyWavelet(conversationWavelet),
                WaveletDataUtil.copyWavelet(viewerUserDataWavelet)));
    Digest digest = new WaveDigester(conversationUtil).build(VIEWER, wave);

    assertEquals(0, digest.getUnreadCount());
  }

  private static ObservableWaveletData createWavelet(WaveletId waveletId, ParticipantId creator) {
    return new WaveletDataImpl(
        waveletId,
        creator,
        1234567890L,
        0L,
        org.waveprotocol.wave.model.version.HashedVersion.unsigned(0),
        0L,
        WAVE_ID,
        ObservablePluggableMutableDocument.createFactory(new SchemaCollection()));
  }

  private static ObservableWaveletData createWritableWaveletData(
      WaveletId waveletId, ParticipantId creator) {
    return new WaveletDataImpl(
        waveletId,
        creator,
        1234567890L,
        0L,
        org.waveprotocol.wave.model.version.HashedVersion.unsigned(0),
        0L,
        WAVE_ID,
        ObservablePluggableMutableDocument.createFactory(new SchemaCollection()));
  }

  private static OpBasedWavelet createWritableWavelet(
      ObservableWaveletData waveletData, ParticipantId creator) {
    return new OpBasedWavelet(
        WAVE_ID,
        waveletData,
        new BasicWaveletOperationContextFactory(creator),
        ParticipationHelper.DEFAULT,
        SilentOperationSink.Executor.build(waveletData),
        SilentOperationSink.VOID);
  }

  private static final class TestSearchProvider extends AbstractSearchProviderImpl {
    TestSearchProvider() {
      super(
          "local.net",
          null,
          org.mockito.Mockito.mock(WaveMap.class));
    }

    boolean matchesViewerUserDataWavelet(ObservableWaveletData wavelet, ParticipantId viewer)
        throws WaveletStateException {
      return isWaveletMatchesCriteria(
          wavelet,
          viewer,
          ParticipantId.ofUnsafe("@local.net"),
          false);
    }

    @Override
    public com.google.wave.api.SearchResult search(ParticipantId user, String query, int startAt,
        int numResults) {
      throw new UnsupportedOperationException();
    }
  }
}
