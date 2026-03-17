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
import com.google.gwt.user.client.ui.ComplexPanel;
import com.google.gwt.user.client.ui.Widget;
import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.model.conversation.ConversationBlip;

/**
 * Cleans up resources associated with a blip during page-out.
 * - Orphans any widgets adopted into the logical panel whose roots live under the blip element
 * - Cancels timers associated with the blip DOM id (via BlipAsyncRegistry)
 * - Placeholder for custom listener cleanup if needed later
 */
public final class BlipResourceCleaner implements BlipPager.ResourceCleaner {
  private final LogicalPanel logicalPanel;

  public BlipResourceCleaner(LogicalPanel logicalPanel) {
    this.logicalPanel = logicalPanel;
  }

  @Override
  public void cleanup(ConversationBlip blip, org.waveprotocol.wave.client.wavepanel.view.dom.BlipViewDomImpl blipDom) {
    if (logicalPanel instanceof ComplexPanel) {
      ComplexPanel panel = (ComplexPanel) logicalPanel;
      Element root = blipDom.getElement();
      // Iterate backwards to avoid index shifting when orphaning
      for (int i = panel.getWidgetCount() - 1; i >= 0; i--) {
        Widget w = panel.getWidget(i);
        if (w != null && w.getElement() != null && isDescendant(root, w.getElement())) {
          try { logicalPanel.doOrphan(w); } catch (Throwable ignored) {}
        }
      }
    }

    // Cancel any timers registered against this blip DOM id
    try { BlipAsyncRegistry.cancelAllFor(blipDom.getId()); } catch (Throwable ignored) {}
  }

  private static boolean isDescendant(Element ancestor, Element node) {
    if (ancestor == null || node == null) return false;
    Element cur = node;
    while (cur != null) {
      if (cur == ancestor) return true;
      cur = cur.getParentElement();
    }
    return false;
  }
}

