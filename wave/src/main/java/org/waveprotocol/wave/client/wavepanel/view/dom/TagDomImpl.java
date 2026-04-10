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

import com.google.gwt.dom.client.Element;

import org.waveprotocol.wave.client.wavepanel.view.IntrinsicTagView;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TagsViewBuilder.Css;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;

/**
 * DOM implementation of a single tag.
 *
 * Ported from Wiab.pro.
 */
public final class TagDomImpl implements DomView, IntrinsicTagView {

  private final Element self;
  private static final Css css = WavePanelResourceLoader.getTags().css();

  TagDomImpl(Element self) {
    this.self = self.cast();
  }

  static TagDomImpl of(Element e) {
    return new TagDomImpl(e);
  }

  @Override
  public void setName(String name) {
    Element label = getLabelElement();
    if (label == null) {
      self.setInnerText(name);
    } else {
      label.setInnerText(name);
    }
  }

  @Override
  public String getName() {
    Element label = getLabelElement();
    return label == null ? self.getInnerText() : label.getInnerText();
  }

  @Override
  public TagState getState() {
    String className = self.getClassName();
    if (className.indexOf(css.added()) != -1) {
      return TagState.ADDED;
    } else if (className.indexOf(css.removed()) != -1) {
      return TagState.REMOVED;
    }
    return TagState.NORMAL;
  }

  @Override
  public void setState(TagState state) {
    String className = css.tag() + " ";
    switch (state) {
      case NORMAL:
        className += css.normal();
        break;
      case ADDED:
        className += css.added();
        break;
      case REMOVED:
        className += css.removed();
        break;
    }
    self.setClassName(className);
  }

  @Override
  public String getHint() {
    return self.getTitle();
  }

  @Override
  public void setHint(String hint) {
    self.setTitle(hint);
  }

  //
  // Structure.
  //

  void remove() {
    self.removeFromParent();
  }

  private Element getLabelElement() {
    Element child = self.getFirstChildElement();
    while (child != null) {
      String childClass = child.getClassName();
      if (childClass != null
          && (" " + childClass + " ").indexOf(" " + css.tagLabel() + " ") != -1) {
        return child;
      }
      child = child.getNextSiblingElement();
    }
    return null;
  }

  //
  // DomView
  //

  @Override
  public Element getElement() {
    return self;
  }

  @Override
  public String getId() {
    return self.getId();
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
