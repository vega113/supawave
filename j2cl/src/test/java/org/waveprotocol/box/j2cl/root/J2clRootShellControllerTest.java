package org.waveprotocol.box.j2cl.root;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.toolbar.J2clDailyToolbarAction;
import org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceController;
import org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceModel;

@J2clTestInput(J2clRootShellControllerTest.class)
public class J2clRootShellControllerTest {
  @Test
  public void editStateForWriteSessionRequiresFullWriteSession() {
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

    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.BOLD));
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
