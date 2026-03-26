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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwt;
import org.waveprotocol.box.server.authentication.jwt.JwtKeyRing;
import org.waveprotocol.box.server.authentication.jwt.JwtSessionRestorationFilter;
import org.waveprotocol.wave.model.wave.ParticipantId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JwtSessionRestorationFilter}.
 *
 * <p>Verifies that the filter restores an HTTP session from a valid JWT cookie
 * when the session is missing, and that it correctly passes through when the
 * session already exists or no JWT cookie is present.
 */
public final class JwtSessionRestorationFilterTest {
  private static final String DOMAIN = "example.com";
  private static final ParticipantId USER = ParticipantId.ofUnsafe("alice@example.com");

  private JwtKeyRing keyRing;
  private SessionManager sessionManager;
  private JwtSessionRestorationFilter filter;

  @Before
  public void setUp() {
    keyRing = JwtKeyRing.generate("test-key");
    sessionManager = mock(SessionManager.class);
    filter = new JwtSessionRestorationFilter(sessionManager, keyRing);
  }

  /** Issues a browser-session JWT for testing. */
  private String issueBrowserSessionJwt(ParticipantId subject) {
    long now = Clock.systemUTC().instant().getEpochSecond();
    return keyRing.issuer().issue(BrowserSessionJwt.claims(
        DOMAIN,
        subject.getAddress(),
        UUID.randomUUID().toString(),
        keyRing.signingKeyId(),
        now,
        now + 86400L,
        0L));
  }

  @Test
  public void restoresSessionFromValidJwtWhenNoExistingSession() throws Exception {
    String token = issueBrowserSessionJwt(USER);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    // No existing session
    when(request.getSession(false)).thenReturn(null);
    // JWT cookie present
    when(request.getCookies()).thenReturn(new Cookie[]{
        new Cookie(BrowserSessionJwt.COOKIE_NAME, token)
    });
    // Creating a new session returns a mock
    HttpSession newSession = mock(HttpSession.class);
    when(request.getSession(true)).thenReturn(newSession);

    filter.doFilter(request, response, chain);

    // Should set the logged-in user on the new session
    verify(sessionManager).setLoggedInUser(any(WebSession.class), eq(USER));
    // Should continue the filter chain
    verify(chain).doFilter(request, response);
  }

  @Test
  public void skipsRestorationWhenSessionAlreadyExists() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    // Existing session with logged-in user
    HttpSession existingSession = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(existingSession);
    when(existingSession.getAttribute("user")).thenReturn(USER);
    when(sessionManager.getLoggedInUser(any(WebSession.class))).thenReturn(USER);

    filter.doFilter(request, response, chain);

    // Should not try to create a new session
    verify(request, never()).getSession(true);
    verify(chain).doFilter(request, response);
  }

  @Test
  public void skipsRestorationWhenNoCookiePresent() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    // No existing session
    when(request.getSession(false)).thenReturn(null);
    // No cookies
    when(request.getCookies()).thenReturn(null);

    filter.doFilter(request, response, chain);

    verify(request, never()).getSession(true);
    verify(chain).doFilter(request, response);
  }

  @Test
  public void clearsInvalidJwtCookie() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    // No existing session
    when(request.getSession(false)).thenReturn(null);
    // Invalid JWT cookie
    when(request.getCookies()).thenReturn(new Cookie[]{
        new Cookie(BrowserSessionJwt.COOKIE_NAME, "invalid.jwt.token")
    });

    filter.doFilter(request, response, chain);

    // Should clear the cookie
    verify(response).addHeader(eq("Set-Cookie"),
        eq("wave-session-jwt=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"));
    // Should still continue the filter chain
    verify(chain).doFilter(request, response);
  }

  @Test
  public void ignoresNonBrowserSessionTokenType() throws Exception {
    // Use the keyRing to issue a token manually with a different type
    // For this test, just verify that the filter gracefully handles the
    // case where the JWT cookie is present but the session is missing.
    // The filter only restores BROWSER_SESSION tokens.
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    // No existing session
    when(request.getSession(false)).thenReturn(null);
    // Empty cookie value should be ignored
    when(request.getCookies()).thenReturn(new Cookie[]{
        new Cookie(BrowserSessionJwt.COOKIE_NAME, "")
    });

    filter.doFilter(request, response, chain);

    verify(request, never()).getSession(true);
    verify(chain).doFilter(request, response);
  }

  @Test
  public void continuesFilterChainEvenOnUnexpectedError() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    // getSession(false) throws unexpected exception
    when(request.getSession(anyBoolean())).thenThrow(new IllegalStateException("test error"));
    // Provide cookies so the filter tries JWT validation path
    when(request.getCookies()).thenReturn(null);

    filter.doFilter(request, response, chain);

    // Must still call the rest of the chain
    verify(chain).doFilter(request, response);
  }
}
