import { LitElement, css, html } from "lit";

export class ShellHeader extends LitElement {
  static properties = {
    signedIn: { type: Boolean, attribute: "signed-in", reflect: true },
    compactGwtTopbar: { type: Boolean, attribute: "compact-gwt-topbar", reflect: true }
  };

  static styles = css`
    :host {
      display: block;
    }

    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--shell-space-inline-default, 12px);
    }

    header.gwt-topbar-panel {
      min-height: 40px;
      gap: 8px;
    }

    .brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
    }

    header.gwt-topbar-panel .brand {
      gap: 8px;
    }

    .actions {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }

    header.gwt-topbar-panel .actions {
      flex-wrap: nowrap;
      gap: 6px;
    }
  `;

  constructor() {
    super();
    this.signedIn = false;
    this.compactGwtTopbar = false;
  }

  render() {
    const classes = [
      this.compactGwtTopbar ? "gwt-topbar-panel" : "",
      this.signedIn ? "is-signed-in" : "is-signed-out"
    ].filter(Boolean).join(" ");
    return html`
      <header class=${classes} role="banner">
        <div class="brand"><slot name="brand"></slot></div>
        <div class="actions">
          ${this.signedIn
            ? html`<slot name="actions-signed-in"></slot>`
            : html`<slot name="actions-signed-out"></slot>`}
        </div>
      </header>
    `;
  }
}

if (!customElements.get("shell-header")) {
  customElements.define("shell-header", ShellHeader);
}
