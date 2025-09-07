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

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.security.jakarta.NoCacheFilter;
import org.waveprotocol.box.server.security.jakarta.StaticCacheFilter;

import java.io.File;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Verifies StaticCacheFilter and NoCacheFilter behavior on EE10.
 */
public class CachingFiltersJakartaIT {
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
      // Simple test servlets instead of DefaultServlet+filesystem
      ctx.addServlet(StaticServlet.class, "/static/*");
      ctx.addServlet(WebclientServlet.class, "/webclient/*");

      // Filters mapped per legacy behavior
      ctx.addFilter(new org.eclipse.jetty.ee10.servlet.FilterHolder(new StaticCacheFilter()), "/static/*",
          java.util.EnumSet.allOf(jakarta.servlet.DispatcherType.class));
      ctx.addFilter(new org.eclipse.jetty.ee10.servlet.FilterHolder(new NoCacheFilter()), "/webclient/*",
          java.util.EnumSet.allOf(jakarta.servlet.DispatcherType.class));

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
    // No temporary filesystem resources used by this test
  }

  @Test
  public void staticGetsImmutableCache() throws Exception {
    URL url = new URL("http://localhost:" + port + "/static/test.js");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    int ccCount = 0;
    String cc = null;
    for (var e : c.getHeaderFields().entrySet()) {
      if (e.getKey() != null && e.getKey().equalsIgnoreCase("Cache-Control")) {
        ccCount += (e.getValue() == null) ? 0 : e.getValue().size();
        if (cc == null && e.getValue() != null && !e.getValue().isEmpty()) cc = e.getValue().get(0);
      }
    }
    assertNotNull(cc);
    assertEquals("Expected exactly one Cache-Control header", 1, ccCount);
    assertTrue(cc.contains("immutable"));
  }

  @Test
  public void webclientGetsNoCache() throws Exception {
    URL url = new URL("http://localhost:" + port + "/webclient/app.nocache.js");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    int ccCount = 0;
    String cc = null;
    for (var e : c.getHeaderFields().entrySet()) {
      if (e.getKey() != null && e.getKey().equalsIgnoreCase("Cache-Control")) {
        ccCount += (e.getValue() == null) ? 0 : e.getValue().size();
        if (cc == null && e.getValue() != null && !e.getValue().isEmpty()) cc = e.getValue().get(0);
      }
    }
    assertNotNull(cc);
    assertEquals("Expected exactly one Cache-Control header", 1, ccCount);
    assertTrue(cc.contains("no-cache"));
  }
}

// Minimal servlets for testing
class StaticServlet extends jakarta.servlet.http.HttpServlet {
  @Override protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                                 jakarta.servlet.http.HttpServletResponse resp)
      throws java.io.IOException {
    resp.setStatus(200);
    resp.setContentType("application/javascript");
    resp.getWriter().write("alert('x')");
  }
}

class WebclientServlet extends jakarta.servlet.http.HttpServlet {
  @Override protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                                 jakarta.servlet.http.HttpServletResponse resp)
      throws java.io.IOException {
    resp.setStatus(200);
    resp.setContentType("application/javascript");
    resp.getWriter().write("// nocache");
  }
}
