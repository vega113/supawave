package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.transport.SidecarViewportHints;

@J2clTestInput(J2clSelectedWaveViewServerFirstLogicTest.class)
public class J2clSelectedWaveViewServerFirstLogicTest {
  @Test
  public void serverSnapshotStaysVisibleWhileSameWaveIsStillLoading() {
    Assert.assertTrue(
        J2clSelectedWaveView.shouldPreserveServerSnapshot(
            "example.com/w+1",
            J2clSelectedWaveModel.loading(
                "example.com/w+1", null, 0, J2clSelectedWaveModel.empty()),
            false));
  }

  @Test
  public void serverSnapshotSurvivesInitialEmptyModelBeforeRouteSelection() {
    Assert.assertTrue(
        J2clSelectedWaveView.shouldPreserveServerSnapshot(
            "example.com/w+1",
            J2clSelectedWaveModel.empty(),
            false));
  }

  @Test
  public void firstLiveContentSwapsServerSnapshotOut() {
    J2clSelectedWaveModel liveModel =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+1",
            "Selected wave",
            "",
            "",
            "Live updates connected.",
            "",
            0,
            Collections.<String>emptyList(),
            Arrays.asList("Live content"),
            null,
            J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT,
            false,
            false,
            false);

    Assert.assertFalse(
        J2clSelectedWaveView.shouldPreserveServerSnapshot("example.com/w+1", liveModel, false));
  }

  @Test
  public void firstLiveReadBlipSwapsServerSnapshotOut() {
    J2clSelectedWaveModel liveModel =
        new J2clSelectedWaveModel(
            true,
            false,
            false,
            "example.com/w+1",
            "Selected wave",
            "",
            "",
            "Live updates connected.",
            "",
            0,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            Arrays.asList(new J2clReadBlip("b+root", "Live content")),
            null,
            J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT,
            false,
            false,
            false);

    Assert.assertFalse(
        J2clSelectedWaveView.shouldPreserveServerSnapshot("example.com/w+1", liveModel, false));
  }

  @Test
  public void differentWaveSelectionDropsOldServerSnapshotImmediately() {
    Assert.assertFalse(
        J2clSelectedWaveView.shouldPreserveServerSnapshot(
            "example.com/w+1",
            J2clSelectedWaveModel.loading(
                "example.com/w+2", null, 0, J2clSelectedWaveModel.empty()),
            false));
  }

  @Test
  public void noWaveFallbackStaysVisibleUntilSelectionExists() {
    Assert.assertTrue(
        J2clSelectedWaveView.shouldPreserveServerSnapshot(
            "",
            J2clSelectedWaveModel.empty(),
            false));
  }

  @Test
  public void clearingSelectionDropsOldServerSnapshotImmediately() {
    Assert.assertFalse(
        J2clSelectedWaveView.shouldPreserveServerSnapshot(
            "example.com/w+1", J2clSelectedWaveModel.clearedSelection(), false));
  }

  @Test
  public void initialViewportHintsUseServerFirstBlipAnchor() {
    SidecarViewportHints hints =
        J2clSelectedWaveView.resolveInitialViewportHints(
            true, "example.com/w+1", "example.com/w+1", "b+root");

    Assert.assertEquals("b+root", hints.getStartBlipId());
    Assert.assertEquals("forward", hints.getDirection());
    Assert.assertNull(hints.getLimit());
  }

  @Test
  public void initialViewportHintsIgnoreMismatchedServerFirstWave() {
    SidecarViewportHints hints =
        J2clSelectedWaveView.resolveInitialViewportHints(
            true, "example.com/w+old", "example.com/w+new", "b+root");

    Assert.assertNull(hints.getStartBlipId());
    Assert.assertNull(hints.getDirection());
    Assert.assertEquals(Integer.valueOf(12), hints.getLimit());
  }

  @Test
  public void initialViewportHintsFallbackToDefaultLimitWhenServerFirstHasNoBlipAnchor() {
    SidecarViewportHints hints =
        J2clSelectedWaveView.resolveInitialViewportHints(
            true, "example.com/w+1", "example.com/w+1", null);

    Assert.assertNull(hints.getStartBlipId());
    Assert.assertNull(hints.getDirection());
    Assert.assertEquals(Integer.valueOf(12), hints.getLimit());
  }

  @Test
  public void configureContentListAddsScrollableRegionA11yAttributes() {
    Map<String, String> attributes = new LinkedHashMap<String, String>();

    J2clSelectedWaveView.configureContentListAttributes(attributes::put);

    Assert.assertEquals("region", attributes.get("role"));
    Assert.assertEquals("Selected wave content", attributes.get("aria-label"));
    Assert.assertEquals("0", attributes.get("tabindex"));
  }
}
