package org.waveprotocol.box.j2cl.transport;

import com.google.j2cl.junit.apt.J2clTestInput;
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
  public void extractSessionBootstrapAddressFromRootHtml() {
    String html =
        "<html><script>window.__session={\"address\":\"user@example.com\",\"id\":\"abc\"};"
            + "</script></html>";

    SidecarSessionBootstrap bootstrap = SidecarSessionBootstrap.fromRootHtml(html);

    Assert.assertEquals("user@example.com", bootstrap.getAddress());
  }

  @Test
  public void extractSessionBootstrapRejectsMissingAddress() {
    String html = "<html><script>window.__session={\"id\":\"abc\"};</script></html>";

    try {
      SidecarSessionBootstrap.fromRootHtml(html);
      Assert.fail("Expected missing address to be rejected");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("did not include an address"));
    }
  }

  @Test
  public void parseJsonObjectRejectsInvalidUnicodeEscapeSequences() {
    try {
      SidecarTransportCodec.parseJsonObject("{\"1\":\"\\u12\"}");
      Assert.fail("Expected truncated unicode escape to fail");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("unicode escape"));
    }

    try {
      SidecarTransportCodec.parseJsonObject("{\"1\":\"\\u12xz\"}");
      Assert.fail("Expected non-hex unicode escape to fail");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("unicode escape"));
    }
  }
}
