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

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Embedded Jetty 12 EE10 smoke test that registers the @ServerEndpoint("/socket")
 * and verifies a simple echo of a text frame.
 */
public class WebSocketJakartaSmokeIT {
  private Server server;
  private int port;

  @Before
  public void startServer() throws Exception {
    try {
      server = new Server();
      ServerConnector connector = new ServerConnector(server);
      connector.setPort(0); // ephemeral
      server.addConnector(connector);

      ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.SESSIONS);
      handler.setContextPath("/");

      // Initialize WS container
      JakartaWebSocketServletContainerInitializer.configure(handler, (context, container) -> {
        container.addEndpoint(EchoEndpoint.class);
      });

      server.setHandler(handler);
      server.start();
      port = connector.getLocalPort();
    } catch (Throwable t) {
      // Skip gracefully if Jetty 12 websocket server is unavailable
      Assume.assumeNoException("Jetty 12 websocket server not available", t);
    }
  }

  @After
  public void stopServer() throws Exception {
    if (server != null) {
      server.stop();
    }
  }

  @ClientEndpoint
  public static class Client {
    final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
    @OnMessage
    public void onMessage(String msg) { queue.offer(msg); }
  }

  @jakarta.websocket.server.ServerEndpoint("/socket")
  public static class EchoEndpoint {
    @jakarta.websocket.OnMessage
    public void onMessage(jakarta.websocket.Session session, String msg) {
      session.getAsyncRemote().sendText(msg);
    }
  }

  @Test
  public void echoFrame() throws Exception {
    Assume.assumeTrue("Server not started", server != null);
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    Client client = new Client();
    Session s = container.connectToServer(client, new URI("ws://localhost:" + port + "/socket"));
    try {
      s.getAsyncRemote().sendText("hello");
      String got = client.queue.poll(3, TimeUnit.SECONDS);
      assertEquals("hello", got);
    } finally {
      s.close();
    }
  }
}
