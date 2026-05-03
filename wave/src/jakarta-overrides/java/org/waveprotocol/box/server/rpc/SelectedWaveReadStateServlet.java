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

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.waveserver.SelectedWaveReadStateHelper;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Serves per-user unread/read state for a single wave.
 *
 * <p>Request: {@code GET /read-state?waveId=<serialised-wave-id>}
 * <p>Response body (200 OK): {@code {"waveId":"...","unreadCount":3,"isRead":false}}
 *
 * <p>Returns 403 for unauthenticated sessions, 400 for missing/malformed
 * {@code waveId}, and 404 for unknown waves OR access-denied — the two cases
 * are intentionally indistinguishable so non-participants cannot probe
 * existence. Introduced for issue #931 as the server seam feeding the J2CL
 * sidecar's selected-wave read-state display.
 */
@SuppressWarnings("serial")
public final class SelectedWaveReadStateServlet extends HttpServlet {
  private static final Log LOG = Log.get(SelectedWaveReadStateServlet.class);

  private final SessionManager sessionManager;
  private final SelectedWaveReadStateHelper helper;

  @Inject
  public SelectedWaveReadStateServlet(
      SessionManager sessionManager, SelectedWaveReadStateHelper helper) {
    this.sessionManager = sessionManager;
    this.helper = helper;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Per-user unread state must never be cached across requests.
    resp.setHeader("Cache-Control", "no-store");

    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String rawWaveId = req.getParameter("waveId");
    if (rawWaveId == null || rawWaveId.isEmpty()) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    WaveId waveId;
    try {
      waveId = ModernIdSerialiser.INSTANCE.deserialiseWaveId(rawWaveId);
    } catch (InvalidIdException e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    SelectedWaveReadStateHelper.Result result;
    try {
      result = helper.computeReadState(user, waveId);
    } catch (RuntimeException e) {
      LOG.warning("read-state: unexpected failure for wave " + rawWaveId, e);
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    if (!result.exists() || !result.accessAllowed()) {
      // Collapse "unknown wave" and "access denied" into a single status with
      // no body so non-participants cannot probe existence.
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    writeJson(resp, rawWaveId, result.getUnreadCount(), result.isRead(), result.getUnreadBlipIds());
  }

  private static void writeJson(
      HttpServletResponse resp,
      String waveId,
      int unreadCount,
      boolean isRead,
      List<String> unreadBlipIds)
      throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("application/json; charset=utf-8");
    StringBuilder body = new StringBuilder(64);
    body.append("{\"waveId\":\"").append(escapeJson(waveId)).append("\",\"unreadCount\":")
        .append(unreadCount).append(",\"isRead\":").append(isRead)
        .append(",\"unreadBlipIds\":[");
    if (unreadBlipIds != null) {
      for (int i = 0; i < unreadBlipIds.size(); i++) {
        if (i > 0) {
          body.append(',');
        }
        body.append('"').append(escapeJson(unreadBlipIds.get(i))).append('"');
      }
    }
    body.append("]}");
    try (var w = resp.getWriter()) {
      w.append(body.toString());
      w.flush();
    }
  }

  private static String escapeJson(String value) {
    StringBuilder out = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"': out.append("\\\""); break;
        case '\\': out.append("\\\\"); break;
        case '\b': out.append("\\b"); break;
        case '\f': out.append("\\f"); break;
        case '\n': out.append("\\n"); break;
        case '\r': out.append("\\r"); break;
        case '\t': out.append("\\t"); break;
        default:
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
      }
    }
    return out.toString();
  }
}
