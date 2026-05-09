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

    /* Round 4 (#1236): when the nav rail wraps a full search-rail panel,
     * the panel manages its own header strip / search input gutters and
     * the GWT-parity contract requires it sit flush at y=0 so the blue
     * title strip aligns with the wave-panel title across panels.
     * Override the inset-panel padding and the ::slotted pill geometry
     * for this specific child. */
    :host(:has(> wavy-search-rail)) nav {
      padding: 0;
    }

    ::slotted(wavy-search-rail) {
      padding: 0;
      border-radius: 0;
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
