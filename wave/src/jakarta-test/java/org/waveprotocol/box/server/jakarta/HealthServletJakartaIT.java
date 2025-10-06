package org.waveprotocol.box.server.jakarta;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.rpc.HealthServlet;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Integration tests for the Jakarta health/readiness servlet. */
public final class HealthServletJakartaIT {
  private Server server;
  private int port;

  @Before
  public void setUp() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
    server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(0);
    server.addConnector(connector);

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    context.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(new HealthServlet()), "/healthz");
    context.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(new HealthServlet()), "/readyz");
    server.setHandler(context);
    server.start();
    port = connector.getLocalPort();
  }

  @After
  public void tearDown() {
    TestSupport.stopServerQuietly(server);
  }

  @Test
  public void healthEndpointReturnsOk() throws Exception {
    HttpURLConnection conn = TestSupport.openConnection(new URL("http://localhost:" + port + "/healthz"));
    assertEquals(200, conn.getResponseCode());
    String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals("ok\n", body);
    assertTrue(conn.getHeaderField("Content-Type").contains("text/plain"));
  }

  @Test
  public void readinessEndpointReturnsOk() throws Exception {
    HttpURLConnection conn = TestSupport.openConnection(new URL("http://localhost:" + port + "/readyz"));
    assertEquals(200, conn.getResponseCode());
    String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals("ok\n", body);
  }
}
