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
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.authentication.JakartaSessionAdapters;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;

@SuppressWarnings("serial")
public final class FetchProfilesServlet extends HttpServlet {
  private static final Log LOG = Log.get(FetchProfilesServlet.class);
  private final SessionManager sessionManager;

  @Inject
  public FetchProfilesServlet(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(JakartaSessionAdapters.fromRequest(req, false));
    if (user == null) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
    // Delegate to existing logic in main servlet via RPC provider utilities if needed.
    // For now, return 200 with empty JSON to keep parity minimal under Jakarta path.
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    try (var w = response.getWriter()) { w.write("{}\n"); w.flush(); }
  }
}
