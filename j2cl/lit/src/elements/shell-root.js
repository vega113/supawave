import { LitElement, css, html } from "lit";

export class ShellRoot extends LitElement {
  static styles = css`
    :host {
      display: grid;
      /* F-4 (#1039 / R-4.7): three-column layout — nav | main | rail —
       * with the rail collapsing to single-column at the existing 860px
       * breakpoint so mobile keeps a single content column. The rail
       * column is sized to comfortably fit a <wavy-rail-panel> (220–280px)
       * without crowding the main read surface. */
      grid-template-columns: minmax(190px, 220px) 1fr minmax(220px, 280px);
      grid-template-rows: auto auto 1fr auto;
      grid-template-areas:
        "skip skip skip"
        "header header header"
        "nav main rail"
        "status status status";
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

    /* F-4 (#1039 / R-4.7): production rail-extension landing zone. Empty
     * by default; plugins (assistant, tasks roll-up, integrations status)
     * target the inner <slot name="rail-extension"> on the projected
     * <wavy-rail-panel>, which inherits the M.4 plugin contract from F-0. */
    slot[name="rail-extension"] {
      grid-area: rail;
      min-width: 0;
      min-height: 0;
    }

    slot[name="status"] {
      grid-area: status;
    }

    @media (max-width: 1100px) {
      /* On mid-width viewports the rail collapses BELOW the main region
       * so the content column keeps its readable width. */
      :host {
        grid-template-columns: minmax(190px, 220px) 1fr;
        grid-template-areas:
          "skip skip"
          "header header"
          "nav main"
          "nav rail"
          "status status";
      }
    }

    @media (max-width: 860px) {
      :host {
        grid-template-columns: 1fr;
        grid-template-areas:
          "skip"
          "header"
          "nav"
          "main"
          "rail"
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
