import { LitElement, css, html, svg } from "lit";
import { ensureWavyConfirmDialogMounted } from "./wavy-confirm-dialog.js";

const LOCK_CYCLE = {
  unlocked: "root",
  root: "all",
  all: "unlocked"
};

function normalizeLockState(value) {
  const normalized = String(value || "").trim().toLowerCase();
  return normalized === "root" || normalized === "all" ? normalized : "unlocked";
}

function participantList(value) {
  if (Array.isArray(value)) {
    return value.map((entry) => String(entry || "").trim()).filter(Boolean);
  }
  if (typeof value === "string") {
    return value.split(",").map((entry) => entry.trim()).filter(Boolean);
  }
  return [];
}

function uniqueValues(values) {
  const seen = new Set();
  const result = [];
  for (const value of values) {
    if (seen.has(value)) continue;
    seen.add(value);
    result.push(value);
  }
  return result;
}

function actionIcon(name) {
  const paths = {
    addParticipant: svg`<path d="M8 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z"></path>
      <path d="M2.5 18a5.5 5.5 0 0 1 11 0"></path>
      <path d="M17 8v6"></path>
      <path d="M14 11h6"></path>`,
    newWave: svg`<path d="M4 5.5h12a3 3 0 0 1 3 3v5a3 3 0 0 1-3 3H9l-5 3v-11a3 3 0 0 1 3-3Z"></path>
      <path d="M11.5 8.5v5"></path>
      <path d="M9 11h5"></path>`,
    public: svg`<path d="M12 20a8 8 0 1 0 0-16 8 8 0 0 0 0 16Z"></path>
      <path d="M4.5 9h15"></path>
      <path d="M4.5 15h15"></path>
      <path d="M12 4a12 12 0 0 1 0 16"></path>
      <path d="M12 4a12 12 0 0 0 0 16"></path>`,
    lock: svg`<rect x="5" y="10" width="14" height="10" rx="2"></rect>
      <path d="M8 10V7a4 4 0 0 1 8 0v3"></path>`
  };
  return svg`<svg
    aria-hidden="true"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
  >${paths[name]}</svg>`;
}

export class WavyWaveHeaderActions extends LitElement {
  static properties = {
    sourceWaveId: { type: String, attribute: "source-wave-id", reflect: true },
    participants: { attribute: false },
    public: { type: Boolean, reflect: true },
    lockState: { type: String, attribute: "lock-state", reflect: true },
    disabled: { type: Boolean, reflect: true },
    _addDialogOpen: { state: true },
    _participantDraft: { state: true },
    _contactSuggestions: { state: true },
    _contactsLoaded: { state: true },
    _contactsLoading: { state: true }
  };

  static styles = css`
    :host {
      display: block;
      box-sizing: border-box;
      background: linear-gradient(
        90deg,
        var(--wavy-toolbar-pill-bg, #f0f4f8),
        var(--wavy-bg-elevated, #ffffff)
      );
      border-bottom: 1px solid var(--wavy-border-hairline, #e2e8f0);
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-2, 8px);
    }

    .row {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: var(--wavy-spacing-2, 8px);
    }

    button {
      border: 1px solid var(--wavy-border-hairline, #e2e8f0);
      border-radius: 999px;
      background: var(--wavy-bg-base, #ffffff);
      color: var(--wavy-text-body, #1a202c);
      cursor: pointer;
      font: var(--wavy-type-meta, 12px / 1.35 Arial, sans-serif);
      min-height: 30px;
      padding: 0 var(--wavy-spacing-3, 12px);
      transition: background-color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        border-color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }

    button svg {
      width: 18px;
      height: 18px;
      display: block;
      color: currentColor;
      stroke: currentColor;
      stroke-width: 2.4;
      overflow: visible;
      pointer-events: none;
    }

    .row > button[data-action] {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 42px;
      padding: 0;
      background: var(--wavy-signal-cyan-soft, #e6f6ff);
      border-color: rgba(0, 119, 182, 0.32);
      color: var(--wavy-signal-cyan-strong, #005f93);
    }

    .action-label {
      position: absolute;
      width: 1px;
      height: 1px;
      margin: -1px;
      padding: 0;
      overflow: hidden;
      clip: rect(0 0 0 0);
      clip-path: inset(50%);
      white-space: nowrap;
      border: 0;
    }

    button:hover:not([disabled]) {
      background: var(--wavy-toolbar-tile-hover-bg, rgba(0, 119, 182, 0.08));
      border-color: var(--wavy-signal-cyan, #0077b6);
      color: var(--wavy-signal-cyan, #0077b6);
    }

    button:focus-visible,
    input:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px rgba(0, 119, 182, 0.16));
    }

    button[disabled] {
      cursor: not-allowed;
      opacity: 0.48;
    }

    button[data-action="publicity-toggle"][aria-pressed="true"],
    button[data-action="lock-toggle"][data-lock-state="root"],
    button[data-action="lock-toggle"][data-lock-state="all"] {
      background: var(--wavy-signal-amber-soft, #fff4e5);
      border-color: var(--wavy-signal-amber, #9a6700);
      color: var(--wavy-signal-amber, #9a6700);
    }

    .add-popover {
      display: grid;
      gap: var(--wavy-spacing-2, 8px);
      margin-top: var(--wavy-spacing-2, 8px);
      max-width: 460px;
      padding: var(--wavy-spacing-3, 12px);
      border: 1px solid var(--wavy-border-hairline, #e2e8f0);
      border-radius: var(--wavy-radius-card, 10px);
      background: var(--wavy-bg-elevated, #ffffff);
      box-shadow: var(--wavy-shadow-card, 0 8px 24px rgba(15, 23, 42, 0.12));
    }

    label {
      display: grid;
      gap: var(--wavy-spacing-1, 4px);
      color: var(--wavy-text-muted, #64748b);
      font: var(--wavy-type-meta, 12px / 1.35 Arial, sans-serif);
    }

    input {
      border: 1px solid var(--wavy-border-hairline, #e2e8f0);
      border-radius: var(--wavy-radius-input, 8px);
      color: var(--wavy-text-body, #1a202c);
      font: var(--wavy-type-body, 14px / 1.45 Arial, sans-serif);
      padding: var(--wavy-spacing-2, 8px);
    }

    .add-actions {
      display: flex;
      gap: var(--wavy-spacing-2, 8px);
      justify-content: flex-end;
    }

    .suggestions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--wavy-spacing-1, 4px);
    }

    .suggestions-label {
      flex-basis: 100%;
      color: var(--wavy-text-muted, #64748b);
      font: var(--wavy-type-meta, 12px / 1.35 Arial, sans-serif);
    }

    .suggestion {
      min-height: 26px;
      padding: 0 var(--wavy-spacing-2, 8px);
    }
  `;

  constructor() {
    super();
    this.sourceWaveId = "";
    this.participants = [];
    this.public = false;
    this.lockState = "unlocked";
    this.disabled = false;
    this._addDialogOpen = false;
    this._participantDraft = "";
    this._contactSuggestions = [];
    this._contactsLoaded = false;
    this._contactsLoading = false;
    this._pendingConfirm = null;
    this._onConfirmResolved = this._onConfirmResolved.bind(this);
  }

  connectedCallback() {
    super.connectedCallback();
    document.body.addEventListener("wavy-confirm-resolved", this._onConfirmResolved);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    document.body.removeEventListener("wavy-confirm-resolved", this._onConfirmResolved);
  }

  willUpdate(changedProperties) {
    if (changedProperties.has("sourceWaveId")) {
      this._pendingConfirm = null;
      this._addDialogOpen = false;
      this._participantDraft = "";
    }
  }

  render() {
    const unavailable = this._unavailable();
    const lockState = normalizeLockState(this.lockState);
    return html`
      <div class="row" role="toolbar" aria-label="Wave header actions">
        <button
          type="button"
          data-action="add-participant"
          aria-label="Add participant"
          ?disabled=${unavailable}
          @click=${this._openAddParticipant}
        >
          ${actionIcon("addParticipant")}
          <span class="action-label">Add participant</span>
        </button>
        <button
          type="button"
          data-action="new-with-participants"
          aria-label="New wave with current participants"
          ?disabled=${unavailable}
          @click=${this._newWithParticipants}
        >
          ${actionIcon("newWave")}
          <span class="action-label">New with participants</span>
        </button>
        <button
          type="button"
          data-action="publicity-toggle"
          aria-label=${this.public ? "Make wave private" : "Make wave public"}
          aria-pressed=${this.public ? "true" : "false"}
          ?disabled=${unavailable}
          @click=${this._confirmPublicityToggle}
        >
          ${actionIcon("public")}
          <span class="action-label">${this.public ? "Public" : "Private"}</span>
        </button>
        <button
          type="button"
          data-action="lock-toggle"
          data-lock-state=${lockState}
          aria-label=${this._lockLabel(lockState)}
          ?disabled=${unavailable}
          @click=${this._confirmLockToggle}
        >
          ${actionIcon("lock")}
          <span class="action-label">${this._lockText(lockState)}</span>
        </button>
      </div>
      ${this._addDialogOpen ? this._renderAddParticipantDialog() : ""}
    `;
  }

  _renderAddParticipantDialog() {
    return html`
      <form class="add-popover" @submit=${this._submitAddParticipant}>
        <label>
          Participant addresses
          <input
            name="participant-addresses"
            autocomplete="off"
            placeholder="alice@example.com, bob@example.com"
            .value=${this._participantDraft}
            @input=${this._onParticipantDraftInput}
          />
        </label>
        ${this._renderContactSuggestions()}
        <div class="add-actions">
          <button
            type="button"
            data-action="add-participant-cancel"
            @click=${this._closeAddParticipant}
          >
            Cancel
          </button>
          <button
            type="submit"
            data-action="add-participant-submit"
            ?disabled=${this._addParticipantAddresses().length === 0}
          >
            Add
          </button>
        </div>
      </form>
    `;
  }

  _unavailable() {
    return this.disabled || String(this.sourceWaveId || "").trim() === "";
  }

  _regularParticipants() {
    return uniqueValues(
      participantList(this.participants).filter((participant) => !participant.startsWith("@"))
    );
  }

  _addParticipantAddresses() {
    return uniqueValues(
      String(this._participantDraft || "")
        .split(",")
        .map((address) => address.trim())
        .filter(Boolean)
    );
  }

  _renderContactSuggestions() {
    const suggestions = this._availableContactSuggestions();
    if (this._contactsLoading) {
      return html`<div class="suggestions" aria-live="polite">Loading frequent contacts...</div>`;
    }
    if (suggestions.length === 0) {
      return "";
    }
    return html`
      <div class="suggestions" aria-label="Frequent contacts">
        <span class="suggestions-label">Frequent contacts</span>
        ${suggestions.map(
          (address) => html`
            <button
              type="button"
              class="suggestion"
              data-contact-suggestion=${address}
              @click=${() => this._appendSuggestedParticipant(address)}
            >
              ${address}
            </button>
          `
        )}
      </div>
    `;
  }

  _availableContactSuggestions() {
    const current = new Set(this._regularParticipants().map((value) => value.toLowerCase()));
    const draft = new Set(this._addParticipantAddresses().map((value) => value.toLowerCase()));
    return uniqueValues(this._contactSuggestions || []).filter((address) => {
      const normalized = String(address || "").trim().toLowerCase();
      return (
        normalized &&
        !normalized.startsWith("@") &&
        !current.has(normalized) &&
        !draft.has(normalized)
      );
    });
  }

  _appendSuggestedParticipant(address) {
    const values = this._addParticipantAddresses();
    values.push(address);
    this._participantDraft = uniqueValues(values).join(", ");
  }

  _openAddParticipant(event) {
    event.stopPropagation();
    if (this._unavailable()) return;
    this._addDialogOpen = true;
    this._loadContactSuggestions();
  }

  _closeAddParticipant(event) {
    if (event) event.stopPropagation();
    this._addDialogOpen = false;
    this._participantDraft = "";
  }

  _onParticipantDraftInput(event) {
    this._participantDraft = event.target.value;
  }

  async _loadContactSuggestions() {
    if (this._contactsLoaded || this._contactsLoading || typeof fetch !== "function") {
      return;
    }
    this._contactsLoading = true;
    try {
      const response = await fetch("/contacts?timestamp=0");
      if (!response || !response.ok) {
        throw new Error("contacts request failed");
      }
      const payload = await response.json();
      const contacts = Array.isArray(payload && payload.contacts) ? payload.contacts : [];
      this._contactSuggestions = uniqueValues(
        contacts
          .map((entry) => String(entry && entry.participant ? entry.participant : "").trim())
          .filter(Boolean)
      );
      this._contactsLoaded = true;
    } catch (_err) {
      this._contactSuggestions = [];
      this._contactsLoaded = true;
    } finally {
      this._contactsLoading = false;
    }
  }

  _submitAddParticipant(event) {
    event.preventDefault();
    event.stopPropagation();
    if (this._unavailable()) return;
    const addresses = this._addParticipantAddresses();
    if (addresses.length === 0) return;
    this._emit("wave-add-participant-requested", { addresses });
    this._closeAddParticipant();
  }

  _newWithParticipants(event) {
    event.stopPropagation();
    if (this._unavailable()) return;
    this._emit("wave-new-with-participants-requested", {
      participants: this._regularParticipants()
    });
  }

  _confirmPublicityToggle(event) {
    event.stopPropagation();
    if (this._unavailable()) return;
    const currentlyPublic = !!this.public;
    const nextPublic = !currentlyPublic;
    this._requestConfirmation(
      "publicity",
      currentlyPublic ? "Make this wave private?" : "Make this wave public?",
      currentlyPublic ? "Make private" : "Make public",
      "wave-publicity-toggle-requested",
      { currentlyPublic, nextPublic }
    );
  }

  _confirmLockToggle(event) {
    event.stopPropagation();
    if (this._unavailable()) return;
    const currentLockState = normalizeLockState(this.lockState);
    const nextLockState = LOCK_CYCLE[currentLockState] || "unlocked";
    this._requestConfirmation(
      "lock",
      this._lockConfirmMessage(currentLockState, nextLockState),
      this._lockConfirmLabel(nextLockState),
      "wave-root-lock-toggle-requested",
      { currentLockState, nextLockState }
    );
  }

  _requestConfirmation(kind, message, confirmLabel, eventName, detail) {
    ensureWavyConfirmDialogMounted();
    const sourceWaveId = String(this.sourceWaveId || "");
    const requestId = [
      "wavy-wave-header-actions",
      kind,
      sourceWaveId,
      String(Date.now())
    ].join(":");
    this._pendingConfirm = { requestId, eventName, detail, sourceWaveId };
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-requested", {
        bubbles: true,
        composed: true,
        detail: {
          requestId,
          message,
          confirmLabel,
          cancelLabel: "Cancel",
          tone: kind === "lock" || detail.nextPublic === false ? "destructive" : "default"
        }
      })
    );
  }

  _onConfirmResolved(event) {
    const pending = this._pendingConfirm;
    if (!pending || event.detail?.requestId !== pending.requestId) return;
    this._pendingConfirm = null;
    if (!event.detail?.confirmed) return;
    this._emit(pending.eventName, pending.detail, pending.sourceWaveId);
  }

  _emit(eventName, extra, sourceWaveIdOverride) {
    this.dispatchEvent(
      new CustomEvent(eventName, {
        bubbles: true,
        composed: true,
        detail: {
          sourceWaveId: sourceWaveIdOverride != null ? sourceWaveIdOverride : this.sourceWaveId,
          ...(extra || {})
        }
      })
    );
  }

  _lockLabel(lockState) {
    if (lockState === "root") return "Lock full wave";
    if (lockState === "all") return "Unlock wave";
    return "Lock root blip";
  }

  _lockText(lockState) {
    if (lockState === "root") return "Root locked";
    if (lockState === "all") return "Wave locked";
    return "Unlocked";
  }

  _lockConfirmLabel(nextLockState) {
    if (nextLockState === "root") return "Lock root";
    if (nextLockState === "all") return "Lock all";
    return "Unlock";
  }

  _lockConfirmMessage(currentLockState, nextLockState) {
    if (nextLockState === "root") return "Lock the root blip?";
    if (nextLockState === "all") return "Lock the full wave?";
    return `Unlock this wave from ${currentLockState} lock?`;
  }
}

if (!customElements.get("wavy-wave-header-actions")) {
  customElements.define("wavy-wave-header-actions", WavyWaveHeaderActions);
}
