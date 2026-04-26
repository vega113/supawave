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

import junit.framework.TestCase;
import org.json.JSONObject;
import org.waveprotocol.box.server.rpc.render.J2clSelectedWaveSnapshotRenderer;

public final class HtmlRendererJ2clRootShellTest extends TestCase {
  public void testSignedInPageUsesLitCustomElements() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");
    session.put("role", "user");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443");

    assertTrue(html.contains("<shell-root"));
    assertTrue(html.contains("<shell-header slot=\"header\" signed-in"));
    assertTrue(html.contains("<shell-nav-rail slot=\"nav\""));
    assertTrue(html.contains("<shell-main-region slot=\"main\""));
    assertTrue(html.contains("<shell-status-strip slot=\"status\""));
    assertTrue(html.contains("<shell-skip-link slot=\"skip-link\""));
    assertTrue(html.contains("/j2cl/assets/shell.js"));
    assertTrue(html.contains("/j2cl/assets/shell.css"));
    // F-0 (#1035): wavy design tokens must load alongside shell.css so
    // F-2/F-3/F-4 recipes resolve --wavy-* under the J2CL root view.
    assertTrue(html.contains("/j2cl/assets/wavy-tokens.css"));
    assertTrue(html.contains("data-j2cl-server-first-workflow=\"true\""));
    assertTrue(html.contains("window.__j2clRootShellStat"));
  }

  public void testSignedOutPageUsesSignedOutRoot() {
    JSONObject session = new JSONObject();

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443");

    assertTrue(html.contains("<shell-root-signed-out"));
    assertFalse(html.contains("<shell-root data-j2cl-root-shell"));
  }

  public void testLegacyShellClassesAreNoLongerEmitted() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443");

    assertFalse(html.contains("j2cl-root-shell-banner"));
    assertFalse(html.contains("j2cl-root-shell-nav"));
    assertFalse(html.contains("j2cl-root-shell-pill"));
  }

  public void testFallbackMarkupIsReadableWithoutJs() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443");

    assertTrue(html.contains("id=\"j2cl-root-shell-workflow\""));
    assertTrue(html.contains("data-j2cl-root-shell-workflow=\"true\""));
    assertTrue(html.contains("data-j2cl-server-first-workflow=\"true\""));
    assertTrue(html.contains("data-j2cl-selected-wave-host=\"true\""));
    assertTrue(html.contains("data-j2cl-server-first-mode=\"no-wave\""));
    assertTrue(html.contains(">Skip to main content</a>"));
  }

  public void testSnapshotMarkupUsesServerFirstCardContract() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session,
        "",
        "commit",
        0L,
        "rel",
        "/?view=j2cl-root&wave=example.com%2Fw%2B1",
        "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.snapshot(
            "example.com/w+1",
            "<div class=\"wave-content\"><h1 class=\"wave-title\">Inbox wave</h1></div>"));

    assertTrue(html.contains("data-j2cl-server-first-selected-wave=\"example.com/w+1\""));
    assertTrue(html.contains("data-j2cl-server-first-mode=\"snapshot\""));
    assertTrue(html.contains("data-j2cl-upgrade-placeholder=\"selected-wave\""));
    assertTrue(html.contains("<div class=\"wave-content\"><h1 class=\"wave-title\">Inbox wave</h1></div>"));
  }

  public void testExternalReturnTargetIsRejectedInBootstrap() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    // External URL (starts with //) must be rejected and replaced with the safe default.
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "//evil.com/phish", "ws.example:443");

    assertFalse("External return target must not appear in bootstrap JS",
        html.contains("evil.com"));
    assertTrue("Safe fallback return target must appear in bootstrap JS",
        html.contains("/?view=j2cl-root"));
  }

  public void testReturnTargetWithAmpersandIsJsEscapedNotHtmlEscaped() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    // A return target containing '&' must be JS-escaped (not HTML-escaped) inside <script>.
    // HTML escaping turns '&' into '&amp;' which the JS engine cannot decode.
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/?view=j2cl-root&wave=x", "ws.example:443");

    // Old broken pattern: single-quoted JS variable with HTML entity
    assertFalse("HTML entity must not appear in JS fallback variable (single-quoted)",
        html.contains("var fallback='/?view=j2cl-root&amp;wave=x'"));
    // New correct pattern: JS-escaped ampersand via \\u0026
    assertTrue("JS-escaped ampersand (\\u0026) must appear in bootstrap script",
        html.contains("\\u0026wave"));
  }

  public void testLegacyHashDeepLinkBootstrapIsPresent() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/mypath/?view=j2cl-root", "ws.example:443");

    assertTrue("Bootstrap must include normalizeLegacyHashDeepLink",
        html.contains("normalizeLegacyHashDeepLink()"));
    assertTrue("Bootstrap must include safeResolvedBasePath wiring",
        html.contains("/mypath/"));
    int markerIdx = html.indexOf("normalizeLegacyHashDeepLink()");
    int scriptOpenIdx = html.lastIndexOf("<script>", markerIdx);
    int scriptCloseIdx = html.indexOf("</script>", markerIdx);
    assertTrue("Expected opening <script> tag before normalizeLegacyHashDeepLink()", scriptOpenIdx >= 0);
    assertTrue("Expected closing </script> tag after normalizeLegacyHashDeepLink()", scriptCloseIdx >= 0);
    assertTrue("Expected <script> to open before </script>", scriptOpenIdx < scriptCloseIdx);
    String bootstrapScript = html.substring(scriptOpenIdx, scriptCloseIdx + "</script>".length());
    assertFalse("HTML entity &amp; must not appear inside bootstrap script",
        bootstrapScript.contains("&amp;"));
  }

  public void testSignedOutReturnTargetLabelHasSyncableSpan() {
    JSONObject session = new JSONObject();
    // No address = signed out; provide a return target to check label markup.

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/wave/inbox", "ws.example:443");

    assertTrue("Signed-out status strip must wrap return-target text in the syncable span",
        html.contains("<span id=\"j2cl-root-return-target-text\">Return target: "));
  }

  public void testSignedOutPageAlsoIncludesReturnTargetBootstrap() {
    JSONObject session = new JSONObject();
    // No address = signed out

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443");

    assertTrue("Signed-out shell must include normalizeLegacyHashDeepLink",
        html.contains("normalizeLegacyHashDeepLink()"));
    assertTrue("Signed-out shell must include syncReturnTargetUi",
        html.contains("syncReturnTargetUi()"));
    assertFalse("Signed-out shell must not include mountWhenReady",
        html.contains("mountWhenReady("));
  }
}
