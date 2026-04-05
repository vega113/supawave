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

package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import org.waveprotocol.wave.model.util.Preconditions;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.wave.client.common.util.WaveRefConstants;
import org.waveprotocol.wave.client.wavepanel.view.BlipLinkPopupView;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n.BlipLinkMessages;
import org.waveprotocol.wave.client.widget.popup.AlignedPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;

/**
 * Widget implementation of a blip link info popup.
 *
 * @author vega113@gmail.com (Yuri Z.)
 */
public final class BlipLinkPopupWidget extends Composite
    implements BlipLinkPopupView, PopupEventListener {

  interface Binder extends UiBinder<HTMLPanel, BlipLinkPopupWidget> {
  }

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
    /** CSS */
    @Source("BlipLinkPopupWidget.css")
    Style style();
  }

  interface Style extends CssResource {

    String explanation();

    String link();

    String linkRow();

    String copyButton();

    String copied();

    String self();

    String title();
  }

  private final static Binder BINDER = GWT.create(Binder.class);

  @UiField(provided = true)
  final static Style style = GWT.<Resources> create(Resources.class).style();

  static {
    // StyleInjector's default behaviour of deferred injection messes up
    // popups, which do synchronous layout queries for positioning. Therefore,
    // we force synchronous injection.
    StyleInjector.inject(style.getText(), true);
  }

  private static final BlipLinkMessages messages = GWT.create(BlipLinkMessages.class);

  /** Duration in milliseconds to show "Copied!" feedback before reverting. */
  private static final int COPIED_FEEDBACK_MS = 2000;

  @UiField
  TextBox linkInfoBox;
  @UiField
  TextBox waverefLink;
  @UiField
  Button copyLinkInfoButton;
  @UiField
  Button copyWaverefButton;

  /** Popup containing this widget. */
  private final UniversalPopup popup;

  /** Optional listener for view events. */
  private Listener listener;

  /**
   * Creates link info popup.
   */
  public BlipLinkPopupWidget(Element relative) {
    initWidget(BINDER.createAndBindUi(this));
    // Wrap in a popup.
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(relative, AlignedPopupPositioner.ABOVE_RIGHT, chrome, true);
    popup.add(this);
    popup.addPopupEventListener(this);

    // Wire up copy-to-clipboard buttons.
    copyLinkInfoButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        copyToClipboard(linkInfoBox.getText(), copyLinkInfoButton);
      }
    });
    copyWaverefButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        copyToClipboard(waverefLink.getText(), copyWaverefButton);
      }
    });
  }

  @Override
  public void init(Listener listener) {
    Preconditions.checkState(this.listener == null, "this.listener == null");
    Preconditions.checkArgument(listener != null, "listener != null");
    this.listener = listener;
  }

  @Override
  public void reset() {
    Preconditions.checkState(this.listener != null, "this.listener != null");
    this.listener = null;
    linkInfoBox.setText(null);
  }

  @Override
  public void setLinkInfo(String url) {
    linkInfoBox.setText(url);
    String path =
        GWT.getHostPageBaseURL() + "waveref/"
            + url.substring(WaveRefConstants.WAVE_URI_PREFIX.length());
    waverefLink.setText(path);
  }

  @Override
  public void show() {
    popup.show();
  }

  @Override
  public void hide() {
    popup.hide();
  }

  @Override
  public void onShow(PopupEventSourcer source) {
    if (listener != null) {
      listener.onShow();
    }
    linkInfoBox.selectAll();
  }

  @Override
  public void onHide(PopupEventSourcer source) {
    if (listener != null) {
      listener.onHide();
    }
  }

  /**
   * Copies the given text to the clipboard and shows brief "Copied!" feedback
   * on the supplied button.
   */
  private void copyToClipboard(String text, final Button button) {
    nativeCopyToClipboard(text);
    final String originalText = button.getText();
    button.setText(messages.copied());
    button.addStyleName(style.copied());
    new Timer() {
      @Override
      public void run() {
        button.setText(originalText);
        button.removeStyleName(style.copied());
      }
    }.schedule(COPIED_FEEDBACK_MS);
  }

  /**
   * Uses the browser Clipboard API to write text. Falls back to
   * {@code document.execCommand('copy')} via a temporary textarea for
   * older browsers.
   */
  private static native void nativeCopyToClipboard(String text) /*-{
    if ($wnd.navigator && $wnd.navigator.clipboard && $wnd.navigator.clipboard.writeText) {
      $wnd.navigator.clipboard.writeText(text);
    } else {
      // Fallback: create a temporary textarea, select its content, and copy.
      var ta = $doc.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.left = '-9999px';
      $doc.body.appendChild(ta);
      ta.select();
      $doc.execCommand('copy');
      $doc.body.removeChild(ta);
    }
  }-*/;
}
