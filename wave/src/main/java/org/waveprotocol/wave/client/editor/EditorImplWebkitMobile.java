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

import com.google.gwt.dom.client.Element;
import org.waveprotocol.wave.client.common.util.UserAgent;

/**
 * Disable selected legacy mobile behaviors while preserving Android edit focus.
 *
 * @author danilatos@google.com (Daniel Danilatos)
 *
 */
public class EditorImplWebkitMobile extends EditorImpl {

  protected EditorImplWebkitMobile(boolean ownsDocument, Element e) {
    super(ownsDocument, e);
  }

  @Override
  public void focus(boolean collapsed) {
    // Android needs the standard focus path so edit sessions start with a real caret.
    if (UserAgent.isAndroid()) {
      super.focus(collapsed);
    }
  }

  @Override
  public void blur() {
    // do nothing
  }
}
