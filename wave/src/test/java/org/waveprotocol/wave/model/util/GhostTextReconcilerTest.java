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

package org.waveprotocol.wave.model.util;

import junit.framework.TestCase;

/**
 * Tests for {@link GhostTextReconciler}, which guards the Android IME
 * "new blip" → "ewlip" regression by re-uniting the scratch-span contents
 * with any ghost characters that the browser inserted into the adjacent
 * DOM text nodes during composition. See the class-level Javadoc on
 * {@link GhostTextReconciler} for the full phone-only failure mode.
 */
public class GhostTextReconcilerTest extends TestCase {

  public void testCombineWithoutGhostReturnsScratchOnly() {
    assertEquals("new", GhostTextReconciler.combine(
        "new", null, null, null, null));
    assertEquals("new", GhostTextReconciler.combine(
        "new", "", "", "", ""));
    assertEquals("new", GhostTextReconciler.combine(
        "new", "hello", "hello", "world", "world"));
  }

  public void testGhostAppendedToPreviousSiblingEmptyBaseline() {
    // The canonical "new blip" → "ewlip" case. Baseline is an empty text
    // node before the IME scratch. Android Chrome inserted 'n' there
    // instead of into the scratch; scratch has only "ew". Effective
    // composition must reunite them as "new".
    assertEquals("new", GhostTextReconciler.combine(
        "ew", "", "n", null, null));
  }

  public void testGhostAppendedToPreviousSiblingWithExistingBaseline() {
    // Caret sat at the end of "hello"; the pre-composition flush split the
    // text node and the ghost insertion appended 'n' to the "hello" node
    // that ended up immediately before the scratch.
    assertEquals("new", GhostTextReconciler.combine(
        "ew", "hello", "hellon", null, null));
  }

  public void testGhostPrependedToNextSibling() {
    // Reverse case: ghost lands at the start of the sibling that lives
    // after the scratch. Rarer but symmetric.
    assertEquals("new", GhostTextReconciler.combine(
        "ne", null, null, "world", "wworld"));
  }

  public void testGhostOnBothSides() {
    assertEquals("new", GhostTextReconciler.combine(
        "e", "hello", "hellon", "world", "wworld"));
  }

  public void testGhostRequiresBaselinePrefixOnPreviousSibling() {
    // If the current previous-sibling text doesn't start with the recorded
    // baseline, something else rewrote the node during composition. We
    // can't tell what is ghost vs. reflow, so fall back to the scratch
    // only rather than inventing text.
    assertEquals("ew", GhostTextReconciler.combine(
        "ew", "hello", "xhellon", null, null));
  }

  public void testGhostRequiresBaselineSuffixOnNextSibling() {
    assertEquals("ne", GhostTextReconciler.combine(
        "ne", null, null, "world", "wworldx"));
  }

  public void testShorterThanBaselineTreatedAsNoGhost() {
    // If the "current" text is shorter than the baseline, the node was
    // cleared or collapsed — don't invent ghost text.
    assertEquals("ew", GhostTextReconciler.combine(
        "ew", "hello", "hell", null, null));
  }

  public void testEmptyScratchWithGhostOnly() {
    // Degenerate case: the browser put the whole composition outside the
    // scratch. Rare, but we should still return the ghost rather than
    // losing everything.
    assertEquals("new", GhostTextReconciler.combine(
        "", "", "new", null, null));
  }

  public void testSecondWordSpaceCapturedAsGhost() {
    // Second cycle of "new blip": after the first word commits, the
    // browser inserts a space into the previous text node ("new") then
    // begins composing the next word. If the baseline for the new scratch
    // was captured after "new" committed but before the space was
    // inserted, baseline="new", currentPrevious="new b", and the new
    // scratch carries "lip" — effective composition is " blip", which
    // preserves both the separator and the first char.
    assertEquals(" blip", GhostTextReconciler.combine(
        "lip", "new", "new b", null, null));
  }

  public void testPreviousModelBaselineRecoversGhostPresentBeforeSnapshot() {
    String baseline = GhostTextReconciler.modelAwarePreviousBaseline("", "n");

    assertEquals("", baseline);
    assertEquals("new", GhostTextReconciler.combine(
        "ew", baseline, "n", null, null));
  }

  public void testPreviousModelBaselineRecoversSecondWordBeforeSnapshot() {
    String baseline = GhostTextReconciler.modelAwarePreviousBaseline(
        "new", "new b");

    assertEquals("new", baseline);
    assertEquals(" blip", GhostTextReconciler.combine(
        "lip", baseline, "new b", null, null));
  }

  public void testNextModelBaselineRecoversGhostPresentBeforeSnapshot() {
    String baseline = GhostTextReconciler.modelAwareNextBaseline(
        "world", "wworld");

    assertEquals("world", baseline);
    assertEquals("new", GhostTextReconciler.combine(
        "ne", null, null, baseline, "wworld"));
  }

  public void testModelBaselineFallsBackWhenCapturedDomDoesNotContainModel() {
    assertEquals("NEW b", GhostTextReconciler.modelAwarePreviousBaseline(
        "new", "NEW b"));
    assertEquals("bNEW", GhostTextReconciler.modelAwareNextBaseline(
        "new", "bNEW"));
  }

  public void testNullScratchThrows() {
    try {
      GhostTextReconciler.combine(null, null, null, null, null);
      fail("Expected NullPointerException for null scratchContent");
    } catch (NullPointerException expected) {
      // pass
    }
  }

  public void testExtractGhostSuffixHandlesNulls() {
    assertEquals("", GhostTextReconciler.extractGhostSuffix(null, null));
    assertEquals("", GhostTextReconciler.extractGhostSuffix("", null));
    assertEquals("", GhostTextReconciler.extractGhostSuffix(null, ""));
    assertEquals("", GhostTextReconciler.extractGhostSuffix("abc", "abc"));
    assertEquals("d", GhostTextReconciler.extractGhostSuffix("abc", "abcd"));
  }

  public void testRestorePreviousSiblingOnlyRemovesRecognizedGhostSuffix() {
    assertEquals("abc",
        GhostTextReconciler.restorePreviousSiblingText("abc", "abcd"));
    assertEquals("ab",
        GhostTextReconciler.restorePreviousSiblingText("abc", "ab"));
    assertEquals("xabc",
        GhostTextReconciler.restorePreviousSiblingText("abc", "xabc"));
  }

  public void testExtractGhostPrefixHandlesNulls() {
    assertEquals("", GhostTextReconciler.extractGhostPrefix(null, null));
    assertEquals("", GhostTextReconciler.extractGhostPrefix("", null));
    assertEquals("", GhostTextReconciler.extractGhostPrefix(null, ""));
    assertEquals("", GhostTextReconciler.extractGhostPrefix("abc", "abc"));
    assertEquals("z", GhostTextReconciler.extractGhostPrefix("abc", "zabc"));
  }

  public void testRestoreNextSiblingOnlyRemovesRecognizedGhostPrefix() {
    assertEquals("abc",
        GhostTextReconciler.restoreNextSiblingText("abc", "zabc"));
    assertEquals("bc",
        GhostTextReconciler.restoreNextSiblingText("abc", "bc"));
    assertEquals("abcx",
        GhostTextReconciler.restoreNextSiblingText("abc", "abcx"));
  }
}
