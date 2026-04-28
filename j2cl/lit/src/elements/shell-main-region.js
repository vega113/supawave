import { LitElement, css, html } from "lit";

export class ShellMainRegion extends LitElement {
  static styles = css`
    :host {
      display: block;
      flex: 1;
      min-width: 0;
    }

    /* V-1 (#1099): wave panel sits flush against the rail / header
     * border. The inner sidecar layout owns its own gutters; this
     * shell wrapper no longer adds an outer panel inset that pushed
     * cards into a narrow centred column. */
    main {
      padding: 0;
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
