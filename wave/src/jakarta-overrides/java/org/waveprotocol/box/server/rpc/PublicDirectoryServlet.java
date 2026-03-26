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
package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.wave.api.OperationQueue;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.SearchResult;
import com.google.wave.api.data.converter.EventDataConverterManager;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Server-rendered HTML directory of public waves at {@code /public}.
 * No authentication required. Uses the search API internally with
 * the shared domain participant to find public waves.
 *
 * <p>Supports server-side pagination via {@code ?page=N} (default 1),
 * showing 20 waves per page.
 */
@Singleton
public final class PublicDirectoryServlet extends HttpServlet {

  private static final Log LOG = Log.get(PublicDirectoryServlet.class);
  private static final int WAVES_PER_PAGE = 20;

  private final String waveDomain;
  private final String siteUrl;
  private final WaveletProvider waveletProvider;
  private final EventDataConverterManager converterManager;
  private final OperationServiceRegistry operationRegistry;
  private final ConversationUtil conversationUtil;
  private final ParticipantId sharedDomainParticipant;

  @Inject
  public PublicDirectoryServlet(
      Config config,
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain,
      WaveletProvider waveletProvider,
      EventDataConverterManager converterManager,
      @Named("DataApiRegistry") OperationServiceRegistry operationRegistry,
      ConversationUtil conversationUtil) {
    this.waveDomain = waveDomain;
    this.siteUrl = RobotsServlet.resolveSiteUrl(config);
    this.waveletProvider = waveletProvider;
    this.converterManager = converterManager;
    this.operationRegistry = operationRegistry;
    this.conversationUtil = conversationUtil;
    this.sharedDomainParticipant =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    int page = 1;
    String pageParam = req.getParameter("page");
    if (pageParam != null) {
      try {
        page = Math.max(1, Integer.parseInt(pageParam.trim()));
      } catch (NumberFormatException ignored) {
        // fall through to default
      }
    }

    int startAt = (page - 1) * WAVES_PER_PAGE;

    // Search for public waves using the domain participant query
    String query = "with:@" + waveDomain;
    SearchResult searchResult = performSearch(query, startAt, WAVES_PER_PAGE);

    List<SearchResult.Digest> digests = searchResult.getDigests();
    int totalResults = searchResult.getTotalResults();
    boolean hasNextPage = digests.size() >= WAVES_PER_PAGE;
    boolean hasPrevPage = page > 1;

    String html = HtmlRenderer.renderPublicDirectoryPage(
        waveDomain, digests, page, hasNextPage, hasPrevPage, totalResults);

    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/html; charset=UTF-8");
    resp.setHeader("Cache-Control", "public, max-age=120");
    resp.getWriter().write(html);
  }

  /**
   * Performs a search query as the shared domain participant.
   */
  private SearchResult performSearch(String query, int startAt, int numResults) {
    OperationQueue opQueue = new OperationQueue();
    opQueue.search(query, startAt, numResults);
    OperationContextImpl context = new OperationContextImpl(
        waveletProvider,
        converterManager.getEventDataConverter(ProtocolVersion.DEFAULT),
        conversationUtil);
    OperationRequest operationRequest = opQueue.getPendingOperations().get(0);
    String opId = operationRequest.getId();
    OperationUtil.executeOperation(
        operationRequest, operationRegistry, context, sharedDomainParticipant);
    JsonRpcResponse jsonRpcResponse = context.getResponses().get(opId);
    SearchResult result =
        (SearchResult) jsonRpcResponse.getData().get(ParamsProperty.SEARCH_RESULTS);
    return result != null ? result : new SearchResult(query);
  }
}
