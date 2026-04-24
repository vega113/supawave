import { LitElement, css, html, nothing } from "lit";

export class ToolbarButton extends LitElement {
  static properties = {
    action: { type: String },
    label: { type: String },
    pressed: { type: Boolean, reflect: true },
    disabled: { type: Boolean, reflect: true },
    toggle: { type: Boolean, reflect: true },
    danger: { type: Boolean, reflect: true }
  };

  static styles = css`
    button {
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 10px;
      padding: 7px 10px;
      background: #fff;
      color: var(--shell-color-text-primary, #181c1d);
      font: inherit;
      cursor: pointer;
    }

    button[aria-pressed="true"] {
      background: #123b5d;
      border-color: #123b5d;
      color: #fff;
    }

    button:disabled {
      cursor: not-allowed;
      opacity: 0.5;
    }

    :host([danger]) button {
      color: #a12a16;
    }
  `;

  constructor() {
    super();
    this.action = "";
    this.label = "";
    this.pressed = false;
    this.disabled = false;
    this.toggle = false;
    this.danger = false;
  }

  render() {
    return html`
      <button
        type="button"
        aria-label=${this.label}
        aria-pressed=${this.toggle ? String(this.pressed) : nothing}
        data-action=${this.action}
        ?disabled=${this.disabled}
        @click=${this.onClick}
      >
        ${this.label}
      </button>
    `;
  }

  onClick() {
    if (this.disabled) {
      return;
    }
    this.dispatchEvent(
      new CustomEvent("toolbar-action", {
        detail: { action: this.action },
        bubbles: true,
        composed: true
      })
    );
  }
}

if (!customElements.get("toolbar-button")) {
  customElements.define("toolbar-button", ToolbarButton);
}
