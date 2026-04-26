import { LitElement, css, html } from "lit";

/**
 * <wavy-pulse-stage> — F-0 (#1035) demo helper that drives a single
 * live-update pulse on its child target. Used by the design preview
 * route to demonstrate the live-update pulse moment, and reusable by
 * F-3 as the canonical "fire a pulse on an arriving blip" helper so
 * F-3 doesn't re-implement the timing.
 *
 * Property:
 *   - target-selector: CSS selector applied within light-DOM children
 *     to find the element that should receive the `live-pulse`
 *     attribute. Defaults to `wavy-blip-card`.
 *
 * Public API:
 *   - firePulse(): toggles the `live-pulse` attribute on the resolved
 *     target using the restart pattern (remove → force layout → add)
 *     so back-to-back pulses animate every time. Removes the attribute
 *     after `--wavy-motion-pulse-duration` resolves at the test/runtime
 *     scope.
 */
export class WavyPulseStage extends LitElement {
  static properties = {
    targetSelector: { type: String, attribute: "target-selector" }
  };

  static styles = css`
    :host {
      display: block;
    }
    button {
      display: inline-block;
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-4, 16px);
      background: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-bg-base, #0b1320);
      border: 0;
      border-radius: var(--wavy-radius-pill, 9999px);
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      font-weight: 600;
      cursor: pointer;
      margin-bottom: var(--wavy-spacing-3, 12px);
    }
    button:focus-visible {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      outline: none;
    }
  `;

  constructor() {
    super();
    this.targetSelector = "wavy-blip-card";
    this._pulseTimer = null;
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    if (this._pulseTimer !== null) {
      clearTimeout(this._pulseTimer);
      this._pulseTimer = null;
    }
  }

  firePulse() {
    if (this._pulseTimer !== null) {
      clearTimeout(this._pulseTimer);
      this._pulseTimer = null;
    }
    const target = this.querySelector(this.targetSelector);
    if (!target) return;
    target.removeAttribute("live-pulse");
    void target.offsetWidth;
    target.setAttribute("live-pulse", "");
    const cs = getComputedStyle(this);
    const raw = cs.getPropertyValue("--wavy-motion-pulse-duration").trim();
    const ms = raw.endsWith("ms")
      ? parseFloat(raw)
      : raw.endsWith("s")
        ? parseFloat(raw) * 1000
        : 600;
    this._pulseTimer = setTimeout(() => {
      target.removeAttribute("live-pulse");
      this._pulseTimer = null;
    }, Math.max(ms, 1) + 50);
  }

  render() {
    return html`
      <button type="button" @click=${this.firePulse}>Fire pulse</button>
      <slot></slot>
    `;
  }
}

if (!customElements.get("wavy-pulse-stage")) {
  customElements.define("wavy-pulse-stage", WavyPulseStage);
}
