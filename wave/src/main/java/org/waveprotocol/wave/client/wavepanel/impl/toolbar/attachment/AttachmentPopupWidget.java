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

import org.waveprotocol.wave.model.util.Preconditions;
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
import com.google.gwt.user.client.ui.FlowPanel;
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
  @UiField
  HTMLPanel displaySizePanel;
  @UiField
  Button sizeBtnSmall;
  @UiField
  Button sizeBtnMedium;
  @UiField
  Button sizeBtnLarge;
  @UiField
  HTMLPanel compressionInfoPanel;
  @UiField
  InlineLabel originalSizeLabel;
  @UiField
  InlineLabel compressedSizeLabel;
  @UiField
  Button compressToggleBtn;

  /** Popup containing this widget. */
  private final UniversalPopup popup;

  /** Optional listener for view events. */
  private Listener listener;

  private AttachmentId attachmentId;
  private String waveRefStr;

  /** Timer for simulating upload progress. */
  private Timer progressTimer;
  private double simulatedProgress;

  /** Currently selected display size. */
  private String selectedDisplaySize = "small";

  /** Whether image compression is enabled. */
  private boolean compressionEnabled = true;

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
          listener.onDoneWithSize(waveRefStr, attachmentId.getId(), fileUpload.getFilename(),
              selectedDisplaySize);
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
          // If compression is enabled and file is an image, compress first
          if (compressionEnabled && isImageFile(filename)) {
            int maxDim;
            switch (selectedDisplaySize) {
              case "medium": maxDim = 800; break;
              case "large": maxDim = 1920; break;
              default: maxDim = 200; break;
            }
            compressAndUploadImage(fileUpload.getElement(), maxDim, maxDim, 0.8);
          } else {
            form.submit();
          }
        }
      }
    });

    // Setup drag-and-drop on the drop zone via native events
    setupDragDrop(dropZone.getElement(), fileUpload.getElement());

    // Initially disable upload button
    uploadBtn.setEnabled(false);

    // Display size selector buttons
    sizeBtnSmall.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        selectDisplaySize("small");
      }
    });
    sizeBtnMedium.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        selectDisplaySize("medium");
      }
    });
    sizeBtnLarge.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        selectDisplaySize("large");
      }
    });
    // Default selection
    selectDisplaySize("small");

    // Compression toggle
    compressToggleBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        compressionEnabled = !compressionEnabled;
        compressToggleBtn.setText(compressionEnabled ? "Compress: ON" : "Compress: OFF");
        compressToggleBtn.getElement().getStyle().setProperty("opacity",
            compressionEnabled ? "1.0" : "0.6");
      }
    });
    compressionInfoPanel.setVisible(false);

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

      // Show compression panel for image files
      boolean isImage = isImageFile(displayName);
      compressionInfoPanel.setVisible(isImage);
      if (isImage) {
        // Try to read file size via native JS
        readFileInfoNative(fileUpload.getElement());
      }
    }
  }

  /**
   * Returns true if the filename looks like an image file.
   */
  private static boolean isImageFile(String filename) {
    if (filename == null) return false;
    String fn = filename.toLowerCase();
    return fn.endsWith(".jpg") || fn.endsWith(".jpeg") || fn.endsWith(".png")
        || fn.endsWith(".gif") || fn.endsWith(".webp") || fn.endsWith(".bmp");
  }

  /**
   * Reads file size information from the native file input element via JSNI.
   */
  private native void readFileInfoNative(Element fileInput) /*-{
    var self = this;
    if (fileInput.files && fileInput.files.length > 0) {
      var file = fileInput.files[0];
      var sizeKB = Math.round(file.size / 1024);
      var sizeStr = sizeKB > 1024 ? (Math.round(sizeKB / 1024 * 10) / 10) + " MB" : sizeKB + " KB";
      self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::setOriginalSize(Ljava/lang/String;)(sizeStr);

      // Estimate compressed size based on selected display size
      var maxDim = 1920; // large
      var quality = 0.8;
      var selectedSize = self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::selectedDisplaySize;
      if (selectedSize === "small") maxDim = 200;
      else if (selectedSize === "medium") maxDim = 800;

      // For images, try to read and estimate
      if (file.type && file.type.match(/^image\//)) {
        var estimatedRatio = (maxDim <= 200) ? 0.05 : (maxDim <= 800) ? 0.2 : 0.5;
        var estimatedSize = Math.round(file.size * estimatedRatio / 1024);
        var estStr = estimatedSize > 1024 ? (Math.round(estimatedSize / 1024 * 10) / 10) + " MB" : estimatedSize + " KB";
        self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::setCompressedSize(Ljava/lang/String;)(estStr);
      }
    }
  }-*/;

  /**
   * Called from JSNI to set the original file size label.
   */
  private void setOriginalSize(String sizeStr) {
    originalSizeLabel.setText("Original: " + sizeStr);
  }

  /**
   * Called from JSNI to set the estimated compressed size label.
   */
  private void setCompressedSize(String sizeStr) {
    compressedSizeLabel.setText("Compressed: ~" + sizeStr);
  }

  /**
   * Compresses an image file using Canvas API before upload. This is called
   * via JSNI to leverage the browser's native canvas for resizing.
   *
   * @param fileInput the file input element
   * @param maxWidth maximum width for the compressed image
   * @param maxHeight maximum height for the compressed image
   * @param quality JPEG quality (0.0 to 1.0)
   */
  private native void compressAndUploadImage(Element fileInput, int maxWidth, int maxHeight,
      double quality) /*-{
    var self = this;
    if (!fileInput.files || fileInput.files.length === 0) return;
    var file = fileInput.files[0];
    if (!file.type.match(/^image\//)) {
      // Not an image, submit form directly
      self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::submitFormDirectly()();
      return;
    }

    var reader = new $wnd.FileReader();
    reader.onload = function(e) {
      var img = new $wnd.Image();
      img.onload = function() {
        var w = img.width;
        var h = img.height;
        // Only compress if image exceeds max dimensions
        if (w <= maxWidth && h <= maxHeight) {
          self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::submitFormDirectly()();
          return;
        }
        // Calculate new dimensions maintaining aspect ratio
        var ratio = Math.min(maxWidth / w, maxHeight / h);
        var newW = Math.round(w * ratio);
        var newH = Math.round(h * ratio);

        var canvas = $doc.createElement('canvas');
        canvas.width = newW;
        canvas.height = newH;
        var ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, newW, newH);

        // Convert to blob and create a new File
        canvas.toBlob(function(blob) {
          if (blob) {
            var compressedFile = new $wnd.File([blob], file.name, {type: 'image/jpeg'});
            // Create a DataTransfer to replace the file input's files
            var dt = new $wnd.DataTransfer();
            dt.items.add(compressedFile);
            fileInput.files = dt.files;

            // Update the compressed size label
            var sizeKB = Math.round(blob.size / 1024);
            var sizeStr = sizeKB > 1024 ? (Math.round(sizeKB / 1024 * 10) / 10) + " MB" : sizeKB + " KB";
            self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::setCompressedSize(Ljava/lang/String;)("Compressed: " + sizeStr);
          }
          // Submit the form with the compressed image
          self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::submitFormDirectly()();
        }, 'image/jpeg', quality);
      };
      img.src = e.target.result;
    };
    reader.readAsDataURL(file);
  }-*/;

  /**
   * Submits the form directly (called after compression, or for non-image files).
   */
  private void submitFormDirectly() {
    form.submit();
  }

  /**
   * Updates the display size button styles to reflect the current selection.
   */
  private void selectDisplaySize(String size) {
    selectedDisplaySize = size;
    sizeBtnSmall.removeStyleName("size-btn-active");
    sizeBtnMedium.removeStyleName("size-btn-active");
    sizeBtnLarge.removeStyleName("size-btn-active");
    switch (size) {
      case "small":
        sizeBtnSmall.addStyleName("size-btn-active");
        break;
      case "medium":
        sizeBtnMedium.addStyleName("size-btn-active");
        break;
      case "large":
        sizeBtnLarge.addStyleName("size-btn-active");
        break;
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
    Preconditions.checkState(this.listener == null, "this.listener == null");
    Preconditions.checkArgument(listener != null, "listener != null");
    this.listener = listener;
  }

  @Override
  public void reset() {
    Preconditions.checkState(this.listener != null, "this.listener != null");
    this.listener = null;
  }

  @Override
  public void show() {
    Preconditions.checkState(this.attachmentId != null, "this.attachmentId != null");
    form.setAction(UPLOAD_ACTION_URL + attachmentId.getId());
    spinnerPanel.setVisible(false);
    filePreviewPanel.setVisible(false);
    compressionInfoPanel.setVisible(false);
    progressBarOuter.removeStyleName("active");
    uploadBtn.setEnabled(false);
    status.setText("");
    status.removeStyleName("error");
    status.removeStyleName("success");
    selectDisplaySize("small");
    compressionEnabled = true;
    compressToggleBtn.setText("Compress: ON");
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
