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
package org.waveprotocol.box.server.frontend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.waveprotocol.box.server.persistence.blocks.VersionRange;
import org.waveprotocol.wave.concurrencycontrol.channel.dto.FragmentsPayload;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.SegmentId;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

/** Builds raw fragment payloads from wavelet snapshots. */
public final class RawFragmentsBuilder {
  private static final Log LOG = Log.get(RawFragmentsBuilder.class);

  private RawFragmentsBuilder() {
  }

  public static List<FragmentsPayload.Fragment> build(ReadableWaveletData data,
      Map<SegmentId, VersionRange> ranges) {
    if (data == null || ranges == null || ranges.isEmpty()) {
      return Collections.emptyList();
    }
    List<FragmentsPayload.Fragment> fragments = new ArrayList<>();
    for (Map.Entry<SegmentId, VersionRange> entry : ranges.entrySet()) {
      SegmentId segment = entry.getKey();
      String documentId = resolveDocumentId(segment);
      if (documentId == null) {
        continue;
      }
      ReadableBlipData document = data.getDocument(documentId);
      if (document == null || document.getContent() == null) {
        continue;
      }
      try {
        DocInitialization init = document.getContent().asOperation();
        String raw = DocOpUtil.debugToXmlString(init);
        fragments.add(new FragmentsPayload.Fragment(segment, raw,
            documentItemCount(init), Collections.emptyList(), Collections.emptyList()));
      } catch (Exception ex) {
        LOG.fine("Failed to build raw fragment for segment " + segment + ": " + ex.getMessage());
      }
    }
    return fragments;
  }

  private static String resolveDocumentId(SegmentId segment) {
    if (segment == null) {
      return null;
    }
    if (SegmentId.MANIFEST_ID.equals(segment)) {
      return IdConstants.MANIFEST_DOCUMENT_ID;
    }
    if (SegmentId.INDEX_ID.equals(segment)) {
      return IdConstants.INDEXABILITY_DATA_DOC_ID;
    }
    if (SegmentId.TAGS_ID.equals(segment)) {
      return IdConstants.TAGS_DOC_ID;
    }
    if (segment.isBlip()) {
      String value = segment.asString();
      return (value.length() > 5) ? value.substring("blip:".length()) : null;
    }
    return null;
  }

  private static int documentItemCount(DocInitialization init) {
    if (init == null) {
      return 0;
    }
    final int[] count = {0};
    init.apply(new DocInitializationCursor() {
      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
        // Annotation boundaries do not advance document position.
      }

      @Override
      public void characters(String chars) {
        if (chars != null) {
          count[0] += chars.length();
        }
      }

      @Override
      public void elementStart(String type, Attributes attrs) {
        count[0]++;
      }

      @Override
      public void elementEnd() {
        count[0]++;
      }
    });
    return count[0];
  }
}
