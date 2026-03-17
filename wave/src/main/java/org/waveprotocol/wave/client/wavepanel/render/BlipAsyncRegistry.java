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

import com.google.gwt.user.client.Timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Simple registry to cancel timers associated with a blip DOM id. */
public final class BlipAsyncRegistry {
  private static final Map<String, List<Timer>> TIMERS = new HashMap<String, List<Timer>>();

  private BlipAsyncRegistry() {}

  public static void registerTimer(String blipDomId, Timer t) {
    if (blipDomId == null || t == null) return;
    List<Timer> list = TIMERS.get(blipDomId);
    if (list == null) {
      list = new ArrayList<Timer>();
      TIMERS.put(blipDomId, list);
    }
    list.add(t);
  }

  public static void cancelAllFor(String blipDomId) {
    List<Timer> list = TIMERS.remove(blipDomId);
    if (list != null) {
      for (int i = 0; i < list.size(); i++) {
        try { list.get(i).cancel(); } catch (Throwable ignored) {}
      }
      list.clear();
    }
  }

  /** Returns the number of timers currently registered across all blips. */
  public static int totalTimers() {
    int total = 0;
    for (Map.Entry<String, List<Timer>> e : TIMERS.entrySet()) {
      List<Timer> list = e.getValue();
      total += (list == null) ? 0 : list.size();
    }
    return total;
  }
}
