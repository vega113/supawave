/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.common.ExceptionalIterator;
import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.FeatureFlagService;
import org.waveprotocol.box.server.persistence.FeatureFlagStore;
import org.waveprotocol.box.server.robots.operations.TestingWaveletData;
import org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRenderer;
import org.waveprotocol.box.server.rpc.render.WavePreRenderer;
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
 * F-1 acceptance fixture: drives a 12-blip wave through both
 * {@code ?view=j2cl-root} and {@code ?view=gwt} via the same
 * {@link WaveClientServlet} instance, asserting that the J2CL surface
 * delivers a viewport-clamped first paint while the legacy GWT path is
 * byte-for-byte unchanged. Demonstrates parity-matrix rows R-3.5, R-3.6,
 * R-6.1, R-6.3, R-7.1, R-7.4 against an in-memory wavelet provider.
 */
public final class J2clViewportFirstPaintParityTest {
  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+f1");
  private static final WaveletId CONV_ROOT = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("alice@example.com");

  private boolean previousMetricsEnabled;
  private long initialWindowsBefore;
  private long fallbackBefore;

  @Before
  public void setUp() {
    previousMetricsEnabled = FragmentsMetrics.isEnabled();
    FragmentsMetrics.setEnabled(true);
    initialWindowsBefore = FragmentsMetrics.j2clViewportInitialWindows.get();
    fallbackBefore = FragmentsMetrics.j2clViewportSnapshotFallbacks.get();
  }

  @After
  public void tearDown() {
    FragmentsMetrics.setEnabled(previousMetricsEnabled);
  }

  /**
   * R-3.5 / R-7.1 / R-6.1: a J2CL root-shell response for a wave with 12
   * blips delivers exactly 5 blips inline plus a terminal placeholder, the
   * server-first surface markers, and bumps the
   * {@code j2clViewportInitialWindows} counter. The
   * {@code j2clViewportSnapshotFallbacks} counter must stay at zero on the
   * snapshot path (R-7.4).
   */
  @Test
  public void j2clRootShellDeliversWindowedSnapshotForLargeWave() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(12));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    assertTrue(
        "J2CL root response carries the server-first surface marker",
        html.contains("data-j2cl-server-first-surface=\"true\""));
    assertTrue(
        "J2CL root response advertises the initial window size",
        html.contains("data-j2cl-initial-window-size=\"5\""));
    assertEquals(
        "J2CL root response renders exactly 5 root-thread blips inline",
        5,
        countOccurrences(html, "data-blip-id="));
    assertTrue(
        "J2CL root response appends the visible-region terminal placeholder",
        html.contains("data-j2cl-server-placeholder=\"true\""));
    assertEquals(
        "J2CL root response keyboard-focuses exactly one blip",
        1,
        countOccurrences(html, "tabindex=\"0\""));
    assertEquals(
        "J2CL root response marks the other inline blips unfocusable",
        4,
        countOccurrences(html, "tabindex=\"-1\""));
    assertEquals(
        "Snapshot path advances the J2CL viewport-initial-window counter",
        initialWindowsBefore + 1,
        FragmentsMetrics.j2clViewportInitialWindows.get());
    assertEquals(
        "Snapshot path must not advance the whole-wave fallback counter",
        fallbackBefore,
        FragmentsMetrics.j2clViewportSnapshotFallbacks.get());
  }

  /**
   * R-3.6: every blip carries a stable {@code data-blip-id} attribute that
   * the J2CL DOM-as-view provider can resolve, even when the rendered HTML
   * is the windowed first paint (no full conversation tree).
   */
  @Test
  public void serverRenderedBlipsExposeDomAsViewAttributes() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(7));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    assertTrue(
        "Root thread carries data-thread-id for DOM-as-view resolution",
        html.contains("data-thread-id="));
    assertTrue(
        "Root thread declares role=list for AT semantics",
        html.contains("role=\"list\""));
    int listitems = countOccurrences(html, "role=\"listitem\"");
    assertTrue(
        "Each root-thread blip declares role=listitem (got " + listitems + ")",
        listitems >= 5);
  }

  /**
   * Reciprocal R-6.4 / R-7.4: the legacy GWT skeleton response for the
   * same wave must not leak any of the F-1 server-first markers (the
   * server-first surface attribute, the initial-window size, the
   * upgrade-placeholder), so rollback stays safe and operators can still
   * route past J2CL via {@code ?view=gwt} without surprising the legacy
   * client. The remainder of the legacy skeleton is shape-tested against
   * its own (existing) test class; this assertion is the F-1 contract.
   */
  @Test
  public void legacyGwtRouteDoesNotEmitServerFirstMarkers() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(12));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "gwt", WAVE_ID.serialise());

    assertFalse(
        "GWT path must not advertise the server-first surface",
        html.contains("data-j2cl-server-first-surface"));
    assertFalse(
        "GWT path must not advertise the J2CL initial-window size",
        html.contains("data-j2cl-initial-window-size"));
    assertFalse(
        "GWT path must not emit the J2CL upgrade-placeholder",
        html.contains("data-j2cl-upgrade-placeholder"));
    assertEquals(
        "GWT path does not advance the J2CL viewport-initial-window counter",
        initialWindowsBefore,
        FragmentsMetrics.j2clViewportInitialWindows.get());
  }

  /**
   * J-UI-8 (#1086, R-6.1): the server-rendered snapshot must land
   * inside the {@code .sidecar-selected-content} grid host (visible by
   * default), not inside the {@code .sidecar-empty-state} element which
   * is {@code hidden} when a snapshot is present, and not nested under
   * any {@code hidden} ancestor or {@code display:none} inline style.
   * Pinning this prevents the audit's "rendered into a hidden seam"
   * regression class.
   */
  @Test
  public void j2clRootShellSnapshotLandsInVisibleRegion() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(7));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    int blipIdIdx = html.indexOf("data-blip-id=");
    assertTrue("Snapshot must surface at least one data-blip-id node", blipIdIdx >= 0);

    // The sidecar-selected-content host is visible by default
    // (.sidecar-selected-content { display: grid } in sidecar.css). The
    // snapshot must live inside this host.
    int contentHostIdx =
        html.lastIndexOf("<div class=\"sidecar-selected-content\"", blipIdIdx);
    assertTrue(
        "Snapshot blip must live inside .sidecar-selected-content",
        contentHostIdx >= 0);

    // The hidden empty-state recipe must come *after* the snapshot in
    // the DOM, never wrap it.
    int hiddenEmptyStateIdx =
        html.indexOf("<div class=\"sidecar-empty-state\" hidden", contentHostIdx);
    assertTrue(
        "Empty-state recipe must be hidden when a snapshot is present",
        hiddenEmptyStateIdx > blipIdIdx);

    // No display:none / visibility:hidden inline styles must wrap the
    // selected-wave host.
    int hostStart = html.lastIndexOf("<div class=\"sidecar-selected-host\"", blipIdIdx);
    assertTrue(hostStart >= 0);
    String hostOpen = html.substring(hostStart, html.indexOf('>', hostStart));
    assertFalse(
        "selected-wave host must not be hidden via inline style",
        hostOpen.contains("display:none") || hostOpen.contains("visibility:hidden")
            || hostOpen.contains(" hidden"));
  }

  /**
   * J-UI-8 (#1086, R-6.3): the server-first card carries
   * {@code aria-busy="true"} on the snapshot path so AT clients know the
   * pre-upgrade state is in flux. The J2CL view clears the attribute in
   * {@code clearServerFirstMarkers()} once the live render replaces the
   * server-first state.
   */
  @Test
  public void j2clRootShellSnapshotCardCarriesAriaBusy() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(5));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    int cardIdx = html.indexOf("<section class=\"sidecar-selected-card\"");
    assertTrue(cardIdx >= 0);
    int cardEnd = html.indexOf('>', cardIdx);
    String cardOpenTag = html.substring(cardIdx, cardEnd);
    // aria-busy is now injected via an inline script so the no-JS path never
    // leaves the region permanently busy. Verify the open tag is attribute-free
    // and the inline script is present in the section body.
    assertFalse(
        "Snapshot card open tag must not carry aria-busy as a static attribute"
            + " — that would be permanent on the no-JS path",
        cardOpenTag.contains("aria-busy"));
    assertTrue(
        "Snapshot mode must emit the aria-busy inline script so AT clients know"
            + " the pre-upgrade content is in flux",
        html.indexOf("document.currentScript.parentElement.setAttribute('aria-busy'", cardIdx) > 0);
  }

  /**
   * R-7.1 boundary: the server-first response payload for the windowed
   * snapshot is meaningfully smaller than the legacy whole-wave HTML
   * (this is the operator-facing reason the lane exists). The windowed
   * shape is exercised through the production
   * {@link J2clSelectedWaveSnapshotRenderer} entry point; the whole-wave
   * baseline is sampled directly from
   * {@link org.waveprotocol.box.server.rpc.render.WaveContentRenderer} so
   * the comparison stays apples-to-apples (both render through the same
   * conversation pipeline; only the windowing changes).
   */
  @Test
  public void windowedSnapshotPayloadIsSmallerThanWholeWaveBaseline() throws Exception {
    List<ObservableWaveletData> waveletData = buildWaveletData(12);

    J2clSelectedWaveSnapshotRenderer windowed =
        new J2clSelectedWaveSnapshotRenderer(providerForWave(waveletData));
    J2clSelectedWaveSnapshotRenderer.SnapshotResult windowedResult =
        windowed.renderRequestedWave(WAVE_ID.serialise(), VIEWER);
    String windowedHtml = windowedResult.getSnapshotHtml();

    org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl waveView =
        org.waveprotocol.wave.model.wave.data.impl.WaveViewDataImpl.create(WAVE_ID);
    for (ObservableWaveletData wavelet : waveletData) {
      waveView.addWavelet(wavelet);
    }
    String wholeHtml =
        org.waveprotocol.box.server.rpc.render.WaveContentRenderer.renderWaveContent(
            waveView, VIEWER);

    int windowedSize = windowedHtml.length();
    int wholeSize = wholeHtml.length();
    assertTrue(
        "Windowed payload (" + windowedSize + ") must stay under 75% of whole-wave ("
            + wholeSize + ")",
        windowedSize * 100L < wholeSize * 75L);
  }

  // --- helpers ---------------------------------------------------------------

  private static List<ObservableWaveletData> buildWaveletData(int blipCount) {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    for (int i = 0; i < blipCount; i++) {
      data.appendBlipWithText(
          "Lorem ipsum dolor sit amet, consectetur adipiscing elit, blip " + i);
    }
    return data.copyWaveletData();
  }

  private static WaveletProvider providerForWave(List<ObservableWaveletData> wavelets) {
    Map<WaveletName, CommittedWaveletSnapshot> snapshots = new HashMap<>();
    ImmutableSet.Builder<WaveletId> waveletIds = ImmutableSet.builder();
    for (ObservableWaveletData waveletData : wavelets) {
      waveletIds.add(waveletData.getWaveletId());
      snapshots.put(
          WaveletName.of(waveletData.getWaveId(), waveletData.getWaveletId()),
          new CommittedWaveletSnapshot(waveletData, HashedVersion.unsigned(10)));
    }
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
        return true;
      }

      @Override
      public ExceptionalIterator<WaveId, WaveServerException> getWaveIds() {
        return null;
      }

      @Override
      public ImmutableSet<WaveletId> getWaveletIds(WaveId waveId) {
        return WAVE_ID.equals(waveId) ? finalWaveletIds : ImmutableSet.of();
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

  private static WaveClientServlet createServlet(
      ParticipantId user, J2clSelectedWaveSnapshotRenderer snapshotRenderer)
      throws Exception {
    Config config = ConfigFactory.parseString(
        "core.http_frontend_addresses=[\"127.0.0.1:9898\"]\n"
            + "core.http_websocket_public_address=\"\"\n"
            + "core.http_websocket_presented_address=\"\"\n"
            + "core.search_type=\"memory\"\n"
            + "administration.analytics_account=\"\"\n");
    SessionManager sessionManager = mock(SessionManager.class);
    AccountStore accountStore = mock(AccountStore.class);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(user);
    when(sessionManager.getLoggedInUser((WebSession) null)).thenReturn(user);
    if (user != null) {
      AccountData accountData = mock(AccountData.class);
      HumanAccountData humanAccountData = mock(HumanAccountData.class);
      when(accountData.isHuman()).thenReturn(true);
      when(accountData.asHuman()).thenReturn(humanAccountData);
      when(humanAccountData.getRole()).thenReturn(HumanAccountData.ROLE_USER);
      when(accountStore.getAccount(user)).thenReturn(accountData);
      when(sessionManager.getLoggedInAccount(any(WebSession.class))).thenReturn(accountData);
      when(sessionManager.getLoggedInAccount((WebSession) null)).thenReturn(accountData);
    }
    return new WaveClientServlet(
        "example.com",
        config,
        sessionManager,
        accountStore,
        new VersionServlet("test", 0L),
        mock(WavePreRenderer.class),
        snapshotRenderer,
        new FeatureFlagService(featureFlagStore()));
  }

  private static String invokeServlet(WaveClientServlet servlet, String view, String waveId)
      throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(request.getParameter("view")).thenReturn(view);
    when(request.getParameterValues("view")).thenReturn(new String[]{view});
    when(request.getParameter("wave")).thenReturn(waveId);
    when(request.getParameterNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getSession(false)).thenReturn(mock(HttpSession.class));
    when(response.getWriter()).thenReturn(new PrintWriter(body));
    servlet.doGet(request, response);
    return body.toString();
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

  private static FeatureFlagStore featureFlagStore() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(Collections.emptyList());
    return store;
  }
}
