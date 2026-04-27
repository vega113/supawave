package org.waveprotocol.box.j2cl.richtext;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;

@J2clTestInput(J2clRichContentDeltaFactoryTest.class)
public class J2clRichContentDeltaFactoryTest {
  @Test
  public void createWaveRequestEmitsTextAnnotationsAndAttachmentElements() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .text("Hello ")
            .annotatedText("fontWeight", "bold", "bold text")
            .text(" before ")
            .imageAttachment("att+hero/1=", "diagram.png", "medium")
            .build();

    J2clRichContentDeltaFactory.CreateWaveRequest request =
        factory.createWaveRequest("User@Example.COM ", document);

    Assert.assertEquals("example.com/w+seedA", request.getCreatedWaveId());
    SidecarSubmitRequest submitRequest = request.getSubmitRequest();
    Assert.assertEquals("example.com/w+seedA/~/conv+root", submitRequest.getWaveletName());
    Assert.assertNull(submitRequest.getChannelId());
    String deltaJson = submitRequest.getDeltaJson();
    assertContains(
        deltaJson,
        "\"1\":{\"1\":0,\"2\":\"" + encodeHex("wave://example.com/w+seedA/conv+root") + "\"}",
        "\"2\":\"user@example.com\"",
        "{\"1\":\"user@example.com\"}",
        "\"1\":\"b+root\"",
        "\"2\":\"Hello \"",
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "\"2\":\"bold text\"",
        "{\"1\":{\"2\":[\"fontWeight\"]}}",
        "\"3\":{\"1\":\"image\"",
        "{\"1\":\"attachment\",\"2\":\"att+hero/1=\"}",
        "{\"1\":\"display-size\",\"2\":\"medium\"}",
        "\"3\":{\"1\":\"caption\"",
        "\"2\":\"diagram.png\"");
    Assert.assertTrue(
        deltaJson.indexOf("{\"1\":\"user@example.com\"}")
            < deltaJson.indexOf("\"1\":\"b+root\""));
  }

  @Test
  public void replyRequestPreservesWriteSessionBasisAndChannel() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root");
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .text("Plain ")
            .annotatedText("link/manual", "https://example.com", "link")
            .build();

    SidecarSubmitRequest request = factory.createReplyRequest("user@example.com", session, document);

    Assert.assertEquals("example.com/w+reply/~/conv+root", request.getWaveletName());
    Assert.assertEquals("chan-7", request.getChannelId());
    assertContains(
        request.getDeltaJson(),
        "\"1\":{\"1\":44,\"2\":\"ABCD\"}",
        "\"2\":\"user@example.com\"",
        "\"1\":\"b+seedA\"",
        "\"2\":\"Plain \"",
        "{\"1\":{\"3\":[{\"1\":\"link/manual\",\"3\":\"https://example.com\"}]}}",
        "\"2\":\"link\"",
        "{\"1\":{\"2\":[\"link/manual\"]}}");
  }

  @Test
  public void replyRequestAllowsVersionZeroWriteSession() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 0L, "ZERO", "b+root");
    J2clComposerDocument document = J2clComposerDocument.builder().text("First reply").build();

    SidecarSubmitRequest request = factory.createReplyRequest("user@example.com", session, document);

    assertContains(request.getDeltaJson(), "\"1\":{\"1\":0,\"2\":\"ZERO\"}", "\"1\":\"b+seedA\"");
  }

  @Test
  public void replyRequestSerializesAttachmentElements() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root");
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .text("See ")
            .imageAttachment("att+reply/1=", "reply.png", "large")
            .build();

    SidecarSubmitRequest request = factory.createReplyRequest("user@example.com", session, document);

    assertContains(
        request.getDeltaJson(),
        "\"1\":\"b+seedA\"",
        "\"2\":\"See \"",
        "\"3\":{\"1\":\"image\"",
        "{\"1\":\"attachment\",\"2\":\"att+reply/1=\"}",
        "{\"1\":\"display-size\",\"2\":\"large\"}",
        "\"3\":{\"1\":\"caption\"",
        "\"2\":\"reply.png\"");
  }

  @Test
  public void emptyCaptionImageClosesCaptionAndImageElementsCleanly() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root");
    J2clComposerDocument document =
        J2clComposerDocument.builder().imageAttachment("att+empty", "", "small").build();

    SidecarSubmitRequest request = factory.createReplyRequest("user@example.com", session, document);

    assertContains(
        request.getDeltaJson(),
        "\"3\":{\"1\":\"caption\"}},{\"4\":true},{\"4\":true}",
        "{\"1\":\"display-size\",\"2\":\"small\"}");
  }

  @Test
  public void escapesJsonInTextAnnotationValuesAndAttachmentAttributes() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .text("Quote \" slash \\ newline\n control" + (char) 1)
            .annotatedText("link/manual", "https://example.com/a?x=\"y\"", "link")
            .imageAttachment("att+quote\"slash\\", "caption \" slash \\", "medium")
            .build();

    String deltaJson =
        factory.createWaveRequest("user@example.com", document).getSubmitRequest().getDeltaJson();

    assertContains(
        deltaJson,
        "Quote \\\" slash \\\\ newline\\n",
        "control\\u0001",
        "https://example.com/a?x=\\\"y\\\"",
        "att+quote\\\"slash\\\\",
        "caption \\\" slash \\\\");
  }

  @Test
  public void validatesStructuredDocumentInputs() {
    assertThrows(() -> J2clComposerDocument.builder().annotatedText("", "bold", "text"));
    assertThrows(() -> J2clComposerDocument.builder().annotatedText("   ", "bold", "text"));
    assertThrows(() -> J2clComposerDocument.builder().annotatedText("fontWeight", "", "text"));
    assertThrows(() -> J2clComposerDocument.builder().annotatedText("fontWeight", "   ", "text"));
    assertThrows(() -> J2clComposerDocument.builder().annotatedText("fontWeight", "bold", ""));
    assertThrows(() -> J2clComposerDocument.builder().annotatedText("fontWeight", "bold", "   "));
    assertThrows(() -> J2clComposerDocument.builder().imageAttachment("", "caption", "small"));
    assertThrows(() -> J2clComposerDocument.builder().imageAttachment("att+1", "caption", "huge"));

    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root");
    J2clComposerDocument document = J2clComposerDocument.builder().text("x").build();
    assertThrows(() -> factory.createWaveRequest(null, document));
    assertThrows(() -> factory.createWaveRequest("user@example.com", null));
    assertThrows(() -> factory.createReplyRequest(" ", session, document));
    assertThrows(() -> factory.createReplyRequest(null, session, document));
    assertThrows(() -> factory.createReplyRequest("user@example.com", null, document));
    assertThrows(() -> factory.createReplyRequest("user@example.com", session, null));
    assertThrows(
        () ->
            factory.createReplyRequest(
                "user@example.com",
                new J2clSidecarWriteSession(null, "chan-7", 44L, "ABCD", "b+root"),
                document));
    assertThrows(
        () ->
            factory.createReplyRequest(
                "user@example.com",
                new J2clSidecarWriteSession("example.com/w+reply", "", 44L, "ABCD", "b+root"),
                document));
    assertThrows(
        () ->
            factory.createReplyRequest(
                "user@example.com",
                new J2clSidecarWriteSession("example.com/w+reply", "chan-7", -1L, "ABCD", "b+root"),
                document));
    assertThrows(
        () ->
            factory.createReplyRequest(
                "user@example.com",
                new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, null, "b+root"),
                document));
    assertThrows(
        () ->
            factory.createWaveRequest(
                "missing-domain", document));
    assertThrows(() -> factory.createReplyRequest("missing-domain", session, document));
    assertThrows(() -> factory.createWaveRequest("user@evil/path", document));
    assertThrows(() -> factory.createWaveRequest("user@example.com@evil.com", document));
    assertThrows(() -> factory.createReplyRequest("user@example.com@evil.com", session, document));
  }

  @Test
  public void replyBlipIdsAdvanceAcrossRequests() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root");
    J2clComposerDocument document = J2clComposerDocument.builder().text("Reply").build();

    SidecarSubmitRequest first = factory.createReplyRequest("user@example.com", session, document);
    SidecarSubmitRequest second = factory.createReplyRequest("user@example.com", session, document);

    assertContains(first.getDeltaJson(), "\"1\":\"b+seedA\"");
    assertContains(second.getDeltaJson(), "\"1\":\"b+seedB\"");
  }

  @Test
  public void invalidReplySessionDoesNotAdvanceReplyBlipCounter() {
    assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
        new J2clSidecarWriteSession(null, "chan-7", 44L, "ABCD", "b+root"));
    assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
        new J2clSidecarWriteSession("", "chan-7", 44L, "ABCD", "b+root"));
    assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
        new J2clSidecarWriteSession("   ", "chan-7", 44L, "ABCD", "b+root"));
    assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
        new J2clSidecarWriteSession("example.com/w+reply", null, 44L, "ABCD", "b+root"));
    assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
        new J2clSidecarWriteSession("example.com/w+reply", "", 44L, "ABCD", "b+root"));
    assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
        new J2clSidecarWriteSession("example.com/w+reply", "   ", 44L, "ABCD", "b+root"));
    assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, null, "b+root"));
    assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "", "b+root"));
    assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "   ", "b+root"));
    assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", -1L, "ABCD", "b+root"));
  }

  @Test
  public void createAndReplyRequestsShareOneTokenCounter() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root");
    J2clComposerDocument document = J2clComposerDocument.builder().text("Body").build();

    J2clRichContentDeltaFactory.CreateWaveRequest create =
        factory.createWaveRequest("user@example.com", document);
    SidecarSubmitRequest reply = factory.createReplyRequest("user@example.com", session, document);

    Assert.assertEquals("example.com/w+seedA", create.getCreatedWaveId());
    assertContains(reply.getDeltaJson(), "\"1\":\"b+seedB\"");
  }

  @Test
  public void tokenCounterUsesMultiCharacterWeb64TokensAfterSixtyFourValues() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root");
    J2clComposerDocument document = J2clComposerDocument.builder().text("Reply").build();

    for (int i = 0; i < 64; i++) {
      factory.createReplyRequest("user@example.com", session, document);
    }
    SidecarSubmitRequest request = factory.createReplyRequest("user@example.com", session, document);

    assertContains(request.getDeltaJson(), "\"1\":\"b+seedBA\"");
  }

  @Test
  public void createWaveIdsAdvanceAcrossRequests() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clComposerDocument document = J2clComposerDocument.builder().text("Wave").build();

    J2clRichContentDeltaFactory.CreateWaveRequest first =
        factory.createWaveRequest("user@example.com", document);
    J2clRichContentDeltaFactory.CreateWaveRequest second =
        factory.createWaveRequest("user@example.com", document);

    Assert.assertEquals("example.com/w+seedA", first.getCreatedWaveId());
    Assert.assertEquals("example.com/w+seedB", second.getCreatedWaveId());
  }

  @Test
  public void consecutiveAnnotatedTextClosesEachAnnotationIndependently() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .annotatedText("fontWeight", "bold", "bold")
            .annotatedText("fontStyle", "italic", "italic")
            .build();

    String deltaJson =
        factory.createWaveRequest("user@example.com", document).getSubmitRequest().getDeltaJson();

    assertContains(
        deltaJson,
        "{\"1\":{\"3\":[{\"1\":\"fontWeight\",\"3\":\"bold\"}]}}",
        "{\"2\":\"bold\"}",
        "{\"1\":{\"2\":[\"fontWeight\"]}}",
        "{\"1\":{\"3\":[{\"1\":\"fontStyle\",\"3\":\"italic\"}]}}",
        "{\"2\":\"italic\"}",
        "{\"1\":{\"2\":[\"fontStyle\"]}}");
  }

  @Test
  public void preservesUnicodeTextAndCaption() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    String text = "\u05e9\u05dc\u05d5\u05dd";
    String caption = "\u05ea\u05de\u05d5\u05e0\u05d4";
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .text(text)
            .imageAttachment("att+unicode", caption, "small")
            .build();

    String deltaJson =
        factory.createWaveRequest("user@example.com", document).getSubmitRequest().getDeltaJson();

    assertContains(deltaJson, "\"2\":\"" + text + "\"", "\"2\":\"" + caption + "\"");
  }

  @Test
  public void normalizesDisplaySizeCaseDeterministically() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .imageAttachment("att+small", "small", "Small")
            .imageAttachment("att+medium", "medium", "MEDIUM")
            .imageAttachment("att+large", "large", "LaRgE")
            .build();

    String deltaJson =
        factory.createWaveRequest("user@example.com", document).getSubmitRequest().getDeltaJson();

    assertContains(
        deltaJson,
        "{\"1\":\"display-size\",\"2\":\"small\"}",
        "{\"1\":\"display-size\",\"2\":\"medium\"}",
        "{\"1\":\"display-size\",\"2\":\"large\"}");
  }

  // F-3.S2 (#1038, R-5.3 step 4): mention insert appends a link/manual
  // annotation whose value is the participant address and whose text
  // is `@displayName`.
  @Test
  public void appendMentionInsertEmitsLinkManualAnnotation() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clComposerDocument.Builder builder = J2clComposerDocument.builder().text("Hi ");
    factory.appendMentionInsert(builder, "Alice@Example.COM ", "Alice Adams");
    builder.text("!");
    J2clComposerDocument document = builder.build();
    SidecarSubmitRequest request =
        factory
            .createReplyRequest(
                "user@example.com",
                new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root"),
                document)
            ;
    String deltaJson = request.getDeltaJson();
    assertContains(
        deltaJson,
        "{\"1\":{\"3\":[{\"1\":\"link/manual\",\"3\":\"alice@example.com\"}]}}",
        "\"2\":\"@Alice Adams\"",
        "{\"1\":{\"2\":[\"link/manual\"]}}");
  }

  @Test
  public void appendMentionInsertFallsBackToAddressWhenDisplayNameIsBlank() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clComposerDocument.Builder builder = J2clComposerDocument.builder();
    factory.appendMentionInsert(builder, "bob@example.com", "");
    String deltaJson =
        factory
            .createReplyRequest(
                "user@example.com",
                new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root"),
                builder.build())
            .getDeltaJson();
    assertContains(deltaJson, "\"2\":\"@bob@example.com\"");
  }

  @Test
  public void appendMentionInsertRejectsInvalidAddress() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clComposerDocument.Builder builder = J2clComposerDocument.builder();
    assertThrows(() -> factory.appendMentionInsert(builder, "no-at-sign", "X"));
  }

  // F-3.S2 (#1038, R-5.4 step 3): task toggle emits a stand-alone
  // delta whose op carries `task/done=true` (or `false`) annotation
  // start + end bracketing the blip body, with no character payload.
  @Test
  public void taskToggleRequestEmitsTaskDoneAnnotation() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+reply", "chan-9", 12L, "HIST", "b+root");
    SidecarSubmitRequest request =
        factory.taskToggleRequest("User@Example.COM", session, "b+root", true);
    Assert.assertEquals("example.com/w+reply/~/conv+root", request.getWaveletName());
    Assert.assertEquals("chan-9", request.getChannelId());
    String deltaJson = request.getDeltaJson();
    assertContains(
        deltaJson,
        "\"1\":{\"1\":12,\"2\":\"HIST\"}",
        "\"2\":\"user@example.com\"",
        "\"1\":\"b+root\"",
        "{\"1\":{\"3\":[{\"1\":\"task/done\",\"3\":\"true\"}]}}",
        "{\"1\":{\"2\":[\"task/done\"]}}");
  }

  @Test
  public void taskToggleRequestRoundTripsBetweenStates() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+x", "chan-1", 0L, "ZERO", "b+root");
    String openJson =
        factory.taskToggleRequest("user@example.com", session, "b+root", false).getDeltaJson();
    String closedJson =
        factory.taskToggleRequest("user@example.com", session, "b+root", true).getDeltaJson();
    assertContains(openJson, "{\"1\":\"task/done\",\"3\":\"false\"}");
    assertContains(closedJson, "{\"1\":\"task/done\",\"3\":\"true\"}");
  }

  @Test
  public void taskToggleRequestRejectsBlankBlipId() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+x", "chan-1", 0L, "ZERO", "b+root");
    assertThrows(() -> factory.taskToggleRequest("user@example.com", session, "", true));
    assertThrows(() -> factory.taskToggleRequest("user@example.com", session, "   ", true));
  }

  // F-3.S2 (#1038, R-5.4 step 5): metadata request emits both
  // task/assignee and task/dueTs annotations bracketing the blip body.
  // PR #1066 review thread PRRT_kwDOBwxLXs593gTP — task/dueTs must be
  // serialised as the millisecond timestamp the reader path expects,
  // not the raw `YYYY-MM-DD` form value.
  @Test
  public void taskMetadataRequestEmitsOwnerAndDueAnnotations() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+x", "chan-2", 5L, "HASH", "b+root");
    SidecarSubmitRequest request =
        factory.taskMetadataRequest(
            "user@example.com", session, "b+root", "alice@example.com", "2026-05-15");
    String deltaJson = request.getDeltaJson();
    assertContains(
        deltaJson,
        "\"1\":\"b+root\"",
        "{\"1\":\"task/assignee\",\"3\":\"alice@example.com\"}",
        "{\"1\":\"task/dueTs\",\"3\":\"1778803200000\"}",
        "{\"1\":{\"2\":[\"task/assignee\",\"task/dueTs\"]}}");
  }

  // PR #1066 review thread PRRT_kwDOBwxLXs593gTP — explicit
  // round-trip assertion: the value written by the metadata writer
  // must parse back via the same Long.parseLong contract the reader
  // path uses, otherwise refresh drops the due date.
  @Test
  public void taskMetadataRequestRoundTripsThroughLongParseLong() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+x", "chan-2", 5L, "HASH", "b+root");
    SidecarSubmitRequest request =
        factory.taskMetadataRequest(
            "user@example.com", session, "b+root", "alice@example.com", "2026-05-15");
    String deltaJson = request.getDeltaJson();
    String marker = "\"1\":\"task/dueTs\",\"3\":\"";
    int start = deltaJson.indexOf(marker) + marker.length();
    int end = deltaJson.indexOf('"', start);
    String value = deltaJson.substring(start, end);
    long parsed = Long.parseLong(value);
    Assert.assertEquals(1778803200000L, parsed);
  }

  // Numeric inputs are passed through unchanged so callers that
  // already supply epoch-millis keep working alongside the date-input
  // form.
  @Test
  public void taskMetadataRequestPassesThroughNumericDueTimestamps() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+x", "chan-2", 5L, "HASH", "b+root");
    SidecarSubmitRequest request =
        factory.taskMetadataRequest(
            "user@example.com", session, "b+root", "alice@example.com", "1714000000000");
    String deltaJson = request.getDeltaJson();
    assertContains(
        deltaJson,
        "{\"1\":\"task/dueTs\",\"3\":\"1714000000000\"}");
  }

  // Unparseable dates coerce to the empty-string "unset" sentinel
  // rather than writing a value the reader silently drops as
  // unknown after refresh.
  @Test
  public void taskMetadataRequestCoercesUnparseableDueDateToUnset() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+x", "chan-2", 5L, "HASH", "b+root");
    SidecarSubmitRequest request =
        factory.taskMetadataRequest(
            "user@example.com", session, "b+root", "alice@example.com", "tomorrow");
    String deltaJson = request.getDeltaJson();
    assertContains(deltaJson, "{\"1\":\"task/dueTs\",\"3\":\"\"}");
  }

  @Test
  public void taskMetadataRequestNormalizesEmptyValuesAsUnset() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+x", "chan-2", 5L, "HASH", "b+root");
    SidecarSubmitRequest request =
        factory.taskMetadataRequest("user@example.com", session, "b+root", "", "");
    String deltaJson = request.getDeltaJson();
    assertContains(
        deltaJson,
        "{\"1\":\"task/assignee\",\"3\":\"\"}",
        "{\"1\":\"task/dueTs\",\"3\":\"\"}");
  }

  // F-3.S3 (#1038, R-5.5): reaction toggle emits ops against the
  // `react+<blipId>` data document. Empty snapshot creates the full
  // `<reactions><reaction emoji=X><user address=Y/></reaction></reactions>`
  // envelope.
  @Test
  public void reactionToggleRequestEmitsFullEnvelopeWhenSnapshotEmpty() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+r", "chan-9", 12L, "HIST", "b+root");
    SidecarSubmitRequest request =
        factory.reactionToggleRequest(
            "User@Example.COM",
            session,
            "b+root",
            "👍", // thumbs-up
            Collections.emptyList(),
            true);
    Assert.assertEquals("example.com/w+r/~/conv+root", request.getWaveletName());
    Assert.assertEquals("chan-9", request.getChannelId());
    String deltaJson = request.getDeltaJson();
    assertContains(
        deltaJson,
        "\"1\":\"react+b+root\"",
        "\"3\":{\"1\":\"reactions\",\"2\":[]}",
        "\"3\":{\"1\":\"reaction\",\"2\":[{\"1\":\"emoji\",\"2\":\"👍\"}]}",
        "\"3\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"user@example.com\"}]}",
        "{\"4\":true}");
  }

  // F-3.S3: when the reactions root already exists, toggle-on appends a
  // sibling <reaction> element via retain + insert.
  @Test
  public void reactionToggleRequestAppendsSiblingWhenRootExists() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+r", "chan-9", 12L, "HIST", "b+root");
    List<SidecarReactionEntry> snapshot = new ArrayList<>();
    snapshot.add(new SidecarReactionEntry("😂", Arrays.asList("alice@example.com")));
    SidecarSubmitRequest request =
        factory.reactionToggleRequest(
            "bob@example.com", session, "b+root", "🎉", snapshot, true);
    String deltaJson = request.getDeltaJson();
    // Existing snapshot: 2 (root) + 4 (reaction with 1 user) = 6 items.
    // Retain 5 (everything except the </reactions> close), then insert.
    assertContains(
        deltaJson,
        "\"1\":\"react+b+root\"",
        "{\"5\":5}",
        "\"3\":{\"1\":\"reaction\",\"2\":[{\"1\":\"emoji\",\"2\":\"🎉\"}]}",
        "\"3\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"bob@example.com\"}]}",
        "{\"5\":1}");
  }

  // F-3.S3: toggle-off when the user is one of multiple reactors emits a
  // delete pair for just the user element.
  @Test
  public void reactionToggleRequestRemovesUserWhenMultipleReactors() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+r", "chan-9", 12L, "HIST", "b+root");
    List<SidecarReactionEntry> snapshot = new ArrayList<>();
    snapshot.add(
        new SidecarReactionEntry(
            "👍", Arrays.asList("alice@example.com", "bob@example.com")));
    SidecarSubmitRequest request =
        factory.reactionToggleRequest(
            "bob@example.com", session, "b+root", "👍", snapshot, false);
    String deltaJson = request.getDeltaJson();
    // root open(1) + reaction open(1) + alice user pair(2) = offset 4 to bob's user start.
    // root total = 2 + (2 + 2*2) = 8; trailing = 8 - 4 - 2 = 2 (alice's pair was already
    // before the deletion, so the trailing covers reaction-end + reactions-end? wait):
    // Layout: [reactions-start, reaction-start, alice-start, alice-end, bob-start, bob-end,
    //          reaction-end, reactions-end].
    // userOffset = 1 (skip root start) + 1 (skip reaction start) + 2 (skip alice pair) = 4.
    // Trailing = 8 - 4 - 2 = 2 (reaction-end + reactions-end).
    assertContains(
        deltaJson,
        "\"1\":\"react+b+root\"",
        "{\"5\":4}",
        "\"7\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"bob@example.com\"}]}",
        "{\"8\":true}",
        "{\"5\":2}");
  }

  // F-3.S3: toggle-off when the user is the only reactor under the
  // only remaining emoji deletes the entire <reactions> root so a
  // subsequent add can re-insert the full envelope cleanly.
  @Test
  public void reactionToggleRequestPrunesReactionsRootWhenLastReaction() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+r", "chan-9", 12L, "HIST", "b+root");
    List<SidecarReactionEntry> snapshot = new ArrayList<>();
    snapshot.add(
        new SidecarReactionEntry("👍", Arrays.asList("alice@example.com")));
    SidecarSubmitRequest request =
        factory.reactionToggleRequest(
            "alice@example.com", session, "b+root", "👍", snapshot, false);
    String deltaJson = request.getDeltaJson();
    // All 6 items deleted (reactions open + reaction open + user open + 3 ends).
    // No retains — the document becomes empty.
    assertContains(
        deltaJson,
        "\"1\":\"react+b+root\"",
        "\"7\":{\"1\":\"reactions\",\"2\":[]}",
        "\"7\":{\"1\":\"reaction\",\"2\":[{\"1\":\"emoji\",\"2\":\"👍\"}]}",
        "\"7\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"alice@example.com\"}]}");
    Assert.assertFalse("No retains expected when pruning entire root", deltaJson.contains("{\"5\":"));
    int deleteEndCount = countOccurrences(deltaJson, "{\"8\":true}");
    Assert.assertEquals(
        "Expected three delete-element-end ops (user, reaction, reactions).",
        3,
        deleteEndCount);
  }

  // F-3.S3: toggle-off when the user was the only reactor under that
  // emoji, but other emoji reactions remain, prunes the <reaction>
  // wrapper only — the surrounding <reactions> root is retained.
  @Test
  public void reactionToggleRequestPrunesEmptyReactionWhenLastUser() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+r", "chan-9", 12L, "HIST", "b+root");
    List<SidecarReactionEntry> snapshot = new ArrayList<>();
    snapshot.add(
        new SidecarReactionEntry("👍", Arrays.asList("alice@example.com")));
    snapshot.add(
        new SidecarReactionEntry("❤️", Arrays.asList("bob@example.com")));
    SidecarSubmitRequest request =
        factory.reactionToggleRequest(
            "alice@example.com", session, "b+root", "👍", snapshot, false);
    String deltaJson = request.getDeltaJson();
    // root total = 2 + 4 + 4 = 10. offset to <reaction emoji=👍> start = 1.
    // Delete 4 items (reaction start + user start + user end + reaction end).
    // Trailing = 10 - 1 - 4 = 5 (❤️ reaction + </reactions>).
    assertContains(
        deltaJson,
        "\"1\":\"react+b+root\"",
        "{\"5\":1}",
        "\"7\":{\"1\":\"reaction\",\"2\":[{\"1\":\"emoji\",\"2\":\"👍\"}]}",
        "\"7\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"alice@example.com\"}]}",
        "{\"5\":5}");
    int deleteEndCount = countOccurrences(deltaJson, "{\"8\":true}");
    Assert.assertEquals(
        "Expected exactly two delete-element-end ops for the wrapper+user pair.",
        2,
        deleteEndCount);
  }

  // F-3.S3: with three emojis and multiple users, removing the middle
  // user of the last reaction lands at the right offset.
  @Test
  public void reactionToggleRequestComputesOffsetAcrossMultipleEmojis() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+r", "chan-9", 12L, "HIST", "b+root");
    List<SidecarReactionEntry> snapshot = new ArrayList<>();
    snapshot.add(
        new SidecarReactionEntry(
            "👍", Arrays.asList("alice@example.com", "bob@example.com")));
    snapshot.add(new SidecarReactionEntry("❤", Arrays.asList("carol@example.com")));
    snapshot.add(
        new SidecarReactionEntry(
            "🎉",
            Arrays.asList("dave@example.com", "eve@example.com", "frank@example.com")));
    SidecarSubmitRequest request =
        factory.reactionToggleRequest(
            "eve@example.com", session, "b+root", "🎉", snapshot, false);
    String deltaJson = request.getDeltaJson();
    // root start (1) + thumbs-up reaction (2 + 2*2 = 6) + heart reaction (2 + 2 = 4) +
    // tada reaction start (1) + dave user pair (2) = 14.
    // Then eve user pair takes 2 items. Trailing = total(2 + 6 + 4 + 2 + 2*3 = 20)
    // - 14 - 2 = 4 (eve... no wait): total = 2 + (2+4) + (2+2) + (2+6) = 2+6+4+8 = 20.
    // userOffset = 1 + 6 + 4 + 1 + 2 = 14. trailing = 20 - 14 - 2 = 4.
    assertContains(
        deltaJson,
        "{\"5\":14}",
        "\"7\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"eve@example.com\"}]}",
        "{\"8\":true}",
        "{\"5\":4}");
  }

  @Test
  public void reactionToggleRequestRejectsBlankBlipIdAndEmoji() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+x", "chan-1", 0L, "ZERO", "b+root");
    assertThrows(
        () ->
            factory.reactionToggleRequest(
                "user@example.com", session, "", "👍", Collections.emptyList(), true));
    assertThrows(
        () ->
            factory.reactionToggleRequest(
                "user@example.com",
                session,
                "b+root",
                "",
                Collections.emptyList(),
                true));
  }

  @Test
  public void reactionToggleRequestRejectsRemoveWhenSnapshotMissing() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+x", "chan-1", 0L, "ZERO", "b+root");
    assertThrows(
        () ->
            factory.reactionToggleRequest(
                "user@example.com",
                session,
                "b+root",
                "👍",
                Collections.emptyList(),
                false));
  }

  @Test
  public void reactionsRootItemCountMatchesEnvelopeSize() {
    Assert.assertEquals(0, J2clRichContentDeltaFactory.reactionsRootItemCount(null));
    Assert.assertEquals(
        0, J2clRichContentDeltaFactory.reactionsRootItemCount(Collections.emptyList()));
    List<SidecarReactionEntry> allNulls = new ArrayList<>();
    allNulls.add(null);
    allNulls.add(null);
    Assert.assertEquals(
        0, J2clRichContentDeltaFactory.reactionsRootItemCount(allNulls));
    List<SidecarReactionEntry> single = new ArrayList<>();
    single.add(new SidecarReactionEntry("👍", Arrays.asList("alice@example.com")));
    Assert.assertEquals(6, J2clRichContentDeltaFactory.reactionsRootItemCount(single));
    List<SidecarReactionEntry> multi = new ArrayList<>();
    multi.add(
        new SidecarReactionEntry("👍", Arrays.asList("a@x.com", "b@x.com")));
    multi.add(new SidecarReactionEntry("❤", Arrays.asList("c@x.com")));
    // 2 root + (2 + 4) + (2 + 2) = 12.
    Assert.assertEquals(12, J2clRichContentDeltaFactory.reactionsRootItemCount(multi));
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) >= 0) {
      count++;
      idx += needle.length();
    }
    return count;
  }

  private static void assertContains(String value, String... fragments) {
    for (String fragment : fragments) {
      Assert.assertTrue(
          "Missing fragment: " + fragment + "\nJSON: " + value, value.contains(fragment));
    }
  }

  private static void assertInvalidSessionDoesNotAdvanceReplyBlipCounter(
      J2clSidecarWriteSession invalidSession) {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clComposerDocument document = J2clComposerDocument.builder().text("Reply").build();
    assertThrows(() -> factory.createReplyRequest("user@example.com", invalidSession, document));

    SidecarSubmitRequest request =
        factory.createReplyRequest(
            "user@example.com",
            new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root"),
            document);

    assertContains(request.getDeltaJson(), "\"1\":\"b+seedA\"");
  }

  // F-3.S4 (#1038, R-5.7): the daily rich-edit affordances (bulleted
  // list / numbered list / block quote / inline link) must round-trip
  // through the model. The factory wraps annotated-text components
  // produced by the lit composer's serializeRichComponents walker; these
  // tests assert the resulting JSON delta carries the expected
  // annotation key + value at the right offsets.

  @Test
  public void createReplyRequestRoundTripsBulletedListAnnotation() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession(
            "example.com/w+abc", "chan-1", 7L, "HASH", "b+root");
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .annotatedText("list/unordered", "true", "Item one")
            .annotatedText("list/unordered", "true", "Item two")
            .build();
    SidecarSubmitRequest request =
        factory.createReplyRequest("user@example.com", session, document);
    String deltaJson = request.getDeltaJson();
    assertContains(
        deltaJson,
        "{\"1\":{\"3\":[{\"1\":\"list/unordered\",\"3\":\"true\"}]}}",
        "\"2\":\"Item one\"",
        "{\"1\":{\"2\":[\"list/unordered\"]}}",
        "\"2\":\"Item two\"");
  }

  @Test
  public void createReplyRequestRoundTripsNumberedListAnnotation() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession(
            "example.com/w+abc", "chan-1", 0L, "HASH", "b+root");
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .annotatedText("list/ordered", "true", "First")
            .annotatedText("list/ordered", "true", "Second")
            .build();
    String deltaJson =
        factory.createReplyRequest("user@example.com", session, document).getDeltaJson();
    assertContains(
        deltaJson,
        "{\"1\":{\"3\":[{\"1\":\"list/ordered\",\"3\":\"true\"}]}}",
        "\"2\":\"First\"",
        "{\"1\":{\"2\":[\"list/ordered\"]}}",
        "\"2\":\"Second\"");
  }

  @Test
  public void createReplyRequestRoundTripsBlockQuoteAnnotation() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession(
            "example.com/w+abc", "chan-2", 1L, "HASH", "b+root");
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .annotatedText("block/quote", "true", "Quoted text")
            .build();
    String deltaJson =
        factory.createReplyRequest("user@example.com", session, document).getDeltaJson();
    assertContains(
        deltaJson,
        "{\"1\":{\"3\":[{\"1\":\"block/quote\",\"3\":\"true\"}]}}",
        "\"2\":\"Quoted text\"",
        "{\"1\":{\"2\":[\"block/quote\"]}}");
  }

  @Test
  public void createReplyRequestRoundTripsInlineLinkAnnotation() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession(
            "example.com/w+abc", "chan-3", 2L, "HASH", "b+root");
    J2clComposerDocument document =
        J2clComposerDocument.builder()
            .text("Visit ")
            .annotatedText("link/manual", "https://example.com", "Example")
            .text(" today")
            .build();
    String deltaJson =
        factory.createReplyRequest("user@example.com", session, document).getDeltaJson();
    assertContains(
        deltaJson,
        "\"2\":\"Visit \"",
        "{\"1\":{\"3\":[{\"1\":\"link/manual\",\"3\":\"https://example.com\"}]}}",
        "\"2\":\"Example\"",
        "{\"1\":{\"2\":[\"link/manual\"]}}",
        "\"2\":\" today\"");
  }

  // F-3.S4 (#1038, R-5.6 F.6): blip-delete request emits a
  // tombstone/deleted=true annotation on the blip body. The op shape
  // mirrors the existing taskToggleRequest single-key writer except for
  // the annotation key + value.
  @Test
  public void blipDeleteRequestEmitsTombstoneDeletedAnnotation() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession(
            "example.com/w+abc", "chan-9", 13L, "HIST", "b+root");
    SidecarSubmitRequest request =
        factory.blipDeleteRequest("User@Example.COM", session, "b+target");
    Assert.assertEquals("example.com/w+abc/~/conv+root", request.getWaveletName());
    Assert.assertEquals("chan-9", request.getChannelId());
    String deltaJson = request.getDeltaJson();
    assertContains(
        deltaJson,
        "\"1\":{\"1\":13,\"2\":\"HIST\"}",
        "\"2\":\"user@example.com\"",
        "\"1\":\"b+target\"",
        "{\"1\":{\"3\":[{\"1\":\"tombstone/deleted\",\"3\":\"true\"}]}}",
        "{\"1\":{\"2\":[\"tombstone/deleted\"]}}");
  }

  @Test
  public void blipDeleteRequestRejectsBlankBlipId() {
    J2clRichContentDeltaFactory factory = new J2clRichContentDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+x", "chan-1", 0L, "ZERO", "b+root");
    assertThrows(() -> factory.blipDeleteRequest("user@example.com", session, ""));
    assertThrows(() -> factory.blipDeleteRequest("user@example.com", session, "   "));
  }

  private static void assertThrows(ThrowingRunnable runnable) {
    try {
      runnable.run();
      Assert.fail("Expected IllegalArgumentException.");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }
  }

  private interface ThrowingRunnable {
    void run();
  }

  // ASCII-only: mirrors the submit factory's version-zero URI hash encoding.
  private static String encodeHex(String value) {
    StringBuilder encoded = new StringBuilder(value.length() * 2);
    for (int i = 0; i < value.length(); i++) {
      int ch = value.charAt(i);
      encoded.append(toHexDigit((ch >> 4) & 0xF));
      encoded.append(toHexDigit(ch & 0xF));
    }
    return encoded.toString();
  }

  private static char toHexDigit(int value) {
    return (char) (value < 10 ? ('0' + value) : ('A' + (value - 10)));
  }
}
