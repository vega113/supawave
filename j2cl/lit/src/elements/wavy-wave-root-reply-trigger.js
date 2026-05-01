import { LitElement, css, html } from "lit";

/**
 * <wavy-wave-root-reply-trigger> — F-3.S1 (#1038, R-5.1 step 5) compact
 * bottom-of-wave reply affordance (J.1 from the GWT inventory).
 *
 * Mounted at the bottom of the read surface. Clicking dispatches a
 * `wave-root-reply-requested` CustomEvent with `{detail: {waveId}}`.
 * The compose view listens and mounts a `<wavy-composer>` (no
 * replyTargetBlipId) at the bottom of the read surface.
 *
 * Public API:
 * - waveId (String) — the active wave id (read on click).
 * - hidden (Boolean) — when true, collapses to display:none (e.g. when
 *   a wave-root composer is already mounted).
 */
export class WavyWaveRootReplyTrigger extends LitElement {
  static properties = {
    waveId: { type: String, attribute: "wave-id" },
    hidden: { type: Boolean, reflect: true }
  };

  static styles = css`
    :host {
      display: inline-flex;
      padding: var(--wavy-spacing-1, 4px) 0;
    }
    :host([hidden]) {
      display: none;
    }
    button {
      width: 24px;
      height: 24px;
      padding: 0;
      border-radius: var(--wavy-radius-pill, 999px);
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.24));
      background: var(--wavy-surface-raised, rgba(255, 255, 255, 0.92));
      color: var(--wavy-signal-cyan, #0077b6);
      font: var(--wavy-type-label, 0.875rem / 1 sans-serif);
      font-weight: 700;
      cursor: pointer;
      text-align: center;
    }
    button:hover {
      border-color: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
    }
    button:focus-visible {
      outline: none;
      border-color: var(--wavy-signal-cyan, #22d3ee);
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
    }
  `;

  constructor() {
    super();
    this.waveId = "";
    this.hidden = false;
  }

  _onClick(event) {
    event.preventDefault();
    this.dispatchEvent(
      new CustomEvent("wave-root-reply-requested", {
        detail: { waveId: this.waveId },
        bubbles: true,
        composed: true
      })
    );
  }

  render() {
    return html`
      <button
        type="button"
        data-wave-root-reply-trigger
        aria-label="Reply to the wave"
        @click=${this._onClick}
      >
        +
      </button>
    `;
  }
}

if (!customElements.get("wavy-wave-root-reply-trigger")) {
  customElements.define("wavy-wave-root-reply-trigger", WavyWaveRootReplyTrigger);
}
