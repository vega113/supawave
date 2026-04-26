import { LitElement, css, html } from "lit";

/**
 * <wavy-blip-card> — F-0 (#1035) recipe for a single blip card in the
 * read surface. F-2 (#1037) consumes this element to render each blip
 * in the J2CL read surface; the named slots provide the data + the
 * `blip-extension` plugin slot point (M.2).
 *
 * Plugin-slot context (per docs/j2cl-plugin-slots.md):
 *   - data attributes (string-only, declarative): data-blip-id,
 *     data-wave-id, data-blip-author, data-blip-is-author.
 *   - JS property `blipView` returns a frozen read-only snapshot of
 *     the blip view (lazy on read; mutation throws under strict mode).
 */
export class WavyBlipCard extends LitElement {
  static properties = {
    // F-0 (#1035): plugin slot-context attributes use the data-* form
    // (per docs/j2cl-plugin-slots.md) so plugins read them via
    // `host.dataset.blipId` / `dataset.waveId` without a JS API surface.
    blipId: { type: String, attribute: "data-blip-id", reflect: true },
    waveId: { type: String, attribute: "data-wave-id", reflect: true },
    authorName: { type: String, attribute: "author-name" },
    postedAt: { type: String, attribute: "posted-at" },
    isAuthor: { type: Boolean, attribute: "is-author", reflect: true },
    focused: { type: Boolean, reflect: true },
    unread: { type: Boolean, reflect: true },
    livePulse: { type: Boolean, attribute: "live-pulse", reflect: true }
  };

  static styles = css`
    :host {
      display: block;
      box-sizing: border-box;
      padding: var(--wavy-spacing-4, 16px);
      margin-bottom: var(--wavy-spacing-3, 12px);
      background: var(--wavy-bg-surface, #11192a);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border-radius: var(--wavy-radius-card, 12px);
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      transition: box-shadow var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
      font: var(--wavy-type-body, 0.9375rem / 1.55 sans-serif);
    }
    :host([focused]) {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      border-color: var(--wavy-signal-cyan, #22d3ee);
    }
    :host([unread])::before {
      content: "";
      display: inline-block;
      width: 8px;
      height: 8px;
      border-radius: var(--wavy-radius-pill, 9999px);
      background: var(--wavy-signal-cyan, #22d3ee);
      margin-right: var(--wavy-spacing-2, 8px);
      vertical-align: middle;
    }
    :host([live-pulse]) {
      animation: wavy-pulse var(--wavy-motion-pulse-duration, 600ms)
        var(--wavy-easing-pulse, cubic-bezier(0.32, 0.72, 0.32, 1)) 1;
    }
    @keyframes wavy-pulse {
      0% {
        box-shadow: 0 0 0 0 var(--wavy-signal-cyan-soft, rgba(34, 211, 238, 0.22));
      }
      70% {
        box-shadow: var(--wavy-pulse-ring, 0 0 0 4px rgba(34, 211, 238, 0.22));
      }
      100% {
        box-shadow: 0 0 0 0 transparent;
      }
    }
    .author {
      font: var(--wavy-type-h3, 1.0625rem / 1.35 sans-serif);
      font-weight: 600;
      margin-right: var(--wavy-spacing-2, 8px);
    }
    .timestamp {
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
    }
    .body {
      margin-top: var(--wavy-spacing-2, 8px);
    }
    /* The blip-extension slot wrapper — empty in production, dashed
     * outline with label when the document is in design preview. */
    :host(:has([slot="blip-extension"])) .ext-slot-wrapper {
      display: block;
      margin-top: var(--wavy-spacing-3, 12px);
    }
    :host-context([data-wavy-design-preview]) .ext-slot-wrapper {
      display: block;
      margin-top: var(--wavy-spacing-3, 12px);
      padding: var(--wavy-spacing-2, 8px);
      border: 1px dashed var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-card, 12px);
      position: relative;
    }
    :host-context([data-wavy-design-preview]) .ext-slot-wrapper::before {
      content: "blip-extension";
      position: absolute;
      top: -10px;
      left: 8px;
      padding: 0 4px;
      background: var(--wavy-bg-surface, #11192a);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
    }
    /* Plugin opt-out: a slotted root with data-wavy-plugin-untheme
     * suppresses the inner glow border so the plugin paints its own. */
    :host(:has([slot="blip-extension"][data-wavy-plugin-untheme])) {
      border-color: transparent;
    }
  `;

  constructor() {
    super();
    this.blipId = "";
    this.waveId = "";
    this.authorName = "";
    this.postedAt = "";
    this.isAuthor = false;
    this.focused = false;
    this.unread = false;
    this.livePulse = false;
  }

  /** Lazy frozen read-only snapshot for plugin consumers. */
  get blipView() {
    return Object.freeze({
      id: this.blipId,
      waveId: this.waveId,
      authorName: this.authorName,
      postedAt: this.postedAt,
      isAuthor: this.isAuthor
    });
  }

  /** Reflect the four data attributes for plugin discoverability. */
  updated(changed) {
    if (changed.has("authorName")) {
      this._reflectDataAttr("data-blip-author", this.authorName);
    }
    if (changed.has("isAuthor")) {
      this._reflectDataAttr("data-blip-is-author", this.isAuthor ? "true" : "false");
    }
  }

  _reflectDataAttr(name, value) {
    if (value == null || value === "") {
      this.removeAttribute(name);
    } else {
      this.setAttribute(name, String(value));
    }
  }

  /** Trigger a single live-update pulse. Restart pattern: remove
   * attribute, force layout, re-add — so back-to-back pulses animate. */
  firePulse() {
    this.removeAttribute("live-pulse");
    void this.offsetWidth;
    this.setAttribute("live-pulse", "");
  }

  render() {
    return html`
      <article role="article" aria-labelledby="author">
        <header>
          <span class="author" id="author">${this.authorName || ""}</span>
          <time class="timestamp">${this.postedAt || ""}</time>
        </header>
        <div class="body"><slot></slot></div>
        <div class="ext-slot-wrapper">
          <slot name="blip-extension"></slot>
        </div>
        <div class="metadata"><slot name="metadata"></slot></div>
        <div class="reactions"><slot name="reactions"></slot></div>
      </article>
    `;
  }
}

if (!customElements.get("wavy-blip-card")) {
  customElements.define("wavy-blip-card", WavyBlipCard);
}
