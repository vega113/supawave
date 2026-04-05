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

package org.waveprotocol.wave.client.widget.popup;

import org.waveprotocol.wave.client.widget.popup.desktopchrome.DesktopPopupChromeProvider;

/**
 * Creates popup chrome by deferring to a provider.
 *
 * <p>TODO(j2cl): deferred binding removed; always uses DesktopPopupChromeProvider.
 * Mobile chrome support (MobilePopupChromeProvider) was previously selected at compile
 * time via GWT replace-with rules; that mechanism is incompatible with J2CL.
 */
public class PopupChromeFactory {

  private static PopupChromeProvider provider;

  /**
   * Returns the singleton popup chrome provider.
   */
  public static PopupChromeProvider getProvider() {
    if (provider == null) {
      provider = new DesktopPopupChromeProvider();
    }
    return provider;
  }

  /**
   * Create some popup chrome.
   */
  public static PopupChrome createPopupChrome() {
    return getProvider().createPopupChrome();
  }
}
