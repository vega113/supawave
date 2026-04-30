import { LitElement, css, html } from "lit";

/**
 * <wavy-compose-card> — F-0 (#1035) recipe for the inline composer
 * surface. F-3 (#1038) consumes this for the compose surface; the
 * `compose-extension` slot is the M.3 plugin slot point.
 *
 * Plugin-slot context (per docs/j2cl-plugin-slots.md):
 *   - data attribute: data-reply-target-blip-id.
 *   - JS properties: composerState (frozen), activeSelection (frozen).
 */
export class WavyComposeCard extends LitElement {
  static properties = {
    focused: { type: Boolean, reflect: true },
    submitting: { type: Boolean, reflect: true },
    // F-0 (#1035): plugin slot-context attribute uses the data-* form
    // (per docs/j2cl-plugin-slots.md) so plugins read it via
    // `host.dataset.replyTargetBlipId`.
    replyTargetBlipId: {
      type: String,
      attribute: "data-reply-target-blip-id",
      reflect: true
    }
  };

  static styles = css`
    :host {
      display: block;
      box-sizing: border-box;
      padding: 4px;
      background: var(--wavy-bg-surface, #f8fafc);
      color: var(--wavy-text-body, #1a202c);
      border-radius: var(--wavy-radius-card, 4px);
      border: 1px solid var(--wavy-border-hairline, #e2e8f0);
      transition: box-shadow var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
      font: var(--wavy-type-body, 13px / 1.35 Arial, sans-serif);
    }
    :host([focused]) {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px rgba(0, 119, 182, 0.16));
      border-color: var(--wavy-signal-cyan, #0077b6);
    }
    .body {
      min-height: var(--wavy-spacing-7, 32px);
    }
    .toolbar-row {
      display: flex;
      align-items: center;
      gap: 0;
      margin-top: 6px;
      flex-wrap: wrap;
    }
    .ext-slot-wrapper {
      display: contents;
    }
    :host-context([data-wavy-design-preview]) .ext-slot-wrapper {
      display: inline-block;
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      border: 1px dashed var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-card, 12px);
      position: relative;
    }
    :host-context([data-wavy-design-preview]) .ext-slot-wrapper::before {
      content: "compose-extension";
      position: absolute;
      top: -10px;
      left: 6px;
      padding: 0 4px;
      background: var(--wavy-bg-surface, #11192a);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
    }
    .affordance-row {
      margin-top: var(--wavy-spacing-1, 4px);
      display: flex;
      justify-content: flex-end;
    }
    :host([submitting]) .affordance-row {
      pointer-events: none;
      opacity: 0.6;
    }
  `;

  constructor() {
    super();
    this.focused = false;
    this.submitting = false;
    this.replyTargetBlipId = "";
    this._composerState = Object.freeze({});
    this._activeSelection = Object.freeze({});
  }

  /** Frozen lazy snapshot of composer state for plugin consumers. */
  get composerState() {
    return this._composerState;
  }
  set composerState(value) {
    this._composerState = Object.freeze({ ...(value || {}) });
  }

  /** Frozen lazy snapshot of active selection for plugin consumers. */
  get activeSelection() {
    return this._activeSelection;
  }
  set activeSelection(value) {
    this._activeSelection = Object.freeze({ ...(value || {}) });
  }

  render() {
    return html`
      <section role="form">
        <div class="body"><slot></slot></div>
        <div class="toolbar-row">
          <slot name="toolbar"></slot>
          <span class="ext-slot-wrapper">
            <slot name="compose-extension"></slot>
          </span>
        </div>
        <div class="affordance-row">
          <slot name="affordance"></slot>
        </div>
      </section>
    `;
  }
}

if (!customElements.get("wavy-compose-card")) {
  customElements.define("wavy-compose-card", WavyComposeCard);
}
