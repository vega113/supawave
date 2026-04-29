package org.waveprotocol.box.j2cl.transport;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.SidecarSearchResponse;

@J2clTestInput(SidecarTransportCodecTest.class)
public class SidecarTransportCodecTest {
  @Test
  public void decodeSelectedWaveReadStateReadsEndpointJson() {
    String json =
        "{\"waveId\":\"example.com/w+abc\",\"unreadCount\":4,\"isRead\":false}";

    SidecarSelectedWaveReadState state = SidecarTransportCodec.decodeSelectedWaveReadState(json);

    Assert.assertEquals("example.com/w+abc", state.getWaveId());
    Assert.assertEquals(4, state.getUnreadCount());
    Assert.assertFalse(state.isRead());
  }

  @Test
  public void decodeSelectedWaveReadStateHandlesMissingOptionalFields() {
    String json = "{\"waveId\":\"example.com/w+abc\"}";

    SidecarSelectedWaveReadState state = SidecarTransportCodec.decodeSelectedWaveReadState(json);

    Assert.assertEquals("example.com/w+abc", state.getWaveId());
    Assert.assertEquals(0, state.getUnreadCount());
    Assert.assertFalse(state.isRead());
  }

  @Test
  public void encodeOpenEnvelopeUsesGeneratedNumericFieldKeys() {
    SidecarOpenRequest request =
        new SidecarOpenRequest(
            "user@example.com", "example.com/w+abc123", Arrays.asList("conv+root"));

    String json = SidecarTransportCodec.encodeOpenEnvelope(8, request);

    Assert.assertTrue(json.contains("\"messageType\":\"ProtocolOpenRequest\""));
    Assert.assertTrue(json.contains("\"1\":\"user@example.com\""));
    Assert.assertTrue(json.contains("\"2\":\"example.com/w+abc123\""));
    Assert.assertTrue(json.contains("\"3\":[\"conv+root\"]"));
  }

  @Test
  public void encodeOpenEnvelopeOmitsViewportHintsWhenAbsent() {
    SidecarOpenRequest request =
        new SidecarOpenRequest(
            "user@example.com", "example.com/w+abc123", Arrays.asList("conv+root"));

    String json = SidecarTransportCodec.encodeOpenEnvelope(8, request);
    Map<String, Object> envelope = SidecarTransportCodec.parseJsonObject(json);
    Map<String, Object> message = asObject(envelope.get("message"));

    Assert.assertFalse(message.containsKey("5"));
    Assert.assertFalse(message.containsKey("6"));
    Assert.assertFalse(message.containsKey("7"));
  }

  @Test
  public void encodeOpenEnvelopeIncludesViewportHintsWhenPresent() {
    SidecarOpenRequest request =
        new SidecarOpenRequest(
            "user@example.com",
            "example.com/w+abc123",
            Arrays.asList("conv+root"),
            new SidecarViewportHints("b+root", "forward", Integer.valueOf(0)));

    String json = SidecarTransportCodec.encodeOpenEnvelope(8, request);
    Map<String, Object> envelope = SidecarTransportCodec.parseJsonObject(json);
    Map<String, Object> message = asObject(envelope.get("message"));

    Assert.assertEquals("b+root", message.get("5"));
    Assert.assertEquals("forward", message.get("6"));
    Assert.assertEquals(0, ((Number) message.get("7")).intValue());
  }

  @Test
  public void parseOpenEnvelopePreservesExplicitZeroViewportLimitField() {
    Map<String, Object> envelope =
        SidecarTransportCodec.parseJsonObject(
            "{\"sequenceNumber\":8,\"messageType\":\"ProtocolOpenRequest\","
                + "\"message\":{\"1\":\"user@example.com\",\"2\":\"example.com/w+abc123\","
                + "\"3\":[\"conv+root\"],\"7\":0}}");
    Map<String, Object> message = asObject(envelope.get("message"));

    Assert.assertTrue(message.containsKey("7"));
    Assert.assertEquals(0, ((Number) message.get("7")).intValue());
  }

  @Test
  public void decodeSearchResponseReadsNumericKeysAndLongWords() {
    String json =
        "{\"1\":\"in:inbox\",\"2\":1,\"3\":[{\"1\":\"Inbox wave\",\"2\":\"Snippet\","
            + "\"3\":\"example.com/w+abc123\",\"4\":[12345,0],\"5\":2,\"6\":4,"
            + "\"7\":[\"user@example.com\"],\"8\":\"user@example.com\",\"9\":true}]}";

    SidecarSearchResponse response = SidecarTransportCodec.decodeSearchResponse(json);

    Assert.assertEquals("in:inbox", response.getQuery());
    Assert.assertEquals(1, response.getTotalResults());
    Assert.assertEquals(1, response.getDigests().size());
    Assert.assertEquals("Inbox wave", response.getDigests().get(0).getTitle());
    Assert.assertEquals(12345L, response.getDigests().get(0).getLastModified());
    Assert.assertTrue(response.getDigests().get(0).isPinned());
  }

  @Test
  public void decodeWaveletUpdateSummaryReadsEnvelopeAndPayload() {
    String json =
        "{\"sequenceNumber\":11,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"example.com!conv+root/example.com!conv+root\","
            + "\"2\":[{\"1\":\"delta\"}],\"6\":true,\"7\":\"chan-1\"}}";

    SidecarWaveletUpdateSummary summary = SidecarTransportCodec.decodeWaveletUpdate(json);

    Assert.assertEquals(11, summary.getSequenceNumber());
    Assert.assertEquals("example.com!conv+root/example.com!conv+root", summary.getWaveletName());
    Assert.assertEquals(1, summary.getAppliedDeltaCount());
    Assert.assertTrue(summary.hasMarker());
    Assert.assertEquals("chan-1", summary.getChannelId());
  }

  @Test
  public void decodeSelectedWaveUpdateReadsSnapshotAndFragmentsForSidecarProjection() {
    String json =
        "{\"sequenceNumber\":12,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"example.com!w+abc123/example.com!conv+root\","
            + "\"4\":{\"1\":44,\"2\":\"ABCD\"},"
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\",\"teammate@example.com\"],"
            + "\"3\":[{\"1\":\"b+root\",\"3\":\"user@example.com\",\"5\":[33,0],\"6\":[44,0]}]},"
            + "\"6\":true,\"7\":\"chan-2\","
            + "\"8\":{\"1\":[44,0],\"2\":[40,0],\"3\":[44,0],"
            + "\"4\":[{\"1\":\"manifest\",\"2\":[40,0],\"3\":[44,0]},"
            + "{\"1\":\"blip:b+root\",\"2\":[41,0],\"3\":[44,0]}],"
            + "\"5\":[{\"1\":\"manifest\",\"2\":{\"1\":\"conversation: Inbox wave\"}},"
            + "{\"1\":\"blip:b+root\",\"2\":{\"1\":\"Hello from the sidecar\"},\"3\":[],\"4\":[]}]}}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    Assert.assertEquals("example.com!w+abc123/example.com!conv+root", update.getWaveletName());
    Assert.assertEquals("chan-2", update.getChannelId());
    Assert.assertTrue(update.hasMarker());
    Assert.assertEquals(44L, update.getResultingVersion());
    Assert.assertEquals("ABCD", update.getResultingVersionHistoryHash());
    Assert.assertEquals(2, update.getParticipantIds().size());
    Assert.assertEquals(1, update.getDocuments().size());
    Assert.assertEquals("b+root", update.getDocuments().get(0).getDocumentId());

    SidecarSelectedWaveFragments fragments = update.getFragments();
    Assert.assertEquals(44L, fragments.getSnapshotVersion());
    Assert.assertEquals(2, fragments.getRanges().size());
    SidecarSelectedWaveFragment fragment = fragments.getEntries().get(1);
    Assert.assertEquals("blip:b+root", fragment.getSegment());
    Assert.assertEquals("Hello from the sidecar", fragment.getRawSnapshot());
  }

  @Test
  public void decodeSelectedWaveUpdateReadsParticipantsFromMetadataOnlySnapshot() {
    String json =
        "{\"sequenceNumber\":12,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"example.com!w+abc123/example.com!conv+root\","
            + "\"4\":{\"1\":44,\"2\":\"ABCD\"},"
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\",\"teammate@example.com\"],"
            + "\"4\":{\"1\":44,\"2\":\"ABCD\"},\"5\":[1234,0],\"6\":\"user@example.com\","
            + "\"7\":[1230,0]},"
            + "\"7\":\"chan-2\","
            + "\"8\":{\"1\":[44,0],\"2\":[40,0],\"3\":[44,0],"
            + "\"4\":[{\"1\":\"blip:b+root\",\"2\":[41,0],\"3\":[44,0]}],"
            + "\"5\":[{\"1\":\"blip:b+root\",\"2\":{\"1\":\"Hello from the sidecar\"}}]}}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    Assert.assertEquals(
        Arrays.asList("user@example.com", "teammate@example.com"),
        update.getParticipantIds());
    Assert.assertEquals(0, update.getDocuments().size());
    Assert.assertEquals(1, update.getFragments().getEntries().size());
  }

  @Test
  public void decodeSelectedWaveUpdateReadsSnapshotDocumentTextWhenFragmentsAreAbsent() {
    String json =
        "{\"sequenceNumber\":13,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s4635670bfbwA/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\"],"
            + "\"3\":[{\"1\":\"b+abc123\","
            + "\"2\":{\"1\":[{\"3\":{\"1\":\"body\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"line\",\"2\":[]}},"
            + "{\"2\":\"  Welcome to SupaWave  \"},"
            + "{\"4\":true},{\"4\":true}]},"
            + "\"3\":\"user@example.com\",\"5\":[1,0],\"6\":[2,0]}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    Assert.assertEquals(1, update.getDocuments().size());
    Assert.assertEquals("b+abc123", update.getDocuments().get(0).getDocumentId());
    Assert.assertEquals("  Welcome to SupaWave  ", update.getDocuments().get(0).getTextContent());
    Assert.assertEquals(0, update.getFragments().getEntries().size());
  }

  @Test
  public void decodeSelectedWaveUpdateReadsMentionAnnotationRanges() {
    String json =
        "{\"sequenceNumber\":17,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s4635670bfbwA/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\"],"
            + "\"3\":[{\"1\":\"b+abc123\","
            + "\"2\":{\"1\":[{\"3\":{\"1\":\"body\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"line\",\"2\":[]}},"
            + "{\"1\":{\"3\":[{\"1\":\"mention/user\",\"3\":\"teammate@example.com\"}]}},"
            + "{\"2\":\"@Teammate\"},"
            + "{\"1\":{\"2\":[\"mention/user\"]}},"
            + "{\"2\":\" please review\"},"
            + "{\"4\":true},{\"4\":true}]},"
            + "\"3\":\"user@example.com\",\"5\":[1,0],\"6\":[2,0]}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    SidecarSelectedWaveDocument document = update.getDocuments().get(0);
    Assert.assertEquals("@Teammate please review", document.getTextContent());
    Assert.assertEquals(1, document.getAnnotationRanges().size());
    SidecarAnnotationRange mention = document.getAnnotationRanges().get(0);
    Assert.assertEquals("mention/user", mention.getKey());
    Assert.assertEquals("teammate@example.com", mention.getValue());
    Assert.assertEquals(0, mention.getStartOffset());
    Assert.assertEquals(9, mention.getEndOffset());
  }

  @Test
  public void decodeSelectedWaveUpdateReadsTaskAnnotationRanges() {
    String json =
        "{\"sequenceNumber\":18,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s4635670bfbwA/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\"],"
            + "\"3\":[{\"1\":\"b+task\","
            + "\"2\":{\"1\":[{\"3\":{\"1\":\"body\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"line\",\"2\":[]}},"
            + "{\"1\":{\"3\":["
            + "{\"1\":\"task/id\",\"3\":\"task-123\"},"
            + "{\"1\":\"task/assignee\",\"3\":\"alice@example.com\"},"
            + "{\"1\":\"task/dueTs\",\"3\":\"1714000000000\"}]}},"
            + "{\"2\":\"Review launch\"},"
            + "{\"1\":{\"2\":[\"task/id\",\"task/assignee\",\"task/dueTs\"]}},"
            + "{\"4\":true},{\"4\":true}]},"
            + "\"3\":\"user@example.com\",\"5\":[3,0],\"6\":[4,0]}]},"
            + "\"6\":true,\"7\":\"ch4\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    SidecarSelectedWaveDocument document = update.getDocuments().get(0);
    Assert.assertEquals("Review launch", document.getTextContent());
    Assert.assertEquals(3, document.getAnnotationRanges().size());
    Assert.assertEquals("task/id", document.getAnnotationRanges().get(0).getKey());
    Assert.assertEquals("task-123", document.getAnnotationRanges().get(0).getValue());
    Assert.assertEquals("task/assignee", document.getAnnotationRanges().get(1).getKey());
    Assert.assertEquals("alice@example.com", document.getAnnotationRanges().get(1).getValue());
    Assert.assertEquals("task/dueTs", document.getAnnotationRanges().get(2).getKey());
    Assert.assertEquals("1714000000000", document.getAnnotationRanges().get(2).getValue());
    for (SidecarAnnotationRange range : document.getAnnotationRanges()) {
      Assert.assertEquals(0, range.getStartOffset());
      Assert.assertEquals(13, range.getEndOffset());
    }
  }

  @Test
  public void decodeSelectedWaveUpdateTreatsAnnotationChangeWithoutNewValueAsRemoval() {
    String json =
        "{\"sequenceNumber\":20,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s4635670bfbwA/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\"],"
            + "\"3\":[{\"1\":\"b+abc123\","
            + "\"2\":{\"1\":[{\"3\":{\"1\":\"body\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"line\",\"2\":[]}},"
            + "{\"1\":{\"3\":[{\"1\":\"mention/user\",\"3\":\"teammate@example.com\"}]}},"
            + "{\"2\":\"one\"},"
            + "{\"1\":{\"3\":[{\"1\":\"mention/user\"}]}},"
            + "{\"2\":\" two\"},"
            + "{\"4\":true},{\"4\":true}]},"
            + "\"3\":\"user@example.com\",\"5\":[1,0],\"6\":[2,0]}]},"
            + "\"6\":true,\"7\":\"ch6\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    SidecarSelectedWaveDocument document = update.getDocuments().get(0);
    Assert.assertEquals("one two", document.getTextContent());
    Assert.assertEquals(1, document.getAnnotationRanges().size());
    SidecarAnnotationRange mention = document.getAnnotationRanges().get(0);
    Assert.assertEquals("mention/user", mention.getKey());
    Assert.assertEquals("teammate@example.com", mention.getValue());
    Assert.assertEquals(0, mention.getStartOffset());
    Assert.assertEquals(3, mention.getEndOffset());
  }

  @Test
  public void decodeSelectedWaveUpdateReadsReactionDataDocumentSnapshot() {
    String json =
        "{\"sequenceNumber\":19,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s4635670bfbwA/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\"],"
            + "\"3\":[{\"1\":\"react+b+abc123\","
            + "\"2\":{\"1\":[{\"3\":{\"1\":\"reactions\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"reaction\",\"2\":[{\"1\":\"emoji\",\"2\":\"thumbs_up\"}]}},"
            + "{\"3\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"alice@example.com\"}]}},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"bob@example.com\"}]}},"
            + "{\"4\":true},{\"4\":true},{\"4\":true}]},"
            + "\"3\":\"user@example.com\",\"5\":[5,0],\"6\":[6,0]}]},"
            + "\"6\":true,\"7\":\"ch5\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    SidecarSelectedWaveDocument document = update.getDocuments().get(0);
    Assert.assertTrue(document.isReactionDataDocument());
    Assert.assertEquals("b+abc123", document.getReactionTargetBlipId());
    Assert.assertEquals(1, document.getReactionEntries().size());
    SidecarReactionEntry reaction = document.getReactionEntries().get(0);
    Assert.assertEquals("thumbs_up", reaction.getEmoji());
    Assert.assertEquals(Arrays.asList("alice@example.com", "bob@example.com"), reaction.getAddresses());
  }

  @Test
  public void reactionDataDocumentRejectsPrefixOnlyDocumentId() {
    SidecarSelectedWaveDocument document =
        new SidecarSelectedWaveDocument("react+", "user@example.com", 5L, 6L, "");

    Assert.assertFalse(document.isReactionDataDocument());
    Assert.assertEquals("", document.getReactionTargetBlipId());
  }

  @Test
  public void decodeSelectedWaveUpdateSkipsReactionEntriesWithoutUsers() {
    String json =
        "{\"sequenceNumber\":20,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s4635670bfbwA/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\"],"
            + "\"3\":[{\"1\":\"react+b+abc123\","
            + "\"2\":{\"1\":[{\"3\":{\"1\":\"reactions\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"reaction\",\"2\":[{\"1\":\"emoji\",\"2\":\"thumbs_up\"}]}},"
            + "{\"4\":true},{\"4\":true}]},"
            + "\"3\":\"user@example.com\",\"5\":[5,0],\"6\":[6,0]}]},"
            + "\"6\":true,\"7\":\"ch5\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    SidecarSelectedWaveDocument document = update.getDocuments().get(0);
    Assert.assertTrue(document.isReactionDataDocument());
    Assert.assertEquals("b+abc123", document.getReactionTargetBlipId());
    Assert.assertEquals(0, document.getReactionEntries().size());
  }

  @Test
  public void decodeSelectedWaveUpdateDeduplicatesReactionAddresses() {
    String json =
        "{\"sequenceNumber\":21,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s4635670bfbwA/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\"],"
            + "\"3\":[{\"1\":\"react+b+abc123\","
            + "\"2\":{\"1\":[{\"3\":{\"1\":\"reactions\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"reaction\",\"2\":[{\"1\":\"emoji\",\"2\":\"thumbs_up\"}]}},"
            + "{\"3\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"alice@example.com\"}]}},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"alice@example.com\"}]}},"
            + "{\"4\":true},{\"4\":true},{\"4\":true}]},"
            + "\"3\":\"user@example.com\",\"5\":[5,0],\"6\":[6,0]}]},"
            + "\"6\":true,\"7\":\"ch5\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    SidecarSelectedWaveDocument document = update.getDocuments().get(0);
    Assert.assertEquals(1, document.getReactionEntries().size());
    SidecarReactionEntry reaction = document.getReactionEntries().get(0);
    Assert.assertEquals("thumbs_up", reaction.getEmoji());
    Assert.assertEquals(Arrays.asList("alice@example.com"), reaction.getAddresses());
  }

  @Test
  public void decodeSelectedWaveUpdateMarksMissingFragmentSnapshotVersionAsAbsent() {
    String json =
        "{\"sequenceNumber\":14,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s4635670bfbwA/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\"],"
            + "\"3\":[{\"1\":\"b+abc123\",\"3\":\"user@example.com\",\"5\":[45,0],\"6\":[46,0]}]},"
            + "\"6\":true,\"7\":\"ch4\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    Assert.assertEquals(-1L, update.getFragments().getSnapshotVersion());
    Assert.assertEquals(0, update.getFragments().getEntries().size());
  }

  @Test
  public void decodeRpcFinishedFailureReadsFailedFlagAndErrorText() {
    String json =
        "{\"sequenceNumber\":15,\"messageType\":\"RpcFinished\",\"message\":{\"1\":true,\"2\":\"boom\"}}";

    java.util.Map<String, Object> envelope = SidecarTransportCodec.parseJsonObject(json);

    Assert.assertTrue(SidecarTransportCodec.decodeRpcFinishedFailed(envelope));
    Assert.assertEquals(
        "boom",
        SidecarTransportCodec.decodeRpcFinishedErrorText(
            envelope, "The selected wave request failed."));
  }

  @Test
  public void encodeSubmitEnvelopeUsesProtocolSubmitRequestShape() {
    SidecarSubmitRequest request =
        new SidecarSubmitRequest(
            "example.com/w+abc123/~/conv+root",
            "{\"1\":{\"1\":44,\"2\":\"\"},\"2\":\"user@example.com\",\"3\":[{\"1\":\"user@example.com\"}]}",
            "chan-9");

    String json = SidecarTransportCodec.encodeSubmitEnvelope(15, request);

    Assert.assertTrue(json.contains("\"sequenceNumber\":15"));
    Assert.assertTrue(json.contains("\"messageType\":\"ProtocolSubmitRequest\""));
    Assert.assertTrue(json.contains("\"1\":\"example.com/w+abc123/~/conv+root\""));
    Assert.assertTrue(json.contains("\"2\":{\"1\":{\"1\":44,\"2\":\"\"},\"2\":\"user@example.com\""));
    Assert.assertTrue(json.contains("\"3\":\"chan-9\""));
  }

  @Test
  public void decodeSubmitResponseReadsOperationsErrorAndVersion() {
    String json =
        "{\"sequenceNumber\":16,\"messageType\":\"ProtocolSubmitResponse\",\"message\":{"
            + "\"1\":2,\"2\":\"submit boom\",\"3\":{\"1\":46,\"2\":\"\"}}}";

    SidecarSubmitResponse response =
        SidecarTransportCodec.decodeSubmitResponse(SidecarTransportCodec.parseJsonObject(json));

    Assert.assertEquals(2, response.getOperationsApplied());
    Assert.assertEquals("submit boom", response.getErrorMessage());
    Assert.assertEquals(46L, response.getResultingVersion());
  }

  @Test
  public void extractSessionBootstrapAddressFromRootHtml() {
    String html =
        "<html><script>window.__session={\"address\":\"user@example.com\",\"id\":\"abc\"};"
            + "window.__websocket_address=\"socket.example.test:7443\";"
            + "</script></html>";

    SidecarSessionBootstrap bootstrap = SidecarSessionBootstrap.fromRootHtml(html);

    Assert.assertEquals("user@example.com", bootstrap.getAddress());
    Assert.assertEquals("socket.example.test:7443", bootstrap.getWebSocketAddress());
  }

  @Test
  public void extractSessionBootstrapRejectsMissingAddress() {
    String html = "<html><script>window.__session={\"id\":\"abc\"};</script></html>";
    expectIllegalArgumentContains(
        "did not include an address", () -> SidecarSessionBootstrap.fromRootHtml(html));
  }

  @Test
  public void extractSessionBootstrapFromBootstrapJson() {
    String json =
        "{\"session\":{\"domain\":\"example.com\",\"address\":\"user@example.com\","
            + "\"role\":\"user\",\"features\":[\"mentions-search\"]},"
            + "\"socket\":{\"address\":\"socket.example.test:7443\"},"
            + "\"shell\":{\"buildCommit\":\"abc\",\"serverBuildTime\":1700000000000,"
            + "\"currentReleaseId\":\"r1\",\"routeReturnTarget\":\"/?view=j2cl-root\"}}";

    SidecarSessionBootstrap bootstrap = SidecarSessionBootstrap.fromBootstrapJson(json);

    Assert.assertEquals("user@example.com", bootstrap.getAddress());
    Assert.assertEquals("socket.example.test:7443", bootstrap.getWebSocketAddress());
  }

  @Test
  public void bootstrapJsonIgnoresLegacySessionIdSeed() {
    String json =
        "{\"session\":{\"domain\":\"example.com\",\"address\":\"user@example.com\","
            + "\"id\":\"legacy-seed\",\"future\":\"ignored\"},"
            + "\"socket\":{\"address\":\"socket.example.test:7443\",\"token\":\"future-933-token\"}}";

    SidecarSessionBootstrap bootstrap = SidecarSessionBootstrap.fromBootstrapJson(json);

    Assert.assertEquals("user@example.com", bootstrap.getAddress());
    Assert.assertEquals("socket.example.test:7443", bootstrap.getWebSocketAddress());
  }

  @Test
  public void bootstrapValueObjectDoesNotExposeJsonSessionSeed() {
    for (java.lang.reflect.Method method : SidecarSessionBootstrap.class.getMethods()) {
      if (method.getReturnType().equals(Void.TYPE)) {
        continue;
      }
      String name = method.getName().toLowerCase(java.util.Locale.ROOT);
      Assert.assertFalse(
          "Unexpected seed accessor: " + method.getName(),
          name.startsWith("get") && name.contains("seed"));
      Assert.assertFalse(
          "Unexpected session id accessor: " + method.getName(), name.equals("getsessionid"));
    }
  }

  @Test
  public void bootstrapJsonIgnoresUnknownKeysUnderSocketForIssue933Compat() {
    String json =
        "{\"session\":{\"address\":\"user@example.com\"},"
            + "\"socket\":{\"address\":\"socket.example.test:7443\",\"token\":\"future-933-token\"}}";

    SidecarSessionBootstrap bootstrap = SidecarSessionBootstrap.fromBootstrapJson(json);

    Assert.assertEquals("user@example.com", bootstrap.getAddress());
    Assert.assertEquals("socket.example.test:7443", bootstrap.getWebSocketAddress());
  }

  @Test
  public void bootstrapJsonRejectsMissingSocketAddress() {
    String json = "{\"session\":{\"address\":\"user@example.com\"},\"socket\":{}}";
    expectIllegalArgumentContains(
        "socket address", () -> SidecarSessionBootstrap.fromBootstrapJson(json));
  }

  @Test
  public void bootstrapJsonSignalsSignedOutWithIllegalStateWhenAddressMissing() {
    String json = "{\"session\":{\"domain\":\"example.com\"},\"socket\":{\"address\":\"h:1\"}}";
    try {
      SidecarSessionBootstrap.fromBootstrapJson(json);
      Assert.fail("Expected IllegalStateException for signed-out session bootstrap");
    } catch (IllegalStateException expected) {
      Assert.assertTrue(
          "Expected sign-in prompt, got: " + expected.getMessage(),
          expected.getMessage().contains("sign in"));
    }
  }

  @Test
  public void bootstrapJsonRejectsMissingSessionObject() {
    String json = "{\"socket\":{\"address\":\"h:1\"}}";
    expectIllegalArgumentContains(
        "session object", () -> SidecarSessionBootstrap.fromBootstrapJson(json));
  }

  @Test
  public void parseJsonObjectRejectsInvalidUnicodeEscapeSequences() {
    expectIllegalArgumentContains(
        "unicode escape",
        () -> SidecarTransportCodec.parseJsonObject("{\"1\":\"\\u12\"}"));
    expectIllegalArgumentContains(
        "unicode escape",
        () -> SidecarTransportCodec.parseJsonObject("{\"1\":\"\\u12xz\"}"));
  }

  @Test
  public void decodeSelectedWaveUpdatePreservesSentinelWhenResultingVersionFieldOneAbsent() {
    String json =
        "{\"sequenceNumber\":15,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"example.com!w+abc/example.com!conv+root\","
            + "\"4\":{\"2\":\"HASHONLY\"},"
            + "\"6\":true,\"7\":\"ch5\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    Assert.assertEquals(-1L, update.getResultingVersion());
  }

  @Test
  public void decodeSubmitResponsePreservesSentinelWhenVersionFieldOneAbsent() {
    String json =
        "{\"sequenceNumber\":16,\"messageType\":\"ProtocolSubmitResponse\",\"message\":{"
            + "\"1\":1,\"2\":\"\",\"3\":{\"2\":\"HASHONLY\"}}}";

    SidecarSubmitResponse response =
        SidecarTransportCodec.decodeSubmitResponse(SidecarTransportCodec.parseJsonObject(json));

    Assert.assertEquals(-1L, response.getResultingVersion());
  }

  @Test
  public void decodeSubmitResponseRejectsFractionalOperationsApplied() {
    expectIllegalArgumentContains(
        "integral numeric value for 1",
        () ->
            SidecarTransportCodec.decodeSubmitResponse(
                SidecarTransportCodec.parseJsonObject(
                    "{\"sequenceNumber\":16,\"messageType\":\"ProtocolSubmitResponse\",\"message\":{"
                        + "\"1\":1.5,\"2\":\"\",\"3\":{\"1\":46,\"2\":\"\"}}}")));
  }

  @Test
  public void decodeSelectedWaveUpdateRejectsFractionalResultingVersion() {
    expectIllegalArgumentContains(
        "integral numeric value for 1",
        () ->
            SidecarTransportCodec.decodeSelectedWaveUpdate(
                "{\"sequenceNumber\":15,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
                    + "\"1\":\"example.com!w+abc/example.com!conv+root\","
                    + "\"4\":{\"1\":44.5,\"2\":\"ABCD\"},"
                    + "\"6\":true,\"7\":\"ch5\"}}"));
  }

  @Test
  public void decodeSubmitResponseRejectsNonFiniteVersionNumbers() {
    Map<String, Object> hashedVersion = new LinkedHashMap<String, Object>();
    hashedVersion.put("1", Double.POSITIVE_INFINITY);
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("1", Long.valueOf(1));
    payload.put("2", "");
    payload.put("3", hashedVersion);
    Map<String, Object> envelope = new LinkedHashMap<String, Object>();
    envelope.put("message", payload);

    expectIllegalArgumentContains(
        "finite numeric value for 1",
        () -> SidecarTransportCodec.decodeSubmitResponse(envelope));
  }

  private static void expectIllegalArgumentContains(String substring, Runnable action) {
    try {
      action.run();
      Assert.fail("Expected IllegalArgumentException containing: " + substring);
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(
          "Expected message to contain \"" + substring + "\" but was: " + expected.getMessage(),
          expected.getMessage().contains(substring));
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object value) {
    return (Map<String, Object>) value;
  }

  // ─── J-UI-4 (#1082, R-3.1) — conversation manifest extraction ──────────

  @Test
  public void decodeSelectedWaveUpdateLeavesManifestEmptyWhenNoConversationDocument() {
    String json =
        "{\"sequenceNumber\":13,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"a@example.com\"],"
            + "\"3\":[{\"1\":\"b+1\",\"2\":{\"1\":[{\"2\":\"hello\"}]},"
            + "\"3\":\"a@example.com\",\"5\":[1,0],\"6\":[2,0]}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    Assert.assertTrue(update.getConversationManifest().isEmpty());
  }

  @Test
  public void decodeSelectedWaveUpdateExtractsRootThreadBlipsFromConversationManifest() {
    String json =
        "{\"sequenceNumber\":13,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"a@example.com\"],"
            + "\"3\":[{\"1\":\"conversation\",\"2\":{\"1\":["
            + "{\"3\":{\"1\":\"conversation\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"root\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+a\"}]}},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+b\"}]}},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"4\":true}]}}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    SidecarConversationManifest manifest = update.getConversationManifest();
    Assert.assertFalse(manifest.isEmpty());
    Assert.assertEquals(2, manifest.getOrderedEntries().size());
    SidecarConversationManifest.Entry first = manifest.getOrderedEntries().get(0);
    Assert.assertEquals("b+a", first.getBlipId());
    Assert.assertEquals("", first.getParentBlipId());
    Assert.assertEquals("root", first.getThreadId());
    Assert.assertEquals(0, first.getDepth());
    Assert.assertEquals(0, first.getSiblingIndex());
    SidecarConversationManifest.Entry second = manifest.getOrderedEntries().get(1);
    Assert.assertEquals("b+b", second.getBlipId());
    Assert.assertEquals("", second.getParentBlipId());
    Assert.assertEquals("root", second.getThreadId());
    Assert.assertEquals(1, second.getSiblingIndex());
  }

  @Test
  public void decodeSelectedWaveUpdateExtractsNestedReplyThreadFromConversationManifest() {
    // <conversation>
    //   <thread id="root">
    //     <blip id="b+parent"/>
    //     <thread id="t+reply">
    //       <blip id="b+child"/>
    //     </thread>
    //   </thread>
    // </conversation>
    String json =
        "{\"sequenceNumber\":13,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"a@example.com\"],"
            + "\"3\":[{\"1\":\"conversation\",\"2\":{\"1\":["
            + "{\"3\":{\"1\":\"conversation\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"root\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+parent\"}]}},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"t+reply\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+child\"}]}},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"4\":true}]}}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarConversationManifest manifest =
        SidecarTransportCodec.decodeSelectedWaveUpdate(json).getConversationManifest();

    Assert.assertEquals(2, manifest.getOrderedEntries().size());
    SidecarConversationManifest.Entry parent = manifest.findByBlipId("b+parent");
    Assert.assertNotNull(parent);
    Assert.assertEquals("", parent.getParentBlipId());
    Assert.assertEquals("root", parent.getThreadId());
    Assert.assertEquals(0, parent.getDepth());
    SidecarConversationManifest.Entry child = manifest.findByBlipId("b+child");
    Assert.assertNotNull(child);
    Assert.assertEquals("b+parent", child.getParentBlipId());
    Assert.assertEquals("t+reply", child.getThreadId());
    Assert.assertEquals(1, child.getDepth());
    Assert.assertEquals(0, child.getSiblingIndex());
    Assert.assertEquals(
        Arrays.asList("b+child"), manifest.getChildBlipIds("b+parent"));
    Assert.assertEquals(
        Arrays.asList("b+parent"), manifest.getChildBlipIds(""));
  }

  @Test
  public void decodeSelectedWaveUpdateTracksReplyThreadInsertPositions() {
    String json =
        "{\"sequenceNumber\":13,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"a@example.com\"],"
            + "\"3\":[{\"1\":\"conversation\",\"2\":{\"1\":["
            + "{\"3\":{\"1\":\"conversation\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+root\"}]}},"
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"t+reply\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+child\"}]}},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"4\":true}]}}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarConversationManifest manifest =
        SidecarTransportCodec.decodeSelectedWaveUpdate(json).getConversationManifest();

    Assert.assertEquals(6, manifest.findByBlipId("b+root").getReplyInsertPosition());
    Assert.assertEquals(4, manifest.findByBlipId("b+child").getReplyInsertPosition());
    Assert.assertEquals(8, manifest.getItemCount());
  }

  @Test
  public void decodeSelectedWaveUpdateExtractsThreeLevelDeepReplyChain() {
    // <conversation>
    //   <thread id="root">
    //     <blip id="b+1"/>
    //     <thread id="t+a"><blip id="b+2"/>
    //       <thread id="t+b"><blip id="b+3"/></thread>
    //     </thread>
    //   </thread>
    // </conversation>
    String json =
        "{\"sequenceNumber\":13,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"a@example.com\"],"
            + "\"3\":[{\"1\":\"conversation\",\"2\":{\"1\":["
            + "{\"3\":{\"1\":\"conversation\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"root\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+1\"}]}},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"t+a\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+2\"}]}},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"t+b\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+3\"}]}},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"4\":true}]}}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarConversationManifest manifest =
        SidecarTransportCodec.decodeSelectedWaveUpdate(json).getConversationManifest();

    Assert.assertEquals(3, manifest.getOrderedEntries().size());
    Assert.assertEquals("b+1", manifest.getOrderedEntries().get(0).getBlipId());
    Assert.assertEquals(0, manifest.getOrderedEntries().get(0).getDepth());
    Assert.assertEquals("b+2", manifest.getOrderedEntries().get(1).getBlipId());
    Assert.assertEquals("b+1", manifest.getOrderedEntries().get(1).getParentBlipId());
    Assert.assertEquals(1, manifest.getOrderedEntries().get(1).getDepth());
    Assert.assertEquals("b+3", manifest.getOrderedEntries().get(2).getBlipId());
    Assert.assertEquals("b+2", manifest.getOrderedEntries().get(2).getParentBlipId());
    Assert.assertEquals(2, manifest.getOrderedEntries().get(2).getDepth());
  }

  @Test
  public void decodeSelectedWaveUpdateGivesEachOpenThreadItsOwnSiblingCounter() {
    // <conversation>
    //   <thread id="root">
    //     <blip id="b+1"/>
    //     <thread id="t+a">
    //       <blip id="b+1a"/>
    //       <blip id="b+1b"/>     <!-- siblingIndex 1 inside t+a under b+1 -->
    //     </thread>
    //     <blip id="b+2"/>
    //     <thread id="t+a">       <!-- re-used id under a different parent -->
    //       <blip id="b+2a"/>     <!-- must be siblingIndex 0, not 2 -->
    //     </thread>
    //   </thread>
    // </conversation>
    String json =
        "{\"sequenceNumber\":13,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"a@example.com\"],"
            + "\"3\":[{\"1\":\"conversation\",\"2\":{\"1\":["
            + "{\"3\":{\"1\":\"conversation\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"root\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+1\"}]}},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"t+a\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+1a\"}]}},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+1b\"}]}},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+2\"}]}},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"t+a\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+2a\"}]}},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"4\":true},"
            + "{\"4\":true}]}}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarConversationManifest manifest =
        SidecarTransportCodec.decodeSelectedWaveUpdate(json).getConversationManifest();

    Assert.assertEquals(0, manifest.findByBlipId("b+1a").getSiblingIndex());
    Assert.assertEquals(1, manifest.findByBlipId("b+1b").getSiblingIndex());
    // b+2a must be siblingIndex 0 inside its own (separate) t+a
    // thread, not 2 — review-1089 round-1 collision check.
    Assert.assertEquals(0, manifest.findByBlipId("b+2a").getSiblingIndex());
    Assert.assertEquals("b+2", manifest.findByBlipId("b+2a").getParentBlipId());
  }

  @Test
  public void decodeSelectedWaveUpdateLeavesManifestEmptyWhenConversationDocOpHasNoComponents() {
    String json =
        "{\"sequenceNumber\":13,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"a@example.com\"],"
            + "\"3\":[{\"1\":\"conversation\",\"2\":{}}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    Assert.assertTrue(update.getConversationManifest().isEmpty());
  }

  @Test
  public void decodeSelectedWaveUpdateSkipsBlipElementsWithMissingId() {
    String json =
        "{\"sequenceNumber\":13,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"a@example.com\"],"
            + "\"3\":[{\"1\":\"conversation\",\"2\":{\"1\":["
            + "{\"3\":{\"1\":\"thread\",\"2\":[{\"1\":\"id\",\"2\":\"root\"}]}},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[]}},"
            + "{\"4\":true},"
            + "{\"3\":{\"1\":\"blip\",\"2\":[{\"1\":\"id\",\"2\":\"b+real\"}]}},"
            + "{\"4\":true},"
            + "{\"4\":true}]}}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarConversationManifest manifest =
        SidecarTransportCodec.decodeSelectedWaveUpdate(json).getConversationManifest();

    Assert.assertEquals(1, manifest.getOrderedEntries().size());
    Assert.assertEquals("b+real", manifest.getOrderedEntries().get(0).getBlipId());
  }
}
