import { LitElement, css, html } from "lit";

/**
 * <wavy-tags-row> — F-3.S1 (#1038, R-5.1 tags) wave-tag editing affordances.
 *
 * I.1 "Tags:" label + I.2 chips with × remove + I.3 Add tag button +
 * I.4 Add tag textbox + I.5 Cancel tag entry. F-2 ships read-only
 * display; F-3 adds the editing affordances.
 *
 * Public API:
 * - tags (Array<String>) — current wave tags.
 * - editing (Boolean) — whether the inline textbox is open.
 * - waveId (String) — active wave id, included in event details.
 *
 * Events:
 * - `wave-tag-add-requested` — `{detail: {waveId, tag}}`
 * - `wave-tag-remove-requested` — `{detail: {waveId, tag}}`
 */
export class WavyTagsRow extends LitElement {
  static properties = {
    tags: { type: Array },
    editing: { type: Boolean, reflect: true },
    waveId: { type: String, attribute: "wave-id" }
  };

  static styles = css`
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
      padding: var(--wavy-spacing-2, 8px) 0;
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
    }
    .label {
      font-weight: 600;
    }
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 2px;
      padding: 2px var(--wavy-spacing-2, 8px);
      border-radius: var(--wavy-radius-pill, 9999px);
      background: var(--wavy-signal-cyan-soft, rgba(34, 211, 238, 0.18));
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
    }
    .chip-remove {
      border: 0;
      background: transparent;
      color: inherit;
      cursor: pointer;
      font: inherit;
      padding: 0 2px;
    }
    .chip-remove:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      border-radius: var(--wavy-radius-pill, 9999px);
    }
    .add-button,
    .cancel-button {
      padding: 2px var(--wavy-spacing-2, 8px);
      border-radius: var(--wavy-radius-pill, 9999px);
      border: 1px dashed var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      background: transparent;
      color: inherit;
      cursor: pointer;
      font: inherit;
    }
    .add-button:hover,
    .cancel-button:hover,
    .add-button:focus-visible,
    .cancel-button:focus-visible {
      border-color: var(--wavy-signal-cyan, #22d3ee);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      outline: none;
    }
    .input {
      padding: 2px var(--wavy-spacing-2, 8px);
      border-radius: var(--wavy-radius-pill, 9999px);
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      background: var(--wavy-bg-base, #0a1322);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      font: inherit;
      min-width: 140px;
    }
    .input:focus-visible {
      outline: none;
      border-color: var(--wavy-signal-cyan, #22d3ee);
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }
  `;

  constructor() {
    super();
    this.tags = [];
    this.editing = false;
    this.waveId = "";
  }

  _onAddClick() {
    this.editing = true;
    this.requestUpdate();
    // Focus the input on the next frame after Lit re-renders.
    requestAnimationFrame(() => {
      const input = this.renderRoot && this.renderRoot.querySelector("[data-tags-input]");
      if (input) input.focus();
    });
  }

  _onCancelClick() {
    this.editing = false;
  }

  _onInputKeydown(event) {
    if (event.key === "Enter") {
      event.preventDefault();
      const value = (event.currentTarget.value || "").trim();
      if (value) {
        this._dispatchAdd(value);
        event.currentTarget.value = "";
      }
    } else if (event.key === "Escape") {
      event.preventDefault();
      this._onCancelClick();
    }
  }

  _dispatchAdd(tag) {
    this.dispatchEvent(
      new CustomEvent("wave-tag-add-requested", {
        detail: { waveId: this.waveId, tag },
        bubbles: true,
        composed: true
      })
    );
  }

  _onRemoveClick(tag) {
    return (event) => {
      event.preventDefault();
      this.dispatchEvent(
        new CustomEvent("wave-tag-remove-requested", {
          detail: { waveId: this.waveId, tag },
          bubbles: true,
          composed: true
        })
      );
    };
  }

  render() {
    const tags = Array.isArray(this.tags) ? this.tags : [];
    return html`
      <span class="label">Tags:</span>
      ${tags.map(
        (tag) => html`<span class="chip" data-tag-chip=${tag}>
          ${tag}
          <button
            type="button"
            class="chip-remove"
            data-tag-remove=${tag}
            aria-label=${`Remove tag ${tag}`}
            @click=${this._onRemoveClick(tag)}
          >×</button>
        </span>`
      )}
      ${this.editing
        ? html`
            <input
              type="text"
              class="input"
              data-tags-input
              placeholder="Add tag"
              aria-label="Add tag"
              @keydown=${this._onInputKeydown}
            />
            <button
              type="button"
              class="cancel-button"
              data-tags-cancel
              aria-label="Cancel tag entry"
              @click=${this._onCancelClick}
            >×</button>
          `
        : html`
            <button
              type="button"
              class="add-button"
              data-tags-add
              aria-label="Add tag"
              @click=${this._onAddClick}
            >+ Add tag</button>
          `}
    `;
  }
}

if (!customElements.get("wavy-tags-row")) {
  customElements.define("wavy-tags-row", WavyTagsRow);
}
