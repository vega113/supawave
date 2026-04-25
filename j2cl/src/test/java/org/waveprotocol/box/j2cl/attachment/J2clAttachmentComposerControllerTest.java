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
  public void emptySelectionListDoesNothing() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    controller.selectFiles(Arrays.asList());

    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
    Assert.assertTrue(transport.requests.isEmpty());
  }

  @Test
  public void nullSelectionListDoesNotQueueStartUploadOrConsumeIds() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    try {
      controller.selectFiles(null);
      Assert.fail("Expected null selection list to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("selections"));
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

    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
  }

  @Test
  public void invalidFileSelectionFactoryDoesNotQueueStartUploadOrConsumeIds() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    assertInvalidFileSelection(
        null,
        "file.png",
        J2clAttachmentComposerController.DisplaySize.SMALL,
        "payload");
    assertInvalidFileSelection(
        new Object(),
        "   ",
        J2clAttachmentComposerController.DisplaySize.SMALL,
        "file name");
    assertInvalidFileSelection(
        new Object(),
        "file.png",
        null,
        "display size");

    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
    Assert.assertTrue(transport.requests.isEmpty());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "valid.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals("/attachment/example.com/seedA", transport.requests.get(0).getUrl());
  }

  @Test
  public void fileSelectionAndWaveRefWhitespaceIsTrimmedBeforeUpload() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        new J2clAttachmentComposerController(
            "  " + WAVE_REF + "  ",
            new J2clAttachmentUploadClient(transport),
            new J2clAttachmentIdGenerator("example.com", "seed"),
            new RecordingInsertionCallback());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "  spaced.png  ",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(WAVE_REF, transport.requests.get(0).getPart(1).getStringValue());
    Assert.assertEquals("spaced.png", transport.requests.get(0).getPart(2).getFileName());
  }

  @Test
  public void blankWaveRefIsRejected() {
    try {
      new J2clAttachmentComposerController(
          "   ",
          new J2clAttachmentUploadClient(new FakeUploadTransport()),
          new J2clAttachmentIdGenerator("example.com", "seed"),
          new RecordingInsertionCallback());
      Assert.fail("Expected blank wave ref to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("Wave ref"));
    }
  }

  @Test
  public void constructorRejectsNullUploadClient() {
    try {
      new J2clAttachmentComposerController(
          WAVE_REF,
          null,
          new J2clAttachmentIdGenerator("example.com", "seed"),
          new RecordingInsertionCallback());
      Assert.fail("Expected null upload client to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("upload client"));
    }
  }

  @Test
  public void constructorRejectsNullIdGenerator() {
    try {
      new J2clAttachmentComposerController(
          WAVE_REF,
          new J2clAttachmentUploadClient(new FakeUploadTransport()),
          null,
          new RecordingInsertionCallback());
      Assert.fail("Expected null id generator to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("id generator"));
    }
  }

  @Test
  public void constructorRejectsNullInsertionCallback() {
    try {
      new J2clAttachmentComposerController(
          WAVE_REF,
          new J2clAttachmentUploadClient(new FakeUploadTransport()),
          new J2clAttachmentIdGenerator("example.com", "seed"),
          null);
      Assert.fail("Expected null insertion callback to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("insertion callback"));
    }
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

    transport.requests.get(0).getProgressCallback().onProgress(68);

    Assert.assertEquals(68, controller.getQueueSnapshot().get(0).getProgressPercent());
  }

  @Test
  public void progressIsClampedToValidPercentRange() {
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
    transport.requests.get(0).getProgressCallback().onProgress(-3);

    Assert.assertEquals(0, controller.getQueueSnapshot().get(0).getProgressPercent());

    transport.requests.get(0).getProgressCallback().onProgress(200);

    Assert.assertEquals(100, controller.getQueueSnapshot().get(0).getProgressPercent());
  }

  @Test
  public void progressAfterTerminalStatusIsIgnored() {
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

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    transport.requests.get(0).getProgressCallback().onProgress(12);

    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.COMPLETE,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(100, controller.getQueueSnapshot().get(0).getProgressPercent());
  }

  @Test
  public void progressAfterCancelResetIsIgnored() {
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
    controller.cancelAndReset();
    transport.requests.get(0).getProgressCallback().onProgress(88);

    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
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

    transport.requests.get(0).getProgressCallback().onProgress(88);

    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(0, controller.getQueueSnapshot().get(0).getProgressPercent());
  }

  @Test
  public void fileUploadUnexpectedResponseMarksItemFailedAndDoesNotInsertDocument() {
    FakeUploadTransport transport = new FakeUploadTransport();
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "unexpected.png",
                "",
                J2clAttachmentComposerController.DisplaySize.MEDIUM)));
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "stored", null));

    Assert.assertTrue(insertionCallback.insertions.isEmpty());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentUploadClient.ErrorType.UNEXPECTED_RESPONSE.name(),
        controller.getQueueSnapshot().get(0).getErrorCode());
  }

  @Test
  public void synchronousFileUploadStartFailureMarksFailedAndStartsNextQueuedItem() {
    FakeUploadTransport transport = new FakeUploadTransport();
    transport.failNextPost(new IllegalStateException("transport unavailable"));
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(1, transport.requests.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentUploadClient.ErrorType.NETWORK.name(),
        controller.getQueueSnapshot().get(0).getErrorCode());
    Assert.assertEquals(
        "transport unavailable",
        controller.getQueueSnapshot().get(0).getErrorMessage());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(1).getStatus());
    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(0).getUrl());
  }

  @Test
  public void synchronousUploadCompletionsDrainQueueWithoutNestedDispatch() {
    FakeUploadTransport transport = new FakeUploadTransport();
    transport.completePostsInline(new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    List<J2clAttachmentComposerController.AttachmentSelection> selections =
        new ArrayList<J2clAttachmentComposerController.AttachmentSelection>();
    for (int i = 0; i < 50; i++) {
      selections.add(
          J2clAttachmentComposerController.AttachmentSelection.file(
              new Object(),
              "inline-" + i + ".png",
              "",
              J2clAttachmentComposerController.DisplaySize.SMALL));
    }

    controller.selectFiles(selections);

    Assert.assertEquals(50, transport.requests.size());
    Assert.assertEquals(50, insertionCallback.insertions.size());
    Assert.assertEquals(1, transport.maxPostDepth);
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.COMPLETE,
        controller.getQueueSnapshot().get(49).getStatus());
  }

  @Test
  public void synchronousUploadStartFailureContinuesInlineDrain() {
    FakeUploadTransport transport = new FakeUploadTransport();
    transport.failNextPost(new IllegalStateException("transport unavailable"));
    transport.completePostsInline(new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(1, transport.requests.size());
    Assert.assertEquals(1, insertionCallback.insertions.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.COMPLETE,
        controller.getQueueSnapshot().get(1).getStatus());
    Assert.assertEquals(1, transport.maxPostDepth);
  }

  @Test
  public void laterSynchronousUploadStartFailureDoesNotRecurseOrStopDrainCleanup() {
    FakeUploadTransport transport = new FakeUploadTransport();
    transport.completePostsInline(new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    transport.failPostNumber(2, new IllegalStateException("second unavailable"));
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(1, transport.requests.size());
    Assert.assertEquals(1, insertionCallback.insertions.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.COMPLETE,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.FAILED,
        controller.getQueueSnapshot().get(1).getStatus());
    Assert.assertEquals(
        "second unavailable", controller.getQueueSnapshot().get(1).getErrorMessage());
    Assert.assertEquals(1, transport.maxPostDepth);
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
  public void cancelAndResetDoesNotResetIdGenerator() {
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
  public void cancelResetWhileIdleIsNoOp() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    controller.cancelAndReset();

    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
    Assert.assertTrue(transport.requests.isEmpty());
  }

  @Test
  public void staleCompletionAfterResetDoesNotAffectNewQueue() {
    FakeUploadTransport transport = new FakeUploadTransport();
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "stale.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    controller.cancelAndReset();
    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "fresh.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertTrue(insertionCallback.insertions.isEmpty());
    Assert.assertEquals(1, controller.getQueueSnapshot().size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(0).getStatus());

    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(1, insertionCallback.insertions.size());
    Assert.assertEquals("example.com/seedB", insertionCallback.insertions.get(0).getAttachmentId());
  }

  @Test
  public void cancelResetAllowsImmediatePasteReuseAndIgnoresLateFileCompletion() {
    FakeUploadTransport transport = new FakeUploadTransport();
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "stale.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));
    controller.cancelAndReset();
    Object pastedPayload = new Object();
    controller.pasteImage(
        pastedPayload,
        "paste caption",
        J2clAttachmentComposerController.DisplaySize.MEDIUM);

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertTrue(insertionCallback.insertions.isEmpty());
    Assert.assertEquals(1, controller.getQueueSnapshot().size());
    Assert.assertSame(pastedPayload, transport.requests.get(1).getPart(2).getPayload());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(0).getStatus());

    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(201, "stored", null));

    Assert.assertEquals(1, insertionCallback.insertions.size());
    Assert.assertEquals("example.com/seedB", insertionCallback.insertions.get(0).getAttachmentId());
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
  public void insertionCallbackFailureStillStartsNextUpload() {
    FakeUploadTransport transport = new FakeUploadTransport();
    OneShotThrowingInsertionCallback insertionCallback =
        new OneShotThrowingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.INSERT_FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.INSERT_FAILED_ERROR_CODE,
        controller.getQueueSnapshot().get(0).getErrorCode());
    Assert.assertEquals("boom", controller.getQueueSnapshot().get(0).getErrorMessage());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(1).getStatus());

    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(1, insertionCallback.insertions.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.COMPLETE,
        controller.getQueueSnapshot().get(1).getStatus());
  }

  @Test
  public void insertionCallbackFailureDuringSynchronousDrainMarksItemAndContinues() {
    FakeUploadTransport transport = new FakeUploadTransport();
    transport.completePostsInline(new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    OneShotThrowingInsertionCallback insertionCallback =
        new OneShotThrowingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals(1, insertionCallback.insertions.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.INSERT_FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.COMPLETE,
        controller.getQueueSnapshot().get(1).getStatus());
    Assert.assertEquals(1, transport.maxPostDepth);
  }

  @Test
  public void progressAfterInsertFailedStatusIsIgnored() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new AlwaysThrowingInsertionCallback());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    transport.requests.get(0).getProgressCallback().onProgress(12);

    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.INSERT_FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(100, controller.getQueueSnapshot().get(0).getProgressPercent());
  }

  @Test
  public void consecutiveInsertionFailuresRemainIndependent() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new AlwaysThrowingInsertionCallback());

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.INSERT_FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.INSERT_FAILED,
        controller.getQueueSnapshot().get(1).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.INSERT_FAILED_ERROR_CODE,
        controller.getQueueSnapshot().get(1).getErrorCode());
  }

  @Test
  public void cancelResetFromInsertionCallbackClearsQueueAndDoesNotStartNextUpload() {
    FakeUploadTransport transport = new FakeUploadTransport();
    CancelingInsertionCallback insertionCallback = new CancelingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    insertionCallback.setController(controller);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(1, insertionCallback.insertCalls);
    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
    Assert.assertEquals(1, transport.requests.size());
  }

  @Test
  public void reentrantSelectionFromInsertionCallbackStartsSingleNextUpload() {
    FakeUploadTransport transport = new FakeUploadTransport();
    ReentrantSelectingInsertionCallback insertionCallback =
        new ReentrantSelectingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    insertionCallback.setController(controller);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(1).getStatus());

    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(2, insertionCallback.insertions.size());
    Assert.assertEquals(2, transport.requests.size());
  }

  @Test
  public void reentrantSelectionDuringSynchronousDrainStartsAfterCurrentPostReturns() {
    FakeUploadTransport transport = new FakeUploadTransport();
    transport.completePostsInline(new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    ReentrantSelectingInsertionCallback insertionCallback =
        new ReentrantSelectingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    insertionCallback.setController(controller);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals(2, insertionCallback.insertions.size());
    Assert.assertEquals(1, transport.maxPostDepth);
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.COMPLETE,
        controller.getQueueSnapshot().get(1).getStatus());
  }

  @Test
  public void reentrantPasteFromInsertionCallbackStartsSingleNextUpload() {
    FakeUploadTransport transport = new FakeUploadTransport();
    ReentrantPastingInsertionCallback insertionCallback = new ReentrantPastingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    insertionCallback.setController(controller);

    controller.pasteImage(
        new Object(),
        "first",
        J2clAttachmentComposerController.DisplaySize.SMALL);

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(201, "stored", null));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    Assert.assertEquals("pasted-image.png", transport.requests.get(1).getPart(2).getFileName());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(1).getStatus());

    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(201, "stored", null));

    Assert.assertEquals(2, insertionCallback.insertions.size());
    Assert.assertEquals(2, transport.requests.size());
  }

  @Test
  public void invalidReentrantSelectionFromInsertionCallbackMarksCurrentItemInsertFailed() {
    FakeUploadTransport transport = new FakeUploadTransport();
    InvalidReentrantSelectingInsertionCallback insertionCallback =
        new InvalidReentrantSelectingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    insertionCallback.setController(controller);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.INSERT_FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.INSERT_FAILED_ERROR_CODE,
        controller.getQueueSnapshot().get(0).getErrorCode());
    Assert.assertTrue(
        controller.getQueueSnapshot().get(0).getErrorMessage().contains("selections"));
    Assert.assertEquals(1, transport.requests.size());
  }

  @Test
  public void reentrantSelectionThenThrowMarksCurrentItemAndKeepsNextUploadRunning() {
    FakeUploadTransport transport = new FakeUploadTransport();
    ReentrantSelectingThenThrowingInsertionCallback insertionCallback =
        new ReentrantSelectingThenThrowingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    insertionCallback.setController(controller);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.INSERT_FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(1).getStatus());
  }

  @Test
  public void resetThenRuntimeFailureFromInsertionCallbackDropsStaleItemMutation() {
    FakeUploadTransport transport = new FakeUploadTransport();
    ResettingThrowingInsertionCallback insertionCallback =
        new ResettingThrowingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    insertionCallback.setController(controller);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
    Assert.assertEquals(1, transport.requests.size());
  }

  @Test
  public void resetThenReentrantSelectionFromInsertionCallbackStartsFreshGeneration() {
    FakeUploadTransport transport = new FakeUploadTransport();
    ResettingSelectingInsertionCallback insertionCallback =
        new ResettingSelectingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    insertionCallback.setController(controller);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals(1, controller.getQueueSnapshot().size());
    Assert.assertEquals("/attachment/example.com/seedB", transport.requests.get(1).getUrl());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(0).getStatus());
  }

  @Test
  public void cancelResetDuringSynchronousDrainClearsQueueAndStopsDrain() {
    FakeUploadTransport transport = new FakeUploadTransport();
    transport.completePostsInline(new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
    CancelingInsertionCallback insertionCallback = new CancelingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);
    insertionCallback.setController(controller);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    Assert.assertEquals(1, insertionCallback.insertCalls);
    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
    Assert.assertEquals(1, transport.requests.size());
    Assert.assertEquals(1, transport.maxPostDepth);
  }

  @Test
  public void insertionCallbackErrorPropagatesAfterStartingNextQueuedUpload() {
    FakeUploadTransport transport = new FakeUploadTransport();
    ErrorThrowingInsertionCallback insertionCallback = new ErrorThrowingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.selectFiles(
        Arrays.asList(
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "first.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL),
            J2clAttachmentComposerController.AttachmentSelection.file(
                new Object(),
                "second.png",
                "",
                J2clAttachmentComposerController.DisplaySize.SMALL)));

    try {
      transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
      Assert.fail("Expected insertion callback error to propagate.");
    } catch (AssertionError expected) {
      Assert.assertEquals("fatal", expected.getMessage());
    }

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.COMPLETE,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(1).getStatus());
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
  public void queueSnapshotItemsAreValueSnapshots() {
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
    List<J2clAttachmentComposerController.UploadItem> snapshot = controller.getQueueSnapshot();

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING, snapshot.get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.COMPLETE,
        controller.getQueueSnapshot().get(0).getStatus());
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
  public void pastedImageBlankCaptionFallsBackToPastedImageFilename() {
    FakeUploadTransport transport = new FakeUploadTransport();
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.pasteImage(
        new Object(),
        "   ",
        J2clAttachmentComposerController.DisplaySize.SMALL);
    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(201, "stored", null));

    Assert.assertEquals("pasted-image.png", insertionCallback.insertions.get(0).getCaption());
    assertDocumentContainsAttachment(
        insertionCallback.documents.get(0),
        "example.com/seedA",
        "pasted-image.png",
        "small");
  }

  @Test
  public void insertionCallbackFailureOnPasteStillStartsNextUpload() {
    FakeUploadTransport transport = new FakeUploadTransport();
    OneShotThrowingInsertionCallback insertionCallback =
        new OneShotThrowingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.pasteImage(new Object(), "first", J2clAttachmentComposerController.DisplaySize.SMALL);
    controller.pasteImage(new Object(), "second", J2clAttachmentComposerController.DisplaySize.SMALL);

    transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(201, "stored", null));

    Assert.assertEquals(2, transport.requests.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.INSERT_FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentComposerController.INSERT_FAILED_ERROR_CODE,
        controller.getQueueSnapshot().get(0).getErrorCode());
    Assert.assertEquals("boom", controller.getQueueSnapshot().get(0).getErrorMessage());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.UPLOADING,
        controller.getQueueSnapshot().get(1).getStatus());

    transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(201, "stored", null));

    Assert.assertEquals(1, insertionCallback.insertions.size());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.COMPLETE,
        controller.getQueueSnapshot().get(1).getStatus());
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
  public void invalidPastedImageDisplaySizeDoesNotQueueStartUploadOrConsumeIds() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentComposerController controller =
        newController(transport, new RecordingInsertionCallback());

    try {
      controller.pasteImage(new Object(), null, null);
      Assert.fail("Expected invalid pasted image display size to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("display size"));
    }

    Assert.assertTrue(controller.getQueueSnapshot().isEmpty());
    Assert.assertTrue(transport.requests.isEmpty());
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
    Assert.assertEquals(
        J2clAttachmentUploadClient.ErrorType.UNEXPECTED_RESPONSE.name(),
        controller.getQueueSnapshot().get(0).getErrorCode());
  }

  @Test
  public void synchronousPastedImageUploadStartFailureMarksFailed() {
    FakeUploadTransport transport = new FakeUploadTransport();
    transport.failNextPost(new IllegalStateException("transport unavailable"));
    RecordingInsertionCallback insertionCallback = new RecordingInsertionCallback();
    J2clAttachmentComposerController controller = newController(transport, insertionCallback);

    controller.pasteImage(
        new Object(),
        "paste caption",
        J2clAttachmentComposerController.DisplaySize.MEDIUM);

    Assert.assertTrue(transport.requests.isEmpty());
    Assert.assertTrue(insertionCallback.insertions.isEmpty());
    Assert.assertEquals(
        J2clAttachmentComposerController.UploadStatus.FAILED,
        controller.getQueueSnapshot().get(0).getStatus());
    Assert.assertEquals(
        J2clAttachmentUploadClient.ErrorType.NETWORK.name(),
        controller.getQueueSnapshot().get(0).getErrorCode());
    Assert.assertEquals(
        "transport unavailable",
        controller.getQueueSnapshot().get(0).getErrorMessage());
  }

  private static void assertInvalidFileSelection(
      Object payload,
      String fileName,
      J2clAttachmentComposerController.DisplaySize displaySize,
      String expectedMessage) {
    try {
      J2clAttachmentComposerController.AttachmentSelection.file(
          payload,
          fileName,
          "",
          displaySize);
      Assert.fail("Expected invalid file selection to fail.");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains(expectedMessage));
    }
  }

  private static J2clAttachmentComposerController newController(
      FakeUploadTransport transport,
      J2clAttachmentComposerController.DocumentInsertionCallback insertionCallback) {
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
    // When set, post() completes uploads before returning to simulate browser/client fakes.
    private J2clAttachmentUploadClient.HttpResponse inlinePostResponse;
    private RuntimeException nextPostFailure;
    private RuntimeException numberedPostFailure;
    private int failingPostNumber;
    private int postCount;
    private int postDepth;
    private int maxPostDepth;

    @Override
    public void post(
        J2clAttachmentUploadClient.MultipartUploadRequest request,
        J2clAttachmentUploadClient.ResponseHandler handler) {
      postDepth++;
      maxPostDepth = Math.max(maxPostDepth, postDepth);
      postCount++;
      try {
        if (nextPostFailure != null) {
          RuntimeException failure = nextPostFailure;
          nextPostFailure = null;
          throw failure;
        }
        if (numberedPostFailure != null && postCount == failingPostNumber) {
          RuntimeException failure = numberedPostFailure;
          numberedPostFailure = null;
          failingPostNumber = 0;
          throw failure;
        }
        requests.add(request);
        handlers.add(handler);
        if (inlinePostResponse != null) {
          handler.onResponse(inlinePostResponse);
        }
      } finally {
        postDepth--;
      }
    }

    void failNextPost(RuntimeException failure) {
      nextPostFailure = failure;
    }

    void failPostNumber(int postNumber, RuntimeException failure) {
      failingPostNumber = postNumber;
      numberedPostFailure = failure;
    }

    void completePostsInline(J2clAttachmentUploadClient.HttpResponse response) {
      inlinePostResponse = response;
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

  private static final class CancelingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    private J2clAttachmentComposerController controller;
    private int insertCalls;

    private void setController(J2clAttachmentComposerController controller) {
      this.controller = controller;
    }

    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      insertCalls++;
      controller.cancelAndReset();
    }
  }

  private static final class ReentrantSelectingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    private final List<J2clAttachmentComposerController.AttachmentInsertion> insertions =
        new ArrayList<J2clAttachmentComposerController.AttachmentInsertion>();
    private J2clAttachmentComposerController controller;
    private boolean selectedReentrantFile;

    private void setController(J2clAttachmentComposerController controller) {
      this.controller = controller;
    }

    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      insertions.add(insertion);
      if (!selectedReentrantFile) {
        selectedReentrantFile = true;
        controller.selectFiles(
            Arrays.asList(
                J2clAttachmentComposerController.AttachmentSelection.file(
                    new Object(),
                    "reentrant.png",
                    "",
                    J2clAttachmentComposerController.DisplaySize.SMALL)));
      }
    }
  }

  private static final class ReentrantPastingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    private final List<J2clAttachmentComposerController.AttachmentInsertion> insertions =
        new ArrayList<J2clAttachmentComposerController.AttachmentInsertion>();
    private J2clAttachmentComposerController controller;
    private boolean pastedReentrantImage;

    private void setController(J2clAttachmentComposerController controller) {
      this.controller = controller;
    }

    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      insertions.add(insertion);
      if (!pastedReentrantImage) {
        pastedReentrantImage = true;
        controller.pasteImage(
            new Object(),
            "reentrant paste",
            J2clAttachmentComposerController.DisplaySize.SMALL);
      }
    }
  }

  private static final class InvalidReentrantSelectingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    private J2clAttachmentComposerController controller;

    private void setController(J2clAttachmentComposerController controller) {
      this.controller = controller;
    }

    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      controller.selectFiles(null);
    }
  }

  private static final class ReentrantSelectingThenThrowingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    private J2clAttachmentComposerController controller;

    private void setController(J2clAttachmentComposerController controller) {
      this.controller = controller;
    }

    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      controller.selectFiles(
          Arrays.asList(
              J2clAttachmentComposerController.AttachmentSelection.file(
                  new Object(),
                  "reentrant.png",
                  "",
                  J2clAttachmentComposerController.DisplaySize.SMALL)));
      throw new IllegalStateException("boom");
    }
  }

  private static final class ResettingThrowingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    private J2clAttachmentComposerController controller;

    private void setController(J2clAttachmentComposerController controller) {
      this.controller = controller;
    }

    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      controller.cancelAndReset();
      throw new IllegalStateException("boom");
    }
  }

  private static final class ResettingSelectingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    private J2clAttachmentComposerController controller;
    private boolean resetAndSelected;

    private void setController(J2clAttachmentComposerController controller) {
      this.controller = controller;
    }

    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      if (!resetAndSelected) {
        resetAndSelected = true;
        controller.cancelAndReset();
        controller.selectFiles(
            Arrays.asList(
                J2clAttachmentComposerController.AttachmentSelection.file(
                    new Object(),
                    "fresh.png",
                    "",
                    J2clAttachmentComposerController.DisplaySize.SMALL)));
      }
    }
  }

  private static final class AlwaysThrowingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      throw new IllegalStateException("boom");
    }
  }

  private static final class ErrorThrowingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      throw new AssertionError("fatal");
    }
  }

  private static final class OneShotThrowingInsertionCallback
      implements J2clAttachmentComposerController.DocumentInsertionCallback {
    private final List<J2clAttachmentComposerController.AttachmentInsertion> insertions =
        new ArrayList<J2clAttachmentComposerController.AttachmentInsertion>();
    private boolean shouldThrow = true;

    @Override
    public void onInsert(
        J2clComposerDocument document,
        J2clAttachmentComposerController.AttachmentInsertion insertion) {
      if (shouldThrow) {
        shouldThrow = false;
        throw new IllegalStateException("boom");
      }
      insertions.add(insertion);
    }
  }
}
