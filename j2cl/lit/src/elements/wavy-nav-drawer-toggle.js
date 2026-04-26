import { LitElement, css, html } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";

/**
 * <wavy-nav-drawer-toggle> — F-2.S4 (#1048, J.4) hamburger / close
 * toggle button for the mobile navigation drawer.
 *
 * Properties:
 *   - open: boolean — when true, the drawer is open (button shows close
 *     glyph + aria-label "Close navigation drawer"). When false, the
 *     button shows hamburger + aria-label "Open navigation drawer".
 *
 * Layout:
 *   - The host is fixed-position in the top-right of the viewport
 *     (escapes the shell-root grid so it sits in the visible header
 *     band rather than below the min-height:100vh shell).
 *
 * A11y:
 *   - role="button", aria-expanded reflects `open`,
 *     aria-controls is preserved from a host attribute (renderer sets
 *     it to the drawer element's id).
 *   - Mobile-first visibility — hidden on desktop via @media rule on
 *     the host. Tests assert the structural contract regardless.
 *
 * Events emitted (CustomEvent, bubbles + composed):
 *   - `wavy-nav-drawer-toggled` — `{detail: {open}}` after the toggle.
 */
export class WavyNavDrawerToggle extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true }
  };

  static styles = css`
    :host {
      position: fixed;
      top: var(--wavy-spacing-3, 12px);
      right: var(--wavy-spacing-3, 12px);
      z-index: 90;
      display: inline-flex;
    }
    @media (min-width: 861px) {
      :host {
        display: none;
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
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 0;
      cursor: pointer;
      border-radius: var(--wavy-radius-pill, 9999px);
      transition: color var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    button:focus-visible {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      outline: none;
    }
  `;

  constructor() {
    super();
    this.open = false;
  }

  _onClick() {
    this._toggle();
  }

  _toggle() {
    this.open = !this.open;
    this.dispatchEvent(
      new CustomEvent("wavy-nav-drawer-toggled", {
        bubbles: true,
        composed: true,
        detail: { open: this.open }
      })
    );
  }

  render() {
    // Inner native <button> owns role + keyboard activation. The host
    // forwards its caller-set `aria-controls` attribute onto the inner
    // button so AT exposes the drawer relationship from the focusable
    // element. The host carries no role/tabindex (no nested-button).
    const ariaControls = this.getAttribute("aria-controls") || undefined;
    const glyph = this.open ? "✕" : "☰";
    const label = this.open ? "Close navigation drawer" : "Open navigation drawer";
    return html`
      <button
        type="button"
        aria-expanded=${this.open ? "true" : "false"}
        aria-label=${label}
        aria-controls=${ifDefined(ariaControls)}
        @click=${this._onClick}
      >
        <span aria-hidden="true">${glyph}</span>
      </button>
    `;
  }
}

if (!customElements.get("wavy-nav-drawer-toggle")) {
  customElements.define("wavy-nav-drawer-toggle", WavyNavDrawerToggle);
}
