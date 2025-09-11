package org.waveprotocol.box.server.jakarta;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.rpc.SignOutServlet;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.*;

public class SignOutServletJakartaIT {
  private Server server;
  private int port;
  private SessionManager sessionManager;

  @Before
  public void start() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    sessionManager = Mockito.mock(SessionManager.class);
    server = new Server();
    ServerConnector c = new ServerConnector(server);
    c.setPort(0);
    server.addConnector(c);
    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(new SignOutServlet(sessionManager)), "/auth/signout");
    server.setHandler(ctx);
    server.start();
    port = c.getLocalPort();
  }

  @After
  public void stop() throws Exception {
    if (server != null) server.stop();
  }

  @Test
  public void signout_withRedirectParam_redirects302() throws Exception {
    URL url = new URL("http://localhost:" + port + "/auth/signout?r=%2Fafter");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    c.setInstanceFollowRedirects(false);
    assertEquals(302, c.getResponseCode());
    assertEquals("/after", c.getHeaderField("Location"));
    Mockito.verify(sessionManager).logout(Mockito.any());
  }

  @Test
  public void signout_withoutRedirect_returns200Html() throws Exception {
    URL url = new URL("http://localhost:" + port + "/auth/signout");
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    assertEquals(200, c.getResponseCode());
    assertTrue(c.getHeaderField("Content-Type").contains("text/html"));
    Mockito.verify(sessionManager).logout(Mockito.any());
  }
}

