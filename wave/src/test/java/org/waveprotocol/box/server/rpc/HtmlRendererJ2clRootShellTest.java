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
    assertTrue(html.contains("data-j2cl-fallback=\"true\""));
    assertTrue(html.contains(">Skip to main content</a>"));
  }

  public void testExternalReturnTargetIsRejectedInBootstrap() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    // External URL (starts with //) must be rejected and replaced with the safe default.
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "//evil.com/phish", "ws.example:443");

    assertFalse("External return target must not appear in bootstrap JS",
        html.contains("evil.com"));
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
    assertFalse("HTML entity &amp; must not appear inside bootstrap script",
        html.contains("&amp;"));
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
