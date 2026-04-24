import { LitElement, css, html } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";

export class InteractionOverlayLayer extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    modal: { type: Boolean, reflect: true },
    label: { type: String },
    focusTargetId: { type: String, attribute: "focus-target-id" }
  };

  static styles = css`
    :host {
      display: contents;
    }

    [part="surface"] {
      position: relative;
      z-index: var(--shell-z-overlay-menu, 20);
      box-sizing: border-box;
      max-width: min(92vw, 420px);
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 16px;
      padding: 12px;
      background: var(--shell-color-surface-overlay, #fff);
      box-shadow: var(--shell-shadow-overlay, 0 16px 44px rgb(10 38 64 / 18%));
      color: var(--shell-color-text-primary, #181c1d);
    }

    :host([modal]) [part="surface"] {
      z-index: var(--shell-z-overlay-modal, 40);
      box-shadow: var(--shell-shadow-modal, 0 22px 70px rgb(10 38 64 / 24%));
    }
  `;

  constructor() {
    super();
    this.open = false;
    this.modal = false;
    this.label = "";
    this.focusTargetId = "";
    this.addEventListener("keydown", this.onKeyDown);
  }

  updated(changed) {
    if (changed.has("open") && this.open && this.modal) {
      queueMicrotask(() => this.renderRoot.querySelector("[part='surface']")?.focus());
    }
  }

  render() {
    if (!this.open) {
      return "";
    }
    return html`
      <div
        part="surface"
        role=${this.modal ? "dialog" : "group"}
        tabindex="-1"
        aria-modal=${ifDefined(this.modal ? "true" : undefined)}
        aria-label=${this.label || "Interaction overlay"}
      >
        <slot></slot>
      </div>
    `;
  }

  onKeyDown = (event) => {
    if (!this.open) {
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      this.close("escape");
      return;
    }
    if (this.modal && event.key === "Tab") {
      this.trapFocus(event);
    }
  };

  close(reason) {
    this.open = false;
    this.dispatchEvent(
      new CustomEvent("overlay-close", {
        detail: { reason, focusTargetId: this.focusTargetId },
        bubbles: true,
        composed: true
      })
    );
  }

  trapFocus(event) {
    const controls = this.focusableControls();
    if (controls.length === 0) {
      return;
    }
    const active = this.renderRoot.activeElement || this.getRootNode().activeElement;
    const currentIndex = controls.indexOf(active);
    if (currentIndex === -1) {
      event.preventDefault();
      controls[event.shiftKey ? controls.length - 1 : 0].focus();
      return;
    }
    if (event.shiftKey && currentIndex === 0) {
      event.preventDefault();
      controls[controls.length - 1].focus();
      return;
    }
    if (!event.shiftKey && currentIndex === controls.length - 1) {
      event.preventDefault();
      controls[0].focus();
    }
  }

  focusableControls() {
    const slot = this.renderRoot.querySelector("slot");
    const assigned = slot?.assignedElements({ flatten: true }) || [];
    const controls = [];
    for (const element of assigned) {
      if (this.isFocusable(element)) {
        controls.push(element);
      }
      controls.push(...Array.from(element.querySelectorAll(this.focusableSelector())));
    }
    const contentControls = controls.filter(Boolean).filter(element => !element.disabled);
    return contentControls.length > 0
      ? contentControls
      : [this.renderRoot.querySelector("[part='surface']")].filter(Boolean);
  }

  isFocusable(element) {
    return element.matches?.(this.focusableSelector());
  }

  focusableSelector() {
    return "button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])";
  }
}

if (!customElements.get("interaction-overlay-layer")) {
  customElements.define("interaction-overlay-layer", InteractionOverlayLayer);
}
