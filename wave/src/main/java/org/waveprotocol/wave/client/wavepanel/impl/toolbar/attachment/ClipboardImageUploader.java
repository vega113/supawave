/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Timer;
import com.google.gwt.http.client.URL;

import org.waveprotocol.wave.client.doodad.attachment.ImageThumbnail;
import org.waveprotocol.wave.client.editor.EditorContextAdapter;
import org.waveprotocol.wave.client.editor.ImagePasteHandler;
import org.waveprotocol.wave.client.editor.content.CMutableDocument;
import org.waveprotocol.wave.client.editor.content.ContentNode;
import org.waveprotocol.wave.client.editor.content.ContentRange;
import org.waveprotocol.wave.client.editor.selection.content.SelectionHelper;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.media.model.AttachmentIdGenerator;
import org.waveprotocol.wave.model.document.indexed.LocationMapper;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.util.AttachmentUploadMobileSupport;
import org.waveprotocol.wave.model.document.util.XmlStringBuilder;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.GwtWaverefEncoder;

/**
 * Handles image paste events by uploading clipboard image data to the
 * attachment server and inserting the result inline at the paste cursor.
 *
 * <p>Lifecycle: created once by {@link
 * org.waveprotocol.wave.client.wavepanel.impl.toolbar.EditToolbar} and
 * registered on the {@link EditorContextAdapter} so it is active for any
 * editor that becomes focused.
 */
public final class ClipboardImageUploader implements ImagePasteHandler {

  private final AttachmentIdGenerator idGenerator;
  private final WaveId waveId;
  private final EditorContextAdapter editor;

  /** Progress indicator element shown during upload. May be null. */
  private Element progressIndicator;

  /** Number of XHR uploads currently in progress. */
  private int inFlightCount;

  /**
   * @param idGenerator generates unique attachment IDs
   * @param waveId      the current wave (used to compute the wave ref token)
   * @param editor      the editor context adapter (provides live document + selection)
   */
  public ClipboardImageUploader(AttachmentIdGenerator idGenerator,
      WaveId waveId, EditorContextAdapter editor) {
    this.idGenerator = idGenerator;
    this.waveId = waveId;
    this.editor = editor;
  }

  @Override
  public boolean handleImagePaste(NativeEvent nativeEvent) {
    if (!hasClipboardImage(nativeEvent)) {
      return false;
    }

    CMutableDocument doc = editor.getDocument();
    if (doc == null) {
      return false;
    }
    SelectionHelper sel = editor.getSelectionHelper();

    // Capture insert position and selection end as live Points.  Live Points
    // hold ContentNode references that remain valid as the document is edited,
    // so the positions stay correct even if the user types during the async
    // XHR.  Crucially, the selection is NOT deleted here: deferring deletion
    // to onUploadSuccess means no content — plain text or rich — is lost if
    // the upload fails.
    final Point<ContentNode> insertPoint = captureInsertPoint(sel, doc);
    final Point<ContentNode> selectionEndPoint = captureSelectionEndPoint(sel);

    AttachmentId attachmentId = idGenerator.newAttachmentId();
    String waveRefToken = URL.encode(
        GwtWaverefEncoder.encodeToUriQueryString(WaveRef.of(waveId)));

    showProgressIndicator();

    // Capture the document reference now; the editor adapter may switch to a
    // different blip before the async upload completes.
    final CMutableDocument capturedDoc = doc;
    final String attachmentIdStr = attachmentId.getId();
    startXhrUpload(nativeEvent, attachmentIdStr, waveRefToken,
        () -> onUploadSuccess(capturedDoc, attachmentIdStr, insertPoint, selectionEndPoint),
        () -> onUploadFailure());

    return true; // consumed — suppress text paste
  }

  // ---------------------------------------------------------------------------
  // Upload callbacks (called from JSNI on the GWT event thread)
  // ---------------------------------------------------------------------------

  private void onUploadSuccess(CMutableDocument doc, String attachmentId,
      Point<ContentNode> insertPoint, Point<ContentNode> selectionEndPoint) {
    hideProgressIndicator();

    @SuppressWarnings("unchecked")
    LocationMapper<ContentNode> mapper = (LocationMapper<ContentNode>) doc;

    // Compute current offsets from the live Points (they may have shifted due
    // to edits made during the async XHR).  If the paste anchor was removed
    // from the document while the upload was in flight (e.g., the user deleted
    // the blip), getLocation will throw.  In that case, the image was uploaded
    // server-side but cannot be inserted client-side.
    int start;
    try {
      start = mapper.getLocation(insertPoint);
    } catch (RuntimeException e) {
      showErrorToast("Image uploaded but could not be inserted.");
      return;
    }

    // Delete selected content now that upload succeeded.  Using live Points
    // means positions remain valid despite edits made during the XHR.
    if (selectionEndPoint != null) {
      int end = mapper.getLocation(selectionEndPoint);
      if (end > start) {
        doc.deleteRange(start, end);
      }
    }

    // Re-locate after potential deletion so the insertion point is fresh.
    Point<ContentNode> safeInsertPoint = mapper.locate(start);

    XmlStringBuilder xml = ImageThumbnail.constructXmlWithSize(
        attachmentId, ImageThumbnail.DISPLAY_SIZE_MEDIUM, "pasted-image.png");
    doc.insertXml(safeInsertPoint, xml);
  }

  private void onUploadFailure() {
    hideProgressIndicator();
    // The selection was never deleted (deletion is deferred to onUploadSuccess),
    // so no content restoration is needed here.
    showErrorToast("Image upload failed.");
  }

  // ---------------------------------------------------------------------------
  // Progress indicator (lightweight DOM element, no GWT widget overhead)
  // ---------------------------------------------------------------------------

  private void showProgressIndicator() {
    inFlightCount++;
    if (progressIndicator != null) {
      return; // already showing; counter bumped above
    }
    progressIndicator = Document.get().createDivElement();
    progressIndicator.setInnerHTML(
        "<span style=\"display:inline-block;width:14px;height:14px;"
        + "border:2px solid #fff;border-top-color:#4285f4;"
        + "border-radius:50%;animation:spin .7s linear infinite;"
        + "vertical-align:middle;margin-right:6px\"></span>"
        + "Uploading pasted image\u2026");
    applyToastStyle(progressIndicator, "#323232");
    injectSpinnerKeyframes();
    Document.get().getBody().appendChild(progressIndicator);
  }

  private void hideProgressIndicator() {
    if (--inFlightCount <= 0) {
      inFlightCount = 0;
      if (progressIndicator != null) {
        progressIndicator.removeFromParent();
        progressIndicator = null;
      }
    }
  }

  private void showErrorToast(String message) {
    final Element toast = Document.get().createDivElement();
    toast.setInnerText(message);
    applyToastStyle(toast, "#c62828");
    Document.get().getBody().appendChild(toast);
    new Timer() {
      @Override
      public void run() {
        toast.removeFromParent();
      }
    }.schedule(3000);
  }

  private static void applyToastStyle(Element el, String background) {
    el.getStyle().setProperty("position", "fixed");
    el.getStyle().setProperty("bottom", "20px");
    el.getStyle().setProperty("right", "20px");
    el.getStyle().setProperty("background", background);
    el.getStyle().setProperty("color", "#fff");
    el.getStyle().setProperty("padding", "8px 14px");
    el.getStyle().setProperty("font", "13px/20px sans-serif");
    el.getStyle().setProperty("borderRadius", "4px");
    el.getStyle().setProperty("boxShadow", "0 2px 8px rgba(0,0,0,.35)");
    el.getStyle().setProperty("zIndex", "2147483647");
  }

  private static native void injectSpinnerKeyframes() /*-{
    if ($wnd.__waveSpinnerInjected) return;
    $wnd.__waveSpinnerInjected = true;
    var style = $doc.createElement('style');
    style.textContent = '@keyframes spin { to { transform: rotate(360deg); } }';
    $doc.head.appendChild(style);
  }-*/;

  // ---------------------------------------------------------------------------
  // JSNI helpers
  // ---------------------------------------------------------------------------

  /**
   * Checks whether the paste event's clipboardData contains at least one image item.
   */
  private static native boolean hasClipboardImage(NativeEvent event) /*-{
    var data = event.clipboardData;
    if (!data) return false;
    var items = data.items;
    if (items) {
      for (var i = 0; i < items.length; i++) {
        if (items[i].type && items[i].type.match(/^image\//)) return true;
      }
    }
    var files = data.files;
    if (files) {
      for (var j = 0; j < files.length; j++) {
        if (@org.waveprotocol.wave.model.util.AttachmentUploadMobileSupport::isImageMime(Ljava/lang/String;)(files[j].type || null)) {
          return true;
        }
      }
    }
    return false;
  }-*/;

  /**
   * Extracts the first image blob from clipboardData and POSTs it to the
   * attachment server via XMLHttpRequest. Calls {@code successCb} on HTTP 200/201,
   * {@code failureCb} otherwise.
   *
   * <p>FormData fields sent:
   * <ul>
   *   <li>{@code attachmentId} — attachment id (matches URL path)</li>
   *   <li>{@code waveRef} — URL-encoded wave ref token</li>
   *   <li>{@code uploadFormElement} — image blob with filename {@code pasted-image.png}</li>
   * </ul>
   */
  private native void startXhrUpload(NativeEvent event,
      String attachmentId, String waveRef,
      Runnable successCb, Runnable failureCb) /*-{
    var data = event.clipboardData;
    if (!data) {
      failureCb.@java.lang.Runnable::run()();
      return;
    }
    var items = data.items;
    var blob = null;
    if (items) {
      for (var i = 0; i < items.length; i++) {
        if (items[i].type && items[i].type.match(/^image\//)) {
          blob = items[i].getAsFile();
          break;
        }
      }
    }
    if (!blob && data.files) {
      for (var j = 0; j < data.files.length; j++) {
        if (@org.waveprotocol.wave.model.util.AttachmentUploadMobileSupport::isImageMime(Ljava/lang/String;)(data.files[j].type || null)) {
          blob = data.files[j];
          break;
        }
      }
    }
    if (!blob) {
      failureCb.@java.lang.Runnable::run()();
      return;
    }

    var fd = new FormData();
    fd.append('attachmentId', attachmentId);
    fd.append('waveRef', waveRef);
    fd.append('uploadFormElement', blob, 'pasted-image.png');

    var xhr = new XMLHttpRequest();
    xhr.open('POST', '/attachment/' + attachmentId, true);
    xhr.timeout = 60000;
    xhr.ontimeout = function() {
      failureCb.@java.lang.Runnable::run()();
    };
    xhr.onload = function() {
      if (xhr.status === 200 || xhr.status === 201) {
        successCb.@java.lang.Runnable::run()();
      } else {
        failureCb.@java.lang.Runnable::run()();
      }
    };
    xhr.onerror = function() {
      failureCb.@java.lang.Runnable::run()();
    };
    xhr.send(fd);
  }-*/;

  // ---------------------------------------------------------------------------
  // Document helpers
  // ---------------------------------------------------------------------------

  /**
   * Returns a live {@link Point} at the start of the current selection, or the
   * last valid position in the document if there is no selection.  Live Points
   * hold ContentNode references that remain valid after subsequent document edits.
   */
  @SuppressWarnings("unchecked")
  private static Point<ContentNode> captureInsertPoint(
      SelectionHelper sel, CMutableDocument doc) {
    if (sel != null) {
      ContentRange range = sel.getOrderedSelectionPoints();
      if (range != null && range.getFirst() != null) {
        return range.getFirst();
      }
    }
    return ((LocationMapper<ContentNode>) doc).locate(doc.size() - 1);
  }

  /**
   * Returns a live {@link Point} at the end of the current selection, or
   * {@code null} if the selection is collapsed or absent.
   */
  private static Point<ContentNode> captureSelectionEndPoint(SelectionHelper sel) {
    if (sel != null) {
      ContentRange range = sel.getOrderedSelectionPoints();
      if (range != null && !range.isCollapsed() && range.getSecond() != null) {
        return range.getSecond();
      }
    }
    return null;
  }
}
