import { LitElement, css, html } from "lit";

/**
 * <wavy-edit-toolbar> — F-0 (#1035) recipe for the H.* edit toolbar.
 * F-3 (#1038) consumes this; the `toolbar-extension` slot is M.5.
 *
 * Plugin-slot context: data-active-selection (JSON-encoded selection
 * descriptor; debounced at ~60 fps so noisy updates from F-3 do not
 * thrash the DOM).
 */
export class WavyEditToolbar extends LitElement {
  static properties = {
    activeSelection: { type: String, attribute: "active-selection" }
  };

  static styles = css`
    :host {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-3, 12px);
      background: var(--wavy-bg-surface, #11192a);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border-radius: var(--wavy-radius-pill, 9999px);
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
    }
    .ext-slot-wrapper {
      display: contents;
    }
    :host-context([data-wavy-design-preview]) .ext-slot-wrapper {
      display: inline-flex;
      align-items: center;
      padding: 2px var(--wavy-spacing-2, 8px);
      border: 1px dashed var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-pill, 9999px);
      position: relative;
    }
    :host-context([data-wavy-design-preview]) .ext-slot-wrapper::before {
      content: "toolbar-extension";
      position: absolute;
      top: -12px;
      left: 6px;
      padding: 0 4px;
      background: var(--wavy-bg-surface, #11192a);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
    }
  `;

  constructor() {
    super();
    this.activeSelection = "";
    this._pendingSelection = null;
    this._frameHandle = 0;
  }

  updated(changed) {
    if (changed.has("activeSelection")) {
      this._scheduleAttrFlush(this.activeSelection);
    }
  }

  _scheduleAttrFlush(value) {
    this._pendingSelection = value;
    if (this._frameHandle) return;
    this._frameHandle = requestAnimationFrame(() => {
      this._frameHandle = 0;
      const v = this._pendingSelection;
      if (v == null || v === "") {
        this.removeAttribute("data-active-selection");
      } else {
        this.setAttribute("data-active-selection", v);
      }
    });
  }

  disconnectedCallback() {
    if (this._frameHandle) {
      cancelAnimationFrame(this._frameHandle);
      this._frameHandle = 0;
    }
    super.disconnectedCallback();
  }

  render() {
    return html`
      <div role="toolbar" aria-label="Formatting toolbar">
        <slot></slot>
        <span class="ext-slot-wrapper">
          <slot name="toolbar-extension"></slot>
        </span>
      </div>
    `;
  }
}

if (!customElements.get("wavy-edit-toolbar")) {
  customElements.define("wavy-edit-toolbar", WavyEditToolbar);
}
