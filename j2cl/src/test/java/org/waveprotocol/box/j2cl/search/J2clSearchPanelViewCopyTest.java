package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clSearchPanelViewCopyTest.class)
public class J2clSearchPanelViewCopyTest {
  @Test
  public void sidecarPresentationKeepsVerificationCopy() {
    J2clSearchPanelView.Copy copy =
        J2clSearchPanelView.copyFor(J2clSearchPanelView.ShellPresentation.SIDE_CAR);

    Assert.assertEquals("Isolated J2CL search slice", copy.eyebrow);
    Assert.assertEquals("First real search/results vertical slice", copy.title);
  }

  @Test
  public void rootShellPresentationUsesShellAwareCopy() {
    J2clSearchPanelView.Copy copy =
        J2clSearchPanelView.copyFor(J2clSearchPanelView.ShellPresentation.ROOT_SHELL);

    Assert.assertEquals("J2CL root shell", copy.eyebrow);
    Assert.assertEquals("Hosted workflow", copy.title);
    Assert.assertTrue(copy.detail.contains("root shell"));
  }
}
