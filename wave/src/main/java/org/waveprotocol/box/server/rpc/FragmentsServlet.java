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
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.frontend.FragmentsFetcherCompat;
import org.waveprotocol.box.server.frontend.FragmentsRequest;
import org.waveprotocol.box.server.frontend.RawFragmentsBuilder;
import org.waveprotocol.box.server.frontend.ViewportLimitPolicy;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.jvm.JavaWaverefEncoder;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    boolean j2clViewportRequest = isJ2clViewportRequest(req);
    if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
      org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.httpRequests.incrementAndGet();
    }
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
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
    if (j2clViewportRequest && org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
      org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics
          .j2clViewportExtensionRequests.incrementAndGet();
    }

    String start = req.getParameter("startBlipId");
    String dir = ViewportLimitPolicy.normalizeDirection(req.getParameter("direction"));
    int limit = clampLimit(req.getParameter("limit"), j2clViewportRequest);
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
      SliceWindow sliceWindow = buildSliceWindow(metas, order, start, dir, limit);
      if (order == null) {
        LOG.fine("FragmentsServlet: using time-based blip order for " + wn
            + ", loaded slice size=" + sliceWindow.getLoadedBlipIds().size());
      }
      long snapshotVersion = FragmentsFetcherCompat.getCommittedVersion(waveletProvider, wn);
      FragmentsRequest fReq = buildFragmentsRequest(snapshotVersion, startVersion, endVersion);
      com.google.common.collect.ImmutableMap<SegmentId, VersionRange> ranges =
          FragmentsFetcherCompat.computeRangesForSegments(
              snapshotVersion, fReq, buildSegments(sliceWindow.getRangeBlipIds()));
      ReadableWaveletData data = null;
      try {
        CommittedWaveletSnapshot snap = waveletProvider.getSnapshot(wn);
        if (snap != null) {
          data = snap.snapshot;
        }
      } catch (WaveServerException ex) {
        LOG.warning("FragmentsServlet: snapshot load failed for " + wn, ex);
      }
      List<FragmentsPayload.Fragment> rawFragments =
          RawFragmentsBuilder.build(
              data,
              filterRawRanges(ranges, buildSegments(sliceWindow.getLoadedBlipIds())));
      // Build safe JSON with proper escaping and canonical waveref encoding
      String json =
          buildJson(
              wn,
              metas,
              sliceWindow.getLoadedBlipIds(),
              snapshotVersion,
              fReq,
              ranges,
              rawFragments);
      // Help browsers avoid content-type sniffing
      resp.setHeader("X-Content-Type-Options", "nosniff");
      resp.getWriter().write(json);
      resp.setStatus(HttpServletResponse.SC_OK);
      if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
        org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.httpOk.incrementAndGet();
        if (j2clViewportRequest) {
          org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics
              .j2clViewportExtensionOk.incrementAndGet();
        }
      }
    } catch (WaveServerException e) {
        LOG.warning("Error fetching fragments", e);
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      try { resp.getWriter().write("{\"status\":\"error\"}"); } catch (Exception ex) {
          LOG.warning("Error writing error response", ex);
      }
      if (org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
        org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.httpErrors.incrementAndGet();
        if (j2clViewportRequest) {
          org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics
              .j2clViewportExtensionErrors.incrementAndGet();
        }
      }
    }
  }

  private WaveletName decodeWaveletName(String ref) {
    try {
      WaveRef waveref = JavaWaverefEncoder.decodeWaveRefFromPath(ref);
      return WaveletName.of(waveref.getWaveId(), waveref.getWaveletId());
    } catch (Exception e) { return null; }
  }

  @VisibleForTesting
  static int resolveLimitForRequest(String rawLimit) {
    return resolveLimitForRequest(rawLimit, false);
  }

  @VisibleForTesting
  static int resolveLimitForRequest(String rawLimit, boolean recordJ2clMetric) {
    Integer requestedLimit = parseInteger(rawLimit);
    int resolvedLimit =
        requestedLimit == null
            ? ViewportLimitPolicy.resolveLimit(rawLimit)
            : ViewportLimitPolicy.resolveLimit(requestedLimit.intValue());
    if (recordJ2clMetric
        && requestedLimit != null
        && resolvedLimit != requestedLimit.intValue()
        && org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics.isEnabled()) {
      org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics
          .j2clViewportClampApplied.incrementAndGet();
    }
    return resolvedLimit;
  }

  private int clampLimit(String lim, boolean recordJ2clMetric) {
    int limit = resolveLimitForRequest(lim, recordJ2clMetric);
    Integer requestedLimit = parseInteger(lim);
    if (lim != null && requestedLimit == null) {
      LOG.fine("Invalid 'limit' parameter '" + lim + "'; using default " + limit);
    }
    return limit;
  }

  private static boolean isJ2clViewportRequest(HttpServletRequest req) {
    return "j2cl".equals(req.getParameter("client"));
  }

  private static Integer parseInteger(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Long parseLong(String v) { if (v == null) return null; try { return Long.parseLong(v); } catch (Exception e) { return null; } }

  private FragmentsRequest buildFragmentsRequest(long snapshot, Long s, Long e) {
    if (s != null && e != null) {
      return new FragmentsRequest.Builder().setStartVersion(s).setEndVersion(e).build();
    }
    return new FragmentsRequest.Builder().setStartVersion(snapshot).setEndVersion(snapshot).build();
  }

  private ArrayList<SegmentId> buildSegments(List<String> slice) {
    ArrayList<SegmentId> segs = new ArrayList<>();
    segs.add(SegmentId.INDEX_ID);
    segs.add(SegmentId.MANIFEST_ID);
    for (String id : slice) segs.add(SegmentId.ofBlipId(id));
    return segs;
  }

  @VisibleForTesting
  static SliceWindow buildSliceWindow(
      Map<String, FragmentsFetcherCompat.BlipMeta> metas,
      List<String> order,
      String start,
      String direction,
      int limit) {
    // Keep this defensive for tests and future internal callers; HTTP doGet normalizes first.
    String normalizedDirection = ViewportLimitPolicy.normalizeDirection(direction);
    int boundedLimit = Math.max(1, limit);
    List<String> rangeSlice =
        FragmentsFetcherCompat.sliceUsingOrder(
            metas, order, start, normalizedDirection, boundedLimit + 1);
    if (rangeSlice.size() <= boundedLimit) {
      return new SliceWindow(rangeSlice, rangeSlice);
    }
    if (ViewportLimitPolicy.DIRECTION_BACKWARD.equals(normalizedDirection)) {
      return new SliceWindow(
          rangeSlice,
          rangeSlice.subList(1, rangeSlice.size()));
    }
    return new SliceWindow(
        rangeSlice,
        rangeSlice.subList(0, boundedLimit));
  }

  private Map<SegmentId, VersionRange> filterRawRanges(
      Map<SegmentId, VersionRange> ranges,
      List<SegmentId> rawSegments) {
    Set<SegmentId> rawSegmentSet = new HashSet<SegmentId>(rawSegments);
    Map<SegmentId, VersionRange> filtered = new LinkedHashMap<SegmentId, VersionRange>();
    for (Map.Entry<SegmentId, VersionRange> entry : ranges.entrySet()) {
      if (rawSegmentSet.contains(entry.getKey())) {
        filtered.put(entry.getKey(), entry.getValue());
      }
    }
    return filtered;
  }

  static final class SliceWindow {
    private final List<String> rangeBlipIds;
    private final List<String> loadedBlipIds;

    private SliceWindow(List<String> rangeBlipIds, List<String> loadedBlipIds) {
      this.rangeBlipIds =
          Collections.unmodifiableList(new ArrayList<String>(rangeBlipIds));
      this.loadedBlipIds =
          Collections.unmodifiableList(new ArrayList<String>(loadedBlipIds));
    }

    List<String> getRangeBlipIds() {
      return rangeBlipIds;
    }

    List<String> getLoadedBlipIds() {
      return loadedBlipIds;
    }
  }

  @VisibleForTesting
  static String buildJson(WaveletName wn,
      Map<String, FragmentsFetcherCompat.BlipMeta> metas,
      List<String> slice,
      long snapshotVersion,
      FragmentsRequest fReq,
      Map<SegmentId, VersionRange> ranges,
      List<FragmentsPayload.Fragment> fragmentsList) {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("status", "ok");
    // Canonical, server-encoded waveref path segment
    out.put(
        "waveRef",
        JavaWaverefEncoder.encodeToUriPathSegment(
            org.waveprotocol.wave.model.waveref.WaveRef.of(wn.waveId, wn.waveletId)));
    Map<String, Object> version = new LinkedHashMap<String, Object>();
    version.put("snapshot", snapshotVersion);
    version.put("start", fReq.startVersion);
    version.put("end", fReq.endVersion);
    out.put("version", version);
    List<Map<String, Object>> blips = new ArrayList<>(slice.size());
    for (String id : slice) {
      FragmentsFetcherCompat.BlipMeta m = metas.get(id);
      Map<String, Object> blip = new LinkedHashMap<String, Object>();
      blip.put("id", id);
      blip.put("author", m == null || m.author == null ? "" : m.author.getAddress());
      blip.put("lastModifiedTime", m == null ? 0L : m.lastModifiedTime);
      blips.add(blip);
    }
    out.put("blips", blips);
    List<Map<String, Object>> rangeList = new ArrayList<>(ranges.size());
    for (Map.Entry<SegmentId, VersionRange> e : ranges.entrySet()) {
      Map<String, Object> range = new LinkedHashMap<String, Object>();
      range.put("segment", e.getKey().asString());
      range.put("from", e.getValue().from());
      range.put("to", e.getValue().to());
      rangeList.add(range);
    }
    out.put("ranges", rangeList);
    if (fragmentsList == null || fragmentsList.isEmpty()) {
      out.put("fragments", Collections.emptyList());
    } else {
      List<Map<String, Object>> fragments = new ArrayList<>(fragmentsList.size());
      for (FragmentsPayload.Fragment fragment : fragmentsList) {
        List<Map<String, Object>> adjust = new ArrayList<>(fragment.adjustOperations.size());
        for (FragmentsPayload.Operation op : fragment.adjustOperations) {
          adjust.add(fragmentOp(op));
        }
        List<Map<String, Object>> diff = new ArrayList<>(fragment.diffOperations.size());
        for (FragmentsPayload.Operation op : fragment.diffOperations) {
          diff.add(fragmentOp(op));
        }
        Map<String, Object> fragmentMap = new LinkedHashMap<String, Object>();
        fragmentMap.put("segment", fragment.segment.asString());
        fragmentMap.put(
            "adjust",
            adjust.isEmpty()
                ? Collections.<Map<String, Object>>emptyList()
                : adjust);
        fragmentMap.put(
            "diff",
            diff.isEmpty()
                ? Collections.<Map<String, Object>>emptyList()
                : diff);
        fragmentMap.put("rawSnapshot", fragment.rawSnapshot == null ? "" : fragment.rawSnapshot);
        fragments.add(fragmentMap);
      }
      out.put("fragments", fragments);
    }
    return new Gson().toJson(out);
  }

  private static Map<String, Object> fragmentOp(FragmentsPayload.Operation op) {
    Map<String, Object> operation = new LinkedHashMap<String, Object>();
    operation.put("operations", op.operations);
    operation.put("author", op.author);
    operation.put("targetVersion", op.targetVersion);
    operation.put("timestamp", op.timestamp);
    return operation;
  }
}
