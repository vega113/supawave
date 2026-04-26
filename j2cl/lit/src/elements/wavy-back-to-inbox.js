import { LitElement, css, html } from "lit";

/**
 * <wavy-back-to-inbox> — F-2.S4 (#1048, J.5) "Back to inbox" header
 * affordance shown on mobile breakpoints. Renders an anchor (so it
 * behaves like a normal link for AT + browser middle-click) but also
 * emits a `wavy-back-to-inbox-clicked` CustomEvent so the future router
 * can intercept (without preventing the default link traversal).
 *
 * Properties:
 *   - href: string — the URL to navigate to. Defaults to "#inbox".
 *
 * Layout:
 *   - The host is fixed-position in the top-left of the viewport (escapes
 *     the shell-root grid so it sits in the visible header band rather
 *     than below the min-height:100vh shell). Mobile-only via @media
 *     (min-width: 861px) hides on desktop.
 *
 * A11y:
 *   - aria-label="Back to inbox"
 *
 * Events emitted (CustomEvent, bubbles + composed, NOT cancelling):
 *   - `wavy-back-to-inbox-clicked` — no detail.
 */
export class WavyBackToInbox extends LitElement {
  static properties = {
    href: { type: String, reflect: true }
  };

  static styles = css`
    :host {
      position: fixed;
      top: var(--wavy-spacing-3, 12px);
      left: var(--wavy-spacing-3, 12px);
      z-index: 90;
      display: inline-flex;
    }
    @media (min-width: 861px) {
      :host {
        display: none;
      }
    }
    a {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-3, 12px);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      text-decoration: none;
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      border-radius: var(--wavy-radius-pill, 9999px);
      transition: color var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    a:hover {
      color: var(--wavy-signal-cyan, #22d3ee);
    }
    a:focus-visible {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      outline: none;
    }
  `;

  constructor() {
    super();
    this.href = "#inbox";
  }

  _onClick() {
    this.dispatchEvent(
      new CustomEvent("wavy-back-to-inbox-clicked", {
        bubbles: true,
        composed: true
      })
    );
  }

  render() {
    return html`
      <a
        href=${this.href || "#inbox"}
        aria-label="Back to inbox"
        @click=${this._onClick}
      >
        <span aria-hidden="true">←</span>
        <span>Inbox</span>
      </a>
    `;
  }
}

if (!customElements.get("wavy-back-to-inbox")) {
  customElements.define("wavy-back-to-inbox", WavyBackToInbox);
}
