package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clSelectedWaveModelCopyTest.class)
public class J2clSelectedWaveModelCopyTest {
  @Test
  public void emptySelectionUsesRouteNeutralCopy() {
    J2clSelectedWaveModel model = J2clSelectedWaveModel.empty();

    Assert.assertTrue(
        model.getDetailText().contains("Copied URLs can restore the selected wave"));
  }

  @Test
  public void loadingSelectionUsesSessionNeutralDisconnectCopy() {
    J2clSelectedWaveModel model =
        J2clSelectedWaveModel.loading("example.com/w+1", null, 1);

    Assert.assertEquals("Reusing the current session after a disconnect.", model.getDetailText());
  }
}
