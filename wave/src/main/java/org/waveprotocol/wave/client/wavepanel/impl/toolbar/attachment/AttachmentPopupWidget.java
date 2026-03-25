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

package org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment;

import com.google.common.base.Preconditions;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormPanel.SubmitEvent;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Hidden;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;

import org.waveprotocol.wave.client.wavepanel.view.AttachmentPopupView;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.media.model.AttachmentId;

/**
 * Modern attachment upload popup with drag-and-drop support, file preview,
 * and upload progress indication.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public final class AttachmentPopupWidget extends Composite implements AttachmentPopupView,
    PopupEventListener {

  interface Binder extends UiBinder<HTMLPanel, AttachmentPopupWidget> {
  }

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
    /** CSS */
    @Source("AttachmentPopupWidget.css")
    Style style();

    @Source("spinner.gif")
    ImageResource spinner();
  }

  interface Style extends CssResource {

    String self();

    String title();

    String spinnerPanel();

    String spinner();

    String status();

    String error();

    String done();

    String hiddenFileInput();
  }

  private final static Binder BINDER = GWT.create(Binder.class);

  @UiField(provided = true)
  final static Style style = GWT.<Resources> create(Resources.class).style();

  private static final String UPLOAD_ACTION_URL = "/attachment/";

  static {
    StyleInjector.inject(style.getText(), true);
  }

  @UiField
  HTMLPanel dropZone;
  @UiField
  FileUpload fileUpload;
  @UiField
  Button uploadBtn;
  @UiField
  FormPanel form;
  @UiField
  Hidden formAttachmentId;
  @UiField
  Hidden formWaveRef;
  @UiField
  HorizontalPanel spinnerPanel;
  @UiField
  Label status;
  @UiField
  Image spinnerImg;
  @UiField
  HTMLPanel filePreviewPanel;
  @UiField
  InlineLabel fileNameLabel;
  @UiField
  InlineLabel fileSizeLabel;
  @UiField
  HTMLPanel progressBarOuter;
  @UiField
  HTMLPanel progressBarFill;

  /** Popup containing this widget. */
  private final UniversalPopup popup;

  /** Optional listener for view events. */
  private Listener listener;

  private AttachmentId attachmentId;
  private String waveRefStr;

  /** Timer for simulating upload progress. */
  private Timer progressTimer;
  private double simulatedProgress;

  /**
   * Creates the modern attachment upload popup.
   */
  public AttachmentPopupWidget() {
    initWidget(BINDER.createAndBindUi(this));
    form.setEncoding(FormPanel.ENCODING_MULTIPART);
    form.setMethod(FormPanel.METHOD_POST);

    // Submit handler
    form.addSubmitHandler(new FormPanel.SubmitHandler() {
      @Override
      public void onSubmit(SubmitEvent event) {
        // Start progress animation
        startProgressAnimation();
      }
    });

    // Submit complete handler
    form.addSubmitCompleteHandler(new FormPanel.SubmitCompleteHandler() {
      @Override
      public void onSubmitComplete(SubmitCompleteEvent event) {
        stopProgressAnimation();
        spinnerImg.setVisible(false);
        String results = event.getResults();
        if (results != null && results.contains("OK")) {
          // Set progress to 100%
          setProgressWidth(100);
          status.setText("Upload complete!");
          status.addStyleName("success");
          status.removeStyleName("error");
          listener.onDone(waveRefStr, attachmentId.getId(), fileUpload.getFilename());
          // Auto-close after a brief delay
          new Timer() {
            @Override
            public void run() {
              hide();
            }
          }.schedule(800);
        } else {
          status.setText("Upload failed. Please try again.");
          status.addStyleName("error");
          status.removeStyleName("success");
          uploadBtn.setEnabled(true);
        }
      }
    });

    // Click on drop zone triggers file browser
    dropZone.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        nativeClickFileInput(fileUpload.getElement());
      }
    }, ClickEvent.getType());

    // File selection change handler
    fileUpload.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        onFileSelected();
      }
    });

    // Upload button handler
    uploadBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        String filename = fileUpload.getFilename();
        if (filename.length() == 0) {
          status.setText("Please select a file first.");
          status.addStyleName("error");
        } else {
          uploadBtn.setEnabled(false);
          spinnerPanel.setVisible(true);
          formAttachmentId.setValue(attachmentId.getId());
          formWaveRef.setValue(waveRefStr);
          form.submit();
        }
      }
    });

    // Setup drag-and-drop on the drop zone via native events
    setupDragDrop(dropZone.getElement(), fileUpload.getElement());

    // Initially disable upload button
    uploadBtn.setEnabled(false);

    // Wrap in a popup
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    popup.add(this);
    popup.addPopupEventListener(this);
  }

  /**
   * Handles file selection - shows preview and enables upload.
   */
  private void onFileSelected() {
    String filename = fileUpload.getFilename();
    if (filename != null && !filename.isEmpty()) {
      // Extract just the file name (strip path)
      int lastSlash = filename.lastIndexOf('/');
      int lastBackSlash = filename.lastIndexOf('\\');
      String displayName = filename;
      if (lastSlash >= 0) {
        displayName = filename.substring(lastSlash + 1);
      } else if (lastBackSlash >= 0) {
        displayName = filename.substring(lastBackSlash + 1);
      }
      fileNameLabel.setText(displayName);
      fileSizeLabel.setText(""); // File size not available via GWT FileUpload
      filePreviewPanel.setVisible(true);
      uploadBtn.setEnabled(true);
      status.setText("");
      status.removeStyleName("error");
      status.removeStyleName("success");
    }
  }

  /**
   * Starts a simulated progress animation while waiting for the upload.
   */
  private void startProgressAnimation() {
    simulatedProgress = 0;
    progressBarOuter.addStyleName("active");
    setProgressWidth(0);

    progressTimer = new Timer() {
      @Override
      public void run() {
        // Asymptotically approach 90% (never reach 100% until server responds)
        simulatedProgress += (90 - simulatedProgress) * 0.08;
        setProgressWidth(simulatedProgress);
      }
    };
    progressTimer.scheduleRepeating(200);
  }

  /**
   * Stops the progress animation.
   */
  private void stopProgressAnimation() {
    if (progressTimer != null) {
      progressTimer.cancel();
      progressTimer = null;
    }
  }

  /**
   * Sets the progress bar width percentage.
   */
  private void setProgressWidth(double percent) {
    if (progressBarFill != null) {
      progressBarFill.getElement().getStyle().setProperty("width", Math.round(percent) + "%");
    }
  }

  /**
   * Programmatically clicks the hidden file input.
   */
  private static native void nativeClickFileInput(Element el) /*-{
    el.click();
  }-*/;

  /**
   * Sets up native drag-and-drop event handlers on the drop zone element.
   * When a file is dropped, it is set on the file input element.
   */
  private native void setupDragDrop(Element dropZoneEl, Element fileInputEl) /*-{
    var self = this;

    dropZoneEl.addEventListener('dragover', function(e) {
      e.preventDefault();
      e.stopPropagation();
      dropZoneEl.classList.add('dragover');
    }, false);

    dropZoneEl.addEventListener('dragleave', function(e) {
      e.preventDefault();
      e.stopPropagation();
      dropZoneEl.classList.remove('dragover');
    }, false);

    dropZoneEl.addEventListener('drop', function(e) {
      e.preventDefault();
      e.stopPropagation();
      dropZoneEl.classList.remove('dragover');

      var files = e.dataTransfer.files;
      if (files && files.length > 0) {
        // Set the dropped file on the file input
        fileInputEl.files = files;
        // Trigger change event so GWT picks it up
        var evt = $doc.createEvent('HTMLEvents');
        evt.initEvent('change', true, false);
        fileInputEl.dispatchEvent(evt);
      }
    }, false);
  }-*/;

  @Override
  public void init(Listener listener) {
    Preconditions.checkState(this.listener == null);
    Preconditions.checkArgument(listener != null);
    this.listener = listener;
  }

  @Override
  public void reset() {
    Preconditions.checkState(this.listener != null);
    this.listener = null;
  }

  @Override
  public void show() {
    Preconditions.checkState(this.attachmentId != null);
    form.setAction(UPLOAD_ACTION_URL + attachmentId.getId());
    spinnerPanel.setVisible(false);
    filePreviewPanel.setVisible(false);
    progressBarOuter.removeStyleName("active");
    uploadBtn.setEnabled(false);
    status.setText("");
    status.removeStyleName("error");
    status.removeStyleName("success");
    popup.show();
  }

  @Override
  public void hide() {
    stopProgressAnimation();
    popup.hide();
  }

  @Override
  public void onShow(PopupEventSourcer source) {
    if (listener != null) {
      listener.onShow();
    }
  }

  @Override
  public void onHide(PopupEventSourcer source) {
    if (listener != null) {
      listener.onHide();
    }
  }

  @Override
  public void setAttachmentId(AttachmentId id) {
    attachmentId = id;
  }

  @Override
  public void setWaveRef(String waveRefStr) {
    this.waveRefStr = waveRefStr;
  }
}
