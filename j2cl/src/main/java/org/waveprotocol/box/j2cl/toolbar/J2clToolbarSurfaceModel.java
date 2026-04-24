package org.waveprotocol.box.j2cl.toolbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class J2clToolbarSurfaceModel {
  public static final class ActionModel {
    private final J2clDailyToolbarAction action;
    private final boolean disabled;
    private final boolean pressed;
    private final String errorText;

    public ActionModel(
        J2clDailyToolbarAction action,
        boolean disabled,
        boolean pressed,
        String errorText) {
      this.action = action;
      this.disabled = disabled;
      this.pressed = pressed;
      this.errorText = errorText == null ? "" : errorText;
    }

    public J2clDailyToolbarAction getAction() {
      return action;
    }

    public String getActionId() {
      return action.id();
    }

    public String getLabel() {
      return action.label();
    }

    public String getGroupLabel() {
      return action.groupLabel();
    }

    public boolean isDisabled() {
      return disabled;
    }

    public boolean isPressed() {
      return pressed;
    }

    public boolean isBusy() {
      return false;
    }

    public boolean isToggle() {
      return action.isToggle();
    }

    public String getErrorText() {
      return errorText;
    }
  }

  private final List<ActionModel> actions;

  public J2clToolbarSurfaceModel(List<ActionModel> actions) {
    this.actions = Collections.unmodifiableList(new ArrayList<ActionModel>(actions));
  }

  public List<ActionModel> getActions() {
    return actions;
  }

  public boolean hasAction(J2clDailyToolbarAction action) {
    return action(action) != null;
  }

  public boolean hasActionId(String actionId) {
    for (ActionModel model : actions) {
      if (model.getActionId().equals(actionId)) {
        return true;
      }
    }
    return false;
  }

  public ActionModel action(J2clDailyToolbarAction action) {
    for (ActionModel model : actions) {
      if (model.getAction() == action) {
        return model;
      }
    }
    return null;
  }
}
