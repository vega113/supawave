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
 * F-0 (#1035, A.10/M.1): the user-menu group previously labelled
 * "Automation / APIs" is renamed to "Plugins / Integrations" in both
 * the wave-client topbar (renderTopBar) and the standalone topbar
 * (renderSharedTopBarHtml). The anchor URLs are unchanged.
 */
public final class HtmlRendererPluginsMenuTest extends TestCase {
  public void testWaveClientTopBarRendersPluginsIntegrationsLabel() {
    String html = HtmlRenderer.renderTopBar("vega", "example.com", "user");
    assertTrue("wave-client topbar must render the new label",
        html.contains("section-label\">Plugins / Integrations"));
  }

  public void testWaveClientTopBarDoesNotRenderTheOldAutomationLabel() {
    String html = HtmlRenderer.renderTopBar("vega", "example.com", "user");
    assertFalse("wave-client topbar must NOT contain the old label",
        html.contains("Automation / APIs"));
  }

  public void testWaveClientTopBarStillLinksToRobotAndApiDocs() {
    String html = HtmlRenderer.renderTopBar("vega", "example.com", "user");
    assertTrue(html.contains("href=\"/account/robots\""));
    assertTrue(html.contains("href=\"/api-docs\""));
    assertTrue("Robot anchor preserves the section-link-strong CSS class",
        html.contains("section-link-strong"));
  }

  public void testStandaloneTopBarRendersPluginsIntegrationsLabel() {
    String html = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "/wave", "user");
    assertTrue("standalone topbar must render the new label",
        html.contains("section-label\">Plugins / Integrations"));
  }

  public void testStandaloneTopBarDoesNotRenderTheOldAutomationLabel() {
    String html = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "/wave", "user");
    assertFalse("standalone topbar must NOT contain the old label",
        html.contains("Automation / APIs"));
  }

  public void testStandaloneTopBarStillLinksToRobotAndApiDocs() {
    String html = HtmlRenderer.renderSharedTopBarHtml("vega@example.com", "/wave", "user");
    assertTrue(html.contains("href=\"/wave/account/robots\""));
    assertTrue(html.contains("href=\"/wave/api-docs\""));
    assertTrue(html.contains("section-link-strong"));
  }
}
