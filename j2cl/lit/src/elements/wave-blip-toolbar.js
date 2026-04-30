import { LitElement, css, html } from "lit";

/**
 * <wave-blip-toolbar> — F-2 (#1037, R-3.1) per-blip action toolbar that
 * surfaces Reply (F.4), Edit (F.5), Link (F.7), Delete (F.6), and the
 * overflow trigger (future F-3 actions).
 *
 * F-3.S4 (#1038, R-5.6): the Delete button now emits a dedicated
 * `wave-blip-toolbar-delete` event so the compose surface can route
 * through a styled wavy confirm dialog and the new
 * `J2clRichContentDeltaFactory.blipDeleteRequest` factory method.
 *
 * V-4 (#1102): the toolbar visual chrome was upgraded to match
 * `02-open-wave-threaded-reading.svg` — a single pill-shaped surface
 * with vertical dividers between glyph-prefixed action labels. The
 * focused-blip variant repaints with a cyan-soft fill via
 * `data-variant="focused"` set on this element by the parent `<wave-blip>`.
 *
 * The toolbar is rendered as a sibling to `<wave-blip>`'s `.body`
 * container (not inside it), so the task-completed strikethrough that
 * applies to `.body` does not propagate to the action buttons. Visibility
 * is driven by the parent's :focus-within + :hover rules; the toolbar
 * itself just renders the buttons.
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
      align-items: center;
      gap: 4px;
      padding: 0;
      background: transparent;
      color: var(--wavy-blip-toolbar-fg, var(--wavy-text-body, #fff));
      border: 0;
      border-radius: 0;
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
    }
    :host([data-variant="focused"]) {
      background: transparent;
    }
    button {
      background: transparent;
      color: inherit;
      border: 0;
      font: inherit;
      width: 28px;
      height: 28px;
      padding: 0;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
      border-radius: var(--wavy-toolbar-tile-radius, 4px);
      transition: background-color var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    button:not(:last-of-type) {
      border-right: 0;
    }
    button:hover {
      background: var(--wavy-toolbar-tile-hover-bg, rgba(255, 255, 255, 0.08));
    }
    button:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }
    .glyph {
      font-size: 14px;
      line-height: 1;
    }
    .label {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
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
        <span class="glyph" aria-hidden="true">↩</span><span class="label">Reply</span>
      </button>
      <button
        type="button"
        data-toolbar-action="edit"
        aria-label="Edit this blip"
        @click=${() => this._emit("wave-blip-toolbar-edit")}
      >
        <span class="glyph" aria-hidden="true">✎</span><span class="label">Edit</span>
      </button>
      <button
        type="button"
        data-toolbar-action="delete"
        aria-label="Delete this blip"
        @click=${() => this._emit("wave-blip-toolbar-delete")}
      >
        <span class="glyph" aria-hidden="true">✕</span><span class="label">Delete</span>
      </button>
      <button
        type="button"
        data-toolbar-action="link"
        aria-label="Copy permalink to this blip"
        @click=${() => this._emit("wave-blip-toolbar-link")}
      >
        <span class="glyph" aria-hidden="true">§</span><span class="label">Link</span>
      </button>
      <button
        type="button"
        data-toolbar-action="overflow"
        aria-label="More blip actions"
        aria-haspopup="menu"
        @click=${() => this._emit("wave-blip-toolbar-overflow")}
      >
        <span class="glyph" aria-hidden="true">⋯</span><span class="label">More</span>
      </button>
    `;
  }
}

if (!customElements.get("wave-blip-toolbar")) {
  customElements.define("wave-blip-toolbar", WaveBlipToolbar);
}
