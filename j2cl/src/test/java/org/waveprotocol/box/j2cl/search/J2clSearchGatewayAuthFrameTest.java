package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Method;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.transport.SidecarTransportCodec;

/**
 * Issue #933 name-guard: the J2CL sidecar transport codec must not expose a
 * ProtocolAuthenticate envelope helper again by accident. Sidecar auth is now
 * established entirely by the WebSocket upgrade handshake (the server resolves
 * loggedInUser from the HttpSession before any frames are exchanged).
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
  public void transportCodecDoesNotExposeAuthenticateEnvelopeHelper() {
    for (Method method : SidecarTransportCodec.class.getDeclaredMethods()) {
      Assert.assertFalse(
          "SidecarTransportCodec must not expose encodeAuthenticateEnvelope; "
              + "sidecar auth is handled by the WebSocket upgrade handshake (#933)",
          "encodeAuthenticateEnvelope".equals(method.getName()));
    }
  }
}
