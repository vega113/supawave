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
package org.waveprotocol.wave.client.doodad.mention;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Event;

import org.waveprotocol.wave.client.account.Profile;
import org.waveprotocol.wave.client.account.ProfileManager;
import org.waveprotocol.wave.client.editor.content.AnnotationPainter;
import org.waveprotocol.wave.client.editor.content.AnnotationPainter.PaintFunction;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.PainterRegistry;
import org.waveprotocol.wave.client.editor.content.Registries;
import org.waveprotocol.wave.client.editor.content.misc.AnnotationPaint;
import org.waveprotocol.wave.client.widget.popup.AlignedPopupPositioner;
import org.waveprotocol.wave.client.widget.profile.ProfilePopupPresenter;
import org.waveprotocol.wave.client.widget.profile.ProfilePopupWidget;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.AnnotationBehaviour.AnnotationFamily;
import org.waveprotocol.wave.model.document.AnnotationBehaviour.BiasDirection;
import org.waveprotocol.wave.model.document.AnnotationBehaviour.CursorDirection;
import org.waveprotocol.wave.model.document.AnnotationBehaviour.DefaultAnnotationBehaviour;
import org.waveprotocol.wave.model.document.AnnotationMutationHandler;
import org.waveprotocol.wave.model.document.util.DocumentContext;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.ReadableStringSet;
import org.waveprotocol.wave.model.util.StringMap;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Annotation handler for @mention annotations. Renders mentioned usernames
 * with a highlight background color and shows a profile popup on click.
 */
public class MentionAnnotationHandler implements AnnotationMutationHandler {

  private static final String MENTION_COLOUR = "#D1E8FF";
  private static final String MENTION_HANDLER_KEY = "mentionProfile";

  private static final ReadableStringSet MENTION_KEYS = CollectionUtils.newStringSet(
      AnnotationConstants.MENTION_USER);

  private static PaintFunction createPaintFunction() {
    return new PaintFunction() {
      @Override
      public Map<String, String> apply(Map<String, Object> from, boolean isEditing) {
        Object value = from.get(AnnotationConstants.MENTION_USER);
        if (value != null && !value.toString().isEmpty()) {
          Map<String, String> styles = new HashMap<String, String>();
          styles.put("backgroundColor", MENTION_COLOUR);
          styles.put("fontWeight", "600");
          styles.put("cursor", "pointer");
          styles.put(AnnotationPaint.MOUSE_LISTENER_ATTR, MENTION_HANDLER_KEY);
          // Store the mention address for retrieval in the event handler.
          // Keys applied through GWT Style.setProperty must stay camelCase; data-* keys are
          // handled as DOM attributes by AnnotationSpreadRenderer instead.
          styles.put("mentionAddr", value.toString());
          styles.put("data-mention-address", value.toString());
          return styles;
        }
        return Collections.emptyMap();
      }
    };
  }

  private final AnnotationPainter painter;

  private MentionAnnotationHandler(AnnotationPainter painter) {
    this.painter = painter;
  }

  public static void register(Registries registries, final ProfileManager profileManager) {
    PainterRegistry painterRegistry = registries.getPaintRegistry();
    MentionAnnotationHandler handler = new MentionAnnotationHandler(painterRegistry.getPainter());
    registries.getAnnotationHandlerRegistry().registerHandler(
        AnnotationConstants.MENTION_PREFIX, handler);
    painterRegistry.registerPaintFunction(MENTION_KEYS, createPaintFunction());
    registries.getAnnotationHandlerRegistry().registerBehaviour(
        AnnotationConstants.MENTION_PREFIX,
        new DefaultAnnotationBehaviour(AnnotationFamily.CONTENT) {
          @Override
          public BiasDirection getBias(StringMap<Object> left, StringMap<Object> right,
              CursorDirection cursorDirection) {
            // Push cursor outside the mention boundary so new text does not inherit
            // the annotation. RIGHT at the right edge (left has annotation) means
            // the cursor associates with the next (unannotated) character; LEFT at
            // the left edge does the same. Mirrors LinkAnnotationHandler exactly.
            if (left.get(AnnotationConstants.MENTION_USER) != null) {
              return BiasDirection.RIGHT;
            }
            if (right.get(AnnotationConstants.MENTION_USER) != null) {
              return BiasDirection.LEFT;
            }
            return BiasDirection.NEITHER;
          }
        });

    AnnotationPaint.registerEventHandler(MENTION_HANDLER_KEY, new AnnotationPaint.EventHandler() {
      @Override
      public void onEvent(ContentElement node, Event event) {
        if (event.getTypeInt() == Event.ONCLICK) {
          String address = node.getAttribute("mentionAddr");
          if (address != null && !address.isEmpty()) {
            showProfilePopup(node.getImplNodelet(), address, profileManager);
          }
        }
      }
    });
  }

  /**
   * Shows a profile popup card anchored below the mention element.
   */
  private static void showProfilePopup(
      Element anchor, String address, ProfileManager profileManager) {
    ParticipantId pid = ParticipantId.ofUnsafe(address);
    Profile profile = profileManager.getProfile(pid);
    if (profile != null) {
      ProfilePopupWidget widget =
          new ProfilePopupWidget(anchor, AlignedPopupPositioner.BELOW_LEFT);
      ProfilePopupPresenter.create(profile, widget, profileManager).show();
    }
  }

  @Override
  public <N, E extends N, T extends N> void handleAnnotationChange(
      DocumentContext<N, E, T> bundle, int start, int end, String key, Object newValue) {
    painter.scheduleRepaint(bundle, start, end);
  }
}
