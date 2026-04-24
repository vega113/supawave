import { LitElement, css, html } from "lit";

let activePicker = null;

export class ReactionPickerPopover extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    blipId: { type: String, attribute: "blip-id" },
    emojis: { type: Array },
    focusTargetId: { type: String, attribute: "focus-target-id" }
  };

  static styles = css`
    .picker {
      display: flex;
      gap: 6px;
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 14px;
      padding: 8px;
      background: var(--shell-color-surface-overlay, #fff);
      box-shadow: var(--shell-shadow-overlay, 0 16px 44px rgb(10 38 64 / 18%));
    }

    button {
      border: 0;
      border-radius: 10px;
      padding: 8px;
      background: transparent;
      font: inherit;
    }

    button:focus {
      outline: 2px solid var(--shell-color-accent-focus, #206ea6);
      outline-offset: 2px;
    }
  `;

  constructor() {
    super();
    this.open = false;
    this.blipId = "";
    this.emojis = [];
    this.focusTargetId = "";
    this.addEventListener("keydown", this.onKeyDown);
  }

  disconnectedCallback() {
    if (activePicker === this) {
      activePicker = null;
    }
    super.disconnectedCallback();
  }

  updated(changed) {
    if (changed.has("open") && this.open) {
      if (activePicker && activePicker !== this) {
        activePicker.close("superseded");
      }
      activePicker = this;
      queueMicrotask(() => this.renderRoot.querySelector("button")?.focus());
    }
  }

  render() {
    if (!this.open) {
      return "";
    }
    return html`
      <div
        class="picker"
        role="menu"
        aria-label="Choose reaction"
        aria-orientation="horizontal"
      >
        ${this.safeEmojis().map(
          emoji => html`
            <button
              type="button"
              role="menuitem"
              data-emoji=${emoji}
              @click=${() => this.pick(emoji)}
            >
              ${emoji}
            </button>
          `
        )}
      </div>
    `;
  }

  onKeyDown = (event) => {
    if (!this.open) {
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      this.close("escape");
      return;
    }
    if (event.key === "ArrowRight") {
      event.preventDefault();
      this.focusByOffset(1);
      return;
    }
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      this.focusByOffset(-1);
      return;
    }
    if (event.key === "Home") {
      event.preventDefault();
      this.focusButton(0);
      return;
    }
    if (event.key === "End") {
      event.preventDefault();
      this.focusButton(this.buttons().length - 1);
    }
  };

  pick(emoji) {
    this.dispatchEvent(
      new CustomEvent("reaction-pick", {
        detail: { blipId: this.blipId, emoji },
        bubbles: true,
        composed: true
      })
    );
  }

  close(reason) {
    this.open = false;
    if (activePicker === this) {
      activePicker = null;
    }
    this.dispatchEvent(
      new CustomEvent("overlay-close", {
        detail: { reason, focusTargetId: this.focusTargetId },
        bubbles: true,
        composed: true
      })
    );
  }

  safeEmojis() {
    return Array.isArray(this.emojis) ? this.emojis : [];
  }

  focusByOffset(offset) {
    const buttons = this.buttons();
    if (buttons.length === 0) {
      return;
    }
    const currentIndex = buttons.indexOf(this.renderRoot.activeElement);
    const nextIndex =
      currentIndex < 0 ? 0 : (currentIndex + offset + buttons.length) % buttons.length;
    this.focusButton(nextIndex);
  }

  focusButton(index) {
    const buttons = this.buttons();
    if (buttons.length === 0) {
      return;
    }
    buttons[(index + buttons.length) % buttons.length].focus();
  }

  buttons() {
    return Array.from(this.renderRoot.querySelectorAll("button"));
  }
}

if (!customElements.get("reaction-picker-popover")) {
  customElements.define("reaction-picker-popover", ReactionPickerPopover);
}
