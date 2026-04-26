import { LitElement, css, html } from "lit";

/**
 * <wavy-header> — F-2 (#1037, #1047 slice 3) header end-region chrome.
 *
 * Inventory affordances covered (from the 2026-04-26 GWT functional
 * inventory):
 * - A.1 SupaWave brand link with cyan signal-dot accent
 * - A.2 Locale picker (en/de/es/fr/ru/sl/zh_TW)
 * - A.5 Notifications bell with violet unread dot (uses
 *       --wavy-signal-violet, NOT cyan)
 * - A.6 Inbox/mail icon — jumps to inbox via /?q=in:inbox
 * - A.7 User-menu trigger: avatar chip (initials) + visible email span;
 *       click emits wavy-user-menu-requested for the F-0 menu sheet
 *       (the menu items A.8–A.18 themselves are owned by F-0 and are
 *       NOT mounted by this slice — only the trigger is)
 *
 * Public API:
 * - signedIn      — boolean (controls visibility of A.5/A.6/A.7)
 * - locale        — current locale code (defaults to "en")
 * - address       — user's email address (data-address; reflected so
 *                   server-side templates can pass it through)
 * - userName      — display name fallback for initials (defaults to address)
 * - unreadCount   — number; A.5 dot becomes visible when > 0
 *
 * Events:
 * - wavy-locale-changed     — `{detail: {locale}}` on <select> change
 * - wavy-user-menu-requested — `{detail: {address}}` on user-menu click
 */
export class WavyHeader extends LitElement {
  static properties = {
    signedIn: { type: Boolean, attribute: "signed-in", reflect: true },
    locale: { type: String, reflect: true },
    address: { type: String, attribute: "data-address", reflect: true },
    userName: { type: String, attribute: "user-name" },
    unreadCount: { type: Number, attribute: "unread-count" }
  };

  // Locale set re-derived from the GWT locale build set; matches the
  // SearchWidgetMessages_*.properties files in the resources tree.
  static LOCALES = [
    { code: "en", label: "English" },
    { code: "de", label: "Deutsch" },
    { code: "es", label: "Español" },
    { code: "fr", label: "Français" },
    { code: "ru", label: "Русский" },
    { code: "sl", label: "Slovenščina" },
    { code: "zh_TW", label: "繁體中文" }
  ];

  static styles = css`
    :host {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-3, 12px);
      flex-wrap: wrap;
      font: var(--wavy-type-body, 0.9375rem / 1.55 sans-serif);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
    }
    .brand {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      text-decoration: none;
      font-weight: 600;
    }
    .brand-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--wavy-signal-cyan, #22d3ee);
      display: inline-block;
    }
    .locale {
      background: var(--wavy-bg-surface, #11192a);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-pill, 9999px);
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
    }
    .bell,
    .mail,
    .user-menu {
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      border: 0;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      border-radius: var(--wavy-radius-pill, 9999px);
      text-decoration: none;
    }
    .bell:hover,
    .bell:focus-visible,
    .mail:hover,
    .mail:focus-visible,
    .user-menu:hover,
    .user-menu:focus-visible {
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      outline: none;
      background: rgba(34, 211, 238, 0.06);
    }
    .bell {
      position: relative;
    }
    .dot.violet {
      position: absolute;
      top: 4px;
      right: 4px;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--wavy-signal-violet, #7c3aed);
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
    .user-email {
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
    }
    @media (max-width: 600px) {
      .user-email {
        display: none;
      }
    }
    svg {
      width: 16px;
      height: 16px;
    }
  `;

  constructor() {
    super();
    this.signedIn = false;
    this.locale = "en";
    this.address = "";
    this.userName = "";
    this.unreadCount = 0;
  }

  _initials() {
    const source = (this.userName || this.address || "").trim();
    if (!source) return "?";
    const local = source.split("@")[0];
    if (!local) return "?";
    if (local.includes(".")) {
      const parts = local.split(".").filter((part) => part);
      if (parts.length > 0) {
        const first = parts[0];
        const last = parts[parts.length - 1];
        return ((first[0] || "") + (last[0] || "")).toUpperCase();
      }
    }
    return local.slice(0, 2).toUpperCase();
  }

  _onLocaleChange(evt) {
    const next = evt.target.value;
    this.locale = next;
    this.dispatchEvent(
      new CustomEvent("wavy-locale-changed", {
        bubbles: true,
        composed: true,
        detail: { locale: next }
      })
    );
  }

  _onUserMenuClick() {
    this.dispatchEvent(
      new CustomEvent("wavy-user-menu-requested", {
        bubbles: true,
        composed: true,
        detail: { address: this.address }
      })
    );
  }

  render() {
    const initials = this._initials();
    const hasUnread = (this.unreadCount || 0) > 0;
    return html`
      <a class="brand" href="/" aria-label="SupaWave home">
        <span class="brand-dot" aria-hidden="true"></span>
        <span class="brand-text">SupaWave</span>
      </a>

      <select
        class="locale"
        aria-label="Language"
        .value=${this.locale}
        @change=${this._onLocaleChange}
      >
        ${WavyHeader.LOCALES.map(
          (loc) =>
            html`<option value=${loc.code} ?selected=${loc.code === this.locale}>
              ${loc.label}
            </option>`
        )}
      </select>

      ${this.signedIn
        ? html`
            <button
              type="button"
              class="bell"
              aria-label="Notifications"
            >
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true">
                <path d="M3 12h10l-1.4-1.4A2 2 0 0 1 11 9.2V7a3 3 0 0 0-6 0v2.2a2 2 0 0 1-.6 1.4L3 12z" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M6.5 13.5a1.5 1.5 0 0 0 3 0" stroke-linecap="round"/>
              </svg>
              <span class="dot violet" ?hidden=${!hasUnread} aria-hidden="true"></span>
            </button>

            <a class="mail" href="/?q=in:inbox" aria-label="Inbox">
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true">
                <rect x="2" y="3.5" width="12" height="9" rx="1.4"/>
                <path d="M2.5 4.5l5.5 4 5.5-4" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </a>

            <button
              type="button"
              class="user-menu"
              aria-haspopup="menu"
              aria-label="Open user menu"
              @click=${this._onUserMenuClick}
            >
              <span class="avatar" aria-hidden="true">${initials}</span>
              <span class="user-email">${this.address || ""}</span>
            </button>
          `
        : null}
    `;
  }
}

if (!customElements.get("wavy-header")) {
  customElements.define("wavy-header", WavyHeader);
}
