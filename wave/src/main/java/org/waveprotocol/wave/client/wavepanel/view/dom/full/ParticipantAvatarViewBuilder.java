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

package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import static org.waveprotocol.wave.client.uibuilder.OutputHelper.image;

import com.google.common.annotations.VisibleForTesting;

import org.waveprotocol.wave.client.common.safehtml.EscapeUtils;
import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicParticipantView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.ParticipantsViewBuilder.Css;

/**
 * UiBuilder for a participant.
 *
 */
public final class ParticipantAvatarViewBuilder implements IntrinsicParticipantView, UiBuilder {

  private final Css css;
  private final String id;

  private String avatarUrl;
  private String name;
  private String address;

  @VisibleForTesting
  ParticipantAvatarViewBuilder(String id, Css css) {
    this.id = id;
    this.css = css;
  }

  public static ParticipantAvatarViewBuilder create(String id) {
    return new ParticipantAvatarViewBuilder(id, WavePanelResourceLoader.getParticipants().css());
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public void setAvatar(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void setAddress(String address) {
    this.address = address;
  }

  @Override
  public void outputHtml(SafeHtmlBuilder output) {
    // Render the avatar image with a data-address attribute so that
    // the page-level profile card JavaScript can extract the participant
    // address on click.
    String safeUrl = avatarUrl != null
        ? EscapeUtils.sanitizeUri(avatarUrl) : null;
    String escapedName = name != null
        ? EscapeUtils.htmlEscape(name) : "";
    String kind = TypeCodes.kind(Type.PARTICIPANT);
    StringBuilder sb = new StringBuilder("<img ");
    sb.append("id='").append(id).append("' ");
    sb.append("class='").append(css.participant()).append("' ");
    if (safeUrl != null) {
      sb.append("src='").append(safeUrl).append("' ");
    }
    sb.append("alt='").append(escapedName).append("' ");
    sb.append("title='").append(escapedName).append("' ");
    sb.append("kind='").append(kind).append("' ");
    if (address != null) {
      sb.append("data-address='").append(EscapeUtils.htmlEscape(address)).append("' ");
    }
    sb.append("></img>");
    output.append(EscapeUtils.fromSafeConstant(sb.toString()));
  }
}
