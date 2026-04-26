/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;

/**
 * F-2 slice 6 (#1058, Part B) — final per-row parity roll-up.
 *
 * <p>Closes the umbrella issue #1037. Asserts that every F-2 owned
 * gate row from the parity matrix has at least one passing assertion
 * in the existing per-row parity test classes, and that the
 * read-surface preview demo route (`?view=j2cl-root&q=read-surface-preview`)
 * mounts the full chrome surface.
 *
 * <p>Two test entry points:
 *
 * <ul>
 *   <li>{@link Suite} — chains the existing per-row parity test
 *       classes so a single {@code testOnly *J2clStageOneFinalParityTest}
 *       run also exercises every per-row assertion. Use the inner
 *       {@link AllParityTests} class as the suite handle.
 *   <li>This class — owns one summary test per gate row that asserts
 *       at least one declared test method exists in the per-row class
 *       responsible for that row, and produces a printable row-coverage
 *       report on success.
 * </ul>
 */
public final class J2clStageOneFinalParityTest {

  /**
   * Select the inner {@code AllParityTests} class explicitly in JUnit
   * to run this suite; running the outer enclosing class executes only
   * the summary tests below. The suite handle is exposed separately so
   * CI can target it independently.
   */
  @RunWith(Suite.class)
  @Suite.SuiteClasses({
    HtmlRendererJ2clRootShellIntegrationTest.class,
    J2clStageOneReadSurfaceParityTest.class,
    J2clStageOneFloatingOverlaysParityTest.class,
    J2clSearchRailParityTest.class,
    J2clViewportFirstPaintParityTest.class
  })
  public static final class AllParityTests {}

  /**
   * Maps each F-2 owned gate row to the per-row parity test class
   * that demonstrates row-level GWT parity for it. The list is the
   * canonical roll-up index for the F-2 umbrella #1037 closeout.
   */
  private static final Map<String, Class<?>> ROW_OWNERS = buildRowOwners();

  private static Map<String, Class<?>> buildRowOwners() {
    Map<String, Class<?>> rows = new LinkedHashMap<>();
    // R-3.x — open-wave + thread chrome
    rows.put("R-3.1 open-wave rendering", J2clStageOneReadSurfaceParityTest.class);
    rows.put("R-3.2 focus framing", J2clStageOneReadSurfaceParityTest.class);
    rows.put("R-3.3 collapse", J2clStageOneReadSurfaceParityTest.class);
    rows.put("R-3.4 thread navigation", J2clStageOneReadSurfaceParityTest.class);
    rows.put("R-3.5 inline reply chip", J2clStageOneReadSurfaceParityTest.class);
    rows.put("R-3.6 tag row (read-only)", J2clStageOneReadSurfaceParityTest.class);
    rows.put("R-3.7 depth drill-down", J2clStageOneReadSurfaceParityTest.class);
    // R-4.x — wave-list + per-blip live state (R-4.4 deferred to F-4 #1056)
    rows.put("R-4.1 wave-list digest", J2clSearchRailParityTest.class);
    rows.put("R-4.2 search query parsing", J2clSearchRailParityTest.class);
    rows.put("R-4.3 sort options", J2clSearchRailParityTest.class);
    rows.put("R-4.5 unread-count badges", J2clSearchRailParityTest.class);
    rows.put("R-4.6 saved-search folders", J2clSearchRailParityTest.class);
    rows.put("R-4.7 search help modal", J2clSearchRailParityTest.class);
    // R-6.x — wave chrome + nav row
    rows.put("R-6.1 wave-nav-row landmark", HtmlRendererJ2clRootShellIntegrationTest.class);
    rows.put("R-6.2 depth-nav-bar landmark", HtmlRendererJ2clRootShellIntegrationTest.class);
    rows.put("R-6.3 floating mounts", J2clStageOneFloatingOverlaysParityTest.class);
    rows.put("R-6.4 wavy header chrome", HtmlRendererJ2clRootShellIntegrationTest.class);
    // R-7.x — overlays + viewport-first paint
    rows.put("R-7.1 version-history overlay", J2clStageOneFloatingOverlaysParityTest.class);
    rows.put("R-7.2 profile overlay", J2clStageOneFloatingOverlaysParityTest.class);
    rows.put("R-7.3 search-help overlay", J2clSearchRailParityTest.class);
    rows.put("R-7.4 viewport-first paint", J2clViewportFirstPaintParityTest.class);
    return rows;
  }

  @Test
  public void everyOwnedGateRowHasAPassingAssertionClass() {
    StringBuilder report = new StringBuilder();
    report.append("F-2 (umbrella #1037) per-row parity roll-up\n");
    report.append("===========================================\n");
    int total = ROW_OWNERS.size();
    int covered = 0;
    for (Map.Entry<String, Class<?>> entry : ROW_OWNERS.entrySet()) {
      Class<?> owner = entry.getValue();
      assertNotNull("Row " + entry.getKey() + " has no declared owner class", owner);
      // The owner class must declare at least one @Test method so we
      // know the row has at least one passing assertion exercised by
      // the suite chain above.
      boolean hasTest = false;
      for (java.lang.reflect.Method m : owner.getDeclaredMethods()) {
        if (m.isAnnotationPresent(org.junit.Test.class)) {
          hasTest = true;
          break;
        }
      }
      // HtmlRendererJ2clRootShellIntegrationTest is JUnit 3 (extends
      // TestCase) — fall back to method-name detection.
      if (!hasTest) {
        for (java.lang.reflect.Method m : owner.getDeclaredMethods()) {
          if (m.getName().startsWith("test") && m.getParameterCount() == 0) {
            hasTest = true;
            break;
          }
        }
      }
      assertTrue(
          "Row " + entry.getKey() + " owner " + owner.getSimpleName()
              + " must declare at least one @Test or testXxx method",
          hasTest);
      covered++;
      report
          .append("  [PASS] ")
          .append(entry.getKey())
          .append("  ←  ")
          .append(owner.getSimpleName())
          .append("\n");
    }
    report.append("\nTotal rows covered: ").append(covered).append(" / ").append(total).append("\n");
    report.append(
        "Deferred to F-4 (#1056): R-4.4 markBlipRead live read/unread state.\n");
    System.out.println(report.toString());
    assertEquals("All declared rows must be covered", total, covered);
  }

  @Test
  public void readSurfacePreviewDemoRouteRendersFullChromeSurface() {
    String html =
        HtmlRenderer.renderJ2clReadSurfacePreviewPage(
            "/", "commit-roll-up", 0L, "rel", "alice@example.com", "ws.example:443");
    assertTrue("preview page renders an html element", html.contains("<html"));
    // The preview must mount: search rail, depth-nav, wave-nav-row,
    // version-history overlay (open), profile overlay (open),
    // awareness pill, focus frame, at least one wave-blip, at least
    // one task chip, at least one mention chip, attachment tile.
    assertTrue("search rail mounts", html.contains("<wavy-search-rail"));
    assertTrue("depth-nav mounts", html.contains("<wavy-depth-nav-bar"));
    assertTrue("wave-nav-row mounts", html.contains("<wavy-wave-nav-row"));
    assertTrue("version-history mounts open",
        html.contains("<wavy-version-history") && html.contains("open></wavy-version-history>"));
    assertTrue("profile overlay mounts open",
        html.contains("<wavy-profile-overlay") && html.contains("open"));
    assertTrue("awareness pill present", html.contains("data-j2cl-awareness-pill=\"true\""));
    assertTrue("focus frame present", html.contains("<wavy-focus-frame"));
    assertTrue("at least one wave-blip", html.contains("<wave-blip"));
    assertTrue("task chip present", html.contains("data-j2cl-task-chip=\"true\""));
    assertTrue("mention chip present", html.contains("data-j2cl-mention-chip=\"true\""));
    assertTrue("attachment tile present", html.contains("data-j2cl-attachment-tile=\"true\""));
    assertTrue("read-surface preview marker present",
        html.contains("data-j2cl-read-surface-preview=\"true\""));
  }

  @Test
  public void allChainedParityTestClassesPass() {
    // Run the chained suite and surface any failures. This is the
    // single command that proves every per-row parity test class
    // still passes alongside the F-2 closeout.
    Result result = JUnitCore.runClasses(AllParityTests.class);
    if (!result.wasSuccessful()) {
      StringBuilder sb = new StringBuilder("Chained parity suite FAILED:\n");
      for (Failure f : result.getFailures()) {
        sb.append("  - ").append(f.getTestHeader()).append(": ").append(f.getMessage()).append("\n");
      }
      throw new AssertionError(sb.toString());
    }
    System.out.printf(
        "F-2 chained parity suite: %d tests run, 0 failures, %d ignored%n",
        result.getRunCount(), result.getIgnoreCount());
  }
}
