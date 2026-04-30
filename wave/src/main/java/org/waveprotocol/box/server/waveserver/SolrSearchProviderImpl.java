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
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.wave.api.SearchResult.Digest;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.wave.api.SearchResult;
import com.typesafe.config.Config;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpStatus;
import org.waveprotocol.box.stat.Timed;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Search provider that offers full text search
 *
 * @author Frank R. <renfeng.cn@gmail.com>
 * @author Yuri Zelikov <yurize@apache.com>
 */
public class SolrSearchProviderImpl extends AbstractSearchProviderImpl {

  private static final Log LOG = Log.get(SolrSearchProviderImpl.class);

  private static final String WORD_START = "(\\b|^)";
  private static final Pattern IN_PATTERN = Pattern.compile("\\bin:\\S*");
  private static final Pattern WITH_PATTERN = Pattern.compile("\\bwith:\\S*");

  public static final int ROWS = 10;

  public static final String ID = "id";
  public static final String WAVE_ID = "waveId_s";
  public static final String WAVELET_ID = "waveletId_s";
  public static final String DOC_NAME = "docName_s";
  public static final String LMT = "lmt_l";
  public static final String WITH = "with_ss";
  public static final String WITH_FUZZY = "with_txt";
  public static final String CREATOR = "creator_t";
  public static final String TEXT = "text_t";
  public static final String IN = "in_ss";

  private final String solrBaseUrl;

  /*-
   * http://wiki.apache.org/solr/CommonQueryParameters#q
   */
  public static final String Q = WAVE_ID + ":[* TO *]"
      + " AND " + WAVELET_ID + ":[* TO *]"
      + " AND " + DOC_NAME + ":[* TO *]"
      + " AND " + LMT + ":[* TO *]"
      + " AND " + WITH + ":[* TO *]"
      + " AND " + WITH_FUZZY + ":[* TO *]"
      + " AND " + CREATOR + ":[* TO *]";

  private static final String FILTER_QUERY_PREFIX = "{!lucene q.op=AND df=" + TEXT + "}" //
      + WITH + ":";

  private final static Function<InputStreamReader, JsonArray> extractDocsJsonFunction =
      new Function<InputStreamReader, JsonArray>() {

    @Override
    public JsonArray apply(InputStreamReader inputStreamResponse) {
      return extractDocsJson(inputStreamResponse);
    }};

  @Inject
  public SolrSearchProviderImpl(WaveDigester digester, WaveMap waveMap, Config config) {
    super(config.getString("core.wave_server_domain"), digester, waveMap);
    solrBaseUrl = config.getString("core.solr_base_url");
  }

  @Timed
  @Override
  public SearchResult search(final ParticipantId user, String query, int startAt, int numResults) {

    LOG.fine("Search query '" + query + "' from user: " + user + " [" + startAt + ", "
        + ((startAt + numResults) - 1) + "]");

    Map<TokenQueryType, Set<String>> queryParams;
    try {
      queryParams = QueryHelper.parseQuery(query);
    } catch (QueryHelper.InvalidQueryException e) {
      LOG.warning("Invalid Query. " + e.getMessage());
      return digester.generateSearchResult(user, query, null);
    }

    // Maybe should be changed in case other folders in addition to 'inbox' are
    // added.
    final boolean isAllQuery = isAllQuery(query);
    // J-UI-2 (#1080 / R-4.5): mirror the SimpleSearchProvider — the rail's
    // "Unread only" chip emits `is:unread`; treat it as a synonym for
    // `unread:true` here so the Solr-backed surface filters identically
    // to the in-memory one.
    final boolean isUnreadOnlyQuery =
        queryParams.containsKey(TokenQueryType.UNREAD)
            || QueryHelper.hasIsValue(queryParams, "unread");
    final boolean hasAttachmentQuery = AttachmentSearchFilter.isHasAttachmentQuery(queryParams);
    if (queryParams.containsKey(TokenQueryType.MENTIONS)) {
      LOG.warning("Mentions queries are not supported by Solr search.");
      return new SearchResult(query);
    }

    LinkedHashMultimap<WaveId, WaveletId> currentUserWavesView = LinkedHashMultimap.create();

    if (numResults > 0) {

      int start = computeSolrStart(startAt, isUnreadOnlyQuery, hasAttachmentQuery);
      int rows = Math.max(numResults, ROWS);

      /*-
       * "fq" stands for Filter Query. see
       * http://wiki.apache.org/solr/CommonQueryParameters#fq
       */
      String fq = buildFilterQuery(query, isAllQuery, user.getAddress(), sharedDomainParticipantId);

      try {
        while (true) {
          String solrQuery = buildCurrentSolrQuery(start, rows, fq);

          JsonArray docsJson = sendSearchRequest(solrQuery, extractDocsJsonFunction);

          addSearchResultsToCurrentWaveView(currentUserWavesView, docsJson);
          if (docsJson.size() < rows) {
            break;
          }
          start += rows;
        }
      } catch (Exception e) {
        LOG.warning("Failed to execute query: " + query);
        LOG.warning(e.getMessage());
        return digester.generateSearchResult(user, query, null);
      }
    }

    ensureWavesHaveUserDataWavelet(currentUserWavesView, user);

    LinkedHashMap<WaveId, WaveViewData> results =
        createResults(user, isAllQuery, currentUserWavesView);

    List<WaveViewData> resultsList = Lists.newArrayList(results.values());

    if (hasAttachmentQuery) {
      expandConversationalWavelets(resultsList, user, isAllQuery);
      AttachmentSearchFilter.filterByHasAttachment(resultsList);
    }

    // Solr does not index tags, so perform post-filtering for tag: queries.
    Set<String> tagValues = queryParams.containsKey(TokenQueryType.TAG)
        ? queryParams.get(TokenQueryType.TAG)
        : Collections.<String>emptySet();
    if (!tagValues.isEmpty()) {
      filterByTags(resultsList, tagValues);
    }

    Map<WaveId, Digest> unreadDigestCache = null;
    if (isUnreadOnlyQuery) {
      unreadDigestCache = new LinkedHashMap<WaveId, Digest>();
      filterByUnreadState(resultsList, user, unreadDigestCache);
    }

    Collection<WaveViewData> searchResult =
        computeSearchResult(user, startAt, numResults, resultsList);
    LOG.info("Search response to '" + query + "': " + searchResult.size() + " results, user: "
        + user);

    return unreadDigestCache == null
        ? digester.generateSearchResult(user, query, searchResult)
        : digester.generateSearchResult(user, query, searchResult, unreadDigestCache);
  }

  private LinkedHashMap<WaveId, WaveViewData> createResults(final ParticipantId user,
      final boolean isAllQuery, LinkedHashMultimap<WaveId, WaveletId> currentUserWavesView) {
    Function<ReadableWaveletData, Boolean> matchesFunction =
        new Function<ReadableWaveletData, Boolean>() {

      @Override
      public Boolean apply(ReadableWaveletData wavelet) {
        try {
          return isWaveletMatchesCriteria(wavelet, user, sharedDomainParticipantId, isAllQuery);
        } catch (WaveletStateException e) {
          LOG.warning(
              "Failed to access wavelet "
                  + WaveletName.of(wavelet.getWaveId(), wavelet.getWaveletId()), e);
          return false;
        }
      }
    };

    LinkedHashMap<WaveId, WaveViewData> results =
        filterWavesViewBySearchCriteria(matchesFunction, currentUserWavesView);
    if (LOG.isFineLoggable()) {
      for (Map.Entry<WaveId, WaveViewData> e : results.entrySet()) {
        LOG.fine("filtered results contains: " + e.getKey());
      }
    }
    return results;
  }

  private void expandConversationalWavelets(List<WaveViewData> results,
      final ParticipantId user, final boolean isAllQuery) {
    Function<ReadableWaveletData, Boolean> matchesFunction =
        new Function<ReadableWaveletData, Boolean>() {

      @Override
      public Boolean apply(ReadableWaveletData wavelet) {
        try {
          return isWaveletMatchesCriteria(wavelet, user, sharedDomainParticipantId, isAllQuery);
        } catch (WaveletStateException e) {
          LOG.warning(
              "Failed to access wavelet "
                  + WaveletName.of(wavelet.getWaveId(), wavelet.getWaveletId()), e);
          return false;
        }
      }
    };

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

  private void addSearchResultsToCurrentWaveView(
      LinkedHashMultimap<WaveId, WaveletId> currentUserWavesView, JsonArray docsJson) {
    for (JsonElement aDocsJson : docsJson) {
      JsonObject docJson = aDocsJson.getAsJsonObject();

      WaveId waveId = WaveId.deserialise(docJson.getAsJsonPrimitive(WAVE_ID).getAsString());
      WaveletId waveletId =
              WaveletId.deserialise(docJson.getAsJsonPrimitive(WAVELET_ID).getAsString());
      currentUserWavesView.put(waveId, waveletId);
    }
  }

  private static JsonArray extractDocsJson(InputStreamReader isr) {
    JsonObject json = JsonParser.parseReader(isr).getAsJsonObject();
    JsonObject responseJson = json.getAsJsonObject("response");
    return responseJson.getAsJsonArray("docs");
  }

  private String buildCurrentSolrQuery(int start, int rows, String fq) {
    return solrBaseUrl + "/select?wt=json" + "&start=" + start + "&rows="
        + rows + "&sort=" + LMT + "+desc" + "&q=" + Q + "&fq=" + fq;
  }

  private JsonArray sendSearchRequest(String solrQuery,
      Function<InputStreamReader, JsonArray> function) throws IOException {
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      HttpGet get = new HttpGet(solrQuery);
      try (CloseableHttpResponse resp = client.execute(get)) {
        int status = resp.getStatusLine().getStatusCode();
        JsonArray docsJson = function.apply(new InputStreamReader(resp.getEntity().getContent()));
        if (status != HttpStatus.SC_OK) {
          LOG.warning("Failed to execute query: " + solrQuery);
          throw new IOException("Search request status is not OK: " + status);
        }
        EntityUtils.consumeQuietly(resp.getEntity());
        return docsJson;
      }
    }
  }

  private static final Pattern TAG_PATTERN = Pattern.compile("\\btag:(\\S+)");
  private static final Pattern UNREAD_PATTERN = Pattern.compile("(^|\\s)unread:\\S+(?=\\s|$)");

  /**
   * J-UI-2 (#1080): the rail's filter chips emit {@code is:unread},
   * {@code has:attachment}, and {@code from:me}. {@code is:unread} is
   * handled equivalently to {@code unread:true} via post-filtering, and
   * {@code has:attachment} is post-filtered by attachment metadata docs.
   * {@code from:me} is URL-only this slice (deferred follow-up).
   * In all three cases the token must be stripped before the query is
   * forwarded to Solr — Solr's schema does not know these prefixes and
   * a literal {@code is:unread} term in the user-query clause fails the
   * Lucene parser.
   */
  private static final Pattern CHIP_TOKEN_PATTERN =
      Pattern.compile("(^|\\s)(is|has|from):\\S+(?=\\s|$)", Pattern.CASE_INSENSITIVE);

  private static final Pattern IN_ALL_PATTERN = Pattern.compile("\\bin:all\\b");

  static String stripUnreadFilterTokens(String query) {
    String cleanedQuery = UNREAD_PATTERN.matcher(query).replaceAll("$1");
    cleanedQuery = CHIP_TOKEN_PATTERN.matcher(cleanedQuery).replaceAll("$1");
    return cleanedQuery.replaceAll("\\s{2,}", " ").trim();
  }

  private static boolean isAllQuery(String query) {
    // The query is an "all" query if there is no 'in:' filter, or if
    // the explicit 'in:all' filter is used.
    return !IN_PATTERN.matcher(query).find() || IN_ALL_PATTERN.matcher(query).find();
  }

  static int computeSolrStart(int startAt, boolean isUnreadOnlyQuery, boolean hasAttachmentQuery) {
    return 0;
  }

  private static String buildUserQuery(String query, ParticipantId sharedDomainParticipantId) {
    // Strip tag: tokens from the Solr query since they are handled by post-filtering.
    String cleanedQuery = TAG_PATTERN.matcher(query).replaceAll("").trim();
    cleanedQuery = stripUnreadFilterTokens(cleanedQuery);
    return cleanedQuery.replaceAll(WORD_START + TokenQueryType.IN.getToken() + ":", IN + ":")
        .replaceAll(WORD_START + TokenQueryType.WITH.getToken() + ":@",
            WITH + ":" + sharedDomainParticipantId.getAddress())
        .replaceAll(WORD_START + TokenQueryType.WITH.getToken() + ":", WITH_FUZZY + ":")
        .replaceAll(WORD_START + TokenQueryType.CREATOR.getToken() + ":", CREATOR + ":");
  }

  private static String buildFilterQuery(String query, final boolean isAllQuery,
      String addressOfRequiredParticipant, ParticipantId sharedDomainParticipantId) {

    String fq;
    if (isAllQuery) {
      fq =
          FILTER_QUERY_PREFIX + "(" + addressOfRequiredParticipant + " OR "
              + sharedDomainParticipantId + ")";
    } else {
      fq = FILTER_QUERY_PREFIX + addressOfRequiredParticipant;
    }
    if (query.length() > 0) {
      String userQuery = buildUserQuery(query, sharedDomainParticipantId);
      if (!userQuery.isEmpty()) {
        fq += " AND (" + userQuery + ")";
      }
    }
    return fq;
  }

  /**
   * Filters wave results by tags. Only waves whose conversation root wavelet
   * contains all of the requested tags are kept. Uses case-insensitive matching.
   *
   * @param results the mutable list of wave views to filter in place.
   * @param requiredTags the set of tag names that must all be present.
   */
  private void filterByTags(List<WaveViewData> results, Set<String> requiredTags) {
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
        org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet opWavelet =
            org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet.createReadOnly(convWavelet);
        if (!org.waveprotocol.wave.model.conversation.WaveletBasedConversation
            .waveletHasConversation(opWavelet)) {
          it.remove();
          continue;
        }
        org.waveprotocol.wave.model.conversation.ObservableConversationView conversations =
            digester.getConversationUtil().buildConversation(opWavelet);
        org.waveprotocol.wave.model.conversation.ObservableConversation rootConversation =
            conversations.getRoot();
        if (rootConversation == null) {
          it.remove();
          continue;
        }
        Set<String> waveTags = rootConversation.getTags();
        Set<String> lowerCaseTags = new HashSet<>();
        for (String wt : waveTags) {
          lowerCaseTags.add(wt.toLowerCase(Locale.ROOT));
        }
        for (String requiredTag : requiredTags) {
          if (!lowerCaseTags.contains(requiredTag.toLowerCase(Locale.ROOT))) {
            it.remove();
            break;
          }
        }
      } catch (Exception e) {
        LOG.warning("Failed to check tags for wave " + wave.getWaveId(), e);
        it.remove();
      }
    }
  }
}
