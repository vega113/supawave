import { LitElement, css, html } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";

let mentionPopoverCounter = 0;

export class MentionSuggestionPopover extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    candidates: { type: Array },
    activeIndex: { type: Number, attribute: "active-index" },
    focusTargetId: { type: String, attribute: "focus-target-id" }
  };

  static styles = css`
    :host {
      display: block;
    }

    .popover {
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 14px;
      padding: 8px;
      background: var(--shell-color-surface-overlay, #fff);
      box-shadow: var(--shell-shadow-overlay, 0 16px 44px rgb(10 38 64 / 18%));
    }

    [role="option"] {
      display: block;
      width: 100%;
      border: 0;
      border-radius: 10px;
      padding: 8px 10px;
      background: transparent;
      text-align: left;
      font: inherit;
      cursor: pointer;
    }

    [aria-selected="true"] {
      background: var(--shell-color-accent-selection, #e4f1fb);
      box-shadow: inset 3px 0 0 var(--shell-color-accent-focus, #206ea6);
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
    this.open = false;
    this.candidates = [];
    this.activeIndex = 0;
    this.focusTargetId = "";
    this.optionIdPrefix = `mention-option-${++mentionPopoverCounter}`;
    this.addEventListener("keydown", this.onKeyDown);
  }

  updated(changed) {
    if (changed.has("open") && this.open) {
      queueMicrotask(() => this.renderRoot.querySelector("[role='listbox']")?.focus());
    }
  }

  render() {
    if (!this.open) {
      return "";
    }
    const candidates = this.safeCandidates();
    const activeIndex = this.clampedIndex(this.activeIndex, candidates);
    const activeId = candidates.length > 0 ? this.optionId(activeIndex) : "";
    return html`
      <div class="popover">
        <div
          role="listbox"
          tabindex="-1"
          aria-label="Mention suggestions"
          aria-activedescendant=${ifDefined(activeId || undefined)}
        >
          ${candidates.length === 0
            ? html`<p>No mention matches</p>`
            : candidates.map((candidate, index) =>
                this.renderCandidate(candidate, index, activeIndex)
              )}
        </div>
        <span class="sr-only" aria-live="polite">${this.liveText(candidates.length)}</span>
      </div>
    `;
  }

  renderCandidate(candidate, index, activeIndex) {
    const active = index === activeIndex;
    return html`
      <div
        id=${this.optionId(index)}
        role="option"
        tabindex="-1"
        aria-selected=${active ? "true" : "false"}
        data-address=${candidate.address || ""}
        @click=${() => this.selectCandidate(index)}
      >
        ${candidate.displayName || candidate.address}
      </div>
    `;
  }

  onKeyDown = (event) => {
    if (!this.open) {
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      this.close("escape");
      return;
    }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const offset = event.key === "ArrowDown" ? 1 : -1;
      this.activeIndex = this.clampedIndex(this.activeIndex + offset);
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      this.selectCandidate(this.clampedIndex(this.activeIndex));
      return;
    }
    if (event.key === "Tab" && this.safeCandidates().length > 0) {
      event.preventDefault();
      this.selectCandidate(this.clampedIndex(this.activeIndex));
    }
  };

  selectCandidate(index) {
    const candidates = this.safeCandidates();
    const candidate = candidates[this.clampedIndex(index, candidates)];
    if (!candidate) {
      return;
    }
    this.dispatchEvent(
      new CustomEvent("mention-select", {
        detail: {
          address: candidate.address || "",
          displayName: candidate.displayName || candidate.address || ""
        },
        bubbles: true,
        composed: true
      })
    );
  }

  close(reason) {
    this.open = false;
    this.dispatchEvent(
      new CustomEvent("overlay-close", {
        detail: { reason, focusTargetId: this.focusTargetId },
        bubbles: true,
        composed: true
      })
    );
  }

  clampedIndex(index, candidates = this.safeCandidates()) {
    const count = candidates.length;
    if (count === 0) {
      return 0;
    }
    return (index + count) % count;
  }

  optionId(index) {
    return `${this.optionIdPrefix}-${index}`;
  }

  safeCandidates() {
    if (!Array.isArray(this.candidates)) {
      return [];
    }
    return this.candidates.filter(
      candidate => candidate && typeof candidate === "object" && !Array.isArray(candidate)
    );
  }

  liveText(count) {
    if (count === 0) {
      return "No mention suggestions.";
    }
    return `${count} mention ${count === 1 ? "suggestion" : "suggestions"}.`;
  }
}

if (!customElements.get("mention-suggestion-popover")) {
  customElements.define("mention-suggestion-popover", MentionSuggestionPopover);
}
