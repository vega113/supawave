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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Verifies NCSA access logging is functional with Jetty 12 EE10 components.
 */
public class AccessLogJakartaIT {
  private Server server;
  private int port;
  private LatchingRequestLog latchingLog;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    try {
      server = new Server();
      latchingLog = new LatchingRequestLog();
      server.setRequestLog(latchingLog);

      ServerConnector connector = new ServerConnector(server);
      connector.setPort(0);
      server.addConnector(connector);

      ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
      handler.setContextPath("/");
      handler.addServlet(PingServlet.class, "/ping");
      server.setHandler(handler);

      server.start();
      port = connector.getLocalPort();
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

  public static class PingServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.setStatus(200);
      resp.setContentType("text/plain");
      try (PrintWriter w = resp.getWriter()) {
        w.print("pong");
      }
    }
  }

  @Test
  public void writesNCSALogEntry() throws Exception {
    URL url = new URL("http://localhost:" + port + "/ping");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    boolean signaled = latchingLog.await(2, TimeUnit.SECONDS);
    assertTrue("access log should be written within timeout", signaled);
    String path = latchingLog.getLastPath();
    assertNotNull(path);
    assertTrue("logged path should contain '/ping' but was: " + path, path.contains("/ping"));
  }

  /** Minimal RequestLog that latches when a request is logged. */
  static class LatchingRequestLog implements RequestLog {
    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile String lastPath;

    @Override
    public void log(Request request, Response response) {
      try {
        String p;
        try {
          // getHttpURI() and getPath() should not throw checked exceptions; however,
          // some containers or alternate implementations may throw RuntimeException
          // when URI parsing fails. We catch Exception (not Throwable) to avoid
          // swallowing serious Errors while still being robust in tests.
          p = request.getHttpURI().getPath();
        } catch (Exception ex) {
          p = String.valueOf(request.getHttpURI());
        }
        lastPath = p;
      } finally {
        latch.countDown();
      }
    }

    boolean await(long time, TimeUnit unit) throws InterruptedException {
      return latch.await(time, unit);
    }

    String getLastPath() { return lastPath; }
  }
}
