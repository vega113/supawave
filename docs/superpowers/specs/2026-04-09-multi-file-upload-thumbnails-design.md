# Multi-File Upload with Thumbnails & Captions — Design Spec

## Goal

Enhance the Wave attachment dialog to support selecting multiple files at once, showing per-file thumbnail previews with individual caption inputs (like Telegram), and uploading all files sequentially into the wave document.

## Key Architecture Decisions

### Caption storage: inside `<image><caption>text</caption></image>`
The `Caption.TAGNAME = "caption"` element already exists in the Wave document model and `ImageThumbnail.constructXmlWithSize(id, size, caption)` already places text there. Currently it receives the filename. We pass user-typed caption text instead. **No document model changes needed.**

### Upload: sequential XHR per file (no server changes)
Replace `FormPanel.submit()` with JSNI `XMLHttpRequest` per file. This gives real `upload.onprogress` events for per-file progress bars and requires zero server changes — `AttachmentServlet` already handles one file per POST.

### Attachment ID generation: on-demand via listener
Add `requestNewAttachmentId()` to `AttachmentPopupView.Listener`. `EditToolbar` generates IDs on demand; the popup requests one per file at upload time. Remove the pre-generated single ID from the show flow.

### Per-file captions (like Telegram)
Each preview card has its own `TextBox`. Empty caption → filename used as fallback (preserving existing behavior).

### Display size: shared for the batch
One size selector for all files in the batch.

## UI Layout

```
┌────────────────────────────────────────────────┐
│  📎 Attach Files                          [×]  │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │  Drop files here or tap to browse       │   │
│  │       [+ Add More Files]                │   │
│  └─────────────────────────────────────────┘   │
│  〰〰〰〰〰〰〰〰〰〰〰〰〰〰〰〰〰〰〰〰〰〰  │
│  ┌──────┐  ┌──────┐  ┌──────┐               │
│  │ 🖼️   │  │ 🖼️   │  │ 📄   │  [×] per card │
│  │thumb │  │thumb │  │report│               │
│  │[cap] │  │[cap] │  │[cap] │               │
│  │▓▓░░░ │  │▓▓▓▓░ │  │░░░░░ │               │
│  └──────┘  └──────┘  └──────┘               │
│                                                 │
│  Size: [S] [M★] [L]     🗜 Compress: ON        │
│                                                 │
│  [Cancel]           [Upload 3 files →]          │
└────────────────────────────────────────────────┘
```

## Components Modified (no new files)

### `AttachmentPopupView.java`
- Add `AttachmentId requestNewAttachmentId()` to `Listener`
- Add `void onDoneWithSizeAndCaption(String waveId, String id, String fileName, String displaySize, String caption)` to `Listener`
- Keep `onDone` / `onDoneWithSize` for backward compatibility

### `EditToolbar.java`
- Implement `requestNewAttachmentId()` → `attachmentIdGenerator.newAttachmentId()`
- Implement `onDoneWithSizeAndCaption()` — passes `caption` (not filename) to `constructXmlWithSize`
- Remove pre-generated single ID from `createInsertAttachmentButton`

### `AttachmentPopupWidget.java`
- Add `multiple` attribute to `FileUpload` via JSNI at construction
- New inner class `FileEntry`: fileIndex, fileName, mimeType, fileSize, caption, card HTMLPanel, progress bar, captionInput TextBox, assigned attachmentId
- `FlowPanel previewGrid` (CSS grid, max-height 300px, scrollable) replaces single `filePreviewPanel`
- `onFileSelected()`: iterate all files, build `FileEntry` per file, call `readPreviewAsync()` for images
- JSNI `readPreviewAsync(element, index)`: FileReader → `onPreviewReady(index, dataUrl)` callback
- JSNI `uploadFileWithXhr(element, fileIndex, attachId, waveRef, compress, maxDim, quality, url)`: XHR upload with real progress events, optional Canvas compression
- Sequential upload loop: `uploadNext(int index)` → XHR → `onFileUploadComplete(index, success)` → `uploadNext(index+1)`

### `AttachmentPopupWidget.ui.xml`
- Add `previewGrid` FlowPanel
- Add "Add More Files" button inside drop zone
- Upload button label becomes "Upload N files →" (updated in Java)

### `AttachmentPopupWidget.css`
- Preview grid: flexbox wrap, 3-col desktop / 2-col mobile (≤480px)
- Preview cards: `border-radius: 8px`, teal border `#4a90d9`, shadow
- Caption input: pill shape (`border-radius: 16px`), teal focus ring
- Progress fill: teal with `@keyframes wave-shimmer` diagonal shimmer
- Wave separator: CSS border-image or SVG path element in ui.xml

## Wave Aesthetic
- Teal/blue color palette matching existing Wave style (`#0077b6`, `#4a90d9`)
- `wave-shimmer` keyframe animation on progress bars (moves highlight left→right like water)
- Pill-shaped caption inputs with soft teal focus glow
- Subtle rounded preview cards with gentle box-shadow

## Mobile
- `multiple` attribute on `<input type="file">` works on iOS/Android
- Grid switches to 2-column at 480px
- All tap targets ≥ 48px (captions, × buttons)

## No Server Changes
All uploads go to the existing `POST /attachment/{id}` endpoint, one at a time.
