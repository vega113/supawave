import { LitElement, css, html } from "lit";
import { t } from "../i18n/t.js";

/**
 * <wavy-floating-scroll-to-new> — F-2.S4 (#1048, J.2) floating "scroll to
 * new messages" pill rendered fixed at the bottom-right of the viewport.
 *
 * Mirrors the GWT NewBlipIndicatorPresenter contract (a11y + behavior):
 *   - The inner native &lt;button&gt; owns role + keyboard activation
 *     (Enter/Space) and carries aria-label="Scroll to new messages".
 *     The host element does NOT carry role/tabindex, so AT does not
 *     announce a nested-button antipattern and the single tab stop is
 *     the inner button. Visibility is driven solely by toggling the
 *     `hidden` HTML attribute on the host (no tabindex flipping, no
 *     screen-reader-only sibling label).
 *   - Click + Enter + Space → emit `wavy-scroll-to-new-clicked`.
 *
 * Properties:
 *   - count: number — how many new messages are below the fold. When 0,
 *     the host gets the `hidden` HTML attribute (which removes the
 *     inner button from the tab order via :host([hidden]) display:none).
 *     When > 0, the `hidden` attribute is removed and the pill reads
 *     "↓ {count} new" with the inner button's aria-label
 *     "Scroll to new messages".
 *
 * Events emitted (CustomEvent, bubbles + composed):
 *   - `wavy-scroll-to-new-clicked` — `{detail: {count}}`. The renderer /
 *     shell consumer wires this to the wave scroller. S4 does not own
 *     the actual scroll target — only the affordance.
 */
export class WavyFloatingScrollToNew extends LitElement {
  static properties = {
    count: { type: Number, reflect: true }
  };

  static styles = css`
    :host {
      position: fixed;
      bottom: var(--wavy-spacing-5, 24px);
      right: var(--wavy-spacing-5, 24px);
      z-index: 100;
      display: inline-flex;
    }
    :host([hidden]) {
      display: none;
    }
    button {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-4, 16px);
      background: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-bg-base, #0b1320);
      border: 0;
      border-radius: var(--wavy-radius-pill, 9999px);
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      font-weight: 600;
      cursor: pointer;
      transition: transform var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    button:hover {
      transform: translateY(-1px);
    }
    button:focus-visible {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      outline: none;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }
  `;

  constructor() {
    super();
    this.count = 0;
  }

  connectedCallback() {
    super.connectedCallback();
    this._syncVisibility();
  }

  willUpdate(changed) {
    if (changed.has("count")) {
      this._syncVisibility();
    }
  }

  _syncVisibility() {
    const visible = (this.count || 0) > 0;
    if (visible) {
      this.removeAttribute("hidden");
    } else {
      this.setAttribute("hidden", "");
    }
    // The inner native <button> owns role + keyboard activation
    // (Enter/Space). The host carries no role/tabindex so AT does
    // not announce a nested-button antipattern, and tab-stop is the
    // single inner button. The host still flips its own `hidden`
    // attribute so external CSS / parity fixtures key on it.
  }

  _onClick() {
    this._dispatchClick();
  }

  _dispatchClick() {
    this.dispatchEvent(
      new CustomEvent("wavy-scroll-to-new-clicked", {
        bubbles: true,
        composed: true,
        detail: { count: this.count || 0 }
      })
    );
  }

  render() {
    const c = this.count || 0;
    const label = t("scrollToNew.label", "Scroll to new messages");
    return html`
      <button
        type="button"
        aria-label=${label}
        title=${label}
        @click=${this._onClick}
      >
        <span aria-hidden="true">↓ ${c} new</span>
      </button>
    `;
  }
}

if (!customElements.get("wavy-floating-scroll-to-new")) {
  customElements.define(
    "wavy-floating-scroll-to-new",
    WavyFloatingScrollToNew
  );
}
