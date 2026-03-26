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

import com.google.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Jakarta servlet serving server-rendered legal/informational pages:
 * Terms of Service (/terms) and Privacy Policy (/privacy).
 * The /contact path is served by {@link ContactServlet}.
 */
@Singleton
public final class LegalServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String path = req.getServletPath();
    String html;
    switch (path) {
      case "/terms":
        html = HtmlRenderer.renderTermsPage();
        break;
      case "/privacy":
        html = HtmlRenderer.renderPrivacyPage();
        break;
      default:
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
    }
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/html; charset=UTF-8");
    resp.getWriter().write(html);
  }
}
