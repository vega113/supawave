import { LitElement, css, html } from "lit";
import { KEY_ACTION, isEditableTarget, matchShortcut } from "../shortcuts/keybindings.js";
import { moveBlipFocus, clearBlipFocus } from "../shortcuts/blip-focus.js";
import { closeTopmostDialog } from "../shortcuts/dialog-stack.js";

export class ShellRoot extends LitElement {
  static RESIZE_STORAGE_KEY = "j2cl.searchRailWidth";
  static MIN_RAIL_WIDTH = 260;
  static MAX_RAIL_WIDTH = 420;
  static RAIL_STEP = 16;

  static styles = css`
    /* V-1 (#1099): the canonical grid for <shell-root> lives in
     * j2cl/lit/src/tokens/shell-tokens.css and targets the host element
     * directly from the document, so it wins over any :host rule
     * declared here (per CSS Scoping spec). Only slot-positioning
     * declarations live in the shadow tree — they apply to the slot
     * placeholders inside the shadow root regardless of what the host
     * grid looks like, so the rail-extension slot resolves to its own
     * named area when the panel is un-hidden by a plugin. */
    slot[name="skip-link"] {
      grid-area: skip;
    }

    slot[name="header"] {
      grid-area: header;
    }

    slot[name="nav"] {
      grid-area: nav;
      min-height: 0;
    }

    slot[name="splitter"] {
      grid-area: splitter;
      min-height: 0;
    }

    slot[name="main"] {
      grid-area: main;
      min-width: 0;
      min-height: 0;
    }

    /* F-4 (#1039 / R-4.7): production rail-extension landing zone. The
     * slotted <wavy-rail-panel> ships hidden on the user route. When a
     * plugin un-hides it, it lands in its own grid row beneath the
     * main region so it never overlaps the wave panel. The auto-sized
     * track collapses to 0 height when the slot is empty / hidden. */
    slot[name="rail-extension"] {
      grid-area: rail-extension;
      min-width: 0;
      min-height: 0;
    }

    slot[name="status"] {
      grid-area: status;
    }
  `;

  constructor() {
    super();
    this._onWindowKeyDown = this._onWindowKeyDown.bind(this);
    this._onResizeKeyDown = this._onResizeKeyDown.bind(this);
    this._onResizePointerDown = this._onResizePointerDown.bind(this);
    this._onResizePointerMove = this._onResizePointerMove.bind(this);
    this._onResizePointerUp = this._onResizePointerUp.bind(this);
    this._onWaveControlsToggled = this._onWaveControlsToggled.bind(this);
    this._resizeStart = null;
  }

  connectedCallback() {
    super.connectedCallback();
    this._restoreRailWidth();
    this.addEventListener("keydown", this._onResizeKeyDown);
    this.addEventListener("pointerdown", this._onResizePointerDown);
    if (typeof document !== "undefined") {
      document.addEventListener("wavy-wave-controls-toggled", this._onWaveControlsToggled);
    }
    // G-PORT-7 (#1116): shell-level keyboard handler. Cloned from the
    // GWT keyboard registry (`KeySignalRouter` + `FocusFrameController.
    // onKeySignal`). The window-level listener captures the document
    // chain after the per-element handlers (which return false +
    // preventDefault when they want to claim the key for themselves)
    // have already run. We use the bubble phase deliberately so that
    // an active mention popover or composer body can still consume
    // its own ArrowDown / Enter / Escape inside its own keydown
    // handler before this one runs.
    if (typeof window !== "undefined") {
      window.addEventListener("keydown", this._onWindowKeyDown);
    }
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    this.removeEventListener("keydown", this._onResizeKeyDown);
    this.removeEventListener("pointerdown", this._onResizePointerDown);
    this._stopResizeTracking();
    if (typeof document !== "undefined") {
      document.removeEventListener("wavy-wave-controls-toggled", this._onWaveControlsToggled);
    }
    if (typeof window !== "undefined") {
      window.removeEventListener("keydown", this._onWindowKeyDown);
    }
  }

  _restoreRailWidth() {
    if (typeof window === "undefined" || !window.localStorage) return;
    const stored = Number(window.localStorage.getItem(ShellRoot.RESIZE_STORAGE_KEY));
    if (!Number.isFinite(stored) || stored <= 0) return;
    this._setRailWidth(stored);
  }

  _onResizeKeyDown(evt) {
    if (!this._isSplitterEvent(evt)) return;
    const current = this._currentRailWidth();
    let next = current;
    if (evt.key === "ArrowLeft") {
      next = current - ShellRoot.RAIL_STEP;
    } else if (evt.key === "ArrowRight") {
      next = current + ShellRoot.RAIL_STEP;
    } else if (evt.key === "Home") {
      next = ShellRoot.MIN_RAIL_WIDTH;
    } else if (evt.key === "End") {
      next = ShellRoot.MAX_RAIL_WIDTH;
    } else {
      return;
    }
    evt.preventDefault();
    evt.stopPropagation();
    this._setRailWidth(next, evt.target);
  }

  _onResizePointerDown(evt) {
    if (!this._isSplitterEvent(evt) || evt.button !== 0) return;
    evt.preventDefault();
    this._resizeStart = {
      x: evt.clientX,
      width: this._currentRailWidth(),
      splitter: evt.target
    };
    if (typeof document !== "undefined") {
      document.addEventListener("pointermove", this._onResizePointerMove);
      document.addEventListener("pointerup", this._onResizePointerUp, { once: true });
    }
  }

  _onResizePointerMove(evt) {
    if (!this._resizeStart) return;
    this._setRailWidth(
      this._resizeStart.width + (evt.clientX - this._resizeStart.x),
      this._resizeStart.splitter
    );
  }

  _onResizePointerUp() {
    this._stopResizeTracking();
  }

  _stopResizeTracking() {
    this._resizeStart = null;
    if (typeof document !== "undefined") {
      document.removeEventListener("pointermove", this._onResizePointerMove);
      document.removeEventListener("pointerup", this._onResizePointerUp);
    }
  }

  _isSplitterEvent(evt) {
    const target = evt && evt.target;
    return !!(
      target &&
      target.getAttribute &&
      target.getAttribute("slot") === "splitter"
    );
  }

  _currentRailWidth() {
    const inline = parseFloat(this.style.getPropertyValue("--j2cl-search-rail-width"));
    if (Number.isFinite(inline) && inline > 0) return inline;
    if (typeof window !== "undefined") {
      const computed = parseFloat(
        window.getComputedStyle(this).getPropertyValue("--j2cl-search-rail-width")
      );
      if (Number.isFinite(computed) && computed > 0) return computed;
    }
    return 296;
  }

  _setRailWidth(width, splitter = null) {
    const next = Math.round(this._clampRailWidth(width));
    this.style.setProperty("--j2cl-search-rail-width", `${next}px`);
    if (splitter && splitter.setAttribute) {
      splitter.setAttribute("aria-valuenow", String(next));
    }
    if (typeof window !== "undefined" && window.localStorage) {
      window.localStorage.setItem(ShellRoot.RESIZE_STORAGE_KEY, String(next));
    }
  }

  _clampRailWidth(width) {
    const viewportMax =
      typeof window === "undefined" || !window.innerWidth
        ? ShellRoot.MAX_RAIL_WIDTH
        : Math.max(ShellRoot.MIN_RAIL_WIDTH, Math.floor(window.innerWidth * 0.5));
    const max = Math.min(ShellRoot.MAX_RAIL_WIDTH, viewportMax);
    return Math.max(ShellRoot.MIN_RAIL_WIDTH, Math.min(max, width));
  }

  _onWaveControlsToggled(evt) {
    const pressed = !!(evt.detail && evt.detail.pressed);
    if (pressed) {
      this.setAttribute("data-wave-controls-compact", "true");
    } else {
      this.removeAttribute("data-wave-controls-compact");
    }
  }

  _onWindowKeyDown(evt) {
    // If the event was already swallowed by an inner element (e.g.
    // the mention popover called preventDefault on Enter), respect
    // that — don't double-fire.
    if (evt.defaultPrevented) return;
    const match = matchShortcut(evt);
    if (!match) return;
    if (!match.global && isEditableTarget(evt)) {
      // j/k inside inputs must reach the input. New Wave uses a
      // browser-level Ctrl/Meta shortcut, so suppress the browser
      // default while leaving the editable content untouched.
      if (match.action === KEY_ACTION.OPEN_NEW_WAVE) {
        evt.preventDefault();
        evt.stopPropagation();
      }
      // Esc remains global per the matcher and bypasses this guard.
      return;
    }
    const handled = this._dispatchAction(match.action, evt);
    if (handled) {
      evt.preventDefault();
      evt.stopPropagation();
    }
  }

  _dispatchAction(action, evt) {
    switch (action) {
      case KEY_ACTION.BLIP_FOCUS_NEXT:
      case KEY_ACTION.BLIP_FOCUS_PREV: {
        // Skip while a modal is open — modal surfaces trap navigation.
        if (this._modalIsOpen()) return false;
        const direction = action === KEY_ACTION.BLIP_FOCUS_NEXT ? 1 : -1;
        return moveBlipFocus(direction);
      }
      case KEY_ACTION.OPEN_NEW_WAVE: {
        // The J2CL root shell already listens for this on document.body
        // (J2clRootShellController.java:149-151); GWT exposes the same
        // semantic on its toolbar, so drives outside the J2CL view
        // route through their own affordance.
        const ev = new CustomEvent("wavy-new-wave-requested", {
          bubbles: true,
          composed: true,
          detail: { source: "keyboard-shortcut" }
        });
        document.body.dispatchEvent(ev);
        return true;
      }
      case KEY_ACTION.CLOSE_TOPMOST: {
        // ONE action per keypress. Close the topmost open dialog
        // first; if nothing was open, drop the focused-blip selection.
        if (closeTopmostDialog()) return true;
        return clearBlipFocus();
      }
      default:
        return false;
    }
  }

  _modalIsOpen() {
    // Match the dialog-stack tier-1 modals AND any element with
    // role=dialog that is open. The wavy-* dialogs reflect `open`
    // either as a property or as the `open` HTML attribute.
    const modals = document.querySelectorAll(
      "wavy-confirm-dialog, wavy-link-modal, wavy-version-history, wavy-search-help, [role=\"dialog\"][open]"
    );
    for (const m of modals) {
      if (m.open === true || (m.hasAttribute && m.hasAttribute("open"))) {
        return true;
      }
    }
    return false;
  }

  render() {
    return html`
      <slot name="skip-link"></slot>
      <slot name="header"></slot>
      <slot name="nav"></slot>
      <slot name="splitter"></slot>
      <slot name="main"></slot>
      <slot name="rail-extension"></slot>
      <slot name="status"></slot>
    `;
  }
}

if (!customElements.get("shell-root")) {
  customElements.define("shell-root", ShellRoot);
}
