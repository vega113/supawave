package org.waveprotocol.box.j2cl.attachment;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clAttachmentUploadClientTest.class)
public class J2clAttachmentUploadClientTest {
  @Test
  public void filePickerUploadBuildsGwtCompatibleMultipartRequest() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentUploadClient client = new J2clAttachmentUploadClient(transport);
    Object file = new Object();

    client.uploadFile(
        "example.com/attachment+ABC",
        "example.com/wave+1/~/conv+root",
        file,
        "wave.png",
        result -> {});

    Assert.assertEquals("/attachment/example.com/attachment+ABC", transport.request.getUrl());
    Assert.assertEquals("attachmentId", transport.request.getPart(0).getName());
    Assert.assertEquals("example.com/attachment+ABC", transport.request.getPart(0).getStringValue());
    Assert.assertEquals("waveRef", transport.request.getPart(1).getName());
    Assert.assertEquals("example.com/wave+1/~/conv+root", transport.request.getPart(1).getStringValue());
    Assert.assertEquals("uploadFormElement", transport.request.getPart(2).getName());
    Assert.assertSame(file, transport.request.getPart(2).getPayload());
    Assert.assertEquals("wave.png", transport.request.getPart(2).getFileName());
    Assert.assertEquals(0, transport.request.getTimeoutMillis());
    transport.request.getProgressCallback().onProgress(12);
  }

  @Test
  public void filePickerUploadTrimsRequestIdentifiersBeforeSending() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentUploadClient client = new J2clAttachmentUploadClient(transport);

    client.uploadFile(
        "  example.com/attachment+ABC  ",
        "  example.com/wave+1/~/conv+root  ",
        new Object(),
        "  wave.png  ",
        result -> {});

    Assert.assertEquals("/attachment/example.com/attachment+ABC", transport.request.getUrl());
    Assert.assertEquals("example.com/attachment+ABC", transport.request.getPart(0).getStringValue());
    Assert.assertEquals("example.com/wave+1/~/conv+root", transport.request.getPart(1).getStringValue());
    Assert.assertEquals("wave.png", transport.request.getPart(2).getFileName());
  }

  @Test
  public void filePickerUploadRequiresOkSentinelInTwoHundredResponse() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentUploadClient client = new J2clAttachmentUploadClient(transport);
    RecordingUploadCallback callback = new RecordingUploadCallback();

    client.uploadFile("a+1", "wave/ref", new Object(), "doc.txt", callback);
    transport.complete(new J2clAttachmentUploadClient.HttpResponse(201, "Created", null));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentUploadClient.ErrorType.UNEXPECTED_RESPONSE, callback.result.getErrorType());

    client.uploadFile("a+2", "wave/ref", new Object(), "doc.txt", callback);
    transport.complete(new J2clAttachmentUploadClient.HttpResponse(204, "OK", null));

    Assert.assertTrue(callback.result.isSuccess());
  }

  @Test
  public void filePickerUploadReturnsHttpStatusBeforeParsingUnexpectedBody() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentUploadClient client = new J2clAttachmentUploadClient(transport);
    RecordingUploadCallback callback = new RecordingUploadCallback();

    client.uploadFile("a+1", "wave/ref", new Object(), "doc.txt", callback);
    transport.complete(new J2clAttachmentUploadClient.HttpResponse(500, "OK", null));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentUploadClient.ErrorType.HTTP_STATUS, callback.result.getErrorType());
  }

  @Test
  public void filePickerUploadRejectsHttpStatusBoundaryResponses() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentUploadClient client = new J2clAttachmentUploadClient(transport);
    RecordingUploadCallback callback = new RecordingUploadCallback();

    client.uploadFile("a+1", "wave/ref", new Object(), "doc.txt", callback);
    transport.complete(new J2clAttachmentUploadClient.HttpResponse(199, "OK", null));
    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentUploadClient.ErrorType.HTTP_STATUS, callback.result.getErrorType());

    client.uploadFile("a+2", "wave/ref", new Object(), "doc.txt", callback);
    transport.complete(new J2clAttachmentUploadClient.HttpResponse(300, "OK", null));
    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentUploadClient.ErrorType.HTTP_STATUS, callback.result.getErrorType());
  }

  @Test
  public void pastedImageUploadAcceptsOnlyHttpOkOrCreatedWithoutOkSentinel() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentUploadClient client = new J2clAttachmentUploadClient(transport);
    RecordingUploadCallback callback = new RecordingUploadCallback();
    Object imageBlob = new Object();

    client.uploadPastedImage("paste+1", "wave/ref", imageBlob, callback);
    transport.complete(new J2clAttachmentUploadClient.HttpResponse(201, "image stored", null));

    Assert.assertTrue(callback.result.isSuccess());
    Assert.assertEquals("pasted-image.png", transport.request.getPart(2).getFileName());
    Assert.assertSame(imageBlob, transport.request.getPart(2).getPayload());

    client.uploadPastedImage("paste+2", "wave/ref", imageBlob, callback);
    transport.complete(new J2clAttachmentUploadClient.HttpResponse(202, "accepted", null));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentUploadClient.ErrorType.UNEXPECTED_RESPONSE, callback.result.getErrorType());
  }

  @Test
  public void uploadProgressCallbackIsExposedOnRequest() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentUploadClient client = new J2clAttachmentUploadClient(transport);
    RecordingUploadCallback callback = new RecordingUploadCallback();
    final int[] progress = {-1};

    client.uploadFile(
        "a+1",
        "wave/ref",
        new Object(),
        "doc.txt",
        percent -> progress[0] = percent,
        callback);
    transport.request.getProgressCallback().onProgress(73);
    transport.complete(new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

    Assert.assertEquals(73, progress[0]);
    Assert.assertTrue(callback.result.isSuccess());
  }

  @Test
  public void networkFailureReturnsTypedError() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentUploadClient client = new J2clAttachmentUploadClient(transport);
    RecordingUploadCallback callback = new RecordingUploadCallback();

    client.uploadFile("a+1", "wave/ref", new Object(), "doc.txt", callback);
    transport.complete(J2clAttachmentUploadClient.HttpResponse.networkError("socket boom"));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(J2clAttachmentUploadClient.ErrorType.NETWORK, callback.result.getErrorType());
    Assert.assertEquals("socket boom", callback.result.getMessage());
  }

  @Test
  public void pastedImageUsesTimeoutAndReturnsTypedNetworkErrors() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentUploadClient client = new J2clAttachmentUploadClient(transport);
    RecordingUploadCallback callback = new RecordingUploadCallback();

    client.uploadPastedImage("paste+1", "wave/ref", new Object(), callback);
    transport.complete(J2clAttachmentUploadClient.HttpResponse.networkError("timeout"));

    Assert.assertEquals(60000, transport.request.getTimeoutMillis());
    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(J2clAttachmentUploadClient.ErrorType.NETWORK, callback.result.getErrorType());
    Assert.assertEquals("timeout", callback.result.getMessage());
  }

  @Test
  public void nullUploadCallbackThrowsIllegalArgumentException() {
    FakeUploadTransport transport = new FakeUploadTransport();
    J2clAttachmentUploadClient client = new J2clAttachmentUploadClient(transport);

    try {
      client.uploadFile("a+1", "wave/ref", new Object(), "doc.txt", null);
      Assert.fail("Expected null upload callback to be rejected.");
    } catch (IllegalArgumentException expected) {
      Assert.assertEquals("Upload callback is required.", expected.getMessage());
    }
  }

  private static final class FakeUploadTransport
      implements J2clAttachmentUploadClient.UploadTransport {
    private J2clAttachmentUploadClient.MultipartUploadRequest request;
    private J2clAttachmentUploadClient.ResponseHandler handler;

    @Override
    public void post(
        J2clAttachmentUploadClient.MultipartUploadRequest request,
        J2clAttachmentUploadClient.ResponseHandler handler) {
      this.request = request;
      this.handler = handler;
    }

    void complete(J2clAttachmentUploadClient.HttpResponse response) {
      handler.onResponse(response);
    }
  }

  private static final class RecordingUploadCallback
      implements J2clAttachmentUploadClient.UploadCallback {
    private J2clAttachmentUploadClient.UploadResult result;

    @Override
    public void onComplete(J2clAttachmentUploadClient.UploadResult result) {
      this.result = result;
    }
  }
}
