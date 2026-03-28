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

package org.waveprotocol.box.webclient.client;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Test;
import org.mockito.InOrder;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.content.ContentDocument;
import org.waveprotocol.wave.client.wavepanel.impl.edit.ReplyLocationResolver;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;

public final class ReplyLocationResolverTest {
  private static final class FakeEditContext implements ReplyLocationResolver.EditContext {
    private final boolean editing;
    private final BlipView blipUi;
    private final Editor editor;

    private FakeEditContext(boolean editing, BlipView blipUi, Editor editor) {
      this.editing = editing;
      this.blipUi = blipUi;
      this.editor = editor;
    }

    @Override
    public boolean isEditing() {
      return editing;
    }

    @Override
    public BlipView getBlip() {
      return blipUi;
    }

    @Override
    public Editor getEditor() {
      return editor;
    }
  }

  @Test
  public void resolveFlushesCurrentEditorBeforeReadingSelection() {
    BlipView blipUi = mock(BlipView.class);
    Editor editor = mock(Editor.class);
    ContentDocument contentDocument = mock(ContentDocument.class);
    FakeEditContext edit = new FakeEditContext(true, blipUi, editor);

    int location = ReplyLocationResolver.resolve(edit, blipUi, contentDocument,
        new ReplyLocationResolver.SelectionLocator() {
          @Override
          public int getLocation(ContentDocument ignored) {
            return 17;
          }
        });

    InOrder order = inOrder(editor);
    order.verify(editor).flushSaveSelection();
    order.verify(editor).flushUpdates();
    assertEquals(17, location);
  }

  @Test
  public void resolveSkipsFlushForDifferentBlip() {
    BlipView currentBlipUi = mock(BlipView.class);
    BlipView targetBlipUi = mock(BlipView.class);
    Editor editor = mock(Editor.class);
    ContentDocument contentDocument = mock(ContentDocument.class);
    FakeEditContext edit = new FakeEditContext(true, currentBlipUi, editor);

    int location = ReplyLocationResolver.resolve(edit, targetBlipUi, contentDocument,
        new ReplyLocationResolver.SelectionLocator() {
          @Override
          public int getLocation(ContentDocument ignored) {
            return 29;
          }
        });

    verifyNoInteractions(editor);
    assertEquals(29, location);
  }

  @Test
  public void resolveSkipsFlushWhenNotEditing() {
    BlipView blipUi = mock(BlipView.class);
    Editor editor = mock(Editor.class);
    ContentDocument contentDocument = mock(ContentDocument.class);
    FakeEditContext edit = new FakeEditContext(false, blipUi, editor);

    int location = ReplyLocationResolver.resolve(edit, blipUi, contentDocument,
        new ReplyLocationResolver.SelectionLocator() {
          @Override
          public int getLocation(ContentDocument ignored) {
            return 41;
          }
        });

    verifyNoInteractions(editor);
    assertEquals(41, location);
  }

  @Test
  public void resolveSkipsFlushWhenEditorMissing() {
    BlipView blipUi = mock(BlipView.class);
    ContentDocument contentDocument = mock(ContentDocument.class);
    FakeEditContext edit = new FakeEditContext(true, blipUi, null);

    int location = ReplyLocationResolver.resolve(edit, blipUi, contentDocument,
        new ReplyLocationResolver.SelectionLocator() {
          @Override
          public int getLocation(ContentDocument ignored) {
            return 53;
          }
        });

    assertEquals(53, location);
  }
}
