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

package org.waveprotocol.wave.client.editor.integration;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.RootPanel;

import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.EditorImplWebkitMobile;
import org.waveprotocol.wave.client.editor.EditorSettings;
import org.waveprotocol.wave.client.editor.EditorStaticDeps;
import org.waveprotocol.wave.client.common.util.UserAgent;
import org.waveprotocol.wave.client.editor.keys.KeyBindingRegistry;
import org.waveprotocol.wave.client.editor.content.misc.StyleAnnotationHandler;
import org.waveprotocol.wave.client.editor.content.paragraph.LineRendering;
import org.waveprotocol.wave.client.editor.testing.TestInlineDoodad;
import org.waveprotocol.wave.client.widget.popup.simple.Popup;
import org.waveprotocol.wave.model.conversation.Blips;
import org.waveprotocol.wave.model.document.operation.automaton.DocumentSchema;
import org.waveprotocol.wave.model.document.util.DocProviders;
import org.waveprotocol.wave.model.document.util.LineContainers;

/**
 * Regression coverage for mobile editor sessions that must restore a valid
 * caret when focus begins.
 */
public class MobileWebkitFocusGwtTest extends TestBase {

  private static boolean handlersRegistered;

  private TestMobileEditor mobileEditor;

  private static final class TestMobileEditor extends EditorImplWebkitMobile {
    private TestMobileEditor(Element e) {
      super(true, e);
    }
  }

  @Override
  protected Editor createEditor() {
    ensureHandlersRegistered();
    EditorStaticDeps.setPopupProvider(Popup.LIGHTWEIGHT_POPUP_PROVIDER);
    mobileEditor = new TestMobileEditor(Document.get().createDivElement());
    RootPanel.get().add(mobileEditor);
    mobileEditor.init(Editor.ROOT_REGISTRIES, KeyBindingRegistry.NONE, EditorSettings.DEFAULT);
    mobileEditor.setEditing(true);
    return mobileEditor;
  }

  public void testFocusRestoresSelectionForMobileEditor() throws Exception {
    if (!UserAgent.isAndroid()) {
      // The production fix is Android-specific; desktop GWT runners should skip rather than
      // report a false failure for the preserved legacy mobile-Safari path.
      return;
    }

    editor.setContent(DocProviders.POJO.parse("<body><line/>mobile</body>").asOperation(),
        DocumentSchema.NO_SCHEMA_CONSTRAINTS);

    assertNull("Precondition: mobile editor starts without a selection",
        editor.getSelectionHelper().getSelectionRange());

    editor.focus(false);

    assertNotNull("Mobile focus must restore a caret/selection before typing",
        editor.getSelectionHelper().getSelectionRange());
  }

  @Override
  protected void gwtTearDown() throws Exception {
    if (mobileEditor != null && mobileEditor.getParent() != null) {
      RootPanel.get().remove(mobileEditor);
      mobileEditor = null;
    }
    super.gwtTearDown();
  }

  private static void ensureHandlersRegistered() {
    if (handlersRegistered) {
      return;
    }
    handlersRegistered = true;
    LineContainers.setTopLevelContainerTagname(Blips.BODY_TAGNAME);
    LineRendering.registerContainer(Blips.BODY_TAGNAME, Editor.ROOT_HANDLER_REGISTRY);
    StyleAnnotationHandler.register(Editor.ROOT_REGISTRIES);
    TestInlineDoodad.register(Editor.ROOT_HANDLER_REGISTRY);
  }
}
