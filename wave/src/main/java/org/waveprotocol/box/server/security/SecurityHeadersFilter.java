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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

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
  private final Config config;

  private static final String DEFAULT_CSP =
      "default-src 'self'; " +
      "base-uri 'self'; frame-ancestors 'self'; object-src 'none'; " +
      "script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; " +
      "img-src 'self' data:; font-src 'self' data:; connect-src 'self' ws: wss:";

  private static final String DEFAULT_REFERRER = "strict-origin-when-cross-origin";
  private static final String DEFAULT_XCTO = "nosniff";
  private static final Pattern CODE_SERVER_VALUE = Pattern.compile("[A-Za-z0-9.\\-\\[\\]:]+");

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

      if (req != null) {
        String cspWithDev = addCodeServerHosts(req, csp);
        if (cspWithDev != null) {
          csp = cspWithDev;
        }
      }

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
        } catch (Throwable ignored) {}
      }
    }

    chain.doFilter(request, response);
  }

  private String addCodeServerHosts(HttpServletRequest req, String originalCsp) {
    String query = req.getQueryString();
    if (query == null || query.isEmpty()) {
      return null;
    }
    Set<String> hosts = new LinkedHashSet<>();
    for (String part : query.split("&")) {
      if (part == null || part.isEmpty()) {
        continue;
      }
      String key;
      String value;
      int idx = part.indexOf('=');
      if (idx >= 0) {
        key = part.substring(0, idx);
        value = part.substring(idx + 1);
      } else {
        key = part;
        value = "";
      }
      if (!key.startsWith("gwt.codesvr")) {
        continue;
      }
      String decoded;
      try {
        decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException ex) {
        decoded = value;
      }
      String host = normaliseCodeServer(decoded);
      if (host != null) {
        hosts.add(host);
      }
    }
    if (hosts.isEmpty()) {
      return null;
    }
    String updated = originalCsp;
    for (String host : hosts) {
      updated = appendToDirective(updated, "script-src", host);
      updated = appendToDirective(updated, "connect-src", host);
    }
    return updated;
  }

  private String normaliseCodeServer(String raw) {
    if (raw == null) {
      return null;
    }
    String value = raw.trim();
    if (value.isEmpty()) {
      return null;
    }
    int delimiter = Math.max(value.lastIndexOf('@'), value.lastIndexOf('='));
    if (delimiter >= 0 && delimiter + 1 < value.length()) {
      value = value.substring(delimiter + 1);
    }
    if (value.startsWith("//")) {
      value = value.substring(2);
    }
    value = value.trim();
    if (value.isEmpty()) {
      return null;
    }
    String candidate = value;
    if (candidate.startsWith("http://")) {
      candidate = candidate.substring(7);
    } else if (candidate.startsWith("https://")) {
      candidate = candidate.substring(8);
    }
    if (candidate.isEmpty() || !CODE_SERVER_VALUE.matcher(candidate).matches()) {
      return null;
    }
    if (!value.startsWith("http://") && !value.startsWith("https://")) {
      value = "http://" + value;
    }
    return value;
  }

  private String appendToDirective(String csp, String directive, String addition) {
    String[] segments = csp.split(";");
    boolean modified = false;
    for (int i = 0; i < segments.length; i++) {
      String part = segments[i].trim();
      if (part.isEmpty()) {
        continue;
      }
      if (part.startsWith(directive)) {
        if (!part.contains(addition)) {
          part = part + " " + addition;
        }
        segments[i] = part;
        modified = true;
      } else {
        segments[i] = part;
      }
    }
    StringBuilder rebuilt = new StringBuilder();
    for (String part : segments) {
      if (part == null || part.isEmpty()) {
        continue;
      }
      if (rebuilt.length() > 0) {
        rebuilt.append("; ");
      }
      rebuilt.append(part);
    }
    if (!modified) {
      if (rebuilt.length() > 0) {
        rebuilt.append("; ");
      }
      rebuilt.append(directive).append(' ').append(addition);
    }
    return rebuilt.toString();
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

