import { LitElement, css, html } from "lit";

export class ShellStatusStrip extends LitElement {
  static styles = css`
    :host {
      display: block;
    }

    aside {
      padding: 6px var(--shell-space-inset-panel, 18px);
      background: var(--shell-color-surface-shell, #fff);
      border-top: 1px solid var(--shell-color-divider-subtle, #e5edf5);
      color: var(--shell-color-text-muted, #5b6b80);
      font-size: 0.85rem;
    }
  `;

  render() {
    return html`<aside role="status" aria-live="polite"><slot></slot></aside>`;
  }
}

if (!customElements.get("shell-status-strip")) {
  customElements.define("shell-status-strip", ShellStatusStrip);
}
