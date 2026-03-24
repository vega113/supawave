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
import com.google.gwt.dom.client.Element;
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
 */
public final class ErrorIndicatorWidget extends Composite implements ErrorIndicatorView {

  interface Binder extends UiBinder<HTMLPanel, ErrorIndicatorWidget> {
  }

  interface Style extends CssResource {
    // Css classes used by code.
    String expanded();

    // Classes not used by code, but forced to be declared thanks to UiBinder.
    String overlay();
    String header();
    String message();
    String actions();
    String btn();
    String btnPrimary();
    String alert();
    String detail();
    String stack();
  }

  private static final Binder BINDER = GWT.create(Binder.class);
  private static final ErrorMessages MESSAGES = GWT.create(ErrorMessages.class);

  @UiField
  Style style;
  @UiField
  Anchor showDetail;
  @UiField
  Element detail;
  @UiField
  Element stack;
  @UiField
  Element bug;
  @UiField
  Element overlay;
  @UiField
  Anchor copyBtn;
  @UiField
  Anchor dismissBtn;

  private Listener listener;


  private ErrorIndicatorWidget() {
    initWidget(BINDER.createAndBindUi(this));
  }

  public static ErrorIndicatorWidget create() {
    return new ErrorIndicatorWidget();
  }

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

  @UiHandler("dismissBtn")
  void handleDismissClick(ClickEvent e) {
    overlay.getStyle().setProperty("display", "none");
  }

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
