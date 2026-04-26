import { LitElement, css, html } from "lit";
import "../design/wavy-depth-nav.js";

/**
 * <wavy-depth-nav-bar> — F-2 (#1037, R-3.7-chrome; slice 2 #1046)
 * depth-navigation chrome that ships G.2 (Up one level) and G.3 (Back to
 * top of wave). G.1 (drill-in) lives on `<wave-blip>` already; G.4–G.6
 * (URL state, keyboard shortcuts, awareness pill) are S5 territory.
 *
 * The bar composes the F-0 `<wavy-depth-nav>` recipe for the breadcrumb
 * path and adds two new chrome buttons + a slot for the G.6 awareness
 * pill that S5 will fill from `J2clSelectedWaveView`.
 *
 * Properties:
 * - currentDepthBlipId: String — current depth focus (empty = top of wave)
 * - parentDepthBlipId: String — parent depth focus (the up-one-level target)
 * - parentAuthorName: String — display name for the up-one-level button label
 * - crumbs: Array<{label, href?, current?, blipId?}> — passed through to the
 *   inner `<wavy-depth-nav>`. The `blipId` field is S2-specific (S5 wires it
 *   to the URL state writer); the inner recipe ignores unknown fields.
 * - unreadAboveCount: Number — reserved for S5 (G.6 awareness pill counter).
 *
 * Events emitted (CustomEvent, bubbles + composed):
 * - `wavy-depth-up` (G.2) — `{detail: {fromBlipId, toBlipId}}`
 * - `wavy-depth-root` (G.3) — `{detail: {fromBlipId}}`
 * - `wavy-depth-jump-to-crumb` — `{detail: {blipId}}` — fires when a
 *   crumb in the inner `<wavy-depth-nav>` is clicked AND has a `blipId`.
 *   S5 consumes for URL state updates.
 *
 * Hidden: when `currentDepthBlipId === ""` AND `crumbs.length === 0`
 * (top-of-wave with no breadcrumb context), the bar reflects the
 * `hidden` attribute on its host.
 */
export class WavyDepthNavBar extends LitElement {
  static properties = {
    currentDepthBlipId: {
      type: String,
      attribute: "current-depth-blip-id",
      reflect: true
    },
    parentDepthBlipId: { type: String, attribute: "parent-depth-blip-id" },
    parentAuthorName: { type: String, attribute: "parent-author-name" },
    crumbs: { attribute: false },
    unreadAboveCount: { type: Number, attribute: "unread-above-count" }
  };

  static styles = css`
    :host {
      display: block;
      background: var(--wavy-bg-surface, #11192a);
      border-bottom: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      padding: var(--wavy-spacing-3, 12px);
    }
    :host([hidden]) {
      display: none;
    }
    .bar {
      display: flex;
      align-items: center;
      gap: var(--wavy-spacing-3, 12px);
    }
    .left {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
    }
    .center {
      flex: 1 1 auto;
      min-width: 0;
    }
    .right {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
    }
    button {
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      border: 1px solid transparent;
      border-radius: var(--wavy-radius-pill, 9999px);
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      cursor: pointer;
      transition: color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        border-color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    button:hover {
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      border-color: var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
    }
    button:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }
    .glyph {
      display: inline-block;
      margin-right: 4px;
    }
  `;

  constructor() {
    super();
    this.currentDepthBlipId = "";
    this.parentDepthBlipId = "";
    this.parentAuthorName = "";
    this.crumbs = [];
    this.unreadAboveCount = 0;
    this._onInnerClick = this._onInnerClick.bind(this);
  }

  willUpdate(changed) {
    super.willUpdate?.(changed);
    // Reflect hidden when there's nothing to show.
    const empty =
      !this.currentDepthBlipId &&
      (!Array.isArray(this.crumbs) || this.crumbs.length === 0);
    if (empty) {
      this.setAttribute("hidden", "");
    } else {
      this.removeAttribute("hidden");
    }
  }

  _onUpOneLevel(event) {
    event.stopPropagation();
    this.dispatchEvent(
      new CustomEvent("wavy-depth-up", {
        bubbles: true,
        composed: true,
        detail: {
          fromBlipId: this.currentDepthBlipId,
          toBlipId: this.parentDepthBlipId
        }
      })
    );
  }

  _onUpToWave(event) {
    event.stopPropagation();
    this.dispatchEvent(
      new CustomEvent("wavy-depth-root", {
        bubbles: true,
        composed: true,
        detail: { fromBlipId: this.currentDepthBlipId }
      })
    );
  }

  _onInnerClick(event) {
    // Delegate crumb clicks: walk up to the closest <a> or <span> with a
    // matching crumb index, then look up the blipId.
    const path = event.composedPath ? event.composedPath() : [event.target];
    const items = Array.isArray(this.crumbs) ? this.crumbs : [];
    for (const node of path) {
      if (!node || node.nodeType !== 1) continue;
      if (node === this || node.tagName === "WAVY-DEPTH-NAV") break;
      // The recipe renders crumbs in order; match by text content as a
      // best-effort (the recipe doesn't carry data-attrs on crumbs).
      const label = node.textContent ? node.textContent.trim() : "";
      const match = items.find((c) => (c.label || "") === label);
      if (match && match.blipId) {
        event.stopPropagation();
        this.dispatchEvent(
          new CustomEvent("wavy-depth-jump-to-crumb", {
            bubbles: true,
            composed: true,
            detail: { blipId: match.blipId }
          })
        );
        return;
      }
    }
  }

  _upOneLevelLabel() {
    return this.parentAuthorName
      ? `Up one level to ${this.parentAuthorName}'s thread`
      : "Up one level";
  }

  render() {
    const items = Array.isArray(this.crumbs) ? this.crumbs : [];
    return html`
      <div class="bar" role="toolbar" aria-label="Depth navigation">
        <div class="left">
          <button
            type="button"
            data-action="up-one-level"
            aria-label=${this._upOneLevelLabel()}
            @click=${this._onUpOneLevel}
          >
            <span class="glyph" aria-hidden="true">▲</span>
            ${this.parentAuthorName || "Up"}
          </button>
        </div>
        <div class="center">
          <wavy-depth-nav
            .crumbs=${items}
            @click=${this._onInnerClick}
          ></wavy-depth-nav>
        </div>
        <div class="right">
          <slot name="awareness-pill"></slot>
          <button
            type="button"
            data-action="up-to-wave"
            aria-label="Back to top of wave"
            @click=${this._onUpToWave}
          >
            <span class="glyph" aria-hidden="true">⌃</span>
            Up to wave
          </button>
        </div>
      </div>
    `;
  }
}

if (!customElements.get("wavy-depth-nav-bar")) {
  customElements.define("wavy-depth-nav-bar", WavyDepthNavBar);
}
