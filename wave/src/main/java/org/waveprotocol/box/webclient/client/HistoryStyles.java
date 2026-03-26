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
 * snapshot rendering, loading states, and history-mode wave panel adjustments).
 * Injection is idempotent -- calling {@link #inject()} more than once is safe.
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
      // -- Keyframe animations --
      "@keyframes history-spin {"
      + "  from { transform: rotate(0deg); }"
      + "  to { transform: rotate(360deg); }"
      + "}"
      + "@keyframes history-fadeIn {"
      + "  from { opacity: 0; transform: translateY(8px); }"
      + "  to { opacity: 1; transform: translateY(0); }"
      + "}"
      + "@keyframes history-slideUp {"
      + "  from { transform: translateY(100%); opacity: 0; }"
      + "  to { transform: translateY(0); opacity: 1; }"
      + "}"

      // -- History scrubber bar (fixed at bottom, body-level) --
      + ".history-scrubber {"
      + "  position: fixed;"
      + "  bottom: 0;"
      + "  left: 0;"
      + "  right: 0;"
      + "  z-index: 10000;"
      + "  display: flex;"
      + "  align-items: center;"
      + "  gap: 8px;"
      + "  padding: 8px 16px;"
      + "  background: linear-gradient(135deg, #0f172a 0%, #1e3a5f 50%, #1a6fa0 100%);"
      + "  color: #e0f0ff;"
      + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;"
      + "  font-size: 12px;"
      + "  box-shadow: 0 -3px 20px rgba(0,0,0,0.35);"
      + "  animation: history-slideUp 0.3s ease-out;"
      + "  border-top: 1px solid rgba(255,255,255,0.1);"
      + "}"

      // -- Exit button --
      + ".history-scrubber-exit {"
      + "  cursor: pointer;"
      + "  padding: 5px 12px;"
      + "  border-radius: 6px;"
      + "  background: rgba(255,255,255,0.12);"
      + "  color: #fff;"
      + "  font-weight: 600;"
      + "  font-size: 12px;"
      + "  white-space: nowrap;"
      + "  border: 1px solid rgba(255,255,255,0.2);"
      + "  transition: all 0.15s ease;"
      + "  flex-shrink: 0;"
      + "}"
      + ".history-scrubber-exit:hover {"
      + "  background: rgba(255,80,80,0.3);"
      + "  border-color: rgba(255,80,80,0.5);"
      + "}"

      // -- Navigation buttons (prev/next arrows) --
      + ".history-scrubber-nav {"
      + "  cursor: pointer;"
      + "  padding: 4px 6px;"
      + "  border-radius: 4px;"
      + "  background: rgba(255,255,255,0.08);"
      + "  color: #fff;"
      + "  border: 1px solid rgba(255,255,255,0.15);"
      + "  transition: all 0.15s ease;"
      + "  flex-shrink: 0;"
      + "  line-height: 0;"
      + "}"
      + ".history-scrubber-nav:hover {"
      + "  background: rgba(255,255,255,0.25);"
      + "}"

      // -- Restore button --
      + ".history-scrubber-restore {"
      + "  cursor: pointer;"
      + "  padding: 5px 14px;"
      + "  border-radius: 6px;"
      + "  background: linear-gradient(135deg, #16a34a, #15803d);"
      + "  color: #fff;"
      + "  font-weight: 600;"
      + "  font-size: 12px;"
      + "  white-space: nowrap;"
      + "  border: 1px solid rgba(255,255,255,0.2);"
      + "  transition: all 0.15s ease;"
      + "  flex-shrink: 0;"
      + "}"
      + ".history-scrubber-restore:hover {"
      + "  background: linear-gradient(135deg, #22c55e, #16a34a);"
      + "  box-shadow: 0 2px 8px rgba(22,163,74,0.4);"
      + "}"

      // -- Filter toggle --
      + ".history-scrubber-filter {"
      + "  cursor: pointer;"
      + "  padding: 4px 10px;"
      + "  border-radius: 4px;"
      + "  background: rgba(255,255,255,0.08);"
      + "  color: rgba(255,255,255,0.7);"
      + "  font-size: 11px;"
      + "  white-space: nowrap;"
      + "  border: 1px solid rgba(255,255,255,0.15);"
      + "  transition: all 0.15s ease;"
      + "  flex-shrink: 0;"
      + "}"
      + ".history-scrubber-filter:hover {"
      + "  background: rgba(255,255,255,0.18);"
      + "  color: #fff;"
      + "}"
      + ".history-scrubber-filter-active {"
      + "  background: rgba(59,130,246,0.3) !important;"
      + "  border-color: rgba(59,130,246,0.5) !important;"
      + "  color: #fff !important;"
      + "}"

      // -- Range input wrapper --
      + ".history-scrubber-range-wrapper {"
      + "  flex: 1;"
      + "  min-width: 80px;"
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
      + "  background: linear-gradient(90deg, rgba(255,255,255,0.15), rgba(255,255,255,0.35));"
      + "  margin: 0;"
      + "}"
      + ".history-scrubber-range::-webkit-slider-thumb {"
      + "  -webkit-appearance: none;"
      + "  width: 18px;"
      + "  height: 18px;"
      + "  border-radius: 50%;"
      + "  background: linear-gradient(135deg, #fff, #e2e8f0);"
      + "  border: 2px solid #3b82f6;"
      + "  box-shadow: 0 1px 6px rgba(0,0,0,0.3);"
      + "  cursor: pointer;"
      + "  transition: transform 0.1s ease;"
      + "}"
      + ".history-scrubber-range::-webkit-slider-thumb:hover {"
      + "  transform: scale(1.15);"
      + "}"
      + ".history-scrubber-range::-moz-range-thumb {"
      + "  width: 18px;"
      + "  height: 18px;"
      + "  border-radius: 50%;"
      + "  background: linear-gradient(135deg, #fff, #e2e8f0);"
      + "  border: 2px solid #3b82f6;"
      + "  box-shadow: 0 1px 6px rgba(0,0,0,0.3);"
      + "  cursor: pointer;"
      + "}"

      // -- Group counter --
      + ".history-scrubber-counter {"
      + "  white-space: nowrap;"
      + "  flex-shrink: 0;"
      + "  font-size: 11px;"
      + "  opacity: 0.8;"
      + "  font-variant-numeric: tabular-nums;"
      + "  min-width: 50px;"
      + "  text-align: center;"
      + "}"
      + ".history-scrubber-counter-num {"
      + "  font-weight: 700;"
      + "  color: #fff;"
      + "}"

      // -- Info label --
      + ".history-scrubber-label {"
      + "  white-space: nowrap;"
      + "  flex-shrink: 1;"
      + "  overflow: hidden;"
      + "  text-overflow: ellipsis;"
      + "  font-size: 12px;"
      + "  min-width: 0;"
      + "}"
      + ".history-scrubber-author {"
      + "  font-weight: 600;"
      + "  color: #fff;"
      + "}"
      + ".history-scrubber-sep {"
      + "  opacity: 0.4;"
      + "  margin: 0 4px;"
      + "}"
      + ".history-scrubber-date {"
      + "  opacity: 0.8;"
      + "}"
      + ".history-scrubber-version {"
      + "  opacity: 0.5;"
      + "  font-size: 11px;"
      + "}"

      // -- Loading state --
      + ".history-loading {"
      + "  display: flex;"
      + "  flex-direction: column;"
      + "  align-items: center;"
      + "  justify-content: center;"
      + "  padding: 60px 24px;"
      + "  animation: history-fadeIn 0.3s ease;"
      + "}"
      + ".history-loading-spinner {"
      + "  width: 32px;"
      + "  height: 32px;"
      + "  border: 3px solid #e2e8f0;"
      + "  border-top-color: #3b82f6;"
      + "  border-radius: 50%;"
      + "  animation: history-spin 0.8s linear infinite;"
      + "  margin-bottom: 16px;"
      + "}"
      + ".history-loading-text {"
      + "  font-size: 14px;"
      + "  color: #64748b;"
      + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;"
      + "}"

      // -- Snapshot header (version info bar at top of rendered snapshot) --
      + ".history-snapshot-header {"
      + "  padding: 14px 18px;"
      + "  margin-bottom: 16px;"
      + "  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);"
      + "  border-radius: 8px;"
      + "  border-left: 4px solid #3b82f6;"
      + "  font-size: 14px;"
      + "  color: #1e3a5c;"
      + "  animation: history-fadeIn 0.25s ease;"
      + "}"
      + ".history-snapshot-header-top {"
      + "  display: flex;"
      + "  align-items: center;"
      + "  flex-wrap: wrap;"
      + "  gap: 2px;"
      + "}"
      + ".history-snapshot-version {"
      + "  font-weight: 700;"
      + "  color: #2563eb;"
      + "}"
      + ".history-snapshot-sep {"
      + "  opacity: 0.35;"
      + "  margin: 0 6px;"
      + "  font-size: 10px;"
      + "}"
      + ".history-snapshot-author {"
      + "  font-weight: 500;"
      + "}"
      + ".history-snapshot-date {"
      + "  opacity: 0.75;"
      + "  font-size: 13px;"
      + "}"
      + ".history-snapshot-participants {"
      + "  margin-top: 6px;"
      + "  font-size: 12px;"
      + "  color: #64748b;"
      + "}"

      // -- Blip cards (rendered snapshot content) --
      + ".history-blip {"
      + "  margin-bottom: 12px;"
      + "  padding: 14px 18px;"
      + "  border-radius: 8px;"
      + "  border: 1px solid #e2e8f0;"
      + "  background: #fff;"
      + "  box-shadow: 0 1px 3px rgba(0,0,0,0.06);"
      + "  animation: history-fadeIn 0.3s ease;"
      + "  transition: border-color 0.15s ease;"
      + "}"
      + ".history-blip:hover {"
      + "  border-color: #cbd5e1;"
      + "}"
      + ".history-blip-header {"
      + "  display: flex;"
      + "  align-items: center;"
      + "  gap: 8px;"
      + "  margin-bottom: 10px;"
      + "  font-size: 13px;"
      + "  color: #475569;"
      + "}"
      + ".history-blip-avatar {"
      + "  display: inline-flex;"
      + "  align-items: center;"
      + "  justify-content: center;"
      + "  width: 26px;"
      + "  height: 26px;"
      + "  border-radius: 50%;"
      + "  background: linear-gradient(135deg, #3b82f6, #2563eb);"
      + "  color: #fff;"
      + "  font-size: 12px;"
      + "  font-weight: 600;"
      + "  flex-shrink: 0;"
      + "}"
      + ".history-blip-author {"
      + "  font-weight: 600;"
      + "  color: #1e293b;"
      + "}"
      + ".history-blip-time {"
      + "  font-size: 12px;"
      + "  opacity: 0.5;"
      + "}"
      + ".history-blip-content {"
      + "  font-size: 14px;"
      + "  line-height: 1.7;"
      + "  white-space: pre-wrap;"
      + "  word-wrap: break-word;"
      + "  color: #334155;"
      + "}"

      // -- Empty / error states --
      + ".history-empty {"
      + "  display: flex;"
      + "  flex-direction: column;"
      + "  align-items: center;"
      + "  text-align: center;"
      + "  padding: 48px 24px;"
      + "  color: #94a3b8;"
      + "  font-style: italic;"
      + "  font-size: 14px;"
      + "  animation: history-fadeIn 0.3s ease;"
      + "}"
      + ".history-empty-icon {"
      + "  margin-bottom: 8px;"
      + "}"
      + ".history-error {"
      + "  display: flex;"
      + "  flex-direction: column;"
      + "  align-items: center;"
      + "  text-align: center;"
      + "  padding: 32px 24px;"
      + "  color: #dc2626;"
      + "  background: #fef2f2;"
      + "  border-radius: 8px;"
      + "  border: 1px solid #fecaca;"
      + "  margin: 16px;"
      + "  font-size: 14px;"
      + "  animation: history-fadeIn 0.3s ease;"
      + "}"
      + ".history-error-icon {"
      + "  margin-bottom: 8px;"
      + "}"
      + ".history-error-detail {"
      + "  margin-top: 6px;"
      + "  font-size: 12px;"
      + "  color: #991b1b;"
      + "  opacity: 0.8;"
      + "}"

      // -- Wave panel adjustments when in history mode --
      + ".history-mode {"
      + "  position: relative;"
      + "  padding: 20px 24px 72px 24px !important;"
      + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;"
      + "  background: #f8fafc !important;"
      + "  min-height: 300px;"
      + "}";
}
