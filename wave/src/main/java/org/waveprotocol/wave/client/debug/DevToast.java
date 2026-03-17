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
package org.waveprotocol.wave.client.debug;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;

import java.util.HashSet;
import java.util.Set;

/** Dev-only toast helper for lightweight, ephemeral notifications. */
public final class DevToast {
  private static final Set<String> shownKeys = new HashSet<>();

  private DevToast() {}

  private static boolean isDevHost() {
    try {
      String h = Window.Location.getHostName();
      if (h == null) return false;
      h = h.toLowerCase();
      return "localhost".equals(h) || "127.0.0.1".equals(h) || "local.net".equals(h);
    } catch (Throwable ignore) {
      return false;
    }
  }

  /** Shows a one-shot toast (by key) if on a dev host or ll=debug is present. */
  public static void showOnce(String key, String message) {
    try {
      String ll = Window.Location.getParameter("ll");
      boolean dev = isDevHost() || "debug".equals(ll);
      if (!dev) return;
      if (shownKeys.contains(key)) return;
      shownKeys.add(key);

      final Element toast = Document.get().createDivElement();
      toast.setInnerText(message);
      toast.getStyle().setProperty("position", "fixed");
      toast.getStyle().setProperty("right", "8px");
      toast.getStyle().setProperty("top", "8px");
      toast.getStyle().setProperty("zIndex", "2147483647");
      toast.getStyle().setProperty("background", "#323232");
      toast.getStyle().setProperty("color", "#fff");
      toast.getStyle().setProperty("padding", "6px 10px");
      toast.getStyle().setProperty("font", "12px/16px sans-serif");
      toast.getStyle().setProperty("borderRadius", "4px");
      toast.getStyle().setProperty("boxShadow", "0 2px 6px rgba(0,0,0,0.3)");
      toast.getStyle().setProperty("opacity", "0.95");
      Document.get().getBody().appendChild(toast);

      new Timer() {
        @Override public void run() {
          try { toast.getStyle().setProperty("transition", "opacity 300ms ease"); toast.getStyle().setProperty("opacity", "0"); } catch (Throwable ignore) {}
          new Timer() { @Override public void run() { try { toast.removeFromParent(); } catch (Throwable ignore) {} } }.schedule(400);
        }
      }.schedule(1600);
    } catch (Throwable ignore) {
      // best-effort only
    }
  }
}

