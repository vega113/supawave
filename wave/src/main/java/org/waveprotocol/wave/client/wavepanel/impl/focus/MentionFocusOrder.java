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

package org.waveprotocol.wave.client.wavepanel.impl.focus;

import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.RangedAnnotation;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.ReadableStringSet;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * A {@link FocusFramePresenter.FocusOrder} that navigates between blips
 * containing a {@code mention/user} annotation matching the signed-in user.
 */
public final class MentionFocusOrder implements FocusFramePresenter.FocusOrder {

  private static final ReadableStringSet MENTION_KEYS =
      CollectionUtils.newStringSet(AnnotationConstants.MENTION_USER);

  private final ViewTraverser traverser;
  private final ModelAsViewProvider views;
  private final String signedInAddress;

  public MentionFocusOrder(ViewTraverser traverser, ModelAsViewProvider views,
      ParticipantId signedInUser) {
    this.traverser = traverser;
    this.views = views;
    this.signedInAddress = signedInUser.getAddress();
  }

  @Override
  public BlipView getNext(BlipView current) {
    BlipView candidate = traverser.getNext(current);
    while (candidate != null) {
      if (hasMention(candidate)) {
        return candidate;
      }
      candidate = traverser.getNext(candidate);
    }
    return null;
  }

  @Override
  public BlipView getPrevious(BlipView current) {
    BlipView candidate = traverser.getPrevious(current);
    while (candidate != null) {
      if (hasMention(candidate)) {
        return candidate;
      }
      candidate = traverser.getPrevious(candidate);
    }
    return null;
  }

  /**
   * Returns the first blip with a mention starting from the given blip
   * (inclusive), walking forward. Returns null if none found.
   */
  public BlipView getFirstFrom(BlipView start) {
    BlipView candidate = start;
    while (candidate != null) {
      if (hasMention(candidate)) {
        return candidate;
      }
      candidate = traverser.getNext(candidate);
    }
    return null;
  }

  private boolean hasMention(BlipView blipView) {
    ConversationBlip blip = views.getBlip(blipView);
    if (blip == null) {
      return false;
    }
    Document doc = blip.getContent();
    if (doc == null || doc.size() == 0) {
      return false;
    }
    for (RangedAnnotation<String> ann : doc.rangedAnnotations(0, doc.size(), MENTION_KEYS)) {
      if (signedInAddress.equals(ann.value())) {
        return true;
      }
    }
    return false;
  }
}
