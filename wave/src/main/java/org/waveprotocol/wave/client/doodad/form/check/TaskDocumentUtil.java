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

import org.waveprotocol.wave.client.editor.content.CMutableDocument;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.ContentNode;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;

/**
 * Utility for inserting task checkboxes into wave documents.
 *
 * <p>A task consists of a {@code <check>} element with a "task:" prefixed name
 * and task/* annotations ({@link AnnotationConstants#TASK_ID},
 * {@link AnnotationConstants#TASK_ASSIGNEE}) applied to the surrounding
 * text range.
 */
public final class TaskDocumentUtil {

  /** Prefix applied to the check element name to mark it as a task checkbox. */
  public static final String TASK_NAME_PREFIX = "task:";

  private TaskDocumentUtil() {}

  /**
   * Generates a unique task ID using a timestamp and random suffix.
   * The resulting ID is short and URL-safe (base-36 encoded).
   */
  public static String generateTaskId() {
    // Use current time millis in base36 + 30-bit random suffix (base36) for collision resistance.
    // Integer.toString(r + 0x40000000, 36) gives 7 base-36 chars with 30 bits of entropy.
    // Both Long.toString and Integer.toString with radix are GWT JRE-emulation compatible.
    int r = (int) (Math.random() * 0x7FFFFFFF);
    return "t" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(r + 0x40000000, 36);
  }

  /**
   * Builds the XML for a task checkbox element.
   * Uses the existing {@link CheckBox#constructXml} pattern with a
   * task-prefixed name.
   *
   * @param taskId unique task identifier
   * @return XML string builder for the check element
   */
  public static XmlStringBuilder constructTaskXml(String taskId) {
    return CheckBox.constructXml(TASK_NAME_PREFIX + taskId, false);
  }

  /**
   * Inserts a task checkbox at the given point in the document and applies
   * task annotations to a small range covering the checkbox.
   *
   * @param doc the mutable document
   * @param point insertion point
   * @param taskId the task ID
   * @param assignee participant address of the assignee, or null
   * @return the inserted content element
   */
  public static ContentElement insertTask(CMutableDocument doc, Point<ContentNode> point,
      String taskId, String assignee) {
    XmlStringBuilder xml = constructTaskXml(taskId);
    ContentElement inserted = doc.insertXml(point, xml);

    // Apply task/id annotation to the element range
    int start = doc.getLocation(inserted);
    int end = start + 1;  // check element is a single node

    doc.setAnnotation(start, end, AnnotationConstants.TASK_ID, taskId);
    if (assignee != null) {
      String normalizedAssignee = assignee.trim();
      if (!normalizedAssignee.isEmpty()) {
        doc.setAnnotation(start, end, AnnotationConstants.TASK_ASSIGNEE, normalizedAssignee);
      }
    }

    return inserted;
  }
}
