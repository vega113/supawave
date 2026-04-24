package org.waveprotocol.box.j2cl.root;

import elemental2.dom.DomGlobal;
import elemental2.dom.Element;
import elemental2.dom.HTMLElement;
import elemental2.dom.NodeList;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;

public final class J2clRootShellView implements J2clRootLiveSurfaceController.ShellSurface {
  private static final String DEFAULT_RETURN_TARGET = "/?view=j2cl-root";
  private static final String LIVE_STATUS_ID = "j2cl-root-live-status-text";
  private static final String LIVE_STATUS_SEPARATOR_ID = "j2cl-root-live-status-separator";
  private final HTMLElement workflowHost;
  private HTMLElement liveStatus;
  private HTMLElement liveStatusSeparator;
  private String lastPublishedLiveStatusText;

  public J2clRootShellView(HTMLElement host) {
    if (J2clServerFirstRootShellDom.hasServerFirstWorkflow(host)) {
      workflowHost = host;
      return;
    }

    host.innerHTML = "";

    HTMLElement root = (HTMLElement) DomGlobal.document.createElement("section");
    root.className = "j2cl-root-shell-runtime";
    root.setAttribute("data-j2cl-root-shell-runtime", "true");
    host.appendChild(root);

    HTMLElement eyebrow = (HTMLElement) DomGlobal.document.createElement("p");
    eyebrow.className = "j2cl-root-shell-runtime-eyebrow";
    eyebrow.textContent = "J2CL root shell";
    root.appendChild(eyebrow);

    HTMLElement title = (HTMLElement) DomGlobal.document.createElement("h2");
    title.className = "j2cl-root-shell-runtime-title";
    title.textContent = "Hosted workflow";
    root.appendChild(title);

    HTMLElement detail = (HTMLElement) DomGlobal.document.createElement("p");
    detail.className = "j2cl-root-shell-runtime-detail";
    detail.textContent =
        "The existing J2CL search/read/write flow now mounts inside the root shell.";
    root.appendChild(detail);

    workflowHost = (HTMLElement) DomGlobal.document.createElement("div");
    workflowHost.className = "j2cl-root-shell-workflow-host";
    root.appendChild(workflowHost);
  }

  public HTMLElement getWorkflowHost() {
    return workflowHost;
  }

  @Override
  public void syncReturnTarget(String routeUrl) {
    String returnTarget = normalizeLocalReturnTarget(routeUrl);
    Element shell = findRootShell();
    if (shell == null) {
      return;
    }
    shell.setAttribute("data-j2cl-root-return-target", returnTarget);
    updateHref(shell, "j2cl-root-brand-link", returnTarget);
    updateAuthHrefs(shell, "[data-j2cl-root-signin='true']", "/auth/signin", returnTarget);
    updateAuthHrefs(shell, "[data-j2cl-root-signout='true']", "/auth/signout", returnTarget);

    HTMLElement returnTargetLabel =
        (HTMLElement) shell.querySelector("#j2cl-root-return-target-text");
    if (returnTargetLabel != null) {
      returnTargetLabel.textContent = "Return target: " + returnTarget;
    }
  }

  @Override
  public void publishLiveStatus(J2clRootLiveSurfaceModel model) {
    HTMLElement liveStatus = ensureLiveStatusElement();
    if (liveStatus != null) {
      String statusText = model == null ? "" : model.getStatusText();
      if (statusText.equals(lastPublishedLiveStatusText)) {
        return;
      }
      liveStatus.textContent = statusText;
      if (liveStatusSeparator != null) {
        liveStatusSeparator.style.display = statusText.isEmpty() ? "none" : "";
      }
      lastPublishedLiveStatusText = statusText;
    }
  }

  private HTMLElement ensureLiveStatusElement() {
    if (liveStatus != null) {
      return liveStatus;
    }
    HTMLElement statusStrip = findStatusStrip();
    if (statusStrip == null) {
      return null;
    }
    if (liveStatusSeparator == null) {
      liveStatusSeparator = (HTMLElement) DomGlobal.document.createElement("span");
      liveStatusSeparator.id = LIVE_STATUS_SEPARATOR_ID;
      liveStatusSeparator.setAttribute("aria-hidden", "true");
      liveStatusSeparator.textContent = " | ";
      statusStrip.appendChild(liveStatusSeparator);
    }
    liveStatus = (HTMLElement) DomGlobal.document.createElement("span");
    liveStatus.id = LIVE_STATUS_ID;
    statusStrip.appendChild(liveStatus);
    return liveStatus;
  }

  private Element findRootShell() {
    Element rootShell = workflowHost;
    // Bind to the nearest owning root shell so nested or parallel shells cannot cross-write status.
    while (rootShell != null) {
      if ("true".equals(rootShell.getAttribute("data-j2cl-root-shell"))) {
        return rootShell;
      }
      rootShell = rootShell.parentElement;
    }
    return null;
  }

  private HTMLElement findStatusStrip() {
    Element rootShell = findRootShell();
    if (rootShell == null) {
      return null;
    }
    // shell-status-strip renders a default slot; appended light-DOM children stay visible.
    return (HTMLElement) rootShell.querySelector("shell-status-strip[slot='status']");
  }

  private static void updateHref(Element scope, String elementId, String href) {
    HTMLElement element = (HTMLElement) scope.querySelector("#" + elementId);
    if (element != null) {
      element.setAttribute("href", href);
    }
  }

  private static void updateAuthHrefs(
      Element scope, String selector, String authPath, String returnTarget) {
    NodeList<elemental2.dom.Element> elements = scope.querySelectorAll(selector);
    for (int index = 0; index < elements.length; index++) {
      HTMLElement element = (HTMLElement) elements.item(index);
      if (element != null) {
        element.setAttribute("href", authPath + "?r=" + encodeLocalReturnTarget(returnTarget));
      }
    }
  }

  private static String normalizeLocalReturnTarget(String routeUrl) {
    if (routeUrl == null || routeUrl.isEmpty()) {
      return DEFAULT_RETURN_TARGET;
    }
    String normalized = routeUrl.charAt(0) == '?' ? "/" + routeUrl : routeUrl;
    if (!normalized.startsWith("/") || normalized.startsWith("//")) {
      return DEFAULT_RETURN_TARGET;
    }
    return normalized;
  }

  private static String encodeLocalReturnTarget(String returnTarget) {
    if (returnTarget == null || returnTarget.isEmpty()) {
      return "";
    }
    if (returnTarget.startsWith("/")) {
      return "/" + encodeUriComponent(returnTarget.substring(1));
    }
    return encodeUriComponent(returnTarget);
  }

  @JsMethod(namespace = JsPackage.GLOBAL, name = "encodeURIComponent")
  private static native String encodeUriComponent(String value);
}
