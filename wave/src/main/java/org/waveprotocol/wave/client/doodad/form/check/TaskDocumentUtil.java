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
import org.waveprotocol.wave.model.conversation.TaskMetadataUtil;
import org.waveprotocol.wave.model.document.ReadableAnnotationSet;
import org.waveprotocol.wave.model.document.ReadableDocument;
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

  public static String getTaskAssignee(ReadableDocument<?, ?, ?> doc, int offset) {
    if (doc == null || offset < 0) {
      return null;
    }
    String assignee;
    try {
      assignee = getAnnotationValue(doc, offset, AnnotationConstants.TASK_ASSIGNEE);
    } catch (RuntimeException e) {
      return null;
    }
    if (assignee == null) {
      return null;
    }
    String normalizedAssignee = assignee.trim();
    return normalizedAssignee.isEmpty() ? null : normalizedAssignee;
  }

  public static long getTaskDueTimestamp(ReadableDocument<?, ?, ?> doc, int offset) {
    if (doc == null || offset < 0) {
      return -1L;
    }
    String dueTs;
    try {
      dueTs = getAnnotationValue(doc, offset, AnnotationConstants.TASK_DUE_TS);
    } catch (RuntimeException e) {
      return -1L;
    }
    if (dueTs == null) {
      return -1L;
    }
    try {
      long parsed = Long.parseLong(dueTs.trim());
      return parsed > 0 ? parsed : -1L;
    } catch (NumberFormatException e) {
      return -1L;
    }
  }

  public static void setTaskAssignee(CMutableDocument doc, int start, int end, String assignee) {
    String normalizedAssignee = assignee == null ? null : assignee.trim();
    doc.setAnnotation(start, end, AnnotationConstants.TASK_ASSIGNEE,
        normalizedAssignee == null || normalizedAssignee.isEmpty() ? null : normalizedAssignee);
  }

  public static void setTaskDueTimestamp(CMutableDocument doc, int start, int end, long ts) {
    if (ts <= 0) {
      clearTaskDueTimestamp(doc, start, end);
      return;
    }
    doc.setAnnotation(start, end, AnnotationConstants.TASK_DUE_TS, String.valueOf(ts));
  }

  public static void clearTaskDueTimestamp(CMutableDocument doc, int start, int end) {
    doc.setAnnotation(start, end, AnnotationConstants.TASK_DUE_TS, null);
  }

  public static String formatTaskAssigneeLabel(String participantAddress) {
    return TaskMetadataUtil.formatTaskAssigneeLabel(participantAddress);
  }

  public static String formatTaskDueLabel(long epochMillis) {
    return TaskMetadataUtil.formatTaskDueLabel(epochMillis);
  }

  public static long parseDateInputValue(String yyyyMmDd) {
    return TaskMetadataUtil.parseDateInputValue(yyyyMmDd);
  }

  public static String formatDateInputValue(long epochMillis) {
    return TaskMetadataUtil.formatDateInputValue(epochMillis);
  }

  public static String getTaskAssignee(ContentElement taskElement) {
    return taskElement == null ? null
        : getTaskAssignee(taskElement.getMutableDoc(), taskElement.getMutableDoc().getLocation(taskElement));
  }

  public static long getTaskDueTimestamp(ContentElement taskElement) {
    return taskElement == null ? -1L
        : getTaskDueTimestamp(taskElement.getMutableDoc(),
            taskElement.getMutableDoc().getLocation(taskElement));
  }

  public static void setTaskAssignee(ContentElement taskElement, String assignee) {
    if (taskElement == null) {
      return;
    }
    CMutableDocument doc = taskElement.getMutableDoc();
    int start = doc.getLocation(taskElement);
    setTaskAssignee(doc, start, start + 1, assignee);
  }

  public static void setTaskDueTimestamp(ContentElement taskElement, long ts) {
    if (taskElement == null) {
      return;
    }
    CMutableDocument doc = taskElement.getMutableDoc();
    int start = doc.getLocation(taskElement);
    setTaskDueTimestamp(doc, start, start + 1, ts);
  }

  public static void clearTaskDueTimestamp(ContentElement taskElement) {
    if (taskElement == null) {
      return;
    }
    CMutableDocument doc = taskElement.getMutableDoc();
    int start = doc.getLocation(taskElement);
    clearTaskDueTimestamp(doc, start, start + 1);
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
    setTaskAssignee(doc, start, end, assignee);

    return inserted;
  }

  @SuppressWarnings("unchecked")
  private static String getAnnotationValue(ReadableDocument<?, ?, ?> doc, int offset, String key) {
    if (!(doc instanceof ReadableAnnotationSet)) {
      return null;
    }
    return ((ReadableAnnotationSet<String>) doc).getAnnotation(offset, key);
  }
}
