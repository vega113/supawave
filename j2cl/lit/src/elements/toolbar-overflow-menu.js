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
    this.addEventListener("toolbar-action", this.onToolbarAction);
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
    const action = this.actionFromEvent(event);
    if (!action) {
      return;
    }
    this.closeAndFocusTrigger();
    if (this.isToolbarButtonEvent(event)) {
      return;
    }
    this.dispatchEvent(
      new CustomEvent("toolbar-action", {
        detail: { action },
        bubbles: true,
        composed: true
      })
    );
  };

  onToolbarAction = (event) => {
    if (event.target === this) {
      return;
    }
    this.closeAndFocusTrigger();
  };

  closeAndFocusTrigger() {
    this.open = false;
    this.updateComplete.then(() => this.renderRoot.querySelector("button").focus());
  }

  actionFromEvent(event) {
    const path = typeof event.composedPath === "function" ? event.composedPath() : [];
    for (const item of path) {
      if (!(item instanceof Element)) {
        continue;
      }
      if (item.hasAttribute("data-action")) {
        return item.getAttribute("data-action") || "";
      }
      if (item.localName === "toolbar-button") {
        return item.action || item.getAttribute("action") || "";
      }
    }
    const actionEl =
      event.target instanceof Element ? event.target.closest("[data-action]") : null;
    return actionEl ? actionEl.getAttribute("data-action") || "" : "";
  }

  isToolbarButtonEvent(event) {
    const path = typeof event.composedPath === "function" ? event.composedPath() : [];
    return path.some(item => item instanceof Element && item.localName === "toolbar-button");
  }

  focusFirstItem() {
    const items = this.items();
    if (items.length > 0) {
      this.focusItem(items[0]);
    }
  }

  focusByOffset(offset) {
    const items = this.items();
    if (items.length === 0) {
      return;
    }
    const currentIndex = this.activeItemIndex(items);
    const nextIndex = currentIndex < 0
      ? 0
      : (currentIndex + offset + items.length) % items.length;
    this.focusItem(items[nextIndex]);
  }

  items() {
    // toolbar-button keeps its native button in shadow DOM, so selecting the host
    // adds it to the focus order without duplicating the internal control.
    return Array.from(this.querySelectorAll("toolbar-button,button,[href],[tabindex]")).filter(
      item => !this.isItemDisabled(item)
    );
  }

  activeItemIndex(items) {
    const activeElement = document.activeElement;
    return items.findIndex(item =>
      item === activeElement
        || item.contains(activeElement)
        || item.renderRoot?.activeElement === activeElement
        || item.shadowRoot?.activeElement === activeElement
    );
  }

  focusItem(item) {
    if (item.localName === "toolbar-button") {
      const button = item.renderRoot?.querySelector("button")
        || item.shadowRoot?.querySelector("button");
      if (button) {
        button.focus();
        return;
      }
    }
    item.focus();
  }

  isItemDisabled(item) {
    if (item.localName === "toolbar-button") {
      return Boolean(item.disabled) || item.hasAttribute("disabled");
    }
    return Boolean(item.disabled);
  }
}

if (!customElements.get("toolbar-overflow-menu")) {
  customElements.define("toolbar-overflow-menu", ToolbarOverflowMenu);
}
