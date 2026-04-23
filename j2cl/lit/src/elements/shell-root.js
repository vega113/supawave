import { LitElement, css, html } from "lit";

export class ShellRoot extends LitElement {
  static styles = css`
    :host {
      display: grid;
      grid-template-columns: minmax(190px, 220px) 1fr;
      grid-template-rows: auto auto 1fr auto;
      grid-template-areas:
        "skip skip"
        "header header"
        "nav main"
        "status status";
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

    slot[name="nav"] {
      grid-area: nav;
      min-height: 0;
    }

    slot[name="main"] {
      grid-area: main;
      min-width: 0;
      min-height: 0;
    }

    slot[name="status"] {
      grid-area: status;
    }

    @media (max-width: 860px) {
      :host {
        grid-template-columns: 1fr;
        grid-template-areas:
          "skip"
          "header"
          "nav"
          "main"
          "status";
      }
    }
  `;

  render() {
    return html`
      <slot name="skip-link"></slot>
      <slot name="header"></slot>
      <slot name="nav"></slot>
      <slot name="main"></slot>
      <slot name="status"></slot>
    `;
  }
}

if (!customElements.get("shell-root")) {
  customElements.define("shell-root", ShellRoot);
}
