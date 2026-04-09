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
    assertTrue(topBarHtml.contains("section-label\">Automation / APIs"));
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
    assertTrue(topBarHtml.contains("aria-haspopup=\"menu\""));
    assertTrue(topBarHtml.contains("aria-expanded=\"false\""));
    assertTrue(topBarHtml.contains("role=\"menu\""));
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
    assertTrue(html.contains("class=\"menu-section\" role=\"presentation\">"));
    assertTrue(html.contains("class=\"menu-signout\""));
    assertTrue(css.contains(".user-menu-dropdown { display: none; position: absolute; right: 0; top: 100%; background: #fff; border-radius: 10px;"));
    assertTrue(css.contains(".user-menu-dropdown .menu-section + .menu-section {"));
    assertTrue(css.contains(".user-menu-dropdown a { display: block; padding: 7px 10px;"));
    assertTrue(css.contains(".user-menu-dropdown .user-info-label {"));
    assertTrue(css.contains(".user-menu-dropdown a:focus-visible { outline: none; box-shadow: inset 0 0 0 2px rgba(0,119,182,0.40); }"));
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
    assertTrue(html.contains("class=\"menu-section\" role=\"presentation\">"));
    assertTrue(html.contains(".user-menu-dropdown .menu-section + .menu-section {"));
    assertTrue(html.contains(".user-menu-dropdown a { display: block; padding: 7px 10px;"));
    assertTrue(html.contains(".user-menu-dropdown a:focus-visible { outline: none; box-shadow: inset 0 0 0 2px rgba(0,119,182,0.40); }"));
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
