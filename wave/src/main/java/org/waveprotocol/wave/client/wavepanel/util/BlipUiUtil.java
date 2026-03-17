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

package org.waveprotocol.wave.client.wavepanel.util;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import org.waveprotocol.wave.client.wavepanel.view.BlipView;

/** Small helpers for working with BlipView UI. */
public final class BlipUiUtil {
  private BlipUiUtil() {}

  /** Returns true if the blip DOM carries the quasi-deleted marker. */
  public static boolean isQuasiDeleted(BlipView blipUi) {
    boolean deleted = false;
    if (blipUi != null) {
      try {
        Element e = Document.get().getElementById(blipUi.getId());
        deleted = (e != null && e.hasAttribute("data-deleted"));
      } catch (Throwable ignore) {
        deleted = false;
      }
    }
    return deleted;
  }
}
