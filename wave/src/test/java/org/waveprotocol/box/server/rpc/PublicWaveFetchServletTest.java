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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;

import io.micrometer.core.instrument.Counter;
import junit.framework.TestCase;

import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.stat.MetricsHolder;
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.WaveletData;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.jvm.JavaWaverefEncoder;
import org.waveprotocol.box.server.util.TestDataUtil;

import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tests for the PublicWaveFetchServlet.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Public waves (with domain participant) are accessible without authentication</li>
 *   <li>Private waves return 404 (not 403, to avoid leaking existence)</li>
 *   <li>Non-existent waves return 404</li>
 *   <li>Invalid wave refs return 404</li>
 *   <li>Write methods return 405</li>
 * </ul>
 */
public class PublicWaveFetchServletTest extends TestCase {
  private static final String DOMAIN = "example.com";
  private static final ParticipantId DOMAIN_PARTICIPANT = ParticipantId.ofUnsafe("@" + DOMAIN);
  private static final ProtoSerializer protoSerializer = new ProtoSerializer();

  private WaveletProviderStub waveletProvider;
  private PublicWaveFetchServlet servlet;

  @Override
  protected void setUp() throws Exception {
    waveletProvider = new WaveletProviderStub();
    MetricsHolder.prometheus().clear();
    servlet = new PublicWaveFetchServlet(waveletProvider, protoSerializer, DOMAIN,
        new AnalyticsRecorder());
  }

  /**
   * Test that an invalid wave ref returns 404.
   */
  public void testGetInvalidWaverefReturnsNotFound() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(request.getPathInfo()).thenReturn("/invalidwaveref");
    servlet.doGet(request, response);
    verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  /**
   * Test that a null path returns 404.
   */
  public void testGetNullPathReturnsNotFound() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(request.getPathInfo()).thenReturn(null);
    servlet.doGet(request, response);
    verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  /**
   * Test that a private wave (without domain participant) returns 404.
   */
  public void testPrivateWaveReturnsNotFound() throws Exception {
    // The default WaveletProviderStub wavelet doesn't have domain participant,
    // so it's private.
    WaveletData wavelet = waveletProvider.getHostedWavelet();
    WaveRef waveref = WaveRef.of(wavelet.getWaveId(), wavelet.getWaveletId());

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn("/" + JavaWaverefEncoder.encodeToUriPathSegment(waveref));

    servlet.doGet(request, response);

    // Should return 404, NOT 403, to avoid leaking wave existence
    verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
    assertEquals(0L, totalApiViews());
  }

  /**
   * Test that a public wave (with domain participant) is accessible.
   */
  public void testPublicWaveIsAccessible() throws Exception {
    // Add domain participant to make the wave public
    WaveletData wavelet = waveletProvider.getHostedWavelet();
    wavelet.addParticipant(DOMAIN_PARTICIPANT);

    WaveRef waveref = WaveRef.of(wavelet.getWaveId(), wavelet.getWaveletId());

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    when(request.getPathInfo()).thenReturn("/" + JavaWaverefEncoder.encodeToUriPathSegment(waveref));

    servlet.doGet(request, response);

    // Should return 200 with content
    verify(response).getWriter();
    verify(response, never()).sendError(anyInt());
    assertTrue("Response should contain JSON data", writer.toString().length() > 0);
    assertEquals(1L, totalApiViews());
  }

  /**
   * Test that a non-existent wave returns 404.
   */
  public void testNonExistentWaveReturnsNotFound() throws Exception {
    WaveRef unknownWave = WaveRef.of(WaveId.of("example.com", "w+unknown"));

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn("/" + JavaWaverefEncoder.encodeToUriPathSegment(unknownWave));

    servlet.doGet(request, response);

    verify(response, times(1)).sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  /**
   * Test that POST returns 405 Method Not Allowed.
   */
  public void testPostReturnsMethodNotAllowed() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    servlet.doPost(request, response);
    verify(response, times(1)).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "Public wave access is read-only");
  }

  /**
   * Test that PUT returns 405 Method Not Allowed.
   */
  public void testPutReturnsMethodNotAllowed() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    servlet.doPut(request, response);
    verify(response, times(1)).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "Public wave access is read-only");
  }

  /**
   * Test that DELETE returns 405 Method Not Allowed.
   */
  public void testDeleteReturnsMethodNotAllowed() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    servlet.doDelete(request, response);
    verify(response, times(1)).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
        "Public wave access is read-only");
  }

  private long totalApiViews() {
    Counter counter = MetricsHolder.registry().find("wave.analytics.public_wave.api_views").counter();
    return counter == null ? 0L : Math.round(counter.count());
  }

  /**
   * Test that a public wave returns no-cache headers to prevent stale content
   * after a wave is toggled from public to private.
   */
  public void testPublicWaveHasNoCacheHeaders() throws Exception {
    WaveletData wavelet = waveletProvider.getHostedWavelet();
    wavelet.addParticipant(DOMAIN_PARTICIPANT);

    WaveRef waveref = WaveRef.of(wavelet.getWaveId(), wavelet.getWaveletId());

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter writer = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(writer));
    when(request.getPathInfo()).thenReturn("/" + JavaWaverefEncoder.encodeToUriPathSegment(waveref));

    servlet.doGet(request, response);

    verify(response).setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    verify(response).setHeader("Pragma", "no-cache");
  }
}
