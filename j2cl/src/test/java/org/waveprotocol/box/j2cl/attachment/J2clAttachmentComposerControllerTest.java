package org.waveprotocol.box.j2cl.attachment;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.richtext.J2clComposerDocument;
import org.waveprotocol.box.j2cl.richtext.J2clRichContentDeltaFactory;

@J2clTestInput(J2clAttachmentComposerControllerTest.class)
public class J2clAttachmentComposerControllerTest {
  private static final String WAVE_REF = "example.com/w+wave/~/conv+root";

  @Test
  public void selectingFilesStartsUploadQueueInOrderAndGeneratesPerFileIds() {
    FakeUploadTransport transport = new FakeUploadTransport();
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    Object firstPayload = new Object();
    Object secondPayload = new Object();

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                firstPayload,
                "first.png",
                "First caption",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                secondPayload,
                "second.png",
                "Second caption",
                J2clAttachmentComposerController.DisplaySize.LARGE)));

    Assert.assertEquals(1, transport.requests.size());
    Assert.assertEquals(
        "/attachment/example.com/seedA", transport.requests.get(0).getUrl());
    Assert.assertSame(firstPayload, transport.requests.get(0).getPart(2).getPayload());
    Assert.assertEquals("first.png", transport.requests.get(0).getPart(2).getFileName());
    Assert.assertEquals(2, controller.getQueueSnapshot().size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.QUEUED,
        controller.getQueueSnapshot().get(1).getStatus());

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals(
        "/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    Assert.assertSame(secondPayload, transport.requests.get(1).getPart(2).getPayload());
    Assert.assertEquals("second.png", transport.requests.get(1).getPart(2).getFileName());
    Assert.assertEquals(1, insertionCallback.insertions.size());
  }

  @Test
  public void invalidSelectionBatchDoesNotPartiallyQueueOrConsumeIds() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    try {
      controller.selectFiles(
          Arrays.asList(
              J2clAttachmentComposerController.AttachmentSelection.file(
                  new Object(),
                  "partial.png",
                  "",
                  J2clAttachmentComposerController.DisplaySize.SMALL),
              null));
      Assert.fail("Expected invalid selection batch to fail.");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }

    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
    Assert.assertTrue(transport.requests.isEmpty());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "next.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(
        "/attachment/example.com/seedA", transport.requests.get(0).getUrl());
  }

  @Test
  public void progressUpdatesActiveItemState() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "progress.png",
                "",
                J2clAttachmentComposerController.DisplaySize.MEDIUM)));
    transport.requests.get(0).getProgressCallback().onProgress(47);

    J2clAttachmentComposerController.UploadItem item = controller.getQueueSnapshot().get(0);
    Assert.assertEquals(J2clAttachmentComposerController.UploadStatus.UPLOADING, item.getStatus());
    Assert.assertEquals(47, item.getProgressPercent());
  }

  @Test
  public void uploadFailureMarksItemFailedAndDoesNotInsertDocument() {
    FakeUploadTransport transport = new FakeUploadTransport();
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "broken.png",
                "",
                J2clAttachmentComposerController.DisplaySize.MEDIUM)));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(500, "OK", null));

    J2clAttachmentComposerController.UploadItem item = controller.getQueueSnapshot().get(0);
    Assert.assertEquals(J2clAttachmentComposerController.UploadStatus.FAILED, item.getStatus());
    Assert.assertEquals(
        J2clAttachmentUploadClient.ErrorType.HTTP_STATUS.name(), item.getErrorCode());
    Assert.assertTrue(item.getErrorMessage().contains("HTTP 500"));
    Assert.assertTrue(insertionCallback.insertions.isEmpty());
  }

  @Test
  public void failedUploadStartsNextQueuedItem() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "broken.png",
                "",
                J2clAttachmentComposerController.DisplaySize.MEDIUM),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "next.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(500, "nope", null));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(1).getStatus());
    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
  }

  @Test
  public void cancelResetClearsQueueAndIgnoresLateUploadCompletion() {
    FakeUploadTransport transport = new FakeUploadTransport();
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "cancel.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "queued.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    controller.cancelAndReset();
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
    Assert.assertTrue(insertionCallback.insertions.isEmpty());
    Assert.assertEquals(1, transport.requests.size());
  }

  @Test
  public void cancelResetAllowsReuseWithNextId() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "cancel.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    controller.cancelAndReset();
    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "next.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    Assert.assertEquals(1, controller.getQueueSnapshot().size());
  }

  @Test
  public void successfulFileUploadInvokesInsertionWithCaptionFallbackAndDisplaySize() {
    FakeUploadTransport transport = new FakeUploadTransport();
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "fallback.png",
                "   ",
                J2clAttachmentComposerController.DisplaySize.LARGE)));

    Assert.assertTrue(insertionCallback.insertions.isEmpty());
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(1, insertionCallback.insertions.size());
    J2clAttachmentComposerController.AttachmentInsertion insertion =
        insertionCallback.insertions.get(0);
    Assert.assertEquals("example.com/seedA", insertion.getAttachmentId());
    Assert.assertEquals("fallback.png", insertion.getCaption());
    Assert.assertEquals(
        J2clAttachmentComposerController.DisplaySize.LARGE, insertion.getDisplaySize());
    assertDocumentContainsAttachment(
        insertionCallback.documents.get(0),
        "example.com/seedA",
        "fallback.png",
        "large");
  }

  @Test
  public void additionalSelectionsWaitBehindActiveUpload() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.MEDIUM)));
    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.MEDIUM)));

    Assert.assertEquals(1, transport.requests.size());

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
  }

  @Test
  public void queueSnapshotIsReadOnly() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "snapshot.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    try {
      controller.getQueueSnapshot().add(null);
      Assert.fail("Expected snapshot to be immutable.");
    } catch (UnsupportedOperationException expected) {
      // Expected.
    }
  }

  @Test
  public void pastedImageUploadCapturesIntentAndMutatesOnlyAfterSuccess() {
    FakeUploadTransport transport = new FakeUploadTransport();
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    Object imagePayload = new Object();

    controller.pasteImage(
        imagePayload,
        "paste caption",
        J2clAttachmentComposerController.DisplaySize.MEDIUM);

    Assert.assertEquals(1, transport.requests.size());
    Assert.assertSame(imagePayload, transport.requests.get(0).getPart(2).getPayload());
    Assert.assertEquals("pasted-image.png", transport.requests.get(0).getPart(2).getFileName());
    Assert.assertTrue(insertionCallback.insertions.isEmpty());

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(201, "stored", null));

    Assert.assertEquals(1, insertionCallback.insertions.size());
    Assert.assertEquals("paste caption", insertionCallback.insertions.get(0).getCaption());
    assertDocumentContainsAttachment(
        insertionCallback.documents.get(0),
        "example.com/seedA",
        "paste caption",
        "medium");
  }

  @Test
  public void invalidPastedImageDoesNotQueueStartUploadOrConsumeIds() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    try {
      controller.pasteImage(
          null,
          "paste caption",
          J2clAttachmentComposerController.DisplaySize.MEDIUM);
      Assert.fail("Expected invalid pasted image to fail.");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }

    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
    Assert.assertTrue(transport.requests.isEmpty());

    controller.pasteImage(
        new Object(),
        "valid paste",
        J2clAttachmentComposerController.DisplaySize.MEDIUM);

    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
  }

  @Test
  public void pastedImageWaitsBehindActiveFileUpload() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    Object pastedPayload = new Object();
    controller.pasteImage(
        pastedPayload,
        "paste caption",
        J2clAttachmentComposerController.DisplaySize.MEDIUM);

    Assert.assertEquals(1, transport.requests.size());

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    Assert.assertSame(pastedPayload, transport.requests.get(1).getPart(2).getPayload());
    Assert.assertEquals("pasted-image.png", transport.requests.get(1).getPart(2).getFileName());
  }

  @Test
  public void pastedImageFailureDoesNotMutateDocument() {
    FakeUploadTransport transport = new FakeUploadTransport();
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.pasteImage(
        new Object(),
        "paste caption",
        J2clAttachmentComposerController.DisplaySize.MEDIUM);
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(202, "accepted", null));

    Assert.assertTrue(insertionCallback.insertions.isEmpty());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
  }

  private static J2clAttachmentComposerController newController(
      FakeUploadTransport transport, RecordingInsertionCallback insertionCallback) {
    return new J2clAttachmentComposerController(
        WAVE_REF,
        new J2clAttachmentUploadClient(transport),
        new J2clAttachmentIdGenerator("example.com", "seed"),
        insertionCallback);
  }

  private static void assertDocumentContainsAttachment(
      J2clComposerDocument document, String attachmentId, String caption, String displaySize) {
    String deltaJson =
        new J2clRichContentDeltaFactory("verify")
            .createWaveRequest("user@example.com", document)
            .getSubmitRequest()
            .getDeltaJson();
    assertContains(
        deltaJson,
        "{\"1\":\"attachment\",\"2\":\"" + attachmentId + "\"}",
        "{\"1\":\"display-size\",\"2\":\"" + displaySize + "\"}",
        "\"2\":\"" + caption + "\"");
  }

  private static void assertContains(String value, String... expectedSubstrings) {
    for (String expectedSubstring : expectedSubstrings) {
      Assert.assertTrue(
          "Expected to find <" + expectedSubstring + "> in <" + value + ">",
          value.contains(expectedSubstring));
    }
  }

  private static final class FakeUploadTransport
      implements J2clAttachmentUploadClient.UploadTransport {
    private final List<J2clAttachmentUploadClient.MultipartUploadRequest> requests =
        new ArrayList<J2clAttachmentUploadClient.MultipartUploadRequest>();
    private final List<J2clAttachmentUploadClient.ResponseHandler> handlers =
        new ArrayList<J2clAttachmentUploadClient.ResponseHandler>();

    @Override
    public void post(
        J2clAttachmentUploadClient.MultipartUploadRequest request,
        J2clAttachmentUploadClient.ResponseHandler handler) {
      requests.add(request);
      handlers.add(handler);
    }

    void complete(int index, J2clAttachmentUploadClient.HttpResponse response) {
      handlers.get(index).onResponse(response);
    }
  }

  private static final class RecordingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    private final List<J2clAttachmentComposerController.AttachmentInsertion> insertions =
        new ArrayList<J2clAttachmentComposerController.AttachmentInsertion>();
    private final List<J2clComposerDocument> documents = new ArrayList<J2clComposerDocument>();

    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      documents.add(document);
      insertions.add(insertion);
    }
  }
}
