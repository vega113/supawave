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

import junit.framework.TestCase;

/** Regression coverage for preserving ancestor scroll positions during focus. */
public final class AncestorScrollPositionsTest extends TestCase {

  private static final class FakeElement {
    private final FakeElement parent;
    private int scrollTop;
    private int scrollLeft;

    private FakeElement(FakeElement parent, int scrollTop, int scrollLeft) {
      this.parent = parent;
      this.scrollTop = scrollTop;
      this.scrollLeft = scrollLeft;
    }
  }

  private static final AncestorScrollPositions.Adapter<FakeElement> ADAPTER =
      new AncestorScrollPositions.Adapter<FakeElement>() {
        @Override
        public int getScrollTop(FakeElement element) {
          return element.scrollTop;
        }

        @Override
        public int getScrollLeft(FakeElement element) {
          return element.scrollLeft;
        }

        @Override
        public void setScrollTop(FakeElement element, int scrollTop) {
          element.scrollTop = scrollTop;
        }

        @Override
        public void setScrollLeft(FakeElement element, int scrollLeft) {
          element.scrollLeft = scrollLeft;
        }

        @Override
        public FakeElement getParent(FakeElement element) {
          return element.parent;
        }
      };

  public void testRestorePreservesHorizontalAndVerticalScroll() {
    FakeElement root = new FakeElement(null, 5, 10);
    FakeElement parent = new FakeElement(root, 40, 120);
    FakeElement child = new FakeElement(parent, 80, 240);

    AncestorScrollPositions<FakeElement> positions =
        AncestorScrollPositions.capture(child, ADAPTER);

    root.scrollTop = 0;
    root.scrollLeft = 0;
    parent.scrollTop = 0;
    parent.scrollLeft = 0;
    child.scrollTop = 0;
    child.scrollLeft = 0;

    positions.restore(ADAPTER);

    assertEquals(5, root.scrollTop);
    assertEquals(10, root.scrollLeft);
    assertEquals(40, parent.scrollTop);
    assertEquals(120, parent.scrollLeft);
    assertEquals(80, child.scrollTop);
    assertEquals(240, child.scrollLeft);
  }
}
