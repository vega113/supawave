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

import com.google.protobuf.Message;
import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.JsonRpcResponse;
import com.google.wave.api.OperationQueue;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.SearchResult;
import com.google.wave.api.data.converter.EventDataConverterManager;
import org.waveprotocol.box.search.SearchProto.SearchRequest;
import org.waveprotocol.box.search.SearchProto.SearchResponse;
import org.waveprotocol.box.search.SearchProto.SearchResponse.Builder;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.robots.OperationContextImpl;
import org.waveprotocol.box.server.robots.OperationServiceRegistry;
import org.waveprotocol.box.server.robots.util.ConversationUtil;
import org.waveprotocol.box.server.robots.util.OperationUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.wave.util.logging.Log;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@SuppressWarnings("serial")
public class SearchServlet extends HttpServlet {
  private static final Log LOG = Log.get(SearchServlet.class);
  private static final int MIN_INDEX = 0;
  private static final int MAX_INDEX = 1_000_000; // hard upper bound to avoid pathological requests
  private static final int MIN_NUM_RESULTS = 1;
  private static final int MAX_NUM_RESULTS = 100; // fixed upper limit of returned digests

  private final SessionManager sessionManager;
  private final EventDataConverterManager converterManager;
  private final OperationServiceRegistry operationRegistry;
  private final WaveletProvider waveletProvider;
  private final ConversationUtil conversationUtil;
  private final ProtoSerializer serializer;

  public SearchServlet(SessionManager sessionManager, EventDataConverterManager converterManager,
                       OperationServiceRegistry operationRegistry, WaveletProvider waveletProvider,
                       ConversationUtil conversationUtil, ProtoSerializer serializer) {
    this.sessionManager = sessionManager;
    this.converterManager = converterManager;
    this.operationRegistry = operationRegistry;
    this.waveletProvider = waveletProvider;
    this.conversationUtil = conversationUtil;
    this.serializer = serializer;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) { response.setStatus(HttpServletResponse.SC_FORBIDDEN); return; }
    SearchRequest searchRequest;
    try {
      searchRequest = parseSearchRequest(req);
    } catch (IllegalArgumentException ex) {
      // Invalid user input: return 400 with a short reason
      LOG.warning("Invalid search parameters: " + ex.getMessage());
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
      return;
    }
    if (searchRequest == null) {
      // Defensive: should not happen, but avoid NPEs if code changes later
      LOG.warning("Invalid search parameters: null request");
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid search request");
      return;
    }
    SearchResult searchResult = performSearch(searchRequest, user);
    int totalGuess = computeTotalResultsNumberGuess(searchRequest, searchResult);
    SearchResponse searchResponse = serializeSearchResult(searchResult, totalGuess);
    String ctx = "user=" + user.getAddress() + ", query=\"" + searchRequest.getQuery() +
        "\", index=" + searchRequest.getIndex() + ", numResults=" + searchRequest.getNumResults() +
        ", remote=" + String.valueOf(req.getRemoteAddr());
    serializeObjectToServlet(searchResponse, ctx, response);
  }

  private static SearchRequest parseSearchRequest(HttpServletRequest req) {
    String query = param(req, "query", "");
    int index = parseIntOrThrow(param(req, "index", "0"), "index");
    int num = parseIntOrThrow(param(req, "numResults", String.valueOf(MIN_NUM_RESULTS)), "numResults");
    // Clamp numeric-but-out-of-range with a trace log
    if (index < MIN_INDEX || index > MAX_INDEX) {
      int clamped = Math.max(MIN_INDEX, Math.min(index, MAX_INDEX));
      if (index != clamped && LOG.isFineLoggable()) {
        LOG.fine("Clamping index from " + index + " to " + clamped);
      }
      index = clamped;
    }
    if (num < MIN_NUM_RESULTS || num > MAX_NUM_RESULTS) {
      int clamped = Math.max(MIN_NUM_RESULTS, Math.min(num, MAX_NUM_RESULTS));
      if (num != clamped && LOG.isFineLoggable()) {
        LOG.fine("Clamping numResults from " + num + " to " + clamped);
      }
      num = clamped;
    }
    return SearchRequest.newBuilder().setQuery(query).setIndex(index).setNumResults(num).build();
  }

  private static String param(HttpServletRequest req, String name, String def) {
    String v = req.getParameter(name);
    return v == null ? def : v;
  }

  private static int parseIntOrThrow(String raw, String name) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (Exception e) {
      throw new IllegalArgumentException(name + " must be an integer");
    }
  }

  protected SearchResult performSearch(SearchRequest searchRequest, ParticipantId user) {
    OperationQueue opQueue = new OperationQueue();
    opQueue.search(searchRequest.getQuery(), searchRequest.getIndex(), searchRequest.getNumResults());
    OperationContextImpl context = new OperationContextImpl(
        waveletProvider, converterManager.getEventDataConverter(ProtocolVersion.DEFAULT), conversationUtil);
    OperationRequest operationRequest = opQueue.getPendingOperations().get(0);
    String opId = operationRequest.getId();
    OperationUtil.executeOperation(operationRequest, operationRegistry, context, user);
    JsonRpcResponse jsonRpcResponse = context.getResponses().get(opId);
    return (SearchResult) jsonRpcResponse.getData().get(ParamsProperty.SEARCH_RESULTS);
  }

  private static int computeTotalResultsNumberGuess(SearchRequest req, SearchResult res) {
    // Use -1 to denote unknown size (avoids depending on client constants)
    return (res.getNumResults() >= req.getNumResults()) ? -1 : req.getIndex() + res.getNumResults();
  }

  public static SearchResponse serializeSearchResult(SearchResult searchResult, int total) {
    Builder searchBuilder = SearchResponse.newBuilder();
    searchBuilder.setQuery(searchResult.getQuery()).setTotalResults(total);
    for (SearchResult.Digest d : searchResult.getDigests()) {
      SearchResponse.Digest.Builder b = SearchResponse.Digest.newBuilder();
      b.setBlipCount(d.getBlipCount());
      b.setLastModified(d.getLastModified());
      b.setSnippet(d.getSnippet());
      b.setTitle(d.getTitle());
      b.setUnreadCount(d.getUnreadCount());
      b.setWaveId(d.getWaveId());
      List<String> parts = d.getParticipants();
      if (parts.isEmpty()) {
        b.setAuthor("nobody@example.com");
      } else {
        b.setAuthor(parts.get(0));
        for (int i = 1; i < parts.size(); i++) b.addParticipants(parts.get(i));
      }
      searchBuilder.addDigests(b.build());
    }
    return searchBuilder.build();
  }

  private <P extends Message> void serializeObjectToServlet(P message, String logContext, HttpServletResponse resp) throws IOException {
    if (message == null) {
      resp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    try {
      String json = serializer.toJson(message).toString();
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("application/json; charset=utf8");
      resp.setHeader("Cache-Control", "no-store");
      try (var w = resp.getWriter()) { w.append(json); w.flush(); }
    } catch (ProtoSerializer.SerializationException e) {
      LOG.severe("Failed to serialize SearchResponse (" + logContext + ")", e);
      if (!resp.isCommitted()) {
        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to serialize search results. Please retry later.");
      }
    }
  }
}
