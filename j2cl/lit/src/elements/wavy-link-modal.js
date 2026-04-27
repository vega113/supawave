import { LitElement, css, html } from "lit";

/**
 * <wavy-link-modal> — F-3.S1 (#1038, R-5.2 step 5) modal dialog used by
 * the H.17 Insert link toolbar action. Surfaces URL + display-text
 * fields, validates URL, and emits the result as a CustomEvent the
 * composer consumes to wrap the active selection in `<a>`.
 *
 * Public API:
 * - open (Boolean) — controls visibility.
 * - urlValue (String) — initial URL value (e.g. existing link href).
 * - displayValue (String) — initial display text value.
 *
 * Events:
 * - `wavy-link-modal-submit` — `{detail: {url, display}}` on form submit.
 * - `wavy-link-modal-cancel` — emitted when the user dismisses the modal
 *   via Esc / backdrop click / Cancel button.
 */
export class WavyLinkModal extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    urlValue: { type: String, attribute: "url-value" },
    displayValue: { type: String, attribute: "display-value" }
  };

  static styles = css`
    :host(:not([open])) {
      display: none;
    }
    :host([open]) {
      position: fixed;
      inset: 0;
      z-index: 1100;
      display: grid;
      place-items: center;
      background: rgba(10, 19, 34, 0.6);
    }
    .modal {
      min-width: 320px;
      max-width: 480px;
      padding: var(--wavy-spacing-4, 16px);
      background: var(--wavy-bg-surface, #11192a);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      border-radius: var(--wavy-radius-card, 12px);
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }
    h2 {
      margin: 0 0 var(--wavy-spacing-2, 8px) 0;
      font: var(--wavy-type-h3, 1.0625rem / 1.35 sans-serif);
    }
    label {
      display: grid;
      gap: 4px;
      margin-bottom: var(--wavy-spacing-2, 8px);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
    }
    input {
      padding: 6px 8px;
      border-radius: 8px;
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      background: var(--wavy-bg-base, #0a1322);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      font: inherit;
    }
    input:focus-visible {
      outline: none;
      border-color: var(--wavy-signal-cyan, #22d3ee);
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--wavy-spacing-2, 8px);
      margin-top: var(--wavy-spacing-3, 12px);
    }
    button {
      padding: 6px 12px;
      border-radius: 8px;
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      background: var(--wavy-bg-base, #0a1322);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      cursor: pointer;
      font: inherit;
    }
    button[type="submit"] {
      background: var(--wavy-signal-cyan-soft, rgba(34, 211, 238, 0.22));
      border-color: var(--wavy-signal-cyan, #22d3ee);
    }
    .error {
      color: var(--wavy-signal-amber, #f59e0b);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      margin-top: 4px;
    }
  `;

  constructor() {
    super();
    this.open = false;
    this.urlValue = "";
    this.displayValue = "";
    this._error = "";
    this._handleKeydown = (event) => this._onKeydown(event);
  }

  connectedCallback() {
    super.connectedCallback();
    document.addEventListener("keydown", this._handleKeydown);
  }

  disconnectedCallback() {
    document.removeEventListener("keydown", this._handleKeydown);
    super.disconnectedCallback();
  }

  _onKeydown(event) {
    if (!this.open) return;
    if (event.key === "Escape") {
      event.preventDefault();
      this._cancel();
    }
  }

  _cancel() {
    this.open = false;
    this._error = "";
    this.requestUpdate();
    this.dispatchEvent(
      new CustomEvent("wavy-link-modal-cancel", { bubbles: true, composed: true })
    );
  }

  _onSubmit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const url = (form.elements.url.value || "").trim();
    const display = (form.elements.display.value || "").trim();
    if (!this._isValidUrl(url)) {
      this._error = "Enter a valid URL (https://example.com)";
      this.requestUpdate();
      return;
    }
    this._error = "";
    this.open = false;
    this.dispatchEvent(
      new CustomEvent("wavy-link-modal-submit", {
        detail: { url, display: display || url },
        bubbles: true,
        composed: true
      })
    );
  }

  _isValidUrl(value) {
    if (!value) return false;
    try {
      const url = new URL(value);
      return url.protocol === "http:" || url.protocol === "https:" || url.protocol === "mailto:";
    } catch {
      return false;
    }
  }

  _onBackdropClick(event) {
    // Only treat clicks on the host (backdrop) as cancel; clicks inside
    // the modal panel propagate but we stopPropagation there.
    if (event.target === this) {
      this._cancel();
    }
  }

  render() {
    return html`
      <div
        class="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="wavy-link-modal-title"
        @click=${(e) => e.stopPropagation()}
      >
        <h2 id="wavy-link-modal-title">Insert link</h2>
        <form @submit=${this._onSubmit}>
          <label>
            URL
            <input
              name="url"
              type="url"
              .value=${this.urlValue}
              autocomplete="off"
              autofocus
              required
              data-link-modal-url
            />
          </label>
          <label>
            Display text
            <input
              name="display"
              type="text"
              .value=${this.displayValue}
              autocomplete="off"
              data-link-modal-display
            />
          </label>
          ${this._error
            ? html`<p class="error" role="alert" data-link-modal-error>${this._error}</p>`
            : ""}
          <div class="actions">
            <button type="button" data-link-modal-cancel @click=${this._cancel.bind(this)}>
              Cancel
            </button>
            <button type="submit" data-link-modal-submit>Insert</button>
          </div>
        </form>
      </div>
    `;
  }

  firstUpdated() {
    this.addEventListener("click", this._onBackdropClick.bind(this));
  }
}

if (!customElements.get("wavy-link-modal")) {
  customElements.define("wavy-link-modal", WavyLinkModal);
}
