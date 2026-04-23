package org.waveprotocol.box.j2cl.root;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLElement;
import elemental2.dom.NodeList;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;

public final class J2clRootShellView {
  private static final String DEFAULT_RETURN_TARGET = "/?view=j2cl-root";
  private final HTMLElement workflowHost;

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

  public void syncReturnTarget(String routeUrl) {
    String returnTarget = normalizeLocalReturnTarget(routeUrl);
    HTMLElement shell = (HTMLElement) DomGlobal.document.querySelector("[data-j2cl-root-shell='true']");
    if (shell != null) {
      shell.setAttribute("data-j2cl-root-return-target", returnTarget);
    }
    updateHref("j2cl-root-brand-link", returnTarget);
    updateAuthHrefs("[data-j2cl-root-signin='true']", "/auth/signin", returnTarget);
    updateAuthHrefs("[data-j2cl-root-signout='true']", "/auth/signout", returnTarget);

    HTMLElement returnTargetLabel =
        (HTMLElement) DomGlobal.document.getElementById("j2cl-root-return-target-text");
    if (returnTargetLabel != null) {
      returnTargetLabel.textContent = "Return target: " + returnTarget;
    }
  }

  private static void updateHref(String elementId, String href) {
    HTMLElement element = (HTMLElement) DomGlobal.document.getElementById(elementId);
    if (element != null) {
      element.setAttribute("href", href);
    }
  }

  private static void updateAuthHrefs(String selector, String authPath, String returnTarget) {
    NodeList<elemental2.dom.Element> elements = DomGlobal.document.querySelectorAll(selector);
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
