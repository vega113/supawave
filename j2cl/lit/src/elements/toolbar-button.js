import { LitElement, css, html, nothing } from "lit";
import { getToolbarIcon } from "../icons/toolbar-icons.js";

export class ToolbarButton extends LitElement {
  static properties = {
    action: { type: String },
    label: { type: String },
    icon: { type: String, reflect: true },
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

    /* V-3 (#1101): icon-mode tile styling. Activated when [icon] is set
     * — wavy-format-toolbar passes icon=<actionId> for the daily-rich
     * edit set. Other consumers (wave-blip-toolbar, etc.) leave icon
     * unset and keep the default text-button rendering. */
    :host([icon]) button {
      width: var(--wavy-toolbar-tile-size, 32px);
      height: var(--wavy-toolbar-tile-size, 32px);
      padding: 0;
      border: none;
      border-radius: var(--wavy-toolbar-tile-radius, 8px);
      background: transparent;
      color: var(--wavy-toolbar-pill-fg, rgba(255, 255, 255, 0.92));
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    :host([icon]) button:hover:not(:disabled) {
      background: var(--wavy-toolbar-tile-hover-bg, rgba(255, 255, 255, 0.08));
    }

    :host([icon]) button:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }

    :host([icon]) button[aria-pressed="true"] {
      background: var(--wavy-toolbar-tile-active-bg, #22d3ee);
      color: var(--wavy-toolbar-tile-active-fg, #0b1320);
      border: none;
    }

    :host([icon]) button[aria-pressed="true"]:hover:not(:disabled) {
      background: var(--wavy-toolbar-tile-active-bg, #22d3ee);
    }

    :host([icon]) svg {
      width: 16px;
      height: 16px;
    }
  `;

  constructor() {
    super();
    this.action = "";
    this.label = "";
    this.icon = "";
    this.pressed = false;
    this.disabled = false;
    this.toggle = false;
    this.danger = false;
  }

  render() {
    const iconTemplate = this.icon ? getToolbarIcon(this.icon) : null;
    return html`
      <button
        type="button"
        aria-label=${this.label}
        title=${this.label || nothing}
        aria-pressed=${this.toggle ? String(this.pressed) : nothing}
        data-action=${this.action}
        ?disabled=${this.disabled}
        @click=${this.onClick}
      >
        ${iconTemplate ? iconTemplate : this.label}
      </button>
    `;
  }

  onClick() {
    if (this.disabled || !this.action) {
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
