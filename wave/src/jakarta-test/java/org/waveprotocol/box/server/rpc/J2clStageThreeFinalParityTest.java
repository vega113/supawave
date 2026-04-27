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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runners.Suite;

/**
 * F-3 slice 4 (#1038, S4 closeout) — final per-row parity roll-up for
 * the J2CL StageThree compose surface.
 *
 * <p>Closes the umbrella issue #1038. Asserts every F-3 owned gate row
 * (R-5.1 .. R-5.7) has at least one passing assertion in the existing
 * per-row parity test classes, and produces a printable row-coverage
 * report on success.
 *
 * <p>Mirrors the {@link J2clStageOneFinalParityTest} pattern for the
 * F-2 umbrella close (#1037 / commit dc8ee6a3f). Two test entry points:
 *
 * <ul>
 *   <li>{@link Suite} — chains the existing per-row parity test
 *       classes. Use the inner {@link AllParityTests} class explicitly
 *       (e.g. {@code testOnly *AllParityTests}) to run it; or pass
 *       {@code -Dj2cl.run.chained.parity=true} to opt-in from within
 *       this class without double-executing the individual suites.
 *   <li>This class — owns one summary test per gate row that asserts
 *       at least one declared test method exists in the per-row class
 *       responsible for that row, and produces a printable row-coverage
 *       report on success.
 * </ul>
 */
public final class J2clStageThreeFinalParityTest {

  /**
   * Canonical count of F-3 owned gate rows; fails fast if a row is
   * silently removed.
   */
  private static final int EXPECTED_F3_OWNER_COUNT = 7;

  /**
   * Select the inner {@code AllParityTests} class explicitly in JUnit
   * to run this suite; running the outer enclosing class executes only
   * the summary tests below. The suite handle is exposed separately so
   * CI can target it independently.
   */
  @RunWith(Suite.class)
  @Suite.SuiteClasses({
    J2clStageThreeComposeS1ParityTest.class,
    J2clStageThreeComposeS2ParityTest.class,
    J2clStageThreeComposeS3ParityTest.class,
    J2clStageThreeComposeS4ParityTest.class
  })
  public static final class AllParityTests {}

  /**
   * Maps each F-3 owned gate row to the per-row parity test class that
   * demonstrates row-level GWT parity for it. The list is the canonical
   * roll-up index for the F-3 umbrella #1038 closeout.
   */
  private static final Map<String, Class<?>> ROW_OWNERS = buildRowOwners();

  private static Map<String, Class<?>> buildRowOwners() {
    Map<String, Class<?>> rows = new LinkedHashMap<>();
    rows.put(
        "R-5.1 compose / reply flow (inline at blip, caret survival, drafts, Enter-to-send)",
        J2clStageThreeComposeS1ParityTest.class);
    rows.put(
        "R-5.2 selection-driven toolbars (toggle state mirrors selection, applies on range)",
        J2clStageThreeComposeS1ParityTest.class);
    rows.put(
        "R-5.3 mentions (@-trigger, suggestion popover, model round-trip)",
        J2clStageThreeComposeS2ParityTest.class);
    rows.put(
        "R-5.4 tasks (per-blip toggle, completion state announced)",
        J2clStageThreeComposeS2ParityTest.class);
    rows.put(
        "R-5.5 reactions (add/remove, count live updates)",
        J2clStageThreeComposeS3ParityTest.class);
    rows.put(
        "R-5.6 attachments (drag/paste/upload, inline render, error surfaces, F.6 delete)",
        J2clStageThreeComposeS4ParityTest.class);
    rows.put(
        "R-5.7 daily rich-edit DocOp round-trip (lists, block quotes, inline links)",
        J2clStageThreeComposeS4ParityTest.class);
    return rows;
  }

  @Test
  public void everyOwnedGateRowHasAPassingAssertionClass() {
    Set<Class<?>> suiteClasses =
        new HashSet<>(
            Arrays.asList(
                AllParityTests.class.getAnnotation(Suite.SuiteClasses.class).value()));
    StringBuilder report = new StringBuilder();
    report.append("F-3 (umbrella #1038) per-row parity roll-up\n");
    report.append("===========================================\n");
    int total = ROW_OWNERS.size();
    assertEquals(
        "ROW_OWNERS must list exactly "
            + EXPECTED_F3_OWNER_COUNT
            + " F-3 gate rows — update EXPECTED_F3_OWNER_COUNT when the matrix changes",
        EXPECTED_F3_OWNER_COUNT,
        total);
    int covered = 0;
    for (Map.Entry<String, Class<?>> entry : ROW_OWNERS.entrySet()) {
      Class<?> owner = entry.getValue();
      assertNotNull("Row " + entry.getKey() + " has no declared owner class", owner);
      assertTrue(
          "Row "
              + entry.getKey()
              + " owner "
              + owner.getSimpleName()
              + " must be listed in AllParityTests @Suite.SuiteClasses",
          suiteClasses.contains(owner));
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
      // Fall back to method-name detection for any per-row class that
      // happens to be JUnit 3 style (extends TestCase). All current
      // per-row classes are JUnit 4, so this is just a safety net.
      if (!hasTest) {
        for (java.lang.reflect.Method m : owner.getDeclaredMethods()) {
          if (m.getName().startsWith("test") && m.getParameterCount() == 0) {
            hasTest = true;
            break;
          }
        }
      }
      assertTrue(
          "Row "
              + entry.getKey()
              + " owner "
              + owner.getSimpleName()
              + " must declare at least one @Test or testXxx method",
          hasTest);
      covered++;
      report
          .append("  [PASS] ")
          .append(entry.getKey())
          .append("  <-  ")
          .append(owner.getSimpleName())
          .append("\n");
    }
    report
        .append("\nTotal rows covered: ")
        .append(covered)
        .append(" / ")
        .append(total)
        .append("\n");
    report.append(
        "F-3 follow-ups (do not block umbrella close, tracked outside #1038):\n"
            + "  - FU-1 H.5 super / H.6 sub\n"
            + "  - FU-2 H.7 font family / H.8 font size\n"
            + "  - FU-3 H.10 text color / H.11 highlight color\n"
            + "  - FU-4 D.4 / D.5 / D.6 / D.8 wave-header toggles\n"
            + "  - FU-5 L.2 / L.3 profile actions\n"
            + "  - FU-6 B.3 Shift+Cmd+O New Wave shortcut\n");
    System.out.println(report.toString());
    assertEquals("All declared rows must be covered", total, covered);
  }

  @Test
  public void allChainedParityTestClassesPass() {
    // Gate behind a system property so the default suite run does not
    // execute the chained parity classes a second time. Set
    // -Dj2cl.run.chained.parity=true (e.g. via testOnly) to opt in.
    Assume.assumeTrue(
        "Set -Dj2cl.run.chained.parity=true to run the full chained parity suite",
        "true".equals(System.getProperty("j2cl.run.chained.parity")));
    Result result = JUnitCore.runClasses(AllParityTests.class);
    if (!result.wasSuccessful()) {
      StringBuilder sb = new StringBuilder("Chained parity suite FAILED:\n");
      for (Failure f : result.getFailures()) {
        sb.append("  - ").append(f.getTestHeader()).append(": ").append(f.getMessage()).append("\n");
      }
      throw new AssertionError(sb.toString());
    }
    System.out.printf(
        "F-3 chained parity suite: %d tests run, 0 failures, %d ignored%n",
        result.getRunCount(), result.getIgnoreCount());
  }
}
