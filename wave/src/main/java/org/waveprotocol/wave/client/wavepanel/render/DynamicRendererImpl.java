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
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipQueueRenderer.PagingHandler;
import org.waveprotocol.wave.client.util.ClientFlags;
import com.google.gwt.core.client.Duration;
import com.google.gwt.core.client.GWT;
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
  private final PagingHandler pager;
  private final FragmentRequester fragmentRequester;

  private final Set<ConversationBlip> pagedIn = new HashSet<ConversationBlip>();
  private int prerenderPxTop = 600;
  private int prerenderPxBottom = 800;
  private int pageOutSlackPx = 1200;
  private int throttleMs = 50;
  private boolean logStats = false;

  private boolean updateQueued = false;
  private double lastUpdateMs = 0;

  public static DynamicRendererImpl create(ObservableConversationView view,
      ModelAsViewProvider modelAsView, BlipQueueRenderer queue, PagingHandler pager,
      FragmentRequester fragmentRequester, ScreenController screen) {
    return new DynamicRendererImpl(view, modelAsView, queue, pager, fragmentRequester, screen);
  }

  private DynamicRendererImpl(ObservableConversationView view,
      ModelAsViewProvider modelAsView, BlipQueueRenderer queue, PagingHandler pager,
      FragmentRequester fragmentRequester, ScreenController screen) {
    this.view = view;
    this.modelAsView = modelAsView;
    this.queue = queue;
    this.pager = pager;
    this.fragmentRequester = fragmentRequester == null ? FragmentRequester.NO_OP : fragmentRequester;
    this.screen = screen;
  }

  @Override
  public void init() {
    // Pull tunables from flags when available
    try {
      if (ClientFlags.get().dynamicPrerenderUpperPx() != null) prerenderPxTop = ClientFlags.get().dynamicPrerenderUpperPx();
      if (ClientFlags.get().dynamicPrerenderLowerPx() != null) prerenderPxBottom = ClientFlags.get().dynamicPrerenderLowerPx();
      if (ClientFlags.get().dynamicPageOutSlackPx() != null) pageOutSlackPx = ClientFlags.get().dynamicPageOutSlackPx();
      if (ClientFlags.get().dynamicScrollThrottleMs() != null) throttleMs = ClientFlags.get().dynamicScrollThrottleMs();
      logStats = Boolean.TRUE.equals(ClientFlags.get().enableViewportStats());
    } catch (Throwable t) {
      // ignore in non-client contexts
    }
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
    throttleUpdate();
  }

  private void throttleUpdate() {
    double now = Duration.currentTimeMillis();
    if ((now - lastUpdateMs) >= throttleMs) {
      lastUpdateMs = now;
      updateWindow();
    } else if (!updateQueued) {
      updateQueued = true;
      com.google.gwt.user.client.Timer t = new com.google.gwt.user.client.Timer() {
        @Override public void run() {
          updateQueued = false;
          lastUpdateMs = Duration.currentTimeMillis();
          updateWindow();
        }
      };
      int delay = Math.max(1, throttleMs - (int)(now - lastUpdateMs));
      t.schedule(delay);
    }
  }

  private void updateWindow() {
    final int top = screen.getScrollTop() - prerenderPxTop;
    final int bottom = screen.getScrollTop() + screen.getViewportHeight() + prerenderPxBottom;

    final int outTop = top - pageOutSlackPx;
    final int outBottom = bottom + pageOutSlackPx;

    final int[] counts = new int[] {0, 0}; // [in, out]

    BlipMappers.depthFirst(new org.waveprotocol.wave.model.util.Predicate<ConversationBlip>() {
      @Override
      public boolean apply(ConversationBlip blip) {
        BlipView bv = modelAsView.getBlipView(blip);
        if (bv == null) return true; // continue
        @SuppressWarnings("unchecked")
        BlipViewImpl<BlipViewDomImpl> impl = (BlipViewImpl<BlipViewDomImpl>) bv;
        Element e = null;
        try { e = impl.getIntrinsic() != null ? impl.getIntrinsic().getElement() : null; } catch (Throwable t) { e = null; }
        if (e == null) return true;
        int absTop;
        int h;
        try {
          absTop = getAbsoluteTop(e);
          h = e.getOffsetHeight();
        } catch (Throwable t) {
          return true; // Skip invalid DOM nodes safely
        }
        if (h <= 0) return true;
        boolean isVisible = intersects(absTop, absTop + h, top, bottom);
        if (isVisible) {
          if (!pagedIn.contains(blip)) {
            queue.add(blip);
            pagedIn.add(blip);
            counts[0]++;
          }
        } else if (pagedIn.contains(blip) && !intersects(absTop, absTop + h, outTop, outBottom)) {
          // Far enough offscreen; page out
          pager.pageOut(blip);
          pagedIn.remove(blip);
          counts[1]++;
        }
        return true;
      }
    }, view);

    try {
      if (Boolean.TRUE.equals(ClientFlags.get().enableFragmentFetch())) {
        fragmentRequester.fetchRange(top, bottom, new FragmentRequester.Callback() {
          @Override public void onSuccess() {}
          @Override public void onError(Throwable error) {
            if (logStats) {
              GWT.log("Fragment fetch failed", error);
            }
          }
        });
      }
    } catch (Throwable ignored) {}

    if (logStats && (counts[0] > 0 || counts[1] > 0)) {
      GWT.log("DynamicRenderer: in=" + counts[0] + " out=" + counts[1] +
          " top=" + top + " bottom=" + bottom + " pagedIn=" + pagedIn.size());
    }
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
