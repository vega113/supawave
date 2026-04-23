import { LitElement, css, html } from "lit";

export class ShellMainRegion extends LitElement {
  static styles = css`
    :host {
      display: block;
      flex: 1;
      min-width: 0;
    }

    main {
      padding: var(--shell-space-inset-panel, 20px);
      min-height: 360px;
    }
  `;

  render() {
    // The #j2cl-root-shell-workflow element is a light-DOM child of this
    // component; it must stay queryable via document.getElementById so
    // J2clRootShellController.start() continues to work unchanged.
    return html`<main><slot></slot></main>`;
  }
}

if (!customElements.get("shell-main-region")) {
  customElements.define("shell-main-region", ShellMainRegion);
}
