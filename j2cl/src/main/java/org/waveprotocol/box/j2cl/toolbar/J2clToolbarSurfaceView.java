package org.waveprotocol.box.j2cl.toolbar;

import elemental2.dom.DomGlobal;
import elemental2.dom.Event;
import elemental2.dom.HTMLElement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jsinterop.base.Js;

public final class J2clToolbarSurfaceView implements J2clToolbarSurfaceController.View {
  private final HTMLElement host;
  private final Map<String, HTMLElement> groupsByLabel = new HashMap<String, HTMLElement>();
  private final Map<String, HTMLElement> buttonsByAction = new HashMap<String, HTMLElement>();
  private final Map<String, HTMLElement> errorsByAction = new HashMap<String, HTMLElement>();
  private J2clToolbarSurfaceController.Listener listener;

  public J2clToolbarSurfaceView(HTMLElement host) {
    this.host = host;
    host.addEventListener("toolbar-action", this::onToolbarAction);
  }

  @Override
  public void bind(J2clToolbarSurfaceController.Listener listener) {
    this.listener = listener;
  }

  @Override
  public void render(J2clToolbarSurfaceModel model) {
    Set<String> seenGroups = new HashSet<String>();
    Set<String> seenActions = new HashSet<String>();
    String currentGroup = "";
    HTMLElement group = null;
    for (J2clToolbarSurfaceModel.ActionModel action : model.getActions()) {
      if (!action.getGroupLabel().equals(currentGroup)) {
        currentGroup = action.getGroupLabel();
        group = ensureGroup(currentGroup);
        seenGroups.add(currentGroup);
      }
      HTMLElement button = ensureButton(action.getActionId());
      setProperty(button, "action", action.getActionId());
      setProperty(button, "label", action.getLabel());
      setProperty(button, "toggle", action.isToggle());
      setProperty(button, "pressed", action.isPressed());
      setProperty(button, "disabled", action.isDisabled());
      if (button.parentElement != group) {
        group.appendChild(button);
      }
      seenActions.add(action.getActionId());
      if (!action.getErrorText().isEmpty()) {
        HTMLElement error = ensureError(action.getActionId());
        error.textContent = action.getErrorText();
        button.setAttribute("aria-describedby", error.id);
        if (error.parentElement != group) {
          group.appendChild(error);
        }
      } else {
        button.removeAttribute("aria-describedby");
        remove(errorsByAction.remove(action.getActionId()));
      }
    }
    removeStaleActions(seenActions);
    removeStaleGroups(seenGroups);
  }

  private void onToolbarAction(Event event) {
    if (listener == null) {
      return;
    }
    Object detail = Js.asPropertyMap(event).get("detail");
    Object action = detail == null ? null : Js.asPropertyMap(detail).get("action");
    if (action != null) {
      listener.onActionRequested(String.valueOf(action));
    }
  }

  private static void setProperty(HTMLElement element, String name, Object value) {
    Js.asPropertyMap(element).set(name, value);
  }

  private HTMLElement ensureGroup(String groupLabel) {
    HTMLElement group = groupsByLabel.get(groupLabel);
    if (group == null) {
      group = (HTMLElement) DomGlobal.document.createElement("toolbar-group");
      group.setAttribute("label", groupLabel);
      groupsByLabel.put(groupLabel, group);
      host.appendChild(group);
    }
    return group;
  }

  private HTMLElement ensureButton(String actionId) {
    HTMLElement button = buttonsByAction.get(actionId);
    if (button == null) {
      button = (HTMLElement) DomGlobal.document.createElement("toolbar-button");
      buttonsByAction.put(actionId, button);
    }
    return button;
  }

  private HTMLElement ensureError(String actionId) {
    HTMLElement error = errorsByAction.get(actionId);
    if (error == null) {
      error = (HTMLElement) DomGlobal.document.createElement("p");
      error.id = "j2cl-toolbar-error-" + actionId;
      error.className = "j2cl-toolbar-error";
      error.setAttribute("role", "status");
      error.setAttribute("aria-live", "polite");
      errorsByAction.put(actionId, error);
    }
    return error;
  }

  private void removeStaleActions(Set<String> seenActions) {
    Set<String> existing = new HashSet<String>(buttonsByAction.keySet());
    for (String actionId : existing) {
      if (!seenActions.contains(actionId)) {
        remove(buttonsByAction.remove(actionId));
        remove(errorsByAction.remove(actionId));
      }
    }
  }

  private void removeStaleGroups(Set<String> seenGroups) {
    Set<String> existing = new HashSet<String>(groupsByLabel.keySet());
    for (String groupLabel : existing) {
      if (!seenGroups.contains(groupLabel)) {
        remove(groupsByLabel.remove(groupLabel));
      }
    }
  }

  private static void remove(HTMLElement element) {
    if (element != null && element.parentElement != null) {
      element.parentElement.removeChild(element);
    }
  }
}
