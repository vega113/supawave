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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

/** Minimal DOM utilities used by the dynamic renderer path. */
public final class RenderUtil {
  private RenderUtil() {}

  public static int clamp(int x, int min, int max) {
    return x < min ? min : (x > max ? max : x);
  }

  public static int getAbsoluteTop(Element e) {
    int top = 0;
    Element cur = e;
    while (cur != null && cur != Document.get().getBody()) {
      top += cur.getOffsetTop();
      cur = cur.getOffsetParent();
    }
    return top;
  }

  public static boolean intersects(int aTop, int aBottom, int bTop, int bBottom) {
    return aBottom >= bTop && aTop <= bBottom;
  }

  public static void setClass(Element e, String cls, boolean add) {
    if (e == null || cls == null || cls.isEmpty()) return;
    // Rely on GWT Element.addClassName/removeClassName idempotence to avoid
    // string scanning per call. This minimizes string work and reflow triggers.
    if (add) {
      e.addClassName(cls);
    } else {
      e.removeClassName(cls);
    }
  }
}
