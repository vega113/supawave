package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Collections;
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
        J2clSelectedWaveModel.loading("example.com/w+1", null, 1, null);

    Assert.assertEquals("Reusing the current session after a disconnect.", model.getDetailText());
  }

  @Test
  public void loadingSelectionDoesNotCarryReadStateAcrossWaveSwitch() {
    J2clSelectedWaveModel previous =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+old",
            "Old",
            "",
            "4 unread.",
            "",
            "",
            0,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            null,
            4,
            false,
            true,
            false);

    J2clSelectedWaveModel model =
        J2clSelectedWaveModel.loading("example.com/w+new", null, 0, previous);

    Assert.assertEquals(J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT, model.getUnreadCount());
    Assert.assertFalse(model.isRead());
    Assert.assertFalse(model.isReadStateKnown());
  }

  @Test
  public void errorSelectionDoesNotCarryReadStateAcrossWaveSwitch() {
    J2clSelectedWaveModel previous =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+old",
            "Old",
            "",
            "Read.",
            "",
            "",
            0,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            null,
            0,
            true,
            true,
            false);

    J2clSelectedWaveModel model =
        J2clSelectedWaveModel.error(
            "example.com/w+new",
            null,
            "Failed",
            "detail",
            previous);

    Assert.assertEquals(J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT, model.getUnreadCount());
    Assert.assertFalse(model.isRead());
    Assert.assertFalse(model.isReadStateKnown());
  }
}
