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

import org.waveprotocol.wave.model.document.Doc;
import org.waveprotocol.wave.model.document.MutableDocument;
import org.waveprotocol.wave.model.document.ObservableDocument;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.wave.ObservableWavelet;

/**
 * Helpers for locating per-blip reaction data documents without creating them unless needed.
 */
public final class ReactionDataDocuments {

  private ReactionDataDocuments() {
  }

  public static boolean hasExistingReactionDocument(ConversationBlip blip) {
    if (blip == null) {
      return false;
    }
    Conversation conversation = blip.getConversation();
    if (conversation instanceof WaveletBasedConversation) {
      String documentId = IdUtil.reactionDataDocumentId(blip.getId());
      return ((WaveletBasedConversation) conversation).getWavelet().getDocumentIds()
          .contains(documentId);
    }
    return false;
  }

  public static ReactionDocument<Doc.N, Doc.E, Doc.T> getIfPresent(ConversationBlip blip) {
    return hasExistingReactionDocument(blip) ? getOrCreate(blip) : null;
  }

  public static ReactionDocument<Doc.N, Doc.E, Doc.T> getOrCreate(ConversationBlip blip) {
    return new ReactionDocument<Doc.N, Doc.E, Doc.T>(getMutableDocument(blip));
  }

  public static ObservableDocument getObservableDocument(ObservableConversationBlip blip) {
    return (ObservableDocument) getMutableDocument(blip);
  }

  public static ObservableWavelet getObservableWavelet(ObservableConversation conversation) {
    if (conversation instanceof WaveletBasedConversation) {
      return ((WaveletBasedConversation) conversation).getWavelet();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static MutableDocument<Doc.N, Doc.E, Doc.T> getMutableDocument(ConversationBlip blip) {
    return (MutableDocument<Doc.N, Doc.E, Doc.T>) blip.getConversation().getDataDocument(
        IdUtil.reactionDataDocumentId(blip.getId()));
  }
}
