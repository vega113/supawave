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
 * Tests for {@link ServerHtmlRenderer}.
 */
public class ServerHtmlRendererTest extends TestCase {

  private static final WaveId WAVE_ID = WaveId.of("example.com", "w+test");
  private static final WaveletId CONV_WAVELET_ID = WaveletId.of("example.com", "conv+root");
  private static final ParticipantId AUTHOR = ParticipantId.ofUnsafe("alice@example.com");
  private static final ParticipantId VIEWER = ParticipantId.ofUnsafe("viewer@example.com");

  // =========================================================================
  // Tests for escapeHtml
  // =========================================================================

  public void testEscapeHtmlHandlesSpecialChars() {
    assertEquals("&amp;&lt;&gt;&quot;&#39;", ServerHtmlRenderer.escapeHtml("&<>\"'"));
  }

  public void testEscapeHtmlHandlesNull() {
    assertEquals("", ServerHtmlRenderer.escapeHtml(null));
  }

  public void testEscapeHtmlPreservesPlainText() {
    assertEquals("Hello world", ServerHtmlRenderer.escapeHtml("Hello world"));
  }

  public void testSanitizeUrlAllowsSafeSchemes() {
    assertEquals("https://example.com", ServerHtmlRenderer.sanitizeUrl("https://example.com", true));
    assertEquals("http://example.com", ServerHtmlRenderer.sanitizeUrl("http://example.com", false));
    assertEquals("/waves/123", ServerHtmlRenderer.sanitizeUrl("/waves/123", false));
    assertEquals("ftp://files.example.com/doc.pdf",
        ServerHtmlRenderer.sanitizeUrl("ftp://files.example.com/doc.pdf", false));
    assertEquals("mailto:user@example.com",
        ServerHtmlRenderer.sanitizeUrl("mailto:user@example.com", true));
    assertEquals("wave://example.com/w+abc",
        ServerHtmlRenderer.sanitizeUrl("wave://example.com/w+abc", true));
    assertEquals("waveid://example.com/w+abc",
        ServerHtmlRenderer.sanitizeUrl("waveid://example.com/w+abc", true));
  }

  public void testSanitizeUrlRejectsUnsafeSchemes() {
    assertNull(ServerHtmlRenderer.sanitizeUrl("javascript:alert(1)", true));
    assertNull(ServerHtmlRenderer.sanitizeUrl("data:text/html;base64,PHNjcmlwdA==", true));
    assertNull(ServerHtmlRenderer.sanitizeUrl("vbscript:msgbox(1)", true));
    assertNull(ServerHtmlRenderer.sanitizeUrl("mailto:user@example.com", false));
    assertNull(ServerHtmlRenderer.sanitizeUrl("//example.com/x", true));
    assertNull(ServerHtmlRenderer.sanitizeUrl("wave://example.com/w+abc", false));
  }

  public void testSanitizeUrlAllowsUrlsWithSpaces() {
    String urlWithSpaces = "https://example.com/search?q=hello world&lang=en";
    String sanitized = ServerHtmlRenderer.sanitizeUrl(urlWithSpaces, true);
    assertNotNull("URL with spaces in query should not be dropped", sanitized);
    assertTrue("Sanitized URL should preserve the https scheme and host",
        sanitized.startsWith("https://example.com/"));
  }

  // =========================================================================
  // Tests for renderWave with empty / missing data
  // =========================================================================

  public void testRenderWaveWithNoConversationReturnsEmptyDiv() {
    // A non-conversational wavelet should produce an empty wrapper.
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, false);
    WaveViewData viewData = data.copyViewData();

    String html = ServerHtmlRenderer.renderWave(viewData, VIEWER);
    assertNotNull(html);
    assertTrue("Expected wave wrapper div", html.contains("class=\"wave\""));
  }

  // =========================================================================
  // Tests for renderWave with real conversation data
  // =========================================================================

  public void testRenderWaveWithSingleBlip() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("Hello, World!");
    WaveViewData viewData = data.copyViewData();

    String html = ServerHtmlRenderer.renderWave(viewData, VIEWER);

    assertNotNull(html);
    // Should contain the blip text.
    assertTrue("Expected blip text", html.contains("Hello, World!"));
    // Should contain the author address.
    assertTrue("Expected author", html.contains("alice@example.com"));
    // Should have structural CSS classes.
    assertTrue("Expected blip div", html.contains("class=\"blip\""));
    assertTrue("Expected conversation div", html.contains("class=\"conversation\""));
    assertTrue("Expected participants div", html.contains("class=\"participants\""));
  }

  public void testRenderWaveWithMultipleBlips() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("First blip");
    data.appendBlipWithText("Second blip");
    data.appendBlipWithText("Third blip");
    WaveViewData viewData = data.copyViewData();

    String html = ServerHtmlRenderer.renderWave(viewData, VIEWER);

    assertNotNull(html);
    assertTrue("Expected first blip text", html.contains("First blip"));
    assertTrue("Expected second blip text", html.contains("Second blip"));
    assertTrue("Expected third blip text", html.contains("Third blip"));
  }

  public void testRenderWavePageProducesCompletePage() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("Page test");
    WaveViewData viewData = data.copyViewData();

    String html = ServerHtmlRenderer.renderWavePage(viewData, VIEWER);

    assertNotNull(html);
    assertTrue("Expected DOCTYPE", html.startsWith("<!DOCTYPE html>"));
    assertTrue("Expected <html>", html.contains("<html>"));
    assertTrue("Expected <style>", html.contains("<style>"));
    assertTrue("Expected page content", html.contains("Page test"));
    assertTrue("Expected closing body", html.contains("</body>"));
  }

  // =========================================================================
  // Tests for HTML escaping in rendered output
  // =========================================================================

  public void testRenderWaveEscapesBlipText() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    data.appendBlipWithText("<script>alert('xss')</script>");
    WaveViewData viewData = data.copyViewData();

    String html = ServerHtmlRenderer.renderWave(viewData, VIEWER);

    assertNotNull(html);
    // The script tag should be escaped.
    assertFalse("XSS text should be escaped", html.contains("<script>"));
    assertTrue("Should contain escaped text", html.contains("&lt;script&gt;"));
  }

  public void testRenderWaveWithBlipWithNoTextProducesStructure() {
    TestingWaveletData data =
        new TestingWaveletData(WAVE_ID, CONV_WAVELET_ID, AUTHOR, true);
    // Append a blip with empty text.
    data.appendBlipWithText("");
    WaveViewData viewData = data.copyViewData();

    String html = ServerHtmlRenderer.renderWave(viewData, VIEWER);

    assertNotNull(html);
    assertTrue("Expected blip div", html.contains("class=\"blip\""));
    assertTrue("Expected author", html.contains("alice@example.com"));
  }
}
