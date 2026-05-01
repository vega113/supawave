import { LitElement, css, html } from "lit";

/**
 * <wavy-profile-overlay> — F-2.S4 (#1048, L.1 + L.5) modal that opens
 * when an avatar is clicked on a <wave-blip>. Mounts the overlay
 * scaffolding and the profile-card parity actions for L.2 (Send Message)
 * and L.3 (Edit Profile). The named `actions` slot remains for future
 * shell-specific extensions.
 *
 * Open path (L.1):
 *   The element listens on `document` for `wave-blip-profile-requested`
 *   (the S1 event from the avatar click; bubbles + composed so it
 *   crosses the wave-blip shadow boundary). On receipt it sets `open =
 *   true`, looks up the matching participant by `authorId`, and emits
 *   `wavy-profile-overlay-opened`.
 *
 * Properties:
 *   - open: boolean — overlay visibility (and `hidden` attribute on host).
 *   - participants: Array<{ id, displayName, avatarUrl? }> — populated
 *     by the renderer from the wave model. Default [].
 *   - index: number — currently shown participant index. Default 0.
 *   - currentUserId: string — signed-in participant address used to gate
 *     own-profile actions.
 *   - editProfileUrl: string — destination for the Edit Profile action.
 *
 * Events emitted (CustomEvent, bubbles + composed):
 *   - `wavy-profile-overlay-opened` — `{detail: {authorId}}`.
 *   - `wavy-profile-participant-changed` — `{detail: {index, participant}}`.
 *   - `wavy-profile-overlay-closed` — emitted on Exit / Escape.
 *   - `wave-new-with-participants-requested` —
 *     `{detail: {participants, source}}` from Send Message.
 *   - `wavy-profile-edit-requested` — cancelable route hook from Edit Profile.
 *
 * L.4 (close): the inventory marks close as F-0-owned (style). S4
 * ships a tactical close (× button + Escape) using wavy tokens so the
 * modal is dismissable on day one; F-0 can later restyle the close
 * affordance without changing the event contract.
 */
export class WavyProfileOverlay extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    participants: { attribute: false },
    index: { type: Number, reflect: true },
    currentUserId: { type: String, attribute: "current-user-id" },
    editProfileUrl: { type: String, attribute: "edit-profile-url" }
  };

  static styles = css`
    :host {
      position: fixed;
      inset: 0;
      z-index: 200;
      display: block;
    }
    :host([hidden]) {
      display: none;
    }
    .backdrop {
      position: absolute;
      inset: 0;
      background: rgba(11, 19, 32, 0.7);
    }
    .panel {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      min-width: 280px;
      max-width: 92vw;
      background: var(--wavy-bg-base, #0b1320);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-card, 12px);
      padding: var(--wavy-spacing-5, 24px);
      display: grid;
      gap: var(--wavy-spacing-3, 12px);
      justify-items: center;
    }
    .avatar {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-bg-base, #0b1320);
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font: var(--wavy-type-section, 0.875rem / 1.4 sans-serif);
      font-weight: 700;
    }
    .avatar img {
      width: 100%;
      height: 100%;
      border-radius: 50%;
      object-fit: cover;
    }
    .name {
      margin: 0;
      font: var(--wavy-type-section, 0.875rem / 1.4 sans-serif);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
    }
    .pid {
      margin: 0;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
    }
    .nav {
      position: absolute;
      top: 50%;
      transform: translateY(-50%);
      width: var(--wavy-spacing-5, 24px);
      height: var(--wavy-spacing-5, 24px);
      padding: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      background: transparent;
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 0;
      cursor: pointer;
      border-radius: var(--wavy-radius-pill, 9999px);
    }
    .nav.prev { left: var(--wavy-spacing-2, 8px); }
    .nav.next { right: var(--wavy-spacing-2, 8px); }
    .nav[disabled] {
      opacity: 0.35;
      cursor: not-allowed;
    }
    .exit {
      position: absolute;
      top: var(--wavy-spacing-3, 12px);
      right: var(--wavy-spacing-3, 12px);
      width: var(--wavy-spacing-5, 24px);
      height: var(--wavy-spacing-5, 24px);
      padding: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: var(--wavy-radius-pill, 9999px);
      border: 0;
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      cursor: pointer;
    }
    button:focus-visible {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      outline: none;
    }
    .actions-slot {
      display: inline-flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: var(--wavy-spacing-2, 8px);
    }
    .profile-action {
      min-height: 34px;
      padding: 0 var(--wavy-spacing-4, 16px);
      border-radius: var(--wavy-radius-pill, 9999px);
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      background: rgba(255, 255, 255, 0.06);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      cursor: pointer;
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      font-weight: 700;
      letter-spacing: 0.01em;
    }
    .profile-action-primary {
      background: var(--wavy-signal-cyan, #22d3ee);
      border-color: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-bg-base, #0b1320);
    }
    .profile-action[hidden] {
      display: none;
    }
  `;

  constructor() {
    super();
    this.open = false;
    this.participants = [];
    this.index = 0;
    this.currentUserId = "";
    this.editProfileUrl = "/userprofile/edit";
    this._fallbackParticipant = null;
    this._onAvatarRequest = this._onAvatarRequest.bind(this);
    this._onKeyDown = this._onKeyDown.bind(this);
  }

  connectedCallback() {
    super.connectedCallback();
    this._syncOpen();
    document.addEventListener("wave-blip-profile-requested", this._onAvatarRequest);
    this.addEventListener("keydown", this._onKeyDown);
  }

  disconnectedCallback() {
    document.removeEventListener("wave-blip-profile-requested", this._onAvatarRequest);
    this.removeEventListener("keydown", this._onKeyDown);
    super.disconnectedCallback();
  }

  willUpdate(changed) {
    if (changed.has("open")) {
      this._syncOpen();
    }
    // Clamp `index` against the current `participants` length so
    // _prev/_next/_emitChange work off an in-bounds value when the
    // participants array shrinks under us. render() already clamps a
    // local idx, but the navigation handlers consume `this.index`
    // directly — keep them in lockstep.
    if (changed.has("participants") || changed.has("index")) {
      const list = Array.isArray(this.participants) ? this.participants : [];
      const max = list.length > 0 ? list.length - 1 : 0;
      const raw = Number.isFinite(this.index) ? this.index : 0;
      const clamped = Math.max(0, Math.min(raw, max));
      if (clamped !== this.index) {
        this.index = clamped;
      }
      // Keep aria-label current when the displayed participant changes while open.
      if (this.open) {
        const name = (list[clamped] && list[clamped].displayName) || "Profile";
        this.setAttribute("aria-label", name);
      }
    }
  }

  _syncOpen() {
    if (this.open) {
      this.removeAttribute("hidden");
      this.setAttribute("role", "dialog");
      this.setAttribute("aria-modal", "true");
      // aria-labelledby cannot resolve across the shadow DOM boundary; use
      // aria-label so assistive tech can announce the dialog name.
      const list = Array.isArray(this.participants) ? this.participants : [];
      const max = list.length > 0 ? list.length - 1 : 0;
      const idx = Math.max(0, Math.min(Number.isFinite(this.index) ? this.index : 0, max));
      const name = (list[idx] && list[idx].displayName) || "Profile";
      this.setAttribute("aria-label", name);
      this.removeAttribute("aria-hidden");
      // Make the host focusable so ArrowLeft / ArrowRight / Escape
      // delivered via real keyboard (not synthetic dispatch) reach the
      // host's keydown handler. tabindex=-1 keeps it out of the tab
      // sequence — focus is moved here explicitly on open.
      this.setAttribute("tabindex", "-1");
      // F-2 slice 5 (#1055, S4 deferral): focus-trap + inert on
      // siblings — modal semantics. Save the previously focused element
      // and inert every <body> direct child except this host.
      this._previouslyFocusedElement =
        (this.ownerDocument && this.ownerDocument.activeElement) || null;
      this._inertedSiblings = [];
      try {
        const body = this.ownerDocument && this.ownerDocument.body;
        if (body) {
          for (const child of Array.from(body.children)) {
            if (child === this) continue;
            if (child.hasAttribute && !child.hasAttribute("inert")) {
              child.setAttribute("inert", "");
              this._inertedSiblings.push(child);
            }
          }
        }
      } catch (_e) {
        // inert support is observational; never block open.
      }
      // Move focus to the host on the next microtask so the browser
      // has finished reflecting the hidden→visible flip.
      Promise.resolve().then(() => {
        if (this.open && typeof this.focus === "function") {
          try { this.focus({ preventScroll: true }); } catch (_e) { this.focus(); }
        }
      });
    } else {
      this.setAttribute("hidden", "");
      this.setAttribute("aria-hidden", "true");
      this.removeAttribute("tabindex");
      this.removeAttribute("aria-label");
      // F-2 slice 5 (#1055, S4 deferral): restore focus + un-inert siblings.
      if (Array.isArray(this._inertedSiblings)) {
        for (const sibling of this._inertedSiblings) {
          try { sibling.removeAttribute("inert"); } catch (_e) {}
        }
        this._inertedSiblings = [];
      }
      const previouslyFocused = this._previouslyFocusedElement;
      this._previouslyFocusedElement = null;
      if (previouslyFocused && typeof previouslyFocused.focus === "function") {
        try { previouslyFocused.focus({ preventScroll: true }); } catch (_e) {
          try { previouslyFocused.focus(); } catch (_err) {}
        }
      }
    }
  }

  _onAvatarRequest(event) {
    const detail = event && event.detail ? event.detail : {};
    const authorId = detail.authorId;
    const list = Array.isArray(this.participants) ? this.participants : [];
    if (authorId) {
      const found = list.findIndex((p) => p && p.id === authorId);
      if (found >= 0) {
        this._fallbackParticipant = null;
        this.index = found;
      } else {
        this._fallbackParticipant = {
          id: String(authorId),
          displayName: String(authorId)
        };
        this.index = 0;
      }
    } else {
      this._fallbackParticipant = null;
      this.index = 0;
    }
    this.open = true;
    this.dispatchEvent(
      new CustomEvent("wavy-profile-overlay-opened", {
        bubbles: true,
        composed: true,
        detail: { authorId: authorId || null }
      })
    );
  }

  open_(index) {
    this._fallbackParticipant = null;
    if (typeof index === "number") {
      const list = Array.isArray(this.participants) ? this.participants : [];
      const max = list.length > 0 ? list.length - 1 : 0;
      this.index = Math.min(Math.max(index, 0), max);
    }
    this.open = true;
  }

  close_() {
    if (!this.open) return;
    this.open = false;
    this.dispatchEvent(
      new CustomEvent("wavy-profile-overlay-closed", {
        bubbles: true,
        composed: true
      })
    );
  }

  _onKeyDown(event) {
    if (!this.open) return;
    if (event.key === "Escape") {
      event.preventDefault();
      this.close_();
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      this._next();
    } else if (event.key === "ArrowLeft") {
      event.preventDefault();
      this._prev();
    }
  }

  _next() {
    const list = Array.isArray(this.participants) ? this.participants : [];
    if (list.length === 0) return;
    if (this.index >= list.length - 1) return;
    this.index = this.index + 1;
    this._emitChange();
  }

  _prev() {
    const list = Array.isArray(this.participants) ? this.participants : [];
    if (list.length === 0) return;
    if (this.index <= 0) return;
    this.index = this.index - 1;
    this._emitChange();
  }

  _emitChange() {
    const list = Array.isArray(this.participants) ? this.participants : [];
    const participant = list[this.index] || null;
    this.dispatchEvent(
      new CustomEvent("wavy-profile-participant-changed", {
        bubbles: true,
        composed: true,
        detail: { index: this.index, participant }
      })
    );
  }

  _onClose() {
    this.close_();
  }

  _profileAddress(participant) {
    return participant && participant.id ? String(participant.id).trim() : "";
  }

  _normalizedAddress(value) {
    return String(value || "").trim().toLowerCase();
  }

  _isSelfParticipant(participant) {
    if (!participant) return false;
    if (participant.isSelf === true || participant.isSelf === "true") {
      return true;
    }
    const profileAddress = this._normalizedAddress(this._profileAddress(participant));
    const currentUserId = this._normalizedAddress(this.currentUserId);
    return Boolean(profileAddress && currentUserId && profileAddress === currentUserId);
  }

  _onSendMessage(participant) {
    const address = this._profileAddress(participant);
    if (!address || this._isSelfParticipant(participant)) {
      return;
    }
    this.dispatchEvent(
      new CustomEvent("wave-new-with-participants-requested", {
        bubbles: true,
        composed: true,
        detail: { participants: [address], source: "profile-overlay" }
      })
    );
    this.close_();
  }

  _onEditProfile(participant) {
    if (!this._isSelfParticipant(participant)) {
      return;
    }
    const url = this.editProfileUrl || "/userprofile/edit";
    const event = new CustomEvent("wavy-profile-edit-requested", {
      bubbles: true,
      composed: true,
      cancelable: true,
      detail: { url, participant }
    });
    const shouldNavigate = this.dispatchEvent(event);
    this.close_();
    if (shouldNavigate) {
      const loc = globalThis && globalThis.location;
      if (loc && typeof loc.assign === "function") {
        loc.assign(url);
      } else if (loc) {
        loc.href = url;
      }
    }
  }

  _initials(displayName) {
    const name = String(displayName || "?").trim();
    if (!name) return "?";
    const parts = name.split(/\s+/).slice(0, 2);
    return parts.map((p) => p.charAt(0).toUpperCase()).join("");
  }

  render() {
    const participantList = Array.isArray(this.participants) ? this.participants : [];
    const list =
      this._fallbackParticipant
          ? [this._fallbackParticipant]
          : participantList;
    const max = list.length > 0 ? list.length - 1 : 0;
    const idx = Math.min(Math.max(this.index || 0, 0), max);
    const current = list[idx] || null;
    const displayName = current ? current.displayName : "Unknown participant";
    const pid = current ? current.id : "";
    const isSelf = this._isSelfParticipant(current);
    const canSendMessage = Boolean(this._profileAddress(current)) && !isSelf;
    const prevDisabled = list.length === 0 || idx === 0;
    const nextDisabled = list.length === 0 || idx >= max;

    return html`
      <div class="backdrop" @click=${this._onClose}></div>
      <div class="panel">
        <button
          class="exit"
          type="button"
          aria-label="Close profile"
          @click=${this._onClose}
        >
          <span aria-hidden="true">×</span>
        </button>
        <button
          class="nav prev"
          type="button"
          aria-label="Previous participant"
          ?disabled=${prevDisabled}
          @click=${this._prev}
        >
          <span aria-hidden="true">←</span>
        </button>
        <button
          class="nav next"
          type="button"
          aria-label="Next participant"
          ?disabled=${nextDisabled}
          @click=${this._next}
        >
          <span aria-hidden="true">→</span>
        </button>
        <div class="avatar" aria-hidden="true">
          ${current && current.avatarUrl
            ? html`<img src=${current.avatarUrl} alt="" />`
            : this._initials(displayName)}
        </div>
        <h2 class="name" id="wavy-profile-name">${displayName}</h2>
        ${pid ? html`<p class="pid">${pid}</p>` : null}
        <div class="actions-slot">
          <button
            class="profile-action profile-action-primary"
            type="button"
            data-profile-send-message
            ?hidden=${!canSendMessage}
            @click=${() => this._onSendMessage(current)}
          >
            Send Message
          </button>
          <button
            class="profile-action"
            type="button"
            data-profile-edit
            ?hidden=${!isSelf}
            @click=${() => this._onEditProfile(current)}
          >
            Edit Profile
          </button>
          <slot name="actions"></slot>
        </div>
      </div>
    `;
  }
}

if (!customElements.get("wavy-profile-overlay")) {
  customElements.define("wavy-profile-overlay", WavyProfileOverlay);
}
