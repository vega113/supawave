import { LitElement, css, html } from "lit";

export class ShellRoot extends LitElement {
  static styles = css`
    :host {
      display: grid;
      /* V-1 (#1099): two-column nav | main layout matching
       * 01-shell-inbox-with-waves.svg. The rail-extension slot still
       * exists so the F-4 plugin contract holds, but its slotted
       * <wavy-rail-panel> is hidden by default on the user route (see
       * HtmlRenderer.java + wavy-rail-panel.js); when a plugin
       * un-hides it, it stacks under the main region rather than
       * stealing a third column. */
      grid-template-columns: minmax(260px, 296px) 1fr;
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

    /* F-4 (#1039 / R-4.7): production rail-extension landing zone. The
     * slotted <wavy-rail-panel> ships hidden by default; plugins
     * (assistant, tasks roll-up, integrations status) un-hide it via
     * the inner <slot name="rail-extension"> on the projected panel. */
    slot[name="rail-extension"] {
      grid-area: main;
      min-width: 0;
      min-height: 0;
    }

    slot[name="status"] {
      grid-area: status;
    }

    @media (max-width: 860px) {
      /* Mobile: single-column stack. Main keeps the 1fr track so the
       * content region still fills the viewport. */
      :host {
        grid-template-columns: 1fr;
        grid-template-rows: auto auto auto 1fr auto;
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
      <slot name="rail-extension"></slot>
      <slot name="status"></slot>
    `;
  }
}

if (!customElements.get("shell-root")) {
  customElements.define("shell-root", ShellRoot);
}
