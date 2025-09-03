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
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.frontend.FragmentsFetcherCompat;
import org.waveprotocol.box.server.frontend.FragmentsRequest;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.box.server.util.UrlParameters;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.jvm.JavaWaverefEncoder;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Fragments endpoint (compat): returns a JSON list of blip ids + metadata near viewport.
 */
public final class FragmentsServlet extends HttpServlet {
  private static final Log LOG = Log.get(FragmentsServlet.class);

  private final WaveletProvider waveletProvider;
  private final SessionManager sessionManager;

  @Inject
  public FragmentsServlet(WaveletProvider waveletProvider, SessionManager sessionManager) {
    this.waveletProvider = waveletProvider;
    this.sessionManager = sessionManager;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("application/json;charset=UTF-8");
    ParticipantId user = sessionManager.getLoggedInUser(req.getSession(false));
    if (user == null) { resp.setStatus(HttpServletResponse.SC_FORBIDDEN); return; }

    String ref = req.getParameter("ref");
    String start = req.getParameter("startBlipId");
    String dir = req.getParameter("direction");
    String lim = req.getParameter("limit");
    String sv = req.getParameter("startVersion");
    String ev = req.getParameter("endVersion");
    int limit = 50;
    if (lim != null) { try { limit = Math.max(1, Math.min(200, Integer.parseInt(lim))); } catch (Exception ignored) {} }
    if (dir == null || dir.isEmpty()) dir = "forward";

    if (ref == null || ref.isEmpty()) { resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); return; }
    WaveletName wn;
    try {
      WaveRef waveref = JavaWaverefEncoder.decodeWaveRefFromPath(ref);
      wn = WaveletName.of(waveref.getWaveId(), waveref.getWaveletId());
    } catch (Exception e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); return;
    }

    try {
      // Best-effort permission check
      if (!waveletProvider.checkAccessPermission(wn, user)) {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN); return;
      }
      Map<String, FragmentsFetcherCompat.BlipMeta> metas = FragmentsFetcherCompat.listBlips(waveletProvider, wn);
      List<String> order = FragmentsFetcherCompat.manifestOrder(waveletProvider, wn);
      List<String> slice = FragmentsFetcherCompat.sliceUsingOrder(metas, order, start, dir, limit);
      long snapshotVersion = FragmentsFetcherCompat.getCommittedVersion(waveletProvider, wn);

      // Build FragmentsRequest (optional start/end); explicit ranges param not supported yet.
      FragmentsRequest.Builder fb = new FragmentsRequest.Builder();
      boolean hasCommon = false;
      if (sv != null && ev != null) {
        try {
          long sver = Long.parseLong(sv);
          long ever = Long.parseLong(ev);
          fb.setStartVersion(sver).setEndVersion(ever);
          hasCommon = true;
        } catch (Exception ignored) {}
      }
      FragmentsRequest fReq = hasCommon ? fb.build() : new FragmentsRequest.Builder()
          .setStartVersion(snapshotVersion)
          .setEndVersion(snapshotVersion)
          .build();

      // Compose segment list: INDEX, MANIFEST, plus slice blips
      java.util.ArrayList<SegmentId> segs = new java.util.ArrayList<>();
      segs.add(SegmentId.INDEX_ID);
      segs.add(SegmentId.MANIFEST_ID);
      for (String id : slice) segs.add(SegmentId.ofBlipId(id));
      com.google.common.collect.ImmutableMap<SegmentId, VersionRange> ranges =
          FragmentsFetcherCompat.computeRangesForSegments(snapshotVersion, fReq, segs);
      StringBuilder sb = new StringBuilder();
      sb.append("{\"status\":\"ok\",\"waveRef\":\"").append(ref)
        .append("\",\"version\":{\"snapshot\":").append(snapshotVersion)
        .append(",\"start\":").append(fReq.startVersion)
        .append(",\"end\":").append(fReq.endVersion)
        .append("},\"blips\":[");
      boolean first = true;
      for (String id : slice) {
        if (!first) sb.append(','); first = false;
        FragmentsFetcherCompat.BlipMeta m = metas.get(id);
        sb.append("{\"id\":\"").append(id).append("\",\"author\":\"")
          .append(m.author == null?"":m.author.getAddress())
          .append("\",\"lastModifiedTime\":").append(m.lastModifiedTime).append('}');
      }
      sb.append("],\"ranges\":[");
      boolean firstR = true;
      for (java.util.Map.Entry<SegmentId, VersionRange> e : ranges.entrySet()) {
        if (!firstR) sb.append(','); firstR = false;
        sb.append("{\"segment\":\"").append(e.getKey().asString())
          .append("\",\"from\":").append(e.getValue().from())
          .append(",\"to\":").append(e.getValue().to())
          .append("}");
      }
      sb.append("]}");
      resp.getWriter().write(sb.toString());
      resp.setStatus(HttpServletResponse.SC_OK);
    } catch (WaveServerException e) {
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
