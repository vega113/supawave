package org.waveprotocol.box.j2cl.root;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;
import org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceController;
import org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceModel;

@J2clTestInput(J2clRootShellControllerTest.class)
public class J2clRootShellControllerTest {
  @Test
  public void editStateForWriteSessionAlwaysDisablesLegacyToolbar() {
    FakeToolbarView view = new FakeToolbarView();
    J2clToolbarSurfaceController toolbarController =
        new J2clToolbarSurfaceController(view, action -> {});

    toolbarController.setViewActionsEnabled(false);
    toolbarController.start();
    toolbarController.onEditStateChanged(J2clRootShellController.editStateForWriteSession(null));

    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.BOLD));

    toolbarController.onEditStateChanged(
        J2clRootShellController.editStateForWriteSession(
            new J2clSidecarWriteSession("example.com/w+1", "chan-1", 44L, "ABCD", "b+root")));

    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.BOLD));
  }

  @Test
  public void normalizeParticipantValuesTrimsAndSkipsBlankEntries() {
    Assert.assertEquals(
        Arrays.asList("Friend@Example.COM", "team@example.com"),
        J2clRootShellController.normalizeParticipantValues(
            Arrays.asList(" Friend@Example.COM ", "", null, 123, "team@example.com")));
  }

  @Test
  public void newWaveTriggerFromSourceMapsShortcutAndDefaultsToButton() {
    Assert.assertEquals(
        "shortcut", J2clRootShellController.newWaveTriggerFromSource("keyboard-shortcut"));
    Assert.assertEquals(
        "shortcut", J2clRootShellController.newWaveTriggerFromSource("Keyboard-Shortcut"));
    Assert.assertEquals("menu", J2clRootShellController.newWaveTriggerFromSource("menu"));
    Assert.assertEquals("button", J2clRootShellController.newWaveTriggerFromSource("button"));
    Assert.assertEquals("button", J2clRootShellController.newWaveTriggerFromSource("unknown"));
    Assert.assertEquals("button", J2clRootShellController.newWaveTriggerFromSource(null));
  }

  @Test
  public void normalizeWaveHeaderEventValuesTrimsSourceWaveAndLockState() {
    Assert.assertEquals(
        "example.com/w+1", J2clRootShellController.normalizeSourceWaveId(" example.com/w+1 "));
    Assert.assertEquals("", J2clRootShellController.normalizeSourceWaveId(null));
    Assert.assertEquals("root", J2clRootShellController.normalizeLockStateValue(" root "));
    Assert.assertEquals("all", J2clRootShellController.normalizeLockStateValue("all"));
    Assert.assertEquals("unlocked", J2clRootShellController.normalizeLockStateValue("bogus"));
  }

  @Test
  public void previewRouteHostSkipsLiveSidecarStartup() {
    Assert.assertTrue(J2clRootShellController.isReadSurfacePreviewHost(true, false));
    Assert.assertTrue(J2clRootShellController.isReadSurfacePreviewHost(false, true));
    Assert.assertFalse(J2clRootShellController.isReadSurfacePreviewHost(false, false));
  }

  private static final class FakeToolbarView implements J2clToolbarSurfaceController.View {
    private J2clToolbarSurfaceModel model;

    @Override
    public void bind(J2clToolbarSurfaceController.Listener listener) {}

    @Override
    public void render(J2clToolbarSurfaceModel model) {
      this.model = model;
    }
  }
}
