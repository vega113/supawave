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

import org.waveprotocol.wave.model.document.MutableDocument;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;

/**
 * Provides an integrity-preserving interface for the lock document.
 * The document stores a single {@code <lock mode="..."/>} element where
 * the mode attribute is one of "unlocked", "root", or "all".
 *
 * <p>Document format:
 * <pre>
 *   &lt;lock mode="root"/&gt;
 * </pre>
 *
 * @param <N> node type
 * @param <E> element type
 * @param <T> text type
 */
public class LockDocument<N, E extends N, T extends N> {

  private static final String LOCK_TAG = "lock";
  private static final String MODE_ATTR = "mode";

  private final MutableDocument<N, E, T> doc;

  public LockDocument(MutableDocument<N, E, T> doc) {
    this.doc = doc;
  }

  /**
   * Reads the lock state from the document without mutating it.
   *
   * @param doc the lock document
   * @return the current lock state, {@link WaveLockState#UNLOCKED} if not set
   */
  public static <N, E extends N, T extends N> WaveLockState getLockState(
      MutableDocument<N, E, T> doc) {
    if (doc == null) {
      return WaveLockState.UNLOCKED;
    }
    N child = doc.getFirstChild(doc.getDocumentElement());
    if (child == null) {
      return WaveLockState.UNLOCKED;
    }
    E element = doc.asElement(child);
    if (element == null) {
      return WaveLockState.UNLOCKED;
    }
    String tagName = doc.getTagName(element);
    if (!LOCK_TAG.equals(tagName)) {
      return WaveLockState.UNLOCKED;
    }
    String mode = doc.getAttribute(element, MODE_ATTR);
    return WaveLockState.fromValue(mode);
  }

  /**
   * Sets the lock state in the document. Replaces any existing lock element.
   *
   * @param state the new lock state
   */
  public void setLockState(WaveLockState state) {
    // Remove existing lock elements.
    N child = doc.getFirstChild(doc.getDocumentElement());
    while (child != null) {
      N next = doc.getNextSibling(child);
      E element = doc.asElement(child);
      if (element != null) {
        doc.deleteNode(element);
      }
      child = next;
    }

    // Write new state (only if not unlocked, to keep doc clean).
    if (state != WaveLockState.UNLOCKED) {
      XmlStringBuilder xml = XmlStringBuilder.createEmpty()
          .wrap(LOCK_TAG, MODE_ATTR, state.getValue());
      doc.appendXml(xml);
    }
  }
}
