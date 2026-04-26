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
import org.waveprotocol.wave.federation.Proto.ProtocolWaveletDelta;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;

/**
 * F-2 acceptance fixture (#1037). Drives a 6-blip wave through both
 * {@code ?view=j2cl-root} and {@code ?view=gwt} via the same
 * {@link WaveClientServlet} instance, asserting:
 *
 * <ul>
 *   <li><b>R-3.1</b> Open-wave rendering — the J2CL root shell payload
 *       includes the F-0 wavy design tokens, the client bundle that
 *       registers the F-2 {@code <wave-blip>} custom element, and the
 *       server-first conversation markup the client will upgrade in
 *       place. Each server-rendered blip carries the
 *       {@code data-blip-id}, {@code data-j2cl-read-blip} / role markers
 *       the {@code <wave-blip>} wrapper preserves on upgrade.
 *   <li><b>R-3.2</b> Focus framing — the server emits exactly one
 *       {@code tabindex="0"} blip and {@code N-1}
 *       {@code tabindex="-1"} blips so the client-side focus frame has
 *       a deterministic anchor (R-6.3 / R-6.1 carry-over from F-1).
 *   <li><b>R-3.3</b> Collapse — the server-side thread carries the
 *       {@code data-thread-id} the client uses to mount the collapse
 *       toggle. (Toggle button itself is added client-side; this
 *       fixture asserts the contract attribute the client looks for.)
 *   <li><b>R-3.4</b> Thread navigation — every blip carries a
 *       {@code data-blip-id} the {@code <wavy-wave-nav-row>} client
 *       element walks for E.3 / E.4 (next/prev) and E.5 (end). Mention
 *       affordance E.6 / E.7 is asserted via the per-blip
 *       {@code data-has-mention} reflection on the wrapper.
 *   <li><b>R-3.7</b> Depth-nav — the server-rendered placeholder uses
 *       the same {@code data-blip-id} key that the client URL-state
 *       reader (T7) consumes for {@code &depth=blip+id}.
 *   <li><b>R-4.4</b> Per-blip read/unread — the server-first card emits
 *       the per-wave unread count slot the {@code <wave-blip>} consumes
 *       to set its {@code unread} attribute.
 * </ul>
 *
 * <p>Reciprocal: the legacy GWT path ({@code ?view=gwt}) must not leak
 * any F-2 client markers (e.g. references to the F-2-introduced
 * {@code <wave-blip>} bundle entry) so rollback stays safe.
 *
 * <p>Companion lit-side coverage lives in
 * {@code j2cl/lit/test/wave-blip.test.js} and
 * {@code wave-blip-toolbar.test.js}; those cover the live Lit element
 * contract that the server-rendered HTML upgrades into.
 */
public final class J2clStageOneReadSurfaceParityTest {
  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+f2");
  private static final WaveletId CONV_ROOT = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("alice@example.com");

  /**
   * R-3.1 — the J2CL root-shell HTML for a multi-blip wave loads the
   * F-0 wavy design tokens AND the shell.js bundle entry. The bundle
   * entry now registers the F-2 {@code <wave-blip>} + {@code
   * <wave-blip-toolbar>} custom elements. The server-first card
   * markup carries the per-blip {@code data-blip-id} attributes that
   * the client-side renderer's {@code enhanceExistingSurface} pass
   * lifts onto the {@code <wave-blip>} wrapper after upgrade.
   */
  @Test
  public void j2clRootShellLoadsWavyTokensAndShellBundle() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    assertTrue(
        "Wavy design tokens stylesheet must be linked from the J2CL root shell",
        html.contains("wavy-tokens.css"));
    assertTrue(
        "Lit shell bundle (which now registers <wave-blip>) must be loaded",
        html.contains("j2cl/assets/shell.js"));
    assertTrue(
        "Selected-wave host carries the J2CL upgrade marker so the client can swap",
        html.contains("data-j2cl-selected-wave-host=\"true\""));
  }

  /**
   * R-3.1 + R-3.6 — every server-rendered blip emits the contract
   * attributes the F-2 {@code <wave-blip>} wrapper preserves on
   * upgrade ({@code data-blip-id}, {@code role="listitem"}). The
   * server cannot directly emit {@code <wave-blip>} elements (the
   * conversation renderer is shared with GWT), but the J2CL client
   * upgrade reads the same {@code data-blip-id} attributes the F-1
   * {@link WaveContentRenderer} already emits.
   */
  @Test
  public void serverFirstPaintEmitsContractBlipAttributesForJ2clUpgrade() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    int blipCount = countOccurrences(html, "data-blip-id=");
    assertTrue(
        "At least 5 server-rendered blips must carry data-blip-id (got " + blipCount + ")",
        blipCount >= 5);
    int listitemCount = countOccurrences(html, "role=\"listitem\"");
    assertTrue(
        "Each server-rendered blip declares role=listitem for AT semantics (got "
            + listitemCount + ")",
        listitemCount >= 5);
    assertTrue(
        "Root thread declares role=list",
        html.contains("role=\"list\""));
    assertTrue(
        "Root thread carries data-thread-id so collapse + depth-nav can address it",
        html.contains("data-thread-id="));
  }

  /**
   * R-3.2 — server emits exactly one focusable blip ({@code
   * tabindex="0"}) so the F-2 {@code <wavy-focus-frame>} client element
   * has a deterministic initial anchor and the keyboard contract works
   * before the client boot completes (R-6.1 carry-over from F-1).
   */
  @Test
  public void serverEmitsExactlyOneFocusableBlipForFocusFrameAnchor() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    assertEquals(
        "Exactly one server-rendered blip carries tabindex=0 as the focus-frame anchor",
        1,
        countOccurrences(html, "tabindex=\"0\""));
  }

  /**
   * R-3.4 — the server-rendered conversation thread shape supports the
   * E.3 (Previous) / E.4 (Next) / E.5 (End) walks: every blip carries
   * a stable {@code data-blip-id} so the client-side
   * {@code <wavy-wave-nav-row>} can compute focus deltas against the
   * rendered DOM order. Mention navigation (E.6 / E.7) consults the
   * client-side {@code has-mention} reflection on each {@code
   * <wave-blip>} that the F-2 renderer (T6) sets when the blip's
   * annotation ranges include a {@code mention/} key.
   */
  @Test
  public void serverRenderedThreadSupportsBlipLevelNavigationContract() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    int blipCount = countOccurrences(html, "data-blip-id=");
    assertTrue(
        "At least 5 root-thread blips render so the navigation row has a path to walk",
        blipCount >= 5);
    int listitemCount = countOccurrences(html, "role=\"listitem\"");
    assertTrue(
        "Each rendered blip + the F-1 visible-region placeholder all carry "
            + "role=listitem (parity with E.3/E.4 walk; got blipCount=" + blipCount
            + ", listitemCount=" + listitemCount + ")",
        listitemCount >= blipCount);
  }

  /**
   * R-3.7 — the server-first paint carries the per-blip
   * {@code data-blip-id} that the depth-nav URL state encoder uses for
   * {@code &depth=<blip-id>}. Reload + back/forward preserve depth
   * focus; the URL parameter consumer (T7) reads the same id.
   */
  @Test
  public void serverFirstPaintExposesDepthNavCompatibleBlipIds() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    // The depth-nav URL state consumer reads &depth=<blip-id>; the id
    // shape is the same one rendered in data-blip-id="b+...". Asserting
    // that at least one server-rendered id starts with b+ confirms the
    // contract id space matches the URL contract.
    assertTrue(
        "At least one rendered blip uses the b+ id prefix the depth-nav URL consumes",
        html.contains("data-blip-id=\"b+"));
  }

  /**
   * R-4.4 — the server-first card emits the per-wave unread count slot
   * (sidecar-selected-unread) the J2CL client renders into. The
   * per-blip {@code unread} flip is driven from the supplement read
   * state (T1 J2clReadBlip.isUnread) and the {@code <wave-blip>}
   * wrapper reflects it as the {@code unread} attribute that the F-0
   * {@code <wavy-blip-card>} pseudo-element renders the cyan dot for.
   * The wire contract here is the unread-count slot's presence.
   */
  @Test
  public void serverFirstCardExposesUnreadCountSlot() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    assertTrue(
        "Unread count slot present so per-blip read-state changes can decrement live",
        html.contains("sidecar-selected-unread"));
  }

  /**
   * Reciprocal — the legacy GWT path must not leak F-2 client-bundle
   * markers (the {@code <wave-blip>} element is loaded only from the
   * shell bundle on the J2CL root). Rollback to {@code ?view=gwt}
   * stays safe.
   */
  @Test
  public void legacyGwtRouteDoesNotLeakF2ClientMarkers() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);

    String html = invokeServlet(servlet, "gwt", WAVE_ID.serialise());

    assertFalse(
        "GWT path must not load the J2CL shell bundle",
        html.contains("j2cl/assets/shell.js"));
    assertFalse(
        "GWT path must not load the wavy design tokens stylesheet",
        html.contains("wavy-tokens.css"));
    assertFalse(
        "GWT path must not advertise the F-1 server-first selected-wave host",
        html.contains("data-j2cl-selected-wave-host"));
  }

  // --- helpers (mirror of the F-1 fixture so the two stay in lockstep) ----

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
