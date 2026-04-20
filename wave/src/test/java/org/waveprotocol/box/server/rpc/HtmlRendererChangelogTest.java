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

import static org.junit.Assert.assertFalse;
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
        "abc123build",
        1700000000000L,
        "2026-03-27-unread-only-search-filter",
        null);

    assertTrue(html.contains("var currentBuildCommit = \"abc123build\";"));
    assertTrue(html.contains("var currentReleaseId = \"2026-03-27-unread-only-search-filter\";"));
    assertTrue(html.contains("fetch('/version?since=' + encodeURIComponent(currentReleaseId || '')"));
    assertTrue(html.contains("showUpgradeBanner(data.releaseNotesStatus, data.releaseNotes || []);"));
    assertTrue(html.contains("data.releaseNotesStatus"));
    assertTrue(html.contains("data.releaseNotes || []"));
    assertTrue(html.contains("whatsNew.href = '/changelog#release-' + encodeURIComponent(releaseNotes[0].releaseId);"));
    assertTrue(html.contains("What's New \\u2192"));
    assertTrue(html.contains("upgrade-banner"));
  }

  @Test
  public void upgradeBannerIncludesStatusSpecificMessages() {
    String html = HtmlRenderer.renderWaveClientPage(
        new JSONObject("{\"id\":\"u\"}"),
        new JSONObject(),
        "localhost:9898",
        HtmlRenderer.renderTopBar("alice", "example.com", "user"),
        "",
        "abc123build",
        1700000000000L,
        "2026-03-27-unread-only-search-filter",
        null);

    assertTrue("missing same_release message", html.contains("'A minor update has been applied.'"));
    assertTrue("missing partial fallback message", html.contains("'Multiple updates have been applied.'"));
    assertTrue("missing partial with title message", html.contains("firstRelease.title + ' and other updates.'"));
    assertTrue("missing generic fallback message", html.contains("'A new version of SupaWave is available.'"));
  }

  @Test
  public void changelogPageRendersEntriesAndFallback() {
    JSONArray entries = new JSONArray(
        "[{\"releaseId\":\"2026-03-27-changelog-system\",\"version\":\"2026-03-27\","
            + "\"date\":\"2026-03-27\","
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
    assertTrue("missing release anchor", html.contains("id=\"release-2026-03-27-changelog-system\""));
    assertTrue("missing empty-state message", emptyHtml.contains("No releases yet"));
  }

  @Test
  public void topBarAndLandingPageExposeWhatsNewLink() {
    String topBar = HtmlRenderer.renderTopBar("alice", "example.com", "user");
    String landing = HtmlRenderer.renderLandingPage("example.com", "");

    assertTrue(topBar.contains("href=\"/changelog\" target=\"_blank\" rel=\"noopener noreferrer\""));
    assertTrue(landing.contains("href=\"/changelog\">What's New</a>"));
    assertFalse(topBar.contains("target=\"_blank\">"));
  }

  @Test
  public void topBarAndLandingPageExposeApiDocsLink() {
    String topBar = HtmlRenderer.renderTopBar("alice", "example.com", "user");
    String landing = HtmlRenderer.renderLandingPage("example.com", "");

    assertTrue(topBar.contains("href=\"/api-docs\" target=\"_blank\" rel=\"noopener noreferrer\""));
    assertTrue(landing.contains("href=\"/api-docs\""));
  }

  @Test
  public void landingPageNavWrapsControlsOnMobile() {
    String landing = HtmlRenderer.renderLandingPage("example.com", "");

    assertTrue(landing.contains("@media (max-width: 640px)"));
    assertTrue(landing.contains(".nav {"));
    assertTrue(landing.contains("padding: 12px 16px;"));
    assertTrue(landing.contains("flex-wrap: wrap;"));
    assertTrue(landing.contains("gap: 12px;"));
    assertTrue(landing.contains(".nav-links {"));
    assertTrue(landing.contains("width: 100%;"));
    assertTrue(landing.contains("justify-content: flex-start;"));
  }

  @Test
  public void landingPageAdvertisesCurrentApiSupportWithoutFederationClaim() {
    String landing = HtmlRenderer.renderLandingPage("example.com", "");

    assertTrue(landing.contains("Robot &amp; Data API"));
    assertTrue(landing.contains("Register robots and mint Data API tokens"));
    assertFalse(landing.contains("Open &amp; Federated"));
    assertFalse(landing.contains("federated SupaWave instances"));
  }
}
