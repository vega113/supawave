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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.wave.api.ApiIdSerializer;
import com.google.wave.api.SearchResult;
import com.google.wave.api.SearchResult.Digest;

import org.waveprotocol.box.common.Snippets;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.waveserver.SimpleSearchProviderImpl.WaveSupplementContext;
import org.waveprotocol.wave.model.conversation.BlipIterators;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.TitleHelper;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplement;
import org.waveprotocol.wave.model.supplement.PrimitiveSupplementImpl;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl.DefaultFollow;
import org.waveprotocol.wave.model.supplement.WaveletBasedSupplement;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates digests for the search service.
 *
 * @author yurize@apache.org
 */
public class WaveDigester {

  private final ConversationUtil conversationUtil;
  private static final int DIGEST_SNIPPET_LENGTH = 140;
  private static final int PARTICIPANTS_SNIPPET_LENGTH = 5;
  private static final String EMPTY_WAVELET_TITLE = "";

  @Inject
  public WaveDigester(ConversationUtil conversationUtil) {
    this.conversationUtil = conversationUtil;
  }

  /** Returns the conversation utility used by this digester. */
  ConversationUtil getConversationUtil() {
    return conversationUtil;
  }

  public SearchResult generateSearchResult(ParticipantId participant, String query,
      Collection<WaveViewData> results) {
    // Generate exactly one digest per wave. This includes conversational and
    // non-conversational waves. The position-based API for search prevents the
    // luxury of extra filtering here. Filtering can only be done in the
    // searchProvider. All waves returned by the search provider must be
    // included in the search result.
    SearchResult result = new SearchResult(query);
    if (results == null) {
      return result;
    }
    for (WaveViewData wave : results) {
      result.addDigest(build(participant, wave));
    }

    assert result.getDigests().size() == results.size();
    return result;
  }

  public SearchResult generateSearchResult(ParticipantId participant, String query,
      Collection<WaveViewData> results, Map<WaveId, Digest> digestCache) {
    SearchResult result = new SearchResult(query);
    if (results == null) {
      return result;
    }
    for (WaveViewData wave : results) {
      Digest digest = digestCache != null ? digestCache.get(wave.getWaveId()) : null;
      if (digest == null) {
        digest = build(participant, wave);
      }
      result.addDigest(digest);
    }
    assert result.getDigests().size() == results.size();
    return result;
  }

  int getUnreadCount(WaveSupplementContext context,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    return countUnread(null, context, waveletAdapters);
  }

  int countUnread(ParticipantId participant, WaveSupplementContext context,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    if (context == null) {
      return 0;
    }
    // Prefer supplement-based counting when available — this matches the
    // counting used by digest generation and correctly iterates only blips
    // in the conversation tree (rather than all blip-id documents in the
    // wavelet, which may include orphaned or non-tree documents).
    if (context.supplement != null && context.conversations != null) {
      return countUnread(context.convWavelet, context.conversationalWavelets, context.supplement,
          context.conversations, waveletAdapters);
    }
    PrimitiveSupplement readState = createReadState(participant, context, waveletAdapters);
    if (readState != null) {
      return countUnreadFromReadState(readState, context.conversationalWavelets);
    }
    return 0;
  }

  private int countUnreadFromReadState(PrimitiveSupplement readState,
      Iterable<? extends ObservableWaveletData> conversationalWavelets) {
    int unreadCount = 0;
    for (ObservableWaveletData conversationalWavelet : conversationalWavelets) {
      WaveletId waveletId = conversationalWavelet.getWaveletId();
      int lastReadWaveletVersion = readState.getLastReadWaveletVersion(waveletId);
      for (String documentId : conversationalWavelet.getDocumentIds()) {
        if (!IdUtil.isBlipId(documentId)) {
          continue;
        }
        ReadableBlipData blip = conversationalWavelet.getDocument(documentId);
        if (blip == null) {
          continue;
        }
        long modifiedVersion = blip.getLastModifiedVersion();
        boolean unreadByBlip = readState.getLastReadBlipVersion(waveletId, documentId)
            == PrimitiveSupplement.NO_VERSION
            || readState.getLastReadBlipVersion(waveletId, documentId) < modifiedVersion;
        boolean unreadByWavelet = lastReadWaveletVersion == PrimitiveSupplement.NO_VERSION
            || lastReadWaveletVersion < modifiedVersion;
        if (unreadByBlip && unreadByWavelet) {
          unreadCount++;
        }
      }
    }
    return unreadCount;
  }

  private PrimitiveSupplement createReadState(ParticipantId participant,
      WaveSupplementContext context,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    if (context.udw != null) {
      OpBasedWavelet userDataWavelet =
          getOrCreateReadOnlyWavelet(context.udw, waveletAdapters);
      return copyAndSeedLegacyPublicReadStateIfNeeded(
          WaveletBasedSupplement.create(userDataWavelet), context.conversationalWavelets);
    }
    if (isExplicitParticipant(participant, context.conversationalWavelets)) {
      PrimitiveSupplementImpl state = new PrimitiveSupplementImpl();
      // On public waves, seed as read so explicit participants without a UDW
      // don't see a stale unread badge in search results.
      if (hasSharedDomainParticipant(context.conversationalWavelets)) {
        for (ObservableWaveletData wd : context.conversationalWavelets) {
          state.setLastReadWaveletVersion(wd.getWaveletId(), (int) wd.getVersion());
        }
      }
      return state;
    }
    return null;
  }

  public Digest build(ParticipantId participant, WaveViewData wave) {

    Digest digest;

    // Note: the indexing infrastructure only supports single-conversation
    // snippeting, but unread state must reflect all conversational wavelets.
    ObservableWaveletData root = null;
    ObservableWaveletData other = null;
    List<ObservableWaveletData> conversationalWavelets = new ArrayList<ObservableWaveletData>();
    for (ObservableWaveletData waveletData : wave.getWavelets()) {
      WaveletId waveletId = waveletData.getWaveletId();
      if (IdUtil.isConversationRootWaveletId(waveletId)) {
        root = waveletData;
        conversationalWavelets.add(waveletData);
      } else if (IdUtil.isConversationalId(waveletId)) {
        conversationalWavelets.add(waveletData);
        other = waveletData;
      }
    }
    ObservableWaveletData udw = findViewerUserDataWavelet(participant, wave.getWavelets());
    Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters =
        new IdentityHashMap<ObservableWaveletData, OpBasedWavelet>();

    ObservableWaveletData convWavelet = root != null ? root : other;
    SupplementedWave supplement = null;
    ObservableConversationView conversations = null;
    if (convWavelet != null) {
      OpBasedWavelet wavelet = getOrCreateReadOnlyWavelet(convWavelet, waveletAdapters);
      if (WaveletBasedConversation.waveletHasConversation(wavelet)) {
        conversations = conversationUtil.buildConversation(wavelet);
        supplement =
            buildSupplement(
                participant, conversations, udw, conversationalWavelets, waveletAdapters);
      }
    }
    if (conversations != null) {
      // This is a conversational wave. Produce a conversational digest.
      digest =
          generateDigest(
              conversations, supplement, convWavelet, conversationalWavelets, waveletAdapters);
    } else {
      // It is unknown how to present this wave.
      digest = generateEmptyorUnknownDigest(wave);
    }

    return digest;
  }

  /**
   * Returns the unread conversational blip ids for the same viewer/wave input
   * used to build search digests. This mirrors {@link #build} rather than
   * deriving from raw document ids so J2CL read-state uses the GWT
   * conversation/supplement semantics and skips non-conversation documents.
   */
  public List<String> getUnreadBlipIds(ParticipantId participant, WaveViewData wave) {
    if (wave == null) {
      return Collections.emptyList();
    }
    ObservableWaveletData root = null;
    ObservableWaveletData other = null;
    List<ObservableWaveletData> conversationalWavelets = new ArrayList<ObservableWaveletData>();
    for (ObservableWaveletData waveletData : wave.getWavelets()) {
      WaveletId waveletId = waveletData.getWaveletId();
      if (IdUtil.isConversationRootWaveletId(waveletId)) {
        root = waveletData;
        conversationalWavelets.add(waveletData);
      } else if (IdUtil.isConversationalId(waveletId)) {
        conversationalWavelets.add(waveletData);
        other = waveletData;
      }
    }
    ObservableWaveletData convWavelet = root != null ? root : other;
    if (convWavelet == null) {
      return Collections.emptyList();
    }
    Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters =
        new IdentityHashMap<ObservableWaveletData, OpBasedWavelet>();
    OpBasedWavelet wavelet = getOrCreateReadOnlyWavelet(convWavelet, waveletAdapters);
    if (!WaveletBasedConversation.waveletHasConversation(wavelet)) {
      return Collections.emptyList();
    }
    ObservableConversationView conversations = conversationUtil.buildConversation(wavelet);
    SupplementedWave supplement =
        buildSupplement(
            participant,
            conversations,
            findViewerUserDataWavelet(participant, wave.getWavelets()),
            conversationalWavelets,
            waveletAdapters);
    return collectUnreadBlipIds(
        convWavelet, conversationalWavelets, supplement, conversations, waveletAdapters);
  }

  private List<String> collectUnreadBlipIds(
      WaveletData convWavelet,
      Iterable<? extends ObservableWaveletData> conversationalWavelets,
      SupplementedWave supplement,
      ObservableConversationView conversations,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    if (convWavelet == null || supplement == null || conversations == null) {
      return Collections.emptyList();
    }
    List<String> unreadBlipIds = new ArrayList<String>();
    ObservableConversation rootConversation = chooseDigestConversation(conversations);
    for (ObservableWaveletData conversationalWavelet : conversationalWavelets) {
      ObservableConversation conversation;
      if (conversationalWavelet == convWavelet) {
        conversation = rootConversation;
      } else {
        OpBasedWavelet wavelet = getOrCreateReadOnlyWavelet(conversationalWavelet, waveletAdapters);
        if (!WaveletBasedConversation.waveletHasConversation(wavelet)) {
          continue;
        }
        conversation = chooseDigestConversation(conversationUtil.buildConversation(wavelet));
      }
      if (conversation == null) {
        continue;
      }
      for (ConversationBlip blip : BlipIterators.breadthFirst(conversation)) {
        if (supplement.isUnread(blip)) {
          unreadBlipIds.add(blip.getId());
        }
      }
    }
    return unreadBlipIds;
  }

  static ObservableWaveletData findViewerUserDataWavelet(
      ParticipantId participant, Iterable<? extends ObservableWaveletData> wavelets) {
    if (participant == null || wavelets == null) {
      return null;
    }
    String address = participant.getAddress();
    for (ObservableWaveletData waveletData : wavelets) {
      WaveletId waveletId = waveletData.getWaveletId();
      if (IdUtil.isUserDataWavelet(address, waveletId)) {
        return waveletData;
      }
    }
    return null;
  }

  /**
   * Produces a digest for a set of conversations. Never returns null.
   *
   * @param conversations the conversation.
   * @param supplement the supplement that allows to easily perform various
   *        queries on user related state of the wavelet.
   * @param rawWaveletData the waveletData from which the digest is generated.
   *        This wavelet is a copy.
   * @return the server representation of the digest for the query.
   */
  Digest generateDigest(ObservableConversationView conversations, SupplementedWave supplement,
      WaveletData rawWaveletData, Iterable<? extends ObservableWaveletData> conversationalWavelets) {
    return generateDigest(
        conversations,
        supplement,
        rawWaveletData,
        conversationalWavelets,
        new IdentityHashMap<ObservableWaveletData, OpBasedWavelet>());
  }

  private Digest generateDigest(
      ObservableConversationView conversations,
      SupplementedWave supplement,
      WaveletData rawWaveletData,
      Iterable<? extends ObservableWaveletData> conversationalWavelets,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    ObservableConversation rootConversation = chooseDigestConversation(conversations);
    ObservableConversationBlip firstBlip = null;
    if ((rootConversation != null) && (rootConversation.getRootThread() != null)
        && (rootConversation.getRootThread().getFirstBlip() != null)) {
      firstBlip = rootConversation.getRootThread().getFirstBlip();
    }
    String title;
    if (firstBlip != null) {
      Document firstBlipContents = firstBlip.getContent();
      title = TitleHelper.extractTitle(firstBlipContents).trim();
    } else {
      title = EMPTY_WAVELET_TITLE;
    }

    String snippet = Snippets.renderSnippet(rawWaveletData, DIGEST_SNIPPET_LENGTH).trim();
    if (snippet.startsWith(title) && !title.isEmpty()) {
      // Strip the title from the snippet if the snippet starts with the title.
      snippet = snippet.substring(title.length()).trim();
    }
    String waveId = ApiIdSerializer.instance().serialiseWaveId(rawWaveletData.getWaveId());
    List<String> participants = CollectionUtils.newArrayList();
    for (ParticipantId p : rawWaveletData.getParticipants()) {
      if (participants.size() < PARTICIPANTS_SNIPPET_LENGTH) {
        participants.add(p.getAddress());
      } else {
        break;
      }
    }
    int blipCount = 0;
    long lastModified = -1;
    int unreadCount = countUnread(rawWaveletData, conversationalWavelets, supplement, conversations,
        waveletAdapters);
    for (ObservableWaveletData conversationalWavelet : conversationalWavelets) {
      ObservableConversation conversation;
      if (conversationalWavelet == rawWaveletData) {
        conversation = rootConversation;
      } else {
        OpBasedWavelet wavelet = getOrCreateReadOnlyWavelet(conversationalWavelet, waveletAdapters);
        if (!WaveletBasedConversation.waveletHasConversation(wavelet)) {
          continue;
        }
        conversation = chooseDigestConversation(conversationUtil.buildConversation(wavelet));
      }
      if (conversation == null) {
        continue;
      }
      for (ConversationBlip blip : BlipIterators.breadthFirst(conversation)) {
        lastModified = Math.max(blip.getLastModifiedTime(), lastModified);
        blipCount++;
      }
    }
    return new Digest(title, snippet, waveId, participants, lastModified,
        rawWaveletData.getCreationTime(), unreadCount, blipCount, supplement.isPinned());
  }

  private int countUnread(WaveletData convWavelet,
      Iterable<? extends ObservableWaveletData> conversationalWavelets,
      SupplementedWave supplement, ObservableConversationView conversations,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    if (convWavelet == null || supplement == null || conversations == null) {
      return 0;
    }

    int unreadCount = 0;
    ObservableConversation rootConversation = chooseDigestConversation(conversations);
    for (ObservableWaveletData conversationalWavelet : conversationalWavelets) {
      ObservableConversation conversation;
      if (conversationalWavelet == convWavelet) {
        conversation = rootConversation;
      } else {
        OpBasedWavelet wavelet = getOrCreateReadOnlyWavelet(conversationalWavelet, waveletAdapters);
        if (!WaveletBasedConversation.waveletHasConversation(wavelet)) {
          continue;
        }
        conversation = chooseDigestConversation(conversationUtil.buildConversation(wavelet));
      }
      if (conversation == null) {
        continue;
      }
      for (ConversationBlip blip : BlipIterators.breadthFirst(conversation)) {
        if (supplement.isUnread(blip)) {
          unreadCount++;
        }
      }
    }
    return unreadCount;
  }

  private ObservableConversation chooseDigestConversation(ObservableConversationView conversations) {
    if (conversations == null) {
      return null;
    }
    ObservableConversation rootConversation = conversations.getRoot();
    if (rootConversation != null) {
      return rootConversation;
    }
    Collection<? extends ObservableConversation> allConversations = conversations.getConversations();
    if (allConversations.isEmpty()) {
      return null;
    }
    return allConversations.iterator().next();
  }

  /** @return a digest for an empty wave. */
  private Digest emptyDigest(WaveViewData wave) {
    String title = ModernIdSerialiser.INSTANCE.serialiseWaveId(wave.getWaveId());
    String id = ApiIdSerializer.instance().serialiseWaveId(wave.getWaveId());
    return new Digest(title, "(empty)", id, Collections.<String> emptyList(), -1L, -1L, 0, 0);
  }

  /** @return a digest for an unrecognised type of wave. */
  private Digest unknownDigest(WaveViewData wave) {
    String title = ModernIdSerialiser.INSTANCE.serialiseWaveId(wave.getWaveId());
    String id = ApiIdSerializer.instance().serialiseWaveId(wave.getWaveId());
    long lmt = -1L;
    long created = -1L;
    int docs = 0;
    List<String> participants = new ArrayList<String>();
    for (WaveletData data : wave.getWavelets()) {
      lmt = Math.max(lmt, data.getLastModifiedTime());
      created = Math.max(lmt, data.getCreationTime());
      docs += data.getDocumentIds().size();

      for (ParticipantId p : data.getParticipants()) {
        if (participants.size() < PARTICIPANTS_SNIPPET_LENGTH) {
          participants.add(p.getAddress());
        } else {
          break;
        }
      }
    }
    return new Digest(title, "(unknown)", id, participants, lmt, created, 0, docs);
  }

  /**
   * Generates an empty digest in case the wave is empty, or an unknown digest
   * otherwise.
   *
   * @param wave the wave.
   * @return the generated digest.
   */
  Digest generateEmptyorUnknownDigest(WaveViewData wave) {
    boolean empty = !wave.getWavelets().iterator().hasNext();
    Digest digest = empty ? emptyDigest(wave) : unknownDigest(wave);
    return digest;
  }

  /**
   * Builds the supplement model from a wave. Never returns null.
   *
   * @param viewer the participant for which the supplement is constructed.
   * @param conversations conversations in the wave
   * @param udw the user data wavelet for the logged user.
   * @param conversationalWavelets the conversational wavelets in the wave.
   * @return the wave supplement.
   */
  @VisibleForTesting
  SupplementedWave buildSupplement(ParticipantId viewer, ObservableConversationView conversations,
      ObservableWaveletData udw, List<ObservableWaveletData> conversationalWavelets) {
    return buildSupplement(
        viewer,
        conversations,
        udw,
        conversationalWavelets,
        new IdentityHashMap<ObservableWaveletData, OpBasedWavelet>());
  }

  private SupplementedWave buildSupplement(
      ParticipantId viewer,
      ObservableConversationView conversations,
      ObservableWaveletData udw,
      List<ObservableWaveletData> conversationalWavelets,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    // Use mock state if there is no UDW.
    PrimitiveSupplement udwState;
    if (udw != null) {
      udwState = copyAndSeedLegacyPublicReadStateIfNeeded(
          WaveletBasedSupplement.create(getOrCreateReadOnlyWavelet(udw, waveletAdapters)),
          conversationalWavelets);
    } else {
      PrimitiveSupplementImpl emptyState = new PrimitiveSupplementImpl();
      // When the viewer has no UDW, seed all blips as read if:
      //   (a) viewer is an implicit participant (sees wave via @domain), OR
      //   (b) viewer is an explicit participant on a PUBLIC wave.
      // Without this, public/shared waves show a stale unread badge because
      // the empty supplement has no read state and every blip version
      // comparison falls through to "unread".
      // Private waves are left alone: a newly-added participant without a
      // UDW should see all content as unread.
      if (!isExplicitParticipant(viewer, conversationalWavelets)
          || hasSharedDomainParticipant(conversationalWavelets)) {
        for (ObservableWaveletData waveletData : conversationalWavelets) {
          emptyState.setLastReadWaveletVersion(waveletData.getWaveletId(),
              (int) waveletData.getVersion());
        }
      }
      udwState = emptyState;
    }
    return SupplementedWaveImpl.create(udwState, conversations, viewer, DefaultFollow.ALWAYS);
  }

  private OpBasedWavelet getOrCreateReadOnlyWavelet(
      ObservableWaveletData waveletData,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    OpBasedWavelet wavelet = waveletAdapters.get(waveletData);
    if (wavelet == null) {
      wavelet = OpBasedWavelet.createReadOnly(waveletData);
      waveletAdapters.put(waveletData, wavelet);
    }
    return wavelet;
  }

  /**
   * Checks whether the given participant is an explicit participant of any
   * of the supplied conversational wavelets.
   */
  private static boolean isExplicitParticipant(ParticipantId participant,
      List<ObservableWaveletData> conversationalWavelets) {
    for (ObservableWaveletData waveletData : conversationalWavelets) {
      if (waveletData.getParticipants().contains(participant)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if any conversational wavelet contains the shared domain
   * participant (i.e., the wave is public).
   */
  private static boolean hasSharedDomainParticipant(
      List<ObservableWaveletData> conversationalWavelets) {
    for (ObservableWaveletData waveletData : conversationalWavelets) {
      ParticipantId sharedDomainParticipant =
          ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(
              waveletData.getWaveId().getDomain());
      if (waveletData.getParticipants().contains(sharedDomainParticipant)) {
        return true;
      }
    }
    return false;
  }

  private PrimitiveSupplement copyAndSeedLegacyPublicReadStateIfNeeded(
      PrimitiveSupplement readState,
      List<ObservableWaveletData> conversationalWavelets) {
    if (readState == null
        || !hasSharedDomainParticipant(conversationalWavelets)
        || hasConversationalReadState(readState, conversationalWavelets)) {
      return readState;
    }

    PrimitiveSupplementImpl seededState = new PrimitiveSupplementImpl(readState);
    for (ObservableWaveletData waveletData : conversationalWavelets) {
      seededState.setLastReadWaveletVersion(
          waveletData.getWaveletId(), (int) waveletData.getVersion());
    }
    return seededState;
  }

  private static boolean hasConversationalReadState(
      PrimitiveSupplement readState,
      List<ObservableWaveletData> conversationalWavelets) {
    for (ObservableWaveletData waveletData : conversationalWavelets) {
      WaveletId waveletId = waveletData.getWaveletId();
      if (readState.getLastReadWaveletVersion(waveletId) != PrimitiveSupplement.NO_VERSION
          || readState.getLastReadParticipantsVersion(waveletId) != PrimitiveSupplement.NO_VERSION
          || readState.getLastReadTagsVersion(waveletId) != PrimitiveSupplement.NO_VERSION
          || readState.getReadBlips(waveletId).iterator().hasNext()) {
        return true;
      }
    }
    return false;
  }
}
