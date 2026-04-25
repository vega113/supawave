import { LitElement, css, html } from "lit";

export class ComposeAttachmentCard extends LitElement {
  static properties = {
    attachmentId: { type: String, attribute: "attachment-id" },
    filename: { type: String },
    mimeType: { type: String, attribute: "mime-type" },
    sizeLabel: { type: String, attribute: "size-label" },
    displaySize: { type: String, attribute: "display-size" },
    openUrl: { type: String, attribute: "open-url" },
    downloadUrl: { type: String, attribute: "download-url" },
    thumbnailUrl: { type: String, attribute: "thumbnail-url" },
    status: { type: String },
    error: { type: String },
    malwareBlocked: { type: Boolean, attribute: "malware-blocked", reflect: true }
  };

  static styles = css`
    :host {
      display: block;
    }

    article {
      display: grid;
      gap: 10px;
      max-width: 360px;
      padding: 12px;
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 14px;
      background: var(--shell-color-surface-shell, #fff);
    }

    .size-small {
      max-width: 240px;
    }

    .size-large {
      max-width: 520px;
    }

    .preview {
      display: grid;
      min-height: 72px;
      place-items: center;
      overflow: hidden;
      border-radius: 10px;
      background: var(--shell-color-surface-muted, #eef4f8);
      color: var(--shell-color-text-muted, #5b6b80);
    }

    img {
      display: block;
      width: 100%;
      max-height: 260px;
      object-fit: contain;
    }

    .meta {
      display: grid;
      gap: 2px;
    }

    .name {
      font-weight: 650;
    }

    .details {
      color: var(--shell-color-text-muted, #5b6b80);
      font-size: 0.9rem;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    button {
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 10px;
      padding: 7px 10px;
      background: #fff;
      color: var(--shell-color-text-primary, #181c1d);
      font: inherit;
      cursor: pointer;
    }

    button:focus-visible {
      outline: 3px solid var(--shell-color-focus-ring, #1e88e5);
      outline-offset: 2px;
    }

    button:disabled {
      cursor: not-allowed;
      opacity: 0.55;
    }

    [role="alert"] {
      color: var(--shell-color-danger, #a12a16);
    }
  `;

  constructor() {
    super();
    this.attachmentId = "";
    this.filename = "Attachment";
    this.mimeType = "";
    this.sizeLabel = "";
    this.displaySize = "medium";
    this.openUrl = "";
    this.downloadUrl = "";
    this.thumbnailUrl = "";
    this.status = "";
    this.error = "";
    this.malwareBlocked = false;
  }

  render() {
    const blocked = this.malwareBlocked || Boolean(this.error);
    const openDisabled = blocked || !this.openUrl;
    const downloadDisabled = blocked || !(this.downloadUrl || this.openUrl);
    const displaySize = ["small", "medium", "large"].includes(this.displaySize)
      ? this.displaySize
      : "medium";
    return html`
      <article class=${`size-${displaySize}`} aria-label=${`Attachment ${this.filename}`}>
        <div class="preview">
          ${this.thumbnailUrl
            ? html`<img src=${this.thumbnailUrl} alt=${this.filename} />`
            : html`<span aria-hidden="true">${this.previewLabel()}</span>`}
        </div>
        <div class="meta">
          <span class="name">${this.filename}</span>
          <span class="details">${this.detailsLabel()}</span>
        </div>
        ${this.status && !blocked
          ? html`<p role="status" aria-live="polite">${this.status}</p>`
          : ""}
        ${blocked
          ? html`<p role="alert" aria-live="assertive">${this.error || "Attachment blocked."}</p>`
          : ""}
        <div class="actions">
          <button
            type="button"
            data-action="attachment-open"
            aria-label=${`Open attachment ${this.filename}`}
            ?disabled=${openDisabled}
            @click=${this.onOpen}
          >
            Open
          </button>
          <button
            type="button"
            data-action="attachment-download"
            aria-label=${`Download attachment ${this.filename}`}
            ?disabled=${downloadDisabled}
            @click=${this.onDownload}
          >
            Download
          </button>
        </div>
      </article>
    `;
  }

  previewLabel() {
    if (this.mimeType?.startsWith("image/")) {
      return "Image attachment";
    }
    return "File attachment";
  }

  detailsLabel() {
    return [this.mimeType, this.sizeLabel].filter(Boolean).join(" - ") || "Attachment file";
  }

  onOpen() {
    this.emitAttachmentAction("attachment-open", this.openUrl);
  }

  onDownload() {
    this.emitAttachmentAction("attachment-download", this.downloadUrl || this.openUrl);
  }

  emitAttachmentAction(type, url) {
    if (this.malwareBlocked || this.error || !url) {
      return;
    }
    this.dispatchEvent(
      new CustomEvent(type, {
        detail: {
          attachmentId: this.attachmentId,
          filename: this.filename,
          url: url || ""
        },
        bubbles: true,
        composed: true
      })
    );
  }
}

if (!customElements.get("compose-attachment-card")) {
  customElements.define("compose-attachment-card", ComposeAttachmentCard);
}
