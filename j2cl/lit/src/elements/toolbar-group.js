import { LitElement, css, html } from "lit";

export class ToolbarGroup extends LitElement {
  static properties = {
    label: { type: String }
  };

  static styles = css`
    :host {
      display: block;
    }

    div {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      align-items: center;
    }
  `;

  constructor() {
    super();
    this.label = "Toolbar group";
  }

  render() {
    return html`<div role="group" aria-label=${this.label}><slot></slot></div>`;
  }
}

if (!customElements.get("toolbar-group")) {
  customElements.define("toolbar-group", ToolbarGroup);
}
