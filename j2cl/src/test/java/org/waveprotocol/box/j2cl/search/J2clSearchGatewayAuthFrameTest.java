package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Method;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;
import org.waveprotocol.box.j2cl.transport.SidecarTransportCodec;

/**
 * Issue #933 regression coverage for the first outbound J2CL sidecar frames.
 * Sidecar auth is now established entirely by the WebSocket upgrade handshake
 * (the server resolves loggedInUser from the HttpSession before any frames are
 * exchanged), so the selected-wave and submit sockets must begin with
 * ProtocolOpenRequest / ProtocolSubmitRequest instead of an auth envelope.
 *
 * <p>This is a static-contract regression, not a behavioural test of the
 * gateways — it cannot catch a future regression that hand-inlines a cookie
 * read in {@link J2clSearchGateway} or the sandbox sidecar. For that defence,
 * see the local-browser verification recorded under
 * {@code journal/local-verification/2026-04-23-issue-933-sidecar-ws-auth.md},
 * which inspects the live {@code /socket} frames for the absence of a
 * {@code ProtocolAuthenticate} payload.
 */
@J2clTestInput(J2clSearchGatewayAuthFrameTest.class)
public class J2clSearchGatewayAuthFrameTest {
  @Test
  public void selectedWaveSocketStartsWithProtocolOpenRequest() {
    String frame =
        J2clSearchGateway.buildSelectedWaveOpenFrame(
            new SidecarSessionBootstrap("rose@example.com", "socket.example.test"),
            "example.com/w+abc");

    Assert.assertEquals("ProtocolOpenRequest", SidecarTransportCodec.decodeMessageType(frame));
    Assert.assertFalse(frame.contains("ProtocolAuthenticate"));
  }

  @Test
  public void submitSocketStartsWithProtocolSubmitRequest() {
    String frame =
        J2clSearchGateway.buildSubmitFrame(
            new SidecarSubmitRequest(
                "example.com/w+abc/conv+root",
                "{\"ops\":[]}",
                "channel-7"));

    Assert.assertEquals("ProtocolSubmitRequest", SidecarTransportCodec.decodeMessageType(frame));
    Assert.assertFalse(frame.contains("ProtocolAuthenticate"));
  }

  @Test
  public void websocketCookieHostMustMatchCurrentPageHost() {
    Assert.assertTrue(
        SidecarSessionBootstrap.usesCompatibleCookieHost(
            "wave.example.com", "wave.example.com:7443"));
    Assert.assertTrue(
        SidecarSessionBootstrap.usesCompatibleCookieHost(
            "wave.example.com", "wave.example.com"));
    Assert.assertFalse(
        SidecarSessionBootstrap.usesCompatibleCookieHost(
            "wave.example.com", "socket.example.com:7443"));
  }

  @Test
  public void transportCodecDoesNotExposeAuthenticateEnvelopeHelper() {
    for (Method method : SidecarTransportCodec.class.getDeclaredMethods()) {
      Assert.assertFalse(
          "SidecarTransportCodec must not expose encodeAuthenticateEnvelope; "
              + "sidecar auth is handled by the WebSocket upgrade handshake (#933)",
          "encodeAuthenticateEnvelope".equals(method.getName()));
    }
  }
}
