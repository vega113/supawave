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

package org.waveprotocol.box.server.security.jakarta;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import javax.inject.Singleton;

import org.waveprotocol.wave.util.logging.Log;

/**
 * Jakarta variant: Adds long-lived caching headers to static assets.
 */
@Singleton
public final class StaticCacheFilter implements Filter {
  private static final Log LOG = Log.get(StaticCacheFilter.class);
  private static final String CACHE_VALUE = "public, max-age=31536000, immutable";

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // No initialization necessary
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (response instanceof HttpServletResponse && request instanceof HttpServletRequest) {
      HttpServletRequest req = (HttpServletRequest) request;
      HttpServletResponse resp = (HttpServletResponse) response;
      if (!resp.isCommitted()) { // keep for safety to avoid setting after commit
        String m = req.getMethod();
        if ("GET".equalsIgnoreCase(m) || "HEAD".equalsIgnoreCase(m)) {
          resp.setHeader("Cache-Control", CACHE_VALUE);
          LOG.fine(
              "StaticCacheFilter (jakarta) set Cache-Control on path=" + req.getRequestURI());
        }
      }
    }
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {
    // No resources to clean up
  }
}
