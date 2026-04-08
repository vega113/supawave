# Paste-to-Upload Image Design

**Date:** 2026-04-08  
**Branch:** feat/paste-image-upload  
**Status:** Approved

## Problem

When a user pastes an image (Ctrl+V / Cmd+V) into the wave editor, nothing happens — the browser's default paste behavior fires but the GWT editor only handles text/HTML. This design adds clipboard image detection, background upload, and inline insertion at the paste cursor position.

## Architecture

### Approach: EditorImpl-level interceptor (Approach A)

Add a pluggable `ImagePasteHandler` hook to `EditorImpl`. The handler is implemented in the toolbar/attachment package (alongside existing attachment infrastructure) and injected via `EditorContextAdapter`. This follows the existing pattern where toolbar concerns stay in the toolbar layer while the editor core stays clean.

### New files

**`ImagePasteHandler.java`** (`org.waveprotocol.wave.client.editor`)
```
interface ImagePasteHandler {
  boolean handleImagePaste(NativeEvent nativeEvent);
}
```
Returns `true` if the event was consumed (image found and upload started); `false` to fall through to the normal text-paste path.

**`ClipboardImageUploader.java`** (`org.waveprotocol.wave.client.wavepanel.impl.toolbar.attachment`)

Implements `ImagePasteHandler`. Contains all JSNI for:
- Detecting `image/*` entries in `event.clipboardData.items`
- Uploading via `XMLHttpRequest` + `FormData` to `/attachment/{id}`

Java callbacks handle document insertion and progress feedback.

Constructor dependencies:
- `AttachmentIdGenerator` — generate a fresh ID per paste
- `WaveId` — compute the wave ref token at call time
- `EditorContextAdapter` — access the live doc + selection at paste time

### Modified files

**`EditorImpl.java`** — minimal change to `handlePaste()`:
```java
@Override
public boolean handlePaste(EditorEvent event) {
  editorUndoManager.maybeCheckpoint();
  if (imagePasteHandler != null
      && imagePasteHandler.handleImagePaste(event.asEvent())) {
    return true;  // consumed — image paste handled
  }
  EditorStaticDeps.logger.trace().log("handling paste");
  return pasteExtractor.handlePasteEvent(currentSelectionBias);
}
```
New field: `private ImagePasteHandler imagePasteHandler;`  
New setter: `public void setImagePasteHandler(ImagePasteHandler h)`

**`EditorContextAdapter.java`** — stores the handler and forwards on `switchEditor()`:
```java
private ImagePasteHandler imagePasteHandler;

public void setImagePasteHandler(ImagePasteHandler h) {
  this.imagePasteHandler = h;
  forwardHandlerToEditor(editor);
}

@Override
public void switchEditor(EditorContext newEditor) {
  this.editor = newEditor;
  forwardHandlerToEditor(newEditor);
}

private void forwardHandlerToEditor(EditorContext ctx) {
  if (imagePasteHandler != null && ctx instanceof EditorImpl) {
    ((EditorImpl) ctx).setImagePasteHandler(imagePasteHandler);
  }
}
```

**`EditToolbar.java`** — creates and registers the uploader in `createInsertAttachmentButton()` (or the toolbar install path):
```java
ClipboardImageUploader pasteUploader =
    new ClipboardImageUploader(attachmentIdGenerator, waveId, editor);
editor.setImagePasteHandler(pasteUploader);
```

## Data Flow

```
User Ctrl+V with image on clipboard
  → EditorEventHandler.handleClipboard()
  → EditorImpl.handlePaste(EditorEvent event)
  → imagePasteHandler.handleImagePaste(event.asEvent())   // JSNI: check clipboardData
      if no image → return false → PasteExtractor (normal text paste)
      if image found:
        1. Generate attachmentId
        2. Compute waveRefToken from WaveId
        3. Capture insertOffset = doc.getLocation(selection.getFirst())
        4. Show upload indicator (floating DOM div)
        5. Start XHR upload (async)
        6. return true   ← consumed, no text paste
  → XHR success:
        Hide indicator
        Point<ContentNode> pt = doc.locate(insertOffset)
        doc.insertXml(pt, ImageThumbnail.constructXmlWithSize(id, "medium", "pasted-image.png"))
  → XHR failure:
        Hide indicator, show "Image upload failed" (3s DOM toast)
```

## Upload Request

`POST /attachment/{attachmentId}` multipart/form-data:
- `attachmentId` — serialized attachment ID (matches URL path)
- `waveRef` — URL-encoded wave ref token
- `uploadFormElement` — image blob with filename `pasted-image.png`

Field names match existing `AttachmentPopupWidget.ui.xml` form field names so the same `AttachmentServlet.readUploadRequest()` parses it without changes.

## Progress Indicator

A lightweight DOM div appended to `document.body` during upload:
- Positioned bottom-right (CSS `position:fixed`)
- Text: "Uploading pasted image..."
- Simple CSS spinner (border animation, no images)
- Removed on success or failure

Not using `DevToast` (dev-only). A small inline JSNI helper creates/removes the element.

## Cursor Position Handling

Cursor is captured synchronously at paste time as an integer document offset (`doc.getLocation(selectionPoint)`). After the async XHR, the offset is resolved back to a `Point<ContentNode>` via `doc.locate(offset)`. This is robust to the user typing more text while the upload is in progress — the image appears at the paste point regardless.

If selection is unavailable at paste time, the image is inserted at document end (`doc.size() - 1`), matching existing fallback behavior in `EditToolbar`.

## Display Size

`"medium"` by default (800px max dimension). Same default as `AttachmentPopupWidget`.

## Out of scope

- Display size selection for pasted images (always "medium")
- Image compression before paste upload (always uploads raw clipboard blob)
- Multiple images in a single paste (first image only)
- Non-image clipboard items (text paste falls through normally)
