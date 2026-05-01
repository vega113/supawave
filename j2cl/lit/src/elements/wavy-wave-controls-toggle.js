import { LitElement, css, html } from "lit";

/**
 * <wavy-wave-controls-toggle> — F-2.S4 (#1048, J.3) toggle button that
 * flips the wave panel into compact mode (hides the secondary wave
 * controls) and back.
 *
 * Properties:
 *   - pressed: boolean — when true, controls are hidden ("compact mode").
 *
 * Layout:
 *   - The host is fixed-position in the top-right of the viewport,
 *     offset to the left of the nav-drawer-toggle on mobile (escapes
 *     the shell-root grid so it sits in the visible header band rather
 *     than below the min-height:100vh shell).
 *
 * A11y:
 *   - The inner native &lt;button&gt; carries the role + keyboard
 *     activation (Enter/Space). The host element does NOT carry a role
 *     or tabindex, so AT does not announce a nested-button antipattern
 *     and the single tab stop is the inner button.
 *   - aria-pressed on the inner button reflects `pressed`, and
 *     aria-label on the inner button flips between "Hide wave controls"
 *     and "Show wave controls" depending on `pressed`.
 *
 * Events emitted (CustomEvent, bubbles + composed):
 *   - `wavy-wave-controls-toggled` — `{detail: {pressed}}` after the
 *     toggle. The renderer / shell consumer wires this to its compact
 *     mode CSS class.
 */
export class WavyWaveControlsToggle extends LitElement {
  static properties = {
    pressed: { type: Boolean, reflect: true }
  };

  static styles = css`
    :host {
      position: fixed;
      top: var(--wavy-spacing-3, 12px);
      right: var(--wavy-spacing-5, 24px);
      z-index: 90;
      display: inline-flex;
    }
    /* Shift further left on mobile to clear the nav-drawer-toggle. */
    @media (max-width: 860px) {
      :host {
        right: calc(var(--wavy-spacing-3, 12px) + var(--wavy-spacing-5, 24px) + var(--wavy-spacing-2, 8px));
      }
    }
    button {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: var(--wavy-spacing-5, 24px);
      height: var(--wavy-spacing-5, 24px);
      padding: 0;
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      border: 0;
      cursor: pointer;
      border-radius: var(--wavy-radius-pill, 9999px);
      transition: color var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    button:hover {
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
    }
    :host([pressed]) button {
      color: var(--wavy-signal-cyan, #22d3ee);
    }
    button:focus-visible {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      outline: none;
    }
    svg {
      width: 18px;
      height: 18px;
      display: block;
    }
  `;

  constructor() {
    super();
    this.pressed = false;
  }

  _onClick() {
    this._toggle();
  }

  _toggle() {
    this.pressed = !this.pressed;
    this.dispatchEvent(
      new CustomEvent("wavy-wave-controls-toggled", {
        bubbles: true,
        composed: true,
        detail: { pressed: this.pressed }
      })
    );
  }

  render() {
    // Inner native <button> owns the role + keyboard activation
    // (Enter / Space). The host carries no role/tabindex so AT does
    // not announce a nested-button antipattern; the toggle's
    // pressed/label state lives on the inner button.
    const label = this.pressed ? "Show wave controls" : "Hide wave controls";
    return html`
      <button
        type="button"
        aria-pressed=${this.pressed ? "true" : "false"}
        aria-label=${label}
        @click=${this._onClick}
      >
        ${this.pressed ? this._showIcon() : this._hideIcon()}
      </button>
    `;
  }

  _hideIcon() {
    return html`
      <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d="M5 7h14M5 12h14M5 17h14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path>
        <path d="M9 5v4M15 10v4M11 15v4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path>
      </svg>
    `;
  }

  _showIcon() {
    return html`
      <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
        <path d="M6 7h12M6 12h12M6 17h12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path>
        <path d="M9 9l3-3 3 3M9 15l3 3 3-3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"></path>
      </svg>
    `;
  }
}

if (!customElements.get("wavy-wave-controls-toggle")) {
  customElements.define(
    "wavy-wave-controls-toggle",
    WavyWaveControlsToggle
  );
}
