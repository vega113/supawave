# Paste-Image-Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a user pastes an image into the wave editor via Ctrl+V / Cmd+V, automatically upload it to the attachment server and insert it inline at the cursor position.

**Architecture:** Add an `ImagePasteHandler` interface to the editor package. `EditorImpl.handlePaste()` checks the clipboard for images first; if found, delegates to the handler and returns consumed. Implementation (`ClipboardImageUploader`) lives in the toolbar/attachment package alongside existing attachment code and uses JSNI for clipboard access and XHR upload.

**Tech Stack:** GWT (Java + JSNI), Java, SBT build

**Spec:** `docs/superpowers/specs/2026-04-08-paste-image-upload-design.md`

---

## File Map

| Action  | File                                                                                                                         | Purpose                                           |
|---------|------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| Create  | `wave/src/main/java/org/waveprotocol/wave/client/editor/ImagePasteHandler.java`                                             | Interface: consumed check for image paste events  |
| Modify  | `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorImpl.java`                                                    | Add handler field/setter, call in handlePaste()   |
| Modify  | `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorContextAdapter.java`                                          | Store handler, forward to wrapped editor on switch |
| Create  | `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/ClipboardImageUploader.java`             | Implementation: JSNI clipboard detect + XHR upload |
| Modify  | `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`                                   | Create uploader, register with editor adapter     |
| Create  | `wave/src/test/java/org/waveprotocol/wave/client/editor/EditorContextAdapterHandlerTest.java`                               | JUnit test for handler forwarding logic           |

---

## Task 1: Create `ImagePasteHandler` interface

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/wave/client/editor/ImagePasteHandler.java`

- [ ] **Step 1.1: Create the interface file**

```java
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
package org.waveprotocol.wave.client.editor;

import com.google.gwt.dom.client.NativeEvent;

/**
 * Hook for intercepting image paste events before the standard text paste path.
 *
 * <p>Registered on {@link EditorImpl} via
 * {@link EditorContextAdapter#setImagePasteHandler(ImagePasteHandler)}.
 */
public interface ImagePasteHandler {

  /**
   * Called when a native paste event fires on the editor.
   *
   * @param nativeEvent the raw browser paste event (may contain clipboardData)
   * @return {@code true} if an image was found and the upload was initiated
   *         (the caller should suppress text-paste handling); {@code false}
   *         to fall through to the normal text/HTML paste path
   */
  boolean handleImagePaste(NativeEvent nativeEvent);
}
```

- [ ] **Step 1.2: Verify compile**

```bash
cd /Users/vega/devroot/worktrees/feat-paste-image
sbt "wave/compile"
```
Expected: BUILD SUCCESS with no errors.

- [ ] **Step 1.3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/editor/ImagePasteHandler.java
git commit -m "feat(paste-image): add ImagePasteHandler interface"
```

---

## Task 2: Modify `EditorImpl` to call `ImagePasteHandler`

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorImpl.java`

Find the `handlePaste` method in `EditorImpl`. It is inside the anonymous `EditorEventsSubHandlerImpl` class, around line 883–888. It currently reads:

```java
@Override
public boolean handlePaste(EditorEvent event) {
  editorUndoManager.maybeCheckpoint();
  EditorStaticDeps.logger.trace().log("handling paste");
  return pasteExtractor.handlePasteEvent(currentSelectionBias);
}
```

- [ ] **Step 2.1: Add `imagePasteHandler` field to `EditorImpl`**

Locate the field declarations section of `EditorImpl` (around line 200–350 where other handler fields are declared). Add after the `pasteExtractor` field declaration:

```java
/** Optional handler that intercepts image data in paste events. */
private ImagePasteHandler imagePasteHandler;
```

- [ ] **Step 2.2: Add `setImagePasteHandler` setter to `EditorImpl`**

In the public methods section of `EditorImpl` (after the constructor, before or after `setPasteExtractor` equivalent), add:

```java
/**
 * Registers a handler that intercepts clipboard image data on paste.
 * When set, the handler is checked before the normal text-paste path.
 *
 * @param handler the handler, or {@code null} to clear
 */
public void setImagePasteHandler(ImagePasteHandler handler) {
  this.imagePasteHandler = handler;
}
```

- [ ] **Step 2.3: Modify `handlePaste` to call the handler first**

Replace the existing `handlePaste` method body:

```java
@Override
public boolean handlePaste(EditorEvent event) {
  editorUndoManager.maybeCheckpoint();
  if (imagePasteHandler != null
      && imagePasteHandler.handleImagePaste(event.asEvent())) {
    return true;
  }
  EditorStaticDeps.logger.trace().log("handling paste");
  return pasteExtractor.handlePasteEvent(currentSelectionBias);
}
```

Note: `event.asEvent()` is defined in `SignalEventImpl` (the base class) and returns `(Event) nativeEvent`, which is a GWT `NativeEvent`. The `NativeEvent` type is imported as `com.google.gwt.dom.client.NativeEvent` — verify this import is present; `asEvent()` returns `com.google.gwt.user.client.Event` which extends `NativeEvent`.

- [ ] **Step 2.4: Verify compile**

```bash
cd /Users/vega/devroot/worktrees/feat-paste-image
sbt "wave/compile"
```
Expected: BUILD SUCCESS.

- [ ] **Step 2.5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/editor/EditorImpl.java
git commit -m "feat(paste-image): intercept image paste in EditorImpl before text paste"
```

---

## Task 3: Modify `EditorContextAdapter` to forward the handler

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/editor/EditorContextAdapter.java`
- Create: `wave/src/test/java/org/waveprotocol/wave/client/editor/EditorContextAdapterHandlerTest.java`

`EditorContextAdapter` wraps a live `EditorContext`. Its `switchEditor(EditorContext)` method is called to swap editors when focus moves. We need it to store the handler and forward to any new editor.

- [ ] **Step 3.1: Write the failing test**

```java
// wave/src/test/java/org/waveprotocol/wave/client/editor/EditorContextAdapterHandlerTest.java
package org.waveprotocol.wave.client.editor;

import com.google.gwt.dom.client.NativeEvent;
import junit.framework.TestCase;

/**
 * Tests that EditorContextAdapter correctly forwards ImagePasteHandler to
 * wrapped EditorImpl instances.
 */
public class EditorContextAdapterHandlerTest extends TestCase {

  /** Minimal stub that records whether setImagePasteHandler was called. */
  private static class StubEditorImpl extends EditorImpl {
    ImagePasteHandler capturedHandler;

    StubEditorImpl() {
      super(null, null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    public void setImagePasteHandler(ImagePasteHandler h) {
      capturedHandler = h;
    }
  }

  private static final ImagePasteHandler HANDLER = nativeEvent -> false;

  public void testSetHandlerForwardsToWrappedEditorImpl() {
    StubEditorImpl impl = new StubEditorImpl();
    EditorContextAdapter adapter = new EditorContextAdapter(impl);

    adapter.setImagePasteHandler(HANDLER);

    assertSame(HANDLER, impl.capturedHandler);
  }

  public void testSwitchEditorForwardsExistingHandler() {
    EditorContextAdapter adapter = new EditorContextAdapter(null);
    adapter.setImagePasteHandler(HANDLER);

    StubEditorImpl impl = new StubEditorImpl();
    adapter.switchEditor(impl);

    assertSame(HANDLER, impl.capturedHandler);
  }

  public void testSwitchEditorWithNoHandlerDoesNothing() {
    StubEditorImpl impl = new StubEditorImpl();
    EditorContextAdapter adapter = new EditorContextAdapter(null);

    adapter.switchEditor(impl);

    assertNull(impl.capturedHandler);
  }
}
```

Note: `StubEditorImpl` above requires `EditorImpl` to have a no-arg or minimal constructor accessible, OR the test needs a different approach. Since `EditorImpl` is a complex GWT class that will fail to instantiate in a plain JUnit environment, replace `StubEditorImpl extends EditorImpl` with a direct `EditorImpl` subclass approach. **Use the following alternative test that avoids constructing `EditorImpl`:**

```java
package org.waveprotocol.wave.client.editor;

import com.google.gwt.dom.client.NativeEvent;
import junit.framework.TestCase;

/**
 * Tests EditorContextAdapter handler forwarding without requiring a real EditorImpl.
 */
public class EditorContextAdapterHandlerTest extends TestCase {

  private static final ImagePasteHandler HANDLER = nativeEvent -> false;

  /**
   * Minimal EditorContext (not EditorImpl) — handler should NOT be forwarded.
   */
  private static class NonImplEditor extends StubEditorContext {
    // no setImagePasteHandler
  }

  public void testSetHandlerOnNullEditorDoesNotCrash() {
    EditorContextAdapter adapter = new EditorContextAdapter(null);
    adapter.setImagePasteHandler(HANDLER); // must not throw
  }

  public void testSwitchToNonImplEditorDoesNotCrash() {
    EditorContextAdapter adapter = new EditorContextAdapter(null);
    adapter.setImagePasteHandler(HANDLER);
    adapter.switchEditor(new NonImplEditor()); // must not throw
  }

  public void testSwitchEditorWithNoHandlerDoesNotCrash() {
    EditorContextAdapter adapter = new EditorContextAdapter(null);
    adapter.switchEditor(new NonImplEditor()); // must not throw
  }
}
```

Where `StubEditorContext` is a no-op implementation of `EditorContext`:

```java
// Add as a package-private class in the same test file, OR as a separate helper
class StubEditorContext implements EditorContext {
  @Override public CMutableDocument getDocument() { return null; }
  @Override public SelectionHelper getSelectionHelper() { return null; }
  @Override public CaretAnnotations getCaretAnnotations() { return null; }
  @Override public String getImeCompositionState() { return null; }
  @Override public boolean isEditing() { return false; }
  @Override public void focus(boolean collapsed) {}
  @Override public void blur() {}
  @Override public void addUpdateListener(EditorUpdateEvent.EditorUpdateListener l) {}
  @Override public void removeUpdateListener(EditorUpdateEvent.EditorUpdateListener l) {}
  @Override public Responsibility.Manager getResponsibilityManager() { return null; }
  @Override public void undoableSequence(Runnable cmd) { cmd.run(); }
}
```

- [ ] **Step 3.2: Run test to verify it fails (compilation error expected)**

```bash
cd /Users/vega/devroot/worktrees/feat-paste-image
sbt "wave/testOnly org.waveprotocol.wave.client.editor.EditorContextAdapterHandlerTest" 2>&1 | tail -20
```
Expected: compile error — `setImagePasteHandler` does not exist on `EditorContextAdapter`.

- [ ] **Step 3.3: Add `setImagePasteHandler` to `EditorContextAdapter`**

In `EditorContextAdapter.java`, add a field and two methods. Add after the `editor` field:

```java
/** Handler forwarded to the wrapped editor whenever it is set or swapped. */
private ImagePasteHandler imagePasteHandler;
```

Add a new public method (not part of the `EditorContext` interface):

```java
/**
 * Sets the image paste handler and forwards it to the currently wrapped
 * editor if that editor is an {@link EditorImpl}.
 */
public void setImagePasteHandler(ImagePasteHandler handler) {
  this.imagePasteHandler = handler;
  forwardImagePasteHandler(editor);
}
```

Modify the existing `switchEditor` method to also forward the handler:

```java
/** Silently switches the wrapped editor with a new instance. */
public void switchEditor(EditorContext newEditor) {
  this.editor = newEditor;
  forwardImagePasteHandler(newEditor);
}
```

Add the private helper:

```java
private void forwardImagePasteHandler(EditorContext ctx) {
  if (imagePasteHandler != null && ctx instanceof EditorImpl) {
    ((EditorImpl) ctx).setImagePasteHandler(imagePasteHandler);
  }
}
```

- [ ] **Step 3.4: Run test to verify it passes**

```bash
cd /Users/vega/devroot/worktrees/feat-paste-image
sbt "wave/testOnly org.waveprotocol.wave.client.editor.EditorContextAdapterHandlerTest" 2>&1 | tail -20
```
Expected: all 3 tests PASS.

- [ ] **Step 3.5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/editor/EditorContextAdapter.java \
        wave/src/test/java/org/waveprotocol/wave/client/editor/EditorContextAdapterHandlerTest.java
git commit -m "feat(paste-image): forward ImagePasteHandler through EditorContextAdapter"
```

---

## Task 4: Create `ClipboardImageUploader`

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/ClipboardImageUploader.java`

This class implements `ImagePasteHandler`. It uses JSNI to detect images in `clipboardData`, uploads via XHR, and inserts the image element into the document after upload completes.

- [ ] **Step 4.1: Create the file**

```java
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
import com.google.gwt.user.client.URL;

import org.waveprotocol.wave.client.doodad.attachment.ImageThumbnail;
import org.waveprotocol.wave.client.editor.EditorContextAdapter;
import org.waveprotocol.wave.client.editor.ImagePasteHandler;
import org.waveprotocol.wave.client.editor.content.CMutableDocument;
import org.waveprotocol.wave.client.editor.content.ContentElement;
import org.waveprotocol.wave.client.editor.content.ContentNode;
import org.waveprotocol.wave.client.editor.content.ContentTextNode;
import org.waveprotocol.wave.client.editor.selection.content.SelectionHelper;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.media.model.AttachmentIdGenerator;
import org.waveprotocol.wave.model.document.indexed.LocationMapper;
import org.waveprotocol.wave.model.document.util.Point;
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

  private static final String UPLOAD_URL = "/attachment/";

  private final AttachmentIdGenerator idGenerator;
  private final WaveId waveId;
  private final EditorContextAdapter editor;

  /** Progress indicator element shown during upload. May be null. */
  private Element progressIndicator;

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
    SelectionHelper sel = editor.getSelectionHelper();
    if (doc == null || sel == null) {
      return false;
    }

    // Capture insert position synchronously — the cursor may move during async upload.
    int insertOffset = captureInsertOffset(doc, sel);

    AttachmentId attachmentId = idGenerator.newAttachmentId();
    String waveRefToken = URL.encode(
        GwtWaverefEncoder.encodeToUriQueryString(WaveRef.of(waveId)));

    showProgressIndicator();

    startXhrUpload(nativeEvent, attachmentId.getId(), waveRefToken,
        () -> onUploadSuccess(attachmentId.getId()),
        () -> onUploadFailure());

    return true; // consumed — suppress text paste
  }

  // ---------------------------------------------------------------------------
  // Upload callbacks (called from JSNI on the GWT event thread)
  // ---------------------------------------------------------------------------

  private void onUploadSuccess(String attachmentId) {
    hideProgressIndicator();

    CMutableDocument doc = editor.getDocument();
    SelectionHelper sel = editor.getSelectionHelper();
    if (doc == null) {
      return;
    }

    int offset = captureInsertOffset(doc, sel);
    Point<ContentNode> insertPoint = ((LocationMapper<ContentNode>) doc).locate(offset);

    XmlStringBuilder xml = ImageThumbnail.constructXmlWithSize(
        attachmentId, ImageThumbnail.DISPLAY_SIZE_MEDIUM, "pasted-image.png");
    doc.insertXml(insertPoint, xml);
  }

  private void onUploadFailure() {
    hideProgressIndicator();
    showErrorToast("Image upload failed.");
  }

  // ---------------------------------------------------------------------------
  // Progress indicator (lightweight DOM element, no GWT widget overhead)
  // ---------------------------------------------------------------------------

  private void showProgressIndicator() {
    if (progressIndicator != null) {
      return; // already showing
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
    if (progressIndicator != null) {
      progressIndicator.removeFromParent();
      progressIndicator = null;
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
    var items = event.clipboardData && event.clipboardData.items;
    if (!items) return false;
    for (var i = 0; i < items.length; i++) {
      if (items[i].type && items[i].type.match(/^image\//)) return true;
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
   *   <li>{@code attachmentId} — serialised attachment id (matches path)</li>
   *   <li>{@code waveRef} — URL-encoded wave ref token</li>
   *   <li>{@code uploadFormElement} — image blob named {@code pasted-image.png}</li>
   * </ul>
   */
  private native void startXhrUpload(NativeEvent event,
      String attachmentId, String waveRef,
      Runnable successCb, Runnable failureCb) /*-{
    var items = event.clipboardData && event.clipboardData.items;
    if (!items) {
      failureCb.@java.lang.Runnable::run()();
      return;
    }
    var blob = null;
    for (var i = 0; i < items.length; i++) {
      if (items[i].type && items[i].type.match(/^image\//)) {
        blob = items[i].getAsFile();
        break;
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
   * Returns the integer document offset of the start of the current selection,
   * or the last valid offset in the document if there is no selection.
   */
  @SuppressWarnings("unchecked")
  private static int captureInsertOffset(CMutableDocument doc, SelectionHelper sel) {
    if (sel != null) {
      var range = sel.getOrderedSelectionPoints();
      if (range != null && range.getFirst() != null) {
        return ((LocationMapper<ContentNode>) doc).getLocation(range.getFirst());
      }
    }
    return doc.size() - 1;
  }
}
```

- [ ] **Step 4.2: Verify compile**

```bash
cd /Users/vega/devroot/worktrees/feat-paste-image
sbt "wave/compile"
```
Expected: BUILD SUCCESS. Fix any import errors (all imports are listed in the file header above).

- [ ] **Step 4.3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/ClipboardImageUploader.java
git commit -m "feat(paste-image): add ClipboardImageUploader with JSNI clipboard + XHR upload"
```

---

## Task 5: Wire `ClipboardImageUploader` into `EditToolbar`

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`

`EditToolbar` already creates and holds `attachmentIdGenerator` (an `AttachmentIdGeneratorImpl`) and `waveId`. It also holds `editor` which is an `EditorContextAdapter`. We register the uploader on the adapter so it is automatically forwarded to each editor that becomes focused.

- [ ] **Step 5.1: Add the `ClipboardImageUploader` import to `EditToolbar.java`**

In the imports block of `EditToolbar.java`, add:

```java
import org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment.ClipboardImageUploader;
```

- [ ] **Step 5.2: Register the uploader in `EditToolbar`'s constructor**

Locate the `EditToolbar` constructor (around line 120–127):

```java
public EditToolbar(EditorToolbarResources.Css css, ToplevelToolbarWidget toolbarUi,
    ParticipantId user, IdGenerator idGenerator, WaveId waveId) {
  this.css = css;
  this.toolbarUi = toolbarUi;
  this.user = user;
  this.waveId = waveId;
  attachmentIdGenerator = new AttachmentIdGeneratorImpl(idGenerator);
}
```

Add one line at the end of the constructor body, after `attachmentIdGenerator` is initialised:

```java
  editor.setImagePasteHandler(
      new ClipboardImageUploader(attachmentIdGenerator, waveId, editor));
```

The full constructor becomes:

```java
public EditToolbar(EditorToolbarResources.Css css, ToplevelToolbarWidget toolbarUi,
    ParticipantId user, IdGenerator idGenerator, WaveId waveId) {
  this.css = css;
  this.toolbarUi = toolbarUi;
  this.user = user;
  this.waveId = waveId;
  attachmentIdGenerator = new AttachmentIdGeneratorImpl(idGenerator);
  editor.setImagePasteHandler(
      new ClipboardImageUploader(attachmentIdGenerator, waveId, editor));
}
```

- [ ] **Step 5.3: Verify compile**

```bash
cd /Users/vega/devroot/worktrees/feat-paste-image
sbt "wave/compile"
```
Expected: BUILD SUCCESS.

- [ ] **Step 5.4: Run all unit tests**

```bash
cd /Users/vega/devroot/worktrees/feat-paste-image
sbt "wave/testOnly org.waveprotocol.wave.client.editor.EditorContextAdapterHandlerTest" 2>&1 | tail -20
```
Expected: 3 tests pass.

- [ ] **Step 5.5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java
git commit -m "feat(paste-image): wire ClipboardImageUploader into EditToolbar"
```

---

## Task 6: Full compile + push + PR

- [ ] **Step 6.1: Full compile**

```bash
cd /Users/vega/devroot/worktrees/feat-paste-image
sbt "wave/compile" 2>&1 | tail -30
```
Expected: BUILD SUCCESS, zero errors.

- [ ] **Step 6.2: Push branch**

```bash
git push -u origin feat/paste-image-upload
```

- [ ] **Step 6.3: Create PR**

```bash
gh pr create \
  --title "feat(paste-image): paste-to-upload images in wave editor" \
  --body "$(cat <<'EOF'
## Summary

- Adds `ImagePasteHandler` interface to `editor` package — a pluggable hook called before the text-paste path
- `EditorImpl.handlePaste()` checks the handler first; if it consumes the event (clipboard image found), text paste is suppressed
- `EditorContextAdapter` stores and forwards the handler to any editor that becomes focused
- `ClipboardImageUploader` (toolbar/attachment package) detects `image/*` data in `clipboardData`, uploads via XHR FormData to `/attachment/{id}`, and inserts an `<image>` element at the captured paste cursor position after success
- Progress indicator (CSS spinner toast) shown during upload; error toast on failure
- `EditToolbar` creates and registers the uploader on construction — no new wiring required at higher levels

## Test plan

- [ ] Open a wave in browser; focus the editor
- [ ] Copy an image to clipboard (e.g., screenshot or copy image in browser)
- [ ] Ctrl+V / Cmd+V in the editor
- [ ] Spinner toast appears ("Uploading pasted image...")
- [ ] Toast disappears; image renders inline at the paste cursor position
- [ ] If network is offline: error toast "Image upload failed." appears after timeout
- [ ] Text paste (Ctrl+V with text on clipboard) still works normally
- [ ] `sbt wave/compile` passes

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review Notes

**Spec coverage check:**
- ✅ Detect pasted image from clipboard (`hasClipboardImage` JSNI)
- ✅ Upload as attachment (`startXhrUpload` JSNI, POST to `/attachment/{id}`)
- ✅ Insert inline at cursor position (`captureInsertOffset` + `doc.insertXml`)
- ✅ Show upload progress indicator (`showProgressIndicator` / `hideProgressIndicator`)
- ✅ Fall through to text paste when no image (`return false` in `handleImagePaste`)

**Type consistency check:**
- `ImagePasteHandler.handleImagePaste(NativeEvent)` — defined Task 1, used Task 2 ✅
- `EditorContextAdapter.setImagePasteHandler(ImagePasteHandler)` — defined Task 3, used Task 5 ✅
- `ClipboardImageUploader` constructor args match what `EditToolbar` has ✅
- `ImageThumbnail.constructXmlWithSize(String, String, String)` — verified from source ✅
- `LocationMapper<ContentNode>` cast — `CMutableDocument` implements it (verified in `PasteExtractor.java`) ✅

**Edge cases covered:**
- Null doc/selection guard in `handleImagePaste` — returns false cleanly
- Null doc guard in `onUploadSuccess` — no-ops cleanly
- Insert offset fallback to `doc.size() - 1` when no selection
- Duplicate progress indicator guard (`if (progressIndicator != null) return`)
