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

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;

public final class ReplyLocationResolver {
  public interface EditContext {
    boolean isEditing();

    BlipView getBlip();

    Editor getEditor();
  }

  public interface SelectionLocator {
    int getLocation(ContentDocument contentDocument);
  }

  private static final SelectionLocator DEFAULT_SELECTION_LOCATOR = new SelectionLocator() {
    @Override
    public int getLocation(ContentDocument contentDocument) {
      return DocumentUtil.getLocationNearSelection(contentDocument);
    }
  };

  private ReplyLocationResolver() {
  }

  public static int resolve(EditSession edit, BlipView blipUi, ContentDocument contentDocument) {
    return resolve(new EditContext() {
      @Override
      public boolean isEditing() {
        return edit.isEditing();
      }

      @Override
      public BlipView getBlip() {
        return edit.getBlip();
      }

      @Override
      public Editor getEditor() {
        return edit.getEditor();
      }
    }, blipUi, contentDocument, DEFAULT_SELECTION_LOCATOR);
  }

  public static int resolve(EditContext edit, BlipView blipUi, ContentDocument contentDocument,
      SelectionLocator selectionLocator) {
    flushCurrentEditor(edit, blipUi);
    return selectionLocator.getLocation(contentDocument);
  }

  private static void flushCurrentEditor(EditContext edit, BlipView blipUi) {
    if (!edit.isEditing() || edit.getBlip() != blipUi) {
      return;
    }

    Editor editor = edit.getEditor();
    if (editor == null) {
      return;
    }

    editor.flushSaveSelection();
    editor.flushUpdates();
  }
}
