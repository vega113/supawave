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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;

import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.waveserver.MarkBlipReadHelper;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Marks a single blip as read for the authenticated user. Implements the J2CL
 * server seam called by {@code J2clSearchGateway.markBlipRead}; closes #1056
 * (R-4.4 supplement-op write path deferred from F-2.S5).
 *
 * <p>Request: {@code POST /j2cl/mark-blip-read} with JSON body
 * {@code {"waveId":"<id>","blipId":"<id>"[,"waveletId":"<id>"]}}. {@code waveletId}
 * defaults to the canonical conversation root for the given wave id when
 * omitted.
 *
 * <p>Response (200 OK): {@code {"ok":true,"unreadCount":N,"alreadyRead":B}}
 * where {@code alreadyRead} reflects whether the supplement was already in the
 * read state for that blip (no UDW delta submitted) — informational, not
 * load-bearing for the client (the new {@code unreadCount} converges
 * regardless).
 *
 * <p>Status codes:
 * <ul>
 *   <li>403 — unauthenticated session.</li>
 *   <li>400 — malformed JSON body, missing/empty fields, invalid wave/wavelet id.</li>
 *   <li>404 — unknown wave/wavelet/blip OR access denied (collapsed). Mirrors the
 *       semantic of {@link SelectedWaveReadStateServlet} so existence cannot be
 *       probed by non-participants.</li>
 *   <li>500 — backend wavelet load / submit failure.</li>
 * </ul>
 *
 * <p>Idempotent at the supplement layer
 * ({@link org.waveprotocol.wave.model.supplement.SupplementedWaveImpl#markAsRead}
 * is a no-op when the blip is already read), so client-side de-dupe is
 * defence-in-depth not load-bearing.
 *
 * <p><b>CSRF posture:</b> the session cookie carries {@code SameSite=Lax}
 * (configured in {@code ServerModule#sessionHandler}), so a cross-site
 * form POST cannot ride the cookie. The endpoint trusts the session +
 * SameSite default; no explicit XSRF token is required, matching the
 * stance of the read-state, fragments, and submit endpoints.
 */
@SuppressWarnings("serial")
public final class MarkBlipReadServlet extends HttpServlet {
  private static final Log LOG = Log.get(MarkBlipReadServlet.class);

  private static final int MAX_BODY_BYTES = 4 * 1024;

  private final SessionManager sessionManager;
  private final MarkBlipReadHelper helper;

  @Inject
  public MarkBlipReadServlet(SessionManager sessionManager, MarkBlipReadHelper helper) {
    this.sessionManager = sessionManager;
    this.helper = helper;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Per-user mutation: never cache.
    resp.setHeader("Cache-Control", "no-store");

    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    // Cap the JSON body so a misbehaving client can't OOM us with multi-MB
    // inputs. The expected body is ~200 bytes of strings; 4 KB is generous.
    // Read up to MAX_BODY_BYTES + 1 chars to detect oversize before parsing —
    // a single hostile line without newlines must NOT buffer unbounded.
    int contentLength = req.getContentLength();
    if (contentLength > MAX_BODY_BYTES) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    String body;
    try (BufferedReader reader = req.getReader()) {
      char[] buf = new char[MAX_BODY_BYTES + 1];
      int total = 0;
      while (total <= MAX_BODY_BYTES) {
        int read = reader.read(buf, total, buf.length - total);
        if (read < 0) {
          break;
        }
        total += read;
      }
      if (total > MAX_BODY_BYTES) {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      body = new String(buf, 0, total);
    } catch (IOException e) {
      LOG.info("mark-blip-read: failed to read body");
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    if (body == null || body.isEmpty()) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    JsonObject root;
    try {
      JsonElement parsed = JsonParser.parseString(body);
      if (parsed == null || !parsed.isJsonObject()) {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
      root = parsed.getAsJsonObject();
    } catch (JsonSyntaxException e) {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    String rawWaveId = readString(root, "waveId");
    String rawBlipId = readString(root, "blipId");
    String rawWaveletId = readString(root, "waveletId");
    if (rawWaveId == null || rawWaveId.isEmpty()
        || rawBlipId == null || rawBlipId.isEmpty()) {
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

    WaveletId waveletId;
    if (rawWaveletId == null || rawWaveletId.isEmpty()) {
      // Default to the canonical conv+root wavelet on the same domain as the
      // wave id. Mirrors the J2CL read surface contract documented in
      // J2clSearchGateway#defaultWaveletId.
      waveletId = WaveletId.of(waveId.getDomain(), IdUtil.CONVERSATION_ROOT_WAVELET);
    } else {
      try {
        waveletId = ModernIdSerialiser.INSTANCE.deserialiseWaveletId(rawWaveletId);
      } catch (InvalidIdException e) {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return;
      }
    }

    MarkBlipReadHelper.Result result;
    try {
      result = helper.markBlipRead(user, waveId, waveletId, rawBlipId);
    } catch (RuntimeException e) {
      LOG.warning("mark-blip-read: unexpected failure for wave " + rawWaveId, e);
      resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    switch (result.getOutcome()) {
      case OK:
        writeOk(resp, rawWaveId, result.getUnreadCountAfter(), false);
        return;
      case ALREADY_READ:
        writeOk(resp, rawWaveId, result.getUnreadCountAfter(), true);
        return;
      case BAD_REQUEST:
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return;
      case NOT_FOUND:
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      case INTERNAL_ERROR:
      default:
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return;
    }
  }

  private static String readString(JsonObject obj, String key) {
    if (obj == null || !obj.has(key)) {
      return null;
    }
    JsonElement value = obj.get(key);
    if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
        || !value.getAsJsonPrimitive().isString()) {
      return null;
    }
    return value.getAsString();
  }

  private static void writeOk(
      HttpServletResponse resp, String waveId, int unreadCount, boolean alreadyRead)
      throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("application/json; charset=utf-8");
    StringBuilder body = new StringBuilder(96);
    body.append("{\"ok\":true,\"waveId\":\"")
        .append(escapeJson(waveId))
        .append("\",\"unreadCount\":")
        .append(Math.max(0, unreadCount))
        .append(",\"alreadyRead\":")
        .append(alreadyRead)
        .append('}');
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
