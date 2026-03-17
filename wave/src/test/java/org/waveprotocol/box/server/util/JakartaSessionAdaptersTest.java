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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.junit.Test;

/**
 * Verifies that JakartaSessionAdapters.fromRequest delegates exactly to
 * HttpServletRequest#getSession(boolean) without side-effects.
 */
public final class JakartaSessionAdaptersTest {

  @Test
  public void returnsNullWhenNoSessionAndDoNotCreate() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getSession(false)).thenReturn(null);

    HttpSession s = JakartaSessionAdapters.fromRequest(req, false);

    assertNull(s);
    verify(req, times(1)).getSession(false);
    verify(req, never()).getSession(true);
  }

  @Test
  public void returnsExistingSessionWhenDoNotCreate() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(req.getSession(false)).thenReturn(session);

    HttpSession s = JakartaSessionAdapters.fromRequest(req, false);

    assertSame(session, s);
    verify(req, times(1)).getSession(false);
    verify(req, never()).getSession(true);
  }

  @Test
  public void createsSessionWhenCreateTrue() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(req.getSession(true)).thenReturn(session);

    HttpSession s = JakartaSessionAdapters.fromRequest(req, true);

    assertSame(session, s);
    verify(req, times(1)).getSession(true);
    // Whether getSession(false) is consulted is implementation-specific; ensure at least create path
  }

  @Test
  public void returnsNullWhenRequestNull() {
    assertNull(JakartaSessionAdapters.fromRequest(null, false));
    assertNull(JakartaSessionAdapters.fromRequest(null, true));
  }
}
