package org.waveprotocol.box.j2cl.sandbox;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteCodec;

@J2clTestInput(SandboxBuildSmokeTest.class)
public class SandboxBuildSmokeTest {
  @Test
  public void renderSummaryIncludesRequestedMode() {
    String mode = "search-sidecar";
    String summary = SandboxEntryPoint.renderSummary(mode);

    Assert.assertTrue(summary.contains(mode));
    Assert.assertEquals(
        "Profile sidecar writes isolated assets without changing the root runtime bootstrap.",
        SandboxEntryPoint.renderSummary("  "));
  }

  @Test
  public void rootShellModeIsRecognized() {
    Assert.assertTrue(SandboxEntryPoint.isRootShellMode("root-shell"));
    Assert.assertFalse(SandboxEntryPoint.isRootShellMode("search-sidecar"));
  }

  @Test
  public void evaluateSocketFrameReportsMalformedMessages() {
    SandboxEntryPoint.SocketFrameResult result =
        SandboxEntryPoint.evaluateSocketFrame(
            "{\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{");

    Assert.assertTrue(result.isError());
    Assert.assertTrue(result.getErrorDetail().contains("unexpected or invalid socket frame"));
    Assert.assertNull(result.getSummary());
  }

  @Test
  public void evaluateSocketFrameDecodesWaveletUpdates() {
    SandboxEntryPoint.SocketFrameResult result =
        SandboxEntryPoint.evaluateSocketFrame(
            "{\"sequenceNumber\":11,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
                + "\"1\":\"example.com!conv+root/example.com!conv+root\","
                + "\"2\":[{\"1\":\"delta\"}],\"6\":true,\"7\":\"chan-1\"}}");

    Assert.assertFalse(result.isError());
    Assert.assertEquals("ProtocolWaveletUpdate", result.getMessageType());
    Assert.assertEquals(
        "example.com!conv+root/example.com!conv+root", result.getSummary().getWaveletName());
  }

  @Test
  public void dummyChannelUpdateIsNotTreatedAsRealWaveletProof() {
    SandboxEntryPoint.SocketFrameResult result =
        SandboxEntryPoint.evaluateSocketFrame(
            "{\"sequenceNumber\":3,\"messageType\":\"ProtocolWaveletUpdate\",\"message\":{"
                + "\"1\":\"local.net/w+abc/~/dummy+root\",\"2\":[],\"6\":true,\"7\":\"ch1\"}}");

    Assert.assertFalse(result.isError());
    Assert.assertTrue(SandboxEntryPoint.isChannelEstablishmentUpdate(result.getSummary()));
  }

  @Test
  public void buildWebSocketUrlUsesPresentedAddress() {
    Assert.assertEquals(
        "wss://socket.example.test:7443/socket",
        SandboxEntryPoint.buildWebSocketUrl("https:", "socket.example.test:7443"));
    Assert.assertEquals(
        "ws://socket.example.test/socket",
        SandboxEntryPoint.buildWebSocketUrl("http:", "socket.example.test"));
  }

  @Test
  public void malformedQueryFallsBackToDefaultSearch() {
    Assert.assertEquals("in:inbox", J2clSidecarRouteCodec.parse("?q=%").getQuery());
  }

  @Test
  public void encodedQueryDecodesPercentEscapes() {
    Assert.assertEquals("with:@", J2clSidecarRouteCodec.parse("?q=with%3A%40").getQuery());
  }

  @Test
  public void encodedQueryTreatsPlusAsSpace() {
    Assert.assertEquals(
        "mentions:me unread:true",
        J2clSidecarRouteCodec.parse("?q=mentions%3Ame+unread%3Atrue").getQuery());
  }

  @Test
  public void malformedCookieValueReturnsNull() {
    Assert.assertNull(
        SandboxEntryPoint.readCookieFromHeader("JSESSIONID=%; theme=dark", "JSESSIONID"));
  }
}
