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

package org.waveprotocol.wave.client.editor.content;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.testing.TestEditors;
import org.waveprotocol.wave.model.document.operation.automaton.DocumentSchema;

/**
 * Regression tests for NodeEventRouter null-guard paths.
 *
 * <p>These tests cover the fix from PR #642 that prevents NPEs when
 * {@code getTypingExtractor()} returns null during editor shutdown.
 */
public class NodeEventRouterGwtTest extends EditorGwtTestCase {

  /**
   * Regression test for PR #642.
   *
   * <p>When the editor is shutting down, {@code LowLevelEditingConcerns.STUB.getTypingExtractor()}
   * returns null. Before PR #642, calling backspace on a single-character text node would NPE
   * inside {@code handleTextNodeDeleteAction} at the {@code extractor.flush()} call site.
   *
   * <p>After the fix, the method returns {@code true} (cancel browser default) without throwing.
   */
  public void testHandleBackspaceNotAtBeginning_returnsTrueWhenTypingExtractorIsNull() {
    // Set up a ContentDocument with rendering (same pattern as TestEditors.createTestDocument),
    // but keep the editor reference so we can detach it later.
    ContentDocument dom = new ContentDocument(DocumentSchema.NO_SCHEMA_CONSTRAINTS);
    Registries registries = Editor.ROOT_REGISTRIES.createExtension();
    for (String tag : new String[]{"q", "a", "b", "c", "x"}) {
      final String t = tag;
      registries.getElementHandlerRegistry().registerRenderer(t,
          new Renderer() {
            @Override
            public Element createDomImpl(Renderer.Renderable element) {
              return element.setAutoAppendContainer(Document.get().createElement(t));
            }
          });
    }
    dom.setRegistries(registries);
    Editor editor = TestEditors.getMinimalEditor();
    editor.setContent(dom);

    // Create a single-character text node so that implDataLength <= 1 triggers the flush path.
    ContentRawDocument c = dom.debugGetRawDocument();
    ContentElement root = c.getDocumentElement();
    ContentTextNode textNode = c.createTextNode("a", root, null);

    // Detach the editor to simulate the "editor shutting down" race condition.
    // editor.removeContent() calls dom.setRendering(), which sets editingConcerns = STUB so
    // that getTypingExtractor() returns null. The document stays at RENDERED level so the
    // impl nodelet remains attached and getImplDataLength() still works.
    editor.removeContent();

    // Verify the null guard: must return true without throwing NullPointerException.
    boolean result = NodeEventRouter.INSTANCE.handleBackspaceNotAtBeginning(textNode, null);
    assertTrue("handleBackspaceNotAtBeginning must return true (cancel browser default) "
        + "when TypingExtractor is null (editor STUB mode)", result);
  }
}
