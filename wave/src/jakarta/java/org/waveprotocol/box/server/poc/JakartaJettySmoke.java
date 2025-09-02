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
package org.waveprotocol.box.server.poc;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

import java.io.IOException;

/**
 * Minimal Jetty 12 (EE10/Jakarta) POC server.
 * Listens on :9899 and serves GET /poc/hello with a small text response.
 */
public final class JakartaJettySmoke {
  public static void main(String[] args) throws Exception {
    int port = 9899;
    Server server = new Server(port);

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    context.setContextPath("/");

    // Simple servlet registered programmatically
    ServletHolder hello = new ServletHolder("hello", new HttpServlet() {
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(200);
        resp.setContentType("text/plain; charset=UTF-8");
        resp.getWriter().write("hello from jetty12/jakarta POC\n");
      }
    });
    context.addServlet(hello, "/poc/hello");

    server.setHandler(context);
    server.start();
    System.out.println("[JakartaJettySmoke] listening on http://localhost:" + port + "/poc/hello");
    server.join();
  }
}
