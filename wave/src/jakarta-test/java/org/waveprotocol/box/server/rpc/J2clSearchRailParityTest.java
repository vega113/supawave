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
 * F-2 slice 3 (#1047) acceptance fixture. Drives the same servlet
 * as {@link J2clStageOneReadSurfaceParityTest} but asserts on the
 * NEW search rail + search-help modal + wavy header chrome that
 * slice 3 mounts inside {@code renderJ2clRootShellPage}.
 *
 * <p>Inventory affordances asserted (45 total, organised below):
 *
 * <ul>
 *   <li><b>A.1 / A.2 / A.5 / A.6 / A.7</b> — wavy header chrome:
 *       brand link with cyan signal-dot, locale picker (seven
 *       options), notifications bell with violet unread dot,
 *       inbox/mail icon, user-menu trigger (avatar + visible email).
 *   <li><b>B.1–B.12</b> — search rail: query box (default
 *       {@code in:inbox}) + waveform glyph; help-trigger; New Wave
 *       button with {@code aria-keyshortcuts}; Manage saved searches;
 *       six saved-search folders with the canonical query strings;
 *       refresh; result-count {@code aria-live}.
 *   <li><b>B.13–B.18</b> — per-digest cards live in
 *       {@code j2cl/lit/test/wavy-search-rail-card.test.js}; this
 *       fixture asserts only that the slot under
 *       {@code <wavy-search-rail>} accepts them.
 *   <li><b>C.1–C.22</b> — every advertised search-help token literal
 *       is present in the server-rendered modal body (mirrors the
 *       parser-side {@code J2clSearchHelpTokenParseTest}).
 * </ul>
 *
 * <p>The fixture asserts on the RAW server-rendered HTML, not on a
 * post-upgrade DOM. T5 SSRs the inner light DOM of every new element
 * so the J2CL client upgrade is content-preserving (mirrors the F-2
 * S1 {@code <wavy-blip-card>} server-render contract).
 *
 * <p>Reciprocal: {@code legacyGwtRouteDoesNotLeakF2S3Markers} ensures
 * none of the new elements appear on {@code ?view=gwt} so rollback
 * stays safe; {@code signedOutJ2clRootShellDoesNotMountSearchRailOrHeaderChrome}
 * defends against accidentally regressing the unauthenticated landing
 * into emitting search/help to anonymous users.
 */
public final class J2clSearchRailParityTest {

  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+f2s3");
  private static final WaveletId CONV_ROOT = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("alice@example.com");

  // ---------- A.* wavy header chrome ----------

  /** A.1, A.2, A.7 wrapper — wavy-header host element with the contract attributes. */
  @Test
  public void j2clRootShellEmitsWavyHeaderHostWithLocaleAndAddressAttributes() throws Exception {
    String html = renderJ2clRootShell();
    assertEquals(
        "Exactly one <wavy-header> host is mounted in the J2CL root shell",
        1,
        countOccurrences(html, "<wavy-header"));
    assertTrue("Header carries signed-in flag", html.contains("signed-in"));
    assertTrue("Header defaults to locale=en", html.contains("locale=\"en\""));
    assertTrue(
        "Header carries data-address with the viewer's email",
        html.contains("data-address=\"alice@example.com\""));
    assertTrue(
        "Header carries user-name (used for the avatar initials fallback)",
        html.contains("user-name=\"alice@example.com\""));
  }

  /** A.2 — every locale supported by the GWT i18n bundle is offered. */
  @Test
  public void wavyHeaderInnerLightDomEmitsAllSevenLocales() throws Exception {
    String html = renderJ2clRootShell();
    String[] codes = {"en", "de", "es", "fr", "ru", "sl", "zh_TW"};
    for (String code : codes) {
      assertTrue(
          "Locale picker must emit <option value=\"" + code + "\">",
          html.contains("<option value=\"" + code + "\""));
    }
  }

  /** A.1, A.2, A.5, A.6, A.7 — every chrome class marker present in light DOM. */
  @Test
  public void wavyHeaderInnerLightDomEmitsBrandLocaleBellMailUserMenuChrome()
      throws Exception {
    String html = renderJ2clRootShell();
    assertTrue("A.1 brand link class", html.contains("class=\"brand\""));
    assertTrue(
        "A.1 cyan signal-dot accent (uses --wavy-signal-cyan in the element CSS)",
        html.contains("class=\"brand-dot\""));
    assertTrue("A.2 locale picker class", html.contains("class=\"locale\""));
    assertTrue("A.5 notifications bell class", html.contains("class=\"bell\""));
    assertTrue(
        "A.5 violet unread dot (initially hidden)",
        html.contains("class=\"dot violet\" hidden"));
    assertTrue("A.6 mail icon class", html.contains("class=\"mail\""));
    assertTrue(
        "A.6 mail icon links to inbox query with J2CL root routing",
        html.contains("href=\"/?view=j2cl-root&amp;q=in%3Ainbox\""));
    assertTrue("A.7 user-menu trigger class", html.contains("class=\"user-menu\""));
    assertTrue(
        "A.7 user-menu trigger advertises menu popup semantics",
        html.contains("aria-haspopup=\"menu\""));
    assertTrue("A.7 avatar chip class", html.contains("class=\"avatar\""));
    assertTrue(
        "A.7 visible user-email span (matches GWT inventory \"avatar + email\")",
        html.contains("class=\"user-email\""));
    assertTrue(
        "A.7 user-email content matches viewer address",
        html.contains(">alice@example.com</span>"));
    // A.5 / A.6 SVG glyphs must SSR so the icons are visible
    // pre-upgrade (matches the no-flicker contract used by the help
    // modal's hidden attribute and the avatar initials parity).
    assertTrue(
        "A.5 bell button server-renders an SVG glyph (no empty pre-upgrade icon)",
        html.contains("<button type=\"button\" class=\"bell\" aria-label=\"Notifications\"><svg"));
    assertTrue(
        "A.6 mail icon server-renders an SVG glyph (no empty pre-upgrade icon)",
        html.contains("class=\"mail\" href=\"/?view=j2cl-root&amp;q=in%3Ainbox\" aria-label=\"Inbox\"><svg"));
  }

  // ---------- B.* search rail ----------

  /** B.1 wrapper — search-rail host element with the default query. */
  @Test
  public void j2clRootShellEmitsWavySearchRailHostWithDefaultQuery() throws Exception {
    String html = renderJ2clRootShell();
    // The rail's opening tag always carries attributes (query=...,
    // data-active-folder=..., result-count=..., optionally
    // data-rail-cards-enabled=...), so we match on `<wavy-search-rail `
    // with a trailing space — that distinguishes the rail host from
    // any nested <wavy-search-rail-card> (which always has at least one
    // character after the dash before `>`).
    assertEquals(
        "Exactly one <wavy-search-rail> host is mounted in the J2CL root shell",
        1,
        countOccurrences(html, "<wavy-search-rail "));
    assertTrue(
        "Rail defaults to query=in:inbox", html.contains("query=\"in:inbox\""));
    assertTrue(
        "Rail defaults to data-active-folder=inbox",
        html.contains("data-active-folder=\"inbox\""));
  }

  /**
   * J-UI-1 (#1079): when the {@code j2cl-search-rail-cards} flag is OFF
   * (default for prod) the rail SSR must NOT carry
   * {@code data-rail-cards-enabled="true"}. The legacy plain-DOM digest
   * path stays in place for OFF viewers.
   */
  @Test
  public void j2clRootShellOmitsRailCardsAttributeWhenFlagOff() throws Exception {
    String html = renderJ2clRootShell();
    assertFalse(
        "Default flag-OFF render must not advertise rail-cards-enabled",
        html.contains("data-rail-cards-enabled=\"true\""));
  }

  /**
   * J-UI-1 (#1079): when the SSR is invoked with the rail-cards path
   * enabled the rail must carry {@code data-rail-cards-enabled="true"}.
   * The view layer reads this attribute on construction to decide which
   * rendering path to take.
   */
  @Test
  public void j2clRootShellEmitsRailCardsAttributeWhenFlagOn() throws Exception {
    String html = renderJ2clRootShellWithRailCards();
    assertTrue(
        "Flag-ON render must advertise rail-cards-enabled on the rail host",
        html.contains("data-rail-cards-enabled=\"true\""));
    // The chrome (folders, filter strip, result-count) stays the same on
    // the SSR side — only the attribute changes.
    assertTrue(
        "Flag-ON render still emits the saved-search folders",
        html.contains("data-folder-id=\"inbox\""));
  }

  /**
   * J-UI-1 (#1079) follow-up: the flag value is also emitted on
   * {@code <shell-root>} as {@code data-j2cl-search-rail-cards="true"}
   * so the J2CL view layer can resolve it independently of the rail.
   * If the rail is missing post-upgrade, the view raises a status
   * error rather than silently falling back to the legacy digest
   * list. Sister assertion to the rail-attribute test above.
   */
  @Test
  public void j2clRootShellEmitsShellRootRailCardsMarkerWhenFlagOn() throws Exception {
    String html = renderJ2clRootShellWithRailCards();
    assertTrue(
        "Flag-ON render must advertise data-j2cl-search-rail-cards on <shell-root>",
        html.contains("data-j2cl-search-rail-cards=\"true\""));
  }

  @Test
  public void j2clRootShellOmitsShellRootRailCardsMarkerWhenFlagOff() throws Exception {
    String html = renderJ2clRootShell();
    assertFalse(
        "Default flag-OFF render must not advertise data-j2cl-search-rail-cards on <shell-root>",
        html.contains("data-j2cl-search-rail-cards=\"true\""));
  }

  /**
   * J-UI-5 (#1083): the SSR must advertise the inline rich-composer
   * flag value on `<shell-root>` so `J2clComposeSurfaceView` can mount
   * the contenteditable composer at the chosen blip. Sister assertion
   * to the rail-cards flag tests above.
   */
  @Test
  public void j2clRootShellEmitsInlineRichComposerMarkerWhenFlagOn() throws Exception {
    String html = renderJ2clRootShellWithInlineRichComposer();
    assertTrue(
        "Flag-ON render must advertise data-j2cl-inline-rich-composer on <shell-root>",
        html.contains("data-j2cl-inline-rich-composer=\"true\""));
  }

  @Test
  public void j2clRootShellOmitsInlineRichComposerMarkerWhenFlagOff() throws Exception {
    String html = renderJ2clRootShell();
    assertFalse(
        "Default flag-OFF render must not advertise data-j2cl-inline-rich-composer on <shell-root>",
        html.contains("data-j2cl-inline-rich-composer=\"true\""));
  }

  /**
   * B.5–B.10 — six saved-search folders with the canonical query
   * strings AND the canonical visible labels. Each folder carries a
   * {@code data-folder-id} so the client-side rail can route clicks
   * back to the F-1 search sidecar without re-parsing the query.
   */
  @Test
  public void wavySearchRailInnerLightDomEmitsAllSixSavedSearchFolders() throws Exception {
    String html = renderJ2clRootShell();
    String[][] folders = {
      {"inbox", "in:inbox", "Inbox"},
      {"mentions", "mentions:me", "Mentions"},
      {"tasks", "tasks:me", "Tasks"},
      {"public", "with:@", "Public"},
      {"archive", "in:archive", "Archive"},
      {"pinned", "in:pinned", "Pinned"}
    };
    for (String[] folder : folders) {
      String id = folder[0];
      String query = folder[1];
      String label = folder[2];
      assertTrue(
          "Folder " + id + " carries data-folder-id=\"" + id + "\"",
          html.contains("data-folder-id=\"" + id + "\""));
      assertTrue(
          "Folder " + id + " carries data-query=\"" + query + "\"",
          html.contains("data-query=\"" + query + "\""));
      assertTrue("Folder " + id + " label \"" + label + "\"", html.contains(">" + label + "<"));
    }
    // Inbox is selected by default; the others are aria-current="false".
    assertTrue(
        "Default Inbox folder is aria-current=\"page\"",
        html.contains("data-folder-id=\"inbox\" data-query=\"in:inbox\" aria-current=\"page\""));
  }

  /** B.2/B.3/B.4/B.11/B.12 — the rail header controls + result-count slot. */
  @Test
  public void wavySearchRailInnerLightDomEmitsHelpTriggerNewWaveManageRefreshAndCountSlots()
      throws Exception {
    String html = renderJ2clRootShell();
    assertTrue("B.2 help-trigger class", html.contains("class=\"help-trigger\""));
    assertTrue(
        "B.2 help-trigger advertises dialog-popup semantics",
        html.contains("aria-haspopup=\"dialog\""));
    assertTrue(
        "B.2 help-trigger references the modal id",
        html.contains("aria-controls=\"wavy-search-help\""));
    assertTrue("B.3 New Wave button class", html.contains("class=\"new-wave\""));
    assertTrue(
        "B.3 New Wave button carries the keyboard-shortcut metadata "
            + "(global listener intentionally deferred to S6)",
        html.contains("aria-keyshortcuts=\"Shift+Meta+O Shift+Control+O\""));
    assertTrue("B.4 Manage saved searches class", html.contains("class=\"manage-saved\""));
    // G-PORT-2 (#1111): Refresh moved into the panel-level action row
    // alongside Sort and Filter — tagged with `data-digest-action`
    // for the cross-view parity selector.
    assertTrue(
        "B.11 Refresh button is tagged data-digest-action=\"refresh\"",
        html.contains("data-digest-action=\"refresh\""));
    assertTrue(
        "B.11 Refresh button has descriptive aria-label",
        html.contains("aria-label=\"Refresh search results\""));
    assertTrue(
        "G-PORT-2 (#1111) Sort button is tagged data-digest-action=\"sort\"",
        html.contains("data-digest-action=\"sort\""));
    assertTrue(
        "G-PORT-2 (#1111) Filter button is tagged data-digest-action=\"filter\"",
        html.contains("data-digest-action=\"filter\""));
    assertTrue(
        "G-PORT-2 (#1111) action row mounts with data-digest-action-row",
        html.contains("data-digest-action-row"));
    assertTrue(
        "B.12 result-count <p> with aria-live=\"polite\"",
        html.contains("class=\"result-count\" aria-live=\"polite\""));
    assertTrue(
        "Saved-searches list announces its name to screen readers via "
            + "aria-labelledby pointing to the folders header h2",
        html.contains("<h2 id=\"folders-title\">Saved searches</h2>"));
    assertTrue(
        "Saved-searches list aria-labelledby relationship intact",
        html.contains("<ul class=\"folders\" aria-labelledby=\"folders-title\""));
  }

  /**
   * F-4 (#1039 / R-4.7) — the rail filter chip strip must SSR alongside
   * the saved-searches list so the strip exists pre-upgrade and the
   * J2CL upgrade is content-preserving (no flash where the strip
   * appears only after the Lit element registers). Mirrors
   * {@code WavySearchRail.FILTERS}: three chips (unread / attachments /
   * from-me) inside a {@code <details data-j2cl-filter-strip>} block.
   */
  @Test
  public void wavySearchRailInnerLightDomEmitsFilterChipStrip() throws Exception {
    String html = renderJ2clRootShell();
    assertTrue(
        "Filter strip mounts as a <details> element",
        html.contains("<details"));
    assertTrue(
        "Filter strip carries class=\"filters\"",
        html.contains("class=\"filters\""));
    assertTrue(
        "Filter strip carries id=\"wavy-search-filter-strip\"",
        html.contains("id=\"wavy-search-filter-strip\""));
    assertTrue(
        "Filter strip carries data-j2cl-filter-strip attribute",
        html.contains("data-j2cl-filter-strip"));
    assertTrue(
        "Filter strip carries a 'Filters' summary",
        html.contains("<summary>Filters</summary>"));
    assertTrue(
        "Filter chips wrap in a role=\"group\" labelled \"Search filters\"",
        html.contains("class=\"filter-chips\" role=\"group\" aria-label=\"Search filters\""));
    String[][] chips = {
      {"unread", "is:unread", "Unread only"},
      {"attachments", "has:attachment", "With attachments"},
      {"from-me", "from:me", "From me"}
    };
    for (String[] chip : chips) {
      String id = chip[0];
      String token = chip[1];
      String label = chip[2];
      assertTrue(
          "Filter chip " + id + " carries data-filter-id=\"" + id + "\"",
          html.contains("data-filter-id=\"" + id + "\""));
      assertTrue(
          "Filter chip " + id + " carries data-filter-token=\"" + token + "\"",
          html.contains("data-filter-token=\"" + token + "\""));
      assertTrue(
          "Filter chip " + id + " label \"" + label + "\"",
          html.contains(">" + label + "</button>"));
    }
    // Default query (in:inbox) does not contain any of the three filter
    // tokens — every chip must SSR with aria-pressed=\"false\" so the
    // pre-upgrade state matches the Lit render's first paint.
    assertTrue(
        "Default state renders the unread chip with aria-pressed=\"false\"",
        html.contains(
            "data-filter-id=\"unread\" data-filter-token=\"is:unread\" aria-pressed=\"false\""));
    assertTrue(
        "Default state renders the attachments chip with aria-pressed=\"false\"",
        html.contains(
            "data-filter-id=\"attachments\" data-filter-token=\"has:attachment\" "
                + "aria-pressed=\"false\""));
    assertTrue(
        "Default state renders the from-me chip with aria-pressed=\"false\"",
        html.contains(
            "data-filter-id=\"from-me\" data-filter-token=\"from:me\" aria-pressed=\"false\""));
  }

  // ---------- C.* search-help modal ----------

  /**
   * C.1–C.22 — the SSR'd modal body MUST contain every one of the
   * 22 token literals advertised in the GWT search-help panel today.
   * Sister fixture {@code J2clSearchHelpTokenParseTest} (under
   * {@code wave/src/test/...}) asserts the parser also accepts each;
   * the two together pin the contract that the modal cannot drift
   * away from the parser.
   */
  @Test
  public void j2clRootShellEmitsWavySearchHelpModalWithAll22TokenLiterals() throws Exception {
    String html = renderJ2clRootShell();
    assertEquals(
        "Exactly one <wavy-search-help> host (singleton) is mounted",
        1,
        countOccurrences(html, "<wavy-search-help"));
    assertTrue(
        "Modal carries the singleton id the rail's help-trigger references",
        html.contains("id=\"wavy-search-help\""));
    assertTrue(
        "Modal SSR carries the `hidden` attribute so the SSR'd light-DOM body "
            + "does not flash before the J2CL bundle upgrades the element "
            + "(connectedCallback drops `hidden` on upgrade so the open/close "
            + "flow takes over).",
        html.contains("<wavy-search-help id=\"wavy-search-help\" hidden"));
    assertFalse(
        "Modal renders closed by default (no open attribute on initial mount)",
        html.contains("<wavy-search-help id=\"wavy-search-help\" open"));
    String[] filterTokens = {
        // C.1–C.4
        ">in:inbox<", ">in:archive<", ">in:all<", ">in:pinned<",
        // C.5–C.8
        ">with:user@domain<", ">with:@<", ">creator:user@domain<", ">tag:name<",
        // C.9–C.11
        ">unread:true<", ">title:text<", ">content:text<",
        // C.12
        ">mentions:me<",
        // C.13–C.15
        ">tasks:all<", ">tasks:me<", ">tasks:user@domain<"
    };
    for (String token : filterTokens) {
      assertTrue(
          "Search-help modal must advertise filter token " + token,
          html.contains(token));
    }
    // C.16 free-text marker.
    assertTrue("C.16 free-text row marker", html.contains("free text"));
    // C.17–C.20 sort tokens.
    String[] sortTokens = {
        ">orderby:datedesc<", ">orderby:dateasc<",
        ">orderby:createddesc<", ">orderby:createdasc<",
        ">orderby:creatordesc<", ">orderby:creatorasc<"
    };
    for (String token : sortTokens) {
      assertTrue(
          "Search-help modal must advertise sort token " + token, html.contains(token));
    }
    // C.21 combinations — the seven canonical example chips.
    String[] combinations = {
        ">in:inbox tag:important<",
        ">in:all orderby:createdasc<",
        ">with:alice@example.com tag:project<",
        ">in:pinned orderby:creatordesc<",
        ">creator:bob in:archive<",
        ">mentions:me unread:true<",
        ">tasks:all unread:true<"
    };
    for (String chip : combinations) {
      assertTrue(
          "Search-help modal must advertise combination chip " + chip,
          html.contains(chip));
    }
    // C.22 dismiss.
    assertTrue("C.22 \"Got it\" dismiss text", html.contains(">Got it<"));
  }

  // ---------- Negative guards ----------

  /**
   * The legacy GWT path must not load any of the F-2.S3 elements so a
   * rollback to {@code ?view=gwt} stays safe. The legacy GWT search
   * panel (g:HTMLPanel + SearchWidget.ui.xml) is unchanged.
   */
  @Test
  public void legacyGwtRouteDoesNotLeakF2S3Markers() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);
    String html = invokeServlet(servlet, "gwt", WAVE_ID.serialise());

    assertFalse(
        "GWT path must not emit <wavy-header> hosts",
        html.contains("<wavy-header"));
    assertFalse(
        "GWT path must not emit <wavy-search-rail> hosts",
        html.contains("<wavy-search-rail"));
    assertFalse(
        "GWT path must not emit <wavy-search-help> hosts",
        html.contains("<wavy-search-help"));
    assertFalse(
        "GWT path must not emit the brand-dot accent",
        html.contains("class=\"brand-dot\""));
  }

  /**
   * The signed-out J2CL root shell must NOT mount the search rail,
   * help modal, or header chrome — those are signed-in-only
   * affordances. Defends against accidentally regressing the
   * unauthenticated landing into emitting search/help to anonymous
   * users.
   */
  @Test
  public void signedOutJ2clRootShellDoesNotMountSearchRailOrHeaderChrome() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(/* user= */ null, renderer);
    String html = invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());

    assertFalse(
        "Signed-out branch must not emit <wavy-header>", html.contains("<wavy-header"));
    assertFalse(
        "Signed-out branch must not emit <wavy-search-rail>",
        html.contains("<wavy-search-rail"));
    assertFalse(
        "Signed-out branch must not emit <wavy-search-help>",
        html.contains("<wavy-search-help"));
    assertTrue(
        "Signed-out branch keeps its existing shell-root-signed-out element",
        html.contains("<shell-root-signed-out"));
  }

  // --- helpers (mirror of the F-1 / F-2.S1 fixtures) ---

  private static String renderJ2clRootShell() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServlet(VIEWER, renderer);
    return invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());
  }

  /**
   * J-UI-1 (#1079): renders the same J2CL root shell with the
   * {@code j2cl-search-rail-cards} flag enabled globally so the SSR
   * emits {@code data-rail-cards-enabled="true"} on the rail.
   */
  private static String renderJ2clRootShellWithRailCards() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServletWithRailCardsFlag(VIEWER, renderer);
    return invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());
  }

  private static WaveClientServlet createServletWithRailCardsFlag(
      ParticipantId user, J2clSelectedWaveSnapshotRenderer snapshotRenderer) throws Exception {
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
        new FeatureFlagService(railCardsFeatureFlagStore()));
  }

  private static FeatureFlagStore railCardsFeatureFlagStore() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(java.util.List.of(
        new FeatureFlagStore.FeatureFlag(
            "j2cl-search-rail-cards",
            "Render J2CL search digests as <wavy-search-rail-card> elements",
            true,
            Collections.emptyMap())));
    return store;
  }

  /**
   * J-UI-5 (#1083): renders the J2CL root shell with the
   * `j2cl-inline-rich-composer` flag enabled globally so the SSR
   * emits `data-j2cl-inline-rich-composer="true"` on `<shell-root>`.
   * Mirrors `renderJ2clRootShellWithRailCards` but flips the
   * inline-composer flag instead.
   */
  private static String renderJ2clRootShellWithInlineRichComposer() throws Exception {
    WaveletProvider provider = providerForWave(buildWaveletData(6));
    J2clSelectedWaveSnapshotRenderer renderer = new J2clSelectedWaveSnapshotRenderer(provider);
    WaveClientServlet servlet = createServletWithInlineRichComposerFlag(VIEWER, renderer);
    return invokeServlet(servlet, "j2cl-root", WAVE_ID.serialise());
  }

  private static WaveClientServlet createServletWithInlineRichComposerFlag(
      ParticipantId user, J2clSelectedWaveSnapshotRenderer snapshotRenderer) throws Exception {
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
        new FeatureFlagService(inlineRichComposerFeatureFlagStore()));
  }

  private static FeatureFlagStore inlineRichComposerFeatureFlagStore() throws Exception {
    FeatureFlagStore store = mock(FeatureFlagStore.class);
    when(store.getAll()).thenReturn(java.util.List.of(
        new FeatureFlagStore.FeatureFlag(
            "j2cl-inline-rich-composer",
            "Open a contenteditable wavy-composer with a selection-driven format toolbar",
            true,
            Collections.emptyMap())));
    return store;
  }

  private static List<ObservableWaveletData> buildWaveletData(int blipCount) {
    TestingWaveletData data = new TestingWaveletData(WAVE_ID, CONV_ROOT, AUTHOR, true);
    for (int i = 0; i < blipCount; i++) {
      data.appendBlipWithText("Body " + i);
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
      ParticipantId user, J2clSelectedWaveSnapshotRenderer snapshotRenderer) throws Exception {
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
