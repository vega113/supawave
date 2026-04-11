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
  private static final String EMOJI_CLASS = "waveReactionEmoji";
  private static final String COUNT_CLASS = "waveReactionCount";
  private static final String ADD_ICON_CLASS = "waveReactionAddIcon";
  private static final String ADD_BUTTON_LABEL = "Add reaction";
  private static final String ADD_ICON_HTML =
      "<span class=\"" + ADD_ICON_CLASS + "\" aria-hidden=\"true\">"
          + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"14\" height=\"14\" "
          + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" "
          + "stroke-width=\"1.75\" stroke-linecap=\"round\" stroke-linejoin=\"round\" "
          + "style=\"display:block\">"
          + "<circle cx=\"10\" cy=\"10\" r=\"6\"></circle>"
          + "<circle cx=\"8\" cy=\"8.5\" r=\"0.75\" fill=\"currentColor\" stroke=\"none\"></circle>"
          + "<circle cx=\"12\" cy=\"8.5\" r=\"0.75\" fill=\"currentColor\" stroke=\"none\"></circle>"
          + "<path d=\"M7.5 11.5c0.7 1.1 1.7 1.7 2.5 1.7s1.8-0.6 2.5-1.7\"></path>"
          + "<circle cx=\"17.5\" cy=\"16.5\" r=\"4.5\"></circle>"
          + "<path d=\"M17.5 14.5v4\"></path>"
          + "<path d=\"M15.5 16.5h4\"></path>"
          + "</svg></span>";

  private ReactionRowRenderer() {
  }

  public static SafeHtml render(String blipId, List<ReactionDocument.Reaction> reactions,
      String currentUserAddress, boolean editable) {
    SafeHtmlBuilder html = new SafeHtmlBuilder();
    if (editable) {
      appendAddButton(html, blipId);
    }
    if (reactions != null) {
      for (ReactionDocument.Reaction reaction : reactions) {
        if (reaction == null) {
          continue;
        }
        List<String> addresses = reaction.getAddresses();
        boolean active = currentUserAddress != null
            && addresses != null
            && addresses.contains(currentUserAddress);
        appendReactionChip(html, blipId, reaction, active);
      }
    }
    return html.toSafeHtml();
  }

  private static void appendAddButton(SafeHtmlBuilder html, String blipId) {
    html.appendHtmlConstant(
        "<button type=\"button\" class=\"" + ADD_CLASS + "\" data-reaction-add=\"true\" "
            + "data-reaction-blip-id=\"" + EscapeUtils.htmlEscape(blipId) + "\" "
            + "aria-label=\"" + ADD_BUTTON_LABEL + "\" title=\"" + ADD_BUTTON_LABEL + "\">");
    html.appendHtmlConstant(ADD_ICON_HTML);
    html.appendHtmlConstant("</button>");
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
    html.appendHtmlConstant("<span class=\"" + EMOJI_CLASS + "\">");
    html.appendEscaped(emoji);
    html.appendHtmlConstant("</span> <span class=\"" + COUNT_CLASS + "\">");
    html.append(count);
    html.appendHtmlConstant("</span></button>");
  }
}
