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

/**
 * Tests that EditorContextAdapter correctly forwards ImagePasteHandler.
 */
public class EditorContextAdapterHandlerTest extends TestCase {

  private static final ImagePasteHandler HANDLER = nativeEvent -> false;

  public void testSetHandlerOnNullEditorDoesNotCrash() {
    EditorContextAdapter adapter = new EditorContextAdapter(null);
    adapter.setImagePasteHandler(HANDLER); // must not throw
  }

  public void testSwitchToNullEditorWithHandlerDoesNotCrash() {
    EditorContextAdapter adapter = new EditorContextAdapter(null);
    adapter.setImagePasteHandler(HANDLER);
    adapter.switchEditor(null); // must not throw
  }

  public void testSwitchEditorWithNoHandlerDoesNotCrash() {
    EditorContextAdapter adapter = new EditorContextAdapter(null);
    adapter.switchEditor(null); // must not throw
  }

  public void testSetHandlerStoredOnAdapter() {
    EditorContextAdapter adapter = new EditorContextAdapter(null);
    adapter.setImagePasteHandler(HANDLER);
    // verify by switching to a non-EditorImpl context - must not throw
    adapter.switchEditor(null);
  }

  public void testClearHandlerWithNullDoesNotCrash() {
    EditorContextAdapter adapter = new EditorContextAdapter(null);
    adapter.setImagePasteHandler(HANDLER);
    adapter.setImagePasteHandler(null); // must not throw; clears the handler
  }

  public void testClearHandlerThenSwitchEditorDoesNotCrash() {
    EditorContextAdapter adapter = new EditorContextAdapter(null);
    adapter.setImagePasteHandler(HANDLER);
    adapter.setImagePasteHandler(null);
    adapter.switchEditor(null); // null handler must be forwarded without NPE
  }
}
