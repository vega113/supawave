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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gwt.user.client.ui.Widget;

import junit.framework.TestCase;

import org.mockito.InOrder;
import org.waveprotocol.wave.client.common.util.LogicalPanel;
import org.waveprotocol.wave.client.doodad.selection.SelectionExtractor;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.wave.DocumentRegistry;
import org.waveprotocol.wave.client.wavepanel.impl.focus.BlipEditStatusListener;
import org.waveprotocol.wave.client.wavepanel.view.BlipMetaView;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;
import org.waveprotocol.wave.client.wavepanel.view.dom.ModelAsViewProvider;

import java.lang.reflect.Field;

public class EditSessionTest extends TestCase {

  public void testStopEditingFlushesPendingInputBeforeDetach() throws Exception {
    Editor editor = mock(Editor.class);
    Widget widget = mock(Widget.class);
    when(editor.getWidget()).thenReturn(widget);
    when(editor.isDraftMode()).thenReturn(false);

    SelectionExtractor selectionExtractor = mock(SelectionExtractor.class);
    LogicalPanel container = mock(LogicalPanel.class);
    BlipEditStatusListener editStatus = mock(BlipEditStatusListener.class);
    BlipMetaView meta = mock(BlipMetaView.class);
    BlipView blip = mock(BlipView.class);
    when(blip.getMeta()).thenReturn(meta);

    EditSession session = createSession(container, selectionExtractor, editStatus);
    setField(session, "editor", editor);
    setField(session, "editing", blip);

    session.stopEditing();

    InOrder ordered = inOrder(editor, selectionExtractor, container, editStatus);
    ordered.verify(editor).flushPendingInput();
    ordered.verify(selectionExtractor).stop(editor);
    ordered.verify(container).doOrphan(widget);
    ordered.verify(editor).blur();
    ordered.verify(editor).setEditing(false);
    ordered.verify(editStatus).setEditing(false);
    ordered.verify(editor).removeContent();
    ordered.verify(editor).reset();
  }

  public void testStopEditingFlushesPendingInputBeforeSavingDraft() throws Exception {
    Editor editor = mock(Editor.class);
    when(editor.isDraftMode()).thenReturn(true);
    when(editor.getWidget()).thenReturn(mock(Widget.class));

    BlipMetaView meta = mock(BlipMetaView.class);
    BlipView blip = mock(BlipView.class);
    when(blip.getMeta()).thenReturn(meta);

    EditSession session = createSession(mock(LogicalPanel.class),
        mock(SelectionExtractor.class), mock(BlipEditStatusListener.class));
    setField(session, "editor", editor);
    setField(session, "editing", blip);

    session.stopEditing();

    InOrder ordered = inOrder(editor);
    ordered.verify(editor).flushPendingInput();
    ordered.verify(editor).leaveDraftMode(true);
  }

  private static EditSession createSession(LogicalPanel container,
      SelectionExtractor selectionExtractor, BlipEditStatusListener editStatus) {
    return new EditSession(mock(ModelAsViewProvider.class),
        mock(DocumentRegistry.class), container, selectionExtractor, editStatus, null);
  }

  private static void setField(EditSession session, String name, Object value) throws Exception {
    Field field = EditSession.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(session, value);
  }
}
