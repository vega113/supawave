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
      gap: 14px;
      padding: 14px;
      border: 1px solid var(--shell-color-divider-subtle, #e5edf5);
      border-radius: 16px;
      background: var(--shell-color-surface-shell, #fff);
    }

    h2,
    h3 {
      margin: 0 0 8px;
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
