package org.waveprotocol.box.j2cl.root;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLElement;

public final class J2clRootShellView {
  private final HTMLElement workflowHost;

  public J2clRootShellView(HTMLElement host) {
    host.innerHTML = "";

    HTMLElement root = (HTMLElement) DomGlobal.document.createElement("section");
    root.className = "j2cl-root-shell-runtime";
    root.setAttribute("data-j2cl-root-shell", "true");
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
}
