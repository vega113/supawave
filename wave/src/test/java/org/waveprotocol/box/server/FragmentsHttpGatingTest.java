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
package org.waveprotocol.box.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import javax.servlet.http.HttpSession;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.rpc.ServerRpcProvider;
import org.waveprotocol.wave.model.util.Pair;
import org.waveprotocol.wave.model.wave.ParticipantId;

/** Tests gating behavior for the HTTP /fragments endpoint. */
public final class FragmentsHttpGatingTest {

  private static ServerRpcProvider newProvider() {
    InetSocketAddress[] addrs = new InetSocketAddress[] { new InetSocketAddress("127.0.0.1", 0) };
    String[] bases = new String[] { "./war" };
    SessionManager sm = new SessionManager() {
      @Override
      public ParticipantId getLoggedInUser(HttpSession session) {
        return null;
      }

      @Override
      public AccountData getLoggedInAccount(HttpSession session) {
        return null;
      }

      @Override
      public void setLoggedInUser(HttpSession session, ParticipantId id) {
      }

      @Override
      public void logout(HttpSession session) {
      }

      @Override
      public String getLoginUrl(String redirect) {
        return "/auth/signin";
      }

      @Override
      public HttpSession getSessionFromToken(String token) {
        return null;
      }
    };
    return new ServerRpcProvider(addrs, bases, sm, new SessionHandler(), "_sessions",
        /*sslEnabled=*/false, "", "", Executors.newSingleThreadExecutor());
  }

  @SuppressWarnings("unchecked")
  private static List<Pair<String, ServletHolder>> getRegistry(ServerRpcProvider p)
      throws Exception {
    Field f = ServerRpcProvider.class.getDeclaredField("servletRegistry");
    f.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<Pair<String, ServletHolder>> r = (List<Pair<String, ServletHolder>>) f.get(p);
    return r;
  }

  private static void callInitializeServlets(ServerRpcProvider p, Config cfg) throws Exception {
    Method m = ServerMain.class.getDeclaredMethod(
        "initializeServlets", ServerRpcProvider.class, Config.class);
    m.setAccessible(true);
    m.invoke(null, p, cfg);
  }

  @Test
  public void fragmentsServletDisabledByDefault() throws Exception {
    ServerRpcProvider p = newProvider();
    Config cfg = ConfigFactory.parseString(
        "core.gadget_server_hostname=\"localhost\", core.gadget_server_port=80, core.enable_profiling=false");
    callInitializeServlets(p, cfg);
    boolean found = false;
    for (Pair<String, ServletHolder> e : getRegistry(p)) {
      if ("/fragments/*".equals(e.first)) {
        found = true;
        break;
      }
    }
    assertFalse("/fragments/* should not be registered by default", found);
  }

  @Test
  public void fragmentsServletEnabledWhenFlagTrue() throws Exception {
    ServerRpcProvider p = newProvider();
    Config cfg = ConfigFactory.parseString(
        "server.enableFragmentsHttp=true, core.gadget_server_hostname=\"localhost\", core.gadget_server_port=80, core.enable_profiling=false");
    callInitializeServlets(p, cfg);
    boolean found = false;
    for (Pair<String, ServletHolder> e : getRegistry(p)) {
      if ("/fragments/*".equals(e.first)) {
        found = true;
        break;
      }
    }
    assertTrue("/fragments/* should be registered when server.enableFragmentsHttp=true", found);
  }

  @Test
  public void fragmentsServletDisabledWhenFlagFalse() throws Exception {
    ServerRpcProvider p = newProvider();
    Config cfg = ConfigFactory.parseString(
        "server.enableFragmentsHttp=false, core.gadget_server_hostname=\"localhost\", core.gadget_server_port=80, core.enable_profiling=false");
    callInitializeServlets(p, cfg);
    boolean found = false;
    for (Pair<String, ServletHolder> e : getRegistry(p)) {
      if ("/fragments/*".equals(e.first)) {
        found = true;
        break;
      }
    }
    assertFalse("/fragments/* should not be registered when server.enableFragmentsHttp=false", found);
  }
}
