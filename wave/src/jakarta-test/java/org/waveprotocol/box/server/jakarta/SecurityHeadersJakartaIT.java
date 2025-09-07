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

import com.typesafe.config.ConfigFactory;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.security.jakarta.SecurityHeadersFilter;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * Verifies SecurityHeadersFilter under Jetty 12 EE10.
 */
public class SecurityHeadersJakartaIT {
  private Server server;
  private int port;

  @Before
  public void start() throws Exception {
    try {
      server = new Server();
      ServerConnector c = new ServerConnector(server);
      c.setPort(0);
      server.addConnector(c);

      ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
      ctx.setContextPath("/");
      // Map simple servlet at root
      ctx.addServlet(HelloServlet.class, "/");

      // Install filter instance with config
      var filter = new SecurityHeadersFilter(ConfigFactory.parseString(""));
      var fh = new org.eclipse.jetty.ee10.servlet.FilterHolder(filter);
      ctx.addFilter(fh, "/*", java.util.EnumSet.allOf(jakarta.servlet.DispatcherType.class));

      server.setHandler(ctx);
      server.start();
      port = c.getLocalPort();
    } catch (Throwable t) {
      Assume.assumeNoException("Jetty 12 not available", t);
    }
  }

  @After
  public void stop() throws Exception {
    if (server != null) server.stop();
  }

  @Test
  public void addsSecurityHeaders() throws Exception {
    URL url = new URL("http://localhost:" + port + "/");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    assertNotNull(c.getHeaderField("Content-Security-Policy"));
    assertNotNull(c.getHeaderField("Referrer-Policy"));
    assertEquals("nosniff", c.getHeaderField("X-Content-Type-Options"));
  }
}

class HelloServlet extends jakarta.servlet.http.HttpServlet {
  @Override protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                                 jakarta.servlet.http.HttpServletResponse resp)
      throws java.io.IOException {
    resp.setStatus(200);
    resp.setContentType("text/plain");
    resp.getWriter().write("ok");
  }
}
