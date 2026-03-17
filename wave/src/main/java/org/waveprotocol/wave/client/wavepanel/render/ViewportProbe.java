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

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;

/** Developer-only probe to log simple viewport/DOM stats for the dynamic renderer.
 * It performs a one-shot measurement and schedules no periodic timers by default
 * to avoid leaks.
 */
public final class ViewportProbe {
  private ViewportProbe() {}

  /** Max elements to scan to bound probe cost. */
  private static final int MAX_SCAN = 10000;

  /** Counts elements whose className contains the given token (e.g., "blip"). */
  private static int countByClassToken(String token) {
    int count = 0;
    try {
      NodeList<Element> all = Document.get().getElementsByTagName("div");
      int n = Math.min(all.getLength(), MAX_SCAN);
      for (int i = 0; i < n; i++) {
        Element e = all.getItem(i);
        String cls = e.getClassName();
        if (cls != null && !cls.isEmpty()) {
          if (cls.equals(token) || cls.startsWith(token + " ") || cls.endsWith(" " + token)
              || cls.contains(" " + token + " ")) {
            count++;
          }
        }
      }
      if (all.getLength() > MAX_SCAN) {
        GWT.log("ViewportProbe: truncated scan at " + MAX_SCAN + " elements (total=" + all.getLength() + ")");
      }
    } catch (Throwable t) {
      GWT.log("ViewportProbe: count failed: " + t.getClass().getSimpleName());
    }
    return count;
  }

  /** Logs a one-shot measurement: blip count, placeholder count, and async timers. */
  public static void runOnce(String tag) {
    try {
      int blips = countByClassToken("blip");
      int placeholders = countByClassToken("placeholder");
      int timers = BlipAsyncRegistry.totalTimers();
      GWT.log("ViewportProbe[" + tag + "]: blips=" + blips + ", placeholders=" + placeholders + ", timers=" + timers);
    } catch (Throwable t) {
      // Keep this silent in prod; the probe is developer-only.
      GWT.log("ViewportProbe failed: " + t.getClass().getSimpleName());
    }
  }
}
