import { LitElement, css, html } from "lit";

export class ShellNavRail extends LitElement {
  static properties = {
    label: { type: String }
  };

  static styles = css`
    :host {
      display: block;
    }

    nav {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: var(--shell-space-inset-panel, 12px);
      min-width: 180px;
    }

    ::slotted(*) {
      display: block;
      padding: 8px 10px;
      border-radius: 8px;
    }
  `;

  constructor() {
    super();
    this.label = "Primary";
  }

  render() {
    return html`<nav aria-label=${this.label}><slot></slot></nav>`;
  }
}

if (!customElements.get("shell-nav-rail")) {
  customElements.define("shell-nav-rail", ShellNavRail);
}
