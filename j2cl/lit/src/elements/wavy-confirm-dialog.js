import { LitElement, css, html } from "lit";

/**
 * <wavy-confirm-dialog> — F-3.S4 (#1038, R-5.6) styled confirm modal
 * used by the compose surface to ask "Delete this blip?" without
 * resorting to Window.confirm (forbidden per project memory rule
 * `feedback_no_browser_popups.md`).
 *
 * Dispatcher contract:
 * - Any component (e.g. the J2CL compose view) dispatches a
 *   `wavy-confirm-requested` CustomEvent on `document.body` carrying
 *   `{message, confirmLabel, cancelLabel, requestId, tone}`.
 * - The dialog mounts at body level once via the `ensureMounted()`
 *   helper exposed from the module.
 * - The dialog listens for the request, opens with the supplied
 *   labels, and on user resolution emits a body-level
 *   `wavy-confirm-resolved` CustomEvent with `{requestId, confirmed}`.
 *
 * Style:
 * - Uses the F-0 wavy tokens (--wavy-*). Centered modal with a soft
 *   backdrop. Confirm button defaults to signal-amber tone for
 *   destructive actions; can be overridden via the `tone` field
 *   (`destructive` | `default`).
 *
 * Accessibility:
 * - role="dialog", aria-modal="true", aria-labelledby="wavy-confirm-title".
 * - On open, focus moves to the cancel button.
 * - Escape resolves with confirmed=false; Enter resolves with
 *   confirmed=true.
 */

const ACTIVE_REQUESTS = new Map();

export class WavyConfirmDialog extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    message: { type: String },
    confirmLabel: { type: String, attribute: "confirm-label" },
    cancelLabel: { type: String, attribute: "cancel-label" },
    tone: { type: String },
    requestId: { type: String, attribute: "request-id" }
  };

  static styles = css`
    :host {
      display: contents;
    }
    /* F-3.S4 (#1038, R-5.6): styling uses /* ... *\/ comments only —
     * no back-tick characters appear inside the css\` ... \` template
     * literal (the F-3.S3 footgun called out in the slice plan). */
    :host([open]) .backdrop {
      display: grid;
    }
    .backdrop {
      display: none;
      position: fixed;
      inset: 0;
      background: rgba(8, 14, 28, 0.62);
      place-items: center;
      z-index: 1100;
    }
    .dialog {
      min-width: 280px;
      max-width: min(92vw, 420px);
      padding: var(--wavy-spacing-4, 20px);
      border-radius: var(--wavy-radius-card, 12px);
      background: var(--wavy-bg-elevated, #11192d);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      box-shadow: var(--wavy-shadow-card, 0 16px 36px rgba(0, 0, 0, 0.32));
      display: grid;
      gap: var(--wavy-spacing-3, 12px);
    }
    .title {
      margin: 0;
      font: var(--wavy-type-title, 600 1rem / 1.4 sans-serif);
    }
    .message {
      margin: 0;
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.62));
      font: var(--wavy-type-body, 0.9375rem / 1.5 sans-serif);
    }
    .actions {
      display: flex;
      gap: var(--wavy-spacing-2, 8px);
      justify-content: flex-end;
    }
    button {
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-3, 12px);
      border-radius: var(--wavy-radius-pill, 9999px);
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      background: transparent;
      color: inherit;
      font: var(--wavy-type-button, 0.875rem / 1.4 sans-serif);
      cursor: pointer;
    }
    button:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }
    button[data-confirm-tone="destructive"] {
      background: var(--wavy-signal-amber-soft, rgba(245, 158, 11, 0.18));
      border-color: var(--wavy-signal-amber, #f59e0b);
      color: var(--wavy-signal-amber, #f59e0b);
    }
    button[data-confirm-tone="default"] {
      background: var(--wavy-signal-cyan-soft, rgba(34, 211, 238, 0.18));
      border-color: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-signal-cyan, #22d3ee);
    }
  `;

  constructor() {
    super();
    this.open = false;
    this.message = "";
    this.confirmLabel = "Confirm";
    this.cancelLabel = "Cancel";
    this.tone = "default";
    this.requestId = "";
    this._handleRequest = (event) => this._onRequest(event);
    this._handleKeyDown = (event) => this._onKeyDown(event);
  }

  connectedCallback() {
    super.connectedCallback();
    document.body.addEventListener("wavy-confirm-requested", this._handleRequest);
    document.addEventListener("keydown", this._handleKeyDown);
  }

  disconnectedCallback() {
    document.body.removeEventListener("wavy-confirm-requested", this._handleRequest);
    document.removeEventListener("keydown", this._handleKeyDown);
    super.disconnectedCallback();
  }

  _onRequest(event) {
    const detail = event.detail || {};
    const nextRequestId = detail.requestId || "";
    // review-1077 Bug 7: if the dialog is already open for a prior
    // request, resolve that request as cancelled before swapping in
    // the new payload. Without this, the prior caller's
    // wavy-confirm-resolved promise would never settle and the
    // ACTIVE_REQUESTS map would leak the stale entry. Compare by
    // requestId so back-to-back requests with the same id (e.g. a
    // Lit re-render) do not cancel themselves.
    if (this.open && this.requestId && this.requestId !== nextRequestId) {
      const supersededRequestId = this.requestId;
      ACTIVE_REQUESTS.delete(supersededRequestId);
      try {
        document.body.dispatchEvent(
          new CustomEvent("wavy-confirm-resolved", {
            bubbles: true,
            composed: true,
            detail: { requestId: supersededRequestId, confirmed: false }
          })
        );
      } catch (ignored) {
        // Resolution dispatch is observational; swallow errors so the
        // new request still opens cleanly even if the body is gone.
      }
    }
    this.message = detail.message || "Are you sure?";
    this.confirmLabel = detail.confirmLabel || "Confirm";
    this.cancelLabel = detail.cancelLabel || "Cancel";
    this.tone = detail.tone === "destructive" ? "destructive" : "default";
    this.requestId = nextRequestId;
    this.open = true;
    if (this.requestId) {
      ACTIVE_REQUESTS.set(this.requestId, true);
    }
    this.updateComplete.then(() => {
      const cancelBtn =
        this.renderRoot &&
        this.renderRoot.querySelector('button[data-confirm-action="cancel"]');
      if (cancelBtn) cancelBtn.focus();
    });
  }

  _onKeyDown(event) {
    if (!this.open) return;
    if (event.key === "Escape") {
      event.preventDefault();
      this._resolve(false);
      return;
    }
    if (event.key === "Enter") {
      // In a real browser, keydown fires on the focused element and
      // bubbles up.  event.composedPath()[0] is therefore the element
      // that physically received the key — the same as the focused
      // element — even across Shadow DOM boundaries.  Using this rather
      // than document.activeElement / shadowRoot.activeElement avoids
      // the shadow-host indirection: Enter only confirms when the
      // confirm button itself is the key origin (not the cancel button).
      const path = event.composedPath ? event.composedPath() : [];
      const keyOrigin = path[0];
      if (
        keyOrigin &&
        keyOrigin.getAttribute &&
        keyOrigin.getAttribute("data-confirm-action") === "confirm"
      ) {
        event.preventDefault();
        this._resolve(true);
      }
    }
  }

  _resolve(confirmed) {
    if (!this.open) return;
    const requestId = this.requestId;
    this.open = false;
    if (requestId) {
      ACTIVE_REQUESTS.delete(requestId);
    }
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-resolved", {
        bubbles: true,
        composed: true,
        detail: { requestId, confirmed: !!confirmed }
      })
    );
  }

  render() {
    return html`
      <div
        class="backdrop"
        role="dialog"
        aria-modal="true"
        aria-labelledby="wavy-confirm-title"
        data-wavy-confirm-dialog="true"
        data-confirm-open=${this.open ? "true" : "false"}
      >
        <div class="dialog">
          <h2 class="title" id="wavy-confirm-title">${this.message}</h2>
          <div class="actions">
            <button
              type="button"
              data-confirm-action="cancel"
              data-confirm-tone="default"
              @click=${() => this._resolve(false)}
            >${this.cancelLabel}</button>
            <button
              type="button"
              data-confirm-action="confirm"
              data-confirm-tone=${this.tone}
              @click=${() => this._resolve(true)}
            >${this.confirmLabel}</button>
          </div>
        </div>
      </div>
    `;
  }
}

if (!customElements.get("wavy-confirm-dialog")) {
  customElements.define("wavy-confirm-dialog", WavyConfirmDialog);
}

/**
 * Mount one `<wavy-confirm-dialog>` instance at the bottom of
 * document.body if not already present. Idempotent — safe to call
 * from multiple consumers.
 */
export function ensureWavyConfirmDialogMounted() {
  if (typeof document === "undefined" || !document.body) return null;
  let existing = document.body.querySelector("wavy-confirm-dialog");
  if (existing) return existing;
  existing = document.createElement("wavy-confirm-dialog");
  document.body.appendChild(existing);
  return existing;
}
