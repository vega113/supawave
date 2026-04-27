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
}
