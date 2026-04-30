import { LitElement, css, html } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";
import "./wavy-task-affordance.js";

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
 * - `wave-blip-delete-requested` — `{detail: {blipId, waveId, bodySize}}`. F-3.S4
 *   (#1038, R-5.6) listens; the compose view shows a styled wavy
 *   confirm dialog and on confirm calls the controller's
 *   onDeleteBlipRequested listener which builds the deletion delta
 *   via `J2clRichContentDeltaFactory.blipDeleteRequest`.
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
    // G-PORT-3 (#1112): cross-view parity hooks. The J2CL renderer
    // also stamps these on the host directly (so the test sees them
    // pre-Lit-upgrade); this property reflection keeps them in sync
    // when authorName / postedAt change after the initial render.
    // Reflect-only — Lit reads from the renderer-set attribute on
    // upgrade (the constructor default is empty string), so the
    // upgrade-clobber pattern documented for data-blip-depth /
    // data-thread-collapsed does NOT apply here: empty string and
    // an absent attribute are both "no value" for the test's
    // non-empty assertion.
    dataBlipAuthor: { type: String, attribute: "data-blip-author", reflect: true },
    dataBlipTime: { type: String, attribute: "data-blip-time", reflect: true },
    isAuthor: { type: Boolean, attribute: "is-author", reflect: true },
    focused: { type: Boolean, reflect: true },
    // G-PORT-3 (#1112): cross-view parity hook for the focused blip.
    // Mirrors the existing `focused` boolean attribute as a string
    // attribute matching the GWT side (which will set
    // data-blip-focused="true" on chrome insert / remove it on chrome
    // remove). Reflected from the `focused` boolean on render.
    dataBlipFocused: { type: String, attribute: "data-blip-focused", reflect: true },
    unread: { type: Boolean, reflect: true },
    hasMention: { type: Boolean, attribute: "has-mention", reflect: true },
    replyCount: { type: Number, attribute: "reply-count", reflect: true },
    livePulse: { type: Boolean, attribute: "live-pulse", reflect: true },
    // V-4 (#1102): blip depth distinguishes the root blip from reply
    // blips so the avatar paints at the larger root size and the
    // header timestamp picks up the ` · root` suffix that the mockup
    // shows on the top-of-thread blip. Set by the Java renderer on
    // the host element directly — Lit reads it but does not reflect
    // back, otherwise the constructor's empty default would clobber
    // the renderer-set attribute on upgrade.
    blipDepth: { type: String, attribute: "data-blip-depth", reflect: false },
    // V-4 (#1102): controls thread chevron glyph orientation. When the
    // thread is collapsed the chevron flips from triangle-down to
    // triangle-right. Set by the toolbar surface controller via the
    // host attribute; Lit reads but does not reflect back for the
    // same upgrade-clobber reason as data-blip-depth.
    threadCollapsed: { type: Boolean, attribute: "data-thread-collapsed", reflect: false },
    // F-3.S2 (#1038, R-5.4): per-blip task state. Reflected to
    // data-task-completed so wave-blip[data-task-completed="true"] can
    // be targeted by external CSS hooks (mark-read pipeline, search
    // filter highlights). The values are sourced from the supplement
    // live-update path (task/done annotation on the blip body).
    taskCompleted: { type: Boolean, attribute: "data-task-completed", reflect: true },
    taskAssignee: { type: String, attribute: "data-task-assignee", reflect: true },
    taskDueDate: { type: String, attribute: "data-task-due-date", reflect: true },
    bodySize: { type: Number, attribute: "data-blip-doc-size", reflect: true }
  };

  static styles = css`
    :host {
      display: block;
      /* The visual envelope lives on the inner <wavy-blip-card>; this
       * host is a transparent wrapper so the F-0 recipe styling owns
       * focus / unread / pulse visuals. */
    }
    /* Per-blip action toolbar. G-PORT-9 keeps the primary action glyphs
     * visible in the metadata strip because GWT paints them in the blip
     * header; task-specific controls still reveal on hover/focus below. */
    .toolbar {
      opacity: 1;
      transition: opacity var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
      pointer-events: auto;
    }
    .header {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-bottom: 0.3em;
      margin-left: 3.35em;
      min-height: 32px;
      padding: 1px 8px 1px 0.3em;
      border-radius: 6px;
      background-color: #f0f4f8;
      color: #718096;
      font-size: 13px;
      line-height: 1.3;
    }
    .thread-chevron {
      flex: 0 0 auto;
      width: 16px;
      text-align: center;
      font: var(--wavy-type-label, 11px / 1.35 Arial, sans-serif);
      color: #718096;
      user-select: none;
    }
    :host([focused]) .thread-chevron {
      color: var(--wavy-signal-cyan, #0077b6);
      font-weight: 700;
    }
    :host([tabindex="0"]) .thread-chevron {
      color: var(--wavy-signal-cyan, #0077b6);
      font-weight: 700;
    }
    /* Hide the chevron entirely when the blip has no replies. The
     * J-UI-4 controller only attaches collapse listeners to threads
     * with children, so the glyph is purely a visual cue here. */
    .thread-chevron[hidden] {
      display: none;
    }
    .avatar {
      flex: 0 0 auto;
      width: var(--wavy-avatar-size-reply, 28px);
      height: var(--wavy-avatar-size-reply, 28px);
      margin-left: -2.6em;
      border-radius: 50%;
      background: var(--wavy-avatar-palette-0, #f8fafc);
      color: var(--wavy-avatar-fg, #1a202c);
      font: var(--wavy-type-label, 11px / 1.35 Arial, sans-serif);
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      border: 1.5px solid #ffffff;
      padding: 0;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
    }
    :host([data-blip-depth="root"]) .avatar {
      width: var(--wavy-avatar-size-root, 28px);
      height: var(--wavy-avatar-size-root, 28px);
      font-size: 11px;
    }
    .avatar[data-palette="0"] { background: var(--wavy-avatar-palette-0, #22d3ee); }
    .avatar[data-palette="1"] { background: var(--wavy-avatar-palette-1, #edf2f7); color: #1a202c; }
    .avatar[data-palette="2"] { background: var(--wavy-avatar-palette-2, #e8f0fe); }
    .avatar[data-palette="3"] {
      background: var(--wavy-avatar-palette-3, #475569);
      color: var(--wavy-avatar-palette-3-fg, #ffffff);
    }
    .avatar:focus-visible {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px rgba(0, 119, 182, 0.16));
      outline: none;
    }
    .author {
      font: var(--wavy-type-h3, 13px / 1.3 Arial, sans-serif);
      font-weight: 600;
      color: #1a202c;
    }
    time.posted {
      font: var(--wavy-type-meta, 11px / 1.4 Arial, sans-serif);
      color: #718096;
      cursor: help;
      margin-left: auto;
    }
    .toolbar {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin: 0 0 0 4px;
      position: relative;
    }
    /* The toolbar lives as a sibling of .body inside the wavy-blip-card
     * default slot specifically so the F-3.S2 task-completed
     * line-through (which targets .body) does not bleed onto action
     * labels. CSS text-decoration painted on an ancestor renders
     * through descendants even when descendants set text-decoration
     * none, so visual isolation must come from DOM placement rather
     * than from a defensive rule. */
    .inline-reply-chip {
      display: inline-block;
      margin-top: 6px;
      padding: 6px 10px;
      border-radius: 8px;
      border: 1.5px dashed #e2e8f0;
      background: #f0f4f8;
      color: #718096;
      font: italic 13px / 1.35 Arial, sans-serif;
      cursor: pointer;
    }
    .inline-reply-chip:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px rgba(0, 119, 182, 0.16));
    }
    /* When the wrapper carries the has-mention attr, paint a cyan
     * accent rail down the left so the mention navigation (E.6 / E.7)
     * has a visual cue without reusing the unread-dot geometry. */
    :host([has-mention])::before {
      content: "";
      position: absolute;
      width: 3px;
      top: 6px;
      bottom: 6px;
      left: 0;
      background: var(--wavy-signal-cyan, #0077b6);
      border-radius: var(--wavy-radius-pill, 999px);
    }
    :host([has-mention]) {
      position: relative;
    }
    /* F-3.S2 (#1038, R-5.4 step 4): completed tasks fade the body via
     * the F-0 quiet text token and apply a strikethrough so the blip
     * card visually communicates the closed state without re-painting
     * the wavy envelope. The strikethrough lives on the body wrapper
     * inside the host so the metadata header (author + timestamp)
     * stays legible. */
    :host([data-task-completed]) .body {
      color: #767676;
      text-decoration: line-through;
      text-decoration-color: #767676;
    }
    .body {
      margin-left: 3.35em;
      min-height: 1.5em;
      padding: 6px 8px;
      border: 1px solid #e2e8f0;
      border-radius: 4px;
      background: #f8fafc;
      color: #1a202c;
      line-height: 1.35;
      overflow-wrap: break-word;
      transition: border-color 200ms ease, background 200ms ease;
    }
    .body:focus-within {
      border-color: #90cdf4;
      background: #ffffff;
    }
    :host([focused]) .body,
    :host([tabindex="0"]) .body {
      border-color: #d9e2ec;
      background: #ffffff;
      color: var(--wavy-signal-cyan, #0077b6);
      font-weight: 600;
    }
    .task-affordance-slot {
      display: inline-flex;
      align-items: center;
      position: absolute;
      top: 100%;
      right: 8px;
      opacity: 0;
      pointer-events: none;
      visibility: hidden;
    }
    :host([focused]) .task-affordance-slot,
    :host([tabindex="0"]) .task-affordance-slot,
    :host(:focus-within) .task-affordance-slot,
    :host(:hover) .task-affordance-slot,
    .task-affordance-slot:focus-within {
      opacity: 1;
      pointer-events: auto;
      visibility: visible;
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
    this.taskCompleted = false;
    this.taskAssignee = "";
    this.taskDueDate = "";
    this.bodySize = 0;
    this.blipDepth = "";
    this.threadCollapsed = false;
    this.dataBlipAuthor = "";
    this.dataBlipTime = "";
    this.dataBlipFocused = null;
    this._participants = [];
  }

  /**
   * G-PORT-3 (#1112): keep the data-blip-* parity hooks in sync with
   * authorName / postedAt / focused so the cross-view Playwright spec
   * sees a stable selector regardless of which side of the upgrade
   * the test runs on.
   */
  willUpdate(changedProperties) {
    if (changedProperties.has("authorName") || changedProperties.has("authorId")) {
      const author = this.authorName || this.authorId || "";
      if (author) {
        this.dataBlipAuthor = author;
      } else {
        this.removeAttribute("data-blip-author");
      }
    }
    if (changedProperties.has("postedAtIso") || changedProperties.has("postedAt")) {
      const time = this.postedAtIso || this.postedAt || "";
      if (time) {
        this.dataBlipTime = time;
      } else {
        this.removeAttribute("data-blip-time");
      }
    }
    if (changedProperties.has("focused")) {
      this.dataBlipFocused = this.focused ? "true" : null;
    }
    super.willUpdate(changedProperties);
  }

  /**
   * Optional Array<{address, displayName}> used as the participant
   * candidate list for the inner <wavy-task-affordance>'s details
   * popover. Set as a JS property by the Java view; absent in unit
   * fixtures (the affordance falls back to "Unassigned" only).
   */
  get participants() {
    return this._participants;
  }
  set participants(value) {
    this._participants = Array.isArray(value) ? value : [];
    this.requestUpdate();
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

  /**
   * V-4 (#1102): deterministic 4-entry avatar palette index keyed off
   * `author-id` (preferred) or `author-name` so the same author keeps
   * the same color across re-renders. The hash is intentionally simple
   * (FNV-1a 32-bit) — palette stability and uniform distribution are
   * the only requirements; cryptographic strength is not relevant.
   */
  _palette() {
    const key = (this.authorId || this.authorName || "").trim();
    if (!key) return 0;
    let hash = 0x811c9dc5;
    for (let i = 0; i < key.length; i++) {
      hash ^= key.charCodeAt(i);
      hash = Math.imul(hash, 0x01000193);
    }
    // Unsigned right-shift coerces the signed Math.imul result into
    // [0, 2^32) before the modulus, so the distribution across the
    // 4-entry palette stays uniform (Math.abs on INT32_MIN would
    // skew it).
    return (hash >>> 0) % 4;
  }

  /**
   * V-4 (#1102): chevron glyph for thread-collapse affordance.
   * Visible only when the blip has at least one reply; flips between
   * ▾ (open) and ▸ (collapsed) based on `data-thread-collapsed`.
   */
  _chevronGlyph() {
    return this.threadCollapsed ? "▸" : "▾";
  }

  _visuallyFocused() {
    return this.focused || this.getAttribute("tabindex") === "0";
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

  /**
   * F-3.S4 (#1038, R-5.6): Delete affordance. Re-emits a public
   * `wave-blip-delete-requested` CustomEvent so the J2CL compose
   * surface can route through a styled wavy confirm dialog (no
   * Window.confirm per the project memory rule
   * `feedback_no_browser_popups.md`) and then call the new
   * `J2clComposeSurfaceController.Listener.onDeleteBlipRequested`
   * listener which builds the deletion delta via
   * `J2clRichContentDeltaFactory.blipDeleteRequest`.
   */
  _onDeleteClick(event) {
    event.stopPropagation();
    this.dispatchEvent(
      new CustomEvent("wave-blip-delete-requested", {
        bubbles: true,
        composed: true,
        detail: {
          blipId: this.blipId,
          waveId: this.waveId,
          bodySize: this.bodySize || 0
        }
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

  _onTaskToggled(event) {
    // Optimistically mirror the affordance toggle into the blip's own
    // taskCompleted property so the strikethrough CSS updates immediately
    // without waiting for the model to round-trip through the server.
    this.taskCompleted = event.detail.completed;
  }

  _buildPermalink() {
    if (typeof window === "undefined" || !window.location) {
      return "";
    }
    // Start from the current href so we preserve view=j2cl-root (and any
    // other shell-recognised query params like q=). The server only
    // recognises the `wave` query parameter today; carry the per-blip
    // anchor in the URL fragment instead of an unsupported `blip` param so
    // the copied permalink restores the J2CL view AND points at this blip.
    const url = new URL(window.location.href);
    if (this.waveId) {
      url.searchParams.set("wave", this.waveId);
    } else {
      url.searchParams.delete("wave");
    }
    url.searchParams.delete("blip");
    url.hash = this.blipId ? `blip-${this.blipId}` : "";
    return url.toString();
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
    const isRoot = this.blipDepth === "root";
    const timestampSuffix = isRoot && this.postedAt ? " · root" : "";
    const palette = this._palette();
    const hasReplies = this.replyCount > 0;
    const visuallyFocused = this._visuallyFocused();
    const chevron = hasReplies
      ? html`<span
          class="thread-chevron"
          data-thread-chevron="true"
          aria-hidden="true"
        >${this._chevronGlyph()}</span>`
      : html`<span class="thread-chevron" hidden></span>`;

    return html`
      <wavy-blip-card
        data-blip-id=${this.blipId}
        data-wave-id=${this.waveId}
        author-name=${this.authorName}
        posted-at=${this.postedAt}
        ?is-author=${this.isAuthor}
        ?focused=${visuallyFocused}
        ?unread=${this.unread}
        ?live-pulse=${this.livePulse}
      >
        <div class="header" slot="metadata">
          ${chevron}
          <button
            type="button"
            class="avatar"
            data-blip-avatar="true"
            data-palette=${String(palette)}
            aria-label=${`Open ${this.authorName || this.authorId || "user"} profile`}
            @click=${this._onAvatarClick}
          >
            ${this._initials()}
          </button>
          <span class="author">${this.authorName || this.authorId || ""}</span>
          <time
            class="posted"
            title=${tooltip}
            datetime=${ifDefined(this.postedAtIso || undefined)}
          >
            ${this.postedAt}${timestampSuffix}
          </time>
          <span class="toolbar" data-blip-toolbar-row="true">
          <wave-blip-toolbar
            data-blip-id=${this.blipId}
            data-wave-id=${this.waveId}
            data-variant=${visuallyFocused ? "focused" : "default"}
            @wave-blip-toolbar-reply=${this._onReplyClick}
            @wave-blip-toolbar-edit=${this._onEditClick}
            @wave-blip-toolbar-link=${this._onLinkClick}
            @wave-blip-toolbar-delete=${this._onDeleteClick}
          ></wave-blip-toolbar>
          <span class="task-affordance-slot" data-task-affordance-slot>
            <wavy-task-affordance
              data-blip-id=${this.blipId}
              data-wave-id=${this.waveId}
              ?data-task-completed=${this.taskCompleted}
              data-task-assignee=${this.taskAssignee || ""}
              data-task-due-date=${this.taskDueDate || ""}
              data-blip-doc-size=${ifDefined(this.bodySize > 0 ? String(this.bodySize) : undefined)}
              .bodySize=${this.bodySize || 0}
              .participants=${this._participants}
              @wave-blip-task-toggled=${this._onTaskToggled}
            ></wavy-task-affordance>
          </span>
          </span>
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
