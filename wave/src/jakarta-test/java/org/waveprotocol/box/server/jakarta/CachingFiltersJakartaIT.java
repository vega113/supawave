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
    TestSupport.assumeJettyEe10PresentOrSkip();
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
    } catch (LinkageError e) {
      TestSupport.assumeJettyEe10PresentOrSkip();
    } catch (Exception e) {
      throw new AssertionError("Failed to start embedded Jetty EE10 server", e);
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
    assertOk(c, "/static/test.js");
    String cc = header(c, "Cache-Control");
    if (cc == null) fail("Cache-Control header missing for /static/*; response headers:\n" + dumpHeaders(c));
    String lc = cc.toLowerCase();
    assertTrue("Cache-Control should indicate long-lived caching but was: " + cc,
        lc.contains("immutable") || lc.contains("max-age=31536000") || lc.contains("public"));
  }

  @Test
  public void webclientGetsNoCache() throws Exception {
    URL url = new URL("http://localhost:" + port + "/webclient/app.nocache.js");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertOk(c, "/webclient/app.nocache.js");
    String cc = header(c, "Cache-Control");
    if (cc == null) fail("Cache-Control header missing for /webclient/*; response headers:\n" + dumpHeaders(c));
    String lc2 = cc.toLowerCase();
    assertTrue("Cache-Control should disable caching but was: " + cc,
        lc2.contains("no-cache") || lc2.contains("no-store"));
  }

  // Helpers to avoid duplication in header/status handling
  private static String header(HttpURLConnection c, String name) {
    String v = c.getHeaderField(name);
    if (v != null) return v;
    for (var e : c.getHeaderFields().entrySet()) {
      if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isEmpty()) {
        return e.getValue().get(0);
      }
    }
    return null;
  }

  private static String dumpHeaders(HttpURLConnection c) {
    StringBuilder hdrDump = new StringBuilder();
    for (var e : c.getHeaderFields().entrySet()) {
      hdrDump.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
    }
    return hdrDump.toString();
  }

  private static void assertOk(HttpURLConnection c, String path) throws java.io.IOException {
    int code = c.getResponseCode();
    if (code != 200) {
      String body;
      try {
        var s = (c.getErrorStream() != null) ? c.getErrorStream() : c.getInputStream();
        body = new String(s.readAllBytes());
      } catch (Exception ex) {
        body = "<unavailable>";
      }
      fail("Expected HTTP 200 from " + path + ", got " + code + "\nHeaders:\n" + dumpHeaders(c) + "Body:\n" + body);
    }
  }
  // Minimal servlets for testing (public to satisfy Jetty reflective instantiation)
  // Serves a tiny JS payload for cache header testing. The exact content is irrelevant;
  // using a comment avoids accidental popup execution and clarifies intent.
  public static class StaticServlet extends jakarta.servlet.http.HttpServlet {
    public StaticServlet() {}
    @Override protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse resp)
        throws java.io.IOException {
      resp.setStatus(200);
      resp.setContentType("application/javascript");
      resp.getWriter().write("/* static-test */");
    }
  }

  // Serves a tiny JS payload under /webclient for no-cache semantics; content is a comment.
  public static class WebclientServlet extends jakarta.servlet.http.HttpServlet {
    public WebclientServlet() {}
    @Override protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse resp)
        throws java.io.IOException {
      resp.setStatus(200);
      resp.setContentType("application/javascript");
      resp.getWriter().write("/* nocache-test */");
    }
  }
}
