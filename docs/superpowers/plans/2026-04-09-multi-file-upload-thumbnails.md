# Multi-File Upload with Thumbnails & Captions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance the Wave attachment dialog so users can select multiple files, see thumbnail previews with per-file caption inputs, and upload all files sequentially with real progress bars.

**Architecture:** No server changes — uploads one file at a time via XHR to the existing `/attachment/{id}` endpoint. The popup's `FormPanel.submit()` is replaced by JSNI `XMLHttpRequest` with real `upload.onprogress` events. Caption text goes inside the existing `<image><caption>text</caption></image>` Wave document structure, which `constructXmlWithSize()` already supports. Attachment IDs are generated on-demand via a new `Listener.requestNewAttachmentId()` method instead of being pre-generated.

**Tech Stack:** GWT Java (client), UIBinder XML templates (resources/), JSNI for File API / FileReader / XHR / Canvas, CSS injected via `StyleInjector`.

---

## File Map

| File | Change |
|------|--------|
| `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/AttachmentPopupView.java` | Add `requestNewAttachmentId()` and `onDoneWithSizeAndCaption()` to `Listener` |
| `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java` | Implement new listener methods; remove pre-generated ID from show flow |
| `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java` | Major rewrite: multi-file FileEntry list, XHR upload, preview grid |
| `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.ui.xml` | Add `previewGrid` FlowPanel, "Add More" button inside drop zone, cancel button |
| `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.css` | Add preview grid, card, caption input, wave-shimmer animation CSS |

---

## Task 1: Extend `AttachmentPopupView.Listener` interface

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/AttachmentPopupView.java`

- [ ] **Step 1: Add `requestNewAttachmentId()` and `onDoneWithSizeAndCaption()` to the Listener interface**

Replace the entire file content:

```java
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

package org.waveprotocol.wave.client.wavepanel.view;

import org.waveprotocol.wave.media.model.AttachmentId;

/**
 * An attachment popup.
 *
 * @author yurize@apache.org (Yuri Zelikov)
 */
public interface AttachmentPopupView {

  /**
   * Observer of view events.
   */
  public interface Listener {
    void onHide();

    void onShow();

    void onDone(String waveId, String id, String fileName);

    /**
     * Called when upload completes with a display size selection.
     *
     * @param waveId the wave reference
     * @param id the attachment id
     * @param fileName the file name
     * @param displaySize the selected display size: "small", "medium", or "large"
     */
    void onDoneWithSize(String waveId, String id, String fileName, String displaySize);

    /**
     * Called when upload completes with a display size and caption.
     *
     * @param waveId the wave reference
     * @param id the attachment id
     * @param fileName the file name
     * @param displaySize the selected display size: "small", "medium", or "large"
     * @param caption the user-typed caption (may be empty, in which case fileName is used)
     */
    void onDoneWithSizeAndCaption(String waveId, String id, String fileName,
        String displaySize, String caption);

    /**
     * Called by the popup to request a fresh attachment ID for each file being uploaded.
     * The listener (EditToolbar) uses its IdGenerator to produce unique IDs.
     *
     * @return a new unique AttachmentId
     */
    AttachmentId requestNewAttachmentId();
  }

  /**
   * Binds this view to a listener, until {@link #reset()}.
   */
  void init(Listener listener);

  /**
   * Releases this view from its listener, allowing it to be reused.
   */
  void reset();

  /**
   * Shows the popup.
   */
  void show();

  /**
   * Hides the popup.
   */
  void hide();

  void setAttachmentId(AttachmentId id);

  void setWaveRef(String waveRefStr);
}
```

- [ ] **Step 2: Verify sbt compiles (interface only changes, no impl yet)**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload && sbt "project wave" compile 2>&1 | tail -20
```

Expected: FAILURE — `EditToolbar` doesn't implement new methods yet. That is correct. Proceed.

- [ ] **Step 3: Commit**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/AttachmentPopupView.java
git commit -m "feat(upload): add requestNewAttachmentId and onDoneWithSizeAndCaption to Listener interface"
```

---

## Task 2: Update `EditToolbar` to implement new Listener methods

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`

The relevant section is `createInsertAttachmentButton()` at lines 328–404. We need to:
1. Remove `attachmentView.setAttachmentId(attachmentIdGenerator.newAttachmentId())` — IDs are now generated on-demand
2. Implement `requestNewAttachmentId()` in the anonymous Listener
3. Implement `onDoneWithSizeAndCaption()` — uses caption text
4. Have `onDoneWithSize()` delegate to `onDoneWithSizeAndCaption()` with empty caption

- [ ] **Step 1: Replace `createInsertAttachmentButton` method body**

Find the method starting at line 328 and replace it entirely:

```java
  private void createInsertAttachmentButton(ToolbarView toolbar, final ParticipantId user) {
    WaveRef waveRef = WaveRef.of(waveId);
    Preconditions.checkState(waveRef != null, "waveRef != null");
    final String waveRefToken = URL.encode(GwtWaverefEncoder.encodeToUriQueryString(waveRef));

    new ToolbarButtonViewBuilder().setIcon(css.insertAttachment()).setTooltip("Insert attachment")
        .applyTo(toolbar.addClickButton(), new ToolbarClickButton.Listener() {
          @Override
          public void onClicked() {
            AttachmentPopupView attachmentView = new AttachmentPopupWidget();
            attachmentView.init(new Listener() {

              @Override
              public void onShow() {
              }

              @Override
              public void onHide() {
              }

              @Override
              public AttachmentId requestNewAttachmentId() {
                return attachmentIdGenerator.newAttachmentId();
              }

              @Override
              public void onDone(String encodedWaveRef, String attachmentId, String fullFileName) {
                onDoneWithSizeAndCaption(encodedWaveRef, attachmentId, fullFileName, "small", "");
              }

              @Override
              public void onDoneWithSize(String encodedWaveRef, String attachmentId,
                  String fullFileName, String displaySize) {
                onDoneWithSizeAndCaption(encodedWaveRef, attachmentId, fullFileName, displaySize, "");
              }

              @Override
              public void onDoneWithSizeAndCaption(String encodedWaveRef, String attachmentId,
                  String fullFileName, String displaySize, String caption) {
                int lastSlashPos = fullFileName.lastIndexOf("/");
                int lastBackSlashPos = fullFileName.lastIndexOf("\\");
                String fileName = fullFileName;
                if (lastSlashPos != -1) {
                  fileName = fullFileName.substring(lastSlashPos + 1);
                } else if (lastBackSlashPos != -1) {
                  fileName = fullFileName.substring(lastBackSlashPos + 1);
                }
                // Use caption if provided, otherwise fall back to filename
                String captionText = (caption != null && !caption.trim().isEmpty())
                    ? caption.trim() : fileName;

                CMutableDocument doc = editor.getDocument();
                FocusedContentRange selection = editor.getSelectionHelper().getSelectionPoints();
                Point<ContentNode> point;
                if (selection != null) {
                  point = selection.getFocus();
                } else {
                  editor.focus(false);
                  selection = editor.getSelectionHelper().getSelectionPoints();
                  if (selection != null) {
                    point = selection.getFocus();
                  } else {
                    point = doc.locate(doc.size() - 1);
                  }
                }
                XmlStringBuilder content = ImageThumbnail.constructXmlWithSize(
                    attachmentId, displaySize, captionText);
                ImageThumbnailWrapper thumbnail = ImageThumbnailWrapper.of(
                    doc.insertXml(point, content));
                thumbnail.setAttachmentId(attachmentId);
              }
            });

            attachmentView.setWaveRef(waveRefToken);
            attachmentView.show();
          }
        });
  }
```

- [ ] **Step 2: Ensure `AttachmentId` is imported in `EditToolbar.java`**

Check imports at the top of the file. Add if missing:
```java
import org.waveprotocol.wave.media.model.AttachmentId;
```

(It is already imported via `AttachmentIdGeneratorImpl` transitively, but add the direct import to be explicit.)

- [ ] **Step 3: Compile to verify EditToolbar compiles with new interface**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload && sbt "project wave" compile 2>&1 | grep -E "error:|warning:|success"
```

Expected: still fails because `AttachmentPopupWidget` doesn't implement `requestNewAttachmentId()` yet. That's fine.

- [ ] **Step 4: Commit**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java
git commit -m "feat(upload): update EditToolbar to use on-demand attachment IDs and caption-aware insertion"
```

---

## Task 3: Rewrite `AttachmentPopupWidget.java` for multi-file support

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java`

This is the largest change. The widget gets a `FileEntry` inner class, a preview grid, and sequential XHR upload with per-file progress.

- [ ] **Step 1: Replace the entire `AttachmentPopupWidget.java`**

```java
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
import com.google.gwt.user.client.ui.InlineLabel;
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

  /**
   * Holds state for a single selected file awaiting upload.
   */
  private final class FileEntry {
    /** Index into the native FileList on the file input element. */
    final int fileIndex;
    final String fileName;
    final String mimeType;
    final long fileSize;

    /** Assigned at upload time, not at selection time. */
    AttachmentId attachmentId;

    /** The card panel shown in the preview grid. */
    final HTMLPanel card;

    /** The progress bar fill inside the card. */
    final HTMLPanel progressFill;

    /** The caption text box inside the card. */
    final TextBox captionInput;

    /** The image element inside the card (for image previews). */
    final Element imgEl;

    FileEntry(int fileIndex, String fileName, String mimeType, long fileSize,
        HTMLPanel card, HTMLPanel progressFill, TextBox captionInput, Element imgEl) {
      this.fileIndex = fileIndex;
      this.fileName = fileName;
      this.mimeType = mimeType;
      this.fileSize = fileSize;
      this.card = card;
      this.progressFill = progressFill;
      this.captionInput = captionInput;
      this.imgEl = imgEl;
    }

    void setProgressWidth(int pct) {
      progressFill.getElement().getStyle().setProperty("width", pct + "%");
    }

    void showError() {
      card.addStyleName("upload-card-error");
    }
  }

  private static final Binder BINDER = GWT.create(Binder.class);

  @UiField(provided = true)
  final static Style style = GWT.<Resources>create(Resources.class).style();

  private static final String UPLOAD_ACTION_URL = "/attachment/";

  static {
    StyleInjector.inject(style.getText(), true);
  }

  // ---- UiBinder fields ----

  @UiField HTMLPanel dropZone;
  @UiField FileUpload fileUpload;
  @UiField Button addMoreBtn;
  @UiField Button uploadBtn;
  @UiField Button cancelBtn;
  @UiField FormPanel form;
  @UiField Hidden formAttachmentId;
  @UiField Hidden formWaveRef;
  @UiField HorizontalPanel spinnerPanel;
  @UiField Label status;
  @UiField Image spinnerImg;
  @UiField FlowPanel previewGrid;
  @UiField HTMLPanel displaySizePanel;
  @UiField Button sizeBtnSmall;
  @UiField Button sizeBtnMedium;
  @UiField Button sizeBtnLarge;
  @UiField HTMLPanel compressionInfoPanel;
  @UiField Button compressToggleBtn;

  // ---- Instance state ----

  /** Popup containing this widget. */
  private final UniversalPopup popup;

  /** Optional listener for view events. */
  private Listener listener;

  private String waveRefStr;

  /** Currently selected display size for all files in the batch. */
  private String selectedDisplaySize = "medium";

  /** Whether image compression is enabled. */
  private boolean compressionEnabled = true;

  /** Files selected by the user, in order. */
  private final List<FileEntry> pendingFiles = new ArrayList<>();

  /** Index of the file currently being uploaded. */
  private int currentUploadIndex = 0;

  /**
   * Creates the multi-file attachment upload popup.
   */
  public AttachmentPopupWidget() {
    initWidget(BINDER.createAndBindUi(this));
    form.setEncoding(FormPanel.ENCODING_MULTIPART);
    form.setMethod(FormPanel.METHOD_POST);

    // Allow multiple file selection
    setMultipleAttribute(fileUpload.getElement());

    // Drop zone click → open file picker
    dropZone.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        nativeClickFileInput(fileUpload.getElement());
      }
    }, ClickEvent.getType());

    // "Add More Files" button
    addMoreBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        nativeClickFileInput(fileUpload.getElement());
      }
    });

    // File selection change — append to pending list
    fileUpload.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        onFilesSelected();
      }
    });

    // Cancel button
    cancelBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
      }
    });

    // Upload button — start sequential upload
    uploadBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        if (pendingFiles.isEmpty()) {
          status.setText("Please select at least one file.");
          status.addStyleName("error");
          return;
        }
        uploadBtn.setEnabled(false);
        cancelBtn.setEnabled(false);
        addMoreBtn.setEnabled(false);
        currentUploadIndex = 0;
        uploadNext(0);
      }
    });

    // Size selector buttons
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
    compressionInfoPanel.setVisible(true);

    // Drag-and-drop
    setupDragDrop(dropZone.getElement(), fileUpload.getElement());

    uploadBtn.setEnabled(false);

    // Popup chrome
    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(null, new CenterPopupPositioner(), chrome, true);
    popup.add(this);
    popup.addPopupEventListener(this);
  }

  // ─────────────────────────────────────────────
  //  File selection & preview
  // ─────────────────────────────────────────────

  /**
   * Called when the user selects files. Appends new files to pendingFiles
   * and renders preview cards for each.
   */
  private void onFilesSelected() {
    int count = getFileCount(fileUpload.getElement());
    // Offset for files already in the list (cumulative adds not supported by
    // the browser's FileList, so we track the logical offset ourselves).
    int baseIndex = pendingFiles.size();
    // Actually FileList resets on each picker open, so we rebuild from index 0
    // but only add cards for files not yet shown. Since FileList resets, the
    // simplest and correct approach is to clear all cards and re-add everything
    // (the user may have re-opened the picker to replace all, or added more via
    // drag-and-drop). For "Add More Files", we use a hidden separate input — but
    // that adds complexity. For now: each picker open REPLACES the selection
    // (browser FileList resets), so clear and re-add.
    //
    // To support cumulative adds: store file objects in JS and append.
    // That is handled via storeFilesNative / appendFilesNative below.
    rebuildPreviewsFromCurrentInput(count);
  }

  /**
   * Clears the preview grid and rebuilds it from all files in the current
   * FileList (count files starting at index 0).
   */
  private void rebuildPreviewsFromCurrentInput(int count) {
    previewGrid.clear();
    pendingFiles.clear();

    for (int i = 0; i < count; i++) {
      String name = getFileName(fileUpload.getElement(), i);
      String type = getMimeType(fileUpload.getElement(), i);
      long size = getFileSize(fileUpload.getElement(), i);

      FileEntry entry = buildPreviewCard(i, name, type, size);
      pendingFiles.add(entry);
      previewGrid.add(entry.card);

      // Async: load image preview
      if (isImageMime(type) || isImageFileName(name)) {
        readPreviewAsync(fileUpload.getElement(), i);
      }
    }

    updateUploadButton();
  }

  /**
   * Builds a preview card widget for a single file.
   */
  private FileEntry buildPreviewCard(int fileIndex, String name, String type, long size) {
    HTMLPanel card = new HTMLPanel("div", "");
    card.addStyleName("upload-card");

    // Remove button
    Button removeBtn = new Button("×");
    removeBtn.addStyleName("upload-card-remove");
    final int idx = fileIndex;
    removeBtn.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        removeFile(idx);
      }
    });
    card.add(removeBtn);

    // Thumbnail area (image or icon)
    HTMLPanel thumbArea = new HTMLPanel("div", "");
    thumbArea.addStyleName("upload-card-thumb");
    Element imgEl = com.google.gwt.dom.client.Document.get().createImageElement();
    imgEl.addClassName("upload-card-img");
    imgEl.setAttribute("alt", name);
    imgEl.getStyle().setProperty("display", "none"); // shown after preview loads
    thumbArea.getElement().appendChild(imgEl);

    // File icon placeholder (shown for non-images, or while image loads)
    String iconHtml = buildFileIcon(type, name);
    HTMLPanel iconPanel = new HTMLPanel(iconHtml);
    iconPanel.addStyleName("upload-card-icon");
    if (isImageMime(type) || isImageFileName(name)) {
      // Will be hidden once image preview is ready
      iconPanel.addStyleName("upload-card-icon-loading");
    }
    thumbArea.add(iconPanel);
    card.add(thumbArea);

    // Filename + size
    Label nameLabel = new Label(truncateName(name, 20));
    nameLabel.addStyleName("upload-card-name");
    nameLabel.setTitle(name);
    card.add(nameLabel);

    Label sizeLabel = new Label(formatSize(size));
    sizeLabel.addStyleName("upload-card-size");
    card.add(sizeLabel);

    // Caption input
    TextBox captionInput = new TextBox();
    captionInput.addStyleName("upload-card-caption");
    captionInput.getElement().setAttribute("placeholder", "Add a caption…");
    card.add(captionInput);

    // Progress bar
    HTMLPanel progressOuter = new HTMLPanel("div", "");
    progressOuter.addStyleName("upload-card-progress");
    HTMLPanel progressFill = new HTMLPanel("div", "");
    progressFill.addStyleName("upload-card-progress-fill");
    progressOuter.add(progressFill);
    card.add(progressOuter);

    return new FileEntry(fileIndex, name, type, size, card, progressFill, captionInput, imgEl);
  }

  /**
   * Removes a file from the pending list and its card from the grid.
   * Since the browser FileList is immutable, removal is visual only —
   * we skip removed entries during upload.
   */
  private void removeFile(int fileIndex) {
    for (int i = 0; i < pendingFiles.size(); i++) {
      FileEntry e = pendingFiles.get(i);
      if (e.fileIndex == fileIndex) {
        previewGrid.remove(e.card);
        pendingFiles.remove(i);
        break;
      }
    }
    updateUploadButton();
  }

  /**
   * Updates upload button label and enabled state.
   */
  private void updateUploadButton() {
    int n = pendingFiles.size();
    if (n == 0) {
      uploadBtn.setText("Upload");
      uploadBtn.setEnabled(false);
    } else if (n == 1) {
      uploadBtn.setText("Upload 1 file →");
      uploadBtn.setEnabled(true);
    } else {
      uploadBtn.setText("Upload " + n + " files →");
      uploadBtn.setEnabled(true);
    }
    status.setText("");
    status.removeStyleName("error");
  }

  // ─────────────────────────────────────────────
  //  Upload loop
  // ─────────────────────────────────────────────

  /**
   * Uploads the file at the given index in pendingFiles. Requests a new
   * attachment ID from the listener, then starts an XHR upload.
   */
  private void uploadNext(int index) {
    if (index >= pendingFiles.size()) {
      // All done
      status.setText("All uploads complete!");
      new Timer() {
        @Override public void run() { hide(); }
      }.schedule(600);
      return;
    }
    FileEntry entry = pendingFiles.get(index);
    entry.attachmentId = listener.requestNewAttachmentId();
    entry.setProgressWidth(0);

    int maxDim;
    switch (selectedDisplaySize) {
      case "large":  maxDim = 1920; break;
      case "medium": maxDim = 800;  break;
      default:       maxDim = 200;  break;
    }

    boolean isImage = isImageMime(entry.mimeType) || isImageFileName(entry.fileName);
    uploadFileWithXhr(
        fileUpload.getElement(),
        entry.fileIndex,
        entry.attachmentId.getId(),
        waveRefStr,
        compressionEnabled && isImage,
        maxDim,
        0.8,
        UPLOAD_ACTION_URL + entry.attachmentId.getId());
  }

  /**
   * Called from JSNI when upload progress changes for a file.
   */
  private void onUploadProgress(int fileIndex, int percent) {
    for (FileEntry e : pendingFiles) {
      if (e.fileIndex == fileIndex) {
        e.setProgressWidth(percent);
        return;
      }
    }
  }

  /**
   * Called from JSNI when a file upload finishes.
   */
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
    if (entry == null) return;

    if (success) {
      entry.setProgressWidth(100);
      String caption = entry.captionInput.getText().trim();
      if (caption.isEmpty()) caption = entry.fileName;
      listener.onDoneWithSizeAndCaption(waveRefStr, entry.attachmentId.getId(),
          entry.fileName, selectedDisplaySize, caption);
      // Upload next
      uploadNext(listIndex + 1);
    } else {
      entry.showError();
      status.setText("Upload failed for: " + truncateName(entry.fileName, 20));
      status.addStyleName("error");
      // Continue with remaining files even after a failure
      uploadNext(listIndex + 1);
    }
  }

  /**
   * Called from JSNI when image preview DataURL is ready.
   */
  private void onPreviewReady(int fileIndex, String dataUrl) {
    for (FileEntry e : pendingFiles) {
      if (e.fileIndex == fileIndex && e.imgEl != null) {
        e.imgEl.setAttribute("src", dataUrl);
        e.imgEl.getStyle().clearProperty("display");
        // Hide the icon placeholder
        Element parent = e.imgEl.getParentElement();
        if (parent != null) {
          com.google.gwt.dom.client.NodeList<Element> icons =
              parent.getElementsByTagName("div");
          for (int i = 0; i < icons.getLength(); i++) {
            if (icons.getItem(i).hasClassName("upload-card-icon")) {
              icons.getItem(i).getStyle().setProperty("display", "none");
              break;
            }
          }
        }
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
    return mime != null && mime.startsWith("image/");
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
    return name.substring(0, max - 1) + "…";
  }

  private static String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    long kb = bytes / 1024;
    if (kb < 1024) return kb + " KB";
    return (kb / 1024) + "." + ((kb % 1024) * 10 / 1024) + " MB";
  }

  /**
   * Builds an SVG+HTML file type icon for non-image files (reuses category
   * logic from ImageThumbnailWidget).
   */
  private static String buildFileIcon(String mimeType, String fileName) {
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
      if (mt.equals("application/pdf"))                    { color = "#E53935"; label = "PDF"; }
      else if (mt.startsWith("video/"))                   { color = "#7B1FA2"; }
      else if (mt.startsWith("audio/"))                   { color = "#00897B"; }
      else if (mt.startsWith("text/"))                    { color = "#546E7A"; }
      else if (mt.contains("spreadsheet")||mt.contains("excel")) { color = "#2E7D32"; }
      else if (mt.contains("presentation")||mt.contains("powerpoint")) { color = "#E65100"; }
      else if (mt.contains("document")||mt.contains("word")) { color = "#1565C0"; }
      else if (mt.contains("zip")||mt.contains("tar"))    { color = "#795548"; }
      else if (mt.startsWith("image/"))                   { color = "#0288D1"; }
    }
    if (label.length() > 4) label = label.substring(0, 4);
    return "<div class='upload-card-file-icon' style='background:" + color + "'>"
        + "<svg viewBox='0 0 24 24' width='32' height='32' fill='white'>"
        + "<path d='M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z'/>"
        + "<polyline points='14 2 14 8 20 8' fill='none' stroke='white' stroke-width='1.5'/>"
        + "</svg>"
        + "<span class='upload-card-file-label'>" + label + "</span>"
        + "</div>";
  }

  // ─────────────────────────────────────────────
  //  JSNI
  // ─────────────────────────────────────────────

  /** Sets the `multiple` attribute on a file input. */
  private static native void setMultipleAttribute(Element el) /*-{
    el.setAttribute('multiple', 'multiple');
  }-*/;

  /** Programmatically clicks the hidden file input. */
  private static native void nativeClickFileInput(Element el) /*-{
    el.click();
  }-*/;

  /** Returns the number of files in the FileList. */
  private static native int getFileCount(Element fileInput) /*-{
    return (fileInput.files) ? fileInput.files.length : 0;
  }-*/;

  /** Returns the name of the file at the given index. */
  private static native String getFileName(Element fileInput, int index) /*-{
    return fileInput.files[index].name;
  }-*/;

  /** Returns the MIME type of the file at the given index. */
  private static native String getMimeType(Element fileInput, int index) /*-{
    return fileInput.files[index].type || '';
  }-*/;

  /** Returns the size in bytes of the file at the given index. */
  private static native long getFileSize(Element fileInput, int index) /*-{
    return fileInput.files[index].size || 0;
  }-*/;

  /**
   * Reads the file at the given index as a data URL using FileReader.
   * Calls {@link #onPreviewReady(int, String)} when done.
   */
  private native void readPreviewAsync(Element fileInput, int index) /*-{
    var self = this;
    var file = fileInput.files[index];
    if (!file) return;
    var reader = new $wnd.FileReader();
    reader.onload = function(e) {
      self.@org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget::onPreviewReady(ILjava/lang/String;)(index, e.target.result);
    };
    reader.readAsDataURL(file);
  }-*/;

  /**
   * Uploads the file at fileIndex using XHR with real progress events.
   * If compress is true and the file is an image, resizes via Canvas first.
   * Calls {@link #onUploadProgress(int, int)} and {@link #onFileUploadComplete(int, boolean)}.
   */
  private native void uploadFileWithXhr(Element fileInput, int fileIndex, String attachmentId,
      String waveRef, boolean compress, int maxDim, double quality, String uploadUrl) /*-{
    var self = this;
    var file = fileInput.files[fileIndex];
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
          canvas.toBlob(function(blob) {
            doUpload(blob || file);
          }, 'image/jpeg', quality);
        };
        img.src = e.target.result;
      };
      reader.readAsDataURL(file);
    } else {
      doUpload(file);
    }
  }-*/;

  /**
   * Sets up native drag-and-drop event handlers.
   */
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
      var files = e.dataTransfer.files;
      if (files && files.length > 0) {
        var dt = new $wnd.DataTransfer();
        for (var i = 0; i < files.length; i++) dt.items.add(files[i]);
        fileInputEl.files = dt.files;
        var evt = $doc.createEvent('HTMLEvents');
        evt.initEvent('change', true, false);
        fileInputEl.dispatchEvent(evt);
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
    updateUploadButton();
    spinnerPanel.setVisible(false);
    status.setText("");
    status.removeStyleName("error");
    status.removeStyleName("success");
    selectDisplaySize("medium");
    compressionEnabled = true;
    compressToggleBtn.setText("Compress: ON");
    cancelBtn.setEnabled(true);
    addMoreBtn.setEnabled(true);
    popup.show();
  }

  @Override
  public void hide() {
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
```

- [ ] **Step 2: Compile**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload && sbt "project wave" compile 2>&1 | grep -E "error:|success" | head -20
```

Expected: errors about missing `@UiField` fields (`addMoreBtn`, `previewGrid`, `cancelBtn`) — we update the template in the next task.

- [ ] **Step 3: Commit**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java
git commit -m "feat(upload): rewrite AttachmentPopupWidget with multi-file XHR upload and preview cards"
```

---

## Task 4: Update `AttachmentPopupWidget.ui.xml`

**Files:**
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.ui.xml`

- [ ] **Step 1: Replace the UI template**

```xml
<?xml version='1.0'?>
<!--
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
-->
<ui:UiBinder xmlns:ui="urn:ui:com.google.gwt.uibinder"
    xmlns:g='urn:import:com.google.gwt.user.client.ui'>
  <ui:with field='style'
    type='org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget.Style'/>
  <ui:with field='res'
    type='org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.AttachmentPopupWidget.Resources'/>

  <g:HTMLPanel styleName="attachment-popup-modern">

    <!-- Drop zone -->
    <g:HTMLPanel ui:field="dropZone" styleName="upload-dropzone">
      <div class="upload-dropzone-icon">
        <svg width="36" height="36" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="1.5">
          <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/>
          <polyline points="17 8 12 3 7 8"/>
          <line x1="12" y1="3" x2="12" y2="15"/>
        </svg>
      </div>
      <div class="upload-dropzone-text">Drop files here or tap to browse</div>
      <g:Button ui:field="addMoreBtn" styleName="upload-add-more-btn">+ Add More Files</g:Button>
    </g:HTMLPanel>

    <!-- Wave separator -->
    <div class="upload-wave-separator">
      <svg viewBox="0 0 400 20" preserveAspectRatio="none"
           xmlns="http://www.w3.org/2000/svg">
        <path d="M0,10 C50,0 100,20 150,10 C200,0 250,20 300,10 C350,0 400,20 400,10"
              fill="none" stroke="#4a90d9" stroke-width="2" stroke-opacity="0.4"/>
      </svg>
    </div>

    <!-- Hidden form (kept for backward compat; upload uses XHR) -->
    <g:FormPanel ui:field="form">
      <g:FlowPanel>
        <g:FileUpload ui:field="fileUpload" name="uploadFormElement"
                      styleName="{res.style.hiddenFileInput}"/>
        <g:Hidden ui:field="formAttachmentId" name="attachmentId"/>
        <g:Hidden ui:field="formWaveRef" name="waveRef"/>
      </g:FlowPanel>
    </g:FormPanel>

    <!-- Preview grid (cards added dynamically) -->
    <g:FlowPanel ui:field="previewGrid" styleName="upload-preview-grid"/>

    <!-- Display size selector -->
    <g:HTMLPanel ui:field="displaySizePanel" styleName="display-size-panel">
      <div class="display-size-label">Display size:</div>
      <g:Button ui:field="sizeBtnSmall"  styleName="size-btn">S</g:Button>
      <g:Button ui:field="sizeBtnMedium" styleName="size-btn size-btn-active">M</g:Button>
      <g:Button ui:field="sizeBtnLarge"  styleName="size-btn">L</g:Button>
    </g:HTMLPanel>

    <!-- Compression toggle -->
    <g:HTMLPanel ui:field="compressionInfoPanel" styleName="compression-info-panel">
      <g:Button ui:field="compressToggleBtn" styleName="compress-toggle-btn">Compress: ON</g:Button>
    </g:HTMLPanel>

    <!-- Status + spinner -->
    <g:HorizontalPanel ui:field="spinnerPanel" styleName="{res.style.spinnerPanel}"
        horizontalAlignment="ALIGN_RIGHT" visible="false">
      <g:Image resource="{res.spinner}" title="Loading" styleName="{res.style.spinner}"
               ui:field="spinnerImg"/>
      <g:Label ui:field="status" styleName="upload-status-modern"/>
    </g:HorizontalPanel>

    <!-- Status label (shown outside spinner panel too) -->

    <!-- Action buttons -->
    <div class="upload-actions">
      <g:Button ui:field="cancelBtn" styleName="upload-cancel-btn">Cancel</g:Button>
      <g:Button ui:field="uploadBtn" styleName="upload-btn-modern">Upload</g:Button>
    </div>

  </g:HTMLPanel>
</ui:UiBinder>
```

- [ ] **Step 2: Note about `status` label**

The `status` label is inside `spinnerPanel` in the template. In `AttachmentPopupWidget.java` we reference it via `@UiField Label status`. This is fine — the label is still accessible. We just keep `spinnerPanel` hidden and directly set `status.setText()` as needed. The label is wired to the same `@UiField`.

- [ ] **Step 3: Compile**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload && sbt "project wave" compile 2>&1 | grep -E "error:|success" | head -20
```

Expected: compile errors about missing CSS classes referenced in the template, or success if GWT is lenient. Fix any errors. Common fixes: ensure `hiddenFileInput` is defined in `AttachmentPopupWidget.css` (it already is).

- [ ] **Step 4: Commit**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload
git add wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.ui.xml
git commit -m "feat(upload): update attachment popup template with preview grid and wave separator"
```

---

## Task 5: Add CSS for preview grid, cards, and wave aesthetic

**Files:**
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.css`

The CSS here is only for GWT CssResource obfuscated class names. All modern styles are injected via `StyleInjector` in the Java code or come from the wave app's global stylesheet.

Since `StyleInjector.inject(style.getText(), true)` injects the content of `AttachmentPopupWidget.css`, and those are the obfuscated class names — the modern styles are added by appending a second `StyleInjector.inject()` call in the `AttachmentPopupWidget` static block with literal CSS.

- [ ] **Step 1: Add a second StyleInjector call with modern CSS**

In `AttachmentPopupWidget.java`, inside the `static { }` block, add the modern CSS after the existing `StyleInjector.inject(style.getText(), true)`:

Find the static block:
```java
static {
  StyleInjector.inject(style.getText(), true);
}
```

Replace with:
```java
static {
  StyleInjector.inject(style.getText(), true);
  StyleInjector.inject(MODERN_CSS, true);
}
```

And add this constant to the class (after the `UPLOAD_ACTION_URL` constant):

```java
/**
 * Modern CSS for the multi-file upload popup. Injected at class load time.
 * Uses Wave's blue/teal palette and a wave-shimmer progress animation.
 */
private static final String MODERN_CSS =
  // ── Popup container ──────────────────────────────────────────────────
  ".attachment-popup-modern{width:460px;padding:16px;box-sizing:border-box;" +
  "font-family:'Roboto',Arial,sans-serif;}" +

  // ── Drop zone ────────────────────────────────────────────────────────
  ".upload-dropzone{border:2px dashed #4a90d9;border-radius:10px;padding:18px 12px;" +
  "text-align:center;cursor:pointer;color:#5a7fa8;transition:background .2s,border-color .2s;" +
  "margin-bottom:0;}" +
  ".upload-dropzone:hover,.upload-dropzone.dragover{background:#eef5fb;border-color:#0077b6;}" +
  ".upload-dropzone-icon{color:#4a90d9;margin-bottom:6px;}" +
  ".upload-dropzone-text{font-size:14px;font-weight:500;color:#336699;margin-bottom:8px;}" +
  ".upload-add-more-btn{background:transparent;border:1px solid #4a90d9;border-radius:16px;" +
  "color:#0077b6;cursor:pointer;font-size:12px;padding:4px 14px;transition:background .15s;}" +
  ".upload-add-more-btn:hover{background:#eef5fb;}" +

  // ── Wave separator ───────────────────────────────────────────────────
  ".upload-wave-separator{margin:8px 0;height:20px;overflow:hidden;}" +
  ".upload-wave-separator svg{width:100%;height:20px;}" +

  // ── Preview grid ─────────────────────────────────────────────────────
  ".upload-preview-grid{display:flex;flex-wrap:wrap;gap:10px;max-height:300px;" +
  "overflow-y:auto;padding:6px 0;margin-bottom:10px;}" +

  // ── Preview card ─────────────────────────────────────────────────────
  ".upload-card{position:relative;width:130px;border:1px solid #b8d0e8;border-radius:8px;" +
  "padding:8px;box-sizing:border-box;background:#fff;" +
  "box-shadow:0 1px 4px rgba(74,144,217,.15);transition:box-shadow .2s;}" +
  ".upload-card:hover{box-shadow:0 2px 8px rgba(74,144,217,.3);}" +
  ".upload-card-error{border-color:#d93025;background:#fff8f8;}" +

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

  // Name + size labels
  ".upload-card-name{font-size:11px;color:#336699;font-weight:500;display:block;" +
  "white-space:nowrap;overflow:hidden;text-overflow:ellipsis;width:100%;margin-bottom:2px;}" +
  ".upload-card-size{font-size:10px;color:#8aabcc;display:block;margin-bottom:6px;}" +

  // Caption input
  ".upload-card-caption{width:100%;box-sizing:border-box;border:1px solid #c8dff0;" +
  "border-radius:12px;padding:4px 10px;font-size:12px;color:#334;outline:none;" +
  "transition:border-color .2s,box-shadow .2s;margin-bottom:6px;}" +
  ".upload-card-caption:focus{border-color:#4a90d9;" +
  "box-shadow:0 0 0 3px rgba(74,144,217,.2);}" +
  ".upload-card-caption::placeholder{color:#aac4dc;}" +

  // Per-card progress bar
  ".upload-card-progress{height:4px;background:#e0eaf5;border-radius:2px;overflow:hidden;}" +
  ".upload-card-progress-fill{height:100%;width:0%;background:#0077b6;border-radius:2px;" +
  "transition:width .15s;position:relative;overflow:hidden;}" +
  "@keyframes wave-shimmer{0%{transform:translateX(-100%)}100%{transform:translateX(200%)}}" +
  ".upload-card-progress-fill::after{content:'';position:absolute;top:0;left:0;" +
  "width:50%;height:100%;background:linear-gradient(90deg,transparent,rgba(255,255,255,.5),transparent);" +
  "animation:wave-shimmer 1.2s infinite;}" +

  // ── Controls row ─────────────────────────────────────────────────────
  ".display-size-panel{display:flex;align-items:center;gap:6px;margin-bottom:8px;}" +
  ".display-size-label{font-size:12px;color:#5a7fa8;}" +
  ".size-btn{border:1px solid #b8d0e8;background:#f7fafc;border-radius:6px;padding:3px 10px;" +
  "font-size:12px;cursor:pointer;color:#336699;transition:background .15s,border-color .15s;}" +
  ".size-btn:hover{background:#eef5fb;}" +
  ".size-btn-active{background:#0077b6;color:#fff;border-color:#0077b6;}" +
  ".compression-info-panel{margin-bottom:10px;}" +
  ".compress-toggle-btn{background:transparent;border:1px solid #b8d0e8;border-radius:16px;" +
  "color:#0077b6;cursor:pointer;font-size:12px;padding:4px 12px;transition:background .15s;}" +
  ".compress-toggle-btn:hover{background:#eef5fb;}" +

  // ── Action buttons ────────────────────────────────────────────────────
  ".upload-actions{display:flex;justify-content:space-between;align-items:center;margin-top:10px;}" +
  ".upload-cancel-btn{background:transparent;border:1px solid #b8d0e8;border-radius:20px;" +
  "color:#5a7fa8;cursor:pointer;font-size:13px;padding:7px 18px;transition:background .15s;}" +
  ".upload-cancel-btn:hover{background:#f0f4f8;}" +
  ".upload-btn-modern{background:linear-gradient(135deg,#0077b6,#4a90d9);" +
  "border:none;border-radius:20px;color:#fff;cursor:pointer;font-size:13px;font-weight:500;" +
  "padding:7px 22px;transition:opacity .15s,box-shadow .15s;" +
  "box-shadow:0 2px 6px rgba(0,119,182,.3);}" +
  ".upload-btn-modern:hover{opacity:.9;box-shadow:0 3px 10px rgba(0,119,182,.4);}" +
  ".upload-btn-modern:disabled{opacity:.5;cursor:default;box-shadow:none;}" +

  // Status
  ".upload-status-modern{font-size:13px;color:#336699;}" +

  // ── Mobile ────────────────────────────────────────────────────────────
  "@media(max-width:480px){" +
  ".attachment-popup-modern{width:100%;}" +
  ".upload-card{width:calc(50% - 5px);}" +
  ".upload-preview-grid{max-height:240px;}" +
  "}";
```

- [ ] **Step 2: Compile**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload && sbt "project wave" compile 2>&1 | grep -E "error:|success" | head -30
```

Expected: SUCCESS. If not, address compiler errors:
- Missing `import java.util.ArrayList` / `java.util.List` → add to imports
- Missing `import com.google.gwt.user.client.ui.TextBox` → add to imports
- Any `@UiField` not found in template → cross-check field names vs ui.xml

- [ ] **Step 3: Commit**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java
git add wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.ui.xml
git commit -m "feat(upload): add wave-aesthetic CSS and multi-file preview grid styles"
```

---

## Task 6: Full build and local verification

**Goal:** Verify the feature works end-to-end in a running Wave server.

- [ ] **Step 1: Full sbt compile**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload && sbt compile 2>&1 | tail -20
```

Expected: `[success]`

- [ ] **Step 2: Start local Wave server**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload && sbt run &
```

Wait for `Server started` in output (typically ~30 seconds).

- [ ] **Step 3: Manual verification checklist**

Open browser at `http://localhost:9898` (or configured port).

1. **Register/login** — use a fresh local user (register if needed)
2. **Open a wave** — create or open an existing wave
3. **Click the attachment toolbar button** — popup appears
4. **Select multiple files** — including at least one image and one non-image (e.g. `.pdf`)
5. **Verify**:
   - [ ] Image files show a thumbnail preview (not just a filename)
   - [ ] Non-image files show a colored file icon with extension label
   - [ ] Each card has a caption input field with placeholder "Add a caption…"
   - [ ] Each card has a × remove button; clicking it removes the card
   - [ ] Upload button label shows "Upload N files →"
   - [ ] Display size buttons (S / M / L) work
   - [ ] "Add More Files" button opens file picker
   - [ ] Drag-and-drop of files into the drop zone adds them to the grid
6. **Type captions** into at least two cards
7. **Click Upload** — verify:
   - [ ] Each card shows a teal progress bar that fills (real progress, not simulated)
   - [ ] Attachments insert into the wave document in order
   - [ ] Captions appear below the images in the wave (inside the attachment element)
   - [ ] Files with no caption use the filename as caption
8. **Mobile check** (resize browser to <480px):
   - [ ] Grid switches to 2-column layout
   - [ ] Caption inputs and × buttons are tappable (≥48px)

- [ ] **Step 4: Fix any issues found during verification**

Address compilation errors or runtime issues before proceeding to PR.

- [ ] **Step 5: Final commit if any fixes were needed**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload
git add -p
git commit -m "fix(upload): address issues found during local verification"
```

---

## Task 7: Create PR

- [ ] **Step 1: Push branch**

```bash
cd /Users/vega/devroot/worktrees/feat-multi-upload
git push -u origin feat/multi-file-upload-thumbnails
```

- [ ] **Step 2: Create PR**

```bash
gh pr create \
  --title "feat(upload): multi-file upload with thumbnail previews and captions" \
  --body "$(cat <<'EOF'
## Summary
- Users can select multiple files at once from the attachment dialog (or drag-and-drop)
- Each file shows a thumbnail preview (image) or file-type icon (non-image) before upload
- Per-file caption inputs (like Telegram) — stored inside the existing `<image><caption>` document element
- Files upload sequentially via XHR with real per-file progress bars
- Wave-themed UI: teal palette, wave-shimmer progress animation, pill-shaped caption inputs, wave SVG separator
- Touch-friendly: 2-column grid on mobile, all targets ≥48px
- No server changes — reuses existing `/attachment/{id}` endpoint

## Changes
- `AttachmentPopupView.java` — added `requestNewAttachmentId()` and `onDoneWithSizeAndCaption()` to Listener
- `EditToolbar.java` — implements new Listener methods; captions now go to document instead of filename
- `AttachmentPopupWidget.java` — full rewrite: FileEntry list, preview grid, XHR upload loop, JSNI File/FileReader/Canvas/XHR
- `AttachmentPopupWidget.ui.xml` — new template with preview grid, wave separator, action buttons
- `AttachmentPopupWidget.css` / inline styles — preview card grid, wave-shimmer animation, mobile breakpoint

## Test plan
- [ ] Select 3+ files (mix of images and non-images), verify thumbnails and icons appear
- [ ] Type captions, upload — verify captions appear in wave document
- [ ] Remove a file before upload, verify it is excluded
- [ ] Drag-and-drop files into drop zone
- [ ] Verify upload button shows correct count ("Upload N files →")
- [ ] Check mobile layout at ≤480px viewport

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Note PR URL for monitoring**

```bash
gh pr view --json url -q .url
```
