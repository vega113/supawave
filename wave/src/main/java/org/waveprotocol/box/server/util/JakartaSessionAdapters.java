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
package org.waveprotocol.box.server.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Minimal adapter for obtaining an HTTP session from a servlet request.
 *
 * Semantics are intentionally identical to {@link HttpServletRequest#getSession(boolean)}:
 * - When {@code create} is {@code false}, this method must not create a session and returns
 *   {@code null} if none exists.
 * - When {@code create} is {@code true}, this method returns the current session if one exists,
 *   or creates and returns a new session otherwise.
 *
 * This indirection exists so future migrations to jakarta.servlet can be isolated behind a
 * single facade without changing call sites or behavior.
 */
public final class JakartaSessionAdapters {
  private JakartaSessionAdapters() {}

  /**
   * Returns the HTTP session associated with the request, with behavior exactly matching
   * {@link HttpServletRequest#getSession(boolean)}. When {@code request} is {@code null},
   * this method returns {@code null} regardless of the {@code create} argument.
   */
  public static HttpSession fromRequest(HttpServletRequest request, boolean create) {
    return (request != null) ? request.getSession(create) : null;
  }
}

