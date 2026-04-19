package org.waveprotocol.box.j2cl.transport;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Method;
import java.util.Arrays;
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
  public void decodeSelectedWaveUpdateReadsSnapshotAndFragmentsForSidecarProjection()
      throws Exception {
    String json =
        "{\"sequenceNumber\":12,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"example.com!w+abc123/example.com!conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\",\"teammate@example.com\"],"
            + "\"3\":[{\"1\":\"b+root\",\"3\":\"user@example.com\",\"5\":[33,0],\"6\":[44,0]}]},"
            + "\"6\":true,\"7\":\"chan-2\","
            + "\"8\":{\"1\":[44,0],\"2\":[40,0],\"3\":[44,0],"
            + "\"4\":[{\"1\":\"manifest\",\"2\":[40,0],\"3\":[44,0]},"
            + "{\"1\":\"blip:b+root\",\"2\":[41,0],\"3\":[44,0]}],"
            + "\"5\":[{\"1\":\"manifest\",\"2\":{\"1\":\"conversation: Inbox wave\"}},"
            + "{\"1\":\"blip:b+root\",\"2\":{\"1\":\"Hello from the sidecar\"},\"3\":[],\"4\":[]}]}}}";

    Method decoder =
        SidecarTransportCodec.class.getMethod("decodeSelectedWaveUpdate", String.class);
    Object update = decoder.invoke(null, json);

    Assert.assertEquals(
        "example.com!w+abc123/example.com!conv+root",
        update.getClass().getMethod("getWaveletName").invoke(update));
    Assert.assertEquals("chan-2", update.getClass().getMethod("getChannelId").invoke(update));
    Assert.assertEquals(Boolean.TRUE, update.getClass().getMethod("hasMarker").invoke(update));

    Object participants = update.getClass().getMethod("getParticipantIds").invoke(update);
    Assert.assertEquals(2, ((java.util.List<?>) participants).size());

    Object documents = update.getClass().getMethod("getDocuments").invoke(update);
    Assert.assertEquals(1, ((java.util.List<?>) documents).size());
    Object document = ((java.util.List<?>) documents).get(0);
    Assert.assertEquals("b+root", document.getClass().getMethod("getDocumentId").invoke(document));

    Object fragments = update.getClass().getMethod("getFragments").invoke(update);
    Assert.assertEquals(44L, fragments.getClass().getMethod("getSnapshotVersion").invoke(fragments));
    Assert.assertEquals(2, ((java.util.List<?>) fragments.getClass().getMethod("getRanges").invoke(fragments)).size());
    Object fragment = ((java.util.List<?>) fragments.getClass().getMethod("getEntries").invoke(fragments)).get(1);
    Assert.assertEquals("blip:b+root", fragment.getClass().getMethod("getSegment").invoke(fragment));
    Assert.assertEquals(
        "Hello from the sidecar",
        fragment.getClass().getMethod("getRawSnapshot").invoke(fragment));
  }

  @Test
  public void decodeSelectedWaveUpdateReadsSnapshotDocumentTextWhenFragmentsAreAbsent()
      throws Exception {
    String json =
        "{\"sequenceNumber\":13,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
            + "\"1\":\"local.net!w+s4635670bfbwA/~/conv+root\","
            + "\"5\":{\"1\":\"conv+root\",\"2\":[\"user@example.com\"],"
            + "\"3\":[{\"1\":\"b+abc123\","
            + "\"2\":{\"1\":[{\"3\":{\"1\":\"body\",\"2\":[]}},"
            + "{\"3\":{\"1\":\"line\",\"2\":[]}},"
            + "{\"2\":\"Welcome to SupaWave\"},"
            + "{\"4\":true},{\"4\":true}]},"
            + "\"3\":\"user@example.com\",\"5\":[1,0],\"6\":[2,0]}]},"
            + "\"6\":true,\"7\":\"ch3\"}}";

    SidecarSelectedWaveUpdate update = SidecarTransportCodec.decodeSelectedWaveUpdate(json);

    Assert.assertEquals(1, update.getDocuments().size());
    Assert.assertEquals("b+abc123", update.getDocuments().get(0).getDocumentId());
    Assert.assertEquals("Welcome to SupaWave", update.getDocuments().get(0).getTextContent());
    Assert.assertEquals(0, update.getFragments().getEntries().size());
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
