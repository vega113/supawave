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

package org.waveprotocol.wave.client.wavepanel.view.impl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;

import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.DraftModeControlsMessages;

/**
 * GWT widget providing compact, icon-based draft-mode controls:
 * Done (check), Cancel (x), and Draft toggle (pencil) with keyboard
 * shortcuts. Appears inside the blip meta area while editing.
 *
 * <p>Keyboard shortcuts (active only while this widget is visible):
 * <ul>
 *   <li>Ctrl+Enter &mdash; Done (save draft)</li>
 *   <li>Escape &mdash; Cancel (discard draft)</li>
 *   <li>Ctrl+D &mdash; Toggle draft mode</li>
 * </ul>
 */
public class DraftModeControlsWidget extends SimplePanel
    implements BlipMetaView.DraftModeControls {

  // Inline SVG icons from Lucide (MIT) — 16x16, no external deps.
  private static final String DONE_SVG =
      "<svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" "
      + "stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" "
      + "stroke-linejoin=\"round\">"
      + "<polyline points=\"20 6 9 17 4 12\"/>"
      + "</svg>";

  private static final String CANCEL_SVG =
      "<svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" "
      + "stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" "
      + "stroke-linejoin=\"round\">"
      + "<line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"/>"
      + "<line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"/>"
      + "</svg>";

  private static final String DRAFT_SVG =
      "<svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" "
      + "stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" "
      + "stroke-linejoin=\"round\">"
      + "<path d=\"M12 20h9\"/>"
      + "<path d=\"M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z\"/>"
      + "</svg>";

  /** Shared CSS injected once into the host page. */
  private static boolean cssInjected = false;

  private static void ensureCssInjected() {
    if (!cssInjected) {
      cssInjected = true;
      String css =
          ".blip-controls{"
          + "display:inline-flex;align-items:center;gap:2px;"
          + "margin:2px 0;padding:1px 4px;"
          + "border-radius:4px;background:#f7fafc;border:1px solid #e2e8f0;"
          + "}"
          + ".blip-action{"
          + "display:inline-flex;align-items:center;justify-content:center;"
          + "width:24px;height:24px;border-radius:3px;"
          + "cursor:pointer;color:#4a5568;"
          + "transition:background .15s,color .15s;"
          + "}"
          + ".blip-action:hover{background:#edf2f7;color:#2d3748;}"
          + ".blip-action-done:hover{color:#38a169;}"
          + ".blip-action-cancel:hover{color:#e53e3e;}"
          + ".blip-action-draft.active{color:#3182ce;background:#ebf8ff;}"
          + ".draft-info{"
          + "font-size:10px;color:#718096;font-style:italic;margin-left:4px;"
          + "display:none;"
          + "}"
          + ".draft-info.visible{display:inline;}";

      Element style = com.google.gwt.dom.client.Document.get().createStyleElement();
      style.setInnerHTML(css);
      com.google.gwt.dom.client.Document.get().getHead().appendChild(style);
    }
  }

  private static final DraftModeControlsMessages messages =
      GWT.create(DraftModeControlsMessages.class);

  private Listener listener;
  private boolean draftActive = false;
  private final Element draftBtn;
  private final Element draftInfo;
  private HandlerRegistration keyHandler;

  /**
   * Constructs the compact icon-based controls widget and attaches it
   * under the given DOM element (the draft-mode controls container in the
   * blip meta).
   *
   * @param containerElement the DOM element to adopt as this widget's element
   */
  public DraftModeControlsWidget(Element containerElement) {
    super(containerElement);
    ensureCssInjected();

    HTML toolbar = new HTML(
        "<span class='blip-controls'>"
        + "<span class='blip-action blip-action-done' title='" + escapeAttr(messages.doneHint()) + "'>"
        + DONE_SVG + "</span>"
        + "<span class='blip-action blip-action-cancel' title='" + escapeAttr(messages.cancelHint()) + "'>"
        + CANCEL_SVG + "</span>"
        + "<span class='blip-action blip-action-draft' title='" + escapeAttr(messages.draftHint()) + "'>"
        + DRAFT_SVG + "</span>"
        + "<span class='draft-info'>" + escapeHtml(messages.draft()) + "</span>"
        + "</span>");

    setWidget(toolbar);

    // Cache references for later manipulation.
    Element root = toolbar.getElement();
    Element controls = root.getFirstChildElement();
    draftBtn = findByClass(controls, "blip-action-draft");
    draftInfo = findByClass(controls, "draft-info");

    // Click handlers via delegation on the toolbar container.
    Event.sinkEvents(controls, Event.ONCLICK);
    Event.setEventListener(controls, event -> {
      if (Event.ONCLICK == event.getTypeInt()) {
        Element target = Element.as(event.getEventTarget());
        // Walk up to find the .blip-action span.
        Element action = findActionAncestor(target, controls);
        if (action == null) {
          return;
        }
        String cls = action.getClassName();
        if (cls.contains("blip-action-done")) {
          if (listener != null) {
            listener.onDone();
          }
        } else if (cls.contains("blip-action-cancel")) {
          if (listener != null) {
            listener.onCancel();
          }
        } else if (cls.contains("blip-action-draft")) {
          toggleDraft();
        }
      }
    });

    // Register keyboard shortcuts.
    registerKeyboardShortcuts();
  }

  /** Walk from target up to (but not including) boundary looking for a .blip-action element. */
  private static Element findActionAncestor(Element target, Element boundary) {
    Element el = target;
    while (el != null && el != boundary) {
      if (el.getClassName() != null && el.getClassName().contains("blip-action")) {
        return el;
      }
      el = el.getParentElement();
    }
    return null;
  }

  /** Find the first descendant (BFS-style) whose className contains the given token. */
  private static Element findByClass(Element parent, String token) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      Element child = parent.getChild(i).cast();
      if (child.getClassName() != null && child.getClassName().contains(token)) {
        return child;
      }
      Element found = findByClass(child, token);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private void toggleDraft() {
    draftActive = !draftActive;
    if (draftActive) {
      draftBtn.addClassName("active");
      draftInfo.addClassName("visible");
    } else {
      draftBtn.removeClassName("active");
      draftInfo.removeClassName("visible");
    }
    if (listener != null) {
      listener.onModeChange(draftActive);
    }
  }

  /**
   * Returns true if the given element is a descendant of (or equal to) the
   * ancestor element. Used to scope keyboard shortcuts to the active editor.
   */
  private static boolean isDescendantOf(Element element, Element ancestor) {
    Element el = element;
    while (el != null) {
      if (el == ancestor) {
        return true;
      }
      el = el.getParentElement();
    }
    return false;
  }

  private void registerKeyboardShortcuts() {
    keyHandler = Event.addNativePreviewHandler(new NativePreviewHandler() {
      @Override
      public void onPreviewNativeEvent(NativePreviewEvent preview) {
        if (preview.getTypeInt() != Event.ONKEYDOWN) {
          return;
        }
        NativeEvent ne = preview.getNativeEvent();

        // Scope shortcuts to the blip that owns this controls widget:
        // only handle events whose target is inside the widget's parent
        // (the blip meta container). This prevents shortcuts from firing
        // when focus is in unrelated UI such as search fields or popups.
        Element target = ne.getEventTarget().cast();
        Element blipContainer = getElement().getParentElement();
        if (blipContainer != null && !isDescendantOf(target, blipContainer)) {
          return;
        }

        boolean ctrl = ne.getCtrlKey() || ne.getMetaKey();

        if (ctrl && ne.getKeyCode() == KeyCodes.KEY_ENTER) {
          // Done.
          if (listener != null) {
            listener.onDone();
          }
          preview.cancel();
        } else if (ne.getKeyCode() == KeyCodes.KEY_ESCAPE) {
          // Cancel.
          if (listener != null) {
            listener.onCancel();
          }
          preview.cancel();
        } else if (ctrl && (ne.getKeyCode() == 'D' || ne.getKeyCode() == 'd')) {
          // Toggle draft.
          ne.preventDefault();
          toggleDraft();
          preview.cancel();
        }
      }
    });
  }

  /** Escapes characters that are special in HTML attribute values. */
  private static String escapeAttr(String s) {
    return s.replace("&", "&amp;").replace("'", "&#39;").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;");
  }

  /** Escapes characters that are special in HTML text content. */
  private static String escapeHtml(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  @Override
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  protected void onDetach() {
    super.onDetach();
    if (keyHandler != null) {
      keyHandler.removeHandler();
      keyHandler = null;
    }
  }
}
