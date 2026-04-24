import { LitElement, css, html } from "lit";

export class ComposerSubmitAffordance extends LitElement {
  static properties = {
    label: { type: String },
    status: { type: String },
    error: { type: String },
    busy: { type: Boolean, reflect: true },
    disabled: { type: Boolean, reflect: true }
  };

  static styles = css`
    :host {
      display: inline-grid;
      gap: 4px;
    }

    button {
      border: 0;
      border-radius: 999px;
      padding: 9px 16px;
      background: var(--shell-color-accent-focus, #1a73e8);
      color: #fff;
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }

    button:disabled {
      cursor: not-allowed;
      opacity: 0.55;
    }

    .message {
      margin: 0;
      color: var(--shell-color-text-muted, #5b6b80);
      font-size: 0.85rem;
    }

    .message.error {
      color: #a12a16;
    }
  `;

  constructor() {
    super();
    this.label = "Submit";
    this.status = "";
    this.error = "";
    this.busy = false;
    this.disabled = false;
  }

  render() {
    const disabled = this.disabled || this.busy;
    const message = this.error || this.status;
    return html`
      <button
        type="button"
        aria-label=${this.label}
        aria-busy=${this.busy ? "true" : "false"}
        ?disabled=${disabled}
        @click=${this.onClick}
      >
        ${this.label}
      </button>
      ${message
        ? html`<p
            class=${this.error ? "message error" : "message"}
            role=${this.error ? "alert" : "status"}
            aria-live=${this.error ? "assertive" : "polite"}
          >
            ${message}
          </p>`
        : ""}
    `;
  }

  onClick() {
    if (this.disabled || this.busy) {
      return;
    }
    this.dispatchEvent(new CustomEvent("submit-affordance", { bubbles: true, composed: true }));
  }
}

if (!customElements.get("composer-submit-affordance")) {
  customElements.define("composer-submit-affordance", ComposerSubmitAffordance);
}
