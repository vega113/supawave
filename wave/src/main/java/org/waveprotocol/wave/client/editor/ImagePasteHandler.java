/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.wave.client.editor;

import com.google.gwt.dom.client.NativeEvent;

/**
 * Hook for intercepting image paste events before the standard text paste path.
 *
 * <p>Registered on {@link EditorImpl} via
 * {@link EditorContextAdapter#setImagePasteHandler(ImagePasteHandler)}.
 */
public interface ImagePasteHandler {

  /**
   * Called when a native paste event fires on the editor.
   *
   * @param nativeEvent the raw browser paste event (may contain clipboardData)
   * @return {@code true} if an image was found and the upload was initiated
   *         (the caller should suppress text-paste handling); {@code false}
   *         to fall through to the normal text/HTML paste path
   */
  boolean handleImagePaste(NativeEvent nativeEvent);
}
