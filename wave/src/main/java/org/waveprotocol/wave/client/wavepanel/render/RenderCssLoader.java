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

package org.waveprotocol.wave.client.wavepanel.render;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;

/** Loads supplemental render CSS (placeholders, animations) used by dynamic renderer. */
public final class RenderCssLoader {
  interface Resources extends ClientBundle {
    @Source("Render.css")
    TextResource css();
  }

  private static final Resources RES = GWT.create(Resources.class);
  private static boolean injected = false;

  private RenderCssLoader() {}

  public static void ensureInjected() {
    if (!injected) {
      StyleInjector.inject(RES.css().getText(), true);
      injected = true;
    }
  }
}

