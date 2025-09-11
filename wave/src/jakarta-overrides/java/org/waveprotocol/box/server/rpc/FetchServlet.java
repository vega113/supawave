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
import com.google.inject.Inject;
import com.google.protobuf.Message;
import org.waveprotocol.box.common.comms.WaveClientRpc.DocumentSnapshot;
import org.waveprotocol.box.common.comms.WaveClientRpc.WaveViewSnapshot;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.common.SnapshotSerializer;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.rpc.ProtoSerializer.SerializationException;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.authentication.JakartaSessionAdapters;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.waveref.InvalidWaveRefException;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.jvm.JavaWaverefEncoder;
import org.waveprotocol.wave.util.logging.Log;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@SuppressWarnings("serial")
public final class FetchServlet extends HttpServlet {
  private static final Log LOG = Log.get(FetchServlet.class);

  @Inject
  public FetchServlet(WaveletProvider waveletProvider, ProtoSerializer serializer, SessionManager sessionManager) {
    this.waveletProvider = waveletProvider;
    this.serializer = serializer;
    this.sessionManager = sessionManager;
  }

  private final ProtoSerializer serializer;
  private final WaveletProvider waveletProvider;
  private final SessionManager sessionManager;

  @Override
  @VisibleForTesting
  protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(JakartaSessionAdapters.fromRequest(req, false));
    String urlPath = req.getPathInfo().substring(1);
    WaveRef waveref;
    try {
      waveref = JavaWaverefEncoder.decodeWaveRefFromPath(urlPath);
    } catch (InvalidWaveRefException e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    renderSnapshot(waveref, user, response);
  }

  private <P extends Message> void serializeObjectToServlet(P message, HttpServletResponse dest) throws IOException {
    if (message == null) {
      dest.sendError(HttpServletResponse.SC_FORBIDDEN);
    } else {
      dest.setStatus(HttpServletResponse.SC_OK);
      dest.setContentType("application/json");
      dest.setHeader("Cache-Control", "no-store");
      try { dest.getWriter().append(serializer.toJson(message).toString()); }
      catch (SerializationException ex) {
        LOG.warning("Failed to serialize fetch response", ex);
        dest.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
    }
  }

  private void renderSnapshot(WaveRef waveref, ParticipantId requester, HttpServletResponse dest)
      throws IOException {
    WaveletId waveletId = waveref.hasWaveletId() ? waveref.getWaveletId()
        : WaveletId.of(waveref.getWaveId().getDomain(), "conv+root");
    WaveletName waveletName = WaveletName.of(waveref.getWaveId(), waveletId);
    CommittedWaveletSnapshot committedSnapshot;
    try {
      if (!waveletProvider.checkAccessPermission(waveletName, requester)) {
        dest.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      committedSnapshot = waveletProvider.getSnapshot(waveletName);
    } catch (WaveServerException e) {
      LOG.warning("Problem fetching snapshot for: " + waveref, e);
      dest.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }
    if (committedSnapshot != null) {
      ReadableWaveletData snapshot = committedSnapshot.snapshot;
      if (waveref.hasDocumentId()) {
        DocumentSnapshot docSnapshot = null;
        for (String docId : snapshot.getDocumentIds()) {
          if (docId.equals(waveref.getDocumentId())) {
            docSnapshot = SnapshotSerializer.serializeDocument(snapshot.getDocument(docId));
            break;
          }
        }
        serializeObjectToServlet(docSnapshot, dest);
      } else if (waveref.hasWaveletId()) {
        serializeObjectToServlet(SnapshotSerializer.serializeWavelet(snapshot, snapshot.getHashedVersion()), dest);
      } else {
        WaveViewSnapshot waveSnapshot = WaveViewSnapshot.newBuilder()
            .setWaveId(ModernIdSerialiser.INSTANCE.serialiseWaveId(waveref.getWaveId()))
            .addWavelet(SnapshotSerializer.serializeWavelet(snapshot, snapshot.getHashedVersion()))
            .build();
        serializeObjectToServlet(waveSnapshot, dest);
      }
    } else {
      dest.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
  }
}
