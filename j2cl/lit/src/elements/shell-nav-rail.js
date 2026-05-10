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

    /* Round 4 (#1236) / Round 5 (#1237): when the nav rail wraps a full
     * search-rail panel, the panel manages its own header strip / search
     * input gutters and the GWT-parity contract requires it sit flush at
     * y=0 so the blue title strip aligns with the wave-panel title.
     *
     * Round 4 used :host(:has(> wavy-search-rail)) but Chromium does
     * not match :has() inside :host() against light-DOM slotted
     * children (verified in shell-nav-rail.test.js), so the rule never
     * applied at runtime — the rail title kept rendering ~12px below
     * the wave-panel title because nav.padding stayed at 12px. Round 5
     * sets a data-flush attribute on the host via a slotchange
     * listener and targets that attribute instead, which Chromium does
     * apply. */
    :host([data-flush]) nav {
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
    this._onSlotChange = this._onSlotChange.bind(this);
  }

  connectedCallback() {
    super.connectedCallback();
    this._refreshFlushState();
  }

  _onSlotChange() {
    this._refreshFlushState();
  }

  _refreshFlushState() {
    const hasSearchRail = this.querySelector(":scope > wavy-search-rail") !== null;
    if (hasSearchRail) {
      this.setAttribute("data-flush", "");
    } else {
      this.removeAttribute("data-flush");
    }
  }

  render() {
    return html`<nav aria-label=${this.label}><slot @slotchange=${this._onSlotChange}></slot></nav>`;
  }
}

if (!customElements.get("shell-nav-rail")) {
  customElements.define("shell-nav-rail", ShellNavRail);
}
