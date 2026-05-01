package org.waveprotocol.box.j2cl.attachment;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clAttachmentRenderModelTest.class)
public class J2clAttachmentRenderModelTest {
  @Test
  public void mediumInlineImageUsesAttachmentUrlForBothPreviewAndOpen() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+hero",
            "Hero diagram",
            "medium",
            metadata(
                "example.com/att+hero",
                "hero.png",
                "image/png",
                "/attachment/example.com/att+hero",
                "/thumbnail/example.com/att+hero",
                new J2clAttachmentMetadata.ImageMetadata(1200, 800),
                false));

    Assert.assertTrue(model.isInlineImage());
    Assert.assertEquals("medium", model.getDisplaySize());
    Assert.assertEquals("/attachment/example.com/att+hero", model.getSourceUrl());
    Assert.assertEquals("/attachment/example.com/att+hero", model.getOpenUrl());
    Assert.assertEquals("/attachment/example.com/att+hero", model.getDownloadUrl());
    Assert.assertEquals("hero.png", model.getDownloadFileName());
    Assert.assertEquals("Open attachment hero.png (image/png)", model.getOpenLabel());
    Assert.assertEquals("Download attachment hero.png (image/png)", model.getDownloadLabel());
  }

  @Test
  public void nonImageStaysCardBasedAndUsesThumbnailSource() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+pdf",
            "Spec",
            "large",
            metadata(
                "example.com/att+pdf",
                "spec.pdf",
                "application/pdf",
                "/attachment/example.com/att+pdf",
                "/thumbnail/example.com/att+pdf",
                null,
                false));

    Assert.assertFalse(model.isInlineImage());
    Assert.assertEquals("large", model.getDisplaySize());
    Assert.assertEquals("/thumbnail/example.com/att+pdf", model.getSourceUrl());
    Assert.assertEquals("/attachment/example.com/att+pdf", model.getOpenUrl());
  }

  @Test
  public void imageMissingDimensionsFallsBackToSmallTile() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+pending",
            "Pending image",
            "large",
            metadata(
                "example.com/att+pending",
                "pending.png",
                "image/png",
                "/attachment/example.com/att+pending",
                "/thumbnail/example.com/att+pending",
                null,
                false));

    Assert.assertFalse(model.isInlineImage());
    Assert.assertEquals("small", model.getDisplaySize());
    Assert.assertEquals("/thumbnail/example.com/att+pending", model.getSourceUrl());
  }

  @Test
  public void smallImageWithDimensionsUsesThumbnailTile() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+small",
            "Small image",
            "small",
            metadata(
                "example.com/att+small",
                "small.png",
                "image/png",
                "/attachment/example.com/att+small",
                "/thumbnail/example.com/att+small",
                new J2clAttachmentMetadata.ImageMetadata(320, 200),
                false));

    Assert.assertFalse(model.isInlineImage());
    Assert.assertEquals("small", model.getDisplaySize());
    Assert.assertEquals("/thumbnail/example.com/att+small", model.getSourceUrl());
  }

  @Test
  public void nonImageWithoutThumbnailFallsBackToAttachmentUrl() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+pdf",
            "Spec",
            "small",
            metadata(
                "example.com/att+pdf",
                "spec.pdf",
                "application/pdf",
                "/attachment/example.com/att+pdf",
                "",
                null,
                false));

    Assert.assertFalse(model.isInlineImage());
    Assert.assertEquals("/attachment/example.com/att+pdf", model.getSourceUrl());
  }

  @Test
  public void unsafeAttachmentUrlsDisablePreviewOpenAndDownload() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+unsafe",
            "Unsafe",
            "medium",
            metadata(
                "example.com/att+unsafe",
                "unsafe.png",
                "image/png",
                "javascript:alert(1)",
                "data:image/png;base64,AAAA",
                new J2clAttachmentMetadata.ImageMetadata(320, 200),
                false));

    Assert.assertEquals("", model.getSourceUrl());
    Assert.assertEquals("", model.getOpenUrl());
    Assert.assertFalse(model.canOpen());
    Assert.assertFalse(model.canDownload());
  }

  @Test
  public void protocolRelativeAndHttpUrlsAreRejected() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+unsafe",
            "Unsafe",
            "medium",
            metadata(
                "example.com/att+unsafe",
                "unsafe.png",
                "image/png",
                "http://cdn.example.test/unsafe.png",
                "//cdn.example.test/unsafe-thumbnail.png",
                new J2clAttachmentMetadata.ImageMetadata(320, 200),
                false));

    Assert.assertEquals("", model.getSourceUrl());
    Assert.assertEquals("", model.getOpenUrl());
    Assert.assertEquals("small", model.getDisplaySize());
    Assert.assertFalse(model.isInlineImage());
  }

  @Test
  public void unsafeThumbnailFallsBackToSafeAttachmentPreview() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+fallback",
            "Fallback",
            "medium",
            metadata(
                "example.com/att+fallback",
                "fallback.png",
                "image/png",
                "/attachment/example.com/att+fallback",
                "http://cdn.example.test/fallback-thumb.png",
                new J2clAttachmentMetadata.ImageMetadata(640, 480),
                false));

    Assert.assertTrue(model.isInlineImage());
    Assert.assertEquals("/attachment/example.com/att+fallback", model.getSourceUrl());
    Assert.assertEquals("/attachment/example.com/att+fallback", model.getOpenUrl());
  }

  @Test
  public void httpsUrlsAreAcceptedAndControlCharactersAreRejected() {
    J2clAttachmentRenderModel accepted =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+https",
            "Https",
            "medium",
            metadata(
                "example.com/att+https",
                "https.png",
                "image/png",
                "https://cdn.example.test/https.png",
                "https://cdn.example.test/https-thumb.png",
                new J2clAttachmentMetadata.ImageMetadata(320, 200),
                false));
    J2clAttachmentRenderModel rejected =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+control",
            "Control",
            "medium",
            metadata(
                "example.com/att+control",
                "control.png",
                "image/png",
                "https://cdn.example.test/control\tbad.png",
                "/thumbnail/example.com/control\r\nbad.png",
                new J2clAttachmentMetadata.ImageMetadata(320, 200),
                false));

    Assert.assertEquals("https://cdn.example.test/https.png", accepted.getSourceUrl());
    Assert.assertTrue(accepted.isInlineImage());
    Assert.assertEquals("", rejected.getOpenUrl());
    Assert.assertFalse(rejected.canOpen());
  }

  @Test
  public void malwareMetadataBlocksOpenAndDownloadControls() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+blocked",
            "Blocked",
            "small",
            metadata(
                "example.com/att+blocked",
                "blocked.exe",
                "application/octet-stream",
                "/attachment/example.com/att+blocked",
                "",
                null,
                true));

    Assert.assertTrue(model.isBlocked());
    Assert.assertFalse(model.canOpen());
    Assert.assertFalse(model.canDownload());
    Assert.assertTrue(model.getStatusText().contains("blocked"));
  }

  @Test
  public void metadataFailureSurfacesErrorState() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.metadataFailure(
            "example.com/att+missing", "Missing", "medium", "metadata endpoint failed");

    Assert.assertTrue(model.isMetadataFailure());
    Assert.assertFalse(model.canOpen());
    Assert.assertFalse(model.canDownload());
    Assert.assertEquals("medium", model.getDisplaySize());
    Assert.assertTrue(model.getStatusText().contains("metadata endpoint failed"));
  }

  @Test
  public void metadataPendingIsDistinctFromFailure() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.metadataPending(
            "example.com/att+pending", "Pending", "medium");

    Assert.assertTrue(model.isMetadataPending());
    Assert.assertFalse(model.isMetadataFailure());
    Assert.assertFalse(model.canOpen());
    Assert.assertFalse(model.canDownload());
    Assert.assertEquals("Attachment metadata loading...", model.getStatusText());
  }

  @Test
  public void nullMetadataUsesFailureWithoutDuplicatingStatusPrefix() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+missing", "Missing", "small", null);

    Assert.assertTrue(model.isMetadataFailure());
    Assert.assertEquals(
        "Attachment metadata unavailable: metadata unavailable", model.getStatusText());
  }

  @Test
  public void downloadFileNameFallsBackToSafeLastPathSegment() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+hero",
            "",
            "small",
            metadata(
                "example.com/att+hero",
                "",
                "application/octet-stream",
                "/attachment/example.com/att+hero",
                "",
                null,
                false));

    Assert.assertEquals("att+hero", model.getDownloadFileName());
  }

  @Test
  public void downloadFileNameRejectsEmptyDotAndDotDotNames() {
    J2clAttachmentRenderModel whitespace =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+blank",
            "",
            "small",
            metadata(
                "example.com/att+blank",
                " ",
                "application/octet-stream",
                "/attachment/example.com/att+blank",
                "",
                null,
                false));
    J2clAttachmentRenderModel dot =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+dot",
            "",
            "small",
            metadata(
                "example.com/att+dot",
                ".",
                "application/octet-stream",
                "/attachment/example.com/att+dot",
                "",
                null,
                false));
    J2clAttachmentRenderModel dotDot =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+dotdot",
            "",
            "small",
            metadata(
                "example.com/att+dotdot",
                "..",
                "application/octet-stream",
                "/attachment/example.com/att+dotdot",
                "",
                null,
                false));

    Assert.assertEquals("att+blank", whitespace.getDownloadFileName());
    Assert.assertEquals("attachment", dot.getDownloadFileName());
    Assert.assertEquals("attachment", dotDot.getDownloadFileName());
  }

  @Test
  public void emptySourceUrlsForceSmallDisplaySize() {
    J2clAttachmentRenderModel model =
        J2clAttachmentRenderModel.fromMetadata(
            "example.com/att+missing",
            "Missing",
            "large",
            metadata(
                "example.com/att+missing",
                "missing.png",
                "image/png",
                "",
                "",
                new J2clAttachmentMetadata.ImageMetadata(320, 200),
                false));

    Assert.assertEquals("small", model.getDisplaySize());
    Assert.assertEquals("", model.getSourceUrl());
    Assert.assertFalse(model.isInlineImage());
  }

  private static J2clAttachmentMetadata metadata(
      String attachmentId,
      String fileName,
      String mimeType,
      String attachmentUrl,
      String thumbnailUrl,
      J2clAttachmentMetadata.ImageMetadata imageMetadata,
      boolean malware) {
    return new J2clAttachmentMetadata(
        attachmentId,
        "example.com/w+1/~/conv+root",
        fileName,
        mimeType,
        1234L,
        "user@example.com",
        attachmentUrl,
        thumbnailUrl,
        imageMetadata,
        null,
        malware);
  }
}
