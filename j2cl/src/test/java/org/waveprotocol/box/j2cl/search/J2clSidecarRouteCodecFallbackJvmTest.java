package org.waveprotocol.box.j2cl.search;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.Assert;
import org.junit.Test;

public class J2clSidecarRouteCodecFallbackJvmTest {
  @Test
  public void fallbackEncoderEmitsSupplementaryCodePointsAsUtf8() throws Exception {
    Assert.assertEquals(
        "%F0%9F%9A%80",
        invokePrivateStringMethod("encodeUriComponentFallback", "\uD83D\uDE80"));
  }

  @Test
  public void fallbackDecoderAcceptsFourByteUtf8Sequences() throws Exception {
    Assert.assertEquals(
        "\uD83D\uDE80",
        invokePrivateStringMethod("decodeUriComponentFallback", "%F0%9F%9A%80"));
  }

  @Test
  public void fallbackEncoderMatchesEncodeUriComponentAllowList() throws Exception {
    Assert.assertEquals(
        "!*'()~",
        invokePrivateStringMethod("encodeUriComponentFallback", "!*'()~"));
    Assert.assertEquals(
        "!%20*'()",
        invokePrivateStringMethod("encodeUriComponentFallback", "! *'()"));
  }

  private static String invokePrivateStringMethod(String methodName, String value) throws Exception {
    Method method = J2clSidecarRouteCodec.class.getDeclaredMethod(methodName, String.class);
    method.setAccessible(true);
    try {
      return (String) method.invoke(null, value);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw e;
    }
  }
}
