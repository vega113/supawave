import { LitElement, css, html } from "lit";

export class ComposerShell extends LitElement {
  static properties = {
    _hasReplyContent: { state: true },
  };

  static styles = css`
    :host {
      display: block;
    }

    .shell {
      display: grid;
      gap: 12px;
      padding: 8px;
      border: 1px solid var(--shell-color-divider-subtle, #e2e8f0);
      border-radius: 4px;
      background: var(--shell-color-surface-shell, #fff);
    }

    h2,
    h3 {
      margin: 0 0 8px;
      font-size: 1.05rem;
    }
  `;

  constructor() {
    super();
    this._hasReplyContent = false;
  }

  _onReplySlotChange(e) {
    this._hasReplyContent = e.target.assignedElements({ flatten: true }).length > 0;
  }

  render() {
    return html`
      <div class="shell">
        <section aria-labelledby="composer-create-title">
          <h2 id="composer-create-title">New wave</h2>
          <slot name="create"></slot>
        </section>
        <section
          aria-labelledby="composer-reply-title"
          ?hidden=${!this._hasReplyContent}
        >
          <h3 id="composer-reply-title">Reply</h3>
          <slot name="reply" @slotchange=${this._onReplySlotChange}></slot>
        </section>
        <slot name="status"></slot>
      </div>
    `;
  }
}

if (!customElements.get("composer-shell")) {
  customElements.define("composer-shell", ComposerShell);
}
