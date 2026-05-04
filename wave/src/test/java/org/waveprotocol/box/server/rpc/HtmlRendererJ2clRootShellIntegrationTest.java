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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import junit.framework.TestCase;
import org.json.JSONObject;
import org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRenderer;

/**
 * F-2 slice 5 (#1055) integration test. Asserts on the no-duplicate
 * contract that the full F-2 chrome bundle establishes:
 *
 * <ul>
 *   <li>exactly one {@code <wavy-search-rail>} (in nav slot),
 *   <li>exactly one {@code <wavy-wave-nav-row>},
 *   <li>exactly one {@code <wavy-depth-nav-bar>},
 *   <li>no legacy "Hosted workflow" intro card content (eyebrow,
 *       h1.sidecar-title, sidecar-detail copy),
 *   <li>the legacy search-card wrapper carries
 *       {@code data-j2cl-legacy-search-card="hidden"} and the form is
 *       marked {@code data-j2cl-legacy-search-form="true"} + hidden,
 *   <li>the search-rail SVG glyph carries explicit width/height attrs,
 *   <li>the wavy empty-state recipe markup is present, and
 *   <li>no orphaned empty-state placeholder text appears alongside the
 *       wavy chrome.
 * </ul>
 */
public final class HtmlRendererJ2clRootShellIntegrationTest extends TestCase {

  private static String renderSignedInPage() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");
    session.put("role", "user");
    return HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443");
  }

  private static String renderSignedInPageWithDebugOverlay(boolean overlayOn) {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");
    session.put("role", "user");
    return HtmlRenderer.renderJ2clRootShellPage(
        session,
        "",
        "commit",
        0L,
        "rel",
        "/",
        "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(),
        false,
        null,
        false,
        false,
        overlayOn);
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while (true) {
      int next = haystack.indexOf(needle, idx);
      if (next < 0) {
        break;
      }
      count++;
      idx = next + needle.length();
    }
    return count;
  }

  // ---------------------------------------------------------------------
  // No-duplicate assertions
  // ---------------------------------------------------------------------

  public void testExactlyOneWavySearchRail() {
    String html = renderSignedInPage();
    assertEquals(
        "Signed-in root shell must mount exactly one <wavy-search-rail>",
        1,
        countOccurrences(html, "<wavy-search-rail"));
  }

  public void testExactlyOneWavyWaveNavRow() {
    String html = renderSignedInPage();
    assertEquals(
        "Signed-in root shell must mount exactly one <wavy-wave-nav-row>",
        1,
        countOccurrences(html, "<wavy-wave-nav-row"));
  }

  public void testExactlyOneWavyDepthNavBar() {
    String html = renderSignedInPage();
    assertEquals(
        "Signed-in root shell must mount exactly one <wavy-depth-nav-bar>",
        1,
        countOccurrences(html, "<wavy-depth-nav-bar"));
  }

  // ---------------------------------------------------------------------
  // Legacy "Hosted workflow" intro card removed
  // ---------------------------------------------------------------------

  public void testLegacyHostedWorkflowTitleIsAbsent() {
    String html = renderSignedInPage();
    assertFalse(
        "The legacy 'Hosted workflow' h1 must not render alongside the wavy chrome",
        html.contains("Hosted workflow"));
    assertFalse(
        "The legacy <h1 class=\"sidecar-title\"> must not render — chrome is the surface",
        html.contains("class=\"sidecar-title\""));
  }

  public void testLegacyEyebrowIsAbsent() {
    String html = renderSignedInPage();
    assertFalse(
        "The legacy 'J2CL root shell' eyebrow must not render with the wavy chrome",
        html.contains("<p class=\"sidecar-eyebrow\">J2CL root shell</p>"));
  }

  public void testLegacyDetailCopyIsAbsent() {
    String html = renderSignedInPage();
    assertFalse(
        "The legacy <p class=\"sidecar-detail\"> body must not render alongside the wavy chrome",
        html.contains("<p class=\"sidecar-detail\">"));
  }

  // ---------------------------------------------------------------------
  // Legacy search-card wrapper retained (J2clSearchPanelView adoption),
  // but flagged + hidden
  // ---------------------------------------------------------------------

  public void testLegacySearchCardWrapperIsFlaggedHidden() {
    String html = renderSignedInPage();
    assertTrue(
        "Legacy <div class=\"sidecar-search-card\"> must carry data-j2cl-legacy-search-card",
        html.contains("data-j2cl-legacy-search-card=\"hidden\""));
  }

  public void testLegacySearchFormIsFlaggedHidden() {
    String html = renderSignedInPage();
    assertTrue(
        "Legacy <form class=\"sidecar-search-toolbar\"> must carry data-j2cl-legacy-search-form",
        html.contains("data-j2cl-legacy-search-form=\"true\""));
    assertTrue(
        "Legacy search form must carry the hidden attribute so it does not duplicate the rail",
        html.contains(
            "<form class=\"sidecar-search-toolbar\" data-j2cl-legacy-search-form=\"true\""
                + " action=\"#\" onsubmit=\"return false;\" hidden>"));
  }

  // ---------------------------------------------------------------------
  // Search-rail waveform glyph sizing fix
  // ---------------------------------------------------------------------

  public void testSearchRailWaveformSvgPinsExplicitDimensions() {
    String html = renderSignedInPage();
    assertTrue(
        "Search-rail SVG must carry explicit width=\"14\" so it does not overflow before"
            + " the Lit upgrade attaches the shadow DOM",
        html.contains(
            "<svg viewBox=\"0 0 14 14\" width=\"14\" height=\"14\" fill=\"none\""));
  }

  // ---------------------------------------------------------------------
  // Wavy empty-state recipe is present
  // ---------------------------------------------------------------------

  public void testWavyEmptyStateRecipeIsMounted() {
    String html = renderSignedInPage();
    assertTrue(
        "Empty-state slot must mount the wavy empty-state recipe",
        html.contains("data-j2cl-empty-state-recipe=\"true\""));
    assertTrue(
        "Wavy empty-state must include the ghost waveform mark",
        html.contains("class=\"wavy-empty-state-mark\""));
    assertTrue(
        "Wavy empty-state must include the headline",
        html.contains("class=\"wavy-empty-state-headline\""));
    assertTrue(
        "Wavy empty-state must include the subhead",
        html.contains("class=\"wavy-empty-state-subhead\""));
  }

  // ---------------------------------------------------------------------
  // Awareness pill is present (default hidden)
  // ---------------------------------------------------------------------

  public void testAwarenessPillIsMountedHidden() {
    String html = renderSignedInPage();
    assertTrue(
        "Selected-wave card must mount the awareness pill landmark",
        html.contains("data-j2cl-awareness-pill=\"true\""));
    assertTrue(
        "Awareness pill must default to hidden so the cyan ring does not flash on cold mount",
        html.contains(
            "<output class=\"wavy-awareness-pill\""
                + " data-j2cl-awareness-pill=\"true\" hidden></output>"));
  }

  // ---------------------------------------------------------------------
  // Compose host carries the F-2 slice 5 marker so the toolbar can be
  // co-located without confusing automation
  // ---------------------------------------------------------------------

  public void testComposeHostCarriesSliceFiveMarker() {
    String html = renderSignedInPage();
    assertTrue(
        "Compose host must carry data-j2cl-compose-host so view-wiring can target it",
        html.contains("data-j2cl-compose-host=\"true\""));
  }

  public void testComposeHostStartsEmptyPreEdit() {
    String html = renderSignedInPage();
    // The compose host is a div that the controller fills with the editor
    // toolbar wall during an active edit session. Pre-edit, it must render
    // empty so the wavy-thread-collapse.css `:empty { display: none }` rule
    // collapses it without a layout gap.
    assertTrue(
        "Compose host must SSR as a self-empty <div ...></div> so the :empty CSS rule applies",
        html.contains(
            "<div class=\"sidecar-selected-compose\" data-j2cl-compose-host=\"true\"></div>"));
  }

  public void testRootShellCreateSurfaceMountsInVisibleSelectedWaveHost() {
    String source =
        readSourceFile(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/root/"
                + "J2clRootShellController.java");
    assertTrue(
        "Root-shell New Wave must mount the create surface in the visible "
            + "selected-wave compose host, not the hidden legacy search card.",
        source.contains(
            "new J2clComposeSurfaceView(selectedCreateHost, selectedReplyHost)"));
    assertTrue(
        "Root-shell New Wave must use a dedicated visible create child host so "
            + "J2clComposeSurfaceView does not clear the reply/toolbar hosts.",
        source.contains(
            "createSiblingHostBefore(selectedWaveComposeHost, \"j2cl-root-create-host\")"));
    assertFalse(
        "Root-shell New Wave must not create the dedicated create host inside "
            + "sidecar-selected-compose because that breaks the :empty collapse sentinel.",
        source.contains(
            "createChildHost(selectedWaveComposeHost, \"j2cl-root-create-host\")"));
    assertFalse(
        "Root-shell New Wave must not bind createHost to searchView.getComposeHost(), "
            + "because the root shell hides the legacy search card.",
        source.contains(
            "new J2clComposeSurfaceView(searchView.getComposeHost(), selectedReplyHost)"));
  }

  // ---------------------------------------------------------------------
  // F-2 slice 6 (#1058, Part A) — visible-rail regression lock.
  //
  // Slice 5 (#1057) marked the legacy search card with
  // data-j2cl-legacy-search-card="hidden" but the matching CSS rule
  // used `display: contents`, which removes the wrapper's box but
  // keeps every child visible. The `.sidecar-digests` adoption target
  // and `.sidecar-empty-state` paragraph painted as a duplicate light
  // surface below the dark wavy rail.
  //
  // These assertions regression-lock both sides of the fix:
  //   1. the SSR markup still carries the legacy-card marker so the
  //      adoption path resolves;
  //   2. the actual CSS rule uses `display: none !important`, NOT
  //      `display: contents` — so the wrapper + every legacy-styled
  //      child is removed from layout.
  // ---------------------------------------------------------------------

  /**
   * Loads the wavy-thread-collapse stylesheet so we can assert against
   * the actual CSS rule shape. The file is shipped under
   * {@code j2cl/lit/src/design/wavy-thread-collapse.css}; the rendered
   * HTML links to {@code j2cl/assets/wavy-thread-collapse.css} (the
   * built copy). We intentionally read the source-of-truth file so a
   * regression in the design source is caught even if the build copy
   * is stale.
   */
  private static String readWavyThreadCollapseCss() {
    Path candidate = Paths.get("j2cl/lit/src/design/wavy-thread-collapse.css");
    if (!Files.isRegularFile(candidate)) {
      candidate = Paths.get("../j2cl/lit/src/design/wavy-thread-collapse.css");
    }
    if (!Files.isRegularFile(candidate)) {
      // Fallback to classpath lookup if the build copy is on the test classpath.
      try (InputStream in =
          HtmlRendererJ2clRootShellIntegrationTest.class
              .getResourceAsStream("/wavy-thread-collapse.css")) {
        if (in != null) {
          return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
      } catch (IOException ignored) {
        // fall through
      }
      throw new AssertionError(
          "wavy-thread-collapse.css not found — run from the repo root or stage the asset");
    }
    try {
      return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError("Could not read " + candidate + ": " + e.getMessage(), e);
    }
  }

  public void testLegacySearchCardCssRuleHidesItVisibly() {
    String css = readWavyThreadCollapseCss();
    // The fix: the rule MUST use `display: none !important` so the
    // wrapper + every adoption-target child is removed from layout.
    assertTrue(
        "wavy-thread-collapse.css must contain the legacy-card hide rule with display: none !important",
        java.util.regex.Pattern.compile(
                "\\.sidecar-search-card\\[data-j2cl-legacy-search-card=\"hidden\"\\]"
                    + "\\s*\\{\\s*display\\s*:\\s*none\\s*!important\\s*;\\s*\\}")
            .matcher(css)
            .find());
    // Regression guard: the buggy `display: contents` shape (S5)
    // must NOT appear on this selector.
    int markerIdx = css.indexOf("[data-j2cl-legacy-search-card=\"hidden\"]");
    while (markerIdx >= 0) {
      int blockStart = css.indexOf('{', markerIdx);
      int blockEnd = css.indexOf('}', blockStart);
      assertTrue(
          "Block for [data-j2cl-legacy-search-card=\"hidden\"] selector must close",
          blockStart > 0 && blockEnd > blockStart);
      String block = css.substring(blockStart, blockEnd);
      assertFalse(
          "Legacy-card hide rule must not use `display: contents` — that keeps children visible",
          block.contains("display: contents"));
      markerIdx = css.indexOf("[data-j2cl-legacy-search-card=\"hidden\"]", blockEnd);
    }
  }

  public void testLegacyComposeHostHasEmptyCollapseRule() {
    String css = readWavyThreadCollapseCss();
    assertTrue(
        "wavy-thread-collapse.css must collapse the empty compose host to avoid a layout gap pre-edit",
        css.contains(
            ".sidecar-selected-compose[data-j2cl-compose-host=\"true\"]:empty"));
  }

  public void testLegacySearchFormHideRulePresent() {
    String css = readWavyThreadCollapseCss();
    assertTrue(
        "Legacy search form must carry an explicit display:none rule",
        css.contains("[data-j2cl-legacy-search-form=\"true\"]"));
  }

  public void testWavyThreadCollapseStylesheetIsLinkedFromRootShell() {
    String html = renderSignedInPage();
    assertTrue(
        "Root shell page must <link> wavy-thread-collapse.css so the legacy-card hide rule applies",
        html.contains("j2cl/assets/wavy-thread-collapse.css"));
  }

  // ---------------------------------------------------------------------
  // F-2 slice 6 (#1058, Part B) — demo route smoke (server-side).
  //
  // The demo route at ?view=j2cl-root&q=read-surface-preview is a
  // server-rendered fixture that exercises the full F-2 chrome surface
  // for design + reviewer walkthrough. The route returns HTML similar
  // to the regular signed-in shell but with a fixture wave pre-mounted.
  // ---------------------------------------------------------------------

  public void testReadSurfacePreviewPageRenders() {
    String html =
        HtmlRenderer.renderJ2clReadSurfacePreviewPage(
            "/", "commit", 0L, "rel", "alice@example.com", "ws.example:443");
    assertTrue(
        "Read-surface preview must render the wavy-search-rail in the nav slot",
        html.contains("<wavy-search-rail"));
    assertTrue(
        "Read-surface preview must mount the depth-nav crumb",
        html.contains("<wavy-depth-nav-bar"));
    assertTrue(
        "Read-surface preview must mount the wave-nav-row chrome",
        html.contains("<wavy-wave-nav-row"));
    assertTrue(
        "Read-surface preview must mount the version-history overlay open",
        html.contains("<wavy-version-history"));
    assertTrue(
        "Read-surface preview must mount the profile overlay open",
        html.contains("<wavy-profile-overlay"));
    assertTrue(
        "Read-surface preview must mount the awareness pill",
        html.contains("data-j2cl-awareness-pill=\"true\""));
    assertTrue(
        "Read-surface preview must include the fixture wave title in the selected card",
        html.contains("Sample read-surface preview wave"));
    assertTrue(
        "Read-surface preview must render at least one wave-blip element with the fixture content",
        html.contains("<wave-blip"));
    assertTrue(
        "Read-surface preview must label itself as a preview route for reviewers",
        html.contains("data-j2cl-read-surface-preview=\"true\""));
  }

  public void testReadSurfacePreviewDoesNotShowLegacySearchCardLight() {
    String html =
        HtmlRenderer.renderJ2clReadSurfacePreviewPage(
            "/", "commit", 0L, "rel", "alice@example.com", "ws.example:443");
    // Same no-duplicate contract as the regular root shell.
    assertTrue(
        "Preview route must hide the legacy search card via the same data-marker as the root shell",
        html.contains("data-j2cl-legacy-search-card=\"hidden\""));
  }

  // ---------------------------------------------------------------------
  // F-2 follow-up (#1060) — visible-regression locks for the three gaps
  // that survived the F-2 closeout (PR #1059, sha dc8ee6a3):
  //
  //   1. <wavy-search-rail> shadow DOM exposed a default <slot></slot>,
  //      which projected the SSR'd light-DOM rail under the rendered
  //      shadow chrome. Live readers saw the rail twice.
  //   2. The J2CL read renderer fell back to "Blip <id>" in the
  //      posted-at slot when a blip had no real modified time, painting
  //      as the entire header text on the live read surface.
  //   3. The J2CL toolbar surface controller emitted the full edit-
  //      formatting toolbar (Bold/Italic/.../attachment buttons) on
  //      every render, causing a permanent editor-toolbar wall on the
  //      right of the read surface even when no compose was active.
  //
  // These assertions are static-source pins so the regression cannot
  // ship by going around the Lit-side jsdom fixture in
  // j2cl/lit/test/visual-regression-1060.test.js.
  // ---------------------------------------------------------------------

  /** Reads a source file relative to the worktree root. */
  private static String readSourceFile(String relativePath) {
    Path candidate = Paths.get(relativePath);
    if (!Files.isRegularFile(candidate)) {
      candidate = Paths.get("../" + relativePath);
    }
    if (!Files.isRegularFile(candidate)) {
      throw new AssertionError(
          "source file not found at "
              + relativePath
              + " — run from the repo root or stage the file");
    }
    try {
      return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new AssertionError("Could not read " + candidate + ": " + e.getMessage(), e);
    }
  }

  public void testWavySearchRailHasNoDefaultSlotInShadowDom() {
    // Gap 1: a default <slot></slot> in the shadow DOM render projects
    // the SSR'd light-DOM rail under the rendered chrome and paints the
    // rail twice. Pin the static source so the regression cannot return.
    String source = readSourceFile("j2cl/lit/src/elements/wavy-search-rail.js");
    // Allow named slots (e.g. <slot name="..."></slot>) — those project
    // only explicitly-slotted children — but reject any <slot ...> that
    // lacks a name= attribute, including self-closing and attributed forms.
    assertFalse(
        "wavy-search-rail must not expose any unnamed <slot ...> "
            + "(see #1060 — projects SSR'd light DOM as a duplicate rail)",
        java.util.regex.Pattern.compile("<slot(?![^>]*\\bname\\s*=)[^>]*>")
            .matcher(source)
            .find());
  }

  public void testJ2clReadRendererDoesNotFallBackToBlipIdLabel() {
    // Gap 2: the renderer used to set posted-at="Blip <id>" when a
    // blip had no real modified time. The wave-blip element rendered
    // that text inside the visible <time> element so every metadata-
    // less blip painted as a flat card titled "Blip fN7oSXulpwB". Pin
    // the static source so the regression cannot return.
    String source =
        readSourceFile(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/read/"
                + "J2clReadSurfaceDomRenderer.java");
    assertFalse(
        "J2clReadSurfaceDomRenderer must not set posted-at to the "
            + "'Blip <id>' fallback (see #1060 — renders as the entire "
            + "header text on the live read surface)",
        source.contains(
            "element.setAttribute(\"posted-at\", blipLabel(blip.getBlipId()));"));
  }

  public void testSelectedWaveContentDoesNotOwnNestedVerticalScrollbar() {
    String css = readSourceFile("j2cl/src/main/webapp/assets/sidecar.css");
    java.util.regex.Pattern rulePattern =
        java.util.regex.Pattern.compile("[^{]*sidecar-selected-content[^{]*\\{([^}]*)\\}");
    java.util.regex.Matcher matcher = rulePattern.matcher(css);
    assertTrue("sidecar.css must define .sidecar-selected-content", matcher.find());
    boolean anyOverflowY = false;
    boolean anyMaxHeight = false;
    do {
      String block = matcher.group(1);
      if (block.contains("overflow-y")) anyOverflowY = true;
      if (block.contains("max-height")) anyMaxHeight = true;
    } while (matcher.find());
    assertFalse(
        "Selected wave content must not own an inner vertical scrollbar; the root page scrolls once.",
        anyOverflowY);
    assertFalse(
        "Selected wave content must not clamp to viewport height; that creates a second wave-panel scrollbar.",
        anyMaxHeight);
  }

  public void testReadSurfaceRendererUsesActiveScrollContainerForViewportLoading() {
    String source =
        readSourceFile(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/read/"
                + "J2clReadSurfaceDomRenderer.java");
    assertTrue(
        "Renderer must listen to page scroll events when selected wave content no longer owns a nested scrollbar.",
        source.contains("DomGlobal.window.addEventListener(\"scroll\", this::onHostScroll);"));
    assertTrue(
        "Viewport edge loading must use host-relative edge helpers, not hard-coded host.scrollTop in onHostScroll.",
        source.contains("if (isNearTopEdge())")
            && source.contains("if (isNearBottomEdge())")
            && source.contains("host.getBoundingClientRect().top >= -EDGE_SCROLL_THRESHOLD_PX")
            && source.contains("host.getBoundingClientRect().top <= EDGE_SCROLL_THRESHOLD_PX")
            && source.contains(
                "host.getBoundingClientRect().bottom - DomGlobal.window.innerHeight"));
    assertTrue(
        "Host-owned scroll path must be guarded by viewport intersection before arming edge/dwell logic.",
        source.contains("hostIntersectsViewport()"));
    assertTrue(
        "Viewport dwell/placeholder math must clip page-level hosts to the visual viewport.",
        source.contains("effectiveViewportBounds()"));
  }

  public void testJ2clToolbarOnlyEmitsEditActionsWhileEditing() {
    // Gap 3: the toolbar controller used to call addEditActions on
    // every render, regardless of editState.editable. The view's
    // host.hidden = actions.isEmpty() guard then never fired and the
    // full Bold/Italic/.../attachment toolbar painted as a permanent
    // wall on the right of the read surface. Pin the static source.
    String source =
        readSourceFile(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/toolbar/"
                + "J2clToolbarSurfaceController.java");
    // The fix: addEditActions must be inside an if (editState.editable)
    // guard. We pin the regex shape so the regression cannot return by
    // dropping the guard and re-introducing the wall.
    assertTrue(
        "J2clToolbarSurfaceController.render must gate addEditActions "
            + "on editState.editable (see #1060 — otherwise the editor-"
            + "toolbar wall paints permanently)",
        java.util.regex.Pattern.compile(
                "if\\s*\\(\\s*editState\\.editable\\s*\\)\\s*\\{\\s*"
                    + "addEditActions\\s*\\(")
            .matcher(source)
            .find());
    // Also verify there is exactly one CALL to addEditActions in the
    // file (the guarded one) — the only other occurrence should be the
    // method declaration. If the guard is dropped and a second
    // unconditional call is added, this count check fails.
    java.util.regex.Matcher callMatcher =
        java.util.regex.Pattern.compile("addEditActions\\s*\\(\\s*actions\\s*\\)\\s*;")
            .matcher(source);
    int callCount = 0;
    while (callMatcher.find()) {
      callCount++;
    }
    assertEquals(
        "J2clToolbarSurfaceController must invoke addEditActions exactly "
            + "once (inside the editState.editable guard — see #1060)",
        1,
        callCount);
  }

  public void testRootShellDoesNotTreatWriteSessionAsVisibleEditToolbar() {
    // A writable wave is not the same thing as an active editor. The root shell
    // already mounts the icon-only floating <wavy-format-toolbar> inside the
    // inline composer; feeding write-session presence into the legacy top
    // toolbar paints the old Bold/Italic/... text-button wall on every writable
    // selected wave.
    String source =
        readSourceFile(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/root/"
                + "J2clRootShellController.java");
    assertFalse(
        "J2clRootShellController must not make the legacy top toolbar editable "
            + "just because a write session exists",
        source.contains(
            "toolbarController.onEditStateChanged(editStateForWriteSession(writeSession));"));
    assertTrue(
        "editStateForWriteSession must keep the legacy top toolbar non-editing "
            + "while the inline composer owns edit chrome, but restore it for "
            + "the legacy-composer fallback",
        source.contains("writeSession != null && !inlineRichComposerEnabled"));
  }

  public void testComposerInlineReplyCollapsesUntilAvailable() {
    // Gap 3 partner: <composer-inline-reply> rendered "Reply target: ..."
    // + an empty Send-reply textarea on every selected wave even when
    // no compose was active. Pin the :host(:not([available]))
    // display:none rule in the Lit element source.
    String source =
        readSourceFile("j2cl/lit/src/elements/composer-inline-reply.js");
    assertTrue(
        "composer-inline-reply must collapse the host until the "
            + "controller flips available=true (see #1060)",
        java.util.regex.Pattern.compile(
                ":host\\(:not\\(\\[available\\]\\)\\)\\s*\\{\\s*display\\s*:\\s*none\\s*;\\s*\\}")
            .matcher(source)
            .find());
  }

  public void testInlineComposerScrollPreservationFallsBackToDocumentScroller() {
    String source =
        readSourceFile(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "Inline reply composer mounting must preserve document/body scroll too; "
            + "otherwise focusing a newly mounted composer can jump to the bottom "
            + "when no nested panel is independently scrollable",
        source.contains("DomGlobal.document.scrollingElement"));
  }

  public void testWavyComposerCollapsesUntilAvailable() {
    // F-3.S1 (#1038, R-5.2 step 8): the new <wavy-composer> Lit element
    // must preserve the F-2.S6 fix (#1060) — collapse to display:none
    // when not [available] so the wave panel does not paint a permanent
    // editor surface pre-compose. If this rule regresses, the inline
    // composer renders for every blip on every wave.
    String source =
        readSourceFile("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must collapse the host until the controller "
            + "flips available=true (R-5.2 step 8 — preserves #1060)",
        java.util.regex.Pattern.compile(
                ":host\\(:not\\(\\[available\\]\\)\\)\\s*\\{\\s*display\\s*:\\s*none\\s*;\\s*\\}")
            .matcher(source)
            .find());
  }

  public void testWavyFormatToolbarHidesItselfWithCollapsedSelection() {
    // F-3.S1 (#1038, R-5.2 steps 7 + 8): the floating <wavy-format-toolbar>
    // must hide itself when the selection is collapsed OR the composer
    // body has no selection at all. Pin the :host([hidden]) collapse rule
    // and the _reposition guard that toggles the hidden attribute.
    String source =
        readSourceFile("j2cl/lit/src/elements/wavy-format-toolbar.js");
    assertTrue(
        "wavy-format-toolbar must collapse via :host([hidden]) when the "
            + "selection is collapsed (R-5.2 step 7)",
        java.util.regex.Pattern.compile(
                ":host\\(\\[hidden\\]\\)\\s*\\{")
            .matcher(source)
            .find());
    assertTrue(
        "wavy-format-toolbar._reposition must set hidden=true on collapsed "
            + "selection (R-5.2 step 7 — preserves the F-2.S6 toolbar-wall fix)",
        java.util.regex.Pattern.compile(
                "if\\s*\\(\\s*collapsed\\s*\\|\\|\\s*!rect")
            .matcher(source)
            .find());
  }

  public void testWavyComposerPreservesCaretAcrossRenders() {
    // F-3.S1 (#1038, R-5.1 step 2): the contenteditable body must NOT be
    // re-created on every Lit render — the composer caches the body in
    // _bodyElement and only writes textContent when the body does NOT
    // own selection. Pin both contracts in the source.
    String source =
        readSourceFile("j2cl/lit/src/elements/wavy-composer.js");
    assertTrue(
        "wavy-composer must cache the body element (R-5.1 step 2 — caret "
            + "survival across renders)",
        java.util.regex.Pattern.compile(
                "if\\s*\\(\\s*!\\s*this\\._bodyElement\\s*\\)")
            .matcher(source)
            .find());
    assertTrue(
        "wavy-composer must guard textContent writes on _bodyOwnsSelection "
            + "(R-5.1 step 2 — caret survival)",
        java.util.regex.Pattern.compile(
                "!\\s*this\\._bodyOwnsSelection\\s*\\(\\s*\\)")
            .matcher(source)
            .find());
  }

  public void testWavyComposerElementsRegisteredInShellBundleEntryPoint() {
    // F-3.S1 (#1038): the lit shell bundle index registers the new
    // F-3.S1 elements alongside the legacy <composer-inline-reply>.
    // The new elements MUST be imported so the shell.js bundle defines
    // them in production.
    String source = readSourceFile("j2cl/lit/src/index.js");
    assertTrue(
        "shell bundle must import wavy-composer (F-3.S1 #1038)",
        source.contains("./elements/wavy-composer.js"));
    assertTrue(
        "shell bundle must import wavy-format-toolbar (F-3.S1 #1038)",
        source.contains("./elements/wavy-format-toolbar.js"));
    assertTrue(
        "shell bundle must import wavy-link-modal (F-3.S1 #1038)",
        source.contains("./elements/wavy-link-modal.js"));
    assertTrue(
        "shell bundle must import wavy-tags-row (F-3.S1 #1038)",
        source.contains("./elements/wavy-tags-row.js"));
    assertTrue(
        "shell bundle must import wavy-wave-root-reply-trigger (F-3.S1 #1038, J.1)",
        source.contains("./elements/wavy-wave-root-reply-trigger.js"));
  }

  // ---------------------------------------------------------------------
  // V-2 (#1100): debug overlay flag + dev-string suppression
  // ---------------------------------------------------------------------

  public void testV2DefaultBodyHasNoDebugOverlayClass() {
    String html = renderSignedInPage();
    assertTrue(
        "Default body must carry j2cl-root-shell-page",
        html.contains("<body class=\"j2cl-root-shell-page\">"));
    assertFalse(
        "Default body must not carry j2cl-debug-overlay-on (V-2 #1100)",
        html.contains("j2cl-debug-overlay-on"));
  }

  public void testV2DebugOverlayOnAddsBodyClass() {
    String html = renderSignedInPageWithDebugOverlay(true);
    assertTrue(
        "Flag-on must add j2cl-debug-overlay-on to <body> (V-2 #1100)",
        html.contains("<body class=\"j2cl-root-shell-page j2cl-debug-overlay-on\">"));
  }

  public void testV2PreviewFixtureEyebrowIsTaggedDebugOnly() {
    String html = renderSignedInPage();
    assertTrue(
        "Preview-fixture eyebrow must carry data-j2cl-debug-only so sidecar.css hides it (V-2 #1100)",
        html.contains(
            "<p class=\"sidecar-eyebrow\" data-j2cl-debug-only=\"true\">Opened wave</p>"));
  }

  public void testV2PreviewFixtureStatusAndDetailAreTaggedDebugOnly() {
    String html = renderSignedInPage();
    // status is NOT marked debug-only — error text must remain visible in non-debug mode.
    // It starts hidden; J2clSelectedWaveView.render() sets hidden=false only on error or debug-on.
    assertTrue(
        "Preview-fixture status must be hidden by default, not debug-only (V-2 #1100 P1 fix)",
        html.contains("class=\"sidecar-selected-status\" hidden"));
    assertFalse(
        "Preview-fixture status must NOT carry data-j2cl-debug-only (V-2 #1100 P1 fix)",
        html.contains("class=\"sidecar-selected-status\" data-j2cl-debug-only"));
    assertTrue(
        "Preview-fixture detail must carry data-j2cl-debug-only (V-2 #1100)",
        html.contains("class=\"sidecar-selected-detail\" data-j2cl-debug-only=\"true\""));
  }

  public void testSelectedWaveStatusAndDetailStayHiddenOutsideErrors() {
    String source =
        readSourceFile(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/search/"
                + "J2clSelectedWaveView.java");
    assertFalse(
        "Selected-wave status must not become visible just because the page "
            + "debug flag is enabled; raw live-update text is not product UI",
        source.contains("status.hidden = !model.isError() && !isDebugOverlayOn();"));
    assertTrue(
        "Selected-wave status must stay hidden unless it carries an actual error",
        source.contains("status.hidden = !model.isError();"));
    assertTrue(
        "Selected-wave detail must stay hidden unless it carries an actual error",
        source.contains("detail.hidden = !model.isError();"));
    assertFalse(
        "Selected-wave detail visibility must not depend on the page debug flag; "
            + "only real error states may expose diagnostics",
        source.contains("detail.hidden = !model.isError() && !isDebugOverlayOn();"));
    assertEquals(
        "Both live and preserved render paths must gate detail visibility on errors",
        2,
        countOccurrences(source, "detail.hidden = !model.isError();"));
    int unreadHelperStart = source.indexOf("private static String effectiveUnreadText");
    assertTrue("J2clSelectedWaveView must define effectiveUnreadText", unreadHelperStart >= 0);
    int unreadHelperEnd = source.indexOf("\n  @", unreadHelperStart);
    String unreadHelper =
        source.substring(
            unreadHelperStart, unreadHelperEnd < 0 ? source.length() : unreadHelperEnd);
    assertFalse(
        "Selected-wave read-state text must not become visible through the debug overlay; GWT shows read state through highlighting.",
        unreadHelper.contains("isDebugOverlayOn()"));
  }

  public void testV2SidecarCssCarriesDebugOnlyHideRule() {
    String css = readSourceFile("j2cl/src/main/webapp/assets/sidecar.css");
    assertTrue(
        "sidecar.css must carry the V-2 debug-only hide rule so dev strings stay hidden by default",
        css.contains("data-j2cl-debug-only")
            && css.contains("j2cl-debug-overlay-on")
            && css.contains("display: none"));
  }

  public void testServerFirstSelectedWaveWrappersFillMainColumn() {
    String css = readSourceFile("j2cl/src/main/webapp/assets/sidecar.css");
    assertTrue(
        "The server-first selected-wave host must stretch to the full shell main column",
        java.util.regex.Pattern.compile(
                "\\.sidecar-selected-host\\s*\\{[^}]*\\bwidth\\s*:\\s*100%\\s*;")
            .matcher(css)
            .find());
    assertTrue(
        "The selected-wave host must include padding in its full-width box calculation",
        java.util.regex.Pattern.compile(
                "\\.sidecar-selected-host\\s*\\{[^}]*\\bbox-sizing\\s*:\\s*border-box\\s*;")
            .matcher(css)
            .find());
    assertTrue(
        "The selected-wave card must not shrink-wrap the read surface on wide screens",
        java.util.regex.Pattern.compile(
                "\\.sidecar-selected-card\\s*\\{[^}]*\\bwidth\\s*:\\s*100%\\s*;")
            .matcher(css)
            .find());
    assertTrue(
        "The selected-wave card must include padding in its full-width box calculation",
        java.util.regex.Pattern.compile(
                "\\.sidecar-selected-card\\s*\\{[^}]*\\bbox-sizing\\s*:\\s*border-box\\s*;")
            .matcher(css)
            .find());
  }

  public void testJ2clComposeSurfaceViewListensForBlipReplyEvents() {
    // F-3.S1 (#1038, R-5.1 step 1): the Java compose view must subscribe
    // to F-2's <wave-blip> Reply / Edit CustomEvents and mount an inline
    // <wavy-composer> at the originating blip. Pin the listener wiring so
    // the contract cannot regress silently.
    String source =
        readSourceFile(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "J2clComposeSurfaceView must listen for wave-blip-reply-requested "
            + "(R-5.1 step 1 — inline composer mounts at the blip)",
        source.contains("\"wave-blip-reply-requested\""));
    assertTrue(
        "J2clComposeSurfaceView must listen for wave-blip-edit-requested "
            + "(R-5.1 step 1 — inline edit composer)",
        source.contains("\"wave-blip-edit-requested\""));
    assertTrue(
        "J2clComposeSurfaceView must listen for wave-root-reply-requested "
            + "(R-5.1 step 5 — J.1 click-here-to-reply)",
        source.contains("\"wave-root-reply-requested\""));
    assertTrue(
        "J2clComposeSurfaceView must listen for wavy-composer-cancelled "
            + "(R-5.1 step 7 — × close removes the inline composer)",
        source.contains("\"wavy-composer-cancelled\""));
    assertTrue(
        "J2clComposeSurfaceView must mount the new <wavy-composer> "
            + "element for inline composer surfaces (R-5.1 step 1)",
        source.contains("createElement(\"wavy-composer\")"));
  }

  public void testJ2clCreateSurfaceStaysHiddenUntilNewWaveRequested() {
    String source =
        readSourceFile(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "J2CL create host must start hidden so the New Wave composer does not "
            + "occupy the selected-wave panel before the user asks for it.",
        source.contains("createHost.hidden = true"));
    assertTrue(
        "J2CL New Wave focus must reveal the create host before focusing title/body.",
        source.contains("createHost.hidden = false"));
    assertTrue(
        "J2CL New Wave button path must reveal before checking title-input disabled state.",
        source.indexOf("revealCreateSurface();", source.indexOf("public void focusCreateSurface()"))
            < source.indexOf("if (!createTitleInput.disabled)", source.indexOf("public void focusCreateSurface()")));
    assertTrue(
        "J2CL New Wave shortcut path must reveal before checking body-input disabled state.",
        source.indexOf("revealCreateSurface();", source.indexOf("public void focusCreateComposer()"))
            < source.indexOf("if (!createInput.disabled)", source.indexOf("public void focusCreateComposer()")));
  }

  public void testRootShellCreateWaveUsesGwtLikeStackedComposeLayout() {
    String source =
        readSourceFile(
            "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                + "J2clComposeSurfaceView.java");
    assertTrue(
        "Root-shell create form must reuse the sidecar compose grid so "
            + "title, body, and Create wave button stack like GWT instead "
            + "of painting as one inline row",
        source.contains("j2cl-compose-create-form sidecar-compose-form"));
    assertTrue(
        "New-wave title input must carry the shared compose-input class "
            + "so it has GWT-like width, spacing, and focus chrome",
        source.contains("j2cl-compose-create-title sidecar-compose-input"));
    assertTrue(
        "New-wave body textarea must carry the shared compose-textarea "
            + "class so it fills the compose card instead of sitting inline",
        source.contains("j2cl-compose-create-body sidecar-compose-textarea"));
    assertFalse(
        "Root-shell create wave should not show implementation copy like "
            + "'self-owned wave inside the root shell' in the compose card; "
            + "GWT keeps the create surface focused on the title/body/button.",
        readSourceFile(
                "j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/"
                    + "J2clComposeSurfaceController.java")
            .contains("Create a self-owned wave inside the root shell."));

    String css = readSourceFile("j2cl/src/main/webapp/assets/sidecar.css");
    assertTrue(
        "Root-shell create form needs a bounded card width matching the "
            + "legacy GWT compose column rather than stretching controls "
            + "across the entire wave panel",
        java.util.regex.Pattern.compile(
                "\\.j2cl-compose-create-form\\s*\\{[^}]*\\bmax-width\\s*:\\s*680px\\s*;")
            .matcher(css)
            .find());
    assertTrue(
        "composer-submit-affordance inside a grid compose form must not stretch "
            + "to full row width; it needs justify-self: start to stay compact",
        java.util.regex.Pattern.compile(
                "\\.sidecar-compose-form\\s+composer-submit-affordance\\s*\\{"
                    + "[^}]*\\bjustify-self\\s*:\\s*start\\s*;")
            .matcher(css)
            .find());

    String shellSource = readSourceFile("j2cl/lit/src/elements/composer-shell.js");
    assertTrue(
        "The create shell should use the same compact, low-radius card "
            + "language as the GWT compose block rather than a large rounded panel",
        java.util.regex.Pattern.compile(
                "\\.shell\\s*\\{[^}]*\\bborder-radius\\s*:\\s*4px\\s*;")
            .matcher(shellSource)
            .find());
    assertTrue(
        "Composer-shell headings should use the same compact font size as "
            + "the GWT compose block rather than large decorative headings",
        java.util.regex.Pattern.compile(
                "h2[^{]*\\{[^}]*\\bfont-size\\s*:\\s*1\\.05rem\\s*;")
            .matcher(shellSource)
            .find());
  }
}
