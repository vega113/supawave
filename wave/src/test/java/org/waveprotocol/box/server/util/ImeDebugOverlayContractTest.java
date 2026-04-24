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

/**
 * Source-level JSNI contract test. The normal JVM test source set excludes most
 * client test packages, so this verifies client overlay invariants from here.
 */
public final class ImeDebugOverlayContractTest extends TestCase {

  public void testOverlayStartsMinimizedWithTouchFriendlyControls() throws Exception {
    String source = readTracerSource();

    assertContains(source, "ime-debug-overlay-minimized");
    assertContains(source, "setExpanded(false)");
    assertContains(source, "Show IME log");
    assertContains(source, "Hide IME log");
    assertContains(source, "style.display = \"none\"");
    assertContains(source, "collapsedHeight = \"44px\"");
    assertContains(source, "toolbar.style.height = collapsedHeight");
    assertContains(source, "toolbar.style.padding = \"0 6px\"");
    assertFalse("Overlay must not start with the previous expanded 45% height",
        source.contains("max-height:45%"));
  }

  public void testOverlayProvidesCopyAndClearButtons() throws Exception {
    String source = readTracerSource();

    assertContains(source, "Copy IME debug log");
    assertContains(source, "Clear IME debug log");
    assertContains(source, "navigator.clipboard.writeText");
    assertContains(source, "execCommand('copy')");
    assertContains(source, "createElement(\"textarea\")");
    assertContains(source, "removeChild(textarea)");
    assertContains(source, "copyLogText");
    assertContains(source, "No IME log lines yet");
    assertContains(source, "IME log copied");
  }

  public void testLogRowsAppendInsideScrollableLogBody() throws Exception {
    String source = readTracerSource();

    assertContains(source, "var log = getLogBody(d)");
    assertContains(source, "log.appendChild(row)");
    assertContains(source, "log.children.length > @org.waveprotocol.wave.client.editor.debug.ImeDebugTracer::MAX_OVERLAY_LINES");
    assertContains(source, "log.removeChild(log.firstChild)");
    assertContains(source, "log.scrollTop = log.scrollHeight");
    assertFalse("Rows should no longer append directly to the overlay chrome",
        source.contains("ov.appendChild(row)"));
  }

  public void testMinimizedOverlayReservesBottomEditingSpace() throws Exception {
    String source = readTracerSource();

    assertContains(source, "reserveBodyBottomSpace");
    assertContains(source, "data-ime-debug-body-padding-reserved");
    assertContains(source, "safeAreaInset = \"env(safe-area-inset-bottom, 0px)\"");
    assertContains(source, "style.paddingBottom");
    assertContains(source, " + collapsedHeight + ");
    assertContains(source, " + safeAreaInset");
  }

  public void testConsoleAndRemoteLoggingCallsRemainPresent() throws Exception {
    String source = readTracerSource();

    assertContains(source, "consoleLogJsni(");
    assertContains(source, "queueRemoteLogJsni(");
    assertContains(source, "queueRemoteLogJsniBridge");
  }

  private static String readTracerSource() throws Exception {
    Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (root != null && !Files.exists(root.resolve("wave/src/main/java"))) {
      root = root.getParent();
    }
    if (root == null) {
      fail("Could not locate repository root from " + System.getProperty("user.dir"));
    }
    byte[] bytes = Files.readAllBytes(root.resolve(
        "wave/src/main/java/org/waveprotocol/wave/client/editor/debug/ImeDebugTracer.java"));
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static void assertContains(String source, String expected) {
    assertTrue("Expected source to contain: " + expected, source.contains(expected));
  }
}
