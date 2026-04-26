import { LitElement, css, html } from "lit";

/**
 * <wavy-wave-nav-row> — F-2 (#1037, R-3.4; slice 2 #1046) wave-level nav
 * toolbar covering audit rows E.1–E.10. The element renders 10 buttons
 * in fixed order (Recent, Next Unread, Previous, Next, End, Prev @,
 * Next @, Archive, Pin, Version History) and emits one CustomEvent per
 * button click.
 *
 * Wired ABOVE the content list (per-wave), NOT per-blip (the audit
 * called out S1's wave-list-level wiring as the bug). The nav row
 * receives the focus anchor via the `selectedBlipId` reactive prop and
 * carries it in every dispatched event so S5 can route the walk against
 * the correct anchor.
 *
 * Properties:
 * - selectedBlipId: String — current focus anchor (echoed into event detail)
 * - sourceWaveId: String — current wave id (echoed into event detail)
 * - unreadCount: Number — drives E.2 cyan emphasis
 * - mentionCount: Number — drives E.6/E.7 violet emphasis
 * - pinned: Boolean — drives E.9 cyan glyph + aria-label toggle
 * - archived: Boolean — drives E.8 aria-label toggle (S5 will wire data)
 *
 * Events emitted (CustomEvent, bubbles + composed, detail = {selectedBlipId, sourceWaveId}):
 * - `wave-nav-recent-requested` (E.1)
 * - `wave-nav-next-unread-requested` (E.2)
 * - `wave-nav-previous-requested` (E.3)
 * - `wave-nav-next-requested` (E.4)
 * - `wave-nav-end-requested` (E.5)
 * - `wave-nav-prev-mention-requested` (E.6)
 * - `wave-nav-next-mention-requested` (E.7)
 * - `wave-nav-archive-toggle-requested` (E.8)
 * - `wave-nav-pin-toggle-requested` (E.9)
 * - `wave-nav-version-history-requested` (E.10) — also bound to `h`/`H` keyboard
 *
 * Keyboard: `h` or `H` emits the version-history event when the
 * user is not in a text-input context. The keydown listener is bound
 * to the closest `[data-j2cl-selected-wave-host]` ancestor (NOT
 * document) to prevent multi-fire when more than one nav-row mounts
 * (e.g. server-first + cold-mount during boot, S6 demo route).
 */
export class WavyWaveNavRow extends LitElement {
  static properties = {
    selectedBlipId: { type: String, attribute: "selected-blip-id", reflect: true },
    sourceWaveId: { type: String, attribute: "source-wave-id", reflect: true },
    unreadCount: { type: Number, attribute: "unread-count", reflect: true },
    mentionCount: { type: Number, attribute: "mention-count", reflect: true },
    pinned: { type: Boolean, reflect: true },
    archived: { type: Boolean, reflect: true }
  };

  static styles = css`
    :host {
      display: block;
      position: relative;
      /* container-type:inline-size enables the @container query below.
       * Without this declaration the mobile-collapse path is dead code. */
      container-type: inline-size;
      container-name: wave-nav-row;
      background: var(--wavy-bg-surface, #11192a);
      border-bottom: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-3, 12px);
    }
    nav {
      display: flex;
      align-items: center;
      gap: var(--wavy-spacing-2, 8px);
      flex-wrap: nowrap;
    }
    button {
      background: transparent;
      color: var(--wavy-text-muted, rgba(232, 240, 255, 0.62));
      border: 1px solid transparent;
      border-radius: var(--wavy-radius-pill, 9999px);
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      padding: var(--wavy-spacing-1, 4px) var(--wavy-spacing-2, 8px);
      cursor: pointer;
      white-space: nowrap;
      transition: color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        border-color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        background-color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    button:hover {
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      background: var(--wavy-signal-cyan-soft, rgba(34, 211, 238, 0.22));
      border-color: var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
    }
    button:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }
    button[disabled] {
      opacity: 0.45;
      cursor: not-allowed;
    }
    /* E.2 next-unread cyan emphasis when unread > 0 */
    button[data-action="next-unread"][data-emphasis="cyan"] {
      color: var(--wavy-signal-cyan, #22d3ee);
    }
    /* E.6/E.7 mention violet emphasis when mentionCount > 0 */
    button[data-action="prev-mention"][data-emphasis="violet"],
    button[data-action="next-mention"][data-emphasis="violet"] {
      color: var(--wavy-signal-violet, #7c3aed);
    }
    /* E.9 pinned cyan glyph */
    button[data-action="pin"][data-emphasis="cyan"] {
      color: var(--wavy-signal-cyan, #22d3ee);
    }

    /* Mobile/narrow collapse — the overflow button + menu are hidden
     * by default on full-width and surface only when the container is
     * narrow. The 3 collapsed actions are also hidden when narrow. */
    .overflow-trigger,
    .overflow-menu {
      display: none;
    }
    @container wave-nav-row (max-width: 480px) {
      button[data-action="prev-mention"],
      button[data-action="next-mention"],
      button[data-action="version-history"] {
        display: none;
      }
      .overflow-trigger {
        display: inline-flex;
      }
      .overflow-menu[data-open="true"] {
        display: block;
        position: absolute;
        right: var(--wavy-spacing-2, 8px);
        background: var(--wavy-bg-surface, #11192a);
        border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
        border-radius: var(--wavy-radius-card, 12px);
        padding: var(--wavy-spacing-2, 8px);
        z-index: 1;
        margin: 0;
        list-style: none;
      }
      .overflow-menu[data-open="true"] button {
        display: block;
        width: 100%;
        text-align: left;
      }
    }
  `;

  constructor() {
    super();
    this.selectedBlipId = "";
    this.sourceWaveId = "";
    this.unreadCount = 0;
    this.mentionCount = 0;
    this.pinned = false;
    this.archived = false;
    this._overflowOpen = false;
    this._onKeyDown = this._onKeyDown.bind(this);
    this._keyTarget = null;
  }

  connectedCallback() {
    super.connectedCallback();
    // Resolve the binding target via closest('[data-j2cl-selected-wave-host]'),
    // falling back to document only when no ancestor is found. Binding to
    // the card root prevents multi-fire when more than one nav-row mounts.
    this._keyTarget =
      this.closest && this.closest("[data-j2cl-selected-wave-host]")
        ? this.closest("[data-j2cl-selected-wave-host]")
        : document;
    this._keyTarget.addEventListener("keydown", this._onKeyDown);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    if (this._keyTarget) {
      this._keyTarget.removeEventListener("keydown", this._onKeyDown);
      this._keyTarget = null;
    }
  }

  _onKeyDown(event) {
    if (!event || (event.key !== "H" && event.key !== "h")) {
      return;
    }
    if (event.ctrlKey || event.metaKey || event.altKey) {
      return;
    }
    const target = event.target;
    if (target && target.nodeType === 1) {
      const tag = target.tagName;
      if (
        tag === "INPUT" ||
        tag === "TEXTAREA" ||
        target.isContentEditable === true
      ) {
        return;
      }
    }
    event.preventDefault();
    this._emit("wave-nav-version-history-requested", { keyboard: true });
  }

  _emit(eventName, extra) {
    const detail = {
      selectedBlipId: this.selectedBlipId,
      sourceWaveId: this.sourceWaveId,
      ...(extra || {})
    };
    this.dispatchEvent(
      new CustomEvent(eventName, { bubbles: true, composed: true, detail })
    );
  }

  _onClick(action) {
    return (event) => {
      event.stopPropagation();
      this._emit(`wave-nav-${action}-requested`);
      // Close overflow menu after click (defensive — narrow viewport only)
      if (this._overflowOpen) {
        this._overflowOpen = false;
        this.requestUpdate();
      }
    };
  }

  _onOverflowToggle(event) {
    event.stopPropagation();
    this._overflowOpen = !this._overflowOpen;
    this.requestUpdate();
  }

  _pinAriaLabel() {
    return this.pinned ? "Unpin wave" : "Pin wave";
  }

  _archiveAriaLabel() {
    return this.archived ? "Restore from archive" : "Move wave to archive";
  }

  _nextUnreadEmphasis() {
    return this.unreadCount > 0 ? "cyan" : "muted";
  }

  _mentionEmphasis() {
    return this.mentionCount > 0 ? "violet" : "muted";
  }

  _pinEmphasis() {
    return this.pinned ? "cyan" : "muted";
  }

  render() {
    return html`
      <nav aria-label="Wave navigation">
        <button
          type="button"
          data-action="recent"
          aria-label="Jump to recent activity"
          @click=${this._onClick("recent")}
        >
          Recent
        </button>
        <button
          type="button"
          data-action="next-unread"
          data-emphasis=${this._nextUnreadEmphasis()}
          aria-label="Jump to next unread blip"
          @click=${this._onClick("next-unread")}
        >
          Next unread
        </button>
        <button
          type="button"
          data-action="previous"
          aria-label="Jump to previous blip"
          @click=${this._onClick("previous")}
        >
          Previous
        </button>
        <button
          type="button"
          data-action="next"
          aria-label="Jump to next blip"
          @click=${this._onClick("next")}
        >
          Next
        </button>
        <button
          type="button"
          data-action="end"
          aria-label="Jump to last blip"
          @click=${this._onClick("end")}
        >
          End
        </button>
        <button
          type="button"
          data-action="prev-mention"
          data-emphasis=${this._mentionEmphasis()}
          aria-label="Jump to previous mention"
          @click=${this._onClick("prev-mention")}
        >
          Prev @
        </button>
        <button
          type="button"
          data-action="next-mention"
          data-emphasis=${this._mentionEmphasis()}
          aria-label="Jump to next mention"
          @click=${this._onClick("next-mention")}
        >
          Next @
        </button>
        <button
          type="button"
          data-action="archive"
          aria-label=${this._archiveAriaLabel()}
          @click=${this._onClick("archive-toggle")}
        >
          ${this.archived ? "Restore" : "Archive"}
        </button>
        <button
          type="button"
          data-action="pin"
          data-emphasis=${this._pinEmphasis()}
          aria-label=${this._pinAriaLabel()}
          @click=${this._onClick("pin-toggle")}
        >
          ${this.pinned ? "Unpin" : "Pin"}
        </button>
        <button
          type="button"
          data-action="version-history"
          aria-label="Open version history (h)"
          aria-keyshortcuts="h H"
          @click=${this._onClick("version-history")}
        >
          Version history
        </button>

        <button
          type="button"
          class="overflow-trigger"
          data-action="overflow"
          aria-haspopup="menu"
          aria-expanded=${this._overflowOpen ? "true" : "false"}
          aria-label="More wave navigation actions"
          @click=${this._onOverflowToggle}
        >
          ⋯
        </button>
        <menu
          class="overflow-menu"
          data-open=${this._overflowOpen ? "true" : "false"}
          role="menu"
        >
          <li role="none">
            <button
              type="button"
              role="menuitem"
              data-action="prev-mention"
              data-overflow="true"
              aria-label="Jump to previous mention"
              @click=${this._onClick("prev-mention")}
            >
              Prev @
            </button>
          </li>
          <li role="none">
            <button
              type="button"
              role="menuitem"
              data-action="next-mention"
              data-overflow="true"
              aria-label="Jump to next mention"
              @click=${this._onClick("next-mention")}
            >
              Next @
            </button>
          </li>
          <li role="none">
            <button
              type="button"
              role="menuitem"
              data-action="version-history"
              data-overflow="true"
              aria-label="Open version history (h)"
              @click=${this._onClick("version-history")}
            >
              Version history
            </button>
          </li>
        </menu>
      </nav>
    `;
  }
}

if (!customElements.get("wavy-wave-nav-row")) {
  customElements.define("wavy-wave-nav-row", WavyWaveNavRow);
}
