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


import static org.waveprotocol.wave.client.uibuilder.BuilderHelper.KIND_ATTRIBUTE;
import static org.waveprotocol.wave.client.wavepanel.view.dom.DomViewHelper.load;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.ImageElement;

import org.waveprotocol.wave.client.common.safehtml.SafeHtmlBuilder;
import org.waveprotocol.wave.client.common.util.StringSequence;
import org.waveprotocol.wave.client.uibuilder.UiBuilder;
import org.waveprotocol.wave.client.wavepanel.view.IntrinsicBlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.View.Type;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipMetaViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipMetaViewBuilder.Components;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipViewBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.CollapsibleBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.TypeCodes;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.BlipMessages;
import org.waveprotocol.wave.model.util.Pair;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * BlipViewDomImpl of the blip view.
 *
 */
public final class BlipMetaDomImpl implements DomView, IntrinsicBlipMetaView {

  /**
   * The INLINE_LOCATOR_ATTRIBUTE is the dom element attribute that contains the
   * serialized string of the inline locator.
   * The INLINE_LOCATOR_PROPERTY is a the dom element property that contains the deserialized
   * inline locator object.
   * The two must be different since in IE, property and attribute are not namespaced separately.
   */
  private static final String INLINE_LOCATOR_PROPERTY = "inlineSequence";
  public static final String INLINE_LOCATOR_ATTRIBUTE = "inline";

  /** The DOM element of this view. */
  private final Element self;

  /** The HTML id of {@code self}. */
  private final String id;
  
  /** The CSS classes used to manipulate style based on state changes. */
  private final BlipViewBuilder.Css css;

  //
  // UI fields for both intrinsic and structural elements.
  // Element references are loaded lazily and cached.
  //

  private Element time;
  private Element contentContainer;
  private ImageElement avatar;
  private Element metaline;
  private Element metabar;
  private Element menu;
  private Element draftModeControls;

  private StringSequence inlineLocators;

  /** DOM element for draft mode notification banner. */
  private Element draftNotificationBanner;

  /** DOM element for draft badge. */
  private Element draftBadge;

  BlipMetaDomImpl(Element self, String id, BlipViewBuilder.Css css) {
    this.self = self;
    this.id = id;
    this.css = css;
  }

  public static BlipMetaDomImpl of(Element e, BlipViewBuilder.Css css) {
    return new BlipMetaDomImpl(e, e.getId(), css);
  }

  @Override
  public void setTime(String time) {
    Element t = getTime();
    t.setInnerText(time);
    // G-PORT-3 (#1112): keep the cross-view parity hook in sync with
    // live-update setTime / setTimeTooltip. The initial render in
    // BlipMetaViewBuilder.outputHtml stamps data-blip-time off the
    // tooltip; subsequent live updates from the server arrive via
    // setTime — without this mirror, the parity hook would drift away
    // from the visible value.
    if (time != null && !time.trim().isEmpty()) {
      t.setAttribute("data-blip-time", time);
    } else {
      t.removeAttribute("data-blip-time");
    }
  }

  @Override
  public void setTimeTooltip(String fullDateTime) {
    Element t = getTime();
    t.setTitle(fullDateTime);
    if (fullDateTime != null && !fullDateTime.trim().isEmpty()) {
      t.setAttribute("data-blip-time", fullDateTime);
    } else {
      t.removeAttribute("data-blip-time");
    }
  }

  @Override
  public void setAvatar(String avatarUrl) {
    getAvatar().setSrc(avatarUrl);
  }

  @Override
  public void setAuthorAddress(String address) {
    Element avatar = getAvatar();
    if (address != null && !address.trim().isEmpty()) {
      avatar.setAttribute("data-address", address);
      // G-PORT-3 (#1112): mirror the address into the parity hook so
      // live updates keep both attributes in sync.
      avatar.setAttribute("data-blip-author", address);
    } else {
      avatar.removeAttribute("data-blip-author");
    }
  }

  @Override
  public void setMetaline(String metaline) {
    getMetaline().setInnerText(metaline);
  }

  @Override
  public void setRead(boolean read) {
    // The entire set of class names is always replaced, because
    // server-generated and client-generated classes do not mix.
    if (read) {
      getMetabar().setClassName(css.metabar() + " " + css.read());
    } else {
      getMetabar().setClassName(css.metabar() + " " + css.unread());
    }
  }

  @Override
  public void enable(Set<MenuOption> toEnable) {
    Pair<EnumMap<MenuOption, BlipMenuItemDomImpl>, EnumSet<MenuOption>>  state = getMenuState();
    EnumSet<MenuOption> options = EnumSet.copyOf(state.first.keySet());
    EnumSet<MenuOption> selected = state.second;
    options.addAll(toEnable);
    setMenuState(options, selected);
  }

  @Override
  public void disable(Set<MenuOption> toDisable) {
    Pair<EnumMap<MenuOption, BlipMenuItemDomImpl>, EnumSet<MenuOption>>  state = getMenuState();
    EnumSet<MenuOption> options = EnumSet.copyOf(state.first.keySet());
    EnumSet<MenuOption> selected = state.second;
    options.removeAll(toDisable);
    selected.removeAll(toDisable);
    setMenuState(options, selected);
  }

  @Override
  public void select(MenuOption option) {
    BlipMenuItemDomImpl item = getMenuState().first.get(option);
    if (item != null) {
      item.select();
    }
  }

  @Override
  public void deselect(MenuOption option) {
    BlipMenuItemDomImpl item = getMenuState().first.get(option);
    if (item != null) {
      item.deselect();
    }
  }

  @Override
  public void setDraftActive(boolean active) {
    if (active) {
      self.addClassName(css.draftActive());
      createDraftIndicators();
    } else {
      self.removeClassName(css.draftActive());
      removeDraftIndicators();
    }
  }

  /**
   * Creates and inserts localized draft mode indicators.
   */
  private void createDraftIndicators() {
    BlipMessages messages = WavePanelResourceLoader.getBlipMessages();

    // Create and insert notification banner after metabar
    draftNotificationBanner = Document.get().createDivElement();
    draftNotificationBanner.setClassName(css.draftActive() + "-notification");
    draftNotificationBanner.setInnerText(messages.draftModeNotification());
    Element metabarParent = getMetabar().getParentElement();
    Element nextSibling = getMetabar().getNextSiblingElement();
    if (nextSibling != null) {
      metabarParent.insertBefore(draftNotificationBanner, nextSibling);
    } else {
      metabarParent.appendChild(draftNotificationBanner);
    }

    // Create and insert draft badge before time text
    draftBadge = Document.get().createSpanElement();
    draftBadge.setClassName(css.draftActive() + "-badge");
    draftBadge.setInnerText(messages.draftBadge());
    getTime().insertBefore(draftBadge, getTime().getFirstChild());
  }

  /**
   * Removes draft mode indicator elements.
   */
  private void removeDraftIndicators() {
    if (draftNotificationBanner != null) {
      draftNotificationBanner.removeFromParent();
      draftNotificationBanner = null;
    }
    if (draftBadge != null) {
      draftBadge.removeFromParent();
      draftBadge = null;
    }
  }

  /**
   * Scrapes the menu state from the DOM. The menu state describes what options
   * exist, and which, if any, are currently selected.
   *
   * @return a mapping from available options to their UI objects, and the
   *         subset of those options that are currently selected.
   */
  private Pair<EnumMap<MenuOption, BlipMenuItemDomImpl>, EnumSet<MenuOption>> getMenuState() {
    EnumMap<MenuOption, BlipMenuItemDomImpl> options =
        new EnumMap<MenuOption, BlipMenuItemDomImpl>(MenuOption.class);
    EnumSet<MenuOption> selected = EnumSet.noneOf(MenuOption.class);
    Element e = getMenu().getFirstChildElement();
    while (e != null) {
      if (e.hasAttribute(KIND_ATTRIBUTE)
          && e.getAttribute(KIND_ATTRIBUTE).equals(TypeCodes.kind(Type.MENU_ITEM))) {
        BlipMenuItemDomImpl item = BlipMenuItemDomImpl.of(e, css);
        MenuOption option = item.getOption();
        options.put(option, item);
        if (item.isSelected()) {
          selected.add(option);
        }
      }
      e = e.getNextSiblingElement();
    }
    return Pair.of(options, selected);
  }

  /**
   * Replaces the current menu DOM with a new menu.
   *
   * @param options options to include in the menu
   * @param selected which options, if any, are to be selected.
   */
  private void setMenuState(Set<MenuOption> options, Set<MenuOption> selected) {
    UiBuilder builder = BlipMetaViewBuilder.menuBuilder(options, selected, css);
    SafeHtmlBuilder  out = new SafeHtmlBuilder();
    builder.outputHtml(out);
    getMenu().setInnerHTML(out.toSafeHtml().asString());
  }

  public void clearContent() {
    getInlineLocators().clear();
    getContentContainer().getFirstChildElement().setInnerHTML("");
  }

  public void setContent(Element document) {
    // Server-side document rendering is not correct - it leaves off the crucial
    // "document" class.
    document.addClassName("document");
    getContentContainer().getFirstChildElement().appendChild(document);
  }

  public StringSequence getInlineLocators() {
    if (inlineLocators == null) {
      Element content = getContentContainer().getFirstChildElement();
      if (content != null) {
        inlineLocators = (StringSequence) content.getPropertyObject(INLINE_LOCATOR_PROPERTY);
        if (inlineLocators == null) {
          // Note: getAttribute() of a missing attribute does not return null on
          // all browsers.
          if (content.hasAttribute(INLINE_LOCATOR_ATTRIBUTE)) {
            String serial = content.getAttribute(INLINE_LOCATOR_ATTRIBUTE);
            inlineLocators = StringSequence.create(serial);
          } else {
            inlineLocators = StringSequence.create();
          }
          content.setPropertyObject(INLINE_LOCATOR_PROPERTY, inlineLocators);
        }
      } else {
        // Leave inlineLocators as null, since the document is not here yet.
      }
    }
    return inlineLocators;
  }

  //
  // Generated code. There is no informative content in the code below.
  //

  private Element getTime() {
    if (time == null) {
      time = load(id, Components.TIME);
    }
    return time;
  }

  private ImageElement getAvatar() {
    if (avatar == null) {
      avatar = load(id, Components.AVATAR).cast();
    }
    return avatar;
  }

  private Element getMetaline() {
    if (metaline == null) {
      metaline = load(id, Components.METALINE);
    }
    return metaline;
  }

  private Element getMetabar() {
    if (metabar == null) {
      metabar = load(id, Components.METABAR);
    }
    return metabar;
  }

  private Element getMenu() {
    if (menu == null) {
      menu = load(id, Components.MENU);
    }
    return menu;
  }

  //
  // Structural elements are public, in order to export structural control.
  //

  public Element getContentContainer() {
    if (contentContainer == null) {
      contentContainer = load(id, Components.CONTENT);
    }
    return contentContainer;
  }

  public Element getDraftModeControls() {
    if (draftModeControls == null) {
      draftModeControls = load(id, Components.DRAFTMODECONTROLS);
    }
    return draftModeControls;
  }

  public void remove() {
    getElement().removeFromParent();
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

  //
  // DOM-specific structural knowledge.
  //

  Element getInlineAnchorAfter(Element ref) {
    String id = ref != null ? ref.getId() : null;
    String nextId = getInlineLocators().getNext(id);
    return Document.get().getElementById(nextId);
  }

  Element getInlineAnchorBefore(Element ref) {
    String id = ref != null ? ref.getId() : null;
    String nextId = getInlineLocators().getPrevious(id);
    return Document.get().getElementById(nextId);
  }

  static String resolveInlineLocatorReference(StringSequence inlineLocators, String refId) {
    if (refId == null || inlineLocators == null || !inlineLocators.contains(refId)) {
      return null;
    }
    return refId;
  }

  void insertInlineLocatorBefore(Element ref, Element x) {
    StringSequence locators = getInlineLocators();
    String refId = ref != null ? ref.getId() : null;
    locators.insertBefore(resolveInlineLocatorReference(locators, refId), x.getId());
  }

  void removeInlineLocator(Element x) {
    getInlineLocators().remove(x.getId());
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
