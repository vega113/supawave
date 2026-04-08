# Inline Wave Images Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make image attachments render as real inline wave content for medium and large display modes instead of only as scaled attachment thumbnails, while preserving attachment-id based document storage and existing attachment services.

**Architecture:** Keep the current `<image attachment="...">` wave document model and extend the existing `ImageThumbnail` doodad so it can choose between thumbnail and full attachment media based on display mode and image metadata. Formalize the display-size attribute in the conversation schema, keep `style="full"` for backward compatibility, and avoid migrating to URL-only `<img>` nodes or reviving gadget-based rendering.

**Tech Stack:** Java 17, GWT client editor/doodads, Wave conversation schema, SBT test runner, GitHub issue workflow, Claude review.

---

## Investigation Summary

### Current upload and rendering path

- Upload entrypoint: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/EditToolbar.java`
  - `createInsertAttachmentButton()` opens `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java`
  - `onDoneWithSize()` always inserts `ImageThumbnail.constructXmlWithSize(...)` into the document.
- Client attachment metadata fetch: `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/AttachmentManagerImpl.java`
  - fetches `/attachmentsInfo?attachmentIds=...`
  - hydrates `AttachmentImpl` with attachment URL, thumbnail URL, mime type, filename, and image metadata.
- Server upload and metadata path:
  - `wave/src/main/java/org/waveprotocol/box/server/rpc/AttachmentServlet.java`
  - `wave/src/main/java/org/waveprotocol/box/server/rpc/AttachmentInfoServlet.java`
  - `wave/src/main/java/org/waveprotocol/box/server/attachment/AttachmentService.java`
  - originals are stored, thumbnails are generated, and both URLs plus dimensions are persisted in attachment metadata.

### Why the current UX is still "thumbnail-first"

- Document insertion uses the custom `<image attachment="...">` doodad, not a generic inline image node:
  - `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/ImageThumbnail.java`
- Rendering is owned by:
  - `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailRenderer.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/ImageThumbnailAttachmentHandler.java`
  - `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailWidget.java`
- `ImageThumbnailWidget.setImageSize()` chooses `thumbnailUrl` unless `style="full"` is set. The newer `display-size=medium|large` code only changes CSS dimensions, so medium and large currently upscale the thumbnail card rather than switching to the attachment image.
- `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java` resets the default selection to `small`, which further biases toward thumbnail-style insertion.

### Wave document model support

- Conversation schema already supports attachment-backed image content:
  - `wave/src/main/java/org/waveprotocol/wave/model/schema/conversation/ConversationSchemas.java`
  - permits `<image attachment="...">`, `<caption>`, and `style="full"`.
- Attachment extraction/export logic relies on the `image@attachment` attribute:
  - `wave/src/main/java/org/waveprotocol/box/expimp/DeltaParser.java`
- API/serializer layers also distinguish:
  - `ElementType.ATTACHMENT` -> `<image attachment="...">`
  - `ElementType.IMAGE` -> `<img src="...">`
  - files: `wave/src/main/java/com/google/wave/api/data/ElementSerializer.java`, `wave/src/main/java/com/google/wave/api/Image.java`
- Gap: the schema does **not** currently permit `display-size`, even though the client now writes and reads it. That needs to be formalized if it remains the chosen encoding.

### Doodad and gadget options

- Active image rendering seam is the doodad path registered in `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`.
- The base editor also still knows how to render plain `<img>` through `wave/src/main/java/org/waveprotocol/wave/client/editor/content/img/ImgDoodad.java`, but that path is URL-based and explicitly described as legacy/undesirable.
- Gadget path is not a good extension point:
  - `Gadget` is deprecated for removal: `wave/src/main/java/com/google/wave/api/Gadget.java`
  - repo design docs explicitly say not to extend OpenSocial gadgets.

## Options

### Option A: Native inline image element path

Use a true inline image node as the stored/rendered representation, based on the existing `<img ...>` / `ElementType.IMAGE` path.

UX fit:
- Best match for a browser-native inline image experience.
- Simplest long-term mental model for image-in-text rendering.

Technical risk:
- High. The current `<img>` path is URL-based, not attachment-id based.
- Export/import and attachment-id discovery currently depend on `image@attachment`.
- Caption handling, malware/file-card behavior, and attachment metadata fetch would need replacement or duplication.

Migration effort:
- High. Existing `<image attachment="...">` content would need migration or dual rendering forever.
- Serializer, schema, robots/API compatibility, and downstream tools would need broader changes.

Security implications:
- Must avoid reviving arbitrary external image URLs as first-class content.
- Internal attachment URLs are safer, but the native path does not currently encode attachment identity.

Moderation implications:
- Current attachment metadata and malware state are attachment-id centric. A URL-only content node weakens that coupling.

Backward compatibility:
- Weak unless both formats are supported indefinitely.

### Option B: Doodad-based inline attachment path

Keep storing `<image attachment="...">`, but make medium and large display modes render the original attachment image inline, preserve aspect ratio, and drop thumbnail chrome when appropriate.

UX fit:
- Delivers the desired Telegram-like inline image feel without changing the stored content primitive.
- Keeps small mode as a compact thumbnail/card for non-inline cases.

Technical risk:
- Moderate and local. Most changes stay in the existing `ImageThumbnail` rendering seam.
- Requires formalizing `display-size` in the schema and making the widget choose attachment media for medium/large.

Migration effort:
- Low. Existing content stays valid.
- Old `style="full"` content continues to work.

Security implications:
- Best fit with current auth and download controls because images still resolve through attachment services.
- No new external fetch surface.

Moderation implications:
- Preserves attachment metadata, MIME-type checks, malware flags, and export/import behavior.

Backward compatibility:
- Strong. Existing `<image attachment="...">` deltas, export/import, and robots keep working.

## Recommendation

Choose **Option B**.

Reasoning:
- It solves the actual product gap with the narrowest change set.
- It preserves the attachment-id based document model that the rest of the repo already depends on.
- It avoids a risky migration to URL-only `<img>` content and avoids extending deprecated gadget paths.

Non-goals for this slice:
- Gallery layouts
- URL/video previews
- Gadget-based media rendering
- Migrating stored content from `<image>` to `<img>`
- Broad SSR/public-wave rendering redesign

## Design constants

- Small mode remains thumbnail-first:
  - source: thumbnail
  - max box: `120x80`
  - chrome: shown
- Medium mode becomes inline-image preview for image attachments:
  - source: original attachment image
  - max box: `300x200`
  - chrome: hidden for image attachments, shown for non-image attachments
- Large mode becomes large inline image for image attachments:
  - source: original attachment image
  - max box: `600x400`
  - chrome: hidden for image attachments, shown for non-image attachments
- Full mode precedence:
  - `style="full"` wins over `display-size`
  - when `style="full"` is present and the attachment is an image, always use the original attachment image
- Missing dimension fallback:
  - if image metadata has not loaded yet, keep the current display box sizing for the selected mode and swap in aspect-ratio scaling once metadata arrives

Implementation note:
- `ImageThumbnailWidget` already has `isContentImage()`. Reuse that as the gate for inline-image behavior instead of inventing a second MIME check in the widget layer.

## Phase 1 scope

- Make medium and large modes load the original attachment image instead of the thumbnail.
- Preserve aspect ratio for inline images.
- Hide thumbnail chrome for inline image modes while keeping non-image attachments on the existing card path.
- Make upload default to `medium` instead of `small`.
- Add schema support for `display-size=small|medium|large`.
- Preserve legacy `style="full"` behavior explicitly with regression coverage.

## Task 1: Formalize the display-size document attribute

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/model/schema/conversation/ConversationSchemas.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/model/conversation/SchemaConstraintsTest.java`

- [ ] **Step 1: Write the failing schema test**

```java
assertTrue(
    BLIP_SCHEMA_CONSTRAINTS.permitsAttribute("image", "display-size", "small"));
assertTrue(
    BLIP_SCHEMA_CONSTRAINTS.permitsAttribute("image", "display-size", "medium"));
assertTrue(
    BLIP_SCHEMA_CONSTRAINTS.permitsAttribute("image", "display-size", "large"));
assertFalse(
    BLIP_SCHEMA_CONSTRAINTS.permitsAttribute("image", "display-size", "giant"));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sbt "testOnly org.waveprotocol.wave.model.conversation.SchemaConstraintsTest"`
Expected: FAIL because `display-size` is not currently permitted.

- [ ] **Step 3: Add schema support**

```java
addAttrWithValues("image", "display-size", "small", "medium", "large");
```

- [ ] **Step 4: Run test to verify it passes**

Run: `sbt "testOnly org.waveprotocol.wave.model.conversation.SchemaConstraintsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/model/schema/conversation/ConversationSchemas.java \
  wave/src/test/java/org/waveprotocol/wave/model/conversation/SchemaConstraintsTest.java
git commit -m "fix(image): formalize display-size document attribute"
```

## Task 2: Move inline image rendering decisions into a testable seam

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailLayout.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailWidget.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailLayoutTest.java`

- [ ] **Step 1: Write the failing layout tests**

```java
assertEquals(SourceKind.THUMBNAIL, layout("small", false, true).sourceKind());
assertEquals(SourceKind.ATTACHMENT, layout("medium", false, true).sourceKind());
assertEquals(SourceKind.ATTACHMENT, layout("large", false, true).sourceKind());
assertEquals(SourceKind.ATTACHMENT, layout(null, true, true).sourceKind());
assertTrue(layout("medium", false, true).hideChrome());
assertFalse(layout("small", false, true).hideChrome());
assertFalse(layout("medium", false, false).hideChrome());
assertEquals(SourceKind.THUMBNAIL, layout("large", false, false).sourceKind());
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sbt "testOnly org.waveprotocol.wave.client.doodad.attachment.render.ImageThumbnailLayoutTest"`
Expected: FAIL because the helper does not exist yet.

- [ ] **Step 3: Implement the minimal layout helper**

```java
final class ImageThumbnailLayout {
  enum SourceKind { THUMBNAIL, ATTACHMENT }

  static Decision decide(String displaySize, boolean fullMode, boolean contentImage) {
    boolean inlineImage = contentImage
        && (fullMode || "medium".equals(displaySize) || "large".equals(displaySize));
    return new Decision(
        inlineImage ? SourceKind.ATTACHMENT : SourceKind.THUMBNAIL,
        inlineImage);
  }
}
```

- [ ] **Step 4: Wire the widget to the helper**

```java
ImageThumbnailLayout.Decision decision =
    ImageThumbnailLayout.decide(displaySize, isFullSize, isContentImage());
String url = decision.sourceKind() == SourceKind.ATTACHMENT ? attachmentUrl : thumbnailUrl;
boolean hideChrome = decision.hideChrome();
```

- [ ] **Step 5: Run test to verify it passes**

Run: `sbt "testOnly org.waveprotocol.wave.client.doodad.attachment.render.ImageThumbnailLayoutTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailLayout.java \
  wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailWidget.java \
  wave/src/test/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailLayoutTest.java
git commit -m "feat(image): render medium and large attachments inline"
```

## Task 3: Preserve aspect ratio and default uploads to medium

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailWidget.java`
- Modify: `wave/src/main/resources/org/waveprotocol/wave/client/doodad/attachment/render/Thumbnail.css`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailLayoutTest.java`

- [ ] **Step 1: Extend the failing test coverage**

```java
assertEquals(new Size(300, 200), scale(1200, 800, "medium"));
assertEquals(new Size(600, 400), scale(1200, 800, "large"));
assertEquals(new Size(120, 80), scale(1200, 800, "small"));
assertEquals(new Size(300, 200), scale(0, 0, "medium"));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `sbt "testOnly org.waveprotocol.wave.client.doodad.attachment.render.ImageThumbnailLayoutTest"`
Expected: FAIL because aspect-ratio scaling logic is not implemented yet.

- [ ] **Step 3: Implement aspect-ratio scaling and chrome classes**

```java
ImageThumbnailLayout.Size scaled =
    ImageThumbnailLayout.scale(sourceWidth, sourceHeight, displaySize, isFullSize);
image.setPixelSize(scaled.width(), scaled.height());
if (decision.hideChrome()) {
  addStyleName("inline-image");
} else {
  removeStyleName("inline-image");
}
```

- [ ] **Step 4: Change upload default to medium**

```java
selectedDisplaySize = "medium";
selectDisplaySize("medium");
```

Update all current `small` defaults in:
- field initialization
- constructor/setup default state
- `show()` reset path

- [ ] **Step 5: Run tests to verify they pass**

Run: `sbt "testOnly org.waveprotocol.wave.client.doodad.attachment.render.ImageThumbnailLayoutTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailWidget.java \
  wave/src/main/resources/org/waveprotocol/wave/client/doodad/attachment/render/Thumbnail.css \
  wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/toolbar/attachment/AttachmentPopupWidget.java \
  wave/src/test/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailLayoutTest.java
git commit -m "feat(image): default uploads to inline preview"
```

## Task 4: Targeted verification

**Files:**
- Test: `wave/src/test/java/org/waveprotocol/wave/model/conversation/SchemaConstraintsTest.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/client/doodad/attachment/render/ImageThumbnailLayoutTest.java`

- [ ] **Step 1: Run the targeted test set**

Run: `sbt "testOnly org.waveprotocol.wave.model.conversation.SchemaConstraintsTest org.waveprotocol.wave.client.doodad.attachment.render.ImageThumbnailLayoutTest"`
Expected: PASS

- [ ] **Step 2: Run one narrow compile sanity check**

Run: `sbt compile`
Expected: PASS

- [ ] **Step 3: Record verification in issue comment**

```text
Verification:
- sbt "testOnly org.waveprotocol.wave.model.conversation.SchemaConstraintsTest org.waveprotocol.wave.client.doodad.attachment.render.ImageThumbnailLayoutTest"
- sbt compile
```

## Review checkpoints

- Plan review: run `claude-review` against this plan before coding.
- Implementation review: run `claude-review` against the final diff before the last commit or as an immediate follow-up fixup commit.

## Open questions

- None blocking for Phase 1. The path is clear enough to implement with the current attachment-backed document model.
