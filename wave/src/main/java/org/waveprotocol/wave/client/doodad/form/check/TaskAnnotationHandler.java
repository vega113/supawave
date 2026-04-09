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

import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.Registries;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.AnnotationMutationHandler;
import org.waveprotocol.wave.model.document.util.DocumentContext;
import org.waveprotocol.wave.model.document.util.Point;

/**
 * Refreshes task metadata pills when task annotations change.
 */
public final class TaskAnnotationHandler implements AnnotationMutationHandler {

  public static void register(Registries registries) {
    registries.getAnnotationHandlerRegistry().registerHandler(
        AnnotationConstants.TASK_PREFIX, new TaskAnnotationHandler());
  }

  @Override
  public <N, E extends N, T extends N> void handleAnnotationChange(
      DocumentContext<N, E, T> bundle, int start, int end, String key, Object newValue) {
    ContentElement taskElement = findTaskElement(bundle, start);
    if (taskElement == null && end > start) {
      taskElement = findTaskElement(bundle, end - 1);
    }
    if (taskElement != null) {
      CheckBox.refreshTaskMetadata(taskElement);
    }
  }

  private static <N, E extends N, T extends N> ContentElement findTaskElement(
      DocumentContext<N, E, T> bundle, int location) {
    Point<N> point = bundle.locationMapper().locate(location);
    if (point == null) {
      return null;
    }
    ContentElement after = asTaskCheckbox(Point.elementAfter(bundle.document(), point));
    if (after != null) {
      return after;
    }
    return asTaskCheckbox(point.getContainer());
  }

  private static ContentElement asTaskCheckbox(Object node) {
    if (!(node instanceof ContentElement)) {
      return null;
    }
    ContentElement element = (ContentElement) node;
    if (!CheckBox.isCheckBox(element)) {
      return null;
    }
    String name = element.getAttribute(ContentElement.NAME);
    return name != null && name.startsWith(TaskDocumentUtil.TASK_NAME_PREFIX) ? element : null;
  }
}
