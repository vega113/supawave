import { LitElement, css, html } from "lit";

export class ReactionAuthorsPopover extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    emoji: { type: String },
    reactionLabel: { type: String, attribute: "reaction-label" },
    authors: { type: Array },
    focusTargetId: { type: String, attribute: "focus-target-id" }
  };

  static styles = css`
    .popover {
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 14px;
      padding: 10px 12px;
      background: var(--shell-color-surface-overlay, #fff);
      box-shadow: var(--shell-shadow-overlay, 0 16px 44px rgb(10 38 64 / 18%));
    }
  `;

  constructor() {
    super();
    this.open = false;
    this.emoji = "";
    this.reactionLabel = "";
    this.authors = [];
    this.focusTargetId = "";
    this.addEventListener("keydown", this.onKeyDown);
  }

  updated(changed) {
    if (changed.has("open") && this.open) {
      queueMicrotask(() => this.renderRoot.querySelector(".popover")?.focus());
    }
  }

  render() {
    if (!this.open) {
      return "";
    }
    const authors = this.safeAuthors();
    const label = this.reactionLabel || this.emoji || "this reaction";
    return html`
      <section
        class="popover"
        role="region"
        tabindex="-1"
        aria-label=${`Authors for ${label}`}
      >
        ${authors.length === 0
          ? html`<p>No reactions yet</p>`
          : html`
              <ul>
                ${authors.map(author => html`<li>${author}</li>`)}
              </ul>
            `}
      </section>
    `;
  }

  onKeyDown = (event) => {
    if (!this.open || event.key !== "Escape") {
      return;
    }
    event.preventDefault();
    this.close("escape");
  };

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

  safeAuthors() {
    return Array.isArray(this.authors) ? this.authors : [];
  }
}

if (!customElements.get("reaction-authors-popover")) {
  customElements.define("reaction-authors-popover", ReactionAuthorsPopover);
}
