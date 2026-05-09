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
 * - A.6 Inbox/mail icon — jumps to inbox via /?view=j2cl-root&q=in:inbox
 * - A.7 User-menu trigger: avatar chip (initials) + visible email span;
 *       click emits wavy-user-menu-requested for the F-0 menu sheet
 *       (the menu items A.8–A.18 themselves are owned by F-0 and are
 *       NOT mounted by this slice — only the trigger is)
 *
 * Public API:
 * - signedIn          — boolean (controls visibility of A.5/A.6/A.7)
 * - locale            — current locale code (defaults to "en")
 * - address           — user's email address (data-address; reflected so
 *                       server-side templates can pass it through)
 * - userName          — display name fallback for initials (defaults to address)
 * - unreadCount       — number; A.5 dot becomes visible when > 0
 * - compact-gwt-topbar — boolean; activates GWT-parity compact mode: hides the
 *                        full email span, shortens locale labels, and reduces
 *                        icon sizing to match the 40px GWT root topbar
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
    unreadCount: { type: Number, attribute: "unread-count" },
    basePath: { type: String, attribute: "base-path" },
    compactGwtTopbar: { type: Boolean, attribute: "compact-gwt-topbar", reflect: true },
    connectionState: { type: String, attribute: "data-connection-state", reflect: true },
    saveState: { type: String, attribute: "data-save-state", reflect: true },
    // V-1 (#1099): when the host carries no-brand the inner SupaWave
    // brand link is suppressed so the J2CL root shell can render the
    // canonical brand from shell-header > [slot="brand"] without
    // double-branding. The light-DOM brand is still SSR'd so the F-2
    // wavyHeaderInnerLightDomEmitsBrandLocaleBellMailUserMenuChrome
    // parity test continues to pass; the shadow render simply omits
    // it post-upgrade and a sibling rule in shell-tokens.css hides
    // the SSR brand pre-upgrade.
    noBrand: { type: Boolean, attribute: "no-brand", reflect: true }
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
    /* V-1 (#1099): host-level opt-out for the inner brand link so the
     * J2CL root shell can render its canonical brand exactly once. */
    :host([no-brand]) .brand {
      display: none;
    }
    :host([compact-gwt-topbar]) {
      gap: 8px;
      flex-wrap: nowrap;
      color: #fff;
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
    :host([compact-gwt-topbar]) .locale {
      width: 40px;
      height: 32px;
      min-width: 40px;
      border: 0;
      border-radius: 6px;
      padding: 0 4px;
      color: #fff;
      background: rgba(255, 255, 255, 0.10);
      background-image: none;
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
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
    :host([compact-gwt-topbar]) .bell,
    :host([compact-gwt-topbar]) .mail,
    :host([compact-gwt-topbar]) .user-menu {
      width: 32px;
      height: 32px;
      min-width: 32px;
      padding: 0;
      justify-content: center;
      color: #fff;
      background: rgba(255, 255, 255, 0.10);
      border-radius: 6px;
    }
    .savestatus,
    .netstatus {
      display: none;
    }
    :host([compact-gwt-topbar]) .savestatus,
    :host([compact-gwt-topbar]) .netstatus {
      position: relative;
      box-sizing: border-box;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      min-width: 32px;
      padding: 0;
      border: 0;
      border-radius: 6px;
      color: #fff;
      background: rgba(255, 255, 255, 0.10);
      cursor: default;
    }
    :host([compact-gwt-topbar]) .savestatus svg,
    :host([compact-gwt-topbar]) .netstatus svg {
      width: 20px;
      height: 20px;
      stroke: #fff;
      color: #fff;
    }
    :host([compact-gwt-topbar]) .savestatus::after,
    :host([compact-gwt-topbar]) .netstatus::after {
      content: "";
      display: none;
      position: absolute;
      top: 5px;
      right: 5px;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      pointer-events: none;
    }
    :host([compact-gwt-topbar]) .savestatus[data-state="saved"]::after {
      display: block;
      background: #48bb78;
      box-shadow: 0 0 4px #48bb78;
    }
    :host([compact-gwt-topbar]) .savestatus[data-state="saving"]::after,
    :host([compact-gwt-topbar]) .savestatus[data-state="unsaved"]::after {
      display: block;
      background: #ecc94b;
      box-shadow: 0 0 4px #ecc94b;
    }
    :host([compact-gwt-topbar]) .netstatus[data-state="online"]::after {
      display: block;
      background: #48bb78;
      box-shadow: 0 0 4px #48bb78;
    }
    :host([compact-gwt-topbar]) .netstatus[data-state="connecting"]::after {
      display: block;
      background: #ecc94b;
      box-shadow: 0 0 4px #ecc94b;
    }
    :host([compact-gwt-topbar]) .netstatus[data-state="offline"]::after {
      display: block;
      background: #fc8181;
      box-shadow: 0 0 4px #fc8181;
    }
    :host([compact-gwt-topbar]) .savestatus:hover,
    :host([compact-gwt-topbar]) .netstatus:hover {
      background: rgba(255, 255, 255, 0.18);
      transform: scale(1.05);
    }
    :host([compact-gwt-topbar]) .bell:focus-visible,
    :host([compact-gwt-topbar]) .mail:focus-visible,
    :host([compact-gwt-topbar]) .user-menu:focus-visible {
      outline: 2px solid rgba(255, 255, 255, 0.95);
      outline-offset: 2px;
      background: rgba(255, 255, 255, 0.18);
    }
    :host([compact-gwt-topbar]) .bell:hover,
    :host([compact-gwt-topbar]) .mail:hover,
    :host([compact-gwt-topbar]) .user-menu:hover {
      color: #fff;
      outline: none;
      background: rgba(255, 255, 255, 0.18);
      transform: scale(1.05);
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
    :host([compact-gwt-topbar]) .user-email {
      /* GWT topbar parity: compact chrome keeps the avatar chip but hides the long email. */
      display: none;
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
    this.basePath = "/";
    this.compactGwtTopbar = false;
    this.noBrand = false;
    this.connectionState = "online";
    this.saveState = "saved";
  }

  _normalizedBasePath() {
    const raw = (this.basePath || "/").trim();
    if (!raw || raw === "/") return "/";
    const withLeading = raw.startsWith("/") ? raw : `/${raw}`;
    return withLeading.endsWith("/") ? withLeading.slice(0, -1) : withLeading;
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

  _saveLabel() {
    if (this.saveState === "saving") return "Saving changes";
    if (this.saveState === "unsaved") return "Unsaved changes";
    return "All changes saved";
  }

  _netLabel() {
    if (this.connectionState === "offline") return "Offline";
    if (this.connectionState === "connecting") return "Connecting";
    return "Online";
  }

  render() {
    const initials = this._initials();
    const hasUnread = (this.unreadCount || 0) > 0;
    const base = this._normalizedBasePath();
    const compact = this.compactGwtTopbar;
    return html`
      ${this.noBrand
        ? null
        : html`
            <a class="brand" href=${base} aria-label="SupaWave home">
              <span class="brand-dot" aria-hidden="true"></span>
              <span class="brand-text">SupaWave</span>
            </a>
          `}

      <select
        class="locale"
        aria-label="Language"
        .value=${this.locale}
        @change=${this._onLocaleChange}
      >
        ${WavyHeader.LOCALES.map(
          (loc) =>
            html`<option value=${loc.code} ?selected=${loc.code === this.locale}>
              ${this.compactGwtTopbar ? loc.code.replace("_", "-").toUpperCase() : loc.label}
            </option>`
        )}
      </select>

      ${compact
        ? html`
            <span
              class="savestatus"
              role="img"
              data-state=${this.saveState || "saved"}
              title=${this._saveLabel()}
              aria-label=${this._saveLabel()}
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"/>
                <path d="M9 15l2 2 4-4" stroke-width="2"/>
              </svg>
            </span>
            <span
              class="netstatus"
              role="img"
              data-state=${this.connectionState || "online"}
              title=${this._netLabel()}
              aria-label=${this._netLabel()}
            >
              ${this.connectionState === "offline"
                ? html`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <line x1="1" y1="1" x2="23" y2="23"/>
                    <path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55"/>
                    <path d="M5 12.55a10.94 10.94 0 0 1 5.17-2.39"/>
                    <path d="M10.71 5.05A16 16 0 0 1 22.56 9"/>
                    <path d="M1.42 9a15.91 15.91 0 0 1 4.7-2.88"/>
                    <path d="M8.53 16.11a6 6 0 0 1 6.95 0"/>
                    <circle cx="12" cy="19.5" r="1"/>
                  </svg>`
                : html`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M1.42 9a16 16 0 0 1 21.16 0"/>
                    <path d="M5.07 12.5a10 10 0 0 1 13.86 0"/>
                    <path d="M8.72 16a6 6 0 0 1 6.56 0"/>
                    <circle cx="12" cy="19.5" r="1"/>
                  </svg>`}
            </span>
          `
        : null}

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

            <a class="mail" href=${`${base}?view=j2cl-root&q=in:inbox`} aria-label="Inbox">
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
