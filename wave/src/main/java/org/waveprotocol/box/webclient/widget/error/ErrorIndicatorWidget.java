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

package org.waveprotocol.box.webclient.widget.error;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;

import org.waveprotocol.box.webclient.widget.error.i18n.ErrorMessages;
import org.waveprotocol.wave.client.common.safehtml.SafeHtml;

/**
 * GWT implementation of the UI for an error indicator.
 * Uses a floating ocean/dark-themed toast banner (SupaWave style).
 *
 * <p>CSS keyframe animations are injected dynamically because GWT's
 * CSS parser does not support {@code @keyframes}.
 */
public final class ErrorIndicatorWidget extends Composite implements ErrorIndicatorView {

  interface Binder extends UiBinder<HTMLPanel, ErrorIndicatorWidget> {
  }

  interface Style extends CssResource {
    String expanded();
    String overlay();
    String header();
    String message();
    String actions();
    String btn();
    String btnPrimary();
    String detail();
    String stack();
    String reportModal();
    String reportTextArea();
    String reportActions();
  }

  private static final Binder BINDER = GWT.create(Binder.class);
  private static final ErrorMessages MESSAGES = GWT.create(ErrorMessages.class);

  /** Whether the global keyframe CSS has been injected. */
  private static boolean cssInjected = false;

  @UiField Style style;
  @UiField Anchor showDetail;
  @UiField Element detail;
  @UiField Element stack;
  @UiField Element bug;
  @UiField Element overlay;
  @UiField Anchor copyBtn;
  @UiField Anchor dismissBtn;
  @UiField Anchor reportBugBtn;
  @UiField Element reportModal;
  @UiField Element reportTextArea;
  @UiField Anchor cancelReportBtn;
  @UiField Anchor sendReportBtn;
  @UiField Element waveDecor;
  @UiField Element glowDot;
  @UiField Element titleText;
  @UiField Element helpText;
  @UiField Element refreshBtn;
  @UiField Element stackHeader;

  private Listener listener;

  private ErrorIndicatorWidget() {
    injectKeyframeCss();
    initWidget(BINDER.createAndBindUi(this));
    applyTheming();
  }

  public static ErrorIndicatorWidget create() {
    return new ErrorIndicatorWidget();
  }

  // ---- Keyframe injection (once per page load) ----

  private static void injectKeyframeCss() {
    if (cssInjected) return;
    cssInjected = true;
    String css =
        "@keyframes errorFadeIn {"
      + "  from { opacity: 0; transform: translateX(-50%) translateY(-24px); }"
      + "  to   { opacity: 1; transform: translateX(-50%) translateY(0); }"
      + "}"
      + "@keyframes errorWaveMotion {"
      + "  0%   { transform: translateX(0); }"
      + "  100% { transform: translateX(-50%); }"
      + "}"
      + "@keyframes glowPulse {"
      + "  0%, 100% { opacity: 0.5; }"
      + "  50%      { opacity: 1; }"
      + "}";
    Element styleEl = Document.get().createStyleElement();
    styleEl.setInnerHTML(css);
    Document.get().getHead().appendChild(styleEl);
  }

  // ---- Apply ocean/dark theme styles programmatically ----

  private void applyTheming() {
    // Overlay: floating toast with ocean gradient
    com.google.gwt.dom.client.Style s = overlay.getStyle();
    s.setProperty("background",
        "linear-gradient(135deg, #0d1b2a 0%, #1b3a5c 40%, #1a6fa0 100%)");
    s.setProperty("color", "#e0f0ff");
    s.setProperty("borderRadius", "14px");
    s.setProperty("boxShadow",
        "0 8px 32px rgba(0,0,0,0.4), 0 0 0 1px rgba(255,255,255,0.06)");
    s.setProperty("animation", "errorFadeIn 0.4s ease-out");
    s.setProperty("transform", "translateX(-50%)");
    s.setProperty("maxWidth", "calc(100vw - 32px)");

    // Glowing dot
    com.google.gwt.dom.client.Style dotStyle = glowDot.getStyle();
    dotStyle.setProperty("background", "#4fc3f7");
    dotStyle.setProperty("boxShadow", "0 0 8px rgba(79,195,247,0.5)");
    dotStyle.setProperty("animation", "glowPulse 2s ease-in-out infinite");

    // Title
    titleText.setInnerText(MESSAGES.somethingWentWrong());
    titleText.getStyle().setProperty("letterSpacing", "0.2px");
    titleText.getStyle().setProperty("color", "#e0f0ff");

    // Help text
    helpText.setInnerText(MESSAGES.errorHelpText());

    // Stack trace header
    stackHeader.setInnerText(MESSAGES.stackTrace());
    com.google.gwt.dom.client.Style shStyle = stackHeader.getStyle();
    shStyle.setProperty("color", "#7ab8db");
    shStyle.setProperty("textTransform", "uppercase");
    shStyle.setProperty("letterSpacing", "0.5px");

    // Detail panel
    com.google.gwt.dom.client.Style detailStyle = detail.getStyle();
    detailStyle.setProperty("background", "rgba(0,0,0,0.2)");
    detailStyle.setProperty("borderTop", "1px solid rgba(255,255,255,0.06)");
    detailStyle.setProperty("borderRadius", "0 0 14px 14px");
    detailStyle.setProperty("transition",
        "max-height 300ms ease-in-out, padding 300ms ease-in-out");

    // Stack trace text
    stack.getStyle().setProperty("color", "#a0cfee");

    // Refresh button (primary)
    applyButtonStyle(refreshBtn, true);
    applyRefreshAction(refreshBtn);

    // Other buttons
    applyButtonStyle(showDetail.getElement(), false);
    applyButtonStyle(copyBtn.getElement(), false);
    applyButtonStyle(reportBugBtn.getElement(), false);
    applyButtonStyle(dismissBtn.getElement(), false);

    // Report modal styling
    reportModal.getStyle().setProperty("background", "rgba(0,0,0,0.2)");
    reportTextArea.getStyle().setProperty("background", "rgba(0,0,0,0.3)");
    reportTextArea.getStyle().setProperty("color", "#e0f0ff");
    reportTextArea.setAttribute("placeholder", MESSAGES.reportBugPlaceholder());

    // Report modal action buttons
    applyButtonStyle(cancelReportBtn.getElement(), false);
    applyButtonStyle(sendReportBtn.getElement(), true);

    // Wave decoration at bottom of banner
    waveDecor.getStyle().setProperty("borderRadius", "0 0 14px 14px");
    waveDecor.setInnerHTML(
        "<div style='"
      + "position: absolute; bottom: -4px; left: 0; width: 200%; height: 28px;"
      + "background: repeating-linear-gradient(90deg,"
      + "  transparent 0px, transparent 30px,"
      + "  rgba(255,255,255,0.04) 30px, rgba(255,255,255,0.04) 60px,"
      + "  transparent 60px, transparent 100px,"
      + "  rgba(255,255,255,0.025) 100px, rgba(255,255,255,0.025) 140px,"
      + "  transparent 140px, transparent 200px);"
      + "border-radius: 40% 40% 0 0;"
      + "animation: errorWaveMotion 8s linear infinite;"
      + "'></div>");
  }

  /** Applies ocean-themed button styling to an element. */
  private static void applyButtonStyle(Element el, boolean primary) {
    com.google.gwt.dom.client.Style s = el.getStyle();
    s.setProperty("borderRadius", "8px");
    s.setProperty("letterSpacing", "0.2px");
    if (primary) {
      s.setProperty("background", "rgba(79,195,247,0.2)");
      s.setProperty("color", "#e0f7ff");
      s.setProperty("border", "1px solid rgba(79,195,247,0.35)");
    } else {
      s.setProperty("background", "rgba(255,255,255,0.08)");
      s.setProperty("color", "#c8e6ff");
      s.setProperty("border", "1px solid rgba(255,255,255,0.15)");
    }
  }

  /** Wires the refresh link to reload the page (stripping the hash). */
  private static native void applyRefreshAction(Element el) /*-{
    el.onclick = function(e) {
      e.preventDefault();
      var href = $wnd.location.href;
      var idx = href.indexOf('#');
      if (idx >= 0) href = href.substring(0, idx);
      $wnd.location.replace(href);
    };
  }-*/;

  // ---- View interface ----

  @Override
  public void init(Listener listener) {
    this.listener = listener;
  }

  @Override
  public void reset() {
    this.listener = null;
  }

  @UiHandler("showDetail")
  void handleClick(ClickEvent e) {
    if (listener != null) {
      listener.onShowDetailClicked();
    }
  }

  @UiHandler("copyBtn")
  void handleCopyClick(ClickEvent e) {
    copyToClipboard(stack.getInnerText(), copyBtn.getElement(),
        MESSAGES.copied(), MESSAGES.copyFailed());
  }

  @UiHandler("reportBugBtn")
  void handleReportBugClick(ClickEvent e) {
    reportModal.getStyle().setProperty("display", "block");
  }

  @UiHandler("cancelReportBtn")
  void handleCancelReportClick(ClickEvent e) {
    reportModal.getStyle().setProperty("display", "none");
  }

  @UiHandler("sendReportBtn")
  void handleSendReportClick(ClickEvent e) {
    String userContext = getTextAreaValue(reportTextArea);
    String stackText = stack.getInnerText();
    sendReportBtn.setText(MESSAGES.sending());
    sendBugReport(stackText, userContext, overlay, reportModal,
        MESSAGES.reportSent(), MESSAGES.reportFailed());
  }

  /** Reads the value from a textarea element. */
  private static native String getTextAreaValue(Element el) /*-{
    return el.value || '';
  }-*/;

  /**
   * Posts a bug report to /contact via XMLHttpRequest, then shows a toast.
   */
  private static native void sendBugReport(String stackTrace, String userContext,
      Element overlayEl, Element modalEl,
      String successMsg, String failMsg) /*-{
    var ua = $wnd.navigator.userAgent || 'unknown';
    var url = $wnd.location.href || 'unknown';
    var ts = new Date().toISOString();
    var message = 'Auto-reported error:\n' + stackTrace
        + '\n\nUser context:\n' + (userContext || '(none provided)')
        + '\n\nBrowser: ' + ua
        + '\nURL: ' + url
        + '\nTime: ' + ts;

    // Escape for JSON
    function esc(s) {
      return s.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
              .replace(/\n/g, '\\n').replace(/\r/g, '\\r')
              .replace(/\t/g, '\\t');
    }

    var body = '{"name":"' + esc('Bug Reporter')
        + '","email":""'
        + ',"subject":"' + esc('Bug Report')
        + '","message":"' + esc(message) + '"}';

    var xhr = new $wnd.XMLHttpRequest();
    xhr.open('POST', '/contact', true);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.onreadystatechange = function() {
      if (xhr.readyState !== 4) return;
      if (xhr.status >= 200 && xhr.status < 300) {
        @org.waveprotocol.box.webclient.widget.error.ErrorIndicatorWidget::showToast(Ljava/lang/String;Lcom/google/gwt/dom/client/Element;)(successMsg, overlayEl);
      } else {
        @org.waveprotocol.box.webclient.widget.error.ErrorIndicatorWidget::showToast(Ljava/lang/String;Lcom/google/gwt/dom/client/Element;)(failMsg, null);
      }
      modalEl.style.display = 'none';
    };
    xhr.send(body);
  }-*/;

  /**
   * Shows a brief toast notification, then optionally dismisses the overlay.
   */
  private static void showToast(String message, Element overlayToDismiss) {
    Element toast = Document.get().createDivElement();
    toast.setInnerText(message);
    com.google.gwt.dom.client.Style ts = toast.getStyle();
    ts.setProperty("position", "fixed");
    ts.setProperty("top", "16px");
    ts.setProperty("left", "50%");
    ts.setProperty("transform", "translateX(-50%)");
    ts.setProperty("zIndex", "100000");
    ts.setProperty("background", "#0d1b2a");
    ts.setProperty("color", "#e0f0ff");
    ts.setProperty("padding", "12px 24px");
    ts.setProperty("borderRadius", "10px");
    ts.setProperty("fontSize", "14px");
    ts.setProperty("fontFamily", "sans-serif");
    ts.setProperty("boxShadow", "0 4px 16px rgba(0,0,0,0.4)");
    ts.setProperty("animation", "errorFadeIn 0.3s ease-out");
    Document.get().getBody().appendChild(toast);
    removeToastAfterDelay(toast, overlayToDismiss);
  }

  /** Removes the toast element after 2.5 seconds, and optionally dismisses the overlay. */
  private static native void removeToastAfterDelay(Element toast, Element overlayEl) /*-{
    $wnd.setTimeout(function() {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
      if (overlayEl) {
        overlayEl.style.transition = 'opacity 0.3s ease-out, transform 0.3s ease-out';
        overlayEl.style.opacity = '0';
        overlayEl.style.transform = 'translateX(-50%) translateY(-24px)';
        $wnd.setTimeout(function() { overlayEl.style.display = 'none'; }, 300);
      }
    }, 2500);
  }-*/;

  @UiHandler("dismissBtn")
  void handleDismissClick(ClickEvent e) {
    dismissWithAnimation(overlay);
  }

  /** Fades the overlay out over 300ms and then hides it. */
  private static native void dismissWithAnimation(Element el) /*-{
    el.style.transition = 'opacity 0.3s ease-out, transform 0.3s ease-out';
    el.style.opacity = '0';
    el.style.transform = 'translateX(-50%) translateY(-24px)';
    $wnd.setTimeout(function() {
      el.style.display = 'none';
    }, 300);
  }-*/;

  private static native void copyToClipboard(String text, Element btn,
      String copiedLabel, String failedLabel) /*-{
    function onSuccess() {
      btn.innerText = copiedLabel;
    }
    function onFailure() {
      btn.innerText = failedLabel;
    }
    function fallback() {
      var ta = $doc.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.left = '-9999px';
      $doc.body.appendChild(ta);
      ta.select();
      try {
        var ok = $doc.execCommand('copy');
        if (ok) {
          onSuccess();
        } else {
          onFailure();
        }
      } catch (e) {
        onFailure();
      }
      $doc.body.removeChild(ta);
    }
    if ($wnd.navigator.clipboard && $wnd.navigator.clipboard.writeText) {
      $wnd.navigator.clipboard.writeText(text).then(onSuccess, fallback);
    } else {
      fallback();
    }
  }-*/;

  @Override
  public void setStack(SafeHtml stack) {
    this.stack.setInnerHTML(stack.asString());
  }

  @Override
  public void setBug(SafeHtml bug) {
    this.bug.setInnerHTML(bug.asString());
  }

  @Override
  public void showDetailLink() {
    showDetail.setVisible(true);
    copyBtn.setVisible(true);
    reportBugBtn.setVisible(true);
  }

  @Override
  public void hideDetailLink() {
    showDetail.setVisible(false);
  }

  @Override
  public void expandDetailBox() {
    detail.addClassName(style.expanded());
  }

  @Override
  public void collapseDetailBox() {
    detail.removeClassName(style.expanded());
  }
}
