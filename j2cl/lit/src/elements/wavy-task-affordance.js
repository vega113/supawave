import { LitElement, css, html } from "lit";
import "./task-metadata-popover.js";

/**
 * <wavy-task-affordance> — F-3.S2 (#1038, R-5.4) per-blip task toggle
 * + metadata trigger. Mounted by <wave-blip> next to the per-blip
 * toolbar in the existing `metadata` slot. Reflects the task state
 * via aria-checked + data-task-completed (so the parent <wave-blip>'s
 * `data-task-completed` attribute stays in sync via property binding).
 *
 * Public API:
 * - blipId, waveId — identity used by the emitted CustomEvent details.
 * - completed (Boolean, attribute "data-task-completed", reflected) —
 *   the current toggle state. Sourced from the per-blip projection.
 * - assigneeAddress (String, attribute "data-task-assignee") —
 *   currently-assigned owner's address.
 * - dueDate (String, attribute "data-task-due-date") — YYYY-MM-DD.
 * - bodySize (Number, attribute "data-blip-doc-size") — wavelet item
 *   count for the backing blip body.
 * - participants (Array of {address, displayName}) — passed to the
 *   inner <task-metadata-popover>.
 *
 * Events emitted (CustomEvent, bubbles + composed):
 * - `wave-blip-task-toggled` — `{detail: {blipId, waveId, completed, bodySize}}`
 *   when the toggle button is clicked. The compose-surface view
 *   listener routes this to `Listener.onTaskToggled(blipId, completed, bodySize)`.
 * - `wave-blip-task-metadata-changed` —
 *   `{detail: {blipId, waveId, assigneeAddress, dueDate, bodySize}}` when the
 *   inner <task-metadata-popover> emits its `task-metadata-submit`.
 *
 * UX rationale (per the slice plan):
 * - Single click on the toggle button toggles completion. We do NOT
 *   reuse double-click for the metadata sheet because that conflicts
 *   with browser text selection on adjacent blip text.
 * - A separate "details" overflow button opens
 *   <task-metadata-popover>; pressing Escape inside the popover
 *   restores focus to the affordance trigger.
 *
 * Token contract:
 * - Active-completed soft fill uses `--wavy-signal-amber` per the F-0
 *   amber/violet split (amber = tasks/time-sensitive).
 * - Focus ring uses `--wavy-focus-ring`.
 */
export class WavyTaskAffordance extends LitElement {
  static properties = {
    blipId: { type: String, attribute: "data-blip-id", reflect: true },
    waveId: { type: String, attribute: "data-wave-id", reflect: true },
    completed: { type: Boolean, attribute: "data-task-completed", reflect: true },
    assigneeAddress: { type: String, attribute: "data-task-assignee", reflect: true },
    dueDate: { type: String, attribute: "data-task-due-date", reflect: true },
    bodySize: { type: Number, attribute: "data-blip-doc-size", reflect: true },
    participants: { type: Array },
    // J-UI-6 (#1084, R-5.4 — "Labels translate"): every visible / aria / live
    // string is exposed as a property so the Java view (or a future lit-i18n
    // slice) can override per-locale without re-architecting the element.
    // English defaults are preserved verbatim from F-3.S2 so the change is
    // non-breaking. Properties are not attribute-reflected because the strings
    // are typically multi-word and would clutter the rendered DOM.
    labelToggleOpen: { type: String },
    labelToggleDone: { type: String },
    labelAriaCheck: { type: String },
    labelAriaUncheck: { type: String },
    labelDetails: { type: String },
    labelAnnounceDone: { type: String },
    labelAnnounceOpen: { type: String }
  };

  static styles = css`
    :host {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-1, 4px);
      position: relative;
    }
    button {
      background: #f1f3f4;
      color: #5f6368;
      border: 1px solid #d0d7de;
      border-radius: 999px;
      font: var(--wavy-type-meta, 11px / 16px Arial, sans-serif);
      font-weight: 600;
      line-height: 16px;
      padding: 2px 8px;
      cursor: pointer;
      transition: color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        border-color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1)),
        background-color var(--wavy-motion-focus-duration, 180ms)
          var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
    }
    button:hover {
      color: #174ea6;
      background: #e8f0fe;
      border-color: #174ea6;
    }
    button:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px rgba(0, 119, 182, 0.16));
    }
    button[aria-checked="true"] {
      background: #fff4e5;
      border-color: #9a6700;
      color: #9a6700;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
    }
    .popover-host {
      position: absolute;
      top: 100%;
      right: 0;
      z-index: 10;
    }
    :host(:not([data-popover-open])) .popover-host {
      display: none;
    }
  `;

  constructor() {
    super();
    this.blipId = "";
    this.waveId = "";
    this.completed = false;
    this.assigneeAddress = "";
    this.dueDate = "";
    this.bodySize = 0;
    this.participants = [];
    this._announceText = "";
    this._popoverOpen = false;
    this.labelToggleOpen = "☐";
    this.labelToggleDone = "☑";
    this.labelAriaCheck = "Mark task complete";
    this.labelAriaUncheck = "Mark task open";
    this.labelDetails = "Edit task details";
    this.labelAnnounceDone = "Task completed";
    this.labelAnnounceOpen = "Task reopened";
  }

  _toggle() {
    const next = !this.completed;
    // Optimistic update: reflect the next state immediately so the
    // affordance feels responsive. The Java view's render(model)
    // overwrites this with the persisted value; if the server rejects
    // the toggle the model snaps back.
    this.completed = next;
    this._announceText = next ? this.labelAnnounceDone : this.labelAnnounceOpen;
    this.requestUpdate();
    this.dispatchEvent(
      new CustomEvent("wave-blip-task-toggled", {
        bubbles: true,
        composed: true,
        detail: {
          blipId: this.blipId,
          waveId: this.waveId,
          completed: next,
          bodySize: this.bodySize || 0
        }
      })
    );
  }

  _openDetails() {
    this._popoverOpen = true;
    this.toggleAttribute("data-popover-open", true);
    this.requestUpdate();
  }

  _onPopoverClose = (event) => {
    this._popoverOpen = false;
    this.toggleAttribute("data-popover-open", false);
    this.requestUpdate();
    // Restore focus to the trigger so keyboard users land back on the
    // affordance — matches the F-0 overlay-close focus contract.
    const trigger = this.renderRoot && this.renderRoot.querySelector('[data-task-details-trigger="true"]');
    if (trigger && typeof trigger.focus === "function") {
      // Defer one rAF so Lit has flushed the popover removal first.
      requestAnimationFrame(() => trigger.focus());
    }
    // Suppress the bubble — overlay-close is internal between this
    // affordance and its inner popover; the parent <wave-blip> should
    // not see it.
    event.stopPropagation();
  };

  _onMetadataSubmit = (event) => {
    const detail = event.detail || {};
    const assignee = String(detail.assigneeAddress || "").trim();
    const due = String(detail.dueDate || "").trim();
    this.assigneeAddress = assignee;
    this.dueDate = due;
    this._popoverOpen = false;
    this.toggleAttribute("data-popover-open", false);
    // F-3.S2 (#1068): _popoverOpen is internal state, not a Lit
    // reactive property. When assignee/dueDate are unchanged, no
    // observed property mutates and Lit otherwise skips the re-render
    // that would tear down the inner <task-metadata-popover>. Force
    // an update so the dialog actually closes on submit.
    this.requestUpdate();
    this.dispatchEvent(
      new CustomEvent("wave-blip-task-metadata-changed", {
        bubbles: true,
        composed: true,
        detail: {
          blipId: this.blipId,
          waveId: this.waveId,
          assigneeAddress: assignee,
          dueDate: due,
          bodySize: this.bodySize || 0
        }
      })
    );
    // Internal event — keep it from bubbling to parent.
    event.stopPropagation();
  };

  render() {
    return html`
      <button
        type="button"
        data-task-toggle-trigger="true"
        role="checkbox"
        aria-checked=${this.completed ? "true" : "false"}
        aria-label=${this.completed ? this.labelAriaUncheck : this.labelAriaCheck}
        title=${this.completed ? this.labelAriaUncheck : this.labelAriaCheck}
        @click=${this._toggle}
      >
        ${this.completed ? this.labelToggleDone : this.labelToggleOpen}
      </button>
      <button
        type="button"
        data-task-details-trigger="true"
        aria-label=${this.labelDetails}
        title=${this.labelDetails}
        aria-haspopup="dialog"
        aria-expanded=${this._popoverOpen ? "true" : "false"}
        @click=${this._openDetails}
      >
        ⋯
      </button>
      <span class="sr-only" aria-live="polite" data-task-announce>${this._announceText}</span>
      <div class="popover-host">
        ${this._popoverOpen
          ? html`<task-metadata-popover
              open
              .taskId=${this.blipId}
              .assigneeAddress=${this.assigneeAddress}
              .dueDate=${this.dueDate}
              .participants=${this.participants || []}
              focus-target-id="task-details-trigger"
              @overlay-close=${this._onPopoverClose}
              @task-metadata-submit=${this._onMetadataSubmit}
            ></task-metadata-popover>`
          : ""}
      </div>
    `;
  }
}

if (!customElements.get("wavy-task-affordance")) {
  customElements.define("wavy-task-affordance", WavyTaskAffordance);
}
