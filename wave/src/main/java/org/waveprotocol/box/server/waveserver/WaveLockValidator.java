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

import org.waveprotocol.wave.model.conversation.WaveLockState;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

/**
 * Server-side validation for wave lock state enforcement.
 */
final class WaveLockValidator {

  private static final String LOCK_TAG = "lock";
  private static final String LOCK_MODE_ATTR = "mode";
  private static final String BLIP_TAG = "blip";
  private static final String BLIP_ID_ATTR = "id";

  private WaveLockValidator() {}

  static String validate(ReadableWaveletData snapshot,
      Iterable<? extends WaveletOperation> ops, ParticipantId author) {
    if (snapshot == null) {
      return null;
    }

    WaveLockState lockState = readLockState(snapshot);
    ParticipantId creator = snapshot.getCreator();
    String rootBlipId = null;

    for (WaveletOperation op : ops) {
      if (!(op instanceof WaveletBlipOperation)) {
        continue;
      }

      String blipId = ((WaveletBlipOperation) op).getBlipId();
      if (IdConstants.LOCK_DOC_ID.equals(blipId)) {
        if (!author.equals(creator)) {
          return "Only the wave creator may change the lock state.";
        }
        continue;
      }

      if (lockState == WaveLockState.ALL_LOCKED) {
        return "This wave is locked. Editing and replies are not allowed.";
      }

      if (lockState == WaveLockState.ROOT_LOCKED) {
        if (rootBlipId == null) {
          rootBlipId = readRootBlipId(snapshot);
        }
        if (rootBlipId != null && rootBlipId.equals(blipId)) {
          return "The root blip is locked. Editing is not allowed here.";
        }
      }
    }

    return null;
  }

  private static WaveLockState readLockState(ReadableWaveletData snapshot) {
    ReadableBlipData lockDoc = snapshot.getDocument(IdConstants.LOCK_DOC_ID);
    if (lockDoc == null) {
      return WaveLockState.UNLOCKED;
    }

    DocInitialization content;
    try {
      content = lockDoc.getContent().asOperation();
    } catch (Throwable ignored) {
      return WaveLockState.UNLOCKED;
    }
    if (content == null) {
      return WaveLockState.UNLOCKED;
    }

    final WaveLockState[] result = {WaveLockState.UNLOCKED};
    final boolean[] found = {false};

    content.apply(new DocInitializationCursor() {
      @Override
      public void elementStart(String type,
          org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        if (!found[0] && LOCK_TAG.equals(type)) {
          found[0] = true;
          result[0] = WaveLockState.fromValue(attrs.get(LOCK_MODE_ATTR));
        }
      }

      @Override
      public void elementEnd() {}

      @Override
      public void characters(String chars) {}

      @Override
      public void annotationBoundary(
          org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {}
    });

    return result[0];
  }

  private static String readRootBlipId(ReadableWaveletData snapshot) {
    ReadableBlipData manifestDoc = snapshot.getDocument(IdConstants.MANIFEST_DOCUMENT_ID);
    if (manifestDoc == null) {
      return null;
    }

    DocInitialization content;
    try {
      content = manifestDoc.getContent().asOperation();
    } catch (Throwable ignored) {
      return null;
    }
    if (content == null) {
      return null;
    }

    final String[] rootBlipId = {null};

    content.apply(new DocInitializationCursor() {
      @Override
      public void elementStart(String type,
          org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        if (rootBlipId[0] == null && BLIP_TAG.equals(type)) {
          rootBlipId[0] = attrs.get(BLIP_ID_ATTR);
        }
      }

      @Override
      public void elementEnd() {}

      @Override
      public void characters(String chars) {}

      @Override
      public void annotationBoundary(
          org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {}
    });

    return rootBlipId[0];
  }
}
