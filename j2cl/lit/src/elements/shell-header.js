import { LitElement, css, html } from "lit";

export class ShellHeader extends LitElement {
  static properties = {
    signedIn: { type: Boolean, attribute: "signed-in", reflect: true }
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
      padding: var(--shell-space-inset-panel, 16px 18px);
      border-bottom: 1px solid var(--shell-color-divider-subtle, #e5edf5);
      background: var(--shell-color-surface-shell, #fff);
    }

    .brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
    }

    .actions {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
  `;

  constructor() {
    super();
    this.signedIn = false;
  }

  render() {
    return html`
      <header role="banner">
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
