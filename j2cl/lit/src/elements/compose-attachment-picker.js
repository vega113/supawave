import { LitElement, css, html } from "lit";
import "./interaction-overlay-layer.js";
import { DISPLAY_SIZES } from "./attachment-display-sizes.js";

export class ComposeAttachmentPicker extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    caption: { type: String },
    displaySize: { type: String, attribute: "display-size" },
    uploadState: { type: String, attribute: "upload-state" },
    uploadProgress: { type: Number, attribute: "upload-progress" },
    uploadMessage: { type: String, attribute: "upload-message" },
    uploadError: { type: String, attribute: "upload-error" }
  };

  static styles = css`
    :host {
      display: inline-block;
    }

    button,
    input,
    textarea {
      font: inherit;
    }

    button {
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 10px;
      padding: 7px 10px;
      background: #fff;
      color: var(--shell-color-text-primary, #181c1d);
      cursor: pointer;
    }

    button:focus-visible,
    input:focus-visible,
    textarea:focus-visible {
      outline: 3px solid var(--shell-color-focus-ring, #1e88e5);
      outline-offset: 2px;
    }

    .panel {
      display: grid;
      gap: 12px;
      min-width: min(84vw, 340px);
    }

    label,
    fieldset {
      display: grid;
      gap: 6px;
      margin: 0;
      padding: 0;
      border: 0;
    }

    legend {
      margin-block-end: 6px;
      font-weight: 650;
    }

    textarea {
      min-height: 72px;
      resize: vertical;
    }

    .sizes {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .sizes label {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
    }

    progress {
      width: 100%;
    }

    [role="alert"] {
      color: var(--shell-color-danger, #a12a16);
    }

    @media (prefers-reduced-motion: reduce) {
      interaction-overlay-layer::part(surface) {
        scroll-behavior: auto;
      }
    }
  `;

  constructor() {
    super();
    this.open = false;
    this.caption = "";
    this.displaySize = "medium";
    this.uploadState = "";
    this.uploadProgress = 0;
    this.uploadMessage = "";
    this.uploadError = "";
  }

  render() {
    return html`
      <button
        id="attachment-picker-trigger"
        type="button"
        data-action="attachment-insert"
        aria-haspopup="dialog"
        aria-expanded=${this.open ? "true" : "false"}
        aria-controls="attachment-picker-overlay"
        @click=${this.openPicker}
      >
        Attach file
      </button>
      <interaction-overlay-layer
        id="attachment-picker-overlay"
        ?open=${this.open}
        modal
        label="Attach a file"
        focus-target-id="attachment-picker-trigger"
        @overlay-close=${this.onOverlayClose}
      >
        <div class="panel">
          <label>
            File
            <input
              type="file"
              multiple
              @change=${this.onFilesSelected}
            />
          </label>
          <label>
            Caption
            <textarea
              name="attachment-caption"
              aria-label="Attachment caption"
              .value=${this.caption}
              @input=${this.onCaptionInput}
            ></textarea>
          </label>
          <fieldset @keydown=${this.onSizeKeyDown}>
            <legend>Display size</legend>
            <div class="sizes">
              ${DISPLAY_SIZES.map(size => html`
                <label>
                  <input
                    type="radio"
                    name="attachment-display-size"
                    value=${size}
                    .checked=${this.displaySize === size}
                    @click=${() => this.selectDisplaySize(size)}
                  />
                  ${size}
                </label>
              `)}
            </div>
          </fieldset>
          ${this.renderUploadState()}
          <div class="actions">
            <button type="button" data-action="attachment-cancel" @click=${this.cancel}>
              Cancel
            </button>
          </div>
        </div>
      </interaction-overlay-layer>
    `;
  }

  renderUploadState() {
    if (this.uploadState === "error") {
      return html`
        <p
          role="alert"
          aria-live="assertive"
          data-state="attachment-error-state"
        >${this.uploadError || "Attachment upload failed."}</p>
      `;
    }
    if (!this.uploadState) {
      return "";
    }
    return html`
      <div role="status" aria-live="polite">
        ${this.uploadMessage || this.uploadState}
        ${this.uploadState === "uploading"
          ? html`
              <progress
                role="progressbar"
                aria-label="Attachment upload progress"
                max="100"
                value=${this.normalizedProgress()}
                aria-valuemin="0"
                aria-valuemax="100"
                aria-valuenow=${String(this.normalizedProgress())}
              ></progress>
            `
          : ""}
      </div>
    `;
  }

  updated(changedProperties) {
    if (changedProperties.has("open") && this.open) {
      this.renderRoot.querySelector("input[type='file']")?.focus();
    }
  }

  openPicker() {
    if (this.open) {
      return;
    }
    this.open = true;
    this.dispatchEvent(
      new CustomEvent("attachment-picker-open", {
        detail: { action: "attachment-insert" },
        bubbles: true,
        composed: true
      })
    );
  }

  cancel() {
    this.closePicker("cancel");
  }

  onOverlayClose(event) {
    event.stopPropagation();
    this.closePicker(event.detail?.reason || "overlay");
  }

  closePicker(reason) {
    this.open = false;
    this.dispatchEvent(
      new CustomEvent("attachment-picker-close", {
        detail: { reason },
        bubbles: true,
        composed: true
      })
    );
    // interaction-overlay-layer reports focusTargetId; this host owns restoration.
    this.updateComplete.then(() => {
      this.renderRoot.querySelector("#attachment-picker-trigger")?.focus();
    });
  }

  onCaptionInput(event) {
    this.caption = event.target.value;
    this.dispatchEvent(
      new CustomEvent("attachment-caption", {
        detail: { action: "attachment-caption", caption: this.caption },
        bubbles: true,
        composed: true
      })
    );
  }

  selectDisplaySize(size) {
    if (!DISPLAY_SIZES.includes(size)) {
      return;
    }
    if (size === this.displaySize) {
      return;
    }
    this.displaySize = size;
    this.dispatchEvent(
      new CustomEvent("attachment-size-selected", {
        detail: { action: `attachment-size-${size}`, displaySize: size },
        bubbles: true,
        composed: true
      })
    );
  }

  onSizeKeyDown(event) {
    if (!["ArrowRight", "ArrowDown", "ArrowLeft", "ArrowUp"].includes(event.key)) {
      return;
    }
    const currentIndex = Math.max(0, DISPLAY_SIZES.indexOf(this.displaySize));
    const offset = event.key === "ArrowRight" || event.key === "ArrowDown" ? 1 : -1;
    const nextIndex = (currentIndex + offset + DISPLAY_SIZES.length) % DISPLAY_SIZES.length;
    event.preventDefault();
    this.selectDisplaySize(DISPLAY_SIZES[nextIndex]);
    this.updateComplete.then(() => {
      this.renderRoot.querySelector(`input[value='${this.displaySize}']`)?.focus();
    });
  }

  onFilesSelected(event) {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) {
      return;
    }
    this.dispatchEvent(
      new CustomEvent("attachment-files-selected", {
        detail: {
          action: "attachment-upload-queue",
          files,
          caption: this.caption,
          displaySize: this.displaySize
        },
        bubbles: true,
        composed: true
      })
    );
    // Reset so selecting the same file(s) again fires another change event.
    event.target.value = "";
  }

  normalizedProgress() {
    const progress = Number(this.uploadProgress);
    if (!Number.isFinite(progress)) {
      return 0;
    }
    return Math.min(100, Math.max(0, Math.round(progress)));
  }
}

if (!customElements.get("compose-attachment-picker")) {
  customElements.define("compose-attachment-picker", ComposeAttachmentPicker);
}
