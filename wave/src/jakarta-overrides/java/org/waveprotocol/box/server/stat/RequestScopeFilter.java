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
package org.waveprotocol.box.server.stat;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Objects;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.stat.RequestScope;
import org.waveprotocol.box.stat.SessionContext;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Jakarta-path request scope filter that records session/user context when
 * {@code core.enable_profiling} is enabled.
 */
public final class RequestScopeFilter implements Filter {
  private final SessionManager sessionManager;

  /** No-arg constructor for containers that rely on reflection. */
  public RequestScopeFilter() {
    this(null);
  }

  /** Injected constructor used by Jakarta ServerRpcProvider. */
  public RequestScopeFilter(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (Timing.isEnabled()) {
      Timing.enterScope();
      HttpServletRequest http = (HttpServletRequest) request;
      HttpSession rawSession = null;
      try {
        rawSession = http.getSession(false);
      } catch (IllegalStateException ignored) {
        // Session already invalidated; treat as anonymous.
      }
      final String sessionId = (rawSession != null) ? rawSession.getId() : null;
      ParticipantId user = null;
      if (rawSession != null) {
        if (sessionManager != null) {
          WebSession webSession = WebSessions.wrap(rawSession);
          user = sessionManager.getLoggedInUser(webSession);
        } else {
          Object attr = rawSession.getAttribute(SessionManager.USER_FIELD);
          if (attr instanceof ParticipantId) {
            user = (ParticipantId) attr;
          }
        }
      }
      final ParticipantId loggedIn = user;
      Timing.setScopeValue(SessionContext.class, new SessionContext() {
        @Override
        public boolean isAuthenticated() {
          return loggedIn != null;
        }

        @Override
        public String getSessionKey() {
          return sessionId;
        }

        @Override
        public ParticipantId getParticipantId() {
          return loggedIn;
        }

        @Override
        public RequestScope.Value clone() {
          return this;
        }

        @Override
        public boolean equals(Object obj) {
          if (!(obj instanceof SessionContext)) {
            return false;
          }
          SessionContext other = (SessionContext) obj;
          return Objects.equals(getSessionKey(), other.getSessionKey())
              && Objects.equals(getParticipantId(), other.getParticipantId());
        }

        @Override
        public int hashCode() {
          return Objects.hash(getSessionKey(), getParticipantId());
        }
      });
    }
    try {
      chain.doFilter(request, response);
    } finally {
      Timing.exitScope();
    }
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
