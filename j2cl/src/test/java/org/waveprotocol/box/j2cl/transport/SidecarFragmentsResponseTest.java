package org.waveprotocol.box.j2cl.transport;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(SidecarFragmentsResponseTest.class)
public class SidecarFragmentsResponseTest {
  @Test
  public void decodeFragmentsServletResponsePreservesRangesAndRawSnapshots() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":44,\"start\":40,\"end\":44},"
                + "\"ranges\":[{\"segment\":\"manifest\",\"from\":40,\"to\":44},"
                + "{\"segment\":\"blip:b+root\",\"from\":41,\"to\":44}],"
                + "\"fragments\":[{\"segment\":\"manifest\",\"rawSnapshot\":\"metadata\","
                + "\"adjust\":[],\"diff\":[]},"
                + "{\"segment\":\"blip:b+root\",\"rawSnapshot\":\"Root text\","
                + "\"adjust\":[{}],\"diff\":[{},{}]}]}");

    Assert.assertEquals("ok", response.getStatus());
    Assert.assertEquals("example.com/w+abc/~/conv+root", response.getWaveRefPath());
    Assert.assertEquals(44L, response.getFragments().getSnapshotVersion());
    Assert.assertEquals(40L, response.getFragments().getStartVersion());
    Assert.assertEquals(44L, response.getFragments().getEndVersion());
    Assert.assertEquals(2, response.getFragments().getRanges().size());
    Assert.assertEquals(2, response.getFragments().getEntries().size());
    Assert.assertEquals(
        "blip:b+root", response.getFragments().getEntries().get(1).getSegment());
    Assert.assertEquals("Root text", response.getFragments().getEntries().get(1).getRawSnapshot());
    Assert.assertEquals(1, response.getFragments().getEntries().get(1).getAdjustOperationCount());
    Assert.assertEquals(2, response.getFragments().getEntries().get(1).getDiffOperationCount());
  }

  @Test
  public void rawManifestFragmentParsesConversationTree() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":71,\"start\":71,\"end\":71},"
                + "\"ranges\":[{\"segment\":\"manifest\",\"from\":71,\"to\":71},"
                + "{\"segment\":\"blip:b+tail\",\"from\":71,\"to\":71}],"
                + "\"fragments\":[{\"segment\":\"manifest\","
                + "\"rawSnapshot\":\"<conversation><blip id=\\\"b+root\\\">"
                + "<thread id=\\\"t+first\\\"><blip id=\\\"b+second\\\">"
                + "<thread id=\\\"t+nested\\\"><blip id=\\\"b+nested\\\"/>"
                + "</thread></blip></thread><thread id=\\\"t+third\\\">"
                + "<blip id=\\\"b+third\\\"/></thread></blip></conversation>\","
                + "\"adjust\":[],\"diff\":[]},"
                + "{\"segment\":\"blip:b+tail\",\"rawSnapshot\":\"Tail\","
                + "\"adjust\":[],\"diff\":[]}]}");

    SidecarConversationManifest manifest =
        SidecarConversationManifest.fromFragments(response.getFragments());

    Assert.assertFalse(manifest.isEmpty());
    Assert.assertEquals(4, manifest.getOrderedEntries().size());
    Assert.assertEquals("b+root", manifest.getOrderedEntries().get(0).getBlipId());
    Assert.assertEquals("b+second", manifest.getOrderedEntries().get(1).getBlipId());
    Assert.assertEquals("b+nested", manifest.getOrderedEntries().get(2).getBlipId());
    Assert.assertEquals("b+third", manifest.getOrderedEntries().get(3).getBlipId());
    Assert.assertEquals("b+root", manifest.findByBlipId("b+second").getParentBlipId());
    Assert.assertEquals("b+second", manifest.findByBlipId("b+nested").getParentBlipId());
    Assert.assertEquals("b+root", manifest.findByBlipId("b+third").getParentBlipId());
  }

  @Test
  public void rawManifestTracksReplyThreadInsertPositions() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":71,\"start\":71,\"end\":71},"
                + "\"ranges\":[{\"segment\":\"manifest\",\"from\":71,\"to\":71}],"
                + "\"fragments\":[{\"segment\":\"manifest\","
                + "\"rawSnapshot\":\"<conversation><blip id=\\\"b+root\\\">"
                + "<thread id=\\\"t+first\\\"><blip id=\\\"b+child\\\"/>"
                + "</thread></blip></conversation>\","
                + "\"adjust\":[],\"diff\":[]}]}");

    SidecarConversationManifest manifest =
        SidecarConversationManifest.fromFragments(response.getFragments());

    Assert.assertEquals(6, manifest.findByBlipId("b+root").getReplyInsertPosition());
    Assert.assertEquals(4, manifest.findByBlipId("b+child").getReplyInsertPosition());
    Assert.assertEquals(8, manifest.getItemCount());
  }

  @Test
  public void rawManifestTracksSelfClosingRootBlipInsertPositionAndItemCount() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":71,\"start\":71,\"end\":71},"
                + "\"ranges\":[{\"segment\":\"manifest\",\"from\":71,\"to\":71}],"
                + "\"fragments\":[{\"segment\":\"manifest\","
                + "\"rawSnapshot\":\"<conversation><blip id=\\\"b+root\\\"/></conversation>\","
                + "\"adjust\":[],\"diff\":[]}]}");

    SidecarConversationManifest manifest =
        SidecarConversationManifest.fromFragments(response.getFragments());

    Assert.assertEquals(1, manifest.getOrderedEntries().size());
    Assert.assertEquals(2, manifest.findByBlipId("b+root").getReplyInsertPosition());
    Assert.assertEquals(4, manifest.getItemCount());
  }

  @Test
  public void rawManifestSelfClosingThreadDoesNotLeakParentStack() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":72,\"start\":72,\"end\":72},"
                + "\"ranges\":[{\"segment\":\"manifest\",\"from\":72,\"to\":72}],"
                + "\"fragments\":[{\"segment\":\"manifest\","
                + "\"rawSnapshot\":\"<conversation><blip id=\\\"b+root\\\">"
                + "<thread id=\\\"t+empty\\\"/><thread id=\\\"t+reply\\\">"
                + "<blip id=\\\"b+reply\\\"/></thread></blip>"
                + "<blip id=\\\"b+tail\\\"/></conversation>\","
                + "\"adjust\":[],\"diff\":[]}]}");

    SidecarConversationManifest manifest =
        SidecarConversationManifest.fromFragments(response.getFragments());

    Assert.assertEquals(3, manifest.getOrderedEntries().size());
    SidecarConversationManifest.Entry reply = manifest.findByBlipId("b+reply");
    Assert.assertEquals("b+root", reply.getParentBlipId());
    Assert.assertEquals("t+reply", reply.getThreadId());
    Assert.assertEquals(
        "self-closing t+empty must not make the following reply look two levels deep",
        1,
        reply.getDepth());
    SidecarConversationManifest.Entry tail = manifest.findByBlipId("b+tail");
    Assert.assertEquals("", tail.getParentBlipId());
    Assert.assertEquals("", tail.getThreadId());
    Assert.assertEquals(0, tail.getDepth());
  }

  @Test
  public void rawManifestParserRespectsQuotedGreaterThanInAttributeValues() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":73,\"start\":73,\"end\":73},"
                + "\"ranges\":[{\"segment\":\"manifest\",\"from\":73,\"to\":73}],"
                + "\"fragments\":[{\"segment\":\"manifest\","
                + "\"rawSnapshot\":\"<conversation><thread id='root'>"
                + "<blip id='b&gt;root'/><blip id=\\\"b>raw\\\"/>"
                + "</thread></conversation>\","
                + "\"adjust\":[],\"diff\":[]}]}");

    SidecarConversationManifest manifest =
        SidecarConversationManifest.fromFragments(response.getFragments());

    Assert.assertEquals(2, manifest.getOrderedEntries().size());
    Assert.assertEquals("b&gt;root", manifest.getOrderedEntries().get(0).getBlipId());
    Assert.assertEquals("b>raw", manifest.getOrderedEntries().get(1).getBlipId());
  }

  @Test
  public void decodeFragmentsServletResponsePreservesBlipMetadata() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":71,\"start\":71,\"end\":71},"
                + "\"blips\":[{\"id\":\"b+tail\",\"author\":\"author@example.com\","
                + "\"lastModifiedTime\":1777463436277}],"
                + "\"fragments\":[{\"segment\":\"blip:b+tail\","
                + "\"rawSnapshot\":\"Tail text\",\"adjust\":[],\"diff\":[]}]}");

    Assert.assertEquals(1, response.getBlips().size());
    Assert.assertEquals("b+tail", response.getBlips().get(0).getId());
    Assert.assertEquals("author@example.com", response.getBlips().get(0).getAuthor());
    Assert.assertEquals(1777463436277L, response.getBlips().get(0).getLastModifiedTime());
  }

  @Test
  public void decodeFragmentsServletResponseRejectsErrorStatus() {
    try {
      SidecarFragmentsResponse.fromJson("{\"status\":\"error\"}");
      Assert.fail("Expected error status to be rejected");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("status"));
    }
  }

  @Test
  public void decodeFragmentsServletResponseAllowsMissingRangesAndFragments() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":44,\"start\":40,\"end\":44}}");

    Assert.assertTrue(response.getFragments().getRanges().isEmpty());
    Assert.assertTrue(response.getFragments().getEntries().isEmpty());
  }

  @Test
  public void decodeFragmentsServletResponsePreservesMissingRawSnapshotAsNull() {
    SidecarFragmentsResponse response =
        SidecarFragmentsResponse.fromJson(
            "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
                + "\"version\":{\"snapshot\":44,\"start\":40,\"end\":44},"
                + "\"fragments\":[{\"segment\":\"blip:b+pending\"}]}");

    Assert.assertNull(response.getFragments().getEntries().get(0).getRawSnapshot());
  }

  @Test
  public void decodeFragmentsServletResponseRequiresVersionObject() {
    try {
      SidecarFragmentsResponse.fromJson(
          "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\"}");
      Assert.fail("Expected missing version to be rejected");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("Expected object"));
    }
  }

  @Test
  public void decodeFragmentsServletResponseRejectsMalformedRangeEntry() {
    try {
      SidecarFragmentsResponse.fromJson(
          "{\"status\":\"ok\",\"waveRef\":\"example.com/w+abc/~/conv+root\","
              + "\"version\":{\"snapshot\":44,\"start\":40,\"end\":44},"
              + "\"ranges\":[\"not-an-object\"]}");
      Assert.fail("Expected malformed range entry to be rejected");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("Expected object"));
    }
  }
}
