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

import org.waveprotocol.box.server.util.WaveletDataUtil;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side validator that enforces a maximum reply (thread nesting) depth
 * on conversation wavelets.
 *
 * <p>The conversation manifest document uses a nested XML structure where
 * {@code <blip>} and {@code <thread>} elements alternate. The nesting depth
 * of {@code <thread>} elements inside {@code <blip>} elements determines the
 * reply depth. This validator computes the current max depth from the manifest
 * and checks whether incoming operations would create threads at a depth
 * exceeding the limit.</p>
 *
 * <p>This is a defence-in-depth measure that mirrors the client-side check in
 * {@code ActionsImpl.reply()}.</p>
 */
public final class ReplyDepthValidator {

  private static final Log LOG = Log.get(ReplyDepthValidator.class);

  /** The manifest document id in a conversation wavelet. */
  private static final String MANIFEST_DOC_ID = IdConstants.MANIFEST_DOCUMENT_ID;

  /** XML element name for threads in the manifest. */
  private static final String THREAD_TAG = "thread";

  /** XML element name for blips in the manifest. */
  private static final String BLIP_TAG = "blip";

  private ReplyDepthValidator() {}

  /**
   * Checks whether a delta would create reply threads deeper than the given
   * limit. This examines the current manifest document state and the incoming
   * operations.
   *
   * @param snapshot   the current wavelet snapshot (pre-apply state)
   * @param ops        the operations in the delta
   * @param maxDepth   maximum allowed reply depth (e.g. 5). 0 means unlimited.
   * @return an error message if the depth limit would be exceeded, or null if OK
   */
  public static String validate(ReadableWaveletData snapshot,
      Iterable<? extends WaveletOperation> ops, int maxDepth) {
    if (maxDepth <= 0) {
      return null; // unlimited
    }

    List<WaveletOperation> opList = new ArrayList<WaveletOperation>();

    // Check if any operation targets the manifest document and inserts thread elements.
    boolean hasManifestThreadInsert = false;
    for (WaveletOperation op : ops) {
      opList.add(op);
      if (op instanceof WaveletBlipOperation) {
        WaveletBlipOperation blipOp = (WaveletBlipOperation) op;
        if (MANIFEST_DOC_ID.equals(blipOp.getBlipId())) {
          if (blipOp.getBlipOp() instanceof BlipContentOperation) {
            DocOp docOp = ((BlipContentOperation) blipOp.getBlipOp()).getContentOp();
            if (docOpInsertsThread(docOp)) {
              hasManifestThreadInsert = true;
              break;
            }
          }
        }
      }
    }

    if (!hasManifestThreadInsert) {
      return null; // no thread creation, nothing to validate
    }

    Integer projectedMaxDepth = computeProjectedMaxDepth(snapshot, opList);
    if (projectedMaxDepth == null) {
      return null;
    }

    if (projectedMaxDepth > maxDepth) {
      LOG.warning("Reply depth limit exceeded: projected max depth " + projectedMaxDepth
          + " > limit " + maxDepth + "; rejecting delta with thread insertion.");
      return "Reply depth limit exceeded (max " + maxDepth
          + "). Cannot create threads deeper than the allowed depth.";
    }

    return null;
  }

  private static Integer computeProjectedMaxDepth(ReadableWaveletData snapshot,
      List<WaveletOperation> ops) {
    if (snapshot == null) {
      return null;
    }
    ObservableWaveletData projected = WaveletDataUtil.copyWavelet(snapshot);
    try {
      for (WaveletOperation op : ops) {
        op.apply(projected);
      }
    } catch (OperationException e) {
      LOG.warning("Failed to simulate reply-depth validation state: " + e.getMessage());
      return null;
    }
    return computeManifestMaxDepth(projected);
  }

  /**
   * Checks whether a DocOp contains an element-start for a {@code <thread>}
   * element (indicating thread creation).
   */
  private static boolean docOpInsertsThread(DocOp docOp) {
    final boolean[] found = {false};
    docOp.apply(new DocOpCursor() {
      @Override public void elementStart(String type, org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        if (THREAD_TAG.equals(type)) {
          found[0] = true;
        }
      }
      @Override public void elementEnd() {}
      @Override public void characters(String chars) {}
      @Override public void retain(int itemCount) {}
      @Override public void deleteCharacters(String chars) {}
      @Override public void deleteElementStart(String type, org.waveprotocol.wave.model.document.operation.Attributes attrs) {}
      @Override public void deleteElementEnd() {}
      @Override public void replaceAttributes(org.waveprotocol.wave.model.document.operation.Attributes oldAttrs, org.waveprotocol.wave.model.document.operation.Attributes newAttrs) {}
      @Override public void updateAttributes(org.waveprotocol.wave.model.document.operation.AttributesUpdate attrUpdate) {}
      @Override public void annotationBoundary(org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {}
    });
    return found[0];
  }

  /**
   * Computes the maximum thread nesting depth in the conversation manifest
   * document. Walks the document initialization counting alternating
   * {@code <blip>} / {@code <thread>} nesting, using a stack to properly
   * track which element is closing at each {@code elementEnd()}.
   *
   * <p>The depth counts the number of nested {@code <thread>} elements.
   * A thread directly inside the root conversation element has depth 1,
   * a thread nested inside a blip within that thread has depth 2, etc.</p>
   *
   * @return the maximum reply depth found, or 0 if the manifest is empty or absent
   */
  static int computeManifestMaxDepth(ReadableWaveletData snapshot) {
    if (snapshot == null) {
      return 0;
    }
    ReadableBlipData manifestBlip = snapshot.getDocument(MANIFEST_DOC_ID);
    if (manifestBlip == null) {
      return 0;
    }
    DocInitialization content;
    try {
      content = manifestBlip.getContent().asOperation();
    } catch (Throwable e) {
      LOG.warning("Failed to read manifest document for depth check: " + e.getMessage());
      return 0;
    }
    if (content == null) {
      return 0;
    }
    return computeMaxThreadDepthFromInit(content);
  }

  /**
   * Computes max thread depth by walking the document initialization and
   * tracking element open/close with a stack.
   */
  private static int computeMaxThreadDepthFromInit(DocInitialization content) {
    final int[] threadDepth = {0};
    final int[] maxThreadDepth = {0};
    // Track the element stack so we know which element is closing.
    final java.util.Deque<String> elementStack = new java.util.ArrayDeque<>();

    content.apply(new DocInitializationCursor() {
      @Override
      public void elementStart(String type,
          org.waveprotocol.wave.model.document.operation.Attributes attrs) {
        elementStack.push(type);
        if (THREAD_TAG.equals(type)) {
          threadDepth[0]++;
          if (threadDepth[0] > maxThreadDepth[0]) {
            maxThreadDepth[0] = threadDepth[0];
          }
        }
      }

      @Override
      public void elementEnd() {
        if (!elementStack.isEmpty()) {
          String type = elementStack.pop();
          if (THREAD_TAG.equals(type)) {
            threadDepth[0]--;
          }
        }
      }

      @Override
      public void characters(String chars) {}
      @Override
      public void annotationBoundary(
          org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap map) {}
    });

    return maxThreadDepth[0];
  }
}
