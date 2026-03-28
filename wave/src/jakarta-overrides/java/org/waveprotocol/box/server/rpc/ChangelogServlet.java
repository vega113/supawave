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
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

@Singleton
public final class ChangelogServlet extends HttpServlet {
  private final ChangelogProvider changelogProvider;

  @Inject
  public ChangelogServlet(ChangelogProvider changelogProvider) {
    this.changelogProvider = changelogProvider;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String pathInfo = req.getPathInfo();
    if (isLatestPath(pathInfo)) {
      JSONObject latestEntry = changelogProvider.getLatestEntry();
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("application/json; charset=UTF-8");
      resp.setHeader("Cache-Control", "no-cache");
      resp.getWriter().write(latestEntry != null ? latestEntry.toString() : "{}");
    } else if (isApiPath(pathInfo)) {
      JSONArray entries = changelogProvider.getEntries();
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("application/json; charset=UTF-8");
      resp.setHeader("Cache-Control", "public, max-age=300");
      resp.getWriter().write(entries.toString());
    } else if (isCurrentPath(pathInfo)) {
      JSONObject currentEntry = changelogProvider.getCurrentReleaseEntry();
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("application/json; charset=UTF-8");
      resp.setHeader("Cache-Control", "no-cache");
      resp.getWriter().write(currentEntry != null ? currentEntry.toString() : "{}");
    } else if (isHtmlPath(pathInfo)) {
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("text/html; charset=UTF-8");
      resp.getWriter().write(HtmlRenderer.renderChangelogPage(changelogProvider.getEntries()));
    } else {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private static boolean isLatestPath(String pathInfo) {
    return "/latest".equals(pathInfo);
  }

  private static boolean isApiPath(String pathInfo) {
    return "/api".equals(pathInfo);
  }

  private static boolean isCurrentPath(String pathInfo) {
    return "/current".equals(pathInfo);
  }

  private static boolean isHtmlPath(String pathInfo) {
    return pathInfo == null || "/".equals(pathInfo);
  }
}
