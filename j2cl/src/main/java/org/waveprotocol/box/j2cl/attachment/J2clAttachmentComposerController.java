package org.waveprotocol.box.j2cl.attachment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.richtext.J2clComposerDocument;

/** Controller-domain layer for composer attachment selection, upload, and insertion callbacks. */
public final class J2clAttachmentComposerController {
  /** Error code value used when upload succeeds but composer document insertion fails. */
  public static final String INSERT_FAILED_ERROR_CODE = "INSERT_FAILED";

  public interface DocumentInsertionCallback {
    /**
     * Runtime exceptions escaping the callback, including from re-entrant controller calls, are
     * contained as {@link UploadStatus#INSERT_FAILED}. VM errors are not contained; completion
     * cleanup still runs, so a queued upload can start before the error reaches the caller. If
     * the callback resets the controller before throwing, stale item mutations are dropped.
     */
    void onInsert(J2clComposerDocument document, AttachmentInsertion insertion);
  }

  public enum DisplaySize {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large");

    private final String documentValue;

    DisplaySize(String documentValue) {
      this.documentValue = documentValue;
    }

    public String getDocumentValue() {
      return documentValue;
    }
  }

  public enum UploadStatus {
    QUEUED,
    UPLOADING,
    COMPLETE,
    FAILED,
    INSERT_FAILED
  }

  public static final class AttachmentSelection {
    private final Object payload;
    private final String fileName;
    private final String caption;
    private final DisplaySize displaySize;
    private final boolean pastedImage;

    private AttachmentSelection(
        Object payload,
        String fileName,
        String caption,
        DisplaySize displaySize,
        boolean pastedImage) {
      this.payload = requirePresent(payload, "Attachment payload is required.");
      this.fileName = requireNonEmpty(fileName, "Attachment file name is required.");
      this.caption = caption == null ? "" : caption;
      this.displaySize = requirePresent(displaySize, "Attachment display size is required.");
      this.pastedImage = pastedImage;
    }

    public static AttachmentSelection file(
        Object payload, String fileName, String caption, DisplaySize displaySize) {
      return new AttachmentSelection(payload, fileName, caption, displaySize, false);
    }

    public static AttachmentSelection pastedImage(
        Object payload, String caption, DisplaySize displaySize) {
      return new AttachmentSelection(
          payload, J2clAttachmentUploadClient.PASTED_IMAGE_FILENAME, caption, displaySize, true);
    }

    public Object getPayload() {
      return payload;
    }

    public String getFileName() {
      return fileName;
    }

    public String getCaption() {
      return caption;
    }

    public DisplaySize getDisplaySize() {
      return displaySize;
    }

    public boolean isPastedImage() {
      return pastedImage;
    }
  }

  public static final class AttachmentInsertion {
    private final String attachmentId;
    private final String caption;
    private final DisplaySize displaySize;

    private AttachmentInsertion(String attachmentId, String caption, DisplaySize displaySize) {
      this.attachmentId = attachmentId;
      this.caption = caption;
      this.displaySize = displaySize;
    }

    public String getAttachmentId() {
      return attachmentId;
    }

    public String getCaption() {
      return caption;
    }

    public DisplaySize getDisplaySize() {
      return displaySize;
    }
  }

  public static final class UploadItem {
    private final String attachmentId;
    private final String fileName;
    private final String caption;
    private final DisplaySize displaySize;
    private final UploadStatus status;
    private final int progressPercent;
    private final String errorCode;
    private final String errorMessage;

    private UploadItem(QueueItem item) {
      this.attachmentId = item.attachmentId;
      this.fileName = item.fileName;
      this.caption = item.caption();
      this.displaySize = item.displaySize;
      this.status = item.status;
      this.progressPercent = item.progressPercent;
      this.errorCode = item.errorCode;
      this.errorMessage = item.errorMessage;
    }

    public String getAttachmentId() {
      return attachmentId;
    }

    public String getFileName() {
      return fileName;
    }

    public String getCaption() {
      return caption;
    }

    public DisplaySize getDisplaySize() {
      return displaySize;
    }

    public UploadStatus getStatus() {
      return status;
    }

    public int getProgressPercent() {
      return progressPercent;
    }

    /**
     * Returns an empty string when there is no error, an upload {@code ErrorType.name()}, or
     * {@code "INSERT_FAILED"} when document insertion fails after upload success.
     */
    public String getErrorCode() {
      return errorCode;
    }

    /** Returns an empty string when there is no error. */
    public String getErrorMessage() {
      return errorMessage;
    }
  }

  private static final class QueueItem {
    private final String attachmentId;
    private final String fileName;
    private final String caption;
    private final DisplaySize displaySize;
    private final boolean pastedImage;
    private Object payload;
    private UploadStatus status = UploadStatus.QUEUED;
    private int progressPercent;
    private String errorCode = "";
    private String errorMessage = "";

    private QueueItem(String attachmentId, AttachmentSelection selection) {
      this.attachmentId = attachmentId;
      this.fileName = selection.fileName;
      this.caption = selection.caption;
      this.displaySize = selection.displaySize;
      this.pastedImage = selection.pastedImage;
      this.payload = selection.payload;
    }

    private String caption() {
      String trimmed = caption.trim();
      return trimmed.isEmpty() ? fileName : trimmed;
    }
  }

  private final String waveRef;
  private final J2clAttachmentUploadClient uploadClient;
  private final J2clAttachmentIdGenerator idGenerator;
  private final DocumentInsertionCallback insertionCallback;
  // Terminal items stay visible until the composer lifecycle calls cancelAndReset or a future
  // explicit clear; this keeps status/error reporting available to the Lit wiring task.
  private final List<QueueItem> queue = new ArrayList<QueueItem>();
  private boolean uploadInProgress;
  private int resetGeneration;

  public J2clAttachmentComposerController(
      String waveRef,
      J2clAttachmentUploadClient uploadClient,
      J2clAttachmentIdGenerator idGenerator,
      DocumentInsertionCallback insertionCallback) {
    this.waveRef = requireNonEmpty(waveRef, "Wave ref is required.");
    this.uploadClient = requirePresent(uploadClient, "Attachment upload client is required.");
    this.idGenerator = requirePresent(idGenerator, "Attachment id generator is required.");
    this.insertionCallback =
        requirePresent(insertionCallback, "Attachment insertion callback is required.");
  }

  public void selectFiles(List<AttachmentSelection> selections) {
    if (selections == null) {
      throw new IllegalArgumentException("Attachment selections are required.");
    }
    // Field validity is enforced by AttachmentSelection factories; this pass keeps batch enqueue
    // atomic by rejecting null entries before consuming any attachment ids.
    List<AttachmentSelection> validatedSelections =
        new ArrayList<AttachmentSelection>(selections.size());
    for (AttachmentSelection selection : selections) {
      validatedSelections.add(requirePresent(selection, "Attachment selection is required."));
    }
    for (AttachmentSelection selection : validatedSelections) {
      enqueue(selection);
    }
    startNextUpload();
  }

  public void pasteImage(Object imagePayload, String caption, DisplaySize displaySize) {
    AttachmentSelection selection =
        AttachmentSelection.pastedImage(imagePayload, caption, displaySize);
    enqueue(selection);
    startNextUpload();
  }

  private void enqueue(AttachmentSelection selection) {
    queue.add(new QueueItem(idGenerator.nextAttachmentId(), selection));
  }

  public List<UploadItem> getQueueSnapshot() {
    List<UploadItem> snapshot = new ArrayList<UploadItem>();
    for (QueueItem item : queue) {
      snapshot.add(new UploadItem(item));
    }
    return Collections.unmodifiableList(snapshot);
  }

  /**
   * Clears controller queue state and ignores late callbacks from the active upload generation.
   *
   * <p>This does not abort the underlying browser transport request; the current upload may
   * continue on the network, but its eventual progress or completion callback will be ignored.
   * The attachment id generator is intentionally not reset so later selections keep globally
   * unique ids.
   */
  public void cancelAndReset() {
    resetGeneration++;
    uploadInProgress = false;
    queue.clear();
  }

  private void startNextUpload() {
    if (uploadInProgress) {
      return;
    }
    QueueItem item = firstQueuedItem();
    if (item == null) {
      return;
    }
    uploadInProgress = true;
    item.status = UploadStatus.UPLOADING;
    int generation = resetGeneration;
    J2clAttachmentUploadClient.UploadProgressCallback progressCallback =
        percent -> {
          if (generation == resetGeneration && item.status == UploadStatus.UPLOADING) {
            item.progressPercent = clampPercent(percent);
          }
        };
    J2clAttachmentUploadClient.UploadCallback uploadCallback =
        result -> handleUploadComplete(generation, item, result);
    if (item.pastedImage) {
      uploadClient.uploadPastedImage(
          item.attachmentId, waveRef, item.payload, progressCallback, uploadCallback);
    } else {
      uploadClient.uploadFile(
          item.attachmentId,
          waveRef,
          item.payload,
          item.fileName,
          progressCallback,
          uploadCallback);
    }
  }

  private QueueItem firstQueuedItem() {
    for (QueueItem item : queue) {
      if (item.status == UploadStatus.QUEUED) {
        return item;
      }
    }
    return null;
  }

  private void handleUploadComplete(
      int generation, QueueItem item, J2clAttachmentUploadClient.UploadResult result) {
    if (generation != resetGeneration) {
      item.payload = null;
      return;
    }
    // This must be false before insertAttachment so re-entrant selectFiles/pasteImage can start.
    uploadInProgress = false;
    try {
      if (result != null && result.isSuccess()) {
        item.status = UploadStatus.COMPLETE;
        item.progressPercent = 100;
        try {
          insertAttachment(item);
        } catch (RuntimeException e) {
          if (generation == resetGeneration) {
            item.status = UploadStatus.INSERT_FAILED;
            item.errorCode = INSERT_FAILED_ERROR_CODE;
            item.errorMessage =
                e.getMessage() == null ? "Attachment insertion failed." : e.getMessage();
          }
        }
      } else {
        item.status = UploadStatus.FAILED;
        J2clAttachmentUploadClient.ErrorType errorType =
            result == null ? J2clAttachmentUploadClient.ErrorType.NETWORK : result.getErrorType();
        item.errorCode = errorType == null ? "" : errorType.name();
        item.errorMessage =
            result == null ? "Attachment upload failed without a result." : result.getMessage();
      }
    } finally {
      item.payload = null;
      // onInsert can enqueue or cancel; startNextUpload is idempotent for those re-entrant paths.
      startNextUpload();
    }
  }

  private void insertAttachment(QueueItem item) {
    AttachmentInsertion insertion =
        new AttachmentInsertion(item.attachmentId, item.caption(), item.displaySize);
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .imageAttachment(
                insertion.getAttachmentId(),
                insertion.getCaption(),
                insertion.getDisplaySize().getDocumentValue())
            .build();
    insertionCallback.onInsert(document, insertion);
  }

  private static String requireNonEmpty(String value, String message) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return trimmed;
  }

  private static int clampPercent(int percent) {
    if (percent < 0) {
      return 0;
    }
    if (percent > 100) {
      return 100;
    }
    return percent;
  }

  private static <T> T requirePresent(T value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }
}
