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
 * snapshot rendering, and history-mode wave panel adjustments). Injection
 * is idempotent -- calling {@link #inject()} more than once is safe.
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
      // -- History scrubber bar (fixed at bottom) --
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

      // -- Restore button --
      + ".history-scrubber-restore {"
      + "  cursor: pointer;"
      + "  padding: 6px 14px;"
      + "  border-radius: 4px;"
      + "  background: #2e7d32;"
      + "  color: #fff;"
      + "  font-weight: 600;"
      + "  font-size: 13px;"
      + "  white-space: nowrap;"
      + "  border: 1px solid rgba(255,255,255,0.25);"
      + "  transition: background 0.15s;"
      + "  flex-shrink: 0;"
      + "}"
      + ".history-scrubber-restore:hover {"
      + "  background: #388e3c;"
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
      + "  background: rgba(255,255,255,0.3);"
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
      + ".history-scrubber-version {"
      + "  opacity: 0.6;"
      + "  font-size: 11px;"
      + "}"

      // -- Snapshot header (version info bar at top of rendered snapshot) --
      + ".history-snapshot-header {"
      + "  padding: 12px 16px;"
      + "  margin-bottom: 16px;"
      + "  background: linear-gradient(135deg, #e8f4f8 0%, #d0e8f2 100%);"
      + "  border-radius: 6px;"
      + "  border-left: 4px solid #0077b6;"
      + "  font-size: 14px;"
      + "  color: #1b3a5c;"
      + "}"
      + ".history-snapshot-version {"
      + "  font-weight: 600;"
      + "  color: #0077b6;"
      + "}"
      + ".history-snapshot-sep {"
      + "  opacity: 0.5;"
      + "  margin: 0 4px;"
      + "}"
      + ".history-snapshot-author {"
      + "  font-weight: 500;"
      + "}"
      + ".history-snapshot-date {"
      + "  opacity: 0.85;"
      + "}"

      // -- Blip cards (rendered snapshot content) --
      + ".history-blip {"
      + "  margin-bottom: 12px;"
      + "  padding: 12px 16px;"
      + "  border-radius: 6px;"
      + "  border: 1px solid #e0e0e0;"
      + "  background: #fafafa;"
      + "}"
      + ".history-blip-header {"
      + "  margin-bottom: 8px;"
      + "  font-size: 13px;"
      + "  color: #444;"
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
      + "  color: #2d3748;"
      + "}"

      // -- Empty / error states --
      + ".history-empty {"
      + "  text-align: center;"
      + "  padding: 24px;"
      + "  color: #888;"
      + "  font-style: italic;"
      + "}"
      + ".history-error {"
      + "  text-align: center;"
      + "  padding: 24px;"
      + "  color: #c53030;"
      + "  background: #fff5f5;"
      + "  border-radius: 6px;"
      + "  border: 1px solid #fed7d7;"
      + "  margin: 16px;"
      + "}"

      // -- Wave panel adjustments when in history mode --
      + ".history-mode {"
      + "  position: relative;"
      + "  padding: 0;"
      + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;"
      + "  overflow: hidden;"
      + "  height: 100%;"
      + "}"

      // -- Scrollable container for snapshot content --
      + ".history-scroll-container {"
      + "  overflow-y: auto;"
      + "  height: 100%;"
      + "  padding: 16px 24px 80px 24px;"
      + "  box-sizing: border-box;"
      + "}"

      // -- Diff highlighting: additions (green background) --
      + ".history-diff-added {"
      + "  background-color: #d4edda;"
      + "  color: #155724;"
      + "  padding: 1px 3px;"
      + "  border-radius: 2px;"
      + "}"

      // -- Diff highlighting: removals (red strikethrough) --
      + ".history-diff-removed {"
      + "  background-color: #f8d7da;"
      + "  color: #721c24;"
      + "  text-decoration: line-through;"
      + "  padding: 1px 3px;"
      + "  border-radius: 2px;"
      + "}"

      // -- Blip card that was entirely removed --
      + ".history-blip-removed {"
      + "  opacity: 0.7;"
      + "  border-color: #f5c6cb;"
      + "  background: #fff5f5;"
      + "}"

      // -- Show-changes toggle in scrubber --
      + ".history-scrubber-toggle {"
      + "  display: flex;"
      + "  align-items: center;"
      + "  gap: 5px;"
      + "  cursor: pointer;"
      + "  white-space: nowrap;"
      + "  font-size: 12px;"
      + "  flex-shrink: 0;"
      + "  user-select: none;"
      + "  -webkit-user-select: none;"
      + "}"
      + ".history-scrubber-toggle input[type='checkbox'] {"
      + "  cursor: pointer;"
      + "  margin: 0;"
      + "}";
}
