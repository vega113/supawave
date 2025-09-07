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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Verifies NCSA access logging is functional with Jetty 12 EE10 components.
 */
public class AccessLogJakartaIT {
  private Server server;
  private int port;
  private File tmpDir;
  private File accessFile;

  @Before
  public void start() throws Exception {
    try {
      tmpDir = Files.createTempDirectory("wave-logs-").toFile();
      accessFile = new File(tmpDir, "access.yyyy_mm_dd.log");

      server = new Server();
      RequestLogWriter logWriter = new RequestLogWriter(accessFile.getPath());
      logWriter.setAppend(true);
      logWriter.setRetainDays(1);
      server.setRequestLog(new CustomRequestLog(logWriter, CustomRequestLog.NCSA_FORMAT));

      ServerConnector connector = new ServerConnector(server);
      connector.setPort(0);
      server.addConnector(connector);

      ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
      handler.setContextPath("/");
      handler.addServlet(PingServlet.class, "/ping");
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
    try {
      if (accessFile != null && accessFile.exists()) {
        accessFile.delete();
      }
      if (tmpDir != null && tmpDir.exists()) {
        java.nio.file.Path root = tmpDir.toPath();
        java.nio.file.Files.walk(root)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(p -> {
              try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignore) {}
            });
      }
    } catch (Throwable ignore) {
      // Best-effort cleanup
    }
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
    // Wait until the access log writer flushes by polling with a bounded timeout
    long deadline = System.nanoTime() + 10_000_000_000L; // 10s
    boolean found = false;
    Exception lastReadError = null;
    while (System.nanoTime() < deadline) {
      if (accessFile.exists()) {
        try {
          String content = Files.readString(accessFile.toPath());
          if (content.contains("/ping")) { found = true; break; }
        } catch (Exception ex) {
          // File may not be readable yet; record and retry
          lastReadError = ex;
        }
      }
      try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
    }
    if (!found) {
      String msg = "access log should contain '/ping' within timeout" +
          (lastReadError != null ? "; last read error: " + lastReadError.getClass().getSimpleName() + ": " + lastReadError.getMessage() : "");
      fail(msg);
    }
  }
}
