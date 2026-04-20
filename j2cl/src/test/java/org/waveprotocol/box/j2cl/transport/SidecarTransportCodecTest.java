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
  public void encodeAuthenticateEnvelopePreservesLegacyWrapperShape() {
    String json = SidecarTransportCodec.encodeAuthenticateEnvelope(7, "cookie-token");

    Assert.assertTrue(json.contains("\"sequenceNumber\":7"));
    Assert.assertTrue(json.contains("\"messageType\":\"ProtocolAuthenticate\""));
    Assert.assertTrue(json.contains("\"message\":{\"1\":\"cookie-token\"}"));
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
}
