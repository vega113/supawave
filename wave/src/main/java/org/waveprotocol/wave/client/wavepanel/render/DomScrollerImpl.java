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
import com.google.gwt.user.client.Window;

/** Minimal scroller using the document body with safe clamping. */
public final class DomScrollerImpl {
  private final Element body = Document.get().getBody();
  private boolean scheduled = false;
  private int pendingY = 0;

  public int getScrollTop() {
    return body.getScrollTop();
  }

  public void setScrollTop(final int yRaw, boolean animate) {
    int viewport = Window.getClientHeight();
    int content = body.getScrollHeight();
    int max = Math.max(0, content - Math.max(0, viewport));
    final int y = RenderUtil.clamp(yRaw, 0, max);
    pendingY = y;
    if (!scheduled) {
      scheduled = true;
      Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
        @Override public void execute() {
          scheduled = false;
          body.setScrollTop(pendingY);
        }
      });
    }
  }
}
