package org.waveprotocol.box.j2cl.toolbar;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;

@J2clTestInput(J2clToolbarSurfaceControllerTest.class)
public class J2clToolbarSurfaceControllerTest {
  @Test
  public void viewToolbarIncludesDailyNavigationAndFolderActions() {
    FakeView view = new FakeView();
    J2clToolbarSurfaceController controller = new J2clToolbarSurfaceController(view, action -> { });

    controller.start();
    controller.onSelectedWaveStateChanged(
        new J2clToolbarSurfaceController.SelectedWaveState(true, false, false, true, false));

    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.RECENT));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.NEXT_UNREAD));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.PREVIOUS));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.NEXT));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.LAST));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ARCHIVE));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.PIN));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.HISTORY));
  }

  @Test
  public void mentionNavigationIsDisabledUntilMentionOrderExists() {
    FakeView view = new FakeView();
    J2clToolbarSurfaceController controller = new J2clToolbarSurfaceController(view, action -> { });

    controller.start();
    controller.onSelectedWaveStateChanged(
        new J2clToolbarSurfaceController.SelectedWaveState(true, false, false, false, false));

    Assert.assertTrue(view.model.action(J2clDailyToolbarAction.PREVIOUS_MENTION).isDisabled());
    Assert.assertTrue(view.model.action(J2clDailyToolbarAction.NEXT_MENTION).isDisabled());
  }

  @Test
  public void actionDispatchUsesCommandInterfaceWithoutLeavingStuckBusyState() {
    FakeView view = new FakeView();
    List<J2clDailyToolbarAction> dispatched = new ArrayList<J2clDailyToolbarAction>();
    J2clToolbarSurfaceController controller =
        new J2clToolbarSurfaceController(view, dispatched::add);

    controller.start();
    controller.onSelectedWaveStateChanged(
        new J2clToolbarSurfaceController.SelectedWaveState(true, false, false, true, false));
    controller.onActionRequested(J2clDailyToolbarAction.ARCHIVE.id());

    Assert.assertEquals(1, dispatched.size());
    Assert.assertEquals(J2clDailyToolbarAction.ARCHIVE, dispatched.get(0));
    Assert.assertFalse(view.model.action(J2clDailyToolbarAction.ARCHIVE).isBusy());
  }

  @Test
  public void archivePinActionsReflectKnownFolderStateAndAreOmittedWhenUnknown() {
    FakeView view = new FakeView();
    J2clToolbarSurfaceController controller = new J2clToolbarSurfaceController(view, action -> { });

    controller.start();
    controller.onSelectedWaveStateChanged(
        new J2clToolbarSurfaceController.SelectedWaveState(true, true, true, true, false));

    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.INBOX));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.ARCHIVE));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.UNPIN));
    Assert.assertTrue(view.model.action(J2clDailyToolbarAction.UNPIN).isPressed());

    controller.onSelectedWaveStateChanged(
        new J2clToolbarSurfaceController.SelectedWaveState(true, false, false, true, false, false));

    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.ARCHIVE));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.PIN));
  }

  @Test
  public void editToolbarIncludesDailyFormattingControlsOnly() {
    FakeView view = new FakeView();
    J2clToolbarSurfaceController controller = new J2clToolbarSurfaceController(view, action -> { });

    controller.start();
    controller.onEditStateChanged(new J2clToolbarSurfaceController.EditState(true, "bold", "rtl"));

    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.BOLD));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ITALIC));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.UNDERLINE));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.STRIKETHROUGH));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.HEADING));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.UNORDERED_LIST));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ORDERED_LIST));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ALIGN_LEFT));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ALIGN_CENTER));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ALIGN_RIGHT));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.RTL));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.LINK));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.UNLINK));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.CLEAR_FORMATTING));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_CANCEL));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_PASTE_IMAGE));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_SIZE_SMALL));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_SIZE_MEDIUM));
    Assert.assertTrue(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_SIZE_LARGE));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.HEADING_DEFAULT));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.HEADING_H1));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.HEADING_H2));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.HEADING_H3));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.HEADING_H4));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.INDENT));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.OUTDENT));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_CAPTION));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_OPEN));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_DOWNLOAD));
    Assert.assertTrue(view.model.action(J2clDailyToolbarAction.BOLD).isPressed());
    Assert.assertTrue(view.model.action(J2clDailyToolbarAction.RTL).isPressed());
    Assert.assertFalse(view.model.hasActionId("attachment-upload"));
    Assert.assertFalse(view.model.hasActionId("task-overlay"));
    Assert.assertFalse(view.model.hasActionId("reaction-picker"));
    Assert.assertFalse(view.model.hasActionId("mention-autocomplete"));
  }

  @Test
  public void editActionWithoutWriteSessionSurfacesExplicitError() {
    FakeView view = new FakeView();
    List<J2clDailyToolbarAction> dispatched = new ArrayList<J2clDailyToolbarAction>();
    J2clToolbarSurfaceController controller =
        new J2clToolbarSurfaceController(view, dispatched::add);

    controller.start();
    controller.onEditStateChanged(new J2clToolbarSurfaceController.EditState(true));
    controller.onActionRequested(J2clDailyToolbarAction.BOLD.id());

    Assert.assertTrue(dispatched.isEmpty());
    Assert.assertEquals(
        "Open a current wave before using edit toolbar actions.",
        view.model.action(J2clDailyToolbarAction.BOLD).getErrorText());

    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan", 1L, "HASH", "b+root"));

    Assert.assertEquals("", view.model.action(J2clDailyToolbarAction.BOLD).getErrorText());
  }

  @Test
  public void editActionWithWriteBasisDispatchesWithoutLegacyGwtClasses() {
    FakeView view = new FakeView();
    List<J2clDailyToolbarAction> dispatched = new ArrayList<J2clDailyToolbarAction>();
    J2clToolbarSurfaceController controller =
        new J2clToolbarSurfaceController(view, dispatched::add);

    controller.start();
    controller.onWriteSessionChanged(
        new J2clSidecarWriteSession("example.com/w+1", "chan", 1L, "HASH", "b+root"));
    controller.onEditStateChanged(new J2clToolbarSurfaceController.EditState(true));
    controller.onActionRequested(J2clDailyToolbarAction.BOLD.id());

    Assert.assertEquals(1, dispatched.size());
    Assert.assertEquals(J2clDailyToolbarAction.BOLD, dispatched.get(0));
  }

  @Test
  public void editStateNotEditableProducesNoEditActions() {
    FakeView view = new FakeView();
    J2clToolbarSurfaceController controller = new J2clToolbarSurfaceController(view, action -> { });

    controller.start();
    controller.onEditStateChanged(new J2clToolbarSurfaceController.EditState(false));

    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.BOLD));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.ITALIC));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.UNDERLINE));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_INSERT));
    Assert.assertFalse(view.model.hasAction(J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE));
  }

  @Test
  public void unavailableActionSurfacesExplicitErrorText() {
    FakeView view = new FakeView();
    J2clToolbarSurfaceController controller = new J2clToolbarSurfaceController(view, action -> { });

    controller.start();
    controller.onSelectedWaveStateChanged(
        new J2clToolbarSurfaceController.SelectedWaveState(true, false, false, true, false));
    controller.onActionUnavailable(J2clDailyToolbarAction.HISTORY, "Not wired yet.");

    Assert.assertEquals("Not wired yet.", view.model.action(J2clDailyToolbarAction.HISTORY).getErrorText());
  }

  private static final class FakeView implements J2clToolbarSurfaceController.View {
    private J2clToolbarSurfaceModel model;

    @Override
    public void bind(J2clToolbarSurfaceController.Listener listener) {
    }

    @Override
    public void render(J2clToolbarSurfaceModel model) {
      this.model = model;
    }
  }
}
