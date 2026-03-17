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
package org.waveprotocol.box.server.dev;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import org.waveprotocol.wave.util.logging.Log;

/** Dev-only endpoint that stores and returns client-side fragments applier stats for the session. */
public final class ClientApplierStatsServlet extends HttpServlet {
  private static final Log LOG = Log.get(ClientApplierStatsServlet.class);
  private static final String ATTR = "clientApplierStats";

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HttpSession s = req.getSession(true);
    String a = req.getParameter("applied");
    String r = req.getParameter("rejected");
    int applied = parseIntSafe(a, "applied");
    int rejected = parseIntSafe(r, "rejected");
    s.setAttribute(ATTR, new Stats(applied, rejected));
    resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    HttpSession s = req.getSession(false);
    resp.setContentType("application/json;charset=UTF-8");
    PrintWriter w = resp.getWriter();
    Stats st = s != null ? (Stats) s.getAttribute(ATTR) : null;
    if (st == null) {
      w.write("{\"applied\":0,\"rejected\":0}");
    } else {
      w.write("{\"applied\":" + st.applied + ",\"rejected\":" + st.rejected + "}");
    }
  }

  private static int parseIntSafe(String v, String field) {
    int out = 0;
    if (v == null) {
      LOG.fine("client-applier-stats: missing '" + field + "' parameter; defaulting to 0");
    } else {
      try {
        out = Integer.parseInt(v.trim());
      } catch (NumberFormatException e) {
        LOG.warning("client-applier-stats: invalid integer for '" + field + "': '" + v + "'", e);
        out = 0;
      } catch (Throwable t) {
        LOG.warning("client-applier-stats: error parsing '" + field + "': '" + v + "'", t);
        out = 0;
      }
    }
    return out;
  }

  private static final class Stats implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    final int applied; final int rejected;
    Stats(int a, int r) { applied = a; rejected = r; }
  }
}
