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
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletIdSerializer;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.supplement.SupplementedWave;
import org.waveprotocol.wave.model.supplement.SupplementedWaveImpl;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.util.logging.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Search provider that reads user specific info from user data wavelet.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public class SimpleSearchProviderImpl extends AbstractSearchProviderImpl {

  private static final Log LOG = Log.get(SimpleSearchProviderImpl.class);

  private final PerUserWaveViewProvider waveViewProvider;

  private enum FolderState {
    INBOX,
    ARCHIVE,
    MUTE
  }

  @Inject
  public SimpleSearchProviderImpl(@Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) final String waveDomain,
      WaveDigester digester, final WaveMap waveMap, PerUserWaveViewProvider userWaveViewProvider) {
    super(waveDomain, digester, waveMap);
    this.waveViewProvider = userWaveViewProvider;
  }

  /**
   * Per-wave cached supplement context. Avoids creating duplicate OpBasedWavelet
   * adapters and conversation models when multiple filter stages (inbox/archive,
   * pinned, promotion) need the same supplement for the same wave.
   *
   * <p>Each wave's context is computed at most once and cached for the duration
   * of a single search request.
   */
  static class WaveSupplementContext {
    final ObservableWaveletData convWavelet;
    final ObservableWaveletData udw;
    final List<ObservableWaveletData> conversationalWavelets;
    /** Null if the wave has no conversation structure. */
    final SupplementedWave supplement;
    /** Null if the wave has no conversation structure. */
    final ObservableConversationView conversations;

    WaveSupplementContext(ObservableWaveletData convWavelet, ObservableWaveletData udw,
        List<ObservableWaveletData> conversationalWavelets,
        SupplementedWave supplement, ObservableConversationView conversations) {
      this.convWavelet = convWavelet;
      this.udw = udw;
      this.conversationalWavelets = conversationalWavelets;
      this.supplement = supplement;
      this.conversations = conversations;
    }
  }

  /**
   * Builds or retrieves the cached supplement context for a wave.
   */
  private WaveSupplementContext getOrBuildContext(
      WaveViewData wave, ParticipantId user,
      Map<WaveId, WaveSupplementContext> cache,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    WaveSupplementContext cached = cache.get(wave.getWaveId());
    if (cached != null) {
      return cached;
    }

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

    if (convWavelet == null && !conversationalWavelets.isEmpty()) {
      convWavelet = conversationalWavelets.get(0);
    }

    if (convWavelet == null) {
      // No conversational wavelet -- cache a null-supplement context.
      WaveSupplementContext ctx = new WaveSupplementContext(
          null, udw, conversationalWavelets, null, null);
      cache.put(wave.getWaveId(), ctx);
      return ctx;
    }

    OpBasedWavelet opWavelet = getOrCreateReadOnlyWavelet(convWavelet, waveletAdapters);
    if (!WaveletBasedConversation.waveletHasConversation(opWavelet)) {
      LOG.fine("Wave " + wave.getWaveId()
          + " conversation root wavelet lacks manifest structure");
      WaveSupplementContext ctx = new WaveSupplementContext(
          convWavelet, udw, conversationalWavelets, null, null);
      cache.put(wave.getWaveId(), ctx);
      return ctx;
    }

    ObservableConversationView conversations =
        digester.getConversationUtil().buildConversation(opWavelet);
    SupplementedWave supplement = digester.buildSupplement(
        user, conversations, udw, conversationalWavelets);

    WaveSupplementContext ctx = new WaveSupplementContext(
        convWavelet, udw, conversationalWavelets, supplement, conversations);
    cache.put(wave.getWaveId(), ctx);
    return ctx;
  }

  /**
   * Gets or creates a read-only OpBasedWavelet adapter, caching by identity to
   * avoid wrapping the same ObservableWaveletData more than once (which would
   * trigger duplicate PluggableMutableDocument.init() calls).
   */
  private static OpBasedWavelet getOrCreateReadOnlyWavelet(
      ObservableWaveletData waveletData,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    OpBasedWavelet wavelet = waveletAdapters.get(waveletData);
    if (wavelet == null) {
      wavelet = OpBasedWavelet.createReadOnly(waveletData);
      waveletAdapters.put(waveletData, wavelet);
    }
    return wavelet;
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
    final boolean isUnreadOnlyQuery = queryParams.containsKey(TokenQueryType.UNREAD);

    LinkedHashMultimap<WaveId, WaveletId> currentUserWavesView =
        createWavesViewToFilter(user, isAllQuery);
    Function<ReadableWaveletData, Boolean> filterWaveletsFunction =
        createFilterWaveletsFunction(user, isAllQuery, withParticipantIds, creatorParticipantIds);

    ensureWavesHaveUserDataWavelet(currentUserWavesView, user);

    List<WaveViewData> results =
        Lists.newArrayList(filterWavesViewBySearchCriteria(filterWaveletsFunction,
            currentUserWavesView).values());
    expandConversationalWavelets(results, filterWaveletsFunction);

    // Shared caches for supplement-building across all filter stages.
    // This prevents creating duplicate OpBasedWavelet adapters for the same
    // underlying data, which was the root cause of duplicate init() calls and
    // silent failures in the conversation model chain.
    boolean needsSupplementFiltering = isInboxQuery || isArchiveQuery || isPinnedQuery
        || isUnreadOnlyQuery;
    Map<WaveId, WaveSupplementContext> supplementCache =
        needsSupplementFiltering ? new HashMap<WaveId, WaveSupplementContext>() : null;
    Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters =
        needsSupplementFiltering ? new IdentityHashMap<ObservableWaveletData, OpBasedWavelet>() : null;

    // Filter by inbox/archive supplement state when the query specifies a folder.
    if (isInboxQuery || isArchiveQuery) {
      filterByFolderState(results, user, isInboxQuery, supplementCache, waveletAdapters);
    }

    // Filter by pinned state when the query specifies in:pinned.
    if (isPinnedQuery) {
      filterByPinnedState(results, user, supplementCache, waveletAdapters);
    }

    // Filter by tags when the query specifies tag: filters.
    if (!tagValues.isEmpty()) {
      LOG.info("Tag filter active: required tags = " + tagValues
          + ", candidates before filter = " + results.size());
      filterByTags(results, tagValues);
      LOG.info("After tag filter: " + results.size() + " results remain");
    }

    // Extract title filter values (e.g., "title:meeting").
    final Set<String> titleValues = queryParams.containsKey(TokenQueryType.TITLE)
        ? queryParams.get(TokenQueryType.TITLE)
        : Collections.<String>emptySet();

    // Extract content filter values (e.g., "content:agenda").
    final Set<String> contentValues = queryParams.containsKey(TokenQueryType.CONTENT)
        ? queryParams.get(TokenQueryType.CONTENT)
        : Collections.<String>emptySet();

    // Filter by title when the query specifies title: filters.
    if (!titleValues.isEmpty()) {
      LOG.info("Title filter active: required terms = " + titleValues
          + ", candidates before filter = " + results.size());
      filterByTitle(results, titleValues);
      LOG.info("After title filter: " + results.size() + " results remain");
    }

    // Filter by content when the query specifies content: filters.
    if (!contentValues.isEmpty()) {
      LOG.info("Content filter active: required terms = " + contentValues
          + ", candidates before filter = " + results.size());
      filterByContent(results, contentValues);
      LOG.info("After content filter: " + results.size() + " results remain");
    }

    if (isUnreadOnlyQuery) {
      LOG.info("Unread filter active: candidates before filter = " + results.size());
      filterByUnreadState(results, user, supplementCache, waveletAdapters);
      LOG.info("After unread filter: " + results.size() + " results remain");
    }

    List<WaveViewData> sortedResults = sort(queryParams, results);

    // Promote pinned waves to the top of results (unless the query is specifically
    // for pinned waves, in which case all results are already pinned).
    if (!isPinnedQuery) {
      // Reuse supplement cache from filter stages if available, otherwise create fresh ones.
      if (supplementCache == null) {
        supplementCache = new HashMap<WaveId, WaveSupplementContext>();
        waveletAdapters = new IdentityHashMap<ObservableWaveletData, OpBasedWavelet>();
      }
      sortedResults = promotePinnedWaves(sortedResults, user, supplementCache, waveletAdapters);
    }

    // Capture the total count BEFORE pagination so the client knows the full result set size.
    int totalBeforePagination = sortedResults.size();

    Collection<WaveViewData> searchResult =
        computeSearchResult(user, startAt, numResults, sortedResults);
    LOG.info("Search response to '" + query + "': " + searchResult.size() + " results"
        + " (total " + totalBeforePagination + "), user: " + user);
    SearchResult result = digester.generateSearchResult(user, query, searchResult);
    result.setTotalResults(totalBeforePagination);
    return result;
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

  private void expandConversationalWavelets(List<WaveViewData> results,
      Function<ReadableWaveletData, Boolean> matchesFunction) {
    Map<WaveId, Wave> loadedWaves = waveMap.getWaves();
    for (WaveViewData wave : results) {
      Set<WaveletId> visibleWaveletIds = new HashSet<WaveletId>();
      for (ObservableWaveletData waveletData : wave.getWavelets()) {
        visibleWaveletIds.add(waveletData.getWaveletId());
      }

      Set<WaveletId> conversationalWaveletIds = new HashSet<WaveletId>();
      try {
        Set<WaveletId> storedWaveletIds = waveMap.lookupWavelets(wave.getWaveId());
        if (storedWaveletIds != null) {
          conversationalWaveletIds.addAll(storedWaveletIds);
        }
      } catch (WaveletStateException e) {
        LOG.warning("Failed to look up stored wavelets for " + wave.getWaveId(), e);
      }

      Wave loadedWave = loadedWaves.get(wave.getWaveId());
      if (loadedWave != null) {
        for (WaveletContainer container : loadedWave) {
          conversationalWaveletIds.add(container.getWaveletName().waveletId);
        }
      }

      for (WaveletId waveletId : conversationalWaveletIds) {
        if (visibleWaveletIds.contains(waveletId) || !IdUtil.isConversationalId(waveletId)) {
          continue;
        }

        WaveletName waveletName = WaveletName.of(wave.getWaveId(), waveletId);
        try {
          WaveletContainer container = waveMap.getWavelet(waveletName);
          if (container == null || !container.applyFunction(matchesFunction)) {
            continue;
          }
          wave.addWavelet(container.copyWaveletData());
          visibleWaveletIds.add(waveletId);
        } catch (WaveletStateException e) {
          LOG.warning("Failed to expand wavelet " + waveletName, e);
        }
      }
    }
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
   * @param supplementCache shared cache of supplement contexts across filter stages.
   * @param waveletAdapters shared cache of OpBasedWavelet adapters.
   */
  private void filterByFolderState(List<WaveViewData> results, ParticipantId user,
      boolean wantInbox, Map<WaveId, WaveSupplementContext> supplementCache,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    Iterator<WaveViewData> it = results.iterator();
    while (it.hasNext()) {
      WaveViewData wave = it.next();
      try {
        FolderState folderState = readFolderState(wave, user);
        if (wantInbox && folderState != FolderState.INBOX) {
          it.remove();
        } else if (!wantInbox && folderState != FolderState.ARCHIVE) {
          it.remove();
        }
      } catch (Exception e) {
        LOG.warning("Failed to check folder state for wave " + wave.getWaveId()
            + ": " + e.getMessage(), e);
        // If we can't determine the state, keep the result for safety.
      }
    }
  }

  /**
   * Promotes pinned waves to the top of the results list while preserving the
   * relative order within the pinned and non-pinned groups.
   *
   * <p>Reads pin state directly from the UDW's folder document ({@code m/folder})
   * via the DocOp representation, avoiding the fragile full conversation model
   * and supplement construction chain.
   *
   * @param results the sorted list of wave views.
   * @param user the participant whose supplement state to check.
   * @param supplementCache shared cache of supplement contexts across filter stages.
   * @param waveletAdapters shared cache of OpBasedWavelet adapters.
   * @return a new list with pinned waves first, followed by non-pinned waves.
   */
  private List<WaveViewData> promotePinnedWaves(List<WaveViewData> results, ParticipantId user,
      Map<WaveId, WaveSupplementContext> supplementCache,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    List<WaveViewData> pinned = new ArrayList<WaveViewData>();
    List<WaveViewData> unpinned = new ArrayList<WaveViewData>();
    for (WaveViewData wave : results) {
      try {
        ObservableWaveletData udw = null;
        for (ObservableWaveletData wd : wave.getWavelets()) {
          if (org.waveprotocol.wave.model.id.IdUtil.isUserDataWavelet(user.getAddress(),
              wd.getWaveletId())) {
            udw = wd;
            break;
          }
        }
        if (udw != null && readPinnedStateFromUdw(udw)) {
          pinned.add(wave);
        } else {
          unpinned.add(wave);
        }
      } catch (Exception e) {
        LOG.fine("Failed to check pinned state during promotion for wave " + wave.getWaveId()
            + ": " + e.getMessage());
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
   * <p>Reads pin state directly from the UDW's folder document ({@code m/folder})
   * via the DocOp representation, avoiding the fragile full conversation model
   * and supplement construction chain that can fail silently for edge-case
   * wavelets (missing manifest, uninitialised sinks, etc.).
   *
   * @param results the mutable list of wave views to filter in place.
   * @param user the participant whose supplement state to check.
   * @param supplementCache shared cache of supplement contexts across filter stages.
   * @param waveletAdapters shared cache of OpBasedWavelet adapters.
   */
  private void filterByPinnedState(List<WaveViewData> results, ParticipantId user,
      Map<WaveId, WaveSupplementContext> supplementCache,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    Iterator<WaveViewData> it = results.iterator();
    while (it.hasNext()) {
      WaveViewData wave = it.next();
      try {
        ObservableWaveletData udw = null;
        for (ObservableWaveletData wd : wave.getWavelets()) {
          if (org.waveprotocol.wave.model.id.IdUtil.isUserDataWavelet(user.getAddress(),
              wd.getWaveletId())) {
            udw = wd;
            break;
          }
        }
        if (udw == null || !readPinnedStateFromUdw(udw)) {
          it.remove();
        }
      } catch (Exception e) {
        LOG.warning("Failed to check pinned state for wave " + wave.getWaveId()
            + ": " + e.getMessage(), e);
        it.remove();
      }
    }
  }

  /**
   * Filters wave results by unread state. Only waves with at least one unread blip are kept.
   *
   * <p>Reuses the existing supplement cache so unread checks do not rebuild conversation state
   * independently of inbox/archive/pinned filtering.
   *
   * @param results the mutable list of wave views to filter in place.
   * @param user the participant whose unread state to check.
   * @param supplementCache shared cache of supplement contexts across filter stages.
   * @param waveletAdapters shared cache of OpBasedWavelet adapters.
   */
  private void filterByUnreadState(List<WaveViewData> results, ParticipantId user,
      Map<WaveId, WaveSupplementContext> supplementCache,
      Map<ObservableWaveletData, OpBasedWavelet> waveletAdapters) {
    Iterator<WaveViewData> it = results.iterator();
    while (it.hasNext()) {
      WaveViewData wave = it.next();
      try {
        WaveSupplementContext ctx = getOrBuildContext(wave, user, supplementCache, waveletAdapters);
        if (digester.countUnread(user, ctx, waveletAdapters) <= 0) {
          it.remove();
        }
      } catch (Exception e) {
        LOG.warning("Failed to check unread state for wave " + wave.getWaveId(), e);
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
   * Filters wave results by title. The title is the text content of the root blip
   * (the first blip in the conversation manifest). Only waves whose title contains
   * all of the requested search terms (case-insensitive substring match) are kept.
   *
   * @param results the mutable list of wave views to filter in place.
   * @param requiredTerms the set of title search terms that must all be present.
   */
  private void filterByTitle(List<WaveViewData> results, Set<String> requiredTerms) {
    // Build lowercase search terms for case-insensitive matching.
    List<String> lowerTerms = new ArrayList<String>();
    for (String term : requiredTerms) {
      lowerTerms.add(term.toLowerCase(java.util.Locale.ROOT));
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
          it.remove();
          continue;
        }

        String rootBlipId = getRootBlipId(convWavelet);
        if (rootBlipId == null) {
          it.remove();
          continue;
        }

        org.waveprotocol.wave.model.wave.data.ReadableBlipData rootBlip =
            convWavelet.getDocument(rootBlipId);
        if (rootBlip == null) {
          it.remove();
          continue;
        }

        String titleText = extractTextFromBlip(rootBlip)
            .toLowerCase(java.util.Locale.ROOT);

        boolean matches = true;
        for (String term : lowerTerms) {
          if (!titleText.contains(term)) {
            matches = false;
            break;
          }
        }
        if (!matches) {
          it.remove();
        }
      } catch (Exception e) {
        LOG.warning("Failed to check title for wave " + wave.getWaveId(), e);
        it.remove();
      }
    }
  }

  /**
   * Filters wave results by content. Searches across ALL blip documents in
   * all conversational wavelets of each wave. Only waves where at least one
   * blip contains all of the requested search terms (case-insensitive substring
   * match) are kept.
   *
   * <p>Short-circuits: stops checking blips for a wave as soon as a match is found.
   *
   * @param results the mutable list of wave views to filter in place.
   * @param requiredTerms the set of content search terms that must all be present.
   */
  private void filterByContent(List<WaveViewData> results, Set<String> requiredTerms) {
    // Build lowercase search terms for case-insensitive matching.
    List<String> lowerTerms = new ArrayList<String>();
    for (String term : requiredTerms) {
      lowerTerms.add(term.toLowerCase(java.util.Locale.ROOT));
    }

    Iterator<WaveViewData> it = results.iterator();
    while (it.hasNext()) {
      WaveViewData wave = it.next();
      try {
        boolean waveMatches = false;

        // Iterate over all conversational wavelets in this wave.
        for (ObservableWaveletData wd : wave.getWavelets()) {
          if (!org.waveprotocol.wave.model.id.IdUtil.isConversationalId(wd.getWaveletId())) {
            continue;
          }

          // Iterate over all documents (blips) in the wavelet.
          for (String docId : wd.getDocumentIds()) {
            org.waveprotocol.wave.model.wave.data.ReadableBlipData blip =
                wd.getDocument(docId);
            if (blip == null) {
              continue;
            }

            String blipText = extractTextFromBlip(blip)
                .toLowerCase(java.util.Locale.ROOT);
            if (blipText.isEmpty()) {
              continue;
            }

            boolean allTermsMatch = true;
            for (String term : lowerTerms) {
              if (!blipText.contains(term)) {
                allTermsMatch = false;
                break;
              }
            }
            if (allTermsMatch) {
              waveMatches = true;
              break; // Short-circuit: one matching blip is enough.
            }
          }

          if (waveMatches) {
            break; // Short-circuit: no need to check other wavelets.
          }
        }

        if (!waveMatches) {
          it.remove();
        }
      } catch (Exception e) {
        LOG.warning("Failed to check content for wave " + wave.getWaveId(), e);
        it.remove();
      }
    }
  }

  /**
   * Extracts plain text content from a blip document by walking its DocOp
   * representation using a DocInitializationCursor and StringBuilder.
   *
   * <p>CRITICAL: characters() calls can be split by annotationBoundary()
   * events, so we always use StringBuilder to accumulate text rather than
   * relying on individual characters() calls.
   *
   * @param blip the blip data to extract text from.
   * @return the plain text content of the blip.
   */
  private static String extractTextFromBlip(
      org.waveprotocol.wave.model.wave.data.ReadableBlipData blip) {
    org.waveprotocol.wave.model.document.operation.DocInitialization docOp =
        blip.getContent().asOperation();

    final StringBuilder textBuilder = new StringBuilder();
    docOp.apply(new org.waveprotocol.wave.model.document.operation.DocInitializationCursor() {
      @Override
      public void elementStart(String type,
          org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        // Insert a space for line elements to separate words across lines.
        if ("line".equals(type) && textBuilder.length() > 0) {
          textBuilder.append(' ');
        }
      }

      @Override
      public void elementEnd() {
        // No action needed.
      }

      @Override
      public void characters(String chars) {
        if (chars != null) {
          textBuilder.append(chars);
        }
      }

      @Override
      public void annotationBoundary(
          org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {
        // Annotation boundaries can appear between characters() calls within
        // the same element. We simply ignore them and keep accumulating text.
      }
    });
    return textBuilder.toString().trim();
  }

  /**
   * Reads the root blip ID from a wavelet's conversation manifest document.
   * The manifest has the form:
   * <pre>{@code <conversation><blip id="b+xyz">...</blip>...</conversation>}</pre>
   * The first {@code <blip>} element's {@code id} attribute is the root blip.
   *
   * @param waveletData the conversation root wavelet data.
   * @return the root blip document ID, or null if not found.
   */
  private static String getRootBlipId(ObservableWaveletData waveletData) {
    org.waveprotocol.wave.model.wave.data.ReadableBlipData manifestDoc =
        waveletData.getDocument(
            org.waveprotocol.box.common.DocumentConstants.MANIFEST_DOCUMENT_ID);
    if (manifestDoc == null) {
      return null;
    }

    org.waveprotocol.wave.model.document.operation.DocInitialization docOp =
        manifestDoc.getContent().asOperation();

    final String[] rootBlipId = {null};
    docOp.apply(new org.waveprotocol.wave.model.document.operation.DocInitializationCursor() {
      @Override
      public void elementStart(String type,
          org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        // Only capture the first blip element's ID.
        if (rootBlipId[0] == null
            && org.waveprotocol.box.common.DocumentConstants.BLIP.equals(type)
            && attrs != null) {
          String id = attrs.get(org.waveprotocol.box.common.DocumentConstants.BLIP_ID);
          if (id != null) {
            rootBlipId[0] = id;
          }
        }
      }

      @Override
      public void elementEnd() {
        // No action needed.
      }

      @Override
      public void characters(String chars) {
        // No characters expected in manifest.
      }

      @Override
      public void annotationBoundary(
          org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {
        // Ignore.
      }
    });

    return rootBlipId[0];
  }

  /**
   * Reads tag names from a wavelet's tags data document by walking the DocOp
   * representation. This avoids the need to build a conversation model or
   * initialise the document's mutable view -- both of which can fail for
   * edge-case wavelets (missing manifest, uninitialised sinks, etc.).
   *
   * <p>The tags document has the form:
   * <pre>{@code <tag>name1</tag><tag>name2</tag> ...}</pre>
   * In DocOp terms this is: elementStart("tag") + characters("name") + elementEnd("tag"),
   * repeated once per tag.
   *
   * <p>Note: annotation boundaries in the DocInitialization can split a single
   * tag's text into multiple {@code characters()} calls, so we must accumulate
   * text with a StringBuilder until the closing elementEnd().
   */
  private Set<String> readTagsFromWaveletData(ObservableWaveletData waveletData) {
    org.waveprotocol.wave.model.wave.data.ReadableBlipData tagsBlip =
        waveletData.getDocument(org.waveprotocol.wave.model.id.IdConstants.TAGS_DOC_ID);
    if (tagsBlip == null) {
      LOG.info("readTagsFromWaveletData: wave " + waveletData.getWaveId()
          + " has no tags document");
      return java.util.Collections.emptySet();
    }
    // The content of a BlipData is a DocumentOperationSink which implements
    // DocOp.IsDocOp -- its asOperation() yields the DocInitialization that
    // describes the full document content.
    org.waveprotocol.wave.model.document.operation.DocInitialization docOp =
        tagsBlip.getContent().asOperation();

    final Set<String> tags = new java.util.HashSet<>();
    final boolean[] insideTag = {false};
    final StringBuilder tagText = new StringBuilder();
    docOp.apply(new org.waveprotocol.wave.model.document.operation.DocInitializationCursor() {
      @Override
      public void elementStart(String type, org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        if ("tag".equals(type)) {
          insideTag[0] = true;
          tagText.setLength(0);
        }
      }

      @Override
      public void elementEnd() {
        if (insideTag[0]) {
          String text = tagText.toString().trim();
          if (!text.isEmpty()) {
            tags.add(text);
          }
          insideTag[0] = false;
        }
      }

      @Override
      public void characters(String chars) {
        if (insideTag[0] && chars != null) {
          tagText.append(chars);
        }
      }

      @Override
      public void annotationBoundary(org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {
        // Annotation boundaries can appear between characters() calls within
        // the same element. We simply ignore them and keep accumulating text.
      }
    });
    LOG.info("readTagsFromWaveletData: wave " + waveletData.getWaveId()
        + " tags = " + tags);
    return tags;
  }

  private FolderState readFolderState(WaveViewData wave, ParticipantId user) {
    ObservableWaveletData userDataWavelet = null;
    List<ObservableWaveletData> conversationalWavelets = new ArrayList<ObservableWaveletData>();
    for (ObservableWaveletData waveletData : wave.getWavelets()) {
      WaveletId waveletId = waveletData.getWaveletId();
      if (IdUtil.isUserDataWavelet(user.getAddress(), waveletId)) {
        userDataWavelet = waveletData;
      } else if (IdUtil.isConversationalId(waveletId)) {
        conversationalWavelets.add(waveletData);
      }
    }
    return readFolderStateFromUdw(userDataWavelet, conversationalWavelets);
  }

  private static FolderState readFolderStateFromUdw(ObservableWaveletData userDataWavelet,
      List<ObservableWaveletData> conversationalWavelets) {
    if (readBooleanStateFromUdw(
        userDataWavelet,
        org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.MUTED_DOCUMENT,
        org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.MUTED_TAG,
        org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.MUTED_ATTR)) {
      return FolderState.MUTE;
    }
    if (conversationalWavelets.isEmpty()) {
      return FolderState.INBOX;
    }
    if (readBooleanStateFromUdw(
        userDataWavelet,
        org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.CLEARED_DOCUMENT,
        org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.CLEARED_TAG,
        org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.CLEARED_ATTR)) {
      return FolderState.INBOX;
    }
    Map<String, Integer> archiveVersions = readArchiveVersionsFromUdw(userDataWavelet);
    for (ObservableWaveletData conversationalWavelet : conversationalWavelets) {
      Integer archivedVersion =
          archiveVersions.get(WaveletIdSerializer.INSTANCE.toString(conversationalWavelet.getWaveletId()));
      if (archivedVersion == null
          || archivedVersion.intValue() < (int) conversationalWavelet.getVersion()) {
        return FolderState.INBOX;
      }
    }
    return FolderState.ARCHIVE;
  }

  private static boolean readBooleanStateFromUdw(ObservableWaveletData userDataWavelet,
      String documentId, String tagName, String attrName) {
    if (userDataWavelet == null) {
      return false;
    }
    org.waveprotocol.wave.model.wave.data.ReadableBlipData stateDocument =
        userDataWavelet.getDocument(documentId);
    if (stateDocument == null) {
      return false;
    }
    org.waveprotocol.wave.model.document.operation.DocInitialization docOp =
        stateDocument.getContent().asOperation();
    final boolean[] state = {false};
    docOp.apply(new org.waveprotocol.wave.model.document.operation.DocInitializationCursor() {
      @Override
      public void elementStart(String type,
          org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        if (tagName.equals(type)) {
          String attrValue = attrs == null ? null : attrs.get(attrName);
          state[0] = attrValue == null || Boolean.parseBoolean(attrValue);
        }
      }

      @Override
      public void elementEnd() {
      }

      @Override
      public void characters(String chars) {
      }

      @Override
      public void annotationBoundary(
          org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {
      }
    });
    return state[0];
  }

  private static Map<String, Integer> readArchiveVersionsFromUdw(
      ObservableWaveletData userDataWavelet) {
    Map<String, Integer> archiveVersions = new HashMap<String, Integer>();
    if (userDataWavelet == null) {
      return archiveVersions;
    }
    org.waveprotocol.wave.model.wave.data.ReadableBlipData archiveDocument =
        userDataWavelet.getDocument(
            org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.ARCHIVING_DOCUMENT);
    if (archiveDocument == null) {
      return archiveVersions;
    }
    org.waveprotocol.wave.model.document.operation.DocInitialization docOp =
        archiveDocument.getContent().asOperation();
    docOp.apply(new org.waveprotocol.wave.model.document.operation.DocInitializationCursor() {
      @Override
      public void elementStart(String type,
          org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        if (org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.ARCHIVE_TAG.equals(type)
            && attrs != null) {
          String waveletId =
              attrs.get(org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.ID_ATTR);
          String versionValue =
              attrs.get(org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.VERSION_ATTR);
          if (waveletId != null && versionValue != null) {
            archiveVersions.put(waveletId, Integer.valueOf(versionValue));
          }
        }
      }

      @Override
      public void elementEnd() {
      }

      @Override
      public void characters(String chars) {
      }

      @Override
      public void annotationBoundary(
          org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {
      }
    });
    return archiveVersions;
  }

  /**
   * Reads the pinned state from a user-data wavelet's folder document
   * ({@code m/folder}) by walking the DocOp representation.  This avoids
   * building a full conversation model and supplement chain — the same
   * approach used by {@link #readTagsFromWaveletData}.
   *
   * <p>The folder document has the form:
   * <pre>{@code <folder i="9"/><folder i="1"/> ...}</pre>
   * A wave is pinned when the document contains a {@code <folder>} element
   * whose {@code i} attribute equals {@code "9"} (the PINNED_FOLDER id).
   */
  private static boolean readPinnedStateFromUdw(ObservableWaveletData udwData) {
    org.waveprotocol.wave.model.wave.data.ReadableBlipData folderDoc =
        udwData.getDocument(
            org.waveprotocol.wave.model.supplement.WaveletBasedSupplement.FOLDERS_DOCUMENT);
    if (folderDoc == null) {
      return false;
    }
    org.waveprotocol.wave.model.document.operation.DocInitialization docOp =
        folderDoc.getContent().asOperation();

    final String pinFolderId =
        String.valueOf(SupplementedWaveImpl.PINNED_FOLDER);
    final boolean[] found = {false};
    docOp.apply(new org.waveprotocol.wave.model.document.operation.DocInitializationCursor() {
      @Override
      public void elementStart(String type,
          org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        if ("folder".equals(type) && attrs != null && pinFolderId.equals(attrs.get("i"))) {
          found[0] = true;
        }
      }

      @Override
      public void elementEnd() {
        // ignore
      }

      @Override
      public void characters(String chars) {
        // ignore
      }

      @Override
      public void annotationBoundary(
          org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {
        // ignore
      }
    });
    return found[0];
  }

  private List<WaveViewData> sort(Map<TokenQueryType, Set<String>> queryParams,
      List<WaveViewData> results) {
    return QueryHelper.computeSorter(queryParams).sortedCopy(results);
  }
}
