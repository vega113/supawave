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

package org.waveprotocol.wave.client.editor.debug;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Text;

/**
 * Flag-gated diagnostic tracer for the Android IME composition pipeline.
 *
 * <p>We have been chasing an Android-phone-only regression for five
 * consecutive PRs (#850, #877, #891, #896, #911). Each fix passed its unit
 * tests and Chrome DevTools emulation but the real Galaxy S25 Ultra kept
 * committing "new blip" as "ewlip". At this point guess-and-check is
 * wasting cycles — we need to see what the device actually does.
 *
 * <p>This class is deliberately simple. When enabled it emits structured
 * lines to the browser console <strong>and</strong> an on-screen overlay
 * at the bottom of the page, so the operator can read the trace directly
 * on the phone without hooking up remote Chrome DevTools. All operations
 * are no-ops when the tracer is disabled, so production users pay nothing.
 *
 * <h3>Activation</h3>
 * <ul>
 *   <li>On the phone: append {@code ?ime_debug=on} to the URL once. The
 *       tracer persists the flag in {@code localStorage} under the
 *       {@code ime_debug} key; subsequent page loads keep it on until
 *       {@code ?ime_debug=off} (or manually clearing {@code localStorage}).
 *   <li>From Chrome DevTools (remote or desktop): run
 *       {@code localStorage.setItem('ime_debug', 'on')} then reload.
 * </ul>
 *
 * <h3>What gets traced</h3>
 * <ul>
 *   <li>Every composition, keydown, beforeinput, input, textInput, and
 *       DOMCharacterDataModified event the browser fires, with timestamp,
 *       target-node shape, and event data. This is a capture-phase listener
 *       so we see events even if the editor swallows them.
 *   <li>{@code ImeExtractor} activate, baseline capture, ghost resolution,
 *       and deactivate — with the actual strings involved.
 *   <li>{@code EditorEventHandler} composition-start/-end, mutation-branch
 *       decisions, and state-machine transitions.
 *   <li>{@code EditorImpl.flushActiveImeComposition} — scratch content,
 *       reconciled composition, and what gets inserted into the model.
 * </ul>
 *
 * <p>The intent is: enable the flag, type "new blip" once, and the trace
 * should reveal which link in the pipeline is actually dropping characters.
 */
public final class ImeDebugTracer {
  private static final String FEATURE_FLAG_NAME = "ime-debug-tracer";
  private static final String FLAG_ON = "on";
  private static final String REMOTE_LOGGING_PATH = "webclient/remote_logging";
  private static final int MAX_OVERLAY_LINES = 200;
  private static final int MAX_FIELD_LEN = 120;
  private static final int ENABLE_REFRESH_RETRY_INTERVAL_MS = 1000;
  private static final int REMOTE_UPLOAD_MAX_BATCH_CHARS = 4096;
  private static final int REMOTE_UPLOAD_MAX_BATCH_LINES = 20;
  private static final int REMOTE_UPLOAD_DELAY_MS = 750;
  private static final int REMOTE_UPLOAD_TIMEOUT_MS = 10000;
  private static final int REMOTE_UPLOAD_MAX_QUEUE_LINES = 200;
  private static final int REMOTE_UPLOAD_MAX_QUEUE_CHARS = 65536;

  private ImeDebugTracer() {
    // Utility.
  }

  private static boolean initialized = false;
  private static boolean enabled = false;
  private static boolean remoteUploadEnabled = false;
  private static double baselineMs = 0.0;
  private static double nextRefreshAttemptMs = 0.0;

  /** Cheap fast-path gate. Safe to call from every hot site. */
  public static boolean isEnabled() {
    if (!initialized) {
      initialize();
    } else if ((!enabled || !remoteUploadEnabled) && shouldRetryRefresh()) {
      refreshEnabledState();
    }
    return enabled;
  }

  private static void initialize() {
    initialized = true;
    try {
      syncFlagFromUrlJsni();
      refreshEnabledState();
    } catch (Throwable t) {
      enabled = false;
    }
  }

  private static void refreshEnabledState() {
    if (enabled && remoteUploadEnabled) {
      return;
    }
    boolean sessionEnabled = isSessionFlagEnabled();
    if (!enabled) {
      if (!sessionEnabled && !FLAG_ON.equals(readFlagJsni())) {
        return;
      }
      enabled = true;
      nextRefreshAttemptMs = 0.0;
      baselineMs = nowMsJsni();
      installGlobalEventListenersJsni(baselineMs);
      ensureOverlayJsni();
    }
    if (sessionEnabled) {
      remoteUploadEnabled = true;
      nextRefreshAttemptMs = 0.0;
    }
  }

  private static boolean shouldRetryRefresh() {
    double now = System.currentTimeMillis();
    if (now < nextRefreshAttemptMs) {
      return false;
    }
    nextRefreshAttemptMs = now + ENABLE_REFRESH_RETRY_INTERVAL_MS;
    return true;
  }

  private static boolean isSessionFlagEnabled() {
    try {
      return hasSessionFeatureJsni(FEATURE_FLAG_NAME);
    } catch (Throwable t) {
      return false;
    }
  }

  /** Begin a new trace line. Caller chains {@code add(k,v)} then {@code emit()}. */
  public static Line start(String event) {
    return new Line(event);
  }

  /** Convenience: emit a bare event with no fields. */
  public static void trace(String event) {
    if (!isEnabled()) {
      return;
    }
    new Line(event).emit();
  }

  /** Describe a DOM node for inclusion in a trace line. */
  public static String describe(Node node) {
    if (node == null) {
      return "null";
    }
    short type = node.getNodeType();
    if (type == Node.TEXT_NODE) {
      return "Text:\"" + truncate(Text.as(node).getData()) + "\"";
    }
    if (type == Node.ELEMENT_NODE) {
      Element e = Element.as(node);
      return "<" + e.getTagName() + " id=\"" + nullToEmpty(e.getId())
          + "\" class=\"" + nullToEmpty(e.getClassName()) + "\">";
    }
    return "node[type=" + type + "]";
  }

  /** Read the character data of {@code node} if it is a text node. */
  public static String readText(Node node) {
    if (node == null) {
      return null;
    }
    if (node.getNodeType() != Node.TEXT_NODE) {
      return null;
    }
    return Text.as(node).getData();
  }

  /** Snapshot of an element's innerText, bounded for log readability. */
  public static String innerText(Element element) {
    if (element == null) {
      return null;
    }
    return truncate(element.getInnerText());
  }

  private static String truncate(String s) {
    if (s == null) {
      return "null";
    }
    if (s.length() <= 200) {
      return escape(s);
    }
    return escape(s.substring(0, 200)) + "…(+" + (s.length() - 200) + ")";
  }

  static String formatFieldValue(String value) {
    if (value == null) {
      return "null";
    }
    boolean truncated = value.length() > MAX_FIELD_LEN;
    String formatted = escape(truncated ? value.substring(0, MAX_FIELD_LEN) : value);
    return truncated ? formatted + '\u2026' : formatted;
  }

  private static String escape(String s) {
    if (s == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(s.length() + 8);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"') {
        out.append("\\\"");
      } else if (c == '\\') {
        out.append("\\\\");
      } else if (c == '\n') {
        out.append("\\n");
      } else if (c == '\r') {
        out.append("\\r");
      } else if (c == '\t') {
        out.append("\\t");
      } else if (c < 0x20) {
        out.append("\\u");
        appendHex(out, (int) c, 4);
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static void appendHex(StringBuilder out, int value, int width) {
    String hex = Integer.toHexString(value);
    for (int i = hex.length(); i < width; i++) {
      out.append('0');
    }
    out.append(hex);
  }

  /** Builder for a single trace line. */
  public static final class Line {

    private final StringBuilder buf;
    private final boolean active;

    Line(String event) {
      this.active = isEnabled();
      if (!active) {
        this.buf = null;
        return;
      }
      double t = nowMsJsni() - baselineMs;
      this.buf = new StringBuilder(128);
      this.buf.append('+');
      appendFixed1(this.buf, t);
      this.buf.append("ms ").append(event);
    }

    public Line add(String key, String value) {
      if (!active) {
        return this;
      }
      buf.append(' ').append(key).append('=');
      if (value == null) {
        buf.append("null");
      } else {
        buf.append('"').append(formatFieldValue(value)).append('"');
      }
      return this;
    }

    public Line add(String key, int value) {
      if (!active) {
        return this;
      }
      buf.append(' ').append(key).append('=').append(value);
      return this;
    }

    public Line add(String key, boolean value) {
      if (!active) {
        return this;
      }
      buf.append(' ').append(key).append('=').append(value);
      return this;
    }

    public void emit() {
      if (!active) {
        return;
      }
      String line = buf.toString();
      consoleLogJsni(line);
      appendToOverlayJsni(line);
      if (remoteUploadEnabled) {
        queueRemoteLogJsni(
            line,
            REMOTE_UPLOAD_MAX_BATCH_CHARS,
            REMOTE_UPLOAD_MAX_BATCH_LINES,
            REMOTE_UPLOAD_DELAY_MS,
            REMOTE_UPLOAD_MAX_QUEUE_LINES,
            REMOTE_UPLOAD_MAX_QUEUE_CHARS);
      }
    }
  }

  private static void appendFixed1(StringBuilder b, double value) {
    long whole = (long) value;
    long frac = Math.abs((long) ((value - whole) * 10));
    if (value < 0 && whole == 0) {
      b.append('-');
    }
    b.append(whole).append('.').append(frac);
  }

  // --- JSNI ------------------------------------------------------------

  private static native double nowMsJsni() /*-{
    if ($wnd.performance && $wnd.performance.now) {
      return $wnd.performance.now();
    }
    return new Date().getTime();
  }-*/;

  private static native void consoleLogJsni(String msg) /*-{
    if ($wnd.console && $wnd.console.log) {
      $wnd.console.log("[IME-DBG] " + msg);
    }
  }-*/;

  private static native String readFlagJsni() /*-{
    try {
      return $wnd.localStorage.getItem("ime_debug") || "";
    } catch (e) {
      return "";
    }
  }-*/;

  private static native boolean hasSessionFeatureJsni(String name) /*-{
    try {
      var session = $wnd.__session;
      var features = session && session.features;
      if (!features || !features.length) return false;
      for (var i = 0; i < features.length; i++) {
        if (features[i] === name) return true;
      }
      return false;
    } catch (e) {
      return false;
    }
  }-*/;

  private static native void syncFlagFromUrlJsni() /*-{
    try {
      var search = $wnd.location && $wnd.location.search ? $wnd.location.search : "";
      var match = /[?&]ime_debug=([^&#]+)/.exec(search);
      if (!match) { return; }
      var value = decodeURIComponent(match[1]);
      if (value === "on") {
        $wnd.localStorage.setItem("ime_debug", "on");
      } else if (value === "off") {
        $wnd.localStorage.removeItem("ime_debug");
      }
    } catch (e) {
      // swallow
    }
  }-*/;

  private static native void installGlobalEventListenersJsni(double baselineMs) /*-{
    var w = $wnd;
    var target = ($doc && $doc.addEventListener) ? $doc : w;
    // Share the Java-side baseline so global-event traces and app-level
    // traces always reference the same time origin. A separately-captured
    // baseline here would drift by the cost of the intervening JSNI calls
    // and make cross-layer correlation harder.
    var baseline = baselineMs;
    var types = [
      'keydown', 'keyup', 'keypress',
      'beforeinput', 'input',
      'compositionstart', 'compositionupdate', 'compositionend',
      'textInput', 'textinput',
      'DOMCharacterDataModified', 'DOMNodeInserted', 'DOMNodeRemoved',
      'selectionchange', 'focus', 'blur'
    ];
    function describeNode(n) {
      if (!n) return 'null';
      if (n.nodeType === 3) {
        var d = n.data || '';
        if (d.length > 120) d = d.substring(0, 120) + '…';
        return 'Text:"' + d.replace(/"/g, '\\"').replace(/\n/g, '\\n') + '"';
      }
      if (n.nodeType === 1) {
        return '<' + n.tagName + ' class="' + (n.className || '') + '">';
      }
      return 'node[type=' + n.nodeType + ']';
    }
    function describeSelection() {
      try {
        var s = w.getSelection && w.getSelection();
        if (!s || s.rangeCount === 0) return 'none';
        return 'col=' + s.isCollapsed
            + ' anchor=' + describeNode(s.anchorNode)
            + '@' + s.anchorOffset
            + ' focus=' + describeNode(s.focusNode)
            + '@' + s.focusOffset;
      } catch (e) {
        return 'err:' + e;
      }
    }
    function formatField(value) {
      return @org.waveprotocol.wave.client.editor.debug.ImeDebugTracer::formatFieldValueForJsniBridge(Ljava/lang/String;)(
          value === null ? null : String(value));
    }
    function handler(e) {
      try {
        var t = ((w.performance && w.performance.now ? w.performance.now() : new Date().getTime()) - baseline).toFixed(1);
        var msg = '+' + t + 'ms GLOBAL ' + e.type;
        if (e.key !== undefined) msg += ' key="' + formatField(e.key) + '"';
        if (e.keyCode !== undefined) msg += ' code=' + e.keyCode;
        if (e.data !== undefined) msg += ' data="' + formatField(e.data) + '"';
        if (e.inputType) msg += ' inputType="' + formatField(e.inputType) + '"';
        if (e.isComposing !== undefined) msg += ' isComposing=' + e.isComposing;
        if (e.target) msg += ' target=' + describeNode(e.target);
        msg += ' sel=' + describeSelection();
        if ($wnd.console && $wnd.console.log) { $wnd.console.log('[IME-DBG] ' + msg); }
        @org.waveprotocol.wave.client.editor.debug.ImeDebugTracer::appendToOverlayJsniBridge(Ljava/lang/String;)(msg);
        @org.waveprotocol.wave.client.editor.debug.ImeDebugTracer::queueRemoteLogJsniBridge(Ljava/lang/String;)(msg);
      } catch (err) {
        // swallow
      }
    }
    for (var i = 0; i < types.length; i++) {
      try {
        target.addEventListener(types[i], handler, true);
      } catch (e) {
        // swallow
      }
    }
  }-*/;

  /** Callable from JSNI so the global event listeners can append to the overlay too. */
  @SuppressWarnings("unused")
  private static void appendToOverlayJsniBridge(String msg) {
    appendToOverlayJsni(msg);
  }

  @SuppressWarnings("unused")
  private static String formatFieldValueForJsniBridge(String value) {
    return formatFieldValue(value);
  }

  /** Callable from JSNI so the global event listeners can queue remote logs too. */
  @SuppressWarnings("unused")
  private static void queueRemoteLogJsniBridge(String msg) {
    if (!remoteUploadEnabled) {
      return;
    }
    queueRemoteLogJsni(
        msg,
        REMOTE_UPLOAD_MAX_BATCH_CHARS,
        REMOTE_UPLOAD_MAX_BATCH_LINES,
        REMOTE_UPLOAD_DELAY_MS,
        REMOTE_UPLOAD_MAX_QUEUE_LINES,
        REMOTE_UPLOAD_MAX_QUEUE_CHARS);
  }

  private static native void ensureOverlayJsni() /*-{
    try {
      var d = $doc;
      var w = $wnd;
      var id = "ime-debug-overlay";
      if (d.getElementById(id)) return;
      var ov = d.createElement("div");
      ov.id = id;
      ov.className = "ime-debug-overlay ime-debug-overlay-minimized";
      ov.tabIndex = 0;
      ov.setAttribute("role", "region");
      ov.setAttribute("aria-label", "IME debug overlay");
      ov.setAttribute("aria-expanded", "false");
      var collapsedHeight = "44px";
      var safeAreaInset = "env(safe-area-inset-bottom, 0px)";
      var reservedHeight = "calc(" + collapsedHeight + " + 1px + " + safeAreaInset + ")";
      ov.style.cssText =
          "position:fixed;left:0;right:0;bottom:0;z-index:2147483646;"
          + "height:" + reservedHeight + ";max-height:" + reservedHeight + ";overflow:hidden;"
          + "background:rgba(8,18,27,0.94);color:#c8ff6a;font:11px/1.25 monospace;"
          + "border-top:1px solid rgba(178,255,89,0.45);"
          + "box-shadow:0 -6px 18px rgba(0,0,0,0.25);pointer-events:auto;"
          + "box-sizing:border-box;padding-bottom:" + safeAreaInset + ";";

      var toolbar = d.createElement("div");
      toolbar.style.height = collapsedHeight;
      toolbar.style.minHeight = collapsedHeight;
      toolbar.style.display = "flex";
      toolbar.style.alignItems = "center";
      toolbar.style.gap = "6px";
      toolbar.style.padding = "0 6px";
      toolbar.style.boxSizing = "border-box";
      toolbar.style.overflowX = "auto";
      toolbar.style.overflowY = "hidden";
      toolbar.style.webkitOverflowScrolling = "touch";

      var title = d.createElement("span");
      title.textContent = "IME";
      title.style.flex = "0 0 auto";
      title.style.fontWeight = "700";
      title.style.minWidth = "0";

      function styleButton(button) {
        button.style.minHeight = collapsedHeight;
        button.style.minWidth = collapsedHeight;
        button.style.border = "1px solid rgba(178,255,89,0.35)";
        button.style.borderRadius = "6px";
        button.style.background = "rgba(255,255,255,0.08)";
        button.style.color = "#e1ff9c";
        button.style.font = "12px/1 sans-serif";
        button.style.padding = "0 10px";
        button.style.cursor = "pointer";
        button.style.flex = "0 0 auto";
        button.style.whiteSpace = "nowrap";
      }

      var toggle = d.createElement("button");
      toggle.type = "button";
      toggle.textContent = "Show";
      toggle.setAttribute("title", "Show IME log");
      styleButton(toggle);

      var copy = d.createElement("button");
      copy.type = "button";
      copy.textContent = "Copy";
      copy.setAttribute("aria-label", "Copy IME debug log");
      copy.setAttribute("title", "Copy IME debug log");
      styleButton(copy);

      var download = d.createElement("button");
      download.type = "button";
      download.textContent = "Download";
      download.setAttribute("aria-label", "Download IME debug log");
      download.setAttribute("title", "Download IME debug log");
      styleButton(download);

      var share = d.createElement("button");
      share.type = "button";
      share.textContent = "Share";
      share.setAttribute("aria-label", "Share IME debug log");
      share.setAttribute("title", "Share IME debug log");
      styleButton(share);

      var clear = d.createElement("button");
      clear.type = "button";
      clear.textContent = "Clear";
      clear.setAttribute("aria-label", "Clear IME debug log");
      clear.setAttribute("title", "Clear IME debug log");
      styleButton(clear);

      var status = d.createElement("span");
      status.setAttribute("aria-live", "polite");
      status.style.flex = "1 1 56px";
      status.style.minWidth = "0";
      status.style.maxWidth = "34vw";
      status.style.overflow = "hidden";
      status.style.textOverflow = "ellipsis";
      status.style.whiteSpace = "nowrap";
      status.style.color = "#e1ff9c";
      status.style.font = "11px/1 sans-serif";

      var log = d.createElement("div");
      log.id = "ime-debug-log";
      log.setAttribute("role", "log");
      log.setAttribute("aria-live", "polite");
      log.style.display = "none";
      log.style.maxHeight = "calc(45vh - " + collapsedHeight + " - 1px - " + safeAreaInset + ")";
      log.style.overflowY = "auto";
      log.style.padding = "0 6px 6px";
      log.style.boxSizing = "border-box";
      log.style.whiteSpace = "pre-wrap";
      log.style.wordBreak = "break-all";

      var statusTimer = null;
      function setStatus(message) {
        status.textContent = message || "";
        if (statusTimer) {
          w.clearTimeout(statusTimer);
          statusTimer = null;
        }
        if (message) {
          statusTimer = w.setTimeout(function () {
            status.textContent = "";
            statusTimer = null;
          }, 1600);
        }
      }

      function setExpanded(expanded) {
        ov.className = expanded
            ? "ime-debug-overlay ime-debug-overlay-expanded"
            : "ime-debug-overlay ime-debug-overlay-minimized";
        ov.setAttribute("aria-expanded", expanded ? "true" : "false");
        toggle.textContent = expanded ? "Hide" : "Show";
        toggle.setAttribute("aria-label", expanded ? "Hide IME debug log" : "Show IME debug log");
        toggle.setAttribute("title", expanded ? "Hide IME log" : "Show IME log");
        log.style.display = expanded ? "block" : "none";
        if (expanded) {
          ov.style.height = "auto";
          ov.style.maxHeight = "45vh";
          log.scrollTop = log.scrollHeight;
        } else {
          ov.style.height = reservedHeight;
          ov.style.maxHeight = reservedHeight;
        }
      }

      function copyLogText(log) {
        var rows = [];
        for (var i = 0; i < log.children.length; i++) {
          rows.push(log.children[i].textContent || "");
        }
        return rows.join("\n");
      }

      function logFilename() {
        var stamp;
        try {
          stamp = new Date().toISOString().replace(/[:.]/g, "-");
        } catch (e) {
          stamp = String(new Date().getTime());
        }
        return "ime-debug-log-" + stamp + ".txt";
      }

      function createLogBlob(text) {
        if (!w.Blob) {
          return null;
        }
        try {
          return new w.Blob([text], {type: "text/plain;charset=utf-8"});
        } catch (e) {
          return null;
        }
      }

      function triggerDownload(text, filename) {
        var anchor = d.createElement("a");
        var blob = createLogBlob(text);
        var url = null;
        if (blob && w.URL && w.URL.createObjectURL) {
          url = w.URL.createObjectURL(blob);
          anchor.href = url;
        } else {
          anchor.href = "data:text/plain;charset=utf-8," + encodeURIComponent(text);
        }
        anchor.download = filename;
        anchor.setAttribute("aria-hidden", "true");
        anchor.style.display = "none";
        d.body.appendChild(anchor);
        try {
          anchor.click();
          return true;
        } catch (e) {
          return false;
        } finally {
          d.body.removeChild(anchor);
          if (url && w.URL && w.URL.revokeObjectURL) {
            w.setTimeout(function () {
              w.URL.revokeObjectURL(url);
            }, 1000);
          }
        }
      }

      function createShareFile(text, filename) {
        if (!w.File) {
          return null;
        }
        try {
          return new w.File([text], filename, {type: "text/plain;charset=utf-8"});
        } catch (e) {
          return null;
        }
      }

      function shareLog(text, filename) {
        var navigator = w.navigator;
        if (!navigator || !navigator.share) {
          return false;
        }
        var file = createShareFile(text, filename);
        var payload = {
          title: "SupaWave IME debug log",
          text: text
        };
        try {
          if (file && navigator.canShare && navigator.canShare({files: [file]})) {
            payload = {
              title: "SupaWave IME debug log",
              text: "SupaWave IME debug log attached.",
              files: [file]
            };
          }
        } catch (e) {
          payload = {
            title: "SupaWave IME debug log",
            text: text
          };
        }
        try {
          navigator.share(payload).then(function () {
            setStatus("IME log shared");
          }, function (err) {
            if (err && err.name === "AbortError") {
              setStatus("Share canceled");
              return;
            }
            if (triggerDownload(text, filename)) {
              setStatus("Share failed; IME log downloaded");
            } else {
              fallbackCopy(text, function (copied) {
                setStatus(copied ? "Share failed; IME log copied" : "Share failed");
              });
            }
          });
        } catch (e) {
          if (triggerDownload(text, filename)) {
            setStatus("Share failed; IME log downloaded");
          } else {
            fallbackCopy(text, function (copied) {
              setStatus(copied ? "Share failed; IME log copied" : "Share failed");
            });
          }
        }
        return true;
      }

      function reserveBottomSpace(element, amount) {
        if (!element || element.getAttribute("data-ime-debug-bottom-space-reserved") === "true") {
          return;
        }
        var previousPadding = element.style.paddingBottom || "";
        var previousScrollPadding = element.style.scrollPaddingBottom || "";
        element.setAttribute("data-ime-debug-bottom-space-reserved", "true");
        element.setAttribute("data-ime-debug-padding-bottom-previous", previousPadding);
        element.setAttribute("data-ime-debug-scroll-padding-bottom-previous", previousScrollPadding);
        element.style.paddingBottom = previousPadding
            ? "calc(" + previousPadding + " + " + amount + ")"
            : amount;
        element.style.scrollPaddingBottom = previousScrollPadding
            ? "calc(" + previousScrollPadding + " + " + amount + ")"
            : amount;
      }

      function reserveBottomEditingSpace(d) {
        if (d.body) {
          reserveBottomSpace(d.body, reservedHeight);
        }
        if (!d.querySelectorAll) {
          return;
        }
        var scrollers = d.querySelectorAll("[data-mobile-role='wave-thread']");
        for (var i = 0; i < scrollers.length; i++) {
          reserveBottomSpace(scrollers[i], reservedHeight);
        }
      }

      function fallbackCopy(text, done) {
        var textarea = d.createElement("textarea");
        textarea.value = text;
        textarea.setAttribute("readonly", "readonly");
        textarea.style.position = "fixed";
        textarea.style.top = "-1000px";
        textarea.style.left = "-1000px";
        d.body.appendChild(textarea);
        textarea.select();
        var copied = false;
        try {
          copied = d.execCommand('copy');
        } catch (e) {
          copied = false;
        }
        d.body.removeChild(textarea);
        done(copied);
      }

      toggle.addEventListener('click', function (ev) {
        setExpanded(log.style.display === "none");
        ev.stopPropagation();
      }, false);
      copy.addEventListener('click', function (ev) {
        var text = copyLogText(log);
        if (!text) {
          setStatus("No IME log lines yet");
          ev.stopPropagation();
          return;
        }
        if (w.navigator && w.navigator.clipboard && w.navigator.clipboard.writeText) {
          w.navigator.clipboard.writeText(text).then(function () {
            setStatus("IME log copied");
          }, function () {
            fallbackCopy(text, function (copied) {
              setStatus(copied ? "IME log copied" : "Copy failed");
            });
          });
        } else {
          fallbackCopy(text, function (copied) {
            setStatus(copied ? "IME log copied" : "Copy failed");
          });
        }
        ev.stopPropagation();
      }, false);
      download.addEventListener('click', function (ev) {
        var text = copyLogText(log);
        if (!text) {
          setStatus("No IME log lines yet");
          ev.stopPropagation();
          return;
        }
        setStatus(triggerDownload(text, logFilename()) ? "IME log downloaded" : "Download failed");
        ev.stopPropagation();
      }, false);
      share.addEventListener('click', function (ev) {
        var text = copyLogText(log);
        var filename = logFilename();
        if (!text) {
          setStatus("No IME log lines yet");
          ev.stopPropagation();
          return;
        }
        if (!shareLog(text, filename)) {
          if (triggerDownload(text, filename)) {
            setStatus("IME log downloaded");
          } else {
            fallbackCopy(text, function (copied) {
              setStatus(copied ? "IME log copied" : "Share unavailable; download and copy failed");
            });
          }
        }
        ev.stopPropagation();
      }, false);
      clear.addEventListener('click', function (ev) {
        log.textContent = "";
        setStatus("IME log cleared");
        ev.stopPropagation();
      }, false);
      ov.addEventListener('keydown', function (ev) {
        if (ev.key === 'Escape') {
          setExpanded(false);
          ev.stopPropagation();
          ev.preventDefault();
        } else if ((ev.key === 'c' && ev.ctrlKey && ev.shiftKey)
            || (ev.key === 'C' && ev.ctrlKey && ev.shiftKey)) {
          log.textContent = "";
          setStatus("IME log cleared");
          ev.stopPropagation();
          ev.preventDefault();
        }
      }, false);

      toolbar.appendChild(title);
      toolbar.appendChild(toggle);
      toolbar.appendChild(copy);
      toolbar.appendChild(download);
      toolbar.appendChild(share);
      toolbar.appendChild(clear);
      toolbar.appendChild(status);
      ov.appendChild(toolbar);
      ov.appendChild(log);
      setExpanded(false);

      if (d.body) {
        reserveBottomEditingSpace(d);
        d.body.appendChild(ov);
      } else {
        d.addEventListener('DOMContentLoaded', function () {
          reserveBottomEditingSpace(d);
          d.body.appendChild(ov);
        }, true);
      }
    } catch (e) {
      // Diagnostic overlay must never break editor initialization.
    }
  }-*/;
  private static native void appendToOverlayJsni(String line) /*-{
    try {
      var d = $doc;
      function getLogBody(d) {
        return d.getElementById("ime-debug-log");
      }
      var log = getLogBody(d);
      if (!log) { return; }
      var row = d.createElement("div");
      row.textContent = line;
      log.appendChild(row);
      while (log.children.length > @org.waveprotocol.wave.client.editor.debug.ImeDebugTracer::MAX_OVERLAY_LINES) {
        log.removeChild(log.firstChild);
      }
      log.scrollTop = log.scrollHeight;
    } catch (e) {
      // swallow
    }
  }-*/;

  private static native void queueRemoteLogJsni(
      String line, int maxBatchChars, int maxBatchLines, int flushDelayMs,
      int maxQueueLines, int maxQueueChars) /*-{
    try {
      var w = $wnd;
      var state = w.__imeDebugRemoteLogState;
      if (!state) {
        state = {
          queue: [],
          queuedChars: 0,
          timer: null,
          inFlight: false
        };
        w.__imeDebugRemoteLogState = state;
      }

      function flush() {
        if (state.timer) {
          w.clearTimeout(state.timer);
          state.timer = null;
        }
        if (state.inFlight || !state.queue.length) {
          return;
        }

        var batch = [];
        var batchChars = 0;
        var batchCount = 0;
        while (batchCount < state.queue.length && batch.length < maxBatchLines) {
          var nextLine = state.queue[batchCount];
          var nextChars = (nextLine ? nextLine.length : 0) + (batch.length > 0 ? 1 : 0);
          if (batch.length > 0 && batchChars + nextChars > maxBatchChars) {
            break;
          }
          batch.push(nextLine);
          batchChars += nextChars;
          batchCount++;
        }
        if (!batch.length) {
          batch.push(state.queue[0]);
          batchCount = 1;
        }
        state.queue = state.queue.slice(batchCount);
        state.queuedChars = 0;
        for (var i = 0; i < state.queue.length; i++) {
          state.queuedChars += (state.queue[i] ? state.queue[i].length : 0) + 1;
        }

        state.inFlight = true;
        var payload = batch.join('\n');

        var xhr = new XMLHttpRequest();
        xhr.open('POST', @org.waveprotocol.wave.client.editor.debug.ImeDebugTracer::REMOTE_LOGGING_PATH, true);
        xhr.withCredentials = true;
        xhr.timeout = @org.waveprotocol.wave.client.editor.debug.ImeDebugTracer::REMOTE_UPLOAD_TIMEOUT_MS;
        xhr.setRequestHeader('Content-Type', 'text/plain; charset=utf-8');
        var finished = false;
        function finish() {
          if (finished) {
            return;
          }
          finished = true;
          state.inFlight = false;
          if (state.queue.length) {
            flush();
          }
        }
        xhr.onreadystatechange = function() {
          if (xhr.readyState !== 4) {
            return;
          }
          finish();
        };
        xhr.onerror = finish;
        xhr.onabort = finish;
        xhr.ontimeout = finish;
        try {
          xhr.send(payload);
        } catch (e) {
          finish();
        }
      }

      state.queue.push(line);
      state.queuedChars += (line ? line.length : 0) + 1;
      while (state.queue.length > maxQueueLines || state.queuedChars > maxQueueChars) {
        var dropped = state.queue.shift();
        state.queuedChars -= (dropped ? dropped.length : 0) + 1;
      }
      if (state.queuedChars < 0) { state.queuedChars = 0; }
      if (state.queuedChars >= maxBatchChars || state.queue.length >= maxBatchLines) {
        flush();
        return;
      }
      if (!state.timer) {
        state.timer = w.setTimeout(flush, flushDelayMs);
      }
    } catch (e) {
      // swallow
    }
  }-*/;
}
