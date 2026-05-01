# J2CL Attachment Display-Size Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make J2CL read-surface attachments respect GWT-compatible source selection and small/medium/large display-size bounds.

**Architecture:** The Java read renderer already emits `data-display-size` and attachment classes. GWT `AttachmentDisplayLayout.decide()` uses attachment URLs for medium/large inline images and thumbnail URLs for small tiles or non-image attachment cards. J2CL follows that source priority while preserving safe fallback to the alternate URL when metadata is incomplete. The missing parity layer is CSS and explicit renderer hooks that cap preview images the same way GWT `Thumbnail.css` caps `.display-size-small|medium|large .itimg`: 120x80, 300x200, 600x400.

**Tech Stack:** J2CL Java renderer tests, Lit/static CSS in `j2cl/src/main/webapp/assets/sidecar.css`, SBT verification, changelog fragments.

---

## File Map

- Modify: `j2cl/src/main/webapp/assets/sidecar.css`
  - Adds read-surface attachment layout and size caps for `.j2cl-read-attachment-preview` under `[data-display-size="small|medium|large"]`.
  - Ensures oversized images cannot exceed the wave panel width.
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
  - Keeps explicit `data-display-size` metadata on image previews so tests and browser diagnostics can prove the selected cap without presentation attributes that can upscale smaller thumbnails.
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`
  - Adds/extends DOM tests for image preview source and display-size attributes.
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentRenderModelTest.java`
  - Updates model expectation so medium/large inline images prefer attachment URLs with thumbnail fallback, while small/card attachments prefer thumbnails with safe attachment fallback.
- Create: `wave/config/changelog.d/2026-05-01-j2cl-attachment-thumbnail-parity.json`
  - New user-facing changelog fragment. Do not hand-edit `wave/config/changelog.json`; assemble/validate via scripts.

## Task 1: Red Tests For Preview URL And Size Bounds

**Files:**
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentRenderModelTest.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`

- [ ] **Step 1: Change the model test to expect GWT-compatible medium image sources**

In `mediumImageUsesOriginalAttachmentUrl`, change the test name to `mediumInlineImageUsesAttachmentUrlForBothPreviewAndOpen` and assert:

```java
Assert.assertTrue(model.isInlineImage());
Assert.assertEquals("medium", model.getDisplaySize());
Assert.assertEquals("/attachment/example.com/att+hero", model.getSourceUrl());
Assert.assertEquals("/attachment/example.com/att+hero", model.getOpenUrl());
Assert.assertEquals("/attachment/example.com/att+hero", model.getDownloadUrl());
```

- [ ] **Step 2: Add renderer assertions for medium preview caps**

In `renderWindowEntriesIncludeKeyboardReachableAttachmentControls`, after `Assert.assertNotNull(tile.querySelector("img"));`, bind `HTMLElement preview = (HTMLElement) tile.querySelector("img");` and assert:

```java
Assert.assertEquals("/attachment/example.com/att+hero", preview.getAttribute("src"));
Assert.assertEquals("medium", preview.getAttribute("data-display-size"));
```

- [ ] **Step 3: Add a large image renderer test**

Add a test named `largeInlineImageUsesAttachmentUrlAndDataDisplaySize`:

```java
@Test
public void largeInlineImageUsesAttachmentUrlAndDataDisplaySize() {
  assumeBrowserDom();
  HTMLDivElement host = createHost();
  J2clAttachmentRenderModel attachment =
      J2clAttachmentRenderModel.fromMetadata(
          "example.com/att+large",
          "Large diagram",
          "large",
          attachmentMetadata(
              "example.com/att+large",
              "large.png",
              "image/png",
              "/attachment/example.com/att+large",
              "/thumbnail/example.com/att+large",
              new J2clAttachmentMetadata.ImageMetadata(2400, 1600),
              false));

  Assert.assertTrue(
      new J2clReadSurfaceDomRenderer(host)
          .render(
              Arrays.asList(
                  new J2clReadBlip("b+root", "Root text", Arrays.asList(attachment))),
              Collections.<String>emptyList()));

  HTMLElement tile =
      (HTMLElement) host.querySelector("[data-attachment-id='example.com/att+large']");
  HTMLElement preview = (HTMLElement) tile.querySelector(".j2cl-read-attachment-preview");
  Assert.assertEquals("large", tile.getAttribute("data-display-size"));
  Assert.assertEquals("/attachment/example.com/att+large", preview.getAttribute("src"));
  Assert.assertEquals("large", preview.getAttribute("data-display-size"));
}
```

- [ ] **Step 4: Run red tests**

Run:

```bash
sbt --batch 'testOnly org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModelTest' 'testOnly org.waveprotocol.box.j2cl.read.J2clReadSurfaceDomRendererTest'
```

Expected: fail because preview images do not carry the selected display-size metadata and CSS does not yet enforce GWT-compatible bounds.

## Task 2: Implement GWT Source Selection And Cap Metadata

**Files:**
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentRenderModel.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`

- [ ] **Step 1: Match GWT preview source selection**

In `J2clAttachmentRenderModel.fromMetadata`, use attachment-first fallback for medium/large inline images, and thumbnail-first fallback for small/card attachments:

```java
String sourceUrl =
    inlineImage
        ? firstNonEmpty(safeUrl(metadata.getAttachmentUrl()), safeUrl(metadata.getThumbnailUrl()))
        : firstNonEmpty(safeUrl(metadata.getThumbnailUrl()), safeUrl(metadata.getAttachmentUrl()));
```

Keep `openUrl` and `downloadUrl` as `metadata.getAttachmentUrl()`.

- [ ] **Step 2: Stamp preview display-size metadata**

In `renderAttachment`, after setting preview `src`, add:

```java
String displaySize = model.getDisplaySize();
preview.setAttribute("data-display-size", displaySize);
```

- [ ] **Step 3: Run green tests**

Run the same focused tests from Task 1. Expected: pass.

## Task 3: Add J2CL Attachment CSS Size Rules

**Files:**
- Modify: `j2cl/src/main/webapp/assets/sidecar.css`

- [ ] **Step 1: Add read attachment CSS after `.j2cl-read-blip-content`**

Add:

```css
.j2cl-read-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 6px 0 0 3.35em;
  max-width: calc(100% - 3.35em);
}

.j2cl-read-attachment {
  box-sizing: border-box;
  max-width: 100%;
}

.j2cl-read-attachment-inline-image,
.j2cl-read-attachment-card {
  display: inline-grid;
  gap: 4px;
}

.j2cl-read-attachment-preview {
  display: block;
  height: auto;
  max-width: min(100%, var(--j2cl-attachment-max-width, 120px));
  max-height: var(--j2cl-attachment-max-height, 80px);
  object-fit: contain;
}

.j2cl-read-attachment[data-display-size="small"] {
  --j2cl-attachment-max-width: 120px;
  --j2cl-attachment-max-height: 80px;
}

.j2cl-read-attachment[data-display-size="medium"] {
  --j2cl-attachment-max-width: 300px;
  --j2cl-attachment-max-height: 200px;
}

.j2cl-read-attachment[data-display-size="large"] {
  --j2cl-attachment-max-width: 600px;
  --j2cl-attachment-max-height: 400px;
}
```

- [ ] **Step 2: Add a CSS regression test by static grep if no CSS test harness exists**

If no CSS test harness exists, rely on browser verification and `git diff --check`; do not invent a fragile parser.

## Task 4: Changelog And Verification

**Files:**
- Create: `wave/config/changelog.d/2026-05-01-j2cl-attachment-thumbnail-parity.json`

- [ ] **Step 1: Add changelog fragment**

Create:

```json
{
  "releaseId": "2026-05-01-j2cl-attachment-thumbnail-parity",
  "version": "Issue #1166",
  "date": "2026-05-01",
  "title": "J2CL attachment display-size parity",
  "summary": "Aligns J2CL attachment previews with GWT source selection and display-size behavior.",
  "sections": [
    {
      "type": "fix",
      "items": [
        "Uses GWT-compatible preview sources: attachment URLs for medium and large inline images, and thumbnail URLs for small tiles and attachment cards.",
        "Caps J2CL small, medium, and large attachment previews to GWT-compatible dimensions so oversized images do not break the wave panel."
      ]
    }
  ]
}
```

- [ ] **Step 2: Run full required verification**

Run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py --fragments-dir wave/config/changelog.d --changelog wave/config/changelog.json
git diff --check
sbt --batch compile Test/compile j2clSearchTest j2clLitTest
```

- [ ] **Step 3: Browser sanity**

Run file-store setup and local server:

```bash
scripts/worktree-file-store.sh --source /Users/vega/devroot/incubator-wave
bash scripts/worktree-boot.sh --port 9916
PORT=9916 JAVA_OPTS='-Djava.util.logging.config.file=/Users/vega/devroot/worktrees/issue-1166-attachment-parity-20260501/wave/config/wiab-logging.conf -Djava.security.auth.login.config=/Users/vega/devroot/worktrees/issue-1166-attachment-parity-20260501/wave/config/jaas.config' bash scripts/wave-smoke.sh start
PORT=9916 bash scripts/wave-smoke.sh check
```

Browser verify against `/?view=j2cl-root` by injecting/rendering a J2CL attachment fixture or opening an existing attachment wave:
- small preview maxes at 120x80
- medium preview maxes at 300x200
- large preview maxes at 600x400 and never exceeds panel width
- preview `src` matches GWT source priority: `/attachment/` for medium/large inline images with safe thumbnail fallback, `/thumbnail/` for small/card previews with safe attachment fallback

## Self-Review

- Spec coverage: The plan covers GWT-compatible source selection, display-size bounds, oversized image containment, tests, changelog, and browser/manual evidence.
- Scope control: The plan does not touch upload flow, metadata fetch authorization, attachment storage, or #1167 thread behavior.
- Placeholder scan: No TBD/TODO placeholders remain. Browser verification has concrete acceptance bullets because the exact local wave may vary.
- Type consistency: Java tests and renderer code use existing classes and helper names already present in the codebase.
