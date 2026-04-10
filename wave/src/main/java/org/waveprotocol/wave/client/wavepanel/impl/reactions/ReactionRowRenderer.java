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

package org.waveprotocol.wave.client.wavepanel.impl.reactions;

import org.waveprotocol.wave.client.common.safehtml.EscapeUtils;
import org.waveprotocol.wave.client.common.safehtml.SafeHtml;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.model.conversation.ReactionDocument;

import java.util.List;

/**
 * Renders a compact reaction row for one blip.
 */
public final class ReactionRowRenderer {

  public static final String CHIP_CLASS = "waveReactionChip";
  public static final String CHIP_ACTIVE_CLASS = "waveReactionChipActive";
  public static final String ADD_CLASS = "waveReactionAdd";

  private ReactionRowRenderer() {
  }

  public static SafeHtml render(String blipId, List<ReactionDocument.Reaction> reactions,
      String currentUserAddress, boolean editable) {
    SafeHtmlBuilder html = new SafeHtmlBuilder();
    if (reactions != null) {
      for (ReactionDocument.Reaction reaction : reactions) {
        appendReactionChip(html, blipId, reaction,
            currentUserAddress != null && reaction.getAddresses().contains(currentUserAddress));
      }
    }
    if (editable) {
      html.appendHtmlConstant(
          "<button type=\"button\" class=\"" + ADD_CLASS + "\" data-reaction-add=\"true\" "
              + "data-reaction-blip-id=\"" + EscapeUtils.htmlEscape(blipId) + "\">+</button>");
    }
    return html.toSafeHtml();
  }

  private static void appendReactionChip(SafeHtmlBuilder html, String blipId,
      ReactionDocument.Reaction reaction,
      boolean active) {
    String emoji = reaction.getEmoji() == null ? "" : reaction.getEmoji();
    int count = reaction.getAddresses() != null ? reaction.getAddresses().size() : 0;
    String classes = CHIP_CLASS + (active ? " " + CHIP_ACTIVE_CLASS : "");
    String safeEmoji = EscapeUtils.htmlEscape(emoji);
    html.appendHtmlConstant("<button type=\"button\" class=\"" + classes
        + "\" data-reaction-emoji=\"" + safeEmoji + "\" data-reaction-active=\""
        + active + "\" data-reaction-blip-id=\"" + EscapeUtils.htmlEscape(blipId) + "\">");
    html.appendEscaped(emoji);
    html.appendHtmlConstant(" <span class=\"waveReactionCount\">");
    html.append(count);
    html.appendHtmlConstant("</span></button>");
  }
}
