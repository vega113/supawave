package org.waveprotocol.box.j2cl.attachment;

import java.util.Locale;

/** Read-surface rendering decision for one Wave attachment element. */
public final class J2clAttachmentRenderModel {
  private static final String DISPLAY_SMALL = "small";
  private static final String DISPLAY_MEDIUM = "medium";
  private static final String DISPLAY_LARGE = "large";
  private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
  private static final String DEFAULT_DOWNLOAD_FILE_NAME = "attachment";

  private final String attachmentId;
  private final String caption;
  private final String displaySize;
  private final String fileName;
  private final String mimeType;
  private final String sourceUrl;
  private final String openUrl;
  private final String downloadUrl;
  private final String downloadFileName;
  private final String statusText;
  private final boolean inlineImage;
  private final boolean blocked;
  private final boolean metadataFailure;
  private final boolean metadataPending;

  private J2clAttachmentRenderModel(
      String attachmentId,
      String caption,
      String displaySize,
      String fileName,
      String mimeType,
      String sourceUrl,
      String openUrl,
      String downloadUrl,
      String statusText,
      boolean inlineImage,
      boolean blocked,
      boolean metadataFailure,
      boolean metadataPending) {
    String safeSourceUrl = safeUrl(sourceUrl);
    this.attachmentId = normalize(attachmentId);
    this.caption = normalize(caption);
    // Empty source URLs mean there is no safe preview, so render as a compact card.
    this.displaySize =
        safeSourceUrl.isEmpty() && !blocked && !metadataFailure && !metadataPending
            ? DISPLAY_SMALL
            : normalizeDisplaySize(displaySize);
    this.fileName = normalize(fileName);
    this.mimeType = normalize(mimeType);
    this.sourceUrl = safeSourceUrl;
    this.openUrl = safeUrl(openUrl);
    this.downloadUrl = safeUrl(downloadUrl);
    this.downloadFileName = safeDownloadFileName(this.fileName, this.attachmentId);
    this.statusText = normalize(statusText);
    this.inlineImage = inlineImage && !this.sourceUrl.isEmpty();
    this.blocked = blocked;
    this.metadataFailure = metadataFailure;
    this.metadataPending = metadataPending;
  }

  public static J2clAttachmentRenderModel fromMetadata(
      String attachmentId,
      String caption,
      String requestedDisplaySize,
      J2clAttachmentMetadata metadata) {
    if (metadata == null) {
      return metadataFailure(
          attachmentId, caption, requestedDisplaySize, "metadata unavailable");
    }
    String normalizedAttachmentId = firstNonEmpty(attachmentId, metadata.getAttachmentId());
    String normalizedFileName =
        firstNonEmpty(metadata.getFileName(), caption, normalizedAttachmentId);
    String normalizedMimeType = firstNonEmpty(metadata.getMimeType(), DEFAULT_MIME_TYPE);
    String normalizedCaption = firstNonEmpty(caption, normalizedFileName, normalizedAttachmentId);
    String normalizedDisplaySize = normalizeDisplaySize(requestedDisplaySize);
    if (metadata.isMalware()) {
      return new J2clAttachmentRenderModel(
          normalizedAttachmentId,
          normalizedCaption,
          normalizedDisplaySize,
          normalizedFileName,
          normalizedMimeType,
          "",
          "",
          "",
          "Attachment blocked by malware scan.",
          false,
          true,
          false,
          false);
    }

    boolean image = isImageMimeType(normalizedMimeType);
    boolean hasImageDimensions = hasDimensions(metadata.getImageMetadata());
    boolean inlineImage =
        image
            && hasImageDimensions
            && (DISPLAY_MEDIUM.equals(normalizedDisplaySize)
                || DISPLAY_LARGE.equals(normalizedDisplaySize));
    String effectiveDisplaySize =
        image && !hasImageDimensions ? DISPLAY_SMALL : normalizedDisplaySize;
    // Inline images (medium/large with known dimensions) use the full attachment URL directly,
    // matching GWT's AttachmentDisplayLayout.SourceKind.ATTACHMENT path. Small previews and
    // non-image attachments prefer the thumbnail URL with a fallback to the attachment URL,
    // matching GWT's SourceKind.THUMBNAIL path.
    String sourceUrl =
        inlineImage
            ? safeUrl(metadata.getAttachmentUrl())
            : firstNonEmpty(
                safeUrl(metadata.getThumbnailUrl()), safeUrl(metadata.getAttachmentUrl()));

    return new J2clAttachmentRenderModel(
        normalizedAttachmentId,
        normalizedCaption,
        effectiveDisplaySize,
        normalizedFileName,
        normalizedMimeType,
        sourceUrl,
        metadata.getAttachmentUrl(),
        metadata.getAttachmentUrl(),
        "",
        inlineImage,
        false,
        false,
        false);
  }

  public static J2clAttachmentRenderModel metadataFailure(
      String attachmentId, String caption, String requestedDisplaySize, String reason) {
    String normalizedAttachmentId = normalize(attachmentId);
    String normalizedCaption = firstNonEmpty(caption, normalizedAttachmentId, "attachment");
    String normalizedReason = firstNonEmpty(reason, "Attachment metadata unavailable.");
    return new J2clAttachmentRenderModel(
        normalizedAttachmentId,
        normalizedCaption,
        requestedDisplaySize,
        normalizedCaption,
        DEFAULT_MIME_TYPE,
        "",
        "",
        "",
        metadataFailureStatus(normalizedReason),
        false,
        false,
        true,
        false);
  }

  public static J2clAttachmentRenderModel metadataPending(
      String attachmentId, String caption, String requestedDisplaySize) {
    String normalizedAttachmentId = normalize(attachmentId);
    String normalizedCaption = firstNonEmpty(caption, normalizedAttachmentId, "attachment");
    return new J2clAttachmentRenderModel(
        normalizedAttachmentId,
        normalizedCaption,
        requestedDisplaySize,
        normalizedCaption,
        DEFAULT_MIME_TYPE,
        "",
        "",
        "",
        "Attachment metadata loading...",
        false,
        false,
        false,
        true);
  }

  public String getAttachmentId() {
    return attachmentId;
  }

  public String getCaption() {
    return caption;
  }

  public String getDisplaySize() {
    return displaySize;
  }

  public String getFileName() {
    return fileName;
  }

  public String getMimeType() {
    return mimeType;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public String getOpenUrl() {
    return openUrl;
  }

  public String getDownloadUrl() {
    return downloadUrl;
  }

  public String getDownloadFileName() {
    return downloadFileName;
  }

  public String getStatusText() {
    return statusText;
  }

  public boolean isInlineImage() {
    return inlineImage;
  }

  public boolean isBlocked() {
    return blocked;
  }

  public boolean isMetadataFailure() {
    return metadataFailure;
  }

  public boolean isMetadataPending() {
    return metadataPending;
  }

  public boolean canOpen() {
    return !blocked && !metadataFailure && !metadataPending && !openUrl.isEmpty();
  }

  public boolean canDownload() {
    return !blocked && !metadataFailure && !metadataPending && !downloadUrl.isEmpty();
  }

  public String getOpenLabel() {
    return "Open attachment " + fileName + " (" + mimeType + ")";
  }

  public String getDownloadLabel() {
    return "Download attachment " + fileName + " (" + mimeType + ")";
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof J2clAttachmentRenderModel)) {
      return false;
    }
    J2clAttachmentRenderModel that = (J2clAttachmentRenderModel) other;
    return attachmentId.equals(that.attachmentId)
        && caption.equals(that.caption)
        && displaySize.equals(that.displaySize)
        && fileName.equals(that.fileName)
        && mimeType.equals(that.mimeType)
        && sourceUrl.equals(that.sourceUrl)
        && openUrl.equals(that.openUrl)
        && downloadUrl.equals(that.downloadUrl)
        && downloadFileName.equals(that.downloadFileName)
        && statusText.equals(that.statusText)
        && inlineImage == that.inlineImage
        && blocked == that.blocked
        && metadataFailure == that.metadataFailure
        && metadataPending == that.metadataPending;
  }

  @Override
  public int hashCode() {
    int result = attachmentId.hashCode();
    result = 31 * result + caption.hashCode();
    result = 31 * result + displaySize.hashCode();
    result = 31 * result + fileName.hashCode();
    result = 31 * result + mimeType.hashCode();
    result = 31 * result + sourceUrl.hashCode();
    result = 31 * result + openUrl.hashCode();
    result = 31 * result + downloadUrl.hashCode();
    result = 31 * result + downloadFileName.hashCode();
    result = 31 * result + statusText.hashCode();
    result = 31 * result + (inlineImage ? 1 : 0);
    result = 31 * result + (blocked ? 1 : 0);
    result = 31 * result + (metadataFailure ? 1 : 0);
    result = 31 * result + (metadataPending ? 1 : 0);
    return result;
  }

  private static String safeUrl(String value) {
    String normalized = normalize(value);
    if (containsControlCharacterOrSpace(normalized)) {
      return "";
    }
    // Root-relative paths are same-origin attachment endpoints, not executable URL schemes.
    if (normalized.startsWith("/") && !normalized.startsWith("//")) {
      return normalized;
    }
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (lower.startsWith("https://")) {
      return normalized;
    }
    return "";
  }

  private static boolean containsControlCharacterOrSpace(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c <= ' ' || c == 0x7F) {
        return true;
      }
    }
    return false;
  }

  private static String safeDownloadFileName(String fileName, String fallback) {
    String candidate =
        firstNonEmpty(fileName, fallback, DEFAULT_DOWNLOAD_FILE_NAME).replace('\\', '/');
    int slash = candidate.lastIndexOf('/');
    if (slash >= 0) {
      candidate = candidate.substring(slash + 1);
    }
    StringBuilder safe = new StringBuilder(candidate.length());
    for (int i = 0; i < candidate.length(); i++) {
      char c = candidate.charAt(i);
      safe.append(c <= ' ' || c == 0x7F || c == '/' || c == '\\' ? '_' : c);
    }
    String normalized = safe.toString().trim();
    if (normalized.isEmpty() || ".".equals(normalized) || "..".equals(normalized)) {
      return DEFAULT_DOWNLOAD_FILE_NAME;
    }
    return normalized;
  }

  private static boolean isImageMimeType(String mimeType) {
    return mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/");
  }

  private static boolean hasDimensions(J2clAttachmentMetadata.ImageMetadata imageMetadata) {
    return imageMetadata != null && imageMetadata.getWidth() > 0 && imageMetadata.getHeight() > 0;
  }

  private static String metadataFailureStatus(String reason) {
    if (reason.toLowerCase(Locale.ROOT).startsWith("attachment metadata unavailable")) {
      return reason;
    }
    return "Attachment metadata unavailable: " + reason;
  }

  private static String normalizeDisplaySize(String displaySize) {
    String normalized = normalize(displaySize).toLowerCase(Locale.ROOT);
    if (DISPLAY_MEDIUM.equals(normalized) || DISPLAY_LARGE.equals(normalized)) {
      return normalized;
    }
    return DISPLAY_SMALL;
  }

  private static String firstNonEmpty(String first, String second) {
    return firstNonEmpty(first, second, "");
  }

  private static String firstNonEmpty(String first, String second, String fallback) {
    String normalizedFirst = normalize(first);
    if (!normalizedFirst.isEmpty()) {
      return normalizedFirst;
    }
    String normalizedSecond = normalize(second);
    return normalizedSecond.isEmpty() ? normalize(fallback) : normalizedSecond;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
