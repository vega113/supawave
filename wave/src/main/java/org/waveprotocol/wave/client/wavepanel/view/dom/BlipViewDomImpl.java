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

package org.waveprotocol.wave.client.wavepanel.view.dom;

import static org.waveprotocol.wave.client.wavepanel.view.dom.DomViewHelper.load;

import com.google.gwt.dom.client.Element;

import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilder.Components;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;

/**
 * BlipViewDomImpl of the blip view.
 *
 */
public final class BlipViewDomImpl implements DomView, IntrinsicBlipView {

  /** The DOM element of this view. */
  private final Element self;

  /** The HTML id of {@code self}. */
  private final String id;

  //
  // UI fields for both intrinsic and structural elements.
  // Element references are loaded lazily and cached.
  //

  private Element meta;
  private Element replies;
  private Element conversations;

  BlipViewDomImpl(Element self, String id) {
    this.self = self;
    this.id = id;
  }

  public static BlipViewDomImpl of(Element e) {
    return new BlipViewDomImpl(e, e.getId());
  }

  //
  // Generated code. There is no informative content in the code below.
  //

  public Element getMetaHolder() {
    if (meta == null) {
      meta = self.getFirstChildElement();
    }
    return meta;
  }

  //
  // Structural elements are public, in order to export structural control.
  //

  public Element getDefaultAnchors() {
    if (replies == null) {
      replies = load(id, Components.REPLIES);
    }
    return replies;
  }

  public Element getConversations() {
    if (conversations == null) {
      conversations = load(id, Components.PRIVATE_REPLIES);
    }
    return conversations;
  }

  public void remove() {
    getElement().removeFromParent();
  }

  /**
   * Marks or unmarks this blip as quasi-deleted in the DOM.
   * Adds a CSS class and a data attribute that can be used by styles.
   */
  public void setQuasiDeleted(boolean deleted) {
    String deletedCls = WavePanelResourceLoader.getBlip().css().deleted();
    if (deleted) {
      self.addClassName(deletedCls);
      self.setAttribute("data-deleted", "true");
    } else {
      self.removeClassName(deletedCls);
      self.removeAttribute("data-deleted");
    }
  }

  //
  // DomView nature.
  //

  @Override
  public Element getElement() {
    return self;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean equals(Object obj) {
    return DomViewHelper.equals(this, obj);
  }

  @Override
  public int hashCode() {
    return DomViewHelper.hashCode(this);
  }
}
