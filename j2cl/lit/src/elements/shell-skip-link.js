import { LitElement, css, html } from "lit";

export class ShellSkipLink extends LitElement {
  static properties = {
    target: { type: String },
    label: { type: String }
  };

  static styles = css`
    :host {
      position: absolute;
      top: 0;
      left: 0;
    }

    ::slotted(a) {
      position: absolute;
      left: -9999px;
      padding: 8px 12px;
      background: var(--shell-color-accent-focus, #1a73e8);
      color: #fff;
      border-radius: 0 0 8px 0;
      font: inherit;
      text-decoration: none;
    }

    ::slotted(a:focus) {
      left: 0;
      outline: 2px solid #fff;
      outline-offset: -4px;
    }
  `;

  constructor() {
    super();
    this.target = "#j2cl-root-shell-workflow";
    this.label = "Skip to main content";
  }

  connectedCallback() {
    super.connectedCallback();
    this.syncAnchor();
  }

  updated() {
    this.syncAnchor();
  }

  render() {
    return html`<slot></slot>`;
  }

  syncAnchor() {
    let anchor = this.querySelector("a");
    if (!anchor) {
      anchor = this.ownerDocument.createElement("a");
      this.appendChild(anchor);
    }
    anchor.setAttribute("href", this.target);
    anchor.textContent = this.label;
  }
}

if (!customElements.get("shell-skip-link")) {
  customElements.define("shell-skip-link", ShellSkipLink);
}
