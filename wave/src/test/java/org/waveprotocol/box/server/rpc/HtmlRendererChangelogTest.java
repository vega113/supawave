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
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class HtmlRendererChangelogTest {
  @Test
  public void waveClientPageUsesVersionPayloadForUpgradeBanner() {
    String html = HtmlRenderer.renderWaveClientPage(
        new JSONObject("{\"id\":\"u\"}"),
        new JSONObject(),
        "localhost:9898",
        HtmlRenderer.renderTopBar("alice", "example.com", "user"),
        "",
        "abc123",
        1700000000000L,
        null);

    assertTrue(html.contains("showUpgradeBanner(data.changelog || null);"));
    assertTrue(html.contains("What's New"));
  }

  @Test
  public void changelogPageRendersEntriesAndFallback() {
    JSONArray entries = new JSONArray(
        "[{\"version\":\"2026-03-27\",\"date\":\"2026-03-27\","
            + "\"title\":\"Changelog System\","
            + "\"summary\":\"You can now see what's new after each deploy.\","
            + "\"sections\":[{\"type\":\"feature\",\"items\":[\"New /changelog page\"]},"
            + "{\"type\":\"fix\",\"items\":[\"Sharper upgrade banner\"]}]}]");

    String html = HtmlRenderer.renderChangelogPage(entries);
    String emptyHtml = HtmlRenderer.renderChangelogPage(new JSONArray());

    assertTrue("missing changelog title", html.contains("Changelog System"));
    assertTrue(
        "missing changelog summary",
        html.contains("You can now see what&#39;s new after each deploy."));
    assertTrue("missing feature item", html.contains("New /changelog page"));
    assertTrue("missing fix item", html.contains("Sharper upgrade banner"));
    assertTrue("missing empty-state message", emptyHtml.contains("No releases yet"));
  }

  @Test
  public void topBarAndLandingPageExposeWhatsNewLink() {
    String topBar = HtmlRenderer.renderTopBar("alice", "example.com", "user");
    String landing = HtmlRenderer.renderLandingPage("example.com", "");

    assertTrue(topBar.contains("href=\"/changelog\""));
    assertTrue(landing.contains("href=\"/changelog\">What's New</a>"));
  }

  @Test
  public void topBarAndLandingPageExposeApiDocsLink() {
    String topBar = HtmlRenderer.renderTopBar("alice", "example.com", "user");
    String landing = HtmlRenderer.renderLandingPage("example.com", "");

    assertTrue(topBar.contains("href=\"/api-docs\""));
    assertTrue(landing.contains("href=\"/api-docs\""));
  }

  @Test
  public void landingPageNavWrapsControlsOnMobile() {
    String landing = HtmlRenderer.renderLandingPage("example.com", "");

    assertTrue(landing.contains(".nav { padding: 12px 16px; flex-wrap: wrap; gap: 12px; }"));
    assertTrue(landing.contains(".nav-links { width: 100%; flex-wrap: wrap; justify-content: flex-start; }"));
  }
}
