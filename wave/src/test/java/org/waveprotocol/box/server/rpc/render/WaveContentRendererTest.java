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
 * Tests for {@link WaveContentRenderer}.
 *
 * <p>Verifies that the Phase 2 SSR renderer correctly produces HTML fragments
 * with title, metadata, tags, and conversation body from wave snapshots.
 */
public class WaveContentRendererTest extends TestCase {

  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+test");
  private static final WaveletId CONV_WAVELET_ID = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("viewer@example.com");

  // =========================================================================
  // Null / empty input
  // =========================================================================

  public void testRenderNullWaveViewReturnsEmptyState() {
    String html = WaveContentRenderer.renderWaveContent(null, VIEWER);
    assertNotNull(html);
    assertTrue("Expected wave-content wrapper", html.contains("class=\"wave-content\""));
    assertTrue("Expected empty-state message", html.contains("class=\"wave-empty\""));
    assertTrue("Expected message text", html.contains("No wave data available"));
  }

  public void testRenderNonConversationalWaveReturnsEmptyState() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, false);
    WaveViewData viewData = data.copyViewData();

    String html = WaveContentRenderer.renderWaveContent(viewData, VIEWER);
    assertNotNull(html);
    assertTrue("Expected wave-content wrapper", html.contains("class=\"wave-content\""));
    assertTrue("Expected empty message", html.contains("class=\"wave-empty\""));
  }

  // =========================================================================
  // Single blip rendering
  // =========================================================================

  public void testRenderSingleBlipWave() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("Hello, World!");
    WaveViewData viewData = data.copyViewData();

    String html = WaveContentRenderer.renderWaveContent(viewData, VIEWER);

    assertNotNull(html);
    // Structure
    assertTrue("Expected wave-content", html.contains("class=\"wave-content\""));
    assertTrue("Expected wave-header", html.contains("class=\"wave-header\""));
    assertTrue("Expected wave-body", html.contains("class=\"wave-body\""));
    assertTrue("Expected wave-meta", html.contains("class=\"wave-meta\""));

    // Content
    assertTrue("Expected blip text", html.contains("Hello, World!"));
    assertTrue("Expected author", html.contains("alice@example.com"));

    // Wave ID data attribute
    assertTrue("Expected wave ID", html.contains("data-wave-id=\""));

    // Message count
    assertTrue("Expected message count", html.contains("1 message"));
  }

  // =========================================================================
  // Multiple blips
  // =========================================================================

  public void testRenderMultipleBlips() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("First message");
    data.appendBlipWithText("Second message");
    data.appendBlipWithText("Third message");
    WaveViewData viewData = data.copyViewData();

    String html = WaveContentRenderer.renderWaveContent(viewData, VIEWER);

    assertNotNull(html);
    assertTrue("Expected first blip", html.contains("First message"));
    assertTrue("Expected second blip", html.contains("Second message"));
    assertTrue("Expected third blip", html.contains("Third message"));
    assertTrue("Expected plural messages", html.contains("3 messages"));
  }

  // =========================================================================
  // Title extraction
  // =========================================================================

  public void testTitleIsExtractedFromFirstBlip() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("Meeting Notes for Q4");
    data.appendBlipWithText("Some follow-up details");
    WaveViewData viewData = data.copyViewData();

    String html = WaveContentRenderer.renderWaveContent(viewData, VIEWER);

    assertNotNull(html);
    // The title should appear in an h1 element.
    assertTrue("Expected wave-title class", html.contains("class=\"wave-title\""));
  }

  // =========================================================================
  // Empty blip
  // =========================================================================

  public void testRenderBlipWithEmptyContent() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("");
    WaveViewData viewData = data.copyViewData();

    String html = WaveContentRenderer.renderWaveContent(viewData, VIEWER);

    assertNotNull(html);
    // Should still have structural elements.
    assertTrue("Expected blip div", html.contains("class=\"blip\""));
    assertTrue("Expected author", html.contains("alice@example.com"));
    assertTrue("Expected 1 message", html.contains("1 message"));
  }

  // =========================================================================
  // HTML escaping / XSS prevention
  // =========================================================================

  public void testXssContentIsEscaped() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("<script>alert('xss')</script>");
    WaveViewData viewData = data.copyViewData();

    String html = WaveContentRenderer.renderWaveContent(viewData, VIEWER);

    assertNotNull(html);
    assertFalse("Script tag must be escaped", html.contains("<script>"));
    assertTrue("Expected escaped content", html.contains("&lt;script&gt;"));
  }

  // =========================================================================
  // Conversation body classes
  // =========================================================================

  public void testConversationStructuralClassesPresent() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("Test");
    WaveViewData viewData = data.copyViewData();

    String html = WaveContentRenderer.renderWaveContent(viewData, VIEWER);

    // Phase 1 CSS classes should still be present in the body section.
    assertTrue("Expected conversation class", html.contains("class=\"conversation\""));
    assertTrue("Expected participants class", html.contains("class=\"participants\""));
    assertTrue("Expected blip-content class", html.contains("class=\"blip-content\""));
    assertTrue("Expected blip-meta class", html.contains("class=\"blip-meta\""));
  }

  // =========================================================================
  // Metadata rendering
  // =========================================================================

  public void testCreationTimeIsRendered() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("Dated wave");
    WaveViewData viewData = data.copyViewData();

    String html = WaveContentRenderer.renderWaveContent(viewData, VIEWER);

    assertNotNull(html);
    // TestingWaveletData uses creationTime of 1234567890.
    // That's a valid epoch-ms timestamp, so we expect "Created:" to appear.
    assertTrue("Expected creation time", html.contains("Created:"));
  }

  // =========================================================================
  // Output is a fragment, not a full page
  // =========================================================================

  public void testOutputIsFragmentNotFullPage() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("Fragment test");
    WaveViewData viewData = data.copyViewData();

    String html = WaveContentRenderer.renderWaveContent(viewData, VIEWER);

    assertNotNull(html);
    assertFalse("Should not contain DOCTYPE", html.contains("<!DOCTYPE"));
    assertFalse("Should not contain <html>", html.contains("<html>"));
    assertFalse("Should not contain <head>", html.contains("<head>"));
    assertFalse("Should not contain <style>", html.contains("<style>"));
    // It should start with a div.
    assertTrue("Should start with div", html.startsWith("<div"));
  }
}
