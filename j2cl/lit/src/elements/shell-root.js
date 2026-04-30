import { LitElement, css, html } from "lit";
import { KEY_ACTION, isEditableTarget, matchShortcut } from "../shortcuts/keybindings.js";
import { moveBlipFocus, clearBlipFocus } from "../shortcuts/blip-focus.js";
import { closeTopmostDialog } from "../shortcuts/dialog-stack.js";

export class ShellRoot extends LitElement {
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
  }

  connectedCallback() {
    super.connectedCallback();
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
    if (typeof window !== "undefined") {
      window.removeEventListener("keydown", this._onWindowKeyDown);
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
      // j/k and New Wave shortcuts inside inputs must reach the input.
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
      <slot name="main"></slot>
      <slot name="rail-extension"></slot>
      <slot name="status"></slot>
    `;
  }
}

if (!customElements.get("shell-root")) {
  customElements.define("shell-root", ShellRoot);
}
