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
    assertTrue(html.contains("<button slot=\"splitter\" class=\"j2cl-shell-splitter\""));
    assertTrue(html.contains("role=\"separator\" aria-orientation=\"vertical\""));
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
        html.contains(
            "<span id=\"j2cl-root-return-target-text\" class=\"j2cl-status-live-text\">"
                + "Return target: "));
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

  // J-UI-8 (#1086, R-6.3): aria-busy on the snapshot card so AT clients
  // know the pre-upgrade content is in flux. Cleared by
  // J2clSelectedWaveView.clearServerFirstMarkers once the live render
  // replaces the server-first state.
  public void testSnapshotCardCarriesAriaBusy() {
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

    int cardIdx = html.indexOf("<section class=\"sidecar-selected-card\"");
    assertTrue("Snapshot mode must emit a sidecar-selected-card", cardIdx >= 0);
    int cardEnd = html.indexOf('>', cardIdx);
    assertTrue(cardEnd > cardIdx);
    String cardOpenTag = html.substring(cardIdx, cardEnd);
    // aria-busy is now injected via an inline script so the no-JS path never
    // leaves the region permanently busy. Verify the open tag is attribute-free
    // and the inline script is present in the section body.
    assertFalse(
        "Snapshot card open tag must not carry aria-busy as a static attribute"
            + " — that would be permanent on the no-JS path",
        cardOpenTag.contains("aria-busy"));
    assertTrue(
        "Snapshot mode must emit the aria-busy inline script so AT clients know"
            + " the pre-upgrade content is in flux",
        html.indexOf("document.currentScript.parentElement.setAttribute('aria-busy'", cardIdx) > 0);
  }

  // R-6.3 corollary: aria-busy is a server-first signal only — the card
  // must not carry it when there is no snapshot to upgrade away from.
  public void testNoWaveCardOmitsAriaBusy() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/?view=j2cl-root", "ws.example:443");

    int cardIdx = html.indexOf("<section class=\"sidecar-selected-card\"");
    assertTrue(cardIdx >= 0);
    int cardEnd = html.indexOf('>', cardIdx);
    String cardOpenTag = html.substring(cardIdx, cardEnd);
    assertFalse(
        "no-wave card must not carry aria-busy — there is nothing to upgrade away from",
        cardOpenTag.contains("aria-busy"));
    assertFalse(
        "no-wave card must not emit aria-busy inline script",
        html.contains("setAttribute('aria-busy'"));
  }

  // J-UI-8 (#1086, R-6.1): <html lang> reflects the viewer's account
  // locale so the static first-paint HTML is AT-correct without waiting
  // on JS.
  public void testHtmlLangReflectsViewerLocale() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session,
        "",
        "commit",
        0L,
        "rel",
        "/",
        "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(),
        false,
        "fr",
        false);

    assertTrue(
        "Locale 'fr' must reach <html lang>",
        html.contains("<html lang=\"fr\">"));
  }

  public void testHtmlLangAcceptsRegionSubtag() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session,
        "",
        "commit",
        0L,
        "rel",
        "/",
        "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(),
        false,
        "pt-BR",
        false);

    assertTrue("Region subtag must round-trip", html.contains("<html lang=\"pt-BR\">"));
  }

  public void testHtmlLangNormalizesUnderscoreToHyphen() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session,
        "",
        "commit",
        0L,
        "rel",
        "/",
        "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(),
        false,
        "zh_CN",
        false);

    assertTrue(
        "java.util.Locale-style underscore must normalize to BCP-47 hyphen",
        html.contains("<html lang=\"zh-CN\">"));
  }

  // Defense-in-depth: the html lang attribute is one of the few SSR
  // points that takes a viewer-supplied string. Hostile payloads that
  // could break out of the attribute or inject script tags must clamp
  // back to the safe default.
  public void testHtmlLangSanitizesInjectionPayload() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session,
        "",
        "commit",
        0L,
        "rel",
        "/",
        "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(),
        false,
        "\"><script>alert(1)</script>",
        false);

    assertTrue(
        "Injection payload must clamp to 'en' — never reach the lang attribute",
        html.contains("<html lang=\"en\">"));
    assertFalse(html.contains("<script>alert(1)"));
  }

  public void testHtmlLangDefaultsWhenNullOrEmpty() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String htmlNull = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, null, false);
    String htmlEmpty = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, "", false);

    assertTrue(htmlNull.contains("<html lang=\"en\">"));
    assertTrue(htmlEmpty.contains("<html lang=\"en\">"));
  }

  // J-UI-8 (#1086, R-6.1): <noscript> info banner ships only when the
  // j2cl-server-first-paint flag is on. The banner is wrapped in
  // <noscript> so it is a no-op when JS is enabled — flag-on with JS-on
  // is identical to flag-off.
  public void testServerFirstPaintFlagOnEmitsNoscriptBanner() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.snapshot(
            "example.com/w+1", "<p>wave content</p>"),
        false, "en", true);

    assertTrue(
        "Flag-on with snapshot must emit the noscript banner element",
        html.contains("data-j2cl-noscript-banner=\"true\""));
    int bannerIdx = html.indexOf("data-j2cl-noscript-banner=\"true\"");
    int noscriptOpen = html.lastIndexOf("<noscript>", bannerIdx);
    int noscriptClose = html.indexOf("</noscript>", bannerIdx);
    assertTrue(
        "Banner must live inside a <noscript> wrapper so JS-on visitors do not see it",
        noscriptOpen >= 0 && noscriptClose >= 0 && noscriptOpen < bannerIdx
            && bannerIdx < noscriptClose);
  }

  public void testNoscriptBannerOmittedWhenNoSnapshot() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, "en", true);

    assertFalse(
        "Flag-on + signed-in but NO_WAVE must not emit the snapshot banner — text would be false",
        html.contains("data-j2cl-noscript-banner"));
  }

  public void testServerFirstPaintFlagOffOmitsNoscriptBanner() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    // Use a real snapshot so the only thing suppressing the banner is the flag.
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.snapshot(
            "example.com/w+1", "<p>wave content</p>"),
        false, "en", false);

    assertFalse(
        "Flag-off must not emit the noscript banner even when a snapshot is present",
        html.contains("data-j2cl-noscript-banner"));
  }

  public void testNoscriptBannerOnlyShipsForSignedInUsers() {
    JSONObject signedIn = new JSONObject();
    signedIn.put("address", "alice@example.com");
    JSONObject signedOut = new JSONObject();

    String htmlSignedIn = HtmlRenderer.renderJ2clRootShellPage(
        signedIn, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.snapshot(
            "example.com/w+1", "<p>wave content</p>"),
        false, "en", true);
    // Use a snapshot for signed-out too so the only gate is the signed-in check.
    String htmlSignedOut = HtmlRenderer.renderJ2clRootShellPage(
        signedOut, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.snapshot(
            "example.com/w+1", "<p>wave content</p>"),
        false, "en", true);

    assertTrue(
        "Signed-in flag-on shell ships the banner",
        htmlSignedIn.contains("data-j2cl-noscript-banner=\"true\""));
    assertTrue(htmlSignedIn.contains("static read-only snapshot"));
    // Signed-out chrome already provides a sign-in CTA; the banner is
    // signed-in only per the J-UI-8 plan to keep the signed-out
    // experience untouched.
    assertFalse(
        "Signed-out flag-on shell must not ship the banner even when snapshot is present",
        htmlSignedOut.contains("data-j2cl-noscript-banner"));
    assertFalse(
        "Signed-out chrome must not contain snapshot content",
        htmlSignedOut.contains("wave content"));
  }

  // A terminal singleton (e.g. "en-a", "en-1") is invalid BCP-47 because an
  // extension/private-use singleton MUST be followed by at least one subtag.
  // These fall back to "en" to avoid a malformed lang attribute.
  public void testHtmlLangRejectsSingleCharSecondarySubtag() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String htmlLetterSubtag = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, "en-a", false);
    String htmlDigitSubtag = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, "en-1", false);

    assertTrue(
        "1-char letter secondary subtag must clamp to en",
        htmlLetterSubtag.contains("<html lang=\"en\">"));
    assertTrue(
        "1-char digit secondary subtag must clamp to en",
        htmlDigitSubtag.contains("<html lang=\"en\">"));
  }

  public void testHtmlLangAcceptsLongerRegionSubtag() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    // 3-digit region code (UN M.49) — valid BCP-47, must round-trip.
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, "es-419", false);

    assertTrue(
        "3-digit region code must round-trip",
        html.contains("<html lang=\"es-419\">"));
  }

  public void testHtmlLangAcceptsScriptSubtag() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, "zh-Hans-CN", false);

    assertTrue(
        "BCP-47 script + region chain must round-trip",
        html.contains("<html lang=\"zh-Hans-CN\">"));
  }

  // J-UI-8 (#1086, R-6.1): the snapshot HTML must land inside
  // .sidecar-selected-content (which is a normal grid host in
  // sidecar.css), not inside the .sidecar-empty-state element which is
  // hidden when a snapshot is present. Pinning this prevents the
  // "rendered into a hidden seam" regression class flagged by the audit.
  public void testSnapshotIsNotEmittedInsideEmptyState() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");
    String snapshotHtml =
        "<div class=\"wave-content\" data-blip-id=\"b+1\"><h1>Inbox wave</h1></div>";

    String html = HtmlRenderer.renderJ2clRootShellPage(
        session,
        "",
        "commit",
        0L,
        "rel",
        "/?view=j2cl-root&wave=example.com%2Fw%2B1",
        "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.snapshot(
            "example.com/w+1", snapshotHtml));

    int snapshotIdx = html.indexOf("data-blip-id=\"b+1\"");
    assertTrue("Snapshot must land in the response", snapshotIdx >= 0);
    // The empty-state is hidden when a snapshot is present; assert that
    // the snapshot is NOT nested under the hidden empty-state element.
    int emptyStateIdx = html.indexOf("<div class=\"sidecar-empty-state\" hidden");
    assertTrue("Snapshot mode must hide the empty-state recipe", emptyStateIdx >= 0);
    assertTrue(
        "Snapshot must be emitted before the hidden empty-state, not inside it",
        snapshotIdx < emptyStateIdx);
    // And it must live inside the visible .sidecar-selected-content
    // grid host.
    int contentHostIdx = html.lastIndexOf("<div class=\"sidecar-selected-content\"", snapshotIdx);
    assertTrue("Snapshot must live inside .sidecar-selected-content", contentHostIdx >= 0);
  }

  public void testHtmlLangAcceptsBcp47ExtensionSubtags() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    // Unicode locale extension (u-ca-gregory) and private-use (x-phonebk)
    // are valid BCP-47 and must round-trip.
    String htmlUnicode = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, "en-US-u-ca-gregory", false);
    String htmlPrivate = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, "de-x-phonebk", false);

    assertTrue(
        "Unicode extension tag must round-trip",
        htmlUnicode.contains("<html lang=\"en-US-u-ca-gregory\">"));
    assertTrue(
        "Private-use extension tag must round-trip",
        htmlPrivate.contains("<html lang=\"de-x-phonebk\">"));
  }

  public void testHtmlLangAcceptsFivePlusDashExtensionTags() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    // zh-Hant-TW-u-ca-gregory has 5 dashes; the old dashCount>4 guard would
    // have rejected it and fallen back to "en".
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false,
        "zh-Hant-TW-u-ca-gregory", false);

    assertTrue(
        "5-dash BCP-47 extension tag must reach <html lang>",
        html.contains("<html lang=\"zh-Hant-TW-u-ca-gregory\">"));
  }

  public void testWavyHeaderLocaleUsesUnderscore() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    // zh-TW in BCP-47 must map to zh_TW (underscore) in <wavy-header locale>,
    // because wavy-header's LOCALES list uses underscore codes.
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, "zh-TW", false);

    assertTrue(
        "<wavy-header locale> must carry zh_TW not zh-TW",
        html.contains("locale=\"zh_TW\""));
    assertFalse(
        "<wavy-header locale> must not carry raw BCP-47 zh-TW",
        html.contains("locale=\"zh-TW\""));
  }

  public void testWavyHeaderLocaleResolvesExtensionSuffixedTag() {
    JSONObject session = new JSONObject();
    session.put("address", "alice@example.com");

    // zh-TW-u-ca-roc carries a calendar extension; wavy-header must still
    // get zh_TW (the closest supported option).
    String html = HtmlRenderer.renderJ2clRootShellPage(
        session, "", "commit", 0L, "rel", "/", "ws.example:443",
        J2clSelectedWaveSnapshotRenderer.SnapshotResult.noWave(), false, "zh-TW-u-ca-roc", false);

    assertTrue(
        "<wavy-header locale> must resolve zh-TW-u-ca-roc to zh_TW",
        html.contains("locale=\"zh_TW\""));
  }
}
