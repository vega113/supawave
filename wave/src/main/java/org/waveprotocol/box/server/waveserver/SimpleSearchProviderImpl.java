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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.wave.api.SearchResult;

import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.waveserver.QueryHelper.InvalidQueryException;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.util.logging.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search provider that reads user specific info from user data wavelet.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class SimpleSearchProviderImpl extends AbstractSearchProviderImpl {

  private static final Log LOG = Log.get(SimpleSearchProviderImpl.class);

  private final PerUserWaveViewProvider waveViewProvider;

  @Inject
  public SimpleSearchProviderImpl(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) final String waveDomain,
      WaveDigester digester, final WaveMap waveMap, PerUserWaveViewProvider userWaveViewProvider) {
    super(waveDomain, digester, waveMap);
    this.waveViewProvider = userWaveViewProvider;
  }

  @Override
  public SearchResult search(final ParticipantId user, String query, int startAt, int numResults) {
    LOG.fine("Search query '" + query + "' from user: " + user + " [" + startAt + ", "
        + ((startAt + numResults) - 1) + "]");
    Map<TokenQueryType, Set<String>> queryParams = null;
    try {
      queryParams = QueryHelper.parseQuery(query);
    } catch (InvalidQueryException e1) {
      // Invalid query param - stop and return empty search results.
      LOG.warning("Invalid Query. " + e1.getMessage());
      return digester.generateSearchResult(user, query, null);
    }
    // The "all" query should include shared domain waves. It is triggered when:
    // 1. No 'in:' filter is present (empty query), or
    // 2. The explicit 'in:all' filter is used.
    // 3. The 'in:pinned' filter is used (pinned waves can be in any state).
    final Set<String> inValues = queryParams.get(TokenQueryType.IN);
    final boolean isAllQuery = !queryParams.containsKey(TokenQueryType.IN)
        || (inValues != null && (inValues.contains("all") || inValues.contains("pinned")));

    // Determine whether we need to filter by inbox, archive, or pinned state.
    final boolean isInboxQuery;
    final boolean isArchiveQuery;
    final boolean isPinnedQuery;
    if (inValues != null) {
      isInboxQuery = inValues.contains("inbox");
      isArchiveQuery = inValues.contains("archive");
      isPinnedQuery = inValues.contains("pinned");
    } else {
      isInboxQuery = false;
      isArchiveQuery = false;
      isPinnedQuery = false;
    }

    final List<ParticipantId> withParticipantIds;
    final List<ParticipantId> creatorParticipantIds;
    try {
      String localDomain = user.getDomain();
      // Build and validate.
      withParticipantIds =
          QueryHelper.buildValidatedParticipantIds(queryParams, TokenQueryType.WITH,
              localDomain);
      creatorParticipantIds =
          QueryHelper.buildValidatedParticipantIds(queryParams, TokenQueryType.CREATOR,
              localDomain);
    } catch (InvalidParticipantAddress e) {
      // Invalid address - stop and return empty search results.
      LOG.warning("Invalid participantId: " + e.getAddress() + " in query: " + query);
      return digester.generateSearchResult(user, query, null);
    }

    // Extract tag filter values (e.g., "tag:nice").
    final Set<String> tagValues = queryParams.containsKey(TokenQueryType.TAG)
        ? queryParams.get(TokenQueryType.TAG)
        : Collections.<String>emptySet();

    LinkedHashMultimap<WaveId, WaveletId> currentUserWavesView =
        createWavesViewToFilter(user, isAllQuery);
    Function<ReadableWaveletData, Boolean> filterWaveletsFunction =
        createFilterWaveletsFunction(user, isAllQuery, withParticipantIds, creatorParticipantIds);

    ensureWavesHaveUserDataWavelet(currentUserWavesView, user);

    List<WaveViewData> results =
        Lists.newArrayList(filterWavesViewBySearchCriteria(filterWaveletsFunction,
            currentUserWavesView).values());

    // Filter by inbox/archive supplement state when the query specifies a folder.
    if (isInboxQuery || isArchiveQuery) {
      filterByFolderState(results, user, isInboxQuery);
    }

    // Filter by pinned state when the query specifies in:pinned.
    if (isPinnedQuery) {
      filterByPinnedState(results, user);
    }

    // Filter by tags when the query specifies tag: filters.
    if (!tagValues.isEmpty()) {
      LOG.info("Tag filter active: required tags = " + tagValues
          + ", candidates before filter = " + results.size());
      filterByTags(results, tagValues);
      LOG.info("After tag filter: " + results.size() + " results remain");
    }

    List<WaveViewData> sortedResults = sort(queryParams, results);

    // Promote pinned waves to the top of results (unless the query is specifically
    // for pinned waves, in which case all results are already pinned).
    if (!isPinnedQuery) {
      sortedResults = promotePinnedWaves(sortedResults, user);
    }

    Collection<WaveViewData> searchResult =
        computeSearchResult(user, startAt, numResults, sortedResults);
    LOG.info("Search response to '" + query + "': " + searchResult.size() + " results, user: "
        + user);
    return digester.generateSearchResult(user, query, searchResult);
  }

  private LinkedHashMultimap<WaveId, WaveletId> createWavesViewToFilter(final ParticipantId user,
      final boolean isAllQuery) {
    LinkedHashMultimap<WaveId, WaveletId> currentUserWavesView;
    currentUserWavesView = LinkedHashMultimap.create();
    currentUserWavesView.putAll(waveViewProvider.retrievePerUserWaveView(user));
    if (isAllQuery) {
      // If it is the "all" query - we need to include also waves view of the
      // shared domain participant.
      currentUserWavesView.putAll(waveViewProvider.retrievePerUserWaveView(sharedDomainParticipantId));
    }

    if(LOG.isFineLoggable()) {
      for (Map.Entry<WaveId, WaveletId> e : currentUserWavesView.entries()) {
        LOG.fine("unfiltered view contains: " + e.getKey() + " " + e.getValue());
      }
    }

    return currentUserWavesView;
  }

  private Function<ReadableWaveletData, Boolean> createFilterWaveletsFunction(
      final ParticipantId user, final boolean isAllQuery,
      final List<ParticipantId> withParticipantIds, final List<ParticipantId> creatorParticipantIds) {
    // A function to be applied by the WaveletContainer.
    Function<ReadableWaveletData, Boolean> matchesFunction =
        new Function<ReadableWaveletData, Boolean>() {

      @Override
      public Boolean apply(ReadableWaveletData wavelet) {
        try {
          return wavelet != null
              && isWaveletMatchesCriteria(wavelet, user, sharedDomainParticipantId,
              withParticipantIds, creatorParticipantIds, isAllQuery);
        } catch (WaveletStateException e) {
          LOG.warning(
              "Failed to access wavelet "
                  + WaveletName.of(wavelet.getWaveId(), wavelet.getWaveletId()), e);
          return false;
        }
      }
    };
    return matchesFunction;
  }

  /**
   * Verifies whether the wavelet matches the filter criteria.
   *
   * @param wavelet the wavelet.
   * @param user the logged in user.
   * @param sharedDomainParticipantId the shared domain participant id.
   * @param withList the list of participants to be used in 'with' filter.
   * @param creatorList the list of participants to be used in 'creator' filter.
   * @param isAllQuery true if the search results should include shared for this
   *        domain waves.
   */
  protected boolean isWaveletMatchesCriteria(ReadableWaveletData wavelet, ParticipantId user,
      ParticipantId sharedDomainParticipantId, List<ParticipantId> withList,
      List<ParticipantId> creatorList, boolean isAllQuery) throws WaveletStateException {
    Preconditions.checkNotNull(wavelet);
    // Filter by creator. This is the fastest check so we perform it first.
    for (ParticipantId creator : creatorList) {
      if (!creator.equals(wavelet.getCreator())) {
        // Skip.
        return false;
      }
    }
    boolean matches =
        super.isWaveletMatchesCriteria(wavelet, user, sharedDomainParticipantId, isAllQuery);
    // Now filter by 'with'.
    for (ParticipantId otherUser : withList) {
      if (!wavelet.getParticipants().contains(otherUser)) {
        // Skip.
        return false;
      }
    }
    return matches;
  }

  /**
   * Filters wave results by inbox or archive state using the user's supplement data.
   * Waves whose supplement indicates they are archived are excluded from inbox queries,
   * and waves that are in the inbox are excluded from archive queries.
   *
   * @param results the mutable list of wave views to filter in place.
   * @param user the participant whose supplement state to check.
   * @param wantInbox if true, keep only inbox waves; if false, keep only archived waves.
   */
  private void filterByFolderState(List<WaveViewData> results, ParticipantId user,
      boolean wantInbox) {
    Iterator<WaveViewData> it = results.iterator();
    while (it.hasNext()) {
      WaveViewData wave = it.next();
      try {
        // Find the conversational wavelet, the user data wavelet, and all conversational wavelets.
        ObservableWaveletData convWavelet = null;
        ObservableWaveletData udw = null;
        List<ObservableWaveletData> conversationalWavelets = new ArrayList<ObservableWaveletData>();
        for (ObservableWaveletData wd : wave.getWavelets()) {
          WaveletId wid = wd.getWaveletId();
          if (org.waveprotocol.wave.model.id.IdUtil.isConversationRootWaveletId(wid)) {
            convWavelet = wd;
            conversationalWavelets.add(wd);
          } else if (org.waveprotocol.wave.model.id.IdUtil.isConversationalId(wid)) {
            conversationalWavelets.add(wd);
          }
          if (org.waveprotocol.wave.model.id.IdUtil.isUserDataWavelet(user.getAddress(), wid)) {
            udw = wd;
          }
        }
        // Waves without a conversation root wavelet or without conversation
        // structure cannot carry archive state. Treat them as inbox waves:
        // keep them for inbox queries, remove them for archive queries.
        if (convWavelet == null) {
          if (!wantInbox) {
            it.remove();
          }
          continue;
        }
        org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet opWavelet =
            org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet.createReadOnly(convWavelet);
        if (!org.waveprotocol.wave.model.conversation.WaveletBasedConversation
            .waveletHasConversation(opWavelet)) {
          if (!wantInbox) {
            it.remove();
          }
          continue;
        }
        // Build the supplement to determine inbox/archive state.
        org.waveprotocol.wave.model.conversation.ObservableConversationView conversations =
            digester.getConversationUtil().buildConversation(opWavelet);
        SupplementedWave supplement = digester.buildSupplement(user, conversations, udw, conversationalWavelets);
        boolean isInbox = supplement.isInbox();
        if (wantInbox && !isInbox) {
          it.remove();
        } else if (!wantInbox && isInbox) {
          it.remove();
        }
      } catch (Exception e) {
        LOG.warning("Failed to check folder state for wave " + wave.getWaveId(), e);
        // If we can't determine the state, keep the result for safety.
      }
    }
  }

  /**
   * Promotes pinned waves to the top of the results list while preserving the
   * relative order within the pinned and non-pinned groups.
   *
   * @param results the sorted list of wave views.
   * @param user the participant whose supplement state to check.
   * @return a new list with pinned waves first, followed by non-pinned waves.
   */
  private List<WaveViewData> promotePinnedWaves(List<WaveViewData> results, ParticipantId user) {
    List<WaveViewData> pinned = new ArrayList<WaveViewData>();
    List<WaveViewData> unpinned = new ArrayList<WaveViewData>();
    for (WaveViewData wave : results) {
      try {
        ObservableWaveletData convWavelet = null;
        ObservableWaveletData udw = null;
        List<ObservableWaveletData> conversationalWavelets = new ArrayList<ObservableWaveletData>();
        for (ObservableWaveletData wd : wave.getWavelets()) {
          WaveletId wid = wd.getWaveletId();
          if (org.waveprotocol.wave.model.id.IdUtil.isConversationRootWaveletId(wid)) {
            convWavelet = wd;
            conversationalWavelets.add(wd);
          } else if (org.waveprotocol.wave.model.id.IdUtil.isConversationalId(wid)) {
            conversationalWavelets.add(wd);
          }
          if (org.waveprotocol.wave.model.id.IdUtil.isUserDataWavelet(user.getAddress(), wid)) {
            udw = wd;
          }
        }
        if (convWavelet != null && udw != null) {
          org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet opWavelet =
              org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet.createReadOnly(convWavelet);
          if (org.waveprotocol.wave.model.conversation.WaveletBasedConversation
              .waveletHasConversation(opWavelet)) {
            org.waveprotocol.wave.model.conversation.ObservableConversationView conversations =
                digester.getConversationUtil().buildConversation(opWavelet);
            SupplementedWave supplement = digester.buildSupplement(
                user, conversations, udw, conversationalWavelets);
            if (supplement.isPinned()) {
              pinned.add(wave);
              continue;
            }
          }
        }
        unpinned.add(wave);
      } catch (Exception e) {
        LOG.fine("Failed to check pinned state during promotion for wave " + wave.getWaveId());
        unpinned.add(wave);
      }
    }
    if (pinned.isEmpty()) {
      return results; // No pinned waves, return original list.
    }
    List<WaveViewData> promoted = new ArrayList<WaveViewData>(pinned.size() + unpinned.size());
    promoted.addAll(pinned);
    promoted.addAll(unpinned);
    return promoted;
  }

  /**
   * Filters wave results by pinned state using the user's supplement data.
   * Only waves whose supplement indicates they are pinned are kept.
   *
   * @param results the mutable list of wave views to filter in place.
   * @param user the participant whose supplement state to check.
   */
  private void filterByPinnedState(List<WaveViewData> results, ParticipantId user) {
    Iterator<WaveViewData> it = results.iterator();
    while (it.hasNext()) {
      WaveViewData wave = it.next();
      try {
        ObservableWaveletData convWavelet = null;
        ObservableWaveletData udw = null;
        List<ObservableWaveletData> conversationalWavelets = new ArrayList<ObservableWaveletData>();
        for (ObservableWaveletData wd : wave.getWavelets()) {
          WaveletId wid = wd.getWaveletId();
          if (org.waveprotocol.wave.model.id.IdUtil.isConversationRootWaveletId(wid)) {
            convWavelet = wd;
            conversationalWavelets.add(wd);
          } else if (org.waveprotocol.wave.model.id.IdUtil.isConversationalId(wid)) {
            conversationalWavelets.add(wd);
          }
          if (org.waveprotocol.wave.model.id.IdUtil.isUserDataWavelet(user.getAddress(), wid)) {
            udw = wd;
          }
        }
        if (convWavelet == null) {
          it.remove();
          continue;
        }
        org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet opWavelet =
            org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet.createReadOnly(convWavelet);
        if (!org.waveprotocol.wave.model.conversation.WaveletBasedConversation
            .waveletHasConversation(opWavelet)) {
          it.remove();
          continue;
        }
        org.waveprotocol.wave.model.conversation.ObservableConversationView conversations =
            digester.getConversationUtil().buildConversation(opWavelet);
        SupplementedWave supplement = digester.buildSupplement(user, conversations, udw, conversationalWavelets);
        if (!supplement.isPinned()) {
          it.remove();
        }
      } catch (Exception e) {
        LOG.warning("Failed to check pinned state for wave " + wave.getWaveId(), e);
        it.remove();
      }
    }
  }

  /**
   * Filters wave results by tags. Only waves whose conversation root wavelet
   * contains all of the requested tags are kept.
   *
   * @param results the mutable list of wave views to filter in place.
   * @param requiredTags the set of tag names that must all be present.
   */
  private void filterByTags(List<WaveViewData> results, Set<String> requiredTags) {
    // Build a lowercase set of the required tags for case-insensitive matching.
    Set<String> lowerRequiredTags = new java.util.HashSet<>();
    for (String tag : requiredTags) {
      lowerRequiredTags.add(tag.toLowerCase(java.util.Locale.ROOT));
    }

    Iterator<WaveViewData> it = results.iterator();
    while (it.hasNext()) {
      WaveViewData wave = it.next();
      try {
        ObservableWaveletData convWavelet = null;
        for (ObservableWaveletData wd : wave.getWavelets()) {
          if (org.waveprotocol.wave.model.id.IdUtil.isConversationRootWaveletId(wd.getWaveletId())) {
            convWavelet = wd;
            break;
          }
        }
        if (convWavelet == null) {
          LOG.fine("filterByTags: wave " + wave.getWaveId()
              + " has no conversation root wavelet, removing");
          it.remove();
          continue;
        }

        // Read tags directly from the wavelet data's tags document using the
        // DocOp representation. This avoids building a full conversation model
        // (which can fail if the manifest document is invalid) and avoids the
        // OpBasedWavelet.createBlip side-effect for non-existent documents.
        Set<String> waveTags = readTagsFromWaveletData(convWavelet);

        if (LOG.isFineLoggable()) {
          LOG.fine("filterByTags: wave " + wave.getWaveId() + " has tags: " + waveTags);
        }

        // Check that all required tags are present (case-insensitive).
        Set<String> lowerWaveTags = new java.util.HashSet<>();
        for (String wt : waveTags) {
          lowerWaveTags.add(wt.toLowerCase(java.util.Locale.ROOT));
        }
        boolean matches = true;
        for (String requiredTag : lowerRequiredTags) {
          if (!lowerWaveTags.contains(requiredTag)) {
            matches = false;
            break;
          }
        }
        if (!matches) {
          it.remove();
        }
      } catch (Exception e) {
        LOG.warning("Failed to check tags for wave " + wave.getWaveId(), e);
        // If we can't determine tags, exclude the result to avoid false matches.
        it.remove();
      }
    }
  }

  /**
   * Reads tag names from a wavelet's tags data document by walking the DocOp
   * representation. This avoids the need to build a conversation model or
   * initialise the document's mutable view — both of which can fail for
   * edge-case wavelets (missing manifest, uninitialised sinks, etc.).
   *
   * <p>The tags document has the form:
   * <pre>{@code <tag>name1</tag><tag>name2</tag> ...}</pre>
   * In DocOp terms this is: elementStart("tag") + characters("name") + elementEnd("tag"),
   * repeated once per tag.
   */
  private static Set<String> readTagsFromWaveletData(ObservableWaveletData waveletData) {
    org.waveprotocol.wave.model.wave.data.ReadableBlipData tagsBlip =
        waveletData.getDocument(org.waveprotocol.wave.model.id.IdConstants.TAGS_DOC_ID);
    if (tagsBlip == null) {
      return java.util.Collections.emptySet();
    }
    // The content of a BlipData is a DocumentOperationSink which implements
    // DocOp.IsDocOp — its asOperation() yields the DocInitialization that
    // describes the full document content.
    org.waveprotocol.wave.model.document.operation.DocInitialization docOp =
        tagsBlip.getContent().asOperation();

    final Set<String> tags = new java.util.HashSet<>();
    final boolean[] insideTag = {false};
    docOp.apply(new org.waveprotocol.wave.model.document.operation.DocInitializationCursor() {
      @Override
      public void elementStart(String type, org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        insideTag[0] = "tag".equals(type);
      }

      @Override
      public void elementEnd() {
        insideTag[0] = false;
      }

      @Override
      public void characters(String chars) {
        if (insideTag[0] && chars != null && !chars.isEmpty()) {
          tags.add(chars);
        }
      }

      @Override
      public void annotationBoundary(org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {
        // ignore
      }
    });
    return tags;
  }

  private List<WaveViewData> sort(Map<TokenQueryType, Set<String>> queryParams,
      List<WaveViewData> results) {
    return QueryHelper.computeSorter(queryParams).sortedCopy(results);
  }
}
