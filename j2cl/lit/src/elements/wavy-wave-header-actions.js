import { LitElement, css, html } from "lit";
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

export class WavyWaveHeaderActions extends LitElement {
  static properties = {
    sourceWaveId: { type: String, attribute: "source-wave-id", reflect: true },
    participants: { attribute: false },
    public: { type: Boolean, reflect: true },
    lockState: { type: String, attribute: "lock-state", reflect: true },
    disabled: { type: Boolean, reflect: true },
    _addDialogOpen: { state: true },
    _participantDraft: { state: true }
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
          Add participant
        </button>
        <button
          type="button"
          data-action="new-with-participants"
          aria-label="New wave with current participants"
          ?disabled=${unavailable}
          @click=${this._newWithParticipants}
        >
          New with participants
        </button>
        <button
          type="button"
          data-action="publicity-toggle"
          aria-label=${this.public ? "Make wave private" : "Make wave public"}
          aria-pressed=${this.public ? "true" : "false"}
          ?disabled=${unavailable}
          @click=${this._confirmPublicityToggle}
        >
          ${this.public ? "Public" : "Private"}
        </button>
        <button
          type="button"
          data-action="lock-toggle"
          data-lock-state=${lockState}
          aria-label=${this._lockLabel(lockState)}
          ?disabled=${unavailable}
          @click=${this._confirmLockToggle}
        >
          ${this._lockText(lockState)}
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

  _openAddParticipant(event) {
    event.stopPropagation();
    if (this._unavailable()) return;
    this._addDialogOpen = true;
  }

  _closeAddParticipant(event) {
    if (event) event.stopPropagation();
    this._addDialogOpen = false;
    this._participantDraft = "";
  }

  _onParticipantDraftInput(event) {
    this._participantDraft = event.target.value;
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
    const requestId = [
      "wavy-wave-header-actions",
      kind,
      String(this.sourceWaveId || ""),
      String(Date.now())
    ].join(":");
    this._pendingConfirm = { requestId, eventName, detail };
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
    this._emit(pending.eventName, pending.detail);
  }

  _emit(eventName, extra) {
    this.dispatchEvent(
      new CustomEvent(eventName, {
        bubbles: true,
        composed: true,
        detail: {
          sourceWaveId: this.sourceWaveId,
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
