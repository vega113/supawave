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
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Hidden;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;

import org.waveprotocol.wave.client.wavepanel.view.AttachmentPopupView;
import org.waveprotocol.wave.client.widget.popup.CenterPopupPositioner;
import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupEventListener;
import org.waveprotocol.wave.client.widget.popup.PopupEventSourcer;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.util.AttachmentUploadMobileSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern attachment upload popup with multi-file support, thumbnail previews,
 * per-file captions, and real XHR upload progress.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public final class AttachmentPopupWidget extends Composite implements AttachmentPopupView,
    PopupEventListener {

  interface Binder extends UiBinder<HTMLPanel, AttachmentPopupWidget> {
  }

  /** Resources used by this widget. */
  public interface Resources extends ClientBundle {
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

  /**
   * Holds state for a single selected file awaiting upload.
   */
  private final class FileEntry {
    /** Index into the native FileList on the file input element. */
    final int fileIndex;
    final String fileName;
    final String mimeType;
    final double fileSize;

    /** Assigned at upload time via listener.requestNewAttachmentId(). */
    AttachmentId attachmentId;

    /** True once the file has been successfully uploaded and inserted into the wave. */
    boolean completed = false;

    /** The card panel shown in the preview grid. */
    final HTMLPanel card;

    /** The progress bar fill inside the card. */
    final HTMLPanel progressFill;

    /** The caption text box inside the card. */
    final TextBox captionInput;

    /** The img element for image previews. */
    final Element imgEl;

    /** The icon container (hidden once preview loads for images). */
    final Element iconEl;

    FileEntry(int fileIndex, String fileName, String mimeType, double fileSize,
        HTMLPanel card, HTMLPanel progressFill, TextBox captionInput,
        Element imgEl, Element iconEl) {
      this.fileIndex = fileIndex;
      this.fileName = fileName;
      this.mimeType = mimeType;
      this.fileSize = fileSize;
      this.card = card;
      this.progressFill = progressFill;
      this.captionInput = captionInput;
      this.imgEl = imgEl;
      this.iconEl = iconEl;
    }

    void setProgressWidth(int pct) {
      progressFill.getElement().getStyle().setProperty("width", pct + "%");
    }

    void showError() {
      card.addStyleName("upload-card-error");
    }

    void markCompleted() {
      completed = true;
      setProgressWidth(100);
      card.addStyleName("upload-card-done");
      setCardControlsEnabled(this, false);
    }
  }

  private static final Binder BINDER = GWT.create(Binder.class);

  @UiField(provided = true)
  static final Style style = GWT.<Resources>create(Resources.class).style();

  private static final String UPLOAD_ACTION_URL = "/attachment/";

  /**
   * Modern CSS for the multi-file upload popup. Injected at class load time.
   * Uses Wave's blue/teal palette with a wave-shimmer progress animation.
   */
  private static final String MODERN_CSS =
    // Popup container
    ".attachment-popup-modern{width:460px;padding:16px;box-sizing:border-box;" +
    "font-family:'Roboto',Arial,sans-serif;}" +
    // Drop zone
    ".upload-dropzone{border:2px dashed #4a90d9;border-radius:10px;padding:18px 12px;" +
    "text-align:center;cursor:pointer;color:#5a7fa8;transition:background .2s,border-color .2s;" +
    "margin-bottom:0;}" +
    ".upload-dropzone:hover,.upload-dropzone.dragover{background:#eef5fb;border-color:#0077b6;}" +
    ".upload-dropzone-icon{color:#4a90d9;margin-bottom:6px;}" +
    ".upload-dropzone-text{font-size:14px;font-weight:500;color:#336699;margin-bottom:8px;}" +
    ".upload-add-more-btn{background:transparent;border:1px solid #4a90d9;border-radius:16px;" +
    "color:#0077b6;cursor:pointer;font-size:12px;padding:4px 14px;transition:background .15s;}" +
    ".upload-add-more-btn:hover{background:#eef5fb;}" +
    // Wave separator
    ".upload-wave-separator{margin:8px 0;height:20px;overflow:hidden;}" +
    ".upload-wave-separator svg{width:100%;height:20px;}" +
    // Preview grid
    ".upload-preview-grid{display:flex;flex-wrap:wrap;gap:10px;max-height:300px;" +
    "overflow-y:auto;padding:6px 0;margin-bottom:10px;}" +
    // Preview card
    ".upload-card{position:relative;width:130px;border:1px solid #b8d0e8;border-radius:8px;" +
    "padding:8px;box-sizing:border-box;background:#fff;" +
    "box-shadow:0 1px 4px rgba(74,144,217,.15);transition:box-shadow .2s;}" +
    ".upload-card:hover{box-shadow:0 2px 8px rgba(74,144,217,.3);}" +
    ".upload-card-error{border-color:#d93025 !important;background:#fff8f8;}" +
    ".upload-card-done{border-color:#2e7d32 !important;opacity:0.75;}" +
    // Remove button
    ".upload-card-remove{position:absolute;top:4px;right:4px;width:20px;height:20px;" +
    "border-radius:50%;border:none;background:#f0f4f8;color:#5a7fa8;cursor:pointer;" +
    "font-size:14px;line-height:18px;padding:0;text-align:center;transition:background .15s;}" +
    ".upload-card-remove:hover{background:#d93025;color:#fff;}" +
    // Thumbnail area
    ".upload-card-thumb{width:100%;height:90px;border-radius:6px;overflow:hidden;" +
    "background:#f0f4f8;display:flex;align-items:center;justify-content:center;margin-bottom:6px;}" +
    ".upload-card-img{max-width:100%;max-height:90px;object-fit:cover;border-radius:4px;display:block;}" +
    // File icon
    ".upload-card-file-icon{display:flex;flex-direction:column;align-items:center;" +
    "justify-content:center;width:56px;height:64px;border-radius:4px;}" +
    ".upload-card-file-label{color:#fff;font-size:10px;font-weight:700;" +
    "margin-top:2px;letter-spacing:.5px;}" +
    // Name + size
    ".upload-card-name{font-size:11px;color:#336699;font-weight:500;display:block;" +
    "white-space:nowrap;overflow:hidden;text-overflow:ellipsis;width:100%;margin-bottom:2px;}" +
    ".upload-card-size{font-size:10px;color:#8aabcc;display:block;margin-bottom:6px;}" +
    // Caption input
    ".upload-card-caption{width:100%;box-sizing:border-box;border:1px solid #c8dff0;" +
    "border-radius:12px;padding:4px 10px;font-size:12px;color:#334;outline:none;" +
    "transition:border-color .2s,box-shadow .2s;margin-bottom:6px;}" +
    ".upload-card-caption:focus{border-color:#4a90d9;box-shadow:0 0 0 3px rgba(74,144,217,.2);}" +
    ".upload-card-caption::placeholder{color:#aac4dc;}" +
    // Per-card progress bar with wave-shimmer
    ".upload-card-progress{height:4px;background:#e0eaf5;border-radius:2px;overflow:hidden;}" +
    ".upload-card-progress-fill{height:100%;width:0%;background:#0077b6;border-radius:2px;" +
    "transition:width .15s;position:relative;overflow:hidden;}" +
    "@keyframes wave-shimmer{0%{transform:translateX(-100%)}100%{transform:translateX(200%)}}" +
    ".upload-card-progress-fill::after{content:'';position:absolute;top:0;left:0;" +
    "width:50%;height:100%;background:linear-gradient(90deg,transparent,rgba(255,255,255,.5),transparent);" +
    "animation:wave-shimmer 1.2s infinite;}" +
    // Controls row
    ".display-size-panel{display:flex;align-items:center;gap:6px;margin-bottom:8px;}" +
    ".display-size-label{font-size:12px;color:#5a7fa8;}" +
    ".size-btn{border:1px solid #b8d0e8;background:#f7fafc;border-radius:6px;padding:3px 10px;" +
    "font-size:12px;cursor:pointer;color:#336699;transition:background .15s,border-color .15s;}" +
    ".size-btn:hover{background:#eef5fb;}" +
    ".size-btn-active{background:#0077b6 !important;color:#fff !important;border-color:#0077b6 !important;}" +
    ".compression-info-panel{margin-bottom:10px;}" +
    ".compress-toggle-btn{background:transparent;border:1px solid #b8d0e8;border-radius:16px;" +
    "color:#0077b6;cursor:pointer;font-size:12px;padding:4px 12px;transition:background .15s;}" +
    ".compress-toggle-btn:hover{background:#eef5fb;}" +
    // Status
    ".upload-status-area{min-height:18px;margin-bottom:6px;font-size:13px;color:#336699;}" +
    ".upload-status-area.error{color:#d93025;}" +
    // Action buttons
    ".upload-actions{display:flex;justify-content:space-between;align-items:center;margin-top:10px;}" +
    ".upload-cancel-btn{background:transparent;border:1px solid #b8d0e8;border-radius:20px;" +
    "color:#5a7fa8;cursor:pointer;font-size:13px;padding:7px 18px;transition:background .15s;}" +
    ".upload-cancel-btn:hover{background:#f0f4f8;}" +
    ".upload-btn-modern{background:linear-gradient(135deg,#0077b6,#4a90d9);" +
    "border:none;border-radius:20px;color:#fff;cursor:pointer;font-size:13px;font-weight:500;" +
    "padding:7px 22px;transition:opacity .15s,box-shadow .15s;" +
    "box-shadow:0 2px 6px rgba(0,119,182,.3);}" +
    ".upload-btn-modern:hover{opacity:.9;box-shadow:0 3px 10px rgba(0,119,182,.4);}" +
    ".upload-btn-modern:disabled,.upload-btn-modern[disabled]{opacity:.5;cursor:default;box-shadow:none;}" +
    // Mobile
    "@media(max-width:480px){" +
    ".attachment-popup-modern{width:100%;}" +
    ".upload-card{width:calc(50% - 5px);}" +
    ".upload-preview-grid{max-height:240px;}" +
    "}";

  static {
    StyleInjector.inject(style.getText(), true);
    StyleInjector.inject(MODERN_CSS, true);
  }

  // ---- UiBinder fields ----
  @UiField HTMLPanel dropZone;
  @UiField HTMLPanel waveSeparator;
  @UiField FileUpload fileUpload;
  @UiField Button addMoreBtn;
  @UiField Button uploadBtn;
  @UiField Button cancelBtn;
  @UiField FormPanel form;
  @UiField Hidden formAttachmentId;
  @UiField Hidden formWaveRef;
  @UiField HorizontalPanel spinnerPanel;
  @UiField Label statusLabel;
  @UiField Image spinnerImg;
  @UiField FlowPanel previewGrid;
  @UiField HTMLPanel displaySizePanel;
  @UiField Button sizeBtnSmall;
  @UiField Button sizeBtnMedium;
  @UiField Button sizeBtnLarge;
  @UiField HTMLPanel compressionInfoPanel;
  @UiField Button compressToggleBtn;

  // ---- Instance state ----
  private final UniversalPopup popup;
  private Listener listener;
  private String waveRefStr;
  private String selectedDisplaySize = "medium";
  private boolean compressionEnabled = true;
  private final List<FileEntry> pendingFiles = new ArrayList<>();
  private boolean isUploading = false;
  private boolean batchFailed = false;
  private String uploadDisplaySize = "medium";
  private boolean uploadCompressionEnabled = true;
  private int nextStoreIndex = 0;
  private boolean awaitingFileSelection = false;
  private Timer fileSelectionRecoveryTimer;
  private int selectionNonce = 0;
  private int processedNonce = -1;
  private int fileCountAtArmTime = 0;

  public AttachmentPopupWidget() {
    initWidget(BINDER.createAndBindUi(this));
    form.setEncoding(FormPanel.ENCODING_MULTIPART);
    form.setMethod(FormPanel.METHOD_POST);

    // Inject SVG wave separator (SVG xmlns can't be in UiBinder XML directly)
    waveSeparator.getElement().setInnerHTML(
        "<svg viewBox='0 0 400 20' preserveAspectRatio='none' " +
        "xmlns='http://www.w3.org/2000/svg'>" +
        "<path d='M0,10 C50,0 100,20 150,10 C200,0 250,20 300,10 C350,0 400,20 400,10' " +
        "fill='none' stroke='#4a90d9' stroke-width='2' stroke-opacity='0.4'/>" +
        "</svg>");

    setMultipleAttribute(fileUpload.getElement());

    dropZone.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (isUploading) return;
        selectionNonce++;
        fileCountAtArmTime = getNativeFileCount(fileUpload.getElement());
        awaitingFileSelection = true;
        armFileSelectionRecovery();
        nativeClickFileInput(fileUpload.getElement());
      }
    }, ClickEvent.getType());

    addMoreBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (isUploading) return;
        selectionNonce++;
        fileCountAtArmTime = getNativeFileCount(fileUpload.getElement());
        awaitingFileSelection = true;
        armFileSelectionRecovery();
        nativeClickFileInput(fileUpload.getElement());
      }
    });

    fileUpload.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        onFilesSelected(selectionNonce);
      }
    });

    cancelBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        stopFileSelectionRecovery();
        hide();
      }
    });

    uploadBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (pendingFiles.isEmpty()) {
          showStatus("Please select at least one file.", true);
          return;
        }
        batchFailed = false;
        isUploading = true;
        uploadDisplaySize = selectedDisplaySize;
        uploadCompressionEnabled = compressionEnabled;
        uploadBtn.setEnabled(false);
        cancelBtn.setEnabled(false);
        addMoreBtn.setEnabled(false);
        sizeBtnSmall.setEnabled(false);
        sizeBtnMedium.setEnabled(false);
        sizeBtnLarge.setEnabled(false);
        compressToggleBtn.setEnabled(false);
        batchFailed = false;
        setElementDisabled(fileUpload.getElement(), true);
        // Disable per-card remove buttons and caption inputs during upload
        for (FileEntry fe : pendingFiles) {
          setCardControlsEnabled(fe, false);
        }
        uploadNext(0);
      }
    });

    sizeBtnSmall.addClickHandler(new ClickHandler() {
      @Override public void onClick(ClickEvent e) { selectDisplaySize("small"); }
    });
    sizeBtnMedium.addClickHandler(new ClickHandler() {
      @Override public void onClick(ClickEvent e) { selectDisplaySize("medium"); }
    });
    sizeBtnLarge.addClickHandler(new ClickHandler() {
      @Override public void onClick(ClickEvent e) { selectDisplaySize("large"); }
    });
    selectDisplaySize("medium");

    compressToggleBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        compressionEnabled = !compressionEnabled;
        compressToggleBtn.setText(compressionEnabled ? "Compress: ON" : "Compress: OFF");
        compressToggleBtn.getElement().getStyle().setProperty("opacity",
            compressionEnabled ? "1.0" : "0.6");
      }
    });

    setupDragDrop(dropZone.getElement(), fileUpload.getElement());
    uploadBtn.setEnabled(false);
    spinnerPanel.setVisible(false);

    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    popup.add(this);
    popup.addPopupEventListener(this);
  }

  // ─────────────────────────────────────────────
  //  File selection & preview
  // ─────────────────────────────────────────────

  /** Called from JSNI drag-drop handler to give each drop its own nonce. */
  private void bumpSelectionNonce() {
    selectionNonce++;
  }

  private void onFilesSelected(int nonce) {
    if (isUploading) return;
    if (processedNonce == nonce) return;
    processedNonce = nonce;
    awaitingFileSelection = false;
    stopFileSelectionRecovery();
    int added = appendFilesToStore(fileUpload.getElement());
    int startIndex = nextStoreIndex;
    nextStoreIndex += added;
    for (int i = 0; i < added; i++) {
      int storeIndex = startIndex + i;
      String name = getStoreFileName(storeIndex);
      String type = getStoreMimeType(storeIndex);
      double size = getStoreFileSize(storeIndex);
      FileEntry entry = buildPreviewCard(storeIndex, name, type, size);
      pendingFiles.add(entry);
      previewGrid.add(entry.card);
      if (isImageMime(type) || isImageFileName(name)) {
        readPreviewAsync(storeIndex);
      }
    }
    updateUploadButton();
  }

  private FileEntry buildPreviewCard(final int fileIndex, String name, String type, double size) {
    HTMLPanel card = new HTMLPanel("div", "");
    card.addStyleName("upload-card");

    Button removeBtn = new Button("×");
    removeBtn.addStyleName("upload-card-remove");
    removeBtn.getElement().setAttribute("title", "Remove file");
    removeBtn.getElement().setAttribute("aria-label", "Remove file");
    removeBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        removeFile(fileIndex);
      }
    });
    card.add(removeBtn);

    // Thumb area
    HTMLPanel thumbArea = new HTMLPanel("div", "");
    thumbArea.addStyleName("upload-card-thumb");

    Element imgEl = com.google.gwt.dom.client.Document.get().createImageElement();
    imgEl.addClassName("upload-card-img");
    imgEl.setAttribute("alt", name);
    imgEl.getStyle().setProperty("display", "none");
    thumbArea.getElement().appendChild(imgEl);

    Element iconEl = buildFileIconElement(type, name);
    thumbArea.getElement().appendChild(iconEl);
    card.add(thumbArea);

    Label nameLabel = new Label(truncateName(name, 18));
    nameLabel.addStyleName("upload-card-name");
    nameLabel.setTitle(name);
    card.add(nameLabel);

    Label sizeLabel = new Label(formatSize(size));
    sizeLabel.addStyleName("upload-card-size");
    card.add(sizeLabel);

    TextBox captionInput = new TextBox();
    captionInput.addStyleName("upload-card-caption");
    captionInput.getElement().setAttribute("placeholder", "Add a caption\u2026");
    card.add(captionInput);

    HTMLPanel progressOuter = new HTMLPanel("div", "");
    progressOuter.addStyleName("upload-card-progress");
    HTMLPanel progressFill = new HTMLPanel("div", "");
    progressFill.addStyleName("upload-card-progress-fill");
    progressOuter.add(progressFill);
    card.add(progressOuter);

    return new FileEntry(fileIndex, name, type, size, card, progressFill, captionInput, imgEl, iconEl);
  }

  private void removeFile(int fileIndex) {
    for (int i = 0; i < pendingFiles.size(); i++) {
      if (pendingFiles.get(i).fileIndex == fileIndex) {
        previewGrid.remove(pendingFiles.get(i).card);
        pendingFiles.remove(i);
        break;
      }
    }
    updateUploadButton();
  }

  /**
   * Enables or disables the per-card interactive controls (remove button and caption input)
   * for a given FileEntry. Called when an upload starts/ends to prevent concurrent mutations.
   */
  private static void setCardControlsEnabled(FileEntry entry, boolean enabled) {
    // The remove button is the first widget in the card (index 0)
    com.google.gwt.user.client.ui.Widget first = entry.card.getWidget(0);
    if (first instanceof Button) {
      ((Button) first).setEnabled(enabled);
    }
    entry.captionInput.setEnabled(enabled);
  }

  private void updateUploadButton() {
    int n = 0;
    for (FileEntry fe : pendingFiles) {
      if (!fe.completed) n++;
    }
    if (n == 0) {
      uploadBtn.setText("Upload");
      uploadBtn.setEnabled(false);
    } else if (n == 1) {
      uploadBtn.setText("Upload 1 file \u2192");
      uploadBtn.setEnabled(true);
    } else {
      uploadBtn.setText("Upload " + n + " files \u2192");
      uploadBtn.setEnabled(true);
    }
    showStatus("", false);
  }

  private void showStatus(String text, boolean isError) {
    statusLabel.setText(text);
    if (isError) {
      statusLabel.addStyleName("error");
    } else {
      statusLabel.removeStyleName("error");
    }
  }

  // ─────────────────────────────────────────────
  //  Upload loop
  // ─────────────────────────────────────────────

  private void uploadNext(int index) {
    if (index >= pendingFiles.size()) {
      isUploading = false;
      setElementDisabled(fileUpload.getElement(), false);
      uploadBtn.setEnabled(false);
      cancelBtn.setEnabled(true);
      addMoreBtn.setEnabled(true);
      sizeBtnSmall.setEnabled(true);
      sizeBtnMedium.setEnabled(true);
      sizeBtnLarge.setEnabled(true);
      compressToggleBtn.setEnabled(true);
      for (FileEntry fe : pendingFiles) {
        if (!fe.completed) setCardControlsEnabled(fe, true);
      }
      if (batchFailed) {
        showStatus("Some uploads failed. Please close and try again, or remove failed files.", true);
      } else {
        showStatus("All uploads complete!", false);
        new Timer() {
          @Override public void run() { hide(); }
        }.schedule(600);
      }
      return;
    }
    FileEntry entry = pendingFiles.get(index);
    // Skip already-completed entries (successfully uploaded in a previous run).
    if (entry.completed) {
      uploadNext(index + 1);
      return;
    }
    entry.attachmentId = listener.requestNewAttachmentId();
    entry.setProgressWidth(0);

    int maxDim;
    switch (uploadDisplaySize) {
      case "large":  maxDim = 1920; break;
      case "medium": maxDim = 800;  break;
      default:       maxDim = 200;  break;
    }

    boolean isImage = isImageMime(entry.mimeType) || isImageFileName(entry.fileName);
    uploadFileWithXhr(
        entry.fileIndex,
        entry.attachmentId.getId(),
        waveRefStr,
        uploadCompressionEnabled && isImage,
        maxDim,
        0.8,
        UPLOAD_ACTION_URL + entry.attachmentId.getId());
  }

  /** Called from JSNI when XHR upload progress fires. */
  private void onUploadProgress(int fileIndex, int percent) {
    for (FileEntry e : pendingFiles) {
      if (e.fileIndex == fileIndex) {
        e.setProgressWidth(percent);
        return;
      }
    }
  }

  /** Called from JSNI when an XHR upload completes (success or failure). */
  private void onFileUploadComplete(int fileIndex, boolean success) {
    FileEntry entry = null;
    int listIndex = -1;
    for (int i = 0; i < pendingFiles.size(); i++) {
      if (pendingFiles.get(i).fileIndex == fileIndex) {
        entry = pendingFiles.get(i);
        listIndex = i;
        break;
      }
    }
    if (entry == null) {
      // Entry was removed while its upload was in progress.
      // Re-enable controls and start from the beginning of the (now-shorter) queue.
      isUploading = false;
      setElementDisabled(fileUpload.getElement(), false);
      showStatus("A file was removed; remaining files will be uploaded.", false);
      uploadBtn.setEnabled(false);
      cancelBtn.setEnabled(true);
      addMoreBtn.setEnabled(true);
      for (FileEntry fe : pendingFiles) {
        if (!fe.completed) setCardControlsEnabled(fe, true);
      }
      uploadNext(0);
      return;
    }

    if (success) {
      entry.markCompleted();
      String caption = entry.captionInput.getText().trim();
      if (caption.isEmpty()) caption = entry.fileName;
      listener.onDoneWithSizeAndCaption(waveRefStr, entry.attachmentId.getId(),
          entry.fileName, uploadDisplaySize, caption);
    } else {
      batchFailed = true;
      entry.showError();
      batchFailed = true;
      showStatus("Upload failed: " + truncateName(entry.fileName, 20), true);
    }
    // Always continue to next file
    uploadNext(listIndex + 1);
  }

  /** Called from JSNI when image preview DataURL is ready. */
  private void onPreviewReady(int fileIndex, String dataUrl) {
    for (FileEntry e : pendingFiles) {
      if (e.fileIndex == fileIndex) {
        e.imgEl.setAttribute("src", dataUrl);
        e.imgEl.getStyle().clearProperty("display");
        e.iconEl.getStyle().setProperty("display", "none");
        return;
      }
    }
  }

  // ─────────────────────────────────────────────
  //  Display size
  // ─────────────────────────────────────────────

  private void selectDisplaySize(String size) {
    selectedDisplaySize = size;
    sizeBtnSmall.removeStyleName("size-btn-active");
    sizeBtnMedium.removeStyleName("size-btn-active");
    sizeBtnLarge.removeStyleName("size-btn-active");
    switch (size) {
      case "small":  sizeBtnSmall.addStyleName("size-btn-active");  break;
      case "medium": sizeBtnMedium.addStyleName("size-btn-active"); break;
      case "large":  sizeBtnLarge.addStyleName("size-btn-active");  break;
    }
  }

  // ─────────────────────────────────────────────
  //  Helpers
  // ─────────────────────────────────────────────

  private static boolean isImageMime(String mime) {
    return AttachmentUploadMobileSupport.isImageMime(mime);
  }

  private static boolean isImageFileName(String name) {
    if (name == null) return false;
    String fn = name.toLowerCase();
    return fn.endsWith(".jpg") || fn.endsWith(".jpeg") || fn.endsWith(".png")
        || fn.endsWith(".gif") || fn.endsWith(".webp") || fn.endsWith(".bmp");
  }

  private static String truncateName(String name, int max) {
    if (name == null) return "";
    if (name.length() <= max) return name;
    return name.substring(0, max - 1) + "\u2026";
  }

  private static String formatSize(double bytes) {
    if (bytes < 1024) return (int) bytes + " B";
    double kb = bytes / 1024;
    if (kb < 1024) return (int) kb + " KB";
    double mb = kb / 1024;
    return (int) mb + "." + ((int)(mb * 10) % 10) + " MB";
  }

  /**
   * Builds the file-type icon element for a preview card using DOM APIs to avoid XSS.
   * The filename extension is set via setInnerText (not innerHTML) so it cannot inject HTML.
   */
  private static Element buildFileIconElement(String mimeType, String fileName) {
    String ext = "";
    if (fileName != null) {
      int dot = fileName.lastIndexOf('.');
      if (dot >= 0 && dot < fileName.length() - 1) {
        ext = fileName.substring(dot + 1).toUpperCase();
      }
    }
    String color = "#757575";
    String label = ext.isEmpty() ? "FILE" : ext;
    if (mimeType != null) {
      String mt = mimeType.toLowerCase();
      if (mt.equals("application/pdf"))                                { color = "#E53935"; label = "PDF"; }
      else if (mt.startsWith("video/"))                                { color = "#7B1FA2"; }
      else if (mt.startsWith("audio/"))                                { color = "#00897B"; }
      else if (mt.startsWith("text/"))                                 { color = "#546E7A"; }
      else if (mt.contains("spreadsheet") || mt.contains("excel"))    { color = "#2E7D32"; }
      else if (mt.contains("presentation") || mt.contains("powerpoint")){ color = "#E65100"; }
      else if (mt.contains("document") || mt.contains("word"))        { color = "#1565C0"; }
      else if (mt.contains("zip") || mt.contains("tar"))              { color = "#795548"; }
      else if (mt.startsWith("image/"))                                { color = "#0288D1"; }
    }
    if (label.length() > 4) label = label.substring(0, 4);

    com.google.gwt.dom.client.Document doc = com.google.gwt.dom.client.Document.get();

    // Outer wrapper div
    Element wrapper = doc.createDivElement();
    wrapper.addClassName("upload-card-file-icon");
    wrapper.getStyle().setProperty("background", color);

    // SVG via innerHTML is safe here: no user data is included
    Element svgWrap = doc.createDivElement();
    svgWrap.setInnerHTML(
        "<svg viewBox='0 0 24 24' width='32' height='32' fill='white'>"
        + "<path d='M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z'/>"
        + "<polyline points='14 2 14 8 20 8' fill='none' stroke='white' stroke-width='1.5'/>"
        + "</svg>");
    wrapper.appendChild(svgWrap.getFirstChildElement());

    // Label span: use setInnerText to prevent XSS from extension strings
    Element span = doc.createSpanElement();
    span.addClassName("upload-card-file-label");
    span.setInnerText(label);  // safe: no HTML injection possible
    wrapper.appendChild(span);

    return wrapper;
  }

  // ─────────────────────────────────────────────
  //  JSNI
  // ─────────────────────────────────────────────

  private static native void setMultipleAttribute(Element el) /*-{
    el.setAttribute('multiple', 'multiple');
  }-*/;

  private static native void setElementDisabled(Element el, boolean disabled) /*-{
    el.disabled = disabled;
  }-*/;

  private static native void nativeClickFileInput(Element el) /*-{
    el.click();
  }-*/;

  private void maybeRecoverFileSelection() {
    if (AttachmentUploadMobileSupport.shouldRecoverSelection(awaitingFileSelection,
        isUploading, getNativeFileCount(fileUpload.getElement()), fileCountAtArmTime)) {
      onFilesSelected(selectionNonce);
    }
  }

  private void armFileSelectionRecovery() {
    stopFileSelectionRecovery();
    fileSelectionRecoveryTimer = new Timer() {
      private int attempts;

      @Override
      public void run() {
        attempts++;
        maybeRecoverFileSelection();
        if (!awaitingFileSelection || attempts >= 240) {
          awaitingFileSelection = false;
          cancel();
          fileSelectionRecoveryTimer = null;
        }
      }
    };
    fileSelectionRecoveryTimer.scheduleRepeating(250);
  }

  private void stopFileSelectionRecovery() {
    if (fileSelectionRecoveryTimer != null) {
      fileSelectionRecoveryTimer.cancel();
      fileSelectionRecoveryTimer = null;
    }
  }

  /** Initializes (or resets) the JS-side file store on this instance. */
  private native void initFileStore() /*-{
    this._fileStore = [];
  }-*/;

  /**
   * Appends all files currently in fileInput.files to the store.
   * Returns the number of files appended.
   */
  private native int appendFilesToStore(Element fileInput) /*-{
    if (!this._fileStore) this._fileStore = [];
    var files = fileInput.files;
    if (!files) return 0;
    for (var i = 0; i < files.length; i++) {
        this._fileStore.push(files[i]);
    }
    return files.length;
  }-*/;

  private static native int getNativeFileCount(Element fileInput) /*-{
    return (fileInput.files && fileInput.files.length) ? fileInput.files.length : 0;
  }-*/;

  private native String getStoreFileName(int index) /*-{
    return this._fileStore[index].name;
  }-*/;

  private native String getStoreMimeType(int index) /*-{
    return this._fileStore[index].type || '';
  }-*/;

  private native double getStoreFileSize(int index) /*-{
    return this._fileStore[index].size || 0;
  }-*/;

  private native void readPreviewAsync(int storeIndex) /*-{
    var self = this;
    var file = this._fileStore[storeIndex];
    if (!file) return;
    var reader = new $wnd.FileReader();
    reader.onload = function(e) {
      self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::onPreviewReady(ILjava/lang/String;)(storeIndex, e.target.result);
    };
    reader.readAsDataURL(file);
  }-*/;

  private native void uploadFileWithXhr(int fileIndex, String attachmentId,
      String waveRef, boolean compress, int maxDim, double quality, String uploadUrl) /*-{
    var self = this;
    var file = this._fileStore[fileIndex];
    if (!file) {
      self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::onFileUploadComplete(IZ)(fileIndex, false);
      return;
    }

    var doUpload = function(fileOrBlob) {
      var fd = new $wnd.FormData();
      fd.append('attachmentId', attachmentId);
      fd.append('waveRef', waveRef);
      fd.append('uploadFormElement', fileOrBlob, file.name);

      var xhr = new $wnd.XMLHttpRequest();
      xhr.open('POST', uploadUrl, true);

      xhr.upload.onprogress = function(e) {
        if (e.lengthComputable) {
          var pct = Math.round((e.loaded / e.total) * 100);
          self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::onUploadProgress(II)(fileIndex, pct);
        }
      };
      xhr.onload = function() {
        var ok = (xhr.status >= 200 && xhr.status < 300)
            && xhr.responseText && (xhr.responseText.indexOf('OK') >= 0);
        self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::onFileUploadComplete(IZ)(fileIndex, ok);
      };
      xhr.onerror = function() {
        self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::onFileUploadComplete(IZ)(fileIndex, false);
      };
      xhr.send(fd);
    };

    if (compress && file.type && file.type.match(/^image\//)) {
      var reader = new $wnd.FileReader();
      reader.onload = function(e) {
        var img = new $wnd.Image();
        img.onerror = function() {
          // Image failed to decode — upload original uncompressed file
          doUpload(file);
        };
        img.onload = function() {
          var w = img.width, h = img.height;
          if (w <= maxDim && h <= maxDim) {
            doUpload(file);
            return;
          }
          var ratio = Math.min(maxDim / w, maxDim / h);
          var nw = Math.round(w * ratio), nh = Math.round(h * ratio);
          var canvas = $doc.createElement('canvas');
          canvas.width = nw; canvas.height = nh;
          canvas.getContext('2d').drawImage(img, 0, 0, nw, nh);
          // Preserve the original MIME type so the server derives the correct Content-Type.
          // If the browser cannot encode to the original type, fall back to the original file.
          var outputMime = file.type;
          var outputQuality = (outputMime === 'image/jpeg' || outputMime === 'image/webp') ? quality : undefined;
          canvas.toBlob(function(blob) {
            doUpload(blob || file);
          }, outputMime, outputQuality);
        };
        img.src = e.target.result;
      };
      reader.onerror = function() {
        // FileReader failed — upload original uncompressed file
        doUpload(file);
      };
      reader.readAsDataURL(file);
    } else {
      doUpload(file);
    }
  }-*/;

  private native void setupDragDrop(Element dropZoneEl, Element fileInputEl) /*-{
    var self = this;
    dropZoneEl.addEventListener('dragover', function(e) {
      e.preventDefault(); e.stopPropagation();
      dropZoneEl.classList.add('dragover');
    }, false);
    dropZoneEl.addEventListener('dragleave', function(e) {
      e.preventDefault(); e.stopPropagation();
      dropZoneEl.classList.remove('dragover');
    }, false);
    dropZoneEl.addEventListener('drop', function(e) {
      e.preventDefault(); e.stopPropagation();
      dropZoneEl.classList.remove('dragover');
      if (self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::isUploading) return;
      var files = e.dataTransfer.files;
      if (files && files.length > 0) {
        // Feature-detect whether DataTransfer is constructible before attempting it.
        var canConstruct = false;
        try {
          var testDt = new $wnd.DataTransfer();
          canConstruct = (testDt && testDt.items != null);
        } catch (ex) {
          canConstruct = false;
        }
        if (canConstruct) {
          var dt = new $wnd.DataTransfer();
          for (var i = 0; i < files.length; i++) dt.items.add(files[i]);
          fileInputEl.files = dt.files;
          self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::bumpSelectionNonce()();
          var evt = $doc.createEvent('HTMLEvents');
          evt.initEvent('change', true, false);
          fileInputEl.dispatchEvent(evt);
        } else {
          // DataTransfer is not constructible in this browser; instruct the user to use
          // the click-to-browse button instead.
          self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::showStatus(Ljava/lang/String;Z)("Drag-and-drop is not supported in this browser. Please use the \"Select Files\" button.", true);
        }
      }
    }, false);
  }-*/;

  // ─────────────────────────────────────────────
  //  AttachmentPopupView interface
  // ─────────────────────────────────────────────

  @Override
  public void init(Listener listener) {
    Preconditions.checkState(this.listener == null, "already initialized");
    Preconditions.checkArgument(listener != null, "listener must not be null");
    this.listener = listener;
  }

  @Override
  public void reset() {
    Preconditions.checkState(this.listener != null, "not initialized");
    this.listener = null;
  }

  @Override
  public void show() {
    pendingFiles.clear();
    previewGrid.clear();
    isUploading = false;
    batchFailed = false;
    nextStoreIndex = 0;
    awaitingFileSelection = false;
    stopFileSelectionRecovery();
    initFileStore();
    updateUploadButton();
    spinnerPanel.setVisible(false);
    showStatus("", false);
    selectDisplaySize("medium");
    compressionEnabled = true;
    compressToggleBtn.setText("Compress: ON");
    cancelBtn.setEnabled(true);
    addMoreBtn.setEnabled(true);
    isUploading = false;
    batchFailed = false;
    setElementDisabled(fileUpload.getElement(), false);
    popup.show();
  }

  @Override
  public void hide() {
    awaitingFileSelection = false;
    stopFileSelectionRecovery();
    popup.hide();
  }

  @Override
  public void onShow(PopupEventSourcer source) {
    if (listener != null) listener.onShow();
  }

  @Override
  public void onHide(PopupEventSourcer source) {
    if (listener != null) listener.onHide();
  }

  @Override
  public void setAttachmentId(AttachmentId id) {
    // No-op: IDs are now requested on-demand via listener.requestNewAttachmentId()
  }

  @Override
  public void setWaveRef(String waveRefStr) {
    this.waveRefStr = waveRefStr;
  }
}
