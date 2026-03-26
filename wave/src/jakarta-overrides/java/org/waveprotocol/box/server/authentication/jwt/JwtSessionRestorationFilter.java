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
package org.waveprotocol.box.server.authentication.jwt;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.wave.model.wave.InvalidParticipantAddress;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Servlet filter that restores the user's HTTP session from a valid JWT cookie
 * when the HTTP session is missing or does not contain a logged-in user.
 *
 * <p>After a server restart or container replacement, the Jetty
 * {@code FileSessionDataStore} may fail to reload serialized sessions.  The
 * JWT cookie ({@code wave-session-jwt}), however, is cryptographically signed
 * with a key that persists on a volume mount and survives deploys.  This filter
 * bridges the gap: it validates the JWT and re-establishes the HTTP session so
 * that the rest of the servlet stack sees an authenticated user without
 * requiring the user to log in again.
 */
public final class JwtSessionRestorationFilter implements Filter {
  private static final Log LOG = Log.get(JwtSessionRestorationFilter.class);

  private final SessionManager sessionManager;
  private final JwtKeyRing keyRing;

  public JwtSessionRestorationFilter(SessionManager sessionManager, JwtKeyRing keyRing) {
    this.sessionManager = sessionManager;
    this.keyRing = keyRing;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request instanceof HttpServletRequest httpReq
        && response instanceof HttpServletResponse httpResp) {
      tryRestoreSession(httpReq, httpResp);
    }
    chain.doFilter(request, response);
  }

  private void tryRestoreSession(HttpServletRequest request, HttpServletResponse response) {
    // If the user already has an active session, nothing to do.
    WebSession existingSession = WebSessions.from(request, false);
    if (existingSession != null && sessionManager.getLoggedInUser(existingSession) != null) {
      return;
    }

    String jwtToken = extractJwtCookie(request);
    if (jwtToken == null) {
      return;
    }

    try {
      // Use a permissive revocation state: browser session JWTs do not
      // participate in version-based revocation at the filter level.
      JwtRevocationState revocationState = new JwtRevocationState(0, 0);
      JwtTokenContext context = keyRing.validator().validate(jwtToken, revocationState);
      JwtClaims claims = context.claims();

      // Only accept browser-session tokens
      if (claims.tokenType() != JwtTokenType.BROWSER_SESSION) {
        return;
      }

      // Only accept tokens with the BROWSER audience
      if (!claims.hasAudience(JwtAudience.BROWSER)) {
        return;
      }

      ParticipantId participant = ParticipantId.of(claims.subject());

      // Create a new HTTP session and set the logged-in user
      WebSession newSession = WebSessions.from(request, true);
      if (newSession != null) {
        sessionManager.setLoggedInUser(newSession, participant);
        if (LOG.isFineLoggable()) {
          LOG.fine("Restored session from JWT for " + participant.getAddress());
        }
      }
    } catch (JwtValidationException e) {
      // Token is invalid, expired, or signature doesn't match.
      // Clear the stale cookie so the browser doesn't keep sending it.
      if (LOG.isFineLoggable()) {
        LOG.fine("JWT session cookie invalid, clearing: " + e.getMessage());
      }
      clearJwtCookie(response);
    } catch (InvalidParticipantAddress e) {
      LOG.warning("JWT contains invalid participant address", e);
      clearJwtCookie(response);
    } catch (Exception e) {
      // Defensive catch-all: never let the filter break the request pipeline.
      LOG.warning("Unexpected error restoring session from JWT", e);
    }
  }

  private static String extractJwtCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (BrowserSessionJwt.COOKIE_NAME.equals(cookie.getName())) {
        String value = cookie.getValue();
        if (value != null && !value.isEmpty()) {
          return value;
        }
      }
    }
    return null;
  }

  private static void clearJwtCookie(HttpServletResponse response) {
    response.addHeader("Set-Cookie",
        BrowserSessionJwt.COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
  }

  @Override
  public void init(FilterConfig filterConfig) {
    // no-op
  }

  @Override
  public void destroy() {
    // no-op
  }
}
