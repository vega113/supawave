import { LitElement, css, html } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";

let mentionPopoverCounter = 0;

/**
 * G-PORT-5 (#1114) — view-only mention suggestion popover.
 *
 * Originally (F-3.S2 / #1038) this popover owned its own keydown
 * listener and stole focus to the listbox in `updated()`. That caused
 * the bug tracked at #1125: focus left the composer body, the
 * composer's `_onSelectionChange` saw the caret leave its bounds and
 * dismissed the popover, and ArrowDown was retargeted away from the
 * composer's keydown listener so `_mentionActiveIndex` never advanced.
 *
 * Per G-PORT-5: the composer body is the SOLE owner of mention
 * keyboard navigation. This element is now view-only — it renders the
 * filtered candidate list, reflects the host-supplied `activeIndex` as
 * the visual highlight, and routes mouse clicks back to the host. It
 * never takes focus, never listens for keystrokes, and does not
 * transfer `document.activeElement` even on mousedown over an option.
 */
export class MentionSuggestionPopover extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    candidates: { type: Array },
    activeIndex: { type: Number, attribute: "active-index" },
    focusTargetId: { type: String, attribute: "focus-target-id" }
  };

  static styles = css`
    :host {
      display: inline-block;
      width: max-content;
      max-width: 100%;
      line-height: 0;
      vertical-align: top;
    }

    .popover {
      min-width: 180px;
      max-height: 200px;
      overflow-y: auto;
      border: 1px solid #c7d2de;
      border-radius: 2px;
      padding: 4px 0;
      background: #fff;
      background-color: #fff;
      box-shadow: var(--shell-shadow-overlay, 0 4px 16px rgb(10 38 64 / 16%));
      line-height: normal;
    }

    [role="option"] {
      display: block;
      box-sizing: border-box;
      border: 0;
      border-radius: 0;
      padding: 6px 12px;
      background: transparent;
      color: #202124;
      text-align: left;
      font: 13px Arial, sans-serif;
      line-height: 16px;
      cursor: pointer;
      white-space: nowrap;
      /* G-PORT-5: options must NOT take focus from the composer body.
       * Suppress the default focus ring on the option div itself —
       * the visual highlight comes from aria-selected styling. We
       * keep :focus-visible as a "revert" so a future change that
       * does intentionally focus an option still paints a ring
       * (regression alarm: it would also still trip the composer
       * blur-dismiss path, which the unit-tests cover). */
      outline: none;
    }
    [role="option"]:focus-visible {
      outline: revert;
    }

    [aria-selected="true"] {
      background: #e8f0fe;
      background-color: #e8f0fe;
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
    // G-PORT-5 (#1114): no keydown listener. The composer body owns
    // ArrowUp/Down/Enter/Tab/Escape while the popover is open. See
    // `wavy-composer._onBodyKeydown` and the issue body of #1125.
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
        aria-selected=${active ? "true" : "false"}
        data-address=${candidate.address || ""}
        data-mention-option-index=${index}
        @mousedown=${this._onOptionMouseDown}
        @click=${() => this.selectCandidate(index)}
      >
        @${candidate.address}
      </div>
    `;
  }

  /**
   * G-PORT-5 (#1114): preventDefault on mousedown so the click does
   * NOT transfer `document.activeElement` away from the composer body
   * (which would trip the composer's blur-dismiss path before the
   * @click handler ran). The click event still fires.
   */
  _onOptionMouseDown(event) {
    event.preventDefault();
  }

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
    const numericIndex = Number(index);
    const finiteIndex = Number.isFinite(numericIndex) ? numericIndex : 0;
    return ((finiteIndex % count) + count) % count;
  }

  optionId(index) {
    return `${this.optionIdPrefix}-${index}`;
  }

  safeCandidates() {
    if (!Array.isArray(this.candidates)) {
      return [];
    }
    return this.candidates
      .filter(
        candidate =>
          candidate &&
          typeof candidate === "object" &&
          !Array.isArray(candidate) &&
          typeof candidate.address === "string" &&
          candidate.address.trim() !== ""
      )
      .map(candidate => ({
        ...candidate,
        address: candidate.address.trim()
      }));
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
