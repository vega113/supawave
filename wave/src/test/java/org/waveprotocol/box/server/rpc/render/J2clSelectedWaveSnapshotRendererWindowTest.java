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

package org.waveprotocol.box.server.rpc.render;

import com.google.common.collect.ImmutableSet;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.robots.operations.TestingWaveletData;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.concurrencycontrol.channel.FragmentsMetrics;
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;

/**
 * F-1: viewport-window contract for the J2CL server-first snapshot renderer.
 *
 * <p>Covers parity rows R-3.5 (visible-region container model), R-6.1
 * (server-rendered first paint), R-7.1 (initial visible window), and R-7.4
 * (the {@code j2cl.viewport.initial_window} counter advances on the
 * server-first path).
 */
public final class J2clSelectedWaveSnapshotRendererWindowTest extends TestCase {
  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+server-window");
  private static final WaveletId CONV_ROOT = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("viewer@example.com");

  private boolean previousMetricsEnabled;
  private long initialWindowsBefore;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    previousMetricsEnabled = FragmentsMetrics.isEnabled();
    FragmentsMetrics.setEnabled(true);
    initialWindowsBefore = FragmentsMetrics.j2clViewportInitialWindows.get();
  }

  @Override
  protected void tearDown() throws Exception {
    FragmentsMetrics.setEnabled(previousMetricsEnabled);
    super.tearDown();
  }

  public void testSnapshotClampsToInitialWindowAndAdvertisesContract() {
    TestingWaveletData data = new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    for (int i = 0; i < 12; i++) {
      data.appendBlipWithText("Body " + i);
    }

    J2clSelectedWaveSnapshotRenderer renderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(data.copyWaveletData(), true),
            500L,
            262144,
            5,
            new SequenceTimeSource(0L, 10L, 20L, 30L, 40L, 50L));

    J2clSelectedWaveSnapshotRenderer.SnapshotResult result =
        renderer.renderRequestedWave(WAVE_ID.serialise(), VIEWER);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.SNAPSHOT, result.getMode());
    String html = result.getSnapshotHtml();
    assertEquals("Five blips inside the window", 5, countOccurrences(html, "data-blip-id="));
    assertTrue(
        "Server-first marker advertised on the wrapper",
        html.contains("data-j2cl-server-first-surface=\"true\""));
    assertTrue(
        "Window-size attribute advertised on the wrapper",
        html.contains("data-j2cl-initial-window-size=\"5\""));
    assertTrue(
        "Terminal placeholder appended after the windowed slice",
        html.contains("data-j2cl-server-placeholder=\"true\""));
    assertEquals(
        "Snapshot-path advances the J2CL viewport-initial-window counter",
        initialWindowsBefore + 1,
        FragmentsMetrics.j2clViewportInitialWindows.get());
  }

  public void testSnapshotPayloadStaysWellBelowWholeWaveBaseline() {
    TestingWaveletData data = new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    for (int i = 0; i < 12; i++) {
      data.appendBlipWithText(
          "Lorem ipsum dolor sit amet, consectetur adipiscing elit, blip " + i);
    }

    J2clSelectedWaveSnapshotRenderer windowedRenderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(data.copyWaveletData(), true),
            500L,
            262144,
            5,
            new SequenceTimeSource(0L, 10L, 20L, 30L, 40L, 50L));
    J2clSelectedWaveSnapshotRenderer.SnapshotResult windowed =
        windowedRenderer.renderRequestedWave(WAVE_ID.serialise(), VIEWER);

    J2clSelectedWaveSnapshotRenderer.SnapshotResult whole =
        new J2clSelectedWaveSnapshotRenderer(
                providerFor(data.copyWaveletData(), true),
                500L,
                262144,
                0,
                new SequenceTimeSource(0L, 10L, 20L, 30L, 40L, 50L))
            .renderRequestedWave(WAVE_ID.serialise(), VIEWER);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.SNAPSHOT, windowed.getMode());
    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.SNAPSHOT, whole.getMode());

    int windowedSize = windowed.getSnapshotHtml().getBytes().length;
    int wholeSize = whole.getSnapshotHtml().getBytes().length;
    assertTrue(
        "Windowed payload (" + windowedSize + " B) must stay under 75% of whole-wave ("
            + wholeSize + " B)",
        windowedSize * 100L < wholeSize * 75L);
  }

  /**
   * Issue #1050: the legacy {@code ?view=gwt} fall-through invokes
   * {@link J2clSelectedWaveSnapshotRenderer#renderRequestedWaveForLegacy}
   * with the same wave ID. The returned snapshot must:
   *
   * <ul>
   *   <li>contain the legacy {@code class="blip"} host markup that
   *       {@code ServerHtmlRenderer} emits (so a GWT rollback still renders
   *       the per-wave first paint instead of an empty skeleton);</li>
   *   <li>NOT carry the F-1 windowed-surface markers
   *       ({@code data-j2cl-server-first-surface},
   *       {@code data-j2cl-initial-window-size}) — those are J2CL-only;</li>
   *   <li>NOT advance the {@code j2cl.viewport.initial_window} counter —
   *       the GWT route is not a J2CL viewport open.</li>
   * </ul>
   */
  public void testLegacyEntryPointEmitsLegacyMarkupAndOmitsJ2clMarkersAndCounter() {
    TestingWaveletData data = new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    for (int i = 0; i < 6; i++) {
      data.appendBlipWithText("Body " + i);
    }

    J2clSelectedWaveSnapshotRenderer renderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(data.copyWaveletData(), true),
            500L,
            262144,
            5,
            new SequenceTimeSource(0L, 10L, 20L, 30L, 40L, 50L));

    J2clSelectedWaveSnapshotRenderer.SnapshotResult result =
        renderer.renderRequestedWaveForLegacy(WAVE_ID.serialise(), VIEWER);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.SNAPSHOT, result.getMode());
    assertTrue(
        "Legacy entry point must emit ServerHtmlRenderer's class=\"blip\" host markup so "
            + "?view=gwt rollback retains the legacy per-wave first paint",
        result.getSnapshotHtml().contains("class=\"blip\""));
    assertFalse(
        "Legacy entry point must not carry the F-1 windowed-surface marker",
        result.getSnapshotHtml().contains("data-j2cl-server-first-surface"));
    assertFalse(
        "Legacy entry point must not carry the F-1 initial-window-size marker",
        result.getSnapshotHtml().contains("data-j2cl-initial-window-size"));
    assertEquals(
        "Legacy entry point must not advance the J2CL viewport-initial-window counter",
        initialWindowsBefore,
        FragmentsMetrics.j2clViewportInitialWindows.get());
  }

  public void testZeroWindowSizeSkipsWindowMarkersAndStillCountsInitialWindow() {
    TestingWaveletData data = new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    data.appendBlipWithText("Single blip");

    J2clSelectedWaveSnapshotRenderer renderer =
        new J2clSelectedWaveSnapshotRenderer(
            providerFor(data.copyWaveletData(), true),
            500L,
            262144,
            0,
            new SequenceTimeSource(0L, 10L, 20L, 30L));

    J2clSelectedWaveSnapshotRenderer.SnapshotResult result =
        renderer.renderRequestedWave(WAVE_ID.serialise(), VIEWER);

    assertEquals(J2clSelectedWaveSnapshotRenderer.Mode.SNAPSHOT, result.getMode());
    assertFalse(
        "No window markers when the window is disabled",
        result.getSnapshotHtml().contains("data-j2cl-initial-window-size"));
    // Even when the window is disabled, the snapshot path still represents a
    // J2CL initial-window event for telemetry — operators care that the
    // counter never silently freezes when the window size is mis-tuned.
    assertEquals(
        "Snapshot-path advances the counter regardless of window size",
        initialWindowsBefore + 1,
        FragmentsMetrics.j2clViewportInitialWindows.get());
  }

  private static int countOccurrences(String haystack, String needle) {
    if (haystack == null || needle == null || needle.isEmpty()) {
      return 0;
    }
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  private static WaveletProvider providerFor(
      List<ObservableWaveletData> wavelets, boolean allowAccess) {
    Map<WaveletName, CommittedWaveletSnapshot> snapshots = new HashMap<WaveletName, CommittedWaveletSnapshot>();
    ImmutableSet.Builder<WaveletId> waveletIds = ImmutableSet.builder();
    WaveId waveId = null;
    for (ObservableWaveletData waveletData : wavelets) {
      waveId = waveletData.getWaveId();
      waveletIds.add(waveletData.getWaveletId());
      snapshots.put(
          WaveletName.of(waveletData.getWaveId(), waveletData.getWaveletId()),
          new CommittedWaveletSnapshot(waveletData, HashedVersion.unsigned(10)));
    }
    final WaveId finalWaveId = waveId;
    final ImmutableSet<WaveletId> finalWaveletIds = waveletIds.build();
    return new WaveletProvider() {
      @Override
      public void initialize() {
      }

      @Override
      public void submitRequest(
          WaveletName waveletName, ProtocolWaveletDelta delta, SubmitRequestListener listener) {
      }

      @Override
      public void getHistory(
          WaveletName waveletName,
          HashedVersion versionStart,
          HashedVersion versionEnd,
          Receiver<TransformedWaveletDelta> receiver) {
      }

      @Override
      public boolean checkAccessPermission(WaveletName waveletName, ParticipantId participantId) {
        return allowAccess;
      }

      @Override
      public ExceptionalIterator<WaveId, WaveServerException> getWaveIds() {
        return null;
      }

      @Override
      public ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) {
        return finalWaveId != null && finalWaveId.equals(waveId)
            ? finalWaveletIds
            : ImmutableSet.<WaveletId>of();
      }

      @Override
      public CommittedWaveletSnapshot getSnapshot(WaveletName waveletName) {
        return snapshots.get(waveletName);
      }

      @Override
      public HashedVersion getHashedVersion(WaveletName waveletName, long version) {
        return null;
      }
    };
  }

  private static final class SequenceTimeSource
      implements J2clSelectedWaveSnapshotRenderer.CurrentTimeSource {
    private final long[] values;
    private int index;

    private SequenceTimeSource(long... values) {
      this.values = values;
    }

    @Override
    public long currentTimeMillis() {
      if (values.length == 0) {
        return 0L;
      }
      if (index >= values.length) {
        return values[values.length - 1];
      }
      return values[index++];
    }
  }
}
