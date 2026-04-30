import { LitElement, css, html } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";

let taskMetadataPopoverCounter = 0;

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
      width: 320px;
      max-width: 92vw;
      border: 0;
      border-radius: 0;
      padding: 18px;
      background: #ffffff;
      box-shadow: none;
      box-sizing: border-box;
      color: #202124;
      font: 13px Arial, sans-serif;
    }

    h2 {
      display: block;
      margin: 0 0 5px;
      font-size: 18px;
      font-weight: 600;
      line-height: 1.2;
      color: #202124;
    }

    label {
      display: grid;
      gap: 6px;
      margin: 2px 0;
      color: #5f6368;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    select,
    input,
    button {
      border: 1px solid #d0d7de;
      border-radius: 8px;
      padding: 9px 10px;
      box-sizing: border-box;
      background: #ffffff;
      color: #202124;
      font: inherit;
      font-size: 14px;
      font-weight: 400;
    }

    select:focus,
    input:focus,
    button:focus {
      outline: none;
      border-color: #1a73e8;
      box-shadow: 0 0 0 2px rgba(26, 115, 232, 0.15);
    }

    .actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
      margin-top: 8px;
    }

    button[type="button"] {
      padding: 8px 12px;
      background: #ffffff;
      color: #3c4043;
    }

    button[type="submit"] {
      padding: 8px 12px;
      border-color: #1a73e8;
      background: #1a73e8;
      color: #ffffff;
      font-weight: 600;
    }

    [role="alert"] {
      margin: -2px 0 0;
      color: #b42318;
      font-size: 12px;
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
    this.instanceId = `task-metadata-${++taskMetadataPopoverCounter}`;
    this.addEventListener("keydown", this.onKeyDown);
  }

  willUpdate(changed) {
    if (changed.has("open") && !this.open) {
      this.error = "";
    }
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
    const titleId = `${this.instanceId}-title`;
    const errorId = `${this.instanceId}-due-date-error`;
    const assigneeOptions = this.assigneeOptions();
    const currentAssigneeKey = this.currentAssigneeKey();
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
            ${assigneeOptions.map(
              participant => html`
                <option
                  value=${participant.address || ""}
                  ?selected=${this.normalizedAddress(participant.address) === currentAssigneeKey}
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
      if (this.usesNativeEnterBehavior(event)) {
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
    const dueDate = (this.renderRoot.querySelector("input[name='dueDate']")?.value || "").trim();
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

  usesNativeEnterBehavior(event) {
    const target = event.composedPath?.()[0] || event.target;
    return Boolean(target?.closest?.("select, textarea, button"));
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
    if (!Array.isArray(this.participants)) {
      return [];
    }
    return this.participants
      .filter(
        participant =>
          participant &&
          typeof participant === "object" &&
          !Array.isArray(participant) &&
          typeof participant.address === "string" &&
          participant.address.trim() !== ""
      )
      .map(participant => ({
        ...participant,
        address: participant.address.trim()
      }));
  }

  assigneeOptions() {
    const participants = this.safeParticipants();
    const assigneeAddress =
      typeof this.assigneeAddress === "string" ? this.assigneeAddress.trim() : "";
    const assigneeKey = this.normalizedAddress(assigneeAddress);
    if (
      assigneeAddress === "" ||
      participants.some(
        participant => this.normalizedAddress(participant.address) === assigneeKey
      )
    ) {
      return participants;
    }
    return [
      {
        address: assigneeAddress,
        displayName: assigneeAddress
      },
      ...participants
    ];
  }

  currentAssigneeKey() {
    return this.normalizedAddress(this.assigneeAddress);
  }

  normalizedAddress(address) {
    return typeof address === "string" ? address.trim().toLowerCase() : "";
  }
}

if (!customElements.get("task-metadata-popover")) {
  customElements.define("task-metadata-popover", TaskMetadataPopover);
}
