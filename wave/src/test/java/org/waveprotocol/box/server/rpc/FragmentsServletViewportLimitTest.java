package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.frontend.FragmentsFetcherCompat;
import org.waveprotocol.box.server.frontend.FragmentsRequest;
import org.waveprotocol.box.server.frontend.ViewportLimitPolicy;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FragmentsServletViewportLimitTest {
  private int previousDefaultLimit;
  private int previousMaxLimit;
  private boolean previousMetricsEnabled;
  private long previousClampApplied;

  @Before
  public void setUp() {
    previousDefaultLimit = ViewportLimitPolicy.getDefaultLimit();
    previousMaxLimit = ViewportLimitPolicy.getMaxLimit();
    previousMetricsEnabled = FragmentsMetrics.isEnabled();
    previousClampApplied = FragmentsMetrics.j2clViewportClampApplied.get();
    FragmentsMetrics.setEnabled(false);
    FragmentsMetrics.j2clViewportClampApplied.set(0L);
  }

  @After
  public void tearDown() {
    ViewportLimitPolicy.setLimits(previousDefaultLimit, previousMaxLimit);
    FragmentsMetrics.j2clViewportClampApplied.set(previousClampApplied);
    FragmentsMetrics.setEnabled(previousMetricsEnabled);
  }

  @Test
  public void servletUsesSharedViewportLimitPolicy() {
    ViewportLimitPolicy.setLimits(6, 11);

    assertEquals(6, FragmentsServlet.resolveLimitForRequest(null));
    assertEquals(6, FragmentsServlet.resolveLimitForRequest("invalid"));
    assertEquals(6, FragmentsServlet.resolveLimitForRequest("0"));
    assertEquals(9, FragmentsServlet.resolveLimitForRequest("9"));
    assertEquals(11, FragmentsServlet.resolveLimitForRequest("90"));
  }

  @Test
  public void servletClampMetricIsScopedToJ2clMarkedRequests() {
    ViewportLimitPolicy.setLimits(6, 11);
    FragmentsMetrics.setEnabled(true);

    assertEquals(11, FragmentsServlet.resolveLimitForRequest("90", false));
    assertEquals(0L, FragmentsMetrics.j2clViewportClampApplied.get());

    assertEquals(11, FragmentsServlet.resolveLimitForRequest("90", true));
    assertEquals(1L, FragmentsMetrics.j2clViewportClampApplied.get());

    assertEquals(6, FragmentsServlet.resolveLimitForRequest("invalid", true));
    assertEquals(1L, FragmentsMetrics.j2clViewportClampApplied.get());
  }

  @Test
  public void servletJsonPayloadIsDecodableByJ2clFragmentsResponse() {
    WaveId waveId = WaveId.of("example.com", "w+json");
    WaveletName waveletName =
        WaveletName.of(waveId, WaveletId.of(waveId.getDomain(), "conv+root"));
    Map<String, FragmentsFetcherCompat.BlipMeta> metas =
        new LinkedHashMap<String, FragmentsFetcherCompat.BlipMeta>();
    metas.put("b+1", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 10L));
    Map<SegmentId, VersionRange> ranges = new LinkedHashMap<SegmentId, VersionRange>();
    ranges.put(SegmentId.ofBlipId("b+1"), VersionRange.of(7L, 9L));
    ranges.put(SegmentId.ofBlipId("b+2"), VersionRange.of(7L, 9L));
    ranges.put(SegmentId.ofBlipId("b+missing"), VersionRange.of(7L, 9L));

    String json =
        FragmentsServlet.buildJson(
            waveletName,
            metas,
            Arrays.asList("b+1", "b+missing"),
            9L,
            new FragmentsRequest.Builder().setStartVersion(7L).setEndVersion(9L).build(),
            ranges,
            Arrays.asList(
                new FragmentsPayload.Fragment(
                    SegmentId.ofBlipId("b+1"),
                    "raw one",
                    Collections.<FragmentsPayload.Operation>emptyList(),
                    Collections.<FragmentsPayload.Operation>emptyList())));

    @SuppressWarnings("unchecked")
    Map<String, Object> decoded = new com.google.gson.Gson().fromJson(json, Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> version = (Map<String, Object>) decoded.get("version");
    @SuppressWarnings("unchecked")
    java.util.List<Map<String, Object>> blips =
        (java.util.List<Map<String, Object>>) decoded.get("blips");
    @SuppressWarnings("unchecked")
    java.util.List<Map<String, Object>> fragments =
        (java.util.List<Map<String, Object>>) decoded.get("fragments");
    Map<String, Object> missingMetaBlip = blips.get(1);
    Map<String, Object> fragment = fragments.get(0);

    assertEquals("ok", decoded.get("status"));
    assertEquals(9.0, version.get("snapshot"));
    assertEquals(2, blips.size());
    assertEquals("b+missing", missingMetaBlip.get("id"));
    assertEquals("", missingMetaBlip.get("author"));
    assertEquals(0.0, missingMetaBlip.get("lastModifiedTime"));
    assertEquals(3, ((java.util.List<?>) decoded.get("ranges")).size());
    assertEquals(1, fragments.size());
    assertEquals("raw one", fragment.get("rawSnapshot"));
    assertEquals(Collections.emptyList(), fragment.get("adjust"));
    assertEquals(Collections.emptyList(), fragment.get("diff"));
  }

  @Test
  public void servletLimitOneStillAddsGrowthPlaceholderRange() {
    Map<String, FragmentsFetcherCompat.BlipMeta> metas =
        new LinkedHashMap<String, FragmentsFetcherCompat.BlipMeta>();
    metas.put("b+1", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 10L));
    metas.put("b+2", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 20L));

    FragmentsServlet.SliceWindow window =
        FragmentsServlet.buildSliceWindow(
            metas,
            Arrays.asList("b+1", "b+2"),
            "b+1",
            "FORWARD",
            1);

    assertEquals(Arrays.asList("b+1", "b+2"), window.getRangeBlipIds());
    assertEquals(Collections.singletonList("b+1"), window.getLoadedBlipIds());
  }

  @Test
  public void servletNonPositiveLimitStillAddsSingleLoadedBlipAndPlaceholder() {
    Map<String, FragmentsFetcherCompat.BlipMeta> metas =
        new LinkedHashMap<String, FragmentsFetcherCompat.BlipMeta>();
    metas.put("b+1", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 10L));
    metas.put("b+2", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 20L));

    FragmentsServlet.SliceWindow window =
        FragmentsServlet.buildSliceWindow(
            metas,
            Arrays.asList("b+1", "b+2"),
            "b+1",
            "forward",
            0);

    assertEquals(Arrays.asList("b+1", "b+2"), window.getRangeBlipIds());
    assertEquals(Collections.singletonList("b+1"), window.getLoadedBlipIds());
  }

  @Test
  public void servletBackwardWindowUsesLeadingPlaceholderRange() {
    Map<String, FragmentsFetcherCompat.BlipMeta> metas =
        new LinkedHashMap<String, FragmentsFetcherCompat.BlipMeta>();
    metas.put("b+1", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 10L));
    metas.put("b+2", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 20L));
    metas.put("b+3", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 30L));

    FragmentsServlet.SliceWindow window =
        FragmentsServlet.buildSliceWindow(
            metas,
            Arrays.asList("b+1", "b+2", "b+3"),
            "b+3",
            "backward",
            2);

    assertEquals(Arrays.asList("b+1", "b+2", "b+3"), window.getRangeBlipIds());
    assertEquals(Arrays.asList("b+2", "b+3"), window.getLoadedBlipIds());
  }

  @Test
  public void servletBackwardAtStartDoesNotAddPlaceholderRange() {
    Map<String, FragmentsFetcherCompat.BlipMeta> metas =
        new LinkedHashMap<String, FragmentsFetcherCompat.BlipMeta>();
    metas.put("b+1", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 10L));
    metas.put("b+2", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 20L));

    FragmentsServlet.SliceWindow window =
        FragmentsServlet.buildSliceWindow(
            metas,
            Arrays.asList("b+1", "b+2"),
            "b+1",
            "backward",
            5);

    assertEquals(Collections.singletonList("b+1"), window.getRangeBlipIds());
    assertEquals(Collections.singletonList("b+1"), window.getLoadedBlipIds());
  }

  @Test
  public void servletEndOfWaveDoesNotAddPlaceholderRange() {
    Map<String, FragmentsFetcherCompat.BlipMeta> metas =
        new LinkedHashMap<String, FragmentsFetcherCompat.BlipMeta>();
    metas.put("b+1", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 10L));
    metas.put("b+2", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 20L));

    FragmentsServlet.SliceWindow window =
        FragmentsServlet.buildSliceWindow(
            metas,
            Arrays.asList("b+1", "b+2"),
            "b+2",
            "forward",
            5);

    assertEquals(Collections.singletonList("b+2"), window.getRangeBlipIds());
    assertEquals(Collections.singletonList("b+2"), window.getLoadedBlipIds());
  }

  @Test
  public void servletLoadsExplicitStartWhenManifestOrderMissesKnownBlip() {
    Map<String, FragmentsFetcherCompat.BlipMeta> metas =
        new LinkedHashMap<String, FragmentsFetcherCompat.BlipMeta>();
    metas.put("b+1", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 10L));
    metas.put("b+2", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 20L));
    metas.put("b+3", new FragmentsFetcherCompat.BlipMeta(
        ParticipantId.ofUnsafe("user@example.com"), 30L));

    FragmentsServlet.SliceWindow window =
        FragmentsServlet.buildSliceWindow(
            metas,
            Arrays.asList("b+1", "b+2"),
            "b+3",
            "forward",
            1);

    assertEquals(Arrays.asList("b+3"), window.getRangeBlipIds());
    assertEquals(Collections.singletonList("b+3"), window.getLoadedBlipIds());
  }
}
