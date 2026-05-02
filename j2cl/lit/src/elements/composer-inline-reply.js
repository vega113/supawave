import { LitElement, css, html } from "lit";
import "./composer-submit-affordance.js";

export class ComposerInlineReply extends LitElement {
  static properties = {
    available: { type: Boolean, reflect: true },
    targetLabel: { type: String, attribute: "target-label" },
    draft: { type: String },
    submitting: { type: Boolean, reflect: true },
    staleBasis: { type: Boolean, attribute: "stale-basis", reflect: true },
    status: { type: String },
    error: { type: String },
    activeCommand: { type: String, attribute: "active-command" },
    commandStatus: { type: String, attribute: "command-status" },
    commandError: { type: String, attribute: "command-error" },
    // V-2 (#1100): when off (default), the "Reply target: <id>" line
    // does not render. This intentionally avoids page-level debug
    // inheritance and only respects this element's [debug-overlay]
    // attribute/property; tests may set the attribute directly.
    debugOverlay: { type: Boolean, attribute: "debug-overlay", reflect: true }
  };

  static styles = css`
    :host {
      display: block;
    }
    /* F-2 follow-up (#1060): collapse the whole reply composer until an
     * edit session marks it as available. Pre-fix this element painted
     * "Reply target: ..." + an empty Send-reply textarea on every
     * selected wave even when the user had not opened a compose, which
     * read as a permanent editor-toolbar wall in the right column. The
     * controller flips the available property to true via setProperty
     * before any compose interaction, so users still see the textarea
     * the moment they Reply / Edit. */
    :host(:not([available])) {
      display: none;
    }

    .reply {
      display: grid;
      gap: 10px;
    }

    textarea {
      min-height: 84px;
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 12px;
      padding: 10px 12px;
      font: inherit;
      resize: vertical;
    }

    .target {
      margin: 0;
      color: var(--shell-color-text-muted, #5b6b80);
      font-size: 0.9rem;
    }
  `;

  constructor() {
    super();
    this.available = false;
    this.targetLabel = "";
    this.draft = "";
    this.submitting = false;
    this.staleBasis = false;
    this.status = "";
    this.error = "";
    this.activeCommand = "";
    this.commandStatus = "";
    this.commandError = "";
    this.debugOverlay = false;
    this._handleFocusRequest = () => this.focusComposer();
  }

  connectedCallback() {
    super.connectedCallback();
    this.addEventListener("composer-focus-request", this._handleFocusRequest);
    // Do not inherit the page-level debug flag into the reply surface.
    // Raw target labels are only visible when this element explicitly
    // carries [debug-overlay].
  }

  disconnectedCallback() {
    this.removeEventListener("composer-focus-request", this._handleFocusRequest);
    super.disconnectedCallback();
  }

  render() {
    const textareaDisabled = !this.available || this.submitting;
    const submitDisabled = textareaDisabled || this.staleBasis;
    return html`
      <div class="reply">
        ${this.debugOverlay
          ? html`<p class="target">Reply target: ${this.targetLabel || "No current wave"}</p>`
          : ""}
        <textarea
          aria-label="Reply"
          .value=${this.draft}
          ?disabled=${textareaDisabled}
          @input=${this.onInput}
          @paste=${this.onPaste}
        ></textarea>
        <composer-submit-affordance
          label="Send reply"
          ?busy=${this.submitting}
          ?disabled=${submitDisabled}
          @submit-affordance=${this.onSubmit}
        ></composer-submit-affordance>
        ${this.status && !this.error
          ? html`<p class="target" role="status" aria-live="polite">${this.status}</p>`
          : ""}
        ${this.error
          ? html`<p class="target" role="alert" aria-live="assertive">${this.error}</p>`
          : ""}
        ${this.commandStatus
          ? html`<p
              class="target"
              data-command-status
              data-active-command=${this.activeCommand}
              role="status"
              aria-live="polite"
            >${this.commandStatus}</p>`
          : ""}
        ${this.commandError
          ? html`<p
              class="target"
              data-command-error
              data-active-command=${this.activeCommand}
              role="alert"
              aria-live="assertive"
            >${this.commandError}</p>`
          : ""}
      </div>
    `;
  }

  onInput(event) {
    this.draft = event.target.value;
    this.dispatchEvent(
      new CustomEvent("draft-change", {
        detail: { value: this.draft },
        bubbles: true,
        composed: true
      })
    );
  }

  onSubmit() {
    this.dispatchEvent(new CustomEvent("reply-submit", { bubbles: true, composed: true }));
  }

  onPaste(event) {
    const items = Array.from(event.clipboardData?.items || []);
    let file = items
      .filter((item) => item.type?.startsWith("image/"))
      .map((item) => this.fileFromClipboardItem(item))
      .find(Boolean);
    // Fallback for browsers (common mobile path) that expose pasted images via files
    if (!file) {
      const files = Array.from(event.clipboardData?.files || []);
      file = files.find((f) => f.type?.startsWith("image/")) ?? null;
    }
    if (!file) {
      return;
    }
    const hasText = items.some((item) => item.type?.startsWith("text/"));
    if (!hasText) {
      event.preventDefault();
    }
    this.dispatchEvent(
      new CustomEvent("attachment-paste-image", {
        detail: { file },
        bubbles: true,
        composed: true
      })
    );
  }

  fileFromClipboardItem(item) {
    try {
      return item.getAsFile?.();
    } catch {
      return null;
    }
  }

  focusComposer() {
    this.renderRoot.querySelector("textarea")?.focus();
  }
}

if (!customElements.get("composer-inline-reply")) {
  customElements.define("composer-inline-reply", ComposerInlineReply);
}
