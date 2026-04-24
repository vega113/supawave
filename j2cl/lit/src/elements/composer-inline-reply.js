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
    error: { type: String }
  };

  static styles = css`
    :host {
      display: block;
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
  }

  render() {
    const textareaDisabled = !this.available || this.submitting;
    const submitDisabled = textareaDisabled || this.staleBasis;
    return html`
      <div class="reply">
        <p class="target">Reply target: ${this.targetLabel || "No current wave"}</p>
        <textarea
          aria-label="Reply"
          .value=${this.draft}
          ?disabled=${textareaDisabled}
          @input=${this.onInput}
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
}

if (!customElements.get("composer-inline-reply")) {
  customElements.define("composer-inline-reply", ComposerInlineReply);
}
