package org.waveprotocol.box.j2cl.sandbox;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

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
  public void shouldHandleAsyncCallbackRejectsStaleGenerations() {
    Assert.assertTrue(SandboxEntryPoint.shouldHandleAsyncCallback(4, 4));
    Assert.assertFalse(SandboxEntryPoint.shouldHandleAsyncCallback(3, 4));
  }

  @Test
  public void shouldHandleSocketCallbackRejectsStaleSockets() {
    Assert.assertTrue(SandboxEntryPoint.shouldHandleSocketCallback(4, 4, true));
    Assert.assertFalse(SandboxEntryPoint.shouldHandleSocketCallback(4, 4, false));
    Assert.assertFalse(SandboxEntryPoint.shouldHandleSocketCallback(3, 4, true));
  }
}
