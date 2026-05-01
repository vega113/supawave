/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;

import junit.framework.TestCase;

public final class HtmlRendererTopBarTest extends TestCase {
  public void testRenderTopBarIncludesRobotDashboardLink() {
    String topBarHtml = HtmlRenderer.renderTopBar("vega", "example.com", "user");

    assertTrue(topBarHtml.contains("Robot &amp; Data API"));
    assertTrue(topBarHtml.contains("href=\"/account/robots\""));
  }

  public void testRenderTopBarGroupsMenuSections() {
    String topBarHtml = HtmlRenderer.renderTopBar("vega", "example.com", "admin");

    assertTrue(topBarHtml.contains("section-label\">Account"));
    // F-0 (#1035): renamed from "Automation / APIs" to "Plugins / Integrations"
    // so the section reads as the user-facing surface for the forthcoming
    // robots/data-API plugin registry.
    assertTrue(topBarHtml.contains("section-label\">Plugins / Integrations"));
    assertTrue(topBarHtml.contains("section-label\">Product / Support"));
    assertTrue(topBarHtml.contains("section-label\">Legal"));
    assertTrue(topBarHtml.contains("href=\"/admin\""));
  }

  public void testRenderSharedTopBarUsesContextPathAndAccessibleControls() {
    String topBarHtml = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "/wave", "admin");

    assertTrue(topBarHtml.contains("href=\"/wave/account/robots\""));
    assertTrue(topBarHtml.contains("href=\"/wave/admin\""));
    assertTrue(topBarHtml.contains("href=\"/wave/auth/signout?r=%2Fwave%2F\""));
    assertTrue(topBarHtml.contains("aria-label=\"Language\""));
    assertTrue(topBarHtml.contains("aria-expanded=\"false\""));
    assertTrue(topBarHtml.contains("aria-controls=\"topbarUserMenu\""));
    assertFalse(topBarHtml.contains("role=\"menu\""));
    assertTrue(topBarHtml.contains("net-icon-online"));
    assertTrue(topBarHtml.contains("net-icon-offline"));
  }

  public void testRenderSharedTopBarCssScopesInfoStyles() {
    String css = HtmlRenderer.renderSharedTopBarCss();

    assertTrue(css.contains(".topbar .info {"));
    assertTrue(css.contains(".topbar .info > a {"));
    assertTrue(css.contains(".lang-icon-btn:focus-within {"));
    assertFalse(css.contains("\n.info {"));
  }

  public void testRenderSharedTopBarJsUsesContextPath() {
    String js = HtmlRenderer.renderSharedTopBarJs("/wave");

    assertTrue(js.contains("var _ctx=\"\\/wave\";"));
    assertTrue(js.contains("fetch(_ctx+'/locale'"));
    assertTrue(js.contains("if(!t||!d||t.contains(e.target)||d.contains(e.target))return;setMenuOpen(false);"));
    assertTrue(js.contains("setMenuOpen(false,true);"));
  }

  public void testRenderSharedTopBarCssIncludesAdminBadge() {
    String css = HtmlRenderer.renderSharedTopBarCss();

    assertTrue(css.contains(".admin-msg-btn"));
    assertTrue(css.contains(".admin-badge"));
    assertTrue(css.contains("admin-glow"));
  }

  public void testRenderSharedTopBarUsesCompactMenuSections() {
    String html = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "/wave", "admin");
    String css = HtmlRenderer.renderSharedTopBarCss();

    assertTrue(html.contains("class=\"user-info-label\">Signed in as</div>"));
    assertTrue(html.contains("class=\"user-info-address\">vega@example.com</div>"));
    assertTrue(html.contains("class=\"user-info\">"));
    assertTrue(html.contains("class=\"menu-section\">"));
    assertTrue(html.contains("class=\"menu-signout\""));
    assertTrue(css.contains(".user-menu-dropdown {"));
    assertTrue(css.contains("display: none;"));
    assertTrue(css.contains("border-radius: 10px;"));
    assertTrue(css.contains(".user-menu-dropdown .menu-section + .menu-section {"));
    assertTrue(css.contains(".user-menu-dropdown a {"));
    assertTrue(css.contains("padding: 7px 10px;"));
    assertTrue(css.contains(".user-menu-dropdown .user-info-label {"));
    assertTrue(css.contains(".user-menu-dropdown a:focus-visible {"));
    assertTrue(css.contains("box-shadow: inset 0 0 0 2px rgba(0,119,182,0.40);"));
  }

  public void testWaveClientPageUsesCompactMenuSectionStyles() {
    String html = HtmlRenderer.renderWaveClientPage(
        new JSONObject("{\"id\":\"u\"}"),
        new JSONObject(),
        "localhost:9898",
        HtmlRenderer.renderTopBar("vega", "example.com", "user"),
        "",
        "abc123build",
        1700000000000L,
        null,
        null);

    assertTrue(html.contains("class=\"user-info-label\">Signed in as</div>"));
    assertTrue(html.contains("class=\"menu-section\">"));
    assertTrue(html.contains(".user-menu-dropdown .menu-section + .menu-section {"));
    assertTrue(html.contains(".user-menu-dropdown a {"));
    assertTrue(html.contains("padding: 7px 10px;"));
    assertTrue(html.contains(".user-menu-dropdown a:focus-visible {"));
    assertTrue(html.contains("box-shadow: inset 0 0 0 2px rgba(0,119,182,0.40);"));
    assertTrue(html.contains("if (!toggle || !dropdown || toggle.contains(e.target) || dropdown.contains(e.target)) return;"));
    assertTrue(html.contains("setMenuOpen(false, true);"));
  }

  public void testWaveClientPageEscapesInlineJsonPayloads() {
    JSONObject sessionJson = new JSONObject();
    sessionJson.put("address", "alice@example.com</script><script>alert(1)</script>");
    JSONObject clientFlags = new JSONObject();
    clientFlags.put("danger", "</script><script>alert(1)</script>");

    String html = HtmlRenderer.renderWaveClientPage(
        sessionJson,
        clientFlags,
        "localhost:9898",
        HtmlRenderer.renderTopBar("vega", "example.com", "user"),
        "",
        "abc123build",
        1700000000000L,
        null,
        null);

    assertTrue(html.contains("\"danger\":\""));
    assertTrue(html.contains("alert(1)"));
    assertTrue(html.contains("\\u003c"));
    assertFalse(html.contains("\"danger\":\"</script><script>alert(1)</script>\""));
    assertTrue(html.contains("alice@example.com"));
    assertFalse(html.contains("alice@example.com</script><script>alert(1)</script>"));
  }

  public void testJ2clRootShellEscapesInlineSessionJsonPayloads() {
    JSONObject sessionJson = new JSONObject();
    sessionJson.put("address", "alice@example.com</script><script>alert(1)</script>");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        sessionJson,
        "",
        "abc123build",
        1700000000000L,
        null,
        "/?view=j2cl-root",
        "localhost:9898");

    assertTrue(html.contains("alice@example.com"));
    assertTrue(html.contains("alert(1)"));
    assertTrue(html.contains("\\u003c"));
    assertFalse(html.contains("alice@example.com</script><script>alert(1)</script>"));
  }

  public void testRenderSharedTopBarHtmlAdminShowsEnvelopeIcon() {
    String html = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "", "admin");

    assertTrue(html.contains("id=\"adminMsgBtn\""));
    assertTrue(html.contains("id=\"adminMsgBadge\""));
    assertTrue(html.contains("href=\"/admin#contacts\""));
  }

  public void testRenderSharedTopBarHtmlUserHasNoEnvelopeIcon() {
    String html = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "", "user");

    assertFalse(html.contains("id=\"adminMsgBtn\""));
    assertFalse(html.contains("id=\"adminMsgBadge\""));
  }

  public void testRenderSharedTopBarHtmlOwnerShowsEnvelopeIcon() {
    String html = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "/wave", "owner");

    assertTrue(html.contains("id=\"adminMsgBtn\""));
    assertTrue(html.contains("href=\"/wave/admin#contacts\""));
  }

  public void testRenderTopBarAdminShowsEnvelopeIcon() {
    String html = HtmlRenderer.renderTopBar("vega", "example.com", "admin");

    assertTrue(html.contains("id=\"adminMsgBtn\""));
    assertTrue(html.contains("id=\"adminMsgBadge\""));
    assertTrue(html.contains("href=\"/admin#contacts\""));
  }

  public void testRenderTopBarUserHasNoEnvelopeIcon() {
    String html = HtmlRenderer.renderTopBar("vega", "example.com", "user");

    assertFalse(html.contains("id=\"adminMsgBtn\""));
  }

  public void testRenderTopBarOwnerShowsEnvelopeIcon() {
    String html = HtmlRenderer.renderTopBar("vega", "example.com", "owner");

    assertTrue(html.contains("id=\"adminMsgBtn\""));
  }

  public void testRenderSharedTopBarJsAdminIncludesPolling() {
    String js = HtmlRenderer.renderSharedTopBarJs("/wave", "admin");

    assertTrue(js.contains("adminMsgBtn"));
    assertTrue(js.contains("/admin/api/contacts?status=new&limit=0"));
    assertTrue(js.contains("setInterval"));
  }

  public void testRenderSharedTopBarJsOwnerIncludesPolling() {
    String js = HtmlRenderer.renderSharedTopBarJs("", "owner");

    assertTrue(js.contains("adminMsgBtn"));
    assertTrue(js.contains("/admin/api/contacts?status=new&limit=0"));
  }

  public void testRenderSharedTopBarJsUserNoPolling() {
    String js = HtmlRenderer.renderSharedTopBarJs("", "user");

    assertFalse(js.contains("adminMsgBtn"));
    assertFalse(js.contains("/admin/api/contacts"));
  }

  public void testRenderSharedTopBarJsOneArgNoPolling() {
    // One-arg overload should not include admin polling
    String js = HtmlRenderer.renderSharedTopBarJs("/wave");

    assertFalse(js.contains("adminMsgBtn"));
  }
}
