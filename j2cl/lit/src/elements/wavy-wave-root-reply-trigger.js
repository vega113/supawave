import { LitElement, css, html } from "lit";
import { subscribe } from "../i18n/locale.js";
import { t } from "../i18n/t.js";

/**
 * <wavy-wave-root-reply-trigger> — F-3.S1 (#1038, R-5.1 step 5)
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
      box-sizing: border-box;
      display: block;
      padding: 4px 8px 8px;
    }
    :host([hidden]) {
      display: none;
    }
    .reply-box {
      align-items: center;
      box-sizing: border-box;
      display: flex;
      gap: 5px;
      min-height: 38px;
      width: 100%;
      margin: 0;
      overflow: hidden;
      padding: 6px 10px;
      border: 1.5px dashed #e2e8f0;
      border-radius: var(--wavy-radius-md, 8px);
      background: #f0f4f8;
      color: #718096;
      cursor: pointer;
      font: var(--wavy-type-body, 13px / 1.35 Arial, sans-serif);
      font-style: italic;
      text-align: left;
      transition: border-color 160ms ease, background 160ms ease, color 160ms ease;
    }
    .reply-box:hover {
      border-color: var(--wavy-signal-cyan, #00b4d8);
      background: rgba(0, 180, 216, 0.04);
      color: var(--wavy-signal-blue, #0077b6);
    }
    .reply-box:focus-visible {
      outline: none;
      border-color: var(--wavy-signal-cyan, #00b4d8);
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px rgba(0, 119, 182, 0.16));
      color: var(--wavy-signal-blue, #0077b6);
    }
    .avatar {
      flex: 0 0 auto;
      width: 24px;
      height: 24px;
      padding: 0;
      border: 1.5px solid #e2e8f0;
      border-radius: 50%;
      background:
        radial-gradient(circle at 50% 35%, #b7c3d0 0 22%, transparent 23%),
        radial-gradient(circle at 50% 82%, #b7c3d0 0 36%, transparent 37%),
        #f8fafc;
      opacity: 0.5;
    }
  `;

  constructor() {
    super();
    this.waveId = "";
    this.hidden = false;
    this._unsubscribeLocale = null;
  }

  connectedCallback() {
    super.connectedCallback();
    this._unsubscribeLocale = subscribe(() => this.requestUpdate());
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    if (this._unsubscribeLocale) {
      this._unsubscribeLocale();
      this._unsubscribeLocale = null;
    }
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
    const label = t("rootReply.label", "Click here to reply");
    return html`
      <button
        type="button"
        class="reply-box"
        data-wave-root-reply-trigger
        data-wave-root-reply-box
        aria-label=${label}
        title=${label}
        @click=${this._onClick}
      >
        <span class="avatar" data-wave-root-reply-avatar aria-hidden="true"></span>
        <span>${label}</span>
      </button>
    `;
  }
}

if (!customElements.get("wavy-wave-root-reply-trigger")) {
  customElements.define("wavy-wave-root-reply-trigger", WavyWaveRootReplyTrigger);
}
