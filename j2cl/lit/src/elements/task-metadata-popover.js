import { LitElement, css, html } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";

export class TaskMetadataPopover extends LitElement {
  static properties = {
    open: { type: Boolean, reflect: true },
    taskId: { type: String, attribute: "task-id" },
    assigneeAddress: { type: String, attribute: "assignee-address" },
    dueDate: { type: String, attribute: "due-date" },
    participants: { type: Array },
    error: { type: String },
    focusTargetId: { type: String, attribute: "focus-target-id" }
  };

  static styles = css`
    :host {
      display: block;
    }

    .dialog {
      display: grid;
      gap: 10px;
      width: min(92vw, 360px);
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 16px;
      padding: 14px;
      background: var(--shell-color-surface-overlay, #fff);
      box-shadow: var(--shell-shadow-modal, 0 22px 70px rgb(10 38 64 / 24%));
    }

    label {
      display: grid;
      gap: 4px;
      color: var(--shell-color-text-muted, #5b6b80);
      font-size: 0.9rem;
    }

    select,
    input,
    button {
      border: 1px solid var(--shell-color-divider-subtle, #d8e3ee);
      border-radius: 10px;
      padding: 8px 10px;
      font: inherit;
    }

    select:focus,
    input:focus,
    button:focus {
      outline: 2px solid var(--shell-color-accent-focus, #206ea6);
      outline-offset: 2px;
    }

    .actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
    }
  `;

  constructor() {
    super();
    this.open = false;
    this.taskId = "";
    this.assigneeAddress = "";
    this.dueDate = "";
    this.participants = [];
    this.error = "";
    this.focusTargetId = "";
    this.addEventListener("keydown", this.onKeyDown);
  }

  updated(changed) {
    if (changed.has("open") && this.open) {
      queueMicrotask(() => this.renderRoot.querySelector("select")?.focus());
    }
  }

  render() {
    if (!this.open) {
      return "";
    }
    const titleId = "task-metadata-title";
    const errorId = "task-due-date-error";
    return html`
      <form
        class="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby=${titleId}
        @submit=${this.submit}
      >
        <h2 id=${titleId}>Task details</h2>
        <label>
          Assignee
          <select name="assignee">
            <option value="">Unassigned</option>
            ${this.safeParticipants().map(
              participant => html`
                <option
                  value=${participant.address || ""}
                  ?selected=${(participant.address || "") === this.assigneeAddress}
                >
                  ${participant.displayName || participant.address}
                </option>
              `
            )}
          </select>
        </label>
        <label>
          Due date
          <input
            name="dueDate"
            inputmode="numeric"
            placeholder="YYYY-MM-DD"
            aria-invalid=${this.error ? "true" : "false"}
            aria-describedby=${ifDefined(this.error ? errorId : undefined)}
            .value=${this.dueDate}
          />
        </label>
        ${this.error ? html`<p id=${errorId} role="alert">${this.error}</p>` : ""}
        <div class="actions">
          <button type="button" @click=${() => this.close("cancel")}>Cancel</button>
          <button type="submit">Save</button>
        </div>
      </form>
    `;
  }

  onKeyDown = (event) => {
    if (!this.open) {
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      this.close("escape");
      return;
    }
    if (event.key === "Tab") {
      this.trapFocus(event);
      return;
    }
    if (event.key === "Enter") {
      if (this.isFormControl(event)) {
        return;
      }
      event.preventDefault();
      this.submit();
    }
  };

  trapFocus(event) {
    const controls = this.focusableControls();
    if (controls.length === 0) {
      return;
    }
    const currentIndex = controls.indexOf(this.renderRoot.activeElement);
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (currentIndex === -1) {
      event.preventDefault();
      (event.shiftKey ? last : first).focus();
      return;
    }
    if (event.shiftKey && (currentIndex <= 0 || this.renderRoot.activeElement === first)) {
      event.preventDefault();
      last.focus();
      return;
    }
    if (!event.shiftKey && this.renderRoot.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  submit = (event) => {
    event?.preventDefault();
    const dueDate = this.renderRoot.querySelector("input[name='dueDate']")?.value || "";
    if (dueDate && !this.isValidDueDate(dueDate)) {
      this.error = "Use YYYY-MM-DD for the due date.";
      return;
    }
    this.error = "";
    this.dispatchEvent(
      new CustomEvent("task-metadata-submit", {
        detail: {
          taskId: this.taskId,
          assigneeAddress: this.renderRoot.querySelector("select[name='assignee']")?.value || "",
          dueDate
        },
        bubbles: true,
        composed: true
      })
    );
  };

  isFormControl(event) {
    const target = event.composedPath?.()[0] || event.target;
    return Boolean(target?.closest?.("input, select, textarea, button"));
  }

  focusableControls() {
    return Array.from(this.renderRoot.querySelectorAll("select, input, button")).filter(
      element => !element.disabled
    );
  }

  isValidDueDate(value) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (!match) {
      return false;
    }
    const year = Number(match[1]);
    const month = Number(match[2]);
    const day = Number(match[3]);
    const date = new Date(Date.UTC(year, month - 1, day));
    return (
      date.getUTCFullYear() === year &&
      date.getUTCMonth() === month - 1 &&
      date.getUTCDate() === day
    );
  }

  close(reason) {
    this.open = false;
    this.dispatchEvent(
      new CustomEvent("overlay-close", {
        detail: { reason, focusTargetId: this.focusTargetId },
        bubbles: true,
        composed: true
      })
    );
  }

  safeParticipants() {
    return Array.isArray(this.participants) ? this.participants : [];
  }
}

if (!customElements.get("task-metadata-popover")) {
  customElements.define("task-metadata-popover", TaskMetadataPopover);
}
