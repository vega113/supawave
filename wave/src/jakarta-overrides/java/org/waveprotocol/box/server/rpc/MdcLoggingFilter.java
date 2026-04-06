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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

import com.google.inject.Singleton;

import org.slf4j.MDC;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Jakarta servlet filter that populates SLF4J MDC with per-request context
 * fields (participantId, sessionId) so that JSON log entries emitted to
 * logs/wave-json.log carry rich labels for Grafana/Loki ingestion.
 *
 * <p>The filter clears MDC fields in a {@code finally} block to avoid leaking
 * context across requests on pooled threads.
 */
@Singleton
public class MdcLoggingFilter implements Filter {

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      if (request instanceof HttpServletRequest) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
          Object participantAttr = session.getAttribute(SessionManager.USER_FIELD);
          if (participantAttr instanceof ParticipantId) {
            ParticipantId participant = (ParticipantId) participantAttr;
            MDC.put("participantId", participant.getAddress());
          }
          String sessionId = session.getId();
          if (sessionId != null && sessionId.length() > 8) {
            sessionId = sessionId.substring(0, 8);
          }
          MDC.put("sessionId", sessionId);
        }
      }
      chain.doFilter(request, response);
    } finally {
      MDC.remove("participantId");
      MDC.remove("sessionId");
      MDC.remove("waveId");
      MDC.remove("waveletId");
    }
  }

  @Override
  public void destroy() {}
}
