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
package org.waveprotocol.box.server.jakarta;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

import org.junit.After;
import org.junit.Test;
import org.slf4j.MDC;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.rpc.MdcLoggingFilter;
import org.waveprotocol.wave.model.wave.ParticipantId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class MdcLoggingFilterTest {

  private final MdcLoggingFilter filter = new MdcLoggingFilter();

  @After
  public void clearMdc() {
    MDC.clear();
  }

  @Test
  public void populatesMdcForAuthenticatedRequest() throws IOException, ServletException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    ParticipantId user = ParticipantId.ofUnsafe("alice@example.com");

    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(SessionManager.USER_FIELD)).thenReturn(user);
    when(session.getId()).thenReturn("abc12345xyz");

    FilterChain chain = (req, res) -> {
      assertEquals("alice@example.com", MDC.get("participantId"));
      assertEquals("abc12345", MDC.get("sessionId")); // truncated to 8 chars
    };

    filter.doFilter(request, response, chain);
  }

  @Test
  public void clearsMdcAfterChainCompletes() throws IOException, ServletException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    HttpSession session = mock(HttpSession.class);

    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(SessionManager.USER_FIELD)).thenReturn(null);
    when(session.getId()).thenReturn("sess1234");

    // Simulate WaveClientRpcImpl setting waveId/waveletId during chain
    FilterChain chain = (req, res) -> {
      MDC.put("waveId", "example.com!w+wave1");
      MDC.put("waveletId", "example.com!conv+root");
    };

    filter.doFilter(request, response, chain);

    assertNull("participantId must be cleared", MDC.get("participantId"));
    assertNull("sessionId must be cleared", MDC.get("sessionId"));
    assertNull("waveId must be cleared", MDC.get("waveId"));
    assertNull("waveletId must be cleared", MDC.get("waveletId"));
  }

  @Test
  public void clearsMdcEvenWhenChainThrows() throws IOException, ServletException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    FilterChain chain = mock(FilterChain.class);
    ParticipantId user = ParticipantId.ofUnsafe("bob@example.com");

    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(SessionManager.USER_FIELD)).thenReturn(user);
    when(session.getId()).thenReturn("failsess1");
    doThrow(new ServletException("chain error")).when(chain).doFilter(request, response);

    try {
      filter.doFilter(request, response, chain);
    } catch (ServletException expected) {
      // expected
    }

    assertNull("participantId must be cleared after chain error", MDC.get("participantId"));
    assertNull("sessionId must be cleared after chain error", MDC.get("sessionId"));
  }

  @Test
  public void doesNotThrowOnNonHttpRequest() throws IOException, ServletException {
    jakarta.servlet.ServletRequest request = mock(jakarta.servlet.ServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertNull(MDC.get("participantId"));
    assertNull(MDC.get("sessionId"));
  }

  @Test
  public void doesNotThrowWhenSessionAttributeIsWrongType() throws IOException, ServletException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    ServletResponse response = mock(ServletResponse.class);
    HttpSession session = mock(HttpSession.class);
    FilterChain chain = mock(FilterChain.class);

    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(SessionManager.USER_FIELD)).thenReturn("not-a-participant-id");
    when(session.getId()).thenReturn("12345678abcd");

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertNull("participantId must not be set for wrong attribute type", MDC.get("participantId"));
  }
}
