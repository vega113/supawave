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
    TestSupport.assumeJettyEe10PresentOrSkip();
    try {
      server = new Server();
      ServerConnector c = new ServerConnector(server);
      c.setPort(0);
      server.addConnector(c);

      ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
      ctx.setContextPath("/");
      // Map simple servlet under an explicit path to avoid container root quirks
      ctx.addServlet(HelloServlet.class, "/hello");

      // Install filter instance with config
      var filter = new SecurityHeadersFilter(ConfigFactory.parseString(""));
      var fh = new org.eclipse.jetty.ee10.servlet.FilterHolder(filter);
      ctx.addFilter(fh, "/*", java.util.EnumSet.allOf(jakarta.servlet.DispatcherType.class));

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
  }

  @Test
  public void addsSecurityHeaders() throws Exception {
    URL url = new URL("http://localhost:" + port + "/hello");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertOk(c, "/hello");
    // Lookup headers case-insensitively for reliability across JDKs/containers
    java.util.Map<String, java.util.List<String>> headers = c.getHeaderFields();
    assertNotNull(getHeader(headers, "Content-Security-Policy"));
    assertNotNull(getHeader(headers, "Referrer-Policy"));
    String xcto = getHeader(headers, "X-Content-Type-Options");
    assertNotNull(xcto);
    assertTrue("X-Content-Type-Options should be nosniff, was: " + xcto, "nosniff".equalsIgnoreCase(xcto));
  }

  @Test
  public void usesCustomConfiguration() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    Server srv = null;
    int p;
    try {
      srv = new Server();
      ServerConnector c = new ServerConnector(srv);
      c.setPort(0);
      srv.addConnector(c);
      ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
      ctx.setContextPath("/");
      ctx.addServlet(HelloServlet.class, "/hello");
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
    } catch (LinkageError e) {
      TestSupport.assumeJettyEe10PresentOrSkip();
      return;
    } catch (Exception e) {
      throw new AssertionError("Failed to start embedded Jetty EE10 server (custom config)", e);
    }

    try {
      URL url = new URL("http://localhost:" + p + "/hello");
      HttpURLConnection c2 = (HttpURLConnection) url.openConnection();
      assertOk(c2, "/hello");
      java.util.Map<String, java.util.List<String>> headers = c2.getHeaderFields();
      String csp = getHeader(headers, "Content-Security-Policy");
      String ref = getHeader(headers, "Referrer-Policy");
      String xcto = getHeader(headers, "X-Content-Type-Options");
      assertEquals("default-src 'none'", csp);
      assertEquals("no-referrer", ref);
      assertNotNull("X-Content-Type-Options header expected", xcto);
      assertTrue("X-Content-Type-Options should be nosniff, was: " + xcto, "nosniff".equalsIgnoreCase(xcto));
    } finally {
      try { srv.stop(); } catch (Exception ignore) {}
    }
  }

  // Helper: case-insensitive header lookup (first value)
  private static String getHeader(java.util.Map<String, java.util.List<String>> headers, String name) {
    if (headers == null) {
      throw new AssertionError("Response headers map is null while looking up '" + name + "'.");
    }
    for (java.util.Map.Entry<String, java.util.List<String>> e : headers.entrySet()) {
      if (e.getKey() == null) continue; // status line
      if (e.getKey().equalsIgnoreCase(name)) {
        java.util.List<String> vals = e.getValue();
        if (vals == null || vals.isEmpty()) {
          // Unexpected: header key present but value list missing
          System.err.println("Header '" + name + "' present with no values. All headers:\n" + dumpHeaders(headers));
          return null;
        }
        return vals.get(0);
      }
    }
    // Not found: log available headers for diagnostics and return null so callers can assert
    System.err.println("Header '" + name + "' not found. All headers:\n" + dumpHeaders(headers));
    return null;
  }

  private static String dumpHeaders(java.util.Map<String, java.util.List<String>> headers) {
    StringBuilder sb = new StringBuilder();
    if (headers == null) return "<null>";
    for (var e : headers.entrySet()) {
      sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
    }
    return sb.toString();
  }

  private static void assertOk(HttpURLConnection c, String path) throws java.io.IOException {
    int code = c.getResponseCode();
    if (code != 200) {
      StringBuilder hdrDump = new StringBuilder();
      for (var e : c.getHeaderFields().entrySet()) {
        hdrDump.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
      }
      String body;
      try {
        var s = (c.getErrorStream() != null) ? c.getErrorStream() : c.getInputStream();
        body = new String(s.readAllBytes());
      } catch (Exception ex) {
        body = "<unavailable>";
      }
      fail("Expected HTTP 200 from " + path + ", got " + code + "\nHeaders:\n" + hdrDump + "Body:\n" + body);
    }
  }
  // Public nested servlet for reliable reflective instantiation
  public static class HelloServlet extends jakarta.servlet.http.HttpServlet {
    public HelloServlet() {}
    @Override protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse resp)
        throws java.io.IOException {
      resp.setStatus(200);
      resp.setContentType("text/plain");
      resp.getWriter().write("ok");
    }
  }
}
