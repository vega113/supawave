import { LitElement, css, html } from "lit";

export class ReactionRow extends LitElement {
  static properties = {
    blipId: { type: String, attribute: "blip-id" },
    reactions: { type: Array }
  };

  // F-3.S3 (#1038, R-5.5): chips and the add button adopt the F-0
  // wavy design tokens — `--wavy-signal-violet` border + soft fill
  // for the active-pressed state, `--wavy-radius-pill` for the full
  // pill chips, and `--wavy-pulse-ring` for the count-up pulse.
  // Falls back to the F-1 shell tokens when the wavy stylesheet is
  // not loaded so legacy fixtures keep rendering.
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
      border: 1px solid var(--wavy-signal-violet-soft, var(--shell-color-divider-subtle, #d8e3ee));
      border-radius: var(--wavy-radius-pill, 999px);
      padding: 5px 9px;
      background: transparent;
      color: var(--wavy-text-body, inherit);
      font: inherit;
      cursor: pointer;
      transition: background-color 180ms ease-out, border-color 180ms ease-out,
        box-shadow 180ms ease-out;
    }

    button:hover {
      border-color: var(--wavy-signal-violet, #7c3aed);
    }

    [aria-pressed="true"] {
      background: var(--wavy-signal-violet-soft, var(--shell-color-accent-selection, #e4f1fb));
      border-color: var(--wavy-signal-violet, var(--shell-color-accent-focus, #206ea6));
    }

    button:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }

    /* F-3.S3 (#1038, R-5.5 step 14): one-shot pulse animation when a
     * chip's count increases via supplement live-update. The
     * --wavy-pulse-ring token defines the ring; the animation class
     * is set and removed by updated() below. */
    button[data-live-pulse="true"] {
      animation: wavyChipPulse 700ms ease-out;
    }

    @keyframes wavyChipPulse {
      0% {
        box-shadow: 0 0 0 0 var(--wavy-signal-violet-soft, rgba(124, 58, 237, 0.22));
      }
      40% {
        box-shadow: var(--wavy-pulse-ring, 0 0 0 4px rgba(124, 58, 237, 0.22));
      }
      100% {
        box-shadow: 0 0 0 0 transparent;
      }
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
    // F-3.S3: track previous counts so updated() can fire a one-shot
    // pulse when a chip's count increases via live-update.
    this._previousCountByEmoji = new Map();
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

  updated(changed) {
    if (!changed.has("reactions")) {
      this._refreshPreviousCounts();
      return;
    }
    // F-3.S3 (#1038, R-5.5 step 14): pulse chips whose count has gone
    // up since the last render. Single-pass: read prev counts, set
    // data-live-pulse on the matching chip, schedule an attribute
    // removal so the animation can re-trigger on the next update.
    const next = this.safeReactions();
    for (const reaction of next) {
      const emoji = reaction.emoji || "";
      if (!emoji) continue;
      const prev = this._previousCountByEmoji.get(emoji);
      const count = this.safeCount(reaction.count);
      if (prev !== undefined && count > prev) {
        const chip = this.renderRoot.querySelector(
          `[data-reaction-chip][data-emoji="${emoji}"]`
        );
        if (chip) {
          chip.setAttribute("data-live-pulse", "true");
          // Remove on the next tick so the animation can re-arm on
          // another count-up after this one finishes.
          setTimeout(() => chip.removeAttribute("data-live-pulse"), 720);
        }
      }
    }
    this._refreshPreviousCounts();
  }

  _refreshPreviousCounts() {
    this._previousCountByEmoji.clear();
    for (const reaction of this.safeReactions()) {
      const emoji = reaction.emoji || "";
      if (!emoji) continue;
      this._previousCountByEmoji.set(emoji, this.safeCount(reaction.count));
    }
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
