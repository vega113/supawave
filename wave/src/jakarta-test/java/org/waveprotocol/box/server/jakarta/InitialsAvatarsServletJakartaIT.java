package org.waveprotocol.box.server.jakarta;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.apache.wave.box.server.rpc.InitialsAvatarsServlet;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class InitialsAvatarsServletJakartaIT {
  private Server server;
  private int port;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    server = new Server();
    ServerConnector c = new ServerConnector(server);
    c.setPort(0);
    server.addConnector(c);
    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(new InitialsAvatarsServlet()), "/iniavatars/*");
    server.setHandler(ctx);
    server.start();
    port = c.getLocalPort();
  }

  @After
  public void stop() throws Exception {
    if (server != null) server.stop();
  }

  @Test
  public void returnsDefaultAvatar200() throws Exception {
    URL url = new URL("http://localhost:" + port + "/iniavatars/100x100/user@example.com");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    assertTrue(c.getHeaderField("Content-Type").toLowerCase().contains("image/jpg"));
    byte[] bytes = c.getInputStream().readAllBytes();
    assertTrue("expected some bytes", bytes.length > 100);
  }
}

