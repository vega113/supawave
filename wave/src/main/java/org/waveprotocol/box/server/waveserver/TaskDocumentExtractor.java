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
package org.waveprotocol.box.server.waveserver;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

/**
 * Extracts task assignees from wave documents by walking DocOp annotations.
 * Shared between legacy search (SimpleSearchProviderImpl) and Lucene indexing
 * (WaveMetadataExtractor).
 */
public final class TaskDocumentExtractor {

  private TaskDocumentExtractor() {
  }

  /**
   * Extracts all task assignees from all conversational wavelets in a wave.
   * Returns a set of lower-case participant addresses.
   */
  public static Set<String> extractTaskAssignees(WaveViewData wave) {
    Set<String> assignees = new LinkedHashSet<>();
    for (ObservableWaveletData wavelet : wave.getWavelets()) {
      if (!IdUtil.isConversationalId(wavelet.getWaveletId())) {
        continue;
      }
      extractTaskAssigneesFromWavelet(wavelet, assignees);
    }
    return assignees;
  }

  /**
   * Extracts task assignees from a single wavelet.
   */
  public static void extractTaskAssigneesFromWavelet(
      ObservableWaveletData wavelet, Set<String> assignees) {
    for (String docId : wavelet.getDocumentIds()) {
      ReadableBlipData blip = wavelet.getDocument(docId);
      if (blip == null) {
        continue;
      }
      DocInitialization docOp = blip.getContent().asOperation();
      docOp.apply(new DocInitializationCursor() {
        @Override
        public void annotationBoundary(AnnotationBoundaryMap map) {
          for (int i = 0; i < map.changeSize(); i++) {
            String key = map.getChangeKey(i);
            String newValue = map.getNewValue(i);
            if (AnnotationConstants.TASK_ASSIGNEE.equals(key)
                && newValue != null && !newValue.isEmpty()) {
              assignees.add(newValue.toLowerCase(Locale.ROOT));
            }
          }
        }

        @Override
        public void characters(String chars) {
        }

        @Override
        public void elementStart(String type, Attributes attrs) {
        }

        @Override
        public void elementEnd() {
        }
      });
    }
  }
}
