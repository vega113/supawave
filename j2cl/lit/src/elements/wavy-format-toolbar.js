import { LitElement, css, html } from "lit";
import "../design/wavy-edit-toolbar.js";
import "./toolbar-button.js";
import "./toolbar-group.js";

/**
 * <wavy-format-toolbar> — F-3.S1 (#1038, R-5.2) selection-driven floating
 * toolbar for the rich-text composer. Wraps the F-0 `<wavy-edit-toolbar>`
 * recipe; mounts the H.* daily-rich-edit subset (H.1-H.4, H.9, H.12-H.18)
 * called out by the issue body.
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
  heading: ["h1", "h2", "h3", "h4"]
};

const DAILY_RICH_EDIT_ACTIONS = [
  { id: "bold", label: "Bold", group: "format", toggle: true },
  { id: "italic", label: "Italic", group: "format", toggle: true },
  { id: "underline", label: "Underline", group: "format", toggle: true },
  { id: "strikethrough", label: "Strikethrough", group: "format", toggle: true },
  { id: "heading", label: "Heading", group: "format", toggle: false },
  { id: "unordered-list", label: "Bulleted list", group: "list", toggle: true },
  { id: "ordered-list", label: "Numbered list", group: "list", toggle: true },
  { id: "indent", label: "Indent", group: "indent", toggle: false },
  { id: "outdent", label: "Outdent", group: "indent", toggle: false },
  { id: "align-left", label: "Align left", group: "align", toggle: true },
  { id: "align-center", label: "Align center", group: "align", toggle: true },
  { id: "align-right", label: "Align right", group: "align", toggle: true },
  { id: "rtl", label: "Right-to-left", group: "align", toggle: true },
  { id: "link", label: "Insert link", group: "link", toggle: false },
  { id: "unlink", label: "Remove link", group: "link", toggle: false },
  { id: "clear-formatting", label: "Clear formatting", group: "format", toggle: false }
];

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
  `;

  constructor() {
    super();
    this.hidden = true;
    this.selectionDescriptor = {};
    this._frameHandle = 0;
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
    // The cached selectionDescriptor's boundingRect is in viewport
    // coordinates, captured at the moment of the last selectionchange.
    // On scroll / resize we re-read the live selection rect so the
    // toolbar tracks the active range as the page scrolls.
    const selection = typeof document !== "undefined" ? document.getSelection() : null;
    if (selection && selection.rangeCount > 0) {
      const range = selection.getRangeAt(0);
      const rect = range.getBoundingClientRect();
      if (rect.width > 0 || rect.height > 0) {
        this.selectionDescriptor = {
          ...(this.selectionDescriptor || {}),
          collapsed: range.collapsed,
          boundingRect: {
            top: rect.top,
            left: rect.left,
            width: rect.width,
            height: rect.height
          }
        };
      }
    }
    this._scheduleReposition();
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

  render() {
    return html`
      <wavy-edit-toolbar @toolbar-action=${this._onToolbarAction.bind(this)}>
        ${DAILY_RICH_EDIT_ACTIONS.map(
          (action) => html`<toolbar-button
            data-toolbar-action=${action.id}
            action=${action.id}
            label=${action.label}
            ?toggle=${action.toggle}
            ?pressed=${this._isActionActive(action)}
          ></toolbar-button>`
        )}
        <slot name="toolbar-extension" slot="toolbar-extension"></slot>
      </wavy-edit-toolbar>
    `;
  }
}

if (!customElements.get("wavy-format-toolbar")) {
  customElements.define("wavy-format-toolbar", WavyFormatToolbar);
}

export const DAILY_RICH_EDIT_ACTION_IDS = DAILY_RICH_EDIT_ACTIONS.map((a) => a.id);
