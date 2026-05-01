import { LitElement, css, html } from "lit";

export class ShellStatusStrip extends LitElement {
  static properties = {
    connectionState: { type: String, attribute: "data-connection-state" },
    saveState: { type: String, attribute: "data-save-state" },
    routeState: { type: String, attribute: "data-route-state" }
  };

  static styles = css`
    :host {
      display: block;
    }

    aside {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px var(--shell-space-inset-panel, 18px);
      background: var(--shell-color-surface-shell, #fff);
      border-top: 1px solid var(--shell-color-divider-subtle, #e5edf5);
      color: var(--shell-color-text-muted, #5b6b80);
      font-size: 0.85rem;
    }

    .chip {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      border-radius: 999px;
      border: 1px solid #d7e3ef;
      background: #f0f4f8;
      color: #0077b6;
      font-size: 13px;
      line-height: 1;
    }

    .chip[data-state="offline"],
    .chip[data-state="unsaved"],
    .chip[data-state="loading"] {
      color: #8f5e00;
      background: #fff7e6;
      border-color: #f1d18a;
    }

    .chip::before {
      content: "";
      display: block;
    }

    .chip[data-status-chip="connection"]::before {
      width: 11px;
      height: 7px;
      border: 2px solid currentColor;
      border-top: 0;
      border-radius: 0 0 12px 12px;
    }

    .chip[data-status-chip="saved"]::before {
      width: 10px;
      height: 5px;
      border-left: 2px solid currentColor;
      border-bottom: 2px solid currentColor;
      transform: rotate(-45deg);
      margin-top: -2px;
    }

    .chip[data-status-chip="route"]::before {
      width: 8px;
      height: 8px;
      border-radius: 999px;
      background: currentColor;
    }

    .status-live-text {
      position: absolute;
      width: 1px;
      height: 1px;
      margin: -1px;
      padding: 0;
      overflow: hidden;
      clip: rect(0 0 0 0);
      clip-path: inset(50%);
      white-space: nowrap;
      border: 0;
    }
  `;

  constructor() {
    super();
    this.connectionState = "online";
    this.saveState = "saved";
    this.routeState = "ready";
  }

  _connectionLabel() {
    return this.connectionState === "offline" ? "Offline" : "Online";
  }

  _saveLabel() {
    return this.saveState === "unsaved" || this.saveState === "saving" ? "Saving changes" : "Saved";
  }

  _routeLabel() {
    switch (this.routeState) {
      case "selected-wave":
        return "Selected wave active";
      case "search":
        return "Search results active";
      case "loading":
        return "Loading workspace";
      default:
        return "Workspace ready";
    }
  }

  render() {
    return html`
      <aside role="status" aria-live="polite">
        <span
          class="chip"
          data-status-chip="connection"
          data-state=${this.connectionState}
          aria-label=${this._connectionLabel()}
          title=${this._connectionLabel()}
        ></span>
        <span
          class="chip"
          data-status-chip="saved"
          data-state=${this.saveState}
          aria-label=${this._saveLabel()}
          title=${this._saveLabel()}
        ></span>
        <span
          class="chip"
          data-status-chip="route"
          data-state=${this.routeState}
          aria-label=${this._routeLabel()}
          title=${this._routeLabel()}
        ></span>
        <slot class="status-live-text"></slot>
      </aside>
    `;
  }
}

if (!customElements.get("shell-status-strip")) {
  customElements.define("shell-status-strip", ShellStatusStrip);
}
