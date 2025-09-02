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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.server.persistence.AccountStore;

import javax.servlet.http.HttpSession;

import static org.junit.Assert.*;

/**
 * Verifies the feature flag plumbing for Jetty 12 session lookup code path.
 * When enabled, the method should handle reflection attempts gracefully and
 * return null if no session is found.
 */
public class SessionLookupFlagTest {

  @Test
  public void returnsNullGracefully_whenFlagEnabled() throws Exception {
    Config cfg = ConfigFactory.parseString("experimental.jetty12_session_lookup = true");
    AccountStore store = Mockito.mock(AccountStore.class);
    // Use reflection to avoid compile-time dependency on Jetty 12 in this test
    final Class<?> shClass;
    try {
      shClass = Class.forName("org.eclipse.jetty.session.SessionHandler");
    } catch (ClassNotFoundException cnfe) {
      org.junit.Assume.assumeTrue("Jetty 12 SessionHandler not present; skipping", false);
      return;
    }
    Object handler = shClass.getConstructor().newInstance();

    // Reflective construction of Jakarta override SessionManagerImpl
    Class<?> smClass = Class.forName("org.waveprotocol.box.server.authentication.SessionManagerImpl");
    Object sm = smClass.getConstructor(
        org.waveprotocol.box.server.persistence.AccountStore.class,
        shClass,
        com.typesafe.config.Config.class
    ).newInstance(store, handler, cfg);

    HttpSession s = (HttpSession) smClass.getMethod("getSessionFromToken", String.class)
        .invoke(sm, "abc.node1");
    assertNull(s);
  }
}
