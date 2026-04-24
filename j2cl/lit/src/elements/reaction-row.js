import { LitElement, css, html } from "lit";

export class ReactionRow extends LitElement {
  static properties = {
    blipId: { type: String, attribute: "blip-id" },
    reactions: { type: Array }
  };

  static styles = css`
    :host {
      display: block;
    }

    .row {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      align-items: center;
    }

    button {
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 999px;
      padding: 5px 9px;
      background: #fff;
      font: inherit;
    }

    [aria-pressed="true"] {
      background: var(--shell-color-accent-selection, #e4f1fb);
      border-color: var(--shell-color-accent-focus, #206ea6);
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
    }
  `;

  constructor() {
    super();
    this.blipId = "";
    this.reactions = [];
  }

  render() {
    return html`
      <div class="row" role="group" aria-label="Reactions">
        ${this.safeReactions().map(reaction => this.renderReaction(reaction))}
        <button
          type="button"
          data-reaction-add
          aria-label="Add reaction"
          @click=${this.onAdd}
        >
          +
        </button>
      </div>
      <span class="sr-only" aria-live="polite">${this.liveText()}</span>
    `;
  }

  renderReaction(reaction) {
    const emoji = reaction.emoji || "";
    const glyph = reaction.glyph || emoji;
    const name = reaction.accessibleName || reaction.label || this.humanizeName(emoji);
    const count = this.safeCount(reaction.count);
    return html`
      <span role="group" aria-label=${`${name} reaction`}>
        <button
          type="button"
          data-reaction-chip
          data-emoji=${emoji}
          aria-pressed=${reaction.active ? "true" : "false"}
          aria-label=${`Toggle ${name} reaction, ${count} ${
            count === 1 ? "person" : "people"
          }`}
          @click=${() => this.emit("reaction-toggle", emoji)}
        >
          ${glyph} ${count}
        </button>
        <button
          type="button"
          data-reaction-inspect
          data-emoji=${emoji}
          aria-label=${reaction.inspectLabel || `Inspect ${name} reactions`}
          @click=${() => this.emit("reaction-inspect", emoji)}
        >
          authors
        </button>
      </span>
    `;
  }

  onAdd = () => {
    this.dispatchEvent(
      new CustomEvent("reaction-add", {
        detail: { blipId: this.blipId },
        bubbles: true,
        composed: true
      })
    );
  };

  emit(type, emoji) {
    this.dispatchEvent(
      new CustomEvent(type, {
        detail: { blipId: this.blipId, emoji },
        bubbles: true,
        composed: true
      })
    );
  }

  liveText() {
    const count = this.safeReactions().reduce(
      (sum, reaction) => sum + this.safeCount(reaction.count),
      0
    );
    return `${count} ${count === 1 ? "reaction" : "reactions"}.`;
  }

  safeCount(value) {
    const count = Number(value || 0);
    return Number.isFinite(count) ? Math.max(0, count) : 0;
  }

  safeReactions() {
    if (!Array.isArray(this.reactions)) {
      return [];
    }
    return this.reactions.filter(
      reaction => reaction && typeof reaction === "object" && !Array.isArray(reaction)
    );
  }

  humanizeName(value) {
    return String(value || "").split("_").join(" ");
  }
}

if (!customElements.get("reaction-row")) {
  customElements.define("reaction-row", ReactionRow);
}
