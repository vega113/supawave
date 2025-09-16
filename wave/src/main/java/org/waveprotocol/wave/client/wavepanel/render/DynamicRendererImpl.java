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

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;

import org.waveprotocol.wave.client.debug.FragmentsDebugIndicator;
import org.waveprotocol.wave.client.render.undercurrent.ScreenController;
import org.waveprotocol.wave.client.util.ClientFlags;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.BlipViewDomImpl;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipQueueRenderer;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.BlipQueueRenderer.PagingHandler;
import org.waveprotocol.wave.client.wavepanel.view.impl.BlipViewImpl;
import org.waveprotocol.wave.model.conversation.BlipMappers;
import org.waveprotocol.wave.model.conversation.ConversationBlip;
import org.waveprotocol.wave.model.conversation.ConversationListenerImpl;
import org.waveprotocol.wave.model.conversation.ObservableConversation;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.conversation.ObservableConversationThread;
import org.waveprotocol.wave.model.conversation.ObservableConversationView;
import org.waveprotocol.wave.model.conversation.ObservableConversationView.Listener;
import org.waveprotocol.wave.model.util.Predicate;

import com.google.gwt.core.client.Duration;
import com.google.gwt.core.client.GWT;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** Minimal dynamic renderer: pages in only visible blips; no page-out yet. */
public final class DynamicRendererImpl implements DynamicRenderer, ScreenController.Listener,
    Listener {
  private final ObservableConversationView view;
  private final ScreenController screen;
  private final ModelAsViewProvider modelAsView;
  private final BlipQueueRenderer queue;
  private final PagingHandler pager;
  private final FragmentRequester fragmentRequester;

  private final Set<ConversationBlip> pagedIn = new HashSet<ConversationBlip>();
  private final Map<ObservableConversation, ObservableConversation.Listener> conversationListeners =
      new HashMap<ObservableConversation, ObservableConversation.Listener>();
  private final Map<ObservableConversation, Integer> conversationBlipCounts =
      new HashMap<ObservableConversation, Integer>();
  private int prerenderPxTop = 600;
  private int prerenderPxBottom = 800;
  private int pageOutSlackPx = 1200;
  private int throttleMs = 50;
  private boolean logStats = false;
  private int bootstrapMax = 12;
  private int totalBlips = 0;
  private int startMs = 0;

  private boolean updateQueued = false;
  private double lastUpdateMs = 0;
  private int lastTopSeen = 0;
  private double lastTopSeenTs = 0;
  private int speedBoostThresholdPx = 800;
  private double speedBoostFactor = 1.8;
  // Fragment fetch retry state (dev-friendly, lightweight)
  private int consecutiveFetchFailures = 0;
  private boolean fetchRetryScheduled = false;
  private int retryBackoffMs = 500; // start backoff at 0.5s
  private static final int MAX_RETRY_BACKOFF_MS = 5000;
  private int lastFetchTop = 0;
  private int lastFetchBottom = 0;

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
      if (ClientFlags.get().dynamicSpeedBoostThresholdPx() != null) speedBoostThresholdPx = ClientFlags.get().dynamicSpeedBoostThresholdPx();
      if (ClientFlags.get().dynamicSpeedBoostFactor() != null) speedBoostFactor = ClientFlags.get().dynamicSpeedBoostFactor();
      logStats = Boolean.TRUE.equals(ClientFlags.get().enableViewportStats());
      // Allow optional bootstrap window size via prerender lower bound heuristic
      if (ClientFlags.get().dynamicPrerenderLowerPx() != null) {
        int vh = screen.getViewportHeight();
        if (vh > 0) {
          // Roughly estimate an initial count from viewport/prerender. Clamp 6..24
          int est = Math.max(6, Math.min(24, (vh + prerenderPxBottom) / 200));
          bootstrapMax = est;
        }
      }
    } catch (Throwable t) {
      // ignore in non-client contexts
    }
    screen.addListener(this);
    attachConversationListeners();
    try { startMs = (int) com.google.gwt.core.client.Duration.currentTimeMillis(); } catch (Throwable ignore) { startMs = 0; }
    // Defer initial update to allow DOM to settle.
    Scheduler.get().scheduleDeferred(
        new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
          @Override public void execute() { updateWindow(); }
        });
  }

  @Override
  public void destroy() {
    screen.removeListener(this);
    detachConversationListeners();
    pagedIn.clear();
    totalBlips = 0;
    conversationBlipCounts.clear();
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
      Timer t = new Timer() {
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
    int currentTop = screen.getScrollTop();
    double now = com.google.gwt.core.client.Duration.currentTimeMillis();
    int delta = Math.abs(currentTop - lastTopSeen);
    boolean boost = delta > speedBoostThresholdPx;
    lastTopSeen = currentTop;
    lastTopSeenTs = now;

    int upper = prerenderPxTop;
    int lower = prerenderPxBottom;
    if (boost) {
      upper = (int) Math.round(upper * speedBoostFactor);
      lower = (int) Math.round(lower * speedBoostFactor);
    }

    final int top = currentTop - upper;
    final int bottom = currentTop + screen.getViewportHeight() + lower;

    final int outTop = top - pageOutSlackPx;
    final int outBottom = bottom + pageOutSlackPx;

    final int[] counts = new int[] {0, 0}; // [in, out]

    // Bootstrap: if nothing is paged in yet, enqueue and flush a small window.
    if (pagedIn.isEmpty()) {
      enqueueBootstrapWindow();
    }

    BlipMappers.depthFirst(new Predicate<ConversationBlip>() {
      @Override
      public boolean apply(ConversationBlip blip) {
        BlipView bv = modelAsView.getBlipView(blip);
        if (bv == null) return true; // continue
        @SuppressWarnings("unchecked")
        BlipViewImpl<BlipViewDomImpl> impl = (BlipViewImpl<BlipViewDomImpl>) bv;
        Element e = null;
        try { e = impl.getIntrinsic() != null ? impl.getIntrinsic().getElement() : null; } catch (ClassCastException | NullPointerException expected) { e = null; }
        if (e == null) return true;
        int absTop;
        int h;
        try {
          absTop = getAbsoluteTop(e);
          h = e.getOffsetHeight();
        } catch (ClassCastException | NullPointerException expected) {
          return true; // Skip invalid DOM nodes safely
        } catch (Exception ex) {
          if (logStats) {
            GWT.log("DynamicRenderer: DOM read failed", ex);
          }
          return true;
        }
        if (h <= 0) {
          // If we still have zero height (e.g., just attached), give it a chance to render on next tick.
          return true;
        }
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
        doFragmentFetch(top, bottom);
      }
    } catch (Exception ex) {
      GWT.log("DynamicRenderer: fragment fetch threw", ex);
    }

    // Dev badge: report blip load stats (paged-in vs total) and elapsed time since init
    try {
      int visible = pagedIn.size();
      int total = Math.max(totalBlips, visible);
      int elapsed = startMs > 0 ? (int) (now - startMs) : 0;
      FragmentsDebugIndicator.setBlipStats(visible, total, elapsed);
      if (logStats) {
        GWT.log("DynamicRenderer: badgeUpdate visible=" + visible +
            " total=" + total + " elapsedMs=" + elapsed);
      }
    } catch (Throwable ignore) {}

    if (logStats && (counts[0] > 0 || counts[1] > 0)) {
      GWT.log("DynamicRenderer: in=" + counts[0] + " out=" + counts[1] +
          " top=" + top + " bottom=" + bottom + " pagedIn=" + pagedIn.size() + (boost?" (boost)":""));
    }
  }

  private void attachConversationListeners() {
    try {
      for (ObservableConversation conversation : view.getConversations()) {
        observeConversation(conversation);
      }
      view.addListener(this);
    } catch (Throwable ignore) {
    }
  }

  private void detachConversationListeners() {
    try {
      view.removeListener(this);
    } catch (Throwable ignore) {
    }
    for (Entry<ObservableConversation, ObservableConversation.Listener> entry :
        conversationListeners.entrySet()) {
      try {
        entry.getKey().removeListener(entry.getValue());
      } catch (Throwable ignore) {
      }
    }
    conversationListeners.clear();
    conversationBlipCounts.clear();
  }

  private void observeConversation(ObservableConversation conversation) {
    if (conversation == null || conversationListeners.containsKey(conversation)) {
      return;
    }
    ObservableConversation.Listener listener = new BlipCountListener(conversation);
    try {
      conversation.addListener(listener);
      conversationListeners.put(conversation, listener);
      int existing = countBlips(conversation);
      conversationBlipCounts.put(conversation, existing);
      totalBlips += existing;
      if (logStats) {
        GWT.log("DynamicRenderer: observed conversation=" + safeConversationId(conversation)
            + " blips=" + existing + " total=" + totalBlips);
      }
    } catch (Throwable ignore) {
    }
  }

  private void removeConversation(ObservableConversation conversation) {
    if (conversation == null) {
      return;
    }
    ObservableConversation.Listener listener = conversationListeners.remove(conversation);
    if (listener != null) {
      try {
        conversation.removeListener(listener);
      } catch (Throwable ignore) {
      }
    }
    Integer count = conversationBlipCounts.remove(conversation);
    if (count != null) {
      totalBlips = Math.max(0, totalBlips - count.intValue());
      if (logStats) {
        GWT.log("DynamicRenderer: removed conversation=" + safeConversationId(conversation)
            + " removedCount=" + count + " total=" + totalBlips);
      }
    }
    Iterator<ConversationBlip> it = pagedIn.iterator();
    while (it.hasNext()) {
      ConversationBlip blip = it.next();
      if (blip != null && blip.getConversation() == conversation) {
        it.remove();
      }
    }
  }

  @Override
  public void onConversationAdded(ObservableConversation conversation) {
    observeConversation(conversation);
    throttleUpdate();
  }

  @Override
  public void onConversationRemoved(ObservableConversation conversation) {
    removeConversation(conversation);
    throttleUpdate();
  }

  private int countBlips(ObservableConversation conversation) {
    final int[] cnt = new int[] {0};
    try {
      BlipMappers.depthFirst(new Predicate<ConversationBlip>() {
        @Override
        public boolean apply(ConversationBlip blip) {
          cnt[0]++;
          return true;
        }
      }, conversation);
    } catch (Throwable ignore) {
      return 0;
    }
    return cnt[0];
  }

  private static String safeConversationId(ObservableConversation conversation) {
    try {
      return conversation != null ? conversation.getId() : "<null>";
    } catch (Throwable ignore) {
      return "<err>";
    }
  }

  private final class BlipCountListener extends ConversationListenerImpl {
    private final ObservableConversation conversation;

    BlipCountListener(ObservableConversation conversation) {
      this.conversation = conversation;
    }

    @Override
    public void onBlipAdded(ObservableConversationBlip blip) {
      Integer count = conversationBlipCounts.get(conversation);
      if (count != null) {
        int updated = count + 1;
        conversationBlipCounts.put(conversation, updated);
        totalBlips++;
        if (logStats) {
          GWT.log("DynamicRenderer: blipAdded convo=" + safeConversationId(conversation)
              + " count=" + updated + " total=" + totalBlips);
        }
      }
      throttleUpdate();
    }

    @Override
    public void onBlipDeleted(ObservableConversationBlip blip) {
      if (blip != null) {
        pagedIn.remove(blip);
      }
      Integer count = conversationBlipCounts.get(conversation);
      if (count != null && count > 0) {
        int updated = count - 1;
        conversationBlipCounts.put(conversation, updated);
        if (totalBlips > 0) totalBlips--;
        if (logStats) {
          GWT.log("DynamicRenderer: blipDeleted convo=" + safeConversationId(conversation)
              + " count=" + updated + " total=" + totalBlips);
        }
      }
      throttleUpdate();
    }

    @Override
    public void onThreadAdded(ObservableConversationThread thread) {
      throttleUpdate();
    }

    @Override
    public void onThreadDeleted(ObservableConversationThread thread) {
      throttleUpdate();
    }

    @Override
    public void onInlineThreadAdded(ObservableConversationThread thread, int location) {
      throttleUpdate();
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

  /**
   * Enqueues a small initial window of blips and defers a flush so the user sees
   * content immediately. Extracted for readability and maintainability.
   */
  private void enqueueBootstrapWindow() {
    final int[] boot = new int[] {0};
    BlipMappers.depthFirst(new Predicate<ConversationBlip>() {
      @Override
      public boolean apply(ConversationBlip blip) {
        if (boot[0] >= bootstrapMax) {
          return false; // stop
        }
        queue.add(blip);
        pagedIn.add(blip);
        boot[0]++;
        return true;
      }
    }, view);
    // Defer flush so we don't block the current UI turn.
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override public void execute() {
        try {
          queue.flush();
        } catch (Throwable t) {
          GWT.log("DynamicRenderer: bootstrap flush failed", t);
        }
      }
    });
  }

  private void doFragmentFetch(int top, int bottom) {
    // Record last requested range for potential retry
    lastFetchTop = top;
    lastFetchBottom = bottom;
    fragmentRequester.fetchRange(top, bottom, new FragmentRequester.Callback() {
      @Override public void onSuccess() {
        consecutiveFetchFailures = 0;
        retryBackoffMs = 500;
        fetchRetryScheduled = false;
      }
      @Override public void onError(Throwable error) {
        consecutiveFetchFailures = consecutiveFetchFailures + 1;
        // Provide simple developer feedback after a few consecutive failures
        if (consecutiveFetchFailures >= 3 && (consecutiveFetchFailures % 3) == 0) {
          GWT.log("DynamicRenderer: fragment fetch failing repeatedly (" + consecutiveFetchFailures + ")");
        }
        scheduleFetchRetry();
      }
    });
  }

  private void scheduleFetchRetry() {
    boolean shouldSchedule = !fetchRetryScheduled;
    if (shouldSchedule) {
      fetchRetryScheduled = true;
      final int delay = retryBackoffMs;
      // Exponential backoff with a cap
      retryBackoffMs = Math.min(MAX_RETRY_BACKOFF_MS, Math.max(retryBackoffMs, 250) * 2);
      try { if (logStats) com.google.gwt.core.client.GWT.log("DynamicRenderer: scheduling fragment fetch retry in " + delay + "ms (failures=" + consecutiveFetchFailures + ")"); } catch (Throwable ignore) {}
      Timer t = new Timer() {
        @Override public void run() {
          fetchRetryScheduled = false;
          doFragmentFetch(lastFetchTop, lastFetchBottom);
        }
      };
      t.schedule(delay);
    }
  }
}
