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

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.protobuf.Message;
import org.waveprotocol.box.common.ListReceiver;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.common.SnapshotSerializer;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.rpc.ProtoSerializer.SerializationException;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Servlet providing versioned access to wavelet state.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /fetch/version/{waveId}/{waveletId}?version=N} — snapshot at version N
 *       (omit version param for current snapshot)</li>
 *   <li>{@code GET /fetch/version/{waveId}/{waveletId}/history?start=S&end=E} — delta metadata
 *       for the given version range</li>
 *   <li>{@code GET /fetch/version/{waveId}/{waveletId}/info} — current version info</li>
 * </ul>
 *
 * <p>All endpoints verify the requesting user is a wavelet participant.
 */
@SuppressWarnings("serial")
public final class VersionedFetchServlet extends HttpServlet {
  private static final Log LOG = Log.get(VersionedFetchServlet.class);

  private final WaveletProvider waveletProvider;
  private final ProtoSerializer serializer;
  private final SessionManager sessionManager;

  @Inject
  public VersionedFetchServlet(
      WaveletProvider waveletProvider, ProtoSerializer serializer, SessionManager sessionManager) {
    this.waveletProvider = waveletProvider;
    this.serializer = serializer;
    this.sessionManager = sessionManager;
  }

  @Override
  @VisibleForTesting
  protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not authenticated");
      return;
    }

    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.length() < 2) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing path");
      return;
    }
    // Strip leading slash
    String path = pathInfo.substring(1);

    // Detect trailing /history or /info
    String mode = "snapshot"; // default
    if (path.endsWith("/history")) {
      mode = "history";
      path = path.substring(0, path.length() - "/history".length());
    } else if (path.endsWith("/info")) {
      mode = "info";
      path = path.substring(0, path.length() - "/info".length());
    }

    // Parse waveId/waveletId from path: domain/waveId/domain/waveletId
    // The path format is: wavedomain/w+id/waveletdomain/waveletid
    String[] parts = path.split("/");
    if (parts.length < 4) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Expected path: /{waveDomain}/{waveId}/{waveletDomain}/{waveletId}");
      return;
    }

    WaveId waveId;
    WaveletId waveletId;
    try {
      waveId = WaveId.of(parts[0], parts[1]);
      waveletId = WaveletId.of(parts[2], parts[3]);
    } catch (IllegalArgumentException e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid wave/wavelet ID: " + e.getMessage());
      return;
    }

    WaveletName waveletName = WaveletName.of(waveId, waveletId);

    // Access control: verify user is a participant
    try {
      if (!waveletProvider.checkAccessPermission(waveletName, user)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
    } catch (WaveServerException e) {
      LOG.warning("Access check failed for " + waveletName, e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    switch (mode) {
      case "snapshot":
        handleSnapshot(req, response, waveletName);
        break;
      case "history":
        handleHistory(req, response, waveletName);
        break;
      case "info":
        handleInfo(response, waveletName);
        break;
      default:
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown mode");
    }
  }

  /**
   * Handles snapshot requests. If {@code ?version=N} is present, replays deltas up to version N.
   * Otherwise returns the current snapshot.
   */
  private void handleSnapshot(HttpServletRequest req, HttpServletResponse response,
      WaveletName waveletName) throws IOException {
    String versionParam = req.getParameter("version");

    if (versionParam == null) {
      // Return current snapshot (same as existing FetchServlet)
      try {
        CommittedWaveletSnapshot committed = waveletProvider.getSnapshot(waveletName);
        if (committed == null) {
          response.sendError(HttpServletResponse.SC_NOT_FOUND, "Wavelet not found");
          return;
        }
        ReadableWaveletData snapshot = committed.snapshot;
        serializeSnapshotToResponse(snapshot, snapshot.getHashedVersion(), response);
      } catch (WaveServerException e) {
        LOG.warning("Failed to fetch snapshot for " + waveletName, e);
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
      return;
    }

    // Parse version
    long targetVersion;
    try {
      targetVersion = Long.parseLong(versionParam);
      if (targetVersion < 0) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Version must be non-negative");
        return;
      }
    } catch (NumberFormatException e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid version: " + versionParam);
      return;
    }

    // Version 0 means empty wavelet — that's not a meaningful snapshot
    if (targetVersion == 0) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Version 0 is the empty wavelet; request version >= 1");
      return;
    }

    // Check current version to validate the requested version
    CommittedWaveletSnapshot current;
    try {
      current = waveletProvider.getSnapshot(waveletName);
    } catch (WaveServerException e) {
      LOG.warning("Failed to fetch current snapshot for " + waveletName, e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    if (current == null) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "Wavelet not found");
      return;
    }

    long currentVersion = current.snapshot.getVersion();
    if (targetVersion > currentVersion) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Requested version " + targetVersion + " exceeds current version " + currentVersion);
      return;
    }

    // If requesting the current version, return it directly
    if (targetVersion == currentVersion) {
      ReadableWaveletData snapshot = current.snapshot;
      serializeSnapshotToResponse(snapshot, snapshot.getHashedVersion(), response);
      return;
    }

    // Replay deltas from version 0 to targetVersion
    try {
      HashedVersion startVersion = HashedVersion.unsigned(0);
      HashedVersion endVersion = HashedVersion.unsigned(targetVersion);
      ListReceiver<TransformedWaveletDelta> receiver = new ListReceiver<>();
      waveletProvider.getHistory(waveletName, startVersion, endVersion, receiver);

      if (receiver.isEmpty()) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND,
            "No deltas found for version range 0.." + targetVersion);
        return;
      }

      // Filter to only include deltas whose resulting version <= targetVersion
      List<TransformedWaveletDelta> filteredDeltas = new ArrayList<>();
      for (TransformedWaveletDelta delta : receiver) {
        if (delta.getResultingVersion().getVersion() <= targetVersion) {
          filteredDeltas.add(delta);
        }
      }

      if (filteredDeltas.isEmpty()) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND,
            "No deltas found up to version " + targetVersion);
        return;
      }

      ObservableWaveletData wavelet = WaveletDataUtil.buildWaveletFromDeltas(
          waveletName, filteredDeltas.iterator());
      serializeSnapshotToResponse(wavelet, wavelet.getHashedVersion(), response);
    } catch (WaveServerException e) {
      LOG.warning("Failed to replay deltas for " + waveletName + " at version " + targetVersion, e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (OperationException e) {
      LOG.warning("Failed to apply deltas for " + waveletName + " at version " + targetVersion, e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Handles history requests. Returns delta metadata for the given version range.
   * Query params: {@code start} (inclusive, default 0), {@code end} (exclusive, default current).
   */
  private void handleHistory(HttpServletRequest req, HttpServletResponse response,
      WaveletName waveletName) throws IOException {
    // Get current version first
    CommittedWaveletSnapshot current;
    try {
      current = waveletProvider.getSnapshot(waveletName);
    } catch (WaveServerException e) {
      LOG.warning("Failed to fetch snapshot for history of " + waveletName, e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    if (current == null) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "Wavelet not found");
      return;
    }

    long currentVersion = current.snapshot.getVersion();

    // Parse start and end
    long start = 0;
    long end = currentVersion;
    String startParam = req.getParameter("start");
    String endParam = req.getParameter("end");

    if (startParam != null) {
      try {
        start = Long.parseLong(startParam);
        if (start < 0) {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, "start must be non-negative");
          return;
        }
      } catch (NumberFormatException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid start: " + startParam);
        return;
      }
    }

    if (endParam != null) {
      try {
        end = Long.parseLong(endParam);
        if (end < 0) {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, "end must be non-negative");
          return;
        }
      } catch (NumberFormatException e) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid end: " + endParam);
        return;
      }
    }

    if (start >= end) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "start must be less than end (start=" + start + ", end=" + end + ")");
      return;
    }

    if (end > currentVersion) {
      end = currentVersion;
    }

    // Fetch deltas in the range
    try {
      HashedVersion startVersion = HashedVersion.unsigned(start);
      HashedVersion endVersion = HashedVersion.unsigned(end);
      ListReceiver<TransformedWaveletDelta> receiver = new ListReceiver<>();
      waveletProvider.getHistory(waveletName, startVersion, endVersion, receiver);

      JsonArray deltas = new JsonArray();
      for (TransformedWaveletDelta delta : receiver) {
        JsonObject entry = new JsonObject();
        entry.addProperty("appliedAtVersion", delta.getAppliedAtVersion());
        entry.addProperty("resultingVersion", delta.getResultingVersion().getVersion());
        entry.addProperty("author", delta.getAuthor().getAddress());
        entry.addProperty("timestamp", delta.getApplicationTimestamp());
        entry.addProperty("operationCount", delta.size());
        deltas.add(entry);
      }

      JsonObject result = new JsonObject();
      result.addProperty("waveletId",
          waveletName.waveId.getDomain() + "/" + waveletName.waveId.getId()
          + "/" + waveletName.waveletId.getDomain() + "/" + waveletName.waveletId.getId());
      result.addProperty("start", start);
      result.addProperty("end", end);
      result.addProperty("currentVersion", currentVersion);
      result.add("deltas", deltas);

      writeJsonResponse(response, result);
    } catch (WaveServerException e) {
      LOG.warning("Failed to fetch history for " + waveletName, e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Handles info requests. Returns current version, creation time, last modified time, and creator.
   */
  private void handleInfo(HttpServletResponse response, WaveletName waveletName) throws IOException {
    CommittedWaveletSnapshot committed;
    try {
      committed = waveletProvider.getSnapshot(waveletName);
    } catch (WaveServerException e) {
      LOG.warning("Failed to fetch info for " + waveletName, e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    if (committed == null) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "Wavelet not found");
      return;
    }

    ReadableWaveletData snapshot = committed.snapshot;
    JsonObject info = new JsonObject();
    info.addProperty("currentVersion", snapshot.getVersion());
    info.addProperty("createdTime", snapshot.getCreationTime());
    info.addProperty("lastModifiedTime", snapshot.getLastModifiedTime());
    info.addProperty("creator", snapshot.getCreator().getAddress());

    writeJsonResponse(response, info);
  }

  private void serializeSnapshotToResponse(ReadableWaveletData snapshot,
      HashedVersion hashedVersion, HttpServletResponse response) throws IOException {
    Message message = SnapshotSerializer.serializeWavelet(snapshot, hashedVersion);
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    try (var w = response.getWriter()) {
      w.append(serializer.toJson(message).toString());
      w.flush();
    } catch (SerializationException e) {
      throw new IOException(e);
    }
  }

  private void writeJsonResponse(HttpServletResponse response, JsonObject json) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    try (var w = response.getWriter()) {
      w.append(json.toString());
      w.flush();
    }
  }
}
