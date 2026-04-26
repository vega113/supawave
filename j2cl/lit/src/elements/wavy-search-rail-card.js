import { LitElement, css, html } from "lit";

/**
 * <wavy-search-rail-card> — F-2 (#1037, #1047 slice 3) per-digest card
 * inside the saved-search rail. One card per wave in the active
 * folder's search results.
 *
 * Inventory affordances covered (B.13–B.18 from the 2026-04-26 GWT
 * functional inventory):
 *
 * - B.13 multi-author avatar stack (overlapping, max 3 visible + +N)
 * - B.14 pinned indicator (cyan pin glyph when pinned attribute set)
 * - B.15 title
 * - B.16 snippet (multi-line truncated at 3 lines)
 * - B.17 msg count + unread badge with cyan signal-pulse on change
 *        (the pulse uses --wavy-pulse-ring composed by F-0)
 * - B.18 relative timestamp with full ISO datetime tooltip
 *
 * The card emits `wavy-search-rail-card-selected` on click (bubbles +
 * composed) so the rail / surrounding shell can route to the wave.
 *
 * Public API:
 * - waveId        — string, reflected to data-wave-id
 * - title         — string (defaults to "(no title)")
 * - snippet       — string
 * - postedAt      — string, e.g. "2m ago"
 * - postedAtIso   — string, ISO-8601 timestamp for tooltip
 * - msgCount      — number (defaults to 0)
 * - unreadCount   — number (defaults to 0); when > 0 the unread badge
 *                   shows and firePulse() restarts the cyan signal-ring
 * - pinned        — boolean
 * - authors       — string (comma-separated display names; the card
 *                   parses into avatar chips with initials)
 *
 * Methods:
 * - firePulse() — sets data-pulse="ring" for --wavy-motion-pulse-duration
 *                 then clears it. The CSS uses --wavy-pulse-ring as the
 *                 box-shadow during the pulse so the badge matches the
 *                 F-0 wavy-blip-card live-pulse contract.
 */
export class WavySearchRailCard extends LitElement {
  static properties = {
    waveId: { type: String, attribute: "data-wave-id", reflect: true },
    title: { type: String },
    snippet: { type: String },
    postedAt: { type: String, attribute: "posted-at" },
    postedAtIso: { type: String, attribute: "posted-at-iso" },
    msgCount: { type: Number, attribute: "msg-count" },
    unreadCount: { type: Number, attribute: "unread-count" },
    pinned: { type: Boolean, reflect: true },
    authors: { type: String }
  };

  static styles = css`
    :host {
      display: block;
      box-sizing: border-box;
      padding: var(--wavy-spacing-3, 12px);
      margin-bottom: var(--wavy-spacing-2, 8px);
      background: var(--wavy-bg-surface, #11192a);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-card, 12px);
      cursor: pointer;
      transition: border-color var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    :host(:hover),
    :host(:focus-within) {
      border-color: var(--wavy-signal-cyan, #22d3ee);
    }
    .top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--wavy-spacing-2, 8px);
      margin-bottom: var(--wavy-spacing-1, 4px);
    }
    .avatar-stack {
      display: inline-flex;
    }
    .avatar {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      background: var(--wavy-bg-base, #0b1120);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      font-weight: 600;
    }
    .avatar + .avatar {
      margin-left: -6px;
    }
    .avatar.more {
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
    }
    .pin {
      color: var(--wavy-signal-cyan, #22d3ee);
      font-size: 14px;
      line-height: 1;
    }
    h3.title {
      margin: 0 0 var(--wavy-spacing-1, 4px);
      font: var(--wavy-type-h3, 1.0625rem / 1.35 sans-serif);
      font-weight: 600;
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
    }
    p.snippet {
      margin: 0 0 var(--wavy-spacing-2, 8px);
      font: var(--wavy-type-body, 0.9375rem / 1.55 sans-serif);
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      display: -webkit-box;
      -webkit-line-clamp: 3;
      line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--wavy-spacing-2, 8px);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
    }
    .msg-count {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-1, 4px);
    }
    .badge.unread {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 18px;
      padding: 0 6px;
      height: 16px;
      border-radius: 9999px;
      background: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-bg-base, #0b1120);
      font-weight: 600;
      transition: box-shadow var(--wavy-motion-pulse-duration, 600ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    :host([data-pulse="ring"]) .badge.unread {
      box-shadow: var(--wavy-pulse-ring, 0 0 0 4px rgba(34, 211, 238, 0.22));
    }
    time.ts {
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
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
  }

  firePulse() {
    this.dataset.pulse = "ring";
    // Clear the pulse marker after the configured duration (matches the
    // wavy-blip-card pattern from F-0). Using setTimeout instead of
    // animationend so we don't depend on a CSS animation declaration.
    const dur =
      parseInt(
        getComputedStyle(this).getPropertyValue("--wavy-motion-pulse-duration") || "600",
        10
      ) || 600;
    setTimeout(() => {
      delete this.dataset.pulse;
    }, dur);
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
        aria-label=${this.title || "(no title)"}
      >
        <div class="top">
          <div class="avatar-stack" aria-label="Authors">
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
          ${this.pinned
            ? html`<span class="pin" aria-label="Pinned" title="Pinned">📌</span>`
            : null}
        </div>
        <h3 class="title">${this.title || "(no title)"}</h3>
        <p class="snippet">${this.snippet || ""}</p>
        <div class="footer">
          <span class="msg-count" aria-label="Message count">
            <span>${this.msgCount}</span>
            ${this.unreadCount > 0
              ? html`<span class="badge unread" aria-label="${this.unreadCount} unread"
                  >${this.unreadCount}</span
                >`
              : null}
          </span>
          ${this.postedAtIso
            ? html`<time
                class="ts"
                datetime=${this.postedAtIso}
                title=${this.postedAtIso}
                >${this.postedAt || ""}</time
              >`
            : html`<time class="ts">${this.postedAt || ""}</time>`}
        </div>
      </article>
    `;
  }
}

if (!customElements.get("wavy-search-rail-card")) {
  customElements.define("wavy-search-rail-card", WavySearchRailCard);
}
