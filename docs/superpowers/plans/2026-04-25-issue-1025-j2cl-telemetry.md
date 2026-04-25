# Issue #1025 J2CL Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emit structured J2CL client telemetry for #971 attachment and rich-edit parity flows through a shared client stats channel.

**Architecture:** Add a small J2CL telemetry package with deterministic event objects, an injectable sink for controller tests, and a browser sink that dispatches compatible events through the existing `window.__stats` / `window.__stats_listener` channel used by the GWT stats layer. Wire telemetry where behavior decisions occur: attachment upload lifecycle in `J2clAttachmentComposerController`, metadata failure classification in `J2clSelectedWaveController`, rich-edit command application in `J2clComposeSurfaceController`, and open/download user clicks in `J2clReadSurfaceDomRenderer` because those clicks originate at DOM links.

**Tech Stack:** J2CL Java, Elemental2, existing GWT `__stats` event channel shape, SBT J2CL test/build tasks, JVM controller tests, and DOM-renderer tests.

---

## Issue Scope

Tracked issue: #1025, follow-up from #971 closeout and parent tracker #904.

Required event names:

- `attachment.upload.started`
- `attachment.upload.succeeded`
- `attachment.upload.failed`
- `attachment.metadata.failed`
- `attachment.open.clicked`
- `attachment.download.clicked`
- `richEdit.command.applied`

Allowed failure reason values:

- `network`
- `forbidden`
- `server`
- `unsupported-file`
- `cancelled`
- `metadata`
- `validation`
- `client-error`

Security and privacy rules:

- Do not emit file contents, attachment payload objects, raw filenames, captions, wave IDs, wave refs, user addresses, attachment IDs, URLs, or auth material.
- Event fields may include low-cardinality values only: event source, command ID, display size, result status, transport status bucket, queue size, and failure reason.
- The browser sink must be best-effort. Telemetry failure must never break upload, metadata hydration, rich-edit command application, or link navigation.

Verification preference:

- Use SBT from the repo root for J2CL verification. Do not add Maven-only verification to this lane.

## File Ownership

Create:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/telemetry/J2clClientTelemetry.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/telemetry/J2clClientTelemetryTest.java`

Modify:

- `j2cl/src/main/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentComposerController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentComposerControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`
- `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`
- `wave/config/changelog.d/2026-04-25-issue-1025-j2cl-telemetry.json`
- `journal/local-verification/2026-04-25-issue-1025-j2cl-telemetry.md`

Do not modify:

- GWT root routing or `/` default behavior.
- #970 Lit overlay primitive files.
- Server attachment servlets or authorization checks.
- Generated `wave/config/changelog.json` by hand. Regenerate it with the changelog script.

## Event Field Contract

Common fields for all J2CL parity telemetry:

```text
surface=j2cl-root
category=parity
```

Attachment upload fields:

```text
source=file-picker|pasted-image
displaySize=small|medium|large
queueSize=<small integer string>
reason=network|forbidden|server|unsupported-file|cancelled|validation|client-error
statusBucket=4xx|5xx|other
```

Attachment metadata fields:

```text
source=selected-wave
reason=network|forbidden|server|metadata|validation|client-error
statusBucket=4xx|5xx|other
```

Attachment click fields:

```text
source=read-surface
displaySize=small|medium|large
```

Rich-edit fields:

```text
commandId=<J2clDailyToolbarAction id>
result=applied|cleared
```

## Task 1: Add Shared J2CL Telemetry Model And Browser Stats Sink

**Files:**

- Create: `j2cl/src/main/java/org/waveprotocol/box/j2cl/telemetry/J2clClientTelemetry.java`
- Create: `j2cl/src/test/java/org/waveprotocol/box/j2cl/telemetry/J2clClientTelemetryTest.java`
- Create: `j2cl/src/test/java/org/waveprotocol/box/j2cl/telemetry/RecordingTelemetrySink.java`

- [ ] **Step 1: Write failing tests for deterministic events and field filtering**

Add tests covering:

```java
@Test
public void eventAddsCommonFieldsAndKeepsLowCardinalityFields() {
  RecordingTelemetrySink sink = new RecordingTelemetrySink();

  sink.record(
      J2clClientTelemetry.event("attachment.upload.failed")
          .field("source", "file-picker")
          .field("reason", "server")
          .field("statusBucket", "5xx")
          .build());

  J2clClientTelemetry.Event event = sink.events().get(0);
  Assert.assertEquals("attachment.upload.failed", event.getName());
  Assert.assertEquals("j2cl-root", event.getFields().get("surface"));
  Assert.assertEquals("parity", event.getFields().get("category"));
  Assert.assertEquals("file-picker", event.getFields().get("source"));
  Assert.assertEquals("server", event.getFields().get("reason"));
  Assert.assertEquals("5xx", event.getFields().get("statusBucket"));
}

@Test
public void eventRejectsSensitiveFieldNames() {
  String[] sensitiveFields = {
      "fileName", "filename", "caption", "waveId", "waveRef", "address",
      "attachmentId", "url", "href", "payload", "content", "token", "ToKeN",
      "moduleName", "subSystem", "evtGroup", "millis", "type"};
  for (String fieldName : sensitiveFields) {
    try {
      J2clClientTelemetry.event("attachment.upload.started")
          .field(fieldName, "secret")
          .build();
      Assert.fail("expected failure for " + fieldName);
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("sensitive"));
    }
  }
}

@Test
public void noopSinkDoesNotThrow() {
  J2clClientTelemetry.noop()
      .record(J2clClientTelemetry.event("richEdit.command.applied").field("commandId", "bold").build());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
sbt -batch j2clSearchTest
```

Expected before implementation: compile failure for missing `J2clClientTelemetry`.
Treat that compile failure as the RED signal for this first test slice. If the
test class compiles after the model skeleton exists, the tests themselves must
fail until common fields, sensitive-field rejection, and no-op sink behavior are
implemented.

- [ ] **Step 3: Implement the telemetry model**

Implement a final utility class with:

```java
public final class J2clClientTelemetry {
  public interface Sink {
    void record(Event event);
  }

  public static Builder event(String name);

  public static Sink noop();

  public static Sink browserStatsSink();

  public static final class Event {
    public String getName();
    public Map<String, String> getFields();
  }
}
```

Implementation details:

- `event(name)` rejects null or blank names.
- `Builder.field(name, value)` rejects blank names and drops null or blank values.
- Add common fields `surface=j2cl-root` and `category=parity` during build.
- Reject field names `fileName`, `filename`, `caption`, `waveId`, `waveRef`, `address`, `attachmentId`, `url`, `href`, `payload`, `content`, and `token` case-insensitively.
- Also reject reserved stats-shape keys `moduleName`, `subSystem`, `evtGroup`,
  `millis`, `type`, `surface`, and `category` case-insensitively so sanitized
  fields cannot overwrite the browser event envelope or common parity fields.
- `Sink.record` failures are contained by callers, not by the sink itself.
- Test code uses `RecordingTelemetrySink` from `j2cl/src/test/java/...`; do not ship a recording helper in main J2CL code.

- [ ] **Step 4: Implement the browser stats sink**

Use Elemental2 / JsInterop in `J2clClientTelemetry.browserStatsSink()`:

```java
public static Sink browserStatsSink() {
  return event -> BrowserStatsSink.dispatch(event);
}
```

Implement `BrowserStatsSink.dispatch(event)` as a package-private nested helper
inside `J2clClientTelemetry`; do not add a separate production source file.

`BrowserStatsSink.dispatch(event)` should:

- Create a JavaScript object with GWT-compatible keys:
  - `moduleName="j2cl-root"`
  - `subSystem="j2cl.parity"`
  - `evtGroup=event.getName()`
  - `millis=0`
  - `type="event"`
- Attach all sanitized fields as direct extra parameters.
- Ensure `window.__stats` is an array before pushing.
- Push the event object into `window.__stats`.
- If `window.__stats_listener` is a function, call it with the event object.
- Catch `Throwable` around Elemental2/JsInterop dispatch so runtime exceptions,
  JavaScript interop failures, and listener exceptions cannot break product behavior.
  Do not rethrow from the browser sink.

Also add a package-visible conversion helper for tests:

```java
static Map<String, Object> statsEventForTesting(Event event)
```

Add a test that asserts the channel shape without browser globals:

```java
@Test
public void statsEventShapeMatchesGwtStatsChannelKeys() {
  Map<String, Object> stats =
      J2clClientTelemetry.statsEventForTesting(
          J2clClientTelemetry.event("attachment.upload.started")
              .field("source", "file-picker")
              .build());

  Assert.assertEquals("j2cl-root", stats.get("moduleName"));
  Assert.assertEquals("j2cl.parity", stats.get("subSystem"));
  Assert.assertEquals("attachment.upload.started", stats.get("evtGroup"));
  Assert.assertEquals("event", stats.get("type"));
  Assert.assertEquals("file-picker", stats.get("source"));
}
```

Add a browser-global resilience test when the test runtime exposes `DomGlobal.window`:

```java
@Test
public void browserStatsSinkSwallowsListenerException() {
  installThrowingStatsListenerForTesting();

  J2clClientTelemetry.browserStatsSink()
      .record(J2clClientTelemetry.event("attachment.upload.started").build());

  Assert.assertTrue(statsArrayLengthForTesting() >= 1);
}
```

If the JVM/J2CL unit harness cannot set `window.__stats_listener` safely, keep
the conversion test above and document the listener-exception branch in the
local verification journal after manual/browser smoke. Do not omit the product
catch around listener invocation.

Helper intent:

- `installThrowingStatsListenerForTesting()` stores the prior
  `window.__stats_listener`, installs a function that throws, and registers test
  cleanup to restore the prior listener.
- `statsArrayLengthForTesting()` reads `window.__stats.length` after ensuring the
  global stats array exists. It must not inspect event payloads beyond length;
  shape assertions stay in `statsEventForTesting`.

- [ ] **Step 5: Run tests**

Run:

```bash
sbt -batch j2clSearchTest
```

Expected: pass.

- [ ] **Step 6: Commit**

Commit message:

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/telemetry/J2clClientTelemetry.java \
  j2cl/src/test/java/org/waveprotocol/box/j2cl/telemetry/J2clClientTelemetryTest.java \
  j2cl/src/test/java/org/waveprotocol/box/j2cl/telemetry/RecordingTelemetrySink.java
git commit -m "feat(j2cl): add client telemetry sink"
```

## Task 2: Emit Attachment Upload Telemetry From The Composer Controller

**Files:**

- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentComposerController.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentComposerControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Add tests covering:

```java
@Test
public void uploadTelemetryRecordsStartedSucceededAndFailedEvents() {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  harness.controller.selectFiles(singleFile("diagram.png"));
  harness.transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

  Assert.assertEquals("attachment.upload.started", telemetry.events().get(0).getName());
  Assert.assertEquals("file-picker", telemetry.events().get(0).getFields().get("source"));
  Assert.assertEquals("medium", telemetry.events().get(0).getFields().get("displaySize"));
  Assert.assertEquals("1", telemetry.events().get(0).getFields().get("queueSize"));
  Assert.assertEquals("attachment.upload.succeeded", telemetry.events().get(1).getName());
  Assert.assertEquals("1", telemetry.events().get(1).getFields().get("queueSize"));

  harness.controller.selectFiles(singleFile("fail.png"));
  harness.transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(500, "fail", null));

  J2clClientTelemetry.Event failed = telemetry.lastEvent();
  Assert.assertEquals("attachment.upload.failed", failed.getName());
  Assert.assertEquals("server", failed.getFields().get("reason"));
  Assert.assertEquals("5xx", failed.getFields().get("statusBucket"));
  Assert.assertEquals("1", failed.getFields().get("queueSize"));
}

@Test
public void cancelEmitsUploadFailedCancelledWithoutPayloadData() {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  harness.controller.selectFiles(singleFile("cancel.png"));
  harness.controller.cancelAndReset();

  J2clClientTelemetry.Event event = telemetry.lastEvent();
  Assert.assertEquals("attachment.upload.failed", event.getName());
  Assert.assertEquals("cancelled", event.getFields().get("reason"));
  Assert.assertFalse(event.getFields().containsKey("fileName"));
  Assert.assertFalse(event.getFields().containsKey("attachmentId"));
}
```

Add table-driven tests for the remaining reason mappings:

```java
@Test
public void uploadTelemetryMapsEveryAllowedUploadReason() {
  assertUploadReason(J2clAttachmentUploadClient.HttpResponse.networkError("offline"), "network", "other");
  assertUploadReason(new J2clAttachmentUploadClient.HttpResponse(403, "forbidden", null), "forbidden", "4xx");
  assertUploadReason(new J2clAttachmentUploadClient.HttpResponse(415, "unsupported", null), "unsupported-file", "4xx");
  assertUploadReason(new J2clAttachmentUploadClient.HttpResponse(500, "server", null), "server", "5xx");
  assertInvalidPayloadReason("validation");
  assertInsertionFailureReason("client-error");
}

@Test
public void cancelWithEmptyQueueDoesNotEmitTelemetry() {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  harness.controller.cancelAndReset();

  Assert.assertTrue(telemetry.events().isEmpty());
}

@Test
public void queuedUploadsEmitStartedAndTerminalEventsInOrder() {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  harness.controller.selectFiles(Arrays.asList(singleFile("one.png"), singleFile("two.png")));
  harness.transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));
  harness.transport.complete(1, new J2clAttachmentUploadClient.HttpResponse(500, "fail", null));

  Assert.assertEquals("attachment.upload.started", telemetry.events().get(0).getName());
  Assert.assertEquals("attachment.upload.succeeded", telemetry.events().get(1).getName());
  Assert.assertEquals("attachment.upload.started", telemetry.events().get(2).getName());
  Assert.assertEquals("attachment.upload.failed", telemetry.events().get(3).getName());
}

@Test
public void pastedImageTelemetryUsesPastedImageSource() {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  harness.controller.pasteImage(new Object(), "", J2clAttachmentComposerController.DisplaySize.MEDIUM);

  Assert.assertEquals("attachment.upload.started", telemetry.lastEvent().getName());
  Assert.assertEquals("pasted-image", telemetry.lastEvent().getFields().get("source"));
}

@Test
public void throwingTelemetrySinkDoesNotBreakUploadCompletion() {
  Harness harness = Harness.withTelemetry(event -> { throw new RuntimeException("telemetry boom"); });

  harness.controller.selectFiles(singleFile("diagram.png"));
  harness.transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(200, "OK", null));

  Assert.assertEquals(J2clAttachmentComposerController.UploadStatus.COMPLETE,
      harness.controller.getQueueSnapshot().get(0).getStatus());
}

@Test
public void throwingTelemetrySinkDoesNotBreakUploadFailure() {
  Harness harness = Harness.withTelemetry(event -> { throw new RuntimeException("telemetry boom"); });

  harness.controller.selectFiles(singleFile("fail.png"));
  harness.transport.complete(0, new J2clAttachmentUploadClient.HttpResponse(500, "fail", null));

  Assert.assertEquals(J2clAttachmentComposerController.UploadStatus.FAILED,
      harness.controller.getQueueSnapshot().get(0).getStatus());
}
```

Helper intent:

- `assertUploadReason(HttpResponse response, String reason, String statusBucket)`
  selects one file, completes transport with `response`, and asserts the last
  `attachment.upload.failed` event has `reason` and `statusBucket`.
- `assertInvalidPayloadReason(String reason)` constructs an invalid selection or
  upload payload path that fails before transport and asserts validation.
- `assertInsertionFailureReason(String reason)` uses a throwing document insertion
  callback after a successful upload and asserts `client-error`.
- `singleFile(String fileName)` creates a valid medium-display file-picker
  selection; it must not leak `fileName` into telemetry.
- Upload event assertions must verify `queueSize` is present as a decimal integer
  string on started, succeeded, and failed events. Do not emit queue contents or
  attachment IDs.

- [ ] **Step 2: Add a telemetry sink constructor seam**

Add an overload:

```java
public J2clAttachmentComposerController(
    String waveRef,
    J2clAttachmentUploadClient uploadClient,
    J2clAttachmentIdGenerator idGenerator,
    DocumentInsertionCallback insertionCallback,
    StateChangeCallback stateChangeCallback,
    J2clClientTelemetry.Sink telemetrySink)
```

Existing constructors must delegate with `J2clClientTelemetry.noop()`.

- [ ] **Step 3: Emit upload lifecycle events**

Emit from controller state transitions:

- `attachment.upload.started` in `startUpload` after the item becomes `UPLOADING`.
- `attachment.upload.succeeded` in `handleUploadComplete` when `UploadResult.isSuccess()` is true.
- `attachment.upload.failed` for failed result, synchronous upload-start exception, insertion failure, and `cancelAndReset()` when there were queued or uploading items.

Field rules:

- Always include `source=file-picker` or `source=pasted-image`.
- Always include `displaySize=<documentValue>`.
- Include `queueSize=<queue.size()>` as a decimal integer string.
- Include `reason` only for failures.
- Include `statusBucket` only when available from the upload result.

- [ ] **Step 4: Extend upload result status and map upload failures to allowed reasons**

Add local helper methods:

```java
private static String uploadFailureReason(J2clAttachmentUploadClient.UploadResult result)
private static String statusBucket(J2clAttachmentUploadClient.UploadResult result)
```

Mapping:

- `INVALID_REQUEST` -> `validation`
- `NETWORK` -> `network`
- `HTTP_STATUS` with status 401 or 403 -> `forbidden`
- `HTTP_STATUS` with status 415 -> `unsupported-file`
- `HTTP_STATUS` otherwise -> `server`
- `UNEXPECTED_RESPONSE` -> `server`
- insertion failure -> `client-error`
- cancel/reset -> `cancelled`

Extend `J2clAttachmentUploadClient.UploadResult` with:

```java
public int getStatusCode()
```

Implementation details:

- `UploadResult.success()` uses status code `0`.
- `UploadResult.failure(ErrorType errorType, String message)` delegates to a new package-visible overload with status code `0`.
- Add package-visible `UploadResult.failure(ErrorType errorType, String message, int statusCode)`.
- `classifyResponse` passes the HTTP status code into failures that come from an HTTP response.
- Use the existing `J2clAttachmentUploadClient.HttpResponse.networkError(String)`
  factory for network tests. If that factory changes, build an equivalent
  `HttpResponse` with status `0` and a non-empty network error.
- Existing tests that assert only `ErrorType` and message must keep passing.

- [ ] **Step 5: Run tests**

Run:

```bash
sbt -batch j2clSearchTest
```

Expected: pass.

- [ ] **Step 6: Commit**

Commit message:

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentComposerController.java \
  j2cl/src/test/java/org/waveprotocol/box/j2cl/attachment/J2clAttachmentComposerControllerTest.java
git commit -m "feat(j2cl): emit attachment upload telemetry"
```

## Task 3: Emit Metadata Failure Telemetry From The Selected-Wave Controller

**Files:**

- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java`

- [ ] **Step 1: Write failing metadata telemetry tests**

Add tests covering:

```java
@Test
public void metadataNetworkFailureEmitsTelemetry() throws Exception {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  harness.openWaveWithAttachment();
  harness.rejectAttachmentMetadata(0, "metadata network failure");

  J2clClientTelemetry.Event event = telemetry.lastEvent();
  Assert.assertEquals("attachment.metadata.failed", event.getName());
  Assert.assertEquals("selected-wave", event.getFields().get("source"));
  Assert.assertEquals("network", event.getFields().get("reason"));
}

@Test
public void metadataMissingResultEmitsMetadataReason() throws Exception {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  harness.openWaveWithAttachment();
  harness.resolveAttachmentMetadata(
      0,
      Collections.<J2clAttachmentMetadata>emptyList(),
      Collections.<String>emptyList());

  J2clClientTelemetry.Event event = telemetry.lastEvent();
  Assert.assertEquals("attachment.metadata.failed", event.getName());
  Assert.assertEquals("metadata", event.getFields().get("reason"));
}
```

Add explicit reason coverage:

```java
@Test
public void metadataTelemetryMapsEveryMetadataReason() throws Exception {
  assertMetadataReason(J2clAttachmentMetadataClient.MetadataResult.failure(
      J2clAttachmentMetadataClient.ErrorType.INVALID_REQUEST, "bad request"), "validation", "other");
  assertMetadataReason(metadataHttpFailure(403), "forbidden", "4xx");
  assertMetadataReason(metadataHttpFailure(500), "server", "5xx");
  assertMetadataReason(J2clAttachmentMetadataClient.MetadataResult.failure(
      J2clAttachmentMetadataClient.ErrorType.UNEXPECTED_CONTENT_TYPE, "html"), "metadata", "other");
  assertMetadataReason(J2clAttachmentMetadataClient.MetadataResult.failure(
      J2clAttachmentMetadataClient.ErrorType.PARSE_ERROR, "bad json"), "metadata", "other");
}

@Test
public void metadataDispatchExceptionEmitsClientErrorReason() throws Exception {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetryAndGateway(
      telemetry,
      new ThrowingMetadataGateway(new RuntimeException("dispatch failed")));

  harness.openWaveWithAttachment();

  J2clClientTelemetry.Event event = telemetry.lastEvent();
  Assert.assertEquals("attachment.metadata.failed", event.getName());
  Assert.assertEquals("client-error", event.getFields().get("reason"));
  Assert.assertEquals("other", event.getFields().get("statusBucket"));
  Assert.assertTrue(harness.firstReadAttachment().isMetadataFailure());
}

@Test
public void throwingTelemetrySinkDoesNotBreakMetadataFailureRendering() throws Exception {
  Harness harness = Harness.withTelemetry(event -> { throw new RuntimeException("telemetry boom"); });

  harness.openWaveWithAttachment();
  harness.rejectAttachmentMetadata(0, "metadata network failure");

  Assert.assertTrue(harness.firstReadAttachment().isMetadataFailure());
}
```

Helper intent:

- `assertMetadataReason(MetadataResult result, String reason, String statusBucket)`
  opens a wave with one pending attachment, completes the metadata callback with
  `result`, and asserts `attachment.metadata.failed`, `source=selected-wave`,
  `reason`, and `statusBucket`.
- `metadataHttpFailure(int statusCode)` creates a `MetadataResult` with
  `ErrorType.HTTP_STATUS`, the provided status code, and a generic message using
  the new status-aware factory/overload.
- `ThrowingMetadataGateway` throws from `fetchAttachmentMetadata` only; bootstrap
  and selected-wave open paths still succeed so the dispatch-exception branch is
  isolated.

- [ ] **Step 2: Add a telemetry sink constructor seam**

Add an overload to `J2clSelectedWaveController` that accepts `J2clClientTelemetry.Sink telemetrySink`. Existing constructors delegate to `J2clClientTelemetry.noop()`.

- [ ] **Step 3: Emit `attachment.metadata.failed`**

Emit when:

- `MetadataResult.isSuccess()` is false.
- Metadata fetch dispatch throws.
- Metadata success returns missing IDs for requested attachments.

Fields:

- `source=selected-wave`
- `reason=<mapped reason>`
- `statusBucket=<4xx|5xx|other>` when the metadata result exposes an HTTP status.

- [ ] **Step 4: Extend metadata result status and map metadata failures**

Mapping:

- `INVALID_REQUEST` -> `validation`
- `NETWORK` -> `network`
- `HTTP_STATUS` with 401 or 403 -> `forbidden`
- `HTTP_STATUS` otherwise -> `server`
- `UNEXPECTED_CONTENT_TYPE` -> `metadata`
- `PARSE_ERROR` -> `metadata`
- missing metadata IDs -> `metadata`
- dispatch exception -> `client-error`

Extend `J2clAttachmentMetadataClient.MetadataResult` with:

```java
public int getStatusCode()
```

Implementation details:

- `MetadataResult.success(...)` uses status code `0`.
- `MetadataResult.failure(ErrorType errorType, String message)` delegates to a new public overload with status code `0`.
- Add public `MetadataResult.failure(ErrorType errorType, String message, int statusCode)`
  so controller tests outside the attachment package can construct status-aware
  HTTP failures.
- `decodeResponse` passes HTTP status code into `HTTP_STATUS` failures.
- Existing controller tests that manually construct metadata results keep compiling.

- [ ] **Step 5: Run tests**

Run:

```bash
sbt -batch j2clSearchTest
```

Expected: pass.

- [ ] **Step 6: Commit**

Commit message:

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java \
  j2cl/src/test/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveControllerTest.java
git commit -m "feat(j2cl): emit attachment metadata telemetry"
```

## Task 4: Emit Rich-Edit Command Telemetry From The Compose Controller

**Files:**

- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java`

- [ ] **Step 1: Write failing command telemetry tests**

Add tests covering:

```java
@Test
public void richEditCommandAppliedEmitsTelemetry() {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  harness.openWaveForReply();
  harness.controller.onToolbarAction(J2clDailyToolbarAction.BOLD);

  J2clClientTelemetry.Event event = telemetry.lastEvent();
  Assert.assertEquals("richEdit.command.applied", event.getName());
  Assert.assertEquals("bold", event.getFields().get("commandId"));
  Assert.assertEquals("applied", event.getFields().get("result"));
}

@Test
public void richEditCommandClearedEmitsTelemetry() {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  harness.openWaveForReply();
  harness.controller.onToolbarAction(J2clDailyToolbarAction.BOLD);
  harness.controller.onToolbarAction(J2clDailyToolbarAction.BOLD);

  J2clClientTelemetry.Event event = telemetry.lastEvent();
  Assert.assertEquals("richEdit.command.applied", event.getName());
  Assert.assertEquals("bold", event.getFields().get("commandId"));
  Assert.assertEquals("cleared", event.getFields().get("result"));
}
```

Add negative and sink-failure coverage:

```java
@Test
public void clearFormattingAcceptedEmitsTelemetry() {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  harness.openWaveForReply();
  harness.controller.onToolbarAction(J2clDailyToolbarAction.CLEAR_FORMATTING);

  Assert.assertEquals("richEdit.command.applied", telemetry.lastEvent().getName());
  Assert.assertEquals("clear-formatting", telemetry.lastEvent().getFields().get("commandId"));
  Assert.assertEquals("cleared", telemetry.lastEvent().getFields().get("result"));
}

@Test
public void richEditTelemetryDoesNotEmitForRejectedActions() {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  Harness harness = Harness.withTelemetry(telemetry);

  // No selected wave yet: rich-edit and attachment actions are accepted for UI
  // status handling but must not emit rich-edit telemetry.
  harness.controller.onToolbarAction(J2clDailyToolbarAction.BOLD);
  harness.controller.onToolbarAction(J2clDailyToolbarAction.ATTACHMENT_INSERT);

  Assert.assertTrue(telemetry.events().isEmpty());
}

@Test
public void throwingTelemetrySinkDoesNotBreakRichEditCommand() {
  Harness harness = Harness.withTelemetry(event -> { throw new RuntimeException("telemetry boom"); });

  harness.openWaveForReply();
  Assert.assertTrue(harness.controller.onToolbarAction(J2clDailyToolbarAction.BOLD));
  Assert.assertEquals("bold", harness.view.model.getActiveCommandId());
}
```

- [ ] **Step 2: Add a telemetry sink constructor seam**

Add constructor overloads that accept `J2clClientTelemetry.Sink telemetrySink`. Existing constructors delegate to `J2clClientTelemetry.noop()`.

- [ ] **Step 3: Emit `richEdit.command.applied` only for accepted rich-edit commands**

Emit after:

- Annotation command apply.
- Annotation command clear.
- `CLEAR_FORMATTING` accepted against an open wave.

Do not emit for:

- Unsupported toolbar IDs.
- Attachment action IDs.
- Signed-out or no-wave failure branches.

Fields:

- `commandId=<action.id()>`
- `result=applied|cleared`

`commandId` must come only from the `J2clDailyToolbarAction` enum's stable
`id()` values. Do not emit user text, toolbar labels, captions, or arbitrary DOM
attributes as command IDs. Verify the current enum values used by tests
(`bold`, `clear-formatting`) and do not rename toolbar IDs as part of this issue.
`CLEAR_FORMATTING` emits `result=cleared` because the current controller semantics
clear formatting state and render "Formatting cleared." even when no toggle was
active.
For #1025, `result=cleared` means the rich-edit clear command was accepted and
the controller entered its cleared formatting state; it is not intended to prove
that user-visible formatting changed. A future `no-op` result is out of scope
unless the product starts distinguishing no-op clears in the controller model.

- [ ] **Step 4: Run tests**

Run:

```bash
sbt -batch j2clSearchTest
```

Expected: pass.

- [ ] **Step 5: Commit**

Commit message:

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java \
  j2cl/src/test/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceControllerTest.java
git commit -m "feat(j2cl): emit rich edit command telemetry"
```

## Task 5: Emit Attachment Open And Download Click Telemetry From The Read Surface

**Files:**

- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java`
- Modify: `j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java`

- [ ] **Step 1: Write failing DOM click telemetry tests**

Add tests covering:

```java
@Test
public void openAndDownloadLinksEmitTelemetryOnClick() {
  RecordingTelemetrySink telemetry = new RecordingTelemetrySink();
  J2clReadSurfaceDomRenderer renderer = new J2clReadSurfaceDomRenderer(host, telemetry);

  renderer.renderWindow(singleAttachmentEntry());

  ((HTMLElement) host.querySelector("[data-j2cl-attachment-open='true']")).click();
  Assert.assertEquals("attachment.open.clicked", telemetry.lastEvent().getName());
  Assert.assertEquals("read-surface", telemetry.lastEvent().getFields().get("source"));
  Assert.assertEquals("medium", telemetry.lastEvent().getFields().get("displaySize"));

  ((HTMLElement) host.querySelector("[data-j2cl-attachment-download='true']")).click();
  Assert.assertEquals("attachment.download.clicked", telemetry.lastEvent().getName());
  Assert.assertEquals("medium", telemetry.lastEvent().getFields().get("displaySize"));
}

@Test
public void throwingTelemetrySinkDoesNotPreventAttachmentLinkClick() {
  J2clReadSurfaceDomRenderer renderer =
      new J2clReadSurfaceDomRenderer(host, event -> { throw new RuntimeException("telemetry boom"); });

  renderer.renderWindow(singleAttachmentEntry());
  HTMLElement open = (HTMLElement) host.querySelector("[data-j2cl-attachment-open='true']");

  open.click();
  Assert.assertEquals("/attachments/hero.png", open.getAttribute("href"));
}
```

Helper intent:

- `singleAttachmentEntry()` returns a read-window entry with one ready attachment
  render model that has both open/download links and `displaySize=medium`.

- [ ] **Step 2: Add a telemetry sink constructor seam**

Add:

```java
public J2clReadSurfaceDomRenderer(HTMLDivElement host, J2clClientTelemetry.Sink telemetrySink)
```

The existing constructor delegates to `J2clClientTelemetry.noop()`.

- [ ] **Step 3: Attach click listeners to rendered links**

In `renderAttachmentLink`, add a click listener that records:

- `attachment.open.clicked` for open links.
- `attachment.download.clicked` for download links.

Fields:

- `source=read-surface`
- `displaySize=<model display size>` by passing display size to `renderAttachmentLink`

Do not prevent default navigation.

- [ ] **Step 4: Run tests**

Run:

```bash
sbt -batch j2clSearchTest
```

Expected: pass.

- [ ] **Step 5: Commit**

Commit message:

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRenderer.java \
  j2cl/src/test/java/org/waveprotocol/box/j2cl/read/J2clReadSurfaceDomRendererTest.java
git commit -m "feat(j2cl): emit attachment action telemetry"
```

## Task 6: Wire The Browser Sink Through The Root Shell

**Files:**

- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java`
- Modify: `j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java`
- Modify: root/controller tests if constructor signatures require updates.

- [ ] **Step 1: Inspect root-shell construction**

Confirm `J2clRootShellController` is the production seam that constructs:

- `J2clComposeSurfaceController`
- `J2clSelectedWaveController`
- `J2clSelectedWaveView`, which constructs `J2clReadSurfaceDomRenderer` at both
  server-first and client-rendered paths.

- [ ] **Step 2: Add production sink injection**

Create one sink:

```java
J2clClientTelemetry.Sink telemetrySink = J2clClientTelemetry.browserStatsSink();
```

Pass it into compose, selected-wave controller, and selected-wave view:

```java
J2clSelectedWaveView selectedWaveView =
    new J2clSelectedWaveView(searchView.getSelectedWaveHost(), telemetrySink);

J2clComposeSurfaceController composeController =
    new J2clComposeSurfaceController(..., telemetrySink);

J2clSelectedWaveController selectedWaveController =
    new J2clSelectedWaveController(..., telemetrySink);
```

Add a concrete view overload:

```java
public J2clSelectedWaveView(HTMLElement host) {
  this(host, J2clClientTelemetry.noop());
}

public J2clSelectedWaveView(HTMLElement host, J2clClientTelemetry.Sink telemetrySink) {
  ...
  readSurface = new J2clReadSurfaceDomRenderer(contentList, telemetrySink);
}
```

Both constructor branches in `J2clSelectedWaveView` must pass the same sink to
`J2clReadSurfaceDomRenderer`: the server-first branch around the existing
`readSurface = new J2clReadSurfaceDomRenderer(contentList)` call and the
client-rendered branch around the second call. `SandboxEntryPoint` may continue
using the one-argument no-op constructor.

- [ ] **Step 3: Preserve test and legacy constructors**

All existing constructor call sites must continue to compile by defaulting to `J2clClientTelemetry.noop()`.
Add a narrow no-op seam smoke where practical: instantiate at least one legacy
constructor path without a telemetry argument, exercise an action that would emit
telemetry when a sink is supplied, and assert the product state still changes or
navigation attribute remains intact without requiring a recording sink.

- [ ] **Step 4: Run tests**

Run:

```bash
sbt -batch j2clSearchTest
```

Expected: pass.

- [ ] **Step 5: Commit**

Commit message:

```bash
git add j2cl/src/main/java/org/waveprotocol/box/j2cl/root/J2clRootShellController.java \
  j2cl/src/main/java/org/waveprotocol/box/j2cl/compose/J2clComposeSurfaceController.java \
  j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveController.java \
  j2cl/src/main/java/org/waveprotocol/box/j2cl/search/J2clSelectedWaveView.java
git commit -m "feat(j2cl): wire telemetry into root shell"
```

## Task 7: Add Changelog And Local Verification Record

**Files:**

- Create: `wave/config/changelog.d/2026-04-25-issue-1025-j2cl-telemetry.json`
- Create: `journal/local-verification/2026-04-25-issue-1025-j2cl-telemetry.md`

- [ ] **Step 1: Add changelog fragment**

Use this fragment content:

```json
{
  "date": "2026-04-25",
  "type": "changed",
  "summary": "J2CL now emits structured client telemetry for attachment and rich-edit parity flows.",
  "issues": [1025, 971, 904]
}
```

- [ ] **Step 2: Assemble and validate changelog**

Run:

```bash
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
```

Expected: assembled changelog and validation passed.

- [ ] **Step 3: Create local verification journal**

Include exact commands and observed results:

```markdown
# Local Verification

- Branch: codex/issue-1025-j2cl-telemetry
- Worktree: /Users/vega/devroot/worktrees/codex-issue-1025-j2cl-telemetry
- Date: 2026-04-25
- Issue: #1025

## Commands

- `sbt -batch j2clSearchTest`
- `sbt -batch j2clProductionBuild j2clLitTest j2clLitBuild`
- `python3 scripts/assemble-changelog.py && python3 scripts/validate-changelog.py`
- `git diff --check`
- `git diff --cached --check`

## Results

- Write the exact observed result for each command before committing the journal.
- If any command fails, stop, fix the failure, rerun the command, and record both
  the failed command and the later passing command.
```

- [ ] **Step 4: Commit docs/config**

Commit message:

```bash
git add wave/config/changelog.d/2026-04-25-issue-1025-j2cl-telemetry.json \
  wave/config/changelog.json \
  journal/local-verification/2026-04-25-issue-1025-j2cl-telemetry.md
git commit -m "docs(j2cl): note telemetry verification"
```

Stage only the `wave/config/changelog.json` file produced by
`python3 scripts/assemble-changelog.py`; do not edit the generated file by hand.
Use `git add -f` for the `journal/local-verification` file if the `journal/` ignore rule blocks it.

## Task 8: Final Verification, Review, PR, And Monitoring

**Files:**

- No planned source edits.

- [ ] **Step 1: Run final verification**

Run:

```bash
sbt -batch j2clSearchTest
sbt -batch j2clProductionBuild j2clLitTest j2clLitBuild
python3 scripts/assemble-changelog.py
python3 scripts/validate-changelog.py
git diff --check
```

Expected:

- J2CL JVM tests pass.
- J2CL production build passes.
- Lit tests/build pass.
- Changelog validation passes.
- Whitespace check passes.

- [ ] **Step 2: Self-review**

Check:

- Every #1025 required event name has a test assertion.
- Every failure reason emitted is in the allowed set.
- Every allowed failure reason has at least one planned or implemented assertion
  where that reason can be produced by the current controllers.
- Browser stats shape is covered by `statsEventForTesting`, including
  `moduleName`, `subSystem`, `evtGroup`, and copied sanitized fields.
- Browser stats reserved keys cannot be supplied as event fields, and listener
  exceptions are contained by the browser sink.
- Throwing telemetry sinks are test-covered for upload, metadata, read-click, and
  rich-edit paths.
- Upload throwing-sink tests cover both success and failure state transitions.
- Upload events assert `queueSize` as a low-cardinality decimal integer string.
- Multi-file upload ordering has an assertion for started and terminal event order.
- Legacy no-telemetry constructor paths still exercise product behavior through a
  no-op sink.
- `RecordingTelemetrySink` is test-only and does not ship in main J2CL code.
- Rich-edit `commandId` values come only from `J2clDailyToolbarAction.id()`.
- No event fields contain payload, file name, caption, URL, wave ID, wave ref, user address, attachment ID, or token data.
- Existing GWT default root remains untouched.
- No Maven command is used as verification evidence.

- [ ] **Step 3: Claude Opus review loop**

Run the Claude review helper against `origin/main..HEAD` with this scope:

```bash
REVIEW_TASK='Issue #1025 J2CL attachment and rich-edit telemetry implementation' \
REVIEW_GOAL='Verify structured J2CL telemetry is emitted for #971 parity flows without leaking sensitive attachment or wave data.' \
REVIEW_ACCEPTANCE=$'- Required event names are emitted and test-covered\n- Failure reasons are mapped to the allowed #1025 set\n- Browser sink is compatible with the existing __stats channel\n- Telemetry failures cannot break product behavior\n- GWT default root remains unchanged\n- Verification uses SBT, not Maven' \
REVIEW_RUNTIME='J2CL Java, Elemental2, SBT J2CL tasks' \
REVIEW_RISKY='Sensitive data leakage, telemetry side effects, missing event paths, stale constructor seams' \
REVIEW_TEST_COMMANDS='sbt -batch j2clSearchTest; sbt -batch j2clProductionBuild j2clLitTest j2clLitBuild; changelog validation; git diff --check' \
REVIEW_DIFF_SPEC='origin/main..HEAD' \
REVIEW_TEMPLATE='task' \
REVIEW_PLATFORM='claude' \
REVIEW_MODEL='opus' \
/Users/vega/.codex/skills/public/claude-review/scripts/review_task.sh
```

Before running the review helper, verify it exists and is executable:

```bash
test -x /Users/vega/.codex/skills/public/claude-review/scripts/review_task.sh
```

Address all blockers, important concerns, required follow-ups, coverage gaps, and optional comments that are correctness- or maintainability-relevant. Rerun until the final review has no remaining comments requiring changes.

- [ ] **Step 4: Push and open PR**

Run:

```bash
git push origin codex/issue-1025-j2cl-telemetry
REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo vega113/supawave)"
gh pr create --repo "$REPO" --base main --head codex/issue-1025-j2cl-telemetry \
  --title "Emit J2CL attachment and rich-edit telemetry" \
  --body-file /tmp/issue-1025-pr-body.md
```

PR body must include:

- Summary.
- Issue links: #1025, #971, #904.
- Plan path.
- Verification commands and results.
- Claude review final verdict.

- [ ] **Step 5: Monitor until merge**

Monitor:

```bash
REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo vega113/supawave)"
PR_NUMBER="$(gh pr view --repo "$REPO" --json number -q .number)"
gh pr checks "$PR_NUMBER" --repo "$REPO"
gh api graphql -f owner="${REPO%/*}" -f name="${REPO#*/}" -F number="$PR_NUMBER" -f query='query($owner:String!,$name:String!,$number:Int!){repository(owner:$owner,name:$name){pullRequest(number:$number){reviewThreads(first:100){nodes{id isResolved isOutdated comments(first:5){nodes{author{login} body url}}}}}}}'
```

Required merge gates:

- `0` unresolved review threads by GraphQL.
- CI passing.
- Changelog gate passing.
- Codex Review Gate passing after the review window.
- Auto-merge completed or manual merge confirmation recorded.

Update #1025 and #904 with final PR URL, merge commit, verification, and review-thread state.
