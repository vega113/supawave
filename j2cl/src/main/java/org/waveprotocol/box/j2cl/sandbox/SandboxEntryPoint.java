package org.waveprotocol.box.j2cl.sandbox;

import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;

@JsType(namespace = JsPackage.GLOBAL, name = "WaveSandboxEntryPoint")
public final class SandboxEntryPoint {
  private static final String DEFAULT_MODE = "sidecar";

  private SandboxEntryPoint() {
  }

  @JsMethod
  public static void mount(String elementId, String requestedMode) {
    HTMLElement host = (HTMLElement) DomGlobal.document.getElementById(elementId);
    if (host == null) {
      return;
    }

    String mode = normalizeMode(requestedMode);
    host.innerHTML = "";

    HTMLDivElement card = (HTMLDivElement) DomGlobal.document.createElement("div");
    card.className = "sidecar-card";

    HTMLElement eyebrow = (HTMLElement) DomGlobal.document.createElement("p");
    eyebrow.className = "sidecar-eyebrow";
    eyebrow.textContent = "Isolated J2CL sidecar";
    card.appendChild(eyebrow);

    HTMLElement title = (HTMLElement) DomGlobal.document.createElement("h1");
    title.className = "sidecar-title";
    title.textContent = "Sandbox build scaffold";
    card.appendChild(title);

    HTMLElement summary = (HTMLElement) DomGlobal.document.createElement("p");
    summary.className = "sidecar-summary";
    summary.textContent = renderSummary(mode);
    card.appendChild(summary);

    HTMLElement detail = (HTMLElement) DomGlobal.document.createElement("p");
    detail.className = "sidecar-detail";
    detail.textContent = "Legacy / stays on the GWT web client while this page proves the sidecar can build and load independently.";
    card.appendChild(detail);

    host.appendChild(card);
  }

  public static String renderSummary(String requestedMode) {
    String mode = normalizeMode(requestedMode);
    return "Profile " + mode + " writes isolated assets without changing the root runtime bootstrap.";
  }

  private static String normalizeMode(String requestedMode) {
    if (requestedMode == null) {
      return DEFAULT_MODE;
    }
    String trimmed = requestedMode.trim();
    return trimmed.isEmpty() ? DEFAULT_MODE : trimmed;
  }
}
