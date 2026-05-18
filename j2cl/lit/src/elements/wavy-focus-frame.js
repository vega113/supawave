import { LitElement, css, html } from "lit";

/**
 * <wavy-focus-frame> — F-2 (#1037, R-3.2; slice 2 #1046) landmark + visual
 * frame that paints the cyan focus ring around the currently focused blip.
 *
 * The element does NOT own focus state — that lives in
 * J2clReadSurfaceDomRenderer. The renderer dispatches a
 * `wavy-focus-changed` CustomEvent on its `host` (= the
 * `.j2cl-read-surface` element) whenever focus changes. This element
 * listens on its parent (which must be the same `host`) and updates its
 * own `focusedBlipId` + `bounds` reactive properties. The frame is then
 * rendered as an absolutely-positioned overlay child element.
 *
 * The element is mounted inside the renderer's `host` (NOT contentList,
 * NOT the card) so the bounds-measurement node, the positioning ancestor,
 * and the event-source node are all the same element. See plan
 * §3.R-3.2.3 for the coordinate-space reconciliation.
 *
 * Public API:
 * - focusedBlipId: String — currently focused blip id (empty = no focus)
 * - bounds: {top, left, width, height} — host-local pixel coordinates
 *
 * Events consumed (on parent, captured in connectedCallback):
 * - `wavy-focus-changed` — `{detail: {blipId, bounds, key}}`
 */
export class WavyFocusFrame extends LitElement {
  static properties = {
    focusedBlipId: { attribute: false },
    bounds: { attribute: false }
  };

  static styles = css`
    :host {
      /* Take no layout space; the inner frame is absolutely positioned
       * relative to the nearest positioned ancestor (the renderer's
       * host = .j2cl-read-surface, which T4 ensures has position: relative). */
      display: contents;
    }
    .frame {
      position: absolute;
      pointer-events: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      border-radius: var(--wavy-radius-card, 12px);
      transition: top var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        left var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        width var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        height var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
      will-change: top, left, width, height;
    }
    .frame[hidden] {
      display: none;
    }
    @media (prefers-reduced-motion: reduce) {
      .frame {
        transition: none;
      }
    }
  `;

  constructor() {
    super();
    this.focusedBlipId = "";
    this.bounds = { top: 0, left: 0, width: 0, height: 0 };
    this._frameVisible = false;
    this._onFocusChanged = this._onFocusChanged.bind(this);
    this._listeningOn = null;
  }

  connectedCallback() {
    super.connectedCallback();
    // The frame listens on its parent — which must be the renderer's host
    // (`.j2cl-read-surface`). The event fires from the renderer (Java)
    // when focusBlip(...) runs.
    this._listeningOn = this.parentNode || this.parentElement;
    if (this._listeningOn && typeof this._listeningOn.addEventListener === "function") {
      this._listeningOn.addEventListener("wavy-focus-changed", this._onFocusChanged);
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    if (this._listeningOn && typeof this._listeningOn.removeEventListener === "function") {
      this._listeningOn.removeEventListener("wavy-focus-changed", this._onFocusChanged);
    }
    this._listeningOn = null;
  }

  _onFocusChanged(event) {
    if (!event || !event.detail) {
      return;
    }
    const { blipId, bounds } = event.detail;
    const nextBlipId = typeof blipId === "string" ? blipId : "";
    const explicitKey =
      typeof event.detail.key === "string" && event.detail.key.trim() !== "";
    if (!nextBlipId) {
      this._frameVisible = false;
    } else if (explicitKey) {
      this._frameVisible = true;
    } else if (nextBlipId !== this.focusedBlipId) {
      this._frameVisible = false;
    }
    this.focusedBlipId = nextBlipId;
    this.bounds =
      bounds && typeof bounds === "object"
        ? {
            top: Number(bounds.top) || 0,
            left: Number(bounds.left) || 0,
            width: Number(bounds.width) || 0,
            height: Number(bounds.height) || 0
          }
        : { top: 0, left: 0, width: 0, height: 0 };
  }

  render() {
    const hidden = !this.focusedBlipId || !this._frameVisible;
    const style = `top: ${this.bounds.top}px; left: ${this.bounds.left}px; width: ${this.bounds.width}px; height: ${this.bounds.height}px;`;
    return html`<div
      class="frame"
      part="frame"
      data-focused-blip-id=${this.focusedBlipId}
      ?hidden=${hidden}
      style=${style}
      aria-hidden="true"
    ></div>`;
  }
}

if (!customElements.get("wavy-focus-frame")) {
  customElements.define("wavy-focus-frame", WavyFocusFrame);
}
