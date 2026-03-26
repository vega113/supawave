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
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.rpc.render.ServerHtmlRenderer;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.conversation.Conversation;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationThread;
import org.waveprotocol.wave.model.conversation.ConversationView;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.TitleHelper;
import org.waveprotocol.wave.model.conversation.WaveBasedConversationView;
import org.waveprotocol.wave.model.conversation.WaveletBasedConversation;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ReadOnlyWaveView;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;
import org.waveprotocol.wave.util.logging.Log;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Render API servlet that returns rendered wave content as JSON or full HTML.
 *
 * <p>Mounted at {@code /render/*}. The path after {@code /render/} is interpreted
 * as a wave ID in the form {@code domain/w+id} (e.g.,
 * {@code /render/supawave.ai/w+abc123}).
 *
 * <p>Supported query parameters:
 * <ul>
 *   <li>{@code format=json} (default) -- returns a JSON object with rendered blips</li>
 *   <li>{@code format=html} -- returns a full HTML page</li>
 *   <li>{@code blipId=b+xxx} -- render only the specified blip</li>
 * </ul>
 *
 * <p>Requires authentication. Returns 401 if the user is not logged in,
 * 403 if they lack access, 404 if the wave is not found.
 *
 * @see ServerHtmlRenderer
 */
@SuppressWarnings("serial")
public final class RenderApiServlet extends HttpServlet {
  private static final Log LOG = Log.get(RenderApiServlet.class);

  private final WaveletProvider waveletProvider;
  private final SessionManager sessionManager;

  @Inject
  public RenderApiServlet(WaveletProvider waveletProvider, SessionManager sessionManager) {
    this.waveletProvider = waveletProvider;
    this.sessionManager = sessionManager;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
      return;
    }

    // Parse wave ID from path: /render/domain/w+id
    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.length() <= 1) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing wave ID in path");
      return;
    }
    // Strip leading slash
    String waveIdStr = pathInfo.substring(1);

    WaveId waveId;
    try {
      waveId = ModernIdSerialiser.INSTANCE.deserialiseWaveId(waveIdStr);
    } catch (Exception e) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid wave ID: " + waveIdStr);
      return;
    }

    // Get the conv+root wavelet
    WaveletId convRootId = WaveletId.of(waveId.getDomain(), "conv+root");
    WaveletName waveletName = WaveletName.of(waveId, convRootId);

    CommittedWaveletSnapshot committedSnapshot;
    try {
      if (!waveletProvider.checkAccessPermission(waveletName, user)) {
        resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      committedSnapshot = waveletProvider.getSnapshot(waveletName);
    } catch (WaveServerException e) {
      LOG.warning("Error fetching wave for render: " + waveId, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    if (committedSnapshot == null) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Wave not found");
      return;
    }

    // Build WaveViewData from the snapshot
    ObservableWaveletData waveletData = WaveletDataUtil.copyWavelet(committedSnapshot.snapshot);
    WaveViewDataImpl waveViewData = WaveViewDataImpl.create(waveId);
    waveViewData.addWavelet(waveletData);

    // Read query params
    String format = req.getParameter("format");
    if (format == null || format.isEmpty()) {
      format = "json";
    }
    String blipIdFilter = req.getParameter("blipId");

    if ("html".equalsIgnoreCase(format)) {
      renderHtml(waveViewData, user, resp);
    } else {
      renderJson(waveViewData, waveId, user, blipIdFilter, resp);
    }
  }

  /**
   * Renders the wave as a full HTML page using ServerHtmlRenderer.
   */
  private void renderHtml(WaveViewData waveViewData, ParticipantId viewer,
      HttpServletResponse resp) throws IOException {
    String html = ServerHtmlRenderer.renderWavePage(waveViewData, viewer);
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/html; charset=UTF-8");
    resp.setHeader("Cache-Control", "no-store");
    try (Writer w = resp.getWriter()) {
      w.write(html);
    }
  }

  /**
   * Renders the wave as a JSON object with blip-level detail.
   */
  private void renderJson(WaveViewData waveViewData, WaveId waveId,
      ParticipantId viewer, String blipIdFilter,
      HttpServletResponse resp) throws IOException {

    // Build conversation model for structured traversal
    ObservableWaveletData convWaveletData = null;
    for (ObservableWaveletData wd : waveViewData.getWavelets()) {
      if (IdUtil.isConversationRootWaveletId(wd.getWaveletId())
          || IdUtil.isConversationalId(wd.getWaveletId())) {
        convWaveletData = wd;
        break;
      }
    }

    if (convWaveletData == null) {
      writeJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "No conversation data found");
      return;
    }

    OpBasedWavelet wavelet = OpBasedWavelet.createReadOnly(convWaveletData);
    if (!WaveletBasedConversation.waveletHasConversation(wavelet)) {
      writeJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "No conversation in wavelet");
      return;
    }

    ReadOnlyWaveView wv = new ReadOnlyWaveView(waveId);
    wv.addWavelet(wavelet);

    IdGenerator readOnlyIdGen = new NoOpIdGenerator();
    ObservableConversationView conversations = WaveBasedConversationView.create(wv, readOnlyIdGen);
    Conversation root = conversations.getRoot();
    if (root == null) {
      writeJsonError(resp, HttpServletResponse.SC_NOT_FOUND, "No root conversation");
      return;
    }

    // Extract title
    String title = "";
    ConversationThread rootThread = root.getRootThread();
    if (rootThread != null) {
      ConversationBlip firstBlip = rootThread.getFirstBlip();
      if (firstBlip != null) {
        Document doc = firstBlip.getContent();
        if (doc != null) {
          title = TitleHelper.extractTitle(doc);
          if (title == null) {
            title = "";
          }
        }
      }
    }

    // Build participant list
    Set<ParticipantId> participantIds = root.getParticipantIds();
    List<String> participants = new ArrayList<>();
    for (ParticipantId pid : participantIds) {
      participants.add(pid.getAddress());
    }

    // Build blip tree
    ServerHtmlRenderer renderer = new ServerHtmlRenderer(viewer);
    String renderedAt = DateTimeFormatter.ISO_INSTANT
        .format(Instant.now().atOffset(ZoneOffset.UTC));

    // Build JSON manually (no external JSON library dependency needed)
    StringBuilder json = new StringBuilder();
    json.append("{");
    json.append("\"waveId\":").append(jsonString(
        ModernIdSerialiser.INSTANCE.serialiseWaveId(waveId)));
    json.append(",\"title\":").append(jsonString(title));
    json.append(",\"participants\":[");
    for (int i = 0; i < participants.size(); i++) {
      if (i > 0) json.append(",");
      json.append(jsonString(participants.get(i)));
    }
    json.append("]");

    json.append(",\"blips\":[");
    if (rootThread != null) {
      boolean first = true;
      for (ConversationBlip blip : rootThread.getBlips()) {
        if (blipIdFilter != null && !blipIdFilter.isEmpty()
            && !blipIdFilter.equals(blip.getId())) {
          continue;
        }
        if (!first) json.append(",");
        first = false;
        renderBlipJson(blip, renderer, json);
      }
    }
    json.append("]");

    json.append(",\"renderedAt\":").append(jsonString(renderedAt));
    json.append("}");

    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("application/json; charset=UTF-8");
    resp.setHeader("Cache-Control", "no-store");
    try (Writer w = resp.getWriter()) {
      w.write(json.toString());
    }
  }

  /**
   * Renders a single blip as a JSON object, recursively including reply threads.
   */
  private void renderBlipJson(ConversationBlip blip, ServerHtmlRenderer renderer,
      StringBuilder json) {
    Document doc = blip.getContent();
    String html = "";
    if (doc != null) {
      html = ServerHtmlRenderer.renderDocument(doc);
    }

    ParticipantId author = blip.getAuthorId();
    String authorAddr = author != null ? author.getAddress() : "unknown";
    long timestamp = blip.getLastModifiedTime();

    json.append("{");
    json.append("\"id\":").append(jsonString(blip.getId()));
    json.append(",\"author\":").append(jsonString(authorAddr));
    json.append(",\"timestamp\":").append(timestamp);
    json.append(",\"html\":").append(jsonString(html));

    // Render reply threads
    json.append(",\"replies\":[");
    boolean firstThread = true;
    for (ConversationThread thread : blip.getReplyThreads()) {
      boolean firstBlip = true;
      for (ConversationBlip replyBlip : thread.getBlips()) {
        if (!firstThread || !firstBlip) json.append(",");
        firstThread = false;
        firstBlip = false;
        renderBlipJson(replyBlip, renderer, json);
      }
    }
    json.append("]");

    json.append("}");
  }

  /**
   * Writes a JSON error response.
   */
  private void writeJsonError(HttpServletResponse resp, int status, String message)
      throws IOException {
    resp.setStatus(status);
    resp.setContentType("application/json; charset=UTF-8");
    resp.setHeader("Cache-Control", "no-store");
    try (Writer w = resp.getWriter()) {
      w.write("{\"error\":" + jsonString(message) + "}");
    }
  }

  /**
   * Encodes a string as a JSON string literal with proper escaping.
   */
  static String jsonString(String value) {
    if (value == null) {
      return "null";
    }
    StringBuilder sb = new StringBuilder(value.length() + 2);
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"':  sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\n': sb.append("\\n");  break;
        case '\r': sb.append("\\r");  break;
        case '\t': sb.append("\\t");  break;
        case '\b': sb.append("\\b");  break;
        case '\f': sb.append("\\f");  break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
    return sb.toString();
  }

  /**
   * A minimal {@link IdGenerator} that throws on any attempt to generate IDs.
   * Used when constructing a read-only conversation view for rendering.
   */
  private static final class NoOpIdGenerator implements IdGenerator {
    @Override public WaveId newWaveId() {
      throw new UnsupportedOperationException("Read-only");
    }
    @Override public WaveletId newConversationWaveletId() {
      throw new UnsupportedOperationException("Read-only");
    }
    @Override public WaveletId newConversationRootWaveletId() {
      throw new UnsupportedOperationException("Read-only");
    }
    @Override public WaveletId buildConversationRootWaveletId(WaveId waveId) {
      throw new UnsupportedOperationException("Read-only");
    }
    @Override public WaveletId newUserDataWaveletId(String address) {
      throw new UnsupportedOperationException("Read-only");
    }
    @Override public String newUniqueToken() {
      throw new UnsupportedOperationException("Read-only");
    }
    @Override public String newDataDocumentId() {
      throw new UnsupportedOperationException("Read-only");
    }
    @Override public String newBlipId() {
      throw new UnsupportedOperationException("Read-only");
    }
    @Override public @Deprecated String peekBlipId() {
      throw new UnsupportedOperationException("Read-only");
    }
    @Override public String newId(String namespace) {
      throw new UnsupportedOperationException("Read-only");
    }
    @Override public String getDefaultDomain() {
      return "local.net";
    }
  }
}
