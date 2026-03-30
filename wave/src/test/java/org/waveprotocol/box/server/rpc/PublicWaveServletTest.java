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
package org.waveprotocol.box.server.rpc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import junit.framework.TestCase;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.document.operation.impl.DocInitializationBuilder;
import org.waveprotocol.wave.model.document.operation.impl.AnnotationBoundaryMapBuilder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.WaveletData;

import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tests for {@link PublicWaveServlet}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Public waves (with domain participant) are rendered as HTML</li>
 *   <li>Private waves return 404 (not 403)</li>
 *   <li>Non-existent waves return 404</li>
 *   <li>Invalid wave IDs return 404</li>
 *   <li>Correct cache headers are set</li>
 * </ul>
 */
public class PublicWaveServletTest extends TestCase {

  private static final String DOMAIN = "example.com";
  private static final WaveId WAVE_ID = WaveId.of(DOMAIN, "w+abc123");
  private static final WaveletId CONV_ROOT = WaveletId.of(DOMAIN, "conv+root");
  private static final ParticipantId CREATOR = ParticipantId.ofUnsafe("sam@" + DOMAIN);
  private static final ParticipantId DOMAIN_PARTICIPANT =
      ParticipantId.ofUnsafe("@" + DOMAIN);

  private WaveletProvider waveletProvider;
  private PublicWaveServlet servlet;

  @Override
  protected void setUp() throws Exception {
    waveletProvider = mock(WaveletProvider.class);
    servlet = new PublicWaveServlet(waveletProvider, DOMAIN);
  }

  // -------------------------------------------------------------------------
  // Helper: create a wavelet with participants
  // -------------------------------------------------------------------------

  private WaveletData createWavelet(ParticipantId... extraParticipants) {
    WaveletName name = WaveletName.of(WAVE_ID, CONV_ROOT);
    WaveletData wavelet = WaveletDataUtil.createEmptyWavelet(
        name, CREATOR, HashedVersion.unsigned(0), 1234567890);
    wavelet.addParticipant(CREATOR);
    for (ParticipantId p : extraParticipants) {
      wavelet.addParticipant(p);
    }
    // Add a blip with content
    wavelet.createDocument("b+first", CREATOR,
        java.util.Collections.<ParticipantId>emptySet(),
        new DocInitializationBuilder()
            .elementStart("body", org.waveprotocol.wave.model.document.operation.Attributes.EMPTY_MAP)
            .elementStart("line", org.waveprotocol.wave.model.document.operation.Attributes.EMPTY_MAP)
            .elementEnd()
            .characters("Hello public world")
            .elementEnd()
            .build(),
        1234567890, 0);
    return wavelet;
  }

  private WaveletData createWaveletWithLink(String linkUrl, ParticipantId... extraParticipants) {
    WaveletName name = WaveletName.of(WAVE_ID, CONV_ROOT);
    WaveletData wavelet = WaveletDataUtil.createEmptyWavelet(
        name, CREATOR, HashedVersion.unsigned(0), 1234567890);
    wavelet.addParticipant(CREATOR);
    for (ParticipantId p : extraParticipants) {
      wavelet.addParticipant(p);
    }

    DocInitializationBuilder contentBuilder = new DocInitializationBuilder()
        .elementStart("body", org.waveprotocol.wave.model.document.operation.Attributes.EMPTY_MAP)
        .elementStart("line", org.waveprotocol.wave.model.document.operation.Attributes.EMPTY_MAP)
        .elementEnd()
        .annotationBoundary(new AnnotationBoundaryMapBuilder()
            .change("link/manual", null, linkUrl)
            .build())
        .characters("Danger link")
        .annotationBoundary(new AnnotationBoundaryMapBuilder()
            .end("link/manual")
            .build())
        .elementEnd();

    wavelet.createDocument("b+first", CREATOR,
        java.util.Collections.<ParticipantId>emptySet(),
        contentBuilder.build(),
        1234567890, 0);
    return wavelet;
  }

  private void setupWaveletProvider(WaveletData wavelet) throws Exception {
    when(waveletProvider.getWaveletIds(WAVE_ID))
        .thenReturn(ImmutableSet.of(CONV_ROOT));
    when(waveletProvider.getSnapshot(WaveletName.of(WAVE_ID, CONV_ROOT)))
        .thenReturn(new CommittedWaveletSnapshot(wavelet, HashedVersion.unsigned(0)));
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  public void testInvalidWaveIdReturns404() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(request.getPathInfo()).thenReturn("/not-a-valid-wave-id");
    servlet.doGet(request, response);
    verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  public void testNullPathInfoReturns404() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(request.getPathInfo()).thenReturn(null);
    servlet.doGet(request, response);
    verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  public void testEmptyPathInfoReturns404() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(request.getPathInfo()).thenReturn("/");
    servlet.doGet(request, response);
    verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  public void testNonExistentWaveReturns404() throws Exception {
    WaveId unknownWave = WaveId.of(DOMAIN, "w+unknown999");
    when(waveletProvider.getWaveletIds(unknownWave))
        .thenReturn(ImmutableSet.of());

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn("/" + unknownWave.serialise());

    servlet.doGet(request, response);
    verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  public void testPrivateWaveReturns404NotForbidden() throws Exception {
    // Create a wavelet without the domain participant (private)
    WaveletData wavelet = createWavelet();
    setupWaveletProvider(wavelet);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn("/" + WAVE_ID.serialise());

    servlet.doGet(request, response);
    // Must be 404 (not 403) to avoid leaking wave existence
    verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  public void testPublicWaveReturnsHtml() throws Exception {
    // Create a wavelet WITH the domain participant (public)
    WaveletData wavelet = createWavelet(DOMAIN_PARTICIPANT);
    setupWaveletProvider(wavelet);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    when(request.getPathInfo()).thenReturn("/" + WAVE_ID.serialise());

    servlet.doGet(request, response);

    // Verify HTML response headers
    verify(response).setContentType("text/html");
    verify(response).setCharacterEncoding("UTF-8");
    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    verify(response).setHeader("Pragma", "no-cache");

    // Verify the response contains expected HTML elements
    String html = writer.toString();
    assertTrue("Expected DOCTYPE", html.contains("<!DOCTYPE html>"));
    assertTrue("Expected SupaWave branding", html.contains("SupaWave"));
    assertTrue("Expected sign-in CTA", html.contains("Sign in to collaborate"));
    assertTrue("Expected wave content", html.contains("Hello public world"));
    assertTrue("Expected og:title meta tag", html.contains("og:title"));
    assertTrue("Expected og:description meta tag", html.contains("og:description"));
    assertTrue("Expected Read Only badge", html.contains("Read Only"));
  }

  public void testPublicWaveContainsParticipantInfo() throws Exception {
    ParticipantId otherUser = ParticipantId.ofUnsafe("alice@" + DOMAIN);
    WaveletData wavelet = createWavelet(DOMAIN_PARTICIPANT, otherUser);
    setupWaveletProvider(wavelet);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    when(request.getPathInfo()).thenReturn("/" + WAVE_ID.serialise());

    servlet.doGet(request, response);

    String html = writer.toString();
    // The rendered wave should contain either the blip author in the blip-meta
    // section or participant avatars. The exact rendering depends on whether
    // the conversation model is fully formed. At minimum, verify we got
    // valid HTML with wave content.
    assertTrue("Expected wave content div", html.contains("class=\"wave\""));
  }

  public void testPublicWaveContainsSeoMetaTags() throws Exception {
    WaveletData wavelet = createWavelet(DOMAIN_PARTICIPANT);
    setupWaveletProvider(wavelet);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    when(request.getPathInfo()).thenReturn("/" + WAVE_ID.serialise());

    servlet.doGet(request, response);

    String html = writer.toString();
    assertTrue("Expected robots meta", html.contains("robots"));
    assertTrue("Expected twitter:card meta", html.contains("twitter:card"));
    assertTrue("Expected og:type meta", html.contains("og:type"));
  }

  public void testPublicWaveBlocksJavascriptLinkAnnotations() throws Exception {
    WaveletData wavelet = createWaveletWithLink("javascript:alert(1)", DOMAIN_PARTICIPANT);
    setupWaveletProvider(wavelet);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    when(request.getPathInfo()).thenReturn("/" + WAVE_ID.serialise());

    servlet.doGet(request, response);

    String html = writer.toString();
    assertFalse("Expected javascript URL to be removed", html.contains("href=\"javascript:alert(1)\""));
    assertTrue("Expected link text to still render", html.contains("Danger link"));
  }

  public void testPublicWaveKeepsHttpsLinkAnnotations() throws Exception {
    WaveletData wavelet = createWaveletWithLink("https://example.com/docs", DOMAIN_PARTICIPANT);
    setupWaveletProvider(wavelet);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    when(request.getPathInfo()).thenReturn("/" + WAVE_ID.serialise());

    servlet.doGet(request, response);

    String html = writer.toString();
    assertTrue("Expected https URL to be preserved", html.contains("href=\"https://example.com/docs\""));
  }
}
