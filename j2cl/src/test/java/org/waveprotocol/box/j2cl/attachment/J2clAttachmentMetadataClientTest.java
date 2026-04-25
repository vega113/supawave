package org.waveprotocol.box.j2cl.attachment;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clAttachmentMetadataClientTest.class)
public class J2clAttachmentMetadataClientTest {
  @Test
  public void buildsEncodedMetadataRequestForAttachmentIds() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);

    client.fetchMetadata(
        Arrays.asList(
            "example.com/att+id=42:raw",
            "plain:id",
            "emoji-\uD83D\uDE00",
            "bold-\uD835\uDC00"),
        result -> {});

    Assert.assertEquals(
        "/attachmentsInfo?attachmentIds="
            + "example.com%2Fatt%2Bid%3D42%3Araw,plain%3Aid,"
            + "emoji-%F0%9F%98%80,bold-%F0%9D%90%80",
        transport.url);
  }

  @Test
  public void appendsAttachmentIdsWithAmpersandWhenBaseUrlAlreadyHasQuery() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client =
        new J2clAttachmentMetadataClient(transport, "/attachmentsInfo?cacheBust=1");

    client.fetchMetadata(Arrays.asList("a+b"), result -> {});

    Assert.assertEquals("/attachmentsInfo?cacheBust=1&attachmentIds=a%2Bb", transport.url);
  }

  @Test
  public void trimsAttachmentIdsBeforeRequestAndMissingCalculation() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("  a+1  "), callback);
    Assert.assertEquals("/attachmentsInfo?attachmentIds=a%2B1", transport.url);

    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            200,
            "application/json",
            "{\"1\":[{\"1\":\"a+1\",\"2\":\"wave/ref\",\"3\":\"doc.txt\","
                + "\"4\":\"text/plain\",\"5\":1,\"6\":\"alice@example.com\","
                + "\"7\":\"/attachment/a+1\",\"8\":\"/thumbnail/a+1\"}]}",
            null));

    Assert.assertTrue(callback.result.isSuccess());
    Assert.assertTrue(callback.result.getMissingAttachmentIds().isEmpty());
  }

  @Test
  public void missingMetadataReportsTrimmedAttachmentId() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("  miss+1  "), callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            200, "application/json", "{\"1\":[]}", null));

    Assert.assertTrue(callback.result.isSuccess());
    Assert.assertEquals(Arrays.asList("miss+1"), callback.result.getMissingAttachmentIds());
  }

  @Test
  public void parsesImageNonImageMalwareAndMissingMetadataFromNumericProtoJson() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(
        Arrays.asList("img+1", "pdf+1", "bad+1", "missing+1"),
        callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            200,
            "application/json; charset=utf8",
            "{\"1\":["
                + "{\"1\":\"img+1\",\"2\":\"wave/ref\",\"3\":\"cat.png\",\"4\":\"image/png\","
                + "\"5\":[123,0],\"6\":\"alice@example.com\",\"7\":\"/attachment/img+1\","
                + "\"8\":\"/thumbnail/img+1\",\"9\":{\"1\":640,\"2\":480},"
                + "\"10\":{\"1\":160,\"2\":120}},"
                + "{\"1\":\"pdf+1\",\"2\":\"wave/ref\",\"3\":\"doc.pdf\",\"4\":\"application/pdf\","
                + "\"5\":456,\"6\":\"bob@example.com\",\"7\":\"/attachment/pdf+1\","
                + "\"8\":\"/thumbnail/pdf+1\"},"
                + "{\"1\":\"bad+1\",\"2\":\"wave/ref\",\"3\":\"bad.exe\",\"4\":\"application/octet-stream\","
                + "\"5\":789,\"6\":\"eve@example.com\",\"7\":\"/attachment/bad+1\","
                + "\"8\":\"/thumbnail/bad+1\",\"11\":true}"
                + "]}",
            null));

    Assert.assertTrue(callback.result.isSuccess());
    Assert.assertEquals(3, callback.result.getAttachments().size());
    Assert.assertEquals(Arrays.asList("missing+1"), callback.result.getMissingAttachmentIds());

    J2clAttachmentMetadata image = callback.result.getAttachments().get(0);
    Assert.assertEquals("cat.png", image.getFileName());
    Assert.assertEquals(123L, image.getSize());
    Assert.assertNotNull(image.getImageMetadata());
    Assert.assertEquals(640, image.getImageMetadata().getWidth());
    Assert.assertEquals(120, image.getThumbnailMetadata().getHeight());

    J2clAttachmentMetadata document = callback.result.getAttachments().get(1);
    Assert.assertEquals("application/pdf", document.getMimeType());
    Assert.assertNull(document.getImageMetadata());
    Assert.assertFalse(document.isMalware());

    J2clAttachmentMetadata malware = callback.result.getAttachments().get(2);
    Assert.assertTrue(malware.isMalware());
  }

  @Test
  public void parsesLargeLongMetadataAndNoMissingAttachmentIds() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("large+1"), callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            200,
            "application/json",
            "{\"1\":[{\"1\":\"large+1\",\"2\":\"wave/ref\",\"3\":\"large.bin\","
                + "\"4\":\"application/octet-stream\",\"5\":[0,1],"
                + "\"6\":\"alice@example.com\",\"7\":\"/attachment/large+1\","
                + "\"8\":\"/thumbnail/large+1\"}]}",
            null));

    Assert.assertTrue(callback.result.isSuccess());
    Assert.assertEquals(1, callback.result.getAttachments().size());
    Assert.assertEquals(4294967296L, callback.result.getAttachments().get(0).getSize());
    Assert.assertTrue(callback.result.getMissingAttachmentIds().isEmpty());
  }

  @Test
  public void duplicateRequestedIdsUseReturnedIdSetForMissingCalculation() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("dup+1", "dup+1"), callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            200,
            "application/json",
            "{\"1\":[{\"1\":\"dup+1\",\"2\":\"wave/ref\",\"3\":\"dup.txt\","
                + "\"4\":\"text/plain\",\"5\":1,\"6\":\"alice@example.com\","
                + "\"7\":\"/attachment/dup+1\",\"8\":\"/thumbnail/dup+1\"}]}",
            null));

    Assert.assertTrue(callback.result.isSuccess());
    Assert.assertTrue(callback.result.getMissingAttachmentIds().isEmpty());
  }

  @Test
  public void invalidAttachmentIdRequestReturnsTypedErrorWithoutThrowing() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("valid+1", " "), callback);

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentMetadataClient.ErrorType.INVALID_REQUEST, callback.result.getErrorType());
    Assert.assertNull(transport.handler);
  }

  @Test
  public void unpairedSurrogateAttachmentIdReturnsTypedErrorWithoutThrowing() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("bad-\uD800x"), callback);

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentMetadataClient.ErrorType.INVALID_REQUEST, callback.result.getErrorType());
    Assert.assertNull(transport.handler);
  }

  @Test
  public void nullMetadataCallbackThrowsIllegalArgumentException() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);

    try {
      client.fetchMetadata(Arrays.asList("a+1"), null);
      Assert.fail("Expected null metadata callback to be rejected.");
    } catch (IllegalArgumentException expected) {
      Assert.assertEquals("Metadata callback is required.", expected.getMessage());
    }
  }

  @Test
  public void negativeBrowserMetadataTimeoutIsRejected() {
    try {
      new J2clAttachmentMetadataClient(-1);
      Assert.fail("Expected negative metadata timeout to be rejected.");
    } catch (IllegalArgumentException expected) {
      Assert.assertEquals("Metadata timeout must not be negative.", expected.getMessage());
    }
  }

  @Test
  public void zeroBrowserMetadataTimeoutIsAcceptedAsDisabled() {
    new J2clAttachmentMetadataClient(0);
  }

  @Test
  public void httpFailureReturnsTypedErrorWithoutThrowing() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("a+1"), callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            403, "application/json", "{\"error\":\"denied\"}", null));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentMetadataClient.ErrorType.HTTP_STATUS, callback.result.getErrorType());
  }

  @Test
  public void httpFailureDoesNotAttemptToParseBody() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("a+1"), callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            500, "application/json", "{not-json", null));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentMetadataClient.ErrorType.HTTP_STATUS, callback.result.getErrorType());
  }

  @Test
  public void nonJsonContentTypeReturnsTypedErrorWithoutThrowing() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("a+1"), callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            200, "text/plain", "not-json", null));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentMetadataClient.ErrorType.UNEXPECTED_CONTENT_TYPE,
        callback.result.getErrorType());
  }

  @Test
  public void malformedJsonReturnsTypedParseErrorWithoutThrowing() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("a+1"), callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            200, "application/json", "{not-json", null));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentMetadataClient.ErrorType.PARSE_ERROR, callback.result.getErrorType());
  }

  @Test
  public void emptyJsonBodyReturnsTypedParseErrorWithoutThrowing() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("a+1"), callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            200, "application/json", "", null));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentMetadataClient.ErrorType.PARSE_ERROR, callback.result.getErrorType());
  }

  @Test
  public void unexpectedMetadataFieldTypeReturnsTypedParseError() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("a+1"), callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            200,
            "application/json",
            "{\"1\":[{\"1\":\"a+1\",\"2\":\"wave/ref\",\"3\":\"doc.txt\","
                + "\"4\":\"text/plain\",\"5\":1,\"6\":\"alice@example.com\","
                + "\"7\":\"/attachment/a+1\",\"8\":\"/thumbnail/a+1\",\"11\":\"true\"}]}",
            null));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentMetadataClient.ErrorType.PARSE_ERROR, callback.result.getErrorType());
  }

  @Test
  public void incompleteLongWordsReturnTypedParseError() {
    FakeMetadataTransport transport = new FakeMetadataTransport();
    J2clAttachmentMetadataClient client = new J2clAttachmentMetadataClient(transport);
    RecordingMetadataCallback callback = new RecordingMetadataCallback();

    client.fetchMetadata(Arrays.asList("a+1"), callback);
    transport.complete(
        new J2clAttachmentMetadataClient.HttpResponse(
            200,
            "application/json",
            "{\"1\":[{\"1\":\"a+1\",\"2\":\"wave/ref\",\"3\":\"doc.txt\","
                + "\"4\":\"text/plain\",\"5\":[1],\"6\":\"alice@example.com\","
                + "\"7\":\"/attachment/a+1\",\"8\":\"/thumbnail/a+1\"}]}",
            null));

    Assert.assertFalse(callback.result.isSuccess());
    Assert.assertEquals(
        J2clAttachmentMetadataClient.ErrorType.PARSE_ERROR, callback.result.getErrorType());
  }

  private static final class FakeMetadataTransport
      implements J2clAttachmentMetadataClient.MetadataTransport {
    private String url;
    private J2clAttachmentMetadataClient.ResponseHandler handler;

    @Override
    public void get(String url, J2clAttachmentMetadataClient.ResponseHandler handler) {
      this.url = url;
      this.handler = handler;
    }

    void complete(J2clAttachmentMetadataClient.HttpResponse response) {
      handler.onResponse(response);
    }
  }

  private static final class RecordingMetadataCallback
      implements J2clAttachmentMetadataClient.MetadataCallback {
    private J2clAttachmentMetadataClient.MetadataResult result;

    @Override
    public void onComplete(J2clAttachmentMetadataClient.MetadataResult result) {
      this.result = result;
    }
  }
}
