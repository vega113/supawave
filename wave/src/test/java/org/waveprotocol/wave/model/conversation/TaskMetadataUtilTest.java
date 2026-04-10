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

package org.waveprotocol.wave.model.conversation;

import junit.framework.TestCase;

/**
 * Unit tests for shared task metadata formatting and parsing helpers.
 */
public class TaskMetadataUtilTest extends TestCase {

  public void testFormatTaskAssigneeLabelOmitsOwnerPrefixForEmailAddress() {
    assertEquals("alice",
        TaskMetadataUtil.formatTaskAssigneeLabel("alice@example.com"));
  }

  public void testFormatParticipantDisplayStripsDomainForEmailAddress() {
    assertEquals("alice",
        TaskMetadataUtil.formatParticipantDisplay("alice@example.com"));
  }

  public void testFormatTaskAssigneeLabelKeepsOpaqueIdentifierWithoutPrefix() {
    assertEquals("build-bot",
        TaskMetadataUtil.formatTaskAssigneeLabel("build-bot"));
  }

  public void testFormatTaskAssigneeLabelReturnsEmptyForNull() {
    assertEquals("", TaskMetadataUtil.formatTaskAssigneeLabel(null));
  }

  public void testFormatTaskAssigneeLabelReturnsEmptyForBlank() {
    assertEquals("", TaskMetadataUtil.formatTaskAssigneeLabel("   "));
  }

  public void testParseDateInputValueRoundTripsThroughFormatter() {
    long dueTs = TaskMetadataUtil.parseDateInputValue("2026-04-15");
    assertEquals("2026-04-15", TaskMetadataUtil.formatDateInputValue(dueTs));
  }

  public void testParseDateInputValueRejectsInvalidDate() {
    assertEquals(-1L, TaskMetadataUtil.parseDateInputValue("2026-13-40"));
  }

  public void testFormatTaskDueLabelUsesMonthDay() {
    long dueTs = TaskMetadataUtil.parseDateInputValue("2026-04-15");
    assertEquals("Due Apr 15", TaskMetadataUtil.formatTaskDueLabel(dueTs));
  }

  public void testParseDateInputValueIsUtcMidnight() {
    // 2026-04-15T00:00:00Z = 1776211200000 ms since epoch
    assertEquals(1776211200000L, TaskMetadataUtil.parseDateInputValue("2026-04-15"));
  }

  public void testFormatDateInputValueHandlesUtcMidnight() {
    // 1776211200000 ms = 2026-04-15T00:00:00Z
    assertEquals("2026-04-15", TaskMetadataUtil.formatDateInputValue(1776211200000L));
  }

  public void testParseDateInputValueRejectsBlankInput() {
    assertEquals(-1L, TaskMetadataUtil.parseDateInputValue(""));
    assertEquals(-1L, TaskMetadataUtil.parseDateInputValue(null));
    assertEquals(-1L, TaskMetadataUtil.parseDateInputValue("   "));
  }

  public void testFormatDateInputValueReturnsEmptyForNegative() {
    assertEquals("", TaskMetadataUtil.formatDateInputValue(-1L));
  }

  public void testFormatTaskDueLabelReturnsEmptyForNegative() {
    assertEquals("", TaskMetadataUtil.formatTaskDueLabel(-1L));
  }
}
