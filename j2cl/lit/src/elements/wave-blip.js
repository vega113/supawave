import { LitElement, css, html } from "lit";

/**
 * <wave-blip> — F-2 (#1037, R-3.1) read-surface wrapper around the F-0
 * (#1035) <wavy-blip-card> recipe. The renderer creates one <wave-blip>
 * per blip in the rendered viewport window.
 *
 * Why a wrapper?
 * - The F-0 recipe ships the visual envelope (hairline cyan border,
 *   focused-state focus ring, unread dot, live-pulse glow) and the
 *   required `blip-extension` plugin slot, all keyed off the wavy
 *   design tokens. F-2 must NOT re-skin those — the plugin contract
 *   (docs/j2cl-plugin-slots.md) reflects them on `<wavy-blip-card>`.
 * - F-2 needs to layer on top: a header with the author display name
 *   + relative timestamp + full ISO datetime tooltip (F.1 / F.2 / F.3),
 *   a per-blip toolbar with Reply / Edit / Link / overflow that reveals
 *   on focus or hover (F.4 / F.5 / F.6 / F.7), an inline-reply chip when
 *   the blip has children (F.10 + R-3.7 G.1 drill-in), and a `has-mention`
 *   reflection so the wave-nav-row can navigate by @-mentions (E.6 / E.7).
 *
 * Plugin slot contract (R-3.1 step 8 + plugin-slot reservation):
 * - `<slot name="blip-extension">` is propagated through to the inner
 *   `<wavy-blip-card>` so the existing F-0 plugin context contract is
 *   preserved unchanged. Plugins keep reading `host.dataset.blipId`
 *   etc. on the inner card (the wrapper does not change the contract).
 * - The wrapper additionally surfaces `data-blip-id`, `data-wave-id`,
 *   `data-blip-author`, `data-blip-is-author`, `data-has-mention`,
 *   `data-reply-count`, `data-unread`, `data-focused` on its OWN host
 *   so external CSS hooks can target wave-blip without piercing the
 *   shadow root.
 *
 * Public API:
 * - blipId, waveId, authorName, postedAt, isAuthor, focused, unread,
 *   hasMention, replyCount, livePulse — primitive state attributes,
 *   reflected to data-attributes per the plugin contract.
 * - firePulse() — restarts the F-0 live-update pulse on the inner card.
 * - blipView (getter) — frozen `{id, waveId, authorName, postedAt,
 *   isAuthor}` snapshot, mirrors the F-0 contract for plugin consumers.
 *
 * Events emitted (CustomEvent, bubbles + composed):
 * - `wave-blip-reply-requested` — `{detail: {blipId, waveId}}`. F-3
 *   (compose) listens.
 * - `wave-blip-edit-requested` — `{detail: {blipId, waveId}}`. F-3 owns
 *   the edit surface; F-2 only emits the request.
 * - `wave-blip-link-copied` — `{detail: {blipId, waveId, url}}`. F-2
 *   handles the clipboard write itself before emitting.
 * - `wave-blip-profile-requested` — `{detail: {blipId, authorId}}`.
 *   Emitted from the avatar click for the L.1 profile overlay.
 * - `wave-blip-drill-in-requested` — `{detail: {blipId, waveId}}`.
 *   Emitted from the inline-reply chip click for the R-3.7 G.1 drill-in.
 */
export class WaveBlip extends LitElement {
  static properties = {
    blipId: { type: String, attribute: "data-blip-id", reflect: true },
    waveId: { type: String, attribute: "data-wave-id", reflect: true },
    authorId: { type: String, attribute: "author-id" },
    authorName: { type: String, attribute: "author-name" },
    postedAt: { type: String, attribute: "posted-at" },
    postedAtIso: { type: String, attribute: "posted-at-iso" },
    isAuthor: { type: Boolean, attribute: "is-author", reflect: true },
    focused: { type: Boolean, reflect: true },
    unread: { type: Boolean, reflect: true },
    hasMention: { type: Boolean, attribute: "has-mention", reflect: true },
    replyCount: { type: Number, attribute: "reply-count", reflect: true },
    livePulse: { type: Boolean, attribute: "live-pulse", reflect: true }
  };

  static styles = css`
    :host {
      display: block;
      /* The visual envelope lives on the inner <wavy-blip-card>; this
       * host is a transparent wrapper so the F-0 recipe styling owns
       * focus / unread / pulse visuals. */
    }
    /* Per-blip toolbar — hidden until focus or hover per F.4 inventory note
     * "reveal on focus/hover". Uses --wavy-motion-focus-duration so it
     * matches the focus-frame timing. */
    .toolbar {
      opacity: 0;
      transition: opacity var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
      pointer-events: none;
    }
    :host([focused]) .toolbar,
    :host(:hover) .toolbar,
    .toolbar:focus-within {
      opacity: 1;
      pointer-events: auto;
    }
    .header {
      display: flex;
      align-items: baseline;
      gap: var(--wavy-spacing-2, 8px);
      margin-bottom: var(--wavy-spacing-2, 8px);
    }
    .avatar {
      flex: 0 0 auto;
      width: 24px;
      height: 24px;
      border-radius: var(--wavy-radius-pill, 9999px);
      background: var(--wavy-signal-cyan-soft, rgba(34, 211, 238, 0.22));
      color: var(--wavy-text-body, #fff);
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      border: 0;
      padding: 0;
    }
    .avatar:focus-visible {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      outline: none;
    }
    .author {
      font: var(--wavy-type-h3, 1.0625rem / 1.35 sans-serif);
      font-weight: 600;
      color: var(--wavy-text-body, #fff);
    }
    time.posted {
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
      cursor: help;
    }
    .inline-reply-chip {
      display: inline-block;
      margin-top: var(--wavy-spacing-2, 8px);
      padding: 2px var(--wavy-spacing-2, 8px);
      border-radius: var(--wavy-radius-pill, 9999px);
      background: var(--wavy-signal-cyan-soft, rgba(34, 211, 238, 0.22));
      color: var(--wavy-text-body, #fff);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      cursor: pointer;
      border: 0;
    }
    .inline-reply-chip:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }
    /* When the wrapper carries the has-mention attr, paint a violet
     * accent rail down the left so the mention navigation (E.6 / E.7)
     * has a visual cue without clashing with unread cyan. */
    :host([has-mention])::before {
      content: "";
      position: absolute;
      width: 3px;
      top: 6px;
      bottom: 6px;
      left: 0;
      background: var(--wavy-signal-violet, #7c3aed);
      border-radius: var(--wavy-radius-pill, 9999px);
    }
    :host([has-mention]) {
      position: relative;
    }
  `;

  constructor() {
    super();
    this.blipId = "";
    this.waveId = "";
    this.authorId = "";
    this.authorName = "";
    this.postedAt = "";
    this.postedAtIso = "";
    this.isAuthor = false;
    this.focused = false;
    this.unread = false;
    this.hasMention = false;
    this.replyCount = 0;
    this.livePulse = false;
  }

  /** Lazy frozen read-only snapshot for plugin consumers. */
  get blipView() {
    return Object.freeze({
      id: this.blipId,
      waveId: this.waveId,
      authorName: this.authorName,
      authorId: this.authorId,
      postedAt: this.postedAt,
      postedAtIso: this.postedAtIso,
      isAuthor: this.isAuthor,
      unread: this.unread,
      hasMention: this.hasMention,
      replyCount: this.replyCount
    });
  }

  /**
   * Restart the F-0 live-update pulse on the inner card. F-2 reuses the
   * F-0 recipe's pulse animation so the live-update visual is identical
   * to the rest of the wavy surface.
   */
  firePulse() {
    const card = this.renderRoot.querySelector("wavy-blip-card");
    if (card && typeof card.firePulse === "function") {
      card.firePulse();
    } else {
      // Card not registered yet; fall back to setting the attribute directly
      // so the next render still picks up the pulse via :host([live-pulse]).
      this.removeAttribute("live-pulse");
      // eslint-disable-next-line no-unused-expressions
      void this.offsetWidth;
      this.setAttribute("live-pulse", "");
    }
  }

  _initials() {
    const name = (this.authorName || this.authorId || "?").trim();
    if (!name) return "?";
    const parts = name.split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  }

  _onAvatarClick(event) {
    event.stopPropagation();
    this.dispatchEvent(
      new CustomEvent("wave-blip-profile-requested", {
        bubbles: true,
        composed: true,
        detail: { blipId: this.blipId, authorId: this.authorId }
      })
    );
  }

  _onReplyClick(event) {
    event.stopPropagation();
    this.dispatchEvent(
      new CustomEvent("wave-blip-reply-requested", {
        bubbles: true,
        composed: true,
        detail: { blipId: this.blipId, waveId: this.waveId }
      })
    );
  }

  _onEditClick(event) {
    event.stopPropagation();
    this.dispatchEvent(
      new CustomEvent("wave-blip-edit-requested", {
        bubbles: true,
        composed: true,
        detail: { blipId: this.blipId, waveId: this.waveId }
      })
    );
  }

  async _onLinkClick(event) {
    event.stopPropagation();
    const url = this._buildPermalink();
    let success = false;
    try {
      if (navigator.clipboard && typeof navigator.clipboard.writeText === "function") {
        await navigator.clipboard.writeText(url);
        success = true;
      }
    } catch (e) {
      success = false;
    }
    this.dispatchEvent(
      new CustomEvent("wave-blip-link-copied", {
        bubbles: true,
        composed: true,
        detail: { blipId: this.blipId, waveId: this.waveId, url, success }
      })
    );
  }

  _onChipClick(event) {
    event.stopPropagation();
    this.dispatchEvent(
      new CustomEvent("wave-blip-drill-in-requested", {
        bubbles: true,
        composed: true,
        detail: { blipId: this.blipId, waveId: this.waveId }
      })
    );
  }

  _buildPermalink() {
    if (typeof window === "undefined" || !window.location) {
      return "";
    }
    const { origin, pathname } = window.location;
    const wave = this.waveId ? encodeURIComponent(this.waveId) : "";
    const blip = this.blipId ? encodeURIComponent(this.blipId) : "";
    return `${origin}${pathname}?wave=${wave}&blip=${blip}`;
  }

  render() {
    const tooltip = this.postedAtIso || this.postedAt;
    const chip =
      this.replyCount > 0
        ? html`<button
            type="button"
            class="inline-reply-chip"
            data-inline-reply-chip="true"
            aria-label=${`Drill into ${this.replyCount} replies under this blip`}
            @click=${this._onChipClick}
          >
            △ ${this.replyCount}
          </button>`
        : null;

    return html`
      <wavy-blip-card
        data-blip-id=${this.blipId}
        data-wave-id=${this.waveId}
        author-name=${this.authorName}
        posted-at=${this.postedAt}
        ?is-author=${this.isAuthor}
        ?focused=${this.focused}
        ?unread=${this.unread}
        ?live-pulse=${this.livePulse}
      >
        <div class="header" slot="metadata">
          <button
            type="button"
            class="avatar"
            data-blip-avatar="true"
            aria-label=${`Open ${this.authorName || this.authorId || "user"} profile`}
            @click=${this._onAvatarClick}
          >
            ${this._initials()}
          </button>
          <span class="author">${this.authorName || this.authorId || ""}</span>
          <time
            class="posted"
            title=${tooltip}
            datetime=${this.postedAtIso || ""}
          >
            ${this.postedAt}
          </time>
        </div>
        <div class="toolbar" slot="metadata">
          <wave-blip-toolbar
            data-blip-id=${this.blipId}
            data-wave-id=${this.waveId}
            @wave-blip-toolbar-reply=${this._onReplyClick}
            @wave-blip-toolbar-edit=${this._onEditClick}
            @wave-blip-toolbar-link=${this._onLinkClick}
          ></wave-blip-toolbar>
        </div>
        <div class="body">
          <slot></slot>
          ${chip}
        </div>
        <slot name="blip-extension" slot="blip-extension"></slot>
        <slot name="reactions" slot="reactions"></slot>
      </wavy-blip-card>
    `;
  }
}

if (!customElements.get("wave-blip")) {
  customElements.define("wave-blip", WaveBlip);
}
