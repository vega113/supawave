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
import com.google.inject.name.Named;
import com.google.protobuf.Message;
import org.waveprotocol.box.common.comms.WaveClientRpc.WaveViewSnapshot;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.common.SnapshotSerializer;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.rpc.ProtoSerializer.SerializationException;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.waveref.InvalidWaveRefException;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.jvm.JavaWaverefEncoder;
import org.waveprotocol.wave.util.logging.Log;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet providing read-only access to public waves without authentication.
 *
 * <p>A wave is "public" when the domain participant ({@code @domain.com}) is in the
 * wavelet's participant list. Anonymous users can view public wave data through
 * this endpoint.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /wave/public/{waveRef}} — returns wave data if public, 404 if not found
 *       or not public (to avoid leaking wave existence)</li>
 * </ul>
 *
 * <p>Security:
 * <ul>
 *   <li>Only GET (read) access is supported; all other methods return 405.</li>
 *   <li>Non-public waves return 404 (not 403) to prevent leaking their existence.</li>
 *   <li>No write operations are permitted.</li>
 * </ul>
 */
@SuppressWarnings("serial")
public final class PublicWaveFetchServlet extends HttpServlet {
  private static final Log LOG = Log.get(PublicWaveFetchServlet.class);

  private final WaveletProvider waveletProvider;
  private final ProtoSerializer serializer;
  private final ParticipantId sharedDomainParticipantId;
  private final AnalyticsRecorder analyticsRecorder;

  @Inject
  public PublicWaveFetchServlet(
      WaveletProvider waveletProvider,
      ProtoSerializer serializer,
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain,
      AnalyticsRecorder analyticsRecorder) {
    this.waveletProvider = waveletProvider;
    this.serializer = serializer;
    this.sharedDomainParticipantId =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain);
    this.analyticsRecorder = analyticsRecorder;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.length() < 2) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    // Strip leading slash
    String urlPath = pathInfo.substring(1);

    WaveRef waveref;
    try {
      waveref = JavaWaverefEncoder.decodeWaveRefFromPath(urlPath);
    } catch (InvalidWaveRefException e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    renderPublicSnapshot(waveref, response);
  }

  /**
   * Only GET is allowed for public wave access. All other methods return 405.
   */
  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Public wave access is read-only");
  }

  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Public wave access is read-only");
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Public wave access is read-only");
  }

  /**
   * Render the snapshot for a public wave. Returns 404 for non-public or non-existent waves
   * to avoid leaking wave existence.
   */
  private void renderPublicSnapshot(WaveRef waveref, HttpServletResponse dest) throws IOException {
    WaveletId waveletId = waveref.hasWaveletId() ? waveref.getWaveletId()
        : WaveletId.of(waveref.getWaveId().getDomain(), "conv+root");
    WaveletName waveletName = WaveletName.of(waveref.getWaveId(), waveletId);

    CommittedWaveletSnapshot committedSnapshot;
    try {
      committedSnapshot = waveletProvider.getSnapshot(waveletName);
    } catch (WaveServerException e) {
      LOG.warning("Problem fetching snapshot for public wave: " + waveref, e);
      dest.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    if (committedSnapshot == null) {
      // Wave doesn't exist — return 404
      dest.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    ReadableWaveletData snapshot = committedSnapshot.snapshot;

    // Check if the wave is public (has domain participant)
    if (!WaveletDataUtil.isPublicWavelet(snapshot, sharedDomainParticipantId)) {
      // Not public — return 404 to avoid leaking existence
      dest.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    // Wave is public — serialize and return
    if (waveref.hasDocumentId()) {
      // Return a specific document
      Message docSnapshot = null;
      for (String docId : snapshot.getDocumentIds()) {
        if (docId.equals(waveref.getDocumentId())) {
          docSnapshot = SnapshotSerializer.serializeDocument(snapshot.getDocument(docId));
          break;
        }
      }
      recordApiView(waveref.getWaveId());
      serializeObjectToResponse(docSnapshot, dest);
    } else if (waveref.hasWaveletId()) {
      // Return the wavelet snapshot
      recordApiView(waveref.getWaveId());
      serializeObjectToResponse(
          SnapshotSerializer.serializeWavelet(snapshot, snapshot.getHashedVersion()), dest);
    } else {
      // Return the full wave view (just conv+root for now)
      WaveViewSnapshot waveSnapshot = WaveViewSnapshot.newBuilder()
          .setWaveId(ModernIdSerialiser.INSTANCE.serialiseWaveId(waveref.getWaveId()))
          .addWavelet(
              SnapshotSerializer.serializeWavelet(snapshot, snapshot.getHashedVersion()))
          .build();
      recordApiView(waveref.getWaveId());
      serializeObjectToResponse(waveSnapshot, dest);
    }
  }

  private void recordApiView(WaveId waveId) {
    analyticsRecorder.incrementApiViews(waveId.serialise(), System.currentTimeMillis());
  }

  private <P extends Message> void serializeObjectToResponse(P message, HttpServletResponse dest)
      throws IOException {
    if (message == null) {
      dest.sendError(HttpServletResponse.SC_NOT_FOUND);
    } else {
      dest.setStatus(HttpServletResponse.SC_OK);
      dest.setContentType("application/json");
      // Do not cache public wave data — when a wave is toggled to private the
      // cached response would continue to serve the wave content. Use no-store
      // to ensure every request hits the server and checks the current
      // participant list.
      dest.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
      dest.setHeader("Pragma", "no-cache");
      try (var w = dest.getWriter()) {
        w.append(serializer.toJson(message).toString());
        w.flush();
      } catch (SerializationException ex) {
        LOG.warning("Failed to serialize public wave response", ex);
        dest.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
    }
  }
}
