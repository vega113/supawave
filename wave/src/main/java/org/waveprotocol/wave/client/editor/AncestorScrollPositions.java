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

package org.waveprotocol.wave.client.editor;

import java.util.IdentityHashMap;
import java.util.Map;

/** Captures and restores scroll positions for an element and all of its ancestors. */
final class AncestorScrollPositions<T> {

  interface Adapter<T> {
    int getScrollTop(T element);

    int getScrollLeft(T element);

    void setScrollTop(T element, int scrollTop);

    void setScrollLeft(T element, int scrollLeft);

    T getParent(T element);
  }

  private static final class ScrollPosition {
    private final int scrollTop;
    private final int scrollLeft;

    private ScrollPosition(int scrollTop, int scrollLeft) {
      this.scrollTop = scrollTop;
      this.scrollLeft = scrollLeft;
    }
  }

  private final IdentityHashMap<T, ScrollPosition> positions;

  private AncestorScrollPositions(IdentityHashMap<T, ScrollPosition> positions) {
    this.positions = positions;
  }

  static <T> AncestorScrollPositions<T> capture(T start, Adapter<T> adapter) {
    IdentityHashMap<T, ScrollPosition> positions = new IdentityHashMap<T, ScrollPosition>();
    T current = start;
    while (current != null) {
      positions.put(current, new ScrollPosition(
          adapter.getScrollTop(current), adapter.getScrollLeft(current)));
      current = adapter.getParent(current);
    }
    return new AncestorScrollPositions<T>(positions);
  }

  void restore(Adapter<T> adapter) {
    for (Map.Entry<T, ScrollPosition> entry : positions.entrySet()) {
      T element = entry.getKey();
      ScrollPosition position = entry.getValue();
      adapter.setScrollTop(element, position.scrollTop);
      adapter.setScrollLeft(element, position.scrollLeft);
    }
  }
}
