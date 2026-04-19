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
 *       DOMCharacterDataModified event the browser fires on the document,
 *       with timestamp, target-node shape, and event data. This is a
 *       capture-phase window listener so we see events even if the editor
 *       swallows them.
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

  private ImeDebugTracer() {
    // Utility.
  }

  private static final String FLAG_ON = "on";
  private static final int MAX_OVERLAY_LINES = 200;
  private static final int MAX_FIELD_LEN = 120;

  private static boolean initialized = false;
  private static boolean enabled = false;
  private static double baselineMs = 0.0;

  /** Cheap fast-path gate. Safe to call from every hot site. */
  public static boolean isEnabled() {
    if (!initialized) {
      initialize();
    }
    return enabled;
  }

  private static void initialize() {
    initialized = true;
    try {
      syncFlagFromUrlJsni();
      enabled = FLAG_ON.equals(readFlagJsni());
      if (enabled) {
        baselineMs = nowMsJsni();
        installGlobalEventListenersJsni(baselineMs);
        ensureOverlayJsni();
      }
    } catch (Throwable t) {
      enabled = false;
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
        boolean truncated = value.length() > MAX_FIELD_LEN;
        String v = truncated ? value.substring(0, MAX_FIELD_LEN) : value;
        buf.append('"').append(escape(v));
        if (truncated) {
          buf.append('\u2026');
        }
        buf.append('"');
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
    function handler(e) {
      try {
        var t = ((w.performance && w.performance.now ? w.performance.now() : new Date().getTime()) - baseline).toFixed(1);
        var msg = '+' + t + 'ms GLOBAL ' + e.type;
        if (e.key !== undefined) msg += ' key="' + e.key + '"';
        if (e.keyCode !== undefined) msg += ' code=' + e.keyCode;
        if (e.data !== undefined) msg += ' data="' + (e.data === null ? 'null' : String(e.data)) + '"';
        if (e.inputType) msg += ' inputType="' + e.inputType + '"';
        if (e.isComposing !== undefined) msg += ' isComposing=' + e.isComposing;
        if (e.target) msg += ' target=' + describeNode(e.target);
        msg += ' sel=' + describeSelection();
        if ($wnd.console && $wnd.console.log) { $wnd.console.log('[IME-DBG] ' + msg); }
        @org.waveprotocol.wave.client.editor.debug.ImeDebugTracer::appendToOverlayJsniBridge(Ljava/lang/String;)(msg);
      } catch (err) {
        // swallow
      }
    }
    for (var i = 0; i < types.length; i++) {
      try {
        w.addEventListener(types[i], handler, true);
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

  private static native void ensureOverlayJsni() /*-{
    try {
      var d = $doc;
      var id = "ime-debug-overlay";
      if (d.getElementById(id)) return;
      var ov = d.createElement("div");
      ov.id = id;
      ov.tabIndex = 0;
      ov.setAttribute("role", "log");
      ov.setAttribute("aria-label",
          "IME debug overlay. Double-tap or press Escape while focused to clear.");
      ov.style.cssText =
          "position:fixed;left:0;right:0;bottom:0;max-height:45%;overflow-y:auto;"
          + "background:rgba(0,0,0,0.85);color:#b2ff59;font:10px/1.25 monospace;"
          + "padding:4px 6px;z-index:2147483646;white-space:pre-wrap;"
          + "word-break:break-all;pointer-events:auto;border-top:2px solid #555;";
      if (d.body) {
        d.body.appendChild(ov);
      } else {
        d.addEventListener('DOMContentLoaded', function () { d.body.appendChild(ov); }, true);
      }
      // Double-tap the overlay to clear it.
      var lastTap = 0;
      ov.addEventListener('click', function (ev) {
        var now = Date.now();
        if (now - lastTap < 400) {
          ov.innerHTML = "";
        }
        lastTap = now;
      }, false);
      // Keyboard-accessible clear: focus the overlay (Tab until the log
      // element is reached) and press Escape. Also Ctrl+Shift+C on the
      // overlay clears without relying on focus order.
      ov.addEventListener('keydown', function (ev) {
        if (ev.key === 'Escape'
            || (ev.key === 'c' && ev.ctrlKey && ev.shiftKey)
            || (ev.key === 'C' && ev.ctrlKey && ev.shiftKey)) {
          ov.innerHTML = "";
          ev.stopPropagation();
          ev.preventDefault();
        }
      }, false);
    } catch (e) {
      // swallow
    }
  }-*/;

  private static native void appendToOverlayJsni(String line) /*-{
    try {
      var d = $doc;
      var ov = d.getElementById("ime-debug-overlay");
      if (!ov) { return; }
      var row = d.createElement("div");
      row.textContent = line;
      ov.appendChild(row);
      while (ov.children.length > @org.waveprotocol.wave.client.editor.debug.ImeDebugTracer::MAX_OVERLAY_LINES) {
        ov.removeChild(ov.firstChild);
      }
      ov.scrollTop = ov.scrollHeight;
    } catch (e) {
      // swallow
    }
  }-*/;
}
