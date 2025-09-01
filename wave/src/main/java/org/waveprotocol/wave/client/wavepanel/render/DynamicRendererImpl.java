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

package org.waveprotocol.wave.client.wavepanel.render;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Document;

import org.waveprotocol.wave.client.render.undercurrent.ScreenController;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipQueueRenderer;
import org.waveprotocol.wave.client.wavepanel.view.impl.BlipViewImpl;
import org.waveprotocol.wave.client.wavepanel.view.dom.BlipViewDomImpl;
import org.waveprotocol.wave.model.conversation.BlipMappers;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;

import java.util.HashSet;
import java.util.Set;

/** Minimal dynamic renderer: pages in only visible blips; no page-out yet. */
public final class DynamicRendererImpl implements DynamicRenderer, ScreenController.Listener {
  private final ObservableConversationView view;
  private final ScreenController screen;
  private final ModelAsViewProvider modelAsView;
  private final BlipQueueRenderer queue;

  private final Set<ConversationBlip> pagedIn = new HashSet<ConversationBlip>();
  private int prerenderPxTop = 600;
  private int prerenderPxBottom = 800;

  public static DynamicRendererImpl create(ObservableConversationView view,
      ModelAsViewProvider modelAsView, BlipQueueRenderer queue, ScreenController screen) {
    return new DynamicRendererImpl(view, modelAsView, queue, screen);
  }

  private DynamicRendererImpl(ObservableConversationView view,
      ModelAsViewProvider modelAsView, BlipQueueRenderer queue, ScreenController screen) {
    this.view = view;
    this.modelAsView = modelAsView;
    this.queue = queue;
    this.screen = screen;
  }

  @Override
  public void init() {
    screen.addListener(this);
    updateWindow();
  }

  @Override
  public void destroy() {
    screen.removeListener(this);
    pagedIn.clear();
  }

  @Override
  public void onScreenChanged(int scrollTop, int viewportHeight) {
    updateWindow();
  }

  private void updateWindow() {
    final int top = screen.getScrollTop() - prerenderPxTop;
    final int bottom = screen.getScrollTop() + screen.getViewportHeight() + prerenderPxBottom;

    BlipMappers.depthFirst(new org.waveprotocol.wave.model.util.Predicate<ConversationBlip>() {
      @Override
      public boolean apply(ConversationBlip blip) {
        BlipView bv = modelAsView.getBlipView(blip);
        if (bv == null) return true; // continue
        @SuppressWarnings("unchecked")
        BlipViewImpl<BlipViewDomImpl> impl = (BlipViewImpl<BlipViewDomImpl>) bv;
        Element e = impl.getIntrinsic().getElement();
        if (e == null) return true;
        int absTop = getAbsoluteTop(e);
        int h = e.getOffsetHeight();
        if (intersects(absTop, absTop + h, top, bottom)) {
          if (!pagedIn.contains(blip)) {
            queue.add(blip);
            pagedIn.add(blip);
          }
        }
        return true;
      }
    }, view);
  }

  private static boolean intersects(int aTop, int aBottom, int bTop, int bBottom) {
    return aBottom >= bTop && aTop <= bBottom;
  }

  private static int getAbsoluteTop(Element e) {
    int top = 0;
    Element cur = e;
    while (cur != null && cur != Document.get().getBody()) {
      top += cur.getOffsetTop();
      cur = cur.getOffsetParent();
    }
    return top;
  }
}
