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
package org.waveprotocol.box.server.jakarta;

import org.junit.Assume;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpSession;

import static org.junit.Assert.*;

/**
 * Tiny smoke test that exercises the compile-time stub for
 * org.eclipse.jetty.server.session.SessionHandler when building with
 * -PjettyFamily=jakarta. Uses reflection so the test can skip gracefully when
 * running against the javax/Jetty 9 build.
 */
public class SessionHandlerStubTest {

  @Test
  public void registerAndLookupSession_whenStubAvailable() throws Exception {
    final Class<?> handlerClass;
    try {
      handlerClass = Class.forName("org.eclipse.jetty.server.session.SessionHandler");
    } catch (ClassNotFoundException cnfe) {
      // Skip gracefully when running in environments without the stub/class available
      Assume.assumeTrue("SessionHandler class not present; skipping", false);
      return;
    }
    // If the stub-only method is not found, skip (likely running against javax/Jetty 9)
    try {
      handlerClass.getMethod("registerSession", String.class, HttpSession.class);
    } catch (NoSuchMethodException e) {
      Assume.assumeTrue("registerSession method not present; skipping", false);
      return;
    }

    Object handler = handlerClass.getConstructor().newInstance();
    HttpSession session = Mockito.mock(HttpSession.class);

    // Call registerSession(id, session) via reflection
    handlerClass.getMethod("registerSession", String.class, HttpSession.class)
        .invoke(handler, "abc", session);

    // Lookup and unwrap
    Object jettySession = handlerClass.getMethod("getSession", String.class)
        .invoke(handler, "abc");
    assertNotNull(jettySession);

    Object unwrapped = jettySession.getClass().getMethod("getSession").invoke(jettySession);
    assertSame("Unwrapped HttpSession should be the same mock", session, unwrapped);
  }
}
