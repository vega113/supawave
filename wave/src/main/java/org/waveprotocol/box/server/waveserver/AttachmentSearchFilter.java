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

import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared server-side execution for the canonical {@code has:attachment} chip.
 */
final class AttachmentSearchFilter {

  static boolean isHasAttachmentQuery(Map<TokenQueryType, Set<String>> queryParams) {
    return QueryHelper.hasTokenValue(queryParams, TokenQueryType.HAS, "attachment");
  }

  static void filterByHasAttachment(List<WaveViewData> results) {
    Iterator<WaveViewData> it = results.iterator();
    while (it.hasNext()) {
      if (!hasAttachmentDocument(it.next())) {
        it.remove();
      }
    }
  }

  static boolean hasAttachmentDocument(WaveViewData wave) {
    for (ObservableWaveletData wavelet : wave.getWavelets()) {
      if (!IdUtil.isConversationalId(wavelet.getWaveletId())) {
        continue;
      }
      for (String documentId : wavelet.getDocumentIds()) {
        if (IdUtil.isAttachmentDataDocument(documentId)) {
          return true;
        }
      }
    }
    return false;
  }

  private AttachmentSearchFilter() {
  }
}
