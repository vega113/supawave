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

package org.waveprotocol.examples.robots.gptbot;

import junit.framework.TestCase;

import java.util.Optional;

/**
 * Tests for explicit mention detection.
 */
public class MentionDetectorTest extends TestCase {

  public void testDetectsCommonMentionForms() {
    MentionDetector detector = new MentionDetector("gpt-bot");

    assertTrue(detector.extractPrompt("@gpt-bot what time is it?").isPresent());
    assertTrue(detector.extractPrompt("Please help, gpt bot").isPresent());
    assertTrue(detector.extractPrompt("gptbot summarize this wave").isPresent());
  }

  public void testExtractsPromptAfterTheMention() {
    MentionDetector detector = new MentionDetector("gpt-bot");

    Optional<String> prompt = detector.extractPrompt("@gpt-bot: write a friendly reply");

    assertTrue(prompt.isPresent());
    assertEquals("write a friendly reply", prompt.get());
  }

  public void testLeavesEmptyPromptWhenOnlyMentioned() {
    MentionDetector detector = new MentionDetector("gpt-bot");

    Optional<String> prompt = detector.extractPrompt("Hey @gpt-bot");

    assertTrue(prompt.isPresent());
    assertEquals("", prompt.get());
  }

  public void testIgnoresUnrelatedWords() {
    MentionDetector detector = new MentionDetector("gpt-bot");

    assertFalse(detector.extractPrompt("This is about a goat bot").isPresent());
    assertFalse(detector.extractPrompt("No mention here").isPresent());
  }

  public void testRejectsBlankRobotName() {
    try {
      new MentionDetector(" ");
      fail("Expected blank robot names to be rejected");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }
  }
}
