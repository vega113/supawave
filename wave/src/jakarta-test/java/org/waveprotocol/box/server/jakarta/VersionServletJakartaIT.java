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
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.json.JSONArray;
import org.waveprotocol.box.server.rpc.ChangelogProvider;
import org.waveprotocol.box.server.rpc.VersionServlet;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for the Jakarta version endpoint servlet.
 *
 * <p>Validates JSON response shape, content type, and cache-busting headers.
 */
public final class VersionServletJakartaIT {
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
    context.addServlet(
        new org.eclipse.jetty.ee10.servlet.ServletHolder(
            new VersionServlet(
                "abc123",
                1700000000000L,
                new ChangelogProvider(
                    new JSONArray(
                        "[{\"releaseId\":\"2026-03-27-unread-only-search-filter\","
                            + "\"version\":\"2026-03-27.403\",\"date\":\"2026-03-27\","
                            + "\"title\":\"Unread-Only Search Filter\","
                            + "\"summary\":\"Unread-only search is now available.\","
                            + "\"sections\":[{\"type\":\"feature\",\"items\":[\"Added unread:true\"]}]},"
                            + "{\"releaseId\":\"2026-03-27-changelog-system\","
                            + "\"version\":\"2026-03-27.383\",\"date\":\"2026-03-27\","
                            + "\"title\":\"Changelog System\","
                            + "\"summary\":\"You can now see what's new after each deploy.\","
                            + "\"sections\":[{\"type\":\"feature\",\"items\":[\"New /changelog page\"]}]}]")))),
        "/version");
    server.setHandler(context);
    server.start();
    port = connector.getLocalPort();
  }

  @After
  public void tearDown() {
    TestSupport.stopServerQuietly(server);
  }

  @Test
  public void returnsJsonWithVersionAndBuildTime() throws Exception {
    HttpURLConnection conn = TestSupport.openConnection(
        new URL("http://localhost:" + port + "/version"));
    assertEquals(200, conn.getResponseCode());
    String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertTrue("Should contain version field", body.contains("\"version\":\"abc123\""));
    assertTrue("Should contain buildCommit field", body.contains("\"buildCommit\":\"abc123\""));
    assertTrue("Should contain buildTime field", body.contains("\"buildTime\":1700000000000"));
    assertTrue(
        "Should contain current release id",
        body.contains("\"releaseId\":\"2026-03-27-unread-only-search-filter\""));
    assertTrue(
        "Should contain current-only releaseNotesStatus",
        body.contains("\"releaseNotesStatus\":\"current_only\""));
  }

  @Test
  public void returnsExactReleaseNotesRangeWhenSinceIsOlder() throws Exception {
    HttpURLConnection conn = TestSupport.openConnection(
        new URL("http://localhost:" + port + "/version?since=2026-03-27-changelog-system"));
    assertEquals(200, conn.getResponseCode());
    String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertTrue(body.contains("\"releaseNotesStatus\":\"exact\""));
    assertTrue(body.contains("\"releaseNotes\":["));
    assertTrue(body.contains("\"releaseId\":\"2026-03-27-unread-only-search-filter\""));
    assertTrue(!body.contains("\"releaseId\":\"2026-03-27-changelog-system\""));
    assertTrue(!body.contains("\"releaseId\":\"2026-03-27-restore-last-wave\""));
  }

  @Test
  public void returnsPartialStatusWhenSinceIsUnknown() throws Exception {
    HttpURLConnection conn = TestSupport.openConnection(
        new URL("http://localhost:" + port + "/version?since=2026-03-28-future"));
    assertEquals(200, conn.getResponseCode());
    String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertTrue(body.contains("\"releaseNotesStatus\":\"partial\""));
  }

  @Test
  public void returnsEmptyReleaseNotesArrayWhenClientAlreadyHasCurrentRelease() throws Exception {
    HttpURLConnection conn = TestSupport.openConnection(
        new URL("http://localhost:" + port + "/version?since=2026-03-27-unread-only-search-filter"));
    assertEquals(200, conn.getResponseCode());
    String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    assertTrue(body.contains("\"releaseNotesStatus\":\"same_release\""));
    assertTrue(body.contains("\"releaseNotes\":[]"));
  }

  @Test
  public void contentTypeIsJson() throws Exception {
    HttpURLConnection conn = TestSupport.openConnection(
        new URL("http://localhost:" + port + "/version"));
    assertEquals(200, conn.getResponseCode());
    String ct = conn.getHeaderField("Content-Type");
    assertTrue("Content-Type should be JSON", ct != null && ct.contains("application/json"));
  }

  @Test
  public void cacheControlPreventsStaleResponses() throws Exception {
    HttpURLConnection conn = TestSupport.openConnection(
        new URL("http://localhost:" + port + "/version"));
    assertEquals(200, conn.getResponseCode());
    String cc = conn.getHeaderField("Cache-Control");
    assertTrue("Should have no-cache", cc != null && cc.contains("no-cache"));
    assertTrue("Should have no-store", cc.contains("no-store"));
  }
}
