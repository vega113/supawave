import { LitElement, css, html } from "lit";

export class ShellRootSignedOut extends LitElement {
  static styles = css`
    :host {
      display: grid;
      grid-template-columns: 1fr;
      grid-template-rows: auto auto 1fr auto;
      grid-template-areas:
        "skip"
        "header"
        "main"
        "status";
      min-height: 100vh;
      background: var(--shell-color-surface-page, #f6fafb);
      color: var(--shell-color-text-primary, #181c1d);
    }

    slot[name="skip-link"] {
      grid-area: skip;
    }

    slot[name="header"] {
      grid-area: header;
    }

    slot[name="main"] {
      grid-area: main;
      min-height: 0;
    }

    slot[name="status"] {
      grid-area: status;
    }
  `;

  render() {
    return html`
      <slot name="skip-link"></slot>
      <slot name="header"></slot>
      <slot name="main"></slot>
      <slot name="status"></slot>
    `;
  }
}

if (!customElements.get("shell-root-signed-out")) {
  customElements.define("shell-root-signed-out", ShellRootSignedOut);
}
