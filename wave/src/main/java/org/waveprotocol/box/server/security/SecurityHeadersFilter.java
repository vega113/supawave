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
package org.waveprotocol.box.server.security;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import org.waveprotocol.wave.util.logging.Log;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Filter that adds basic security headers to HTTP responses.
 *
 * Headers:
 * - Content-Security-Policy
 * - X-Content-Type-Options
 * - Referrer-Policy
 *
 * Values are configurable via:
 *   security.csp
 *   security.referrer_policy
 *   security.x_content_type_options
 */
@Singleton
public final class SecurityHeadersFilter implements Filter {
  private static final Log LOG = Log.get(SecurityHeadersFilter.class);
  private final Config config;

  private static final String DEFAULT_CSP =
      "default-src 'self'; " +
      "base-uri 'self'; frame-ancestors 'self'; object-src 'none'; " +
      "script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; " +
      "img-src 'self' data:; font-src 'self' data:; connect-src 'self' ws: wss:";

  private static final String DEFAULT_REFERRER = "strict-origin-when-cross-origin";
  private static final String DEFAULT_XCTO = "nosniff";

  @Inject
  public SecurityHeadersFilter(Config config) {
    this.config = config;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (request instanceof HttpServletRequest) ? (HttpServletRequest) request : null;
    if (response instanceof HttpServletResponse) {
      HttpServletResponse http = (HttpServletResponse) response;

      String csp = config.hasPath("security.csp") ? config.getString("security.csp") : DEFAULT_CSP;
      String referrer = config.hasPath("security.referrer_policy")
          ? config.getString("security.referrer_policy") : DEFAULT_REFERRER;
      String xcto = config.hasPath("security.x_content_type_options")
          ? config.getString("security.x_content_type_options") : DEFAULT_XCTO;

      if (!isCommitted(http)) {
        http.setHeader("Content-Security-Policy", csp);
        http.setHeader("Referrer-Policy", referrer);
        http.setHeader("X-Content-Type-Options", xcto);

        // Conditionally add HSTS when connection is secure
    try {
      int hstsMaxAge = config.hasPath("security.hsts_max_age") ? config.getInt("security.hsts_max_age") : 0;
      boolean hstsIncludeSub = config.hasPath("security.hsts_include_subdomains") && config.getBoolean("security.hsts_include_subdomains");
      if (hstsMaxAge > 0 && req != null && req.isSecure()) {
        String value = "max-age=" + hstsMaxAge + (hstsIncludeSub ? "; includeSubDomains" : "");
        http.setHeader("Strict-Transport-Security", value);
      }
    } catch (Exception t) {
      LOG.warning("Failed to configure HSTS header", t);
    }
      }
    }

    chain.doFilter(request, response);
  }

  private boolean isCommitted(HttpServletResponse http) {
    try {
      return http.isCommitted();
    } catch (Throwable t) {
      return false;
    }
  }

  @Override
  public void destroy() {}
}
