import { LitElement, css, html } from "lit";

/**
 * <wavy-rail-panel> — F-0 (#1035) recipe for a right-rail panel
 * (saved searches, assistant, integrations status). F-2 (#1037)
 * mounts this for rail surfaces; the `rail-extension` slot is M.4.
 *
 * Plugin-slot context: data-active-wave-id, data-active-folder.
 */
export class WavyRailPanel extends LitElement {
  static properties = {
    panelTitle: { type: String, attribute: "panel-title" },
    collapsed: { type: Boolean, reflect: true },
    // F-0 (#1035): plugin slot-context attributes use the data-* form
    // (per docs/j2cl-plugin-slots.md) so plugins read them via
    // `host.dataset.activeWaveId` / `host.dataset.activeFolder`.
    activeWaveId: { type: String, attribute: "data-active-wave-id", reflect: true },
    activeFolder: { type: String, attribute: "data-active-folder", reflect: true }
  };

  static styles = css`
    :host {
      display: block;
      box-sizing: border-box;
      padding: var(--wavy-spacing-4, 16px);
      background: var(--wavy-bg-surface, #11192a);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border-radius: var(--wavy-radius-card, 12px);
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      margin-bottom: var(--wavy-spacing-3, 12px);
      font: var(--wavy-type-body, 0.9375rem / 1.55 sans-serif);
    }
    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--wavy-spacing-2, 8px);
    }
    h2 {
      margin: 0;
      font: var(--wavy-type-h3, 1.0625rem / 1.35 sans-serif);
      font-weight: 600;
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
    }
    button.toggle {
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      border: 0;
      cursor: pointer;
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
    }
    .body {
      display: grid;
      grid-template-rows: 1fr;
      transition: grid-template-rows var(--wavy-motion-collapse-duration, 240ms)
        var(--wavy-easing-collapse, cubic-bezier(0.4, 0, 0.2, 1));
      overflow: hidden;
      margin-top: var(--wavy-spacing-3, 12px);
    }
    :host([collapsed]) .body {
      grid-template-rows: 0fr;
    }
    .body > .body-inner {
      min-height: 0;
    }
    .ext-slot-wrapper {
      display: contents;
    }
    :host-context([data-wavy-design-preview]) .ext-slot-wrapper {
      display: block;
      margin-top: var(--wavy-spacing-3, 12px);
      padding: var(--wavy-spacing-2, 8px);
      border: 1px dashed var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-card, 12px);
      position: relative;
    }
    :host-context([data-wavy-design-preview]) .ext-slot-wrapper::before {
      content: "rail-extension";
      position: absolute;
      top: -10px;
      left: 8px;
      padding: 0 4px;
      background: var(--wavy-bg-surface, #11192a);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
    }
  `;

  constructor() {
    super();
    this.panelTitle = "";
    this.collapsed = false;
    this.activeWaveId = "";
    this.activeFolder = "";
  }

  toggleCollapsed() {
    this.collapsed = !this.collapsed;
  }

  render() {
    return html`
      <section
        role="region"
        aria-labelledby="panel-title"
        aria-expanded=${String(!this.collapsed)}
      >
        <header>
          <h2 id="panel-title">${this.panelTitle || ""}</h2>
          <span class="header-actions"><slot name="header-actions"></slot></span>
          <button
            class="toggle"
            type="button"
            aria-label=${this.collapsed ? "Expand panel" : "Collapse panel"}
            @click=${this.toggleCollapsed}
          >
            ${this.collapsed ? "+" : "–"}
          </button>
        </header>
        <div class="body">
          <div class="body-inner">
            <slot></slot>
            <div class="ext-slot-wrapper">
              <slot name="rail-extension"></slot>
            </div>
            <footer><slot name="footer"></slot></footer>
          </div>
        </div>
      </section>
    `;
  }
}

if (!customElements.get("wavy-rail-panel")) {
  customElements.define("wavy-rail-panel", WavyRailPanel);
}
