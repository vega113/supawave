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

  public void testAndroidSingleLetterReplacementSequenceRecoversNew() {
    ImeCompositionTextTracker tracker = new ImeCompositionTextTracker();

    tracker.observe("n");
    tracker.observe("e");
    tracker.observe("e");
    tracker.observe("ew");
    tracker.observe("ew");

    assertEquals("new", tracker.effectiveText("ew"));
  }

  public void testAndroidSecondWordReplacementSequenceRecoversBlip() {
    ImeCompositionTextTracker tracker = new ImeCompositionTextTracker();

    tracker.observe("b");
    tracker.observe("l");
    tracker.observe("l");
    tracker.observe("li");
    tracker.observe("li");
    tracker.observe("lip");

    assertEquals("blip", tracker.effectiveText("lip"));
  }

  public void testRepeatedSingleCharacterDoesNotDuplicate() {
    ImeCompositionTextTracker tracker = new ImeCompositionTextTracker();

    tracker.observe("n");
    tracker.observe("e");
    tracker.observe("e");

    assertEquals("ne", tracker.effectiveText("e"));
  }

  public void testBrowserCaughtUpScratchWins() {
    ImeCompositionTextTracker tracker = new ImeCompositionTextTracker();

    tracker.observe("n");
    tracker.observe("e");

    assertEquals("new", tracker.effectiveText("new"));
  }

  public void testUnrelatedMultiCharacterReplacementFallsBackToScratch() {
    ImeCompositionTextTracker tracker = new ImeCompositionTextTracker();

    tracker.observe("ab");
    tracker.observe("c");

    assertEquals("c", tracker.effectiveText("c"));
  }
}
