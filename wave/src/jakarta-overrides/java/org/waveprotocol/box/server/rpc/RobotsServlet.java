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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Serves {@code /robots.txt} for web crawlers. Allows crawling of public wave
 * pages ({@code /wave/}) and static assets while disallowing authenticated
 * endpoints (auth, admin, API, etc.) and the sitemap location.
 */
@Singleton
public final class RobotsServlet extends HttpServlet {

  private final String siteUrl;

  @Inject
  public RobotsServlet(Config config) {
    this.siteUrl = resolveSiteUrl(config);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/plain; charset=UTF-8");
    resp.setHeader("Cache-Control", "public, max-age=86400");

    StringBuilder sb = new StringBuilder(512);
    sb.append("User-agent: *\n");
    sb.append("Allow: /wave/\n");
    sb.append("Allow: /static/\n");
    sb.append("Allow: /terms\n");
    sb.append("Allow: /privacy\n");
    sb.append("Allow: /contact\n");
    sb.append("Disallow: /auth/\n");
    sb.append("Disallow: /admin\n");
    sb.append("Disallow: /fetch/\n");
    sb.append("Disallow: /search/\n");
    sb.append("Disallow: /robot/\n");
    sb.append("Disallow: /gadget/\n");
    sb.append("Disallow: /attachment/\n");
    sb.append("Disallow: /thumbnail/\n");
    sb.append("Disallow: /profile/\n");
    sb.append("Disallow: /userprofile/\n");
    sb.append("Disallow: /folder/\n");
    sb.append("Disallow: /fragments\n");
    sb.append("Disallow: /history/\n");
    sb.append("Disallow: /waveref/\n");
    sb.append("\n");
    sb.append("Sitemap: ").append(siteUrl).append("/sitemap.xml\n");

    resp.getWriter().write(sb.toString());
  }

  static String resolveSiteUrl(Config config) {
    // Prefer an explicit public URL if configured.
    if (config.hasPath("core.public_url")) {
      String url = config.getString("core.public_url").trim();
      if (!url.isEmpty()) {
        // Strip trailing slash for consistency.
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
      }
    }
    // Fall back to the first HTTP frontend address.
    try {
      String addr = config.getStringList("core.http_frontend_addresses").get(0);
      boolean ssl = false;
      try { ssl = config.getBoolean("security.enable_ssl"); } catch (Exception ignored) {}
      String scheme = ssl ? "https" : "http";
      return scheme + "://" + addr;
    } catch (Exception e) {
      return "https://supawave.ai";
    }
  }
}
