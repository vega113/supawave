import { LitElement, css, html, nothing } from "lit";

/**
 * <wavy-search-rail-card> — G-PORT-2 (#1111) per-digest card. Cloned
 * from the GWT digest layout (`DigestDomImpl.ui.xml` + `digest.css`) so
 * a freshly registered user sees the same shape on `?view=j2cl-root`
 * as on `?view=gwt`.
 *
 * GWT shape:
 *   .digest > .inner > .avatars(.avatar*) + .info(.pinIcon + .time + .msgs)
 *                    + .title + .snippet
 *
 * J2CL shape (this file):
 *   <article> > .inner > .avatars(.avatar*) + .info(.pin + time.ts + .msgs)
 *                      + h3.title + p.snippet
 *
 * The host carries `data-digest-card` so the G-PORT-2 Playwright
 * parity test can resolve cards on both views with one selector. The
 * sub-elements carry stable `data-digest-*` attributes (avatars,
 * title, snippet, msg-count, time) so the test reads the same six
 * children on each view without scraping shadow DOM.
 *
 * Public API (UNCHANGED from F-2.S3 / J-UI-1 / J-UI-7):
 * - waveId        — string, reflected to data-wave-id
 * - title         — string (defaults to "(no title)")
 * - snippet       — string
 * - postedAt      — string, e.g. "2m ago"
 * - postedAtIso   — string, ISO-8601 timestamp for tooltip
 * - msgCount      — number (defaults to 0)
 * - unreadCount   — number (defaults to 0); reflected to attribute
 * - pinned        — boolean
 * - authors       — string (comma-separated display names)
 * - selected      — boolean (reflects aria-current on inner article)
 *
 * Methods preserved: firePulse(). Events preserved:
 * wavy-search-rail-card-selected.
 *
 * The class names `.avatar`, `.avatar.more`, `.badge.unread`, `.pin`,
 * `h3.title`, `p.snippet`, `time.ts`, and `<article>` are all preserved
 * verbatim because the existing j2cl/lit unit suite asserts against them.
 */
export class WavySearchRailCard extends LitElement {
  static properties = {
    waveId: { type: String, attribute: "data-wave-id", reflect: true },
    title: { type: String },
    snippet: { type: String },
    postedAt: { type: String, attribute: "posted-at" },
    postedAtIso: { type: String, attribute: "posted-at-iso" },
    msgCount: { type: Number, attribute: "msg-count" },
    unreadCount: { type: Number, attribute: "unread-count", reflect: true },
    pinned: { type: Boolean, reflect: true },
    authors: { type: String },
    selected: { type: Boolean, reflect: true }
  };

  static styles = css`
    :host {
      display: block;
      box-sizing: border-box;
      /* G-PORT-2: clone GWT digest.css padding (10px 12px 10px 8px),
       * white background, hairline bottom border, pointer cursor. The
       * existing V-5 token plumbing still wins when callers set them. */
      padding: var(--wavy-rail-card-padding-y, 10px)
        var(--wavy-rail-card-padding-x, 12px) var(--wavy-rail-card-padding-y, 10px)
        var(--wavy-rail-card-padding-left, 8px);
      margin-bottom: var(--wavy-rail-card-gap, 0);
      background: var(--wavy-rail-card-bg, #fff);
      color: var(--wavy-rail-card-color, #718096);
      border-bottom: 1px solid var(--wavy-rail-card-divider, #e2e8f0);
      cursor: pointer;
      transition: background 0.15s ease;
    }
    :host(:hover) {
      background-color: var(--wavy-rail-card-hover-bg, rgba(144, 224, 239, 0.12));
    }
    :host([selected]) {
      background-color: var(--wavy-rail-card-selected-bg, #0077b6);
      color: var(--wavy-rail-card-selected-color, #fff);
    }
    :host([data-pulse="ring"]) {
      box-shadow: var(--wavy-pulse-ring, 0 0 0 4px rgba(34, 211, 238, 0.22));
    }
    /* J-UI-7 read-state hook (preserved). */
    :host([unread-count="0"]) {
      --wavy-rail-card-read: 1;
    }

    article {
      display: block;
      outline: none;
    }
    article:focus-visible {
      outline: 2px solid var(--wavy-rail-card-focus-ring, #0077b6);
      outline-offset: -2px;
    }

    /* GWT: .inner — line-height 16px, exactly two text lines (32px tall),
     * but we let it grow to fit the title + snippet rows. The original
     * GWT card limits to 32px because it floats avatars left and the
     * info right; we keep the float layout for parity but allow wrap. */
    .inner {
      line-height: 16px;
      min-height: 32px;
      overflow: hidden;
    }

    /* GWT: .avatars — float left, width 108px (3 * (32+4)). */
    .avatars {
      float: left;
      width: 108px;
      margin-right: 1em;
      display: inline-flex;
    }

    /* GWT: .avatar — 30px round, hairline border. We render initials
     * inside since profile image URLs require a back-end fetch the
     * SSR'd path doesn't carry. */
    .avatar {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      height: 30px;
      width: 30px;
      margin-right: 4px;
      border: 1.5px solid var(--wavy-rail-avatar-border, #e2e8f0);
      border-radius: 50%;
      background: var(--wavy-rail-avatar-bg, #f8fafc);
      color: var(--wavy-rail-avatar-color, #1a202c);
      font-size: 11px;
      font-weight: 600;
      line-height: 1;
    }
    .avatar + .avatar {
      margin-left: -6px;
    }
    .avatar.more {
      color: var(--wavy-text-muted, #718096);
    }

    /* GWT: .info — float right with timestamp, msg counts, optional pin. */
    .info {
      float: right;
      margin-left: 1em;
      text-align: right;
      font-size: 11px;
      color: inherit;
    }
    :host([selected]) .info {
      color: rgba(255, 255, 255, 0.8);
    }
    .pin {
      display: inline;
      font-size: 11px;
      color: var(--wavy-rail-pin-color, #888);
      margin-right: 4px;
      vertical-align: middle;
    }
    time.ts {
      font-size: 11px;
      color: inherit;
    }
    .msgs {
      font-size: 11px;
      color: inherit;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin-left: 6px;
      vertical-align: middle;
    }

    /* GWT: .unreadCount — teal pill (#00b4d8) with bold count. We keep
     * the .badge.unread selector for the existing tests, but the
     * styling matches GWT digest.css. */
    .badge.unread {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 18px;
      height: 16px;
      padding: 1px 8px;
      border-radius: 10px;
      background-color: var(--wavy-rail-unread-bg, #00b4d8);
      color: var(--wavy-rail-unread-color, #1a202c);
      font-size: 11px;
      font-weight: 600;
    }

    /* GWT: .title — bold, ellipsised. */
    h3.title {
      margin: 0;
      font: inherit;
      font-weight: bold;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      color: var(--wavy-rail-title-color, #1a202c);
      font-size: 13px;
    }
    /* J-UI-7: title bolds when unread (mirroring GWT's .unread class). */
    :host(:not([unread-count="0"])) h3.title {
      font-weight: 600;
      color: var(--wavy-rail-title-unread-color, #1a202c);
    }
    :host([selected]) h3.title {
      color: #fff;
    }

    /* GWT: .snippet — small, ellipsised. We keep the 3-line clamp
     * the existing test asserts. */
    p.snippet {
      margin: 0;
      color: var(--wavy-rail-snippet-color, #718096);
      font-size: 12px;
      display: -webkit-box;
      -webkit-line-clamp: 3;
      line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    :host([selected]) p.snippet {
      color: rgba(255, 255, 255, 0.7);
    }
  `;

  constructor() {
    super();
    this.waveId = "";
    this.title = "";
    this.snippet = "";
    this.postedAt = "";
    this.postedAtIso = "";
    this.msgCount = 0;
    this.unreadCount = 0;
    this.pinned = false;
    this.authors = "";
    this.selected = false;
  }

  connectedCallback() {
    super.connectedCallback();
    // G-PORT-2: tag the host with the parity selector. Reflective
    // properties cannot be used because the attribute is constant — we
    // set it once on connection.
    if (!this.hasAttribute("data-digest-card")) {
      this.setAttribute("data-digest-card", "");
    }
  }

  firePulse() {
    this.dataset.pulse = "ring";
    const dur =
      parseInt(
        getComputedStyle(this).getPropertyValue("--wavy-motion-pulse-duration") || "600",
        10
      ) || 600;
    if (this._pulseClearHandle) {
      clearTimeout(this._pulseClearHandle);
    }
    this._pulseClearHandle = setTimeout(() => {
      delete this.dataset.pulse;
      this._pulseClearHandle = 0;
    }, dur);
  }

  updated(changed) {
    const wasInitialized = this._initialUpdateComplete;
    this._initialUpdateComplete = true;
    if (!wasInitialized) {
      return;
    }
    if (changed.has("unreadCount")) {
      this.firePulse();
    }
  }

  _emitSelected() {
    this.dispatchEvent(
      new CustomEvent("wavy-search-rail-card-selected", {
        bubbles: true,
        composed: true,
        detail: { waveId: this.waveId }
      })
    );
  }

  _composeAriaLabel() {
    const title = this.title || "(no title)";
    const count = Math.max(0, this.unreadCount || 0);
    if (count <= 0) {
      return title + ". Read.";
    }
    return title + ". " + count + " unread.";
  }

  _initials(name) {
    if (!name) return "?";
    const parts = String(name).trim().split(/\s+/);
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  _authorList() {
    return (this.authors || "")
      .split(",")
      .map((a) => a.trim())
      .filter((a) => a.length > 0);
  }

  render() {
    const authors = this._authorList();
    const visible = authors.slice(0, 3);
    const overflow = Math.max(0, authors.length - 3);
    const unread = Math.max(0, this.unreadCount || 0);
    return html`
      <article
        @click=${this._emitSelected}
        @keydown=${(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            this._emitSelected();
          }
        }}
        tabindex="0"
        role="article"
        aria-label=${this._composeAriaLabel()}
        aria-current=${this.selected ? "true" : nothing}
      >
        <div class="inner">
          <div class="avatars" data-digest-avatars aria-label="Authors">
            ${visible.map(
              (name) =>
                html`<span class="avatar" data-initials=${this._initials(name)} title=${name}
                  >${this._initials(name)}</span
                >`
            )}
            ${overflow > 0
              ? html`<span class="avatar more" title="and ${overflow} more">+${overflow}</span>`
              : null}
          </div>
          <div class="info">
            ${this.pinned
              ? html`<span class="pin" aria-label="Pinned" title="Pinned">📌</span>`
              : null}
            ${this.postedAtIso
              ? html`<time
                  class="ts"
                  data-digest-time
                  datetime=${this.postedAtIso}
                  title=${this.postedAtIso}
                  >${this.postedAt || ""}</time
                >`
              : html`<time class="ts" data-digest-time>${this.postedAt || ""}</time>`}
            <span class="msgs" data-digest-msg-count aria-label="Message count">
              <span class="msg-total">${this.msgCount}</span>
              ${unread > 0
                ? html`<span class="badge unread" aria-label="${unread} unread">${unread}</span>`
                : null}
            </span>
          </div>
          <h3 class="title" data-digest-title>${this.title || "(no title)"}</h3>
          <p class="snippet" data-digest-snippet>${this.snippet || ""}</p>
        </div>
      </article>
    `;
  }
}

if (!customElements.get("wavy-search-rail-card")) {
  customElements.define("wavy-search-rail-card", WavySearchRailCard);
}
