package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;

@J2clTestInput(J2clPlainTextDeltaFactoryTest.class)
public class J2clPlainTextDeltaFactoryTest {
  @Test
  public void createWaveRequestGeneratesSelfOwnedConversationRootDelta() {
    J2clPlainTextDeltaFactory factory = new J2clPlainTextDeltaFactory("seed");

    J2clPlainTextDeltaFactory.CreateWaveRequest request =
        factory.createWaveRequest("user@example.com", "Hello\nSidecar");

    Assert.assertEquals("example.com/w+seedA", request.getCreatedWaveId());
    assertSubmitRequest(
        request.getSubmitRequest(),
        "example.com/w+seedA/~/conv+root",
        null,
        "\"1\":{\"1\":0,\"2\":\""
            + encodeHex("wave://example.com/w+seedA/conv+root")
            + "\"}",
        "\"2\":\"user@example.com\"",
        "\"1\":\"b+root\"",
        "Hello\\nSidecar");
    Assert.assertTrue(
        request.getSubmitRequest().getDeltaJson().contains("\"1\":\"user@example.com\""));
  }

  @Test
  public void replyRequestTargetsOpenedWaveAndCarriesChannelAndVersion() {
    J2clPlainTextDeltaFactory factory = new J2clPlainTextDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession("example.com/w+reply", "chan-7", 44L, "ABCD", "b+root");

    SidecarSubmitRequest request =
        factory.createReplyRequest("user@example.com", session, "Plain reply");

    assertSubmitRequest(
        request,
        "example.com/w+reply/~/conv+root",
        "chan-7",
        "\"1\":{\"1\":44,\"2\":\"ABCD\"}",
        "\"2\":\"user@example.com\"",
        "\"1\":\"b+seedA\"",
        "Plain reply");
  }

  @Test
  public void replyRequestLinksNewBlipIntoManifestWhenInsertPositionKnown() {
    J2clPlainTextDeltaFactory factory = new J2clPlainTextDeltaFactory("seed");
    J2clSidecarWriteSession session =
        new J2clSidecarWriteSession(
            "example.com/w+reply", "chan-7", 44L, "ABCD", "b+root", 6, 8);

    SidecarSubmitRequest request =
        factory.createReplyRequest("user@example.com", session, "Plain reply");
    String deltaJson = request.getDeltaJson();

    Assert.assertEquals("b+seedA", request.getClientCreatedBlipId());
    assertSubmitRequest(
        request,
        "example.com/w+reply/~/conv+root",
        "chan-7",
        "\"1\":{\"1\":44,\"2\":\"ABCD\"}",
        "\"2\":\"user@example.com\"",
        "\"1\":\"b+seedA\"",
        "Plain reply");
    Assert.assertTrue(deltaJson.contains("\"1\":\"conversation\""));
    Assert.assertTrue(deltaJson.contains("{\"5\":6}"));
    Assert.assertTrue(deltaJson.contains("{\"5\":2}"));
    Assert.assertEquals(
        "reply blip must appear once in manifest and once as the new document",
        2,
        countOccurrences(deltaJson, "b+seedA"));
  }

  private static void assertSubmitRequest(
      SidecarSubmitRequest request,
      String expectedWaveletName,
      String expectedChannelId,
      String versionFragment,
      String authorFragment,
      String documentIdFragment,
      String textFragment) {
    Assert.assertEquals(expectedWaveletName, request.getWaveletName());
    Assert.assertEquals(expectedChannelId, request.getChannelId());
    Assert.assertTrue(request.getDeltaJson().contains(versionFragment));
    Assert.assertTrue(request.getDeltaJson().contains(authorFragment));
    Assert.assertTrue(request.getDeltaJson().contains(documentIdFragment));
    Assert.assertTrue(request.getDeltaJson().contains(textFragment));
  }

  // ASCII-only: mirrors production encodeHex which treats each char as a single byte.
  // Test fixtures use ASCII wave URIs only; non-ASCII inputs are not supported.
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

  private static int countOccurrences(String value, String fragment) {
    if (fragment == null || fragment.isEmpty()) {
      throw new IllegalArgumentException("fragment must be non-empty");
    }
    int count = 0;
    int cursor = 0;
    while (cursor >= 0) {
      cursor = value.indexOf(fragment, cursor);
      if (cursor >= 0) {
        count++;
        cursor += fragment.length();
      }
    }
    return count;
  }
}
