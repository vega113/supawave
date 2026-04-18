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
}
