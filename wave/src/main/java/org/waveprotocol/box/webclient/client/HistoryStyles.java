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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

/**
 * Injects CSS styles for the inline version history UI (scrubber bar,
 * diff overlay, and history-mode wave panel adjustments). Injection is
 * idempotent -- calling {@link #inject()} more than once is safe.
 */
public final class HistoryStyles {

  private static boolean injected = false;

  private HistoryStyles() {
  }

  /** Injects the history CSS into the document head. Safe to call multiple times. */
  public static void inject() {
    if (injected) {
      return;
    }
    injected = true;

    Element style = Document.get().createStyleElement();
    style.setInnerHTML(CSS);
    Document.get().getHead().appendChild(style);
  }

  private static final String CSS =
      // -- History scrubber bar (bottom of wave panel) --
      ".history-scrubber {"
      + "  position: fixed;"
      + "  bottom: 0;"
      + "  left: 0;"
      + "  right: 0;"
      + "  z-index: 10000;"
      + "  display: flex;"
      + "  align-items: center;"
      + "  gap: 12px;"
      + "  padding: 10px 20px;"
      + "  background: linear-gradient(135deg, #0d1b2a 0%, #1b3a5c 50%, #1a6fa0 100%);"
      + "  color: #e0f0ff;"
      + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;"
      + "  font-size: 13px;"
      + "  box-shadow: 0 -2px 12px rgba(0,0,0,0.3);"
      + "}"

      // -- Exit button --
      + ".history-scrubber-exit {"
      + "  cursor: pointer;"
      + "  padding: 6px 14px;"
      + "  border-radius: 4px;"
      + "  background: rgba(255,255,255,0.15);"
      + "  color: #fff;"
      + "  font-weight: 600;"
      + "  font-size: 13px;"
      + "  white-space: nowrap;"
      + "  border: 1px solid rgba(255,255,255,0.25);"
      + "  transition: background 0.15s;"
      + "  flex-shrink: 0;"
      + "}"
      + ".history-scrubber-exit:hover {"
      + "  background: rgba(255,255,255,0.3);"
      + "}"

      // -- Range input wrapper --
      + ".history-scrubber-range-wrapper {"
      + "  flex: 1;"
      + "  min-width: 100px;"
      + "}"

      // -- Range slider --
      + ".history-scrubber-range {"
      + "  width: 100%;"
      + "  height: 6px;"
      + "  -webkit-appearance: none;"
      + "  appearance: none;"
      + "  border-radius: 3px;"
      + "  outline: none;"
      + "  cursor: pointer;"
      + "}"
      + ".history-scrubber-range::-webkit-slider-thumb {"
      + "  -webkit-appearance: none;"
      + "  width: 18px;"
      + "  height: 18px;"
      + "  border-radius: 50%;"
      + "  background: #fff;"
      + "  border: 2px solid #0077b6;"
      + "  box-shadow: 0 1px 4px rgba(0,0,0,0.3);"
      + "  cursor: pointer;"
      + "}"
      + ".history-scrubber-range::-moz-range-thumb {"
      + "  width: 18px;"
      + "  height: 18px;"
      + "  border-radius: 50%;"
      + "  background: #fff;"
      + "  border: 2px solid #0077b6;"
      + "  box-shadow: 0 1px 4px rgba(0,0,0,0.3);"
      + "  cursor: pointer;"
      + "}"

      // -- Info label --
      + ".history-scrubber-label {"
      + "  white-space: nowrap;"
      + "  flex-shrink: 0;"
      + "  font-size: 12px;"
      + "}"
      + ".history-scrubber-author {"
      + "  font-weight: 600;"
      + "}"
      + ".history-scrubber-sep {"
      + "  opacity: 0.5;"
      + "  margin: 0 2px;"
      + "}"
      + ".history-scrubber-date {"
      + "  opacity: 0.85;"
      + "}"
      + ".history-scrubber-changes {"
      + "  opacity: 0.85;"
      + "}"
      + ".history-scrubber-version {"
      + "  opacity: 0.6;"
      + "  font-size: 11px;"
      + "}"

      // -- Tooltip --
      + ".history-scrubber-tooltip {"
      + "  position: absolute;"
      + "  bottom: 100%;"
      + "  margin-bottom: 8px;"
      + "  background: #1b3a5c;"
      + "  color: #e0f0ff;"
      + "  padding: 6px 10px;"
      + "  border-radius: 4px;"
      + "  font-size: 12px;"
      + "  white-space: nowrap;"
      + "  pointer-events: none;"
      + "  box-shadow: 0 2px 8px rgba(0,0,0,0.3);"
      + "}"

      // -- Diff container overlay --
      + ".history-diff-container {"
      + "  position: absolute;"
      + "  top: 0;"
      + "  left: 0;"
      + "  right: 0;"
      + "  bottom: 0;"
      + "  z-index: 100;"
      + "  background: #fff;"
      + "  overflow-y: auto;"
      + "  padding: 16px 24px 80px 24px;"
      + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;"
      + "}"

      // -- History header --
      + ".history-header {"
      + "  padding: 12px 16px;"
      + "  margin-bottom: 16px;"
      + "  background: linear-gradient(135deg, #e8f4f8 0%, #d0e8f2 100%);"
      + "  border-radius: 6px;"
      + "  border-left: 4px solid #0077b6;"
      + "  font-size: 14px;"
      + "  color: #1b3a5c;"
      + "}"
      + ".history-header-label {"
      + "  font-weight: 600;"
      + "  color: #0077b6;"
      + "}"

      // -- Blip cards --
      + ".history-blip {"
      + "  margin-bottom: 12px;"
      + "  padding: 12px 16px;"
      + "  border-radius: 6px;"
      + "  border: 1px solid #e0e0e0;"
      + "  background: #fafafa;"
      + "}"
      + ".history-blip-changed {"
      + "  border-color: #b3d9ff;"
      + "  background: #f0f8ff;"
      + "}"
      + ".history-blip-deleted {"
      + "  border-color: #ffb3b3;"
      + "  background: #fff5f5;"
      + "}"
      + ".history-blip-unchanged {"
      + "  opacity: 0.5;"
      + "}"
      + ".history-blip-header {"
      + "  margin-bottom: 8px;"
      + "  font-size: 13px;"
      + "  color: #444;"
      + "}"
      + ".history-blip-badge {"
      + "  display: inline-block;"
      + "  padding: 1px 6px;"
      + "  border-radius: 3px;"
      + "  font-size: 10px;"
      + "  font-weight: 700;"
      + "  text-transform: uppercase;"
      + "  letter-spacing: 0.5px;"
      + "  vertical-align: middle;"
      + "}"
      + ".history-blip-badge-new {"
      + "  background: #d4edda;"
      + "  color: #155724;"
      + "}"
      + ".history-blip-badge-modified {"
      + "  background: #cce5ff;"
      + "  color: #004085;"
      + "}"
      + ".history-blip-badge-deleted {"
      + "  background: #f8d7da;"
      + "  color: #721c24;"
      + "}"
      + ".history-blip-time {"
      + "  font-size: 12px;"
      + "  opacity: 0.6;"
      + "  margin-left: 6px;"
      + "}"
      + ".history-blip-content {"
      + "  font-size: 14px;"
      + "  line-height: 1.6;"
      + "  white-space: pre-wrap;"
      + "  word-wrap: break-word;"
      + "}"

      // -- Diff highlighting --
      + ".diff-add {"
      + "  background: #d4edda;"
      + "  color: #155724;"
      + "  padding: 1px 3px;"
      + "  border-radius: 2px;"
      + "  text-decoration: none;"
      + "}"
      + ".diff-del {"
      + "  background: #f8d7da;"
      + "  color: #721c24;"
      + "  padding: 1px 3px;"
      + "  border-radius: 2px;"
      + "  text-decoration: line-through;"
      + "}"

      // -- No changes message --
      + ".history-no-changes {"
      + "  text-align: center;"
      + "  padding: 24px;"
      + "  color: #888;"
      + "  font-style: italic;"
      + "}"

      // -- Wave panel adjustments when in history mode --
      + ".history-mode {"
      + "  position: relative;"
      + "  padding-bottom: 60px;"
      + "}";
}
