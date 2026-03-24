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
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.common.SnapshotSerializer;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Servlet that serves a version history UI for browsing and diffing
 * historical wave versions. Handles both HTML page delivery and JSON
 * API requests for version data.
 *
 * <p>URL patterns:
 * <ul>
 *   <li>{@code /history/{waveDomain}/{waveId}/{waveletDomain}/{waveletId}} - HTML page</li>
 *   <li>{@code /history/{waveDomain}/{waveId}/{waveletDomain}/{waveletId}/api/info} - current version info</li>
 *   <li>{@code /history/{waveDomain}/{waveId}/{waveletDomain}/{waveletId}/api/history?start=S&end=E} - delta metadata</li>
 *   <li>{@code /history/{waveDomain}/{waveId}/{waveletDomain}/{waveletId}/api/snapshot?version=N} - snapshot at version N</li>
 * </ul>
 */
@SuppressWarnings("serial")
public final class VersionHistoryServlet extends HttpServlet {
  private static final Log LOG = Log.get(VersionHistoryServlet.class);

  private final WaveletProvider waveletProvider;
  private final SessionManager sessionManager;

  @Inject
  public VersionHistoryServlet(WaveletProvider waveletProvider, SessionManager sessionManager) {
    this.waveletProvider = waveletProvider;
    this.sessionManager = sessionManager;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
      return;
    }

    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.length() < 2) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing wave/wavelet path");
      return;
    }

    // Strip leading slash
    String path = pathInfo.substring(1);

    // Check if this is an API call
    if (path.contains("/api/")) {
      handleApiRequest(path, req, resp, user);
    } else {
      handlePageRequest(path, resp, user);
    }
  }

  /**
   * Parses wave/wavelet identifiers from the path prefix.
   * Expected format: {waveDomain}/{waveId}/{waveletDomain}/{waveletId}[/api/...]
   */
  private WaveletName parseWaveletName(String path) {
    // Remove /api/... suffix if present
    String waveletPath = path;
    int apiIdx = waveletPath.indexOf("/api/");
    if (apiIdx >= 0) {
      waveletPath = waveletPath.substring(0, apiIdx);
    }

    String[] parts = waveletPath.split("/");
    if (parts.length < 4) {
      return null;
    }
    try {
      WaveId waveId = WaveId.of(parts[0], parts[1]);
      WaveletId waveletId = WaveletId.of(parts[2], parts[3]);
      return WaveletName.of(waveId, waveletId);
    } catch (Exception e) {
      LOG.warning("Failed to parse wavelet name from path: " + waveletPath, e);
      return null;
    }
  }

  // =========================================================================
  // API Handlers
  // =========================================================================

  private void handleApiRequest(String path, HttpServletRequest req,
      HttpServletResponse resp, ParticipantId user) throws IOException {
    WaveletName waveletName = parseWaveletName(path);
    if (waveletName == null) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid wave/wavelet path");
      return;
    }

    try {
      if (!waveletProvider.checkAccessPermission(waveletName, user)) {
        resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied");
        return;
      }
    } catch (WaveServerException e) {
      LOG.warning("Access check failed for " + waveletName, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    String apiPath = path.substring(path.indexOf("/api/") + 5);
    if (apiPath.startsWith("info")) {
      handleInfoApi(waveletName, resp);
    } else if (apiPath.startsWith("history")) {
      handleHistoryApi(waveletName, req, resp);
    } else if (apiPath.startsWith("snapshot")) {
      handleSnapshotApi(waveletName, req, resp, user);
    } else {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown API: " + apiPath);
    }
  }

  /** Returns current version info as JSON. */
  private void handleInfoApi(WaveletName waveletName, HttpServletResponse resp) throws IOException {
    try {
      CommittedWaveletSnapshot snapshot = waveletProvider.getSnapshot(waveletName);
      if (snapshot == null) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Wavelet not found");
        return;
      }
      ReadableWaveletData data = snapshot.snapshot;
      long version = data.getHashedVersion().getVersion();

      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("application/json");
      resp.setHeader("Cache-Control", "no-store");
      try (PrintWriter w = resp.getWriter()) {
        w.append("{\"version\":").append(String.valueOf(version));
        w.append(",\"creator\":").append(jsonStr(data.getCreator().getAddress()));
        w.append(",\"creationTime\":").append(String.valueOf(data.getCreationTime()));
        w.append(",\"lastModifiedTime\":").append(String.valueOf(data.getLastModifiedTime()));
        w.append("}");
        w.flush();
      }
    } catch (WaveServerException e) {
      LOG.warning("Failed to get info for " + waveletName, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /** Returns delta metadata (version, author, timestamp, opCount) as JSON array. */
  private void handleHistoryApi(WaveletName waveletName, HttpServletRequest req,
      HttpServletResponse resp) throws IOException {
    String startParam = req.getParameter("start");
    String endParam = req.getParameter("end");
    long start = 0;
    long end = Long.MAX_VALUE;
    try {
      if (startParam != null) start = Long.parseLong(startParam);
      if (endParam != null) end = Long.parseLong(endParam);
    } catch (NumberFormatException e) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid start/end");
      return;
    }

    // Get current version to cap the end
    try {
      CommittedWaveletSnapshot snapshot = waveletProvider.getSnapshot(waveletName);
      if (snapshot == null) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Wavelet not found");
        return;
      }
      long currentVersion = snapshot.snapshot.getHashedVersion().getVersion();
      if (end > currentVersion) {
        end = currentVersion;
      }
    } catch (WaveServerException e) {
      LOG.warning("Failed to get snapshot for history endpoint", e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    if (start >= end) {
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("application/json");
      resp.setHeader("Cache-Control", "no-store");
      try (PrintWriter w = resp.getWriter()) {
        w.append("[]");
        w.flush();
      }
      return;
    }

    try {
      HashedVersion startVersion = HashedVersion.unsigned(start);
      HashedVersion endVersion = HashedVersion.unsigned(end);
      List<DeltaInfo> deltas = new ArrayList<>();

      waveletProvider.getHistory(waveletName, startVersion, endVersion, new Receiver<TransformedWaveletDelta>() {
        @Override
        public boolean put(TransformedWaveletDelta delta) {
          deltas.add(new DeltaInfo(
              delta.getAppliedAtVersion(),
              delta.getResultingVersion().getVersion(),
              delta.getAuthor().getAddress(),
              delta.getApplicationTimestamp(),
              delta.size()
          ));
          // Cap at 1000 deltas to prevent memory issues
          return deltas.size() < 1000;
        }
      });

      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("application/json");
      resp.setHeader("Cache-Control", "no-store");
      try (PrintWriter w = resp.getWriter()) {
        w.append("[");
        for (int i = 0; i < deltas.size(); i++) {
          if (i > 0) w.append(",");
          DeltaInfo d = deltas.get(i);
          w.append("{\"appliedAt\":").append(String.valueOf(d.appliedAt));
          w.append(",\"resultingVersion\":").append(String.valueOf(d.resultingVersion));
          w.append(",\"author\":").append(jsonStr(d.author));
          w.append(",\"timestamp\":").append(String.valueOf(d.timestamp));
          w.append(",\"opCount\":").append(String.valueOf(d.opCount));
          w.append("}");
        }
        w.append("]");
        w.flush();
      }
    } catch (WaveServerException e) {
      LOG.warning("Failed to get history for " + waveletName, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /** Returns document content at a specific version as JSON. */
  private void handleSnapshotApi(WaveletName waveletName, HttpServletRequest req,
      HttpServletResponse resp, ParticipantId user) throws IOException {
    String versionParam = req.getParameter("version");
    if (versionParam == null) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing version parameter");
      return;
    }
    long targetVersion;
    try {
      targetVersion = Long.parseLong(versionParam);
    } catch (NumberFormatException e) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid version");
      return;
    }

    try {
      // Get current snapshot first for the wavelet metadata
      CommittedWaveletSnapshot currentSnapshot = waveletProvider.getSnapshot(waveletName);
      if (currentSnapshot == null) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Wavelet not found");
        return;
      }

      long currentVersion = currentSnapshot.snapshot.getHashedVersion().getVersion();
      if (targetVersion < 0 || targetVersion > currentVersion) {
        resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "Version out of range [0, " + currentVersion + "]");
        return;
      }

      // If requesting current version, use the existing snapshot
      if (targetVersion == currentVersion) {
        writeSnapshotJson(currentSnapshot.snapshot, resp);
        return;
      }

      // Build wavelet state at targetVersion by replaying deltas
      if (targetVersion == 0) {
        // Version 0 is the empty state
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json");
        resp.setHeader("Cache-Control", "no-store");
        try (PrintWriter w = resp.getWriter()) {
          w.append("{\"documents\":[],\"participants\":[],\"version\":0}");
          w.flush();
        }
        return;
      }

      // Replay deltas from 0 to targetVersion to reconstruct the state
      HashedVersion startVer = HashedVersion.unsigned(0);
      HashedVersion endVer = HashedVersion.unsigned(targetVersion);
      List<TransformedWaveletDelta> deltaList = new ArrayList<>();

      waveletProvider.getHistory(waveletName, startVer, endVer, new Receiver<TransformedWaveletDelta>() {
        @Override
        public boolean put(TransformedWaveletDelta delta) {
          deltaList.add(delta);
          return true;
        }
      });

      if (deltaList.isEmpty()) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "No deltas found for version " + targetVersion);
        return;
      }

      // Build wavelet from deltas
      ReadableWaveletData waveletData =
          org.waveprotocol.box.server.util.WaveletDataUtil.buildWaveletFromDeltas(
              waveletName, deltaList.iterator());

      writeSnapshotJson(waveletData, resp);

    } catch (WaveServerException e) {
      LOG.warning("Failed to get snapshot at version for " + waveletName, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (org.waveprotocol.wave.model.operation.OperationException e) {
      LOG.warning("Failed to replay deltas for " + waveletName, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /** Writes a wavelet snapshot as JSON, extracting blip text content. */
  private void writeSnapshotJson(ReadableWaveletData waveletData, HttpServletResponse resp)
      throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("application/json");
    resp.setHeader("Cache-Control", "no-store");
    try (PrintWriter w = resp.getWriter()) {
      w.append("{\"version\":").append(String.valueOf(waveletData.getHashedVersion().getVersion()));
      w.append(",\"creator\":").append(jsonStr(waveletData.getCreator().getAddress()));
      w.append(",\"lastModifiedTime\":").append(String.valueOf(waveletData.getLastModifiedTime()));
      w.append(",\"participants\":[");
      boolean firstP = true;
      for (ParticipantId p : waveletData.getParticipants()) {
        if (!firstP) w.append(",");
        w.append(jsonStr(p.getAddress()));
        firstP = false;
      }
      w.append("],\"documents\":[");

      boolean firstDoc = true;
      for (String docId : waveletData.getDocumentIds()) {
        ReadableBlipData blip = waveletData.getDocument(docId);
        if (blip == null) continue;

        // Extract text content from the document operations
        String textContent = extractTextFromBlip(blip);

        if (!firstDoc) w.append(",");
        w.append("{\"id\":").append(jsonStr(docId));
        w.append(",\"author\":").append(jsonStr(blip.getAuthor().getAddress()));
        w.append(",\"lastModified\":").append(String.valueOf(blip.getLastModifiedTime()));
        w.append(",\"content\":").append(jsonStr(textContent));
        w.append("}");
        firstDoc = false;
      }
      w.append("]}");
      w.flush();
    }
  }

  /** Extracts plain text content from a blip's document initialization. */
  private String extractTextFromBlip(ReadableBlipData blip) {
    try {
      DocInitialization docInit = blip.getContent().asOperation();
      // Use DocOpUtil to get XML representation and then extract text
      String xml = DocOpUtil.toXmlString(docInit);
      // Simple XML tag stripping to get plain text
      return stripXmlTags(xml);
    } catch (Exception e) {
      LOG.warning("Failed to extract text from blip " + blip.getId(), e);
      return "";
    }
  }

  /** Strips XML tags and decodes common entities. */
  static String stripXmlTags(String xml) {
    if (xml == null || xml.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(xml.length());
    boolean inTag = false;
    for (int i = 0; i < xml.length(); i++) {
      char c = xml.charAt(i);
      if (c == '<') {
        // Check for </line> or <line/> or <line> to insert newline
        if (i + 1 < xml.length()) {
          String rest = xml.substring(i);
          if (rest.startsWith("<line") || rest.startsWith("</line")) {
            sb.append('\n');
          }
        }
        inTag = true;
      } else if (c == '>') {
        inTag = false;
      } else if (!inTag) {
        sb.append(c);
      }
    }
    // Decode common XML entities
    String result = sb.toString();
    result = result.replace("&amp;", "&");
    result = result.replace("&lt;", "<");
    result = result.replace("&gt;", ">");
    result = result.replace("&quot;", "\"");
    result = result.replace("&#39;", "'");
    return result.trim();
  }

  // =========================================================================
  // HTML Page
  // =========================================================================

  private void handlePageRequest(String path, HttpServletResponse resp, ParticipantId user)
      throws IOException {
    WaveletName waveletName = parseWaveletName(path);
    if (waveletName == null) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid wave/wavelet path. "
          + "Expected: /history/{waveDomain}/{waveId}/{waveletDomain}/{waveletId}");
      return;
    }

    try {
      if (!waveletProvider.checkAccessPermission(waveletName, user)) {
        resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied to this wave");
        return;
      }
    } catch (WaveServerException e) {
      LOG.warning("Access check failed for " + waveletName, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    String waveDomain = waveletName.waveId.getDomain();
    String waveId = waveletName.waveId.getId();
    String waveletDomain = waveletName.waveletId.getDomain();
    String waveletId = waveletName.waveletId.getId();
    String basePath = "/history/" + waveDomain + "/" + waveId + "/" + waveletDomain + "/" + waveletId;

    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/html;charset=UTF-8");
    resp.setHeader("Cache-Control", "no-store");
    try (PrintWriter w = resp.getWriter()) {
      w.append(renderPage(basePath, waveDomain, waveId, waveletDomain, waveletId));
      w.flush();
    }
  }

  // =========================================================================
  // Self-contained HTML/CSS/JS page
  // =========================================================================

  private static String renderPage(String basePath, String waveDomain, String waveId,
      String waveletDomain, String waveletId) {
    String ePath = HtmlRenderer.escapeHtml(basePath);
    String eWaveDomain = HtmlRenderer.escapeHtml(waveDomain);
    String eWaveId = HtmlRenderer.escapeHtml(waveId);
    String eWaveletDomain = HtmlRenderer.escapeHtml(waveletDomain);
    String eWaveletId = HtmlRenderer.escapeHtml(waveletId);

    StringBuilder sb = new StringBuilder(16384);
    sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
    sb.append("<meta charset=\"UTF-8\">\n");
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
    sb.append("<title>Version History - ").append(eWaveDomain).append("/").append(eWaveId).append("</title>\n");
    sb.append("<link rel=\"shortcut icon\" href=\"/static/favicon.ico\">\n");

    // ── CSS ──
    sb.append("<style>\n");
    sb.append("*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }\n");
    sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f0f4f8; color: #1a202c; height: 100vh; overflow: hidden; }\n");
    sb.append(":root { --wave-primary: #2b6cb0; --wave-primary-light: #ebf4ff; --wave-primary-dark: #1a4971; --sidebar-width: 320px; }\n");

    // Top bar
    sb.append(".vh-topbar { display: flex; align-items: center; height: 48px; background: linear-gradient(135deg, #2b6cb0, #2c5282); color: #fff; padding: 0 16px; box-shadow: 0 1px 4px rgba(0,0,0,0.15); z-index: 10; }\n");
    sb.append(".vh-topbar h1 { font-size: 16px; font-weight: 600; flex: 1; }\n");
    sb.append(".vh-topbar a { color: #ebf4ff; text-decoration: none; font-size: 13px; padding: 6px 12px; border-radius: 6px; transition: background 0.15s; }\n");
    sb.append(".vh-topbar a:hover { background: rgba(255,255,255,0.15); }\n");

    // Container
    sb.append(".vh-container { display: flex; height: calc(100vh - 48px); }\n");

    // Sidebar
    sb.append(".vh-sidebar { width: var(--sidebar-width); min-width: var(--sidebar-width); background: #fff; border-right: 1px solid #e2e8f0; display: flex; flex-direction: column; overflow: hidden; }\n");
    sb.append(".vh-sidebar-header { padding: 16px; border-bottom: 1px solid #e2e8f0; }\n");
    sb.append(".vh-sidebar-header h2 { font-size: 14px; font-weight: 600; color: #4a5568; margin-bottom: 4px; }\n");
    sb.append(".vh-info { font-size: 12px; color: #718096; }\n");
    sb.append(".vh-info .version-badge { display: inline-block; background: var(--wave-primary); color: #fff; padding: 1px 8px; border-radius: 10px; font-size: 11px; font-weight: 600; margin-left: 4px; }\n");

    // Timeline
    sb.append(".vh-timeline { flex: 1; overflow-y: auto; padding: 8px 0; }\n");
    sb.append(".vh-entry { display: flex; align-items: flex-start; padding: 10px 16px; cursor: pointer; border-left: 3px solid transparent; transition: all 0.15s; gap: 10px; }\n");
    sb.append(".vh-entry:hover { background: #f7fafc; }\n");
    sb.append(".vh-entry.selected { background: var(--wave-primary-light); border-left-color: var(--wave-primary); }\n");
    sb.append(".vh-avatar { width: 32px; height: 32px; border-radius: 50%; background: #e2e8f0; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; color: #4a5568; flex-shrink: 0; }\n");
    sb.append(".vh-entry.selected .vh-avatar { background: var(--wave-primary); color: #fff; }\n");
    sb.append(".vh-entry-info { flex: 1; min-width: 0; }\n");
    sb.append(".vh-entry-author { font-size: 13px; font-weight: 500; color: #2d3748; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }\n");
    sb.append(".vh-entry-meta { font-size: 11px; color: #a0aec0; margin-top: 2px; }\n");
    sb.append(".vh-entry-version { font-size: 11px; font-weight: 600; color: var(--wave-primary); background: var(--wave-primary-light); padding: 1px 6px; border-radius: 8px; white-space: nowrap; }\n");

    // Content area
    sb.append(".vh-content { flex: 1; display: flex; flex-direction: column; overflow: hidden; }\n");
    sb.append(".vh-toolbar { display: flex; align-items: center; gap: 16px; padding: 10px 20px; background: #fff; border-bottom: 1px solid #e2e8f0; flex-shrink: 0; }\n");
    sb.append("#version-label { font-size: 14px; font-weight: 600; color: #2d3748; }\n");
    sb.append(".vh-toolbar label { font-size: 13px; color: #4a5568; cursor: pointer; display: flex; align-items: center; gap: 6px; user-select: none; }\n");
    sb.append(".vh-toolbar input[type=checkbox] { accent-color: var(--wave-primary); }\n");
    sb.append(".vh-spacer { flex: 1; }\n");

    // Content area
    sb.append("#content-area { flex: 1; overflow-y: auto; padding: 24px; }\n");
    sb.append(".blip { background: #fff; border-radius: 8px; padding: 16px 20px; margin-bottom: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); border: 1px solid #e2e8f0; }\n");
    sb.append(".blip-header { font-size: 11px; color: #a0aec0; margin-bottom: 8px; padding-bottom: 6px; border-bottom: 1px solid #f0f4f8; }\n");
    sb.append(".blip-content { font-size: 14px; line-height: 1.7; color: #2d3748; white-space: pre-wrap; word-wrap: break-word; }\n");

    // Diff styles
    sb.append(".diff-add { background: #c6f6d5; padding: 1px 2px; border-radius: 2px; }\n");
    sb.append(".diff-del { background: #fed7d7; text-decoration: line-through; color: #9b2c2c; padding: 1px 2px; border-radius: 2px; }\n");

    // Loading state
    sb.append(".vh-loading { display: flex; align-items: center; justify-content: center; height: 100%; color: #a0aec0; font-size: 14px; }\n");
    sb.append(".spinner { width: 20px; height: 20px; border: 2px solid #e2e8f0; border-top-color: var(--wave-primary); border-radius: 50%; animation: spin 0.6s linear infinite; margin-right: 10px; }\n");
    sb.append("@keyframes spin { to { transform: rotate(360deg); } }\n");

    // Error state
    sb.append(".vh-error { color: #c53030; background: #fff5f5; padding: 12px 16px; border-radius: 8px; border: 1px solid #fed7d7; margin: 16px; font-size: 13px; }\n");

    // Empty state
    sb.append(".vh-empty { text-align: center; color: #a0aec0; padding: 60px 20px; }\n");
    sb.append(".vh-empty svg { width: 48px; height: 48px; margin-bottom: 12px; opacity: 0.5; }\n");

    // Responsive
    sb.append("@media (max-width: 768px) {\n");
    sb.append("  :root { --sidebar-width: 240px; }\n");
    sb.append("  .vh-topbar h1 { font-size: 14px; }\n");
    sb.append("}\n");
    sb.append("@media (max-width: 600px) {\n");
    sb.append("  .vh-container { flex-direction: column; }\n");
    sb.append("  .vh-sidebar { width: 100%; min-width: 100%; max-height: 40vh; border-right: none; border-bottom: 1px solid #e2e8f0; }\n");
    sb.append("  .vh-content { min-height: 0; }\n");
    sb.append("}\n");
    sb.append("</style>\n");
    sb.append("</head>\n<body>\n");

    // ── Top bar ──
    sb.append("<div class=\"vh-topbar\">\n");
    sb.append("  <h1>Version History</h1>\n");
    sb.append("  <a href=\"/\">&#8592; Back to Wave</a>\n");
    sb.append("</div>\n");

    // ── Container ──
    sb.append("<div class=\"vh-container\">\n");

    // Sidebar
    sb.append("  <div class=\"vh-sidebar\">\n");
    sb.append("    <div class=\"vh-sidebar-header\">\n");
    sb.append("      <h2>").append(eWaveDomain).append("/").append(eWaveId).append("</h2>\n");
    sb.append("      <div class=\"vh-info\" id=\"vh-info\">Loading...</div>\n");
    sb.append("    </div>\n");
    sb.append("    <div class=\"vh-timeline\" id=\"timeline\"></div>\n");
    sb.append("  </div>\n");

    // Content
    sb.append("  <div class=\"vh-content\">\n");
    sb.append("    <div class=\"vh-toolbar\">\n");
    sb.append("      <span id=\"version-label\">Select a version</span>\n");
    sb.append("      <div class=\"vh-spacer\"></div>\n");
    sb.append("      <label><input type=\"checkbox\" id=\"diff-toggle\"> Show changes</label>\n");
    sb.append("    </div>\n");
    sb.append("    <div id=\"content-area\">\n");
    sb.append("      <div class=\"vh-empty\">\n");
    sb.append("        <svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.5\"><path d=\"M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z\"/></svg>\n");
    sb.append("        <p>Select a version from the timeline to view its content</p>\n");
    sb.append("      </div>\n");
    sb.append("    </div>\n");
    sb.append("  </div>\n");
    sb.append("</div>\n");

    // ── JavaScript ──
    sb.append("<script>\n");
    sb.append("(function() {\n");
    sb.append("'use strict';\n\n");

    // Config
    sb.append("var BASE = ").append(HtmlRenderer.escapeJsonString(basePath)).append(";\n");
    sb.append("var selectedVersion = null;\n");
    sb.append("var snapshotCache = {};\n");
    sb.append("var deltaList = [];\n");
    sb.append("var currentMaxVersion = 0;\n\n");

    // Utility: escape HTML to prevent XSS from blip content
    sb.append("function esc(s) {\n");
    sb.append("  if (!s) return '';\n");
    sb.append("  var d = document.createElement('div');\n");
    sb.append("  d.appendChild(document.createTextNode(s));\n");
    sb.append("  return d.innerHTML;\n");
    sb.append("}\n\n");

    // Utility: format timestamp
    sb.append("function fmtTime(ts) {\n");
    sb.append("  if (!ts) return '';\n");
    sb.append("  var d = new Date(ts);\n");
    sb.append("  var now = new Date();\n");
    sb.append("  var opts = { hour: '2-digit', minute: '2-digit' };\n");
    sb.append("  if (d.toDateString() !== now.toDateString()) {\n");
    sb.append("    opts.month = 'short'; opts.day = 'numeric';\n");
    sb.append("    if (d.getFullYear() !== now.getFullYear()) opts.year = 'numeric';\n");
    sb.append("  }\n");
    sb.append("  return d.toLocaleString(undefined, opts);\n");
    sb.append("}\n\n");

    // Utility: author initials
    sb.append("function initials(author) {\n");
    sb.append("  if (!author) return '?';\n");
    sb.append("  var name = author.split('@')[0];\n");
    sb.append("  return name.substring(0, 2).toUpperCase();\n");
    sb.append("}\n\n");

    // Utility: short author name (strip @domain)
    sb.append("function shortAuthor(author) {\n");
    sb.append("  if (!author) return 'Unknown';\n");
    sb.append("  return author.split('@')[0];\n");
    sb.append("}\n\n");

    // Loading indicator
    sb.append("function showLoading(el) {\n");
    sb.append("  el.innerHTML = '<div class=\"vh-loading\"><div class=\"spinner\"></div>Loading...</div>';\n");
    sb.append("}\n\n");

    // Error display
    sb.append("function showError(el, msg) {\n");
    sb.append("  el.innerHTML = '<div class=\"vh-error\">' + esc(msg) + '</div>';\n");
    sb.append("}\n\n");

    // Fetch wrapper
    sb.append("function apiFetch(endpoint, params) {\n");
    sb.append("  var url = BASE + '/api/' + endpoint;\n");
    sb.append("  if (params) {\n");
    sb.append("    var qs = Object.keys(params).map(function(k) { return k + '=' + encodeURIComponent(params[k]); }).join('&');\n");
    sb.append("    url += '?' + qs;\n");
    sb.append("  }\n");
    sb.append("  return fetch(url, { credentials: 'same-origin' }).then(function(r) {\n");
    sb.append("    if (!r.ok) throw new Error('HTTP ' + r.status + ': ' + r.statusText);\n");
    sb.append("    return r.json();\n");
    sb.append("  });\n");
    sb.append("}\n\n");

    // Build timeline
    sb.append("function buildTimeline() {\n");
    sb.append("  var tl = document.getElementById('timeline');\n");
    sb.append("  if (deltaList.length === 0) {\n");
    sb.append("    tl.innerHTML = '<div class=\"vh-empty\"><p>No version history available</p></div>';\n");
    sb.append("    return;\n");
    sb.append("  }\n");
    sb.append("  var html = '';\n");
    // Show in reverse chronological order
    sb.append("  for (var i = deltaList.length - 1; i >= 0; i--) {\n");
    sb.append("    var d = deltaList[i];\n");
    sb.append("    html += '<div class=\"vh-entry\" data-version=\"' + d.resultingVersion + '\" data-idx=\"' + i + '\" onclick=\"window._selectVersion(' + d.resultingVersion + ', ' + i + ')\">';\n");
    sb.append("    html += '<div class=\"vh-avatar\">' + esc(initials(d.author)) + '</div>';\n");
    sb.append("    html += '<div class=\"vh-entry-info\">';\n");
    sb.append("    html += '<div class=\"vh-entry-author\">' + esc(shortAuthor(d.author)) + '</div>';\n");
    sb.append("    html += '<div class=\"vh-entry-meta\">' + fmtTime(d.timestamp) + ' &middot; ' + d.opCount + ' op' + (d.opCount !== 1 ? 's' : '') + '</div>';\n");
    sb.append("    html += '</div>';\n");
    sb.append("    html += '<span class=\"vh-entry-version\">v' + d.resultingVersion + '</span>';\n");
    sb.append("    html += '</div>';\n");
    sb.append("  }\n");
    sb.append("  tl.innerHTML = html;\n");
    sb.append("}\n\n");

    // Select version
    sb.append("window._selectVersion = function(version, idx) {\n");
    sb.append("  selectedVersion = version;\n");
    sb.append("  // Update selected class\n");
    sb.append("  var entries = document.querySelectorAll('.vh-entry');\n");
    sb.append("  for (var i = 0; i < entries.length; i++) {\n");
    sb.append("    entries[i].classList.toggle('selected', parseInt(entries[i].getAttribute('data-version')) === version);\n");
    sb.append("  }\n");
    sb.append("  loadVersion(version);\n");
    sb.append("};\n\n");

    // Load version content
    sb.append("function loadVersion(version) {\n");
    sb.append("  var area = document.getElementById('content-area');\n");
    sb.append("  var label = document.getElementById('version-label');\n");
    sb.append("  label.textContent = 'Version ' + version;\n");
    sb.append("  var diffOn = document.getElementById('diff-toggle').checked;\n\n");
    sb.append("  if (diffOn && version > 0) {\n");
    sb.append("    loadDiff(version, area);\n");
    sb.append("  } else {\n");
    sb.append("    loadSnapshot(version, area);\n");
    sb.append("  }\n");
    sb.append("}\n\n");

    // Load and render a single snapshot
    sb.append("function loadSnapshot(version, area) {\n");
    sb.append("  if (snapshotCache[version]) {\n");
    sb.append("    renderSnapshot(snapshotCache[version], area);\n");
    sb.append("    return;\n");
    sb.append("  }\n");
    sb.append("  showLoading(area);\n");
    sb.append("  apiFetch('snapshot', { version: version }).then(function(data) {\n");
    sb.append("    snapshotCache[version] = data;\n");
    sb.append("    if (selectedVersion === version) renderSnapshot(data, area);\n");
    sb.append("  }).catch(function(e) {\n");
    sb.append("    if (selectedVersion === version) showError(area, 'Failed to load version ' + version + ': ' + e.message);\n");
    sb.append("  });\n");
    sb.append("}\n\n");

    // Render snapshot
    sb.append("function renderSnapshot(data, area) {\n");
    sb.append("  if (!data.documents || data.documents.length === 0) {\n");
    sb.append("    area.innerHTML = '<div class=\"vh-empty\"><p>No document content at this version</p></div>';\n");
    sb.append("    return;\n");
    sb.append("  }\n");
    sb.append("  var html = '';\n");
    sb.append("  for (var i = 0; i < data.documents.length; i++) {\n");
    sb.append("    var doc = data.documents[i];\n");
    sb.append("    // Skip metadata documents (starting with special prefixes)\n");
    sb.append("    if (doc.id && (doc.id.indexOf('b+') !== 0 && doc.id !== 'main')) continue;\n");
    sb.append("    html += '<div class=\"blip\">';\n");
    sb.append("    html += '<div class=\"blip-header\">' + esc(doc.id) + ' &middot; ' + esc(shortAuthor(doc.author)) + '</div>';\n");
    sb.append("    html += '<div class=\"blip-content\">' + esc(doc.content || '') + '</div>';\n");
    sb.append("    html += '</div>';\n");
    sb.append("  }\n");
    sb.append("  if (!html) html = '<div class=\"vh-empty\"><p>No blip content at this version</p></div>';\n");
    sb.append("  area.innerHTML = html;\n");
    sb.append("}\n\n");

    // Load diff between version N and N-1
    sb.append("function loadDiff(version, area) {\n");
    sb.append("  var prevVersion = findPrevVersion(version);\n");
    sb.append("  if (prevVersion < 0) {\n");
    sb.append("    loadSnapshot(version, area);\n");
    sb.append("    return;\n");
    sb.append("  }\n");
    sb.append("  var needed = [];\n");
    sb.append("  if (!snapshotCache[version]) needed.push(apiFetch('snapshot', { version: version }).then(function(d) { snapshotCache[version] = d; }));\n");
    sb.append("  if (!snapshotCache[prevVersion]) needed.push(apiFetch('snapshot', { version: prevVersion }).then(function(d) { snapshotCache[prevVersion] = d; }));\n\n");
    sb.append("  if (needed.length === 0) {\n");
    sb.append("    renderDiff(snapshotCache[prevVersion], snapshotCache[version], area);\n");
    sb.append("    return;\n");
    sb.append("  }\n");
    sb.append("  showLoading(area);\n");
    sb.append("  Promise.all(needed).then(function() {\n");
    sb.append("    if (selectedVersion === version) renderDiff(snapshotCache[prevVersion], snapshotCache[version], area);\n");
    sb.append("  }).catch(function(e) {\n");
    sb.append("    if (selectedVersion === version) showError(area, 'Failed to load diff: ' + e.message);\n");
    sb.append("  });\n");
    sb.append("}\n\n");

    // Find previous version in delta list
    sb.append("function findPrevVersion(version) {\n");
    sb.append("  for (var i = 0; i < deltaList.length; i++) {\n");
    sb.append("    if (deltaList[i].resultingVersion === version) {\n");
    sb.append("      return deltaList[i].appliedAt;\n");
    sb.append("    }\n");
    sb.append("  }\n");
    sb.append("  return -1;\n");
    sb.append("}\n\n");

    // Render diff
    sb.append("function renderDiff(oldSnap, newSnap, area) {\n");
    sb.append("  var oldDocs = buildDocMap(oldSnap);\n");
    sb.append("  var newDocs = buildDocMap(newSnap);\n");
    sb.append("  var allIds = Object.keys(Object.assign({}, oldDocs, newDocs));\n");
    sb.append("  var html = '';\n");
    sb.append("  for (var i = 0; i < allIds.length; i++) {\n");
    sb.append("    var id = allIds[i];\n");
    sb.append("    // Skip non-blip docs\n");
    sb.append("    if (id.indexOf('b+') !== 0 && id !== 'main') continue;\n");
    sb.append("    var oldText = oldDocs[id] || '';\n");
    sb.append("    var newText = newDocs[id] || '';\n");
    sb.append("    if (oldText === newText) continue;\n");
    sb.append("    var diffHtml = wordDiff(oldText, newText);\n");
    sb.append("    html += '<div class=\"blip\">';\n");
    sb.append("    html += '<div class=\"blip-header\">' + esc(id) + '</div>';\n");
    sb.append("    html += '<div class=\"blip-content\">' + diffHtml + '</div>';\n");
    sb.append("    html += '</div>';\n");
    sb.append("  }\n");
    sb.append("  if (!html) html = '<div class=\"vh-empty\"><p>No changes in this version</p></div>';\n");
    sb.append("  area.innerHTML = html;\n");
    sb.append("}\n\n");

    // Build doc map from snapshot
    sb.append("function buildDocMap(snap) {\n");
    sb.append("  var m = {};\n");
    sb.append("  if (snap && snap.documents) {\n");
    sb.append("    for (var i = 0; i < snap.documents.length; i++) {\n");
    sb.append("      m[snap.documents[i].id] = snap.documents[i].content || '';\n");
    sb.append("    }\n");
    sb.append("  }\n");
    sb.append("  return m;\n");
    sb.append("}\n\n");

    // Word-level diff using LCS
    sb.append("function wordDiff(oldText, newText) {\n");
    sb.append("  var oldWords = tokenize(oldText);\n");
    sb.append("  var newWords = tokenize(newText);\n");
    sb.append("  var lcs = computeLCS(oldWords, newWords);\n");
    sb.append("  var result = '';\n");
    sb.append("  var oi = 0, ni = 0, li = 0;\n");
    sb.append("  while (oi < oldWords.length || ni < newWords.length) {\n");
    sb.append("    if (li < lcs.length && oi < oldWords.length && ni < newWords.length && oldWords[oi] === lcs[li] && newWords[ni] === lcs[li]) {\n");
    sb.append("      result += esc(lcs[li]);\n");
    sb.append("      oi++; ni++; li++;\n");
    sb.append("    } else {\n");
    sb.append("      // Emit deletions from old until we match LCS or exhaust old\n");
    sb.append("      while (oi < oldWords.length && (li >= lcs.length || oldWords[oi] !== lcs[li])) {\n");
    sb.append("        result += '<span class=\"diff-del\">' + esc(oldWords[oi]) + '</span>';\n");
    sb.append("        oi++;\n");
    sb.append("      }\n");
    sb.append("      // Emit additions from new until we match LCS or exhaust new\n");
    sb.append("      while (ni < newWords.length && (li >= lcs.length || newWords[ni] !== lcs[li])) {\n");
    sb.append("        result += '<span class=\"diff-add\">' + esc(newWords[ni]) + '</span>';\n");
    sb.append("        ni++;\n");
    sb.append("      }\n");
    sb.append("    }\n");
    sb.append("  }\n");
    sb.append("  return result;\n");
    sb.append("}\n\n");

    // Tokenize into words (preserving whitespace as separate tokens)
    sb.append("function tokenize(text) {\n");
    sb.append("  if (!text) return [];\n");
    sb.append("  return text.match(/\\S+|\\s+/g) || [];\n");
    sb.append("}\n\n");

    // Compute LCS (longest common subsequence)
    sb.append("function computeLCS(a, b) {\n");
    sb.append("  var m = a.length, n = b.length;\n");
    sb.append("  // For very large inputs, skip diff to avoid freezing\n");
    sb.append("  if (m > 5000 || n > 5000) return [];\n");
    sb.append("  var dp = new Array(m + 1);\n");
    sb.append("  for (var i = 0; i <= m; i++) {\n");
    sb.append("    dp[i] = new Array(n + 1).fill(0);\n");
    sb.append("  }\n");
    sb.append("  for (var i = 1; i <= m; i++) {\n");
    sb.append("    for (var j = 1; j <= n; j++) {\n");
    sb.append("      if (a[i-1] === b[j-1]) dp[i][j] = dp[i-1][j-1] + 1;\n");
    sb.append("      else dp[i][j] = Math.max(dp[i-1][j], dp[i][j-1]);\n");
    sb.append("    }\n");
    sb.append("  }\n");
    sb.append("  // Backtrack\n");
    sb.append("  var result = [];\n");
    sb.append("  var i = m, j = n;\n");
    sb.append("  while (i > 0 && j > 0) {\n");
    sb.append("    if (a[i-1] === b[j-1]) { result.unshift(a[i-1]); i--; j--; }\n");
    sb.append("    else if (dp[i-1][j] > dp[i][j-1]) i--;\n");
    sb.append("    else j--;\n");
    sb.append("  }\n");
    sb.append("  return result;\n");
    sb.append("}\n\n");

    // Diff toggle handler
    sb.append("document.getElementById('diff-toggle').addEventListener('change', function() {\n");
    sb.append("  if (selectedVersion !== null) loadVersion(selectedVersion);\n");
    sb.append("});\n\n");

    // Init: fetch info then history
    sb.append("function init() {\n");
    sb.append("  var infoEl = document.getElementById('vh-info');\n");
    sb.append("  var tl = document.getElementById('timeline');\n");
    sb.append("  showLoading(tl);\n\n");
    sb.append("  apiFetch('info').then(function(info) {\n");
    sb.append("    currentMaxVersion = info.version;\n");
    sb.append("    infoEl.innerHTML = 'Current version: <span class=\"version-badge\">v' + info.version + '</span>';\n\n");
    sb.append("    return apiFetch('history', { start: 0, end: info.version });\n");
    sb.append("  }).then(function(history) {\n");
    sb.append("    deltaList = history;\n");
    sb.append("    buildTimeline();\n");
    sb.append("    // Auto-select the latest version\n");
    sb.append("    if (deltaList.length > 0) {\n");
    sb.append("      var latest = deltaList[deltaList.length - 1];\n");
    sb.append("      window._selectVersion(latest.resultingVersion, deltaList.length - 1);\n");
    sb.append("    }\n");
    sb.append("  }).catch(function(e) {\n");
    sb.append("    showError(tl, 'Failed to load version history: ' + e.message);\n");
    sb.append("  });\n");
    sb.append("}\n\n");

    sb.append("init();\n");
    sb.append("})();\n");
    sb.append("</script>\n");

    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  /** JSON string literal helper. */
  private static String jsonStr(String s) {
    return HtmlRenderer.escapeJsonString(s);
  }

  /** Simple DTO for delta metadata. */
  private static final class DeltaInfo {
    final long appliedAt;
    final long resultingVersion;
    final String author;
    final long timestamp;
    final int opCount;

    DeltaInfo(long appliedAt, long resultingVersion, String author, long timestamp, int opCount) {
      this.appliedAt = appliedAt;
      this.resultingVersion = resultingVersion;
      this.author = author;
      this.timestamp = timestamp;
      this.opCount = opCount;
    }
  }
}
