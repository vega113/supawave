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

package org.waveprotocol.wave.client.doodad.form.check;

import junit.framework.TestCase;

import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link TaskDocumentUtil}.
 */
public class TaskDocumentUtilTest extends TestCase {

  public void testGenerateTaskIdReturnsNonNullNonEmpty() {
    String id = TaskDocumentUtil.generateTaskId();
    assertNotNull("Task ID must not be null", id);
    assertTrue("Task ID must not be empty", id.length() > 0);
  }

  public void testGenerateTaskIdStartsWithPrefix() {
    String id = TaskDocumentUtil.generateTaskId();
    assertTrue("Task ID should start with 't'", id.startsWith("t"));
  }

  public void testGenerateTaskIdProducesUniqueValues() {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      ids.add(TaskDocumentUtil.generateTaskId());
    }
    // With timestamp + random, 100 IDs should all be unique
    assertEquals("100 generated IDs should all be unique", 100, ids.size());
  }

  public void testConstructTaskXmlContainsCheckTag() {
    String taskId = "testid123";
    String xml = TaskDocumentUtil.constructTaskXml(taskId).getXmlString();
    assertTrue("XML should contain <check tag", xml.contains("<check"));
    assertTrue("XML should contain task-prefixed name",
        xml.contains("task:" + taskId));
    assertTrue("XML should contain value=\"false\"",
        xml.contains("value=\"false\""));
  }

  public void testConstructTaskXmlMatchesCheckBoxPattern() {
    String taskId = "abc";
    String taskXml = TaskDocumentUtil.constructTaskXml(taskId).getXmlString();
    String checkXml = CheckBox.constructXml("task:" + taskId, false).getXmlString();
    assertEquals("Task XML should match CheckBox.constructXml output", checkXml, taskXml);
  }

  public void testTaskNamePrefix() {
    assertEquals("task:", TaskDocumentUtil.TASK_NAME_PREFIX);
  }
}
