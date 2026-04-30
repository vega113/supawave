import { LitElement, css, html } from "lit";
import "../design/wavy-edit-toolbar.js";
import { FONT_FAMILY_OPTIONS, FONT_SIZE_OPTIONS } from "../format/font-options.js";
import "./toolbar-button.js";
import "./toolbar-group.js";

/**
 * <wavy-format-toolbar> — F-3.S1 (#1038, R-5.2) selection-driven floating
 * toolbar for the rich-text composer. Wraps the F-0 `<wavy-edit-toolbar>`
 * recipe; mounts the H.* daily-rich-edit subset called out by the issue body.
 *
 * Anchoring (R-5.2 step 1):
 * - position: fixed + transform: translate(<x>px, <y>px) keyed off
 *   `selection.getRangeAt(0).getBoundingClientRect()` of the active
 *   composer body.
 * - Recomputed on `selectionchange` (via the selectionDescriptor
 *   property setter) AND on `scroll` AND on `resize`, all coalesced
 *   through one rAF callback.
 * - Hidden when the selection is collapsed OR the composer loses focus
 *   (the controller updates `selectionDescriptor` to a collapsed value).
 *
 * Toggle state (R-5.2 step 3):
 * - Each toolbar button's aria-pressed reflects the active annotation
 *   set in the selectionDescriptor.activeAnnotations array. The map
 *   below pins each `J2clDailyToolbarAction` ID to the annotation tag
 *   names that should light its toggle.
 *
 * Action emission (R-5.2 step 4):
 * - Clicking a toolbar button emits `wavy-format-toolbar-action`
 *   `{detail: {actionId, selectionDescriptor}}`. The composer
 *   controller maps each actionId to a DocOp delta via
 *   `J2clRichContentDeltaFactory`.
 *
 * Plugin slot:
 * - `toolbar-extension` is forwarded to the inner `<wavy-edit-toolbar>`
 *   recipe (which exposes the slot per F-0).
 */
const ACTIVE_ANNOTATION_MAP = {
  bold: ["strong", "b"],
  italic: ["em", "i"],
  underline: ["u"],
  strikethrough: ["s", "strike", "del"],
  superscript: ["sup"],
  subscript: ["sub"],
  "unordered-list": ["ul"],
  "ordered-list": ["ol"],
  "align-left": [],
  "align-center": [],
  "align-right": [],
  rtl: [],
  link: ["a"],
  unlink: ["a"],
  "clear-formatting": [],
  indent: [],
  outdent: [],
  heading: ["h1", "h2", "h3", "h4"],
  // F-3.S2 (#1038, R-5.4 step 6): Insert-task is non-toggleable; the
  // active state is owned by the per-blip task affordance, not the
  // inline checkbox markup.
  "insert-task": [],
  // F-3.S4 (#1038, R-5.6 H.19): attachment paperclip is non-toggleable;
  // clicking it opens the existing hidden file input via the view's
  // openAttachmentPicker path.
  "attachment-insert": []
};

/*
 * V-3 (#1101): groups + ordering reflect the
 * 03-inline-rich-text-composer.svg mockup left-to-right. Each group
 * id is rendered as a <toolbar-group> wrapper, with explicit
 * <span class="toolbar-divider"> siblings between groups. The action
 * IDs are unchanged — wavy-format-toolbar-action subscribers (composer
 * controller, etc.) see the same `actionId` payload. This is purely a
 * visual structural change.
 */
const DAILY_RICH_EDIT_ACTIONS = [
  { id: "bold", label: "Bold", group: "text", toggle: true },
  { id: "italic", label: "Italic", group: "text", toggle: true },
  { id: "underline", label: "Underline", group: "text", toggle: true },
  { id: "strikethrough", label: "Strikethrough", group: "text", toggle: true },
  { id: "superscript", label: "Superscript", group: "text", toggle: true },
  { id: "subscript", label: "Subscript", group: "text", toggle: true },
  {
    id: "font-family",
    label: "Font",
    group: "text",
    toggle: false,
    kind: "select",
    options: FONT_FAMILY_OPTIONS
  },
  {
    id: "font-size",
    label: "Size",
    group: "text",
    toggle: false,
    kind: "select",
    options: FONT_SIZE_OPTIONS
  },
  { id: "heading", label: "Heading", group: "block", toggle: false },
  { id: "unordered-list", label: "Bulleted list", group: "block", toggle: true },
  { id: "ordered-list", label: "Numbered list", group: "block", toggle: true },
  { id: "indent", label: "Indent", group: "block", toggle: false },
  { id: "outdent", label: "Outdent", group: "block", toggle: false },
  { id: "align-left", label: "Align left", group: "align", toggle: false },
  { id: "align-center", label: "Align center", group: "align", toggle: false },
  { id: "align-right", label: "Align right", group: "align", toggle: false },
  { id: "rtl", label: "Right-to-left", group: "align", toggle: false },
  { id: "link", label: "Insert link", group: "link", toggle: false },
  { id: "unlink", label: "Remove link", group: "link", toggle: false },
  { id: "clear-formatting", label: "Clear formatting", group: "clear", toggle: false },
  // F-3.S2 (#1038, R-5.4 step 6 + H.20): inline task list insert.
  // Display-only inside the body (the `<input type="checkbox">` is
  // disabled — the per-blip task affordance owns model state).
  { id: "insert-task", label: "Insert task", group: "insert", toggle: false },
  // F-3.S4 (#1038, R-5.6 H.19): attachment paperclip. Clicking emits
  // wavy-format-toolbar-action with actionId "attachment-insert"; the
  // controller maps that to view.openAttachmentPicker() via the
  // existing handleAttachmentToolbarAction path.
  { id: "attachment-insert", label: "Attach file", group: "insert", toggle: false }
];

const GROUP_ORDER = ["text", "block", "align", "link", "clear", "insert"];
const GROUP_LABELS = {
  text: "Text formatting",
  block: "Block formatting",
  align: "Alignment",
  link: "Link",
  clear: "Clear formatting",
  insert: "Insert"
};

export class WavyFormatToolbar extends LitElement {
  static properties = {
    hidden: { type: Boolean, reflect: true },
    selectionDescriptor: { type: Object, attribute: false }
  };

  static styles = css`
    :host {
      position: fixed;
      top: 0;
      left: 0;
      transform: translate(-9999px, -9999px);
      z-index: 1000;
      transition: opacity var(--wavy-motion-focus-duration, 180ms)
        var(--wavy-easing-focus, cubic-bezier(0.2, 0, 0.2, 1));
      opacity: 1;
      pointer-events: auto;
    }
    :host([hidden]) {
      opacity: 0;
      pointer-events: none;
      display: none;
    }
    .toolbar-select {
      min-height: 28px;
      max-width: 8.75rem;
      border: 1px solid var(--wavy-border-hairline, rgba(11, 19, 32, 0.16));
      border-radius: var(--wavy-radius-pill, 9999px);
      background: var(--wavy-bg-card, #ffffff);
      color: var(--wavy-text-body, #172033);
      font: var(--wavy-type-label, 0.75rem / 1.35 Arial, sans-serif);
      padding: 0 var(--wavy-spacing-3, 12px);
    }
    .toolbar-select:focus-visible {
      box-shadow: var(--wavy-focus-ring, 0 0 0 2px #22d3ee);
      outline: none;
    }
  `;

  constructor() {
    super();
    this.hidden = true;
    this.selectionDescriptor = {};
    this._frameHandle = 0;
    this._cachedRange = null;
    this._handleScroll = () => this._scheduleRepositionFromCachedRect();
    this._handleResize = () => this._scheduleRepositionFromCachedRect();
  }

  connectedCallback() {
    super.connectedCallback();
    window.addEventListener("scroll", this._handleScroll, true);
    window.addEventListener("resize", this._handleResize);
  }

  disconnectedCallback() {
    window.removeEventListener("scroll", this._handleScroll, true);
    window.removeEventListener("resize", this._handleResize);
    if (this._frameHandle) {
      cancelAnimationFrame(this._frameHandle);
      this._frameHandle = 0;
    }
    super.disconnectedCallback();
  }

  updated(changed) {
    if (changed.has("selectionDescriptor")) {
      // Cache the live Range when the selection becomes non-collapsed so that
      // scroll/resize repositioning re-reads the updated viewport rect of the
      // *same* range rather than whatever happens to be selected elsewhere.
      const descriptor = this.selectionDescriptor || {};
      if (descriptor.collapsed === false) {
        const sel = typeof document !== "undefined" ? document.getSelection() : null;
        this._cachedRange = sel && sel.rangeCount > 0 ? sel.getRangeAt(0) : null;
      } else {
        this._cachedRange = null;
      }
      this._scheduleReposition();
    }
  }

  _scheduleReposition() {
    if (this._frameHandle) return;
    this._frameHandle = requestAnimationFrame(() => {
      this._frameHandle = 0;
      this._reposition();
    });
  }

  _scheduleRepositionFromCachedRect() {
    // Re-read the position from the cached (composer-owned) Range rather than
    // document.getSelection(), which may point to an unrelated selection.
    if (this._frameHandle) return;
    this._frameHandle = requestAnimationFrame(() => {
      this._frameHandle = 0;
      if (!this.hidden && this._cachedRange) {
        const rect = this._cachedRange.getBoundingClientRect();
        if (rect.width > 0 || rect.height > 0) {
          this._repositionToRect(rect);
          return;
        }
      }
      this._reposition();
    });
  }

  _reposition() {
    const descriptor = this.selectionDescriptor || {};
    const collapsed = descriptor.collapsed !== false;
    const rect = descriptor.boundingRect;
    if (collapsed || !rect || (rect.width === 0 && rect.height === 0)) {
      this.hidden = true;
      this.style.transform = "translate(-9999px, -9999px)";
      return;
    }
    this._repositionToRect(rect);
  }

  _repositionToRect(rect) {
    this.hidden = false;
    // Position above the selection rect, anchor center horizontally.
    const x = Math.max(8, rect.left + rect.width / 2 - this.offsetWidth / 2);
    const y = Math.max(8, rect.top - this.offsetHeight - 8);
    this.style.transform = `translate(${Math.round(x)}px, ${Math.round(y)}px)`;
  }

  _isActionActive(action) {
    if (!action.toggle) return false;
    const annotations = (this.selectionDescriptor && this.selectionDescriptor.activeAnnotations) || [];
    const targets = ACTIVE_ANNOTATION_MAP[action.id] || [];
    if (targets.length === 0) return false;
    return targets.some((tag) => annotations.includes(tag));
  }

  _onToolbarAction(event) {
    // toolbar-button bubbles the canonical toolbar-action event with
    // {detail: {action: <id>}}. Re-dispatch as wavy-format-toolbar-action
    // so the composer controller sees one canonical event.
    const actionId = (event.detail && event.detail.action) || "";
    if (!actionId) return;
    event.stopPropagation();
    this.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: {
          actionId,
          selectionDescriptor: this.selectionDescriptor
        },
        bubbles: true,
        composed: true
      })
    );
  }

  _onSelectAction(action, event) {
    const target = event.target;
    const value = target && target.value ? target.value : "";
    if (!value) return;
    this.dispatchEvent(
      new CustomEvent("wavy-format-toolbar-action", {
        detail: {
          actionId: action.id,
          value,
          selectionDescriptor: this.selectionDescriptor
        },
        bubbles: true,
        composed: true
      })
    );
    target.value = "";
  }

  _renderAction(action) {
    if (action.kind === "select") {
      return html`<select
        class="toolbar-select"
        data-toolbar-action=${action.id}
        aria-label=${action.label}
        @change=${(event) => this._onSelectAction(action, event)}
      >
        <option value="">${action.label}</option>
        ${action.options.map((option) => html`<option value=${option}>${option}</option>`)}
      </select>`;
    }
    return html`<toolbar-button
      data-toolbar-action=${action.id}
      action=${action.id}
      icon=${action.id}
      label=${action.label}
      ?toggle=${action.toggle}
      ?pressed=${this._isActionActive(action)}
    ></toolbar-button>`;
  }

  _renderGroup(groupId) {
    const actions = DAILY_RICH_EDIT_ACTIONS.filter((a) => a.group === groupId);
    if (actions.length === 0) return null;
    return html`<toolbar-group label=${GROUP_LABELS[groupId] || groupId}>
      ${actions.map((action) => this._renderAction(action))}
    </toolbar-group>`;
  }

  render() {
    const groups = [];
    GROUP_ORDER.forEach((gid, idx) => {
      const tpl = this._renderGroup(gid);
      if (!tpl) return;
      if (groups.length > 0) {
        groups.push(html`<span class="toolbar-divider" aria-hidden="true"></span>`);
      }
      groups.push(tpl);
    });
    return html`
      <wavy-edit-toolbar @toolbar-action=${this._onToolbarAction.bind(this)}>
        ${groups}
        <slot name="toolbar-extension" slot="toolbar-extension"></slot>
      </wavy-edit-toolbar>
    `;
  }
}

if (!customElements.get("wavy-format-toolbar")) {
  customElements.define("wavy-format-toolbar", WavyFormatToolbar);
}

export const DAILY_RICH_EDIT_ACTION_IDS = DAILY_RICH_EDIT_ACTIONS.map((a) => a.id);
