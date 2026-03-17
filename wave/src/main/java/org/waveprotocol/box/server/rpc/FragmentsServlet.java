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
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.frontend.FragmentsFetcherCompat;
import org.waveprotocol.box.server.frontend.FragmentsRequest;
import org.waveprotocol.box.server.frontend.RawFragmentsBuilder;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.jvm.JavaWaverefEncoder;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
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
    if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
      org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.httpRequests.incrementAndGet();
    }
    ParticipantId user = sessionManager.getLoggedInUser(req.getSession(false));
    if (user == null) { resp.setStatus(HttpServletResponse.SC_FORBIDDEN); return; }

    String ref = req.getParameter("ref");
    WaveletName wn = null;
    if (ref != null && !ref.isEmpty()) {
      wn = decodeWaveletName(ref);
    }
    if (wn == null) {
      String waveIdParam = req.getParameter("waveId");
      String waveletIdParam = req.getParameter("waveletId");
      if (waveIdParam != null && waveletIdParam != null) {
        try {
          WaveId waveId = ModernIdSerialiser.INSTANCE.deserialiseWaveId(waveIdParam);
          WaveletId waveletId = ModernIdSerialiser.INSTANCE.deserialiseWaveletId(waveletIdParam);
          wn = WaveletName.of(waveId, waveletId);
        } catch (Exception ex) {
          wn = null;
        }
      }
    }
    if (wn == null) { resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); return; }

    String start = req.getParameter("startBlipId");
    String dir = normalizeDirection(req.getParameter("direction"));
    int limit = clampLimit(req.getParameter("limit"));
    Long startVersion = parseLong(req.getParameter("startVersion"));
    Long endVersion = parseLong(req.getParameter("endVersion"));

    try {
      if (!waveletProvider.checkAccessPermission(wn, user)) { resp.setStatus(HttpServletResponse.SC_FORBIDDEN); return; }
      Map<String, FragmentsFetcherCompat.BlipMeta> metas = FragmentsFetcherCompat.listBlips(waveletProvider, wn);
      List<String> order;
      try {
        order = org.waveprotocol.box.server.frontend.ManifestOrderCache.getOrCompute(waveletProvider, wn);
      } catch (Exception ex) {
        // Manifest order may fail in compat paths; log and fall back to time-based ordering.
        LOG.warning("FragmentsServlet: manifestOrder failed for " + wn + ", falling back to time-based ordering", ex);
        order = null;
      }
      List<String> slice = FragmentsFetcherCompat.sliceUsingOrder(metas, order, start, dir, limit);
      if (order == null) {
        LOG.fine("FragmentsServlet: using time-based blip order for " + wn + ", slice size=" + slice.size());
      }
      long snapshotVersion = FragmentsFetcherCompat.getCommittedVersion(waveletProvider, wn);
      FragmentsRequest fReq = buildFragmentsRequest(snapshotVersion, startVersion, endVersion);
      com.google.common.collect.ImmutableMap<SegmentId, VersionRange> ranges =
          FragmentsFetcherCompat.computeRangesForSegments(snapshotVersion, fReq, buildSegments(slice));
      ReadableWaveletData data = null;
      try {
        CommittedWaveletSnapshot snap = waveletProvider.getSnapshot(wn);
        if (snap != null) {
          data = snap.snapshot;
        }
      } catch (WaveServerException ex) {
        LOG.warning("FragmentsServlet: snapshot load failed for " + wn, ex);
      }
      List<FragmentsPayload.Fragment> rawFragments = RawFragmentsBuilder.build(data, ranges);
      // Build safe JSON with proper escaping and canonical waveref encoding
      String json = buildJson(wn, metas, slice, snapshotVersion, fReq, ranges, rawFragments);
      // Help browsers avoid content-type sniffing
      resp.setHeader("X-Content-Type-Options", "nosniff");
      resp.getWriter().write(json);
      resp.setStatus(HttpServletResponse.SC_OK);
      if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
        org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.httpOk.incrementAndGet();
      }
    } catch (WaveServerException e) {
        LOG.warning("Error fetching fragments", e);
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      try { resp.getWriter().write("{\"status\":\"error\"}"); } catch (Exception ex) {
          LOG.warning("Error writing error response", ex);
      }
      if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
        org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.httpErrors.incrementAndGet();
      }
    }
  }

  private WaveletName decodeWaveletName(String ref) {
    try {
      WaveRef waveref = JavaWaverefEncoder.decodeWaveRefFromPath(ref);
      return WaveletName.of(waveref.getWaveId(), waveref.getWaveletId());
    } catch (Exception e) { return null; }
  }

  private String normalizeDirection(String dir) {
    return (dir == null || dir.isEmpty()) ? "forward" : dir;
  }

  private int clampLimit(String lim) {
    int limit = 50;
    if (lim == null) return limit;
    try { limit = Math.max(1, Math.min(200, Integer.parseInt(lim))); } catch (Exception ex) {
      LOG.fine("Invalid 'limit' parameter '" + lim + "'; using default " + limit);
    }
    return limit;
  }

  private Long parseLong(String v) { if (v == null) return null; try { return Long.parseLong(v); } catch (Exception e) { return null; } }

  private FragmentsRequest buildFragmentsRequest(long snapshot, Long s, Long e) {
    if (s != null && e != null) {
      return new FragmentsRequest.Builder().setStartVersion(s).setEndVersion(e).build();
    }
    return new FragmentsRequest.Builder().setStartVersion(snapshot).setEndVersion(snapshot).build();
  }

  private java.util.ArrayList<SegmentId> buildSegments(List<String> slice) {
    java.util.ArrayList<SegmentId> segs = new java.util.ArrayList<>();
    segs.add(SegmentId.INDEX_ID);
    segs.add(SegmentId.MANIFEST_ID);
    for (String id : slice) segs.add(SegmentId.ofBlipId(id));
    return segs;
  }

  private String buildJson(WaveletName wn,
      Map<String, FragmentsFetcherCompat.BlipMeta> metas,
      List<String> slice,
      long snapshotVersion,
      FragmentsRequest fReq,
      java.util.Map<SegmentId, VersionRange> ranges,
      List<FragmentsPayload.Fragment> fragmentsList) {
    class VersionInfo {
      long snapshot; long start; long end;
      VersionInfo(long s, long st, long en) { snapshot=s; start=st; end=en; }
    }
    class BlipInfo {
      String id; String author; long lastModifiedTime;
      BlipInfo(String i, String a, long t) { id=i; author=a; lastModifiedTime=t; }
    }
    class RangeInfo {
      String segment; long from; long to;
      RangeInfo(String s, long f, long t) { segment=s; from=f; to=t; }
    }
    class FragmentOp {
      String operations;
      String author;
      long targetVersion;
      long timestamp;
      FragmentOp(String operations, String author, long targetVersion, long timestamp) {
        this.operations = operations;
        this.author = author;
        this.targetVersion = targetVersion;
        this.timestamp = timestamp;
      }
    }
    class FragmentInfo {
      String segment;
      List<FragmentOp> adjust;
      List<FragmentOp> diff;
      String rawSnapshot;
      FragmentInfo(String segment, List<FragmentOp> adjust, List<FragmentOp> diff, String rawSnapshot) {
        this.segment = segment;
        this.adjust = adjust;
        this.diff = diff;
        this.rawSnapshot = rawSnapshot;
      }
    }
    class Response {
      String status = "ok";
      @SerializedName("waveRef") String waveRefPath;
      VersionInfo version;
      List<BlipInfo> blips;
      List<RangeInfo> ranges;
      List<FragmentInfo> fragments;
    }
    Response out = new Response();
    // Canonical, server-encoded waveref path segment
    out.waveRefPath = JavaWaverefEncoder.encodeToUriPathSegment(
        org.waveprotocol.wave.model.waveref.WaveRef.of(wn.waveId, wn.waveletId));
    out.version = new VersionInfo(snapshotVersion, fReq.startVersion, fReq.endVersion);
    out.blips = new java.util.ArrayList<>(slice.size());
    for (String id : slice) {
      FragmentsFetcherCompat.BlipMeta m = metas.get(id);
      out.blips.add(new BlipInfo(id, (m.author==null? "" : m.author.getAddress()), m.lastModifiedTime));
    }
    out.ranges = new java.util.ArrayList<>(ranges.size());
    for (java.util.Map.Entry<SegmentId, VersionRange> e : ranges.entrySet()) {
      out.ranges.add(new RangeInfo(e.getKey().asString(), e.getValue().from(), e.getValue().to()));
    }
    if (fragmentsList == null || fragmentsList.isEmpty()) {
      out.fragments = java.util.Collections.emptyList();
    } else {
      out.fragments = new java.util.ArrayList<>(fragmentsList.size());
      for (FragmentsPayload.Fragment fragment : fragmentsList) {
        List<FragmentOp> adjust = new java.util.ArrayList<>(fragment.adjustOperations.size());
        for (FragmentsPayload.Operation op : fragment.adjustOperations) {
          adjust.add(new FragmentOp(op.operations, op.author, op.targetVersion, op.timestamp));
        }
        List<FragmentOp> diff = new java.util.ArrayList<>(fragment.diffOperations.size());
        for (FragmentsPayload.Operation op : fragment.diffOperations) {
          diff.add(new FragmentOp(op.operations, op.author, op.targetVersion, op.timestamp));
        }
        out.fragments.add(new FragmentInfo(
            fragment.segment.asString(),
            adjust.isEmpty() ? java.util.Collections.<FragmentOp>emptyList() : adjust,
            diff.isEmpty() ? java.util.Collections.<FragmentOp>emptyList() : diff,
            fragment.rawSnapshot == null ? "" : fragment.rawSnapshot));
      }
    }
    return new Gson().toJson(out);
  }
}
