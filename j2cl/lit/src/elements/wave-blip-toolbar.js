import { LitElement, css, html } from "lit";

/**
 * <wave-blip-toolbar> — F-2 (#1037, R-3.1) per-blip action toolbar that
 * surfaces Reply (F.4), Edit (F.5), Link (F.7), and the overflow trigger
 * (F.6 Delete + future F-3 actions live behind it).
 *
 * The toolbar is structurally inside `<wave-blip>`'s `metadata` slot so
 * the F-0 `<wavy-blip-card>` recipe envelope stays in charge of focus +
 * unread + pulse. Visibility is driven by the parent's :focus-within
 * + :hover rules; the toolbar itself just renders the buttons.
 *
 * Each button emits its own event so the parent <wave-blip> can re-emit
 * the public `wave-blip-*-requested` events with the blip context.
 */
export class WaveBlipToolbar extends LitElement {
  static properties = {
    blipId: { type: String, attribute: "data-blip-id", reflect: true },
    waveId: { type: String, attribute: "data-wave-id", reflect: true }
  };

  static styles = css`
    :host {
      display: inline-flex;
      gap: var(--wavy-spacing-1, 4px);
      align-items: center;
    }
    button {
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      border: 1px solid transparent;
      border-radius: var(--wavy-radius-pill, 9999px);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      cursor: pointer;
      transition: color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        border-color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    button:hover {
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border-color: var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
    }
    button:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }
  `;

  constructor() {
    super();
    this.blipId = "";
    this.waveId = "";
  }

  _emit(eventName) {
    this.dispatchEvent(
      new CustomEvent(eventName, {
        bubbles: true,
        composed: true,
        detail: { blipId: this.blipId, waveId: this.waveId }
      })
    );
  }

  render() {
    return html`
      <button
        type="button"
        data-toolbar-action="reply"
        aria-label="Reply to this blip"
        @click=${() => this._emit("wave-blip-toolbar-reply")}
      >
        Reply
      </button>
      <button
        type="button"
        data-toolbar-action="edit"
        aria-label="Edit this blip"
        @click=${() => this._emit("wave-blip-toolbar-edit")}
      >
        Edit
      </button>
      <button
        type="button"
        data-toolbar-action="link"
        aria-label="Copy permalink to this blip"
        @click=${() => this._emit("wave-blip-toolbar-link")}
      >
        Link
      </button>
      <button
        type="button"
        data-toolbar-action="overflow"
        aria-label="More blip actions"
        aria-haspopup="menu"
        @click=${() => this._emit("wave-blip-toolbar-overflow")}
      >
        ⋯
      </button>
    `;
  }
}

if (!customElements.get("wave-blip-toolbar")) {
  customElements.define("wave-blip-toolbar", WaveBlipToolbar);
}
