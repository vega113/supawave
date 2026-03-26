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

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.authentication.jwt.BrowserSessionJwt;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;

/**
 * Jakarta variant of SignOutServlet using jakarta.servlet.* and bridging
 * session handling to the legacy javax-based SessionManager.
 */
@SuppressWarnings("serial")
@Singleton
public class SignOutServlet extends HttpServlet {
  private final SessionManager sessionManager;

  @Inject
  public SignOutServlet(SessionManager sessionManager) {
    Preconditions.checkNotNull(sessionManager, "Session manager is null");
    this.sessionManager = sessionManager;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // 1. Remove the logged-in user from the session attributes.
    WebSession session = WebSessions.from(req, false);
    sessionManager.logout(session);

    // 2. Clear the JWT session cookie so the JwtSessionRestorationFilter
    //    does not immediately re-establish the session on the next request.
    resp.addHeader("Set-Cookie",
        BrowserSessionJwt.COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");

    // 3. Invalidate the underlying HTTP session to discard all server-side state.
    HttpSession httpSession = req.getSession(false);
    if (httpSession != null) {
      try {
        httpSession.invalidate();
      } catch (IllegalStateException ignored) {
        // Already invalidated — safe to ignore.
      }
    }

    String redirectUrl = req.getParameter("r");
    if (isSafeLocalRedirect(redirectUrl)) {
      try {
        URI u = new URI(redirectUrl).normalize();
        resp.sendRedirect(u.toString());
        return;
      } catch (URISyntaxException ignore) {
        // fall through
      }
    }

    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/html");
    try (var w = resp.getWriter()) { w.print("<html><body>Logged out.</body></html>"); w.flush(); }
  }

  private static boolean isSafeLocalRedirect(String r) {
    if (r == null || r.isEmpty()) return false;
    if (r.length() > 2048) return false;
    if (r.indexOf('\r') >= 0 || r.indexOf('\n') >= 0) return false;
    if (r.startsWith("//")) return false;
    try {
      URI u = new URI(r).normalize();
      boolean hasScheme = u.getScheme() != null;
      boolean hasAuthority = u.getRawAuthority() != null || u.getHost() != null;
      String path = u.getPath();
      boolean startsWithSlash = path != null && path.startsWith("/");
      boolean containsTraversal = path != null && (path.contains("/../") || path.contains("/./"));
      return !hasScheme && !hasAuthority && startsWithSlash && !containsTraversal;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
