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

/**
 * F-0 (#1035): renderer-level test for the wavy design preview page.
 * Asserts that the page mounts every recipe element, references the
 * three asset bundles, and emits all three theme-variant section
 * markers.
 */
public final class HtmlRendererJ2clDesignPreviewPageTest extends TestCase {
  public void testReferencesAllThreeAssetBundles() {
    String html = HtmlRenderer.renderJ2clDesignPreviewPage("/", "commit", 0L, "rel");
    assertTrue("design preview page must reference sidecar.css",
        html.contains("/j2cl/assets/sidecar.css"));
    assertTrue("design preview page must reference shell.css",
        html.contains("/j2cl/assets/shell.css"));
    assertTrue("design preview page must reference wavy-tokens.css",
        html.contains("/j2cl/assets/wavy-tokens.css"));
    assertTrue("design preview page must reference shell.js",
        html.contains("/j2cl/assets/shell.js"));
  }

  public void testMountsEveryRecipeElement() {
    String html = HtmlRenderer.renderJ2clDesignPreviewPage("/", "commit", 0L, "rel");
    assertTrue("must mount <wavy-blip-card>", html.contains("<wavy-blip-card"));
    assertTrue("must mount <wavy-compose-card>", html.contains("<wavy-compose-card"));
    assertTrue("must mount <wavy-rail-panel>", html.contains("<wavy-rail-panel"));
    assertTrue("must mount <wavy-edit-toolbar>", html.contains("<wavy-edit-toolbar"));
    assertTrue("must mount <wavy-depth-nav>", html.contains("<wavy-depth-nav"));
    assertTrue("must mount <wavy-pulse-stage>", html.contains("<wavy-pulse-stage"));
  }

  public void testSetsDesignPreviewMarkerOnHtml() {
    String html = HtmlRenderer.renderJ2clDesignPreviewPage("/", "commit", 0L, "rel");
    assertTrue("design preview must mark <html data-wavy-design-preview>",
        html.contains("<html lang=\"en\" data-wavy-design-preview>"));
  }

  public void testEmitsAllThreeThemeSections() {
    String html = HtmlRenderer.renderJ2clDesignPreviewPage("/", "commit", 0L, "rel");
    // Dark variant has no data-wavy-theme attribute (default scope).
    assertTrue("dark variant section header", html.contains("Dark variant"));
    assertTrue("light variant section attribute",
        html.contains("data-wavy-theme=\"light\""));
    assertTrue("light variant header", html.contains("Light variant"));
    assertTrue("contrast variant section attribute",
        html.contains("data-wavy-theme=\"contrast\""));
    assertTrue("high-contrast variant header",
        html.contains("High-contrast variant"));
  }

  public void testEmitsTheFourPluginSlotSamplePlugins() {
    String html = HtmlRenderer.renderJ2clDesignPreviewPage("/", "commit", 0L, "rel");
    assertTrue("blip plugin sample",
        html.contains("data-wavy-design-preview-plugin=\"blip\""));
    assertTrue("compose plugin sample",
        html.contains("data-wavy-design-preview-plugin=\"compose\""));
    assertTrue("rail plugin sample",
        html.contains("data-wavy-design-preview-plugin=\"rail\""));
    assertTrue("toolbar plugin sample",
        html.contains("data-wavy-design-preview-plugin=\"toolbar\""));
  }

  public void testHandlesEmptyContextPathByDefaulting() {
    String html = HtmlRenderer.renderJ2clDesignPreviewPage(null, "commit", 0L, "rel");
    // null context path should resolve to "/" so absolute paths remain
    // absolute (and not "j2cl/assets/..." which would be relative).
    assertTrue("null contextPath should resolve to /j2cl/assets",
        html.contains("\"/j2cl/assets/wavy-tokens.css\""));
  }

  public void testDoesNotIncludeShellRootBootstrap() {
    String html = HtmlRenderer.renderJ2clDesignPreviewPage("/", "commit", 0L, "rel");
    // Design preview is intentionally not the production shell — no
    // shell-root, no bootstrap script.
    assertFalse("design preview must NOT mount shell-root",
        html.contains("<shell-root data-j2cl-root-shell"));
    assertFalse("design preview must NOT include the root shell bootstrap script",
        html.contains("normalizeLegacyHashDeepLink()"));
  }
}
