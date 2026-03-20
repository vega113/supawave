package org.waveprotocol.box.server.jakarta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.dev.ClientApplierStatsJakartaServlet;

public class ClientApplierStatsJakartaIT {
  private Server server;
  private int port;

  @Before
  public void startServer() throws Exception {
    port = reservePort();
    server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(port);
    server.addConnector(connector);
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    context.addServlet(ClientApplierStatsJakartaServlet.class, "/dev/client-applier-stats");
    server.setHandler(context);
    server.start();
  }

  @After
  public void stopServer() throws Exception {
    if (server != null) {
      server.stop();
      server.join();
    }
  }

  @Test
  public void postThenGetReturnsStoredStats() throws Exception {
    HttpURLConnection post = open("/dev/client-applier-stats", "POST");
    post.setDoOutput(true);
    post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    try (OutputStream out = post.getOutputStream()) {
      out.write("applied=7&rejected=2".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    assertEquals(204, post.getResponseCode());
    String cookie = post.getHeaderField("Set-Cookie");
    assertTrue(cookie != null && !cookie.isEmpty());

    HttpURLConnection get = open("/dev/client-applier-stats", "GET");
    get.setRequestProperty("Cookie", cookie);
    assertEquals(200, get.getResponseCode());
    assertEquals("application/json;charset=UTF-8", get.getContentType());
    assertEquals("{\"applied\":7,\"rejected\":2}", slurp(get));
  }

  private HttpURLConnection open(String path, String method) throws Exception {
    URL url = new URL("http://localhost:" + port + path);
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod(method);
    return connection;
  }

  private static String slurp(HttpURLConnection connection) throws Exception {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line);
      }
      return builder.toString();
    }
  }

  private static int reservePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
