package org.waveprotocol.box.server.jakarta;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.waveprotocol.box.server.rpc.GadgetProviderServlet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class GadgetProviderServletJakartaIT {
  private Server server;
  private int port;
  private File jsonFile;

  @Before
  public void setup() throws Exception {
    TestSupport.assumeJettyEe10PresentOrSkip();
  }

  @After
  public void cleanup() throws Exception {
    if (server != null) server.stop();
    if (jsonFile != null) jsonFile.delete();
  }

  private void startServer() throws Exception {
    server = new Server();
    ServerConnector c = new ServerConnector(server);
    c.setPort(0);
    server.addConnector(c);
    ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
    ctx.setContextPath("/");
    ctx.addServlet(new org.eclipse.jetty.ee10.servlet.ServletHolder(new GadgetProviderServlet()), "/gadget/gadgetlist");
    server.setHandler(ctx);
    server.start();
    port = c.getLocalPort();
  }

  @Test
  public void returnsJson200WhenFilePresent() throws Exception {
    jsonFile = new File("jsongadgets.json");
    try (FileWriter fw = new FileWriter(jsonFile, StandardCharsets.UTF_8)) {
      fw.write("{\"gadgets\":[{\"id\":1}]}\n");
    }

    startServer();

    HttpURLConnection c = (HttpURLConnection) new URL("http://localhost:" + port + "/gadget/gadgetlist").openConnection();
    assertEquals(200, c.getResponseCode());
    assertTrue(c.getHeaderField("Content-Type").contains("application/json"));
    String body = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals("{\"gadgets\":[{\"id\":1}]}", body);
  }

  @Test
  public void returns500WhenFileMissing() throws Exception {
    jsonFile = new File("jsongadgets.json");
    if (jsonFile.exists()) jsonFile.delete();

    startServer();

    HttpURLConnection c = (HttpURLConnection) new URL("http://localhost:" + port + "/gadget/gadgetlist").openConnection();
    assertEquals(500, c.getResponseCode());
  }

  @Test
  public void cacheTtlFiveMinutes_preventsImmediateRefresh() throws Exception {
    jsonFile = new File("jsongadgets.json");
    try (FileWriter fw = new FileWriter(jsonFile, StandardCharsets.UTF_8)) {
      fw.write("{\"v\":1}\n");
    }
    startServer();

    HttpURLConnection c1 = (HttpURLConnection) new URL("http://localhost:" + port + "/gadget/gadgetlist").openConnection();
    assertEquals(200, c1.getResponseCode());
    String body1 = new String(c1.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals("{\"v\":1}", body1);

    // Overwrite file; within TTL, servlet should still serve old content
    try (FileWriter fw = new FileWriter(jsonFile, StandardCharsets.UTF_8, false)) {
      fw.write("{\"v\":2}\n");
    }

    HttpURLConnection c2 = (HttpURLConnection) new URL("http://localhost:" + port + "/gadget/gadgetlist").openConnection();
    assertEquals(200, c2.getResponseCode());
    String body2 = new String(c2.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals("{\"v\":1}", body2);
  }
}

