package org.waveprotocol.box.j2cl.toolbar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;

public final class J2clToolbarSurfaceController {
  public interface View {
    void bind(Listener listener);

    void render(J2clToolbarSurfaceModel model);
  }

  public interface Listener {
    void onActionRequested(String actionId);
  }

  @FunctionalInterface
  public interface ActionDispatcher {
    void dispatch(J2clDailyToolbarAction action);
  }

  public static final class SelectedWaveState {
    private final boolean selectedWavePresent;
    private final boolean archived;
    private final boolean pinned;
    private final boolean historyVisible;
    private final boolean mentionOrderAvailable;
    private final boolean folderStateAvailable;

    public SelectedWaveState(
        boolean selectedWavePresent,
        boolean archived,
        boolean pinned,
        boolean historyVisible,
        boolean mentionOrderAvailable) {
      this(selectedWavePresent, archived, pinned, historyVisible, mentionOrderAvailable, true);
    }

    public SelectedWaveState(
        boolean selectedWavePresent,
        boolean archived,
        boolean pinned,
        boolean historyVisible,
        boolean mentionOrderAvailable,
        boolean folderStateAvailable) {
      this.selectedWavePresent = selectedWavePresent;
      this.archived = archived;
      this.pinned = pinned;
      this.historyVisible = historyVisible;
      this.mentionOrderAvailable = mentionOrderAvailable;
      this.folderStateAvailable = folderStateAvailable;
    }
  }

  public static final class EditState {
    private final boolean editable;
    private final Set<String> pressedActionIds = new HashSet<String>();

    public EditState(boolean editable, String... pressedActionIds) {
      this.editable = editable;
      if (pressedActionIds != null) {
        for (String actionId : pressedActionIds) {
          this.pressedActionIds.add(actionId);
        }
      }
    }
  }

  private final View view;
  private final ActionDispatcher dispatcher;
  private SelectedWaveState selectedWaveState =
      new SelectedWaveState(false, false, false, false, false, false);
  private EditState editState = new EditState(false);
  private J2clSidecarWriteSession writeSession;
  private J2clDailyToolbarAction errorAction;
  private String errorText = "";
  private boolean started;
  // F-2 slice 5 (#1055, A.3): suppress legacy view actions in callers
  // where <wavy-wave-nav-row> already provides the same affordances. The
  // sidecar (legacy) presentation keeps the default `true`.
  private boolean viewActionsEnabled = true;

  public J2clToolbarSurfaceController(View view, ActionDispatcher dispatcher) {
    this.view = view;
    this.dispatcher = dispatcher;
  }

  /**
   * F-2 slice 5 (#1055, A.3) — opt out of view actions when the caller's
   * presentation already mounts the canonical view-nav chrome (i.e. the
   * J2CL root shell mounts {@code <wavy-wave-nav-row>}). Defaults to
   * {@code true} so the sidecar presentation is unchanged.
   */
  public void setViewActionsEnabled(boolean enabled) {
    if (this.viewActionsEnabled == enabled) {
      return;
    }
    this.viewActionsEnabled = enabled;
    if (started) {
      render();
    }
  }

  public void start() {
    if (started) {
      return;
    }
    started = true;
    view.bind(this::onActionRequested);
    render();
  }

  public void onSelectedWaveStateChanged(SelectedWaveState state) {
    selectedWaveState =
        state == null ? new SelectedWaveState(false, false, false, false, false, false) : state;
    render();
  }

  public void onEditStateChanged(EditState state) {
    editState = state == null ? new EditState(false) : state;
    render();
  }

  public void onWriteSessionChanged(J2clSidecarWriteSession writeSession) {
    this.writeSession = writeSession;
    if (writeSession != null && errorAction != null && errorAction.isEditAction()) {
      errorAction = null;
      errorText = "";
    }
    render();
  }

  public void onActionRequested(String actionId) {
    J2clDailyToolbarAction action = J2clDailyToolbarAction.fromId(actionId);
    if (action == null) {
      return;
    }
    errorAction = null;
    errorText = "";
    if (action.isEditAction() && writeSession == null) {
      errorAction = action;
      errorText = "Open a current wave before using edit toolbar actions.";
      render();
      return;
    }
    if (dispatcher != null) {
      dispatcher.dispatch(action);
    }
    render();
  }

  public void onActionUnavailable(J2clDailyToolbarAction action, String message) {
    if (action == null) {
      return;
    }
    errorAction = action;
    errorText = message == null || message.isEmpty() ? "Toolbar action is not available." : message;
    render();
  }

  private void render() {
    if (!started) {
      return;
    }
    List<J2clToolbarSurfaceModel.ActionModel> actions =
        new ArrayList<J2clToolbarSurfaceModel.ActionModel>();
    if (viewActionsEnabled) {
      addViewActions(actions);
    }
    // F-2 follow-up (#1060): only emit the edit-formatting toolbar
    // (Bold / Italic / Underline / ... / attachment buttons) while an
    // edit session is active. Previously the controller emitted the
    // full edit-action set on every render with the buttons disabled,
    // which painted as a permanent editor-toolbar wall on the right of
    // the read surface even when no compose was open. The view's
    // host.hidden = actions.isEmpty() guard then resolves the toolbar
    // host to display:none, and the wavy-thread-collapse `:empty`
    // collapse rule on the parent compose host removes the layout
    // gap entirely until an edit session is opened.
    if (editState.editable) {
      addEditActions(actions);
    }
    view.render(new J2clToolbarSurfaceModel(actions));
  }

  private void addViewActions(List<J2clToolbarSurfaceModel.ActionModel> actions) {
    add(actions, J2clDailyToolbarAction.RECENT, !selectedWaveState.selectedWavePresent, false);
    add(actions, J2clDailyToolbarAction.NEXT_UNREAD, !selectedWaveState.selectedWavePresent, false);
    add(actions, J2clDailyToolbarAction.PREVIOUS, !selectedWaveState.selectedWavePresent, false);
    add(actions, J2clDailyToolbarAction.NEXT, !selectedWaveState.selectedWavePresent, false);
    add(actions, J2clDailyToolbarAction.LAST, !selectedWaveState.selectedWavePresent, false);
    add(
        actions,
        J2clDailyToolbarAction.PREVIOUS_MENTION,
        !selectedWaveState.selectedWavePresent || !selectedWaveState.mentionOrderAvailable,
        false);
    add(
        actions,
        J2clDailyToolbarAction.NEXT_MENTION,
        !selectedWaveState.selectedWavePresent || !selectedWaveState.mentionOrderAvailable,
        false);
    if (selectedWaveState.folderStateAvailable) {
      add(
          actions,
          selectedWaveState.archived ? J2clDailyToolbarAction.INBOX : J2clDailyToolbarAction.ARCHIVE,
          !selectedWaveState.selectedWavePresent,
          false);
      add(
          actions,
          selectedWaveState.pinned ? J2clDailyToolbarAction.UNPIN : J2clDailyToolbarAction.PIN,
          !selectedWaveState.selectedWavePresent,
          selectedWaveState.pinned);
    }
    add(
        actions,
        J2clDailyToolbarAction.HISTORY,
        !selectedWaveState.selectedWavePresent || !selectedWaveState.historyVisible,
        false);
  }

  private void addEditActions(List<J2clToolbarSurfaceModel.ActionModel> actions) {
    addEdit(actions, J2clDailyToolbarAction.BOLD);
    addEdit(actions, J2clDailyToolbarAction.ITALIC);
    addEdit(actions, J2clDailyToolbarAction.UNDERLINE);
    addEdit(actions, J2clDailyToolbarAction.STRIKETHROUGH);
    addEdit(actions, J2clDailyToolbarAction.FONT_FAMILY);
    addEdit(actions, J2clDailyToolbarAction.FONT_SIZE);
    addEdit(actions, J2clDailyToolbarAction.HEADING);
    addEdit(actions, J2clDailyToolbarAction.UNORDERED_LIST);
    addEdit(actions, J2clDailyToolbarAction.ORDERED_LIST);
    addEdit(actions, J2clDailyToolbarAction.ALIGN_LEFT);
    addEdit(actions, J2clDailyToolbarAction.ALIGN_CENTER);
    addEdit(actions, J2clDailyToolbarAction.ALIGN_RIGHT);
    addEdit(actions, J2clDailyToolbarAction.RTL);
    addEdit(actions, J2clDailyToolbarAction.LINK);
    addEdit(actions, J2clDailyToolbarAction.UNLINK);
    addEdit(actions, J2clDailyToolbarAction.CLEAR_FORMATTING);
    addEdit(actions, J2clDailyToolbarAction.ATTACHMENT_INSERT);
    addEdit(actions, J2clDailyToolbarAction.ATTACHMENT_UPLOAD_QUEUE);
    addEdit(actions, J2clDailyToolbarAction.ATTACHMENT_CANCEL);
    addEdit(actions, J2clDailyToolbarAction.ATTACHMENT_PASTE_IMAGE);
    addEdit(actions, J2clDailyToolbarAction.ATTACHMENT_SIZE_SMALL);
    addEdit(actions, J2clDailyToolbarAction.ATTACHMENT_SIZE_MEDIUM);
    addEdit(actions, J2clDailyToolbarAction.ATTACHMENT_SIZE_LARGE);
  }

  private void addEdit(
      List<J2clToolbarSurfaceModel.ActionModel> actions, J2clDailyToolbarAction action) {
    add(actions, action, !editState.editable, editState.pressedActionIds.contains(action.id()));
  }

  private void add(
      List<J2clToolbarSurfaceModel.ActionModel> actions,
      J2clDailyToolbarAction action,
      boolean disabled,
      boolean pressed) {
    actions.add(
        new J2clToolbarSurfaceModel.ActionModel(
            action,
            disabled,
            pressed,
            errorAction == action ? errorText : ""));
  }
}
