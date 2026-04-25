package org.waveprotocol.box.j2cl.attachment;

import elemental2.dom.FormData;
import elemental2.dom.XMLHttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jsinterop.base.Js;

public final class J2clAttachmentUploadClient {
  private static final String ATTACHMENT_URL_PREFIX = "/attachment/";
  private static final String PASTED_IMAGE_FILENAME = "pasted-image.png";
  private static final int NO_TIMEOUT_MILLIS = 0;
  private static final int PASTED_IMAGE_TIMEOUT_MILLIS = 60000;
  private static final UploadProgressCallback NO_UPLOAD_PROGRESS_CALLBACK =
      percent -> {};

  public interface UploadTransport {
    void post(MultipartUploadRequest request, ResponseHandler handler);
  }

  public interface ResponseHandler {
    void onResponse(HttpResponse response);
  }

  public interface UploadCallback {
    void onComplete(UploadResult result);
  }

  public interface UploadProgressCallback {
    void onProgress(int percent);
  }

  public enum ErrorType {
    INVALID_REQUEST,
    NETWORK,
    HTTP_STATUS,
    UNEXPECTED_RESPONSE
  }

  private enum UploadSource {
    FILE_PICKER,
    PASTED_IMAGE
  }

  private final UploadTransport transport;

  public J2clAttachmentUploadClient() {
    this(new BrowserUploadTransport());
  }

  public J2clAttachmentUploadClient(UploadTransport transport) {
    if (transport == null) {
      throw new IllegalArgumentException("Upload transport is required.");
    }
    this.transport = transport;
  }

  public void uploadFile(
      String attachmentId,
      String waveRef,
      Object filePayload,
      String fileName,
      UploadCallback callback) {
    uploadFile(attachmentId, waveRef, filePayload, fileName, null, callback);
  }

  public void uploadFile(
      String attachmentId,
      String waveRef,
      Object filePayload,
      String fileName,
      UploadProgressCallback progressCallback,
      UploadCallback callback) {
    upload(
        attachmentId,
        waveRef,
        filePayload,
        fileName,
        UploadSource.FILE_PICKER,
        progressCallback,
        callback);
  }

  public void uploadPastedImage(
      String attachmentId,
      String waveRef,
      Object imagePayload,
      UploadCallback callback) {
    uploadPastedImage(attachmentId, waveRef, imagePayload, null, callback);
  }

  public void uploadPastedImage(
      String attachmentId,
      String waveRef,
      Object imagePayload,
      UploadProgressCallback progressCallback,
      UploadCallback callback) {
    upload(
        attachmentId,
        waveRef,
        imagePayload,
        PASTED_IMAGE_FILENAME,
        UploadSource.PASTED_IMAGE,
        progressCallback,
        callback);
  }

  private void upload(
      String attachmentId,
      String waveRef,
      Object payload,
      String fileName,
      UploadSource source,
      UploadProgressCallback progressCallback,
      UploadCallback callback) {
    requireCallback(callback);
    String normalizedAttachmentId = requireNonEmpty(attachmentId, "Attachment id is required.");
    String normalizedWaveRef = requireNonEmpty(waveRef, "Wave ref is required.");
    String normalizedFileName = requireNonEmpty(fileName, "File name is required.");
    if (payload == null) {
      callback.onComplete(UploadResult.failure(ErrorType.INVALID_REQUEST, "Upload payload is required."));
      return;
    }

    MultipartUploadRequest request =
        new MultipartUploadRequest(
            ATTACHMENT_URL_PREFIX + normalizedAttachmentId,
            source == UploadSource.PASTED_IMAGE ? PASTED_IMAGE_TIMEOUT_MILLIS : NO_TIMEOUT_MILLIS,
            progressCallback == null ? NO_UPLOAD_PROGRESS_CALLBACK : progressCallback,
            MultipartPart.stringPart("attachmentId", normalizedAttachmentId),
            MultipartPart.stringPart("waveRef", normalizedWaveRef),
            MultipartPart.filePart("uploadFormElement", payload, normalizedFileName));
    transport.post(request, response -> callback.onComplete(classifyResponse(source, response)));
  }

  private static UploadResult classifyResponse(UploadSource source, HttpResponse response) {
    if (response == null) {
      return UploadResult.failure(ErrorType.NETWORK, "Attachment upload failed without a response.");
    }
    if (response.getNetworkError() != null && !response.getNetworkError().isEmpty()) {
      return UploadResult.failure(ErrorType.NETWORK, response.getNetworkError());
    }
    int statusCode = response.getStatusCode();
    if (statusCode < 200 || statusCode >= 300) {
      return UploadResult.failure(ErrorType.HTTP_STATUS, "HTTP " + statusCode + " while uploading attachment.");
    }
    if (source == UploadSource.FILE_PICKER) {
      // Preserve the legacy GWT file-picker contract: any 2xx response containing "OK" succeeds.
      if (response.getResponseText() != null
          && response.getResponseText().contains("OK")) {
        return UploadResult.success();
      }
      return UploadResult.failure(
          ErrorType.UNEXPECTED_RESPONSE,
          "Attachment upload did not return the expected OK sentinel.");
    }
    if (statusCode == 200 || statusCode == 201) {
      return UploadResult.success();
    }
    return UploadResult.failure(
        ErrorType.UNEXPECTED_RESPONSE,
        "Pasted image upload returned an unexpected HTTP " + statusCode + " response.");
  }

  private static void requireCallback(UploadCallback callback) {
    if (callback == null) {
      throw new IllegalArgumentException("Upload callback is required.");
    }
  }

  private static String requireNonEmpty(String value, String message) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return trimmed;
  }

  public static final class MultipartUploadRequest {
    private final String url;
    private final int timeoutMillis;
    private final UploadProgressCallback progressCallback;
    private final List<MultipartPart> parts;

    MultipartUploadRequest(
        String url,
        int timeoutMillis,
        UploadProgressCallback progressCallback,
        MultipartPart... parts) {
      this.url = url;
      this.timeoutMillis = timeoutMillis;
      this.progressCallback = progressCallback;
      List<MultipartPart> copied = new ArrayList<MultipartPart>();
      Collections.addAll(copied, parts);
      this.parts = Collections.unmodifiableList(copied);
    }

    public String getUrl() {
      return url;
    }

    public int getTimeoutMillis() {
      return timeoutMillis;
    }

    public UploadProgressCallback getProgressCallback() {
      return progressCallback;
    }

    public List<MultipartPart> getParts() {
      return parts;
    }

    public MultipartPart getPart(int index) {
      return parts.get(index);
    }
  }

  public static final class MultipartPart {
    private final String name;
    private final String stringValue;
    private final Object payload;
    private final String fileName;

    private MultipartPart(String name, String stringValue, Object payload, String fileName) {
      this.name = name;
      this.stringValue = stringValue;
      this.payload = payload;
      this.fileName = fileName;
    }

    static MultipartPart stringPart(String name, String value) {
      return new MultipartPart(name, value, null, null);
    }

    static MultipartPart filePart(String name, Object payload, String fileName) {
      return new MultipartPart(name, null, payload, fileName);
    }

    public String getName() {
      return name;
    }

    public String getStringValue() {
      return stringValue;
    }

    public Object getPayload() {
      return payload;
    }

    public String getFileName() {
      return fileName;
    }

    boolean isFile() {
      return payload != null;
    }
  }

  public static final class HttpResponse {
    private final int statusCode;
    private final String responseText;
    private final String networkError;

    public HttpResponse(int statusCode, String responseText, String networkError) {
      this.statusCode = statusCode;
      this.responseText = responseText == null ? "" : responseText;
      this.networkError = networkError;
    }

    public static HttpResponse networkError(String message) {
      return new HttpResponse(0, "", message == null || message.isEmpty() ? "Network failure." : message);
    }

    public int getStatusCode() {
      return statusCode;
    }

    public String getResponseText() {
      return responseText;
    }

    public String getNetworkError() {
      return networkError;
    }
  }

  public static final class UploadResult {
    private final boolean success;
    private final ErrorType errorType;
    private final String message;

    private UploadResult(boolean success, ErrorType errorType, String message) {
      this.success = success;
      this.errorType = errorType;
      this.message = message == null ? "" : message;
    }

    static UploadResult success() {
      return new UploadResult(true, null, "");
    }

    static UploadResult failure(ErrorType errorType, String message) {
      return new UploadResult(false, errorType, message);
    }

    public boolean isSuccess() {
      return success;
    }

    public ErrorType getErrorType() {
      return errorType;
    }

    public String getMessage() {
      return message;
    }
  }

  private static final class BrowserUploadTransport implements UploadTransport {
    @Override
    public void post(MultipartUploadRequest request, ResponseHandler handler) {
      FormData formData = new FormData();
      for (MultipartPart part : request.getParts()) {
        if (part.isFile()) {
          FormData.AppendValueUnionType value = Js.uncheckedCast(part.getPayload());
          formData.append(part.getName(), value, part.getFileName());
        } else {
          formData.append(part.getName(), part.getStringValue());
        }
      }

      XMLHttpRequest xhr = new XMLHttpRequest();
      xhr.open("POST", request.getUrl(), true);
      if (request.getTimeoutMillis() > 0) {
        xhr.timeout = request.getTimeoutMillis();
      }
      xhr.upload.onprogress =
          event -> {
            if (event.lengthComputable && event.total > 0) {
              request
                  .getProgressCallback()
                  .onProgress((int) Math.round((event.loaded / event.total) * 100));
            }
          };
      xhr.onload =
          event -> handler.onResponse(new HttpResponse(xhr.status, xhr.responseText, null));
      xhr.ontimeout =
          event -> handler.onResponse(HttpResponse.networkError("Attachment upload timed out."));
      xhr.onerror =
          event -> {
            handler.onResponse(HttpResponse.networkError("Network failure while uploading attachment."));
            return null;
          };
      xhr.send(formData);
    }
  }
}
