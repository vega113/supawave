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
    // Lookup headers case-insensitively for reliability across JDKs/containers
    java.util.Map<String, java.util.List<String>> headers = c.getHeaderFields();
    java.util.function.Function<String,String> get = (name) -> {
      for (java.util.Map.Entry<String, java.util.List<String>> e : headers.entrySet()) {
        if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
          return (e.getValue() != null && !e.getValue().isEmpty()) ? e.getValue().get(0) : null;
        }
      }
      return null;
    };
    assertNotNull(get.apply("Content-Security-Policy"));
    assertNotNull(get.apply("Referrer-Policy"));
    String xcto = get.apply("X-Content-Type-Options");
    assertNotNull(xcto);
    assertTrue("X-Content-Type-Options should be nosniff, was: " + xcto, "nosniff".equalsIgnoreCase(xcto));
  }

  @Test
  public void usesCustomConfiguration() throws Exception {
    Server srv = null;
    int p;
    try {
      srv = new Server();
      ServerConnector c = new ServerConnector(srv);
      c.setPort(0);
      srv.addConnector(c);
      ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
      ctx.setContextPath("/");
      ctx.addServlet(HelloServlet.class, "/");
      var cfg = ConfigFactory.parseString(
          "security.csp=\"default-src 'none'\"\n" +
          "security.referrer_policy=\"no-referrer\"\n" +
          "security.x_content_type_options=\"nosniff\"\n");
      var filter = new SecurityHeadersFilter(cfg);
      ctx.addFilter(new org.eclipse.jetty.ee10.servlet.FilterHolder(filter), "/*",
          java.util.EnumSet.allOf(jakarta.servlet.DispatcherType.class));
      srv.setHandler(ctx);
      srv.start();
      p = c.getLocalPort();
    } catch (Throwable t) {
      Assume.assumeNoException("Jetty 12 not available", t);
      return;
    }

    try {
      URL url = new URL("http://localhost:" + p + "/");
      HttpURLConnection c2 = (HttpURLConnection) url.openConnection();
      assertEquals(200, c2.getResponseCode());
      java.util.Map<String, java.util.List<String>> headers = c2.getHeaderFields();
      String csp = null, ref = null;
      for (var e : headers.entrySet()) {
        if (e.getKey() == null) continue;
        if (e.getKey().equalsIgnoreCase("Content-Security-Policy")) csp = e.getValue().get(0);
        if (e.getKey().equalsIgnoreCase("Referrer-Policy")) ref = e.getValue().get(0);
      }
      assertEquals("default-src 'none'", csp);
      assertEquals("no-referrer", ref);
    } finally {
      try { srv.stop(); } catch (Exception ignore) {}
    }
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
