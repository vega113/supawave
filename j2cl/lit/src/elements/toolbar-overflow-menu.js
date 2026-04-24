import { LitElement, css, html } from "lit";

export class ToolbarOverflowMenu extends LitElement {
  static properties = {
    label: { type: String },
    open: { type: Boolean, reflect: true }
  };

  static styles = css`
    :host {
      display: inline-block;
      position: relative;
    }

    button {
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 10px;
      padding: 7px 10px;
      background: #fff;
      font: inherit;
    }

    .menu {
      display: none;
      position: absolute;
      z-index: 3;
      min-width: 180px;
      padding: 8px;
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 12px;
      background: #fff;
      box-shadow: 0 12px 32px rgb(10 38 64 / 18%);
    }

    :host([open]) .menu {
      display: grid;
      gap: 4px;
    }
  `;

  constructor() {
    super();
    this.label = "More actions";
    this.open = false;
    this.addEventListener("keydown", this.onKeyDown);
    this.addEventListener("click", this.onLightDomClick);
  }

  render() {
    return html`
      <button
        type="button"
        aria-haspopup="true"
        aria-expanded=${this.open ? "true" : "false"}
        @click=${this.toggle}
        @keydown=${this.onTriggerKeyDown}
      >
        ${this.label}
      </button>
      <div class="menu"><slot></slot></div>
    `;
  }

  toggle() {
    this.open = !this.open;
    if (this.open) {
      this.updateComplete.then(() => this.focusFirstItem());
    }
  }

  onTriggerKeyDown(event) {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      this.open = true;
      this.updateComplete.then(() => this.focusFirstItem());
    }
  }

  onKeyDown = (event) => {
    if (!this.open) {
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      this.open = false;
      this.updateComplete.then(() => this.renderRoot.querySelector("button").focus());
      return;
    }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      this.focusByOffset(event.key === "ArrowDown" ? 1 : -1);
    }
  };

  onLightDomClick = (event) => {
    const actionEl =
      event.target instanceof Element ? event.target.closest("[data-action]") : null;
    const action = actionEl ? actionEl.getAttribute("data-action") : "";
    if (!action) {
      return;
    }
    this.dispatchEvent(
      new CustomEvent("toolbar-action", {
        detail: { action },
        bubbles: true,
        composed: true
      })
    );
    this.open = false;
    this.updateComplete.then(() => this.renderRoot.querySelector("button").focus());
  };

  focusFirstItem() {
    const items = this.items();
    if (items.length > 0) {
      items[0].focus();
    }
  }

  focusByOffset(offset) {
    const items = this.items();
    if (items.length === 0) {
      return;
    }
    const currentIndex = items.indexOf(document.activeElement);
    const nextIndex = currentIndex < 0
      ? 0
      : (currentIndex + offset + items.length) % items.length;
    items[nextIndex].focus();
  }

  items() {
    return Array.from(this.querySelectorAll("button,[href],[tabindex]")).filter(
      item => !item.disabled
    );
  }
}

if (!customElements.get("toolbar-overflow-menu")) {
  customElements.define("toolbar-overflow-menu", ToolbarOverflowMenu);
}
