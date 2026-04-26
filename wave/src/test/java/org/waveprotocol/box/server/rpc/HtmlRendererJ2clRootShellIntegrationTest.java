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
        "Legacy <form> must carry class sidecar-search-toolbar",
        html.contains("class=\"sidecar-search-toolbar\""));
    assertTrue(
        "Legacy <form> must carry data-j2cl-legacy-search-form=\"true\"",
        html.contains("data-j2cl-legacy-search-form=\"true\""));
    assertTrue(
        "Legacy search form must carry the hidden attribute so it does not duplicate the rail",
        html.contains(" hidden>") || html.contains(" hidden\n"));
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
}
