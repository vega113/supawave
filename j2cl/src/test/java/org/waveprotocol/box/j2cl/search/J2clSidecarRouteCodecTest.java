package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clSidecarRouteCodecTest.class)
public class J2clSidecarRouteCodecTest {
  @Test
  public void blankOrMissingQueryFallsBackToDefault() {
    Assert.assertEquals(
        J2clSearchResultProjector.DEFAULT_QUERY,
        J2clSidecarRouteCodec.parse("").getQuery());
    Assert.assertEquals(
        J2clSearchResultProjector.DEFAULT_QUERY,
        J2clSidecarRouteCodec.parse("?q=").getQuery());
    Assert.assertEquals(
        J2clSearchResultProjector.DEFAULT_QUERY,
        J2clSidecarRouteCodec.parse("?q=%").getQuery());
  }

  @Test
  public void queryOnlyRouteRoundTripsToCanonicalUrl() {
    J2clSidecarRouteState state = J2clSidecarRouteCodec.parse("?q=with%3A%40");

    Assert.assertEquals("with:@", state.getQuery());
    Assert.assertNull(state.getSelectedWaveId());
    Assert.assertEquals(
        "?q=with%3A%40",
        J2clSidecarRouteCodec.toUrl(state));
  }

  @Test
  public void queryAndSelectedWaveRouteRoundTripsToCanonicalUrl() {
    J2clSidecarRouteState state =
        J2clSidecarRouteCodec.parse("?wave=example.com%2Fw%2Babc123&q=with%3A%40");

    Assert.assertEquals("with:@", state.getQuery());
    Assert.assertEquals("example.com/w+abc123", state.getSelectedWaveId());
    Assert.assertEquals(
        "?q=with%3A%40&wave=example.com%2Fw%2Babc123",
        J2clSidecarRouteCodec.toUrl(state));
  }

  @Test
  public void malformedOrIncompleteWaveParameterIsDroppedSafely() {
    Assert.assertNull(J2clSidecarRouteCodec.parse("?wave=").getSelectedWaveId());
    Assert.assertNull(J2clSidecarRouteCodec.parse("?wave=not-a-wave-id").getSelectedWaveId());
    Assert.assertNull(J2clSidecarRouteCodec.parse("?wave=bad%20value").getSelectedWaveId());
  }

  @Test
  public void waveParameterPreservesLiteralPlusInWaveId() {
    Assert.assertEquals(
        "example.com/w+abc123",
        J2clSidecarRouteCodec.parse("?wave=example.com/w+abc123").getSelectedWaveId());
  }

  @Test
  public void queryDecodingTreatsPlusAsSpace() {
    Assert.assertEquals(
        "mentions:me unread:true",
        J2clSidecarRouteCodec.parse("?q=mentions%3Ame+unread%3Atrue").getQuery());
  }
}
