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
 * Tests for Android Chrome composition streams where the browser replaces the
 * whole scratch text with a shortened value while the user is still typing.
 */
public class ImeCompositionTextTrackerTest extends TestCase {

  private void assertImeSequence(String expectedText, String scratchText, String... observedValues) {
    ImeCompositionTextTracker tracker = new ImeCompositionTextTracker();
    for (String v : observedValues) {
      tracker.observe(v);
    }
    assertEquals(expectedText, tracker.effectiveText(scratchText));
  }

  public void testAndroidSingleLetterReplacementSequenceRecoversNew() {
    assertImeSequence("new", "ew", "n", "e", "e", "ew", "ew");
  }

  public void testAndroidSecondWordReplacementSequenceRecoversBlip() {
    assertImeSequence("blip", "lip", "b", "l", "l", "li", "li", "lip");
  }

  public void testAndroidReplacementCanRecoverWhenScratchExtendsPendingValue() {
    assertImeSequence("new", "ew", "n", "e", "ew");
  }

  public void testRepeatedSingleCharacterDoesNotDuplicate() {
    assertImeSequence("ne", "e", "n", "e", "e");
  }

  public void testBrowserCaughtUpScratchWins() {
    assertImeSequence("new", "new", "n", "e");
  }

  public void testUnrelatedMultiCharacterReplacementFallsBackToScratch() {
    assertImeSequence("c", "c", "ab", "c");
  }

  public void testEmptyUpdateClearsRecoveredText() {
    assertImeSequence("", "", "a", "");
  }

  public void testShrinkWithoutReplacementEvidenceKeepsScratch() {
    assertImeSequence("bc", "bc", "abc", "bc");
  }

  public void testSingleCharacterDiacriticReplacementKeepsScratch() {
    assertImeSequence("\u00e1", "\u00e1", "a", "\u00e1", "\u00e1");
  }

  public void testNullObservedValueIsIgnored() {
    ImeCompositionTextTracker tracker = new ImeCompositionTextTracker();
    tracker.observe("n");
    tracker.observe(null);
    assertEquals("n", tracker.effectiveText("n"));
  }

  public void testNullScratchFallsBackToEmptyString() {
    ImeCompositionTextTracker tracker = new ImeCompositionTextTracker();
    tracker.observe("n");
    assertEquals("", tracker.effectiveText(null));
  }
}
