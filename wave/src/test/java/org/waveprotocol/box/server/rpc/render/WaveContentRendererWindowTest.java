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

import junit.framework.TestCase;

import org.waveprotocol.box.server.robots.operations.TestingWaveletData;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

/**
 * Tests for the F-1 viewport-window contract on
 * {@link WaveContentRenderer#renderWaveContent(WaveViewData, ParticipantId, int)}.
 *
 * <p>Covers parity-matrix rows R-3.5 (visible-region container model),
 * R-6.1 (server-rendered read-only first paint with keyboard contract),
 * and R-7.1 (initial visible window).
 */
public class WaveContentRendererWindowTest extends TestCase {

  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+window");
  private static final WaveletId CONV_WAVELET_ID = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("viewer@example.com");

  private WaveViewData buildWave(int blipCount) {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    for (int i = 0; i < blipCount; i++) {
      data.appendBlipWithText("Body " + i);
    }
    return data.copyViewData();
  }

  /**
   * R-7.1: when the wave has more blips than the requested window, the
   * rendered HTML emits exactly that many root-thread blips and one
   * placeholder marker. Inline reply threads under emitted blips are still
   * rendered (the window applies only to the root-thread sequence).
   */
  public void testWindowClampsRootThreadAndAppendsPlaceholder() {
    WaveViewData wave = buildWave(12);

    String html = WaveContentRenderer.renderWaveContent(wave, VIEWER, 5);

    int blipCount = countOccurrences(html, "data-blip-id=");
    assertEquals("Expected exactly 5 blips inside the window", 5, blipCount);
    assertTrue(
        "Expected the visible-region terminal placeholder",
        html.contains("data-j2cl-server-placeholder=\"true\""));
    assertTrue(
        "Expected the AT-busy hint on the placeholder",
        html.contains("aria-busy=\"true\""));
    assertTrue(
        "Expected the wrapper to expose the server window size",
        html.contains("data-j2cl-initial-window-size=\"5\""));
    assertTrue(
        "Expected the server-first surface marker on the wave-content wrapper",
        html.contains("data-j2cl-server-first-surface=\"true\""));
  }

  /**
   * R-6.1: the static HTML must always have exactly one focusable blip
   * (the first root-thread blip carries {@code tabindex="0"}; every other
   * blip carries {@code tabindex="-1"}). This holds regardless of whether
   * the renderer is in window mode.
   */
  public void testFirstRootBlipCarriesTabindexZero() {
    WaveViewData wave = buildWave(3);

    String html = WaveContentRenderer.renderWaveContent(wave, VIEWER, 5);

    int focusableCount = countOccurrences(html, "tabindex=\"0\"");
    assertEquals("Exactly one focusable blip expected", 1, focusableCount);
    int nonFocusableCount = countOccurrences(html, "tabindex=\"-1\"");
    assertEquals("Two non-focusable blips expected", 2, nonFocusableCount);
  }

  /**
   * R-6.1: every blip carries an explicit ARIA role; root-thread blips are
   * {@code listitem} (because the root thread is a {@code role="list"}).
   */
  public void testRolesAreEmittedOnBlipsAndThreads() {
    WaveViewData wave = buildWave(2);

    String html = WaveContentRenderer.renderWaveContent(wave, VIEWER, 5);

    assertTrue(
        "Root thread should expose role=list", html.contains("role=\"list\""));
    int listitemCount = countOccurrences(html, "role=\"listitem\"");
    assertEquals("Two root-thread blips with role=listitem expected", 2, listitemCount);
  }

  /**
   * R-7.1 boundary: when the wave already fits inside the window, the
   * placeholder is suppressed but the wrapper still advertises the window
   * size. The keyboard contract still holds (one focusable blip).
   */
  public void testWindowExactlyMatchingDoesNotEmitPlaceholder() {
    WaveViewData wave = buildWave(3);

    String html = WaveContentRenderer.renderWaveContent(wave, VIEWER, 5);

    assertFalse(
        "No placeholder when conversation fits inside the window",
        html.contains("data-j2cl-server-placeholder=\"true\""));
    assertTrue(
        "Wrapper still advertises the window contract for client telemetry",
        html.contains("data-j2cl-initial-window-size=\"5\""));
  }

  /**
   * Back-compat for the legacy GWT pre-render path
   * ({@link WavePreRenderer#prerenderForUser}): the no-arg overload (and
   * an explicit {@code initialWindowSize <= 0}) must continue to render the
   * whole wave with no window markers. Critical for rollback safety.
   */
  public void testZeroWindowSizeRendersWholeWaveWithoutWindowMarkers() {
    WaveViewData wave = buildWave(8);

    String html = WaveContentRenderer.renderWaveContent(wave, VIEWER, 0);

    int blipCount = countOccurrences(html, "data-blip-id=");
    assertEquals("All blips rendered when window disabled", 8, blipCount);
    assertFalse(
        "No window marker on the legacy whole-wave path",
        html.contains("data-j2cl-initial-window-size"));
    assertFalse(
        "No server-first marker on the legacy whole-wave path",
        html.contains("data-j2cl-server-first-surface"));
    assertFalse(
        "No placeholder on the legacy whole-wave path",
        html.contains("data-j2cl-server-placeholder"));
  }

  /**
   * R-6.1: the keyboard contract must hold even on the legacy whole-wave
   * path. Without exactly one initially focusable blip the J2CL upgrade
   * cannot preserve focus during shell swap (R-6.3).
   */
  public void testWholeWavePathStillEmitsExactlyOneFocusableBlip() {
    WaveViewData wave = buildWave(3);

    String html = WaveContentRenderer.renderWaveContent(wave, VIEWER, 0);

    assertEquals(
        "Exactly one focusable blip on the whole-wave path",
        1,
        countOccurrences(html, "tabindex=\"0\""));
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
}
