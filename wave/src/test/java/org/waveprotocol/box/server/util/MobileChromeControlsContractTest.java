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

package org.waveprotocol.box.server.util;

import junit.framework.TestCase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source-level contract for the server-rendered mobile wave/tag controls. */
public final class MobileChromeControlsContractTest extends TestCase {

  public void testMobileControlsUseIconButtonsWithAccessibleLabels() throws Exception {
    String source = readHtmlRendererSource();

    assertContains(source, "mobile-control-svg");
    assertContains(source, "mobile-control-label");
    assertContains(source, "width: 44px");
    assertContains(source, "height: 44px");
    assertContains(source, "border-radius: 12px");
    assertContains(source, "title=\\\"Show tags tray\\\"");
    assertContains(source, "title=\\\"Pin tags tray\\\"");
  }

  public void testMobileControlStateSyncPreservesIconMarkup() throws Exception {
    String source = readHtmlRendererSource();

    assertContains(source, "function setToggleState(button, pressed, label)");
    assertContains(source, "button.setAttribute('title', label)");
    assertContains(source, "hiddenLabel.textContent = label");
    assertFalse("State sync must not replace icon markup with visible text",
        source.contains("button.textContent = text"));
    assertFalse("Tags tray control should not render as a visible text pill",
        source.contains("aria-pressed=\\\"false\\\">Tags</button>"));
    assertFalse("Tag pin control should not render as a visible text pill",
        source.contains("aria-pressed=\\\"false\\\">Tag Pin</button>"));
  }

  private static String readHtmlRendererSource() throws Exception {
    Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (root != null && !Files.exists(root.resolve("wave/src/jakarta-overrides/java"))) {
      root = root.getParent();
    }
    if (root == null) {
      fail("Could not locate repository root from " + System.getProperty("user.dir"));
    }
    byte[] bytes = Files.readAllBytes(root.resolve(
        "wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/HtmlRenderer.java"));
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static void assertContains(String source, String expected) {
    assertTrue("Expected source to contain: " + expected, source.contains(expected));
  }
}
