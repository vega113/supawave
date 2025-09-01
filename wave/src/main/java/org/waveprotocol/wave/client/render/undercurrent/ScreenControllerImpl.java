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

package org.waveprotocol.wave.client.render.undercurrent;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Window.ScrollEvent;
import com.google.gwt.user.client.Window.ScrollHandler;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.user.client.Window;

import java.util.ArrayList;
import java.util.List;

/** Minimal, safe implementation using the document body as the scroll panel. */
public final class ScreenControllerImpl implements ScreenController {
  private final List<Listener> listeners = new ArrayList<Listener>();
  private final Element scrollEl;

  private ScreenControllerImpl(Element scrollEl) {
    this.scrollEl = scrollEl;
    Window.addResizeHandler(new ResizeHandler() {
      @Override public void onResize(ResizeEvent event) { notifyListeners(); }
    });
    Window.addWindowScrollHandler(new ScrollHandler() {
      @Override public void onWindowScroll(ScrollEvent event) { notifyListeners(); }
    });
    // Poll after scroll settles; direct DOM scroll events not wired here to keep minimal.
  }

  public static ScreenControllerImpl createDefault() {
    Element body = Document.get().getBody();
    return new ScreenControllerImpl(body);
  }

  @Override
  public void addListener(Listener l) {
    if (l != null && !listeners.contains(l)) listeners.add(l);
  }

  @Override
  public void removeListener(Listener l) { listeners.remove(l); }

  @Override
  public int getScrollTop() { return scrollEl.getScrollTop(); }

  @Override
  public int getViewportHeight() { return Window.getClientHeight(); }

  @Override
  public void setScrollTop(final int y, boolean animate) {
    // Ignore animation; schedule to let other tasks run first.
    Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
      @Override public void execute() { scrollEl.setScrollTop(y); notifyListeners(); }
    });
  }

  private void notifyListeners() {
    int top = getScrollTop();
    int h = getViewportHeight();
    for (int i = 0; i < listeners.size(); i++) {
      try { listeners.get(i).onScreenChanged(top, h); } catch (RuntimeException ignored) {}
    }
  }
}
