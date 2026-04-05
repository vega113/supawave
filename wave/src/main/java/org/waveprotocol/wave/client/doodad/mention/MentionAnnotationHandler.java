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

import org.waveprotocol.wave.client.editor.content.AnnotationPainter.PaintFunction;
import org.waveprotocol.wave.client.editor.content.AnnotationPainter;
import org.waveprotocol.wave.client.editor.content.PainterRegistry;
import org.waveprotocol.wave.client.editor.content.Registries;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.AnnotationBehaviour.DefaultAnnotationBehaviour;
import org.waveprotocol.wave.model.document.AnnotationBehaviour.BiasDirection;
import org.waveprotocol.wave.model.document.AnnotationBehaviour.CursorDirection;
import org.waveprotocol.wave.model.document.AnnotationMutationHandler;
import org.waveprotocol.wave.model.document.util.DocumentContext;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.ReadableStringSet;
import org.waveprotocol.wave.model.util.StringMap;

import java.util.Collections;
import java.util.Map;

/**
 * Annotation handler for @mention annotations. Renders mentioned usernames
 * with a highlight background color.
 */
public class MentionAnnotationHandler implements AnnotationMutationHandler {

  private static final String MENTION_COLOUR = "#D1E8FF";

  private static final ReadableStringSet MENTION_KEYS = CollectionUtils.newStringSet(
      AnnotationConstants.MENTION_USER);

  private static final PaintFunction MENTION_PAINT_FUNC = new PaintFunction() {
    @Override
    public Map<String, String> apply(Map<String, Object> from, boolean isEditing) {
      Object value = from.get(AnnotationConstants.MENTION_USER);
      if (value != null && !value.toString().isEmpty()) {
        Map<String, String> styles = new java.util.HashMap<String, String>();
        styles.put("backgroundColor", MENTION_COLOUR);
        styles.put("fontWeight", "600");
        return styles;
      }
      return Collections.emptyMap();
    }
  };

  private final AnnotationPainter painter;

  private MentionAnnotationHandler(AnnotationPainter painter) {
    this.painter = painter;
  }

  public static void register(Registries registries) {
    PainterRegistry painterRegistry = registries.getPaintRegistry();
    MentionAnnotationHandler handler = new MentionAnnotationHandler(painterRegistry.getPainter());
    registries.getAnnotationHandlerRegistry().registerHandler(
        AnnotationConstants.MENTION_PREFIX, handler);
    painterRegistry.registerPaintFunction(MENTION_KEYS, MENTION_PAINT_FUNC);
    registries.getAnnotationHandlerRegistry().registerBehaviour(
        AnnotationConstants.MENTION_PREFIX,
        new DefaultAnnotationBehaviour() {
          @Override
          public BiasDirection getBias(StringMap<Object> left, StringMap<Object> right,
              CursorDirection cursorDirection) {
            return BiasDirection.LEFT;
          }
        });
  }

  @Override
  public <N, E extends N, T extends N> void handleAnnotationChange(
      DocumentContext<N, E, T> bundle, int start, int end, String key, Object newValue) {
    painter.scheduleRepaint(bundle, start, end);
  }
}
