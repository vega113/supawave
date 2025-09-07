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
import org.eclipse.jetty.server.*;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * Verifies ForwardedRequestCustomizer behavior under Jetty 12 (EE10) when enabled.
 */
public class ForwardedHeadersJakartaIT {
  private Server server;
  private int port;

  @Before
  public void start() throws Exception {
    try {
      server = new Server();

      HttpConfiguration httpConfig = new HttpConfiguration();
      // Enable forwarded-headers processing (mirrors legacy toggle)
      httpConfig.addCustomizer(new ForwardedRequestCustomizer());

      ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
      connector.setPort(0);
      server.addConnector(connector);

      ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
      handler.setContextPath("/");
      handler.addServlet(WhoAmIServlet.class, "/whoami");
      server.setHandler(handler);

      server.start();
      port = connector.getLocalPort();
    } catch (Throwable t) {
      Assume.assumeNoException("Jetty 12 EE10 not available", t);
    }
  }

  @After
  public void stop() throws Exception {
    if (server != null) server.stop();
  }

  public static class WhoAmIServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.setStatus(200);
      resp.setContentType("text/plain");
      try (PrintWriter w = resp.getWriter()) {
        w.println("scheme=" + req.getScheme());
        w.println("remote=" + req.getRemoteAddr());
      }
    }
  }

  @Test
  public void forwardedHeadersAffectSchemeAndRemote() throws Exception {
    URL url = new URL("http://localhost:" + port + "/whoami");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    c.setRequestProperty("X-Forwarded-Proto", "https");
    c.setRequestProperty("X-Forwarded-For", "203.0.113.9");
    assertEquals(200, c.getResponseCode());
    String body = new String(c.getInputStream().readAllBytes());
    assertTrue("scheme should be https (sent X-Forwarded-Proto=https). Body=" + body,
        body.contains("scheme=https"));
    assertTrue("remote should be 203.0.113.9 (sent X-Forwarded-For=203.0.113.9). Body=" + body,
        body.contains("remote=203.0.113.9"));
  }

  @Test
  public void noForwardedHeadersUsesActualConnection() throws Exception {
    URL url = new URL("http://localhost:" + port + "/whoami");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    String body = new String(c.getInputStream().readAllBytes());
    // Without headers, scheme should reflect the actual request
    assertTrue("scheme should be http when no headers: " + body, body.contains("scheme=http"));
  }

  @Test
  public void malformedForwardedHeadersAreIgnored() throws Exception {
    URL url = new URL("http://localhost:" + port + "/whoami");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    c.setRequestProperty("X-Forwarded-Proto", "!!!");
    c.setRequestProperty("X-Forwarded-For", "not_an_ip");
    assertEquals(200, c.getResponseCode());
    String body = new String(c.getInputStream().readAllBytes());
    // Jetty should fall back to the original connection properties.
    assertTrue("scheme should be http when malformed X-Forwarded-Proto. Body=" + body,
        body.contains("scheme=http"));
    assertFalse("remote should not equal the malformed header literal. Body=" + body,
        body.contains("remote=not_an_ip"));
    // Remote should match a loopback address (IPv4 or IPv6)
    boolean loopback = body.contains("remote=127.0.0.1") || body.contains("remote=::1");
    assertTrue("remote should be loopback on direct connection. Body=" + body, loopback);
  }
}
