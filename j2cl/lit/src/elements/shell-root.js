import { LitElement, css, html } from "lit";

export class ShellRoot extends LitElement {
  static styles = css`
    /* V-1 (#1099): the canonical grid for <shell-root> lives in
     * j2cl/lit/src/tokens/shell-tokens.css and targets the host element
     * directly from the document, so it wins over any :host rule
     * declared here (per CSS Scoping spec). Only slot-positioning
     * declarations live in the shadow tree — they apply to the slot
     * placeholders inside the shadow root regardless of what the host
     * grid looks like, so the rail-extension slot resolves to its own
     * named area when the panel is un-hidden by a plugin. */
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
     * slotted <wavy-rail-panel> ships hidden on the user route. When a
     * plugin un-hides it, it lands in its own grid row beneath the
     * main region so it never overlaps the wave panel. The auto-sized
     * track collapses to 0 height when the slot is empty / hidden. */
    slot[name="rail-extension"] {
      grid-area: rail-extension;
      min-width: 0;
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
