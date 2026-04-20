package org.waveprotocol.box.j2cl.sandbox;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class SandboxHostPageAssetPathTest {
  @Test
  public void hostPageUsesProfileRelativeAssetUrls() throws Exception {
    String html =
        new String(
            Files.readAllBytes(Paths.get("src/main/webapp/index.html")),
            StandardCharsets.UTF_8);

    Assert.assertTrue(html.contains("href=\"./assets/sidecar.css\""));
    Assert.assertTrue(html.contains("src=\"./sidecar/j2cl-sidecar.js\""));
    Assert.assertFalse(html.contains("href=\"/j2cl-search/assets/sidecar.css\""));
    Assert.assertFalse(html.contains("src=\"/j2cl-search/sidecar/j2cl-sidecar.js\""));
  }
}
