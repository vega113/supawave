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

  @Test
  public void rejectsAbsoluteUrlOrSchemeRelativeOrTraversal() throws Exception {
    // Absolute URL -> no redirect, simple 200 HTML
    URL u1 = new URL("http://localhost:" + port + "/auth/signout?r=http%3A%2F%2Fevil.example%2Fout");
    HttpURLConnection c1 = (HttpURLConnection) u1.openConnection();
    c1.setInstanceFollowRedirects(false);
    assertEquals(200, c1.getResponseCode());
    assertNull(c1.getHeaderField("Location"));

    // Scheme-relative //evil.example
    URL u2 = new URL("http://localhost:" + port + "/auth/signout?r=%2F%2Fevil.example");
    HttpURLConnection c2 = (HttpURLConnection) u2.openConnection();
    c2.setInstanceFollowRedirects(false);
    assertEquals(200, c2.getResponseCode());
    assertNull(c2.getHeaderField("Location"));

    // Traversal /../secret
    URL u3 = new URL("http://localhost:" + port + "/auth/signout?r=%2F..%2Fsecret");
    HttpURLConnection c3 = (HttpURLConnection) u3.openConnection();
    c3.setInstanceFollowRedirects(false);
    assertEquals(200, c3.getResponseCode());
    assertNull(c3.getHeaderField("Location"));
  }
}
