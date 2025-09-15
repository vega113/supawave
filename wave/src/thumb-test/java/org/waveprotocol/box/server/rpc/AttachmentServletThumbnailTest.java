package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.*;

import java.io.InputStream;
import org.junit.Test;
import org.waveprotocol.box.server.persistence.AttachmentStore.AttachmentData;

import java.lang.reflect.Method;

public class AttachmentServletThumbnailTest {

  private static AttachmentData invokeGetThumb(AttachmentServlet s, String mime) throws Exception {
    Method m = AttachmentServlet.class.getDeclaredMethod("getThumbnailByContentType", String.class);
    m.setAccessible(true);
    return (AttachmentData) m.invoke(s, mime);
  }

  private static boolean hasPngSignature(InputStream in) throws Exception {
    byte[] sig = new byte[4];
    int n = in.read(sig);
    return n == 4 && (sig[0] & 0xFF) == 0x89 && sig[1] == 'P' && sig[2] == 'N' && sig[3] == 'G';
  }

  private static AttachmentServlet newServletWithDir(String dir) throws Exception {
    com.typesafe.config.Config cfg = com.typesafe.config.ConfigFactory.parseString(
        "core.thumbnail_patterns_directory=\"" + dir + "\"");
    java.lang.reflect.Constructor<AttachmentServlet> c = AttachmentServlet.class.getDeclaredConstructor(
        org.waveprotocol.box.server.attachment.AttachmentService.class,
        org.waveprotocol.box.server.waveserver.WaveletProvider.class,
        org.waveprotocol.box.server.authentication.SessionManager.class,
        com.typesafe.config.Config.class
    );
    c.setAccessible(true);
    return c.newInstance(null, null, null, cfg);
  }

  @Test
  public void fallbackUsedForNullBlankAndStrangeMime() throws Exception {
    AttachmentServlet servlet = newServletWithDir("/path/does/not/exist");

    for (String mime : new String[] { null, "", "   ", "weird\0type", "image/png??" }) {
      AttachmentData data = invokeGetThumb(servlet, mime);
      assertNotNull("AttachmentData should not be null for mime=" + mime, data);
      try (InputStream in = data.getInputStream()) {
        assertNotNull(in);
        assertTrue("Fallback should be PNG signature for mime=" + mime, hasPngSignature(in));
      }
      assertTrue("Size should be positive for mime=" + mime, data.getSize() > 0);
    }
  }

  @Test
  public void missingPatternsDirectoryYieldsFallback() throws Exception {
    AttachmentServlet servlet = newServletWithDir("/surely/not/a/real/dir");
    AttachmentData data = invokeGetThumb(servlet, "image/jpeg");
    assertNotNull(data);
    assertTrue(data.getSize() > 0);
    try (InputStream in = data.getInputStream()) {
      assertTrue(hasPngSignature(in));
    }
  }
}
