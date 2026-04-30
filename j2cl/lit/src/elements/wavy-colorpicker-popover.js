import { LitElement, css, html, nothing } from "lit";
import { WAVY_COLOR_PALETTE } from "../format/color-options.js";

const COLS = 10;

export class WavyColorpickerPopover extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    mode: { type: String, reflect: true },
    activeIndex: { type: Number, attribute: "active-index" }
  };

  static styles = css`
    :host {
      display: block;
      position: relative;
    }

    .panel {
      position: absolute;
      top: var(--wavy-spacing-6, 24px);
      left: 0;
      z-index: 1001;
      display: grid;
      grid-template-columns: repeat(10, 18px);
      gap: 3px;
      padding: var(--wavy-spacing-2, 8px);
      border: 1px solid var(--wavy-border-hairline, #d8e3ee);
      border-radius: var(--wavy-radius-panel, 8px);
      background: var(--wavy-bg-base, #ffffff);
      box-shadow: var(--wavy-shadow-card, 0 8px 24px rgba(15, 23, 42, 0.16));
    }

    button {
      width: 18px;
      height: 18px;
      border: 1px solid rgba(11, 19, 32, 0.18);
      border-radius: 3px;
      cursor: pointer;
      padding: 0;
    }

    button[aria-selected="true"] {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px rgba(0, 119, 182, 0.24));
      border-color: var(--wavy-signal-cyan, #0077b6);
    }

    button:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px rgba(0, 119, 182, 0.24));
    }
  `;

  constructor() {
    super();
    this.open = false;
    this.mode = "text";
    this.activeIndex = 0;
  }

  updated(changed) {
    if (changed.has("open") && this.open) {
      this.activeIndex = this._clampIndex(this.activeIndex);
      this.updateComplete.then(() => {
        this.renderRoot.querySelector("[role='grid']")?.focus();
      });
    }
  }

  render() {
    if (!this.open) return nothing;
    const label = this.mode === "highlight" ? "Highlight color palette" : "Text color palette";
    return html`<div
      class="panel"
      role="grid"
      aria-label=${label}
      tabindex="0"
      @keydown=${this._onKeyDown}
    >
      ${WAVY_COLOR_PALETTE.map((color, index) => html`<button
        type="button"
        role="gridcell"
        data-color=${color}
        aria-label=${color}
        aria-selected=${String(index === this.activeIndex)}
        style=${`background: ${color}`}
        @click=${() => this._select(index)}
      ></button>`)}
    </div>`;
  }

  _onKeyDown(event) {
    let next = this.activeIndex;
    if (event.key === "ArrowRight") {
      next += 1;
    } else if (event.key === "ArrowLeft") {
      next -= 1;
    } else if (event.key === "ArrowDown") {
      next += COLS;
    } else if (event.key === "ArrowUp") {
      next -= COLS;
    } else if (event.key === "Home") {
      next = 0;
    } else if (event.key === "End") {
      next = WAVY_COLOR_PALETTE.length - 1;
    } else if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      this._select(this.activeIndex);
      return;
    } else if (event.key === "Escape") {
      event.preventDefault();
      this.open = false;
      return;
    } else {
      return;
    }
    event.preventDefault();
    this.activeIndex = this._clampIndex(next);
  }

  _select(index) {
    const color = WAVY_COLOR_PALETTE[this._clampIndex(index)] || "";
    if (!color) return;
    this.activeIndex = this._clampIndex(index);
    this.open = false;
    this.dispatchEvent(
      new CustomEvent("wavy-colorpicker-color-selected", {
        detail: {
          color,
          mode: this.mode === "highlight" ? "highlight" : "text"
        },
        bubbles: true,
        composed: true
      })
    );
  }

  _clampIndex(index) {
    const normalized = Number.isFinite(index) ? Math.trunc(index) : 0;
    return Math.max(0, Math.min(WAVY_COLOR_PALETTE.length - 1, normalized));
  }
}

if (!customElements.get("wavy-colorpicker-popover")) {
  customElements.define("wavy-colorpicker-popover", WavyColorpickerPopover);
}
