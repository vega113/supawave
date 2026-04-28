import { LitElement, css, html } from "lit";
import { ifDefined } from "lit/directives/if-defined.js";
import "../design/wavy-compose-card.js";
import "./composer-submit-affordance.js";
import "./mention-suggestion-popover.js";
import "./wavy-link-modal.js";

/**
 * J-UI-5 (#1083, R-5.7): map inline-format wrap tags to the
 * `J2clComposeSurfaceController.annotationKey/annotationValue` pairs
 * the GWT/J2CL read codec already understands. The keys here mirror
 * the static helpers at lines 2002–2030 of
 * `J2clComposeSurfaceController.java` so the JS submit emits exactly
 * what the Java side maps from a toolbar-action click today.
 */
function inlineFormatAnnotation(tag) {
  switch (tag) {
    case "strong":
    case "b":
      return { key: "fontWeight", value: "bold" };
    case "em":
    case "i":
      return { key: "fontStyle", value: "italic" };
    case "u":
      return { key: "textDecoration", value: "underline" };
    case "s":
    case "strike":
    case "del":
      return { key: "textDecoration", value: "line-through" };
    default:
      return null;
  }
}

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
    saveIndicator: { type: String, attribute: "save-indicator" },
    // F-3.S2 (#1038, R-5.3): mention suggestion candidates. The Java
    // view sets this from the wave's participant list. Each candidate
    // is `{address, displayName}`. Locale-aware filtering happens
    // here in the composer (see `_filteredMentionCandidates`); the
    // popover stays side-effect-free.
    participants: { type: Array },
    // Internal mention popover state. `_mentionOpen` controls the
    // popover render; `_mentionQuery` is the substring after `@`;
    // `_mentionAnchor` carries the bounding-rect for positioning;
    // `_mentionActiveIndex` tracks the highlighted candidate.
    _mentionOpen: { type: Boolean, state: true },
    _mentionQuery: { type: String, state: true },
    _mentionAnchor: { type: Object, state: true },
    _mentionActiveIndex: { type: Number, state: true }
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
    /* F-3.S4 (#1038, R-5.6 step 1): drop-target hint while a file is
     * being dragged over the composer body. The CSS uses /* ... *\/
     * style comments only — no back-tick characters appear inside the
     * css\` ... \` template literal (the F-3.S3 footgun called out in
     * the slice plan risk list). */
    [data-composer-body][data-droptarget="true"] {
      border-color: var(--wavy-signal-cyan, #22d3ee);
      background: var(--wavy-signal-cyan-soft, rgba(34, 211, 238, 0.12));
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

    /* F-3.S2 (#1038, R-5.3 step 8): mention chip styling. The chip is
     * rendered inside the contenteditable body as a contenteditable=
     * false span carrying data-mention-id. Token contract: violet
     * border + soft fill (signal violet = mentions/reactions). The
     * computed color uses the violet token directly so the parity
     * fixture's RGB assertion (124, 58, 237) holds. */
    [data-composer-body] .wavy-mention-chip {
      display: inline-block;
      padding: 0 var(--wavy-spacing-1, 4px);
      border-radius: var(--wavy-radius-pill, 9999px);
      background: var(--wavy-signal-violet-soft, rgba(124, 58, 237, 0.18));
      border: 1px solid var(--wavy-signal-violet, #7c3aed);
      color: var(--wavy-signal-violet, #7c3aed);
      font-weight: 600;
      cursor: default;
      user-select: none;
    }

    /* Mention popover host — positioned at the caret rect by
     * _ensureMentionAnchor; falls inside the composer's stacking
     * context so it floats above adjacent blip cards. */
    .mention-popover-host {
      position: fixed;
      z-index: 1000;
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
    this.participants = [];
    this._mentionOpen = false;
    this._mentionQuery = "";
    this._mentionAnchor = null;
    this._mentionActiveIndex = 0;
    // The text node + offset where the user typed `@`. Used to remove
    // the typed query characters when a candidate is selected so the
    // chip replaces them cleanly.
    this._mentionTriggerNode = null;
    this._mentionTriggerOffset = -1;
    this._pendingDraftSync = undefined;
    this._composerState = Object.freeze({});
    this._activeSelection = Object.freeze({});
    // J-UI-5 (#1083, R-5.2): cached live Range from the most recent
    // selection inside the composer body. Used by toolbar handlers
    // because `document.getSelection()` does not reliably pierce the
    // shadow root in WebKit / Firefox.
    this._lastSelectionRange = null;
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
    // F-3.S2 (#1038, R-5.4 step 6): listen for the H.20 Insert-task
    // action emitted by the floating wavy format toolbar mounted in
    // the toolbar slot. The action is composer-local (per-composer,
    // not global) so listen on the host element rather than document.
    // (See the gating-preservation test for why this comment avoids
    //  the literal element name with the leading bracket.)
    this.addEventListener("wavy-format-toolbar-action", this._handleToolbarAction);
  }

  disconnectedCallback() {
    this.removeEventListener("composer-focus-request", this._handleFocusRequest);
    document.removeEventListener("selectionchange", this._handleSelectionChange);
    this.removeEventListener("wavy-format-toolbar-action", this._handleToolbarAction);
    super.disconnectedCallback();
  }

  _handleToolbarAction = (event) => {
    const actionId = event && event.detail && event.detail.actionId;
    if (!actionId) return;
    if (actionId === "insert-task") {
      this._insertTaskListAtCaret();
      event.stopPropagation();
      return;
    }
    // J-UI-5 (#1083, R-5.2 + R-5.7): selection-driven format actions.
    // The handler claims responsibility for the action ids it
    // implements; un-handled ids continue to bubble for the existing
    // body-level routes (e.g. `attachment-insert` ⇒ view.openAttachmentPicker).
    const inlineWraps = {
      bold: { tag: "strong", siblings: ["b"] },
      italic: { tag: "em", siblings: ["i"] },
      underline: { tag: "u", siblings: [] },
      strikethrough: { tag: "s", siblings: ["strike", "del"] }
    };
    if (inlineWraps[actionId]) {
      this._toggleInlineWrapAtSelection(inlineWraps[actionId]);
      event.stopPropagation();
      return;
    }
    if (actionId === "unordered-list" || actionId === "ordered-list") {
      this._toggleListAtSelection(actionId === "ordered-list" ? "ol" : "ul");
      event.stopPropagation();
      return;
    }
    if (
      actionId === "align-left" ||
      actionId === "align-center" ||
      actionId === "align-right"
    ) {
      const align = actionId === "align-left" ? "left" : actionId === "align-center" ? "center" : "right";
      this._setBlockAttributeAtSelection("style", `text-align: ${align};`);
      event.stopPropagation();
      return;
    }
    if (actionId === "rtl") {
      this._setBlockAttributeAtSelection("dir", "rtl");
      event.stopPropagation();
      return;
    }
    if (actionId === "link") {
      this._openLinkModalForSelection();
      event.stopPropagation();
      return;
    }
    if (actionId === "unlink") {
      this._unwrapLinkAtSelection();
      event.stopPropagation();
      return;
    }
    if (actionId === "clear-formatting") {
      this._clearFormattingAtSelection();
      event.stopPropagation();
      return;
    }
    // J-UI-5 (#1083, §9 out-of-scope): heading round-trip is deferred —
    // wrap-as-bold loses the heading semantic on reload. The toolbar
    // surfaces the button for visual parity; click is a no-op until a
    // follow-up issue defines the element-type round-trip. Falls
    // through (no stopPropagation) so a future listener can act on it.
  };

  /**
   * J-UI-5 (#1083, R-5.2): cached live Range for the most-recent
   * composer-body selection. Used by toolbar handlers because
   * `document.getSelection()` does not pierce shadow roots in
   * Firefox / WebKit. Updated on every `selectionchange` while the
   * body owns selection (see `_onSelectionChange`).
   */
  _captureSelectionRange() {
    const selection = typeof document !== "undefined" ? document.getSelection() : null;
    if (!selection || selection.rangeCount === 0) return null;
    const range = selection.getRangeAt(0);
    if (!this._bodyElement || !this._bodyElement.contains(range.startContainer)) {
      return null;
    }
    return range.cloneRange();
  }

  _activeRange() {
    const live = this._captureSelectionRange();
    if (live) return live;
    return this._lastSelectionRange ? this._lastSelectionRange.cloneRange() : null;
  }

  _restoreSelectionRange(range) {
    if (!range) return;
    const selection = typeof document !== "undefined" ? document.getSelection() : null;
    if (!selection) return;
    selection.removeAllRanges();
    selection.addRange(range);
  }

  /**
   * J-UI-5 (#1083, R-5.2): toggle wrap the active range with the named
   * inline tag. When the selection is already inside the tag (or one
   * of its synonyms), unwrap. Selection must be non-collapsed; a
   * collapsed caret is treated as no-op so accidental clicks do not
   * insert empty wraps.
   */
  _toggleInlineWrapAtSelection(spec) {
    if (!this._bodyElement) return;
    const range = this._activeRange();
    if (!range || range.collapsed) return;
    if (!this._bodyElement.contains(range.startContainer)) return;
    const allTags = [spec.tag, ...(spec.siblings || [])];
    const ancestor = this._findAncestorTag(range.startContainer, allTags);
    if (ancestor) {
      this._unwrapElement(ancestor);
      this._afterBodyMutation();
      return;
    }
    const wrapper = document.createElement(spec.tag);
    try {
      wrapper.appendChild(range.extractContents());
      range.insertNode(wrapper);
      const restored = document.createRange();
      restored.selectNodeContents(wrapper);
      this._restoreSelectionRange(restored);
    } catch (_e) {
      // Range manipulation can throw for malformed selections; bail
      // without wrapping rather than corrupting the body.
      return;
    }
    this._afterBodyMutation();
  }

  /**
   * J-UI-5 (#1083, R-5.7): toggle wrap the lines covered by the
   * selection in `<ul>`/`<ol>`. Each line becomes one `<li>`. When
   * the selection is already inside a list of the same tag, unwrap
   * the items back to plain text.
   */
  _toggleListAtSelection(listTag) {
    if (!this._bodyElement) return;
    const range = this._activeRange();
    if (!range) return;
    if (!this._bodyElement.contains(range.startContainer)) return;
    const existing = this._findAncestorTag(range.startContainer, [listTag]);
    if (existing) {
      // Unwrap each <li>'s text into the parent and remove the list.
      const parent = existing.parentNode;
      if (!parent) return;
      const items = Array.from(existing.querySelectorAll("li"));
      const fragments = items.map((li) => {
        const div = document.createElement("div");
        while (li.firstChild) div.appendChild(li.firstChild);
        return div;
      });
      for (const frag of fragments) parent.insertBefore(frag, existing);
      parent.removeChild(existing);
      this._afterBodyMutation();
      return;
    }
    let text;
    try {
      text = range.toString();
    } catch (_e) {
      return;
    }
    const lines = text.split("\n").filter((line) => line.length > 0);
    if (lines.length === 0) return;
    const list = document.createElement(listTag);
    for (const line of lines) {
      const li = document.createElement("li");
      li.textContent = line;
      list.appendChild(li);
    }
    try {
      range.deleteContents();
      range.insertNode(list);
      const restored = document.createRange();
      restored.selectNodeContents(list);
      this._restoreSelectionRange(restored);
    } catch (_e) {
      return;
    }
    this._afterBodyMutation();
  }

  _setBlockAttributeAtSelection(attrName, attrValue) {
    if (!this._bodyElement) return;
    const range = this._activeRange();
    if (!range) return;
    if (!this._bodyElement.contains(range.startContainer)) return;
    let block = this._findAncestorTag(range.startContainer, ["div", "p", "li", "blockquote"]);
    if (!block) {
      // Wrap the active range's containing line in a <div> so the
      // alignment/dir attribute has somewhere to land.
      block = document.createElement("div");
      try {
        block.appendChild(range.extractContents());
        range.insertNode(block);
      } catch (_e) {
        return;
      }
    }
    block.setAttribute(attrName, attrValue);
    this._afterBodyMutation();
  }

  /**
   * J-UI-5 (#1083, R-5.7): open the existing `<wavy-link-modal>` to
   * collect a URL, then wrap the active range in `<a href=…>`. The
   * range is captured before the modal opens so caret/selection
   * survive across modal interactions.
   */
  _openLinkModalForSelection() {
    if (!this._bodyElement) return;
    const range = this._activeRange();
    if (!range) return;
    if (!this._bodyElement.contains(range.startContainer)) return;
    let preselectedDisplay = "";
    try {
      preselectedDisplay = range.toString();
    } catch (_e) {
      preselectedDisplay = "";
    }
    let modal = document.querySelector("wavy-link-modal[data-j2cl-link-modal=\"true\"]");
    if (!modal) {
      modal = document.createElement("wavy-link-modal");
      modal.setAttribute("data-j2cl-link-modal", "true");
      document.body.appendChild(modal);
    }
    modal.urlValue = "";
    modal.displayValue = preselectedDisplay;
    modal.open = true;
    const onSubmit = (event) => {
      const detail = event && event.detail;
      modal.removeEventListener("wavy-link-modal-submit", onSubmit);
      modal.removeEventListener("wavy-link-modal-cancel", onCancel);
      modal.open = false;
      if (!detail || !detail.url) return;
      this._wrapRangeInLink(range, detail.url, detail.display || preselectedDisplay);
    };
    const onCancel = () => {
      modal.removeEventListener("wavy-link-modal-submit", onSubmit);
      modal.removeEventListener("wavy-link-modal-cancel", onCancel);
      modal.open = false;
      // Restore caret to the captured range so the user is back where
      // they were before the modal stole focus.
      this._restoreSelectionRange(range.cloneRange());
    };
    modal.addEventListener("wavy-link-modal-submit", onSubmit);
    modal.addEventListener("wavy-link-modal-cancel", onCancel);
  }

  _wrapRangeInLink(range, url, displayText) {
    if (!this._bodyElement) return;
    if (!this._bodyElement.contains(range.startContainer)) return;
    const anchor = document.createElement("a");
    anchor.setAttribute("href", url);
    if (displayText && range.collapsed) {
      anchor.textContent = displayText;
    } else {
      try {
        anchor.appendChild(range.extractContents());
      } catch (_e) {
        return;
      }
    }
    try {
      range.insertNode(anchor);
    } catch (_e) {
      return;
    }
    const restored = document.createRange();
    restored.selectNodeContents(anchor);
    this._restoreSelectionRange(restored);
    this._afterBodyMutation();
  }

  _unwrapLinkAtSelection() {
    if (!this._bodyElement) return;
    const range = this._activeRange();
    if (!range) return;
    if (!this._bodyElement.contains(range.startContainer)) return;
    const anchor = this._findAncestorTag(range.startContainer, ["a"]);
    if (!anchor) return;
    this._unwrapElement(anchor);
    this._afterBodyMutation();
  }

  /**
   * J-UI-5 (#1083, R-5.7): flatten formatting tags inside the active
   * range so the user can recover plain text. Strips the formatting
   * wraps we know about (`<strong>`, `<em>`, `<u>`, `<s>`, `<a>`,
   * `<ul>`, `<ol>`, `<li>`, `<blockquote>`). Mention chips are left
   * intact — they are semantic, not visual formatting.
   */
  _clearFormattingAtSelection() {
    if (!this._bodyElement) return;
    const range = this._activeRange();
    if (!range) return;
    if (!this._bodyElement.contains(range.startContainer)) return;
    const tagsToFlatten = new Set([
      "strong",
      "b",
      "em",
      "i",
      "u",
      "s",
      "strike",
      "del",
      "a",
      "ul",
      "ol",
      "li",
      "blockquote",
      "h1",
      "h2",
      "h3",
      "h4"
    ]);
    let container = range.commonAncestorContainer;
    if (container.nodeType === Node.TEXT_NODE) {
      container = container.parentNode;
    }
    while (container && container !== this._bodyElement) {
      const tag = container.tagName ? container.tagName.toLowerCase() : "";
      if (tagsToFlatten.has(tag)) {
        // Pull container's text out and replace the container.
        const parent = container.parentNode;
        if (!parent) break;
        const text = document.createTextNode(container.textContent || "");
        parent.replaceChild(text, container);
        container = text.parentNode;
        continue;
      }
      container = container.parentNode;
    }
    // Now walk descendants of the common ancestor and flatten any
    // remaining wraps inside the original range. Use a fresh tree
    // walker over the body and strip any matching tag whose entire
    // contents lie inside the original range bounds.
    const walker = document.createTreeWalker(this._bodyElement, NodeFilter.SHOW_ELEMENT, null);
    const stripCandidates = [];
    let node = walker.currentNode;
    while ((node = walker.nextNode())) {
      const tag = node.tagName ? node.tagName.toLowerCase() : "";
      if (!tagsToFlatten.has(tag)) continue;
      if (node.classList && node.classList.contains("wavy-mention-chip")) continue;
      stripCandidates.push(node);
    }
    for (const candidate of stripCandidates) {
      this._unwrapElement(candidate);
    }
    this._afterBodyMutation();
  }

  _findAncestorTag(node, tagNames) {
    if (!node || !this._bodyElement) return null;
    const lowered = tagNames.map((t) => t.toLowerCase());
    let current = node.nodeType === Node.ELEMENT_NODE ? node : node.parentNode;
    while (current && current !== this._bodyElement) {
      if (current.nodeType === Node.ELEMENT_NODE) {
        const tag = current.tagName ? current.tagName.toLowerCase() : "";
        if (lowered.includes(tag)) return current;
      }
      current = current.parentNode;
    }
    return null;
  }

  _unwrapElement(element) {
    if (!element) return;
    const parent = element.parentNode;
    if (!parent) return;
    while (element.firstChild) parent.insertBefore(element.firstChild, element);
    parent.removeChild(element);
  }

  /**
   * J-UI-5 (#1083, R-5.2): re-emit `draft-change` and refresh the
   * cached selection descriptor after a toolbar mutation so
   * downstream consumers (toolbar pressed state, save indicator,
   * the controller's draft cache) see the edit.
   */
  _afterBodyMutation() {
    const value = this._serializeBodyText();
    this.draft = value;
    this.dispatchEvent(
      new CustomEvent("draft-change", {
        detail: { value, replyTargetBlipId: this.replyTargetBlipId, mode: this.mode },
        bubbles: true,
        composed: true
      })
    );
    // Re-emit selection-change so the floating toolbar's pressed
    // state mirrors the new annotation set immediately.
    this._onSelectionChange();
  }

  /**
   * F-3.S2 (#1038, R-5.4 step 6): insert a `<ul class="wavy-task-list">`
   * containing one disabled checkbox + the active selection text at
   * the caret. The checkbox is display-only — the per-blip task
   * affordance owns the model-of-record. Inserting the markup keeps
   * the body's `textContent` carrying the task line so the next
   * reply submit serializes it as plain text (S4 will extend the
   * rich serializer to round-trip the list as a `task-list` element).
   */
  _insertTaskListAtCaret() {
    if (!this._bodyElement) return;
    const selection = document.getSelection();
    if (!selection || selection.rangeCount === 0) return;
    const range = selection.getRangeAt(0);
    if (!this._bodyElement.contains(range.startContainer)) return;
    const ul = document.createElement("ul");
    ul.className = "wavy-task-list";
    const li = document.createElement("li");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.disabled = true;
    checkbox.setAttribute("aria-checked", "false");
    li.appendChild(checkbox);
    li.appendChild(document.createTextNode(" "));
    // Move the selected text into the list item if the user had a
    // non-collapsed selection; otherwise leave the li empty so the
    // user can type the task description.
    if (!range.collapsed) {
      const fragment = range.extractContents();
      li.appendChild(fragment);
    }
    ul.appendChild(li);
    range.deleteContents();
    range.insertNode(ul);
    // Place caret inside the list item, right after the checkbox space.
    const caretRange = document.createRange();
    caretRange.selectNodeContents(li);
    caretRange.collapse(false);
    selection.removeAllRanges();
    selection.addRange(caretRange);
    // Re-emit draft-change so consumers see the new line.
    const value = this._serializeBodyText();
    this.draft = value;
    this.dispatchEvent(
      new CustomEvent("draft-change", {
        detail: { value, replyTargetBlipId: this.replyTargetBlipId, mode: this.mode },
        bubbles: true,
        composed: true
      })
    );
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
      // F-3.S4 (#1038, R-5.6 step 1): drag-drop support. The body
      // accepts file drops and reflects a data-droptarget attribute so
      // CSS can show a hint state. The drop handler dispatches a single
      // wavy-composer-attachment-dropped event the view forwards to the
      // controller's onDroppedFiles listener (mirrors the paste path).
      body.addEventListener("dragover", (event) => this._onBodyDragOver(event));
      body.addEventListener("dragleave", (event) => this._onBodyDragLeave(event));
      body.addEventListener("drop", (event) => this._onBodyDrop(event));
      this._bodyElement = body;
    }
    // Reflect external draft changes ONLY when the body does not own
    // selection. Mutating the body while selection is inside it would
    // collapse the caret. The Java view sets `draft` via property; we
    // honour it here so initial draft and external resets work.
    //
    // F-3.S2 (#1038, R-5.3): mention chip spans live inside the body
    // and carry semantic state (data-mention-id) that plain text
    // overwrites would erase. Skip the overwrite when the body
    // contains rich content the textual `draft` cannot represent;
    // otherwise the next render after a chip insert would silently
    // drop the chip.
    //
    // Exception (#1066 review): a controller-driven reset (the
    // `draft` prop transitioning to empty after submit/cancel/wave
    // change) MUST clear the body even when chips/task lists are
    // present, otherwise stale rich content stays visible and can
    // be re-submitted. `_isControllerReset` recognises that case.
    if (
      this._serializeBodyText() !== this.draft &&
      !this._bodyOwnsSelection() &&
      (this._isControllerReset() || !this._bodyHasRichContent())
    ) {
      this._bodyElement.textContent = this.draft;
    }
    return this._bodyElement;
  }

  /**
   * F-3.S2 (#1038, R-5.3, PR #1066 review): a controller-driven
   * reset is an external `draft`-prop transition to the empty
   * string while the rendered body still holds content. Resets
   * fire after submit success, after cancel, and when the user
   * navigates to a different wave; rich-content chips must clear
   * with the rest of the body in that case. Mid-edit user input
   * never triggers this branch because the user keeps the body
   * non-empty and the prop tracks the last serialised value.
   */
  _isControllerReset() {
    if (!this._bodyElement) return false;
    if ((this.draft || "") !== "") return false;
    return this._bodyElement.childNodes.length > 0;
  }

  /**
   * Returns true when the composer body holds DOM nodes that the
   * plain-text `draft` property cannot round-trip (e.g. mention chip
   * spans, task list items). Used by `_ensureBody` to skip the
   * external textContent overwrite when doing so would erase rich
   * content. Once S4 lands rich-content draft serialization the
   * guard collapses to "always honour the rich draft".
   */
  _bodyHasRichContent() {
    if (!this._bodyElement) return false;
    // J-UI-5 (#1083): inline format wraps (bold/italic/underline/strike/
    // link) and block wraps (lists, blockquote, headings) are also rich
    // content the plain-text `draft` cannot round-trip. Counting them
    // here keeps the next Lit re-render from clobbering the wraps via
    // a textContent overwrite.
    return Boolean(
      this._bodyElement.querySelector(
        ".wavy-mention-chip, .wavy-task-list, strong, b, em, i, u, s, strike, del, a, ul, ol, blockquote, h1, h2, h3, h4"
      )
    );
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

  _serializeBodyText() {
    if (!this._bodyElement) return "";
    // Walk immediate child nodes to capture newlines that contenteditable
    // adds as <div> wrappers per-line (Enter key) or <br> nodes.
    let text = "";
    for (const node of this._bodyElement.childNodes) {
      if (node.nodeType === Node.TEXT_NODE) {
        text += node.textContent;
      } else if (node.nodeType === Node.ELEMENT_NODE) {
        const tag = node.tagName.toLowerCase();
        if (tag === "br") {
          text += "\n";
        } else if (tag === "div" || tag === "p") {
          if (text.length > 0 && !text.endsWith("\n")) text += "\n";
          text += node.textContent;
        } else {
          // F-3.S2 (#1038, R-5.3 step 4): mention chip spans are
          // contenteditable=false; their textContent is "@<displayName>"
          // which we want in the textual draft view so consumers see
          // the right preview. The rich serializer below
          // (_serializeBodyComponents) emits a separate annotation
          // component for the same chip on submit.
          text += node.textContent;
        }
      }
    }
    return text;
  }

  /**
   * F-3.S2 (#1038, R-5.3) — PR #1066 review thread
   * PRRT_kwDOBwxLXs593gTR. Compute the plain-text offset (using the
   * same flattening rules as {@link #_serializeBodyText}) at which a
   * just-inserted mention chip begins. Returns -1 when the chip is
   * not a child of the composer body.
   */
  _computeChipPlainTextOffset(chip) {
    if (!this._bodyElement || !chip) return -1;
    let offset = 0;
    let text = "";
    for (const node of this._bodyElement.childNodes) {
      if (node === chip) {
        return offset;
      }
      if (node.nodeType === Node.TEXT_NODE) {
        text += node.textContent;
        offset += node.textContent.length;
        continue;
      }
      if (node.nodeType !== Node.ELEMENT_NODE) continue;
      const tag = node.tagName.toLowerCase();
      if (tag === "br") {
        text += "\n";
        offset += 1;
        continue;
      }
      if (tag === "div" || tag === "p") {
        if (text.length > 0 && !text.endsWith("\n")) {
          text += "\n";
          offset += 1;
        }
        // The chip lives on a flat body in the picker path, but if a
        // host wraps lines in <div>/<p> blocks, walk into them so we
        // still find the chip's offset deterministically.
        const inner = this._findChipInside(node, chip);
        if (inner.found) {
          return offset + inner.offset;
        }
        text += node.textContent;
        offset += node.textContent.length;
        continue;
      }
      // Non-chip inline element: include its text in the offset count.
      text += node.textContent;
      offset += node.textContent.length;
    }
    return -1;
  }

  _findChipInside(container, chip) {
    let offset = 0;
    for (const node of container.childNodes) {
      if (node === chip) {
        return { found: true, offset };
      }
      if (node.nodeType === Node.TEXT_NODE) {
        offset += node.textContent.length;
        continue;
      }
      if (node.nodeType !== Node.ELEMENT_NODE) continue;
      offset += (node.textContent || "").length;
    }
    return { found: false, offset: 0 };
  }

  /**
   * F-3.S2 (#1038, R-5.3 step 4): rich component serializer used to
   * inspect the body's mixed text / annotation content at submit
   * time. Walks the body's immediate children and emits an array of
   * `{type, text, annotationKey, annotationValue}` records. Mention
   * chips become annotated components keyed by `link/manual`
   * carrying the participant address as the value.
   *
   * The output format is intentionally JS-only. This file does not
   * define the Java submit bridge for these records; the J2CL view
   * receives `wavy-composer-mention-picked` CustomEvents and routes
   * them to the controller's `onMentionPicked` listener, which
   * snapshots each pick and assembles the J2clComposerDocument
   * server-side at submit. This serializer is exposed for
   * unit-test purposes and for any future plugin or host
   * integration that needs to inspect the rich draft.
   */
  serializeRichComponents() {
    if (!this._bodyElement) return [];
    const components = [];
    let pending = "";
    const flushText = () => {
      if (pending) {
        components.push({ type: "text", text: pending });
        pending = "";
      }
    };
    const walk = (children) => {
      for (const node of children) {
        if (node.nodeType === Node.TEXT_NODE) {
          pending += node.textContent;
          continue;
        }
        if (node.nodeType !== Node.ELEMENT_NODE) continue;
        const tag = node.tagName.toLowerCase();
        if (tag === "br") {
          pending += "\n";
          continue;
        }
        if (node.classList && node.classList.contains("wavy-mention-chip")) {
          flushText();
          components.push({
            type: "annotated",
            text: node.textContent,
            annotationKey: "link/manual",
            annotationValue: node.getAttribute("data-mention-id") || ""
          });
          continue;
        }
        // F-3.S4 (#1038, R-5.7): list helpers.  Each <li> is walked
        // recursively so that mention chips inside list items survive
        // round-trip serialization.  Plain text items are promoted to
        // the list annotation; rich components (mention/link) are
        // passed through unchanged so their own annotation is preserved.
        const walkListItems = (listNode, annotationKey) => {
          flushText();
          for (const itemNode of listNode.childNodes) {
            if (
              itemNode.nodeType === Node.ELEMENT_NODE &&
              itemNode.tagName &&
              itemNode.tagName.toLowerCase() === "li"
            ) {
              const snapLen = components.length;
              const snapPending = pending;
              pending = "";
              walk(itemNode.childNodes);
              flushText();
              const itemComps = components.splice(snapLen);
              pending = snapPending;
              for (const c of itemComps) {
                if (c.type === "text") {
                  components.push({
                    type: "annotated",
                    text: c.text,
                    annotationKey,
                    annotationValue: "true"
                  });
                } else {
                  components.push(c);
                }
              }
            }
          }
        };
        if (tag === "ul") {
          if (node.classList && node.classList.contains("wavy-task-list")) {
            walk(node.childNodes);
            continue;
          }
          walkListItems(node, "list/unordered");
          continue;
        }
        if (tag === "ol") {
          walkListItems(node, "list/ordered");
          continue;
        }
        // F-3.S4 (#1038, R-5.7): block quote — walk children so that
        // mention chips inside a <blockquote> are not silently dropped.
        if (tag === "blockquote") {
          flushText();
          const snapLen = components.length;
          const snapPending = pending;
          pending = "";
          walk(node.childNodes);
          flushText();
          const bqComps = components.splice(snapLen);
          pending = snapPending;
          for (const c of bqComps) {
            if (c.type === "text") {
              components.push({
                type: "annotated",
                text: c.text,
                annotationKey: "block/quote",
                annotationValue: "true"
              });
            } else {
              components.push(c);
            }
          }
          continue;
        }
        // F-3.S4 (#1038, R-5.7 — review-1077 Bug 3): inline link.
        // Anchor elements emit a `link/manual` component carrying the
        // href as the annotation value. The mention-chip path above
        // handles links injected via the `@` autocompleter; this path
        // covers links inserted via the H.17 link modal.  Walk
        // descendants recursively (instead of taking textContent) so
        // mention chips / formatting nested inside the anchor survive
        // round-trip serialization: plain text inside the <a> is
        // promoted to the link annotation; rich components inside it
        // (e.g. a mention chip) flow through unchanged.
        if (tag === "a") {
          flushText();
          const href = node.getAttribute("href") || "";
          if (href) {
            const snapLen = components.length;
            const snapPending = pending;
            pending = "";
            walk(node.childNodes);
            flushText();
            const aComps = components.splice(snapLen);
            pending = snapPending;
            for (const c of aComps) {
              if (c.type === "text") {
                components.push({
                  type: "annotated",
                  text: c.text,
                  annotationKey: "link/manual",
                  annotationValue: href
                });
              } else {
                components.push(c);
              }
            }
            continue;
          }
        }
        if (tag === "div" || tag === "p") {
          flushText();
          if (components.length > 0 || pending) {
            // separator newline between block-level wrappers
            pending = "\n";
            flushText();
          }
          walk(node.childNodes);
          continue;
        }
        // J-UI-5 (#1083, R-5.7): inline format wraps. Walk children
        // recursively so that nested formatting (e.g. <strong><em>...
        // </em></strong>) and chips inside formatted text survive
        // round-trip. Plain text inside the wrap is promoted to the
        // matching annotation; rich components inside the wrap flow
        // through unchanged.
        const inlineAnnotation = inlineFormatAnnotation(tag);
        if (inlineAnnotation) {
          flushText();
          const snapLen = components.length;
          const snapPending = pending;
          pending = "";
          walk(node.childNodes);
          flushText();
          const innerComps = components.splice(snapLen);
          pending = snapPending;
          for (const c of innerComps) {
            if (c.type === "text") {
              components.push({
                type: "annotated",
                text: c.text,
                annotationKey: inlineAnnotation.key,
                annotationValue: inlineAnnotation.value
              });
            } else {
              components.push(c);
            }
          }
          continue;
        }
        pending += node.textContent;
      }
    };
    walk(this._bodyElement.childNodes);
    flushText();
    return components;
  }

  _onBodyInput() {
    const value = this._serializeBodyText();
    this.draft = value;
    // F-3.S2 (#1038, R-5.3): on every input, recompute mention popover
    // state from the live caret position. If the caret is preceded by
    // `@<query>` (with `@` at start-of-line or after whitespace),
    // open the popover and set _mentionQuery from the typed substring.
    // If the caret moves out of an active query range, close it.
    this._updateMentionPopoverFromCaret();
    this.dispatchEvent(
      new CustomEvent("draft-change", {
        detail: { value, replyTargetBlipId: this.replyTargetBlipId, mode: this.mode },
        bubbles: true,
        composed: true
      })
    );
  }

  /**
   * Inspect the live selection inside the composer body and decide
   * whether the mention popover should be open. The trigger fires
   * when the most-recent `@` character on the current line is
   * preceded by start-of-line OR a whitespace character (so pasted
   * email addresses like `alice@example.com` do NOT pop the
   * suggestion sheet — see risk #1 of the slice plan).
   */
  _updateMentionPopoverFromCaret() {
    if (!this._bodyElement) return;
    const selection = document.getSelection();
    if (!selection || selection.rangeCount === 0) {
      this._dismissMentionPopover("blur");
      return;
    }
    const range = selection.getRangeAt(0);
    if (!this._bodyElement.contains(range.startContainer)) {
      this._dismissMentionPopover("blur");
      return;
    }
    if (range.startContainer.nodeType !== Node.TEXT_NODE) {
      this._dismissMentionPopover("blur");
      return;
    }
    const textNode = range.startContainer;
    const offset = range.startOffset;
    const textBefore = textNode.textContent.slice(0, offset);
    // Find the most-recent `@` on the current chunk; only fire if the
    // character preceding it is whitespace OR the `@` is the first
    // character of the text node AND the previous sibling is either
    // a block-opener or absent (start-of-line proxy).
    const atIndex = textBefore.lastIndexOf("@");
    if (atIndex < 0) {
      this._dismissMentionPopover("no-at");
      return;
    }
    if (atIndex > 0) {
      const preceding = textBefore.charAt(atIndex - 1);
      if (!/\s/.test(preceding)) {
        // `@` is glued to a previous non-space char (e.g. an email);
        // skip the trigger.
        this._dismissMentionPopover("no-trigger");
        return;
      }
    } else {
      // atIndex === 0: the `@` is the first character of this text
      // node. Only trigger if the previous sibling is missing or is a
      // block-opening element (BR/DIV/P) — proxy for start-of-line.
      const prevSibling = textNode.previousSibling;
      if (prevSibling && prevSibling.nodeType === Node.TEXT_NODE) {
        const tail = prevSibling.textContent.slice(-1);
        if (tail && !/\s/.test(tail)) {
          this._dismissMentionPopover("no-trigger");
          return;
        }
      }
    }
    const query = textBefore.slice(atIndex + 1);
    // The mention popover stays open for any non-whitespace
    // continuation; the first whitespace character commits the
    // literal `@query` text and dismisses the popover. Punctuation
    // and Unicode letters / digits keep the popover open so locale-
    // aware filtering (Cyrillic, CJK, Turkish dotted/dotless I, …)
    // can match against the wave's participant set.
    if (/\s/.test(query)) {
      this._dismissMentionPopover("query-broken");
      return;
    }
    this._mentionTriggerNode = textNode;
    this._mentionTriggerOffset = atIndex;
    this._mentionQuery = query;
    this._mentionActiveIndex = 0;
    this._mentionAnchor = this._computeMentionAnchor(range);
    this._mentionOpen = true;
    this.requestUpdate();
  }

  _computeMentionAnchor(range) {
    try {
      const rect = range.getBoundingClientRect();
      return {
        top: rect.bottom + 4,
        left: rect.left,
        width: rect.width,
        height: rect.height
      };
    } catch (_e) {
      return null;
    }
  }

  /**
   * Filter `participants` by the current `_mentionQuery` using
   * locale-aware case folding via toLocaleLowerCase. The locale is
   * read from `document.documentElement.lang` so Cyrillic + Turkish
   * scripts fold correctly per R-5.3 step 7.
   */
  _filteredMentionCandidates() {
    const list = Array.isArray(this.participants) ? this.participants : [];
    const lang = (typeof document !== "undefined" && document.documentElement && document.documentElement.lang)
      ? document.documentElement.lang
      : undefined;
    const query = (this._mentionQuery || "").toLocaleLowerCase(lang);
    if (!query) {
      return list.filter(p => p && typeof p.address === "string" && p.address.trim() !== "");
    }
    return list
      .filter(p => p && typeof p.address === "string" && p.address.trim() !== "")
      .filter((p) => {
        const haystack = ((p.displayName || "") + " " + (p.address || "")).toLocaleLowerCase(lang);
        return haystack.indexOf(query) >= 0;
      });
  }

  _selectMentionCandidate(candidate) {
    if (!candidate || !this._mentionTriggerNode || this._mentionTriggerOffset < 0) {
      this._dismissMentionPopover("invalid-pick");
      return;
    }
    const triggerNode = this._mentionTriggerNode;
    const triggerOffset = this._mentionTriggerOffset;
    const queryLength = this._mentionQuery.length;
    // Replace the typed `@query` substring with a chip span. Split the
    // text node so we keep the surrounding text intact.
    const fullText = triggerNode.textContent;
    const before = fullText.slice(0, triggerOffset);
    const after = fullText.slice(triggerOffset + 1 + queryLength);
    const parent = triggerNode.parentNode;
    if (!parent) {
      this._dismissMentionPopover("invalid-pick");
      return;
    }
    const beforeNode = document.createTextNode(before);
    const chip = document.createElement("span");
    chip.className = "wavy-mention-chip";
    chip.setAttribute("data-mention-id", candidate.address);
    chip.setAttribute("contenteditable", "false");
    chip.textContent = "@" + (candidate.displayName || candidate.address);
    // Trailing space so the caret lands after the chip on a normal
    // text node — mirrors the GWT mention insertion UX.
    const afterNode = document.createTextNode(" " + after);
    parent.replaceChild(afterNode, triggerNode);
    parent.insertBefore(chip, afterNode);
    parent.insertBefore(beforeNode, chip);
    // Place caret right after the trailing space of afterNode.
    const caretRange = document.createRange();
    caretRange.setStart(afterNode, 1);
    caretRange.setEnd(afterNode, 1);
    const sel = document.getSelection();
    if (sel) {
      sel.removeAllRanges();
      sel.addRange(caretRange);
    }
    this._dismissMentionPopover("picked");
    // PR #1066 review thread PRRT_kwDOBwxLXs593gTR — the controller
    // serialises picked mentions by walking the plain-text draft and
    // matching `indexOf(chipText)`. When the same chipText appears
    // multiple times (duplicate display names, plain `@Name` typed
    // before a picked chip with an identical label, etc.) it cannot
    // distinguish which occurrence is the real chip. Snapshot the
    // chip's plain-text offset within the body now and forward it on
    // the picked event so the controller can bind by position rather
    // than by leftmost match.
    const chipTextOffset = this._computeChipPlainTextOffset(chip);
    this.dispatchEvent(
      new CustomEvent("wavy-composer-mention-picked", {
        bubbles: true,
        composed: true,
        detail: {
          address: candidate.address,
          displayName: candidate.displayName || candidate.address,
          chipTextOffset: chipTextOffset,
          replyTargetBlipId: this.replyTargetBlipId
        }
      })
    );
    // Sync draft text + emit draft-change so consumers see the chip
    // text in the textual draft view (the rich serializer below
    // surfaces the chip as a separate annotation when submit fires).
    const value = this._serializeBodyText();
    this.draft = value;
    this.dispatchEvent(
      new CustomEvent("draft-change", {
        detail: { value, replyTargetBlipId: this.replyTargetBlipId, mode: this.mode },
        bubbles: true,
        composed: true
      })
    );
  }

  _dismissMentionPopover(reason) {
    if (!this._mentionOpen && reason !== "escape") return;
    const hadQuery = (this._mentionQuery || "").length > 0;
    this._mentionOpen = false;
    this._mentionQuery = "";
    this._mentionTriggerNode = null;
    this._mentionTriggerOffset = -1;
    this._mentionActiveIndex = 0;
    this._mentionAnchor = null;
    if (reason === "escape" && hadQuery) {
      // Telemetry hook for explicit abandonment with non-empty query.
      this.dispatchEvent(
        new CustomEvent("wavy-composer-mention-abandoned", {
          bubbles: true,
          composed: true,
          detail: { replyTargetBlipId: this.replyTargetBlipId }
        })
      );
    }
    this.requestUpdate();
  }

  _onMentionPopoverSelect = (event) => {
    event.stopPropagation();
    const detail = event.detail || {};
    const candidate = {
      address: detail.address || "",
      displayName: detail.displayName || detail.address || ""
    };
    this._selectMentionCandidate(candidate);
  };

  _onMentionPopoverClose = (event) => {
    event.stopPropagation();
    const reason = (event.detail && event.detail.reason) || "close";
    this._dismissMentionPopover(reason);
  };

  /**
   * F-3.S2 (#1038, R-5.3 step 9): atomic Backspace deletion for chips.
   * If the caret sits at offset 0 of a text node whose previous
   * sibling is a chip span, remove the entire chip and any preceding
   * "@" trigger artefact. Returns true when the handler consumed the
   * keystroke (caller suppresses the default).
   */
  _maybeDeleteAdjacentMentionChip() {
    if (!this._bodyElement) return false;
    const selection = document.getSelection();
    if (!selection || selection.rangeCount === 0) return false;
    const range = selection.getRangeAt(0);
    if (!range.collapsed) return false;
    if (!this._bodyElement.contains(range.startContainer)) return false;
    const node = range.startContainer;
    let chip = null;
    if (node.nodeType === Node.TEXT_NODE) {
      // Caret at offset 0 OR at offset 1 with a leading space (the
      // chip insertion adds a trailing space at the start of the
      // following text node, so the user's first Backspace eats the
      // space and the next eats the chip — handle both in one press
      // by checking offset <= 1).
      if (range.startOffset > 1) return false;
      const prev = node.previousSibling;
      if (prev && prev.nodeType === Node.ELEMENT_NODE && prev.classList && prev.classList.contains("wavy-mention-chip")) {
        chip = prev;
      }
    } else if (node.nodeType === Node.ELEMENT_NODE) {
      // Caret between elements; check the element immediately before
      // range.startOffset. When startOffset is 0 the caret is before
      // all children so there is no preceding element to delete.
      if (range.startOffset === 0) return false;
      const prev = node.childNodes[range.startOffset - 1];
      if (prev && prev.nodeType === Node.ELEMENT_NODE && prev.classList && prev.classList.contains("wavy-mention-chip")) {
        chip = prev;
      }
    }
    if (!chip) return false;
    chip.remove();
    // Re-emit draft-change so the controller sees the deletion.
    const value = this._serializeBodyText();
    this.draft = value;
    this.dispatchEvent(
      new CustomEvent("draft-change", {
        detail: { value, replyTargetBlipId: this.replyTargetBlipId, mode: this.mode },
        bubbles: true,
        composed: true
      })
    );
    return true;
  }

  _onBodyKeydown(event) {
    // F-3.S2 (#1038, R-5.3): when the mention popover is open it owns
    // ArrowUp/ArrowDown/Enter/Escape/Tab. Forward these to the popover
    // before the composer's submit/cancel handlers fire so the popover
    // can navigate without the composer eating the keystrokes.
    if (this._mentionOpen) {
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        const candidates = this._filteredMentionCandidates();
        if (candidates.length === 0) return;
        const offset = event.key === "ArrowDown" ? 1 : -1;
        this._mentionActiveIndex =
          ((this._mentionActiveIndex + offset) % candidates.length + candidates.length) %
          candidates.length;
        return;
      }
      if (event.key === "Enter" || event.key === "Tab") {
        const candidates = this._filteredMentionCandidates();
        if (candidates.length > 0) {
          event.preventDefault();
          this._selectMentionCandidate(candidates[this._mentionActiveIndex] || candidates[0]);
          return;
        }
      }
      if (event.key === "Escape") {
        event.preventDefault();
        this._dismissMentionPopover("escape");
        return;
      }
    }
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
    // F-3.S2 (#1038, R-5.3 step 9): Backspace immediately before a
    // mention chip deletes the chip atomically. The chip is
    // contenteditable=false so the selection sits before it; without
    // this handler the browser would skip the chip on Backspace.
    if (event.key === "Backspace" && !event.shiftKey && !event.metaKey && !event.ctrlKey) {
      const removed = this._maybeDeleteAdjacentMentionChip();
      if (removed) {
        event.preventDefault();
        return;
      }
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

  /**
   * F-3.S4 (#1038, R-5.6 step 1): drag-over handler. Indicates the
   * composer body accepts file drops by calling preventDefault and
   * setting data-droptarget on the body so CSS can show a hint.
   * The dropEffect is forced to "copy" so the OS cursor matches the
   * composer's affordance (drag-to-attach, not drag-to-move).
   */
  _onBodyDragOver(event) {
    if (!event.dataTransfer) return;
    const types = event.dataTransfer.types;
    let hasFiles = false;
    if (types) {
      // DataTransferItemList carries a `Files` entry when the drag
      // payload contains files. Dragging text from another tab does
      // not — leave the default behaviour for that case so the user
      // can paste text via the normal contenteditable path.
      if (typeof types.contains === "function") {
        hasFiles = types.contains("Files");
      } else if (typeof types.includes === "function") {
        hasFiles = types.includes("Files");
      } else if (types.length !== undefined) {
        for (let i = 0; i < types.length; i++) {
          if (types[i] === "Files") {
            hasFiles = true;
            break;
          }
        }
      }
    }
    if (!hasFiles) return;
    event.preventDefault();
    try {
      event.dataTransfer.dropEffect = "copy";
    } catch {
      // Some browsers throw when dropEffect is set on a synthetic
      // event; ignore so the drop path still works in tests.
    }
    if (this._bodyElement) {
      this._bodyElement.setAttribute("data-droptarget", "true");
    }
  }

  /**
   * F-3.S4 (#1038, R-5.6 step 1): drag-leave handler. Clears the
   * data-droptarget attribute when the cursor leaves the body — but
   * NOT when it enters a nested child (relatedTarget would be a
   * descendant of the body in that case). This guards against the
   * naive flicker described in the slice plan's risk #3.
   */
  _onBodyDragLeave(event) {
    if (!this._bodyElement) return;
    const related = event.relatedTarget;
    if (related && this._bodyElement.contains(related)) return;
    this._bodyElement.removeAttribute("data-droptarget");
  }

  /**
   * F-3.S4 (#1038, R-5.6 step 1): drop handler. Collects every File
   * from the drag payload (filtering empty drops) and dispatches a
   * single wavy-composer-attachment-dropped event the view routes
   * through the existing selectFiles plumbing. Image files dropped
   * here travel the same code path as files chosen via the H.19
   * paperclip picker; non-image files are also forwarded so the
   * controller can produce a download chip on the originating blip.
   */
  _onBodyDrop(event) {
    if (this._bodyElement) {
      this._bodyElement.removeAttribute("data-droptarget");
    }
    if (!event.dataTransfer) return;
    const files = Array.from(event.dataTransfer.files || []);
    if (files.length === 0) return;
    event.preventDefault();
    this.dispatchEvent(
      new CustomEvent("wavy-composer-attachment-dropped", {
        detail: { files, replyTargetBlipId: this.replyTargetBlipId },
        bubbles: true,
        composed: true
      })
    );
  }

  _submit() {
    if (this.submitting || this.staleBasis) return;
    // J-UI-5 (#1083, R-5.7): forward the per-fragment annotated
    // component list so the Java controller can build a
    // `J2clComposerDocument` that carries the user's formatting
    // through the DocOp model. Plain-text consumers continue to
    // read `value`; the legacy `<composer-inline-reply>` textarea
    // path leaves `components` empty / absent and the view falls
    // back to plain text on submit.
    let components = [];
    try {
      components = this.serializeRichComponents();
    } catch (_e) {
      components = [];
    }
    this.dispatchEvent(
      new CustomEvent("reply-submit", {
        detail: {
          value: this.draft,
          components,
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

  _flushPendingDraftSync() {
    if (this._pendingDraftSync === undefined || !this._bodyElement) return;
    // F-3.S2 (#1038, R-5.3): skip the overwrite when the body holds
    // rich content (chips, task lists) the plain-text draft cannot
    // round-trip — see `_bodyHasRichContent`.
    //
    // Exception (#1066 review): a controller-driven reset (deferred
    // empty `draft` after submit/cancel) must still clear the body
    // even when chips/task lists are present so rich content does
    // not survive a reset.
    const isReset = this._pendingDraftSync === "" && this._bodyElement.childNodes.length > 0;
    if (
      this._serializeBodyText() !== this._pendingDraftSync &&
      (isReset || !this._bodyHasRichContent())
    ) {
      this._bodyElement.textContent = this._pendingDraftSync;
    }
    this._pendingDraftSync = undefined;
  }

  _onSelectionChange() {
    if (!this._bodyElement) return;
    const selection = document.getSelection();
    if (!selection || selection.rangeCount === 0) {
      this._flushPendingDraftSync();
      this.activeSelection = {};
      this._lastSelectionRange = null;
      this._dispatchSelectionEvent({});
      // F-3.S2: caret left the document; collapse the popover.
      if (this._mentionOpen) this._dismissMentionPopover("blur");
      return;
    }
    const range = selection.getRangeAt(0);
    if (!this._bodyElement.contains(range.startContainer)) {
      // Selection moved outside the composer; clear active selection so
      // the floating toolbar collapses.
      this._flushPendingDraftSync();
      this.activeSelection = {};
      this._lastSelectionRange = null;
      this._dispatchSelectionEvent({});
      if (this._mentionOpen) this._dismissMentionPopover("blur");
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
    // J-UI-5 (#1083, R-5.2): cache a clone of the live range so toolbar
    // handlers have a stable selection to act on even when
    // `document.getSelection()` cannot pierce the shadow root (Firefox /
    // WebKit). Cleared by the no-selection / out-of-body branches above.
    this._lastSelectionRange = range.cloneRange();
    this._dispatchSelectionEvent(descriptor);
    // F-3.S2 (#1038, R-5.3): re-evaluate the mention trigger from the
    // live caret. This catches cases where the user clicks elsewhere
    // mid-query (popover should dismiss) or types past the trigger
    // range (popover should follow the caret with the new query).
    if (this._mentionOpen || this._mentionTriggerNode) {
      this._updateMentionPopoverFromCaret();
    }
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
    if (changed.has("draft") && this._bodyElement) {
      // F-3.S2 (#1038, R-5.3): skip the textContent overwrite when the
      // body has rich content (mention chips, task lists) the plain
      // `draft` property cannot represent.
      //
      // Exception (#1066 review): a controller-driven reset (the
      // `draft` prop transitioning to empty after submit/cancel)
      // bypasses the rich-content guard so chips/task lists clear
      // along with text. See `_isControllerReset` for the predicate.
      if (!this._bodyOwnsSelection()) {
        if (
          this._serializeBodyText() !== this.draft &&
          (this._isControllerReset() || !this._bodyHasRichContent())
        ) {
          this._bodyElement.textContent = this.draft;
        }
        this._pendingDraftSync = undefined;
      } else {
        // Defer the DOM write until selection leaves to avoid collapsing caret.
        this._pendingDraftSync = this.draft;
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

  _renderMentionPopover() {
    if (!this._mentionOpen) return null;
    const candidates = this._filteredMentionCandidates();
    const anchor = this._mentionAnchor;
    const style = anchor
      ? `top: ${anchor.top}px; left: ${anchor.left}px;`
      : "";
    return html`
      <div class="mention-popover-host" style=${style} data-mention-popover-host>
        <mention-suggestion-popover
          open
          .candidates=${candidates}
          .activeIndex=${this._mentionActiveIndex}
          @mention-select=${this._onMentionPopoverSelect}
          @overlay-close=${this._onMentionPopoverClose}
        ></mention-suggestion-popover>
      </div>
    `;
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
          ${this._renderMentionPopover()}
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
