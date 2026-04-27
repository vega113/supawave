import { LitElement, css, html } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";
import "../design/wavy-compose-card.js";
import "./composer-submit-affordance.js";

/**
 * <wavy-composer> — F-3.S1 (#1038, R-5.1 + R-5.2) inline rich-text
 * composer that mounts at the chosen reply position attached to a
 * <wave-blip>.
 *
 * Replaces the plain <textarea> in <composer-inline-reply> with a
 * contenteditable body region that participates in the F-0 wavy
 * design recipe (`<wavy-compose-card>`) and exposes the H.* edit
 * toolbar via the active selection.
 *
 * Public API (matches <composer-inline-reply> for property-by-property
 * compatibility so the Java view's setProperty calls flow through
 * unchanged):
 * - available (Boolean) — whether the composer is open / interactive.
 * - replyTargetBlipId (String, attribute "reply-target-blip-id") —
 *   the blip the composer is replying to (absent for wave-root +
 *   create-wave composers).
 * - mode (String) — "reply" | "edit" | "create" | "wave-root";
 *   surfaces the verb used in the host chip and submit button label.
 * - targetLabel (String) — display name of the reply target ("Yuri",
 *   "Top thread", …) used in the "Replying to <author>" chip.
 * - draft (String) — current draft text (textContent of the body).
 * - submitting (Boolean) — submit-in-flight state.
 * - staleBasis (Boolean) — submit blocked because the basis is stale.
 * - status (String) — non-error status line ("Saved").
 * - error (String) — error status line.
 * - activeCommand, commandStatus, commandError (String) — toolbar
 *   command status surfaces from the existing controller.
 *
 * Events emitted (CustomEvent, bubbles + composed):
 * - `draft-change` — `{detail: {value: string}}` on every edit.
 * - `reply-submit` — fired on Shift+Enter or the Send button click.
 * - `attachment-paste-image` — `{detail: {file}}` when the user pastes
 *   an image into the body.
 * - `wavy-composer-cancelled` — `{detail: {replyTargetBlipId, hadContent}}`
 *   on Esc / × close. The view listens and removes the host node.
 * - `wavy-composer-selection-change` — `{detail: SelectionDescriptor}`
 *   when the body selection changes; payload carries `start`, `end`,
 *   `collapsed`, `boundingRect`, and the active annotation set so the
 *   floating toolbar can mirror toggle state.
 *
 * Plugin slot contract:
 * - `compose-extension` is forwarded to the inner <wavy-compose-card>
 *   recipe (which exposes the slot per F-0). Plugins read
 *   `composerState` and `activeSelection` JS properties on the host.
 *
 * Caret survival contract (R-5.1 step 2):
 * - The contenteditable body element is created once in `_ensureBody`
 *   and is NEVER replaced by a Lit re-render. Lit re-renders only the
 *   header chip, the save indicator, the hint strip, and the Send
 *   affordance — none of which steal selection from the body.
 * - Property updates (status, error, targetLabel, etc.) flow through
 *   the host without touching the body element, so the caret position
 *   inside the body survives across `view.render(model)` calls in the
 *   Java view layer.
 *
 * Plain-text scope for S1:
 * - The composer body's `textContent` is the source of truth for the
 *   `draft` property in S1. External `draft` writes via the `draft`
 *   property setter overwrite the body's textContent ONLY when the body
 *   does not own selection — see `_ensureBody`. This means S1's compose
 *   surface ships PLAIN TEXT through the existing controller; rich-text
 *   formatting (Bold / Italic / etc.) wraps DOM nodes inside the body,
 *   but the controller's `J2clRichContentDeltaFactory` path consumes
 *   the body's textContent for delta generation. Mention chips, task
 *   checkboxes, attachment thumbs, and the full DocOp-driven incremental
 *   patching land in S2 / S3 / S4 when the corresponding rows ship.
 */
export class WavyComposer extends LitElement {
  static properties = {
    available: { type: Boolean, reflect: true },
    replyTargetBlipId: { type: String, attribute: "reply-target-blip-id", reflect: true },
    mode: { type: String, reflect: true },
    targetLabel: { type: String, attribute: "target-label" },
    draft: { type: String },
    submitting: { type: Boolean, reflect: true },
    staleBasis: { type: Boolean, attribute: "stale-basis", reflect: true },
    status: { type: String },
    error: { type: String },
    activeCommand: { type: String, attribute: "active-command" },
    commandStatus: { type: String, attribute: "command-status" },
    commandError: { type: String, attribute: "command-error" },
    keymapHint: { type: String, attribute: "keymap-hint" },
    saveIndicator: { type: String, attribute: "save-indicator" }
  };

  static styles = css`
    :host {
      display: block;
    }
    /* F-2.S6 gating: a composer that is not "available" collapses to
     * zero height so the wave panel does not paint a permanent
     * editor-toolbar wall. Only an active Reply / Edit session sets
     * available=true via the Java view. */
    :host(:not([available])) {
      display: none;
    }

    .composer-stack {
      display: grid;
      gap: var(--wavy-spacing-2, 8px);
    }

    .reply-chip {
      display: inline-flex;
      align-items: center;
      gap: var(--wavy-spacing-1, 4px);
      padding: 2px var(--wavy-spacing-2, 8px);
      border-radius: var(--wavy-radius-pill, 9999px);
      background: var(--wavy-signal-cyan-soft, rgba(34, 211, 238, 0.18));
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      font: var(--wavy-type-label, 0.75rem / 1.35 sans-serif);
      width: max-content;
    }

    .reply-chip-close {
      border: 0;
      background: transparent;
      color: inherit;
      cursor: pointer;
      padding: 0 2px;
      font: inherit;
    }
    .reply-chip-close:focus-visible {
      outline: none;
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      border-radius: var(--wavy-radius-pill, 9999px);
    }

    [data-composer-body] {
      min-height: var(--wavy-spacing-7, 32px);
      padding: var(--wavy-spacing-2, 8px) var(--wavy-spacing-3, 12px);
      border-radius: var(--wavy-radius-card, 12px);
      border: 1px solid var(--wavy-border-hairline, rgba(34, 211, 238, 0.18));
      background: var(--wavy-bg-base, #0a1322);
      color: var(--wavy-text-body, rgba(232, 240, 255, 0.92));
      outline: none;
      font: var(--wavy-type-body, 0.9375rem / 1.55 sans-serif);
    }
    [data-composer-body]:focus-visible {
      border-color: var(--wavy-signal-cyan, #22d3ee);
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
    }
    :host([submitting]) [data-composer-body] {
      opacity: 0.6;
      pointer-events: none;
    }

    .save-indicator {
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
    }

    .hint-strip {
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
    }

    .target {
      margin: 0;
      color: var(--wavy-text-quiet, rgba(232, 240, 255, 0.42));
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
    }

    [role="status"],
    [role="alert"] {
      font: var(--wavy-type-meta, 0.6875rem / 1.4 sans-serif);
    }
    [role="alert"] {
      color: var(--wavy-signal-amber, #f59e0b);
    }
  `;

  constructor() {
    super();
    this.available = false;
    this.replyTargetBlipId = "";
    this.mode = "reply";
    this.targetLabel = "";
    this.draft = "";
    this.submitting = false;
    this.staleBasis = false;
    this.status = "";
    this.error = "";
    this.activeCommand = "";
    this.commandStatus = "";
    this.commandError = "";
    this.keymapHint = "Shift+Enter to send, Esc to discard";
    this.saveIndicator = "";
    this._composerState = Object.freeze({});
    this._activeSelection = Object.freeze({});
    this._handleFocusRequest = () => this.focusComposer();
    this._handleSelectionChange = () => this._onSelectionChange();
  }

  /** Frozen lazy snapshot of composer state for plugin consumers. */
  get composerState() {
    return this._composerState;
  }
  set composerState(value) {
    this._composerState = Object.freeze({ ...(value || {}) });
    this._propagateContextToCard();
  }

  /** Frozen lazy snapshot of active selection for plugin consumers. */
  get activeSelection() {
    return this._activeSelection;
  }
  set activeSelection(value) {
    this._activeSelection = Object.freeze({ ...(value || {}) });
    this._propagateContextToCard();
  }

  connectedCallback() {
    super.connectedCallback();
    this.addEventListener("composer-focus-request", this._handleFocusRequest);
    document.addEventListener("selectionchange", this._handleSelectionChange);
  }

  disconnectedCallback() {
    this.removeEventListener("composer-focus-request", this._handleFocusRequest);
    document.removeEventListener("selectionchange", this._handleSelectionChange);
    super.disconnectedCallback();
  }

  /**
   * Stable contenteditable body element. Created once and reused across
   * Lit re-renders so the caret position is preserved. The body lives in
   * shadow DOM and Lit re-renders place it back via a property reference
   * (the same node object) — Lit treats it as a stable child.
   */
  _ensureBody() {
    if (!this._bodyElement) {
      const body = document.createElement("div");
      body.setAttribute("contenteditable", "true");
      body.setAttribute("role", "textbox");
      body.setAttribute("aria-multiline", "true");
      body.setAttribute("aria-label", this._composeBodyAriaLabel());
      body.setAttribute("data-composer-body", "true");
      body.setAttribute("spellcheck", "true");
      body.addEventListener("input", (event) => this._onBodyInput(event));
      body.addEventListener("keydown", (event) => this._onBodyKeydown(event));
      body.addEventListener("paste", (event) => this._onBodyPaste(event));
      this._bodyElement = body;
    }
    // Reflect external draft changes ONLY when the body does not own
    // selection. Mutating the body while selection is inside it would
    // collapse the caret. The Java view sets `draft` via property; we
    // honour it here so initial draft and external resets work.
    if (this._bodyElement.textContent !== this.draft && !this._bodyOwnsSelection()) {
      this._bodyElement.textContent = this.draft;
    }
    return this._bodyElement;
  }

  _bodyOwnsSelection() {
    if (!this._bodyElement) return false;
    const selection = document.getSelection();
    if (!selection || selection.rangeCount === 0) return false;
    const range = selection.getRangeAt(0);
    return this._bodyElement.contains(range.startContainer);
  }

  _composeBodyAriaLabel() {
    if (this.mode === "edit") return "Edit blip";
    if (this.mode === "wave-root") return "Reply to wave";
    if (this.mode === "create") return "New wave";
    return this.targetLabel ? `Reply to ${this.targetLabel}` : "Reply";
  }

  _onBodyInput() {
    const value = this._bodyElement ? this._bodyElement.textContent : "";
    this.draft = value;
    this.dispatchEvent(
      new CustomEvent("draft-change", {
        detail: { value, replyTargetBlipId: this.replyTargetBlipId, mode: this.mode },
        bubbles: true,
        composed: true
      })
    );
  }

  _onBodyKeydown(event) {
    // Shift+Enter submits per H.22 hint and the issue body's
    // Reply-composer contract.
    if (event.key === "Enter" && event.shiftKey) {
      event.preventDefault();
      this._submit();
      return;
    }
    // Esc discards (with confirm if non-empty) per the issue body's
    // Reply-composer contract.
    if (event.key === "Escape") {
      event.preventDefault();
      this._cancel();
      return;
    }
    // Plain Enter inserts a newline (matches GWT keymap). The default
    // browser behaviour for Enter inside a contenteditable is to insert
    // a <div> or <br>; we let the default through for "matches GWT".
  }

  _onBodyPaste(event) {
    if (!event.clipboardData) {
      return;
    }
    const items = Array.from(event.clipboardData.items || []);
    let file = items
      .filter((item) => item.type && item.type.startsWith("image/"))
      .map((item) => this._fileFromClipboardItem(item))
      .find(Boolean);
    if (!file) {
      const files = Array.from(event.clipboardData.files || []);
      file = files.find((f) => f.type && f.type.startsWith("image/")) ?? null;
    }
    if (!file) {
      return;
    }
    const hasText = items.some((item) => item.type && item.type.startsWith("text/"));
    if (!hasText) {
      event.preventDefault();
    }
    this.dispatchEvent(
      new CustomEvent("attachment-paste-image", {
        detail: { file, replyTargetBlipId: this.replyTargetBlipId },
        bubbles: true,
        composed: true
      })
    );
  }

  _fileFromClipboardItem(item) {
    try {
      return item.getAsFile && item.getAsFile();
    } catch {
      return null;
    }
  }

  _submit() {
    if (this.submitting || this.staleBasis) return;
    this.dispatchEvent(
      new CustomEvent("reply-submit", {
        detail: {
          value: this.draft,
          replyTargetBlipId: this.replyTargetBlipId,
          mode: this.mode
        },
        bubbles: true,
        composed: true
      })
    );
  }

  _cancel() {
    const hadContent = (this.draft || "").trim().length > 0;
    this.dispatchEvent(
      new CustomEvent("wavy-composer-cancelled", {
        detail: {
          replyTargetBlipId: this.replyTargetBlipId,
          mode: this.mode,
          hadContent
        },
        bubbles: true,
        composed: true
      })
    );
  }

  _onSelectionChange() {
    if (!this._bodyElement) return;
    const selection = document.getSelection();
    if (!selection || selection.rangeCount === 0) {
      this.activeSelection = {};
      this._dispatchSelectionEvent({});
      return;
    }
    const range = selection.getRangeAt(0);
    if (!this._bodyElement.contains(range.startContainer)) {
      // Selection moved outside the composer; clear active selection so
      // the floating toolbar collapses.
      this.activeSelection = {};
      this._dispatchSelectionEvent({});
      return;
    }
    const rect = range.getBoundingClientRect();
    const descriptor = {
      collapsed: range.collapsed,
      startOffset: range.startOffset,
      endOffset: range.endOffset,
      boundingRect: {
        top: rect.top,
        left: rect.left,
        width: rect.width,
        height: rect.height
      },
      activeAnnotations: this._collectActiveAnnotations(range)
    };
    this.activeSelection = descriptor;
    this._dispatchSelectionEvent(descriptor);
  }

  _collectActiveAnnotations(range) {
    // Walk up from the start container; collect element tag names that
    // represent rich-text annotations. The floating toolbar reads these
    // to drive toggle state (Bold lit when selection is inside <strong>,
    // etc.).
    const set = new Set();
    let node = range.startContainer;
    while (node && node !== this._bodyElement) {
      if (node.nodeType === 1) {
        const tag = node.tagName ? node.tagName.toLowerCase() : "";
        if (tag) set.add(tag);
      }
      node = node.parentNode;
    }
    return Array.from(set);
  }

  _dispatchSelectionEvent(descriptor) {
    this.dispatchEvent(
      new CustomEvent("wavy-composer-selection-change", {
        detail: descriptor,
        bubbles: true,
        composed: true
      })
    );
  }

  _propagateContextToCard() {
    const card = this.renderRoot && this.renderRoot.querySelector("wavy-compose-card");
    if (!card) return;
    card.composerState = this._composerState;
    card.activeSelection = this._activeSelection;
  }

  /** Focus the contenteditable body. Bound to `composer-focus-request`. */
  focusComposer() {
    if (!this.available) return;
    const body = this._bodyElement;
    if (!body || !body.isConnected) return;
    body.focus();
    // Place caret at end if the body is non-empty.
    const range = document.createRange();
    range.selectNodeContents(body);
    range.collapse(false);
    const selection = document.getSelection();
    if (selection) {
      selection.removeAllRanges();
      selection.addRange(range);
    }
  }

  updated(changed) {
    if (changed.has("draft") && this._bodyElement && !this._bodyOwnsSelection()) {
      if (this._bodyElement.textContent !== this.draft) {
        this._bodyElement.textContent = this.draft;
      }
    }
    if (changed.has("mode") || changed.has("targetLabel")) {
      if (this._bodyElement) {
        this._bodyElement.setAttribute("aria-label", this._composeBodyAriaLabel());
      }
    }
    this._propagateContextToCard();
  }

  _onCloseClick(event) {
    event.preventDefault();
    event.stopPropagation();
    this._cancel();
  }

  _onSendClick() {
    this._submit();
  }

  _renderReplyChip() {
    if (!this.replyTargetBlipId) return null;
    const verb = this.mode === "edit" ? "Editing" : "Replying to";
    const label = this.targetLabel || "blip";
    return html`
      <span class="reply-chip" data-reply-chip="true">
        <span>${verb} <strong>${label}</strong></span>
        <button
          type="button"
          class="reply-chip-close"
          data-reply-chip-close="true"
          aria-label="Cancel"
          @click=${this._onCloseClick}
        >×</button>
      </span>
    `;
  }

  _renderSaveIndicator() {
    const indicator = this.saveIndicator || "";
    if (!indicator && !this.submitting) return null;
    const text = this.submitting ? "Saving…" : indicator;
    return html`<span class="save-indicator" data-save-indicator role="status" aria-live="polite">${text}</span>`;
  }

  _renderHintStrip() {
    return html`<small class="hint-strip" data-hint-strip>${this.keymapHint}</small>`;
  }

  render() {
    const body = this._ensureBody();
    const sendDisabled = !this.available || this.submitting || this.staleBasis;
    const sendLabel =
      this.mode === "edit"
        ? "Save"
        : this.mode === "create"
        ? "Create wave"
        : "Send reply";

    return html`
      <wavy-compose-card
        ?focused=${this.available}
        ?submitting=${this.submitting}
        data-reply-target-blip-id=${ifDefined(this.replyTargetBlipId || undefined)}
      >
        <div class="composer-stack">
          ${this._renderReplyChip()}
          ${body}
          ${this._renderHintStrip()}
          ${this.targetLabel
            ? html`<p class="target">Reply target: ${this.targetLabel}</p>`
            : ""}
          ${this.status && !this.error
            ? html`<p class="target" role="status" aria-live="polite">${this.status}</p>`
            : ""}
          ${this.error
            ? html`<p class="target" role="alert" aria-live="assertive">${this.error}</p>`
            : ""}
          ${this.commandStatus
            ? html`<p
                class="target"
                data-command-status
                data-active-command=${this.activeCommand}
                role="status"
                aria-live="polite"
              >${this.commandStatus}</p>`
            : ""}
          ${this.commandError
            ? html`<p
                class="target"
                data-command-error
                data-active-command=${this.activeCommand}
                role="alert"
                aria-live="assertive"
              >${this.commandError}</p>`
            : ""}
        </div>
        <slot name="toolbar" slot="toolbar"></slot>
        <slot name="compose-extension" slot="compose-extension"></slot>
        <div slot="affordance" class="affordance-row">
          ${this._renderSaveIndicator()}
          <composer-submit-affordance
            label=${sendLabel}
            ?busy=${this.submitting}
            ?disabled=${sendDisabled}
            @submit-affordance=${this._onSendClick}
          ></composer-submit-affordance>
        </div>
      </wavy-compose-card>
    `;
  }
}

if (!customElements.get("wavy-composer")) {
  customElements.define("wavy-composer", WavyComposer);
}
